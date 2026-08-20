package com.voicetodo.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public final class TodoItemReminderTest {
    @Test
    public void multipleReminderOffsetsAreUniqueAndOrdered() {
        TodoItem item = item("1주 전,1일 전,30분 전");

        item.setReminderOffsets(List.of(1_440, 10_080, 30, 1_440));

        assertEquals(List.of(10_080, 1_440, 30), item.reminderOffsetList());
        assertEquals("10080,1440,30", item.reminderOffsets);
        assertEquals(10_080, item.reminderMinutes);
    }

    @Test
    public void clearingOffsetsDisablesIndividualReminder() {
        TodoItem item = item("1440");

        item.setReminderOffsets(List.of());

        assertTrue(item.reminderOffsetList().isEmpty());
        assertEquals("", item.reminderOffsets);
    }

    @Test
    public void editingUpdatesTitleAndSchedule() {
        TodoItem item = item("30");
        ZoneId zoneId = ZoneId.of("Asia/Seoul");

        item.applyEdit("월간 보고", LocalDate.of(2026, 8, 24), LocalTime.of(10, 30),
                LocalDate.of(2026, 8, 25), LocalTime.of(11, 0), false, zoneId);

        assertEquals("월간 보고", item.title);
        assertEquals(LocalDate.of(2026, 8, 24),
                Instant.ofEpochMilli(item.scheduledAt).atZone(zoneId).toLocalDate());
        assertEquals(LocalTime.of(10, 30),
                Instant.ofEpochMilli(item.scheduledAt).atZone(zoneId).toLocalTime());
        assertEquals(LocalDate.of(2026, 8, 25),
                Instant.ofEpochMilli(item.endAt).atZone(zoneId).toLocalDate());
        assertTrue(item.multiDay);
        assertFalse(item.allDay);
    }

    @Test
    public void editingRejectsAnEndBeforeTheStart() {
        TodoItem item = item("");
        try {
            item.applyEdit("잘못된 일정", LocalDate.of(2026, 8, 25), LocalTime.of(10, 0),
                    LocalDate.of(2026, 8, 24), LocalTime.of(10, 0), false,
                    ZoneId.of("Asia/Seoul"));
            fail("Expected invalid edit to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("종료일은 시작일보다 빠를 수 없습니다.", expected.getMessage());
        }
    }

    private TodoItem item(String offsets) {
        return new TodoItem(
                "id", "일정", "일정", 2_000_000L, 2_000_000L, 0, offsets,
                "개인", false, false, "EVENT", "EXACT", "", 1,
                "", "", 0, 0, 0, false, false, 1_000_000L);
    }
}
