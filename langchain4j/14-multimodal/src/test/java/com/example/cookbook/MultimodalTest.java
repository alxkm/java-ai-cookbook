package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultimodalTest {

    static class RecordingModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from("four bars")).build();
        }
    }

    @Test
    void sendsTextAndImageInOneUserMessage() throws IOException {
        RecordingModel model = new RecordingModel();

        UserMessage message = UserMessage.from(
                TextContent.from("Describe this chart."),
                ImageContent.from(base64(), "image/png"));

        assertThat(model.chat(message).aiMessage().text()).isEqualTo("four bars");

        UserMessage sent = (UserMessage) model.requests.get(0).messages().get(0);
        assertThat(sent.contents()).hasSize(2);
        assertThat(sent.contents().get(0)).isInstanceOf(TextContent.class);

        ImageContent image = (ImageContent) sent.contents().get(1);
        assertThat(image.image().mimeType()).isEqualTo("image/png");
        assertThat(image.image().base64Data()).isNotBlank();
    }

    private static String base64() throws IOException {
        try (InputStream in = MultimodalTest.class.getResourceAsStream("/images/chart.png")) {
            assertThat(in).isNotNull();
            return Base64.getEncoder().encodeToString(in.readAllBytes());
        }
    }
}
