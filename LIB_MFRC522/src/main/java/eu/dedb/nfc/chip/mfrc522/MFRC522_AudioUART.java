package eu.dedb.nfc.chip.mfrc522;

import java.io.IOException;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;


import eu.dedb.audio.uart.AudioUART;
import eu.dedb.nfc.lib.TransceiveResponse;

public final class MFRC522_AudioUART extends MFRC522 {

	private static final String TAG = "MFRC522";
	private static final int Audio_TIMEOUT = 1000;

	public static final int BAUDRATE_DEFAULT = 9600;
	public static final int BAUDRATE_HIGHSPEED = 115200;
	public static final int BAUDRATE_MIN = 7200;
	public static final int BAUDRATE_MAX = 1228800;

	public String toString() {
		return super.toString() + " via " + uart.getClass().getSimpleName();
	}

	private static final SparseArray<Byte> BAUDRATES;
	static {
		BAUDRATES = new SparseArray<Byte>();
		BAUDRATES.put(7200, (byte) 0xFA);
		BAUDRATES.put(9600, (byte) 0xEB);
		BAUDRATES.put(14400, (byte) 0xDA);
		BAUDRATES.put(19200, (byte) 0xCB);
		BAUDRATES.put(38400, (byte) 0xAB);
		BAUDRATES.put(57600, (byte) 0x9A);
		BAUDRATES.put(115200, (byte) 0x7A);
		BAUDRATES.put(128000, (byte) 0x74);
		BAUDRATES.put(230400, (byte) 0x5A);
		BAUDRATES.put(460800, (byte) 0x3A);
		BAUDRATES.put(921600, (byte) 0x1C);
		BAUDRATES.put(1228800, (byte) 0x15);
	}

	private final Context mContext;
	private final AudioUART uart;
	private final int mAudioOutput;
	private final int mAudioInput;
	private int baudrate = BAUDRATE_DEFAULT;
	private byte[] recvBuffer = new byte[258];

	public static MFRC522_AudioUART get(Context ctx, int audioOutput,
			int audioInput) {
		return get(ctx, audioOutput, audioInput, BAUDRATE_DEFAULT);
	}

	public static MFRC522_AudioUART get(Context ctx, int audioOutput,
			int audioInput, int baudrate) {
		AudioUART serial = new AudioUART(audioOutput, audioInput);
		if (serial != null) {
			MFRC522_AudioUART reader = new MFRC522_AudioUART(ctx, audioOutput,
					audioInput, serial, baudrate);
			// TODO check baudrates

			reader.flushUART();
			reader.uart.setBaudRate(BAUDRATE_DEFAULT);
			reader.flushUART();
			boolean defaultBaudrate = reader.setBaudrate(BAUDRATE_DEFAULT);
			if (!defaultBaudrate) {
				reader.flushUART();
				reader.uart.setBaudRate(baudrate);
				reader.flushUART();
				defaultBaudrate = reader.setBaudrate(BAUDRATE_DEFAULT);
			}
			if (defaultBaudrate) {
				reader.flushUART();
				reader.uart.setBaudRate(BAUDRATE_DEFAULT);
				reader.flushUART();
				defaultBaudrate = reader.setBaudrate(baudrate);
			}

			// try {
			// if (reader.selfTest()) {
			// return reader;
			// }
			// } catch (IOException e) {
			// e.printStackTrace();
			// }
			return reader;
		}
		serial.close();
		return null;
	}

	public MFRC522_AudioUART recover() {
		MFRC522_AudioUART reader = MFRC522_AudioUART.get(mContext,
				mAudioOutput, mAudioInput, baudrate);
		if (reader != null)
			try {
				reader.init();
				return reader;
			} catch (IOException e) {
			}
		return null;
	}

