package com.waangu.platform.guard;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Security guard that prevents PII (Personally Identifiable Information) leakage.
 * <p>
 * Validates that data structures (typically response maps) do not contain
 * sensitive information that should not be exposed to external systems like
 * chat widgets or logging services.
 * </p>
 * <p>
 * Blocked PII categories:
 * <ul>
 *   <li>Financial: IBAN, card numbers, CVV, PAN, account numbers</li>
 *   <li>Identity: Passport, national ID, SSN</li>
 *   <li>Contact: Phone numbers, email addresses, physical addresses</li>
 *   <li>Personal: Full names, date of birth</li>
 * </ul>
 * </p>
 * <p>
 * This guard is used by {@link com.waangu.platform.support.SupportContextController}
 * to ensure chat support context does not leak sensitive data.
 * </p>
 */
@Component
public class PiiGuard {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "iban",
            "card_number", "cardnumber", "card-number",
            "pan",
            "cvv", "cvc", "cvv2",
            "track2",
            "passport", "passport_number",
            "national_id", "nationalid",
            "ssn", "social_security",
            "phone", "phone_number", "phonenumber", "telephone",
            "mobile", "mobile_number",
            "email", "email_address",
            "full_name", "fullname", "first_name", "last_name",
            "firstname", "lastname",
            "address", "street_address", "postal_address",
            "date_of_birth", "dob", "birthdate",
            "account_number", "accountnumber",
            "routing_number", "routingnumber",
            "swift", "bic",
            "pin", "pin_code",
            "password", "secret", "credential"
    );

    private static final Pattern IBAN_PATTERN = Pattern.compile(
            "[A-Z]{2}[0-9]{2}[A-Z0-9]{4,30}", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b[0-9]{13,19}\\b"
    );

    // private static final Pattern PHONE_PATTERN = Pattern.compile(
    //         "\\+?[0-9]{10,15}"
    // );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    /**
     * Validates that the given context map does not contain PII.
     *
     * @param ctx The context map to validate
     * @throws IllegalStateException if PII is detected
     */
    public void assertNoPII(Map<String, Object> ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return;
        }

        String serialized = ctx.toString().toLowerCase();

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (serialized.contains(keyword)) {
                throw new IllegalStateException("PII_FORBIDDEN_IN_CONTEXT: keyword '" + keyword + "' detected");
            }
        }

        for (Map.Entry<String, Object> entry : ctx.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();

            for (String keyword : FORBIDDEN_KEYWORDS) {
                if (key.contains(keyword)) {
                    throw new IllegalStateException("PII_FORBIDDEN_IN_CONTEXT: field '" + entry.getKey() + "' is not allowed");
                }
            }

            if (value != null) {
                String valueStr = value.toString();
                
                if (IBAN_PATTERN.matcher(valueStr).find()) {
                    throw new IllegalStateException("PII_FORBIDDEN_IN_CONTEXT: IBAN pattern detected in field '" + entry.getKey() + "'");
                }
                
                if (CARD_PATTERN.matcher(valueStr).find() && valueStr.length() >= 13) {
                    throw new IllegalStateException("PII_FORBIDDEN_IN_CONTEXT: Card number pattern detected in field '" + entry.getKey() + "'");
                }
                
                if (EMAIL_PATTERN.matcher(valueStr).find()) {
                    throw new IllegalStateException("PII_FORBIDDEN_IN_CONTEXT: Email pattern detected in field '" + entry.getKey() + "'");
                }
            }
        }
    }

    /**
     * Checks if the given string contains potential PII.
     *
     * @param text The text to check
     * @return true if PII is detected, false otherwise
     */
    public boolean containsPII(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String lower = text.toLowerCase();
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }

        return IBAN_PATTERN.matcher(text).find()
                || (CARD_PATTERN.matcher(text).find() && text.length() >= 13)
                || EMAIL_PATTERN.matcher(text).find();
    }
}
