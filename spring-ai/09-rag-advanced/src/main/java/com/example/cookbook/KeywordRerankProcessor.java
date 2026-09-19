package com.example.cookbook;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A re-ranker with no model behind it: score by how many query terms a chunk actually contains,
 * then keep the top few.
 *
 * Embedding similarity is good at topic, weak at specifics - it will happily rank a chunk about
 * deployments above the one that mentions the exact term you asked for. A cheap lexical pass on
 * top of the vector search fixes a surprising share of that. Swap in a cross-encoder or a hosted
 * re-rank endpoint when you outgrow it; the interface stays the same.
 */
class KeywordRerankProcessor implements DocumentPostProcessor {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "do", "does", "how", "what", "when", "who", "i", "to", "of", "in", "on");

    private final int keep;

    KeywordRerankProcessor(int keep) {
        this.keep = keep;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        Set<String> terms = terms(query.text());

        return documents.stream()
                .sorted(Comparator.comparingLong((Document doc) -> overlap(terms, doc)).reversed())
                .limit(keep)
                .toList();
    }

    private long overlap(Set<String> terms, Document document) {
        Set<String> documentTerms = terms(document.getText());
        return terms.stream().filter(documentTerms::contains).count();
    }

    private Set<String> terms(String text) {
        if (text == null) {
            return Set.of();
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 2 && !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }
}
