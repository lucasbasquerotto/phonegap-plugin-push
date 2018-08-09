package com.adobe.phonegap.push;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.WearableExtender;
import android.support.v4.app.RemoteInput;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@SuppressLint("NewApi")
public class FCMService extends FirebaseMessagingService implements PushConstants {

  private static final String LOG_TAG = "Push_FCMService";
  private static Map<Integer, Boolean> hasNotificationMap = Collections.synchronizedMap(new HashMap<Integer, Boolean>());
  private static Map<Integer, List<Bundle>> notificationMap = Collections.synchronizedMap(new HashMap<Integer, List<Bundle>>());

  @Override
  public void onMessageReceived(RemoteMessage message) {
    if (message == null) {
      return;
    }

    String from = message.getFrom();
    Log.d(LOG_TAG, "onMessage - from: " + from);

    Bundle extras = new Bundle();

    if (message.getNotification() != null) {
      extras.putString(TITLE, message.getNotification().getTitle());
      extras.putString(MESSAGE, message.getNotification().getBody());
      extras.putString(SOUND, message.getNotification().getSound());
      extras.putString(ICON, message.getNotification().getIcon());
      extras.putString(COLOR, message.getNotification().getColor());
    }

    for (Map.Entry<String, String> entry : message.getData().entrySet()) {
      extras.putString(entry.getKey(), entry.getValue());
    }

    if ((extras != null) && isAvailableSender(from)) {
      Context applicationContext = getApplicationContext();

      SharedPreferences prefs = getSharedPreferences();
      boolean forceShow = prefs.getBoolean(FORCE_SHOW, false);
      boolean clearBadge = prefs.getBoolean(CLEAR_BADGE, false);
      String messageKey = prefs.getString(MESSAGE_KEY, MESSAGE);
      String titleKey = prefs.getString(TITLE_KEY, TITLE);

      extras = normalizeExtras(applicationContext, extras, messageKey, titleKey);

      if (clearBadge) {
        PushPlugin.setApplicationIconBadgeNumber(getApplicationContext(), 0);
      }

      // if we are in the foreground and forceShow is `false` only send data
      if (!forceShow && PushPlugin.isInForeground()) {
        Log.d(LOG_TAG, "foreground");
        extras.putBoolean(FOREGROUND, true);
        extras.putBoolean(COLDSTART, false);
        PushPlugin.sendExtras(extras);
      }
      // if we are in the foreground and forceShow is `true`, force show the notification if the data has at least a message or title
      else if (forceShow && PushPlugin.isInForeground()) {
        Log.d(LOG_TAG, "foreground force");
        extras.putBoolean(FOREGROUND, true);
        extras.putBoolean(COLDSTART, false);

        showNotificationIfPossible(applicationContext, extras);
      }
      // if we are not in the foreground always send notification if the data has at least a message or title
      else {
        Log.d(LOG_TAG, "background");
        extras.putBoolean(FOREGROUND, false);
        extras.putBoolean(COLDSTART, PushPlugin.isActive());

        showNotificationIfPossible(applicationContext, extras);
      }
    }
  }

  /*
  * Change a values key in the extras bundle
  */
  private void replaceKey(Context context, String oldKey, String newKey, Bundle extras, Bundle newExtras) {
    Object value = extras.get(oldKey);

    if (value != null) {
      if (value instanceof String) {
        value = localizeKey(context, newKey, (String) value);

        newExtras.putString(newKey, (String) value);
      } else if (value instanceof Boolean) {
        newExtras.putBoolean(newKey, (Boolean) value);
      } else if (value instanceof Number) {
        newExtras.putDouble(newKey, ((Number) value).doubleValue());
      } else {
        newExtras.putString(newKey, String.valueOf(value));
      }
    }
  }

  /*
  * Normalize localization for key
  */
  private String localizeKey(Context context, String key, String value) {
    if (key.equals(TITLE) || key.equals(MESSAGE) || key.equals(SUMMARY_TEXT)) {
      try {
        JSONObject localeObject = new JSONObject(value);
        String localeKey = localeObject.getString(LOC_KEY);
        ArrayList<String> localeFormatData = new ArrayList<String>();

        if (!localeObject.isNull(LOC_DATA)) {
          String localeData = localeObject.getString(LOC_DATA);
          JSONArray localeDataArray = new JSONArray(localeData);

          for (int i = 0; i < localeDataArray.length(); i++) {
            localeFormatData.add(localeDataArray.getString(i));
          }
        }

        String packageName = context.getPackageName();
        Resources resources = context.getResources();

        int resourceId = resources.getIdentifier(localeKey, "string", packageName);

        if (resourceId != 0) {
          return resources.getString(resourceId, localeFormatData.toArray());
        } else {
          Log.d(LOG_TAG, "can't find resource for locale key = " + localeKey);

          return value;
        }
      } catch (JSONException e) {
        Log.d(LOG_TAG, "no locale found for key = " + key + ", error " + e.getMessage());

        return value;
      }
    }

    return value;
  }

