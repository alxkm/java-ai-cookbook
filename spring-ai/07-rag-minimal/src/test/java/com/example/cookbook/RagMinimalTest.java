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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of a RAG test is not the answer, it is the context: did the right chunk end up in
 * the prompt? That can be checked without a model.
 *
 * Every test here goes through the recipe's own split() and retrieval(). An earlier version built
 * its own splitter and its own search request with the same numbers typed out again, which meant it
 * tested a copy: put the recipe back on the 800-token default and it still passed - the exact
 * regression it had been written to catch.
 */
class RagMinimalTest {

    private static final ClassPathResource HANDBOOK = new ClassPathResource("/docs/handbook.md");

    private static VectorStore indexedHandbook() {
        VectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
        store.add(RagMinimalApplication.split(HANDBOOK));
        return store;
    }

    /**
     * Chunk size is a retrieval decision, and getting it wrong is invisible: the app still runs,
     * the answers just get worse. With the splitter default of 800 tokens this handbook came out
     * as a single chunk, so top-k had nothing to choose between and a local model answered the
     * approvals question with the deployment rule.
     */
    @Test
    void theHandbookIsSplitIntoSeveralChunks() {
        List<Document> chunks = RagMinimalApplication.split(HANDBOOK);

        assertThat(chunks).hasSizeGreaterThan(1);

        // The two rules a reader is most likely to confuse must not share a chunk.
        assertThat(chunks)
                .filteredOn(chunk -> chunk.getText().contains("payments module"))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk.getText()).doesNotContain("Tuesdays and Thursdays"));
    }

    @Test
    void theRightChunkOfTheRealHandbookReachesThePrompt() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel stub = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };

        ChatClient.builder(stub)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(indexedHandbook())
                        .searchRequest(RagMinimalApplication.retrieval())
                        .build())
                .build()
                .prompt()
                .user("How many approvals does a payments change need?")
                .call()
                .content();

        // Split, stored and retrieved with the recipe's own settings, threshold included.
        assertThat(captured.get().getInstructions().toString()).contains("payments team");
    }

    @Test
    void anUnrelatedQuestionRetrievesNothingRatherThanTheClosestNoise() {
        // This is what the 0.4 threshold is for. Without it top-k always returns three chunks, and a
        // question the handbook cannot answer gets answered anyway, out of whatever scored highest.
        List<Document> hits = indexedHandbook().similaritySearch(SearchRequest.from(RagMinimalApplication.retrieval())
                .query("what is the capital of France")
                .build());

        assertThat(hits).isEmpty();
    }

    @Test
    void searchRanksTheRelevantChunkFirst() {
        List<Document> hits = indexedHandbook().similaritySearch(SearchRequest.from(RagMinimalApplication.retrieval())
                .query("when can I deploy on tuesday")
                .build());

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getText()).contains("Production deployments");
    }
}
