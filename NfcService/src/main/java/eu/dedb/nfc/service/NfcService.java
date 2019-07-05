package eu.dedb.nfc.service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

//import com.acs.smartcard.Reader;
import com.felhr.usbserial.UsbSerialDevice;

import eu.dedb.nfc.acs.ACS;
import eu.dedb.nfc.chip.mfrc522.MFRC522_UART;
import eu.dedb.nfc.chip.pn53x.PN53X_UART;
import eu.dedb.nfc.lib.AndroidNfcHelper;
import eu.dedb.nfc.lib.PCD;
import eu.dedb.nfc.lib.PICC;
import eu.dedb.nfc.lib.TransceiveResponse;
import eu.dedb.nfc.service.RegisteredComponentCache.ComponentInfo;

public class NfcService extends Service {

	private static boolean isRunning = false;
	private boolean isPaused = false;

	private static final String TAG = "EXT_NFC_SRV";

	public static final String ACTION_DISPATCH_TAG = "eu.dedb.nfc.ACTION_DISPATCH_TAG";
	public static final String ACTION_START_CHOOSER = "eu.dedb.nfc.ACTION_START_CHOOSER";
	public static final String ACTION_STOP_SERVICE = "eu.dedb.nfc.ACTION_STOP_SERVICE";
	public static final String ACTION_USB_PERMISSION = "eu.dedb.nfc.ACTION_USB_PERMISSION";
	public static final String EXTERNAL_NFC = "external";
	public static final String EXTERNAL_NFC_SERVICE = "externalService";
	public static final String EXTERNAL_NFC_DEVICE = "externalDevice";

	private static final String DESCRIPTOR = "android.nfc.INfcTag";
	private static final int SERVICE_NOTIFICATION_ID = 0xDEDB;

	private static final int POLLING_INTERVAL = 500;

	private UsbManager mUsbManager;
	private Vibrator mVibrator;
	// private AudioManager mAudioManager;
	private PendingIntent permissionIntent;
	private SharedPreferences preferences;

	private NotificationManager mNotificationManager;
	private Notification mNotification;
	private RegisteredComponentCache mRegisteredComponentCache;

