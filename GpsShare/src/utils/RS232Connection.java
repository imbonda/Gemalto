package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.CommConnection;
import javax.microedition.io.Connector;

public class RS232Connection {

	private static final String TAG = "[RS232Connection]: ";
	
	CommConnection  commConn;
	InputStream     inStream;
	OutputStream    outStream;
	
	public RS232Connection(String comStr) throws IOException{
		Utils.printWithTAG(TAG, "Available COM-Ports: " + System.getProperty("microedition.commports"));
		commConn = (CommConnection)Connector.open(comStr);
		Utils.printWithTAG(TAG, "CommConnection(" + comStr + ") opened");
		Utils.printWithTAG(TAG, "Real baud rate: " + commConn.getBaudRate());
		inStream  = commConn.openInputStream();
		outStream = commConn.openOutputStream();
		Utils.printWithTAG(TAG, "InputStream and OutputStream opened");
	}
	
	public void close() { 
	    try {
	    	inStream.close();
	    	outStream.close();
	    	commConn.close();
	    	Utils.printWithTAG(TAG, "Streams and connection closed");
	    } catch(IOException e) {
	    	Utils.printWithTAG(TAG, e.getMessage());
	    }
	}
	
	public int read() throws IOException {
		return inStream.read();
	}
}
