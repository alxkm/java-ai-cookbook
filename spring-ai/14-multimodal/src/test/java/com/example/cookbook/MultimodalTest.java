package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MultimodalTest {

    @Test
    void sendsTextAndImageInOneUserMessage() {
        AtomicReference<Prompt> captured = new AtomicReference<>();

        ChatModel stub = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("four bars"))));
        };

        String answer = ChatClient.builder(stub)
                .build()
                .prompt()
                .user(user -> user
                        .text("Describe this chart.")
                        .media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/images/chart.png")))
                .call()
                .content();

        assertThat(answer).isEqualTo("four bars");

        UserMessage sent = (UserMessage) captured.get().getInstructions().get(0);
        assertThat(sent.getText()).isEqualTo("Describe this chart.");
        assertThat(sent.getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG));
    }
}
