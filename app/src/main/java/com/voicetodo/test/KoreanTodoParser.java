package com.voicetodo.test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KoreanTodoParser {
    static final class Result {
        String title;
        long scheduledAt;
        int reminderMinutes;
        String category;
    }

    private static final Pattern FULL_DATE = Pattern.compile("(?:(\\d{4})년\\s*)?(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern DAY_ONLY = Pattern.compile("(?<!월\\s)(\\d{1,2})일");
    private static final Pattern TIME = Pattern.compile("(?:(오전|오후|아침|점심|저녁|밤)\\s*)?(\\d{1,2})시(?:\\s*(\\d{1,2})분)?");
    private static final Pattern HALF_TIME = Pattern.compile("(?:(오전|오후|아침|점심|저녁|밤)\\s*)?(\\d{1,2})시\\s*반");
    private static final Pattern REMINDER = Pattern.compile("(\\d+)\\s*(분|시간|일)\\s*전");

    static Result parse(String source) {
        String text = source == null ? "" : source.trim();
        LocalDate nowDate = LocalDate.now();
        LocalDate date = parseDate(text, nowDate);
        LocalTime time = parseTime(text);

        Result result = new Result();
        result.scheduledAt = LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        result.reminderMinutes = parseReminder(text);
        result.category = guessCategory(text);
        result.title = cleanTitle(text);
        if (result.title.isBlank()) result.title = "새 할 일";
        return result;
    }

    private static LocalDate parseDate(String text, LocalDate today) {
        if (text.contains("모레")) return today.plusDays(2);
        if (text.contains("내일")) return today.plusDays(1);
        if (text.contains("오늘")) return today;

        Matcher full = FULL_DATE.matcher(text);
        if (full.find()) {
            int year = full.group(1) == null ? today.getYear() : Integer.parseInt(full.group(1));
            int month = Integer.parseInt(full.group(2));
            int day = Integer.parseInt(full.group(3));
            try {
                LocalDate parsed = LocalDate.of(year, month, day);
                if (full.group(1) == null && parsed.isBefore(today)) parsed = parsed.plusYears(1);
                return parsed;
            } catch (RuntimeException ignored) {
                return today;
            }
        }

        DayOfWeek weekday = findWeekday(text);
        if (weekday != null) {
            LocalDate next = today.with(TemporalAdjusters.nextOrSame(weekday));
            if (text.contains("다음 주")) next = next.plusWeeks(next.isAfter(today.plusDays(6)) ? 0 : 1);
            return next;
        }

        Matcher dayOnly = DAY_ONLY.matcher(text);
        if (dayOnly.find()) {
            int day = Integer.parseInt(dayOnly.group(1));
            try {
                LocalDate parsed = today.withDayOfMonth(day);
                return parsed.isBefore(today) ? parsed.plusMonths(1) : parsed;
            } catch (RuntimeException ignored) {
                return today;
            }
        }
        return today;
    }

    private static DayOfWeek findWeekday(String text) {
        String[] names = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};
        DayOfWeek[] values = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};
        for (int i = 0; i < names.length; i++) if (text.contains(names[i])) return values[i];
        return null;
    }

    private static LocalTime parseTime(String text) {
        Matcher half = HALF_TIME.matcher(text);
        if (half.find()) return normalizeTime(half.group(1), Integer.parseInt(half.group(2)), 30);
        Matcher matcher = TIME.matcher(text);
        if (matcher.find()) {
            int minute = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return normalizeTime(matcher.group(1), Integer.parseInt(matcher.group(2)), minute);
        }
        if (text.contains("아침")) return LocalTime.of(8, 0);
        if (text.contains("점심")) return LocalTime.of(12, 0);
        if (text.contains("오후")) return LocalTime.of(15, 0);
        if (text.contains("저녁")) return LocalTime.of(19, 0);
        if (text.contains("밤")) return LocalTime.of(21, 0);
        // 시간이 언급되지 않은 일정은 고정된 오전 9시가 아니라 입력 시점의
        // 현지 시각을 사용한다. 날짜만 말한 경우에도 과거 시각으로 저장되지 않는다.
        return LocalTime.now().withSecond(0).withNano(0);
    }

    private static LocalTime normalizeTime(String period, int hour, int minute) {
        if (period != null) {
            boolean pm = period.equals("오후") || period.equals("저녁") || period.equals("밤");
            if (pm && hour < 12) hour += 12;
            if ((period.equals("오전") || period.equals("아침")) && hour == 12) hour = 0;
            if (period.equals("점심") && hour < 11) hour += 12;
        }
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        return LocalTime.of(hour, minute);
    }

    private static int parseReminder(String text) {
        Matcher matcher = REMINDER.matcher(text);
        if (!matcher.find()) return 0;
        int amount = Integer.parseInt(matcher.group(1));
        return switch (matcher.group(2)) {
            case "시간" -> amount * 60;
            case "일" -> amount * 24 * 60;
            default -> amount;
        };
    }

    private static String guessCategory(String text) {
        String lower = text.toLowerCase(Locale.KOREAN);
        if (containsAny(lower, "회의", "보고서", "업무", "회사", "프로젝트")) return "업무";
        if (containsAny(lower, "병원", "치과", "운동", "약", "검진")) return "건강";
        if (containsAny(lower, "구매", "장보기", "사기", "마트")) return "쇼핑";
        if (containsAny(lower, "만나", "약속", "예약", "미팅")) return "약속";
        return "개인";
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static String cleanTitle(String text) {
        String cleaned = text;
        cleaned = FULL_DATE.matcher(cleaned).replaceAll(" ");
        cleaned = HALF_TIME.matcher(cleaned).replaceAll(" ");
        cleaned = TIME.matcher(cleaned).replaceAll(" ");
        cleaned = REMINDER.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("오늘|내일|모레|이번 주|다음 주", " ");
        cleaned = cleaned.replaceAll("월요일|화요일|수요일|목요일|금요일|토요일|일요일", " ");
        cleaned = cleaned.replaceAll("알림|알려줘|알려 줘|저장해 줘|저장해줘|일정에|일정으로|추가해 줘|추가해줘", " ");
        cleaned = cleaned.replaceAll("(^|\\s)(에|까지|부터)(?=\\s|$)", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.replaceAll("^[,·\\-]|[,·\\-]$", "").trim();
    }
}
