package com.qi.qiaiaagent.config;

import com.qi.qiaiaagent.chatmemory.FileBasedChatMemory;
import com.qi.qiaiaagent.constant.FileConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public FileBasedChatMemory chatMemory() {
        return new FileBasedChatMemory(FileConstant.MEMORY_SAVE_DIR);
    }
}