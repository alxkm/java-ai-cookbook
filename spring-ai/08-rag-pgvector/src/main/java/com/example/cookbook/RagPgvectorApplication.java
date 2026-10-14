package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Same pipeline as recipe 07, but the vectors live in Postgres. The difference that matters is
 * ingestion: it has to be idempotent, because the store outlives the process now.
 */
@SpringBootApplication
public class RagPgvectorApplication {

    /** What a start-up did to the index, so the demo can say so and the tests can assert it. */
    enum Ingest {
        INDEXED, UP_TO_DATE, REINDEXED
    }

    private static final String SOURCE = "handbook.md";

    public static void main(String[] args) {
        SpringApplication.run(RagPgvectorApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
                .defaultSystem("Answer only from the provided context. If it is not there, say so.")
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(3).similarityThreshold(0.4).build())
                        .build())
                .build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient,
                          VectorStore vectorStore,
                          JdbcTemplate jdbcTemplate,
                          @Value("classpath:/docs/handbook.md") Resource handbook,
                          @Value("${spring.ai.vectorstore.pgvector.table-name}") String tableName) {

        return args -> {
            Ingest outcome = ingestOnce(vectorStore, jdbcTemplate, tableName, SOURCE,
                    handbook.getContentAsString(StandardCharsets.UTF_8));
            System.out.println(SOURCE + ": " + outcome.name().toLowerCase().replace('_', ' '));

            List<String> questions = args.length > 0
                    ? List.of(String.join(" ", args))
                    : List.of("When can I deploy to production?",
                            "How many approvals does a payments change need?",
                            "What is the refund policy?");

            for (String question : questions) {
                System.out.println("> " + question);
                System.out.println(chatClient.prompt().user(question).call().content());
                System.out.println();
            }
        };
    }

    /**
     * Re-running the app must not double the corpus - and editing the document must not leave the
     * old version answering questions.
     *
     * The obvious check, "are there rows for this source already", gets the first half right and
     * the second half silently wrong: change a line in the handbook and every start-up after that
     * finds rows, skips, and keeps retrieving the old text. So each chunk carries a hash of the
     * document it came from, and a different hash means the source is deleted and added again.
     */
    static Ingest ingestOnce(VectorStore vectorStore, JdbcTemplate jdbcTemplate, String tableName,
                             String source, String text) {
        String hash = sha256(text);

        // A chunk written before hashes were stored reads back as null, and is replaced like any other.
        List<String> stored = jdbcTemplate.queryForList(
                "SELECT metadata->>'source_hash' FROM " + tableName + " WHERE metadata->>'source' = ? LIMIT 1",
                String.class, source);

        if (!stored.isEmpty()) {
            if (hash.equals(stored.get(0))) {
                return Ingest.UP_TO_DATE;
            }
            vectorStore.delete("source == '" + source + "'");
        }

        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(100)
                .withMinChunkSizeChars(50)
                .build().apply(List.of(new Document(text)));
        chunks.forEach(chunk -> {
            chunk.getMetadata().put("source", source);
            chunk.getMetadata().put("source_hash", hash);
        });
        vectorStore.add(chunks);

        return stored.isEmpty() ? Ingest.INDEXED : Ingest.REINDEXED;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM is required to provide SHA-256", e);
        }
    }
}
