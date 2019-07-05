package eu.dedb.nfc.chip.pn53x;

public class Frame {
	public final static byte PREAMBLE = (byte) 0x00;
	public final static byte STARTCODE0 = (byte) 0x00;
	public final static byte STARTCODE1 = (byte) 0xFF;
	public final static byte POSTAMBLE = (byte) 0x00;

	public final static byte EXTENDED0 = (byte) 0xFF;
	public final static byte EXTENDED1 = (byte) 0xFF;
	public final static byte ACK0 = (byte) 0x00;
	public final static byte ACK1 = (byte) 0xFF;
	public final static byte NACK0 = (byte) 0xFF;
	public final static byte NACK1 = (byte) 0x00;
	public final static byte ERROR0 = (byte) 0x01;
	public final static byte ERROR1 = (byte) 0xFF;
	public final static byte ERROR2 = (byte) 0x7F;

	public final static byte TFI_HOST2PN53X = (byte) 0xD4;
	public final static byte TFI_PN53X2HOST = (byte) 0xD5;

	public final static byte[] ACKFrame = new byte[] { PREAMBLE, STARTCODE0,
			STARTCODE1, ACK0, ACK1, POSTAMBLE };

	public final static byte[] NACKFrame = new byte[] { PREAMBLE, STARTCODE0,
			STARTCODE1, NACK0, NACK1, POSTAMBLE };

	public final static byte[] ERRORFrame = new byte[] { PREAMBLE, STARTCODE0,
			STARTCODE1, ERROR0, ERROR1, ERROR2, -ERROR2, POSTAMBLE };

	public static byte checksum(byte... data) {
		byte sum = 0x00;
		for (byte b : data) {
			sum += b;
		}
		return (byte) -sum;
	}

	public static byte[] wrap(byte... data) {
		boolean extendedFrame = data.length > 255;
		int dataOffset = extendedFrame ? 8 : 5;
		int frameLength = dataOffset + data.length + 2;
		byte[] frame = new byte[frameLength];
		frame[0] = PREAMBLE;
		frame[1] = STARTCODE0;
		frame[2] = STARTCODE1;
		if (extendedFrame) {
			frame[3] = EXTENDED0;
			frame[4] = EXTENDED1;
			frame[5] = (byte) ((data.length >> 8) & 0xFF);
			frame[6] = (byte) (data.length & 0xFF);
			frame[7] = (byte) -(frame[5] + frame[6]);
		} else {
			frame[3] = (byte) (data.length & 0xFF);
			frame[4] = (byte) -frame[3];
		}
		System.arraycopy(data, 0, frame, dataOffset, data.length);
		frame[frame.length - 2] = checksum(data);
		frame[frame.length - 1] = POSTAMBLE;
		return frame;
	}
}
