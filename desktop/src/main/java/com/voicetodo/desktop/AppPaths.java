package com.voicetodo.desktop;

import java.nio.file.Path;

final class AppPaths {
    private AppPaths() {}

    static Path directory() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".toktodo")
                : Path.of(appData, "TokTodo");
        return base;
    }

    static Path data() { return directory().resolve("data.json"); }
    static Path backup() { return directory().resolve("data.backup.json"); }
    static Path credentials() { return directory().resolve("credentials.json"); }
    static Path token() { return directory().resolve("token.json"); }
}
