package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class ToolSearchApplication {

    /** The advisor keys its index by session, so a conversation id doubles as the index key. */
    static final String SESSION_ID = "support-desk";

    public static void main(String[] args) {
        SpringApplication.run(ToolSearchApplication.class, args);
    }

    /**
     * BM25 over the tool names and descriptions. The alternative index types are a regex matcher
     * and a vector store; Lucene is the middle one - no embedding model to call, better than
     * substring matching.
     */
    @Bean
    ToolIndex toolIndex() {
        return new LuceneToolIndex();
    }

    /**
     * Indexing is not automatic. The advisor searches whatever is in the index for the session, and
     * an empty index means the model is told there are no tools rather than told to look harder.
     */
    @Bean
    List<ToolReference> indexedTools(ToolIndex toolIndex, ToolCatalog catalog) {
        List<ToolReference> references = Arrays.stream(ToolCallbacks.from(catalog))
                .map(callback -> ToolReference.builder()
                        .toolName(callback.getToolDefinition().name())
                        .summary(callback.getToolDefinition().description())
                        .build())
                .toList();

        toolIndex.indexTools(SESSION_ID, references);
        return references;
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolIndex toolIndex, ToolCatalog catalog) {
        return builder
                .defaultSystem("You are a support agent. Search for a tool before answering.")
                // The catalogue is still registered - the advisor decides which of these the model is
                // shown, it does not fetch them from anywhere else.
                .defaultTools(catalog)
                .defaultAdvisors(ToolSearchToolCallingAdvisor.builder()
                        .toolIndex(toolIndex)
                        .maxResults(3)
                        .build())
                .build();
    }

    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient, List<ToolReference> indexed) {
        return args -> {
            String question = args.length > 0
                    ? String.join(" ", args)
                    : "The customer wants their money back for order A-1001.";

            System.out.println(indexed.size() + " tools indexed, at most 3 reach the model per turn");
            System.out.println("> " + question);
            System.out.println(chatClient.prompt()
                    .user(question)
                    .advisors(a -> a.param("sessionId", SESSION_ID))
                    .call()
                    .content());
        };
    }
}
