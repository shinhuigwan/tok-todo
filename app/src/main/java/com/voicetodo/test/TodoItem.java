package com.voicetodo.test;

import org.json.JSONException;
import org.json.JSONObject;

final class TodoItem {
    final String id;
    String title;
    String originalVoiceText;
    long scheduledAt;
    int reminderMinutes;
    String category;
    boolean completed;
    final long createdAt;

    TodoItem(String id, String title, String originalVoiceText, long scheduledAt,
             int reminderMinutes, String category, boolean completed, long createdAt) {
        this.id = id;
        this.title = title;
        this.originalVoiceText = originalVoiceText;
        this.scheduledAt = scheduledAt;
        this.reminderMinutes = reminderMinutes;
        this.category = category;
        this.completed = completed;
        this.createdAt = createdAt;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("originalVoiceText", originalVoiceText);
        json.put("scheduledAt", scheduledAt);
        json.put("reminderMinutes", reminderMinutes);
        json.put("category", category);
        json.put("completed", completed);
        json.put("createdAt", createdAt);
        return json;
    }

    static TodoItem fromJson(JSONObject json) {
        return new TodoItem(
                json.optString("id"),
                json.optString("title", "할 일"),
                json.optString("originalVoiceText"),
                json.optLong("scheduledAt"),
                json.optInt("reminderMinutes", 0),
                json.optString("category", "개인"),
                json.optBoolean("completed"),
                json.optLong("createdAt", System.currentTimeMillis())
        );
    }
}
