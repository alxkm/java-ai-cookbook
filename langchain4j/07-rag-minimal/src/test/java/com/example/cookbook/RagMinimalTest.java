package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of a RAG test is not the answer, it is the context: did the right chunk end up in
 * the prompt? That can be checked without a model.
 *
 * Every test here goes through the recipe's own splitter(), ingest() and retriever(). An earlier
 * version typed the same splitter and a different retriever out again, so it tested a copy and
 * would have passed whatever the recipe was set to.
 */
class RagMinimalTest {

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

    private static final KeywordEmbeddingModel EMBEDDINGS = new KeywordEmbeddingModel();

    private static EmbeddingStore<TextSegment> indexedHandbook() throws IOException {
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        RagMinimal.ingest(RagMinimal.readResource("/docs/handbook.md"), EMBEDDINGS, store);
        return store;
    }

    private static List<String> retrieve(String question) throws IOException {
        return RagMinimal.retriever(indexedHandbook(), EMBEDDINGS).retrieve(Query.from(question)).stream()
                .map(Content::textSegment)
                .map(TextSegment::text)
                .toList();
    }

    /**
     * Chunk size is a retrieval decision, and getting it wrong is invisible: the app still runs,
     * the answers just get worse. One chunk for the whole corpus means top-k has nothing to
     * choose between, and the model answers from whichever rule it read first.
     */
    @Test
    void theHandbookIsSplitIntoSeveralChunks() throws IOException {
        List<TextSegment> chunks = RagMinimal.splitter()
                .split(Document.from(RagMinimal.readResource("/docs/handbook.md")));

        assertThat(chunks).hasSizeGreaterThan(1);

        // The two rules a reader is most likely to confuse must not share a chunk.
        assertThat(chunks)
                .filteredOn(chunk -> chunk.text().contains("payments module"))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk.text()).doesNotContain("Tuesdays and Thursdays"));
    }

    @Test
    void theRightChunkOfTheRealHandbookReachesThePrompt() throws IOException {
        RecordingModel model = new RecordingModel();

        AiServices.builder(Handbook.class)
                .chatModel(model)
                .contentRetriever(RagMinimal.retriever(indexedHandbook(), EMBEDDINGS))
                .build()
                .ask("How many approvals does a payments change need?");

        // Split, stored and retrieved with the recipe's own settings, score floor included.
        assertThat(model.requests.get(0).messages().toString()).contains("payments team");
    }

    @Test
    void anUnrelatedQuestionRetrievesNothingRatherThanTheClosestNoise() throws IOException {
        // What the 0.4 floor is for. Without it maxResults always fills up, and a question the
        // handbook cannot answer gets answered anyway, out of whatever scored highest.
        assertThat(retrieve("what is the capital of France")).isEmpty();
    }

    @Test
    void aDeploymentQuestionRetrievesTheDeploymentRules() throws IOException {
        assertThat(retrieve("when can I deploy on tuesday"))
                .isNotEmpty()
                .first().asString().contains("Production deployments");
    }
}