  /*
  * Replace alternate keys with our canonical value
  */
  private String normalizeKey(String key, String messageKey, String titleKey) {
    if (
      key.equals(BODY) || 
      key.equals(ALERT) || 
      key.equals(MP_MESSAGE) || 
      key.equals(GCM_NOTIFICATION_BODY) || 
      key.equals(TWILIO_BODY) || 
      key.equals(messageKey)
    ) {
      return MESSAGE;
    } else if (key.equals(TWILIO_TITLE) || key.equals(SUBJECT) || key.equals(titleKey)) {
      return TITLE;
    } else if (key.equals(MSGCNT) || key.equals(BADGE)) {
      return COUNT;
    } else if (key.equals(SOUNDNAME) || key.equals(TWILIO_SOUND)) {
      return SOUND;
    } else if (key.startsWith(GCM_NOTIFICATION)) {
      return key.substring(GCM_NOTIFICATION.length() + 1, key.length());
    } else if (key.startsWith(GCM_N)) {
      return key.substring(GCM_N.length() + 1, key.length());
    } else if (key.startsWith(UA_PREFIX)) {
      key = key.substring(UA_PREFIX.length() + 1, key.length());
      return key.toLowerCase();
    } else {
      return key;
    }
  }

  /*
  * Parse bundle into normalized keys.
  */
  private Bundle normalizeExtras(Context context, Bundle extras, String messageKey, String titleKey) {
    Log.d(LOG_TAG, "normalize extras");
    Iterator<String> it = extras.keySet().iterator();
    Bundle newExtras = new Bundle();

    while (it.hasNext()) {
      String key = it.next();
      Log.d(LOG_TAG, "key = " + key);

      // If normalizeKeythe key is "data" or "message" and the value is a json object extract
      // This is to support parse.com and other services. Issue #147 and pull #218
      if (key.equals(PARSE_COM_DATA) || key.equals(MESSAGE) || key.equals(messageKey)) {
        Object json = extras.get(key);

        // Make sure data is json object stringified
        if (json instanceof String && ((String) json).startsWith("{")) {
          Log.d(LOG_TAG, "extracting nested message data from key = " + key);
          try {
            // If object contains message keys promote each value to the root of the bundle
            JSONObject data = new JSONObject((String) json);
            if (
              data.has(ALERT) || 
              data.has(MESSAGE) || 
              data.has(BODY) || 
              data.has(TITLE) || 
              data.has(messageKey) || 
              data.has(titleKey)
            ) {
              Iterator<String> jsonIter = data.keys();

              while (jsonIter.hasNext()) {
                String jsonKey = jsonIter.next();

                Log.d(LOG_TAG, "key = data/" + jsonKey);

                String value = data.getString(jsonKey);
                jsonKey = normalizeKey(jsonKey, messageKey, titleKey);
                value = localizeKey(context, jsonKey, value);

                newExtras.putString(jsonKey, value);
              }
            } else if (data.has(LOC_KEY) || data.has(LOC_DATA)) {
              String newKey = normalizeKey(key, messageKey, titleKey);
              Log.d(LOG_TAG, "replace key " + key + " with " + newKey);
              replaceKey(context, key, newKey, extras, newExtras);
            }
          } catch (JSONException e) {
            Log.e(LOG_TAG, "normalizeExtras: JSON exception");
          }
        } else {
          String newKey = normalizeKey(key, messageKey, titleKey);
          Log.d(LOG_TAG, "replace key " + key + " with " + newKey);
          replaceKey(context, key, newKey, extras, newExtras);
        }
      } else if (key.equals(("notification"))) {
        Bundle value = extras.getBundle(key);
        Iterator<String> iterator = value.keySet().iterator();
        while (iterator.hasNext()) {
          String notifkey = iterator.next();

          Log.d(LOG_TAG, "notifkey = " + notifkey);
          String newKey = normalizeKey(notifkey, messageKey, titleKey);
          Log.d(LOG_TAG, "replace key " + notifkey + " with " + newKey);

          String valueData = value.getString(notifkey);
          valueData = localizeKey(context, newKey, valueData);

          newExtras.putString(newKey, valueData);
        }

        continue;
        // In case we weren't working on the payload data node or the notification node,
        // normalize the key.
        // This allows to have "message" as the payload data key without colliding
        // with the other "message" key (holding the body of the payload)
        // See issue #1663
      } else {
        String newKey = normalizeKey(key, messageKey, titleKey);
        Log.d(LOG_TAG, "replace key " + key + " with " + newKey);
        replaceKey(context, key, newKey, extras, newExtras);
      }

    } // while

    return newExtras;
  }

  private int extractBadgeCount(Bundle extras) {
    int count = -1;
    String msgcnt = extras.getString(COUNT);

    try {
      if (msgcnt != null) {
        count = Integer.parseInt(msgcnt);
      }
    } catch (NumberFormatException e) {
      Log.e(LOG_TAG, e.getLocalizedMessage(), e);
    }

    return count;
  }

