package eu.dedb.audio.uart;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

public class AudioUART {
	public final static short LOW = Short.MAX_VALUE;
	public final static short HIGH = Short.MIN_VALUE;

	public final static int PARITY_NONE = 0;
	public final static int PARITY_ODD = 1;
	public final static int PARITY_EVEN = 2;
	public final static int PARITY_MARK = 3;
	public final static int PARITY_SPACE = 4;

	public final static int STOPBIT_ONE = 2;
	public final static int STOPBIT_ONEPOINTFIVE = 3;
	public final static int STOPBIT_TWO = 4;

	public final static int MODE_SILENCE = 0;
	public final static int MODE_CLOCK = 1;
	public final static int MODE_MAIN = 2;
	public final static int MODE_DIFFERENTIAL = 3;

	private final static int DEFAULT_BAUDRATE = 9600;

	private Queue<short[]> queueTX = new LinkedList<short[]>();
	private ByteBuffer dataRX = ByteBuffer.allocate(256);
	private short[] bufferRX;
	private short[] bufferTX;

	private final int output;
	private final int input;
	private final int samplerate;
	private final int minTxBufferSize;
	private final int minRxBufferSize;
	private final AudioTrack audioTX;
	private final AudioRecord audioRX;

	private AudioUARTReadCallback mReceiver;
	private short[] idleTx;
	private int sampleperbit;
	private int alignmentbit;
	private int bitspersymbol;

	private int mode = MODE_CLOCK;
	private int baudrate = DEFAULT_BAUDRATE;
	private int databits = 8;
	private int stopbits = STOPBIT_ONE;
	private int paritybit = PARITY_NONE;

	private double threshold0level = 0.5;
	private double threshold1level = 0.7;
	private int framesTxBuffer = 16;
	private int framesRxBuffer = 8;

	private long syncTXmarker = 0;

