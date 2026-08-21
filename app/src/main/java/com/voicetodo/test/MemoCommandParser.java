package com.voicetodo.test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

final class MemoCommandParser {
    private static final Pattern COMMAND = Pattern.compile(
            "메모(?:장)?(?:에|로)?\\s*(?:" +
                    "해\\s*(?:줘(?:요)?|주세요|달라)|" +
                    "저장\\s*해\\s*(?:줘(?:요)?|주세요)|" +
                    "남겨\\s*(?:줘(?:요)?|주세요))");

    static boolean isMemoCommand(String source) {
        if (source == null || source.isBlank()) return false;
        return COMMAND.matcher(source).find();
    }

    static Draft parse(String source) {
        ZoneId zoneId = ZoneId.systemDefault();
        return parse(source, Clock.system(zoneId), zoneId);
    }

    static Draft parse(String source, Clock clock, ZoneId zoneId) {
        String cleaned = COMMAND.matcher(source == null ? "" : source).replaceAll(" ")
                .replaceAll("\\s+", " ").trim();
        ParsedCalendarEvent parsed = KoreanTodoParser.parse(
                cleaned, clock, zoneId, Locale.KOREAN);
        String title = parsed.title == null || parsed.title.isBlank() || "새 할 일".equals(parsed.title)
                ? summarizeTitle(cleaned) : parsed.title;
        if (title.isBlank()) title = "새 메모";
        return new Draft(parsed.startDate, title, cleaned);
    }

    static String summarizeTitle(String content) {
        if (content == null || content.isBlank()) return "";
        String firstLine = content.strip().split("\\R", 2)[0].replaceAll("\\s+", " ").trim();
        int limit = 28;
        return firstLine.length() <= limit ? firstLine : firstLine.substring(0, limit).trim() + "…";
    }

    static final class Draft {
        final LocalDate date;
        final String title;
        final String content;

        Draft(LocalDate date, String title, String content) {
            this.date = date;
            this.title = title;
            this.content = content;
        }
    }

    private MemoCommandParser() {}
}
