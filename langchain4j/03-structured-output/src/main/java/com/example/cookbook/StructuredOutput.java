package com.example.cookbook;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

/**
 * AiServices turns an interface into a typed call: the return type drives the JSON schema,
 * and the parsing back into Java happens for you.
 */
public class StructuredOutput {

    public record Recipe(String title, List<String> ingredients, int prepMinutes, boolean vegetarian) {
    }

    /**
     * A list has to be wrapped in a record rather than returned directly.
     *
     * Returning {@code List<Recipe>} works against a provider with native JSON schema support and
     * throws IllegalStateException against one without it - Ollama, for instance - because there is
     * no prompt-only format instruction for a collection of objects. Wrapping costs one record and
     * behaves the same everywhere.
     */
    public record Variants(List<Recipe> recipes) {
    }

    interface Chef {

        @UserMessage("Give me a recipe for {{dish}}.")
        Recipe suggest(@V("dish") String dish);

        @UserMessage("Give me two quick variants of {{dish}}.")
        Variants variants(@V("dish") String dish);
    }

    public static void main(String[] args) {
        String dish = args.length > 0 ? String.join(" ", args) : "mushroom risotto";

        Chef chef = AiServices.create(Chef.class, Models.chat());

        System.out.println(chef.suggest(dish));

        try {
            chef.variants(dish).recipes().forEach(System.out::println);
        } catch (RuntimeException parsingFailed) {
            // Worth seeing rather than hiding: a model without native JSON schema support gets a
            // list of objects wrong often enough to matter. Against OpenAI this never fires;
            // against llama3.2 it fires regularly, usually by dropping the closing brace.
            // Recipe 18 shows the retry-and-salvage pattern that makes this survivable.
            System.out.println("variants() could not be parsed: " + parsingFailed.getMessage());
        }
    }
}
