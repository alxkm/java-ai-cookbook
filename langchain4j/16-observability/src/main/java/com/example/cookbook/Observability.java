package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Comparator;
import java.util.List;

public class Observability {

    public static void main(String[] args) {
        MeterRegistry registry = new SimpleMeterRegistry();

        // Listeners are configured on the model, not on the call - every call through this model
        // is measured, including the ones made deep inside an AiService.
        ChatModel model = "openai".equals(Models.provider())
                ? OpenAiChatModel.builder()
                        .apiKey(Models.require("OPENAI_API_KEY"))
                        .modelName("gpt-4o-mini")
                        .listeners(List.of(new MetricsListener(registry)))
                        .build()
                : Models.chat();

        List<String> questions = args.length > 0
                ? List.of(String.join(" ", args))
                : List.of("What is a virtual thread?",
                        "What is a record?",
                        "What is a sealed interface?");

        for (String question : questions) {
            System.out.println("> " + question);
            System.out.println(model.chat(question));
        }

        System.out.println();
        System.out.println("--- meters ---");
        registry.getMeters().stream()
                .sorted(Comparator.comparing(meter -> meter.getId().getName()))
                .forEach(meter -> System.out.printf("%-28s %-40s %s%n",
                        meter.getId().getName(), meter.getId().getTags(), meter.measure()));
    }
}
