package com.liushiqi.blogmain.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RAG 问答链路的两套 HTTP 客户端装配。
 * <p>
 * 本类做两件事：
 * 1. 用 {@code @EnableConfigurationProperties(RagProperties.class)} 把 {@link RagProperties}
 *    注册进 Spring 容器（这样就不用去改启动类 BlogMainApplication，侵入最小）。
 * 2. 声明两个 {@link RestClient} Bean：embeddingRestClient（向量化）与 chatRestClient（答案生成）。
 * <p>
 * 🔧 为什么拆成两个 Client，但它们又指向同一个地址？
 * - 同一个中转站：embedding 与 chat 都走同一个 OpenAI 兼容网关，base-url 与 API key 完全一致，
 *   都取自 {@link RagProperties} 的 baseUrl / apiKey，不再区分供应商。
 * - 仅仅因为超时不同：向量化是「一进一出」的短请求，15s 足够；而 LLM 生成是逐 token 吐字的慢请求，
 *   读超时必须放大到 30s，否则长回答会被中途掐断。两者若共用一套超时，要么 embedding 等太久、
 *   要么 chat 被误杀，所以拆成两个只「读超时不同」的 Client（connectTimeout 仍共用同一值）。
 * <p>
 * 🚀 为什么用 {@link JdkClientHttpRequestFactory}？
 * - 它底层直接用 JDK 自带的 {@code java.net.http.HttpClient}（Java 11+ 内置），零新增依赖，
 *   不用再引 Apache HttpClient / OkHttp，打包体积和依赖冲突都更可控。
 * - 连接超时（connectTimeout）设在 {@link HttpClient} 上，读超时（readTimeout）设在工厂上，
 *   两者语义不同：前者是「建立 TCP 连接」的等待上限，后者是「连接建好后等响应数据」的等待上限。
 * <p>
 * ⚠️ 故障隔离：这里创建 Bean 时【不校验】apiKey 是否为空——即便 key 是空串，Bean 也照常装配，
 * 应用能正常启动，博客主站完全不受影响。只有真正发起 /kb 问答、调用到大模型时才会因鉴权失败报错。
 * 这样把「大模型不可用」的爆炸半径死死限制在 RAG 链路内，绝不外溢到主站。
 * <p>
 * 💡 八股文关联：
 * - {@code Authorization: Bearer <key>} 是 OpenAI 兼容接口的统一鉴权约定，中转站聚合的各模型都遵循。
 * - defaultHeader 在 build 时固定下来，之后每次请求自动带上，避免在业务代码里重复拼鉴权头。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagRestClientConfig {

    /**
     * embedding（文本向量化）客户端：指向中转站的 OpenAI 兼容接口。
     * <p>与 chatRestClient 共用同一个 baseUrl 与同一个 apiKey，仅读超时不同。
     *
     * @param props RAG 配置，提供共用的 base-url、apiKey 与超时参数
     * @return 已固定 baseUrl、鉴权头与超时的 RestClient
     */
    @Bean("embeddingRestClient")
    public RestClient embeddingRestClient(RagProperties props) {
        // 连接超时设在 JDK HttpClient 上，读超时设在工厂上，二者组合出完整的超时控制
        JdkClientHttpRequestFactory factory = requestFactory(
                props.getConnectTimeoutMs(), props.getEmbeddingReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .requestFactory(factory)
                .build();
    }

    /**
     * chat（答案生成）客户端：同样指向中转站的 OpenAI 兼容接口。
     * <p>与 embeddingRestClient 共用同一个 baseUrl 与同一个 apiKey，只是读超时放大到 chatReadTimeoutMs，
     * 因为 LLM 生成响应慢。
     *
     * @param props RAG 配置，提供共用的 base-url、apiKey 与超时参数
     * @return 已固定 baseUrl、鉴权头与超时的 RestClient
     */
    @Bean("chatRestClient")
    public RestClient chatRestClient(RagProperties props) {
        // chat 的读超时显著大于 embedding，因为 LLM 生成响应慢；baseUrl / apiKey 与 embedding 完全相同
        JdkClientHttpRequestFactory factory = requestFactory(
                props.getConnectTimeoutMs(), props.getChatReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .requestFactory(factory)
                .build();
    }

    /**
     * 构造一个带显式超时的 {@link JdkClientHttpRequestFactory}（基于 JDK 内置 HttpClient，零新增依赖）。
     *
     * @param connectTimeoutMs 建立连接的超时（毫秒）
     * @param readTimeoutMs    读取响应的超时（毫秒）
     * @return 配置好超时的请求工厂
     */
    private JdkClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }
}
