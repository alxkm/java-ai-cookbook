package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No model here. What is worth testing about progressive disclosure is the disclosure: given a
 * question, does the shortlist contain the tool that can answer it, and does it leave out the
 * seventeen that cannot.
 */
class KeywordToolProviderTest {

    private static final Map<ToolSpecification, ToolExecutor> CATALOGUE =
            ToolSearch.catalogue(new ToolCatalog());

    private static List<String> selected(String question, int maxTools) {
        ToolProviderResult result = new KeywordToolProvider(CATALOGUE, maxTools)
                .provideTools(new ToolProviderRequest("conversation-1", UserMessage.from(question)));
        return result.tools().keySet().stream().map(ToolSpecification::name).toList();
    }

    @Test
    void theWholeCatalogueIsAvailableToChooseFrom() {
        assertThat(CATALOGUE).hasSize(20);
    }

    @Test
    void aRefundQuestionSelectsTheRefundTool() {
        assertThat(selected("the customer wants a refund for their order", 3)).contains("refundOrder");
    }

    @Test
    void aShortlistIsThreeToolsRatherThanTwenty() {
        List<String> shortlist = selected("where is my parcel, can you track the delivery", 3);

        assertThat(shortlist).hasSizeLessThanOrEqualTo(3);
        assertThat(shortlist).contains("trackParcel");
        assertThat(shortlist).doesNotContain("refundOrder", "chargeBalance", "stockLevel");
    }

    @Test
    void differentDomainsSelectDifferentTools() {
        assertThat(selected("how many units of this product are in stock", 3)).contains("stockLevel");
        assertThat(selected("cancel this order before it ships", 3)).contains("cancelOrder");
        assertThat(selected("print a return shipping label", 3)).contains("returnLabel");
    }

    @Test
    void nothingMatchedMeansNothingSentRatherThanEverything() {
        // The tempting fallback is the whole catalogue, and it undoes the recipe on exactly the
        // requests where the prompt is already longest. An empty result is the honest answer: the
        // model then says it cannot help instead of picking a tool at random from twenty.
        assertThat(selected("what is the weather like in Leipzig today", 3)).isEmpty();
    }

    @Test
    void theCapIsRespected() {
        assertThat(selected("order invoice refund stock parcel delivery", 2)).hasSizeLessThanOrEqualTo(2);
        assertThat(selected("order invoice refund stock parcel delivery", 5)).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void executorsComeBackAlongsideTheSpecifications() {
        ToolProviderResult result = new KeywordToolProvider(CATALOGUE, 3)
                .provideTools(new ToolProviderRequest("c", UserMessage.from("issue a refund")));

        // A provider that returns specifications without executors compiles, advertises the tools,
        // and then fails at call time - which is a long way from where the mistake was made.
        assertThat(result.tools()).isNotEmpty();
        assertThat(result.toolExecutorByName("refundOrder")).isNotNull();
    }

    @Test
    void toolNamesAreMatchedNotJustDescriptions() {
        // camelCase names carry most of the signal, so they are split before matching. Without that
        // "stock level" in a question would miss stockLevel entirely.
        assertThat(selected("stock level please", 3)).contains("stockLevel");
    }
    @Test
    void aSynonymIsAMiss() {
        // The refund tool is right there in the catalogue and these questions do not find it,
        // because term overlap has no idea that "money back" means refund or that a package is a
        // parcel. Nothing errors - the model is simply handed nothing and says it cannot help.
        assertThat(selected("give me my money back please", 3)).isEmpty();
        assertThat(selected("where has my package got to", 3)).isEmpty();

        // Worse than empty: a near miss picks a plausible wrong tool. "customer" is enough to
        // rank the customer-orders lookup, and the refund tool is still absent.
        assertThat(selected("the customer wants their money back", 3))
                .containsExactly("ordersForCustomer");

        // Say it the way the tools say it and it is found. This is the real trade against the
        // Spring AI side of this recipe: there the model writes the search query, so it can look
        // for "refund" when the user said "money back". Here the match is against the user's
        // literal words. Embeddings are the usual answer.
        assertThat(selected("issue a refund for order A-1001", 3)).contains("refundOrder");
    }
}