  private void showNotificationIfPossible(Context context, Bundle extras) {
    // Send a notification if there is a message or title, otherwise just send data
    int notId = parseInt(NOT_ID, extras);
    String title = getNotificationTitle(notId, extras);
    String message = extras.getString(MESSAGE);
    String contentAvailable = extras.getString(CONTENT_AVAILABLE);
    String forceStart = extras.getString(FORCE_START);
    int badgeCount = extractBadgeCount(extras);

    if (badgeCount >= 0) {
      Log.d(LOG_TAG, "count =[" + badgeCount + "]");
      PushPlugin.setApplicationIconBadgeNumber(context, badgeCount);
    }

    Log.d(LOG_TAG, "message =[" + message + "]");
    Log.d(LOG_TAG, "title =[" + title + "]");
    Log.d(LOG_TAG, "contentAvailable =[" + contentAvailable + "]");
    Log.d(LOG_TAG, "forceStart =[" + forceStart + "]");

    if ((message != null && message.length() != 0) || (title != null && title.length() != 0)) {
      Log.d(LOG_TAG, "create notification");

      if (title == null || title.isEmpty()) {
        extras.putString(TITLE, getAppName(this));
      }

      createNotification(context, extras);
    }

    if (!PushPlugin.isActive() && "1".equals(forceStart)) {
      Log.d(LOG_TAG, "app is not running but we should start it and put in background");
      Intent intent = new Intent(this, PushHandlerActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.putExtra(PUSH_BUNDLE, extras);
      intent.putExtra(START_IN_BACKGROUND, true);
      intent.putExtra(FOREGROUND, false);
      startActivity(intent);
    } else if ("1".equals(contentAvailable)) {
      Log.d(LOG_TAG, "app is not running and content available true");
      Log.d(LOG_TAG, "send notification event");
      PushPlugin.sendExtras(extras);
    }
  }

  public void createNotification(Context context, Bundle extras) {
    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    String appName = getAppName(this);
    String packageName = context.getPackageName();
    Resources resources = context.getResources();
    int notId = parseInt(NOT_ID, extras);

    ArrayList<Bundle> extrasList = addNotification(notId, extras);

    boolean deleted = verifyDelete(mNotificationManager, appName, notId, extras);

    if (deleted) {
      return;
    }

    Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    notificationIntent.putExtra(NOT_ID, notId);
    notificationIntent.putExtra(PUSH_BUNDLE, extras);
    notificationIntent.putParcelableArrayListExtra(PUSH_LIST_BUNDLE, extrasList);

    int requestCode = new Random().nextInt();
    PendingIntent contentIntent = PendingIntent.getActivity(
      this, requestCode, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    Intent dismissedNotificationIntent = new Intent(this, PushDismissedHandler.class);
    dismissedNotificationIntent.putExtra(PUSH_BUNDLE, extras);
    dismissedNotificationIntent.putExtra(NOT_ID, notId);
    dismissedNotificationIntent.putExtra(DISMISSED, true);
    dismissedNotificationIntent.setAction(PUSH_DISMISSED);

    requestCode = new Random().nextInt();
    PendingIntent deleteIntent = PendingIntent.getBroadcast(
      this, requestCode, dismissedNotificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

    String title = getNotificationTitle(notId, extras);

    NotificationCompat.Builder mBuilder = getBuilder(context, mNotificationManager, extras);

    mBuilder.setWhen(System.currentTimeMillis())
      .setContentIntent(contentIntent)
      .setDeleteIntent(deleteIntent)
      .setAutoCancel(true)
      .setContentTitle(fromHtml(title))
      .setTicker(fromHtml(title));

    SharedPreferences prefs = context.getSharedPreferences(PushPlugin.COM_ADOBE_PHONEGAP_PUSH, Context.MODE_PRIVATE);
    boolean hasOldNotification = hasNotificationMap.containsKey(notId);
    String localIcon = prefs.getString(ICON, null);
    String localIconColor = prefs.getString(ICON_COLOR, null);
    boolean soundOption = prefs.getBoolean(SOUND, true);
    boolean vibrateOption = prefs.getBoolean(VIBRATE, true);
    Log.d(LOG_TAG, "hasOldNotification=" + hasOldNotification);
    Log.d(LOG_TAG, "stored icon=" + localIcon);
    Log.d(LOG_TAG, "stored iconColor=" + localIconColor);
    Log.d(LOG_TAG, "stored sound=" + soundOption);
    Log.d(LOG_TAG, "stored vibrate=" + vibrateOption);

    /*
    * Notification Vibration
    */

    setNotificationVibration(extras, vibrateOption, hasOldNotification, mBuilder);

    /*
    * Notification Icon Color
    *
    * Sets the small-icon background color of the notification.
    * To use, add the `iconColor` key to plugin android options
    *
    */
    setNotificationIconColor(extras.getString(COLOR), mBuilder, localIconColor);

    /*
    * Notification Icon
    *
    * Sets the small-icon of the notification.
    *
    * - checks the plugin options for `icon` key
    * - if none, uses the application icon
    *
    * The icon value must be a string that maps to a drawable resource.
    * If no resource is found, falls
    *
    */
    setNotificationSmallIcon(context, extras, packageName, resources, mBuilder, localIcon);

    /*
    * Notification Large-Icon
    *
    * Sets the large-icon of the notification
    *
    * - checks the gcm data for the `image` key
    * - checks to see if remote image, loads it.
    * - checks to see if assets image, Loads It.
    * - checks to see if resource image, LOADS IT!
    * - if none, we don't set the large icon
    *
    */
    setNotificationLargeIcon(notId, extras, packageName, resources, mBuilder);

    /*
    * Notification Sound
    */
    if (soundOption) {
      setNotificationSound(context, extras, hasOldNotification, mBuilder);
    }

    /*
    *  LED Notification
    */
    setNotificationLedColor(extras, mBuilder);

    /*
    *  Priority Notification
    */
    setNotificationPriority(extras, mBuilder);

    /*
    * Notification message
    */
    setNotificationMessage(notId, extras, mBuilder);

    /*
    * Notification count
    */
    setNotificationCount(context, extras, mBuilder);

    /*
    *  Notification ongoing
    */
    setNotificationOngoing(extras, mBuilder);

    /*
    * Notification count
    */
    setVisibility(context, extras, mBuilder);

    /*
    * Notification add actions
    */
    createActions(extras, mBuilder, resources, packageName, notId);

    synchronized(hasNotificationMap) {
      hasNotificationMap.put(notId, true);
    }

    mNotificationManager.notify(appName, notId, mBuilder.build());
  }

  private static NotificationCompat.Builder getBuilder(Context context, NotificationManager mNotificationManager, Bundle extras) {
    NotificationCompat.Builder mBuilder = null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      String channelID = extras.getString(ANDROID_CHANNEL_ID);

      // if the push payload specifies a channel use it
      if (channelID != null) {
        mBuilder = new NotificationCompat.Builder(context, channelID);
      } else {
        List<NotificationChannel> channels = mNotificationManager.getNotificationChannels();

        if (channels.size() == 1) {
          channelID = channels.get(0).getId();
        } else {
          channelID = extras.getString(ANDROID_CHANNEL_ID, DEFAULT_CHANNEL_ID);
        }

        Log.d(LOG_TAG, "Using channel ID = " + channelID);
        mBuilder = new NotificationCompat.Builder(context, channelID);
      }

    } else {
      mBuilder = new NotificationCompat.Builder(context);
    }

    return mBuilder;
  }

  private void updateIntent(Intent intent, String callback, Bundle extras, boolean foreground, int notId) {
    intent.putExtra(CALLBACK, callback);
    intent.putExtra(PUSH_BUNDLE, extras);
    intent.putExtra(FOREGROUND, foreground);
    intent.putExtra(NOT_ID, notId);
  }

  private void createActions(
    Bundle extras, 
    NotificationCompat.Builder mBuilder, 
    Resources resources, 
    String packageName, 
    int notId
  ) {
    Log.d(LOG_TAG, "create actions: with in-line");
    String actions = extras.getString(ACTIONS);

    if (actions != null) {
      try {
        JSONArray actionsArray = new JSONArray(actions);
        ArrayList<NotificationCompat.Action> wActions = new ArrayList<NotificationCompat.Action>();

        for (int i = 0; i < actionsArray.length(); i++) {
          int min = 1;
          int max = 2000000000;
          Random random = new Random();
          int uniquePendingIntentRequestCode = random.nextInt((max - min) + 1) + min;
          Log.d(LOG_TAG, "adding action");
          JSONObject action = actionsArray.getJSONObject(i);
          Log.d(LOG_TAG, "adding callback = " + action.getString(CALLBACK));
          boolean foreground = action.optBoolean(FOREGROUND, true);
          boolean inline = action.optBoolean("inline", false);
          Intent intent = null;
          PendingIntent pIntent = null;

          if (inline) {
            Log.d(LOG_TAG, "Version: " + android.os.Build.VERSION.SDK_INT + " = " + android.os.Build.VERSION_CODES.M);
            
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
              Log.d(LOG_TAG, "push activity");
              intent = new Intent(this, PushHandlerActivity.class);
            } else {
              Log.d(LOG_TAG, "push receiver");
              intent = new Intent(this, BackgroundActionButtonHandler.class);
            }

            updateIntent(intent, action.getString(CALLBACK), extras, foreground, notId);

            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
              Log.d(LOG_TAG, "push activity for notId " + notId);
              pIntent = PendingIntent.getActivity(this, uniquePendingIntentRequestCode, intent,
                PendingIntent.FLAG_ONE_SHOT);
            } else {
              Log.d(LOG_TAG, "push receiver for notId " + notId);
              pIntent = PendingIntent.getBroadcast(this, uniquePendingIntentRequestCode, intent,
                PendingIntent.FLAG_ONE_SHOT);
            }
          } else if (foreground) {
            intent = new Intent(this, PushHandlerActivity.class);
            updateIntent(intent, action.getString(CALLBACK), extras, foreground, notId);
            pIntent = PendingIntent.getActivity(this, uniquePendingIntentRequestCode, intent,
              PendingIntent.FLAG_UPDATE_CURRENT);
          } else {
            intent = new Intent(this, BackgroundActionButtonHandler.class);
            updateIntent(intent, action.getString(CALLBACK), extras, foreground, notId);
            pIntent = PendingIntent.getBroadcast(this, uniquePendingIntentRequestCode, intent,
              PendingIntent.FLAG_UPDATE_CURRENT);
          }

          NotificationCompat.Action.Builder actionBuilder = new NotificationCompat.Action.Builder(
            getImageId(resources, action.optString(ICON, ""), packageName), 
            action.getString(TITLE), 
            pIntent);

          RemoteInput remoteInput = null;

          if (inline) {
            Log.d(LOG_TAG, "create remote input");
            String replyLabel = action.optString(INLINE_REPLY_LABEL, "Enter your reply here");
            remoteInput = new RemoteInput.Builder(INLINE_REPLY).setLabel(replyLabel).build();
            actionBuilder.addRemoteInput(remoteInput);
          }

          NotificationCompat.Action wAction = actionBuilder.build();
          wActions.add(actionBuilder.build());

          if (inline) {
            mBuilder.addAction(wAction);
          } else {
            mBuilder.addAction(
              getImageId(resources, action.optString(ICON, ""), packageName), 
              action.getString(TITLE),
              pIntent);
          }

          wAction = null;
          pIntent = null;
        }

        mBuilder.extend(new WearableExtender().addActions(wActions));
        wActions.clear();
      } catch (JSONException e) {
        // nope
      }
    }
  }

