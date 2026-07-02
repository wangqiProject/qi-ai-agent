package com.qi.qiaiaagent.controller;

import com.qi.qiaiaagent.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 同步聊天
     */
    @PostMapping("/sync")
    public Map<String, Object> chatSync(@RequestBody ChatRequest request) {
        return chatService.chatSync(request.message(), request.sessionId());
    }

    /**
     * 流式聊天
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request.message(), request.sessionId());
    }

    /**
     * 新建会话
     */
    @PostMapping("/session/new")
    public Map<String, String> newSession() {
        return Map.of("sessionId", chatService.newSession());
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/history")
    public List<Map<String, String>> getHistory(
            @RequestParam(defaultValue = "") String sessionId) {
        return chatService.getHistory(sessionId);
    }
}