package com.voicetodo.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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

    private TodoItem item(String offsets) {
        return new TodoItem(
                "id", "일정", "일정", 2_000_000L, 2_000_000L, 0, offsets,
                "개인", false, false, "EVENT", "EXACT", "", 1,
                "", "", 0, 0, 0, false, false, 1_000_000L);
    }
}
