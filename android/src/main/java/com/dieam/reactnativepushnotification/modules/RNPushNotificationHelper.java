package com.dieam.reactnativepushnotification.modules;


import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.facebook.react.bridge.ReadableMap;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Arrays;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;
import static com.dieam.reactnativepushnotification.modules.RNPushNotificationAttributes.fromJson;



public class RNPushNotificationHelper {
    public static final String PREFERENCES_KEY = "rn_push_notification";
    private static final long DEFAULT_VIBRATION = 300L;

    private Context context;
    private final SharedPreferences scheduledNotificationsPersistence;
    private static final int ONE_MINUTE = 60 * 1000;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;
    private static final long ONE_DAY = 24 * ONE_HOUR;

    /* = = = = = = NOTIFICATION STACKING = = = = = = */
    public int currentNotificationID = 0;
    public static int combinedNotificationCounter = 0;
    /* = = = = = = NOTIFICATION STACKING = = = = = = */


    public RNPushNotificationHelper(Application context) {
        this.context = context;
        this.scheduledNotificationsPersistence = context.getSharedPreferences(RNPushNotificationHelper.PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public Class getMainActivityClass() {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private AlarmManager getAlarmManager() {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private PendingIntent toScheduleNotificationIntent(Bundle bundle) {
        int notificationID = Integer.parseInt(bundle.getString("id"));

        Intent notificationIntent = new Intent(context, RNPushNotificationPublisher.class);
        notificationIntent.putExtra(RNPushNotificationPublisher.NOTIFICATION_ID, notificationID);
        notificationIntent.putExtras(bundle);

        return PendingIntent.getBroadcast(context, notificationID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void sendNotificationScheduled(Bundle bundle) {
        Class intentClass = getMainActivityClass();
        if (intentClass == null) {
            Log.e(LOG_TAG, "No activity class found for the scheduled notification");
            return;
        }

        if (bundle.getString("message") == null) {
            Log.e(LOG_TAG, "No message specified for the scheduled notification");
            return;
        }

        if (bundle.getString("id") == null) {
            Log.e(LOG_TAG, "No notification ID specified for the scheduled notification");
            return;
        }

        double fireDate = bundle.getDouble("fireDate");
        if (fireDate == 0) {
            Log.e(LOG_TAG, "No date specified for the scheduled notification");
            return;
        }

        RNPushNotificationAttributes notificationAttributes = new RNPushNotificationAttributes(bundle);
        String id = notificationAttributes.getId();

        Log.d(LOG_TAG, "Storing push notification with id " + id);

        SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
        editor.putString(id, notificationAttributes.toJson().toString());
        commit(editor);

        boolean isSaved = scheduledNotificationsPersistence.contains(id);
        if (!isSaved) {
            Log.e(LOG_TAG, "Failed to save " + id);
        }

        sendNotificationScheduledCore(bundle);
    }

    public void sendNotificationScheduledCore(Bundle bundle) {
        long fireDate = (long) bundle.getDouble("fireDate");

        // If the fireDate is in past, this will fire immediately and show the
        // notification to the user
        PendingIntent pendingIntent = toScheduleNotificationIntent(bundle);

        Log.d(LOG_TAG, String.format("Setting a notification with id %s at time %s",
                bundle.getString("id"), Long.toString(fireDate)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getAlarmManager().setExact(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
        } else {
            getAlarmManager().set(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
        }
    }

    public void sendToNotificationCentre(Bundle bundle) {
        try {
          Log.d(LOG_TAG, "---------------------------------------------------------------------------------");
            Class intentClass = getMainActivityClass();
            if (intentClass == null) {
                Log.e(LOG_TAG, "No activity class found for the notification");
                return;
            }

            if (bundle.getString("message") == null) {
                // this happens when a 'data' notification is received - we do not synthesize a local notification in this case
                Log.d(LOG_TAG, "Cannot send to notification centre because there is no 'message' field in: " + bundle);
                return;
            }

            String notificationIdString = bundle.getString("id");
            if (notificationIdString == null) {
                Log.e(LOG_TAG, "No notification ID specified for the notification");
                return;
            }

            Resources res = context.getResources();
            String packageName = context.getPackageName();

            String title = bundle.getString("title");
            if (title == null) {
                ApplicationInfo appInfo = context.getApplicationInfo();
                title = context.getPackageManager().getApplicationLabel(appInfo).toString();
            }

            combinedNotificationCounter++;
            NotificationCompat.Builder notification = new NotificationCompat.Builder(context)
                    .setContentTitle(title)
                    .setTicker(bundle.getString("ticker"))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setGroup(bundle.getString("group"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(bundle.getBoolean("autoCancel", true))
                    .setGroupSummary(true)
                    .setContentText(combinedNotificationCounter + " new messages");

            String largeIcon = bundle.getString("largeIcon");

            int smallIconResId;
            int largeIconResId;

            String smallIcon = bundle.getString("smallIcon");

            if (smallIcon != null) {
                smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
            } else {
                smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }

            if (smallIconResId == 0) {
                smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

                if (smallIconResId == 0) {
                    smallIconResId = android.R.drawable.ic_dialog_info;
                }
            }

            if (largeIcon != null) {
                largeIconResId = res.getIdentifier(largeIcon, "mipmap", packageName);
            } else {
                largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
            }

            Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

            if (largeIconResId != 0 && (largeIcon != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
                notification.setLargeIcon(largeIconBitmap);
            }
            notification.setSmallIcon(smallIconResId);
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(title);
            // inboxStyle.setSummaryText("");
            for (int i = 0; i < combinedNotificationCounter; i++) {
                // inboxStyle.addLine("This is Test" + i);
              inboxStyle.addLine(bundle.getString("message"));
            }
            currentNotificationID = 500;
            notification.setStyle(inboxStyle);
            Intent intent = new Intent(context, intentClass);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationManager notificationManager = notificationManager();

            notification.setContentIntent(pendingIntent);

            Notification notificationNew = notification.build();
            notificationNew.flags = Notification.FLAG_AUTO_CANCEL;
            notificationNew.defaults = Notification.DEFAULT_SOUND;
            currentNotificationID++;
            int notificationId = currentNotificationID;
            if (notificationId == Integer.MAX_VALUE - 1)
                notificationId = 0;
            notificationManager.notify(notificationId, notificationNew);
        } catch (Exception e) {
            Log.e(LOG_TAG, "failed to send push notification", e);
        }
    }

    public static void resetReadCountHelper() {
      Log.d(LOG_TAG, "--------------------------------------resetReadCountPushNotification-------------------------------------------");
      RNPushNotificationHelper.combinedNotificationCounter = 0;
    }

    private void scheduleNextNotificationIfRepeating(Bundle bundle) {
        String repeatType = bundle.getString("repeatType");
        long repeatTime = (long) bundle.getDouble("repeatTime");

        if (repeatType != null) {
            long fireDate = (long) bundle.getDouble("fireDate");

            boolean validRepeatType = Arrays.asList("time", "week", "day", "hour", "minute").contains(repeatType);

            // Sanity checks
            if (!validRepeatType) {
                Log.w(LOG_TAG, String.format("Invalid repeatType specified as %s", repeatType));
                return;
            }

            if ("time".equals(repeatType) && repeatTime <= 0) {
                Log.w(LOG_TAG, "repeatType specified as time but no repeatTime " +
                        "has been mentioned");
                return;
            }

            long newFireDate = 0;

            switch (repeatType) {
                case "time":
                    newFireDate = fireDate + repeatTime;
                    break;
                case "week":
                    newFireDate = fireDate + 7 * ONE_DAY;
                    break;
                case "day":
                    newFireDate = fireDate + ONE_DAY;
                    break;
                case "hour":
                    newFireDate = fireDate + ONE_HOUR;
                    break;
                case "minute":
                    newFireDate = fireDate + ONE_MINUTE;
                    break;
            }

            // Sanity check, should never happen
            if (newFireDate != 0) {
                Log.d(LOG_TAG, String.format("Repeating notification with id %s at time %s",
                        bundle.getString("id"), Long.toString(newFireDate)));
                bundle.putDouble("fireDate", newFireDate);
                this.sendNotificationScheduled(bundle);
            }
        }
    }

    public void clearNotifications() {
        Log.i(LOG_TAG, "Clearing alerts from the notification centre");
        combinedNotificationCounter = 0;
        NotificationManager notificationManager = notificationManager();
        notificationManager.cancelAll();
    }

    public void cancelAllScheduledNotifications() {
        Log.i(LOG_TAG, "Cancelling all notifications");

        for (String id : scheduledNotificationsPersistence.getAll().keySet()) {
            cancelScheduledNotification(id);
        }
    }

    public void cancelScheduledNotification(ReadableMap userInfo) {
        for (String id : scheduledNotificationsPersistence.getAll().keySet()) {
            try {
                String notificationAttributesJson = scheduledNotificationsPersistence.getString(id, null);
                if (notificationAttributesJson != null) {
                    RNPushNotificationAttributes notificationAttributes = fromJson(notificationAttributesJson);
                    if (notificationAttributes.matches(userInfo)) {
                        cancelScheduledNotification(id);
                    }
                }
            } catch (JSONException e) {
                Log.w(LOG_TAG, "Problem dealing with scheduled notification " + id, e);
            }
        }
    }

    private void cancelScheduledNotification(String notificationIDString) {
        Log.i(LOG_TAG, "Cancelling notification: " + notificationIDString);

        // remove it from the alarm manger schedule
        Bundle b = new Bundle();
        b.putString("id", notificationIDString);
        getAlarmManager().cancel(toScheduleNotificationIntent(b));

        if (scheduledNotificationsPersistence.contains(notificationIDString)) {
            // remove it from local storage
            SharedPreferences.Editor editor = scheduledNotificationsPersistence.edit();
            editor.remove(notificationIDString);
            commit(editor);
        } else {
            Log.w(LOG_TAG, "Unable to find notification " + notificationIDString);
        }

        // removed it from the notification center
        NotificationManager notificationManager = notificationManager();

        notificationManager.cancel(Integer.parseInt(notificationIDString));
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void commit(SharedPreferences.Editor editor) {
        if (Build.VERSION.SDK_INT < 9) {
            editor.commit();
        } else {
            editor.apply();
        }
    }
}