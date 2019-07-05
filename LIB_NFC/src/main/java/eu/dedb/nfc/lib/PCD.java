package eu.dedb.nfc.lib;

import java.io.IOException;

public interface PCD {

	public static final int IGNORE_BCC_ERROR = 1;
	public static final int ACTIVATE_CHINESE = 2;
	public static final int AUTH_ALL = 4;
	public static final int PCSC_MODE = 8;
	
	PCD recover();
	void init() throws IOException;
//	void power(boolean state) throws IOException;
	TransceiveResponse transceive_bytes(byte[] sendBuf, int sendLen, int timeout) throws IOException;
	TransceiveResponse transceive_bits(byte[] sendBuf, int sendLen, int timeout) throws IOException;
	TransceiveResponse transceive(byte[] sendBuf, boolean raw) throws IOException;
	PICC poll(int flags) throws IOException;
	void close();
}
