package com.liushiqi.blogmain.common.util;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TextChunker} 纯函数单测，不依赖 Spring 上下文与网络。
 */
class TextChunkerTest {

    // ---- chunk：分块与重叠 ----

    @Test
    void chunkNull() {
        assertTrue(TextChunker.chunk(null, 10, 2).isEmpty());
    }

    @Test
    void chunkEmpty() {
        assertTrue(TextChunker.chunk("", 10, 2).isEmpty());
    }

    @Test
    void chunkShorterThanSizeKeepsWhole() {
        // 文本短于 size，整段作为唯一一块保留
        List<String> chunks = TextChunker.chunk("ab", 5, 0);
        assertEquals(List.of("ab"), chunks);
    }

    @Test
    void chunkExactSingleBlock() {
        // 恰好一整块，无剩余
        List<String> chunks = TextChunker.chunk("abcd", 4, 0);
        assertEquals(List.of("abcd"), chunks);
    }

    @Test
    void chunkNoOverlapCoversAll() {
        // 无重叠定长切分，末尾不足一块也保留
        List<String> chunks = TextChunker.chunk("abcdefghij", 4, 0);
        assertEquals(List.of("abcd", "efgh", "ij"), chunks);
    }

    @Test
    void chunkWithOverlapKeepsLastPartial() {
        // size=5 overlap=2 步长=3：末尾不足一块的 "6789" 也保留
        List<String> chunks = TextChunker.chunk("0123456789", 5, 2);
        assertEquals(List.of("01234", "34567", "6789"), chunks);
    }

    @Test
    void chunkOverlapIsCorrect() {
        // 相邻满块之间：前块末尾 overlap 个字符 == 后块开头 overlap 个字符
        List<String> chunks = TextChunker.chunk("0123456789", 5, 2);
        for (int i = 0; i + 1 < chunks.size(); i++) {
            String cur = chunks.get(i);
            String next = chunks.get(i + 1);
            if (cur.length() == 5 && next.length() == 5) {
                assertEquals(cur.substring(cur.length() - 2), next.substring(0, 2));
            }
        }
    }

    @Test
    void chunkSkipsBlankPieces() {
        // 纯空白块被跳过，非空白块保留
        List<String> chunks = TextChunker.chunk("abcd    efgh", 4, 0);
        assertEquals(List.of("abcd", "efgh"), chunks);
    }

    @Test
    void chunkIllegalOverlapDoesNotLoopForever() {
        // overlap >= size 收敛为 size-1，步长 >=1，不进入死循环
        List<String> chunks = TextChunker.chunk("abcd", 2, 5);
        assertFalse(chunks.isEmpty());
        assertEquals("ab", chunks.get(0));
    }

    @Test
    void chunkIllegalSizeReturnsEmpty() {
        assertTrue(TextChunker.chunk("abcd", 0, 0).isEmpty());
        assertTrue(TextChunker.chunk("abcd", -1, 0).isEmpty());
    }

    // ---- sha256：已知摘要值 ----

    @Test
    void sha256KnownVector() {
        // NIST 标准测试向量
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TextChunker.sha256("abc"));
    }

    @Test
    void sha256EmptyString() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                TextChunker.sha256(""));
    }

    @Test
    void sha256IsLowercaseHex64() {
        String hash = TextChunker.sha256("你好，世界");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    // ---- toBytes / toFloatArray：往返与字节序 ----

    @Test
    void roundTripPreservesValues() {
        float[] vec = {1.0f, -2.5f, 0.0f, 3.14159f, -0.0f, Float.MIN_VALUE, Float.MAX_VALUE};
        float[] back = TextChunker.toFloatArray(TextChunker.toBytes(vec));
        assertArrayEquals(vec, back);
    }

    @Test
    void toBytesLengthIsFourTimesVector() {
        assertEquals(12, TextChunker.toBytes(new float[]{1f, 2f, 3f}).length);
    }

    @Test
    void toBytesIsLittleEndian() {
        // 1.0f 的 IEEE-754 为 0x3F800000，小端存储前 4 字节应为 00 00 80 3F
        byte[] bytes = TextChunker.toBytes(new float[]{1.0f});
        assertEquals((byte) 0x00, bytes[0]);
        assertEquals((byte) 0x00, bytes[1]);
        assertEquals((byte) 0x80, bytes[2]);
        assertEquals((byte) 0x3F, bytes[3]);
    }

    @Test
    void toBytesMatchesLittleEndianByteBuffer() {
        float[] vec = {7.25f, -1.5f};
        ByteBuffer expected = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        expected.putFloat(7.25f).putFloat(-1.5f);
        assertArrayEquals(expected.array(), TextChunker.toBytes(vec));
    }

    @Test
    void emptyAndNullVectors() {
        assertEquals(0, TextChunker.toBytes(null).length);
        assertEquals(0, TextChunker.toFloatArray(null).length);
        assertEquals(0, TextChunker.toFloatArray(new byte[0]).length);
    }
}
