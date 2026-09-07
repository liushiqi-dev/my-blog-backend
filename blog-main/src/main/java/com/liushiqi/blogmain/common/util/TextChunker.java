package com.liushiqi.blogmain.common.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 知识库文本处理工具：定长重叠分块、SHA-256 指纹、FLOAT32 小端向量字节编解码。
 * <p>
 * 无状态纯静态方法，不依赖 Spring 容器，可直接在 Service 层调用与单测覆盖。
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * 按字符数定长滑窗分块，步长 = size - overlap，相邻块保留 overlap 个字符重叠以延续上下文语义。
     * <p>
     * 边界处理：text 为 null/空返回空列表；size &lt;= 0 无法成块返回空列表；overlap 为负按 0 处理，
     * overlap &gt;= size 时收敛为 size-1 以保证步长 &gt;= 1（避免死循环）；末尾不足 size 的块保留；纯空白块跳过。
     *
     * @param text    原始文本
     * @param size    单块字符数
     * @param overlap 相邻块重叠字符数
     * @return 分块结果
     */
    public static List<String> chunk(String text, int size, int overlap) {
        if (text == null || text.isEmpty() || size <= 0) {
            return List.of();
        }
        int safeOverlap = overlap < 0 ? 0 : overlap;
        if (safeOverlap >= size) {
            safeOverlap = size - 1;
        }
        int step = size - safeOverlap;
        int len = text.length();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < len; start += step) {
            int end = Math.min(start + size, len);
            String piece = text.substring(start, end);
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
            if (end == len) {
                break;
            }
        }
        return chunks;
    }

    /**
     * 计算文本的 SHA-256 摘要，返回十六进制小写字符串（UTF-8 字节）。
     * <p>用作分块内容指纹，判断内容是否变化以决定是否需要重新向量化。
     *
     * @param text 原始文本，null 按空串处理
     * @return 64 位十六进制小写摘要
     */
    public static String sha256(String text) {
        String input = text == null ? "" : text;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，正常不会触发
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 将 float 向量按 FLOAT32 小端序编码为字节数组，供向量库以二进制存储。
     *
     * @param vec 浮点向量
     * @return 小端字节数组，长度为 vec.length * 4
     */
    public static byte[] toBytes(float[] vec) {
        if (vec == null) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vec) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    /**
     * {@link #toBytes(float[])} 的逆变换：将小端字节数组还原为 float 向量。
     *
     * @param bytes 小端字节数组，长度须为 4 的倍数
     * @return 浮点向量
     */
    public static float[] toFloatArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vec = new float[bytes.length / 4];
        for (int i = 0; i < vec.length; i++) {
            vec[i] = buffer.getFloat();
        }
        return vec;
    }
}
