package com.voicetodo.desktop;

import java.util.ArrayList;
import java.util.List;

final class AppState {
    List<Todo> todos = new ArrayList<>();
    List<Journal> journals = new ArrayList<>();
    String calendarId = "";
}