  private void setNotificationCount(Context context, Bundle extras, NotificationCompat.Builder mBuilder) {
    int count = extractBadgeCount(extras);

    if (count >= 0) {
      Log.d(LOG_TAG, "count =[" + count + "]");
      mBuilder.setNumber(count);
    }
  }

  private void setVisibility(Context context, Bundle extras, NotificationCompat.Builder mBuilder) {
    String visibilityStr = extras.getString(VISIBILITY);

    if (visibilityStr != null) {
      try {
        Integer visibility = Integer.parseInt(visibilityStr);

        if (visibility >= NotificationCompat.VISIBILITY_SECRET && visibility <= NotificationCompat.VISIBILITY_PUBLIC) {
          mBuilder.setVisibility(visibility);
        } else {
          Log.e(LOG_TAG, "Visibility parameter must be between -1 and 1");
        }
      } catch (NumberFormatException e) {
        e.printStackTrace();
      }
    }
  }

  private void setNotificationVibration(Bundle extras, Boolean vibrateOption, boolean hasOldNotification, NotificationCompat.Builder mBuilder) {
    vibrateOption = (vibrateOption != null) ? vibrateOption : true;

    String vibrateFlag = hasOldNotification ? extras.getString(VIBRATE_RECURRENT) : null;
    vibrateFlag = (vibrateFlag != null) ? vibrateFlag : extras.getString(VIBRATE, "true");
    boolean vibrate = (vibrateFlag != null) ? vibrateFlag.equals("true") : vibrateOption;

    if (vibrate) {
      String vibrationPattern = extras.getString(VIBRATION_PATTERN);

      if (vibrationPattern != null) {
        String[] items = vibrationPattern.replaceAll("\\[", "").replaceAll("\\]", "").split(",");
        long[] results = new long[items.length];

        for (int i = 0; i < items.length; i++) {
          try {
            results[i] = Long.parseLong(items[i].trim());
          } catch (NumberFormatException nfe) {
          }
        }

        mBuilder.setVibrate(results);
      } else if (vibrateOption) {
        mBuilder.setDefaults(Notification.DEFAULT_VIBRATE);
      }
    }
  }