	public MFRC522_AudioUART(Context ctx, int audioOutput, int audioInput,
			AudioUART serial, int baudrate) {
		this.mContext = ctx;
		this.uart = serial;
		this.baudrate = baudrate;
		this.mAudioOutput = audioOutput;
		this.mAudioInput = audioInput;

		uart.open();
		uart.setBaudRate(baudrate);
		uart.setDataBits(8);
		uart.setStopBits(AudioUART.STOPBIT_ONE);
		uart.setParity(AudioUART.PARITY_NONE);
		flushUART();
	}

	public void init(int baudrate) throws IOException {
		setBaudrate(baudrate);
		init();
	}

	public void init() throws IOException {
		 super.init();
//		uart.syncWrite(
//				new byte[] { 0x11, 0x3D, 0x14, (byte) 0x83, 0x15, 0x40 },
//				Audio_TIMEOUT);
	}

	public void flushUART() {
		while (uart.syncRead(recvBuffer, 1000) >= 0) {
			Log.v("MFRC522_UART", "Buffer is not clear!");
		}
		// Log.v("MFRC522_UART", "Buffer is clear!");
	}

	public void reset() throws IOException {
		int baudrate = this.baudrate;
		setBaudrate(BAUDRATE_DEFAULT);
		super.reset();
		setBaudrate(baudrate);
	}


	public byte readReg(byte addr) throws IOException {
		// flushUART();
		int length;
		byte[] sendBuffer = new byte[1];
		sendBuffer[0] = (byte) (0x80 | (addr & 0x3F));
		// write
		length = uart.syncWrite(sendBuffer, Audio_TIMEOUT);
		if (length != sendBuffer.length)
			throw new IOException("readReg write to device error " + length);
		// read
		length = uart.syncRead(recvBuffer, Audio_TIMEOUT);
		if (length < 1)
			throw new IOException("readReg read from device error " + length);

		return recvBuffer[length - 1];
	}

	// @Override
	// public void writeFIFO(byte... data) throws IOException {
	// int length;
	// byte[] sendBuffer = new byte[data.length * 2];
	// byte addr = REG.FIFODataReg & 0x3F;
	// // write at once data
	// for (int i = 0; i < data.length; i += 2) {
	// sendBuffer[i] = addr;
	// sendBuffer[i + 1] = data[i / 2];
	// }
	// // write
	// length = uart.syncWrite(sendBuffer, USB_TIMEOUT);
	// if (length != sendBuffer.length)
	// throw new IOException("writeFIFO write to device error " + length);
	//
	// // read
	// length = uart.syncRead(recvBuffer, USB_TIMEOUT);
	// if (length != data.length)
	// throw new IOException("writeFIFO read from device error " + length);
	//
	// for (int i = 0; i < length; i++)
	// if (addr != recvBuffer[i])
	// throw new IOException("writeFIFO incorrect response @" + i
	// + " " + recvBuffer[i] + ", but excepted "
	// + sendBuffer[0]);
	// }

