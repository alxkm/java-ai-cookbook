package com.example.cookbook;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SpringBootApplication
public class EmbeddingsApplication {

    private static final List<String> CORPUS = List.of(
            "The garbage collector reclaims memory that is no longer reachable.",
            "Virtual threads make blocking calls cheap on the JVM.",
            "Espresso is brewed by forcing hot water through finely ground coffee.",
            "A record is an immutable data carrier with a compact constructor.");

    record Scored(String text, double score) {
    }

    /**
     * The dimension travels with the result because asking the model for it is not free: the
     * default dimension() embeds a throwaway string - one more billed call - on any provider that
     * does not hardcode its own. The query vector already knows.
     */
    record Ranking(int dimensions, List<Scored> scored) {
    }

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingsApplication.class, args);
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(EmbeddingModel embeddingModel) {
        return args -> {
            String query = args.length > 0 ? String.join(" ", args) : "How does the JVM free memory?";

            Ranking ranking = rank(embeddingModel, query, CORPUS);

            System.out.println("dimensions: " + ranking.dimensions());
            System.out.println("query: " + query);
            System.out.println();
            ranking.scored().forEach(s -> System.out.printf("%.4f  %s%n", s.score(), s.text()));
        };
    }

    /**
     * Every text in the corpus, scored against the query and sorted best first.
     *
     * One call for the whole corpus. Batching matters: per-text calls are the usual reason an ingest
     * job takes minutes instead of seconds.
     */
    static Ranking rank(EmbeddingModel embeddingModel, String query, List<String> corpus) {
        EmbeddingResponse response = embeddingModel.embedForResponse(corpus);

        // A provider that drops a text - an empty string, one over the token limit - returns fewer
        // results than it was given. Paired by position after that, every later text is labelled
        // with its neighbour's score and nothing fails. Refuse instead.
        if (response.getResults().size() != corpus.size()) {
            throw new IllegalStateException("asked for " + corpus.size() + " embeddings, got "
                    + response.getResults().size());
        }

        // After the check: no point paying for the query embedding when the corpus is unusable.
        float[] queryVector = embeddingModel.embed(query);

        List<Scored> scored = new ArrayList<>();
        for (Embedding result : response.getResults()) {
            // Paired by the index the result carries, not by where it sits in the list. The API
            // does not promise the two agree, and when they do not, nothing looks wrong.
            scored.add(new Scored(corpus.get(result.getIndex()), Similarity.cosine(queryVector, result.getOutput())));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return new Ranking(queryVector.length, scored);
    }
}
