package com.qi.qiaiaagent.controller;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatModel chatModel;

    public ChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 同步聊天 —— 一次性返回完整回复
     */
    @PostMapping("/sync")
    public Map<String, String> chatSync(@RequestBody ChatRequest request) {
        String reply = chatModel.call(request.message());
        return Map.of("reply", reply);
    }

    /**
     * 流式聊天 —— SSE 流式输出
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        Prompt prompt = new Prompt(new UserMessage(request.message()));
        return chatModel.stream(prompt)
                .map(ChatResponse::getResult)
                .map(result -> result.getOutput().getText())
                .map(text -> ServerSentEvent.<String>builder()
                        .data(text)
                        .build());
    }
}