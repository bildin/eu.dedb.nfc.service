package eu.dedb.nfc.chip.pn53x;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.util.SparseArray;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.usbserial.UsbSerialInterface.UsbReadCallback;

import eu.dedb.nfc.lib.PCD;
import eu.dedb.nfc.lib.PICC;
import eu.dedb.nfc.lib.PICC_A;
import eu.dedb.nfc.lib.TransceiveResponse;

public class PN53X_UART extends PN53X implements UsbReadCallback {

	private static final String TAG = PN53X_UART.class.getSimpleName();

	private static final int USB_TIMEOUT = 1000;

	public static final int BAUDRATE_DEFAULT = 115200;
	public static final int BAUDRATE_MIN = 7200;
	public static final int BAUDRATE_MAX = 1228800;

	// private ByteBuffer rxBuffer = ByteBuffer.allocate(1024);
	int bufSize = 1024;
	byte[] buf = new byte[bufSize];
	int wOffset = 0;
	int rOffset = 0;
	int bufLen = 0;
	int minFrameLen = 6;
	int minExtFrameLen = 9;
	int waitLen = minFrameLen;
	boolean parsing = false;
	boolean extended = false;

	boolean waitACK = false;
	byte[] response;

	public String toString() {
		return super.toString() + " via " + uart.getClass().getSimpleName();
	}

	private static final SparseArray<Byte> BAUDRATES;
	static {
		BAUDRATES = new SparseArray<Byte>();
		BAUDRATES.put(9600, (byte) 0x00);
		BAUDRATES.put(19200, (byte) 0x01);
		BAUDRATES.put(38400, (byte) 0x02);
		BAUDRATES.put(57600, (byte) 0x03);
		BAUDRATES.put(115200, (byte) 0x04);
		BAUDRATES.put(230400, (byte) 0x05);
		BAUDRATES.put(460800, (byte) 0x06);
		BAUDRATES.put(921600, (byte) 0x07);
		BAUDRATES.put(1228800, (byte) 0x08);
	}

	private final Context mContext;
	private final UsbDevice mUsbDevice;
	private final UsbSerialDevice uart;
	private int baudrate = BAUDRATE_DEFAULT;
	private byte[] recvBuffer = new byte[258];

	public PN53X_UART(Context ctx, UsbDevice device, UsbSerialDevice serial, int baudrate) {
		this.mContext = ctx;
		this.mUsbDevice = device;
		this.uart = serial;
		if (baudrate == 0)
			baudrate = BAUDRATE_DEFAULT;
		this.baudrate = baudrate;

		// uart.syncOpen();
		uart.open();
		uart.read(this);
		uart.setBaudRate(baudrate);
		uart.setDataBits(UsbSerialInterface.DATA_BITS_8);
		uart.setStopBits(UsbSerialInterface.STOP_BITS_1);
		uart.setParity(UsbSerialInterface.PARITY_NONE);
		uart.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
		flushUART();
	}

	public static PN53X_UART get(Context ctx, UsbDevice mUsbDevice) {
		return get(ctx, mUsbDevice, BAUDRATE_DEFAULT);
	}

