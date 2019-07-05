package eu.dedb.nfc.chip.pn53x;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class TransferBuilder {

	private ByteArrayOutputStream outputStream;
	private ByteArrayOutputStream inputStream;

	public TransferBuilder() {
		outputStream = new ByteArrayOutputStream();
		inputStream = new ByteArrayOutputStream();
		outputStream.write(Frame.TFI_HOST2PN53X);
	}

	public TransferBuilder write(byte... data) {
		for (byte b: data) {
			outputStream.write(b);
		}
		return this;
	}

	public TransferBuilder readReg(short addr) {
		outputStream.write(addr >> 8);
		outputStream.write(addr);
		return this;
	}

	public TransferBuilder readReg(short addr, int count) {
		for(int i = 0; i < count; i++) {
			outputStream.write(addr >> 8);
			outputStream.write(addr);
		}
		return this;
	}

	public TransferBuilder writeReg(short addr, int value) {
			outputStream.write(addr >> 8);
			outputStream.write(addr);
			outputStream.write(value);
		return this;
	}

	public TransferBuilder writeReg(short addr, byte... value) {
		for(int i = 0; i < value.length; i++) {
			outputStream.write(addr >> 8);
			outputStream.write(addr);
			outputStream.write(value[i]);
		}
		return this;
	}

	public TransferBuilder run(PN53X pcd) throws IOException {
		inputStream.reset();
	    return pcd.transfer(this);
    }

	public boolean check() {
		byte[] input = inputStream.toByteArray();
		byte[] output = outputStream.toByteArray();
		if(input.length >= 2 && input[0] == Frame.TFI_PN53X2HOST && input[1] == (output[1] + 1))
			return true;
		return false;
	}

	public boolean fill(int len, byte... data) {
		try {
			inputStream.write(Arrays.copyOf(data, len));
			return check();
		} catch (IOException e) {
		}
		return false;
	}

	public byte[] getOutput() {
		return outputStream.toByteArray();
	}

	public byte[] getInput() {
		return inputStream.toByteArray();
	}

	public TransferBuilder clear() {
		outputStream.reset();
		inputStream.reset();
		return this;
	}

	public static TransferBuilder get() {
	    return new TransferBuilder();
	}
}
