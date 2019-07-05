package eu.dedb.nfc.chip.mfrc522;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class TransferBuilder {

	private ByteArrayOutputStream outputStream;
	private ByteArrayOutputStream exceptedStream;
	private ByteArrayOutputStream inputStream;

	public TransferBuilder() {
		outputStream = new ByteArrayOutputStream();
		exceptedStream = new ByteArrayOutputStream();
		inputStream = new ByteArrayOutputStream();
	}

	public TransferBuilder readReg(byte addr) {
		outputStream.write(0x80 | addr & 0x3F);
		exceptedStream.write(0x00);
		return this;
	}

	public TransferBuilder writeReg(byte addr, byte... value) {
		for(int i = 0; i < value.length; i ++) {
			outputStream.write(addr & 0x3F);
			exceptedStream.write(addr & 0x3F);
			outputStream.write(value[i]);
		}
		return this;
	}

	public TransferBuilder run(MFRC522 pcd) throws IOException {
		inputStream.reset();
		return pcd.transfer(this);
	}

	public boolean check() {
		byte[] input = inputStream.toByteArray();
		byte[] excepted = exceptedStream.toByteArray();
		if(input.length == excepted.length) {
			for(int i = 0; i < input.length; i++)
				if(excepted[i] != 0x00 && input[i] != excepted[i])
					return false;
			return true;
		}
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

	public byte[] getExcepted() {
		return exceptedStream.toByteArray();
	}

	public TransferBuilder clear() {
		outputStream.reset();
		exceptedStream.reset();
		inputStream.reset();
		return this;
	}

	public static TransferBuilder get() {
		return new TransferBuilder();
	}
}
