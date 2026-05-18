package com.example.community.im.controller;

import com.example.community.im.filter.SensitiveWordFilter;
import com.example.community.im.mapper.SensitiveWordMapper;
import com.example.community.utils.PageResult;
import com.example.community.utils.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sensitive")
public class SensitiveWordController {

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @PostMapping("/add")
    public Result<Void> add(@RequestBody Map<String, String> body) {
        sensitiveWordMapper.insertWord(body.get("word"));
        sensitiveWordFilter.reload();
        return Result.success(null);
    }

    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestBody Map<String, String> body) {
        sensitiveWordMapper.deleteWord(body.get("word"));
        sensitiveWordFilter.reload();
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<PageResult<String>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<String> words = sensitiveWordMapper.selectWordsPaged(offset, size);
        long total = sensitiveWordMapper.countWords();
        return Result.success(new PageResult<>(total, words));
    }
}
