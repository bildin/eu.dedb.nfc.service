package eu.dedb.nfc.chip.mfrc522;

import java.io.IOException;

import android.util.Log;
import eu.dedb.nfc.lib.PCD;
import eu.dedb.nfc.lib.PICC;
import eu.dedb.nfc.lib.PICC_A;
import eu.dedb.nfc.lib.TransceiveResponse;

public abstract class MFRC522 implements PCD {

	private static final String TAG = MFRC522.class.getSimpleName();
	protected static final int EMERGENCY_TIMEOUT = 5000;

	private String version_name = "MFRC522 UNKNOWN";

	public static final int TRANSCEIVE_BYTES = 0;
	public static final int TRANSCEIVE_BITS = 1;
	public static final int CRC_TX = 2;
	public static final int CRC_RX = 4;

	abstract public TransferBuilder transfer(TransferBuilder tb) throws IOException;

	public byte[] readFIFO() throws IOException {
		return readFIFO(64);
	}

	public byte[] readFIFO(int len) throws IOException {
		TransferBuilder bulk = TransferBuilder.get();
		bulk.readReg(REG.FIFOLevelReg);
		for(int i = 0; i < len; i++) {
			bulk.readReg(REG.FIFODataReg);
		}
		bulk.readReg(REG.FIFOLevelReg);
		byte[] buf = transfer(bulk).getInput();
		int length = buf[0];
		int remain = buf[buf.length - 1];
		byte[] bb = new byte[length];
		System.arraycopy(buf, 1, bb, 0, length);
		return bb;
	}

	public void writeFIFO(byte... buffer) throws IOException {
		transfer(TransferBuilder.get().writeReg(REG.FIFODataReg, buffer));
	}

	public void flushFIFO() throws IOException {
		transfer(TransferBuilder.get().writeReg(REG.FIFOLevelReg, (byte) 0x80));
	}

	public String toString() {
		return version_name;
	}

	@Override
	public void init() throws IOException {
		reset();
		transfer(
				TransferBuilder.get().
						writeReg(REG.TxASKReg, (byte) 0x40).
						writeReg(REG.ModeReg, (byte) 0x3D)
		);
	}

	public void reset() throws IOException {
		transfer(TransferBuilder.get().
				writeReg(REG.CommandReg, CMD.SoftReset)
		);
		while ((transfer(TransferBuilder.get().
				readReg(REG.CommandReg)
		).getInput()[0] & 0x10) != 0) {

		}
	}

	public void antenna(boolean state) throws IOException {
		// byte value = readReg(REG.TxControlReg);
		byte config = (byte) 0x80;
		if (state) {
			// if ((value & 0x03) != 0x03) {
			transfer(TransferBuilder.get().
					writeReg(REG.TxControlReg, (byte) (config | 0x03))
			);
			// }
		} else {
			// if ((value & 0x03) != 0x00) {
			transfer(TransferBuilder.get().
					writeReg(REG.TxControlReg, (byte) (config & ~0x03))
			);
			// }
		}
	}

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

