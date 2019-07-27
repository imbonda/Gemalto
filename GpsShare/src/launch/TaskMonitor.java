package launch;

import utils.NetworkConnection;
import utils.RS232Connection;
import utils.StreamParser;
import utils.TransmitRateRegulator;
import utils.Utils;

public class TaskMonitor implements Runnable {

	private static final String TAG = "[TaskMonitor]: ";
	
	boolean debug;
	RS232Connection rs232conn;
	String filterDataType;
	NetworkConnection nc;
	TransmitRateRegulator trr;
	
	public TaskMonitor(boolean debug, RS232Connection rs232conn, String filterDataType, NetworkConnection nc,
			TransmitRateRegulator trr) {
		this.debug = debug;
		this.rs232conn = rs232conn;
		this.filterDataType = filterDataType;
		this.nc = nc;
		this.trr = trr;
	}
	
	public void run() {
		while (true) {
			String message = StreamParser.getNextSentence(this.rs232conn, this.filterDataType);
			message = Utils.reformatGNRMC(message);
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
		
		this.trr.recordTransmit(isTransmitAllowed);
	}
}
