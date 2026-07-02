package com.qi.qiaiaagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileBasedChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(FileBasedChatMemory.class);

    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
    }

    private final String baseDir;

    public FileBasedChatMemory(String baseDir) {
        this.baseDir = baseDir;
        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> existing = get(conversationId);
        existing.addAll(messages);
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, existing);
        } catch (Exception e) {
            log.error("写入对话记忆失败，conversationId={}", conversationId, e);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            return kryo.readObject(input, ArrayList.class);
        } catch (Exception e) {
            log.error("读取对话记忆失败，conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(baseDir, conversationId + ".kryo");
    }
}