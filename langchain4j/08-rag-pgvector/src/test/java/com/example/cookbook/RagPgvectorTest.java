package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

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

    private final KeywordEmbeddingModel embeddingModel = new KeywordEmbeddingModel();

    private PgVectorEmbeddingStore store(String table) {
        return PgVectorEmbeddingStore.builder()
                .host(postgres.getHost())
                .port(postgres.getFirstMappedPort())
                .database(postgres.getDatabaseName())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .table(table)
                .dimension(embeddingModel.dimension())
                .createTable(true)
                .build();
    }

    @Test
    void storesAndRetrievesAcrossConnections() {
        PgVectorEmbeddingStore store = store("chunks_basic");

        ingest(store,
                Document.from("Production deployments happen on Tuesday and Thursday.",
                        Metadata.from("source", "handbook.md")),
                Document.from("Changes to the payments module need two approval steps.",
                        Metadata.from("source", "handbook.md")),
                Document.from("The oncall engineer acknowledges a page within 15 minutes.",
                        Metadata.from("source", "runbook.md")));

        var hits = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("who approves a payments change").content())
                .maxResults(1)
                .build()).matches();

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).embedded().text()).contains("payments module");
    }

    @Test
    void metadataFilterNarrowsTheSearch() {
        PgVectorEmbeddingStore store = store("chunks_filtered");

        ingest(store,
                Document.from("Production deployments happen on Tuesday.", Metadata.from("source", "handbook.md")),
                Document.from("Deploy freezes are listed in the runbook.", Metadata.from("source", "runbook.md")));

        var hits = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("deploy").content())
                .filter(MetadataFilterBuilder.metadataKey("source").isEqualTo("runbook.md"))
                .maxResults(5)
                .build()).matches();

        assertThat(hits).isNotEmpty();
        assertThat(hits).allSatisfy(match ->
                assertThat(match.embedded().metadata().getString("source")).isEqualTo("runbook.md"));
    }

    private void ingest(PgVectorEmbeddingStore store, Document... documents) {
        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(List.of(documents));
    }
    /** Every chunk stored for a source, read back through the store API the recipe uses. */
    private List<String> chunksOf(PgVectorEmbeddingStore store, String source) {
        return store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(source).content())
                        .filter(MetadataFilterBuilder.metadataKey("source").isEqualTo(source))
                        .maxResults(1000)
                        .minScore(0.0)
                        .build())
                .matches().stream().map(match -> match.embedded().text()).toList();
    }

    private static final String HANDBOOK = "Production deployments happen on Tuesday and Thursday.\n\n"
            + "Changes to the payments module need two approval steps.";

    @Test
    void aSecondStartIndexesNothing() {
        PgVectorEmbeddingStore store = store("ingest_twice");

        assertThat(RagPgvector.ingestOnce(store, embeddingModel, "handbook.md", HANDBOOK)).isEqualTo(RagPgvector.Ingest.INDEXED);
        int afterFirst = chunksOf(store, "handbook.md").size();

        assertThat(RagPgvector.ingestOnce(store, embeddingModel, "handbook.md", HANDBOOK)).isEqualTo(RagPgvector.Ingest.UP_TO_DATE);
        assertThat(chunksOf(store, "handbook.md")).hasSize(afterFirst);
    }

    @Test
    void anEditedDocumentReplacesTheOldOne() {
        PgVectorEmbeddingStore store = store("ingest_edited");
        RagPgvector.ingestOnce(store, embeddingModel, "handbook.md", HANDBOOK);

        String edited = HANDBOOK.replace("Tuesday and Thursday", "Monday only");
        assertThat(RagPgvector.ingestOnce(store, embeddingModel, "handbook.md", edited)).isEqualTo(RagPgvector.Ingest.REINDEXED);

        // The failure this guards against is quiet: an "already indexed" check keeps the old text in
        // the table and the app goes on answering from it after the document has changed.
        List<String> stored = chunksOf(store, "handbook.md");
        assertThat(String.join(" ", stored)).contains("Monday only").doesNotContain("Tuesday and Thursday");
    }

    @Test
    void replacingOneSourceLeavesTheOthersAlone() {
        PgVectorEmbeddingStore store = store("ingest_two_sources");
        RagPgvector.ingestOnce(store, embeddingModel, "handbook.md", HANDBOOK);
        RagPgvector.ingestOnce(store, embeddingModel, "runbook.md", "The oncall engineer acknowledges a page within 15 minutes.");

        RagPgvector.ingestOnce(store, embeddingModel, "handbook.md", HANDBOOK + " Hotfixes skip the queue.");

        assertThat(chunksOf(store, "runbook.md")).singleElement().asString().contains("oncall engineer");
    }

    @Test
    void chunksFromBeforeHashesWereStoredAreReplaced() {
        PgVectorEmbeddingStore store = store("ingest_legacy");
        // What the previous version of the recipe wrote: a source, and no hash.
        ingest(store, Document.from("An old copy of the handbook.", Metadata.from("source", "handbook.md")));

        assertThat(RagPgvector.ingestOnce(store, embeddingModel, "handbook.md", HANDBOOK)).isEqualTo(RagPgvector.Ingest.REINDEXED);
        assertThat(String.join(" ", chunksOf(store, "handbook.md"))).doesNotContain("An old copy");
    }
}
