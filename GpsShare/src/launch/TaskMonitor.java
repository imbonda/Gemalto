package launch;

import utils.NetworkConnection;
import utils.RS232Connection;
import utils.StreamParser;
import utils.Utils;

public class TaskMonitor implements Runnable {

	private static final String TAG = "[TaskMonitor]: ";
	
	boolean debug;
	RS232Connection rs232conn;
	String filterDataType;
	NetworkConnection nc;
	
	public TaskMonitor(boolean debug, RS232Connection rs232conn, String filterDataType, NetworkConnection nc) {
		this.debug = debug;
		this.rs232conn = rs232conn;
		this.filterDataType = filterDataType;
		this.nc = nc;
	}
	
	public void run() {
		while (true) {
			String message = StreamParser.getNextSentence(this.rs232conn, this.filterDataType);
			this.nc.send(message);
			
			if (this.debug) {
				Utils.printWithTAG(TAG, "Sending message: " + message);
			}
		}
	}
}
