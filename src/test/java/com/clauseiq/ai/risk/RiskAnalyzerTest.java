package com.clauseiq.ai.risk;

import com.clauseiq.ai.extraction.ContractExtractionResult;
import com.clauseiq.ai.risk.RiskFinding.Severity;
import com.clauseiq.ai.risk.RiskFinding.Type;
import com.clauseiq.config.ClauseIqProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RiskAnalyzerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final RiskAnalyzer analyzer = new RiskAnalyzer(
            new ClauseIqProperties.Risk(60, 120, 12, List.of("New York", "Delaware", "India")), FIXED);

    /** A contract with no risky terms; individual tests change one field at a time. */
    private static ContractExtractionResult clean() {
        return new ContractExtractionResult(List.of("Acme", "Globex"), "2025-01-01", "2028-01-01",
                "Renews annually", true, 12, 30, "State of New York", "Fees paid in prior 12 months", "Net 30");
    }

    private static ContractExtractionResult with(Integer noticeDays, Boolean autoRenew, Integer renewalMonths,
                                                 String law, String cap, String expiration) {
        return new ContractExtractionResult(List.of("Acme"), "2025-01-01", expiration, null, autoRenew,
                renewalMonths, noticeDays, law, cap, null);
    }

    @Test
    void cleanContractHasNoRisks() {
        assertThat(analyzer.analyze(clean())).isEmpty();
    }

    @Test
    void terminationNoticeAboveThresholdIsMediumAndFarAboveIsHigh() {
        assertThat(analyzer.analyze(with(60, null, null, "New York", "cap", null))).isEmpty();
        assertThat(analyzer.analyze(with(90, null, null, "New York", "cap", null)))
                .extracting(RiskFinding::type, RiskFinding::severity)
                .containsExactly(tuple(Type.TERMINATION, Severity.MEDIUM));
        assertThat(analyzer.analyze(with(180, null, null, "New York", "cap", null)))
                .extracting(RiskFinding::type, RiskFinding::severity)
                .containsExactly(tuple(Type.TERMINATION, Severity.HIGH));
    }

    @Test
    void missingLiabilityCapIsHigh() {
        assertThat(analyzer.analyze(with(30, null, null, "New York", null, null)))
                .extracting(RiskFinding::type, RiskFinding::severity)
                .containsExactly(tuple(Type.MISSING_LIABILITY_CAP, Severity.HIGH));
    }

    @Test
    void unlimitedLiabilityIsHigh() {
        assertThat(analyzer.analyze(with(30, null, null, "New York", "Unlimited", null)))
                .extracting(RiskFinding::type).containsExactly(Type.UNLIMITED_LIABILITY);
    }

    @Test
    void longAutoRenewalIsFlaggedButManualRenewalIsNot() {
        assertThat(analyzer.analyze(with(30, true, 24, "New York", "cap", null)))
                .extracting(RiskFinding::type).containsExactly(Type.AUTO_RENEWAL);
        assertThat(analyzer.analyze(with(30, false, 24, "New York", "cap", null))).isEmpty();
    }

    @Test
    void unusualOrMissingGoverningLawIsLow() {
        assertThat(analyzer.analyze(with(30, null, null, "Republic of Zanzibar", "cap", null)))
                .extracting(RiskFinding::type, RiskFinding::severity)
                .containsExactly(tuple(Type.GOVERNING_LAW, Severity.LOW));
        assertThat(analyzer.analyze(with(30, null, null, null, "cap", null)))
                .extracting(RiskFinding::type).containsExactly(Type.GOVERNING_LAW);
        assertThat(analyzer.analyze(with(30, null, null, "laws of the state of delaware", "cap", null))).isEmpty();
    }

    @Test
    void upcomingExpirationIsMediumAndPastExpirationIsLow() {
        assertThat(analyzer.analyze(with(30, null, null, "New York", "cap", "2026-02-15")))
                .extracting(RiskFinding::type, RiskFinding::severity)
                .containsExactly(tuple(Type.EXPIRATION, Severity.MEDIUM));
        assertThat(analyzer.analyze(with(30, null, null, "New York", "cap", "2025-06-01")))
                .extracting(RiskFinding::severity).containsExactly(Severity.LOW);
        assertThat(analyzer.analyze(with(30, null, null, "New York", "cap", "2027-01-01"))).isEmpty();
    }
}
