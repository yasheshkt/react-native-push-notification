package com.dieam.reactnativepushnotification.modules;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.dieam.reactnativepushnotification.helpers.ApplicationBadgeHelper;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RNPushNotification extends ReactContextBaseJavaModule implements ActivityEventListener {
    public static final String LOG_TAG = "RNPushNotification";// all logging should use this tag

    private RNPushNotificationHelper mRNPushNotificationHelper;
    private final Random mRandomNumberGenerator = new Random(System.currentTimeMillis());
    private RNPushNotificationJsDelivery mJsDelivery;

    public RNPushNotification(ReactApplicationContext reactContext) {
        super(reactContext);

        reactContext.addActivityEventListener(this);

        Application applicationContext = (Application) reactContext.getApplicationContext();
        // The @ReactNative methods use this
        mRNPushNotificationHelper = new RNPushNotificationHelper(applicationContext);
        // This is used to delivery callbacks to JS
        mJsDelivery = new RNPushNotificationJsDelivery(reactContext);

        registerNotificationsRegistration();
    }

    @Override
    public String getName() {
        return "RNPushNotification";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();

        return constants;
    }

    public void onNewIntent(Intent intent) {
        if (intent.hasExtra("notification")) {
            Bundle bundle = intent.getBundleExtra("notification");
            bundle.putBoolean("foreground", false);
            intent.putExtra("notification", bundle);
            mJsDelivery.notifyNotification(bundle);
        }
    }

    private void registerNotificationsRegistration() {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 72-------------------------------------------");
        IntentFilter intentFilter = new IntentFilter(getReactApplicationContext().getPackageName() + ".RNPushNotificationRegisteredToken");

        getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String token = intent.getStringExtra("token");
                WritableMap params = Arguments.createMap();
                params.putString("deviceToken", token);

                mJsDelivery.sendEvent("remoteNotificationsRegistered", params);
            }
        }, intentFilter);
    }

    private void registerNotificationsReceiveNotificationActions(ReadableArray actions) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 88-------------------------------------------");
        IntentFilter intentFilter = new IntentFilter();
        // Add filter for each actions.
        for (int i = 0; i < actions.size(); i++) {
            String action = actions.getString(i);
            intentFilter.addAction(getReactApplicationContext().getPackageName() + "." + action);
        }
        getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getBundleExtra("notification");

                // Notify the action.
                mJsDelivery.notifyNotificationAction(bundle);

                // Dismiss the notification popup.
                NotificationManager manager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);
                int notificationID = Integer.parseInt(bundle.getString("id"));
                manager.cancel(notificationID);
            }
        }, intentFilter);
    }

    @ReactMethod
    public void requestPermissions(String senderID) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 113-------------------------------------------");
        ReactContext reactContext = getReactApplicationContext();

        Intent GCMService = new Intent(reactContext, RNPushNotificationRegistrationService.class);

        GCMService.putExtra("senderID", senderID);
        reactContext.startService(GCMService);
    }

    @ReactMethod
    public void presentLocalNotification(ReadableMap details) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 124-------------------------------------------");
        Bundle bundle = Arguments.toBundle(details);
        // If notification ID is not provided by the user, generate one at random
        if (bundle.getString("id") == null) {
            bundle.putString("id", String.valueOf(mRandomNumberGenerator.nextInt()));
        }
        mRNPushNotificationHelper.sendToNotificationCentre(bundle);
    }

    @ReactMethod
    public void resetReadCountPushNotification() {
      Log.d(LOG_TAG, "--------------------------------------resetReadCountPushNotification-------------------------------------------");
      RNPushNotificationHelper.resetReadCountHelper();
    }

    @ReactMethod
    public void scheduleLocalNotification(ReadableMap details) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 135-------------------------------------------");
        Bundle bundle = Arguments.toBundle(details);
        // If notification ID is not provided by the user, generate one at random
        if (bundle.getString("id") == null) {
            bundle.putString("id", String.valueOf(mRandomNumberGenerator.nextInt()));
        }
        mRNPushNotificationHelper.sendNotificationScheduled(bundle);
    }

    @ReactMethod
    public void getInitialNotification(Promise promise) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 146-------------------------------------------");
        WritableMap params = Arguments.createMap();
        Activity activity = getCurrentActivity();
        if (activity != null) {
            Intent intent = activity.getIntent();
            Bundle bundle = intent.getBundleExtra("notification");
            if (bundle != null) {
                bundle.putBoolean("foreground", false);
                String bundleString = mJsDelivery.convertJSON(bundle);
                params.putString("dataJSON", bundleString);
            }
        }
        promise.resolve(params);
    }

    @ReactMethod
    public void setApplicationIconBadgeNumber(int number) {
        ApplicationBadgeHelper.INSTANCE.setApplicationIconBadgeNumber(getReactApplicationContext(), number);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 168-------------------------------------------");
        onActivityResult(requestCode, resultCode, data);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }

    @ReactMethod
    /**
     * Cancels all scheduled local notifications, and removes all entries from the notification
     * centre.
     *
     * We're attempting to keep feature parity with the RN iOS implementation in
     * <a href="https://github.com/facebook/react-native/blob/master/Libraries/PushNotificationIOS/RCTPushNotificationManager.m#L289">RCTPushNotificationManager</a>.
     *
     * @see <a href="https://facebook.github.io/react-native/docs/pushnotificationios.html">RN docs</a>
     */
    public void cancelAllLocalNotifications() {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 188-------------------------------------------");
        mRNPushNotificationHelper.cancelAllScheduledNotifications();
        mRNPushNotificationHelper.clearNotifications();
    }

    @ReactMethod
    /**
     * Cancel scheduled notifications, and removes notifications from the notification centre.
     *
     * Note - as we are trying to achieve feature parity with iOS, this method cannot be used
     * to remove specific alerts from the notification centre.
     *
     * @see <a href="https://facebook.github.io/react-native/docs/pushnotificationios.html">RN docs</a>
     */
    public void cancelLocalNotifications(ReadableMap userInfo) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 203-------------------------------------------");
        mRNPushNotificationHelper.cancelScheduledNotification(userInfo);
    }

    @ReactMethod
    public void registerNotificationActions(ReadableArray actions) {
      Log.d(LOG_TAG, "--------------------------------------RNPushNotification 209-------------------------------------------");
        registerNotificationsReceiveNotificationActions(actions);
    }
}
