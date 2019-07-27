package utils;


public class Utils {

	/**
	 * Output for debugging.
	 * 
	 * @param tag		A unique string identifier for the message (each class should have it's own).
	 * @param message	Message to output.
	 */
	public static void printWithTAG(String tag, String message) {
		System.out.println(tag + message);
	}
	
	/**
	 * Reformatting a GNRMC strings to allow sorting by time stamps.
	 *  
	 * @param data	A NMEA data string of type GNRMC.
	 * @return		A reformatted copy of the given string with (allowing to sort by time-stamp field).
	 */
	public static String reformatGNRMC(String data) {
		String secondItem = null;
		StringBuffer reformatted = new StringBuffer(data.length());
		int itemNumber = 1;
		int splitIndex;
		while ((splitIndex = data.indexOf(',')) != -1) {
			String item = data.substring(0, splitIndex);
			if (itemNumber == 2) {
				secondItem = item;
			}
			if (itemNumber == 10) {
				item = Utils.reverseDate(item);
				if (item != null && secondItem != null) {
					item = item + secondItem;
				}
			}
			reformatted.append(item);
			reformatted.append(',');
			++itemNumber;
			// Clip the data object for next iteration.
			data = data.substring(splitIndex + 1);
		}
		// Add remaining data.
		reformatted.append(data);
		return reformatted.toString();
	}
	
	private static String reverseDate(String date) {
		StringBuffer reversed = new StringBuffer(date.length());
		for (int i = date.length() - 2; i >= 0; i -= 2) {
			reversed.append(date.substring(i, i + 2));
		}
		return reversed.toString();
	}
}
