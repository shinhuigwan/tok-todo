package com.voicetodo.test;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class NotificationHelper {
    static final String CHANNEL_ID = "voice_todo_reminders";

    static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "할 일 알림", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("음성으로 저장한 일정 알림");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    static void schedule(Context context, TodoItem item) {
        cancel(context, item.id);
        long triggerAt = item.scheduledAt - item.reminderMinutes * 60_000L;
        if (triggerAt <= System.currentTimeMillis() || item.completed) return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("id", item.id);
        intent.putExtra("title", item.title);
        intent.putExtra("scheduledAt", item.scheduledAt);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode(item.id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }

    static void cancel(Context context, String id) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode(id), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            context.getSystemService(AlarmManager.class).cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static int requestCode(String id) {
        return id.hashCode() & 0x7fffffff;
    }

    private NotificationHelper() {}
}
