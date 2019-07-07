//package utils;
//import com.cinterion.misc.Watchdog2;
//import javax.microedition.midlet.MIDlet;
//import javax.microedition.midlet.MIDletStateChangeException;
//
//public class Main extends MIDlet {
//	public static boolean continueEx = false; // Thread.stop() is depreciated so
//												// use while() loop and boolean
//												// to stop
//	private static Thread myRunTimeThread = null;
//	private static final int RESTARTDELAY = 239; // >0 => The number of seconds
//													// after which the Watchdog
//													// will trigger.
//
//	private static String connParamsSocket = null; // read from .JAD file
//	private static String locationProviderKey = null; // read from .JAD file
//	private static String gpioInput = null; // read from .JAD file
//	private static String gpioOutput = null; // read from .JAD file
//	private static String subscriberPhoneNumber1 = null; // read from .JAD file
//	private static String subscriberPhoneNumber2 = null; // read from .JAD file
//	private static String subscriberPhoneNumber3 = null; // read from .JAD file
//	private static int allowNoShieldMode = 1; // read from .JAD file 1 =>
//												// continue even if Shield is
//												// missing
//	private static int allowOffLineMode = 1; // read from .JAD file 1 =>
//												// continue even if SIM not
//												// inserted
//
//	public Main() {
//		System.out.println("");
//		System.out.println("[" + this.getClass().getName() + "]: Constructor");
//	}
//
//	protected void startApp() throws MIDletStateChangeException {
//		System.out.println("[Main]: startApp");
//		System.out.println("");
//
//		System.out.println("Alarm via SMS demo");
//		System.out.println("");
//
//		if (AtcHandler.getInstance().resourcesAllocatedSuccessfully()) {
//			AtcHandler.getInstance().start();
//			AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
//			AtcHandler.getInstance().sendATC(
//					"AT^SCFG=\"Userware/Watchdog\",\"0\"\r");
//			System.out
//					.println("[Main]: Watchdog will do nothing if triggered.");
//		}
//
//		if (HardwareManager.getInstance().resourcesAllocatedSuccessfully()) {
//			HardwareManager.getInstance().start();
//			System.out
//					.println("[Main]: Hardware interfaces allocated successfully.");
//		}
//
//		System.out.println("");
//
//		readSettingsFromJADFile(); // Here we gather any user specific values
//									// from JAD file
//
//		if (I2CHandler.getInstance().resourcesAllocatedSuccessfully()) {
//			I2CHandler.getInstance().start();
//			System.out.println("[Main]: I2C bus ready.");
//		} else {
//			System.out.println("[Main]: Warning I2C Handler not ready.");
//		}
//
//		// Configuring an input, the module input should be set first, followed
//		// by the level shifter input.
//		if (gpioInput != null) {
//			System.out.println("[Main]: Changing GPIO direction: " + gpioInput
//					+ " -> input");
//			ButtonHandler.getInstance().start(gpioInput);
//			HardwareManager.setGPIOasInput(gpioInput);
//		} else {
//			ButtonHandler.getInstance().start();
//		}
//
//		// Configuring a Java Terminal output, the level shifter output should
//		// be set first, followed by the module output configuration.
//		if (gpioOutput != null) {
//			System.out.println("[Main]: Changing GPIO direction: " + gpioOutput
//					+ " -> output");
//			HardwareManager.setGPIOasOutput(gpioOutput);
//			GpioHandler.getInstance().start(gpioOutput);
//		} else {
//			GpioHandler.getInstance().start();
//		}
//
//		if (ExternalDevice.getInstance().resourcesAllocatedSuccessfully()) {
//			ExternalDevice.getInstance().start(this);
//		} else {
//			if (allowNoShieldMode == 1) {
//				ExternalDevice.getInstance().start(this);
//			}
//			System.out
//					.println("[Main]: Warning ExternalDevice failed to respond.");
//		}
//		System.out.println("");
//
//		// Although it seems like these applyHost() calls would be good to put
//		// in the constructor, you can't do so.
//		// An object is only seen by the rest of the system once its constructor
//		// has finished.
//		// Netbeans would warn saying "leaking .this in the constructor".
//
//		if (ButtonHandler.getInstance().resourcesAllocatedSuccessfully()
//				&& GpioHandler.getInstance().resourcesAllocatedSuccessfully()
//				&& AtcHandler.getInstance().resourcesAllocatedSuccessfully()) {
//
//			// register this object with the Singleton Handlers, so that they
//			// can see destroyApp()
//			SmsHandler.getInstance().start();
//			SubscriptionHandler.getInstance().start();
//			if (subscriberPhoneNumber1 != null) {
//				SubscriptionHandler.getInstance().addSubscriberBySMS(
//						subscriberPhoneNumber1);
//				SubscriptionHandler.getInstance().editSubscriberRate(
//						subscriberPhoneNumber1, "RATE 59", false);
//			}
//			if (subscriberPhoneNumber2 != null) {
//				SubscriptionHandler.getInstance().addSubscriberBySMS(
//						subscriberPhoneNumber2);
//				SubscriptionHandler.getInstance().editSubscriberRate(
//						subscriberPhoneNumber2, "RATE 59", false);
//			}
//			if (subscriberPhoneNumber3 != null) {
//				SubscriptionHandler.getInstance().addSubscriberBySMS(
//						subscriberPhoneNumber3);
//				SubscriptionHandler.getInstance().editSubscriberRate(
//						subscriberPhoneNumber3, "RATE 59", false);
//			}
//
//			GpioHandler.getInstance().setLedState(GpioHandler.LED_OFF);
//
//			AtcHandler.getInstance().sendATC("AT+CNUM\r"); // Probably a good
//															// idea if I know my
//															// own number...
//
//			System.out.println("[Main]: Ready.");
//
//			if (ConnectionManager.getInstance().succesfulNetworkRegistration()) {
//				// Watchdog timer is started now so we restart in a few minutes
//				// if nothing is good
//				ConnectionManager.getInstance().start(); // Starts HouseKeeping
//															// Thread too
//
//				// Right now we are in a good CREG state so assume Home or
//				// Roaming registered CS state is achieved
//				continueEx = true;
//
//				if (myRunTimeThread == null) {
//					myRunTimeThread = new Main.MyRuntimeThread();
//					myRunTimeThread.start();
//				}
//
//				System.out.println("");
//				System.out.println("[Main]: Runtime thread started.");
//
//			} else {
//				System.out
//						.println("[Main]: Warning could not get a successful network registration.");
//			}
//		} else {
//			System.out
//					.println("[Main]: Warning one of ButtonHandler, GpioHandler or AtcHandler was not ready.");
//		}
//
//		if (continueEx == false) { // We did not achieve network registration,
//									// so what to do next
//			if (allowOffLineMode == 0) { // We ought to restart the module and
//											// retry, or...
//				Watchdog2 tmpwd = new Watchdog2();
//				if (tmpwd != null) { // Valid values are 0 to switch off, or
//										// between 10 and 300 to set watchdog
//										// time-out.
//					AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
//					AtcHandler.getInstance().sendATC(
//							"AT^SCFG=\"Userware/Watchdog\",\"1\"\r"); // The
//																		// Watchdog2
//																		// will
//																		// restart
//																		// the
//																		// system
//
//					try {
//						tmpwd.start(RESTARTDELAY);
//					} // The number of seconds after which the watchdog will
//						// trigger.
//					catch (Exception e) {
//						System.out.println("[Main]: Watchdog Start Exception "
//								+ e.toString());
//					}
//
//					System.out
//							.println("[Main]: Watchdog will restart the system in "
//									+ RESTARTDELAY + " seconds.");
//				}
//				try {
//					destroyApp(true);
//				} catch (Exception e) {
//					System.out.println("[Main]: Start-up failed "
//							+ e.toString());
//				}
//			} else { // We can just accept the offline working state and relax
//				AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
//				AtcHandler.getInstance().sendATC(
//						"AT^SCFG=\"Userware/Watchdog\",\"0\"\r");
//				System.out
//						.println("[Main]: Now in offline mode with no cellular comms.");
//				System.out
//						.println("[Main]: Will make no attempt to re-attach or re-register.");
//				System.out.println("[Main]: WatchDog will not restart module.");
//				System.out.println("");
//				continueEx = true; // let all the offline tasks start.
//			}
//		}
//
//		SmsHandler.getInstance().purgeAllSavedSmsMessages();
//
//		System.out.println("[Main]: startApp complete.");
//	}
//
//	protected void pauseApp() {
//		System.out.println("[Main]: pauseApp");
//	}
//
//	protected void destroyApp(boolean unconditional)
//			throws MIDletStateChangeException {
//		System.out.println("[Main]: destroyApp");
//
//		continueEx = false;
//
//		if (myRunTimeThread != null) {
//			myRunTimeThread.interrupt();
//		}
//
//		ExternalDevice.getInstance().release(); // Release resources
//		SubscriptionHandler.getInstance().release(); // Release Subscription
//														// resources
//		SmsHandler.getInstance().release(); // Release SMS resource
//		HardwareManager.setGPIOasReleased(gpioOutput); // Use this on the
//														// Concept Board to go
//														// back to dip-switch
//														// settings
//		HardwareManager.setGPIOasReleased(gpioInput); // Use this on the Concept
//														// Board to go back to
//														// dip-switch settings
//		HardwareManager.getInstance().release(); // tidy up
//		I2CHandler.getInstance().release(); // Release I2C resource
//		GpioHandler.getInstance().release(); // Release GPIO resource
//		ConnectionManager.getInstance().release(); // Release ConnectionManager
//													// resources
//		ButtonHandler.getInstance().release(); // Release Button resource
//		AtcHandler.getInstance().release(); // Release ATCommand resource
//
//		notifyDestroyed();
//	}
//
//	class MyRuntimeThread extends Thread {
//		final boolean DEBUG = false;
//
//		public void run() {
//			System.out.println("[Main]: Ready and waiting for SMS...");
//			System.out.println("");
//
//			while (continueEx) {
//				if (DEBUG) {
//					System.out.println("[Main]: +MyRuntimeThread()");
//				}
//				if ((ConnectionManager.houseKeepingIsHappy() == false)) {
//					// Blink madly if Connection Manager reports that network
//					// conditions are bad...
//					GpioHandler.getInstance().flashLedState(277);
//				} else {
//					// This is a thread, not a timer task, so it really ought to
//					// sleep here, for a prime number of milli-seconds
//					try {
//						Thread.sleep(5277);
//					} catch (Exception e) {
//						System.out.println("[Main]: Exception " + e.toString());
//					}
//				}
//				if (DEBUG) {
//					System.out.println("[Main]: -MyRuntimeThread()");
//				}
//			}
//			System.out
//					.println("[Main]: continueEx is 'false' so MyRuntimeThread() has finished.");
//		}
//	}
//
//	// read configuration properties from .jad file
//	private void readSettingsFromJADFile() {
//		String allowNoShieldModeParam = null;
//		String allowOffLineModeParam = null;
//
//		connParamsSocket = getAppProperty("connParamsSocket");
//		locationProviderKey = getAppProperty("locationProviderKey");
//
//		gpioInput = getAppProperty("gpioInput");
//		gpioOutput = getAppProperty("gpioOutput");
//
//		subscriberPhoneNumber1 = getAppProperty("subscriberPhoneNumber1");
//		subscriberPhoneNumber2 = getAppProperty("subscriberPhoneNumber2");
//		subscriberPhoneNumber3 = getAppProperty("subscriberPhoneNumber3");
//		if (subscriberPhoneNumber1 == null) {
//			subscriberPhoneNumber1 = getAppProperty("subscriberPhoneNumber");
//		}
//
//		allowNoShieldModeParam = getAppProperty("allowNoShieldMode");
//		allowOffLineModeParam = getAppProperty("allowOffLineMode");
//
//		if (allowNoShieldModeParam != null) {
//			try {
//				allowNoShieldMode = Integer.parseInt(allowNoShieldModeParam);
//			} catch (Exception e) {
//				System.out.println("[Main]: Warning: allowNoShieldMode - "
//						+ e.toString());
//			}
//			if ((allowNoShieldMode < 0) || (allowNoShieldMode > 1)) {
//				allowNoShieldMode = 0;
//				System.out
//						.println("[Main]: Warning: Invalid allowNoShieldMode value read in JAD file.");
//			}
//		}
//
//		if (allowOffLineModeParam != null) {
//			try {
//				allowOffLineMode = Integer.parseInt(allowOffLineModeParam);
//			} catch (Exception e) {
//				System.out.println("[Main]: Warning: allowOffLineMode - "
//						+ e.toString());
//			}
//			if ((allowOffLineMode < 0) || (allowOffLineMode > 1)) {
//				allowOffLineMode = 0;
//				System.out
//						.println("[Main]: Warning: Invalid allowOffLineMode value read in JAD file.");
//			}
//		}
//
//		if ((connParamsSocket != null) || (locationProviderKey != null)
//				|| (gpioInput != null) || (gpioOutput != null)
//				|| (subscriberPhoneNumber1 != null)
//				|| (allowNoShieldModeParam != null)
//				|| (allowOffLineModeParam != null)) {
//			System.out
//					.println("[Main]: ------------ Settings from JAD ------------");
//			System.out.println("[Main]: JAD connParamsSocket: "
//					+ connParamsSocket);
//			System.out.println("[Main]: JAD locationProviderKey: "
//					+ locationProviderKey);
//			System.out.println("[Main]: JAD gpioInput: " + gpioInput);
//			System.out.println("[Main]: JAD gpioOutput: " + gpioOutput);
//			System.out.println("[Main]: JAD subscriberPhoneNumber1: "
//					+ subscriberPhoneNumber1);
//			System.out.println("[Main]: JAD subscriberPhoneNumber2: "
//					+ subscriberPhoneNumber2);
//			System.out.println("[Main]: JAD subscriberPhoneNumber3: "
//					+ subscriberPhoneNumber3);
//			System.out.println("[Main]: JAD allowNoShieldMode: "
//					+ allowNoShieldMode);
//			System.out.println("[Main]: JAD allowOffLineMode:  "
//					+ allowOffLineMode);
//			System.out
//					.println("[Main]: ------------------- end -------------------");
//		}
//	}
//}
