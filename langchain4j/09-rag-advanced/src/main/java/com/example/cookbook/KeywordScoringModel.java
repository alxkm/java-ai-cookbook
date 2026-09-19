package com.example.cookbook;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A re-ranker with no model behind it: score by how many query terms a chunk actually contains.
 *
 * Embedding similarity is good at topic, weak at specifics - it will happily rank a chunk about
 * deployments above the one that mentions the exact term you asked for. A cheap lexical pass on
 * top of the vector search fixes a surprising share of that. Swap in a cross-encoder or a hosted
 * re-rank endpoint when you outgrow it; `ScoringModel` stays the same.
 */
public class KeywordScoringModel implements ScoringModel {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "do", "does", "how", "what", "when", "who", "i", "to", "of", "in", "on");

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        Set<String> queryTerms = terms(query);

        List<Double> scores = segments.stream()
                .map(segment -> overlap(queryTerms, segment.text()))
                .toList();

        return Response.from(scores);
    }

    private double overlap(Set<String> queryTerms, String text) {
        if (queryTerms.isEmpty()) {
            return 0;
        }
        Set<String> textTerms = terms(text);
        long hits = queryTerms.stream().filter(textTerms::contains).count();
        return (double) hits / queryTerms.size();
    }

    private Set<String> terms(String text) {
        if (text == null) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 2 && !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }
}
