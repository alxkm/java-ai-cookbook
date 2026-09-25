package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Goes through the recipe's own describe(...) rather than rebuilding the prompt here. The old test
 * built its own request, so it would have kept passing if the recipe had built a wrong one.
 */
class MultimodalTest {

    private final AtomicReference<Prompt> captured = new AtomicReference<>();

    private ChatClient client() {
        ChatModel stub = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("four bars"))));
        };
        return ChatClient.builder(stub).build();
    }

    @Test
    void sendsTextAndImageInOneUserMessage() throws Exception {
        String answer = MultimodalApplication.describe(client(), "Describe this chart.",
                new ClassPathResource("/images/chart.png"));

        assertThat(answer).isEqualTo("four bars");

        UserMessage sent = (UserMessage) captured.get().getInstructions().get(0);
        assertThat(sent.getText()).isEqualTo("Describe this chart.");
        assertThat(sent.getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG));
    }

    @Test
    void theMimeTypeFollowsTheBytesNotTheFileName() throws Exception {
        // A JPEG saved under a .png name - the realistic mistake. The declared type follows what
        // the file actually is.
        ByteArrayResource jpeg = new ByteArrayResource(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1)) {
            @Override
            public String getFilename() {
                return "chart.png";
            }
        };

        MultimodalApplication.describe(client(), "Describe this.", jpeg);

        UserMessage sent = (UserMessage) captured.get().getInstructions().get(0);
        assertThat(sent.getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_JPEG));
    }

    @Test
    void recognisesTheFormatsProvidersAccept() {
        assertThat(ImageTypes.of(bytes('G', 'I', 'F', '8', '9', 'a'))).isEqualTo(MimeTypeUtils.IMAGE_GIF);
        assertThat(ImageTypes.of(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')))
                .isEqualTo(MimeType.valueOf("image/webp"));
    }

    @Test
    void refusesSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> ImageTypes.of("not an image".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageTypes.of(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
