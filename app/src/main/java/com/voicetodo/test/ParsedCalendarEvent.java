package com.voicetodo.test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

final class ParsedCalendarEvent {
    enum Intent { CREATE_EVENT, UPDATE_EVENT, DELETE_EVENT, SEARCH_EVENT }
    enum EventType { EVENT, DEADLINE }
    enum Precision { EXACT, APPROXIMATE }

    static final class RepeatRule {
        String frequency;
        int interval = 1;
        final List<DayOfWeek> byDays = new ArrayList<>();
        final List<Integer> byMonthDays = new ArrayList<>();
        Integer bySetPosition;
        LocalDate until;
        Integer count;

        String summary() {
            if (frequency == null) return "";
            return switch (frequency) {
                case "DAILY" -> interval == 1 ? "매일" : interval + "일마다";
                case "WEEKLY" -> interval == 1 ? "매주" : interval + "주마다";
                case "MONTHLY" -> "매월";
                case "YEARLY" -> "매년";
                default -> frequency;
            };
        }
    }

    Intent intent = Intent.CREATE_EVENT;
    EventType eventType = EventType.EVENT;
    String title;
    LocalDate startDate;
    LocalDate endDate;
    LocalTime startTime;
    LocalTime endTime;
    boolean allDay;
    boolean multiDay;
    Integer durationMinutes;
    RepeatRule repeat;
    String dateExpression;
    String timeExpression;
    Precision precision = Precision.EXACT;
    double confidence = 1.0;
    boolean requiresConfirmation;
    String confirmationReason;
    String originalText;
    String normalizedText;
    int reminderMinutes;
    String category = "개인";

    long startEpochMillis(ZoneId zoneId) {
        LocalTime time = startTime == null ? LocalTime.of(9, 0) : startTime;
        return startDate.atTime(time).atZone(zoneId).toInstant().toEpochMilli();
    }

    long endEpochMillis(ZoneId zoneId) {
        LocalDate date = endDate == null ? startDate : endDate;
        LocalTime time = endTime != null ? endTime : (startTime == null ? LocalTime.of(9, 0) : startTime);
        return date.atTime(time).atZone(zoneId).toInstant().toEpochMilli();
    }
}
