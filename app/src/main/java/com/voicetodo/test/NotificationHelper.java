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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

final class NotificationHelper {
    static final String CHANNEL_ID = "voice_todo_reminders";
    static final String DAILY_CHANNEL_ID = "voice_todo_daily_summary";
    static final String ACTION_DAILY_SUMMARY = "com.voicetodo.test.DAILY_SUMMARY";
    static final int DAILY_NOTIFICATION_ID = 9_000_001;
    private static final int DAILY_REQUEST_CODE = 9_000_002;
    private static final int MAX_REMINDERS_PER_ITEM = 24;

    static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "할 일 알림", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("음성으로 저장한 일정 알림");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);

        NotificationChannel dailyChannel = new NotificationChannel(
                DAILY_CHANNEL_ID, "오전 9시 미완료 요약", NotificationManager.IMPORTANCE_HIGH);
        dailyChannel.setDescription("완료하지 않은 지난 일정을 매일 오전 9시에 한 번에 알려줍니다");
        dailyChannel.enableVibration(true);
        manager.createNotificationChannel(dailyChannel);
    }

    static int schedule(Context context, TodoItem item) {
        cancel(context, item.id);
        if (item.completed) return 0;

        List<Integer> offsets = item.reminderOffsetList();
        int scheduled = 0;
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        for (int slot = 0; slot < offsets.size() && slot < MAX_REMINDERS_PER_ITEM; slot++) {
            int offset = offsets.get(slot);
            long triggerAt = item.scheduledAt - offset * 60_000L;
            if (triggerAt <= System.currentTimeMillis()) continue;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("id", item.id);
            intent.putExtra("title", item.title);
            intent.putExtra("important", item.important);
            intent.putExtra("reminderMinutes", offset);
            intent.putExtra("scheduledAt", item.scheduledAt);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode(item.id, slot), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            setAlarm(alarmManager, triggerAt, pendingIntent);
            scheduled++;
        }
        return scheduled;
    }

    static void cancel(Context context, String id) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        // 0.8 이하 버전에서 사용하던 단일 알림도 함께 취소한다.
        cancelPending(context, alarmManager, requestCode(id));
        for (int slot = 0; slot < MAX_REMINDERS_PER_ITEM; slot++) {
            cancelPending(context, alarmManager, requestCode(id, slot));
        }
    }

    private static void cancelPending(Context context, AlarmManager alarmManager, int requestCode) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    static void scheduleNextDailySummary(Context context) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = LocalDate.now(zoneId).atTime(9, 0).atZone(zoneId);
        if (!next.isAfter(now)) next = next.plusDays(1);

        Intent intent = new Intent(context, DailyReminderReceiver.class)
                .setAction(ACTION_DAILY_SUMMARY);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, DAILY_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        long triggerAt = next.toInstant().toEpochMilli();
        setAlarm(alarmManager, triggerAt, pendingIntent);
    }

    static void clearDailySummary(Context context) {
        context.getSystemService(NotificationManager.class).cancel(DAILY_NOTIFICATION_ID);
    }

    static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static int requestCode(String id) {
        return id.hashCode() & 0x7fffffff;
    }

    private static int requestCode(String id, int slot) {
        return (id + "#reminder#" + slot).hashCode() & 0x7fffffff;
    }

    private static void setAlarm(AlarmManager alarmManager, long triggerAt, PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            // 별도 설정을 요구하지 않는 편의성 우선 모드. 절전 정책에 따라 몇 분 늦을 수 있다.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    private NotificationHelper() {}
}