	public AudioUART(int outputstream, int inputstream) {
		this.output = outputstream;
		this.input = inputstream;
		this.samplerate = AudioTrack.getNativeOutputSampleRate(output);
		this.minTxBufferSize = AudioTrack.getMinBufferSize(samplerate,
				AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
		this.minRxBufferSize = AudioRecord.getMinBufferSize(samplerate,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		bufferRX = new short[minRxBufferSize];
		this.audioTX = new AudioTrack(output, samplerate,
				AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
				minTxBufferSize, AudioTrack.MODE_STREAM);
		this.audioRX = new AudioRecord(input, samplerate,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
				minRxBufferSize / framesRxBuffer);
		sampleperbit = samplerate / baudrate;
		alignmentbit = (samplerate % baudrate) == 0 ? 0
				: (baudrate * sampleperbit) / (samplerate % baudrate) + 1;
		bitspersymbol = 1 + databits + (paritybit != PARITY_NONE ? 1 : 0);
		idleTx = idle();
	}

	public interface AudioUARTReadCallback {
		abstract void onReceivedData(byte data);
	}

	private class Integrator {
		final int[] frame;
		final int framesize;
		int value = 0;
		int position = 0;

		public Integrator(int size) {
			framesize = size;
			frame = new int[framesize];
		}

		public int integrate(int in) {
			value -= frame[position];
			value += in;
			frame[position] = in;
			position = (position + 1) % framesize;
			return value;
		}
	}

	private class PeakDetector {
		final int[] frame;
		final int framesize;
		int value = 0;
		int position = 0;

		public PeakDetector(int size) {
			framesize = size;
			frame = new int[framesize];
		}

		public int detect(int in) {
			frame[position] = in;
			position = (position + 1) % framesize;
			value = 0;
			for (int peak : frame)
				if (peak > value)
					value = peak;
			return value;
		}
	}

	Thread threadTX = new Thread() {
		@Override
		public void run() {
			audioTX.play();
			// short[] buffer;
			while (!this.isInterrupted()) {
				// buffer = queueTX.poll();
				if (bufferTX == null) {
					audioTX.write(idleTx, 0, idleTx.length);
				} else {
					Log.v("TX", "write to audio " + System.currentTimeMillis());
					audioTX.write(bufferTX, 0, bufferTX.length);
					bufferTX = null;
					Log.v("TX",
							"written to audio " + System.currentTimeMillis());
				}
			}
			audioTX.stop();
		}
	};

	Thread threadRX = new Thread() {
		@Override
		public void run() {
			audioRX.startRecording();
			// use full reference carrier wave length for bit state detector
			// (ASK demodulator)
			Integrator integrator = new Integrator(4);
			// use double symbol interval for peak detector as auto-adjustable
			// reference level
			PeakDetector peak = new PeakDetector(sampleperbit * bitspersymbol
					* 2);
			int integrated = 0;

			int length = 0;

			int max = 0;
			int threshold0 = 0;
			int threshold1 = 0;

			boolean data = false;
			int state = -1;
			int statecounter = 0;
			int samplecounter = 0;
			int aligncounter = 0;
			int bitcounter = 0;
			byte value = 0;

			while (!this.isInterrupted()) {
				length = audioRX.read(bufferRX, 0, minRxBufferSize
						/ framesRxBuffer);
				Log.v("BUS", "READ " + length + " / "
						+ (minRxBufferSize / framesRxBuffer) + " / "
						+ minRxBufferSize);
				if (length == 0)
					break;

				for (int i = 0; i < length; i++) {
					if (data && alignmentbit != 0
							&& aligncounter % alignmentbit == alignmentbit - 1)
						samplecounter--;

					integrated = integrator.integrate(Math.abs(bufferRX[i]));

					max = peak.detect(integrated);
					threshold0 = (int) (max * threshold0level);
					threshold1 = (int) (max * threshold1level);

					// Log.v("LEVEL", "MAX = " + max + ", CUR = " + integrated);

					if (integrated < threshold0) { // logical 0
						if (state != 0) {
							Log.v("BUS", "RAW 1 x" + statecounter + " " + data
									+ " @" + samplecounter + ":" + aligncounter);
							state = 0;
							statecounter = 0;
							if (data == false) { // begin receiving
								Log.v("BUS",
										"START begin "
												+ System.currentTimeMillis());
								data = true;
								samplecounter = 0;
								aligncounter = 0;
								bitcounter = 0;
							}
						}
					}

					if (integrated > threshold1) { // logical 1
						if (state != 1) {
							Log.v("BUS", "RAW 0 x" + statecounter + " " + data
									+ " @" + samplecounter + ":" + aligncounter);
							state = 1;
							statecounter = 0;
						}
					}

					if (data == true) {
						// sample point at the middle of bit
						if (samplecounter % sampleperbit == sampleperbit / 2) {
							if (bitcounter == 0) {
								// start bit
								Log.v("BUS", "SAMPLE startbit " + state + " @"
										+ samplecounter + ":" + aligncounter);
								value = 0;
							} else if (bitcounter <= databits) {
								// data bits
								Log.v("BUS", "SAMPLE databit["
										+ (bitcounter - 1) + "] " + state
										+ " @" + samplecounter + ":"
										+ aligncounter);
								value |= state << (bitcounter - 1);
							} else
							// TODO parity bit check
							// if () {} else
							if (bitcounter == bitspersymbol) {
								Log.v("BUS", "SAMPLE stopbit " + state + " @"
										+ samplecounter + ":" + aligncounter);
								Log.v("BUS",
										String.format("DATA RX %02X (%s)",
												value, new String(
														new byte[] { value }))
												+ " "
												+ System.currentTimeMillis()
												+ " loopback latency "
												+ (System.currentTimeMillis() - syncTXmarker));
								if (mReceiver != null) {
									mReceiver.onReceivedData(value);
								} else {
									synchronized (dataRX) {
										if (dataRX.hasRemaining()) {
											dataRX.put(value);
											Log.v("BUS", "BUFFER RX LEVEL "
													+ dataRX.position() + "/"
													+ dataRX.capacity());
										} else {
											Log.v("BUS", "BUFFER RX OVERFLOW");
										}
									}
								}
								data = false;
							} else {
								Log.v("BUS", "SAMPLE unexcpected " + state
										+ " @" + samplecounter);
							}
							bitcounter++;
						}
						samplecounter++;
						aligncounter++;
					}

					statecounter++;
				}
			}
			audioRX.stop();
		}
	};

	public boolean setBaudRate(int baudrateValue) {
		if (baudrateValue > samplerate / 4 || baudrateValue < 300)
			return false;
		this.baudrate = baudrateValue;
		sampleperbit = samplerate / baudrate;
		alignmentbit = (samplerate % baudrate) == 0 ? 0
				: (baudrate * sampleperbit) / (samplerate % baudrate) + 1;
		return true;
	}

	public boolean setDataBits(int databitsValue) {
		if (databitsValue < 5 || databitsValue > 8) {
			return false;
		}
		this.databits = databitsValue;
		bitspersymbol = 1 + databits + (paritybit != PARITY_NONE ? 1 : 0);
		return true;
	}

	public boolean setParity(int paritybitValue) {
		if (paritybitValue < PARITY_NONE || paritybitValue > PARITY_SPACE) {
			return false;
		}
		this.paritybit = paritybitValue;
		bitspersymbol = 1 + databits + (paritybit != PARITY_NONE ? 1 : 0);
		return true;
	}

	public boolean setStopBits(int stopbitsValue) {
		if (stopbitsValue < STOPBIT_ONE || stopbitsValue > STOPBIT_TWO) {
			return false;
		}
		this.stopbits = stopbitsValue;
		bitspersymbol = 1 + databits + (paritybit != PARITY_NONE ? 1 : 0);
		return true;
	}

	public boolean setMode(int modeValue) {
		if (modeValue < MODE_SILENCE || modeValue > MODE_DIFFERENTIAL) {
			return false;
		}
		this.mode = modeValue;
		idleTx = idle();
		return true;
	}

	// TODO defer sync or async open
	public void open() {
		threadTX.start();
		threadRX.start();
	}

	public void close() {
		audioTX.release();
		audioRX.release();
		threadTX.interrupt();
		threadRX.interrupt();
	}

	public void write(byte... data) {
		synchronized (queueTX) {
			queueTX.add(encode(data));
			Log.v("TX", "data queried " + System.currentTimeMillis());
		}
	}

	public void read(AudioUARTReadCallback receiver) {
		mReceiver = receiver;
	}

	public int syncWrite(byte[] data, int timeout) {
		StringBuilder sb = new StringBuilder();
		for (short b : data) {
			sb.append(String.format("%02X ", b & 0xFF));
		}
		Log.v("BUS", "DATA TX " + sb.toString());
		bufferTX = encode(data);
		long started = System.currentTimeMillis();
		while (bufferTX != null) {
			if (System.currentTimeMillis() - started > timeout)
				return -1;
		}
		syncTXmarker = System.currentTimeMillis();
		return data.length;
	}

	public int getReceivedSize() {
		return dataRX.position();
	}

	public int syncRead(byte[] data, int timeout) {

		long started = System.currentTimeMillis();

		while (dataRX.position() == 0) {
			if (System.currentTimeMillis() - started > timeout)
				return -1;
		}
		synchronized (dataRX) {
			int size = dataRX.position();
			byte[] buffer = dataRX.array();
			dataRX.rewind();
			if (data.length < size)
				return -1;
			System.arraycopy(buffer, 0, data, 0, size);
			return size;
		}
	}

	private short[] idle() {
		short[] buffer = new short[minTxBufferSize / framesTxBuffer];
		// fill bus
		for (int pos = 0; pos < minTxBufferSize / framesTxBuffer; pos++) {
			if (pos % 2 == 0) {
				if (mode == MODE_SILENCE) {
					// Leave zero level
				} else if (mode == MODE_CLOCK) {
					int i = pos >> 1;
					switch (i % 4) {
					case 0:
						buffer[pos] = HIGH;
						break;
					case 1:
						buffer[pos] = HIGH;
						break;
					case 2:
						buffer[pos] = LOW;
						break;
					case 3:
						buffer[pos] = LOW;
						break;
					}
				} else if (mode == MODE_DIFFERENTIAL) {
					buffer[pos] = LOW;
				} else if (mode == MODE_MAIN) {
					buffer[pos] = HIGH;
				}
			} else {
				buffer[pos] = HIGH;
			}
		}
		return buffer;
	}

	private short[] encode(byte... data) {
		if (data == null)
			return idleTx;
		// TODO include alignment bits
		int need = 2 * data.length
				* (sampleperbit * bitspersymbol + sampleperbit * stopbits / 2);
		int size = need + (alignmentbit != 0 ? need / (alignmentbit - 1) : 0);
		while (size % 8 != 0)
			size++;

		// even - left channel (see mode)
		// odd - right channel (main)
		short[] buffer = new short[size];

		// fill bus
		for (int pos = 0; pos < size; pos++) {
			if (pos % 2 == 0) {
				if (mode == MODE_SILENCE) {
					// Leave zero level
				} else if (mode == MODE_CLOCK) {
					int clock = pos >> 1;
					switch (clock % 4) {
					case 0:
						buffer[pos] = HIGH;
						break;
					case 1:
						buffer[pos] = HIGH;
						break;
					case 2:
						buffer[pos] = LOW;
						break;
					case 3:
						buffer[pos] = LOW;
						break;
					}
				} else if (mode == MODE_DIFFERENTIAL) {
					buffer[pos] = LOW;
				} else if (mode == MODE_MAIN) {
					buffer[pos] = HIGH;
				}
			} else {
				buffer[pos] = HIGH;
			}
		}

		// transmit data
		int pos = 0;
		for (byte symbol : data) {
			// initialize parity
			boolean parity = true;
			// start bit
			{
				boolean value = false;
				short valueRight = value ? HIGH : LOW;
				boolean skipLeft = false;
				short valueLeft = 0;
				if (mode == MODE_DIFFERENTIAL) {
					valueLeft = value ? LOW : HIGH;
				} else if (mode == MODE_MAIN) {
					valueLeft = value ? HIGH : LOW;
				} else {
					skipLeft = true;
				}
				for (int i = 0; i < sampleperbit; i++) {
					if (alignmentbit != 0 && (pos >> 1) % alignmentbit == 0) {
						i--;
					}
					if (skipLeft) {
						pos++;
					} else {
						buffer[pos++] = valueLeft;
					}
					buffer[pos++] = valueRight;
				}
			}
			// data bits
			for (int b = 0; b < databits; b++) {
				boolean value = (symbol & (1 << b)) != 0;
				if (value)
					parity = !parity;
				short valueRight = value ? HIGH : LOW;
				boolean skipLeft = false;
				short valueLeft = 0;
				if (mode == MODE_DIFFERENTIAL) {
					valueLeft = value ? LOW : HIGH;
				} else if (mode == MODE_MAIN) {
					valueLeft = value ? HIGH : LOW;
				} else {
					skipLeft = true;
				}
				for (int i = 0; i < sampleperbit; i++) {
					if (alignmentbit != 0 && (pos >> 1) % alignmentbit == 0) {
						i--;
					}
					if (skipLeft) {
						pos++;
					} else {
						buffer[pos++] = valueLeft;
					}
					buffer[pos++] = valueRight;
				}
			}
			// parity bit
			if (paritybit != PARITY_NONE) {
				boolean value;
				switch (paritybit) {
				case PARITY_ODD:
					value = parity;
					break;
				case PARITY_EVEN:
					value = !parity;
					break;
				case PARITY_MARK:
					value = true;
					break;
				case PARITY_SPACE:
					value = false;
					break;
				default:
					value = false;
				}
				short valueRight = value ? HIGH : LOW;
				boolean skipLeft = false;
				short valueLeft = 0;
				if (mode == MODE_DIFFERENTIAL) {
					valueLeft = value ? LOW : HIGH;
				} else if (mode == MODE_MAIN) {
					valueLeft = value ? HIGH : LOW;
				} else {
					skipLeft = true;
				}
				for (int i = 0; i < sampleperbit; i++) {
					if (alignmentbit != 0 && (pos >> 1) % alignmentbit == 0) {
						i--;
					}
					if (skipLeft) {
						pos++;
					} else {
						buffer[pos++] = valueLeft;
					}
					buffer[pos++] = valueRight;
				}
			}
			// stop bit(s)
			for (int i = 0; i < stopbits * sampleperbit / 2; i++) {
				if (alignmentbit != 0 && (pos >> 1) % alignmentbit == 0) {
					i--;
				}
				pos++;
				pos++;
			}
		}
		return buffer;
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("InlinedApi")
	public static void configureAudioOutput(AudioManager mAudioManager,
			int output) {
		// MUTE ALL
		// TODO save and restore current level
		mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
		if (Build.VERSION.SDK_INT >= 23) {
			mAudioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL,
					AudioManager.ADJUST_MUTE, 0);
//			mAudioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM,
//					AudioManager.ADJUST_MUTE, 0);
			mAudioManager.adjustStreamVolume(AudioManager.STREAM_RING,
					AudioManager.ADJUST_MUTE, 0);
//			mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
//					AudioManager.ADJUST_MUTE, 0);
			mAudioManager.adjustStreamVolume(AudioManager.STREAM_ALARM,
					AudioManager.ADJUST_MUTE, 0);
			mAudioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION,
					AudioManager.ADJUST_MUTE, 0);
			mAudioManager.adjustStreamVolume(AudioManager.STREAM_DTMF,
					AudioManager.ADJUST_MUTE, 0);
		} else {
			mAudioManager.setStreamMute(AudioManager.STREAM_VOICE_CALL, true);
//			mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
			mAudioManager.setStreamMute(AudioManager.STREAM_RING, true);
			mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
			mAudioManager.setStreamMute(AudioManager.STREAM_ALARM, true);
			mAudioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
			mAudioManager.setStreamMute(AudioManager.STREAM_DTMF, true);
		}
		// UNMUTE TARGET
		if (output != AudioManager.STREAM_MUSIC) {
			if (Build.VERSION.SDK_INT >= 23) {
				mAudioManager.adjustStreamVolume(output,
						AudioManager.ADJUST_MUTE, 0);
			} else {
				mAudioManager.setStreamMute(output, true);
			}
		}
		mAudioManager.setStreamVolume(output,
				mAudioManager.getStreamMaxVolume(output) - 1, 0);
	};
}
