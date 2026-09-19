package com.example.cookbook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimilarityTest {

    @Test
    void identicalVectorsScoreOne() {
        float[] v = {0.1f, 0.9f, 0.4f};
        assertThat(Similarity.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertThat(Similarity.cosine(new float[]{1, 0}, new float[]{0, 1}))
                .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void closerMeaningScoresHigher() {
        float[] query = {1.0f, 0.1f};
        float[] related = {0.9f, 0.2f};
        float[] unrelated = {0.1f, 1.0f};

        assertThat(Similarity.cosine(query, related)).isGreaterThan(Similarity.cosine(query, unrelated));
    }

    @Test
    void refusesMismatchedDimensions() {
        assertThatThrownBy(() -> Similarity.cosine(new float[]{1, 2}, new float[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different dimensions");
    }
}
