package com.voicetodo.test;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class MemoStore {
    private static final String PREFS = "voice_todo_test";
    private static final String KEY = "memos";
    private final SharedPreferences preferences;

    MemoStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<MemoItem> load() {
        List<MemoItem> memos = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY, "[]"));
            for (int index = 0; index < array.length(); index++) {
                memos.add(MemoItem.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
            // 손상된 메모 한 건 때문에 앱 전체가 실행되지 않는 상황을 막는다.
        }
        memos.sort(Comparator.comparingLong((MemoItem memo) -> memo.dateEpochDay)
                .thenComparingLong(memo -> memo.createdAt).reversed());
        return memos;
    }

    void save(List<MemoItem> memos) {
        JSONArray array = new JSONArray();
        for (MemoItem memo : memos) {
            try {
                array.put(memo.toJson());
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }
}
