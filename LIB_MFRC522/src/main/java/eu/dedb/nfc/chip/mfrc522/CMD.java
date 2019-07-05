package eu.dedb.nfc.chip.mfrc522;

/** MFRC522 commands */
public final class CMD {
	/** no action, cancels current command execution */
	public static final byte Idle = 0x00;
	/** stores 25 bytes into the internal buffer */
	public static final byte Mem = 0x01;
	/** generates a 10-byte random ID number */
	public static final byte RandomID = 0x02;
	/** activates the CRC coprocessor or performs a self test */
	public static final byte CalcCRC = 0x03;
	/** transmits data from the FIFO buffer */
	public static final byte Transmit = 0x04;
	/**
	 * no command change, can be used to modify the CommandReg register bits
	 * without affecting the command, for example, the PowerDown bit
	 */
	public static final byte NoCmdChange = 0x07;
	/** activates the receiver circuits */
	public static final byte Receive = 0x08;
	/**
	 * transmits data from FIFO buffer to antenna and automatically activates
	 * the receiver after transmission
	 */
	public static final byte Transceive = 0x0C;
	/** performs the MIFARE standard authentication as a reader */
	public static final byte MFAuthent = 0x0E;
	/** resets the MFRC522 */
	public static final byte SoftReset = 0x0F;
}