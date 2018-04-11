package com.adobe.phonegap.push;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.support.v4.app.RemoteInput;

import java.util.List;

public class PushHandlerActivity extends Activity implements PushConstants {
	private static String LOG_TAG = "Push_HandlerActivity";

	/*
	* this activity will be started if the user touches a notification that we own.
	* We send it's data off to the push plugin for processing.
	* If needed, we boot up the main activity to kickstart the application.
	* @see android.app.Activity#onCreate(android.os.Bundle)
	*/
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Intent intent = getIntent();

		int notId = intent.getExtras().getInt(NOT_ID, 0);
		Log.d(LOG_TAG, "not id = " + notId);

		FCMService.dismissNotification(notId);

		super.onCreate(savedInstanceState);		
		Log.v(LOG_TAG, "onCreate");

		String callback = getIntent().getExtras().getString("callback");
		Log.d(LOG_TAG, "callback = " + callback);

		boolean foreground = getIntent().getExtras().getBoolean("foreground", true);
		boolean startOnBackground = getIntent().getExtras().getBoolean(START_IN_BACKGROUND, false);
		boolean dismissed = getIntent().getExtras().getBoolean(DISMISSED, false);
		Log.d(LOG_TAG, "dismissed = " + dismissed);

		if (!startOnBackground) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancel(FCMService.getAppName(this), notId);
		}

		boolean isPushPluginActive = PushPlugin.isActive();
		boolean inline = processPushBundle(isPushPluginActive, intent);

		if (inline && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N && !startOnBackground) {
			foreground = true;
		}

		Log.d(LOG_TAG, "bringToForeground = " + foreground);

		finish();

		if (!dismissed) {
			Log.d(LOG_TAG, "isPushPluginActive = " + isPushPluginActive);

			if (!isPushPluginActive && foreground && inline) {
				Log.d(LOG_TAG, "forceMainActivityReload");
				forceMainActivityReload(false);
			} else if (startOnBackground) {
				Log.d(LOG_TAG, "startOnBackgroundTrue");
				forceMainActivityReload(true);
			} else {
				Log.d(LOG_TAG, "don't want main activity");
			}
		}
	}

	/**
	* Takes the pushBundle extras from the intent,
	* and sends it through to the PushPlugin for processing.
	*/
	private boolean processPushBundle(boolean isPushPluginActive, Intent intent) {
		Bundle extras = getIntent().getExtras();
		Bundle remoteInput = null;

		if (extras != null) {
			Bundle originalExtras = extras.getBundle(PUSH_BUNDLE);
			List<Bundle> extrasList = extras.getParcelableArrayList(PUSH_LIST_BUNDLE);

			boolean foreground = false;
			boolean coldstart = !isPushPluginActive;
			Boolean dismissed = extras.getBoolean(DISMISSED);
			String actionCallback = extras.getString(CALLBACK);

			originalExtras.putBoolean(FOREGROUND, foreground);
			originalExtras.putBoolean(COLDSTART, coldstart);
			originalExtras.putBoolean(DISMISSED, dismissed);
			originalExtras.putString(ACTION_CALLBACK, actionCallback);
			originalExtras.remove(NO_CACHE);

			remoteInput = RemoteInput.getResultsFromIntent(intent);
			
			if (remoteInput != null) {
				String inputString = remoteInput.getCharSequence(INLINE_REPLY).toString();
				Log.d(LOG_TAG, "response: " + inputString);
				originalExtras.putString(INLINE_REPLY, inputString);
			}

			// PushPlugin.sendExtras(originalExtras);
			PushPlugin.sendMainData(originalExtras, extrasList);
		}
		return remoteInput == null;
	}

	/**
	* Forces the main activity to re-launch if it's unloaded.
	*/
	private void forceMainActivityReload(boolean startOnBackground) {
		PackageManager pm = getPackageManager();
		Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());

		Bundle extras = getIntent().getExtras();

		if (extras != null) {
			Bundle originalExtras = extras.getBundle(PUSH_BUNDLE);

			if (originalExtras != null) {
				launchIntent.putExtras(originalExtras);
			}

			launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			launchIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
			launchIntent.putExtra(START_IN_BACKGROUND, startOnBackground);
		}

		startActivity(launchIntent);
	}

	@Override
	protected void onResume() {
		super.onResume();
		final NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();
	}
}
