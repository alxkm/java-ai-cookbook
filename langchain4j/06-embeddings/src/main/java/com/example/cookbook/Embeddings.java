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

    /**
     * The dimension travels with the result because asking the model for it is not free: the
     * default dimension() embeds a throwaway string - one more billed call - on any provider that
     * does not hardcode its own. The query vector already knows.
     */
    record Ranking(int dimensions, List<Scored> scored) {
    }

    public static void main(String[] args) {
        String query = args.length > 0 ? String.join(" ", args) : "How does the JVM free memory?";

        EmbeddingModel model = Models.embedding();

        Ranking ranking = rank(model, query, CORPUS);

        System.out.println("dimensions: " + ranking.dimensions());
        System.out.println("query: " + query);
        System.out.println();
        ranking.scored().forEach(s -> System.out.printf("%.4f  %s%n", s.score(), s.text()));
    }

    /**
     * Every text in the corpus, scored against the query and sorted best first.
     *
     * One call for the whole corpus. Batching matters: per-text calls are the usual reason an ingest
     * job takes minutes instead of seconds.
     */
    static Ranking rank(EmbeddingModel model, String query, List<String> corpus) {
        List<Embedding> vectors = model.embedAll(corpus.stream().map(TextSegment::from).toList()).content();

        // A LangChain4j Embedding carries no index, so the order of embedAll's result is the only
        // thing tying a vector to its text. If a provider drops one - an empty string, one over the
        // token limit - every later text is labelled with its neighbour's score and nothing fails.
        // Refuse instead.
        if (vectors.size() != corpus.size()) {
            throw new IllegalStateException("asked for " + corpus.size() + " embeddings, got " + vectors.size());
        }

        // After the check: no point paying for the query embedding when the corpus is unusable.
        Embedding queryVector = model.embed(query).content();

        List<Scored> scored = IntStream.range(0, corpus.size())
                .mapToObj(i -> new Scored(corpus.get(i), CosineSimilarity.between(queryVector, vectors.get(i))))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();
        return new Ranking(queryVector.dimension(), scored);
    }
}
