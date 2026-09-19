package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LLM-as-judge, written as a typed interface.
 *
 * LangChain4j has no built-in evaluator, which turns out to be fine: a judge is one prompt and one
 * return type. Keep the questions narrow - a judge asked to score "quality" will invent a scale,
 * a judge asked one yes/no question is reproducible.
 */
public interface Judge {

    record Verdict(boolean supported, String reason) {
    }

    @UserMessage("""
            Reference material:
            {{reference}}

            Question: {{question}}
            Answer: {{answer}}

            Is every statement in the answer supported by the reference material,
            and does it actually answer the question?
            Reply with supported=true or supported=false and a one sentence reason.
            """)
    Verdict judge(@V("reference") String reference, @V("question") String question, @V("answer") String answer);

    static Judge create(ChatModel model) {
        return AiServices.create(Judge.class, model);
    }
}
