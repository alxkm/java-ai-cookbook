package com.example.cookbook;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public class Embeddings {

    private static final List<String> CORPUS = List.of(
            "The garbage collector reclaims memory that is no longer reachable.",
            "Virtual threads make blocking calls cheap on the JVM.",
            "Espresso is brewed by forcing hot water through finely ground coffee.",
            "A record is an immutable data carrier with a compact constructor.");

    record Scored(String text, double score) {
    }

    public static void main(String[] args) {
        String query = args.length > 0 ? String.join(" ", args) : "How does the JVM free memory?";

        EmbeddingModel model = Models.embedding();

        // One call for the whole corpus. Batching matters: per-text calls are the usual
        // reason an ingest job takes minutes instead of seconds.
        List<Embedding> corpus = model.embedAll(CORPUS.stream().map(TextSegment::from).toList()).content();
        Embedding queryVector = model.embed(query).content();

        System.out.println("dimensions: " + queryVector.dimension());
        System.out.println("query: " + query);
        System.out.println();

        IntStream.range(0, CORPUS.size())
                .mapToObj(i -> new Scored(CORPUS.get(i),
                        CosineSimilarity.between(queryVector, corpus.get(i))))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .forEach(s -> System.out.printf("%.4f  %s%n", s.score(), s.text()));
    }
}
