package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Same pipeline as recipe 07, but the vectors live in Postgres. The difference that matters is
 * ingestion: it has to be idempotent, because the store outlives the process now.
 */
public class RagPgvector {

    /** What a start-up did to the index, so the demo can say so and the tests can assert it. */
    enum Ingest {
        INDEXED, UP_TO_DATE, REINDEXED
    }

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

        Ingest outcome = ingestOnce(store, embeddingModel, SOURCE, readResource("/docs/" + SOURCE));
        System.out.println(SOURCE + ": " + outcome.name().toLowerCase().replace('_', ' '));

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
     * Re-running the app must not double the corpus - and editing the document must not leave the
     * old version answering questions.
     *
     * The obvious check, "is anything from this source indexed already", gets the first half right
     * and the second half silently wrong: change a line in the handbook and every start-up after that
     * reports "already indexed" and keeps retrieving the old text, forever. So each chunk carries a
     * hash of the document it came from, and a different hash means the source is replaced.
     */
    static Ingest ingestOnce(EmbeddingStore<TextSegment> store, EmbeddingModel embeddingModel,
                             String source, String text) {
        String hash = sha256(text);
        Filter ofSource = MetadataFilterBuilder.metadataKey("source").isEqualTo(source);

        // Any vector will do as the query: the filter and a zero score floor make this "is there a
        // chunk from this source", which is all the store API can ask without raw SQL.
        List<EmbeddingMatch<TextSegment>> existing = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(source).content())
                .filter(ofSource)
                .maxResults(1)
                .minScore(0.0)
                .build()).matches();

        if (!existing.isEmpty()) {
            // A chunk written before hashes were stored has none, and is replaced like any other.
            if (hash.equals(existing.get(0).embedded().metadata().getString("source_hash"))) {
                return Ingest.UP_TO_DATE;
            }
            store.removeAll(ofSource);
        }

        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(Document.from(text, Metadata.from("source", source).put("source_hash", hash)));

        return existing.isEmpty() ? Ingest.INDEXED : Ingest.REINDEXED;
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

    static String readResource(String path) throws IOException {
        try (InputStream in = RagPgvector.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
