package com.example.cookbook;

import com.example.cookbook.StructuredOutput.Recipe;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputTest {

    interface Chef {
        Recipe suggest(String dish);

        StructuredOutput.Variants variants(String dish);
    }

    @Test
    void parsesJsonIntoRecord() {
        StubChatModel model = new StubChatModel("""
                {"title":"Mushroom risotto","ingredients":["rice","mushrooms"],
                 "prepMinutes":35,"vegetarian":true}
                """);

        Recipe recipe = AiServices.create(Chef.class, model).suggest("mushroom risotto");

        assertThat(recipe.title()).isEqualTo("Mushroom risotto");
        assertThat(recipe.prepMinutes()).isEqualTo(35);
        assertThat(recipe.vegetarian()).isTrue();
        // Without native schema support the field names are pushed into the prompt instead.
        assertThat(model.requests.get(0).messages().toString()).contains("prepMinutes");
    }

    /**
     * A stub model advertises no JSON schema support, which is exactly the case that used to blow
     * up: returning List<Recipe> straight from the interface throws IllegalStateException, because
     * there is no prompt-only format instruction for a collection of objects. The wrapper record
     * is what makes it work on every provider, and this test is what keeps it that way.
     */
    @Test
    void parsesAWrappedListWithoutNativeSchemaSupport() {
        StubChatModel model = new StubChatModel("""
                {"recipes":[
                  {"title":"Classic","ingredients":["rice"],"prepMinutes":30,"vegetarian":false},
                  {"title":"Spinach","ingredients":["rice","spinach"],"prepMinutes":20,"vegetarian":true}
                ]}
                """);

        StructuredOutput.Variants variants = AiServices.create(Chef.class, model).variants("risotto");

        assertThat(variants.recipes()).hasSize(2);
        assertThat(variants.recipes().get(1).vegetarian()).isTrue();
    }
}
