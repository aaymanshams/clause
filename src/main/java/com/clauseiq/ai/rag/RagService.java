package com.clauseiq.ai.rag;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.Prompts;
import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.rag.ChatDtos.ChatResponse;
import com.clauseiq.ai.rag.ChatDtos.Source;
import com.clauseiq.search.SemanticSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retrieval-augmented question answering:
 * question -> embedding -> tenant-filtered vector search -> top-K chunks -> grounded prompt -> LLM
 * -> answer + citations.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /** Matches "[S1]" and grouped forms such as "[S1, S3]". */
    private static final Pattern CITATION_GROUP = Pattern.compile("\\[\\s*(S\\d{1,3}(?:\\s*,\\s*S\\d{1,3})*)\\s*]");
    private static final int EXCERPT_CHARS = 280;

    private final SemanticSearchService searchService;
    private final AiService aiService;

    public RagService(SemanticSearchService searchService, AiService aiService) {
        this.searchService = searchService;
        this.aiService = aiService;
    }

    public ChatResponse ask(Long tenantId, String question, Long contractId) {
        List<RetrievedChunk> context = searchService.search(tenantId, question, contractId, null);
        if (context.isEmpty()) {
            // Nothing relevant retrieved: answer without calling the LLM, so it cannot hallucinate.
            return new ChatResponse(Prompts.NO_ANSWER, false, List.of(), aiService.providerName());
        }
        String answer = aiService.generateAnswer(question, context);
        if (answer == null || answer.isBlank() || answer.contains(Prompts.NO_ANSWER)) {
            return new ChatResponse(Prompts.NO_ANSWER, false, List.of(), aiService.providerName());
        }
        Optional<Set<Integer>> cited = validCitations(answer, context.size());
        if (cited.isEmpty()) {
            // An answer we cannot trace to the retrieved excerpts is treated as ungrounded.
            log.warn("Discarding answer with missing or out-of-range citations ({} excerpts retrieved)", context.size());
            return new ChatResponse(Prompts.NO_ANSWER, false, List.of(), aiService.providerName());
        }
        return new ChatResponse(answer, true, toSources(cited.get(), context), aiService.providerName());
    }

    /**
     * Returns the cited excerpt numbers, or empty if the answer cites nothing or cites an excerpt that
     * was never provided (a fabricated citation). Sources are therefore always a subset of what was
     * actually retrieved for this tenant and shown to the model.
     */
    static Optional<Set<Integer>> validCitations(String answer, int contextSize) {
        Set<Integer> cited = new LinkedHashSet<>();
        Matcher m = CITATION_GROUP.matcher(answer);
        while (m.find()) {
            for (String label : m.group(1).split("\\s*,\\s*")) {
                int n = Integer.parseInt(label.substring(1));
                if (n < 1 || n > contextSize) {
                    return Optional.empty();
                }
                cited.add(n);
            }
        }
        return cited.isEmpty() ? Optional.empty() : Optional.of(cited);
    }

    static List<Source> toSources(Set<Integer> cited, List<RetrievedChunk> context) {
        List<Source> sources = new ArrayList<>();
        for (int n : cited) {
            RetrievedChunk c = context.get(n - 1);
            String excerpt = c.text().length() > EXCERPT_CHARS ? c.text().substring(0, EXCERPT_CHARS) + "…" : c.text();
            sources.add(new Source("S" + n, c.contractId(), c.contractName(), c.chunkId(), c.chunkIndex(),
                    c.pageNumber(), excerpt));
        }
        return sources;
    }
}
