package com.voicetodo.desktop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GoogleCalendarSync {
    private static final String API = "https://www.googleapis.com/calendar/v3";
    private static final String CALENDAR_NAME = "톡todo 업무일지";
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final GoogleOAuth oauth = new GoogleOAuth();

    SyncResult sync(AppState state, boolean interactive) throws Exception {
        String token = oauth.accessToken(interactive);
        String calendarId = ensureCalendar(state, token);

        pushDeletions(state, calendarId, token);
        List<JsonObject> remoteEvents = listEvents(calendarId, token);
        Map<String, JsonObject> remoteByLocalId = new HashMap<>();
        Set<String> activeGoogleIds = new HashSet<>();
        for (JsonObject event : remoteEvents) {
            String eventId = string(event, "id");
            activeGoogleIds.add(eventId);
            JsonObject props = privateProperties(event);
            if (!"true".equals(string(props, "tokTodoManaged"))) continue;
            String localId = string(props, "localId");
            if (!localId.isBlank()) remoteByLocalId.put(localId, event);
        }

        int pulled = 0;
        int pushed = 0;
        pulled += mergeRemoteTodos(state, remoteByLocalId);
        pulled += mergeRemoteJournals(state, remoteByLocalId);

        state.todos.removeIf(todo -> !todo.googleEventId.isBlank() && !activeGoogleIds.contains(todo.googleEventId));
        state.journals.removeIf(journal -> !journal.googleEventId.isBlank() && !activeGoogleIds.contains(journal.googleEventId));

        for (Todo todo : state.todos) {
            if (todo.deleted) continue;
            JsonObject remote = remoteByLocalId.get(todo.id);
            long remoteUpdated = remote == null ? -1 : propertyUpdatedAt(remote);
            if (remote == null) {
                JsonObject created = request("POST", API + "/calendars/" + enc(calendarId) + "/events",
                        todoEvent(todo), token);
                todo.googleEventId = string(created, "id");
                pushed++;
            } else if (todo.updatedAt > remoteUpdated) {
                request("PUT", API + "/calendars/" + enc(calendarId) + "/events/" + enc(string(remote, "id")),
                        todoEvent(todo), token);
                todo.googleEventId = string(remote, "id");
                pushed++;
            }
        }
        for (Journal journal : state.journals) {
            if (journal.deleted || journal.content.isBlank()) continue;
            JsonObject remote = remoteByLocalId.get(journal.id);
            long remoteUpdated = remote == null ? -1 : propertyUpdatedAt(remote);
            if (remote == null) {
                JsonObject created = request("POST", API + "/calendars/" + enc(calendarId) + "/events",
                        journalEvent(journal), token);
                journal.googleEventId = string(created, "id");
                pushed++;
            } else if (journal.updatedAt > remoteUpdated) {
                request("PUT", API + "/calendars/" + enc(calendarId) + "/events/" + enc(string(remote, "id")),
                        journalEvent(journal), token);
                journal.googleEventId = string(remote, "id");
                pushed++;
            }
        }
        return new SyncResult(pulled, pushed);
    }

    private void pushDeletions(AppState state, String calendarId, String token) throws Exception {
        for (Todo todo : new ArrayList<>(state.todos)) {
            if (!todo.deleted) continue;
            if (!todo.googleEventId.isBlank()) deleteEvent(calendarId, todo.googleEventId, token);
            state.todos.remove(todo);
        }
        for (Journal journal : new ArrayList<>(state.journals)) {
            if (!journal.deleted) continue;
            if (!journal.googleEventId.isBlank()) deleteEvent(calendarId, journal.googleEventId, token);
            state.journals.remove(journal);
        }
    }

    private int mergeRemoteTodos(AppState state, Map<String, JsonObject> remote) {
        Map<String, Todo> local = new HashMap<>();
        for (Todo todo : state.todos) local.put(todo.id, todo);
        int count = 0;
        for (JsonObject event : remote.values()) {
            JsonObject props = privateProperties(event);
            if (!"todo".equals(string(props, "type"))) continue;
            String id = string(props, "localId");
            Todo todo = local.get(id);
            long remoteUpdated = propertyUpdatedAt(event);
            if (todo == null) {
                todo = new Todo();
                todo.id = id;
                state.todos.add(todo);
            } else if (todo.deleted || todo.updatedAt >= remoteUpdated) {
                continue;
            }
            todo.title = string(event, "summary").replaceFirst("^[☐☑]\\s*", "");
            todo.scheduledAt = eventStartMillis(event);
            todo.category = defaultIfBlank(string(props, "category"), "업무");
            todo.completed = Boolean.parseBoolean(string(props, "completed"));
            todo.reminderMinutes = intValue(props, "reminderMinutes");
            todo.createdAt = longValue(props, "createdAt", remoteUpdated);
            todo.updatedAt = remoteUpdated;
            todo.googleEventId = string(event, "id");
            count++;
        }
        return count;
    }

    private int mergeRemoteJournals(AppState state, Map<String, JsonObject> remote) {
        Map<String, Journal> local = new HashMap<>();
        for (Journal journal : state.journals) local.put(journal.id, journal);
        int count = 0;
        for (JsonObject event : remote.values()) {
            JsonObject props = privateProperties(event);
            if (!"journal".equals(string(props, "type"))) continue;
            String id = string(props, "localId");
            Journal journal = local.get(id);
            long remoteUpdated = propertyUpdatedAt(event);
            if (journal == null) {
                journal = new Journal();
                journal.id = id;
                state.journals.add(journal);
            } else if (journal.deleted || journal.updatedAt >= remoteUpdated) {
                continue;
            }
            journal.date = defaultIfBlank(string(props, "date"), eventDate(event));
            journal.content = string(event, "description");
            journal.updatedAt = remoteUpdated;
            journal.googleEventId = string(event, "id");
            count++;
        }
        return count;
    }

    private String ensureCalendar(AppState state, String token) throws Exception {
        if (!state.calendarId.isBlank()) return state.calendarId;
        String pageToken = "";
        do {
            String url = API + "/users/me/calendarList?maxResults=250" +
                    (pageToken.isBlank() ? "" : "&pageToken=" + enc(pageToken));
            JsonObject response = request("GET", url, null, token);
            for (JsonElement element : array(response, "items")) {
                JsonObject item = element.getAsJsonObject();
                if (CALENDAR_NAME.equals(string(item, "summary"))) {
                    state.calendarId = string(item, "id");
                    return state.calendarId;
                }
            }
            pageToken = string(response, "nextPageToken");
        } while (!pageToken.isBlank());

        JsonObject body = new JsonObject();
        body.addProperty("summary", CALENDAR_NAME);
        body.addProperty("description", "톡todo가 일정과 일일업무일지를 동기화하는 전용 캘린더입니다.");
        body.addProperty("timeZone", ZoneId.systemDefault().getId());
        JsonObject created = request("POST", API + "/calendars", body, token);
        state.calendarId = string(created, "id");
        return state.calendarId;
    }

    private List<JsonObject> listEvents(String calendarId, String token) throws Exception {
        List<JsonObject> events = new ArrayList<>();
        String pageToken = "";
        do {
            String url = API + "/calendars/" + enc(calendarId) + "/events?maxResults=2500&showDeleted=false" +
                    (pageToken.isBlank() ? "" : "&pageToken=" + enc(pageToken));
            JsonObject response = request("GET", url, null, token);
            for (JsonElement element : array(response, "items")) events.add(element.getAsJsonObject());
            pageToken = string(response, "nextPageToken");
        } while (!pageToken.isBlank());
        return events;
    }

    private void deleteEvent(String calendarId, String eventId, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/calendars/" + enc(calendarId) + "/events/" + enc(eventId)))
                .header("Authorization", "Bearer " + token)
                .DELETE().timeout(Duration.ofSeconds(30)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 404 && response.statusCode() != 410 && response.statusCode() / 100 != 2) {
            throw new IOException("Google Calendar 삭제 실패 (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    private JsonObject todoEvent(Todo todo) {
        JsonObject event = new JsonObject();
        event.addProperty("summary", (todo.completed ? "☑ " : "☐ ") + todo.title);
        event.addProperty("description", "톡todo에서 관리하는 일정입니다.");
        JsonObject start = new JsonObject();
        start.addProperty("dateTime", Instant.ofEpochMilli(todo.scheduledAt).toString());
        start.addProperty("timeZone", ZoneId.systemDefault().getId());
        JsonObject end = new JsonObject();
        end.addProperty("dateTime", Instant.ofEpochMilli(todo.scheduledAt).plus(1, ChronoUnit.HOURS).toString());
        end.addProperty("timeZone", ZoneId.systemDefault().getId());
        event.add("start", start);
        event.add("end", end);
        JsonObject reminders = new JsonObject();
        reminders.addProperty("useDefault", todo.reminderMinutes <= 0);
        if (todo.reminderMinutes > 0) {
            JsonArray overrides = new JsonArray();
            JsonObject popup = new JsonObject();
            popup.addProperty("method", "popup");
            popup.addProperty("minutes", todo.reminderMinutes);
            overrides.add(popup);
            reminders.add("overrides", overrides);
        }
        event.add("reminders", reminders);
        event.add("extendedProperties", properties(Map.of(
                "tokTodoManaged", "true", "type", "todo", "localId", todo.id,
                "category", todo.category, "completed", String.valueOf(todo.completed),
                "reminderMinutes", String.valueOf(todo.reminderMinutes),
                "createdAt", String.valueOf(todo.createdAt), "updatedAt", String.valueOf(todo.updatedAt)
        )));
        return event;
    }

    private JsonObject journalEvent(Journal journal) {
        LocalDate date = LocalDate.parse(journal.date);
        JsonObject event = new JsonObject();
        event.addProperty("summary", "업무일지 " + journal.date);
        event.addProperty("description", journal.content);
        JsonObject start = new JsonObject();
        start.addProperty("date", date.toString());
        JsonObject end = new JsonObject();
        end.addProperty("date", date.plusDays(1).toString());
        event.add("start", start);
        event.add("end", end);
        event.add("extendedProperties", properties(Map.of(
                "tokTodoManaged", "true", "type", "journal", "localId", journal.id,
                "date", journal.date, "updatedAt", String.valueOf(journal.updatedAt)
        )));
        return event;
    }

    private JsonObject properties(Map<String, String> values) {
        JsonObject privateProps = new JsonObject();
        values.forEach(privateProps::addProperty);
        JsonObject extended = new JsonObject();
        extended.add("private", privateProps);
        return extended;
    }

    private JsonObject request(String method, String url, JsonObject body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
        if (body != null) builder.header("Content-Type", "application/json; charset=utf-8");
        if ("POST".equals(method)) builder.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8));
        else if ("PUT".equals(method)) builder.PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8));
        else builder.GET();
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Google Calendar 요청 실패 (HTTP " + response.statusCode() + "): " + response.body());
        }
        return response.body().isBlank() ? new JsonObject() : gson.fromJson(response.body(), JsonObject.class);
    }

    private static JsonObject privateProperties(JsonObject event) {
        if (!event.has("extendedProperties")) return new JsonObject();
        JsonObject extended = event.getAsJsonObject("extendedProperties");
        return extended.has("private") ? extended.getAsJsonObject("private") : new JsonObject();
    }

    private static long propertyUpdatedAt(JsonObject event) {
        long localValue = longValue(privateProperties(event), "updatedAt", 0);
        long googleValue = 0;
        try { googleValue = Instant.parse(string(event, "updated")).toEpochMilli(); }
        catch (Exception ignored) {}
        return Math.max(localValue, googleValue);
    }

    private static long eventStartMillis(JsonObject event) {
        JsonObject start = event.getAsJsonObject("start");
        try {
            if (start.has("dateTime")) return Instant.parse(start.get("dateTime").getAsString()).toEpochMilli();
            return LocalDate.parse(start.get("date").getAsString()).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private static String eventDate(JsonObject event) {
        return Instant.ofEpochMilli(eventStartMillis(event)).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static int intValue(JsonObject object, String key) {
        try { return Integer.parseInt(string(object, key)); }
        catch (Exception ignored) { return 0; }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try { return Long.parseLong(string(object, key)); }
        catch (Exception ignored) { return fallback; }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    record SyncResult(int pulled, int pushed) {}
}
