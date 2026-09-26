package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Goes through the recipe's own rank(...). The earlier tests covered only the cosine function, so
 * the parts this recipe is actually about - one batch call, and pairing each vector with the text it
 * came from - were never exercised.
 */
class RankTest {

    private static final List<String> CORPUS = List.of(
            "The garbage collector reclaims memory that is no longer reachable.",
            "Virtual threads make blocking calls cheap on the JVM.",
            "Espresso is brewed by forcing hot water through finely ground coffee.");

    /** One dimension per keyword. Deterministic, and it counts the requests it receives. */
    static class KeywordModel implements EmbeddingModel {

        private static final List<String> WORDS = List.of("memory", "collector", "threads", "coffee", "espresso");

        final List<List<String>> requests = new ArrayList<>();
        private final UnaryOperator<List<Embedding>> tamper;

        KeywordModel(UnaryOperator<List<Embedding>> tamper) {
            this.tamper = tamper;
        }

        KeywordModel() {
            this(results -> results);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            requests.add(request.getInstructions());
            List<Embedding> results = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                results.add(new Embedding(vector(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(tamper.apply(results));
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
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
        List<EmbeddingsApplication.Scored> ranked =
                EmbeddingsApplication.rank(new KeywordModel(), "How does the collector free memory?", CORPUS).scored();

        assertThat(ranked.get(0).text()).contains("garbage collector");
        assertThat(ranked).extracting(EmbeddingsApplication.Scored::score).isSortedAccordingTo(Collections.reverseOrder());
    }

    @Test
    void theWholeCorpusIsEmbeddedInOneRequest() {
        KeywordModel model = new KeywordModel();

        EmbeddingsApplication.rank(model, "memory", CORPUS);

        // The recipe's claim about batching, asserted: the corpus goes in one request, and the
        // query in a second. A loop of per-text calls would show up here as four requests.
        assertThat(model.requests).hasSize(2);
        assertThat(model.requests.get(0)).containsExactlyElementsOf(CORPUS);
    }

    @Test
    void resultsArePairedByTheirIndexNotTheirPosition() {
        // The same results, returned in reverse order but still carrying the right index. Paired by
        // position, the espresso sentence would be labelled with the garbage-collector score.
        KeywordModel shuffled = new KeywordModel(results -> {
            List<Embedding> reversed = new ArrayList<>(results);
            Collections.reverse(reversed);
            return reversed;
        });

        List<EmbeddingsApplication.Scored> ranked =
                EmbeddingsApplication.rank(shuffled, "How does the collector free memory?", CORPUS).scored();

        assertThat(ranked.get(0).text()).contains("garbage collector");
    }

    @Test
    void aProviderThatDropsATextIsRefusedRatherThanMisaligned() {
        // Drops the last text of the batch, and only of the batch - the query call is left intact so
        // the test fails on the thing it is about.
        KeywordModel lossy = new KeywordModel(results ->
                results.size() > 1 ? results.subList(0, results.size() - 1) : results);

        assertThatThrownBy(() -> EmbeddingsApplication.rank(lossy, "memory", CORPUS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asked for 3 embeddings, got 2");
    }

    @Test
    void theDimensionComesFromTheQueryVectorWithoutAnExtraCall() {
        KeywordModel model = new KeywordModel();

        EmbeddingsApplication.Ranking ranking = EmbeddingsApplication.rank(model, "memory", CORPUS);

        // Six keyword dimensions, and still exactly two model calls: the corpus and the query.
        // Calling the model's own dimension method would have made it three.
        assertThat(ranking.dimensions()).isEqualTo(6);
        assertThat(model.requests).hasSize(2);
    }
}
