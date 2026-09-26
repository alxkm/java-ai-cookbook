package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real pgvector instance, with a fake embedding model. That combination is the
 * useful one: the SQL, the index and the metadata filter are real, the API key is not needed.
 *
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class RagPgvectorTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("cookbook")
            .withUsername("cookbook")
            .withPassword("cookbook");

    private PgVectorStore store(String table) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        KeywordEmbeddingModel embeddings = new KeywordEmbeddingModel();
        PgVectorStore store = PgVectorStore.builder(new JdbcTemplate(dataSource), embeddings)
                .vectorTableName(table)
                // Asked of the model, never typed in. The recipe learned this the hard way - a
                // hardcoded 1536 died on the first INSERT with Ollama embeddings - and this test
                // kept a literal 8 until the stub grew a dimension and the table refused it.
                .dimensions(embeddings.dimensions())
                .initializeSchema(true)
                .build();

        store.afterPropertiesSet();
        return store;
    }

    @Test
    void storesAndRetrievesAcrossConnections() throws Exception {
        PgVectorStore store = store("chunks_basic");

        store.add(List.of(
                new Document("Production deployments happen on Tuesday and Thursday.",
                        Map.of("source", "handbook.md")),
                new Document("Changes to the payments module need two approval steps.",
                        Map.of("source", "handbook.md")),
                new Document("The oncall engineer acknowledges a page within 15 minutes.",
                        Map.of("source", "runbook.md"))));

        List<Document> hits = store.similaritySearch(
                SearchRequest.builder().query("who approves a payments change").topK(1).build());

        assertThat(hits).isNotNull().hasSize(1);
        assertThat(hits.get(0).getText()).contains("payments module");
    }

    @Test
    void metadataFilterNarrowsTheSearch() throws Exception {
        PgVectorStore store = store("chunks_filtered");

        store.add(List.of(
                new Document("Production deployments happen on Tuesday.", Map.of("source", "handbook.md")),
                new Document("Deploy freezes are listed in the runbook.", Map.of("source", "runbook.md"))));

        List<Document> hits = store.similaritySearch(SearchRequest.builder()
                .query("deploy")
                .topK(5)
                .filterExpression("source == 'runbook.md'")
                .build());

        assertThat(hits).isNotNull().isNotEmpty();
        assertThat(hits).allSatisfy(doc ->
                assertThat(doc.getMetadata()).containsEntry("source", "runbook.md"));
    }
}
