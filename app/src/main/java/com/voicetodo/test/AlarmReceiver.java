package com.voicetodo.test;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.app.Notification;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String id = intent.getStringExtra("id");
        String title = intent.getStringExtra("title");
        boolean important = intent.getBooleanExtra("important", false);
        int reminderMinutes = intent.getIntExtra("reminderMinutes", 0);
        Notification notification = new Notification.Builder(context, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(important ? "톡todo · 중요 일정" : "톡todo 알림")
                .setContentText(title == null ? "예정된 할 일이 있어요."
                        : (important ? "★ " : "") + title)
                .setSubText(reminderLabel(reminderMinutes))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        context.getSystemService(NotificationManager.class)
                .notify(id == null ? 1 : id.hashCode(), notification);
    }

    private String reminderLabel(int minutes) {
        if (minutes == 0) return "일정 시간";
        if (minutes % 10_080 == 0) return (minutes / 10_080) + "주 전";
        if (minutes % 1_440 == 0) return (minutes / 1_440) + "일 전";
        if (minutes % 60 == 0) return (minutes / 60) + "시간 전";
        return minutes + "분 전";
    }
}
