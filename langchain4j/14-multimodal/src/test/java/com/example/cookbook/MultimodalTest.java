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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Goes through the recipe's own question(...) rather than rebuilding the message here. The old test
 * built its own UserMessage, so it would have kept passing if the recipe had built a wrong one.
 */
class MultimodalTest {

    static class RecordingModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from("four bars")).build();
        }
    }

    private static byte[] chart() throws IOException {
        try (InputStream in = MultimodalTest.class.getResourceAsStream("/images/chart.png")) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void sendsTextAndImageInOneUserMessage() throws IOException {
        RecordingModel model = new RecordingModel();

        assertThat(model.chat(Multimodal.question("Describe this chart.", "/images/chart.png"))
                .aiMessage().text()).isEqualTo("four bars");

        UserMessage sent = (UserMessage) model.requests.get(0).messages().get(0);
        assertThat(sent.contents()).hasSize(2);
        assertThat(sent.contents().get(0)).isInstanceOf(TextContent.class);
        assertThat(((TextContent) sent.contents().get(0)).text()).isEqualTo("Describe this chart.");
        assertThat(sent.contents().get(1)).isInstanceOf(ImageContent.class);
    }

    @Test
    void theImageArrivesByteForByte() throws IOException {
        ImageContent image = (ImageContent) Multimodal.question("q", "/images/chart.png").contents().get(1);

        // Worth asserting the decoded bytes rather than "not blank": a truncated read or a
        // double-encoding still produces a non-blank string, and the model then describes garbage.
        assertThat(Base64.getDecoder().decode(image.image().base64Data())).isEqualTo(chart());
    }

    @Test
    void theMimeTypeComesFromTheBytesNotTheFileName() throws IOException {
        ImageContent image = (ImageContent) Multimodal.question("q", "/images/chart.png").contents().get(1);
        assertThat(image.image().mimeType()).isEqualTo("image/png");
    }

    @Test
    void recognisesTheFormatsProvidersAccept() {
        assertThat(Multimodal.mimeType(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("image/jpeg");
        assertThat(Multimodal.mimeType(bytes('G', 'I', 'F', '8', '9', 'a'))).isEqualTo("image/gif");
        assertThat(Multimodal.mimeType(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')))
                .isEqualTo("image/webp");
    }

    @Test
    void refusesSomethingThatIsNotAnImage() {
        // A text file renamed to .png is the realistic case. Guessing a type would send it anyway.
        assertThatThrownBy(() -> Multimodal.mimeType("not an image".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Multimodal.mimeType(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingImageNamesTheResource() {
        assertThatThrownBy(() -> Multimodal.question("q", "/images/nope.png"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("/images/nope.png");
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
