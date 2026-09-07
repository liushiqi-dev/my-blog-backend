package com.liushiqi.blogmain.kb;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liushiqi.blogmain.common.exception.BusinessException;
import com.liushiqi.blogmain.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 中转站 OpenAI 兼容端点的 embedding 与 chat 适配层。
 * <p>
 * 复用 {@code RagRestClientConfig} 装配的两个 RestClient（共用 base-url 与鉴权头，仅读超时不同），
 * 在其后分别拼 {@code /embeddings} 与 {@code /chat/completions}。请求/响应用 record 承接 JSON，
 * 由 Spring 默认的消息转换器（Jackson）完成序列化与反序列化，请求体默认按 UTF-8 编码。
 * <p>
 * 两类调用都对网关的偶发连接重置/空响应做有限重试；失败信息只带状态码与响应体片段，不含鉴权 key。
 */
@Component
@Slf4j
public class LlmClient {

    /** 记录失败响应体片段的最大长度，避免超长响应刷爆日志。 */
    private static final int SNIPPET_LIMIT = 200;

    /** 重试前的退避基数（毫秒），按尝试次数线性递增，给网关从偶发连接重置中恢复的时间。 */
    private static final long RETRY_BACKOFF_MS = 500;

    private final RestClient embeddingRestClient;
    private final RestClient chatRestClient;
    private final RagProperties props;

    public LlmClient(@Qualifier("embeddingRestClient") RestClient embeddingRestClient,
                     @Qualifier("chatRestClient") RestClient chatRestClient,
                     RagProperties props) {
        this.embeddingRestClient = embeddingRestClient;
        this.chatRestClient = chatRestClient;
        this.props = props;
    }

