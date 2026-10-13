package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Goes through the recipe's own assistant(...): its system prompt, its window and its memory. The
 * earlier test built an assistant of its own with no system prompt and an in-memory window, so it
 * could not see what the recipe actually sent once the window filled up.
 */
class MemoryTest {

    static class RecordingModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from("noted " + requests.size())).build();
        }

        List<ChatMessage> last() {
            return requests.get(requests.size() - 1).messages();
        }
    }

    private static List<ChatMessageType> types(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessage::type).toList();
    }

    @Test
    void conversationsDoNotLeakIntoEachOther() {
        RecordingModel model = new RecordingModel();
        Memory.Assistant assistant = Memory.assistant(model, new InMemoryChatMemoryStore());

        assistant.chat("alice", "My name is Alice.");
        assistant.chat("bob", "My name is Bob.");
        assistant.chat("alice", "What is my name?");

        String third = model.last().toString();
        assertThat(third).contains("My name is Alice.");
        assertThat(third).doesNotContain("My name is Bob.");
    }

    @Test
    void everyRequestStartsWithAQuestionEvenOnceTheWindowIsFull() {
        RecordingModel model = new RecordingModel();
        Memory.Assistant assistant = Memory.assistant(model, new InMemoryChatMemoryStore());

        for (int turn = 1; turn <= 3 * Memory.WINDOW; turn++) {
            assistant.chat("alice", "question " + turn);
        }

        // A window that evicts one message at a time keeps the answer to a question it has just
        // dropped. Without the alignment every request from the sixth turn on opened with that
        // orphaned answer - which Anthropic has been reported to reject with a 400.
        for (ChatRequest request : model.requests) {
            List<ChatMessageType> sent = types(request.messages());
            assertThat(sent.get(0)).isEqualTo(ChatMessageType.SYSTEM);
            assertThat(sent.get(1)).as("first message after the system prompt").isEqualTo(ChatMessageType.USER);
        }
    }

    @Test
    void theSystemPromptRidesOnTopOfTheWindow() {
        RecordingModel model = new RecordingModel();
        Memory.Assistant assistant = Memory.assistant(model, new InMemoryChatMemoryStore());

        for (int turn = 1; turn <= 3 * Memory.WINDOW; turn++) {
            assistant.chat("alice", "question " + turn);
        }

        List<ChatMessage> sent = model.last();
        assertThat(sent.get(0)).isEqualTo(SystemMessage.from("You are a terse assistant."));
        // The prompt is not one of the WINDOW messages - it is sent in addition to them - and the
        // orphaned answer is what keeps the rest one short of the window.
        assertThat(sent).hasSizeLessThanOrEqualTo(Memory.WINDOW + 1);
        assertThat(sent.get(sent.size() - 1)).isEqualTo(UserMessage.from("question " + 3 * Memory.WINDOW));
    }

    @Test
    void theStoreStillHoldsWhatTheAlignmentLeavesOut() {
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();
        Memory.Assistant assistant = Memory.assistant(new RecordingModel(), store);

        for (int turn = 1; turn <= 3 * Memory.WINDOW; turn++) {
            assistant.chat("alice", "question " + turn);
        }

        // Only the request is trimmed. The window in the store is left as the window put it, so
        // nothing is lost that the next turn might bring back into range.
        List<ChatMessage> stored = store.getMessages("alice");
        List<ChatMessage> windowView = MessageWindowChatMemory.builder()
                .id("alice").maxMessages(Memory.WINDOW).chatMemoryStore(store).build().messages();
        assertThat(windowView).isEqualTo(stored);
        assertThat(types(stored)).contains(ChatMessageType.AI);
    }
}
