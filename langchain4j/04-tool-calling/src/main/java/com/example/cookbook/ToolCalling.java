package com.example.cookbook;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

public class ToolCalling {

    interface SupportAgent {

        @SystemMessage("You are a support agent. Use the tools instead of guessing.")
        String answer(String question);
    }

    public static void main(String[] args) {
        String question = args.length > 0
                ? String.join(" ", args)
                : "Where is order A-1002 and when will it arrive?";

        // AiServices runs the loop: model asks for a tool, the tool runs, the result goes back,
        // repeat until the model answers in plain text.
        SupportAgent agent = AiServices.builder(SupportAgent.class)
                .chatModel(Models.chat())
                .tools(new OrderTools())
                .build();

        System.out.println("> " + question);
        System.out.println(agent.answer(question));
    }
}
