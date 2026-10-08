package com.clauseiq.ai.risk;

public record RiskFinding(Type type, Severity severity, String description) {

    public enum Type { TERMINATION, MISSING_LIABILITY_CAP, UNLIMITED_LIABILITY, AUTO_RENEWAL, GOVERNING_LAW, EXPIRATION }

    public enum Severity { LOW, MEDIUM, HIGH }
}
