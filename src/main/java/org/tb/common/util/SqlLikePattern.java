package org.tb.common.util;

import java.util.regex.Pattern;

/**
 * An SQL {@code LIKE <pattern>%} comparison evaluated in Java.
 *
 * <p>Some columns hold a value that the reporting SQL interpolates into a {@code LIKE} pattern with
 * a trailing {@code %} rather than comparing it for equality — {@code order_pricing.suborder_sign}
 * is the case this was written for. Wherever the application resolves such a value itself it has to
 * apply the very same rule, otherwise application and report disagree.
 *
 * <p>{@code %} and {@code _} are honoured as wildcards, everything else is literal. Matching is case
 * sensitive, which is what the {@code utf8mb3_bin} collation of those columns does. Backslash
 * escaping of a literal {@code %} is not supported; no stored value uses it.
 */
public final class SqlLikePattern {

    private final String pattern;
    private final Pattern regex;

    private SqlLikePattern(String pattern, Pattern regex) {
        this.pattern = pattern;
        this.regex = regex;
    }

    /**
     * Compiles {@code LIKE pattern%}. A {@code null} or empty pattern matches everything, mirroring
     * the {@code IFNULL(..., '')} the reporting SQL wraps the column in.
     */
    public static SqlLikePattern startingWith(String pattern) {
        var value = pattern == null ? "" : pattern;
        return new SqlLikePattern(value, Pattern.compile(toRegex(value), Pattern.DOTALL));
    }

    public boolean matches(String value) {
        return value != null && regex.matcher(value).matches();
    }

    /**
     * Length of the raw pattern. Used to rank matches by specificity — the longer pattern is the
     * more specific one, and an empty pattern is the least specific of all.
     */
    public int length() {
        return pattern.length();
    }

    private static String toRegex(String pattern) {
        var regex = new StringBuilder();
        var literal = new StringBuilder();
        for (var c : pattern.toCharArray()) {
            if (c == '%' || c == '_') {
                appendLiteral(regex, literal);
                regex.append(c == '%' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        appendLiteral(regex, literal);
        return regex.append(".*").toString(); // the trailing % of LIKE pattern%
    }

    private static void appendLiteral(StringBuilder regex, StringBuilder literal) {
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
            literal.setLength(0);
        }
    }

}
