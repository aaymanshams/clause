package com.clauseiq.ai.rag;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.Prompts;
import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.rag.ChatDtos.ChatResponse;
import com.clauseiq.search.SemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    private static final Long TENANT = 42L;

    @Mock
    private SemanticSearchService searchService;

    @Mock
    private AiService aiService;

    @InjectMocks
    private RagService ragService;

    private static RetrievedChunk chunk(long id, Integer page, String text) {
        return new RetrievedChunk(id, 10L, "acme.pdf", (int) id, page, text, 0.8);
    }

    @Test
    void searchesWithinTheCallersTenantOnly() {
        when(searchService.search(TENANT, "q?", null, null)).thenReturn(List.of());
        ragService.ask(TENANT, "q?", null);
        verify(searchService).search(TENANT, "q?", null, null);
    }

    @Test
    void noRelevantContextReturnsFallbackWithoutCallingTheLlm() {
        when(searchService.search(TENANT, "q?", null, null)).thenReturn(List.of());

        ChatResponse response = ragService.ask(TENANT, "q?", null);

        assertThat(response.answered()).isFalse();
        assertThat(response.answer()).isEqualTo(Prompts.NO_ANSWER);
        assertThat(response.sources()).isEmpty();
        verify(aiService, never()).generateAnswer(anyString(), anyList());
    }

    @Test
    void onlyCitedSourcesAreReturnedWithPageNumbers() {
        var context = List.of(chunk(1, 1, "intro"), chunk(2, 3, "Notice is 90 days."), chunk(3, null, "other"));
        when(searchService.search(TENANT, "notice?", null, null)).thenReturn(context);
        when(aiService.generateAnswer(any(), any())).thenReturn("The notice period is 90 days [S2].");

        ChatResponse response = ragService.ask(TENANT, "notice?", null);

        assertThat(response.answered()).isTrue();
        assertThat(response.sources()).singleElement().satisfies(s -> {
            assertThat(s.label()).isEqualTo("S2");
            assertThat(s.chunkId()).isEqualTo(2L);
            assertThat(s.pageNumber()).isEqualTo(3);
        });
    }

    @Test
    void groupedCitationsAreAcceptedAndDeduplicated() {
        assertThat(RagService.validCitations("A [S1, S3] and again [S3].", 3)).contains(java.util.Set.of(1, 3));
    }

    @Test
    void answersWithoutCitationsAreRejectedAsUngrounded() {
        when(searchService.search(TENANT, "q?", null, null)).thenReturn(List.of(chunk(1, 1, "a"), chunk(2, 2, "b")));
        when(aiService.generateAnswer(any(), any())).thenReturn("The notice period is 90 days.");

        ChatResponse response = ragService.ask(TENANT, "q?", null);

        assertThat(response.answered()).isFalse();
        assertThat(response.answer()).isEqualTo(Prompts.NO_ANSWER);
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void answersCitingAnExcerptThatWasNeverRetrievedAreRejected() {
        when(searchService.search(TENANT, "q?", null, null)).thenReturn(List.of(chunk(1, 1, "a"), chunk(2, 2, "b")));
        when(aiService.generateAnswer(any(), any())).thenReturn("It is 90 days [S1][S7].");

        ChatResponse response = ragService.ask(TENANT, "q?", null);

        assertThat(response.answered()).isFalse();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void llmRefusalIsNormalisedToTheFallbackAnswer() {
        when(searchService.search(TENANT, "q?", null, null)).thenReturn(List.of(chunk(1, 1, "text")));
        when(aiService.generateAnswer(any(), any())).thenReturn(Prompts.NO_ANSWER);

        ChatResponse response = ragService.ask(TENANT, "q?", null);

        assertThat(response.answered()).isFalse();
        assertThat(response.sources()).isEmpty();
    }
}
