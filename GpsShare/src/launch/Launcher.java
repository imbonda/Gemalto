package launch;

import java.io.IOException;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import utils.NetworkConnection;
import utils.RS232Connection;
import utils.TransmitRateRegulator;
import utils.Utils;

public class Launcher extends MIDlet {
	
	private static final String TAG = "[Launcher]: ";
	private static final int RADIX = 10;

	boolean debugMode;
	String deviceId;
	String netConnProfile;
	String netUrl;
	String netDataParams;
	String rs232ComStr;
	String rs232DataFilter;
	long transmitTimeInterval;
	long transmitCountInterval;
	RS232Connection rs232conn;
	
	/**
	 * Default constructor.
	 */
	public Launcher() {
		this.debugMode = false;
		this.netConnProfile = null;
		this.netUrl = null;
		this.netDataParams = null;
		this.rs232DataFilter = null;
		this.rs232ComStr = null;
		this.rs232conn = null;
	}

	protected void startApp() throws MIDletStateChangeException {
		Utils.printWithTAG(TAG, "startup()");
		loadSettings();
		if (this.debugMode) {
			printSettings();
		}
		
		try {
			this.rs232conn = new RS232Connection(this.rs232ComStr);
		}
		catch (IOException e) {
			Utils.printWithTAG(TAG, e.getMessage());
			destroyApp(true);
			return;
		}
		
		NetworkConnection nc = new NetworkConnection(this.debugMode, this.netConnProfile, this.netUrl, this.netDataParams);
		TransmitRateRegulator trr = new TransmitRateRegulator(this.transmitTimeInterval, this.transmitCountInterval);
		
		TaskMonitor tm = new TaskMonitor(
				this.debugMode,
				this.deviceId,
				this.rs232DataFilter,
				this.rs232conn,
				nc,
				trr);
		Thread t = new Thread(tm);
		t.start();
	}
	
	/**
	 * Loading the settings from JAD file.
	 */
	private void loadSettings() {
		this.deviceId = getAppProperty("DEVICE_ID");
		this.netUrl = getAppProperty("NET_URL");
		this.netDataParams = getAppProperty("NET_DATA_PARAMS");
		this.netConnProfile = getAppProperty("NET_CONN_PROFILE");
		this.rs232DataFilter = getAppProperty("RS232_DATA_FILTER");
		this.rs232ComStr = getAppProperty("RS232_COM");
		
		String debugStr = getAppProperty("DEBUG_MODE");
		String timeIntervalStr = getAppProperty("TRANSMIT_INTERVAL_TIME");
		String countIntervalStr = getAppProperty("TRANSMIT_INTERVAL_COUNT");
		
		if (debugStr.equals("true")) {
			// Debug mode is on.
			this.debugMode = true;
		}
		if (this.deviceId == null || this.deviceId.equals("")) {
			// Device id is not set.
			this.deviceId = "NAN";
		}
		
		// Set time interval.
		try {
			this.transmitTimeInterval = Long.parseLong(timeIntervalStr, RADIX);
		}
		catch (NumberFormatException e) {
			this.transmitTimeInterval = 0;
		}
		// Set count interval.
		try {
			this.transmitCountInterval = Long.parseLong(countIntervalStr, RADIX);
		}
		catch (NumberFormatException e) {
			this.transmitCountInterval = 0;
		}
	}
	
	private void printSettings() {
		Utils.printWithTAG(TAG, "------------ Settings from JAD ------------");
		Utils.printWithTAG(TAG, "JAD debug: " + this.debugMode);
		Utils.printWithTAG(TAG, "JAD id: " + this.deviceId);
		Utils.printWithTAG(TAG, "JAD profile: " + this.netConnProfile);
		Utils.printWithTAG(TAG, "JAD url: " + this.netUrl);
		Utils.printWithTAG(TAG, "JAD post params: " + this.netDataParams);
		Utils.printWithTAG(TAG, "JAD RS232 com: " + this.rs232ComStr);
		Utils.printWithTAG(TAG, "JAD NMEA filter: " + this.rs232DataFilter);
		Utils.printWithTAG(TAG, "JAD Transmittion time interval: " + this.transmitTimeInterval);
		Utils.printWithTAG(TAG, "JAD Transmittion count interval: " + this.transmitCountInterval);
		Utils.printWithTAG(TAG, "------------------- end -------------------");
	}
	
	protected void pauseApp() {
		if (this.debugMode) {
			Utils.printWithTAG(TAG, "pauseApp()");
		}
	}
	
	protected void destroyApp(boolean unconditional)
			throws MIDletStateChangeException {
		if (this.debugMode) {
			Utils.printWithTAG(TAG, "destroyApp()");
		}
		if (this.rs232conn != null) {
			this.rs232conn.close();
		}
		notifyDestroyed();
	}

}
