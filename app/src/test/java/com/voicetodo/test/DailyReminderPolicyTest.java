package com.voicetodo.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class DailyReminderPolicyTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test
    public void onlyUnfinishedItemsBeforeTodayAreIncluded() {
        TodoItem yesterdayOpen = item("어제 미완료", TODAY.minusDays(1), false);
        TodoItem yesterdayDone = item("어제 완료", TODAY.minusDays(1), true);
        TodoItem todayOpen = item("오늘 일정", TODAY, false);
        TodoItem tomorrowOpen = item("내일 일정", TODAY.plusDays(1), false);

        List<TodoItem> result = DailyReminderPolicy.overdueItems(
                List.of(yesterdayOpen, yesterdayDone, todayOpen, tomorrowOpen), TODAY, SEOUL);

        assertEquals(1, result.size());
        assertEquals("어제 미완료", result.get(0).title);
    }

    @Test
    public void multiDayItemBecomesOverdueAfterItsFinalDay() {
        TodoItem range = item("출장", TODAY.minusDays(2), false);
        range.multiDay = true;
        range.endAt = TODAY.atTime(9, 0).atZone(SEOUL).toInstant().toEpochMilli();

        assertTrue(DailyReminderPolicy.overdueItems(List.of(range), TODAY, SEOUL).isEmpty());
        assertEquals(1, DailyReminderPolicy.overdueItems(
                List.of(range), TODAY.plusDays(1), SEOUL).size());
    }

    @Test
    public void importantItemsArePlacedFirstInTheSummary() {
        TodoItem normal = item("일반 일정", TODAY.minusDays(2), false);
        TodoItem important = item("중요 일정", TODAY.minusDays(1), false);
        important.important = true;

        List<TodoItem> result = DailyReminderPolicy.overdueItems(
                List.of(normal, important), TODAY, SEOUL);

        assertEquals("중요 일정", result.get(0).title);
    }

    private TodoItem item(String title, LocalDate date, boolean completed) {
        long at = date.atTime(9, 0).atZone(SEOUL).toInstant().toEpochMilli();
        return new TodoItem(
                title, title, title, at, at, 0, "", "개인", true, false,
                "EVENT", "EXACT", "", 1, "", "", 0, 0, 0,
                false, completed, System.currentTimeMillis());
    }
}
