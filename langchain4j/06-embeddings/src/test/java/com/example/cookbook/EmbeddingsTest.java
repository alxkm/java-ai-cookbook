package com.example.cookbook;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingsTest {

    @Test
    void identicalVectorsScoreOne() {
        Embedding v = Embedding.from(new float[]{0.1f, 0.9f, 0.4f});
        assertThat(CosineSimilarity.between(v, v)).isCloseTo(1.0, Offset.offset(1e-6));
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertThat(CosineSimilarity.between(
                Embedding.from(new float[]{1, 0}),
                Embedding.from(new float[]{0, 1})))
                .isCloseTo(0.0, Offset.offset(1e-6));
    }

    @Test
    void closerMeaningScoresHigher() {
        Embedding query = Embedding.from(new float[]{1.0f, 0.1f});
        Embedding related = Embedding.from(new float[]{0.9f, 0.2f});
        Embedding unrelated = Embedding.from(new float[]{0.1f, 1.0f});

        assertThat(CosineSimilarity.between(query, related))
                .isGreaterThan(CosineSimilarity.between(query, unrelated));
    }
}
