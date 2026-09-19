package com.example.cookbook;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Locale;

/**
 * A deterministic stand-in for a real embedding model: one dimension per keyword, value is the
 * number of times the keyword appears. Good enough for cosine similarity, and it never leaves the JVM.
 */
class KeywordEmbeddingModel implements EmbeddingModel {

    private static final List<String> VOCABULARY =
            List.of("deploy", "tuesday", "approval", "payments", "oncall", "page", "sev", "refund");

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        return Response.from(segments.stream().map(segment -> Embedding.from(vector(segment.text()))).toList());
    }

    private float[] vector(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        float[] vector = new float[VOCABULARY.size()];
        for (int i = 0; i < VOCABULARY.size(); i++) {
            vector[i] = count(lower, VOCABULARY.get(i));
        }
        // avoid the all-zero vector, which has no defined cosine similarity
        vector[0] += 0.01f;
        return vector;
    }

    private int count(String text, String word) {
        int total = 0;
        int from = 0;
        while ((from = text.indexOf(word, from)) >= 0) {
            total++;
            from += word.length();
        }
        return total;
    }
}
