package com.example.community.controller;

import com.example.community.service.ContactService;
import com.example.community.utils.PageResult;
import com.example.community.utils.Result;
import com.example.community.vo.ContactVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @Resource
    private ContactService contactService;

    @PostMapping("/follow/{followeeId}")
    public Result<Void> follow(@PathVariable Long followeeId) {
        contactService.follow(followeeId);
        return Result.success(null);
    }

    @DeleteMapping("/follow/{followeeId}")
    public Result<Void> unfollow(@PathVariable Long followeeId) {
        contactService.unfollow(followeeId);
        return Result.success(null);
    }

    @PostMapping("/block/{blockedId}")
    public Result<Void> block(@PathVariable Long blockedId) {
        contactService.block(blockedId);
        return Result.success(null);
    }

    @DeleteMapping("/block/{blockedId}")
    public Result<Void> unblock(@PathVariable Long blockedId) {
        contactService.unblock(blockedId);
        return Result.success(null);
    }

    @GetMapping("/friends")
    public Result<PageResult<ContactVO>> getFriends(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(contactService.getFriends(page, size));
    }
}
