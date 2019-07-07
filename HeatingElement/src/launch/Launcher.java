package launch;

import utils.ConnectionManager;
import utils.ButtonHandler;
import utils.AtcHandler;
import utils.GpioHandler;
import utils.HardwareManager;
import utils.I2CHandler;
import utils.SmsHandler;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import com.cinterion.misc.Watchdog2;


public class Launcher extends MIDlet {
	
	private static final String TAG = "[Launcher]: ";
	
	private static Notifier heNotifier = null;
	// >0 => The number of seconds after which the Watchdog will trigger.
	private static final int RESTARTDELAY = 239;
	
	// Parameters read from .JAD file
	private static String subscriberPhoneNumber1 = null; 
	private static String subscriberPhoneNumber2 = null;
	private static String subscriberPhoneNumber3 = null;
	private static String gpioInput1 = null;
	private static String gpioInput2 = null;

	private static void printWithTAG(String message) {
		System.out.println(TAG + message);
	}
	
	/**
	 * Default constructor.
	 */
	public Launcher() {
		System.out.println("");
		printWithTAG("Constructor()");
		System.out.println(this.getClass().getName());
	}

	protected void startApp() throws MIDletStateChangeException {
		boolean correctStartup = false;
		
		printWithTAG("startApp()");
		
		try {
			initResources();
			loadJADSettings();
			printJADSettings();
			configureGpio();
			
			// Although it seems like these applyHost() calls would be good to put
			// in the constructor, you can't do so.
			// An object is only seen by the rest of the system once its constructor
			// has finished.
			// Netbeans would warn saying "leaking .this in the constructor".
			if (ButtonHandler.getInstance().resourcesAllocatedSuccessfully() &&
				GpioHandler.getInstance().resourcesAllocatedSuccessfully() &&
				AtcHandler.getInstance().resourcesAllocatedSuccessfully()) {

				// register this object with the Singleton Handlers, so that they
				// can see destroyApp()
				SmsHandler.getInstance().start();
				
				printWithTAG("Ready.");
				correctStartup = setupNotifier();
			}
			
			if (correctStartup == false) {
				// We did not achieve network registration,
				// so what to do next:
				// We ought to restart the module and retry.
				Watchdog2 tmpwd = new Watchdog2();
				if (tmpwd != null) {
					// Valid values are 0 to switch off, or
					// between 10 and 300 to set watchdog time-out.
					AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
					AtcHandler.getInstance().sendATC(
							"AT^SCFG=\"Userware/Watchdog\",\"1\"\r");
					// The Watchdog2 will restart the system

					try {
						tmpwd.start(RESTARTDELAY);
					} // The number of seconds after which the watchdog will trigger.
					catch (Exception e) {
						printWithTAG("Watchdog Start Exception "
								+ e.toString());
					}

					printWithTAG("Watchdog will restart the system in "
							+ RESTARTDELAY + " seconds.");
				}
				try {
					destroyApp(true);
				} catch (Exception e) {
					printWithTAG("Start-up failed " + e.toString());
				}
			}
		}
		catch (Exception e) {
			printWithTAG(e.getMessage());
		}
	}
	
	private void initResources() {
		if (AtcHandler.getInstance().resourcesAllocatedSuccessfully()) {
			AtcHandler.getInstance().start();
			AtcHandler.getInstance().sendATC("ATE0\r");
			AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
			AtcHandler.getInstance().sendATC("AT^SCFG=\"Userware/Watchdog\",\"0\"\r");
		}
		
		if (HardwareManager.getInstance().resourcesAllocatedSuccessfully()) {
			HardwareManager.getInstance().start();
			printWithTAG("Hardware interfaces allocated successfully.");
		}
		
		if (I2CHandler.getInstance().resourcesAllocatedSuccessfully()) {
			I2CHandler.getInstance().start();
			printWithTAG("I2C bus ready.");
		} else {
			printWithTAG("Warning I2C Handler not ready.");
		}
	}
	
