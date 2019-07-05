package eu.dedb.nfc.lib;

import java.io.IOException;

public interface AndroidNfcTag {

	public int close(int nativeHandle) throws android.os.RemoteException;

	public int connect(int nativeHandle, int technology)
			throws android.os.RemoteException;

	public int reconnect(int nativeHandle) throws android.os.RemoteException;

	public int[] getTechList(int nativeHandle)
			throws android.os.RemoteException;

	public boolean isNdef(int nativeHandle) throws android.os.RemoteException;

	public boolean isPresent(int nativeHandle)
			throws android.os.RemoteException, IOException;

	// public android.nfc.TransceiveResult transceive(int nativeHandle, byte[]
	// data, boolean raw) throws android.os.RemoteException;
	public TransceiveResponse transceive(int nativeHandle, byte[] data,
			boolean raw) throws android.os.RemoteException, IOException;

	public android.nfc.NdefMessage ndefRead(int nativeHandle)
			throws android.os.RemoteException;

	public int ndefWrite(int nativeHandle, android.nfc.NdefMessage msg)
			throws android.os.RemoteException;

	public int ndefMakeReadOnly(int nativeHandle)
			throws android.os.RemoteException;

	public boolean ndefIsWritable(int nativeHandle)
			throws android.os.RemoteException;

	public int formatNdef(int nativeHandle, byte[] key)
			throws android.os.RemoteException;

	public android.nfc.Tag rediscover(int nativehandle)
			throws android.os.RemoteException;

	public int setTimeout(int technology, int timeout)
			throws android.os.RemoteException;

	public int getTimeout(int technology) throws android.os.RemoteException;

	public void resetTimeouts() throws android.os.RemoteException;

	public boolean canMakeReadOnly(int ndefType)
			throws android.os.RemoteException;

	public int getMaxTransceiveLength(int technology)
			throws android.os.RemoteException;

	public boolean getExtendedLengthApdusSupported()
			throws android.os.RemoteException;
}
