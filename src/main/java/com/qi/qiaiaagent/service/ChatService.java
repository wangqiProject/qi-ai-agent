package com.qi.qiaiaagent.service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface ChatService {

    /**
     * 同步聊天 —— 返回完整回复，携带会话记忆
     */
    Map<String, Object> chatSync(String message, String sessionId);

    /**
     * 流式聊天 —— 纯文本流式输出
     */
    Flux<String> chatStream(String message, String sessionId);

    /**
     * 新建会话 —— 返回新 sessionId
     */
    String newSession();

    /**
     * 获取会话历史
     */
    List<Map<String, String>> getHistory(String sessionId);
}