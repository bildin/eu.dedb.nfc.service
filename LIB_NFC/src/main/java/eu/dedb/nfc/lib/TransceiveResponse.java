package eu.dedb.nfc.lib;

import android.os.Parcel;
import android.os.Parcelable;

public final class TransceiveResponse implements Parcelable {

	public static final int RESULT_SUCCESS = 0;
	public static final int RESULT_FAILURE = 1;
	public static final int RESULT_TAGLOST = 2;
	public static final int RESULT_EXCEEDED_LENGTH = 3;

	public final static byte ACK = 0xA;

	public final static int TYPE_DATA = 0;
	public final static int TYPE_ACK = 1;
	public final static int TYPE_NAK = -1;

	final private int mResult;
	final private byte[] mResponseData;

	final private int mValidBits;
	final private int mResponseType;

	public TransceiveResponse(int result, byte[] data, int validBits) {
		this.mResult = result;
		this.mResponseData = data;
		// extended part
		this.mValidBits = validBits;
		if (validBits == 4 && data.length == 1) {
			if (data[0] == ACK) {
				mResponseType = TYPE_ACK;
			} else {
				mResponseType = TYPE_NAK;
			}
		} else {
			mResponseType = TYPE_DATA;
		}
	}

	protected TransceiveResponse(Parcel in) {
		mResult = in.readInt();
		mResponseData = in.createByteArray();
		mValidBits = in.readInt();
		mResponseType = in.readInt();
	}

	public static final Creator<TransceiveResponse> CREATOR = new Creator<TransceiveResponse>() {
		@Override
		public TransceiveResponse createFromParcel(Parcel in) {
			return new TransceiveResponse(in);
		}

		@Override
		public TransceiveResponse[] newArray(int size) {
			return new TransceiveResponse[size];
		}
	};

	public static TransceiveResponse getSuccessResponse() {
		return new TransceiveResponse(RESULT_SUCCESS, new byte[0], 0);
	}

	public static TransceiveResponse getIOErrorResponse() {
		return new TransceiveResponse(RESULT_FAILURE, null, 0);
	}

	public static TransceiveResponse getTagLostResponse() {
		return new TransceiveResponse(RESULT_TAGLOST, null, 0);
	}

	public int getErrorCode() {
		return mResult;
	}

	public byte[] getResponse() {
		return mResponseData;
	}

	public int getValidBits() {
		return mValidBits;
	}

	public boolean isACK() {
		return mResponseType == TYPE_ACK;
	}

	public boolean isNAK() {
		return mResponseType == TYPE_NAK;
	}

	public boolean isDATA() {
		return mResponseType == TYPE_DATA;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(mResult);
		if (mResult == RESULT_SUCCESS) {
			dest.writeInt(mResponseData.length);
			dest.writeByteArray(mResponseData);
		}
	}

}
