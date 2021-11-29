package eu.dedb.nfc.chip.pn53x;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import android.util.Log;
import eu.dedb.nfc.lib.PCD;
import eu.dedb.nfc.lib.PICC;
import eu.dedb.nfc.lib.PICC_A;
import eu.dedb.nfc.lib.TransceiveResponse;

public abstract class PN53X implements PCD {

	private static final String TAG = PN53X.class.getSimpleName();
	private static final int EMERGENCY_TIMEOUT = 5000;

	private String version_name = "PN53X UNKNOWN";
	
	private Receiver mReceiver;

	//private boolean targetInited = false;

	private int mFlags = 0;

	abstract public TransferBuilder transfer(TransferBuilder tb) throws IOException;

	abstract public int transfer(byte[] txData, byte[] rxData, int timeout);

	public byte readReg(short addr) throws IOException {
		byte[] value = new byte[3];
		int rlen = transfer(new byte[] { Frame.TFI_HOST2PN53X, CMD.ReadRegister, (byte) ((addr >> 8) & 0xFF),
				(byte) (addr & 0xFF) }, value, 100);

		if (rlen == 3 && value[0] == Frame.TFI_PN53X2HOST && value[1] == CMD.ReadRegister + 1)
			return value[2];

		throw new IOException("readReg " + addr + " rlen: " + rlen);
	}

	public void writeReg(short addr, byte... data) throws IOException {
		for (byte d : data) {
			byte[] value = new byte[3];
			int rlen = transfer(new byte[] { Frame.TFI_HOST2PN53X, CMD.WriteRegister, (byte) ((addr >> 8) & 0xFF),
					(byte) (addr & 0xFF), d }, value, 100);

			if (rlen == 2 && value[0] == Frame.TFI_PN53X2HOST && value[1] == CMD.WriteRegister + 1) {
			} else
				throw new IOException("writeReg " + addr + " rlen: " + rlen);
		}

	}

	public void setBitmask(short addr, byte bitmask) throws IOException {
		byte value = readReg(addr);
		value = (byte) (value | bitmask);
		writeReg(addr, value);
	}

	public void clearBitmask(short addr, byte bitmask) throws IOException {
		byte value = readReg(addr);
		value = (byte) (value & (~bitmask));
		writeReg(addr, value);
	}

	public byte[] readFIFO() throws IOException {
		byte[] bb = new byte[0];
		while (readReg(REG.CIU_FIFOLevel) > 0) {
			byte b = readReg(REG.CIU_FIFOData);
			byte[] tmp = new byte[bb.length + 1];
			System.arraycopy(bb, 0, tmp, 0, bb.length);
			tmp[bb.length] = b;
			bb = tmp;
		}
		return bb;
	}

	public void writeFIFO(byte... buffer) throws IOException {
		writeReg(REG.CIU_FIFOData, buffer);
	}

	public void flushFIFO() throws IOException {
		setBitmask(REG.CIU_FIFOLevel, (byte) 0x80);
	}

	public boolean selfTest() throws IOException {
		byte[] tx_buf = new byte[] { Frame.TFI_HOST2PN53X, CMD.GetFirmwareVersion };
		byte[] rx_buf = new byte[256];
		int rlen = transfer(tx_buf, rx_buf, 100);
		if(rlen == 6) {
			version_name =  String.format("PN5%02X (%d.%d)", rx_buf[2],rx_buf[3],rx_buf[4]);
			return true;
		}
		return false;
	}

	public String toString() {
		return version_name;
	}

	@Override
	public void init() throws IOException {
		reset();

		TransferBuilder.get().write(CMD.WriteRegister)
			.writeReg(REG.CIU_Control, 0x10)
			.writeReg(REG.CIU_TxAuto,0x40)
			.writeReg(REG.CIU_Mode, 0x3D)
		.run(this);

	}

	public void reset() throws IOException {
		writeReg(REG.CIU_Command, CIU_CMD.SoftReset);
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		while ((readReg(REG.CIU_Command) & 0x10) != 0) {

		}
	}

