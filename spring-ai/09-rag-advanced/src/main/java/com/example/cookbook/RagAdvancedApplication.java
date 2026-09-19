package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * Three things recipe 07 does not do:
 *
 *   1. rewrite the user question into a better search query
 *   2. restrict the search to one source with a metadata filter
 *   3. re-rank what came back, and refuse to answer when nothing relevant did
 */
@SpringBootApplication
public class RagAdvancedApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAdvancedApplication.class, args);
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel,
                            @Value("classpath:/docs/handbook.md") Resource handbook,
                            @Value("classpath:/docs/runbook.md") Resource runbook) {

        List<Document> chunks = new ArrayList<>();
        chunks.addAll(load(handbook, "handbook.md"));
        chunks.addAll(load(runbook, "runbook.md"));

        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        store.add(chunks);

        System.out.println("indexed " + chunks.size() + " chunks from two sources");
        return store;
    }

    private List<Document> load(Resource resource, String source) {
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(100)
                .withMinChunkSizeChars(50)
                .build().apply(new TextReader(resource).get());
        chunks.forEach(chunk -> chunk.getMetadata().put("source", source));
        return chunks;
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {

        var onlyHandbook = new FilterExpressionBuilder().eq("source", "handbook.md").build();

        var advisor = RetrievalAugmentationAdvisor.builder()
                // "and what about payments?" is a terrible search query. Rewrite it first.
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(builder.build().mutate())
                        .build())
                // Over-fetch, then let the re-ranker cut it down. topK=3 straight from the vector
                // search leaves nothing for the second pass to improve.
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(8)
                        .similarityThreshold(0.3)
                        .filterExpression(onlyHandbook)
                        .build())
                .documentPostProcessors(new KeywordRerankProcessor(3))
                // allowEmptyContext(false) makes the model say it does not know instead of
                // answering from its own memory when retrieval comes back empty.
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .build())
                .build();

        return builder
                .defaultSystem("Answer only from the provided context.")
                .defaultAdvisors(advisor)
                .build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient) {
        return args -> {
            List<String> questions = args.length > 0
                    ? List.of(String.join(" ", args))
                    : List.of("How many approvals does a payments change need?",
                            "Can I deploy on 28 December?",
                            "How do I roll back a release?");

            for (String question : questions) {
                System.out.println("> " + question);
                System.out.println(chatClient.prompt().user(question).call().content());
                System.out.println();
            }
        };
    }
}
