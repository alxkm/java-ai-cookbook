package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 20 - Progressive tool disclosure (LangChain4j).
 *
 * Twenty tools are registered and at most three reach the model on any turn.
 */
public final class ToolSearch {

    interface SupportAgent {
        String chat(String question);
    }

    private ToolSearch() {
    }

    /** The catalogue, as the pairs of specification and executor a ToolProvider has to return. */
    static Map<ToolSpecification, ToolExecutor> catalogue(Object tools) {
        Map<ToolSpecification, ToolExecutor> catalogue = new LinkedHashMap<>();
        for (Method method : tools.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                catalogue.put(ToolSpecifications.toolSpecificationFrom(method),
                        new DefaultToolExecutor(tools, method));
            }
        }
        return catalogue;
    }

    public static void main(String[] args) {
        String question = args.length > 0
                ? String.join(" ", args)
                // Phrased in the words the tools use. "wants their money back" matches nothing,
                // which is the limitation this recipe is honest about - see the README.
                : "Issue a refund for order A-1001.";

        Map<ToolSpecification, ToolExecutor> catalogue = catalogue(new ToolCatalog());

        SupportAgent agent = AiServices.builder(SupportAgent.class)
                .chatModel(Models.chat())
                // toolProvider, not tools: the list is computed per request rather than fixed at
                // build time, and that is the whole mechanism.
                .toolProvider(new KeywordToolProvider(catalogue, 3))
                .build();

        System.out.println(catalogue.size() + " tools registered, at most 3 reach the model per turn");
        System.out.println("> " + question);
        System.out.println(agent.chat(question));
    }
}
