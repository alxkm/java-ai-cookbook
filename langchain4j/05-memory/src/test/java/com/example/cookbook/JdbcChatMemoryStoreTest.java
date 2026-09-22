package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The half of this recipe the README used to promise and the code did not have: history that
 * outlives the process. No model is involved - what is being checked is where the messages go.
 */
class JdbcChatMemoryStoreTest {

    private String url;
    private JdbcChatMemoryStore store;

    @BeforeEach
    void openAFreshDatabase() {
        // DB_CLOSE_DELAY keeps the in-memory database alive between connections, which is what the
        // reopen test needs; a fresh name per test keeps them from seeing each other's rows.
        url = "jdbc:h2:mem:memory-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        store = new JdbcChatMemoryStore(url);
    }

    @AfterEach
    void closeIt() {
        store.close();
    }

    @Test
    void historySurvivesReopeningTheDatabase() {
        String conversationId = "alice";

        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(10)
                .chatMemoryStore(store)
                .build();
        memory.add(UserMessage.from("My name is Alice and I work on payments."));
        memory.add(AiMessage.from("Noted."));

        // A different store object over the same database, which is as close to a restart as a test
        // gets. In-memory storage passes every other assertion here and fails this one.
        try (JdbcChatMemoryStore reopened = new JdbcChatMemoryStore(url)) {
            MessageWindowChatMemory after = MessageWindowChatMemory.builder()
                    .id(conversationId)
                    .maxMessages(10)
                    .chatMemoryStore(reopened)
                    .build();

            assertThat(after.messages())
                    .extracting(message -> message.getClass().getSimpleName())
                    .containsExactly("UserMessage", "AiMessage");
            assertThat(after.messages().get(0)).isEqualTo(UserMessage.from("My name is Alice and I work on payments."));
        }
    }

    @Test
    void anUnknownConversationIsEmptyRatherThanAnError() {
        // The provider is called the first time an id is seen, so this is the normal path for every
        // new conversation, not an edge case.
        assertThat(store.getMessages("never-seen-before")).isEmpty();
    }

    @Test
    void conversationsDoNotLeakIntoEachOther() {
        store.updateMessages("alice", List.of(UserMessage.from("I work on payments.")));
        store.updateMessages("bob", List.of(UserMessage.from("I work on search.")));

        assertThat(store.getMessages("alice")).containsExactly(UserMessage.from("I work on payments."));
        assertThat(store.getMessages("bob")).containsExactly(UserMessage.from("I work on search."));
        assertThat(store.conversationIds()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void updatingAConversationReplacesItRatherThanAppending() {
        store.updateMessages("alice", List.of(UserMessage.from("first")));
        store.updateMessages("alice", List.of(UserMessage.from("first"), AiMessage.from("second")));

        // updateMessages hands over the whole list every time, so a store that inserted instead of
        // merging would quietly double every conversation.
        assertThat(store.getMessages("alice")).hasSize(2);
        assertThat(store.conversationIds()).containsExactly("alice");
    }

    @Test
    void deletingOneConversationLeavesTheOthers() {
        store.updateMessages("alice", List.of(UserMessage.from("payments")));
        store.updateMessages("bob", List.of(UserMessage.from("search")));

        store.deleteMessages("alice");

        assertThat(store.getMessages("alice")).isEmpty();
        assertThat(store.getMessages("bob")).hasSize(1);
        assertThat(store.conversationIds()).containsExactly("bob");
    }

    @Test
    void theWindowBoundsWhatTheModelSees() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .id("alice")
                .maxMessages(4)
                .chatMemoryStore(store)
                .build();

        for (int turn = 1; turn <= 6; turn++) {
            memory.add(UserMessage.from("turn " + turn));
        }

        assertThat(memory.messages())
                .extracting(message -> ((UserMessage) message).singleText())
                .containsExactly("turn 3", "turn 4", "turn 5", "turn 6");
    }

    @Test
    void messageTypesSurviveTheRoundTrip() {
        store.updateMessages("alice", List.of(UserMessage.from("question"), AiMessage.from("answer")));

        // Serialising to JSON and back is where a hand-written store usually loses something: the
        // role, the tool calls, or the distinction between a user and an assistant turn.
        assertThat(store.getMessages("alice"))
                .containsExactly(UserMessage.from("question"), AiMessage.from("answer"));
    }
}
