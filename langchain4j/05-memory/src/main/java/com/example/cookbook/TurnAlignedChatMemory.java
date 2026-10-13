package com.example.cookbook;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * A chat memory whose window never starts in the middle of a turn.
 *
 * MessageWindowChatMemory evicts one message at a time. A conversation alternates question and
 * answer, so once the window is full and the next question arrives, the oldest message to survive
 * is an answer whose question has just been evicted: the request goes out as system prompt, then an
 * assistant reply to nothing, then the rest. With an even window that is every request from then on.
 *
 * The model reads an answer to a question it cannot see, and Anthropic's API has been reported to
 * refuse it outright - "messages: first message must use the 'user' role" - which with this recipe's
 * window of ten would start failing on the sixth turn of every conversation.
 *
 * Storage and eviction stay with the wrapped memory; this only drops the orphaned leading messages
 * from what is sent. Nothing is deleted from the store.
 */
final class TurnAlignedChatMemory implements ChatMemory {

    private final ChatMemory delegate;

    TurnAlignedChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        delegate.add(message);
    }

    @Override
    public void set(Iterable<ChatMessage> messages) {
        delegate.set(messages);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> all = delegate.messages();
        List<ChatMessage> aligned = new ArrayList<>(all.size());

        boolean seenUser = false;
        for (ChatMessage message : all) {
            if (message instanceof SystemMessage) {
                aligned.add(message);
            }
            else if (seenUser || message instanceof UserMessage) {
                seenUser = true;
                aligned.add(message);
            }
            // anything before the first user message, other than the system prompt, is the tail of a
            // turn whose question has already been evicted
        }
        return aligned;
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
