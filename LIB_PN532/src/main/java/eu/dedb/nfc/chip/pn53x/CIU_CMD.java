package eu.dedb.nfc.chip.pn53x;

public final class CIU_CMD {
	/**
	 * No action; cancels current command execution.
	 */
	public static final byte Idle = 0x00;
	/**
	 * Configures the CIU for FeliCa, MIFARE and NFCIP-1 communication.
	 */
	public static final byte Config = 0x01;
	/**
	 * Generates 10-byte random ID number
	 */
	public static final byte RandomID = 0x02;
	/**
	 * Activates the CRC co-processor or perform self-test
	 */
	public static final byte CalcCRC = 0x03;
	/**
	 * Transmits data from the FIFO buffer
	 */
	public static final byte Transmit = 0x04;
	/**
	 * No command change. This command can be used to modify different bits in
	 * the CIU_Command register without touching the command. e.g. Power-down
	 * bit.
	 */
	public static final byte NoCmdChange = 0x07;
	/**
	 * Activates the receiver circuitry.
	 */
	public static final byte Receive = 0x08;
	/**
	 * Activates the self-test
	 */
	public static final byte SelfTest = 0x09;
	/**
	 * If bit Initiator in the register CIU_Control is set to logic 1: Transmits
	 * data from FIFO buffer to the antenna and activates automatically the
	 * receiver after transmission is finished. If bit Initiator in the register
	 * CIU_Control is set to logic 0: Receives data from antenna and activates
	 * automatically the transmitter after reception.
	 */
	public static final byte Transceive = 0x0C;
	/**
	 * Handles FeliCa polling (Card operating mode only) and MIFARE
	 * anticollision (Card operating mode only)
	 */
	public static final byte AutoColl = 0x0D;
	/**
	 * Performs the MIFARE 1 KB or MIFARE 4 KB emulation authentication in
	 * MIFARE Reader/Writer mode only
	 */
	public static final byte MFAuthent = 0x0E;
	/**
	 * Resets the CIU
	 */
	public static final byte SoftReset = 0x0F;
}
