package utils;

import com.cinterion.io.*;
import java.util.Vector;

public class GpioHandler {
    private static GpioHandler instance = null;
    private static boolean continueEx = false;

    private static GPIOButtonHandlerListener buttonListener = null;

    private static final String gpioPin5 = "GPIO5";         // also used as Status LED on BGS5 and EHSx
    private static final String gpioPin6 = "GPIO6";         // also used as PWM2
    private static final String gpioPin7 = "GPIO7";         // also used as PWM1
    private static final String gpioPin8 = "GPIO8";         // also used as COUNTER
    private static String gpioPin = gpioPin5;               // Actual GPIO to use
    private static OutPort led = null;
    public static final int LED_ON = 1;
    public static final int LED_OFF = 0;
    private static int lastState = LED_OFF;

    // Requested SMSes for GPIO handler
    public static final String REQ_SMS1 = "LED ON";     
    public static final String REQ_SMS2 = "LED OFF";
    public static final String REQ_SMS3 = "__SOMETHING__";
    
    private GpioHandler() {
//      Constructor
    }
	
//  Method return the proper instance of the singleton class
    public static GpioHandler getInstance() {
        if (instance == null) {
            instance = new GpioHandler();   //	if instance doesn't exist - create one
            instance.init();                //  initialize the instance
        }
        return instance;                //  returns the proper instance
    }

//  Initialises GpioHandler object, creates Vectors and Output ports	
    private void init() {
    }

    public boolean resourcesAllocatedSuccessfully() {
        boolean retVal = false;
        
        if (led != null) {retVal = true;}
        
        return retVal;
    }

    // Releases resources	
    public void release() {
        continueEx = false;

        // Release OutPort resource, still needs the AtCHandler instance at this point
        if (led != null) {
            try {
                led.setValue(LED_OFF);
                led.release();
            } catch (Exception e) {System.out.println("[GpioHandler]: Exception in release() " + e.toString());}

            led = null;
            instance = null;
        }
    }

    public void start(String gpioIn) {
        if ((gpioIn != null) && ((gpioIn.indexOf("GPIO") > -1))) {
            gpioPin = gpioIn;
        }

        start();
    }

    public void start() {
        continueEx = true;

        try {
            Vector pins   = new Vector(1);
            Vector values = new Vector(1);

            if ((pins != null) && (values != null)) {
                pins.addElement(gpioPin);
                values.addElement(new Integer(1));
                led = new OutPort(pins, values);        // try to grab the resource
            }
        } catch (Exception e) {System.out.println("[GpioHandler]: start() new OutPort " + e.toString());}

        if (led == null) {
            System.out.println("[GpioHandler]: Failed to grab " + gpioPin + " for output.");
        } else {
            // The LED wants to be able to toggle its state upon Button Events, so request to listen to ButtonHandler.
            if (buttonListener == null) {
                buttonListener = new GPIOButtonHandlerListener();
                if (buttonListener != null) {
                    ButtonHandler.getInstance().addButtonListener(buttonListener);
                }
            }
        }
    }

    public boolean parseCommandsInSMS(String oa, String responseIn) {
        boolean retVal = false;
        
        if (responseIn != null) {
            //      Here we check if the message should turn the SYNC LED on or off
//            if ((responseIn.toUpperCase().indexOf(REQ_SMS1) > -1) || (responseIn.toUpperCase().indexOf("ON") > -1) || (responseIn.toUpperCase().indexOf("1") > -1)) {
            if (responseIn.toUpperCase().indexOf(REQ_SMS1) > -1) {
                setLedState(LED_ON);
                retVal = true;
            }

//            if ((responseIn.toUpperCase().indexOf(REQ_SMS2) > -1) || (responseIn.indexOf("OFF") > -1) || (responseIn.indexOf("0") > -1)) {
            if (responseIn.toUpperCase().indexOf(REQ_SMS2) > -1) {
                setLedState(LED_OFF);
                retVal = true;
            }
        } else {
            System.out.println("[GpioHandler]: Warning parseCommandsInSMS() called but responseIn == null");
        }
        
        return retVal;
    }

//  Method turns the default LED ON or OFF
    public void setLedState(int inState) {
        final boolean DEBUG = false;

        if ((inState < LED_OFF) || (inState > LED_ON)) {
            inState = LED_OFF;
            System.out.println("[GpioHandler]: LED state value incorrect use " + LED_OFF + "=OFF and " + LED_ON + "=ON");
        } else {
            if (led != null)
            {
                if (inState == LED_OFF) {
                    if (DEBUG) {System.out.println("[GpioHandler]: LED OFF");}
                } else {
                    if (DEBUG) {System.out.println("[GpioHandler]: LED ON");}
                }

                try {led.setValue(inState);} catch (Exception e) {System.out.println(e.toString());}
                lastState = inState;
            } else {
                System.out.println("[GpioHandler]: No LED instance variable available.");
            }
        }
    }

//  Method flashes LED but leaves it in its original state
    public void flashLedState(int pauseIn) {
        if (led != null) {
            try {
                if (getLastLedState() == LED_OFF) {
                    led.setValue(LED_ON);  Thread.sleep(pauseIn);
                    led.setValue(LED_OFF); Thread.sleep(pauseIn);
                } else {
                    led.setValue(LED_OFF); Thread.sleep(pauseIn);
                    led.setValue(LED_ON);  Thread.sleep(pauseIn);
                }
            } catch (Exception e) {System.out.println("[GpioHandler]: Exception in flashLedState() " + e.toString());}
        } else {
            System.out.println("[GpioHandler]: flashLedState() called with no LED instance variable available.");
        }
    }

//  Method toggles LED state and changes its state accordingly
    public void toggleLedState() {
        if (led != null) {
            try {
                if (getLastLedState() == LED_OFF) {
                    setLedState(LED_ON);
                } else {
                    setLedState(LED_OFF);
                }
            } catch (Exception e) {System.out.println("[GpioHandler]: Exception in toggleLedState() " + e.toString());}
        } else {
            System.out.println("[GpioHandler]: toggleLedState() called with no LED instance variable available.");
        }
    }

    public int getLastLedState() {
        return lastState;
    }
}

// --------------------------------------------------------------------------- //
// https://www.ibm.com/developerworks/library/j-jtp07265/
// Guidelines for writing and supporting event listeners
// --------------------------------------------------------------------------- //

class GPIOButtonHandlerListener implements InPortListener {
    final boolean DEBUG = false;

    public void GPIOButtonHandlerListener() {
    }

    public void portValueChanged(int changedInPortValue) {
        if (DEBUG) {System.out.println("[ExternalDeviceButtonHandlerListener]: " + changedInPortValue);}
        
        if (changedInPortValue == ButtonHandler.BUTTON_DOWN_ON) {
            GpioHandler.getInstance().toggleLedState();
        }
    }
}
