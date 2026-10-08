package com.clauseiq.document;

import com.clauseiq.document.DocumentTextExtractor.PageText;
import com.clauseiq.document.TextChunker.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void chunksNeverSpanPagesAndKeepPageNumbers() {
        List<TextChunk> chunks = chunker.chunk(List.of(
                new PageText(1, "Page one paragraph about payment terms and invoices."),
                new PageText(2, "Page two paragraph about termination and notice periods.")));

        assertThat(chunks).extracting(TextChunk::pageNumber).containsExactly(1, 2);
        assertThat(chunks).extracting(TextChunk::index).containsExactly(0, 1);
    }

    @Test
    void packsParagraphsUpToTheSizeLimit() {
        String paragraph = "This clause has some reasonably long contract wording in it. ".repeat(5).trim();
        String page = String.join("\n\n", paragraph, paragraph, paragraph, paragraph, paragraph, paragraph);

        List<TextChunk> chunks = chunker.chunk(List.of(new PageText(1, page)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(c -> c.text().length() <= TextChunker.MAX_CHARS);
        assertThat(String.join(" ", chunks.stream().map(TextChunk::text).toList()))
                .contains("reasonably long contract wording");
    }

    @Test
    void splitsOversizedParagraphsOnSentenceBoundaries() {
        String sentence = "The supplier shall deliver goods in accordance with the schedule. ";
        List<TextChunk> chunks = chunker.chunk(List.of(new PageText(null, sentence.repeat(60))));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(c -> c.text().length() <= TextChunker.MAX_CHARS);
        assertThat(chunks).allMatch(c -> c.pageNumber() == null);
        assertThat(chunks.get(0).text()).endsWith(".");
    }

    @Test
    void tinyFragmentsAreMergedIntoThePreviousChunk() {
        String body = "A".repeat(TextChunker.MAX_CHARS - 20);
        List<TextChunk> chunks = chunker.chunk(List.of(new PageText(1, body + "\n\nShort tail.")));
        assertThat(chunks).hasSize(1);
    }

    @Test
    void blankPagesProduceNoChunks() {
        assertThat(chunker.chunk(List.of(new PageText(1, "   \n\n  ")))).isEmpty();
    }
}
