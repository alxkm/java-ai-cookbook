package com.example.cookbook;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Comparator;
import java.util.List;

@SpringBootApplication
public class EmbeddingsApplication {

    private static final List<String> CORPUS = List.of(
            "The garbage collector reclaims memory that is no longer reachable.",
            "Virtual threads make blocking calls cheap on the JVM.",
            "Espresso is brewed by forcing hot water through finely ground coffee.",
            "A record is an immutable data carrier with a compact constructor.");

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingsApplication.class, args);
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(EmbeddingModel embeddingModel) {
        return args -> {
            String query = args.length > 0 ? String.join(" ", args) : "How does the JVM free memory?";

            // One call for the whole corpus. Batching matters: per-text calls are the usual
            // reason an ingest job takes minutes instead of seconds.
            EmbeddingResponse response = embeddingModel.embedForResponse(CORPUS);
            float[] queryVector = embeddingModel.embed(query);

            System.out.println("dimensions: " + queryVector.length);
            System.out.println("query: " + query);
            System.out.println();

            record Scored(String text, double score) {
            }

            java.util.stream.IntStream.range(0, CORPUS.size())
                    .mapToObj(i -> new Scored(CORPUS.get(i),
                            Similarity.cosine(queryVector, response.getResults().get(i).getOutput())))
                    .sorted(Comparator.comparingDouble(Scored::score).reversed())
                    .forEach(s -> System.out.printf("%.4f  %s%n", s.score(), s.text()));
        };
    }
}
