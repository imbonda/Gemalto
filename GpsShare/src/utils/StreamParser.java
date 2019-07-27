package utils;

import java.io.IOException;

public class StreamParser {

	private static final String TAG = "[StreamParser]: ";
	
	/**
	 * Iterating over the NMEA input stream until coming across the key message.
	 *  
	 * @param rs232		The RS232 connection to read and filter from.
	 * @param dataType	The data type filer (e.g. "GNRMC")
	 * @return
	 */
	public static String getNextSentence(RS232Connection rs232, String dataType) {
		int dataTypeLength = dataType.length();
		// Actual maximum message length is 80 plus the line termination.
		StringBuffer recorded = new StringBuffer(100);
		boolean isRecording = false;
		boolean filterPassed = false;
		int ch = -1;
		while (true) {
			try {
				ch = rs232.read();
			}
			catch (IOException e) {
				isRecording = false;
				// Clear recorded sentence.
				recorded.setLength(0);
				// Debug print.
				Utils.printWithTAG(TAG, e.getMessage());
				continue;
			}
			
			if (ch == '$') {
				if (filterPassed) {
					return recorded.toString();
				}
				// Clear recorded sentence.
				recorded.setLength(0);
				// Sentence beginning.
				isRecording = true;
				continue;
			}
			
			if (!isRecording) {
				continue;
			}

			// If we got so far, then we are recording a new sentence.
			if (recorded.length() == dataTypeLength) {
				// Check if matching filter data type.
				if (!recorded.toString().equals(dataType)) {
					// The sentence did not begin with the filter..
					isRecording = false;
					continue;
				}
				else {
					// The sentence begins with the filter, now we just have to record it until it ends.
					filterPassed = true;
				}
			}
			// Notice skip of carriage return and new line.
			if (ch != '\r' && ch != '\n') {
				recorded.append((char)ch);
			}
			if (ch == '\n') {
				// Sentence ending.
				if (filterPassed) {
					return recorded.toString();
				}
				else {
					// Sentence ended before could match against the filter.
					isRecording = false;
				}
			}
		}
	}
}
