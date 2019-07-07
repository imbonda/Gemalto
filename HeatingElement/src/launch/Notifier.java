package launch;

import utils.ButtonHandler;
import utils.GpioHandler;
import utils.SmsHandler;
import utils.SmsHandler.SmsHandlerListener;

import com.cinterion.io.InPortListener;

class Notifier implements SmsHandlerListener, InPortListener {
	
	private static final boolean DEBUG = true;
	private static final String TAG = "[Notifier]: ";
	// SMS messages.
	private static final String STARTUP_MESSAGE = "Power On";
	private static final String GPIO_MESSAGE = "HE Off";
	private static final String PONG_MESSAGE = "Ok";

	private String[] subPhoneNumbers = null;
	
	private static void printWithTAG(String message) {
		System.out.println(TAG + message);
	}
	
	private void registerSmsListener() {
		SmsHandler.getInstance().addSmsListener(this);
	}
	
	private void registerButtonListener() {
		ButtonHandler.getInstance().addButtonListener(this);
	}
	
	public Notifier(String[] subscribedPhoneNumbers) {
		this.subPhoneNumbers = subscribedPhoneNumbers;
		if (subscribedPhoneNumbers == null) {
			printWithTAG("Warning: subscribed phone list is empty!");
		}
	}
	
	public boolean parseCommandsInSMS(String oaIn, String responseIn) {
		return SmsHandler.getInstance().smsSend(oaIn, PONG_MESSAGE, SmsHandler.CLASS1);
	}
	
	public void portValueChanged(int changedInPortValue) {
        if (DEBUG) {
        	printWithTAG("Button value changed - " + changedInPortValue);
        }

        if (changedInPortValue == ButtonHandler.BUTTON_DOWN_ON) {
            GpioHandler.getInstance().toggleLedState();
            
            notifySubscriber(GPIO_MESSAGE);
        }

        if (changedInPortValue == ButtonHandler.BUTTON_UP_OFF) {
            GpioHandler.getInstance().toggleLedState();
        }
    }
	
	public void start() {
		printWithTAG("Thread started.");
		
		printWithTAG("Registerring sms listener.");
		registerSmsListener();
		
		printWithTAG("Registerring button listener.");
		registerButtonListener();
		
		printWithTAG("Sending startup sms.");
		notifySubscriber(STARTUP_MESSAGE);
	}
	
	private void notifySubscriber(String message) {
		if (null == this.subPhoneNumbers) {
			return;
		}
		for (int i = 0; i < this.subPhoneNumbers.length; ++i) {
			String subPhoneNumber = this.subPhoneNumbers[i];
			if (DEBUG) {
				printWithTAG("Sending sms, '" + message + "' ,to '" + subPhoneNumber+ "'");
			}
			try {
				SmsHandler.getInstance().smsSend(subPhoneNumber, message, SmsHandler.CLASS1);
			} catch (Exception e) {
				printWithTAG("Exception " + e.toString());
			}
		}
	}
}