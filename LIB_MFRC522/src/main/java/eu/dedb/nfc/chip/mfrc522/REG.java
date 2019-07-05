package eu.dedb.nfc.chip.mfrc522;

/** MFRC522 register addresses */
public final class REG {
	// Command and status
	/** reserved for future use */
	public static final byte Reserved_00 = 0x00;
	/**
	 * starts and stops command execution
	 * <p>
	 * {@link eu.dedb.nfc.chip.mfrc522.CMD}
	 */
	public static final byte CommandReg = 0x01;
	/** enable and disable interrupt request control bits */
	public static final byte ComIEnReg = 0x02;
	/** enable and disable interrupt request control bits */
	public static final byte DivIEnReg = 0x03;
	/** interrupt request bits */
	public static final byte ComIrqReg = 0x04;
	/** interrupt request bits */
	public static final byte DivIrqReg = 0x05;
	/** error bits showing the error status of the last command executed */
	public static final byte ErrorReg = 0x06;
	/** communication status bits */
	public static final byte Status1Reg = 0x07;
	/** receiver and transmitter status bits */
	public static final byte Status2Reg = 0x08;
	/** input and output of 64 byte FIFO buffer */
	public static final byte FIFODataReg = 0x09;
	/** number of bytes stored in the FIFO buffer */
	public static final byte FIFOLevelReg = 0x0A;
	/** level for FIFO underflow and overflow warning */
	public static final byte WaterLevelReg = 0x0B;
	/** miscellaneous control registers */
	public static final byte ControlReg = 0x0C;
	/** adjustments for bit-oriented frames */
	public static final byte BitFramingReg = 0x0D;
	/** bit position of the first bit-collision detected on the RF interface */
	public static final byte CollReg = 0x0E;
	/** reserved for future use */
	public static final byte Reserved_0F = 0x0F;

	// Command
	/** reserved for future use */
	public static final byte Reserved_10 = 0x10;
	/** defines general modes for transmitting and receiving */
	public static final byte ModeReg = 0x11;
	/** defines transmission data rate and framing */
	public static final byte TxModeReg = 0x12;
	/** defines reception data rate and framing */
	public static final byte RxModeReg = 0x13;
	/** controls the logical behavior of the antenna driver pins TX1 and TX2 */
	public static final byte TxControlReg = 0x14;
	/** controls the setting of the transmission modulation */
	public static final byte TxASKReg = 0x15;
	/** selects the internal sources for the antenna driver */
	public static final byte TxSelReg = 0x16;
	/** selects internal receiver settings */
	public static final byte RxSelReg = 0x17;
	/** selects thresholds for the bit decoder */
	public static final byte RxThresholdReg = 0x18;
	/** defines demodulator settings */
	public static final byte DemodReg = 0x19;
	/** reserved for future use */
	public static final byte Reserved_1A = 0x1A;
	/** reserved for future use */
	public static final byte Reserved_1B = 0x1B;
	/** controls some MIFARE communication transmit parameters */
	public static final byte MfTxReg = 0x1C;
	/** controls some MIFARE communication receive parameters */
	public static final byte MfRxReg = 0x1D;
	/** reserved for future use */
	public static final byte Reserved_1E = 0x1E;
	/** selects the speed of the serial UART interface */
	public static final byte SerialSpeedReg = 0x1F;

	// Configuration
	/** reserved for future use */
	public static final byte Reserved_20 = 0x20;
	/** shows the LSB value of the CRC calculation */
	public static final byte CRCResultReg_H = 0x21;
	/** shows the MSB value of the CRC calculation */
	public static final byte CRCResultReg_L = 0x22;
	/** reserved for future use */
	public static final byte Reserved_23 = 0x23;
	/** controls the ModWidth setting */
	public static final byte ModWidthReg = 0x24;
	/**  */
	public static final byte Reserved_25 = 0x25;
	/** configures the receiver gain */
	public static final byte RFCfgReg = 0x26;
	/**
	 * selects the conductance of the antenna driver pins TX1 and TX2 for
	 * modulation
	 */
	public static final byte GsNReg = 0x27;
	/**
	 * defines the conductance of the p-driver output during periods of no
	 * modulation
	 */
	public static final byte CWGsPReg = 0x28;
	/**
	 * defines the conductance of the p-driver output during periods of
	 * modulation
	 */
	public static final byte ModGsPReg = 0x29;
	/** defines settings for the internal timer */
	public static final byte TModeReg = 0x2A;
	/** defines settings for the internal timer */
	public static final byte TPrescalerReg = 0x2B;
	/** defines the 16-bit timer reload value */
	public static final byte TReloadReg_H = 0x2C;
	/** defines the 16-bit timer reload value */
	public static final byte TReloadReg_L = 0x2D;
	/** shows the 16-bit timer value */
	public static final byte TCounterValReg_H = 0x2E;
	/** shows the 16-bit timer value */
	public static final byte TCounterValReg_L = 0x2F;

	// Test register
	/** reserved for future use */
	public static final byte Reserved_30 = 0x30;
	/** general test signal configuration */
	public static final byte TestSel1Reg = 0x31;
	/** general test signal configuration and PRBS control */
	public static final byte TestSel2Reg = 0x32;
	/** enables pin output driver on pins D1 to D7 */
	public static final byte TestPinEnReg = 0x33;
	/** defines the values for D1 to D7 when it is used as an I/O bus */
	public static final byte TestPinValueReg = 0x34;
	/** shows the status of the internal test bus */
	public static final byte TestBusReg = 0x35;
	/** controls the digital self test */
	public static final byte AutoTestReg = 0x36;
	/** shows the software version */
	public static final byte VersionReg = 0x37;
	/** controls the pins AUX1 and AUX2 */
	public static final byte AnalogTestReg = 0x38;
	/** defines the test value for TestDAC1 */
	public static final byte TestDAC1Reg = 0x39;
	/** defines the test value for TestDAC2 */
	public static final byte TestDAC2Reg = 0x3A;
	/** shows the value of ADC I and Q channels */
	public static final byte TestADCReg = 0x3B;
	/** reserved for production tests */
	public static final byte Reserved_3C = 0x3C;
	/** reserved for production tests */
	public static final byte Reserved_3D = 0x3D;
	/** reserved for production tests */
	public static final byte Reserved_3E = 0x3E;
	/** reserved for production tests */
	public static final byte Reserved_3F = 0x3F;
}
