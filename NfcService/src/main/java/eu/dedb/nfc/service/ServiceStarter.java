package eu.dedb.nfc.service;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ServiceStarter extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		intent.setClass(this, NfcService.class);
		startService(intent);
		finish();
	}
}
