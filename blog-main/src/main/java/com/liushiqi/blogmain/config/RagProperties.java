package com.liushiqi.blogmain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG（检索增强生成）知识库问答的集中配置。
 * <p>
 * 本类通过 {@code @ConfigurationProperties(prefix = "rag")} 把 application-*.yaml 里
 * {@code rag.*} 一段配置一次性映射成强类型对象，避免在业务代码里到处写
 * {@code @Value("${rag.xxx}")}。所有和「模型 / 分块 / 超时」相关的可调参数都收敛到这里，
 * 做到「单一配置源」——比如向量维度 dimension，建库、写入、检索、FT.CREATE 的 DIM
 * 必须完全一致，只在这里配一次就不会出现三处写死对不上的问题。
 * <p>
 * 注册方式：本类只标注 {@code @ConfigurationProperties}，不加 {@code @Component}，
 * 改由 {@link RagRestClientConfig} 上的 {@code @EnableConfigurationProperties(RagProperties.class)}
 * 完成注册。这样做侵入最小——不用去改启动类 BlogMainApplication，配置和装配集中在 config 包内。
 * <p>
 * 💡 八股文关联：
 * - {@code @ConfigurationProperties} 支持「松散绑定」：yaml 里的 base-url / batch-size
 *   会自动绑定到 Java 的 baseUrl / batchSize 字段（kebab-case ↔ camelCase）。
 * - 与 {@code @Value} 相比，它能一次性绑定整棵对象树、支持嵌套、支持校验，适合成组的配置。
 * <p>
 * ⚠️ 故障隔离设计：embedding.apiKey / chat.apiKey 在 yaml 里用 {@code ${DASHSCOPE_API_KEY:}}
 * 带「空默认值」的占位符，含义是「环境变量没配也不报错，就当成空串」。这样即使运维忘了配
 * 大模型的 key，博客主站（发文、点赞、评论等）照样能启动，只有真正调用大模型的 /kb 问答链路
 * 会在运行时报错。切忌在这里做「key 为空就抛异常」的强校验，那会导致整个应用起不来。
 */
@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** embedding（文本向量化）供应商配置：走阿里云百炼 DashScope 的 OpenAI 兼容接口。 */
    private Embedding embedding = new Embedding();

    /** chat（答案生成）供应商配置：走 DeepSeek 的 OpenAI 兼容接口。 */
    private Chat chat = new Chat();

    /** KNN 向量检索返回的候选分块数量，越大召回越全但噪声与耗时也越多。 */
    private int topK = 5;

    /** 文章切分块时的单块长度（字符数）。 */
    private int chunkSize = 500;

    /** 相邻分块之间的重叠长度（字符数），用于保留跨块的上下文语义。 */
    private int chunkOverlap = 50;

    /** 建立 TCP 连接的超时时间（毫秒），embedding 与 chat 两个 Client 共用。 */
    private int connectTimeoutMs = 3000;

    /** 读取 embedding 响应的超时时间（毫秒），向量化较快，给 15s 足够。 */
    private int embeddingReadTimeoutMs = 15000;

    /** 读取 chat 响应的超时时间（毫秒），LLM 生成慢，须显著大于连接超时。 */
    private int chatReadTimeoutMs = 30000;

    /**
     * embedding 供应商（阿里云百炼 DashScope，OpenAI 兼容）相关参数。
     */
    @Data
    public static class Embedding {

        /** OpenAI 兼容接口的 base-url，请求路径在其后拼 /embeddings。 */
        private String baseUrl;

        /** 鉴权用的 API key，最终以 {@code Authorization: Bearer <apiKey>} 头发出；可能为空串。 */
        private String apiKey;

        /** 向量模型名称，此处为 text-embedding-v4。 */
        private String model;

        /** 向量维度，必须与建库、FT.CREATE 的 DIM 保持一致，否则检索会失败或召回失真。 */
        private int dimension;

        /** 单次请求最多携带的文本条数，DashScope 官方硬上限为 10。 */
        private int batchSize;
    }

    /**
     * chat 供应商（DeepSeek，OpenAI 兼容）相关参数。
     */
    @Data
    public static class Chat {

        /** OpenAI 兼容接口的 base-url，请求路径在其后拼 /chat/completions。 */
        private String baseUrl;

        /** 鉴权用的 API key，最终以 {@code Authorization: Bearer <apiKey>} 头发出；可能为空串。 */
        private String apiKey;

        /** 对话模型名称，此处为 deepseek-v4-flash（旧名 deepseek-chat 已停用，切勿回退）。 */
        private String model;

        /** 单次生成的最大 token 数，用于控制输出长度、成本与延迟。 */
        private int maxTokens;
    }
}
