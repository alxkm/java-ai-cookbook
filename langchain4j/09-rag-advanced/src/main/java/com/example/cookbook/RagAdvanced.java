package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Three things recipe 07 does not do:
 *
 *   1. expand the user question into several search queries
 *   2. restrict the search to one source with a metadata filter
 *   3. re-rank what came back and drop everything below a score
 */
public class RagAdvanced {

    interface Handbook {

        @SystemMessage("Answer only from the provided context. If it is not there, say so.")
        String ask(String question);
    }

    public static void main(String[] args) throws IOException {
        ChatModel chatModel = Models.chat();
        EmbeddingModel embeddingModel = Models.embedding();

        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();

        ingestor.ingest(List.of(
                load("handbook.md"),
                load("runbook.md")));

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                // "and what about payments?" is a terrible search query. Turn it into three better ones.
                .queryTransformer(new ExpandingQueryTransformer(chatModel, 3))
                // Over-fetch, then let the re-ranker cut it down. maxResults=3 straight from the
                // vector search leaves nothing for the second pass to improve.
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(store)
                        .embeddingModel(embeddingModel)
                        .maxResults(8)
                        .minScore(0.3)
                        .filter(MetadataFilterBuilder.metadataKey("source").isEqualTo("handbook.md"))
                        .build())
                .contentAggregator(ReRankingContentAggregator.builder()
                        .scoringModel(new KeywordScoringModel())
                        // Expanding the query produces several queries, and the aggregator then
                        // refuses to guess which one to re-rank against. Say it explicitly: the
                        // original question is the one the user actually asked.
                        .querySelector(queryToContents -> queryToContents.keySet().iterator().next())
                        .minScore(0.1)
                        .maxResults(3)
                        .build())
                .build();

        Handbook handbook = AiServices.builder(Handbook.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentor)
                .build();

        List<String> questions = args.length > 0
                ? List.of(String.join(" ", args))
                : List.of("How many approvals does a payments change need?",
                        "Can I deploy on 28 December?",
                        "How do I roll back a release?");

        for (String question : questions) {
            System.out.println("> " + question);
            System.out.println(handbook.ask(question));
            System.out.println();
        }
    }

    private static Document load(String name) throws IOException {
        try (InputStream in = RagAdvanced.class.getResourceAsStream("/docs/" + name)) {
            if (in == null) {
                throw new IOException("Resource not found: " + name);
            }
            return Document.from(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    Metadata.from("source", name));
        }
    }
}
