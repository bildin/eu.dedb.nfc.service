package eu.dedb.nfc.lib;

import java.io.IOException;
import java.util.Arrays;

import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

public class PICC_A extends PICC {

	final private PCD mReader;
	private byte[] mATQA;
	private byte[] mUID;
	private short mSAK;
	private static int handleCounter = 1;
	final private int mHandle;
	final private int mFlags;

	private byte[][] AntiCollResp = new byte[3][];
	private byte[] ChineseResp;

	private final static int STATE_IDLE = 0x00;
	private final static int STATE_READY_CL1 = 0x01;
	private final static int STATE_READY_CL2 = 0x02;
	private final static int STATE_READY_CL3 = 0x03;
	private final static int STATE_ACTIVE = 0x04;
	private final static int STATE_HALT = 0x08;
	private final static int STATE_AUTHED = 0x10;
	private final static int STATE_SELECTED = 0x20;

	private int state = STATE_IDLE;

	private final static int MAX_TRANSCEIVE_LENGTH = 62; // 62|253
	private final static int DEFAULT_TIMEOUT = 618; // 618;
	private final static int INTERNAL_TIMEOUT = 20;

	private final static int MIFARE_WRITE_PART1_TIMEOUT = 5;
	private final static int MIFARE_WRITE_PART2_TIMEOUT = 10;
	private final static int MIFARE_HW_AUTH_TIMEOUT = 10;

	private static final String TAG = "PICC_A";

	private int timeout = DEFAULT_TIMEOUT;
	private boolean isPresent = false;

	private byte[] lastAuth;
	private byte[] lastRATS;
	private boolean isBusy;

	private PICC_A(PCD pcd, int flags) {
		this.mReader = pcd;
		this.mFlags = flags;
		this.mHandle = handleCounter++;
	}

	public PICC_A(PCD pcd, byte[] atqa, byte[] uid, short sak, int flags) {
		this.mReader = pcd;
		this.mATQA = atqa;
		this.mUID = uid;
		this.mSAK = sak;
		this.mFlags = flags;
		this.mHandle = handleCounter++;
		isPresent = true;
	}

	public byte[] getATQA() {
		return mATQA;
	}

	public byte[] getUID() {
		return mUID;
	}

	public short getSAK() {
		return mSAK;
	}

	public boolean equals(Object obj) {
		if (obj instanceof PICC_A
				&& Arrays.equals(((PICC_A) obj).mATQA, this.mATQA)
				&& Arrays.equals(((PICC_A) obj).mUID, this.mUID)
				&& ((PICC_A) obj).mSAK == this.mSAK) {
			return true;
		}
		return false;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("[UID:");
		if (mUID != null) {
			for (byte b : mUID) {
				sb.append(String.format("%02X", b));
			}
		} else {
			sb.append("null");
		}

		sb.append("; ATQA:");
		if (mATQA != null) {
			for (byte b : mATQA) {
				sb.append(String.format("%02X", b));
			}
		} else {
			sb.append("null");
		}

		sb.append("; SAK:");
		sb.append(String.format("%02X", mSAK));

		sb.append("]");

		return sb.toString();
	}

	public byte[] REQA() throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[1];
			cmd[0] = (byte) 0x26;