	private void loadJADSettings() {
		gpioInput1 = getAppProperty("gpioInput1");
		gpioInput2 = getAppProperty("gpioInput2");
		subscriberPhoneNumber1 = getAppProperty("subscriberPhoneNumber1");
		subscriberPhoneNumber2 = getAppProperty("subscriberPhoneNumber2");
		subscriberPhoneNumber3 = getAppProperty("subscriberPhoneNumber3");
		if (subscriberPhoneNumber1 == null) {
			subscriberPhoneNumber1 = getAppProperty("subscriberPhoneNumber");
		}
		
		// Ignoring duplicate subscribers
		if (subscriberPhoneNumber3.equals(subscriberPhoneNumber1) ||
				subscriberPhoneNumber3.equals(subscriberPhoneNumber2)) {	
			subscriberPhoneNumber3 = null;
		}
		if (subscriberPhoneNumber2.equals(subscriberPhoneNumber1)) {
			subscriberPhoneNumber2 = null;
		}
	}
	
	private void printJADSettings() {
		printWithTAG("------------ Settings from JAD ------------");
		printWithTAG("JAD gpioInput1: " + gpioInput1);
		printWithTAG("JAD gpioInput2: " + gpioInput2);
		printWithTAG("JAD subscriberPhoneNumber1: " + subscriberPhoneNumber1);
		printWithTAG("JAD subscriberPhoneNumber2: " + subscriberPhoneNumber2);
		printWithTAG("JAD subscriberPhoneNumber3: " + subscriberPhoneNumber3);
		printWithTAG("------------------- end -------------------");
	}
	
	private void configureGpio() {
		//
		// Change gpio directions
		//
		// Gpio 1
		printWithTAG("Changing GPIO1 direction: " + gpioInput1 + " -> input");
		ButtonHandler.getInstance().start(gpioInput1);
		HardwareManager.setGPIOasInput(gpioInput1);
		// Gpio 2
		printWithTAG("Changing GPIO2 direction: " + gpioInput2 + " -> input");
		ButtonHandler.getInstance().start(gpioInput2);
		HardwareManager.setGPIOasInput(gpioInput2);
		
		// Configuring a Java Terminal output, the level shifter output should
		// be set first, followed by the module output configuration.
		GpioHandler.getInstance().start();
	}
	
	private boolean setupNotifier() {
		boolean correctStartup = false;
		if (ConnectionManager.getInstance().succesfulNetworkRegistration()) {
			// Watchdog timer is started now so we restart in a few minutes
			// if nothing is good
			ConnectionManager.getInstance().start(); // Starts HouseKeeping
														// Thread too

			// Right now we are in a good CREG state so assume Home or
			// Roaming registered CS state is achieved
			correctStartup = true;

			if (heNotifier == null) {
				heNotifier = new Notifier(getSubscribedPhoneNumbers());
				heNotifier.start();
			}

			System.out.println("");
			printWithTAG("Notifier started.");

		} else {
			printWithTAG("Warning could not get a successful network registration.");
		}
		return correctStartup;
	}
	
	private String[] getSubscribedPhoneNumbers() {
		if (subscriberPhoneNumber1 != null &&
			subscriberPhoneNumber2 != null &&
			subscriberPhoneNumber3 != null) {
			return new String[]{subscriberPhoneNumber1,
								subscriberPhoneNumber2,
								subscriberPhoneNumber3};
		}
		else if (subscriberPhoneNumber1 != null &&
				 subscriberPhoneNumber2 != null) {
			return new String[]{subscriberPhoneNumber1,
								subscriberPhoneNumber2};
		}
		else if (subscriberPhoneNumber1 != null) {
			return new String[]{subscriberPhoneNumber1};
		}
		return null;
	}
	
	protected void pauseApp() {
		printWithTAG("pauseApp()");
	}
	
	protected void destroyApp(boolean cond)
			throws MIDletStateChangeException {
		
		printWithTAG("destroyApp(" + cond + ")");
		
		heNotifier = null;
		
		// Release SMS resource
		SmsHandler.getInstance().release();
		// Use this on the Concept Board to go back to dip-switch settings
		HardwareManager.setGPIOasReleased(gpioInput1);
		HardwareManager.setGPIOasReleased(gpioInput2);
		// Tidy up
		HardwareManager.getInstance().release();
		// Release I2C resource
		I2CHandler.getInstance().release();
		// Release GPIO resource
		GpioHandler.getInstance().release();
		// Release ConnectionManager resources
		ConnectionManager.getInstance().release();
		// Release Button resource
		ButtonHandler.getInstance().release();
		// Release ATCommand resource
		AtcHandler.getInstance().release();
		
		notifyDestroyed();
	}
}
