package lab4.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiiSanitizer {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)([a-z0-9._%+-]+)@([a-z0-9.-]+\\.[a-z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{10,12}");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)\"(password|token|secret|apiKey)\"\\s*:\\s*\"[^\"]+\"");

    public static String sanitize(String message) {
        if (message == null) return null;

        // Повне редагування секретів
        String result = SECRET_PATTERN.matcher(message).replaceAll("\"$1\":\"[REDACTED]\"");

        // Маскування Email (j***n@example.com)
        Matcher emailMatcher = EMAIL_PATTERN.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (emailMatcher.find()) {
            String name = emailMatcher.group(1);
            String domain = emailMatcher.group(2);
            String maskedName = name.length() > 2 ? name.charAt(0) + "***" + name.charAt(name.length() - 1) : "***";
            emailMatcher.appendReplacement(sb, maskedName + "@" + domain);
        }
        emailMatcher.appendTail(sb);

        // Маскування Телефонів (+380*****12)
        Matcher phoneMatcher = PHONE_PATTERN.matcher(sb.toString());
        StringBuffer sbPhone = new StringBuffer();
        while (phoneMatcher.find()) {
            String phone = phoneMatcher.group();
            String maskedPhone = phone.substring(0, 4) + "*****" + phone.substring(phone.length() - 2);
            phoneMatcher.appendReplacement(sbPhone, maskedPhone);
        }
        phoneMatcher.appendTail(sbPhone);

        return sbPhone.toString();
    }
}