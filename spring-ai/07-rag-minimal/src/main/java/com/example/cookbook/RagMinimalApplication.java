package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * The whole RAG pipeline in one file: load, split, embed, retrieve, answer.
 */
@SpringBootApplication
public class RagMinimalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagMinimalApplication.class, args);
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel,
                            @Value("classpath:/docs/handbook.md") Resource handbook) {

        // load -> split -> embed -> store. In memory, so it is rebuilt on every start.
        List<Document> chunks = split(handbook);

        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        store.add(chunks);

        System.out.println("indexed " + chunks.size() + " chunks");
        return store;
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
                .defaultSystem("Answer only from the provided context. If it is not there, say so.")
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(retrieval())
                        .build())
                .build();
    }

    /**
     * The splitter default is 800 tokens, which swallows this short handbook whole and leaves
     * retrieval nothing to choose between. Sizing the chunks to the corpus is not cosmetic: with one
     * chunk a local model answered the approvals question with the deployment rule, and with three
     * it quotes the right sentence. A method rather than an inline builder so the test checks these
     * numbers and not a copy of them.
     */
    static List<Document> split(Resource source) {
        return TokenTextSplitter.builder()
                .withChunkSize(100)
                .withMinChunkSizeChars(50)
                .build().apply(new TextReader(source).get());
    }

    /**
     * Three chunks, and nothing below 0.4. The threshold is the setting that decides whether a
     * weakly related chunk reaches the prompt at all, so it is the one worth testing as-is.
     */
    static SearchRequest retrieval() {
        return SearchRequest.builder().topK(3).similarityThreshold(0.4).build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient) {
        return args -> {
            List<String> questions = args.length > 0
                    ? List.of(String.join(" ", args))
                    : List.of("When can I deploy to production?",
                    "How many approvals does a payments change need?",
                    "What is the refund policy?");

            for (String question : questions) {
                System.out.println("> " + question);
                System.out.println(chatClient.prompt().user(question).call().content());
                System.out.println();
            }
        };
    }
}
