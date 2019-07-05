package eu.dedb.nfc.service;

import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class Notification {

	public final static int STATE_IDLE = 0;
	public final static int STATE_CONNECTED = 1;
	public final static int STATE_POLLING = 2;
	public final static int STATE_PRESENT = 3;
	public final static int STATE_TRANSFER = 4;
	public final static int STATE_ERROR = 5;
	private static final String TAG = "EXT_NFC_NTF";

	private Context mContext;
	private NotificationManager mNotificationManager;
	private NotificationCompat.Builder mBuilder;
	private int mState = STATE_IDLE;

	public Notification(Context ctx) {
		mContext = ctx;
		mNotificationManager = (NotificationManager) ctx
				.getSystemService(Context.NOTIFICATION_SERVICE);

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(TAG, "NFC",
					NotificationManager.IMPORTANCE_HIGH);
			//channel.setDescription("My channel description");
			//channel.enableLights(true);
			//channel.setLightColor(Color.RED);
			//channel.enableVibration(false);
			mNotificationManager.createNotificationChannel(channel);
		}

		mBuilder = new NotificationCompat.Builder(ctx,TAG);
		PendingIntent pendingIntent = PendingIntent.getActivity(
				ctx.getApplicationContext(), 0,
				new Intent(ctx, Settings.class), PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(pendingIntent);

		mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_idle);
		mBuilder.setContentTitle(ctx.getString(R.string.state_no_device));
	}

	public android.app.Notification get() {
		return mBuilder.build();
	}

	public void update(int id, String title) {
		mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_connected);
		mBuilder.setContentTitle(title);
		mNotificationManager.notify(id, mBuilder.build());
	}

	public void update(int id, int state) {
		Log.v(TAG, "Service notification state changed from " + mState + " to "
				+ state);
		if (state == mState)
			return;

		mState = state;
		switch (state) {
		case STATE_IDLE:
			mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_idle);
			mBuilder.setContentTitle(mContext
					.getString(R.string.state_no_device));
			mBuilder.setContentInfo(null);
			break;
		case STATE_CONNECTED:
			mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_connected);
			break;
		case STATE_POLLING:
			mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_polling);
			mBuilder.setContentInfo(mContext
					.getString(R.string.state_card_polling));
			break;
		case STATE_PRESENT:
			mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_present);
			mBuilder.setContentInfo(mContext
					.getString(R.string.state_card_present));
			break;
		case STATE_TRANSFER:
			mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_transfer);
			mBuilder.setContentInfo(mContext
					.getString(R.string.state_card_active));
			break;
		case STATE_ERROR:
			mBuilder.setSmallIcon(R.drawable.ic_stat_nfc_error);
			mBuilder.setContentInfo(mContext.getString(R.string.state_error));
			break;
		}
		mNotificationManager.notify(id, mBuilder.build());
	}
}
