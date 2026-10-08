package com.clauseiq.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the fictional demo contracts in /samples. Run with the test classpath:
 * {@code java -cp target/test-classes:$(cat cp.txt) com.clauseiq.support.SampleContracts samples}
 */
public final class SampleContracts {

    private SampleContracts() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "samples");
        Files.createDirectories(dir);
        Files.write(dir.resolve("acme-northwind-master-services-agreement.pdf"), TestDocuments.pdf(
                """
                MASTER SERVICES AGREEMENT

                This Master Services Agreement (the "Agreement") is entered into and effective as of January 1, 2025 by and between Acme Corporation, a Delaware corporation ("Customer"), and Northwind Traders Ltd ("Provider").

                1. SERVICES. Provider shall provide the cloud logistics and analytics services described in each Statement of Work. Each Statement of Work is governed by this Agreement.

                2. TERM. The Agreement shall remain in force until December 31, 2027 and shall automatically renew for successive periods of two (2) years unless either party gives written notice of non-renewal at least ninety (90) days before the end of the then-current term.
                """,
                """
                3. FEES AND PAYMENT. Customer shall pay all undisputed invoices within thirty (30) days of receipt of the invoice. Late payments accrue interest at 1% per month.

                4. TERMINATION. Either party may terminate this Agreement for convenience by giving ninety (90) days prior written notice to the other party. Either party may terminate immediately on written notice if the other party materially breaches this Agreement and fails to cure the breach within thirty (30) days.

                5. CONFIDENTIALITY. Each party shall protect the other party's Confidential Information with at least the same degree of care it uses for its own, and no less than reasonable care, for five (5) years after termination.
                """,
                """
                6. LIMITATION OF LIABILITY. Except for breaches of confidentiality, each party's aggregate liability under this Agreement shall not exceed the total fees paid by Customer in the twelve (12) months preceding the claim. Neither party is liable for indirect or consequential damages.

                7. DATA PROTECTION. Provider shall process Customer personal data only on documented instructions and shall notify Customer of any personal data breach within forty-eight (48) hours.

                8. GOVERNING LAW. This Agreement shall be governed by the laws of the State of New York, without regard to conflict of law principles. The courts of New York County have exclusive jurisdiction.
                """));

        Files.write(dir.resolve("globex-supply-agreement.docx"), TestDocuments.docx(
                "SUPPLY AGREEMENT",
                "This Supply Agreement is made between Globex Industries and Orbit Components Pte Ltd, effective as of March 1, 2025.",
                "1. SUPPLY. Orbit Components Pte Ltd shall manufacture and deliver the components listed in Schedule A according to the agreed delivery schedule.",
                "2. TERM AND TERMINATION. This Agreement expires on February 28, 2028. Either party may terminate this agreement upon fifteen (15) days written notice.",
                "3. PAYMENT. Payment is due Net 45 from the date of invoice.",
                "4. LIABILITY. The supplier's liability shall be unlimited for all claims arising from defective components or late delivery.",
                "5. GOVERNING LAW. This agreement is governed by the laws of Singapore."));

        Files.write(dir.resolve("initech-saas-subscription.pdf"), TestDocuments.pdf(
                """
                SOFTWARE-AS-A-SERVICE SUBSCRIPTION AGREEMENT

                This Subscription Agreement is effective as of December 1, 2023 between Initech LLC ("Subscriber") and Hooli Cloud Inc ("Vendor").

                1. SUBSCRIPTION. Vendor grants Subscriber a non-exclusive right to use the Hooli analytics platform for up to 250 named users.

                2. TERM. The subscription term ends on November 30, 2026. This Agreement does not renew automatically; any renewal requires a signed order form.

                3. FEES. Subscriber shall pay the annual subscription fee of USD 120,000 within thirty (30) days of receipt of invoice.

                4. TERMINATION. Either party may terminate this Agreement on thirty (30) days written notice if the other party materially breaches it and fails to cure.

                5. LIMITATION OF LIABILITY. Vendor's total aggregate liability shall not exceed USD 120,000.

                6. GOVERNING LAW. This Agreement is governed by the laws of the State of Delaware.
                """));
        System.out.println("Wrote sample contracts to " + dir.toAbsolutePath());
    }
}
