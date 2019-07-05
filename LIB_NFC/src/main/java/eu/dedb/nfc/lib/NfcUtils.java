package eu.dedb.nfc.lib;

public class NfcUtils {

	public final static int CRC_INIT_VALUE_DEFAULT = 0x6363;
	public final static int CRC_INIT_VALUE_1 = 0x0000;
	public final static int CRC_INIT_VALUE_2 = 0xA671;
	public final static int CRC_INIT_VALUE_3 = 0xFFFF;

	public static byte[] CRC(byte... data) {
		return CRC(CRC_INIT_VALUE_DEFAULT, data);
	}

	public static byte[] CRC(int init, byte... data) {
		byte[] crc = new byte[2];
		for (int i = 0; i < data.length; i++) {
			int b = data[i] & 0xFF;
			b ^= (init & 0xFF);
			b ^= (b << 4) & 0xFF;
			init = ((init >> 8) ^ (b << 8) ^ (b << 3) ^ (b >> 4));
		}
		crc[0] = (byte) (init & 0xFF);
		crc[1] = (byte) ((init >> 8) & 0xFF);
		return crc;
	}

}
