package com.voicetodo.test;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TodoStore {
    private static final String PREFS = "voice_todo_test";
    private static final String KEY = "items";
    private final SharedPreferences preferences;

    TodoStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<TodoItem> load() {
        List<TodoItem> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                items.add(TodoItem.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // Corrupt test data is ignored so the prototype can still start.
        }
        items.sort(Comparator.comparingLong(item -> item.scheduledAt));
        return items;
    }

    void save(List<TodoItem> items) {
        JSONArray array = new JSONArray();
        for (TodoItem item : items) {
            try {
                array.put(item.toJson());
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }
}
