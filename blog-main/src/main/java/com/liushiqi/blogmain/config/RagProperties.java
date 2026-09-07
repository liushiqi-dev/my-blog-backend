package com.liushiqi.blogmain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG（检索增强生成）知识库问答的集中配置。
 * <p>
 * 本类通过 {@code @ConfigurationProperties(prefix = "rag")} 把 application-*.yaml 里
 * {@code rag.*} 一段配置一次性映射成强类型对象，避免在业务代码里到处写
 * {@code @Value("${rag.xxx}")}。所有和「模型 / 分块 / 超时」相关的可调参数都收敛到这里，
 * 做到「单一配置源」——比如向量维度 embeddingDimension，建库、写入、检索、FT.CREATE 的 DIM
 * 必须完全一致，只在这里配一次就不会出现三处写死对不上的问题。
 * <p>
 * 🔗 单端点单 key 设计：本项目【不使用】阿里云、【不做】双供应商，而是统一走一个「中转站」
 * （聚合各家大模型的 OpenAI 兼容网关）——同一个 {@code baseUrl} + 同一个 {@code apiKey}
 * 同时提供 embedding（文本向量化）与 chat（答案生成）两种能力。因此这里不再有 embedding / chat
 * 两个嵌套子对象，而是把 baseUrl、apiKey 提到顶层共用；两种能力仅在「模型名」与「读超时」上不同。
 * <p>
 * 注册方式：本类只标注 {@code @ConfigurationProperties}，不加 {@code @Component}，
 * 改由 {@link RagRestClientConfig} 上的 {@code @EnableConfigurationProperties(RagProperties.class)}
 * 完成注册。这样做侵入最小——不用去改启动类 BlogMainApplication，配置和装配集中在 config 包内。
 * <p>
 * 💡 八股文关联：
 * - {@code @ConfigurationProperties} 支持「松散绑定」：yaml 里的 base-url / embedding-batch-size
 *   会自动绑定到 Java 的 baseUrl / embeddingBatchSize 字段（kebab-case ↔ camelCase）。
 * - 与 {@code @Value} 相比，它能一次性绑定整棵对象树、支持嵌套、支持校验，适合成组的配置。
 * <p>
 * ⚠️ 故障隔离设计：apiKey 在 yaml 里用 {@code ${LLM_API_KEY:}} 带「空默认值」的占位符，含义是
 * 「环境变量没配也不报错，就当成空串」；baseUrl 同样带一个非空默认值兜底。这样即使运维忘了配
 * 中转站的 key，博客主站（发文、点赞、评论等）照样能启动，只有真正调用大模型的 /kb 问答链路
 * 会在运行时报错。切忌在这里做「key 为空就抛异常」的强校验，那会导致整个应用起不来。
 */
@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /**
     * 中转站的 OpenAI 兼容端点 base-url，embedding 与 chat 共用同一个地址。
     * 请求时在其后分别拼 {@code /embeddings} 与 {@code /chat/completions}。
     */
    private String baseUrl;

    /**
     * 中转站的统一鉴权 key，embedding 与 chat 共用同一个 key，
     * 最终以 {@code Authorization: Bearer <apiKey>} 头发出；未配置时可能为空串（故障隔离，见类注释）。
     */
    private String apiKey;

    /** embedding（文本向量化）模型名称，此处为 text-embedding-3-small。 */
    private String embeddingModel;

    /**
     * 向量维度，必须与建库、写入、检索、FT.CREATE 的 DIM 完全一致，否则检索会失败或召回失真。
     * 这是「单一配置源」：text-embedding-3-small 对应 1536 维，换模型时改这一处即可。
     */
    private int embeddingDimension;

    /** 单次 embedding 请求携带的文本条数，取保守值 10，避免超出网关限制。 */
    private int embeddingBatchSize;

    /** embedding 调用在网关偶发连接重置/空响应时的重试次数（不含首次尝试）。 */
    private int embeddingMaxRetries = 2;

    /** chat（答案生成）模型名称，此处为 deepseek-v4-flash（旧名 deepseek-chat 已停用，切勿回退）。 */
    private String chatModel;

    /** 单次生成的最大 token 数，用于控制输出长度、成本与延迟。 */
    private int chatMaxTokens;

    /** chat 调用在网关偶发连接重置或空响应时的重试次数。 */
    private int chatMaxRetries = 2;

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
}
