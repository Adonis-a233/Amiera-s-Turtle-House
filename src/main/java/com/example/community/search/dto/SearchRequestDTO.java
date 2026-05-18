package com.example.community.search.dto;

import lombok.Data;

/**
 * 搜索入参。三种合法组合：
 *   1. textQuery 非空，imageBase64 为 null  →  纯文本语义搜索
 *   2. textQuery 为 null，imageBase64 非空  →  以图搜图
 *   3. 两者都有                              →  图文联合搜索（RRF）
 */
@Data
public class SearchRequestDTO {

    private String textQuery;

    /** Base64 编码的查询图片（不含 data:image/... 前缀）。 */
    private String imageBase64;

    /** 图片 MIME 类型，如 "image/jpeg"。 */
    private String imageMimeType;
}
