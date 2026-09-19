package com.example.cookbook;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

public class ChatBasic {

    public static void main(String[] args) {
        String question = args.length > 0
                ? String.join(" ", args)
                : "Why does Java still not have value types in the language?";

        ChatModel model = Models.chat();

        // ChatModel is the low-level API: messages in, response out, no state kept between calls.
        ChatResponse response = model.chat(
                SystemMessage.from("You are a terse Java assistant. Answer in at most three sentences."),
                UserMessage.from(question));

        System.out.println("> " + question);
        System.out.println(response.aiMessage().text());
        System.out.printf("%n[%s, %d tokens]%n", Models.provider(), response.tokenUsage().totalTokenCount());
    }
}
