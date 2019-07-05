package eu.dedb.nfc.chip.pn53x;

/** PN53X commands */
public final class CMD {
	// Miscellaneous
	public static final byte Diagnose = (byte) 0x00;
	public static final byte GetFirmwareVersion = (byte) 0x02;
	public static final byte GetGeneralStatus = (byte) 0x04;
	public static final byte ReadRegister = (byte) 0x06;
	public static final byte WriteRegister = (byte) 0x08;
	public static final byte ReadGPIO = (byte) 0x0C;
	public static final byte WriteGPIO = (byte) 0x0E;
	public static final byte SetSerialBaudRate = (byte) 0x10;
	public static final byte SetParameters = (byte) 0x12;
	public static final byte SAMConfiguration = (byte) 0x14;
	public static final byte PowerDown = (byte) 0x16;
	// RF communication
	public static final byte RFConfiguration = (byte) 0x32;
	public static final byte RFRegulationTest = (byte) 0x58;
	// Initiator
	public static final byte InJumpForDEP = (byte) 0x56;
	public static final byte InJumpForPSL = (byte) 0x46;
	public static final byte InListPassiveTarget = (byte) 0x4A;
	public static final byte InATR = (byte) 0x50;
	public static final byte InPSL = (byte) 0x4E;
	public static final byte InDataExchange = (byte) 0x40;
	public static final byte InCommunicateThru = (byte) 0x42;
	public static final byte InDeselect = (byte) 0x44;
	public static final byte InRelease = (byte) 0x52;
	public static final byte InSelect = (byte) 0x54;
	public static final byte InAutoPoll = (byte) 0x60;
	// Target
	public static final byte TgInitAsTarget = (byte) 0x8C;
	public static final byte TgSetGeneralBytes = (byte) 0x92;
	public static final byte TgGetData = (byte) 0x86;
	public static final byte TgSetData = (byte) 0x8E;
	public static final byte TgSetMetaData = (byte) 0x94;
	public static final byte TgGetInitiatorCommand = (byte) 0x88;
	public static final byte TgResponseToInitiator = (byte) 0x90;
	public static final byte TgGetTargetStatus = (byte) 0x8A;
}