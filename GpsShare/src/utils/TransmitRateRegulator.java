package utils;

public class TransmitRateRegulator {

	private static final int RADIX = 10;
	private static final int MILLISECONDS = 1000;
	
	private long timeInterval;
	private long countInterval;
	private long latestTransmissionTime;
	private long countSinceTransmission;
	private boolean noTransmissionRecords;
	
	public TransmitRateRegulator(String timeInterval, String countInterval) {
		// Set time interval.
		try {
			this.timeInterval = Long.parseLong(timeInterval, RADIX);
		}
		catch (NumberFormatException e) {
			this.timeInterval = 0;
		}
		// Set count interval.
		try {
			this.countInterval = Long.parseLong(countInterval, RADIX);
		}
		catch (NumberFormatException e) {
			this.countInterval = 0;
		}
		
		this.latestTransmissionTime = 0;
		this.countSinceTransmission = 0;
		this.noTransmissionRecords = true;
		
	}
	
	/**
	 * Decision is based on time interval if present, otherwise taking count interval into account.
	 * 
	 * @return	Whether or not can transmit network data.
	 */
	public boolean isTransmitAllowed() {
		if (this.noTransmissionRecords) {
			// This is the first transmission.
			return true;
		}
		if (this.timeInterval > 0) {
			long timeSinceTransmission = System.currentTimeMillis() - latestTransmissionTime;
			long timeInSeconds = timeSinceTransmission / MILLISECONDS;
			return timeInSeconds >= this.timeInterval;
		}
		return this.countSinceTransmission >= this.countInterval - 1;
	}
	
	/**
	 * Updating data for next decision making calls.
	 * 
	 * @param wasAllowed	Whether or not the last transmission was allowed.
	 */
	public void recordTransmit(boolean wasAllowed) {
		if (wasAllowed) {
			this.countSinceTransmission = 0;
			this.latestTransmissionTime = System.currentTimeMillis();
			this.noTransmissionRecords = false;
		}
		else {
			this.countSinceTransmission += 1;
		}
	}
}