    /**
     * 将文本批量向量化，返回与输入等长、顺序一致的向量列表。
     * <p>
     * 单次请求条数受 embeddingBatchSize 限制，超出时按批切片；每批响应内的 data 按 index 字段
     * 排序还原顺序（网关可能乱序返回）。返回前校验每条向量维度等于 embeddingDimension，
     * 防止维度不匹配的向量污染后续向量库。
     *
     * @param texts 待向量化文本
     * @return 与输入顺序一致的向量列表；输入为空时返回空列表
     */
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        int batchSize = props.getEmbeddingBatchSize();
        if (batchSize <= 0) {
            batchSize = texts.size();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            vectors.addAll(embedBatch(texts.subList(start, end)));
        }
        return vectors;
    }

    /**
     * 单批 embedding 调用，失败（IO 异常 / 非 2xx / 解析失败）时重试一次。
     */
    private List<float[]> embedBatch(List<String> batch) {
        EmbeddingRequest request = new EmbeddingRequest(
                props.getEmbeddingModel(), batch, props.getEmbeddingDimension(), "float");
        int maxAttempts = Math.max(1, props.getEmbeddingMaxRetries() + 1);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                EmbeddingResponse response = embeddingRestClient.post()
                        .uri("/embeddings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(EmbeddingResponse.class);
                return toOrderedVectors(response, batch.size());
            } catch (RestClientResponseException e) {
                last = e;
                log.warn("embedding 调用失败 HTTP {}（第 {}/{} 次），响应片段: {}",
                        e.getStatusCode().value(), attempt, maxAttempts, snippet(e.getResponseBodyAsString()));
                if (isNonRetryable(e)) {
                    break;
                }
            } catch (RestClientException e) {
                last = e;
                log.warn("embedding 调用异常（第 {}/{} 次）: {}", attempt, maxAttempts, e.getMessage());
            }
            backoff(attempt, maxAttempts);
        }
        throw new BusinessException("embedding 调用失败: " + describe(last), last);
    }

    /**
     * 将单批响应按 index 排序还原为与请求等长的向量列表，并校验每条向量维度。
     */
    private List<float[]> toOrderedVectors(EmbeddingResponse response, int expected) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new BusinessException("embedding 响应体为空");
        }
        List<EmbeddingData> data = new ArrayList<>(response.data());
        data.sort(Comparator.comparingInt(EmbeddingData::index));
        if (data.size() != expected) {
            throw new BusinessException("embedding 返回条数 " + data.size() + " 与请求条数 " + expected + " 不一致");
        }
        int dimension = props.getEmbeddingDimension();
        List<float[]> vectors = new ArrayList<>(expected);
        for (EmbeddingData item : data) {
            float[] embedding = item.embedding();
            int actual = embedding == null ? 0 : embedding.length;
            if (actual != dimension) {
                throw new BusinessException("embedding 维度不匹配，期望 " + dimension + " 实际 " + actual);
            }
            vectors.add(embedding);
        }
        return vectors;
    }

    /**
     * 调用 chat 补全接口生成回答，仅取 choices[0].message.content。
     * <p>
     * 该模型为推理模型，响应含 reasoning_content 字段，此处只取 content 而忽略 reasoning_content。
     * 对连接异常、响应体为空、content 为空的情况重试至多 chatMaxRetries 次，仍失败则抛业务异常。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型生成的正文内容
     */
    public String chat(String systemPrompt, String userPrompt) {
        ChatRequest request = new ChatRequest(
                props.getChatModel(),
                List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userPrompt)),
                false,
                props.getChatMaxTokens());
        int maxAttempts = Math.max(1, props.getChatMaxRetries() + 1);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ChatResponse response = chatRestClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(ChatResponse.class);
                String content = extractContent(response);
                if (content != null && !content.isBlank()) {
                    return content;
                }
                // content 为空（含被 max_tokens 截断、finish_reason=length 的情况）视为失败并重试
                last = new BusinessException("chat content 为空");
                log.warn("chat 返回空 content（第 {}/{} 次），finish_reason={}",
                        attempt, maxAttempts, finishReason(response));
            } catch (RestClientResponseException e) {
                last = e;
                log.warn("chat 调用失败 HTTP {}（第 {}/{} 次），响应片段: {}",
                        e.getStatusCode().value(), attempt, maxAttempts, snippet(e.getResponseBodyAsString()));
                if (isNonRetryable(e)) {
                    break;
                }
            } catch (RestClientException e) {
                last = e;
                log.warn("chat 调用异常（第 {}/{} 次）: {}", attempt, maxAttempts, e.getMessage());
            }
            backoff(attempt, maxAttempts);
        }
        throw new BusinessException("chat 调用失败: " + describe(last), last);
    }

    /**
     * 从 chat 响应取出首个 choice 的 message.content；结构不完整时返回 null。
     */
    private String extractContent(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        ChatChoice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            return null;
        }
        return choice.message().content();
    }

    /**
     * 读取首个 choice 的 finish_reason，仅用于失败日志。
     */
    private String finishReason(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        ChatChoice choice = response.choices().get(0);
        return choice == null ? null : choice.finishReason();
    }

    /**
     * 重试前线性退避；已是最后一次尝试则不再等待，避免白白拖长失败响应。
     */
    private void backoff(int attempt, int maxAttempts) {
        if (attempt >= maxAttempts) {
            return;
        }
        try {
            Thread.sleep(RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 4xx（429 限流除外）是请求本身的问题——鉴权失败、模型名不存在、参数非法，
     * 重试只会白等一轮再报同样的错，故直接失败；5xx 与连接异常才是网关偶发故障，值得重试。
     */
    private static boolean isNonRetryable(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return status >= 400 && status < 500 && status != 429;
    }

    /**
     * 构造失败描述：HTTP 响应异常带状态码与响应体片段，其余异常带原始消息。均不含鉴权 key。
     */
    private String describe(RuntimeException e) {
        if (e instanceof RestClientResponseException re) {
            return "HTTP " + re.getStatusCode().value() + " " + snippet(re.getResponseBodyAsString());
        }
        return e == null ? "未知错误" : String.valueOf(e.getMessage());
    }

    /**
     * 截断响应体用于日志，避免超长输出。
     */
    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.strip();
        return trimmed.length() <= SNIPPET_LIMIT ? trimmed : trimmed.substring(0, SNIPPET_LIMIT) + "...";
    }

    // ---- 请求/响应 JSON 结构（record 由 Jackson 直接映射） ----

    record EmbeddingRequest(String model,
                            List<String> input,
                            int dimensions,
                            @JsonProperty("encoding_format") String encodingFormat) {
    }

    record EmbeddingResponse(List<EmbeddingData> data) {
    }

    record EmbeddingData(int index, float[] embedding) {
    }

    record ChatRequest(String model,
                       List<ChatMessage> messages,
                       boolean stream,
                       @JsonProperty("max_tokens") int maxTokens) {
    }

    record ChatMessage(String role, String content) {
    }

    record ChatResponse(List<ChatChoice> choices) {
    }

    record ChatChoice(ChatResponseMessage message,
                      @JsonProperty("finish_reason") String finishReason) {
    }

    record ChatResponseMessage(String role,
                               String content,
                               @JsonProperty("reasoning_content") String reasoningContent) {
    }
}