	private HashMap<UsbDevice, PCDBinder> binders = new HashMap<UsbDevice, PCDBinder>();

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "BCI " + intent);
			String action = intent.getAction();
			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				UsbDevice device = intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				if (device != null && isSupported(device)) {
					mUsbManager.requestPermission(device, permissionIntent);
				}
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				UsbDevice device = intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);

				PCDBinder pcdBinder = binders.get(device);
				binders.remove(device);
				if (pcdBinder != null) {
					pcdBinder.stopPolling();

					if (binders.size() == 0) {
						mNotification.update(SERVICE_NOTIFICATION_ID,
								Notification.STATE_IDLE);
					}
				}
			} else if (ACTION_STOP_SERVICE.equals(action)) {
				stopSelf();
			} else if (Intent.ACTION_SCREEN_ON.equals(action)) {
				isPaused = false;
			} else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
				boolean settings_ignore_screen_lock_state = preferences
						.getBoolean(
								Settings.PREFERENCES_settings_ignore_screen_lock,
								false);
				isPaused = true & !settings_ignore_screen_lock_state;
			}
		}
	};

	private LocalBroadcastManager mLocalBroadcastManager;

	public static boolean isRunning() {
		return isRunning;
	}

	public static boolean isSupported(UsbDevice device) {
		return ACS.isSupported(device) || UsbSerialDevice.isSupported(device);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		isRunning = true;

		// Get System Services
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		// mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		// Get Preferences
		preferences = getSharedPreferences(Settings.PREFERENCES,
				Context.MODE_PRIVATE);

		// Get Registered Component Cache
		mRegisteredComponentCache = new RegisteredComponentCache(this,
				NfcAdapter.ACTION_TECH_DISCOVERED,
				NfcAdapter.ACTION_TECH_DISCOVERED);

		// Register Global Broadcast Receiver
		IntentFilter filter = new IntentFilter();
		//filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		// if (Build.VERSION.SDK_INT >= 21) filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
		registerReceiver(mBroadcastReceiver, filter);

		// Register Local Broadcast Receiver
		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
		IntentFilter localFilter = new IntentFilter();
		localFilter.addAction(NfcService.ACTION_STOP_SERVICE);
		mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, localFilter);

		// Start foreground service with notification
		mNotification = new Notification(this);
		startForeground(SERVICE_NOTIFICATION_ID, mNotification.get());
		mNotification.update(SERVICE_NOTIFICATION_ID, Notification.STATE_IDLE);

		// Prepare pending intent for USB permission requests
		permissionIntent = PendingIntent.getService(this, 0, new Intent(
				ACTION_USB_PERMISSION).setClass(this, this.getClass()), 0);

		// Scan for connected devices
		Iterator<UsbDevice> deviceIterator = mUsbManager.getDeviceList().values().iterator();
		while (deviceIterator.hasNext()) {
			UsbDevice device = deviceIterator.next();
			if (isSupported(device)) {
				mUsbManager.requestPermission(device, permissionIntent);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(TAG, "SCI " + intent);
		String action = intent == null ? null : intent.getAction();
		if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
			UsbDevice device = intent
					.getParcelableExtra(UsbManager.EXTRA_DEVICE);
			if (device != null && isSupported(device)) {
				mUsbManager.requestPermission(device, permissionIntent);
			}
		} else if (ACTION_USB_PERMISSION.equals(action)) {
			if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,
					false)) {
				UsbDevice device = intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				if (!binders.containsKey(device)) {
					PCD pcd = null;

					for (int attempt = 0; attempt < 2; attempt++) {
						pcd = getPCD(device);
						if (pcd != null) {
							mNotification.update(SERVICE_NOTIFICATION_ID,
									pcd.toString());

							PCDBinder pcdBinder = new PCDBinder(pcd);
							pcdBinder.startPolling(POLLING_INTERVAL);
							binders.put(device, pcdBinder);
							break;
						}
					}

					if (pcd == null) {
						Toast.makeText(getApplicationContext(),
								getString(R.string.connection_error),
								Toast.LENGTH_LONG).show();
					}
				}
			} else {
				// TODO Permission not granted
			}
		}
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		// stop all PCD binders
		for (Entry<UsbDevice, PCDBinder> entry : binders.entrySet()) {
			entry.getValue().stopPolling();
		}

		// close Registered Component Cache
		mRegisteredComponentCache.close();

		// unregister broadcast receivers
		unregisterReceiver(mBroadcastReceiver);
		mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);

		// stop foreground service and cancel notifications
		stopForeground(true);
		mNotificationManager.cancel(SERVICE_NOTIFICATION_ID);
		mNotificationManager.cancelAll();

		super.onDestroy();
		isRunning = false;
	}

	private PCD getPCD(UsbDevice mUsbDevice) {

		if (mUsbDevice == null) {
			return null;
		}
		// Try ACS Readers
		ACS mACS = ACS.get(getApplicationContext(), mUsbDevice);
		if (mACS != null) {
			Log.v(TAG, "ACS Reader detected");
			try {
				mACS.init();
				Log.v(TAG, "ACS init OK!");
				return mACS;
			} catch (IOException e) {
				Log.v(TAG, "ACS init failed! " + e.getMessage());
			}
		}

		// Get serial settings
		int baudrate = preferences.getInt(
				Settings.PREFERENCES_settings_baudrate, 0);

		// Try MFRC522 Readers
		MFRC522_UART mMFRC522 = MFRC522_UART.get(getApplicationContext(),
				mUsbDevice, baudrate);
		if (mMFRC522 != null) {
			Log.v(TAG, "MFRC522 detected");
			try {
				mMFRC522.init(baudrate);
				Log.v(TAG, "MFRC522 init OK!");
				return mMFRC522;
			} catch (IOException e) {
				Log.v(TAG, "MFRC522 init failed! " + e.getMessage());
			}
		}

		// Try PN53x Readers
		PN53X_UART mPN53X = PN53X_UART.get(getApplicationContext(), mUsbDevice,
				baudrate);
		if (mPN53X != null) {
			Log.v(TAG, "PN53X detected");
			try {
				mPN53X.init(baudrate);
				Log.v(TAG, "PN53X init OK!");
				return mPN53X;
			} catch (IOException e) {
				Log.v(TAG, "PN53X init failed! " + e.getMessage());
			}
		}

		return null;
	}

	/* PCD (Reader) binder class */
	class PCDBinder extends Binder {

		private PICC mPICC;
		private PCD mPCD;
		private final Timer pollingTimer;

		public PCDBinder(PCD pcd) {
			this.mPCD = pcd;
			this.pollingTimer = new Timer();
			this.attachInterface(null, DESCRIPTOR);

			// statBuilder = new
			// NotificationCompat.Builder(getApplicationContext());
			// statBuilder.setSmallIcon(R.drawable.ic_stat_nfc_connected);
			// statBuilder.setContentTitle(mPCD.toString());

			// mNotificationManager.notify(SERVICE_NOTIFICATION_ID,
			// mBuilder.build());
			// startForeground(SERVICE_NOTIFICATION_ID + 1,
			// statBuilder.build());
		}

		public PICC getPICC() {
			return mPICC;
		}

		public PCD getPCD() {
			return mPCD;
		}

		public void pausePolling(boolean state) {
			isPaused = state;
		}

		ArrayList<ResolveInfo> tryTech(Tag tag) {

			String[] tagTechs = tag.getTechList();
			Arrays.sort(tagTechs);

			// Standard tech dispatch path
			ArrayList<ResolveInfo> matches = new ArrayList<ResolveInfo>();
			List<ComponentInfo> registered = mRegisteredComponentCache.getComponents();

			PackageManager pm = getApplicationContext().getPackageManager();

			// Check each registered activity to see if it matches
			for (ComponentInfo info : registered) {
				// Don't allow wild card matching
				if (filterMatch(tagTechs, info.techs)
						&& isComponentEnabled(pm, info.resolveInfo)) {
					// Add the activity as a match if it's not already in the
					// list
					if (!matches.contains(info.resolveInfo)) {
						matches.add(info.resolveInfo);
					}
				}
			}
			return matches;
		}

		ArrayList<ResolveInfo> tryTag() {

			// Standard tech dispatch path
			ArrayList<ResolveInfo> matches = new ArrayList<ResolveInfo>();
			ArrayList<ResolveInfo> registered = (ArrayList<ResolveInfo>) getApplicationContext()
					.getPackageManager().queryIntentActivities(
							new Intent(NfcAdapter.ACTION_TAG_DISCOVERED), 0);

			PackageManager pm = getApplicationContext().getPackageManager();

			// Check each registered activity to see if it matches
			for (ResolveInfo info : registered) {
				if (isComponentEnabled(pm, info)) {
					// Add the activity as a match if it's not already in the
					// list
					if (!matches.contains(info)) {
						matches.add(info);
					}
				}
			}
			return matches;
		}

		/** Returns true if the tech list filter matches the techs on the tag */
		boolean filterMatch(String[] tagTechs, String[] filterTechs) {
			if (filterTechs == null || filterTechs.length == 0)
				return false;

			for (String tech : filterTechs) {
				if (Arrays.binarySearch(tagTechs, tech) < 0) {
					return false;
				}
			}
			return true;
		}

		boolean isComponentEnabled(PackageManager pm, ResolveInfo info) {
			boolean enabled = false;
			ComponentName compname = new ComponentName(
					info.activityInfo.packageName, info.activityInfo.name);
			try {
				// Note that getActivityInfo() will internally call
				// isEnabledLP() to determine whether the component
				// enabled. If it's not, null is returned.
				if (pm.getActivityInfo(compname, 0) != null) {
					enabled = true;
				}
			} catch (PackageManager.NameNotFoundException e) {
				enabled = false;
			}
			if (!enabled) {
				Log.d(TAG, "Component not enabled: " + compname);
			}
			return enabled;
		}

		private boolean recoverPCD() {
			// TODO try to recover IO errors
			if (mPCD != null) {
				mPCD = mPCD.recover();
				Log.v(TAG, "PCD is died, trying to recover");
				mNotification.update(SERVICE_NOTIFICATION_ID,
						Notification.STATE_ERROR);
			}
			if (mPCD == null) {
				stopPolling();
				return false;
			} else {
				return true;
			}
		}

		public void startPolling(int pollingInterval) {
			pollingTimer.schedule(pollingTask, 0, pollingInterval);
		}

		public void stopPolling() {
			pollingTask.cancel();
			if (mPCD != null) {
				mPCD.close();
				mPCD = null;
			}
		}

		private TimerTask pollingTask = new TimerTask() {

			@Override
			public void run() {
				//if (isBusy)
				//	return;
				if (mPCD != null) {
					// Check if present
					if (mPICC != null) {

						if (AppChooser.isActive()) {
							mNotification.update(SERVICE_NOTIFICATION_ID,
									Notification.STATE_TRANSFER);
						} else {
							mNotification.update(SERVICE_NOTIFICATION_ID,
									Notification.STATE_PRESENT);
						}

						Log.v(TAG, "PICC presence check");
						boolean isPresent = false;
						try {
							isPresent = mPICC.isPresent(mPICC.getHandle());
						} catch (Exception e) {
							e.printStackTrace();
							recoverPCD();
						}
						if (!isPresent) {
							Log.v(TAG, "PICC is lost");
							mPICC = null;
						} else {
							Log.v(TAG, "PICC is present");
						}
					}

					// Poll if absent
					if (mPICC == null && !isPaused) {

						mNotification.update(SERVICE_NOTIFICATION_ID,
								Notification.STATE_POLLING);

						int flags = 0;
						if (preferences.getBoolean(Settings.PREFERENCES_settings_chinese_activation,false))
							flags |= PCD.ACTIVATE_CHINESE;
						if (preferences.getBoolean(Settings.PREFERENCES_settings_chinese_any_key,false))
							flags |= PCD.AUTH_ALL;
						if (preferences.getBoolean(Settings.PREFERENCES_settings_ignore_BCC_error,false))
							flags |= PCD.IGNORE_BCC_ERROR;
						if (preferences.getBoolean(Settings.PREFERENCES_settings_pcsc_mode, false))
							flags |= PCD.PCSC_MODE;

						Log.v(TAG, "PICC discovering");
						try {
							mPICC = mPCD.poll(flags);
						} catch (Exception e) {
							e.printStackTrace();
							recoverPCD();
						}
						if (mPICC != null) {

							mNotification.update(SERVICE_NOTIFICATION_ID,
									Notification.STATE_PRESENT);

							Log.v(TAG, "PICC is discovered");
							dispatchTag();
						} else {
							Log.v(TAG, "No PICC");
						}
					}
				}
			}
		};
		//private boolean isBusy;

		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply,
				int flags) throws RemoteException {
			//isBusy = true;
			String tName = AndroidNfcHelper.TRANSACTIONS.get(code);
			Log.v(TAG, "Transaction " + code + "(" + tName + ") from "
					+ getPackageManager().getNameForUid(Binder.getCallingUid()));

			if (tName == null) {
				return false;
			} else if (tName.equals("TRANSACTION_isPresent")) {
				data.enforceInterface(DESCRIPTOR);
				int nativeHandle = data.readInt();

				// TODO
				boolean isPresent = false;
				if (mPICC != null) {
					try {
						isPresent = mPICC.isPresent(nativeHandle);
					} catch (IOException e) {
						e.printStackTrace();
						recoverPCD();
					}
				}
				reply.writeNoException();
				reply.writeInt(isPresent ? 1 : 0);
			} else if (tName.equals("TRANSACTION_connect")) {
				data.enforceInterface(DESCRIPTOR);
				int nativeHandle = data.readInt();
				int technology = data.readInt();
				// TODO
				int error;
				if (mPICC != null) {
					error = mPICC.connect(nativeHandle, technology);
				} else {
					error = -5;
				}
				reply.writeNoException();
				reply.writeInt(error);
			} else if (tName.equals("TRANSACTION_reconnect")) {
				data.enforceInterface(DESCRIPTOR);
				int nativeHandle = data.readInt();
				reply.readException();
				// TODO
				int error = 0;
				reply.writeNoException();
				reply.writeInt(error);
			} else if (tName.equals("TRANSACTION_transceive")) {
				Log.v(TAG, "Transceive started");
				data.enforceInterface(DESCRIPTOR);
				int nativeHandle = data.readInt();
				byte[] sendBuf = data.createByteArray();
				boolean raw = (0 != data.readInt());

				TransceiveResponse tresponse = null;
				if (mPICC != null) {
					try {
						tresponse = mPICC.transceive(nativeHandle, sendBuf, raw);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();

						recoverPCD();
					}
				}

				reply.writeNoException();
				if (tresponse != null) {
					reply.writeInt(1);
					tresponse.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
				} else {
					reply.writeInt(0);
				}

				int success = (tresponse == null) ? 0 : 1;

				reply.writeInt(success);
				if (success == 1) {
					int mResult = tresponse.getErrorCode();
					reply.writeInt(mResult);
					if (mResult == 0) {
						byte[] mResponseData = tresponse.getResponse();
						reply.writeInt(mResponseData.length);
						reply.writeByteArray(mResponseData);
					}
				}
				reply.setDataPosition(0);
				Log.v(TAG, "Transceive finished");
			} else if (tName.equals("TRANSACTION_resetTimeouts")) {
				data.enforceInterface(DESCRIPTOR);

				// TODO

				if (mPICC != null) {
					mPICC.resetTimeouts();

				}
				reply.writeNoException();
				reply.setDataPosition(0);
			} else if (tName.equals("TRANSACTION_setTimeout")) {
				data.enforceInterface(DESCRIPTOR);
				int technology = data.readInt();
				int timeout = data.readInt();
				int success = -1;

				// TODO

				if (mPICC != null) {
					success = mPICC.setTimeout(technology, timeout);
				}
				reply.writeNoException();
				reply.writeInt(success);
				reply.setDataPosition(0);
			} else if (tName.equals("TRANSACTION_getTimeout")) {
				data.enforceInterface(DESCRIPTOR);
				int technology = data.readInt();
				int timeout = -1;

				// TODO

				if (mPICC != null) {
					timeout = mPICC.getTimeout(technology);
				}
				reply.writeNoException();
				reply.writeInt(timeout);
				reply.setDataPosition(0);
			} else if (tName.equals("TRANSACTION_getMaxTransceiveLength")) {
				data.enforceInterface(DESCRIPTOR);
				int technology = data.readInt();
				int length = 0;

				// TODO

				if (mPICC != null) {
					length = mPICC.getMaxTransceiveLength(technology);
				}
				reply.writeNoException();
				reply.writeInt(length);
				reply.setDataPosition(0);
			} else {
				Log.v(TAG, "Unimplemented " + tName);
			}

			//isBusy = false;
			return true;
		}

		private void dispatchTag() {
			if (mPICC != null) {
				dispatchTag(mPICC.getTag(this));
			}
		}

		private void dispatchTag(Tag tag) {
			if (tag == null) {
				return;
			}

			boolean settings_vibrate_state = preferences.getBoolean(
					Settings.PREFERENCES_settings_vibrate, false);
			if (settings_vibrate_state)
				mVibrator.vibrate(100);

			boolean system_dispatched = false;
			boolean hasPermission = checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
			boolean settings_system_dispatch_state = preferences.getBoolean(Settings.PREFERENCES_settings_system_dispatch, false);
			if (hasPermission && settings_system_dispatch_state) {
				// Try system dispatch
				NfcAdapter defaultAdapter = NfcAdapter
						.getDefaultAdapter(getApplicationContext());
				if (defaultAdapter != null) {
					try {
						Method dispatch = defaultAdapter.getClass().getMethod(
								"dispatch", tag.getClass());
						dispatch.invoke(defaultAdapter, tag);
						system_dispatched = true;
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					// IN CASE OF NO INTERNAL NFC
					// try {
					// Class<?> cServiceManager =
					// Class.forName("android.os.ServiceManager");
					// Method getService =
					// cServiceManager.getMethod("getService", String.class);
					// IBinder b = (IBinder) getService.invoke(null, "nfc");
					// if(b == null) {
					// Log.v(TAG, "INfcAdapter is null");
					// Method listServices =
					// cServiceManager.getMethod("listServices");
					// String[] services = (String[]) listServices.invoke(null);
					// for(String service : services)
					// Log.v(TAG, "Service: " + service);
					// }
					// } catch (Exception e) {
					// e.printStackTrace();
					// }
				}
			}

			if (!system_dispatched) {
				// Create targetIntent
				Intent targetIntent = new Intent(
						NfcAdapter.ACTION_TECH_DISCOVERED);
				targetIntent.putExtra(NfcAdapter.EXTRA_TAG, tag);
				targetIntent.putExtra(NfcAdapter.EXTRA_ID, tag.getId());
				targetIntent.putExtra(NfcService.EXTERNAL_NFC, true);
				targetIntent.putExtra(NfcService.EXTERNAL_NFC_SERVICE, this
						.getClass().getCanonicalName());
				targetIntent.putExtra(NfcService.EXTERNAL_NFC_DEVICE,
						mPCD.toString());

				ArrayList<ResolveInfo> rList = tryTech(tag);

				if (rList.isEmpty()) {
					targetIntent.setAction(NfcAdapter.ACTION_TAG_DISCOVERED);
					rList = tryTag();
				}

				// Create intent with target intent
				Intent intent = new Intent(NfcService.ACTION_DISPATCH_TAG);
				intent.putExtra(Intent.EXTRA_INTENT, targetIntent);
				intent.putParcelableArrayListExtra(
						AppChooser.EXTRA_RESOLVE_INFOS, rList);

				if (AppChooser.isActive()) {
					// Broadcast intent if AppChooser is running
					mLocalBroadcastManager.sendBroadcast(intent);
				} else {
					// Else start AppChooser with Intent
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
							// | Intent.FLAG_ACTIVITY_CLEAR_TASK
							// | Intent.FLAG_ACTIVITY_NO_HISTORY
							// | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
					);
					intent.setClass(getApplicationContext(), AppChooser.class);
					startActivity(intent);
				}
			}
		}
	}
}
