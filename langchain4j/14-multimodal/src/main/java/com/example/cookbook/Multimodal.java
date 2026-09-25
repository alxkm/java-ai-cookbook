package com.example.cookbook;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public class Multimodal {

    public static void main(String[] args) throws IOException {
        String question = args.length > 0
                ? String.join(" ", args)
                : "Describe this chart. How many bars are there, and which one is tallest?";

        ChatModel model = Models.chat();

        System.out.println("> " + question);
        System.out.println(model.chat(question(question, "/images/chart.png")).aiMessage().text());
    }

    /**
     * Text and image are two contents of one user message. The model sees a single message, which
     * is what lets the question refer to "this chart" at all.
     */
    static UserMessage question(String text, String imageResource) throws IOException {
        byte[] image = read(imageResource);
        return UserMessage.from(
                TextContent.from(text),
                ImageContent.from(Base64.getEncoder().encodeToString(image), mimeType(image)));
    }

    /**
     * Read off the file's first bytes rather than assumed from its name.
     *
     * The mime type travels with the image and the provider takes it at its word. Hardcoding
     * "image/png" means the first JPEG someone drops in is sent labelled as a PNG, and the failure
     * then surfaces at the provider, a long way from where the file was chosen.
     */
    static String mimeType(byte[] bytes) {
        if (startsWith(bytes, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (startsWith(bytes, 'R', 'I', 'F', 'F') && bytes.length >= 12
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        // Refuse rather than guess. A clear error here beats a confident wrong description later.
        throw new IllegalArgumentException("not a PNG, JPEG, GIF or WebP image");
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] read(String resource) throws IOException {
        try (InputStream in = Multimodal.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resource);
            }
            return in.readAllBytes();
        }
    }
}
