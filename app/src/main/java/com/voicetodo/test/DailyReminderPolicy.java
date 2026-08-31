package com.voicetodo.test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class DailyReminderPolicy {
    static List<TodoItem> overdueItems(List<TodoItem> items, LocalDate today, ZoneId zoneId) {
        List<TodoItem> overdue = new ArrayList<>();
        for (TodoItem item : items) {
            if (item.completed) continue;
            long dueAt = item.endAt > 0 ? item.endAt : item.scheduledAt;
            LocalDate dueDate = Instant.ofEpochMilli(dueAt).atZone(zoneId).toLocalDate();
            if (dueDate.isBefore(today)) overdue.add(item);
        }
        overdue.sort(Comparator.comparing((TodoItem item) -> !item.important)
                .thenComparingLong(item -> item.endAt > 0 ? item.endAt : item.scheduledAt));
        return overdue;
    }

    private DailyReminderPolicy() {}
}
