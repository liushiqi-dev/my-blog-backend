package com.liushiqi.blogmain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库问答结果VO：LLM 生成的回答与其引用的文章来源
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AskVo {
    /**
     * 回答正文；召回为空或资料不足时为降级提示语
     */
    private String answer;
    /**
     * 引用来源，按向量命中顺序对文章去重
     */
    private List<Source> sources;

    /**
     * 单条引用来源
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Source {
        /**
         * 文章ID
         */
        private Long postId;
        /**
         * 文章标题
         */
        private String title;
    }
}