	public static PN53X_UART get(Context ctx, UsbDevice mUsbDevice, int baudrate) {

		Log.v(TAG, "Trying to connect...");

		if (mUsbDevice == null)
			return null;

		UsbManager mUsbManager = (UsbManager) ctx
				.getSystemService(Context.USB_SERVICE);

		UsbDeviceConnection usbConnection = mUsbManager.openDevice(mUsbDevice);

		if (usbConnection == null)
			return null;

		UsbSerialDevice serial = UsbSerialDevice.createUsbSerialDevice(
				mUsbDevice, usbConnection);

		if (serial != null) {
			try {
				if (BAUDRATES.get(baudrate) == null)
					baudrate = BAUDRATE_DEFAULT;

				PN53X_UART reader = new PN53X_UART(ctx, mUsbDevice, serial,
						baudrate);
				// TODO check baudrates

				boolean baudrateSynced = false;

				// try default -> default
				Log.v(TAG, "Try baudrate " + BAUDRATE_DEFAULT);
				reader.uart.setBaudRate(BAUDRATE_DEFAULT);
				reader.flushUART();
				try {
					reader.init(baudrate);
					baudrateSynced = true;
				} catch (IOException e) {
					if(baudrate != BAUDRATE_DEFAULT) {
						// if not default try target -> default
						Log.v(TAG, "Try baudrate " + baudrate);
						reader.uart.setBaudRate(baudrate);
						reader.flushUART();
						try {
							reader.init(baudrate);
							baudrateSynced = true;
						} catch (IOException e2) {
							reader.uart.close();
						}
					}
				}

				if (baudrateSynced) {
					if (reader.selfTest()) {
						Log.v(TAG, "Connection succeed!");
						return reader;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		usbConnection.close();
		Log.v(TAG, "Connection failed!");
		return null;
	}

	public void wakeup() {
		byte[] wakeup = new byte[1];
		wakeup[0] = 0x55;
		Log.v(TAG, "WAKE UP > " + toStr(wakeup));
		uart.write(wakeup);
	}

	@Override
	public void onReceivedData(byte[] data) {
		Log.v(TAG, "RX < " + toStr(data));
		// synchronized (rxBuffer) {
		// rxBuffer.put(data);
		// Receiver mReceiver = super.getReceiver();
		// if (mReceiver != null) {
		// mReceiver.onReceive(data);
		// }
		// }
		synchronized (buf) {
			for (int i = 0; i < data.length; i++) {
				buf[wOffset] = data[i];
				wOffset = (wOffset + 1) % bufSize;
			}
			bufLen += data.length;

			while (bufLen >= waitLen) {
				if (!parsing && buf[(rOffset) % bufSize] == Frame.PREAMBLE
						&& buf[(rOffset + 1) % bufSize] == Frame.STARTCODE0
						&& buf[(rOffset + 2) % bufSize] == Frame.STARTCODE1) {
					// Frame sync
					if (buf[(rOffset + 3) % bufSize] == Frame.ACK0 && buf[(rOffset + 4) % bufSize] == Frame.ACK1
							&& buf[(rOffset + 5) % bufSize] == Frame.POSTAMBLE) {
						// ACK - Frame
						Log.v(TAG, "RX << ACK");
						rOffset = (rOffset + waitLen) % bufSize;
						bufLen -= waitLen;
						waitLen = minFrameLen;
						waitACK = false;
						continue;
					} else if (buf[(rOffset + 3) % bufSize] == Frame.NACK0
							&& buf[(rOffset + 4) % bufSize] == Frame.NACK1
							&& buf[(rOffset + 5) % bufSize] == Frame.POSTAMBLE) {
						// NACK - Frame
						Log.v(TAG, "RX << NACK");
						rOffset = (rOffset + waitLen) % bufSize;
						bufLen -= waitLen;
						waitLen = minFrameLen;
						continue;
					} else if (buf[(rOffset + 3) % bufSize] == Frame.EXTENDED0
							&& buf[(rOffset + 4) % bufSize] == Frame.EXTENDED1) {
						// Extended Frame
						if (waitLen < minExtFrameLen) {
							waitLen = minExtFrameLen;
							continue;
						} else if (waitLen == minExtFrameLen
								&& (((buf[(rOffset + 5) % bufSize] & 0xFF) + (buf[(rOffset + 6) % bufSize] & 0xFF))
										& 0xFF) == (-buf[(rOffset + 7) % bufSize] & 0xFF)) {
							waitLen += (buf[(rOffset + 5) % bufSize] & 0xFF) << 8
									+ (buf[(rOffset + 6) % bufSize] & 0xFF) + 1;
							parsing = true;
							extended = true;
							continue;
						} else {
							rOffset = (rOffset + 1) % bufSize;
							bufLen--;
							waitLen = minFrameLen;
							continue;
						}
					} else if ((buf[(rOffset + 3) % bufSize] & 0xFF) == (-buf[(rOffset + 4) % bufSize] & 0xFF)) {
						// Normal Frame
						waitLen += (buf[(rOffset + 3) % bufSize] & 0xFF) + 1;
						parsing = true;
						continue;
					}
				} else {
					// Data parsing
					int dataLen = waitLen - 1 - (extended ? minExtFrameLen : minFrameLen);
					int dataOffset = (extended ? minExtFrameLen : minFrameLen) - 1;
					byte dcs = 0x00;
					for (int i = 0; i < dataLen; i++)
						dcs += buf[(rOffset + dataOffset + i) % bufSize];
					if ((dcs & 0xFF) != (-buf[(rOffset + waitLen - 2) % bufSize] & 0xFF)
							|| (buf[(rOffset + waitLen - 1) % bufSize] & 0xFF) != Frame.POSTAMBLE) {
						rOffset = (rOffset + 1) % bufSize;
						bufLen--;
						waitLen = minFrameLen;
						parsing = false;
						extended = false;
						continue;
					}

					byte[] content = new byte[dataLen];
					for (int i = 0; i < dataLen; i++) {
						content[i] = buf[(rOffset + dataOffset + i) % bufSize];
					}

					Log.v(TAG, "RX << " + toStr(content));

					if (dataLen == 1) {
						Log.v(TAG, "RX << ERROR");
					} else {
						response = content;
					}

					if (dataLen > 1) {
						Receiver mReceiver = super.getReceiver();
						if (mReceiver != null) {
							mReceiver.onReceive(content);
						}
					}

					rOffset = (rOffset + waitLen) % bufSize;
					bufLen -= waitLen;
					waitLen = minFrameLen;
					parsing = false;
					extended = false;
					continue;
				}
			}
		}
	}

	@Override
	public PCD recover() {
		// TODO Auto-generated method stub
		return null;
	}

	public void init(int baudrate) throws IOException {
		init();
		if (baudrate > 0)
			setBaudrate(baudrate);
	}

	public void init() throws IOException {
		wakeup();
		byte[] buffer = new byte[1024];

		byte[] buf;
		buf = new byte[] { Frame.TFI_HOST2PN53X, CMD.SAMConfiguration, 0x01 };

		int rlen = transfer(buf, buffer, 100);
		if (rlen < 0)
			throw new IOException("init rlen: " + rlen);

		super.init();

		buf = new byte[] { Frame.TFI_HOST2PN53X, CMD.SetParameters, 0x0};

		rlen = transfer(buf, buffer, 100);
	}

	public void flushUART() {
		// TODO legacy compat
	}

	public boolean setBaudrate(int baudrate) {
		Log.v(TAG, "SET BAUDRATE TO " + baudrate);
		Byte value = BAUDRATES.get(baudrate);
		if (value != null) {
			int rlen = transfer(new byte[] { Frame.TFI_HOST2PN53X, CMD.SetSerialBaudRate, value }, new byte[256], 100);
			if (rlen == 2) {
				this.baudrate = baudrate;
				uart.write(Frame.ACKFrame);
				Log.v(TAG, "ACK > " + toStr(Frame.ACKFrame));
				//long started = System.currentTimeMillis();
				//while(System.currentTimeMillis() - started > 1) {}
				uart.setBaudRate(baudrate);
				Log.v(TAG, "BAUDRATE IS SET TO " + this.baudrate);
				return true;
			} else {
				Log.v(TAG, "BAUDRATE SET FAILED!");
				return false;
			}
		}
		Log.v(TAG, "WRONG BAUDRATE " + baudrate);
		return false;
	}

	@Override
	public TransferBuilder transfer(TransferBuilder tb) throws IOException{
		boolean noACK = false;

		byte[] txData = tb.getOutput();
		int timeout = 100;

		Log.v(TAG, "TX >> " + toStr(txData));

		byte[] tx_buf = Frame.wrap(txData);
		Log.v(TAG, "TX > " + toStr(tx_buf));

		waitACK = true;
		uart.write(tx_buf);

		long started = System.currentTimeMillis();

		while (waitACK) {
			if (System.currentTimeMillis() - started > timeout) {
				waitACK = false;
				noACK = true;
				break;
			}
		}

		if (noACK)
			throw new IOException("No ACK error!");

		while (response == null) {
			if (System.currentTimeMillis() - started > timeout)
				throw new IOException("Response timeout!");
		}

		tb.fill(response.length, response);
		response = null;

		if(!tb.check())
			throw new IOException("Frame error");
		return tb;
	}

	@Override
	public int transfer(byte[] txData, byte[] rxData, int timeout) {

		boolean noACK = false;

		Log.v(TAG, "TX >> " + toStr(txData));

		byte[] tx_buf = Frame.wrap(txData);
		Log.v(TAG, "TX > " + toStr(tx_buf));

		waitACK = true;
		uart.write(tx_buf);

		long started = System.currentTimeMillis();

		while (waitACK) {
			if (System.currentTimeMillis() - started > timeout) {
				waitACK = false;
				noACK = true;
				break;
			}
		}

		if (noACK)
			return -1;

		while (response == null) {
			if (System.currentTimeMillis() - started > timeout)
				return -4;
		}

		int length = response.length;
		System.arraycopy(response, 0, rxData, 0, length);
		response = null;
		// TODO parse response

		// TODO check Frame

		// int length = rxBuffer.array()[6 + 3] & 0xFF;
		// int lcs = rxBuffer.array()[6 + 4] & 0xFF;
		//
		// if (((length + lcs) & 0xFF) != 0) {
		// // TODO check extended Frame
		// return -5;
		// }
		//
		// while (rxBuffer.position() < 6 + 5 + length + 2) {
		// if (System.currentTimeMillis() - started > timeout)
		// return -6;
		// }
		//
		// if (length > rxData.length) {
		// return -7;
		// }
		//
		// Log.v(TAG, length + "/" + rxData.length);
		//
		// System.arraycopy(rxBuffer.array(), 6 + 5, rxData, 0, length);
		//
		// byte dcs = rxBuffer.array()[6 + 5 + (length & 0xFF)];
		// byte cs = Frame.checksum(rxData);
		// if (cs - dcs != 0)
		// return -8;

		return length;
	}

	@Override
	public void close() {
		uart.close();
	}
}
