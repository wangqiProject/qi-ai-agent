package com.qi.qiaiaagent.controller;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(String message, String sessionId) {

    public ChatRequest(String message) {
        this(message, null);
    }
}