  private void setNotificationOngoing(Bundle extras, NotificationCompat.Builder mBuilder) {
    boolean ongoing = Boolean.parseBoolean(extras.getString(ONGOING, "false"));
    mBuilder.setOngoing(ongoing);
  }

  private void setNotificationMessage(int notId, Bundle extras, NotificationCompat.Builder mBuilder) {
    String message = extras.getString(MESSAGE);
    String style = extras.getString(STYLE, STYLE_TEXT);
    String title = getNotificationTitle(notId, extras);

    if (STYLE_INBOX.equals(style)) {
      List<Bundle> notificationList = notificationMap.get(notId);
      List<Bundle> notificationListAux = new ArrayList<Bundle>();

      if (notificationList != null) {
        notificationListAux.addAll(notificationList);
      }

      int amount = notificationListAux.size();
      message = (message != null) ? message : "";

      if (amount > 1) {
        String summaryText = getSummaryText(notId, extras);

        NotificationCompat.InboxStyle notificationInbox = new NotificationCompat.InboxStyle()
          .setBigContentTitle(fromHtml(title))
          .setSummaryText(fromHtml(summaryText));

        for (int i = notificationListAux.size() - 1; i >= 0; i--) {
          Bundle item = notificationListAux.get(i);
          String messageAux = item.getString(MESSAGE);
          messageAux = (messageAux != null) ? messageAux : "";

          String messagePrefix = getMessagePrefix(notId, item);
          messagePrefix = (messagePrefix != null) ? messagePrefix : "";

          notificationInbox.addLine(fromHtml(messagePrefix + messageAux));
        }

        mBuilder.setContentText(fromHtml(summaryText)).setStyle(notificationInbox);
      } else {
        String messagePrefix = getMessagePrefix(notId, extras);
        messagePrefix = (messagePrefix != null) ? messagePrefix : "";
        mBuilder.setContentText(fromHtml(messagePrefix + message));

        NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();

        if (message != null) {
          bigText.bigText(fromHtml(messagePrefix + message));
          bigText.setBigContentTitle(fromHtml(title));
          mBuilder.setStyle(bigText);
        }
      }
    } else if (STYLE_PICTURE.equals(style)) {
      NotificationCompat.BigPictureStyle bigPicture = new NotificationCompat.BigPictureStyle();
      bigPicture.bigPicture(getBitmapFromURL(extras.getString(PICTURE)));
      bigPicture.setBigContentTitle(fromHtml(title));
      bigPicture.setSummaryText(fromHtml(extras.getString(SUMMARY_TEXT)));

      mBuilder.setContentTitle(fromHtml(title));
      mBuilder.setContentText(fromHtml(message));

      mBuilder.setStyle(bigPicture);
    } else {
      NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();

      if (message != null) {
        mBuilder.setContentText(fromHtml(message));

        bigText.bigText(fromHtml(message));
        bigText.setBigContentTitle(fromHtml(title));

        String summaryText = extras.getString(SUMMARY_TEXT);

        if (summaryText != null) {
          bigText.setSummaryText(fromHtml(summaryText));
        }

        mBuilder.setStyle(bigText);
      } else {
        // mBuilder.setContentText("<missing message content>");
      }
    }
  }

