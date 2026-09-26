package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Same pipeline as recipe 07, but the vectors live in Postgres. The difference that matters is
 * ingestion: it has to be idempotent, because the store outlives the process now.
 */
public class RagPgvector {

    private static final String SOURCE = "handbook.md";

    interface Handbook {

        @SystemMessage("Answer only from the provided context. If it is not there, say so.")
        String ask(String question);
    }

    public static void main(String[] args) throws IOException {
        EmbeddingModel embeddingModel = Models.embedding();

        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host(Models.envOr("PGHOST", "localhost"))
                .port(Integer.parseInt(Models.envOr("PGPORT", "5432")))
                .database(Models.envOr("PGDATABASE", "cookbook"))
                .user(Models.envOr("PGUSER", "cookbook"))
                .password(Models.envOr("PGPASSWORD", "cookbook"))
                .table("handbook_chunks")
                .dimension(embeddingModel.dimension())
                // creates the table and the pgvector extension on first run; in production you
                // own this schema and manage it with your migration tool instead
                .createTable(true)
                .build();

        ingestOnce(store, embeddingModel);

        Handbook handbook = AiServices.builder(Handbook.class)
                .chatModel(Models.chat())
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(store)
                        .embeddingModel(embeddingModel)
                        .maxResults(3)
                        // minScore is a relevance score, (cosine + 1) / 2, not a cosine. A bare 0.4 here meant
                        // cosine -0.2 and let through chunks with no similarity at all - unrelated questions came
                        // back with three chunks of context. This is cosine 0.4, the same floor as the Spring side.
                        .minScore(RelevanceScore.fromCosineSimilarity(0.4))
                        .build())
                .build();

        List<String> questions = args.length > 0
                ? List.of(String.join(" ", args))
                : List.of("When can I deploy to production?",
                        "How many approvals does a payments change need?",
                        "What is the refund policy?");

        for (String question : questions) {
            System.out.println("> " + question);
            System.out.println(handbook.ask(question));
            System.out.println();
        }
    }

    /**
     * Re-running the app must not double the corpus. Searching for the source marker before
     * ingesting is the simplest thing that works; a content hash per chunk is the next step up.
     */
    private static void ingestOnce(PgVectorEmbeddingStore store, EmbeddingModel embeddingModel) throws IOException {
        var probe = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("deployment").content())
                .filter(MetadataFilterBuilder.metadataKey("source").isEqualTo(SOURCE))
                .maxResults(1)
                .minScore(0.0)
                .build());

        if (!probe.matches().isEmpty()) {
            System.out.println("already indexed");
            return;
        }

        Document document = Document.from(readResource("/docs/" + SOURCE), Metadata.from("source", SOURCE));

        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(document);

        System.out.println("indexed " + SOURCE);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = RagPgvector.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
