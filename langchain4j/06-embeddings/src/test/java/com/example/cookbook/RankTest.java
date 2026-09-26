package com.example.cookbook;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Goes through the recipe's own rank(...). The earlier tests covered only CosineSimilarity, which is
 * the library's code, so the parts this recipe is actually about - one batch call, and pairing each
 * vector with the text it came from - were never exercised.
 */
class RankTest {

    private static final List<String> CORPUS = List.of(
            "The garbage collector reclaims memory that is no longer reachable.",
            "Virtual threads make blocking calls cheap on the JVM.",
            "Espresso is brewed by forcing hot water through finely ground coffee.");

    /** One dimension per keyword. Deterministic, and it counts the batches it receives. */
    static class KeywordModel implements EmbeddingModel {

        private static final List<String> WORDS = List.of("memory", "collector", "threads", "coffee", "espresso");

        final List<Integer> batchSizes = new ArrayList<>();
        private final boolean dropLastOfBatch;

        KeywordModel(boolean dropLastOfBatch) {
            this.dropLastOfBatch = dropLastOfBatch;
        }

        KeywordModel() {
            this(false);
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            batchSizes.add(segments.size());
            List<Embedding> out = new ArrayList<>();
            for (TextSegment segment : segments) {
                out.add(Embedding.from(vector(segment.text())));
            }
            if (dropLastOfBatch && out.size() > 1) {
                out.remove(out.size() - 1);
            }
            return Response.from(out);
        }

        @Override
        public int dimension() {
            return WORDS.size() + 1;
        }

        private static float[] vector(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            float[] v = new float[WORDS.size() + 1];
            for (int i = 0; i < WORDS.size(); i++) {
                v[i] = lower.contains(WORDS.get(i)) ? 1 : 0;
            }
            v[WORDS.size()] = 0.01f;
            return v;
        }
    }

    @Test
    void theClosestTextComesFirst() {
        List<Embeddings.Scored> ranked = Embeddings.rank(new KeywordModel(), "How does the collector free memory?", CORPUS).scored();

        assertThat(ranked.get(0).text()).contains("garbage collector");
        assertThat(ranked).extracting(Embeddings.Scored::score).isSortedAccordingTo(Collections.reverseOrder());
    }

    @Test
    void theWholeCorpusIsEmbeddedInOneBatch() {
        KeywordModel model = new KeywordModel();

        Embeddings.rank(model, "memory", CORPUS);

        // The recipe's claim about batching, asserted: one batch for the corpus, one for the query.
        // A loop of per-text calls would show up here as four batches of one.
        assertThat(model.batchSizes).containsExactly(3, 1);
    }

    @Test
    void aProviderThatDropsATextIsRefusedRatherThanMisaligned() {
        assertThatThrownBy(() -> Embeddings.rank(new KeywordModel(true), "memory", CORPUS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asked for 3 embeddings, got 2");
    }

    @Test
    void everyTextIsScoredExactlyOnce() {
        assertThat(Embeddings.rank(new KeywordModel(), "memory", CORPUS).scored())
                .extracting(Embeddings.Scored::text)
                .containsExactlyInAnyOrderElementsOf(CORPUS);
    }

    @Test
    void theDimensionComesFromTheQueryVectorWithoutAnExtraCall() {
        KeywordModel model = new KeywordModel();

        Embeddings.Ranking ranking = Embeddings.rank(model, "memory", CORPUS);

        // Six keyword dimensions, and still exactly two model calls: the corpus and the query.
        // Calling the model's own dimension method would have made it three.
        assertThat(ranking.dimensions()).isEqualTo(6);
        assertThat(model.batchSizes).hasSize(2);
    }
}
