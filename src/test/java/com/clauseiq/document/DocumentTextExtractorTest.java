package com.clauseiq.document;

import com.clauseiq.document.DocumentTextExtractor.ExtractedDocument;
import com.clauseiq.document.DocumentTextExtractor.PageText;
import com.clauseiq.support.TestDocuments;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void pdfPagesKeepTheirRealPageNumbers() throws Exception {
        ExtractedDocument doc = extractor.extract(new ByteArrayInputStream(
                TestDocuments.pdf("Alpha clause on page one.", "Bravo clause on page two.", "Charlie clause on page three.")));

        assertThat(doc.paginated()).isTrue();
        assertThat(doc.pages()).extracting(PageText::pageNumber).containsExactly(1, 2, 3);
        assertThat(doc.pages().get(1).text()).contains("Bravo");
        assertThat(doc.pages().get(2).text()).contains("Charlie");
    }

    @Test
    void docxHasNoInventedPageNumbers() throws Exception {
        ExtractedDocument doc = extractor.extract(new ByteArrayInputStream(
                TestDocuments.docx("First paragraph.", "Second paragraph.")));

        assertThat(doc.paginated()).isFalse();
        assertThat(doc.pages()).allMatch(p -> p.pageNumber() == null);
        assertThat(doc.fullText()).contains("First paragraph.").contains("Second paragraph.");
    }

    @Test
    void documentsAboveTheTextLimitAreRejectedWithAUserSafeMessage() {
        DocumentTextExtractor limited = new DocumentTextExtractor(50);
        assertThatThrownBy(() -> limited.extract(new ByteArrayInputStream(TestDocuments.pdf(
                "This page has quite a lot more than fifty characters of contract text on it."))))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("exceeds the limit");
    }

    @Test
    void detectsRealTypeFromContentNotFilename() throws Exception {
        assertThat(extractor.detectType(new ByteArrayInputStream(TestDocuments.pdf("x")), "a.pdf"))
                .isEqualTo(DocumentTextExtractor.PDF);
        assertThat(extractor.detectType(new ByteArrayInputStream(TestDocuments.docx("x")), "a.docx"))
                .isEqualTo(DocumentTextExtractor.DOCX);
        assertThat(extractor.detectType(new ByteArrayInputStream(
                "plain text".getBytes(StandardCharsets.UTF_8)), "fake.pdf"))
                .isNotEqualTo(DocumentTextExtractor.PDF);
    }
}
