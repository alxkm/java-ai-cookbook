package com.example.cookbook;

import com.example.cookbook.StructuredOutputApplication.Recipe;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputTest {

    @Test
    void parsesJsonIntoRecordAndSendsSchema() {
        AtomicReference<Prompt> captured = new AtomicReference<>();

        ChatModel stub = prompt -> {
            captured.set(prompt);
            String json = """
                    {"title":"Mushroom risotto","ingredients":["rice","mushrooms"],
                     "prepMinutes":35,"vegetarian":true}
                    """;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        };

        Recipe recipe = ChatClient.builder(stub)
                .build()
                .prompt()
                .user("Give me a recipe for mushroom risotto.")
                .call()
                .entity(Recipe.class);

        assertThat(recipe.title()).isEqualTo("Mushroom risotto");
        assertThat(recipe.prepMinutes()).isEqualTo(35);
        assertThat(recipe.vegetarian()).isTrue();

        // The converter appends the schema to the user message - that is what makes the model comply.
        String sent = captured.get().getInstructions().get(0).getText();
        assertThat(sent).contains("prepMinutes");
    }
}
