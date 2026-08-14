package com.pos.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts cardholder data and credentials before anything reaches an appender.
 *
 * <p>This matters far more now than it did when logs only went to a container's
 * stdout: once shipped to Elasticsearch, a logged PAN is indexed, replicated,
 * retained for the life of the ILM policy and searchable by anyone with Kibana
 * access. PCI-DSS requirement 3 prohibits storing the full PAN after
 * authorisation, and a log index is storage.
 *
 * <p>Two passes are applied, because sensitive values arrive in two shapes:
 * <ol>
 *   <li><b>Named fields</b> — Lombok {@code toString()} renders DTOs as
 *       {@code PaymentRequest(cardNumber=4111111111111111, cvv=123)}, so the
 *       field name is present and can be matched directly.</li>
 *   <li><b>Bare digit runs</b> — a PAN concatenated into a message string has no
 *       field name, so any 13-19 digit sequence passing a Luhn check is treated
 *       as a card number.</li>
 * </ol>
 *
 * <p>The Luhn check on the second pass is what stops this from mangling order
 * totals, timestamps and check numbers, which are also long digit runs.
 */
public final class SensitiveDataMasker {

    private static final String REDACTED = "***REDACTED***";

    /**
     * Field names whose values are replaced wholesale. Matches
     * {@code name=value}, {@code name: value} and {@code "name":"value"} so it
     * works against both Lombok output and raw JSON bodies.
     */
    private static final Pattern NAMED_FIELDS = Pattern.compile(
            "(?i)([\"']?\\b(?:card_?number|pan|cvv|cvc|cvv2|security_?code|"
                    + "password|passwd|pwd|secret|token|access_?token|refresh_?token|"
                    + "authorization|api_?key|private_?key|gift_?card_?number|"
                    + "account_?number|ssn|pin)\\b[\"']?\\s*[=:]\\s*)"
                    + "([\"']?)([^,;)\\s\"']+)([\"']?)");

    /** 13-19 digits, optionally separated by spaces or hyphens in groups. */
    private static final Pattern BARE_CARD_NUMBER = Pattern.compile(
            "\\b(?:\\d[ -]?){12,18}\\d\\b");

    private SensitiveDataMasker() {
    }

    /**
     * @param input raw text destined for a log appender; may be {@code null}
     * @return the same text with sensitive values redacted or truncated to the
     *         last four digits
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return maskBareCardNumbers(maskNamedFields(input));
    }

    private static String maskNamedFields(String input) {
        Matcher matcher = NAMED_FIELDS.matcher(input);
        StringBuilder out = new StringBuilder(input.length());

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String openQuote = matcher.group(2);
            String value = matcher.group(4);
            String closeQuote = matcher.group(5);

            // A card number keeps its last four so support can still identify
            // the transaction; everything else is redacted outright.
            String replacement = isCardField(prefix) ? lastFour(value) : REDACTED;

            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    prefix + openQuote + replacement + closeQuote));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String maskBareCardNumbers(String input) {
        Matcher matcher = BARE_CARD_NUMBER.matcher(input);
        StringBuilder out = new StringBuilder(input.length());

        while (matcher.find()) {
            String candidate = matcher.group();
            String digits = candidate.replaceAll("[ -]", "");
            // Without the Luhn check this would redact order ids, epoch
            // timestamps and check numbers, which are also long digit runs.
            String replacement = isLuhnValid(digits) ? lastFour(digits) : candidate;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean isCardField(String prefix) {
        String lower = prefix.toLowerCase();
        return lower.contains("card") || lower.contains("pan") || lower.contains("account");
    }

    private static String lastFour(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return REDACTED;
        }
        return "****" + digits.substring(digits.length() - 4);
    }

    private static boolean isLuhnValid(String digits) {
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
