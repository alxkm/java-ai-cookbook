package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of a RAG test is not the answer, it is the context: did the right chunk end up in
 * the prompt? That can be checked without a model.
 */
class RagMinimalTest {

    private static final Document DEPLOY = new Document(
            "Production deployments happen on Tuesday and Thursday. Every deploy needs two approval steps.");
    private static final Document PAYMENTS = new Document(
            "Changes to the payments module need two approval steps, one from the payments team.");
    private static final Document ONCALL = new Document(
            "The oncall engineer acknowledges a page within 15 minutes. Sev-1 pages immediately.");

    @Test
    void retrievedChunksAreInjectedIntoThePrompt() {
        VectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
        store.add(List.of(DEPLOY, PAYMENTS, ONCALL));

        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel stub = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };

        ChatClient.builder(stub)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(store)
                        .searchRequest(SearchRequest.builder().topK(1).similarityThreshold(0.0).build())
                        .build())
                .build()
                .prompt()
                .user("Who approves a payments change?")
                .call()
                .content();

        String sent = captured.get().getInstructions().toString();
        assertThat(sent).contains("payments team");
        assertThat(sent).doesNotContain("oncall engineer");
    }

    @Test
    void searchRanksTheRelevantChunkFirst() {
        VectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
        store.add(List.of(DEPLOY, PAYMENTS, ONCALL));

        List<Document> hits = store.similaritySearch(
                SearchRequest.builder().query("when can I deploy on tuesday").topK(1).build());

        assertThat(hits).isNotNull();
        assertThat(hits.get(0).getText()).contains("Production deployments");
    }

    /**
     * Chunk size is a retrieval decision, and getting it wrong is invisible: the app still runs,
     * the answers just get worse. With the splitter default of 800 tokens this handbook came out
     * as a single chunk, so top-k had nothing to choose between and a local model answered the
     * approvals question with the deployment rule.
     */
    @Test
    void theHandbookIsSplitIntoSeveralChunks() {
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(100)
                .withMinChunkSizeChars(50)
                .build()
                .apply(new TextReader(new ClassPathResource("/docs/handbook.md")).get());

        assertThat(chunks).hasSizeGreaterThan(1);

        // The two rules a reader is most likely to confuse must not share a chunk.
        assertThat(chunks)
                .filteredOn(chunk -> chunk.getText().contains("payments module"))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk.getText()).doesNotContain("Tuesdays and Thursdays"));
    }
}