  private String getNotificationTitle(int notId, Bundle extras) {
    int amount = getNotificationAmount(notId);

    String titleSingle = extras.getString(TITLE_SINGLE);

    if ((titleSingle != null) && (amount == 1)) {
      return titleSingle;
    }

    Set<Integer> subIds = getStoredNotificationSubIds(notId);

    String titleSubSingle = extras.getString(TITLE_SUB_SINGLE);

    if ((titleSubSingle != null) && (subIds.size() == 1)) {
      return titleSubSingle;
    }

    String title = extras.getString(TITLE);
    
    return title;
  }

  private static String getMessagePrefix(int notId, Bundle extras) {
    int amount = getNotificationAmount(notId);

    String messagePrefixSingle = extras.getString(MESSAGE_PREFIX_SINGLE);

    if ((messagePrefixSingle != null) && (amount == 1)) {
      return messagePrefixSingle;
    }

    Set<Integer> subIds = getStoredNotificationSubIds(notId);

    String messagePrefixSubSingle = extras.getString(MESSAGE_PREFIX_SUB_SINGLE);

    if ((messagePrefixSubSingle != null) && (subIds.size() == 1)) {
      return messagePrefixSubSingle;
    }

    String messagePrefix = extras.getString(MESSAGE_PREFIX);

    return messagePrefix;
  }

  private static String getSummaryText(int notId, Bundle extras) {
    int amount = getNotificationAmount(notId);

    if (amount == 1) {
      return null;
    }

    Set<Integer> subIds = getStoredNotificationSubIds(notId);

    String summaryTextSubSingle = extras.getString(SUMMARY_TEXT_SUB_SINGLE);

    if ((summaryTextSubSingle != null) && (subIds.size() == 1)) {
      summaryTextSubSingle = summaryTextSubSingle.replace("%n%", String.valueOf(amount));
      return summaryTextSubSingle;
    }

    String summaryText = extras.getString(SUMMARY_TEXT);
    summaryText = (summaryText != null) ? summaryText : ("+%n%");
    summaryText = summaryText.replace("%n%", String.valueOf(amount));
    summaryText = summaryText.replace("%m%", String.valueOf(subIds.size()));

    return summaryText;
  }

