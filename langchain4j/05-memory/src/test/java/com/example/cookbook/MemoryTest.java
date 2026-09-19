package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryTest {

    interface Assistant {
        String chat(@MemoryId String conversationId, @UserMessage String message);
    }

    static class RecordingModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    @Test
    void conversationsDoNotLeakIntoEachOther() {
        RecordingModel model = new RecordingModel();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder().id(id).maxMessages(10).build())
                .build();

        assistant.chat("alice", "My name is Alice.");
        assistant.chat("bob", "My name is Bob.");
        assistant.chat("alice", "What is my name?");

        String third = model.requests.get(2).messages().toString();
        assertThat(third).contains("My name is Alice.");
        assertThat(third).doesNotContain("My name is Bob.");
    }

    @Test
    void windowDropsOldestMessages() {
        RecordingModel model = new RecordingModel();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder().id(id).maxMessages(4).build())
                .build();

        for (int i = 1; i <= 5; i++) {
            assistant.chat("alice", "message " + i);
        }

        // The window is trimmed to maxMessages before the new user message is appended,
        // so the last request carries four remembered messages plus the current one.
        List<?> lastSent = model.requests.get(model.requests.size() - 1).messages();
        assertThat(lastSent).hasSizeLessThanOrEqualTo(5);
        assertThat(lastSent.toString()).doesNotContain("message 1", "message 2");
    }
}