	public void antenna(boolean state) throws IOException {
		// byte value = readReg(REG.TxControlReg);
		byte config = (byte) 0x80;
		if (state) {
			// if ((value & 0x03) != 0x03) {
			writeReg(REG.CIU_TxControl, (byte) (config | 0x03));
			// }
		} else {
			// if ((value & 0x03) != 0x00) {
			writeReg(REG.CIU_TxControl, (byte) (config & ~0x03));
			// }
		}
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
						byte[] uid = new byte[] { sendBuf[2], sendBuf[3], sendBuf[4], sendBuf[5] };
						byte[] key = new byte[] { sendBuf[6], sendBuf[7], sendBuf[8], sendBuf[9], sendBuf[10],
								sendBuf[11] };
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
						return TransceiveResponse.getSuccessResponse();
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

	/*
	public TransceiveResponse authenticate(byte keyType, byte blockNumber,
										   byte[] key, byte[] uid) throws IOException {
		Log.v(TAG, "AUTH > block " + toStr(blockNumber) + "key " + (keyType == 0x61 ? "B" : "A") + ": " + toStr(key));

		byte[] config = new byte[] {
				Frame.TFI_HOST2PN53X,
				CMD.WriteRegister,

				REG.CIU_TxMode >> 8,
				REG.CIU_TxMode & 0xFF,
				(byte) 0x80,

				REG.CIU_RxMode >> 8,
				REG.CIU_RxMode & 0xFF,
				(byte) 0x80,

				REG.CIU_BitFraming >> 8,
				REG.CIU_BitFraming & 0xFF,
				(byte) 0x00
		};

		byte[] value = new byte[3];
		int rlen = transfer(config, value, 100);

		if (!(rlen == 2 && value[0] == Frame.TFI_PN53X2HOST && value[1] == CMD.WriteRegister + 1))
			throw new IOException("config writeReg failed, rlen: " + rlen);

		byte[] response;
		byte[] cmd = new byte[15];
		cmd[0] = Frame.TFI_HOST2PN53X;
		cmd[1] = CMD.InDataExchange;
		cmd[2] = 0x01;
		cmd[3] = keyType;
		cmd[4] = blockNumber;
		System.arraycopy(key, 0, cmd, 5, 6);
		System.arraycopy(uid, 0, cmd, 11, 4);

		byte[] buffer = new byte[1024];
		rlen = transfer(cmd, buffer, 100);

		if (rlen > 2 && buffer[0] == Frame.TFI_PN53X2HOST && buffer[1] == (byte) (CMD.InDataExchange + 1)) {

			response = new byte[rlen - 2];
			System.arraycopy(buffer, 2, response, 0, response.length);

			if (response.length > 0) {
				if (response[0] == 0) {
					// No Error
					Log.v(TAG, "AUTH < OK ");
					return TransceiveResponse.getSuccessResponse();
				} else if (response[0] == 1) {
					// Timeout
					Log.v(TAG, "AUTH < FAILED! (Timeout)");
					return TransceiveResponse.getIOErrorResponse();
				} else if (response[0] == 0x14) {
					// MIFARE AUTH ERROR
					Log.v(TAG, "AUTH < FAILED! (Auth error)");
					return TransceiveResponse.getIOErrorResponse();
				}
			}
		}
		// TODO
		Log.v(TAG, "AUTH < FAILED! (Error)");
		return TransceiveResponse.getIOErrorResponse();
	}
	// */

	public TransceiveResponse authenticate(byte keyType, byte blockNumber,
										   byte[] key, byte[] uid) throws IOException {


		Log.v(TAG, "AUTH > block " + toStr(blockNumber) + "key " + (keyType == 0x61 ? "B" : "A") + ": " + toStr(key));

		TransferBuilder.get().write(CMD.WriteRegister)
				.writeReg(REG.CIU_Command, CIU_CMD.Idle)
				.writeReg(REG.CIU_CommIrq, 0x7F)
				.writeReg(REG.CIU_FIFOLevel, 0x80)
				.writeReg(REG.CIU_FIFOData, keyType)
				.writeReg(REG.CIU_FIFOData, blockNumber)
				.writeReg(REG.CIU_FIFOData, key)
				.writeReg(REG.CIU_FIFOData, uid)
				.writeReg(REG.CIU_Command, CIU_CMD.MFAuthent)
		.run(this);


		byte irq, error, status;

		TransferBuilder tb = TransferBuilder.get().write(CMD.ReadRegister)
				.readReg(REG.CIU_CommIrq)
				.readReg(REG.CIU_Error)
				.readReg(REG.CIU_Status2);


		int timeout = 10;
		long start = System.currentTimeMillis();
		do {
			long timer = System.currentTimeMillis() - start;

			byte[] rx_buffer = tb.run(this).getInput();
			if(rx_buffer.length != 5)
				throw new IOException("check readReg failed");

			irq = rx_buffer[2];
			error = rx_buffer[3];
			status = rx_buffer[4];
			if (timer > timeout) {
				Log.v(TAG, "AUTH < FAILED! (timeout " + timeout + ")");
				return TransceiveResponse.getIOErrorResponse();
			}
		} while ((irq & 0x32) == 0);

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

	/*
	public TransceiveResponse transceive(byte[] sendBuf, int txLastBits,
										  int timeout, int flags) throws IOException {

		Log.v(TAG, "DATA > " + toStr(sendBuf) + (((txLastBits & 0x07) != 0) ? ("(" + (txLastBits & 0x07) + " bit)") : ""));

		byte[] config = new byte[] {
				Frame.TFI_HOST2PN53X,
				CMD.WriteRegister,

				REG.CIU_TxMode >> 8,
				REG.CIU_TxMode & 0xFF,
				(byte) ((flags & CRC_TX) != 0 ? 0x80 : 0x00),

				REG.CIU_RxMode >> 8,
				REG.CIU_RxMode & 0xFF,
				0x00, //(byte) ((flags & CRC_RX) != 0 ? 0x80 : 0x00),

				REG.CIU_BitFraming >> 8,
				REG.CIU_BitFraming & 0xFF,
				(byte) (txLastBits & 0x07)
		};

		byte[] value = new byte[3];
		int rlen = transfer(config, value, 100);

		if (!(rlen == 2 && value[0] == Frame.TFI_PN53X2HOST && value[1] == CMD.WriteRegister + 1))
			throw new IOException("config writeReg failed, rlen: " + rlen);


		byte[] cmd = new byte[sendBuf.length + 2];
		cmd[0] = Frame.TFI_HOST2PN53X;
		cmd[1] = CMD.InCommunicateThru;
		System.arraycopy(sendBuf, 0, cmd, 2, sendBuf.length);

		byte[] buffer = new byte[1024];
		rlen = transfer(cmd, buffer, 500);

		if (rlen > 2 && buffer[0] == Frame.TFI_PN53X2HOST && buffer[1] == (byte) (CMD.InCommunicateThru + 1)) {

			if (buffer[2] == 0x00) {
				int validBits = readReg(REG.CIU_Control) & 0x07;

				int recvLen = (validBits == 0 && (flags & CRC_RX) != 0) ? rlen - 5 : rlen - 3;
				byte[] recvBuf = new byte[recvLen];
				System.arraycopy(buffer, 3, recvBuf, 0, recvBuf.length);

				Log.v(TAG, "DATA < " + toStr(recvBuf) + ((validBits != 0) ? ("(" + validBits + " bit)") : ""));
				return new TransceiveResponse(TransceiveResponse.RESULT_SUCCESS, recvBuf, validBits);
			} else {
				Log.v(TAG, "DATA < ERROR " + toStr(buffer[2]));
				return TransceiveResponse.getIOErrorResponse();
			}
		}
		Log.v(TAG, "DATA < ERROR, rlen: " + rlen);
		return TransceiveResponse.getIOErrorResponse();
	}
	// */


	public TransceiveResponse transceive(byte[] sendBuf, int txLastBits, int timeout, int flags) throws IOException {

		byte[] rx_buffer;

		Log.v(TAG, "DATA > " + toStr(sendBuf) + (((txLastBits & 0x07) != 0) ? ("(" + (txLastBits & 0x07) + " bit)") : ""));

		TransferBuilder.get().write(CMD.WriteRegister)
				.writeReg(REG.CIU_Command, CIU_CMD.Idle)
				.writeReg(REG.CIU_CommIrq, 0x7F)
				.writeReg(REG.CIU_TxMode, (flags & CRC_TX) != 0 ? 0x80 : 0x00)
				.writeReg(REG.CIU_RxMode, (flags & CRC_RX) != 0 ? 0x80 : 0x00)
				.writeReg(REG.CIU_ManualRCV, (flags & PARITY_AS_DATA) != 0 ? 0x80 : 0x00)
				.writeReg(REG.CIU_FIFOLevel, 0x80)
				.writeReg(REG.CIU_FIFOData, sendBuf)
				.writeReg(REG.CIU_Command, CIU_CMD.Transceive)
				.writeReg(REG.CIU_BitFraming, 0x80 | txLastBits)
		.run(this);

		TransferBuilder tb = TransferBuilder.get().write(CMD.ReadRegister)
				.readReg(REG.CIU_CommIrq)
				.readReg(REG.CIU_Error)
				.readReg(REG.CIU_Control)
				.readReg(REG.CIU_FIFOLevel);

		byte irq, error, validBits, validBytes;

		long start = System.currentTimeMillis();
		do {
			long timer = System.currentTimeMillis() - start;

			rx_buffer = tb.run(this).getInput();
			if(rx_buffer.length != 6)
				throw new IOException("check readReg failed");

			irq = rx_buffer[2];
			error = rx_buffer[3];
			validBits = (byte) (rx_buffer[4] & 0x07);
			validBytes = rx_buffer[5];
			if (timer > timeout) {
				Log.v(TAG, "DATA < FAILED! (timeout " + timeout + ")");
				return TransceiveResponse.getIOErrorResponse();
			}
		} while ((irq & 0x30) == 0);

		if ((error & 0x13) != 0) { // 0x1B with collision
			Log.v(TAG, "DATA < FAILED! (I/O error 0x" + toStr(error) + ")");
			// return new TransceiveResponse(-1, null, 0);
			return TransceiveResponse.getIOErrorResponse();
		}

		rx_buffer = TransferBuilder.get().write(CMD.ReadRegister).readReg(REG.CIU_FIFOData, validBytes).run(this).getInput();
		if(rx_buffer.length != validBytes + 2)
			throw new IOException("read readReg failed");

		byte[] recvBuf = new byte[validBytes];
		System.arraycopy(rx_buffer, 2, recvBuf, 0, validBytes);

		Log.v(TAG, "DATA < " + toStr(recvBuf) + ((validBits != 0) ? ("(" + validBits + " bit)") : ""));
		return new TransceiveResponse(TransceiveResponse.RESULT_SUCCESS, recvBuf, validBits);
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
		PICC picc = null;
		if ((flags & (PCD.ACTIVATE_CHINESE | PCD.IGNORE_BCC_ERROR)) != 0) {
			antenna(true);
			// clear crypto state
			writeReg(REG.CIU_Status2, (byte) 0x00);

			picc = PICC_A.poll(this, flags);

			if (picc == null)
				antenna(false);
		} else { // faster macro command

			byte[] buffer = new byte[1024];

			int rlen = transfer(new byte[] { Frame.TFI_HOST2PN53X, CMD.InListPassiveTarget, 0x01, 0x00 }, buffer, 250);
			if (rlen > 2 && buffer[0] == Frame.TFI_PN53X2HOST && buffer[1] == (byte) (CMD.InListPassiveTarget + 1)) {
				byte[] atr = new byte[rlen - 2];
				System.arraycopy(buffer, 2, atr, 0, atr.length);

				if (atr.length > 2) {
					int count = atr[0] & 0xFF; // 0x01
					int target = atr[1] & 0xFF; // 0x01
					if (count != 0 && target == 0x01) {
						byte[] atqa = new byte[]{atr[3], atr[2]};
						short sak = (short) (atr[4] & 0xFF);
						int uidLength = atr[5] & 0xFF;
						byte[] uid = new byte[uidLength];
						System.arraycopy(atr, 6, uid, 0, uidLength);
						picc = new PICC_A(this, atqa, uid, sak, flags);
					}
				}
			}
		}
		return picc;
	}

	public boolean receive(Receiver receiver) {
		mReceiver = receiver;
		return mReceiver != null;
	}
	
	Receiver getReceiver() {
		return mReceiver;
	}
	
	public interface Receiver {
		public void onReceive(byte[] data);
	}
}
