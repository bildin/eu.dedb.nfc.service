package eu.dedb.nfc.service;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.Spinner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

import eu.dedb.nfc.acs.ACS;
import eu.dedb.nfc.chip.mfrc522.MFRC522_UART;
import eu.dedb.nfc.chip.pn53x.PN53X;
import eu.dedb.nfc.chip.pn53x.PN53X_UART;
import eu.dedb.nfc.lib.PICC;
import eu.dedb.nfc.lib.PICC_A;

public class Settings extends Activity implements OnLongClickListener,
		OnItemSelectedListener {

	public final static String PREFERENCES = "preferences";
	public final static String PREFERENCES_settings_start_at_boot = "settings_start_at_boot";
	public final static String PREFERENCES_settings_vibrate = "settings_vibrate";
	public final static String PREFERENCES_settings_system_dispatch = "settings_system_dispatch";
	public final static String PREFERENCES_settings_pcsc_mode = "settings_pcsc_mode";
	public final static String PREFERENCES_settings_chinese_activation = "settings_chinese_activation";
	public final static String PREFERENCES_settings_chinese_any_key = "settings_chinese_any_key";
	public final static String PREFERENCES_settings_ignore_BCC_error = "settings_ignore_BCC_error";
	public final static String PREFERENCES_settings_ignore_screen_lock = "settings_ignore_screen_lock";
	public final static String PREFERENCES_settings_baudrate = "settings_baudrate";

	private SharedPreferences preferences;

	boolean hasPermission = false;
	private LocalBroadcastManager mLocalBroadcastManager;
	ProgressDialog progressDialog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_settings_layout);

		CheckBox settings_service_state = (CheckBox) findViewById(R.id.settings_service_state);
		CheckBox settings_start_at_boot = (CheckBox) findViewById(R.id.settings_start_at_boot);
		CheckBox settings_system_dispatch = (CheckBox) findViewById(R.id.settings_system_dispatch);
		CheckBox settings_pcsc_mode = (CheckBox) findViewById(R.id.settings_pcsc_mode);
		CheckBox settings_vibrate = (CheckBox) findViewById(R.id.settings_vibrate);
		CheckBox settings_ignore_screen_lock = (CheckBox) findViewById(R.id.settings_ignore_screen_lock);
		CheckBox settings_chinese_activation = (CheckBox) findViewById(R.id.settings_chinese_activation);
		CheckBox settings_chinese_any_key = (CheckBox) findViewById(R.id.settings_chinese_any_key);
		CheckBox settings_ignore_BCC_error = (CheckBox) findViewById(R.id.settings_ignore_BCC_error);
		Spinner settings_baudrate = (Spinner) findViewById(R.id.settings_baudrate);

		preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);

		boolean settings_start_at_boot_state = preferences.getBoolean(
				PREFERENCES_settings_start_at_boot, false);
		boolean settings_vibrate_state = preferences.getBoolean(
				PREFERENCES_settings_vibrate, false);
		boolean settings_system_dispatch_state = preferences.getBoolean(
				PREFERENCES_settings_system_dispatch, false);
		boolean settings_pcsc_mode_state = preferences.getBoolean(
				PREFERENCES_settings_pcsc_mode, false);
		boolean settings_chinese_activation_state = preferences.getBoolean(
				PREFERENCES_settings_chinese_activation, false);
		boolean settings_chinese_any_key_state = preferences.getBoolean(
				PREFERENCES_settings_chinese_any_key, false);
		boolean settings_ignore_BCC_error_state = preferences.getBoolean(
				PREFERENCES_settings_ignore_BCC_error, false);
		boolean settings_ignore_screen_lock_state = preferences.getBoolean(
				PREFERENCES_settings_ignore_screen_lock, false);
		int settings_baudrate_state = preferences.getInt(
				PREFERENCES_settings_baudrate, 0);

		hasPermission = checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;

		settings_service_state.setChecked(NfcService.isRunning());
		settings_start_at_boot.setChecked(settings_start_at_boot_state);
		settings_vibrate.setChecked(settings_vibrate_state);
		settings_pcsc_mode.setChecked(settings_pcsc_mode_state);
		settings_system_dispatch.setChecked(hasPermission
				&& settings_system_dispatch_state);
		settings_chinese_activation
				.setChecked(settings_chinese_activation_state);
		settings_chinese_any_key.setChecked(settings_chinese_any_key_state);
		settings_ignore_BCC_error.setChecked(settings_ignore_BCC_error_state);
		settings_ignore_screen_lock
				.setChecked(settings_ignore_screen_lock_state);

		int settings_baudrate_state_index = 0;
		int settings_baudrate_state_count = settings_baudrate.getAdapter()
				.getCount();
		for (int position = 1; position < settings_baudrate_state_count; position++) {
			try {
				if (Integer.parseInt(settings_baudrate.getAdapter()
						.getItem(position).toString()) == settings_baudrate_state) {
					settings_baudrate_state_index = position;
					break;
				}
			} catch (Exception e) {
			}
		}
		settings_baudrate.setSelection(settings_baudrate_state_index);

		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
		progressDialog = new ProgressDialog(this);
		settings_service_state.setOnLongClickListener(this);
		settings_baudrate.setOnItemSelectedListener(this);
	}

	public void onClick(final View view) {
		SharedPreferences.Editor editor = preferences.edit();
		boolean state = false;
		switch (view.getId()) {
		case R.id.settings_service_state:
			((CheckBox) view).setChecked(NfcService.isRunning());
			test();
			break;
		case R.id.settings_start_at_boot:
			state = ((CheckBox) view).isChecked();
			int flag = (state ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
					: PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
			ComponentName component = new ComponentName(this, StartUp.class);
			getPackageManager().setComponentEnabledSetting(component, flag,
					PackageManager.DONT_KILL_APP);
			editor.putBoolean(PREFERENCES_settings_start_at_boot, state);
			editor.commit();
			break;
		case R.id.settings_vibrate:
			state = ((CheckBox) view).isChecked();
			editor.putBoolean(PREFERENCES_settings_vibrate, state);
			editor.commit();
			break;
		case R.id.settings_pcsc_mode:
			state = ((CheckBox) view).isChecked();
			editor.putBoolean(PREFERENCES_settings_pcsc_mode, state);
			editor.commit();
			break;
		case R.id.settings_system_dispatch:
			state = ((CheckBox) view).isChecked();
			hasPermission = checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
			if (!hasPermission) {
				final boolean request = state;
				final ProgressDialog progressDialog = new ProgressDialog(this);
				progressDialog.setMessage(getString(R.string.root_request));
				progressDialog.setCancelable(false);
				progressDialog.setOnCancelListener(new OnCancelListener() {

					@Override
					public void onCancel(DialogInterface dialog) {
						boolean state = hasPermission ? request : false;
						((CheckBox) view).setChecked(state);

						SharedPreferences.Editor editor = preferences.edit();
						editor.putBoolean(PREFERENCES_settings_system_dispatch,
								state);
						editor.commit();
					}
				});
				progressDialog.show();

				new Thread(new Runnable() {
					@Override
					public void run() {
						String[] cmd = {
								"su",
								"-c",
								"pm grant "
										+ getPackageName()
										+ " "
										+ android.Manifest.permission.WRITE_SECURE_SETTINGS };
						int success = -1;
						try {
							Process p = Runtime.getRuntime().exec(cmd);
							success = p.waitFor();
							if (success == 0) {
								hasPermission = true;
							} else {
							}
						} catch (Exception e) {
						}
						String message;
						switch (success) {
						case -1:
							message = getString(R.string.root_request_unavailable);
							break;
						case 0:
							message = getString(R.string.root_request_granted);
							break;
						default:
							message = getString(R.string.root_request_declined);
							break;
						}
						final String msg = message;
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								progressDialog.setMessage(msg);
								progressDialog.setCancelable(true);
							}
						});
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
						progressDialog.cancel();
					}
				}).start();

			} else {
				editor.putBoolean(PREFERENCES_settings_system_dispatch, state);
				editor.commit();
			}
			break;
		case R.id.settings_ignore_screen_lock:
			state = ((CheckBox) view).isChecked();
			editor.putBoolean(PREFERENCES_settings_ignore_screen_lock, state);
			editor.commit();
			break;
		case R.id.settings_chinese_activation:
			state = ((CheckBox) view).isChecked();
			editor.putBoolean(PREFERENCES_settings_chinese_activation, state);
			editor.commit();
			break;
		case R.id.settings_chinese_any_key:
			state = ((CheckBox) view).isChecked();
			editor.putBoolean(PREFERENCES_settings_chinese_any_key, state);
			editor.commit();
			break;
		case R.id.settings_ignore_BCC_error:
			state = ((CheckBox) view).isChecked();
			editor.putBoolean(PREFERENCES_settings_ignore_BCC_error, state);
			editor.commit();
			break;
		default:
		}
	}

	@Override
	public boolean onLongClick(final View view) {
		progressDialog.setCancelable(false);
		progressDialog.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface dialog) {
				((CheckBox) view).setChecked(NfcService.isRunning());
			}
		});
		boolean settings_service_current_state = NfcService.isRunning();
		final boolean request;
		if (settings_service_current_state) {
			request = false;
			Intent stopServiceIntent = new Intent(
					NfcService.ACTION_STOP_SERVICE);
			mLocalBroadcastManager.sendBroadcast(stopServiceIntent);
			progressDialog.setMessage(getString(R.string.service_stopping));
			Log.v("DEBUG", "Stop Service");
		} else {
			request = true;
			Intent startServiceIntent = getIntent();
			startServiceIntent.setClass(getApplicationContext(),
					NfcService.class);
			startService(startServiceIntent);
			progressDialog.setMessage(getString(R.string.service_starting));
			Log.v("DEBUG", "Start Service");
		}

		progressDialog.show();

		new Thread(new Runnable() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				while (NfcService.isRunning() != request) {
					if (System.currentTimeMillis() - start > 5000)
						break;
				}
				progressDialog.cancel();
			}
		}).start();

		return true;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		int baudrate = 0;
		if (position > 0)
			try {
				baudrate = Integer
						.parseInt(parent.getSelectedItem().toString());
			} catch (Exception e) {

			}
		int state = preferences.getInt(PREFERENCES_settings_baudrate, 0);
		if (state != baudrate) {
			SharedPreferences.Editor editor = preferences.edit();
			editor.putInt(PREFERENCES_settings_baudrate, baudrate);
			editor.commit();
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
		// TODO Auto-generated method stub

	}

	protected void test() {
		if(false)
			return;


		boolean hasNfcFeatureValue = getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
		Log.v("TEST", "hasNfcFeatureValue " + hasNfcFeatureValue);

		NfcManager nfcManager = (NfcManager) getSystemService(Context.NFC_SERVICE);
		Log.v("TEST", "nfcManager " + nfcManager);

		NfcAdapter nfcAdapter = nfcManager.getDefaultAdapter();
		Log.v("TEST", "nfcAdapter " + nfcAdapter);

		PICC mPICC;

		UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		PendingIntent permissionIntent = PendingIntent.getService(this, 0, new Intent(
				NfcService.ACTION_USB_PERMISSION).setClass(this, this.getClass()), 0);
		Iterator<UsbDevice> deviceIterator = mUsbManager.getDeviceList()
				.values().iterator();
		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();
			if (NfcService.isSupported(device)) {
				//mUsbManager.requestPermission(device, permissionIntent);
				int baudrate = preferences.getInt(
						Settings.PREFERENCES_settings_baudrate, 0);

				/*
				MFRC522_UART mMFRC522 = MFRC522_UART.get(getApplicationContext(),
						device, baudrate);
				if(mMFRC522 != null) {
					Log.v("TEST", mMFRC522.toString());
					try {
						mMFRC522.init();
						PICC mPICC = mMFRC522.poll(0);
						if(mPICC != null) {
							Log.v("TEST", mPICC.toString());
						} else {
							Log.v("TEST", "Poll failed!");
						}
					} catch (IOException e) {
						e.printStackTrace();
					}

				} else {
					Log.v("TEST", "Connection failed!");
				}
				// */

				PN53X_UART mPN532 = PN53X_UART.get(getApplicationContext(),
						device, baudrate);
				if(mPN532 != null) {
					Log.v("TEST", mPN532.toString());
					try {
						mPN532.init();
						/*
						for(short addr = 0x6300; addr <= 0x633f; addr++) {
							Log.v("REGMAP", String.format("%04X: %02X", addr, mPN532.readReg(addr)));
						}
						// */

						Log.v("TEST", "Macro");
						mPICC = mPN532.poll(0);

						Log.v("TEST", String.format("%02X", mPN532.readReg((short) 0x6304)));

						if(mPICC != null) {
							Log.v("TEST", mPICC.toString());
							Log.v("TEST", PN53X.toStr(mPN532.transceive(new byte[] {0x30, 0x00},true).getResponse()));
						} else {
							Log.v("TEST", "Poll failed!");
						}


						mPN532.init();

						Log.v("TEST", "Manual");
						mPICC = mPN532.poll(1);

						Log.v("TEST", String.format("%02X", mPN532.readReg((short) 0x6304)));

						if(mPICC != null) {
							Log.v("TEST", mPICC.toString());
							Log.v("TEST", PN53X.toStr(mPN532.transceive(new byte[] {0x30, 0x00},true).getResponse()));
						} else {
							Log.v("TEST", "Poll failed!");
						}
					} catch (IOException e) {
						e.printStackTrace();
					}

				} else {
					Log.v("TEST", "Connection failed!");
				}

			}
		}
	}
}
