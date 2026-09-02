package pl.foxmoderation.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {
    private static final Pattern PATTERN = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)min)?(?:(\\d+)s)?");

    private DurationUtil() {
    }

    public static Long parseDurationSeconds(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("perm")) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(input.toLowerCase());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Niepoprawny format czasu: " + input);
        }
        long days = group(matcher, 1);
        long hours = group(matcher, 2);
        long minutes = group(matcher, 3);
        long seconds = group(matcher, 4);
        long total = Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds).getSeconds();
        if (total <= 0) {
            throw new IllegalArgumentException("Czas musi być większy od 0");
        }
        return total;
    }

    private static long group(Matcher matcher, int index) {
        String value = matcher.group(index);
        return value == null || value.isBlank() ? 0L : Long.parseLong(value);
    }

    public static String formatDuration(Long seconds) {
        if (seconds == null) {
            return "Nigdy";
        }
        long remaining = seconds;
        long days = remaining / 86400;
        remaining %= 86400;
        long hours = remaining / 3600;
        remaining %= 3600;
        long minutes = remaining / 60;
        remaining %= 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) builder.append(days).append("d ");
        if (hours > 0 || days > 0) builder.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) builder.append(minutes).append("min ");
        if (remaining > 0 && days == 0) builder.append(remaining).append("s ");
        return builder.toString().trim();
    }
}