  private void setNotificationSound(Context context, Bundle extras, boolean hasOldNotification, NotificationCompat.Builder mBuilder) {
    String soundname = hasOldNotification ? extras.getString(SOUND_RECURRENT) : null;
    soundname = (soundname != null) ? soundname : extras.getString(SOUNDNAME);
    soundname = (soundname != null) ? soundname : extras.getString(SOUND);

    if (SOUND_RINGTONE.equals(soundname)) {
      mBuilder.setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI);
    } else if ((soundname != null) && !soundname.contentEquals(SOUND_DEFAULT)) {
      Uri sound = Uri
        .parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/raw/" + soundname);
      Log.d(LOG_TAG, sound.toString());
      mBuilder.setSound(sound);
    } else {
      mBuilder.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
    }
  }

  private void setNotificationLedColor(Bundle extras, NotificationCompat.Builder mBuilder) {
    String ledColor = extras.getString(LED_COLOR);

    if (ledColor != null) {
      // Converts parse Int Array from ledColor
      String[] items = ledColor.replaceAll("\\[", "").replaceAll("\\]", "").split(",");
      int[] results = new int[items.length];

      for (int i = 0; i < items.length; i++) {
        try {
          results[i] = Integer.parseInt(items[i].trim());
        } catch (NumberFormatException nfe) {
        }
      }

      if (results.length == 4) {
        mBuilder.setLights(Color.argb(results[0], results[1], results[2], results[3]), 500, 500);
      } else {
        Log.e(LOG_TAG, "ledColor parameter must be an array of length == 4 (ARGB)");
      }
    }
  }

  private void setNotificationPriority(Bundle extras, NotificationCompat.Builder mBuilder) {
    String priorityStr = extras.getString(PRIORITY);

    if (priorityStr != null) {
      try {
        Integer priority = Integer.parseInt(priorityStr);

        if (priority >= NotificationCompat.PRIORITY_MIN && priority <= NotificationCompat.PRIORITY_MAX) {
          mBuilder.setPriority(priority);
        } else {
          Log.e(LOG_TAG, "Priority parameter must be between -2 and 2");
        }
      } catch (NumberFormatException e) {
        e.printStackTrace();
      }
    }
  }

  private Bitmap getCircleBitmap(Bitmap bitmap) {
    if (bitmap == null) {
      return null;
    }

    final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(output);
    final int color = Color.RED;
    final Paint paint = new Paint();
    final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    final RectF rectF = new RectF(rect);

    paint.setAntiAlias(true);
    canvas.drawARGB(0, 0, 0, 0);
    paint.setColor(color);
    float cx = bitmap.getWidth() / 2;
    float cy = bitmap.getHeight() / 2;
    float radius = cx < cy ? cx : cy;
    canvas.drawCircle(cx, cy, radius, paint);

    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(bitmap, rect, rect, paint);

    bitmap.recycle();

    return output;
  }

  private void setNotificationLargeIcon(int notId, Bundle extras, String packageName, Resources resources,
    NotificationCompat.Builder mBuilder) {
    String gcmLargeIcon = getImage(notId, extras); // from gcm

    if ((gcmLargeIcon != null) && (!gcmLargeIcon.isEmpty())) {
      String imageType = extras.getString(IMAGE_TYPE, IMAGE_TYPE_SQUARE);

      if (gcmLargeIcon.startsWith("http://") || gcmLargeIcon.startsWith("https://")) {
        Bitmap bitmap = getBitmapFromURL(gcmLargeIcon);

        if (IMAGE_TYPE_SQUARE.equalsIgnoreCase(imageType)) {
          mBuilder.setLargeIcon(bitmap);
        } else {
          Bitmap bm = getCircleBitmap(bitmap);
          mBuilder.setLargeIcon(bm);
        }

        Log.d(LOG_TAG, "using remote large-icon from gcm");
      } else {
        AssetManager assetManager = getAssets();
        InputStream istr;

        try {
          istr = assetManager.open(gcmLargeIcon);
          Bitmap bitmap = BitmapFactory.decodeStream(istr);

          if (IMAGE_TYPE_SQUARE.equalsIgnoreCase(imageType)) {
            mBuilder.setLargeIcon(bitmap);
          } else {
            Bitmap bm = getCircleBitmap(bitmap);
            mBuilder.setLargeIcon(bm);
          }

          Log.d(LOG_TAG, "using assets large-icon from gcm");
        } catch (IOException e) {
          int largeIconId = 0;
          largeIconId = getImageId(resources, gcmLargeIcon, packageName);

          if (largeIconId != 0) {
            Bitmap largeIconBitmap = BitmapFactory.decodeResource(resources, largeIconId);
            mBuilder.setLargeIcon(largeIconBitmap);
            Log.d(LOG_TAG, "using resources large-icon from gcm");
          } else {
            Log.d(LOG_TAG, "Not setting large icon");
          }
        }
      }
    }
  }

  private static String getImage(int notId, Bundle extras) {
    int amount = getNotificationAmount(notId);

    String imageSingle = extras.getString(IMAGE_SINGLE);

    if ((imageSingle != null) && (amount == 1)) {
      return imageSingle;
    }

    Set<Integer> subIds = getStoredNotificationSubIds(notId);

    String imageSubSingle = extras.getString(IMAGE_SUB_SINGLE);

    if ((imageSubSingle != null) && (subIds.size() == 1)) {
      return imageSubSingle;
    }

    String image = extras.getString(IMAGE);

    return image;
  }

  private int getImageId(Resources resources, String icon, String packageName) {
    int iconId = resources.getIdentifier(icon, DRAWABLE, packageName);

    if (iconId == 0) {
      iconId = resources.getIdentifier(icon, "mipmap", packageName);
    }

    return iconId;
  }

  private void setNotificationSmallIcon(Context context, Bundle extras, String packageName, Resources resources,
    NotificationCompat.Builder mBuilder, String localIcon) {
    int iconId = 0;
    String icon = extras.getString(ICON);

    if (icon != null && !"".equals(icon)) {
      iconId = getImageId(resources, icon, packageName);
      Log.d(LOG_TAG, "using icon from plugin options");
    } else if (localIcon != null && !"".equals(localIcon)) {
      iconId = getImageId(resources, localIcon, packageName);
      Log.d(LOG_TAG, "using icon from plugin options");
    }

    if (iconId == 0) {
      Log.d(LOG_TAG, "no icon resource found - using application icon");
      iconId = context.getApplicationInfo().icon;
    }

    mBuilder.setSmallIcon(iconId);
  }

  private void setNotificationIconColor(String color, NotificationCompat.Builder mBuilder, String localIconColor) {
    int iconColor = 0;

    if (color != null && !"".equals(color)) {
      try {
        iconColor = Color.parseColor(color);
      } catch (IllegalArgumentException e) {
        Log.e(LOG_TAG, "couldn't parse color from android options");
      }
    } else if (localIconColor != null && !"".equals(localIconColor)) {
      try {
        iconColor = Color.parseColor(localIconColor);
      } catch (IllegalArgumentException e) {
        Log.e(LOG_TAG, "couldn't parse color from android options");
      }
    }

    if (iconColor != 0) {
      mBuilder.setColor(iconColor);
    }
  }

  private static Bitmap getBitmapFromURL(String strURL) {
    try {
      URL url = new URL(strURL);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(15000);
      connection.setDoInput(true);
      connection.connect();
      InputStream input = connection.getInputStream();
      return BitmapFactory.decodeStream(input);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static String getAppName(Context context) {
    CharSequence appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
    return (String) appName;
  }

  private static int parseInt(String value, Bundle extras) {
    int retval = 0;

    try {
      retval = Integer.parseInt(extras.getString(value));
    } catch (NumberFormatException e) {
      Log.e(LOG_TAG, "Number format exception - Error parsing " + value + ": " + e.getMessage());
    } catch (Exception e) {
      Log.e(LOG_TAG, "Number format exception - Error parsing " + value + ": " + e.getMessage());
    }

    return retval;
  }

  private static Spanned fromHtml(String source) {
    if (source != null) {
      return Html.fromHtml(source);
    } else {
      return null;
    }
  }

  private boolean isAvailableSender(String from) {
    if (from == null) {
      return false;
    }

    SharedPreferences sharedPref = getSharedPreferences();

    if (sharedPref == null) {
      return false;
    }

    String savedSenderID = sharedPref.getString(SENDER_ID, "");

    return from.equals(savedSenderID) || from.startsWith("/topics/");
  }

  private SharedPreferences getSharedPreferences() {
    SharedPreferences sharedPref = getApplicationContext()
      .getSharedPreferences(PushPlugin.COM_ADOBE_PHONEGAP_PUSH, Context.MODE_PRIVATE);
    return sharedPref;
  }

  public static void dismissNotification(int notId) {
    Log.d(LOG_TAG, "dismissNotification");
    clearNotificationMessages(notId);

    synchronized(hasNotificationMap) {
      hasNotificationMap.remove(notId);
    }
  }

  private static void clearNotificationMessages(int notId) {
    Log.d(LOG_TAG, "clearNotificationMessages");

    synchronized(notificationMap) {
      List<Bundle> notificationList = notificationMap.remove(notId);
    }
  }

  private static ArrayList<Bundle> addNotification(int notId, Bundle extras) {
    ArrayList<Bundle> extrasList = new ArrayList<Bundle>();

    if (!isInbox(extras)) {
      clearNotificationMessages(notId);
    }

    synchronized(notificationMap) {
      List<Bundle> notificationList = notificationMap.get(notId);

      if (notificationList == null) {
        notificationList = new ArrayList<Bundle>();
        notificationMap.put(notId, notificationList);
      }

      notificationList.add(extras);
      extrasList.addAll(notificationList);
    }

    return extrasList;
  }

  private static boolean verifyDelete(NotificationManager mNotificationManager, String appName, int notId, Bundle extras) {
    int amount = getNotificationAmount(notId);

    String deleteSingleStr = extras.getString(DELETE_SINGLE);
    boolean deleteSingle = (deleteSingleStr != null) && deleteSingleStr.equals("true");

    if (deleteSingle && (amount == 1)) {
      Log.d(LOG_TAG, "createNotification - deleteSingle: " + notId);
      mNotificationManager.cancel(appName, notId); 
      dismissNotification(notId);
      return true;
    }

    Set<Integer> subIds = getStoredNotificationSubIds(notId);

    String deleteSubSingleStr = extras.getString(DELETE_SUB_SINGLE);
    boolean deleteSubSingle = (deleteSubSingleStr != null) && deleteSubSingleStr.equals("true");

    if (deleteSubSingle && (subIds.size() == 1)) {
      Log.d(LOG_TAG, "createNotification - deleteSubSingle: " + notId);
      mNotificationManager.cancel(appName, notId); 
      dismissNotification(notId);
      return true;
    }

    String deleteStr = extras.getString(DELETE);
    boolean delete = (deleteStr != null) && deleteStr.equals("true");

    if (delete) {
      Log.d(LOG_TAG, "createNotification - delete: " + notId);

      if (notId == 0) {
        mNotificationManager.cancelAll();

        for (Integer notIdAux : notificationMap.keySet()) {
          dismissNotification(notIdAux);
        }
      } else {
        mNotificationManager.cancel(appName, notId); 
        dismissNotification(notId);
      }

      return true;
    }

    return false;
  }

  private static int getNotificationAmount(int notId) {
    List<Bundle> notificationList = notificationMap.get(notId);
    int amount = (notificationList != null) ? notificationList.size() : 0;
    return amount;
  }

  private static Set<Integer> getStoredNotificationSubIds(int notId) {
    List<Bundle> list = notificationMap.get(notId);
    Set<Integer> subIds = new HashSet<Integer>();

    if (list != null) {
      for (Bundle item : list) {
        int subId = parseInt(SUB_ID, item);
        subIds.add(subId);
      }
    }

    return subIds;
  }

  private static boolean isInbox(Bundle extras) {
    String style = extras.getString(STYLE, STYLE_TEXT);
    boolean inbox = STYLE_INBOX.equals(style);
    return inbox;
  }

}
