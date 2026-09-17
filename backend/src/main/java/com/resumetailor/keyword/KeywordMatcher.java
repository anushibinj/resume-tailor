package com.resumetailor.keyword;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides which JD keywords actually appear in a resume, and turns that into a score.
 *
 * <p><b>This is computed in Java on purpose.</b> The LLM extracts which keywords the
 * job asks for, but it never gets to report whether the resume contains them -- a model
 * grading its own rewrite would inflate the one number the user is meant to trust.
 */
@Component
public class KeywordMatcher {

    /** LaTeX control sequences such as {@code \textbf} -- markup, not resume content. */
    private static final Pattern LATEX_COMMAND = Pattern.compile("\\\\[a-zA-Z]+\\*?");

    /** Markdown links: keep the visible text, drop the URL. */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]*)]\\([^)]*\\)");

    /**
     * Everything that is not a letter, digit, or one of {@code + # .} becomes a space.
     * Those three survive because dropping them would silently destroy real keywords:
     * C++, C#, .NET and Node.js are all things a JD asks for by name.
     */
    private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9+#.]+");

    /**
     * Flattens resume markup into comparable plain text, padded with spaces so callers
     * can test for word boundaries with a simple contains check.
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return " ";
        }
        String result = MARKDOWN_LINK.matcher(text).replaceAll("$1");
        result = LATEX_COMMAND.matcher(result).replaceAll(" ");
        result = result.toLowerCase();
        result = NON_TOKEN.matcher(result).replaceAll(" ");
        result = result.replaceAll("\\s+", " ").trim();
        return " " + result + " ";
    }

    /**
     * Decides whether {@code keyword} is present in already-normalized resume text.
     *
     * @param keyword             the raw keyword from the JD, e.g. "Kubernetes", "CI/CD", "C++"
     * @param normalizedResume    output of {@link #normalize(String)}: lowercase, space
     *                            separated, padded with a leading and trailing space
     */
    public boolean matches(String keyword, String normalizedResume) {
        String needle = normalize(keyword).trim();
        if (needle.isEmpty()) {
            return false;
        }
        if (containsWhole(normalizedResume, needle)) {
            return true;
        }
        // Tolerate the singular/plural split ("API" vs "APIs", "microservice" vs
        // "microservices"). Full stemming is not worth the false positives it buys.
        String alternate = needle.endsWith("s")
                ? needle.substring(0, needle.length() - 1)
                : needle + "s";
        return containsWhole(normalizedResume, alternate);
    }

    /**
     * Whole-token containment. Both arguments are space-normalized and the haystack is
     * space-padded, so this matches on token boundaries rather than substrings -- "R"
     * does not match "React", and multi-word needles like "ci cd" match as a phrase.
     *
     * <p>Short common words can still collide ("Go" matching "go to market"). Erring
     * toward a miss is the wrong trade here anyway: a false positive tells the user
     * they are covered when they are not, and they find that out in the interview.
     */
    private static boolean containsWhole(String haystack, String needle) {
        return haystack.contains(" " + needle + " ");
    }

    /** Evaluates every keyword against both versions of the resume. */
    public List<KeywordMatch> match(List<JdKeyword> keywords, String originalText, String tailoredText) {
        String original = normalize(originalText);
        String tailored = normalize(tailoredText);
        return keywords.stream()
                .map(k -> new KeywordMatch(
                        k.keyword(),
                        k.importance(),
                        matches(k.keyword(), original),
                        matches(k.keyword(), tailored)))
                .toList();
    }

    /**
     * Weighted percentage of JD keywords present in the tailored resume.
     * Returns 0 when the JD yielded no keywords at all.
     */
    public int score(List<KeywordMatch> matches) {
        int total = matches.stream().mapToInt(m -> m.importance().weight()).sum();
        if (total == 0) {
            return 0;
        }
        int earned = matches.stream()
                .filter(KeywordMatch::presentInTailored)
                .mapToInt(m -> m.importance().weight())
                .sum();
        return Math.round((earned * 100f) / total);
    }
}
