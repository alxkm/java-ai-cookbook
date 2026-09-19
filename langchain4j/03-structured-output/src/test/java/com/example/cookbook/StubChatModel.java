package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/** Records what was sent and replies with a canned answer. No network, no key. */
class StubChatModel implements ChatModel {

    final List<ChatRequest> requests = new ArrayList<>();
    private final String reply;

    StubChatModel(String reply) {
        this.reply = reply;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        requests.add(request);
        return ChatResponse.builder().aiMessage(AiMessage.from(reply)).build();
    }
}
