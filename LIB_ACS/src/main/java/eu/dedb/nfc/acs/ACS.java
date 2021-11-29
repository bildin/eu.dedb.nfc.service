package eu.dedb.nfc.acs;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.acs.smartcard.Reader;
import com.acs.smartcard.Reader.OnStateChangeListener;
import com.acs.smartcard.ReaderException;

import eu.dedb.nfc.lib.NfcUtils;
import eu.dedb.nfc.lib.PCD;
import eu.dedb.nfc.lib.PICC;
import eu.dedb.nfc.lib.PICC_A;
import eu.dedb.nfc.lib.TransceiveResponse;

public class ACS implements PCD {

    private static final String TAG = ACS.class.getSimpleName();
    private static final int EMERGENCY_TIMEOUT = 5000;

    private String version_name = "ACR UNKNOWN";

	private boolean targetInited = false;

	private UsbManager mUsbManager;
	private Reader mReader;
	protected int piccState;
	private int mFlags;
	private int mSlot = 0;
	private static final byte[] PSEUDO_APDU = new byte[] { (byte) 0xFF , 0x00, 0x00, 0x00 };

	public static boolean isSupported(UsbDevice mUsbDevice) {
		try {
			return new Reader((UsbManager) UsbManager.class.getConstructors()[0].newInstance(null,null)).isSupported(mUsbDevice);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public static ACS get(Context ctx, UsbDevice mUsbDevice) {

		Log.v(TAG, "Trying to connect...");

		if (mUsbDevice == null)
			return null;
		try {
			UsbManager mUsbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
			Reader mReader = new Reader(mUsbManager);
			if (mReader.isSupported(mUsbDevice)) {
				mReader.open(mUsbDevice); // throws IllegalArgumentException;
				Log.v(TAG, "Connection succeed!");
				return new ACS(mReader);
			} else {
				Log.v(TAG, "Connection not supported!");
			}
			// mReader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		Log.v(TAG, "Connection failed!");
		return null;
	}

	@Override
	public void close() {
		mReader.close();
	}

	public ACS recover() {
		return this;
	}

	public static final String[] stateStrings = { "Unknown", "Absent",
			"Present", "Swallowed", "Powered", "Negotiable", "Specific" };

	public interface ACSStateListener extends OnStateChangeListener {
		// @Override
		// public void onStateChange(int arg0, int arg1, int arg2) {
		// // TODO Auto-generated method stub
		//
		// }};
	}

	public void setStateListener(ACSStateListener stateListener) {
		mReader.setOnStateChangeListener(stateListener);
	}

	public ACS(Reader reader) {
		// TODO constructor
		mReader = reader;

		try {
			// get ACR122 FW version
			byte[] firmware = escape(new byte[] { (byte) 0xFF, 0x00, 0x48,
					(byte) 0x00, 0x00 });
			version_name = new String(firmware);
			Log.v(TAG, version_name);
		} catch (ReaderException e) {
			e.printStackTrace();
		}

	}

	public ACS(UsbDevice device, Context ctx) {
		// TODO constructor
		mUsbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
		mReader = new Reader(mUsbManager);
		if (mReader.isSupported(device)) {
			mReader.open(device); // throws IllegalArgumentException;

			try {
				// get ACR122 FW version
				byte[] firmware = escape(new byte[] { (byte) 0xFF, 0x00, 0x48,
						(byte) 0x00, 0x00 });
				version_name = new String(firmware);
				Log.v(TAG, version_name);
			} catch (ReaderException e) {
				e.printStackTrace();
			}
		}
	}

	public byte[] transmit(byte... bufferTx) throws ReaderException {

		byte[] bufferRx = new byte[300];
		int responseLength = mReader.transmit(mSlot, bufferTx, bufferTx.length,
				bufferRx, bufferRx.length);
		byte[] received = new byte[responseLength];
		System.arraycopy(bufferRx, 0, received, 0, responseLength);
		return received;
	}

	public byte[] escape(byte... bufferTx) throws ReaderException {
		StringBuilder sb = new StringBuilder();
		for (byte b : bufferTx)
			sb.append(String.format("%02X", b & 0xFF));
		Log.v("ACS", "CtrlTX: " + sb.toString());

		byte[] bufferRx = new byte[300];
		int responseLength = mReader.control(mSlot, 3500, bufferTx,
				bufferTx.length, bufferRx, bufferRx.length);
		byte[] received = new byte[responseLength];
		System.arraycopy(bufferRx, 0, received, 0, responseLength);

		sb = new StringBuilder();
		for (byte b : received)
			sb.append(String.format("%02X", b & 0xFF));
		Log.v("ACS", "CtrlRX: " + sb.toString());
		return received;
	}

	@Override
	public void init() throws IOException {
		// try {
		// Disable AutoPolling
		// escape(new byte[] { (byte) 0xFF, 0x00, 0x51, (byte) 0x00, 0x00
		// });
		// Disable AutoRATS
		// escape(new byte[] { (byte) 0xFF, 0x00, 0x51, (byte) 0xBF, 0x00
		// });
		// } catch (ReaderException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// throw new IOException(e.getMessage());
		// }
	}

	public byte readReg(int addr) throws IOException {
		return readReg((short) addr);
	}

	public byte readReg(short addr) throws IOException {
		return communicate((byte) 0x06, new byte[] {
				(byte) ((addr >> 8) & 0xFF), (byte) (addr & 0xFF) })[0];

	}

	public byte[] readFIFO() throws IOException {
		byte[] bb = new byte[0];
		while (readReg(0x633A) > 0) {
			byte b = readReg(0x6339);
			byte[] tmp = new byte[bb.length + 1];
			System.arraycopy(bb, 0, tmp, 0, bb.length);
			tmp[bb.length] = b;
			bb = tmp;
		}
		return bb;
	}

	public void writeReg(int addr, int value) throws IOException {
		writeReg((short) addr, (byte) value);
	}

	public void writeReg(short addr, byte value) throws IOException {
		communicate((byte) 0x08, new byte[] { (byte) ((addr >> 8) & 0xFF),
				(byte) (addr & 0xFF), value });
	}

	public void writeReg(short addr, byte[] value) throws IOException {
		for (byte b : value)
			communicate((byte) 0x08, new byte[] { (byte) ((addr >> 8) & 0xFF),
					(byte) (addr & 0xFF), b });
	}

	public byte[] communicate(byte command, byte... data) throws IOException {
		int lengthTx = PSEUDO_APDU.length + 3 + data.length;
		byte[] bufferTx = new byte[lengthTx];
		System.arraycopy(PSEUDO_APDU, 0, bufferTx, 0, PSEUDO_APDU.length);
		bufferTx[PSEUDO_APDU.length] = (byte) (lengthTx - PSEUDO_APDU.length - 1);
		bufferTx[PSEUDO_APDU.length + 1] = (byte) 0xD4;
		bufferTx[PSEUDO_APDU.length + 2] = command;
		System.arraycopy(data, 0, bufferTx, PSEUDO_APDU.length + 3, data.length);

		try {
			byte[] received = escape(bufferTx);
			if (received.length >= 3) {
				if (received[0] == (byte) 0xD5
						&& received[received.length - 2] == (byte) 0x90
						&& received[received.length - 1] == (byte) 0x00) {
					byte[] response = new byte[received.length - 4];
					System.arraycopy(received, 2, response, 0, response.length);
					return response;
				} else {
					// TODO incorrect response
					Log.v("ACS", "incorrect response code");
				}
			} else {
				// TODO incorrect response
				Log.v("ACS", "incorrect response");
			}
		} catch (ReaderException e) {
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
		throw new IOException();
	}

	public String toString() {
		return mReader.getReaderName() + " (" + version_name + ")";
	}

	/*
	@Override
	public TransceiveResponse transceive_bytes(byte[] sendBuf, int sendLen,
			int timeout) throws IOException {
		return transceive(sendBuf, 0, timeout, TRANSCEIVE_BYTES);
	}

	@Override
	public TransceiveResponse transceive_bits(byte[] sendBuf, int sendLen,
			int timeout) throws IOException {
		return transceive(sendBuf, sendLen, timeout, TRANSCEIVE_BITS);
	}
	//*/

	public TransceiveResponse authenticateMR(byte keyType, byte blockNumber,
			byte[] key, byte[] uid) throws IOException {
		writeReg(0x6331, 0x0);// stop
		writeReg(0x6334, (byte) 0x7F);// reset IRQ
		writeReg(0x633A, (byte) 0x80);// flush FIFO
		writeReg(0x6339, keyType);// write FIFO
		writeReg(0x6339, blockNumber);// write FIFO
		writeReg((short) 0x6339, key);// write FIFO
		writeReg((short) 0x6339, uid);// write FIFO
		writeReg(0x6331, 0xE);// auth

		long start = System.currentTimeMillis();
		long timer;
		// TODO timeout constants
		int timeout = 10;
		Log.v(TAG, "Processing...");
		while (true) {
			timer = System.currentTimeMillis() - start;
			byte irq = readReg(0x6334);
			if ((irq & 0x30) != 0) {
				break;
			}
			if (timer > timeout) {
				Log.v(TAG, "Transceive timeout " + timeout);
				return TransceiveResponse.getIOErrorResponse();
			}
		}
		Log.v(TAG, "Done...");
		byte error = readReg(0x6336);
		if ((error & 0x13) != 0) { // 0x1B with collision
			Log.v(TAG, "Transceive error " + error);
			return TransceiveResponse.getIOErrorResponse();
		}
		byte validBits = readReg(0x633C);
		byte[] recvBuf = readFIFO();
		return new TransceiveResponse(0, recvBuf, validBits & 0x07);
	}

	public TransceiveResponse authenticate(byte keyType, byte blockNumber,
			byte[] key, byte[] uid) throws IOException {
        Log.v(TAG, "AUTH > block " + toStr(blockNumber) + "key " + (keyType == 0x61 ? "B" : "A") + ": " + toStr(key));

        byte[] response;
		byte[] tmp = new byte[13];
		tmp[0] = 1;
		tmp[1] = keyType;
		tmp[2] = blockNumber;
		System.arraycopy(key, 0, tmp, 3, 6);
		System.arraycopy(uid, 0, tmp, 9, 4);
		response = communicate((byte) 0x40, tmp);
		if (response.length > 0) {
			if (response[0] == 0) {
				// No Error
				return TransceiveResponse.getSuccessResponse();
			} else if (response[0] == 1) {
                // Timeout
                Log.v(TAG, "AUTH < FAILED! (Timeout)");
				return TransceiveResponse.getIOErrorResponse();
			} else if (response[0] == 0x14) {
                // MIFARE AUTH ERROR
                Log.v(TAG, "AUTH < FAILED! (Auth error)");
				// Force PICC halt state if auth failed
				communicate((byte) 0x42, new byte[] { 0x52, 0x00, 0x57,
						(byte) 0xCD });
				return TransceiveResponse.getIOErrorResponse();
			}
		}
		// TODO
        Log.v(TAG, "AUTH < FAILED! (Error)");
		return TransceiveResponse.getIOErrorResponse();
	}

	public TransceiveResponse transceiveMR(byte[] sendBuf, int txLastBits,
			int timeout, int flags) throws IOException {
		Log.v(TAG, "> " + toStr(sendBuf) + "(" + (txLastBits & 0x07) + ")");

		if (timeout == 0)
			timeout = EMERGENCY_TIMEOUT;

		writeReg(0x6331, 0x0);
		writeReg(0x6334, (byte) 0x7F);
		writeReg(0x6302, (flags & CRC_TX) != 0 ? (byte) 0x80 : (byte) 0x00);
		writeReg(0x6303, (flags & CRC_RX) != 0 ? (byte) 0x80 : (byte) 0x00);
		writeReg(0x633A, (byte) 0x80);
		writeReg((short) 0x6339, sendBuf);
		writeReg(0x6331, 0xC);
		writeReg(0x633D, (byte) (0x80 | txLastBits & 0x07));

		long start = System.currentTimeMillis();
		Log.v(TAG, "Processing...");
		while (true) {
			long timer = System.currentTimeMillis() - start;
			byte irq = readReg(0x6334);
			if ((irq & 0x30) != 0) {
				break;
			}
			if (timer > timeout) {
				Log.v(TAG, "Transceive timeout " + timeout);
				return TransceiveResponse.getIOErrorResponse();
			}
		}
		Log.v(TAG, "Done...");

		byte error = readReg(0x6336);
		if ((error & 0x13) != 0) { // 0x1B with collision
			Log.v(TAG, "Transceive error " + error);
			return new TransceiveResponse(-1, null, 0);
		}
		byte validBits = readReg(0x633C);
		byte[] recvBuf = readFIFO();
		Log.v(TAG, "< " + toStr(recvBuf) + "(" + (validBits & 0x07) + ")");
		return new TransceiveResponse(0, recvBuf, validBits & 0x07);
	}

	public TransceiveResponse transceive(byte[] sendBuf, int txLastBits, int timeout, int flags) throws IOException {
        Log.v(TAG, "DATA > " + toStr(sendBuf) + (((txLastBits & 0x07) != 0) ? ("(" + (txLastBits & 0x07) + " bit)") : ""));

        // Disable Tx CRC
		// writeReg(0x6302, (flags & CRC_TX) != 0 ? (byte) 0x80 : (byte) 0x00);
		// Disable Rx CRC
		// writeReg(0x6303, (flags & CRC_RX) != 0 ? (byte) 0x80 : (byte) 0x00);
		// Set bit data length
		// writeReg(0x633D, (byte) (txLastBits & 0x07));
		// set registers at once
		communicate((byte) 0x08, new byte[] {

		0x63, 0x02, (flags & CRC_TX) != 0 ? (byte) 0x80 : (byte) 0x00,

		0x63, 0x03, (flags & CRC_RX) != 0 ? (byte) 0x80 : (byte) 0x00,

		0x63, 0x0D, (flags & PARITY_AS_DATA) != 0 ? (byte) 0x10 : (byte) 0x00,

		0x63, 0x3D, (byte) (txLastBits & 0x7F)

		});

		// InCommunicateThru
		byte[] response = communicate((byte) 0x42, sendBuf);

		if (response.length > 0) {
			if (response[0] == 0) {
				// No Error
				// Last bits received
				int validBits = readReg(0x633C) & 0x07;
				int recvLen = response.length - 1; //(validBits == 0 && (flags & CRC_RX) != 0) ? response.length - 3 : response.length - 1;
				byte[] recvBuf = new byte[recvLen];

                System.arraycopy(response, 1, recvBuf, 0, recvBuf.length);

                /*
				if (validBits == 0 && (flags & CRC_RX) != 0) {
					// TODO isSupported CRC
					recvBuf = new byte[response.length - 3];
					System.arraycopy(response, 1, recvBuf, 0, recvBuf.length);
				} else {
					recvBuf = new byte[response.length - 1];
					System.arraycopy(response, 1, recvBuf, 0, recvBuf.length);
				}
				// */

                Log.v(TAG, "DATA < " + toStr(recvBuf) + ((validBits != 0) ? ("(" + validBits + " bit)") : ""));
                return new TransceiveResponse(0, recvBuf, validBits);
			} else if (response[0] == 1) {
				// Timeout
                Log.v(TAG, "DATA < FAILED! (Timeout)");
				return TransceiveResponse.getIOErrorResponse();
			}
			// else if (response[0] == 2) {
			// // CRC (ACK or NACK)
			// int validBits = readReg(0x633C);
			// Log.v(TAG, "< " + "CRC error" + "(" + (validBits & 0x07) + ")");
			// return new TransceiveResponse(0, new byte[0], validBits & 0x07);
			// }
		}
		// TODO
        Log.v(TAG, "DATA < FAILED! (Error)");
		return TransceiveResponse.getIOErrorResponse();
	}

	@Override
	public PICC poll(int flags) throws IOException {
		mFlags = flags;
		PICC polled = null;

		if ((flags & PCD.PCSC_MODE) != 0) {
			// TODO ACS PC/SC polling
			try {
				try {
					// Disable AutoRATS (for ACR122)
					// Auto PICC Polling | Polling Interval 250 ms | ISO14443A
					byte config = (byte) (
							1 << 0 | // ISO14443A
							0 << 1 | // ISO14443B
							0 << 2 | // Topaz
							0 << 3 | // FeliCa 212K
							0 << 4 | // FeliCa 424K
							1 << 5 | // Pooling Interval (0: 500ms; 1: 250ms)
							0 << 6 | // Auto ATS Generation
							1 << 7 // Auto PICC Polling
					);
					escape(new byte[] { (byte) 0xFF, 0x00, 0x51, config, 0x00 });
				} catch (Exception e) {
					// Disable AutoRATS (for ACR1251, ACR1281)
					// Auto PICC Polling | Polling Interval 250 ms | Turn off
					// Antenna Field if no PICC is found
					byte config = (byte) (
							1 << 0 | // Auto PICC Polling
							1 << 1 | // Turn off Antenna Field if no PICC is
										// found
							0 << 2 | // Turn off Antenna Field if the PICC is
										// inactive
							1 << 3 | // ACR1251: Activate the PICC when
										// detected; ACR1281: RFU
							0 << 4 | // Pooling Interval
							0 << 5 | // (00: 250ms; 01: 200ms; 10: 1000ms; 11:
										// 2500ms)
							0 << 6 | // RFU
							0 << 7 // Enforce ISO14443A-4
					);
					escape(new byte[] { (byte) 0xE0, 0x00, 0x00, 0x23, 0x01,
							config });
				}

				int slots = mReader.getNumSlots();

				byte[] atr = null;
				for (int slot = 0; slot < slots; slot++) {
					try {
						atr = mReader.power(slot, Reader.CARD_WARM_RESET);
						mSlot = slot;
						mReader.setProtocol(mSlot, Reader.PROTOCOL_TX);
						break;
					} catch (ReaderException e) {
					}
				}
				if (atr == null)
					return null;

				StringBuilder sb = new StringBuilder();
				for (byte b : atr)
					sb.append(String.format("%02X", b & 0xFF));
				Log.v("ACS", "ATR: " + sb.toString());
				// TODO ATR parsing
				if (atr != null && atr.length == 20 && atr[0] == 0x3B
						&& atr[7] == (byte) 0xA0 && atr[12] == 0x03) {

					// 3B 8B 80 01:00 31 C0 64 08 04 61 00 00 90 00:62 - JCOP
					// DEP

					// 10 78 80 70 02:00 31 C0 64 08 04 61 00 00 90 00:90 00 -
					// COP DEP(4)ATS

					// 3B 8F 80 01 80 4F 0C A0 00 00 03 06 03 FF 28 00 00 00 00
					// BC - JCOP CL

					// 3B 8F 80 01 80 4F 0C A0 00 00 03 06 03 00 03 00 00 00 00
					// 68 - UL

					// 3B 8F 80 01 80 4F 0C A0 00 00 03 06 03 00 01 00 00 00 00
					// 6A - CL

					// 3B 8F 80 01 80 4F 0C A0 00 00 03 06 03 FF 88 00 00 00 00
					// 1C - CL

					// 3B 8F 80 01 80 4F 0C A0 00 00 03 06 03 FF 20 00 00 00 00
					// B4 - DEP(3)

					// 3B 8E 80 01:80 31 80 66 B1 84 0C 01 6E 01 83 00 90 00:1C
					// - DEP(4)

					// 13 78 80 72 02:80 31 80 66 B1 84 0C 01 6E 01 83 00 90
					// 00:90 00 - DEP(4)ATS

					byte[] uid_resp = transmit(new byte[] { (byte) 0xFF,
							(byte) 0xCA, 0x00, 0x00, 0x00 });
					byte[] UID = new byte[uid_resp.length - 2];
					System.arraycopy(uid_resp, 0, UID, 0, UID.length);
					byte[] ATQA = new byte[2];
					switch (UID.length) {
					case 4:
						ATQA[0] = 0x04;
						break;
					case 7:
						ATQA[0] = 0x44;
						break;
					case 10:
						ATQA[0] = (byte) 0x84;
						break;
					}
					short SAK = -1;
					if (atr[13] == (byte) 0xFF) {
						SAK = (short) (atr[14] & 0xFF);
					} else if (atr[13] == (byte) 0x00) {
						switch (atr[14]) {
						case 0x01:
							SAK = 0x08;
							break;
						case 0x02:
							SAK = 0x18;
							break;
						case 0x03:
							SAK = 0x00;
							break;
						case 0x26:
							SAK = 0x09;
							break;
						}
					}
					polled = new PICC_A(this, ATQA, UID, SAK, flags);
				}
			} catch (ReaderException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			// Advanced mode (manual polling) ACR122 only
			try {
				// Disable AutoPolling
				escape(new byte[] { (byte) 0xFF, 0x00, 0x51, (byte) 0x00, 0x00 });
				// PICC polling
				// LED: YELLOW
				escape(new byte[] { (byte) 0xFF, 0x00, 0x40, 0x0F, 0x04, 0x00,
						0x00, 0x00, 0x00 });
			} catch (ReaderException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// Turn antenna On
			communicate((byte) 0x32, new byte[] { 0x01, 0x01 });

			if (targetInited
					&& (flags & (PCD.ACTIVATE_CHINESE | PCD.IGNORE_BCC_ERROR)) != 0) {
				// Manual polling if target is available and preferences are set
				polled = PICC_A.poll(this, flags);
			} else {
				targetInited = false;
				// InAutoPoll PICC_A once
				byte[] atr = communicate((byte) 0x60, new byte[] { 0x01, 0x01,
						0x10 });
				// NbTg ||: TYPEn | Ln | Nb | ATQAl | ATQAh | SAK | Lid | UID0
				// ...
				// :||
				if (atr.length > 2) {
					int count = atr[0] & 0xFF; // 0+
					int type = atr[1] & 0xFF; // 0x10
					int length = atr[2] & 0xFF; // 7,10,13
					if (count != 0 && type == 0x10 && atr.length >= length + 3) {
						int target = atr[3]; // should be 1
						byte[] atqa = new byte[] { atr[5], atr[4] };
						short sak = (short) (atr[6] & 0xFF);
						int uidLength = atr[7] & 0xFF;
						byte[] uid = new byte[uidLength];
						System.arraycopy(atr, 8, uid, 0, uidLength);
						polled = new PICC_A(this, atqa, uid, sak, flags);
						targetInited = true;
					}
				}
			}
			if (polled != null) {
				try {
					// PICC is present
					// LED: GREEN
					escape(new byte[] { (byte) 0xFF, 0x00, 0x40, 0x0E, 0x04,
							0x00, 0x00, 0x00, 0x00 });
				} catch (ReaderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					if (targetInited) {
						// PCD is ready for manual polling
						// LED: RED
						escape(new byte[] { (byte) 0xFF, 0x00, 0x40, 0x0D,
								0x04, 0x00, 0x00, 0x00, 0x00 });
					} else {
						// PCD is ready for auto polling
						// LED: NONE
						escape(new byte[] { (byte) 0xFF, 0x00, 0x40, 0x0C,
								0x04, 0x00, 0x00, 0x00, 0x00 });
					}
				} catch (ReaderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// Turn antenna Off
				communicate((byte) 0x32, new byte[] { 0x01, 0x00 });
			}
		}
		return polled;
	}

	private TransceiveResponse parseAPDUResponse(byte[] resp) {
		if (resp.length >= 2) {
			if (resp[resp.length - 2] == (byte) 0x90
					&& resp[resp.length - 1] == (byte) 0x00) {
				return new TransceiveResponse(0, Arrays.copyOf(resp,
						resp.length - 2), 0);
			} else {
				return TransceiveResponse.getIOErrorResponse();
			}
		} else {
			return TransceiveResponse.getTagLostResponse();
		}
	}

	@Override
	public TransceiveResponse transceive(byte[] sendBuf, boolean raw)
			throws IOException {
        Log.v(TAG, "TRANSCEIVE " + toStr(sendBuf) + "(" + (raw ? "RAW" : "PROTOCOL") + ")");
        try {
			if (!raw) {
				// parse MIFARE proprietary protocols
				switch (sendBuf[0]) {
				case 0x00:
					Log.v(TAG, "<Mifare raw transaction>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					// TODO pseudo tunnel protocol implementation
					break;
				case (byte) 0x01:
					Log.v(TAG, "<Mifare proprietary ReadN>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					break;
				case (byte) 0x02:
					Log.v(TAG, "<Mifare proprietary WriteN>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					break;
				case (byte) 0x03:
					Log.v(TAG, "<Mifare proprietary SectorSel>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					break;
				case (byte) 0x04:
					Log.v(TAG, "<Mifare proprietary Auth>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					break;
				case (byte) 0x05:
					Log.v(TAG, "<Mifare proprietary ProxCheck>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					break;
				case 0x30:
					if (sendBuf.length == 2) {
						Log.v(TAG, "<Mifare Read>");
						if ((mFlags & PCD.PCSC_MODE) != 0) {
							byte[] resp = transmit(new byte[] { (byte) 0xFF,
									(byte) 0xB0, 0x00, sendBuf[1], 0x10 });
							return parseAPDUResponse(resp);
						} else {
							return transceive(sendBuf, true);
						}
					}
					break;
				case (byte) 0x38:
					Log.v(TAG, "<Mifare Read sector (Macro)>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					break;
				case 0x60:
				case 0x61:
					if (sendBuf.length == 12) {
						Log.v(TAG, "<Mifare Auth>");
						byte keyType = sendBuf[0];
						byte blockNumber = sendBuf[1];
						byte[] uid = new byte[] { sendBuf[2], sendBuf[3],
								sendBuf[4], sendBuf[5] };
						byte[] key = new byte[] { sendBuf[6], sendBuf[7],
								sendBuf[8], sendBuf[9], sendBuf[10],
								sendBuf[11] };
						byte keyNumber = 0x00;
						if ((mFlags & PCD.PCSC_MODE) != 0) {
							// Load key
							byte[] loaded = transmit(new byte[] { (byte) 0xFF,
									(byte) 0x82, 0x00, keyNumber, 0x06, key[0],
									key[1], key[2], key[3], key[4], key[5] });
							if (loaded.length == 2 && loaded[0] != (byte) 0x90) {
								transmit(new byte[] { (byte) 0xFF, (byte) 0x82,
										0x20, keyNumber, 0x06, key[0], key[1],
										key[2], key[3], key[4], key[5] });
							}
							// AUTH
							byte[] resp = transmit(new byte[] { (byte) 0xFF,
									(byte) 0x88, 0x00, blockNumber, keyType,
									keyNumber });
							return parseAPDUResponse(resp);
						} else {
							return authenticate(keyType, blockNumber, key, uid);
						}
					}
					break;
				case (byte) 0xA0:
					if (sendBuf.length == 18) {
						Log.v(TAG, "<Mifare Write 16 bytes>");
						if ((mFlags & PCD.PCSC_MODE) != 0) {
							byte[] resp = transmit(new byte[] { (byte) 0xFF,
									(byte) 0xD6, 0x00, sendBuf[1], 0x10,
									sendBuf[2], sendBuf[3], sendBuf[4],
									sendBuf[5], sendBuf[6], sendBuf[7],
									sendBuf[8], sendBuf[9], sendBuf[10],
									sendBuf[11], sendBuf[12], sendBuf[13],
									sendBuf[14], sendBuf[15], sendBuf[16],
									sendBuf[17] });
							// TODO resp parse
							// return new TransceiveResponse(0, resp, 0);
							return parseAPDUResponse(resp);
						} else {
							byte[] part1 = new byte[2];
							System.arraycopy(sendBuf, 0, part1, 0, 2);
							TransceiveResponse part1r = transceive(part1, true);
							if (!part1r.isACK()) {
								return TransceiveResponse.getIOErrorResponse();
							}
							byte[] part2 = new byte[16];
							System.arraycopy(sendBuf, 2, part2, 0, 16);
							TransceiveResponse part2r = transceive(part2, true);
							if (!part2r.isACK()) {
								return TransceiveResponse.getIOErrorResponse();
							}
							return part2r;
						}
					}
					break;
				case (byte) 0xA2:
					if (sendBuf.length == 6) {
						Log.v(TAG, "<Mifare Write 4 bytes>");
						if ((mFlags & PCD.PCSC_MODE) != 0) {
							byte[] resp = transmit(new byte[] { (byte) 0xFF,
									(byte) 0xD6, 0x00, sendBuf[1], 0x04,
									sendBuf[2], sendBuf[3], sendBuf[4],
									sendBuf[5] });
							// TODO resp parse
							// return new TransceiveResponse(0, resp, 0);
							return parseAPDUResponse(resp);
						} else {
							return transceive(sendBuf, true);
						}
					}
					break;
				case (byte) 0xA8:
					Log.v(TAG, "<Mifare Write sector (Macro)>");
					Log.v(TAG, "Unimplemented proprietary transaction");
					break;
				case (byte) 0xB0:
					if (sendBuf.length == 2) {
						Log.v(TAG, "<Mifare Transfer>");
						Log.v(TAG, "Unimplemented proprietary transaction");
						// TODO
					}
					break;
				case (byte) 0xC0:
					if (sendBuf.length == 6) {
						Log.v(TAG, "<Mifare Decrement>");
						Log.v(TAG, "Unimplemented proprietary transaction");
						// TODO
					}
					break;
				case (byte) 0xC1:
					if (sendBuf.length == 6) {
						Log.v(TAG, "<Mifare Increment>");
						Log.v(TAG, "Unimplemented proprietary transaction");
						// TODO
					}
					break;
				case (byte) 0xC2:
					if (sendBuf.length == 2) {
						Log.v(TAG, "<Mifare Restore>");
						Log.v(TAG, "Unimplemented proprietary transaction");
						// TODO
					}
					break;
				case (byte) 0xFF:
					break;
				default:
					break;
				}
			} else {
				if ((mFlags & PCD.PCSC_MODE) != 0) {
					byte[] resp = transmit(sendBuf);
					return new TransceiveResponse(0, resp, 0);
				} else {
					return transceive(sendBuf, 0, 1000, CRC_TX | CRC_RX);
				}
			}
			return null;
		} catch (ReaderException e) {
			throw new IOException(e.getMessage());
		}

	}

	public static String toStr(byte... data) {
		StringBuilder sb = new StringBuilder();
		for (byte b : data) {
			sb.append(String.format("%02X ", b));
		}
		return sb.toString();
	}
}
