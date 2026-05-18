package com.example.community.im.controller;

import com.example.community.im.service.ConversationService;
import com.example.community.im.vo.ConversationVO;
import com.example.community.im.vo.MessageVO;
import com.example.community.utils.PageResult;
import com.example.community.utils.Result;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/im/conversation")
public class ConversationController {

    @Resource
    private ConversationService conversationService;

    @GetMapping("/list")
    public Result<PageResult<ConversationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(conversationService.getMyConversations(page, size));
    }

    @PostMapping("/group")
    public Result<Long> createGroup(@RequestBody Map<String, Object> body) {
        String groupName   = (String) body.get("groupName");
        String groupAvatar = (String) body.getOrDefault("groupAvatar", "");
        @SuppressWarnings("unchecked")
        List<Long> memberIds = ((List<Number>) body.get("memberIds"))
                .stream().map(Number::longValue).toList();
        return Result.success(conversationService.createGroup(groupName, groupAvatar, memberIds));
    }

    @PostMapping("/{conversationId}/read")
    public Result<Void> markRead(@PathVariable Long conversationId) {
        conversationService.markRead(conversationId);
        return Result.success(null);
    }

    @GetMapping("/{conversationId}/history")
    public Result<List<MessageVO>> history(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(conversationService.getHistory(conversationId, beforeSeq, limit));
    }

    /** 校验成员身份后下发会话密钥，非成员返回 403 */
    @GetMapping("/{convId}/key")
    public ResponseEntity<Result<String>> getKey(@PathVariable Long convId) {
        String key = conversationService.getSecretKey(convId);
        if (key == null) {
            return ResponseEntity.status(403).body(Result.error("无权限"));
        }
        return ResponseEntity.ok(Result.success(key));
    }

    /** 当前用户所有会话的总未读数，用于首页角标 */
    @GetMapping("/unread/total")
    public Result<Long> getTotalUnread() {
        return Result.success(conversationService.getTotalUnread());
    }
}