	// @Override
	// public byte[] readFIFO() throws IOException {
	// int length;
	// int fifosize = readReg(REG.FIFOLevelReg) & 0xFF;
	// byte[] sendBuffer = new byte[fifosize];
	// byte addr = (byte) (0x80 | (REG.FIFODataReg & 0x3F));
	// // read at once data
	// for (int i = 0; i < fifosize; i++) {
	// sendBuffer[i] = addr;
	// }
	// // write
	// length = uart.syncWrite(sendBuffer, USB_TIMEOUT);
	// if (length != sendBuffer.length)
	// throw new IOException("readFIFO write to device error " + length);
	// // read
	// length = uart.syncRead(recvBuffer, USB_TIMEOUT);
	// if (length != fifosize)
	// throw new IOException("readFIFO read from device error " + length);
	//
	// byte[] bb = new byte[fifosize];
	// System.arraycopy(recvBuffer, 0, bb, 0, fifosize);
	// return bb;
	// }

/*
	@Override
	public void writeReg(byte addr, byte... data) throws IOException {
		// flushUART();
		int length;
		byte[] sendBuffer = new byte[2];
		sendBuffer[0] = (byte) (addr & 0x3F);
		for (int i = 0; i < data.length; i++) {
			sendBuffer[1] = data[i];
			// write
			length = uart.syncWrite(sendBuffer, Audio_TIMEOUT);
			if (length != sendBuffer.length)
				throw new IOException("writeReg write to device error "
						+ length);
			// read
			length = uart.syncRead(recvBuffer, Audio_TIMEOUT);
			if (length < 1)
				throw new IOException("writeReg read from device error "
						+ length);

			if (sendBuffer[0] != recvBuffer[length - 1])
				throw new IOException("writeReg incorrect response "
						+ recvBuffer[length - 1] + ", but excepted "
						+ sendBuffer[0]);
		}
	}
*/
	public boolean setBaudrate(int baudrate) {
		Byte value = BAUDRATES.get(baudrate);
		if (value != null) {
			try {
				transfer(TransferBuilder.get().writeReg(REG.SerialSpeedReg, value));
				this.baudrate = baudrate;
				uart.setBaudRate(baudrate);
				Log.v("MFRC522_UART", "BAUDRATE: " + baudrate);
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				Log.v("MFRC522_UART", "BAUDRATE FAILED!");
				return false;
			}
		}
		return false;
	}

	public int getBaudrate() {
		return this.baudrate;
	}

	public AudioUART getSerial() {
		return uart;
	}

	@Override
	public TransceiveResponse transceive(byte[] sendBuf, int sendLen,
			int timeout, int flags) throws IOException {
		return super.transceive(sendBuf, sendLen, Audio_TIMEOUT, flags);
	}

