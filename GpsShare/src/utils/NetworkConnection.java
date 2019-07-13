package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;


public class NetworkConnection {

	private static final String TAG = "[NetworkConnection]: ";

	boolean debug;
	String profile;
	String url;
	String params;
    
	/**
	 * Default constructor.
	 */
	public NetworkConnection(boolean debug, String profile, String url, String paramsFormat) {
		this.debug = debug;
		this.profile = profile;
		this.url = url;
		if (paramsFormat != null) {
			this.params = paramsFormat;
		}
		else {
			this.params = "";
		}
	}
	
	public String openParamString() {
		return this.url + ";" + this.profile;
	}
	
	private String formatMessage(String message) {
		if (message != null) {
			return this.params + message;
		}
		return message;
	}
	
	public HttpConnection getConnection() throws IOException {
		/* Open all */
        String openParm = this.openParamString();
        if (this.debug) {
        	Utils.printWithTAG(TAG, "Connector open: " + openParm);
        }
        return (HttpConnection)Connector.open(openParm);        	
	}
	
	private void closeAll(HttpConnection conn, InputStream in, OutputStream out) {
		if (in != null) {
			try {
				in.close();
			} catch (IOException e) {
				Utils.printWithTAG(TAG, e.getMessage());
			}
		}
		if (out != null) {
			try {
				out.close();
			} catch (IOException e) {
				Utils.printWithTAG(TAG, e.getMessage());
			}
		}
		try {
			if (conn != null) {
				conn.close();
			}
		} catch (IOException e) {
			Utils.printWithTAG(TAG, e.getMessage());
		}
	}
	
	public void send(String message) {
		HttpConnection conn = null;
		InputStream inStream = null; 
	    OutputStream outStream = null;
		
		// Sending POST request.
		try {
			conn = getConnection();
			// Set the request method and headers
			conn.setRequestMethod(HttpConnection.POST);
			conn.setRequestProperty(
					"User-Agent",
					"Profile/MIDP-2.0 Configuration/CLDC-1.0");
			conn.setRequestProperty(
					"Content-Language",
					"en-US");
			conn.setRequestProperty(
					"Content-Type",
					"application/x-www-form-urlencoded");
			outStream = conn.openOutputStream();
			String data = formatMessage(message);
			outStream.write(data.getBytes());
		    // Optional, getResponseCode will flush
			outStream.flush();
            
			if (this.debug) {
			    /* Read Data */
			    inStream = conn.openInputStream();
	            StringBuffer str = new StringBuffer();
	            int ch;
	            while ((ch = inStream.read()) != -1) {
	                str.append((char)ch);
	            }
	            Utils.printWithTAG(TAG, "received: " + str);
			}
		}
		catch (IOException e) {
			Utils.printWithTAG(TAG, e.getMessage());
		}
		finally {
			closeAll(conn, inStream, outStream);
		}
	}
}
