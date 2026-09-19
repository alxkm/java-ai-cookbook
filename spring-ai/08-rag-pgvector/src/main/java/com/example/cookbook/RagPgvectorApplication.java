package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
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

import java.util.List;

/**
 * Same pipeline as recipe 07, but the vectors live in Postgres. The difference that matters is
 * ingestion: it has to be idempotent, because the store outlives the process now.
 */
@SpringBootApplication
public class RagPgvectorApplication {

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
            ingestOnce(vectorStore, jdbcTemplate, handbook, tableName);

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
     * Re-running the app must not double the corpus. Deleting by source metadata and re-adding is
     * the simplest thing that works; a content hash per chunk is the next step up.
     */
    private void ingestOnce(VectorStore vectorStore, JdbcTemplate jdbcTemplate, Resource handbook, String tableName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tableName + " WHERE metadata->>'source' = ?", Integer.class, SOURCE);

        if (existing != null && existing > 0) {
            System.out.println("already indexed: " + existing + " chunks");
            return;
        }

        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(100)
                .withMinChunkSizeChars(50)
                .build().apply(new TextReader(handbook).get());
        chunks.forEach(chunk -> chunk.getMetadata().put("source", SOURCE));
        vectorStore.add(chunks);

        System.out.println("indexed " + chunks.size() + " chunks");
    }
}
