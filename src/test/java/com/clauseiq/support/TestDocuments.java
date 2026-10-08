package com.clauseiq.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Builds real PDF/DOCX files in memory so tests exercise the actual Tika parsing path. */
public final class TestDocuments {

    private TestDocuments() {
    }

    /** Each argument is one page; paragraphs within a page are separated by blank lines. */
    public static byte[] pdf(String... pages) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String pageText : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 10);
                    cs.setLeading(14);
                    cs.newLineAtOffset(50, 740);
                    for (String paragraph : pageText.split("\n\n")) {
                        for (String line : wrap(paragraph, 95)) {
                            cs.showText(line);
                            cs.newLine();
                        }
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] docx(String... paragraphs) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() + word.length() + 1 > width && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static final String ACME_PAGE_1 = """
            MASTER SERVICES AGREEMENT

            This Master Services Agreement is entered into and effective as of January 1, 2025 by and between Acme Corporation and Northwind Traders Ltd.

            The Agreement shall remain in force until December 31, 2027 and shall automatically renew for successive periods of two (2) years unless either party gives notice of non-renewal.
            """;

    public static final String ACME_PAGE_2 = """
            TERMINATION. Either party may terminate this Agreement for convenience by giving ninety (90) days prior written notice to the other party.

            PAYMENT. Customer shall pay all undisputed invoices within thirty (30) days of receipt of the invoice.

            LIMITATION OF LIABILITY. Each party's aggregate liability under this Agreement shall not exceed the total fees paid in the twelve months preceding the claim.

            GOVERNING LAW. This Agreement shall be governed by the laws of the State of New York, without regard to conflict of law principles.
            """;

    public static final String GLOBEX_TEXT = """
            ZANZIBAR LOGISTICS SUPPLY AGREEMENT

            This Supply Agreement is made between Globex Industries and Zanzibar Shipping Company.

            Either party may terminate this agreement upon fifteen (15) days written notice. The supplier's liability shall be unlimited for all claims arising from late delivery of Zanzibar cargo.

            This agreement is governed by the laws of Zanzibar.
            """;
}
