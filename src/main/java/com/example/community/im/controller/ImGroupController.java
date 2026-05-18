package com.example.community.im.controller;

import com.example.community.im.service.ConversationService;
import com.example.community.utils.Result;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/im/group")
public class ImGroupController {

    @Resource
    private ConversationService conversationService;

    /**
     * 群聊入场门禁：用户提交密钥，校验后加入群聊。
     * 不匹配时只返回"加入失败"，不暴露原因。
     */
    @PostMapping("/{convId}/join")
    public ResponseEntity<Result<Void>> joinGroup(
            @PathVariable Long convId,
            @RequestBody Map<String, String> body) {
        boolean success = conversationService.joinGroup(convId, body.get("key"));
        if (success) {
            return ResponseEntity.ok(Result.success(null));
        }
        return ResponseEntity.status(403).body(Result.error("加入失败"));
    }
}
