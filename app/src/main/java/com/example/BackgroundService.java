package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class BackgroundService extends Service {

    public static final String CHANNEL_SERVICE_ID = "rikichat_service_channel";
    public static final String CHANNEL_MESSAGES_ID = "rikichat_messages_channel";
    public static final String CHANNEL_CALLS_ID = "rikichat_calls_channel";

    private static final int NOTIFICATION_SERVICE_ID = 1001;
    private static final int NOTIFICATION_MESSAGE_ID = 1002;
    private static final int NOTIFICATION_CALL_ID = 2001;

    private DatabaseReference unreadRef;
    private ValueEventListener unreadListener;

    private DatabaseReference callRef;
    private ValueEventListener callListener;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        startServiceForeground();
        attachFirebaseListeners();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            // 1. Low Priority Channel for Foreground Service
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_SERVICE_ID,
                    "RikiChat Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps RikiChat connected for incoming calls and notifications");
            nm.createNotificationChannel(serviceChannel);

            // 2. High Priority Channel for Messages
            NotificationChannel messageChannel = new NotificationChannel(
                    CHANNEL_MESSAGES_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageChannel.setDescription("Notifications for new chat messages");
            messageChannel.enableVibration(true);
            nm.createNotificationChannel(messageChannel);

            // 3. Max Priority Channel for Calls (Bypass DND)
            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_CALLS_ID,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
            );
            callChannel.setDescription("Notifications and full-screen intents for incoming voice/video calls");
            callChannel.setBypassDnd(true);
            callChannel.enableVibration(true);
            Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build();
            callChannel.setSound(ringtone, audioAttributes);
            nm.createNotificationChannel(callChannel);
        }
    }

    private void startServiceForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
                .setContentTitle("RikiChat")
                .setContentText("Connected in background for calls and chats")
                .setSmallIcon(R.drawable.ic_chats)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_SERVICE_ID, notification);
        }
    }

    private void attachFirebaseListeners() {
        String uid = null;
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                uid = user.getUid();
            }
        } catch (Exception ignored) {}

        if (uid == null) {
            SharedPreferences sp = getSharedPreferences("rikichat_prefs", Context.MODE_PRIVATE);
            uid = sp.getString("current_uid", null);
        }

        if (uid == null) return;

        try {
            FirebaseDatabase db = FirebaseDatabase.getInstance();

            // 1. Listen to users/{uid}/unread
            unreadRef = db.getReference("users").child(uid).child("unread");
            unreadListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (MainActivity.isAppInForeground) {
                        return; // App in foreground, UI handles live messages
                    }
                    long totalUnread = 0;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Long count = child.getValue(Long.class);
                        if (count != null && count > 0) {
                            totalUnread += count;
                        }
                    }
                    if (totalUnread > 0) {
                        showUnreadMessageNotification(totalUnread);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            unreadRef.addValueEventListener(unreadListener);

            // 2. Listen to calls/{myUid} for incoming calls
            callRef = db.getReference("calls").child(uid);
            callListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) return;
                    Boolean accepted = snapshot.child("accepted").getValue(Boolean.class);
                    String from = snapshot.child("from").getValue(String.class);
                    String callerName = snapshot.child("callerName").getValue(String.class);
                    String callerEmoji = snapshot.child("callerEmoji").getValue(String.class);
                    String type = snapshot.child("type").getValue(String.class);

                    if (from != null && (accepted == null || !accepted)) {
                        showIncomingCallNotification(from, callerName != null ? callerName : "Someone", callerEmoji != null ? callerEmoji : "👤", type != null ? type : "voice");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            callRef.addValueEventListener(callListener);
        } catch (Exception ignored) {}
    }

    private void showUnreadMessageNotification(long count) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 10, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_MESSAGES_ID)
                .setContentTitle("RikiChat")
                .setContentText("You have " + count + " new unread message" + (count > 1 ? "s" : ""))
                .setSmallIcon(R.drawable.ic_chats)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_MESSAGE_ID, notification);
        }
    }

    private void showIncomingCallNotification(String fromUid, String callerName, String callerEmoji, String callType) {
        Intent fullScreenIntent = new Intent(this, MainActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        fullScreenIntent.putExtra("incoming_call", true);
        fullScreenIntent.putExtra("caller_uid", fromUid);
        fullScreenIntent.putExtra("caller_name", callerName);
        fullScreenIntent.putExtra("caller_emoji", callerEmoji);
        fullScreenIntent.putExtra("call_type", callType);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, 20, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_CALLS_ID)
                .setContentTitle("Incoming " + ("video".equalsIgnoreCase(callType) ? "Video" : "Voice") + " Call")
                .setContentText(callerEmoji + " " + callerName + " is calling you…")
                .setSmallIcon(R.drawable.ic_call)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_CALL_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (unreadRef != null && unreadListener != null) {
            unreadRef.removeEventListener(unreadListener);
        }
        if (callRef != null && callListener != null) {
            callRef.removeEventListener(callListener);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
