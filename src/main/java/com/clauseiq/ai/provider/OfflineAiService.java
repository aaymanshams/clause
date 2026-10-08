package com.clauseiq.ai.provider;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.Prompts;
import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.extraction.ContractExtractionResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, network-free fallback used when no API key is configured (and in tests).
 * <ul>
 *   <li>Embeddings: feature hashing of word unigrams/bigrams — lexical, not truly semantic.</li>
 *   <li>Extraction: regular-expression heuristics for common clause wording.</li>
 *   <li>Answers: extractive — returns the best-matching sentence verbatim, never generated text.</li>
 * </ul>
 * It keeps the whole pipeline (pgvector, tenant filtering, citations) runnable without an LLM.
 */
public class OfflineAiService implements AiService {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "of", "to", "in", "on", "for", "and", "or", "is", "are", "be", "by", "with",
            "this", "that", "what", "which", "who", "how", "when", "does", "do", "any", "all", "as", "at",
            "it", "its", "from", "shall", "will", "may", "our", "we", "us", "there", "contract", "agreement");

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final double MIN_ANSWER_OVERLAP = 0.5;
    private static final int MAX_ANSWER_CHARS = 600;

    @Override
    public String providerName() {
        return "offline";
    }

    /** Hashed bag-of-words vectors of short queries vs. long chunks have low absolute cosine scores. */
    @Override
    public double minRelevantSimilarity() {
        return 0.05;
    }

    // ---------------------------------------------------------------- embeddings

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        return texts.stream().map(OfflineAiService::hashEmbedding).toList();
    }

    static float[] hashEmbedding(String text) {
        float[] v = new float[EMBEDDING_DIMENSIONS];
        List<String> tokens = tokens(text);
        for (int i = 0; i < tokens.size(); i++) {
            v[Math.floorMod(tokens.get(i).hashCode(), EMBEDDING_DIMENSIONS)] += 1f;
            if (i > 0) {
                v[Math.floorMod((tokens.get(i - 1) + "_" + tokens.get(i)).hashCode(), EMBEDDING_DIMENSIONS)] += 0.5f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1f; // pgvector cosine distance is undefined for zero vectors
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            if (STOPWORDS.contains(t)) {
                continue;
            }
            if (t.length() > 3 && t.endsWith("s")) {
                t = t.substring(0, t.length() - 1);
            }
            // Crude prefix stemming: "terminate", "termination", "terminating" -> "termin".
            out.add(t.length() > 6 ? t.substring(0, 6) : t);
        }
        return out;
    }

    // ---------------------------------------------------------------- answers

    @Override
    public String generateAnswer(String question, List<RetrievedChunk> context) {
        Set<String> q = new HashSet<>(tokens(question));
        if (q.isEmpty() || context.isEmpty()) {
            return Prompts.NO_ANSWER;
        }
        String best = null;
        int bestSource = -1;
        double bestScore = 0;
        for (int i = 0; i < context.size(); i++) {
            // Paragraph-level answers keep a clause together with its heading.
            for (String sentence : context.get(i).text().split("\\n+")) {
                Set<String> s = new HashSet<>(tokens(sentence));
                long hits = q.stream().filter(s::contains).count();
                double score = (double) hits / q.size();
                if (score > bestScore) {
                    bestScore = score;
                    best = sentence.trim();
                    bestSource = i + 1;
                }
            }
        }
        if (best == null || bestScore < MIN_ANSWER_OVERLAP) {
            return Prompts.NO_ANSWER;
        }
        if (best.length() > MAX_ANSWER_CHARS) {
            best = best.substring(0, MAX_ANSWER_CHARS) + "…";
        }
        return "The most relevant clause states: \"" + best + "\" [S" + bestSource + "]";
    }

    // ---------------------------------------------------------------- extraction

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("twelve", 12), Map.entry("eighteen", 18),
            Map.entry("twenty-four", 24), Map.entry("thirty", 30), Map.entry("sixty", 60), Map.entry("ninety", 90));

    private static final String DATE = "(\\d{4}-\\d{2}-\\d{2}|[A-Z][a-z]+ \\d{1,2}, \\d{4}|\\d{1,2} [A-Z][a-z]+,? \\d{4})";

    private static final Pattern PARTIES = Pattern.compile(
            "between\\s+(.{3,100}?)(?:\\s*\\([^)]{0,60}\\))?,?\\s+and\\s+(.{3,100}?)(?:\\s*\\(|,|\\.|\\n)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EFFECTIVE = Pattern.compile(
            "effective(?:\\s+as\\s+of|\\s+date(?:\\s+of)?|\\s+on)?[^.\\d]{0,40}?" + DATE, Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPIRATION = Pattern.compile(
            "(?:expire|expiration|expiry|terminate|ends?\\s+on|until|through)[^.\\d]{0,40}?" + DATE, Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTICE_DAYS = Pattern.compile(
            "(\\d{1,4}|[a-z]+(?:-[a-z]+)?)\\)?\\s*(?:\\(\\d{1,4}\\)\\s*)?(?:calendar\\s+|business\\s+)?days['’]?\\s+"
                    + "(?:prior\\s+|advance\\s+)?(?:written\\s+)?notice", Pattern.CASE_INSENSITIVE);
    private static final Pattern RENEWAL_TERM = Pattern.compile(
            "renew\\w*[^.]{0,60}?(?:for|of)\\s+(?:successive\\s+|additional\\s+|subsequent\\s+)*"
                    + "(?:periods?\\s+of\\s+|terms?\\s+of\\s+)?(\\d{1,3}|[a-z]+(?:-[a-z]+)?)\\s*(?:\\((\\d{1,3})\\)\\s*)?"
                    + "[- ]?(year|month)s?", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOVERNING_LAW = Pattern.compile(
            "governed\\s+by[^.]{0,60}?laws?\\s+of\\s+(?:the\\s+)?(?:State\\s+of\\s+)?([A-Z][A-Za-z ]{1,40}?)"
                    + "(?=\\s*(?:[,.;(]|without|and\\s+the|$))");
    private static final Pattern PAYMENT = Pattern.compile(
            "[^.]*(?:net\\s*\\d{1,3}|within\\s+\\w+\\s*(?:\\(\\d+\\)\\s*)?days\\s+(?:of|after|from)\\s+"
                    + "(?:the\\s+)?(?:receipt|invoice|date))[^.]*\\.", Pattern.CASE_INSENSITIVE);

    @Override
    public ContractExtractionResult extractContractData(String text) {
        String flat = text.replaceAll("\\s+", " ");
        List<String> parties = new ArrayList<>();
        Matcher pm = PARTIES.matcher(flat);
        if (pm.find()) {
            parties.add(cleanParty(pm.group(1)));
            parties.add(cleanParty(pm.group(2)));
        }
        parties.removeIf(String::isBlank);

        Boolean autoRenewal = Pattern.compile("automatic(ally)?\\s+renew|auto-renew", Pattern.CASE_INSENSITIVE)
                .matcher(flat).find() ? Boolean.TRUE : null;
        Integer renewalMonths = null;
        String renewalPeriod = null;
        Matcher rm = RENEWAL_TERM.matcher(flat);
        if (rm.find()) {
            Integer n = rm.group(2) != null ? Integer.valueOf(rm.group(2)) : toNumber(rm.group(1));
            if (n != null) {
                renewalMonths = rm.group(3).equalsIgnoreCase("year") ? n * 12 : n;
                renewalPeriod = (Boolean.TRUE.equals(autoRenewal) ? "Auto-renews for " : "Renews for ")
                        + renewalMonths + "-month terms";
            }
        }

        LocalDate effective = firstDate(EFFECTIVE, flat);
        LocalDate expiration = firstDate(EXPIRATION, flat);
        if (effective != null && expiration != null && expiration.isBefore(effective)) {
            expiration = null;
        }

        return new ContractExtractionResult(
                List.copyOf(new LinkedHashSet<>(parties)),
                effective == null ? null : effective.toString(),
                expiration == null ? null : expiration.toString(),
                renewalPeriod,
                autoRenewal,
                renewalMonths,
                noticeDays(flat),
                group(GOVERNING_LAW, flat, 1),
                liabilityCap(flat),
                group(PAYMENT, flat, 0));
    }

    private static Integer noticeDays(String flat) {
        Matcher m = NOTICE_DAYS.matcher(flat);
        while (m.find()) {
            String paren = m.group().replaceAll(".*?\\((\\d{1,4})\\).*", "$1");
            Integer n = paren.matches("\\d+") ? Integer.valueOf(paren) : toNumber(m.group(1));
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    private static String liabilityCap(String flat) {
        for (String sentence : flat.split("(?<=\\.)\\s+")) {
            String s = sentence.toLowerCase(Locale.ROOT);
            if (!s.contains("liab")) {
                continue;
            }
            if (s.contains("unlimited") || s.contains("uncapped") || s.contains("shall not be limited")) {
                return "Unlimited";
            }
            if (s.contains("shall not exceed") || s.contains("limited to") || s.contains("aggregate liability")
                    || s.contains("cap")) {
                return sentence.length() > 300 ? sentence.substring(0, 300) : sentence.trim();
            }
        }
        return null;
    }

    private static LocalDate firstDate(Pattern p, String text) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            LocalDate d = parseDate(m.group(1));
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static LocalDate parseDate(String s) {
        for (String fmt : List.of("yyyy-MM-dd", "MMMM d, yyyy", "d MMMM yyyy", "d MMMM, yyyy")) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private static Integer toNumber(String token) {
        if (token == null) {
            return null;
        }
        if (token.matches("\\d+")) {
            return Integer.valueOf(token);
        }
        return NUMBER_WORDS.get(token.toLowerCase(Locale.ROOT));
    }

    private static String group(Pattern p, String text, int group) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(group).trim() : null;
    }

    private static String cleanParty(String raw) {
        return raw.replaceAll("^(?:the\\s+)", "")
                .replaceAll(",\\s+an?\\s+.*$", "") // drop descriptors like ", a Delaware corporation"
                .replaceAll("[\"“”]", "").trim();
    }
}
