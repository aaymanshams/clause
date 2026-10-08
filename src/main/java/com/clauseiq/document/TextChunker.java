package com.clauseiq.document;

import com.clauseiq.document.DocumentTextExtractor.PageText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Paragraph-aware chunker. Paragraphs are packed into chunks of up to {@link #MAX_CHARS}; a chunk
 * never spans pages, so each chunk's page number is exact. Oversized paragraphs are split on
 * sentence boundaries (or hard-split as a last resort).
 */
@Component
public class TextChunker {

    static final int MAX_CHARS = 1200;
    private static final int MIN_CHARS = 40;

    public record TextChunk(int index, Integer pageNumber, String text) {
    }

    public List<TextChunk> chunk(List<PageText> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        for (PageText page : pages) {
            StringBuilder buffer = new StringBuilder();
            for (String paragraph : splitParagraphs(page.text())) {
                for (String piece : splitOversized(paragraph)) {
                    if (buffer.length() > 0 && buffer.length() + piece.length() + 2 > MAX_CHARS) {
                        add(chunks, page.pageNumber(), buffer.toString());
                        buffer.setLength(0);
                    }
                    if (buffer.length() > 0) {
                        buffer.append("\n\n");
                    }
                    buffer.append(piece);
                }
            }
            add(chunks, page.pageNumber(), buffer.toString());
        }
        return chunks;
    }

    private static void add(List<TextChunk> chunks, Integer pageNumber, String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        // Merge tiny fragments (e.g. a lone heading) into the previous chunk on the same page.
        if (trimmed.length() < MIN_CHARS && !chunks.isEmpty()) {
            TextChunk last = chunks.get(chunks.size() - 1);
            if (java.util.Objects.equals(last.pageNumber(), pageNumber)
                    && last.text().length() + trimmed.length() + 2 <= MAX_CHARS) {
                chunks.set(chunks.size() - 1, new TextChunk(last.index(), pageNumber, last.text() + "\n\n" + trimmed));
                return;
            }
        }
        chunks.add(new TextChunk(chunks.size(), pageNumber, trimmed));
    }

    private static List<String> splitParagraphs(String text) {
        List<String> result = new ArrayList<>();
        for (String p : text.split("\\n\\s*\\n")) {
            String cleaned = p.replaceAll("\\s*\\n\\s*", " ").trim();
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private static List<String> splitOversized(String paragraph) {
        if (paragraph.length() <= MAX_CHARS) {
            return List.of(paragraph);
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.;:!?])\\s+")) {
            if (current.length() > 0 && current.length() + sentence.length() + 1 > MAX_CHARS) {
                pieces.add(current.toString());
                current.setLength(0);
            }
            if (sentence.length() > MAX_CHARS) {
                for (int i = 0; i < sentence.length(); i += MAX_CHARS) {
                    pieces.add(sentence.substring(i, Math.min(sentence.length(), i + MAX_CHARS)));
                }
                continue;
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }
}
