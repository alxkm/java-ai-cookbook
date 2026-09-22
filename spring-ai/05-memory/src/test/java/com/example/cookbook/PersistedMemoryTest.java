package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The half of this recipe the README used to promise and the code did not have: history that
 * outlives the process. No model is involved - what is being checked is where the messages go.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:memory-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PersistedMemoryTest {

    @Autowired
    ChatMemoryRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void historySurvivesTheObjectThatWroteIt() {
        String conversationId = UUID.randomUUID().toString();

        ChatMemory writer = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
        writer.add(conversationId, new UserMessage("My name is Alice and I work on payments."));
        writer.add(conversationId, new AssistantMessage("Noted."));

        // A different ChatMemory over the same repository. If the window were holding the messages
        // in a field this would come back empty - which is exactly the bug the in-memory default
        // hides until the first restart.
        ChatMemory reader = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();

        assertThat(reader.get(conversationId))
                .extracting(message -> message.getText())
                .containsExactly("My name is Alice and I work on payments.", "Noted.");
    }

    @Test
    void conversationsAreRowsYouCanListAndDelete() {
        String alice = UUID.randomUUID().toString();
        String bob = UUID.randomUUID().toString();

        ChatMemory memory = MessageWindowChatMemory.builder().chatMemoryRepository(repository).build();
        memory.add(alice, new UserMessage("payments"));
        memory.add(bob, new UserMessage("search"));

        assertThat(repository.findConversationIds()).contains(alice, bob);

        // Deleting one conversation must not touch the other. Worth asserting: a store keyed only
        // by user, or a stray delete without a predicate, passes every other test in this file.
        repository.deleteByConversationId(alice);

        assertThat(repository.findConversationIds()).doesNotContain(alice).contains(bob);
        assertThat(memory.get(bob)).hasSize(1);
    }

    @Test
    void theStarterCreatedItsTable() {
        // initialize-schema: always is doing the work here. Left unset the repository assumes the
        // schema is someone else's job and the first query fails instead.
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'SPRING_AI_CHAT_MEMORY'",
                Integer.class);

        assertThat(tables).isEqualTo(1);
    }

    @Test
    void theWindowBoundsWhatIsSentWithoutTruncatingWhatIsStored() {
        String conversationId = UUID.randomUUID().toString();
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(4)
                .build();

        for (int i = 1; i <= 6; i++) {
            memory.add(conversationId, new UserMessage("turn " + i));
        }

        // The window is what the model sees, and it is the last four turns.
        assertThat(memory.get(conversationId))
                .extracting(message -> message.getText())
                .containsExactly("turn 3", "turn 4", "turn 5", "turn 6");
    }

    @Test
    void separateConversationsDoNotLeakIntoEachOther() {
        String alice = UUID.randomUUID().toString();
        String bob = UUID.randomUUID().toString();
        ChatMemory memory = MessageWindowChatMemory.builder().chatMemoryRepository(repository).build();

        memory.add(alice, new UserMessage("I work on payments."));
        memory.add(bob, new UserMessage("I work on search."));

        assertThat(memory.get(alice)).singleElement()
                .extracting(message -> message.getText()).isEqualTo("I work on payments.");
        assertThat(memory.get(bob)).singleElement()
                .extracting(message -> message.getText()).isEqualTo("I work on search.");
    }

    @Test
    void messagesComeBackInTheOrderTheyWereWritten() {
        String conversationId = UUID.randomUUID().toString();
        ChatMemory memory = MessageWindowChatMemory.builder().chatMemoryRepository(repository).build();

        List<String> turns = List.of("first", "second", "third", "fourth");
        turns.forEach(turn -> memory.add(conversationId, new UserMessage(turn)));

        // The schema carries both a timestamp and a sequence_id, because timestamps collide when
        // two messages land in the same millisecond - which they do on every fast turn.
        assertThat(memory.get(conversationId))
                .extracting(message -> message.getText())
                .containsExactlyElementsOf(turns);
    }
}
