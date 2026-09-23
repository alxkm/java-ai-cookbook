package com.example.cookbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No model here. What is worth testing about progressive disclosure is the disclosure: given a
 * question, does the shortlist contain the tool that can answer it, and does it leave out the
 * nineteen that cannot.
 */
class ToolIndexTest {

    private static final String SESSION = "test-session";

    private LuceneToolIndex index;

    @BeforeEach
    void indexTheCatalogue() {
        index = new LuceneToolIndex();
        index.indexTools(SESSION, references());
    }

    private static List<ToolReference> references() {
        return Arrays.stream(ToolCallbacks.from(new ToolCatalog()))
                .map(callback -> ToolReference.builder()
                        .toolName(callback.getToolDefinition().name())
                        .summary(callback.getToolDefinition().description())
                        .build())
                .toList();
    }

    private List<String> search(String query, int maxResults) {
        ToolSearchResponse response = index.search(new ToolSearchRequest(SESSION, query, maxResults, null));
        return response.toolReferences().stream().map(ToolReference::toolName).toList();
    }

    @Test
    void theWholeCatalogueIsIndexed() {
        assertThat(index.size(SESSION)).isEqualTo(20);
    }

    @Test
    void aRefundQuestionFindsTheRefundTool() {
        assertThat(search("the customer wants a refund for their order", 3)).contains("refundOrder");
    }

    @Test
    void aShortlistIsThreeToolsRatherThanTwenty() {
        List<String> shortlist = search("issue a refund for the invoice", 3);

        // The point of the whole exercise: the model is shown three tools, and the seventeen it
        // does not need never become prompt text.
        assertThat(shortlist).hasSize(3);
        assertThat(shortlist).containsExactlyInAnyOrder("invoice", "refundOrder", "applyDiscount");
    }

    @Test
    void aCommonVerbDragsInAToolFromAnotherDomain() {
        // "check" is in the description of stockLevel ("Check how many units..."), so a billing
        // question phrased with it spends one of three slots on inventory. This is not a bug to
        // work around, it is what BM25 does - and it is the argument for raising
        // min-score-threshold, or for the vector index when descriptions share their verbs.
        assertThat(search("check the invoice", 3)).contains("stockLevel");

        // Drop the shared verb and the same question retrieves only billing tools.
        assertThat(search("issue a refund for the invoice", 3)).doesNotContain("stockLevel");
    }

    @Test
    void aPreciseQueryReturnsOnlyWhatScoresWellEnough() {
        // maxResults is a ceiling, not a quota. Nothing else clears min-score-threshold here, so
        // the model is shown one tool even though it asked for up to three.
        assertThat(search("refund", 3)).containsExactly("refundOrder");
    }

    @Test
    void differentDomainsRetrieveDifferentTools() {
        assertThat(search("where is my parcel right now", 3)).contains("trackParcel");
        assertThat(search("how many units are left in stock", 3)).contains("stockLevel");
        assertThat(search("cancel this order before it ships", 3)).contains("cancelOrder");
    }

    @Test
    void maxResultsBoundsWhatReachesTheModel() {
        assertThat(search("order", 1)).hasSize(1);
        assertThat(search("order", 5)).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void theIndexIsKeyedBySessionNotShared() {
        ToolSearchResponse otherSession =
                index.search(new ToolSearchRequest("nobody-indexed-this", "refund", 3, null));

        // Worth asserting because it is the shape of the failure: a session with nothing indexed
        // does not fall back to another session's tools, it finds nothing at all - and the model is
        // then told there are no tools rather than told to search again.
        assertThat(otherSession.toolReferences()).isEmpty();
        assertThat(index.size("nobody-indexed-this")).isZero();
    }

    @Test
    void clearingOneSessionLeavesTheOthers() {
        index.indexTools("second", references());
        assertThat(index.size("second")).isEqualTo(20);

        index.clearIndex("second");

        assertThat(index.size("second")).isZero();
        assertThat(index.size(SESSION)).isEqualTo(20);
    }

    @Test
    void searchingAnEmptyIndexIsEmptyRatherThanEverything() {
        ToolIndex empty = new LuceneToolIndex();

        // The failure mode this guards against is the opposite default: an index that returns the
        // whole catalogue when it has nothing to match, which quietly undoes the recipe.
        assertThat(empty.search(new ToolSearchRequest(SESSION, "refund", 3, null)).toolReferences()).isEmpty();
    }
}
