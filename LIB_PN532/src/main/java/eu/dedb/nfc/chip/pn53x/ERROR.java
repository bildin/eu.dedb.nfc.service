package eu.dedb.nfc.chip.pn53x;

/** PN53X errors */
public final class ERROR {
	/**
	 * Time Out, the target has not answered
	 */
	public static final byte ETIMEOUT = 0x01;
	/**
	 * A CRC error has been detected by the CIU
	 */
	public static final byte ECRC = 0x02;
	/**
	 * A Parity error has been detected by the CIU
	 */
	public static final byte EPARITY = 0x03;
	/**
	 * During an anti-collision/select operation (ISO/IEC14443-3 Type A and
	 * ISO/IEC18092 106 kbps passive mode), an erroneous Bit Count has been
	 * detected
	 */
	public static final byte EBITCOUNT = 0x04;
	/**
	 * Framing error during Mifare operation
	 */
	public static final byte EFRAMING = 0x05;
	/**
	 * An abnormal bit-collision has been detected during bit wise
	 * anti-collision at 106 kbps
	 */
	public static final byte EBITCOLL = 0x06;
	/**
	 * Communication buffer size insufficient
	 */
	public static final byte ESMALLBUF = 0x07;
	/**
	 * RF Buffer overflow has been detected by the CIU (bit BufferOvfl of the
	 * register CIU_Error)
	 */
	public static final byte EBUFOVF = 0x09;
	/**
	 * In active communication mode, the RF field has not been switched on in
	 * time by the counterpart (as defined in NFCIP-1 standard)
	 */
	public static final byte ERFTIMEOUT = 0x0A;
	/**
	 * RF Protocol error
	 */
	public static final byte ERFPROTO = 0x0B;
	/**
	 * Temperature error: the internal temperature sensor has detected
	 * overheating, and therefore has automatically switched off the antenna
	 * drivers
	 */
	public static final byte EOVHEAT = 0x0D;
	/**
	 * Internal buffer overflow
	 */
	public static final byte EINBUFOVF = 0x0E;
	/**
	 * Invalid parameter (range, format, ...)
	 */
	public static final byte EINVPARAM = 0x10;
	/**
	 * DEP Protocol: The PN53X configured in target mode does not support the
	 * command received from the initiator (the command received is not one of
	 * the following: ATR_REQ, WUP_REQ, PSL_REQ, DEP_REQ, DSL_REQ, RLS_REQ).
	 */
	public static final byte EDEPUNKCMD = 0x12;
	/**
	 * DEP Protocol, Mifare or ISO/IEC14443-4: The data format does not match to
	 * the specification. Depending on the RF protocol used, it can be: - Bad
	 * length of RF received frame, - Incorrect value of PCB or PFB, - Invalid
	 * or unexpected RF received frame, - NAD or DID incoherence.
	 */
	public static final byte EINVRXFRAM = 0x13;
	/**
	 * MIFARE: Authentication error
	 */
	public static final byte EMFAUTH = 0x14;
	/**
	 * PN533: Target or Initiator does not support NFC Secure
	 */
	public static final byte ENSECNOTSUPP = 0x18;
	/**
	 * PN533: I2C bus line is Busy. A TDA transaction is going on
	 */
	public static final byte EI2CBUSY = 0x19;
	/**
	 * ISO/IEC14443-3: UID Check byte is wrong
	 */
	public static final byte EBCC = 0x23;
	/**
	 * DEP Protocol: Invalid device state, the system is in a state which does
	 * not allow the operation
	 */
	public static final byte EDEPINVSTATE = 0x25;
	/**
	 * Operation not allowed in this configuration (host controller interface)
	 */
	public static final byte EOPNOTALL = 0x26;
	/**
	 * This command is not acceptable due to the current context of the PN53X
	 * (Initiator vs. Target, unknown target number, Target not in the good
	 * state, �)
	 */
	public static final byte ECMD = 0x27;
	/**
	 * The PN53X configured as target has been released by its initiator
	 */
	public static final byte ETGREL = 0x29;
	/**
	 * PN53X and ISO/IEC14443-3B only: the ID of the card does not match,
	 * meaning that the expected card has been exchanged with another one.
	 */
	public static final byte ECID = 0x2A;
	/**
	 * PN53X and ISO/IEC14443-3B only: the card previously activated has
	 * disappeared.
	 */
	public static final byte ECDISCARDED = 0x2B;
	/**
	 * Mismatch between the NFCID3 initiator and the NFCID3 target in DEP
	 * 212/424 kbps passive.
	 */
	public static final byte ENFCID3 = 0x2C;
	/**
	 * An over-current event has been detected
	 */
	public static final byte EOVCURRENT = 0x2D;
	/**
	 * NAD missing in DEP frame
	 */
	public static final byte ENAD = 0x2E;
}
