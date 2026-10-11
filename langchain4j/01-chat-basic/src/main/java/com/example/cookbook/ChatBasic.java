package com.example.cookbook;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public class ChatBasic {

    static final String SYSTEM_PROMPT = "You are a terse Java assistant. Answer in at most three sentences.";

    public static void main(String[] args) {
        String question = args.length > 0
                ? String.join(" ", args)
                : "Why does Java still not have value types in the language?";

        ChatResponse response = ask(Models.chat(), question);

        System.out.println("> " + question);
        System.out.println(response.aiMessage().text());
        System.out.printf("%n[%s, %s]%n", Models.provider(), usage(response));
    }

    /** ChatModel is the low-level API: messages in, response out, no state kept between calls. */
    static ChatResponse ask(ChatModel model, String question) {
        return model.chat(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(question));
    }

    /**
     * Token usage is reported by the provider, not computed here, and not every provider reports it -
     * an OpenAI-compatible local server often leaves it out. Read unguarded, a missing figure turns an
     * answer that has already been printed into a NullPointerException.
     */
    static String usage(ChatResponse response) {
        TokenUsage usage = response.tokenUsage();
        if (usage == null || usage.totalTokenCount() == null) {
            return "usage not reported";
        }
        return usage.totalTokenCount() + " tokens";
    }
}
