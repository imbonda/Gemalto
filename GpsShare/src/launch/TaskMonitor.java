package launch;

import utils.NetworkConnection;
import utils.RS232Connection;
import utils.StreamParser;
import utils.TransmitRateRegulator;
import utils.Utils;

public class TaskMonitor implements Runnable {

	private static final String TAG = "[TaskMonitor]: ";
	
	boolean debug;
	String deviceId;
	String filterDataType;
	RS232Connection rs232conn;
	NetworkConnection nc;
	TransmitRateRegulator trr;
	
	public TaskMonitor(boolean debug, String deviceId, String filterDataType,
			RS232Connection rs232conn, NetworkConnection nc, TransmitRateRegulator trr) {
		this.debug = debug;
		this.deviceId = deviceId;
		this.filterDataType = filterDataType;
		this.rs232conn = rs232conn;
		this.nc = nc;
		this.trr = trr;
	}
	
	public void run() {
		while (true) {
			String message = StreamParser.getNextSentence(this.rs232conn, this.filterDataType);
			message = Utils.reformatGNRMC(message, this.deviceId);
			transmit(message);
		}
	}
	
	private void transmit(String message) {
		boolean isTransmitAllowed = this.trr.isTransmitAllowed();
		if (isTransmitAllowed) {
			// Transmitting data.
			this.nc.send(message);
			
			if (this.debug) {
				Utils.printWithTAG(TAG, "Sending message: " + message);
			}
		}
		else {
			if (this.debug) {
				Utils.printWithTAG(TAG, "Skipping message transmission: " + message);
			}
		}
		
		this.trr.recordTransmit(isTransmitAllowed);
	}
}