	public TransceiveResponse transceive_atonce(byte[] sendBuf, int sendLen,
			int timeout, int flags) throws IOException {

		if ((flags & 1) != 0) {	//TODO TRANSCEIVE_BITS
			byte[] atonce = new byte[18];
			int responseLength = 11;
			int i = 0;

			atonce[i++] = (byte) 0x01;
			atonce[i++] = (byte) 0x0C;

			atonce[i++] = (byte) 0x12;
			atonce[i++] = (byte) 0x00;

			atonce[i++] = (byte) 0x04;
			atonce[i++] = (byte) 0x7F;

			atonce[i++] = (byte) 0x0A;
			atonce[i++] = (byte) 0x80;

			atonce[i++] = (byte) 0x09;
			atonce[i++] = (byte) sendBuf[0];

			atonce[i++] = (byte) 0x0D;
			atonce[i++] = (byte) (0x80 | (sendLen & 0x07));

			atonce[i++] = (byte) 0x0D;
			atonce[i++] = (byte) (0x80 | (sendLen & 0x07));

			atonce[i++] = (byte) 0x84;

			atonce[i++] = (byte) 0x86;

			atonce[i++] = (byte) 0x8C;

			atonce[i++] = (byte) 0x8A;

			log("aTX", atonce);
			uart.syncWrite(atonce, Audio_TIMEOUT);
			long start = System.currentTimeMillis();
			while (uart.getReceivedSize() != responseLength) {
				if (System.currentTimeMillis() - start > Audio_TIMEOUT)
					return new TransceiveResponse(-3, null, 0);
			}
			byte[] response = new byte[responseLength];
			int length = uart.syncRead(response, Audio_TIMEOUT);
			// TODO check
			log("aTX", response);
			int fifoLength = response[length - 1];
			int validBits = response[length - 2] & 0x07;
			if (fifoLength != 0x00) {
				atonce = new byte[fifoLength];
				for (i = 0; i < fifoLength; i++) {
					atonce[i] = (byte) 0x89;
				}
				log("aTX", atonce);
				uart.syncWrite(atonce, Audio_TIMEOUT);
				start = System.currentTimeMillis();
				while (uart.getReceivedSize() != fifoLength) {
					if (System.currentTimeMillis() - start > Audio_TIMEOUT)
						return new TransceiveResponse(-3, null, 0);
				}
				response = new byte[fifoLength];
				length = uart.syncRead(response, Audio_TIMEOUT);
				log("aTX", response);
				return new TransceiveResponse(0, response, validBits);
			}
			// TODO error check
			return new TransceiveResponse(-3, null, 0);
		} else {
			byte[] atonce = new byte[16 + 2 * sendLen];
			int responseLength = 10 + sendLen;
			int i = 0;

			atonce[i++] = (byte) 0x01;
			atonce[i++] = (byte) 0x0C;

			atonce[i++] = (byte) 0x12;
			atonce[i++] = (byte) 0x00;

			atonce[i++] = (byte) 0x04;
			atonce[i++] = (byte) 0x7F;

			atonce[i++] = (byte) 0x0A;
			atonce[i++] = (byte) 0x80;

			for (int j = 0; j < sendLen; j++) {
				atonce[i++] = (byte) 0x09;
				atonce[i++] = (byte) sendBuf[j];
			}

			atonce[i++] = (byte) 0x0D;
			atonce[i++] = (byte) (0x80 | (sendLen & 0x07));

			atonce[i++] = (byte) 0x0D;
			atonce[i++] = (byte) (0x80 | (sendLen & 0x07));

			atonce[i++] = (byte) 0x84;

			atonce[i++] = (byte) 0x86;

			atonce[i++] = (byte) 0x8C;

			atonce[i++] = (byte) 0x8A;

			log("aTX", atonce);
			uart.syncWrite(atonce, Audio_TIMEOUT);
			long start = System.currentTimeMillis();
			while (uart.getReceivedSize() != responseLength) {
				if (System.currentTimeMillis() - start > Audio_TIMEOUT)
					return new TransceiveResponse(-3, null, 0);
			}
			byte[] response = new byte[responseLength];
			int length = uart.syncRead(response, Audio_TIMEOUT);
			// TODO check
			log("aTX", response);
			int fifoLength = response[length - 1];
			int validBits = response[length - 2] & 0x07;
			if (fifoLength != 0x00) {
				atonce = new byte[fifoLength];
				for (i = 0; i < fifoLength; i++) {
					atonce[i] = (byte) 0x89;
				}
				log("aTX", atonce);
				uart.syncWrite(atonce, Audio_TIMEOUT);
				start = System.currentTimeMillis();
				while (uart.getReceivedSize() != fifoLength) {
					if (System.currentTimeMillis() - start > Audio_TIMEOUT)
						return new TransceiveResponse(-3, null, 0);
				}
				response = new byte[fifoLength];
				length = uart.syncRead(response, Audio_TIMEOUT);
				log("aTX", response);
				return new TransceiveResponse(0, response, validBits);
			}
			// TODO error check
			return new TransceiveResponse(-3, null, 0);
		}

		// if (sendBuf.length == 12 && (sendBuf[0] == 0x60 || sendBuf[0] ==
		// 0x61)) {
		// byte[] tmp = new byte[12];
		// System.arraycopy(data, 0, tmp, 0, 2);
		// System.arraycopy(data, 6, tmp, 2, 6);
		// System.arraycopy(data, 2, tmp, 8, 4);
		// data = tmp;
		// Log.v(TAG, "> " + toStr(data));
		// flushFIFO();
		// writeFIFO(data);
		// writeReg(REG.CommandReg, CMD.MFAuthent);
		// }
	}

	private void log(String stage, byte... data) {
		StringBuilder sb = new StringBuilder();
		for (byte b : data) {
			sb.append(String.format("%02X ", b & 0xff));
		}
		Log.v(TAG, stage + " " + sb.toString());
	}

	@Override
	public TransferBuilder transfer(TransferBuilder tb) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close() {
		uart.close();
	}
}
