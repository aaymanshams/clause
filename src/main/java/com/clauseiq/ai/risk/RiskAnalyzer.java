package com.clauseiq.ai.risk;

import com.clauseiq.ai.extraction.ContractExtractionResult;
import com.clauseiq.ai.extraction.ExtractionValidator;
import com.clauseiq.ai.risk.RiskFinding.Severity;
import com.clauseiq.ai.risk.RiskFinding.Type;
import com.clauseiq.config.ClauseIqProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, explainable risk rules applied to the structured extraction. The LLM only
 * interprets clause wording (during extraction); the risk decision itself is plain, testable code.
 */
@Component
public class RiskAnalyzer {

    static final int EXPIRY_WARNING_DAYS = 90;

    private final ClauseIqProperties.Risk config;
    private final Clock clock;

    @Autowired
    public RiskAnalyzer(ClauseIqProperties properties) {
        this(properties.risk(), Clock.systemDefaultZone());
    }

    RiskAnalyzer(ClauseIqProperties.Risk config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    public List<RiskFinding> analyze(ContractExtractionResult data) {
        List<RiskFinding> risks = new ArrayList<>();
        checkTermination(data, risks);
        checkLiability(data, risks);
        checkAutoRenewal(data, risks);
        checkGoverningLaw(data, risks);
        checkExpiration(data, risks);
        return risks;
    }

    private void checkTermination(ContractExtractionResult d, List<RiskFinding> risks) {
        Integer days = d.terminationNoticeDays();
        if (days == null) {
            return;
        }
        if (days > config.highTerminationNoticeDays()) {
            risks.add(new RiskFinding(Type.TERMINATION, Severity.HIGH,
                    "Termination notice of " + days + " days is far above the recommended maximum of "
                            + config.maxTerminationNoticeDays() + " days."));
        } else if (days > config.maxTerminationNoticeDays()) {
            risks.add(new RiskFinding(Type.TERMINATION, Severity.MEDIUM,
                    "Termination notice of " + days + " days exceeds the recommended threshold of "
                            + config.maxTerminationNoticeDays() + " days."));
        }
    }

    private void checkLiability(ContractExtractionResult d, List<RiskFinding> risks) {
        String cap = d.liabilityCap();
        if (cap == null || cap.isBlank()) {
            risks.add(new RiskFinding(Type.MISSING_LIABILITY_CAP, Severity.HIGH,
                    "No limitation-of-liability cap was found in the contract."));
            return;
        }
        String lower = cap.toLowerCase(Locale.ROOT);
        if (lower.contains("unlimited") || lower.contains("uncapped") || lower.contains("no limit")) {
            risks.add(new RiskFinding(Type.UNLIMITED_LIABILITY, Severity.HIGH,
                    "Liability is expressly unlimited: \"" + cap + "\"."));
        }
    }

    private void checkAutoRenewal(ContractExtractionResult d, List<RiskFinding> risks) {
        if (!Boolean.TRUE.equals(d.autoRenewal()) || d.renewalTermMonths() == null) {
            return;
        }
        if (d.renewalTermMonths() > config.maxAutoRenewalMonths()) {
            risks.add(new RiskFinding(Type.AUTO_RENEWAL, Severity.MEDIUM,
                    "Contract auto-renews for " + d.renewalTermMonths() + "-month terms (recommended maximum "
                            + config.maxAutoRenewalMonths() + " months)."));
        }
    }

    private void checkGoverningLaw(ContractExtractionResult d, List<RiskFinding> risks) {
        String law = d.governingLaw();
        if (law == null || law.isBlank()) {
            risks.add(new RiskFinding(Type.GOVERNING_LAW, Severity.LOW, "No governing-law clause was found."));
            return;
        }
        String lower = law.toLowerCase(Locale.ROOT);
        boolean preferred = config.preferredGoverningLaws().stream()
                .anyMatch(p -> lower.contains(p.toLowerCase(Locale.ROOT)));
        if (!preferred) {
            risks.add(new RiskFinding(Type.GOVERNING_LAW, Severity.LOW,
                    "Governing law (" + law + ") is not one of the organization's preferred jurisdictions."));
        }
    }

    private void checkExpiration(ContractExtractionResult d, List<RiskFinding> risks) {
        LocalDate expiration = ExtractionValidator.parseDate(d.expirationDate());
        if (expiration == null) {
            return;
        }
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(clock), expiration);
        if (daysLeft < 0) {
            risks.add(new RiskFinding(Type.EXPIRATION, Severity.LOW, "Contract expired on " + expiration + "."));
        } else if (daysLeft <= EXPIRY_WARNING_DAYS) {
            risks.add(new RiskFinding(Type.EXPIRATION, Severity.MEDIUM,
                    "Contract expires in " + daysLeft + " days (" + expiration + "); review renewal now."));
        }
    }
}