			TransceiveResponse tresponse = mReader.transceive(cmd, 7, INTERNAL_TIMEOUT, 0);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				state = STATE_READY_CL1;
				return tresponse.getResponse();
			} else {
				state &= STATE_HALT;
				return null;
			}
		}
	}

	public byte[] WUPA() throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[1];
			cmd[0] = (byte) 0x52;

			TransceiveResponse tresponse = mReader.transceive(cmd, 7, INTERNAL_TIMEOUT, 0);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				state &= STATE_HALT;
				state |= STATE_READY_CL1;
				return tresponse.getResponse();
			} else {
				state &= STATE_HALT;
				return null;
			}
		}
	}

	public boolean CHINESE_ACTIVATION() throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[1];
			cmd[0] = (byte) 0x40;

			TransceiveResponse tresponse = mReader.transceive(cmd, 7, INTERNAL_TIMEOUT, 0);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				if (tresponse.isACK()) {
					cmd[0] = (byte) 0x43;
					tresponse = mReader.transceive(cmd, 0, INTERNAL_TIMEOUT, 0);
					errorCode = tresponse.getErrorCode();
					if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
						if (tresponse.isACK()) {
							cmd = new byte[] { 0x30, 0, 0x02, (byte) 0xA8 };
							tresponse = mReader.transceive(cmd, 0, INTERNAL_TIMEOUT, 0);
							errorCode = tresponse.getErrorCode();
							if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
								ChineseResp = tresponse.getResponse();
								if (ChineseResp.length == 18) {
									byte[] atqa = new byte[2];
									System.arraycopy(ChineseResp, 6, atqa, 0, 2);
									byte[] uid = new byte[4];
									Short sak = (short) ((short) ChineseResp[5] & 0xFF);
									System.arraycopy(ChineseResp, 0, uid, 0, 4);
									if (!isPresent) {
										mATQA = atqa;
										mUID = uid;
										mSAK = sak;
										isPresent = true;
									} else {
										PICC_A nPICC = new PICC_A(mReader,
												atqa, uid, sak, mFlags);
										if (!equals(nPICC)) {
											// TODO
											Log.v("MFRC522 PICC_A",
													"Mismatch: "
															+ this.toString()
															+ " vs. "
															+ nPICC.toString());
											state &= STATE_HALT;
											return false;
										}
									}
									state &= STATE_HALT;
									state |= STATE_ACTIVE;
									return true;
								}
							} else {
								state &= STATE_HALT;
								return false;
							}
						}
					} else {
						state &= STATE_HALT;
						return false;
					}
				}
			} else {
				state &= STATE_HALT;
				return false;
			}
		}
		return false;
	}

	public byte[] HALT() throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[4];
			cmd[0] = (byte) 0x50;
			cmd[1] = (byte) 0x00;
			cmd[2] = (byte) 0x57;
			cmd[3] = (byte) 0xCD;

			TransceiveResponse tresponse = mReader.transceive(cmd, 0, INTERNAL_TIMEOUT, 0);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				state = STATE_HALT;
				return tresponse.getResponse();
			} else {
				state = STATE_HALT;
				return null;
			}
		}
	}

	public byte[] ANTICOLLISION(int CL) throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[2];
			if (CL == 1)
				cmd[0] = (byte) 0x93;
			else if (CL == 2)
				cmd[0] = (byte) 0x95;
			else if (CL == 3)
				cmd[0] = (byte) 0x97;
			cmd[1] = (byte) 0x20;

			TransceiveResponse tresponse = mReader.transceive(cmd, 0, INTERNAL_TIMEOUT, 0);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				return tresponse.getResponse();
			} else {
				state &= STATE_HALT;
				return null;
			}
		}
	}

	public byte[] SELECT(byte[] uid_part, int CL) throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[7];
			if (CL == 1)
				cmd[0] = (byte) 0x93;
			else if (CL == 2)
				cmd[0] = (byte) 0x95;
			else if (CL == 3)
				cmd[0] = (byte) 0x97;
			cmd[1] = (byte) 0x70;
			System.arraycopy(uid_part, 0, cmd, 2, 5);
			cmd = appendCRC(cmd);

			TransceiveResponse tresponse = mReader.transceive(cmd, 0, INTERNAL_TIMEOUT, 0);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				return tresponse.getResponse();
			} else {
				state &= STATE_HALT;
				return null;
			}
		}
	}

	public byte[] RATS() throws IOException {
		return RATS((byte) 0x80);
	}

	public byte[] RATS(byte param) throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[2];
			cmd[0] = (byte) 0xE0;
			cmd[1] = param;
			// cmd = appendCRC(cmd);

			// TransceiveResponse tresponse = mReader.transceive_bytes(cmd,
			// cmd.length, INTERNAL_TIMEOUT);
			TransceiveResponse tresponse = mReader.transceive(cmd, true);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				state &= STATE_HALT;
				state |= STATE_SELECTED;
				return tresponse.getResponse();
			} else {
				state &= STATE_HALT;
				return null;
			}
		}
	}

	public byte[] DESELECT() throws IOException {
		synchronized (mReader) {
			byte[] cmd = new byte[1];
			cmd[0] = (byte) 0xC2;
			// cmd[1] = (byte) 0xE0;
			// cmd[2] = (byte) 0xB4;

			// TransceiveResponse tresponse = mReader.transceive_bytes(cmd,
			// cmd.length, INTERNAL_TIMEOUT);
			TransceiveResponse tresponse = mReader.transceive(cmd, true);
			int errorCode = tresponse.getErrorCode();
			if (errorCode == TransceiveResponse.RESULT_SUCCESS) {
				state &= STATE_HALT;
				return tresponse.getResponse();
			} else {
				state &= STATE_HALT;
				return null;
			}
		}
	}

	private static boolean checkBCC(byte[] uid_part) {
		byte check = 0x00;
		for (byte b : uid_part) {
			check ^= b;
		}
		return check == 0x00;
	}

	private byte[] appendCRC(byte[] data) {
		byte[] result = new byte[data.length + 2];
		byte[] crc = NfcUtils.CRC(data);
		System.arraycopy(data, 0, result, 0, data.length);
		System.arraycopy(crc, 0, result, data.length, 2);
		return result;
	}

	private static boolean checkCRC(byte[] data) {
		byte[] result = new byte[data.length - 2];
		System.arraycopy(data, 0, result, 0, data.length - 2);
		byte[] crc = NfcUtils.CRC(result);
		return data[data.length - 2] == crc[0]
				&& data[data.length - 1] == crc[1];
	}

	private boolean AntiCollisionLoop(byte[] atqa) throws IOException {
		if (atqa == null || atqa.length != 2) {
			return false;
		}
		byte[] uid_part;
		byte[] uid = null;
		byte[] sak;
		boolean isFull = false;
		for (int CL = 1; CL <= 3; CL++) {
			uid_part = ANTICOLLISION(CL);
			boolean checkBCC = (mFlags & PCD.IGNORE_BCC_ERROR) == 0;
			if (uid_part == null || uid_part.length != 5
					|| (checkBCC && !checkBCC(uid_part))) {
				// TODO
				break;
			}

			AntiCollResp[CL - 1] = uid_part;

			sak = SELECT(uid_part, CL);
			if (sak == null || sak.length != 3 || !checkCRC(sak)) {
				// TODO
				break;
			}

			isFull = (sak[0] & 0x04) == 0x00;

			if (CL == 1) {
				uid = new byte[4];
				System.arraycopy(uid_part, 0, uid, 0, 4);
				state &= STATE_HALT;
				state |= STATE_READY_CL2;
			} else if (CL == 2) {
				byte[] tmp = new byte[7];
				System.arraycopy(uid, 1, tmp, 0, 3);
				System.arraycopy(uid_part, 0, tmp, 3, 4);
				uid = tmp;
				state &= STATE_HALT;
				state |= STATE_READY_CL3;
			} else if (CL == 3) {
				byte[] tmp = new byte[10];
				System.arraycopy(uid, 0, tmp, 0, 7);
				System.arraycopy(uid_part, 0, tmp, 7, 4);
				uid = tmp;
				if (!isFull) {
					// TODO
				}
			}

			if (isFull) {
				if (!isPresent) {
					mATQA = atqa;
					mUID = uid;
					mSAK = (short) (sak[0] & 0xFF);
					isPresent = true;
				} else {
					PICC_A nPICC = new PICC_A(mReader, atqa, uid, sak[0],
							mFlags);
					if (!equals(nPICC)) {
						// TODO
						Log.v("MFRC522 PICC_A", "Mismatch: " + this.toString()
								+ " vs. " + nPICC.toString());
						state &= STATE_HALT;
						return false;
					}
				}
				state &= STATE_HALT;
				state |= STATE_ACTIVE;
				return true;
			}
		}
		state &= STATE_HALT;
		return false;
	}

	public static PICC_A poll(PCD pcd) throws IOException {
		return poll(pcd, 0);
	}

	public static PICC_A poll(PCD pcd, int flags) throws IOException {

		PICC_A nCard = new PICC_A(pcd, flags);

		byte[] atqa = null;

		if ((flags & PCD.ACTIVATE_CHINESE) != 0) {
			if (nCard.CHINESE_ACTIVATION()) {
				return nCard;
			}
		}

		atqa = nCard.WUPA();
		if(atqa == null)
			atqa = nCard.WUPA();
		if (nCard.AntiCollisionLoop(atqa)) {
			return nCard;
		}
		return null;
	}

	@Override
	public Tag getTag(IBinder tagService) {
		Bundle extra = new Bundle();
		extra.putShort("sak", mSAK);
		extra.putByteArray("atqa", mATQA);

		int[] techList = new int[1];
		Bundle[] techListExtras = new Bundle[1];

		if ((mSAK & 0x08) != 0) {
			// put MifareClassic tech
			techList = new int[2];
			techListExtras = new Bundle[2];
			techList[0] = 8;
			techListExtras[0] = extra;
		}

		if (mSAK == 0 && mUID[0] == 0x04) {
			// put MifareUltralight tech
			techList = new int[2];
			techListExtras = new Bundle[2];
			techList[0] = 9;
			techListExtras[0] = extra;
		}

		// put NfcA tech
		techList[techList.length - 1] = 1;
		techListExtras[techList.length - 1] = extra;

		Parcel nParcel = Parcel.obtain();
		nParcel.writeInt(mUID.length);
		nParcel.writeByteArray(mUID);
		nParcel.writeInt(techList.length);
		nParcel.writeIntArray(techList);
		nParcel.writeTypedArray(techListExtras, 0);
		nParcel.writeInt(mHandle);
		int isMock = (tagService == null) ? 1 : 0;
		nParcel.writeInt(isMock);
		if (isMock == 0) {
			nParcel.writeStrongBinder(tagService);
		}
		nParcel.setDataPosition(0);
		Tag nTag = Tag.CREATOR.createFromParcel(nParcel);
		nParcel.recycle();
		return nTag;
	}

	@Override
	public int close(int nativeHandle) {
		// TODO
		return 0;
	}

	@Override
	public int connect(int nativeHandle, int technology) {
		// TODO
		if (mHandle != nativeHandle)
			return -5;
		if (technology == 1 || technology == 9
				|| (technology == 8 && ((mSAK & 0x08) != 0))) {
			return 0;
		}
		return -1;
	}

	@Override
	public int reconnect(int nativeHandle) {
		// TODO
		return 0;
	}

	@Override
	public int[] getTechList(int nativeHandle) {
		// TODO
		return null;
	}

	@Override
	public boolean isNdef(int nativeHandle) {
		// TODO
		return false;
	}

	private boolean reActivate() {
		boolean isPresent = false;

		// try {
		// // Activate chinese as prior
		// if ((mFlags & ACTIVATE_CHINESE) != 0) {
		// if (CHINESE_ACTIVATION()) {
		// state &= STATE_HALT;
		// state |= STATE_ACTIVE;
		// return true;
		// }
		// }
		// } catch (IOException e) {
		// e.printStackTrace();
		// }

		try {
			isPresent = this.equals(mReader.poll(mFlags));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return isPresent;

		// try {
		// // Activate chinese as prior
		// if ((mFlags & ACTIVATE_CHINESE) != 0) {
		// if (CHINESE_ACTIVATION()) {
		// state &= STATE_HALT;
		// state |= STATE_ACTIVE;
		// return true;
		// }
		// }
		//
		// byte[] atqa = WUPA();
		// // retry
		// if(atqa == null)
		// atqa = WUPA();
		// if (atqa != null && Arrays.equals(atqa, mATQA)) {
		// byte[] sak;
		// for (int CL = 1; CL <= 3; CL++) {
		// if (AntiCollResp[CL - 1] == null)
		// break;
		// sak = SELECT(AntiCollResp[CL - 1], CL);
		// if (sak == null || sak.length != 3 || !checkCRC(sak)) {
		// // TODO
		// break;
		// }
		// if ((sak[0] & 0x04) == 0 && sak[0] == mSAK) {
		// state &= STATE_HALT;
		// state |= STATE_ACTIVE;
		// return true;
		// }
		// }
		// }
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// state &= STATE_HALT;
		// return false;
	}

	@Override
	public boolean isPresent(int nativeHandle) throws IOException {
		if (mHandle != nativeHandle)
			return false;
//		if (isBusy || true)
//			return isPresent;
		synchronized (mReader) {
			if (!isPresent)
				return false;

			if (state == STATE_SELECTED) {
				try {
					DESELECT();
					isPresent = reActivate();
					if (isPresent && lastRATS != null && lastRATS.length == 4) {
						RATS(lastRATS[1]);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

			} else if (state == STATE_AUTHED && ChineseResp == null) {
				// TransceiveResponse tr = mReader.transceive_bytes(lastAuth,
				// lastAuth.length, INTERNAL_TIMEOUT);
				TransceiveResponse tr = mReader.transceive(lastAuth, false);
				if (tr.getErrorCode() != TransceiveResponse.RESULT_SUCCESS) {
					isPresent = reActivate();
				}
			} else {
				byte[] cmd = new byte[2];
				cmd[0] = 0x30;
				cmd[1] = 0x00;
				// cmd[2] = 0x02;
				// cmd[3] = (byte) 0xA8;
				// TransceiveResponse tr = mReader.transceive_bytes(cmd,
				// cmd.length, INTERNAL_TIMEOUT);
				TransceiveResponse tr = mReader.transceive(cmd, false);
				if (tr.getErrorCode() != TransceiveResponse.RESULT_SUCCESS
						|| tr.isNAK()) {
					isPresent = reActivate();
				}
			}
		}
		return isPresent;
	}

	@Override
	public TransceiveResponse transceive(int nativeHandle, byte[] data, boolean raw) throws IOException {
		if (mHandle != nativeHandle || data == null || data.length == 0) {
			return null;
		}
//		isBusy = true;
		synchronized (mReader) {
			if (!isPresent)
				return new TransceiveResponse(2, null, 0);
			boolean auth = false;
			boolean rats = false;
			boolean deselect = false;
			boolean pcsc = false;

			if (pcsc) {
				return mReader.transceive(data, raw);
			}

			if (raw) {
				// check for ISO-DEP selection
				switch (data[0]) {
				case (byte) 0xE0:
					// RATS
					if (data.length == 2) { // && (state & STATE_SELECTED) ==
											// 0){
						Log.v(TAG, "<ISO-DEP RATS command>");
						rats = true;
					}
					break;
				case (byte) 0xC2:
					// DESELECT
					if (data.length == 1 && (state & STATE_SELECTED) != 0) {
						Log.v(TAG, "<ISO-DEP DESELECT command>");
						deselect = true;
					}
					break;
				default:
				}
			} else {
				// parse MIFARE proprietary protocols
				switch (data[0]) {
				case 0x60:
				case 0x61:
					if (data.length == 12) {
						auth = true;
						if (ChineseResp != null && (mFlags & PCD.AUTH_ALL) != 0) {
							Log.v(TAG, "<Mifare Auth>");
							Log.v(TAG, "Fake response (no real transaction)");
							state &= STATE_HALT;
							state |= STATE_AUTHED;
							lastAuth = data;
							return TransceiveResponse.getSuccessResponse();
						}
					}
					break;
				default:
				}
			}

			TransceiveResponse tr = mReader.transceive(data, raw);

			if (tr.getErrorCode() != TransceiveResponse.RESULT_SUCCESS
					|| tr.isNAK()) {
				isPresent = reActivate();
				if (isPresent) {
					return TransceiveResponse.getIOErrorResponse();
				} else {
					return TransceiveResponse.getTagLostResponse();
				}
			}

			if (auth) {
				state &= STATE_HALT;
				state |= STATE_AUTHED;
				lastAuth = data;
			}

			if (rats) {
				state &= STATE_HALT;
				state |= STATE_SELECTED;
				lastRATS = data;
			}

			if (deselect) {
				state = STATE_HALT;
			}
//			isBusy = false;
			return tr;
		}
	}

	@Override
	public NdefMessage ndefRead(int nativeHandle) {
		// TODO
		return null;
	}

	@Override
	public int ndefWrite(int nativeHandle, NdefMessage msg) {
		// TODO
		return 0;
	}

	@Override
	public int ndefMakeReadOnly(int nativeHandle) {
		// TODO
		return 0;
	}

	@Override
	public boolean ndefIsWritable(int nativeHandle) {
		// TODO
		return false;
	}

	@Override
	public int formatNdef(int nativeHandle, byte[] key) {
		// TODO
		return 0;
	}

	@Override
	public Tag rediscover(int nativehandle) {
		// TODO
		return null;
	}

	@Override
	public int setTimeout(int technology, int timeout) {
		if (technology == 1 || technology == 9
				|| (technology == 8 && ((mSAK & 0x08) != 0))) {
			this.timeout = timeout;
			return 0;
		}
		return -1;
	}

	@Override
	public int getTimeout(int technology) {
		if (technology == 1 || technology == 9
				|| (technology == 8 && ((mSAK & 0x08) != 0))) {
			return timeout;
		}
		return -1;
	}

	@Override
	public void resetTimeouts() {
		timeout = DEFAULT_TIMEOUT;
	}

	@Override
	public boolean canMakeReadOnly(int ndefType) {
		// TODO
		return false;
	}

	@Override
	public int getMaxTransceiveLength(int technology) {
		if (technology == 1 || technology == 9
				|| (technology == 8 && ((mSAK & 0x08) != 0))) {
			return MAX_TRANSCEIVE_LENGTH;
		}
		return 0;
	}

	@Override
	public boolean getExtendedLengthApdusSupported() {
		// TODO
		return false;
	}

	public int getHandle() {
		return mHandle;
	}
}
