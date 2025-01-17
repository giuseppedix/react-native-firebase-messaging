package io.invertase.firebase.messaging;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;

import io.invertase.firebase.app.ReactNativeFirebaseApp;
import io.invertase.firebase.common.ReactNativeFirebaseEventEmitter;
import io.invertase.firebase.common.SharedUtils;

public class ReactNativeFirebaseMessagingReceiver extends BroadcastReceiver {
  private static final String TAG = "RNFirebaseMsgReceiver";
  static HashMap<String, RemoteMessage> notifications = new HashMap<>();


  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "broadcast received for message");
    if (ReactNativeFirebaseApp.getApplicationContext() == null) {
      ReactNativeFirebaseApp.setApplicationContext(context.getApplicationContext());
    }
    RemoteMessage remoteMessage = new RemoteMessage(intent.getExtras());
    ReactNativeFirebaseEventEmitter emitter = ReactNativeFirebaseEventEmitter.getSharedInstance();

    // Add a RemoteMessage if the message contains a notification payload
    if (remoteMessage.getNotification() != null) {
      Log.d(TAG, "broadcast received for message: SECOND IF");
      notifications.put(remoteMessage.getMessageId(), remoteMessage);
      ReactNativeFirebaseMessagingStoreHelper.getInstance().getMessagingStore().storeFirebaseMessage(remoteMessage);
    }

    //  |-> ---------------------
    //      App in Foreground
    //   ------------------------
    if (SharedUtils.isAppInForeground(context)) {
      Log.d(TAG, "broadcast received for message: APP IN FOREGROUND");
      String notifDataType = remoteMessage.getData().get("notificationType");
      String startCallType="MEETING_START";
      String disconnectCallType="calldisconnected";
      Log.d(TAG, "broadcast received for message: " + notifDataType);
      if(startCallType.equals(notifDataType)) {
        try {
          this.sendWakeUpIntent(context, remoteMessage, Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
      }
      emitter.sendEvent(ReactNativeFirebaseMessagingSerializer.remoteMessageToEvent(remoteMessage, false));
      return;
    }


    //  |-> ---------------------
    //    App in Background/Quit
    //   ------------------------

    try {
      String notifDataType = remoteMessage.getData().get("notificationType");
      String startCallType="MEETING_START";
      String disconnectCallType="calldisconnected";
      Log.d(TAG, "broadcast received for message: " + notifDataType);
      if(startCallType.equals(notifDataType)) {
        this.sendWakeUpIntent(context, remoteMessage, Intent.FLAG_ACTIVITY_NEW_TASK);
      }
      Intent backgroundIntent = new Intent(context, ReactNativeFirebaseMessagingHeadlessService.class);
      backgroundIntent.putExtra("message", remoteMessage);
      ComponentName name = context.startService(backgroundIntent);
      if (name != null) {
        HeadlessJsTaskService.acquireWakeLockNow(context);
      }
    } catch (IllegalStateException | ClassNotFoundException ex) {
      // By default, data only messages are "default" priority and cannot trigger Headless tasks
      Log.e(
        TAG,
        "Background messages only work if the message priority is set to 'high'",
        ex
      );
    }
  }

  private void sendWakeUpIntent(Context context, RemoteMessage remoteMessage, int flagActivity) throws ClassNotFoundException {
    String packageName = context.getPackageName();
    Log.d(TAG, "broadcast received for message: " + packageName);
    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
    String className = launchIntent.getComponent().getClassName();
    Class<?> activityClass = Class.forName(className);
    Log.d(TAG, "broadcast received for message: " + className);
    Intent activityIntent = new Intent(context, activityClass);
    String entityId = remoteMessage.getData().get("entityId");
    activityIntent.putExtra("entityId", entityId);
    String notifDataType = remoteMessage.getData().get("notificationType");
    activityIntent.putExtra("notificationType", notifDataType);
    activityIntent.addFlags(flagActivity);
    context.startActivity(activityIntent);
  }
}
