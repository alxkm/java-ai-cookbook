package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryTest {

    static class RecordingModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }
    }

    @Test
    void repliesFromEarlierTurnsAreResentAndConversationsStaySeparate() {
        RecordingModel model = new RecordingModel();
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(10).build();

        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();

        ask(client, "alice", "My name is Alice.");
        ask(client, "bob", "My name is Bob.");
        ask(client, "alice", "What is my name?");

        // Third call is Alice's second turn: her first question and its answer are replayed.
        String third = model.prompts.get(2).getInstructions().toString();
        assertThat(third).contains("My name is Alice.");
        assertThat(third).doesNotContain("My name is Bob.");
    }

    @Test
    void windowDropsOldestMessages() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(4).build();
        ChatClient client = ChatClient.builder(new RecordingModel())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();

        for (int i = 1; i <= 5; i++) {
            ask(client, "alice", "message " + i);
        }

        assertThat(memory.get("alice")).hasSizeLessThanOrEqualTo(4);
        assertThat(memory.get("alice").toString()).doesNotContain("message 1");
    }

    private void ask(ChatClient client, String conversationId, String message) {
        client.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
