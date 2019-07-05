package eu.dedb.nfc.chip.mfrc522;

import java.io.IOException;
import java.util.Arrays;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.util.SparseArray;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public final class MFRC522_UART extends MFRC522 {

	private static final int USB_TIMEOUT = 1000;

	public static final int BAUDRATE_DEFAULT = 9600;
	public static final int BAUDRATE_HIGHSPEED = 115200;
	public static final int BAUDRATE_MIN = 7200;
	public static final int BAUDRATE_MAX = 1228800;
	public static final String TAG = "MFRC522_UART";

	public String toString() {
		return super.toString() + " via " + uart.getClass().getSimpleName();
	}

	private static final SparseArray<Byte> BAUDRATES;
	static {
		BAUDRATES = new SparseArray<Byte>();
		BAUDRATES.put(7200, (byte) 0xFA); // Not supported by usbserial
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
	private final UsbDevice mUsbDevice;
	private final UsbSerialDevice uart;
	private int baudrate = BAUDRATE_DEFAULT;
	private byte[] recvBuffer = new byte[258];

	public MFRC522_UART(Context ctx, UsbDevice device, UsbSerialDevice serial,
						int baudrate) {
		this.mContext = ctx;
		this.mUsbDevice = device;
		this.uart = serial;
		if (BAUDRATES.get(baudrate) == null)
			baudrate = BAUDRATE_DEFAULT;
		this.baudrate = baudrate;

		uart.syncOpen();
		uart.setBaudRate(this.baudrate);
		uart.setDataBits(UsbSerialInterface.DATA_BITS_8);
		uart.setStopBits(UsbSerialInterface.STOP_BITS_1);
		uart.setParity(UsbSerialInterface.PARITY_NONE);
		uart.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

		flushUART();
	}

	public static MFRC522_UART get(Context ctx, UsbDevice mUsbDevice) {
		return get(ctx, mUsbDevice, BAUDRATE_DEFAULT);
	}

	public static MFRC522_UART get(Context ctx, UsbDevice mUsbDevice,
			int baudrate) {

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

				MFRC522_UART reader = new MFRC522_UART(ctx, mUsbDevice, serial,
						baudrate);
				// TODO check baudrates

				boolean baudrateSynced;

				// try default -> default
				Log.v(TAG, "Try baudrate " + BAUDRATE_DEFAULT);
				reader.uart.setBaudRate(BAUDRATE_DEFAULT);
				reader.flushUART();
				baudrateSynced = reader.setBaudrate(BAUDRATE_DEFAULT);

				if (!baudrateSynced && baudrate != BAUDRATE_DEFAULT) {
					// if not default try target -> default
					Log.v(TAG, "Try baudrate " + baudrate);
					reader.uart.setBaudRate(baudrate);
					reader.flushUART();
					baudrateSynced = reader.setBaudrate(BAUDRATE_DEFAULT);
				}

				if (baudrateSynced) {
					// if default is set default -> target
					Log.v(TAG, "Try baudrate " + BAUDRATE_DEFAULT);
					reader.uart.setBaudRate(BAUDRATE_DEFAULT);
					reader.flushUART();
					baudrateSynced = reader.setBaudrate(baudrate);
				}

				if (baudrateSynced) {
					if (reader.selfTest()) {
						Log.v(TAG, "Connection succeed!");
						return reader;
					}
				}

                reader.uart.syncClose();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		usbConnection.close();
		Log.v(TAG, "Connection failed!");
		return null;
	}

	// Does it really needed?
	public MFRC522_UART recover() {
		MFRC522_UART reader = MFRC522_UART.get(mContext, mUsbDevice, baudrate);
		if (reader != null)
			try {
				reader.init();
				return reader;
			} catch (IOException e) {
			}
		return null;
	}

	public void init(int baudrate) throws IOException {
		if (baudrate > 0)
			setBaudrate(baudrate);
		super.init();
	}

	public void init() throws IOException {
		super.init();
	}

	@Override
	public void reset() throws IOException {
		int baudrate = this.baudrate;
		setBaudrate(BAUDRATE_DEFAULT);
		super.reset();
		setBaudrate(baudrate);
	}

	public void flushUART() {
		while (uart.syncRead(recvBuffer, 1) > 0) {
			Log.v(TAG, "UART buffer is not clear...");
		}
	}

	public boolean setBaudrate(int baudrate) {
		Log.v(TAG, "SET BAUDRATE TO " + baudrate);
		Byte value = BAUDRATES.get(baudrate);
		if (value != null) {
			try {
				transfer(TransferBuilder.get().
						writeReg(REG.SerialSpeedReg, value)
				);
				this.baudrate = baudrate;
				uart.setBaudRate(this.baudrate);
				Log.v(TAG, "BAUDRATE IS SET TO " + this.baudrate);
				return true;
			} catch (IOException e) {
				Log.v(TAG, "BAUDRATE SET FAILED!");
				return false;
			}
		}
		Log.v(TAG, "WRONG BAUDRATE " + baudrate);
		return false;
	}

	@Override
	public TransferBuilder transfer(TransferBuilder tb) throws IOException {

		flushUART();

		// write
		byte[] bulk = tb.getOutput();
		Log.v(TAG, "TX >> " + toStr(bulk));
		int len = uart.syncWrite(bulk, USB_TIMEOUT);
		if (len != bulk.length) {
			Log.v(TAG, "TX > TIMEOUT transmitted " + len + " of " + bulk.length + " bytes");
			throw new IOException("transfer write to device error ");
		}

		// read
		boolean filled = false;
		while (!filled) {
			int read = uart.syncRead(recvBuffer, USB_TIMEOUT);
			if(read > 0) {
				Log.v(TAG, "RX < " + toStr(Arrays.copyOf(recvBuffer, read)));
				filled = tb.fill(read, recvBuffer);
			} else {
				Log.v(TAG, "RX < TIMEOUT received " + tb.getInput().length + " of " + tb.getExcepted().length + " bytes: " + toStr(tb.getInput()));
				throw new IOException("transfer read timeout");
			}
		}

		Log.v(TAG, "RX << " + toStr(tb.getInput()));
		return tb;
	}

	@Override
	public void close() {
		uart.syncClose();
	}
}
