package com.voicetodo.test;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

final class TodoItem {
    final String id;
    String title;
    String originalVoiceText;
    long scheduledAt;
    long endAt;
    int reminderMinutes;
    String reminderOffsets;
    String category;
    boolean allDay;
    boolean multiDay;
    String eventType;
    String precision;
    String repeatFrequency;
    int repeatInterval;
    String repeatDays;
    String repeatMonthDays;
    int repeatSetPosition;
    long repeatUntil;
    int repeatCount;
    boolean important;
    boolean completed;
    final long createdAt;

    TodoItem(String id, String title, String originalVoiceText, long scheduledAt,
             long endAt, int reminderMinutes, String reminderOffsets, String category,
             boolean allDay, boolean multiDay,
             String eventType, String precision, String repeatFrequency, int repeatInterval,
             String repeatDays, String repeatMonthDays, int repeatSetPosition, long repeatUntil,
             int repeatCount, boolean important, boolean completed, long createdAt) {
        this.id = id;
        this.title = title;
        this.originalVoiceText = originalVoiceText;
        this.scheduledAt = scheduledAt;
        this.endAt = endAt;
        this.reminderMinutes = reminderMinutes;
        this.reminderOffsets = reminderOffsets;
        this.category = category;
        this.allDay = allDay;
        this.multiDay = multiDay;
        this.eventType = eventType;
        this.precision = precision;
        this.repeatFrequency = repeatFrequency;
        this.repeatInterval = repeatInterval;
        this.repeatDays = repeatDays;
        this.repeatMonthDays = repeatMonthDays;
        this.repeatSetPosition = repeatSetPosition;
        this.repeatUntil = repeatUntil;
        this.repeatCount = repeatCount;
        this.important = important;
        this.completed = completed;
        this.createdAt = createdAt;
    }

    static TodoItem fromParsed(String id, String source, ParsedCalendarEvent event,
                               ZoneId zoneId, long createdAt) {
        ParsedCalendarEvent.RepeatRule repeat = event.repeat;
        StringJoiner days = new StringJoiner(",");
        StringJoiner monthDays = new StringJoiner(",");
        if (repeat != null) {
            for (DayOfWeek day : repeat.byDays) days.add(day.name());
            for (Integer day : repeat.byMonthDays) monthDays.add(String.valueOf(day));
        }
        long repeatUntil = repeat == null || repeat.until == null ? 0L
                : repeat.until.atStartOfDay(zoneId).toInstant().toEpochMilli();
        return new TodoItem(
                id, event.title, source, event.startEpochMillis(zoneId), event.endEpochMillis(zoneId),
                event.reminderMinutes,
                event.allDay && event.reminderMinutes == 0 ? "" : String.valueOf(event.reminderMinutes),
                event.category, event.allDay, event.multiDay,
                event.eventType.name(), event.precision.name(),
                repeat == null ? "" : repeat.frequency,
                repeat == null ? 1 : repeat.interval,
                days.toString(), monthDays.toString(),
                repeat == null || repeat.bySetPosition == null ? 0 : repeat.bySetPosition,
                repeatUntil, repeat == null || repeat.count == null ? 0 : repeat.count,
                hasImportantKeyword(source), false, createdAt);
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("originalVoiceText", originalVoiceText);
        json.put("scheduledAt", scheduledAt);
        json.put("endAt", endAt);
        json.put("reminderMinutes", reminderMinutes);
        json.put("reminderOffsets", reminderOffsets);
        json.put("category", category);
        json.put("allDay", allDay);
        json.put("multiDay", multiDay);
        json.put("eventType", eventType);
        json.put("precision", precision);
        json.put("repeatFrequency", repeatFrequency);
        json.put("repeatInterval", repeatInterval);
        json.put("repeatDays", repeatDays);
        json.put("repeatMonthDays", repeatMonthDays);
        json.put("repeatSetPosition", repeatSetPosition);
        json.put("repeatUntil", repeatUntil);
        json.put("repeatCount", repeatCount);
        json.put("important", important);
        json.put("completed", completed);
        json.put("createdAt", createdAt);
        return json;
    }

    static TodoItem fromJson(JSONObject json) {
        boolean allDay = json.optBoolean("allDay");
        int legacyReminder = json.optInt("reminderMinutes", 0);
        String offsets = json.has("reminderOffsets")
                ? json.optString("reminderOffsets")
                : (allDay && legacyReminder == 0 ? "" : String.valueOf(legacyReminder));
        return new TodoItem(
                json.optString("id"),
                json.optString("title", "할 일"),
                json.optString("originalVoiceText"),
                json.optLong("scheduledAt"),
                json.optLong("endAt", json.optLong("scheduledAt")),
                legacyReminder,
                offsets,
                json.optString("category", "개인"),
                allDay,
                json.optBoolean("multiDay"),
                json.optString("eventType", "EVENT"),
                json.optString("precision", "EXACT"),
                json.optString("repeatFrequency"),
                Math.max(1, json.optInt("repeatInterval", 1)),
                json.optString("repeatDays"),
                json.optString("repeatMonthDays"),
                json.optInt("repeatSetPosition"),
                json.optLong("repeatUntil"),
                json.optInt("repeatCount"),
                json.optBoolean("important"),
                json.optBoolean("completed"),
                json.optLong("createdAt", System.currentTimeMillis())
        );
    }

    List<Integer> reminderOffsetList() {
        List<Integer> values = new ArrayList<>();
        if (reminderOffsets == null || reminderOffsets.isBlank()) return values;
        for (String value : reminderOffsets.split(",")) {
            try {
                int minutes = Integer.parseInt(value.trim());
                if (minutes >= 0 && !values.contains(minutes)) values.add(minutes);
            } catch (NumberFormatException ignored) {
            }
        }
        values.sort((left, right) -> Integer.compare(right, left));
        return values;
    }

    void setReminderOffsets(List<Integer> offsets) {
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer value : offsets) {
            if (value != null && value >= 0) unique.add(value);
        }
        List<Integer> sorted = new ArrayList<>(unique);
        sorted.sort((left, right) -> Integer.compare(right, left));
        StringJoiner values = new StringJoiner(",");
        for (Integer value : sorted) values.add(String.valueOf(value));
        reminderOffsets = values.toString();
        reminderMinutes = sorted.isEmpty() ? 0 : sorted.get(0);
    }

    private static boolean hasImportantKeyword(String source) {
        if (source == null) return false;
        String compact = source.replaceAll("\\s+", "");
        if (compact.contains("중요하지않") || compact.contains("안중요")) return false;
        return compact.contains("중요") || compact.contains("긴급") || compact.contains("최우선")
                || compact.contains("반드시") || compact.contains("꼭해야");
    }
}
