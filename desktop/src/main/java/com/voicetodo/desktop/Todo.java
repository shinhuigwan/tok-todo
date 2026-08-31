package com.voicetodo.desktop;

import java.util.UUID;

final class Todo {
    String id = UUID.randomUUID().toString();
    String title = "";
    long scheduledAt;
    int reminderMinutes;
    String category = "업무";
    boolean completed;
    long createdAt = System.currentTimeMillis();
    long updatedAt = createdAt;
    boolean deleted;
    String googleEventId = "";
}
