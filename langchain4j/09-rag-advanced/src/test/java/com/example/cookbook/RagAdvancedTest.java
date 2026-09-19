package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagAdvancedTest {

    interface Handbook {
        String ask(String question);
    }

    static class RecordingModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    @Test
    void rerankerScoresTheChunkWithTheQueryTermsHigher() {
        var scores = new KeywordScoringModel().scoreAll(
                List.of(TextSegment.from("Production deployments happen on Tuesday and Thursday."),
                        TextSegment.from("Changes to the payments module need two approving reviews.")),
                "How many reviews does a payments change need?").content();

        assertThat(scores.get(1)).isGreaterThan(scores.get(0));
    }

    @Test
    void metadataFilterAndRerankingNarrowTheContext() {
        KeywordEmbeddingModel embeddingModel = new KeywordEmbeddingModel();
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(List.of(
                        Document.from("Production deployments happen on Tuesday and Thursday.",
                                Metadata.from("source", "handbook.md")),
                        Document.from("Changes to the payments module need two approving reviews.",
                                Metadata.from("source", "handbook.md")),
                        Document.from("A rollback is a deployment of the previous release tag.",
                                Metadata.from("source", "runbook.md"))));

        RecordingModel model = new RecordingModel();

        AiServices.builder(Handbook.class)
                .chatModel(model)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(EmbeddingStoreContentRetriever.builder()
                                .embeddingStore(store)
                                .embeddingModel(embeddingModel)
                                .maxResults(8)
                                .filter(MetadataFilterBuilder.metadataKey("source").isEqualTo("handbook.md"))
                                .build())
                        .contentAggregator(ReRankingContentAggregator.builder()
                                .scoringModel(new KeywordScoringModel())
                                .minScore(0.1)
                                .maxResults(1)
                                .build())
                        .build())
                .build()
                .ask("Who approves a payments change?");

        String prompt = model.requests.get(0).messages().toString();
        assertThat(prompt).contains("payments module");
        assertThat(prompt).doesNotContain("previous release tag");
    }

    /**
     * Expanding the query and re-ranking do not compose by default: the aggregator refuses to
     * guess which of several queries to score against, and throws. It only shows up once a real
     * query transformer is in the pipeline, which is why this test puts one there.
     */
    @Test
    void reRankingSurvivesAnExpandedQuery() {
        KeywordEmbeddingModel embeddingModel = new KeywordEmbeddingModel();
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(List.of(
                        Document.from("Changes to the payments module need two approving reviews.",
                                Metadata.from("source", "handbook.md")),
                        Document.from("Production deployments happen on Tuesday and Thursday.",
                                Metadata.from("source", "handbook.md"))));

        RecordingModel model = new RecordingModel();

        AiServices.builder(Handbook.class)
                .chatModel(model)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        // stands in for ExpandingQueryTransformer, without needing a model
                        .queryTransformer(query -> List.of(
                                query,
                                Query.from("payments approval rules"),
                                Query.from("who signs off a payments change")))
                        .contentRetriever(EmbeddingStoreContentRetriever.builder()
                                .embeddingStore(store)
                                .embeddingModel(embeddingModel)
                                .maxResults(8)
                                .build())
                        .contentAggregator(ReRankingContentAggregator.builder()
                                .scoringModel(new KeywordScoringModel())
                                .querySelector(queryToContents -> queryToContents.keySet().iterator().next())
                                .minScore(0.1)
                                .maxResults(1)
                                .build())
                        .build())
                .build()
                .ask("Who approves a payments change?");

        assertThat(model.requests.get(0).messages().toString()).contains("approving reviews");
    }
}
