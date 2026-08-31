package com.voicetodo.test;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class DailyReminderReceiver extends BroadcastReceiver {
    private static final int MAX_VISIBLE_TITLES = 5;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.createChannel(context);

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            NotificationHelper.scheduleNextDailySummary(context);
            return;
        }

        try {
            showSummaryIfNeeded(context);
        } finally {
            NotificationHelper.scheduleNextDailySummary(context);
        }
    }

    private void showSummaryIfNeeded(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        List<TodoItem> overdue = DailyReminderPolicy.overdueItems(
                new TodoStore(context).load(), LocalDate.now(), ZoneId.systemDefault());
        if (overdue.isEmpty() || !NotificationHelper.canNotify(context)) {
            manager.cancel(NotificationHelper.DAILY_NOTIFICATION_ID);
            return;
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, NotificationHelper.DAILY_NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.InboxStyle style = new Notification.InboxStyle()
                .setBigContentTitle("미완료 일정 " + overdue.size() + "개가 남아 있어요");
        int visible = Math.min(MAX_VISIBLE_TITLES, overdue.size());
        int importantCount = 0;
        for (TodoItem item : overdue) if (item.important) importantCount++;
        for (int i = 0; i < visible; i++) {
            TodoItem item = overdue.get(i);
            style.addLine((item.important ? "★ " : "• ") + item.title);
        }
        if (overdue.size() > visible) style.addLine("외 " + (overdue.size() - visible) + "개");

        String content = overdue.size() == 1
                ? overdue.get(0).title
                : overdue.get(0).title + " 외 " + (overdue.size() - 1) + "개";
        Notification notification = new Notification.Builder(context, NotificationHelper.DAILY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(importantCount > 0
                        ? "톡todo · 중요 " + importantCount + "개 포함"
                        : "톡todo · 오전 9시 미완료 요약")
                .setContentText(content)
                .setStyle(style)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setNumber(overdue.size())
                .build();
        manager.notify(NotificationHelper.DAILY_NOTIFICATION_ID, notification);
    }
}
