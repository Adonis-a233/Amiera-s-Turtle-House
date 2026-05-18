package com.example.community.search.controller;

import com.example.community.search.dto.SearchRequestDTO;
import com.example.community.search.dto.SearchResultDTO;
import com.example.community.search.service.PostSearchService;
import com.example.community.utils.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

/**
 * 搜索接口。
 *
 *   GET  /api/search?q=...      纯文本语义搜索
 *   POST /api/search/image      以图搜图（multipart/form-data）
 *   POST /api/search/hybrid     图文混合搜索
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final PostSearchService searchService;

    @GetMapping
    public Result<SearchResultDTO> searchByText(@RequestParam("q") String query) {
        SearchRequestDTO req = new SearchRequestDTO();
        req.setTextQuery(query);
        return Result.success(searchService.search(req));
    }

    @PostMapping("/image")
    public Result<SearchResultDTO> searchByImage(
            @RequestParam("image") MultipartFile imageFile) throws Exception {
        SearchRequestDTO req = new SearchRequestDTO();
        req.setImageBase64(Base64.getEncoder().encodeToString(imageFile.getBytes()));
        req.setImageMimeType(imageFile.getContentType());
        return Result.success(searchService.search(req));
    }

    @PostMapping("/hybrid")
    public Result<SearchResultDTO> searchHybrid(
            @RequestParam("q") String query,
            @RequestParam("image") MultipartFile imageFile) throws Exception {
        SearchRequestDTO req = new SearchRequestDTO();
        req.setTextQuery(query);
        req.setImageBase64(Base64.getEncoder().encodeToString(imageFile.getBytes()));
        req.setImageMimeType(imageFile.getContentType());
        return Result.success(searchService.search(req));
    }
}
