package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Progressive tool disclosure, the LangChain4j way: the selection happens in code, before the call.
 *
 * This is the real difference from the Spring AI side of this recipe. There, the model is given a
 * tool for searching tools, notices it needs one, searches, and calls what it found - two round
 * trips, and the model decides. Here, ToolProvider is asked once per request what the model may
 * see, and the answer is computed from the user message - one round trip, and the code decides.
 *
 * Neither is strictly better. This one is cheaper and predictable, and it can only match on what
 * the user already said; the model cannot discover a tool mid-conversation that the first message
 * gave no hint about.
 */
final class KeywordToolProvider implements ToolProvider {

    private static final Pattern WORDS = Pattern.compile("[^a-z0-9]+");

    /** Words that appear in almost every description and so rank nothing. */
    private static final Set<String> NOISE = Set.of(
            "a", "an", "and", "the", "for", "of", "on", "to", "by", "it", "its", "in", "that",
            "with", "from", "is", "was", "has", "have", "return", "returns", "their", "this");

    private final Map<ToolSpecification, ToolExecutor> catalogue;
    private final int maxTools;

    KeywordToolProvider(Map<ToolSpecification, ToolExecutor> catalogue, int maxTools) {
        this.catalogue = catalogue;
        this.maxTools = maxTools;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Set<String> asked = terms(request.userMessage().singleText());

        List<Map.Entry<ToolSpecification, ToolExecutor>> ranked = catalogue.entrySet().stream()
                .map(entry -> Map.entry(entry, overlap(asked, entry.getKey())))
                .filter(scored -> scored.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Map.Entry<ToolSpecification, ToolExecutor>, Long>>comparingLong(
                        Map.Entry::getValue).reversed())
                .limit(maxTools)
                .map(Map.Entry::getKey)
                .toList();

        // Nothing matched: send nothing rather than everything. Falling back to the whole catalogue
        // is the tempting default and it undoes the entire point on exactly the requests where the
        // prompt is already longest.
        Map<ToolSpecification, ToolExecutor> selected = new LinkedHashMap<>();
        ranked.forEach(entry -> selected.put(entry.getKey(), entry.getValue()));
        return new ToolProviderResult(selected);
    }

    private static long overlap(Set<String> asked, ToolSpecification specification) {
        Set<String> tool = terms(specification.name() + " " + specification.description());
        return tool.stream().filter(asked::contains).count();
    }

    private static Set<String> terms(String text) {
        if (text == null) {
            return Set.of();
        }
        // camelCase tool names carry most of the signal, so split them before lowercasing.
        String split = text.replaceAll("([a-z])([A-Z])", "$1 $2");
        // Arrays.stream, not Set.of: a word that appears in both the tool name and its
        // description is a duplicate, and Set.of throws on those rather than collapsing them.
        return Arrays.stream(WORDS.split(split.toLowerCase(Locale.ROOT)))
                .filter(word -> word.length() > 2 && !NOISE.contains(word))
                .collect(Collectors.toUnmodifiableSet());
    }
}
