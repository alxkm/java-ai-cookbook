package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagAdvancedTest {

    private static final Document DEPLOY = new Document(
            "Production deployments happen on Tuesday and Thursday.", Map.of("source", "handbook.md"));
    private static final Document PAYMENTS = new Document(
            "Changes to the payments module need two approving reviews.", Map.of("source", "handbook.md"));
    private static final Document ROLLBACK = new Document(
            "A rollback is a deployment of the previous release tag.", Map.of("source", "runbook.md"));

    @Test
    void rerankerPutsTheChunkWithTheQueryTermsFirst() {
        List<Document> reranked = new KeywordRerankProcessor(2).process(
                new Query("How many reviews does a payments change need?"),
                List.of(DEPLOY, ROLLBACK, PAYMENTS));

        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).getText()).contains("payments module");
    }

    @Test
    void metadataFilterKeepsTheOtherSourceOut() {
        VectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
        store.add(List.of(DEPLOY, PAYMENTS, ROLLBACK));

        List<Prompt> sent = new ArrayList<>();
        ChatModel stub = prompt -> {
            sent.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };

        var advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(store)
                        .topK(8)
                        .similarityThreshold(0.0)
                        .filterExpression(new FilterExpressionBuilder().eq("source", "handbook.md").build())
                        .build())
                .documentPostProcessors(new KeywordRerankProcessor(3))
                .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
                .build();

        ChatClient.builder(stub)
                .defaultAdvisors(advisor)
                .build()
                .prompt()
                .user("Who approves a payments change?")
                .call()
                .content();

        String prompt = sent.get(0).getInstructions().toString();
        assertThat(prompt).contains("payments module");
        assertThat(prompt).doesNotContain("previous release tag");
    }
}
