package com.voicetodo.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class LocalStore {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    synchronized AppState load() throws IOException {
        Files.createDirectories(AppPaths.directory());
        if (!Files.exists(AppPaths.data())) return new AppState();
        AppState state = gson.fromJson(Files.readString(AppPaths.data(), StandardCharsets.UTF_8), AppState.class);
        if (state == null) state = new AppState();
        if (state.todos == null) state.todos = new java.util.ArrayList<>();
        if (state.journals == null) state.journals = new java.util.ArrayList<>();
        if (state.calendarId == null) state.calendarId = "";
        return state;
    }

    synchronized void save(AppState state) throws IOException {
        Files.createDirectories(AppPaths.directory());
        if (Files.exists(AppPaths.data())) {
            Files.copy(AppPaths.data(), AppPaths.backup(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(AppPaths.data(), gson.toJson(state), StandardCharsets.UTF_8);
    }
}
