package com.voicetodo.desktop;

import java.time.LocalDate;
import java.util.UUID;

final class Journal {
    String id = UUID.randomUUID().toString();
    String date = LocalDate.now().toString();
    String content = "";
    long updatedAt = System.currentTimeMillis();
    boolean deleted;
    String googleEventId = "";
}
