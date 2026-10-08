package com.clauseiq.ai.rag;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.Prompts;
import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.rag.ChatDtos.ChatResponse;
import com.clauseiq.ai.rag.ChatDtos.Source;
import com.clauseiq.search.SemanticSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final Pattern CITATION = Pattern.compile("\\[S(\\d+)]");
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
        return new ChatResponse(answer, true, citedSources(answer, context), aiService.providerName());
    }

    /**
     * Returns only the excerpts the model actually cited. If the model cited nothing, all retrieved
     * excerpts are returned so the answer is still traceable to its context.
     */
    static List<Source> citedSources(String answer, List<RetrievedChunk> context) {
        Set<Integer> cited = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= context.size()) {
                cited.add(n);
            }
        }
        if (cited.isEmpty()) {
            for (int i = 1; i <= context.size(); i++) {
                cited.add(i);
            }
        }
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
