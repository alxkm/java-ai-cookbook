package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The whole RAG pipeline in one file: load, split, embed, retrieve, answer.
 */
public class RagMinimal {

    interface Handbook {

        @SystemMessage("Answer only from the provided context. If it is not there, say so.")
        String ask(String question);
    }

    public static void main(String[] args) throws IOException {
        EmbeddingModel embeddingModel = Models.embedding();
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // load -> split -> embed -> store. In memory, so it is rebuilt on every start.
        ingest(readResource("/docs/handbook.md"), embeddingModel, store);
        ContentRetriever retriever = retriever(store, embeddingModel);

        Handbook handbook = AiServices.builder(Handbook.class)
                .chatModel(Models.chat())
                .contentRetriever(retriever)
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
     * 500 characters with 100 of overlap, split on paragraphs first. Methods rather than inline
     * builders so the test checks these numbers and not a copy of them - an earlier test typed the
     * same splitter out again and would have passed whatever the recipe was set to.
     */
    static DocumentSplitter splitter() {
        return DocumentSplitters.recursive(500, 100);
    }

    static void ingest(String text, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter())
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(Document.from(text));
    }

    /**
     * Three segments, and nothing scoring under 0.4. The floor is what stops a question the
     * handbook cannot answer from being answered anyway out of whatever came closest.
     */
    static ContentRetriever retriever(EmbeddingStore<TextSegment> store, EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                // minScore is a relevance score, (cosine + 1) / 2, not a cosine. A bare 0.4 here meant
                // cosine -0.2 and let through chunks with no similarity at all - unrelated questions came
                // back with three chunks of context. This is cosine 0.4, the same floor as the Spring side.
                .minScore(RelevanceScore.fromCosineSimilarity(0.4))
                .build();
    }

    static String readResource(String path) throws IOException {
        try (InputStream in = RagMinimal.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
