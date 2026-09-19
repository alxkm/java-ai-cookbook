package com.example.cookbook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of a RAG test is not the answer, it is the context: did the right chunk end up in
 * the prompt? That can be checked without a model.
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

    @Test
    void retrievedChunksAreInjectedIntoThePrompt() {
        KeywordEmbeddingModel embeddingModel = new KeywordEmbeddingModel();
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(List.of(
                        Document.from("Production deployments happen on Tuesday and Thursday."),
                        Document.from("Changes to the payments module need two approval steps, "
                                + "one from the payments team."),
                        Document.from("The oncall engineer acknowledges a page within 15 minutes.")));

        RecordingModel model = new RecordingModel();

        AiServices.builder(Handbook.class)
                .chatModel(model)
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(store)
                        .embeddingModel(embeddingModel)
                        .maxResults(1)
                        .build())
                .build()
                .ask("Who approves a payments change?");

        String sent = model.requests.get(0).messages().toString();
        assertThat(sent).contains("payments team");
        assertThat(sent).doesNotContain("oncall engineer");
    }

    /**
     * Chunk size is a retrieval decision, and getting it wrong is invisible: the app still runs,
     * the answers just get worse. One chunk for the whole corpus means top-k has nothing to
     * choose between, and the model answers from whichever rule it read first.
     */
    @Test
    void theHandbookIsSplitIntoSeveralChunks() throws IOException {
        List<TextSegment> chunks = DocumentSplitters.recursive(500, 100)
                .split(Document.from(readHandbook()));

        assertThat(chunks).hasSizeGreaterThan(1);

        // The two rules a reader is most likely to confuse must not share a chunk.
        assertThat(chunks)
                .filteredOn(chunk -> chunk.text().contains("payments module"))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk.text()).doesNotContain("Tuesdays and Thursdays"));
    }

    private static String readHandbook() throws IOException {
        try (InputStream in = RagMinimalTest.class.getResourceAsStream("/docs/handbook.md")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
