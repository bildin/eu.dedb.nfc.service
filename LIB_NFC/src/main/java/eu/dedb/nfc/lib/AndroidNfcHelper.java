package eu.dedb.nfc.lib;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.util.SparseArray;

public final class AndroidNfcHelper {

	public final static SparseArray<String> TagTechnology = getSupportedTechnologies();
	public final static SparseArray<String> TRANSACTIONS = getSupportedTransactions();

	private static SparseArray<String> getSupportedTechnologies() {
		SparseArray<String> technologies = new SparseArray<String>();
		try {
			Class<?> cl = Class.forName("android.nfc.tech.TagTechnology");
			Field[] flds = cl.getDeclaredFields();
			for (Field fld : flds) {
				String fldname = fld.getName();
				fld.setAccessible(true);
				Object fldvalue = fld.get(null);
				if (fldvalue instanceof Integer)
					technologies.put((Integer) fldvalue, fldname);
			}
		} catch (Exception e) {
		}
		return technologies;
	}

	private static SparseArray<String> getSupportedTransactions() {
		SparseArray<String> transactions = new SparseArray<String>();
		try {
			Class<?> cl = Class.forName("android.nfc.INfcTag$Stub");
			Field[] flds = cl.getDeclaredFields();
			for (Field fld : flds) {
				String fldname = fld.getName();
				fld.setAccessible(true);
				Object fldvalue = fld.get(null);
				if (fldvalue instanceof Integer
						&& fldname.startsWith("TRANSACTION"))
					transactions.put((Integer) fldvalue, fldname);
			}
		} catch (Exception e) {
		}
		return transactions;
	}
	
	public static String getErrorName(int code) {
		String name = null;
		try {
			Class<?> cl = Class.forName("android.nfc.ErrorCodes");
			Method asString = cl.getMethod("asString", int.class);
			name = (String) asString.invoke(null, code);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return name;
	}

	public static boolean setForegroundDispatch(Context ctx,
			PendingIntent intent, IntentFilter[] filter) {
		try {
			NfcAdapter dAdapter = NfcAdapter.getDefaultAdapter(ctx);
			Field sServiceField = dAdapter.getClass().getDeclaredField(
					"sService");
			sServiceField.setAccessible(true);
			Object sService = sServiceField.get(dAdapter);
			Class<?> classTechListParcel = Class
					.forName("android.nfc.TechListParcel");
			Method setForegroundDispatch = sService
					.getClass()
					.getDeclaredMethod(
							"setForegroundDispatch",
							new Class<?>[] { PendingIntent.class,
									IntentFilter[].class, classTechListParcel });
			setForegroundDispatch.invoke(sService, new Object[] { intent,
					filter, null });
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
