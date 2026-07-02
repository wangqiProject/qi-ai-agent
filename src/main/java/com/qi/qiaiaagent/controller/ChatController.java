package com.qi.qiaiaagent.controller;

import com.qi.qiaiaagent.chatmemory.FileBasedChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final String SYSTEM_PROMPT = """
            你是 DeepSeek AI 助手，请遵循以下规则回复：

            1. 使用 Markdown 格式组织回复内容
            2. 需要时使用标题（## 或 ###）、列表、表格、代码块等
            3. 长内容使用分段和小标题，让结构清晰
            4. 代码块请标注语言类型
            5. 回复语气热情、细腻、专业
            """;

    private final ChatModel chatModel;
    private final FileBasedChatMemory chatMemory;

    public ChatController(ChatModel chatModel, FileBasedChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
    }

    /**
     * 同步聊天 —— 返回完整回复，携带会话记忆
     */
    @PostMapping("/sync")
    public Map<String, Object> chatSync(@RequestBody ChatRequest request) {
        String sessionId = resolveSessionId(request.sessionId());

        // 加载历史 + 构建 Prompt
        List<Message> history = chatMemory.get(sessionId);
        Prompt prompt = buildPromptWithHistory(history, request.message());

        // 调用 AI
        String reply = chatModel.call(prompt).getResult().getOutput().getText();

        // 保存对话
        chatMemory.add(sessionId, List.of(
                new UserMessage(request.message()),
                new AssistantMessage(reply)
        ));

        log.info("同步聊天完成，sessionId={}", sessionId);
        return Map.of("reply", reply, "sessionId", sessionId);
    }

    /**
     * 流式聊天 —— 纯文本流式输出（避免 SSE 行拆分导致 Markdown 结构破坏）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        String sessionId = resolveSessionId(request.sessionId());

        // 加载历史 + 构建 Prompt
        List<Message> history = chatMemory.get(sessionId);
        Prompt prompt = buildPromptWithHistory(history, request.message());

        // 先保存用户消息
        chatMemory.add(sessionId, List.of(new UserMessage(request.message())));

        StringBuilder fullReply = new StringBuilder();

        return chatModel.stream(prompt)
                .map(ChatResponse::getResult)
                .map(result -> result.getOutput().getText())
                .doOnNext(fullReply::append)
                .doFinally(signalType -> {
                    if (fullReply.length() > 0) {
                        chatMemory.add(sessionId, List.of(new AssistantMessage(fullReply.toString())));
                        log.info("流式聊天完成，sessionId={}，回复长度={}", sessionId, fullReply.length());
                    }
                });
    }

    /**
     * 新建会话 —— 返回新 sessionId
     */
    @PostMapping("/session/new")
    public Map<String, String> newSession() {
        String sessionId = UUID.randomUUID().toString();
        return Map.of("sessionId", sessionId);
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/history")
    public List<Map<String, String>> getHistory(
            @RequestParam(defaultValue = "") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        List<Message> messages = chatMemory.get(sessionId);
        List<Map<String, String>> result = new ArrayList<>();
        for (Message msg : messages) {
            String role;
            if (msg instanceof UserMessage) {
                role = "user";
            } else if (msg instanceof AssistantMessage) {
                role = "assistant";
            } else {
                continue;
            }
            result.add(Map.of("role", role, "content", msg.getText()));
        }
        return result;
    }

    /**
     * 构建带有系统提示和历史消息的 Prompt
     */
    private Prompt buildPromptWithHistory(List<Message> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(new UserMessage(userMessage));
        return new Prompt(messages);
    }

    /**
     * 解析 sessionId：前端未传时自动生成
     */
    private String resolveSessionId(String sessionId) {
        return (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();
    }
}