package com.clauseiq.document;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extracts text from PDF/DOCX with Apache Tika.
 * For PDFs, Tika wraps each page in {@code <div class="page">}, which we use to keep real page numbers.
 * DOCX has no fixed pagination, so its single "page" has a null page number (we never invent one).
 */
@Component
public class DocumentTextExtractor {

    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final Set<String> SUPPORTED_TYPES = Set.of(PDF, DOCX);

    /**
     * Upper bound on extracted text (~400 pages). Bounds memory and, more importantly, the number of
     * chunks sent for embedding: a 10 MB upload can otherwise expand to millions of characters.
     */
    static final int DEFAULT_MAX_CHARS = 1_000_000;

    public record PageText(Integer pageNumber, String text) {
    }

    public record ExtractedDocument(List<PageText> pages, boolean paginated) {
        public String fullText() {
            StringBuilder sb = new StringBuilder();
            for (PageText p : pages) {
                sb.append(p.text()).append("\n\n");
            }
            return sb.toString().trim();
        }
    }

    private final Tika tika = new Tika();
    private final int maxChars;

    public DocumentTextExtractor() {
        this(DEFAULT_MAX_CHARS);
    }

    DocumentTextExtractor(int maxChars) {
        this.maxChars = maxChars;
    }

    /** Detects the real media type from file content (magic bytes), using the name only as a hint. */
    public String detectType(InputStream content, String filename) throws IOException {
        return tika.detect(content, filename);
    }

    public ExtractedDocument extract(InputStream content) throws IOException {
        PageCollector collector = new PageCollector(maxChars);
        try {
            new AutoDetectParser().parse(content, collector, new Metadata(), new ParseContext());
        } catch (SAXException | TikaException e) {
            throw new IOException("Could not parse document: " + e.getMessage(), e);
        }
        if (collector.limitExceeded) {
            throw new DocumentProcessingException(
                    "The document contains more than " + maxChars + " characters of text, which exceeds the limit");
        }
        return collector.result();
    }

    /** SAX handler that splits Tika's XHTML output into pages and paragraphs. */
    private static final class PageCollector extends DefaultHandler {

        private final List<StringBuilder> pages = new ArrayList<>();
        private final int maxChars;
        private StringBuilder current;
        private boolean sawPageDiv;
        private int totalChars;
        private boolean limitExceeded;

        PageCollector(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = localName.isEmpty() ? qName : localName;
            if ("div".equals(name) && "page".equals(attributes.getValue("class"))) {
                if (!sawPageDiv) {
                    // Drop whitespace Tika emits before the first page, or every page number shifts by one.
                    pages.removeIf(sb -> sb.toString().isBlank());
                }
                sawPageDiv = true;
                current = new StringBuilder();
                pages.add(current);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = localName.isEmpty() ? qName : localName;
            if (current != null && (name.equals("p") || name.matches("h[1-6]") || name.equals("li")
                    || name.equals("tr") || name.equals("div"))) {
                current.append("\n\n");
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            totalChars += length;
            if (totalChars > maxChars) {
                limitExceeded = true; // stop accumulating; the caller rejects the document after parsing
                return;
            }
            if (current == null) {
                current = new StringBuilder();
                pages.add(current);
            }
            current.append(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) {
            characters(ch, start, length);
        }

        ExtractedDocument result() {
            List<PageText> result = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                String text = normalize(pages.get(i).toString());
                if (!text.isBlank()) {
                    result.add(new PageText(sawPageDiv ? i + 1 : null, text));
                }
            }
            return new ExtractedDocument(result, sawPageDiv);
        }

        private static String normalize(String text) {
            return text.replace(' ', ' ')
                    .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                    .replaceAll(" *\\n *", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
        }
    }
}
