package com.example.cookbook;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * History that outlives the process.
 *
 * LangChain4j ships one store, InMemoryChatMemoryStore, and an interface with three methods. That
 * is the whole difference from the Spring AI side of this recipe, where the same capability is a
 * starter and a generated schema: here you write it, which is more code and fewer assumptions
 * about your database.
 *
 * Deliberately one row per conversation holding the serialised list, not one row per message. It
 * matches what the interface actually asks for - updateMessages replaces the whole list - and a
 * per-message table would need a sequence column to read back in order anyway.
 */
final class JdbcChatMemoryStore implements ChatMemoryStore, AutoCloseable {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS chat_memory (
                conversation_id VARCHAR(64) PRIMARY KEY,
                messages CLOB NOT NULL
            )
            """;

    private final Connection connection;

    JdbcChatMemoryStore(String url) {
        try {
            // One connection on purpose: this is a recipe, not a service. Anything with concurrent
            // conversations wants a pool, and the interface does not change when you add one.
            this.connection = DriverManager.getConnection(url);
            try (PreparedStatement schema = connection.prepareStatement(SCHEMA)) {
                schema.execute();
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("could not open the chat memory database at " + url, e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sql = "SELECT messages FROM chat_memory WHERE conversation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, String.valueOf(memoryId));
            try (ResultSet rows = statement.executeQuery()) {
                // An unknown id is a new conversation, not an error: the provider is called the
                // first time an id is seen and expects an empty list back.
                return rows.next() ? ChatMessageDeserializer.messagesFromJson(rows.getString(1)) : List.of();
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("could not read conversation " + memoryId, e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sql = """
                MERGE INTO chat_memory (conversation_id, messages) KEY (conversation_id) VALUES (?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, String.valueOf(memoryId));
            statement.setString(2, ChatMessageSerializer.messagesToJson(messages));
            statement.executeUpdate();
        }
        catch (SQLException e) {
            throw new IllegalStateException("could not write conversation " + memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try (PreparedStatement statement =
                     connection.prepareStatement("DELETE FROM chat_memory WHERE conversation_id = ?")) {
            statement.setString(1, String.valueOf(memoryId));
            statement.executeUpdate();
        }
        catch (SQLException e) {
            throw new IllegalStateException("could not delete conversation " + memoryId, e);
        }
    }

    /** Not part of the interface, and the first thing you want when operating this. */
    List<String> conversationIds() {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT conversation_id FROM chat_memory");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                ids.add(rows.getString(1));
            }
            return ids;
        }
        catch (SQLException e) {
            throw new IllegalStateException("could not list conversations", e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        }
        catch (SQLException e) {
            throw new IllegalStateException("could not close the chat memory database", e);
        }
    }
}
