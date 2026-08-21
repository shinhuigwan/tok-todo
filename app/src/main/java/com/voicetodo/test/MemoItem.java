package com.voicetodo.test;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;

final class MemoItem {
    final String id;
    final String title;
    final String content;
    final long dateEpochDay;
    final long createdAt;

    MemoItem(String id, String title, String content, long dateEpochDay, long createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.dateEpochDay = dateEpochDay;
        this.createdAt = createdAt;
    }

    LocalDate date() {
        return LocalDate.ofEpochDay(dateEpochDay);
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("content", content);
        json.put("dateEpochDay", dateEpochDay);
        json.put("createdAt", createdAt);
        return json;
    }

    static MemoItem fromJson(JSONObject json) {
        return new MemoItem(
                json.optString("id"),
                json.optString("title", "메모"),
                json.optString("content"),
                json.optLong("dateEpochDay", LocalDate.now().toEpochDay()),
                json.optLong("createdAt", System.currentTimeMillis())
        );
    }
}
