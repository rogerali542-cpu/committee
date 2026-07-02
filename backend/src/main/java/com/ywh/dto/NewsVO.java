package com.ywh.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成的党建新闻通稿。title=标题，content=正文（多段，用 \n 分隔），source=llm/fallback。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsVO {
    private String title;
    private String content;
    private String source; // llm | fallback
}