	public TransceiveResponse transceive(byte[] sendBuf, boolean raw) throws IOException {
		Log.v(TAG, "TRANSCEIVE " + toStr(sendBuf) + "(" + (raw ? "RAW" : "PROTOCOL") + ")");
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
					return transceive(sendBuf, true);
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
							sendBuf[8], sendBuf[9], sendBuf[10], sendBuf[11] };
					return authenticate(keyType, blockNumber, key, uid);
				}
				break;
			case (byte) 0xA0:
				if (sendBuf.length == 18) {
					Log.v(TAG, "<Mifare Write 16 bytes>");
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
				break;
			case (byte) 0xA2:
				if (sendBuf.length == 6) {
					Log.v(TAG, "<Mifare Write 4 bytes>");
					return transceive(sendBuf, true);
				}
				break;
			case (byte) 0xA8:
				Log.v(TAG, "<Mifare Write sector (Macro)>");
				Log.v(TAG, "Unimplemented proprietary transaction");
				break;
			case (byte) 0xB0:
				if (sendBuf.length == 2) {
					Log.v(TAG, "<Mifare Transfer>");
					return transceive(sendBuf, true);
				}
				break;
			case (byte) 0xC0:
			case (byte) 0xC1:
				if (sendBuf.length == 6) {
					if(sendBuf[0] == 0xC0)
						Log.v(TAG, "<Mifare Decrement>");
					if(sendBuf[0] == 0xC1)
						Log.v(TAG, "<Mifare Increment>");

					byte[] part1 = new byte[2];
					System.arraycopy(sendBuf, 0, part1, 0, 2);
					TransceiveResponse part1r = transceive(part1, true);
					if (!part1r.isACK()) {
						return TransceiveResponse.getIOErrorResponse();
					}

					byte[] part2 = new byte[4];
					System.arraycopy(sendBuf, 2, part2, 0, 4);
					return transceive(part2, true);
				}
				break;
			case (byte) 0xC2:
				if (sendBuf.length == 2) {
					Log.v(TAG, "<Mifare Restore>");
					return transceive(sendBuf, true);
				}
				break;
			case (byte) 0xFF:
				break;
			default:
				break;
			}
		} else {
			// RAW TRANSCEIVE
			return transceive(sendBuf, 0, 1000, CRC_TX | CRC_RX);
		}
		Log.v(TAG, "TRANSCEIVE IGNORED!");
		return null;
	}

	public TransceiveResponse authenticate(byte keyType, byte blockNumber,
			byte[] key, byte[] uid) throws IOException {
		Log.v(TAG, "AUTH > block " + toStr(blockNumber) + "key " + (keyType == 0x61 ? "B" : "A") + ": " + toStr(key));

		byte irq, error, status;
		byte[] response;
		response = transfer(TransferBuilder.get().
                writeReg(REG.CommandReg, CMD.Idle).// stop
                writeReg(REG.ComIrqReg, (byte) 0x7F).// reset IRQ
                writeReg(REG.FIFOLevelReg, (byte) 0x80).// flush FIFO
                writeReg(REG.FIFODataReg, keyType).// write FIFO
                writeReg(REG.FIFODataReg, blockNumber).// write FIFO
                writeReg(REG.FIFODataReg, key).// write FIFO
                writeReg(REG.FIFODataReg, uid).// write FIFO
                writeReg(REG.CommandReg, CMD.MFAuthent).// auth
                
                readReg(REG.ComIrqReg).
                readReg(REG.ErrorReg).
				readReg(REG.Status2Reg)
		).getInput();

		// note response length depends of sendBuf length
		irq = response[response.length - 3];
		error = response[response.length - 2];
		status = response[response.length - 1];


		long timer;
		// TODO timeout constants
		int timeout = 10;

		long start = System.currentTimeMillis();
		while ((irq & 0x32) == 0) {
			timer = System.currentTimeMillis() - start;
			response = transfer(TransferBuilder.get().
					readReg(REG.ComIrqReg).
					readReg(REG.ErrorReg).
                    readReg(REG.Status2Reg)
            ).getInput();

			irq = response[0];
			error = response[1];
			status = response[2];

			if (timer > timeout) {
				Log.v(TAG, "AUTH < FAILED! (timeout "+ timeout + ")");
				// return new TransceiveResponse(-3, null, 0);
				return TransceiveResponse.getIOErrorResponse();
			}
		}

		if ((irq & 0x02) != 0) {
			Log.v(TAG, "AUTH < FAILED! (IRQ error 0x" + toStr(irq) + ")");
			// return new TransceiveResponse(-3, null, 0);
			return TransceiveResponse.getIOErrorResponse();
		}

		if ((error & 0x13) != 0) { // 0x1B with collision
			Log.v(TAG, "AUTH < FAILED! (I/O error 0x" + toStr(error) + ")");
			// return new TransceiveResponse(-1, null, 0);
			return TransceiveResponse.getIOErrorResponse();
		}

		if ((status & 0x08) == 0) {
			Log.v(TAG, "AUTH < FAILED! (Status error 0x" + toStr(status) + ")");
			// return new TransceiveResponse(-1, null, 0);
			return TransceiveResponse.getIOErrorResponse();
		}

		Log.v(TAG, "AUTH < OK ");

		return new TransceiveResponse(0, new byte[0],0);
	}

	public TransceiveResponse transceive(byte[] sendBuf, int txLastBits,
			int timeout, int flags) throws IOException {
		Log.v(TAG, "DATA > " + toStr(sendBuf) + (((txLastBits & 0x07) != 0) ? ("(" + (txLastBits & 0x07) + " bit)") : ""));

		if (timeout == 0)
			timeout = EMERGENCY_TIMEOUT;
		byte irq, error, validBits, validBytes;
		byte[] response;
		response = transfer(TransferBuilder.get().
			writeReg(REG.CommandReg, CMD.Idle).
			writeReg(REG.ComIrqReg, (byte) 0x7F).
			writeReg(REG.TxModeReg, (flags & CRC_TX) != 0 ? (byte) 0x80
					: (byte) 0x00).
			writeReg(REG.RxModeReg, (flags & CRC_RX) != 0 ? (byte) 0x80
					: (byte) 0x00).
			writeReg(REG.FIFOLevelReg, (byte) 0x80).
			writeReg(REG.FIFODataReg, sendBuf).
			writeReg(REG.CommandReg, CMD.Transceive).
			writeReg(REG.BitFramingReg, (byte) (0x80 | txLastBits & 0x07)).

			//readReg(REG.ComIrqReg). // just wait - enough for 9600 baudrate to exclude wait loop
			readReg(REG.ComIrqReg).
			readReg(REG.ErrorReg).
			readReg(REG.ControlReg).
			readReg(REG.FIFOLevelReg)
		).getInput();

		// note response length depends of sendBuf length
		irq = response[response.length - 4];
		error = response[response.length - 3];
		validBits = (byte) (response[response.length - 2] & 0x07);
		validBytes = response[response.length - 1];

		long start = System.currentTimeMillis();
		while ((irq & 0x30) == 0) {
			long timer = System.currentTimeMillis() - start;
			response = transfer(TransferBuilder.get().
					readReg(REG.ComIrqReg).
					readReg(REG.ErrorReg).
					readReg(REG.ControlReg).
					readReg(REG.FIFOLevelReg)
			).getInput();
			irq = response[0];
			error = response[1];
			validBits = (byte) (response[2]& 0x07);
			validBytes = response[3];
			if (timer > timeout) {
				Log.v(TAG, "DATA < FAILED! (timeout " + timeout + ")");
				// return new TransceiveResponse(-3, null, 0);
				return TransceiveResponse.getIOErrorResponse();
			}
		}

		if ((error & 0x13) != 0) { // 0x1B with collision
			Log.v(TAG, "DATA < FAILED! (I/O error 0x" + toStr(error) + ")");
			// return new TransceiveResponse(-1, null, 0);
			return TransceiveResponse.getIOErrorResponse();
		}

		byte[] recvBuf = readFIFO(validBytes);

		Log.v(TAG, "DATA < " + toStr(recvBuf) + ((validBits != 0) ? ("(" + validBits + " bit)") : ""));
		return new TransceiveResponse(0, recvBuf, validBits);
	}

	public boolean selfTest() throws IOException {
		Log.v(TAG, "SELF TEST > BEGIN");
		// See 16.1.1 of MFRC522 Datasheet
		// 1.
		reset();

		transfer(TransferBuilder.get().
		// 2.
				writeReg(REG.FIFOLevelReg, (byte) 0x80).
				writeReg(REG.FIFODataReg, new byte[25]).
				writeReg(REG.CommandReg, CMD.Mem).
		// 3.
				writeReg(REG.AutoTestReg, (byte) 0x09).
		// 4.
				writeReg(REG.FIFODataReg,(byte) 0x00).
		// 5.
				writeReg(REG.CommandReg, CMD.CalcCRC)
		);

		// 6.
/*
		long start = System.currentTimeMillis();
		byte n;
		while (true) {
			long timer = System.currentTimeMillis() - start;
			n = transfer(TransferBuilder.get().
					readReg(REG.FIFOLevelReg)
			).getInput()[0];
			if (n >= 64) {
				break;
			}
			if (timer > EMERGENCY_TIMEOUT) {
				throw new IOException("Timeout");
			}
		}
*/

		// 7.
		byte[] result = readFIFO(64);

		if (result.length != 64)
			return false;

		byte version = transfer(TransferBuilder.get().
				readReg(REG.VersionReg).
				writeReg(REG.CommandReg, CMD.Idle).
				writeReg(REG.AutoTestReg, (byte) 0x00)
		).getInput()[0];

		byte[] reference;

		switch (version) {
		case (byte) 0x88: // Fudan Semiconductor FM17522 clone
			version_name = "FM17522";
			reference = SELFTEST.FM17522_firmware_reference;
			break;
		case (byte) 0x90: // Version 0.0
			version_name = "MFRC522 (0.0)";
			reference = SELFTEST.MFRC522_firmware_referenceV0_0;
			break;
		case (byte) 0x91: // Version 1.0
			version_name = "MFRC522 (1.0)";
			reference = SELFTEST.MFRC522_firmware_referenceV1_0;
			break;
		case (byte) 0x92: // Version 2.0
			version_name = "MFRC522 (2.0)";
			reference = SELFTEST.MFRC522_firmware_referenceV2_0;
			break;
		default: // Unknown version
			return false; // abort test
		}

		for (int i = 0; i < 64; i++) {
			if (result[i] != reference[i]) {
				Log.v(TAG, "SELF TEST < FAILED " + version_name);
				return false;
			}
		}
		Log.v(TAG, "SELF TEST < PASSED " + version_name);
		return true;
	}

	public static String toStr(byte... data) {
		StringBuilder sb = new StringBuilder();
		for (byte b : data) {
			sb.append(String.format("%02X ", b));
		}
		return sb.toString();
	}

	@Override
	public PICC poll(int flags) throws IOException {
		antenna(true);
		PICC picc = PICC_A.poll(this, flags);
		if (picc == null)
			antenna(false);
		return picc;
	}
}
