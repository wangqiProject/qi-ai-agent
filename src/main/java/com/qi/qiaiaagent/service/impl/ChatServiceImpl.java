package com.qi.qiaiaagent.service.impl;

import com.qi.qiaiaagent.chatmemory.FileBasedChatMemory;
import com.qi.qiaiaagent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

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

    public ChatServiceImpl(ChatModel chatModel, FileBasedChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
    }

    @Override
    public Map<String, Object> chatSync(String message, String sessionId) {
        String resolvedSessionId = resolveSessionId(sessionId);

        List<Message> history = chatMemory.get(resolvedSessionId);
        Prompt prompt = buildPromptWithHistory(history, message);

        String reply = chatModel.call(prompt).getResult().getOutput().getText();

        chatMemory.add(resolvedSessionId, List.of(
                new UserMessage(message),
                new AssistantMessage(reply)
        ));

        log.info("同步聊天完成，sessionId={}", resolvedSessionId);
        return Map.of("reply", reply, "sessionId", resolvedSessionId);
    }

    @Override
    public Flux<String> chatStream(String message, String sessionId) {
        String resolvedSessionId = resolveSessionId(sessionId);

        List<Message> history = chatMemory.get(resolvedSessionId);
        Prompt prompt = buildPromptWithHistory(history, message);

        chatMemory.add(resolvedSessionId, List.of(new UserMessage(message)));

        StringBuilder fullReply = new StringBuilder();

        return chatModel.stream(prompt)
                .map(ChatResponse::getResult)
                .map(result -> result.getOutput().getText())
                .doOnNext(fullReply::append)
                .doFinally(signalType -> {
                    if (fullReply.length() > 0) {
                        chatMemory.add(resolvedSessionId, List.of(new AssistantMessage(fullReply.toString())));
                        log.info("流式聊天完成，sessionId={}，回复长度={}", resolvedSessionId, fullReply.length());
                    }
                });
    }

    @Override
    public String newSession() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<Map<String, String>> getHistory(String sessionId) {
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

    private Prompt buildPromptWithHistory(List<Message> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(new UserMessage(userMessage));
        return new Prompt(messages);
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();
    }
}