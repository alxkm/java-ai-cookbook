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

        // Text and image are two contents of one user message. The model sees a single message.
        UserMessage message = UserMessage.from(
                TextContent.from(question),
                ImageContent.from(base64("/images/chart.png"), "image/png"));

        System.out.println("> " + question);
        System.out.println(model.chat(message).aiMessage().text());
    }

    private static String base64(String resource) throws IOException {
        try (InputStream in = Multimodal.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resource);
            }
            return Base64.getEncoder().encodeToString(in.readAllBytes());
        }
    }
}
