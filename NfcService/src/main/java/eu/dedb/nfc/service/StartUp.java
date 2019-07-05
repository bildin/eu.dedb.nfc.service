package eu.dedb.nfc.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartUp extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		intent.setClass(context, NfcService.class);
		context.startService(intent);
	}
}
