package utils;

import com.cinterion.io.*;
import java.util.Vector;

public class ButtonHandler {
    private static ButtonHandler instance = null;
    private static boolean continueEx = false;

    private static final String gpioPin1  = "GPIO1";        // BTN-1 on ELSx Connect Shield / Serial Interface ASC0 DTR0
    private static final String gpioPin11 = "GPIO11";       // BTN-A on EHSx Concept Board
    public  static String gpioPin = gpioPin11;              // Actual GPIO to use
    private static InPort button = null;                    // Can have multiple listeners
    
    private static ButtonHandlerInPortListener buttonListener = null;
       
    public static final int BUTTON_UP_OFF = 0;
    public static final int BUTTON_DOWN_ON = 1;
    private static int lastState = BUTTON_UP_OFF;

    private ButtonHandler() {
//      Constructor
    }
	
//  Method return the proper instance of the singleton class
    public static ButtonHandler getInstance() {
        if (instance == null) {
            instance = new ButtonHandler();     // if instance doesn't exist - create one
            instance.init();                    // initialize the instance
        }
        return instance;                        // returns the proper instance
    }

//  Initialises ButtonHandler object, creates Vectors and Output ports	
    private void init() {
    }

    public boolean resourcesAllocatedSuccessfully() {
        if (button != null) {
            return true;
        } else {
            return false;
        }
    }

//  Releases resources	
    public void release() {
        continueEx = false;

        // Release OutPort resource, still needs the AtCHandler instance at this point
        if (button != null)
        {
            try {button.clearListener();} catch (Exception e) {System.out.println(e.toString());}
            try {button.release();} catch (Exception e) {System.out.println(e.toString());}

            button = null;
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
            Vector pins = new Vector(1);

            if (pins != null) {
                pins.addElement(gpioPin);
                button = new InPort(pins);        // try to grab the resource
            }
        } catch (Exception e) {System.out.println("[ButtonHandler]: start() new InPort " + e.toString());}

        if (button == null) {
            System.out.println("[ButtonHandler]: Failed to grab " + gpioPin + " for input.");
        } else {
            if (buttonListener == null) {
                buttonListener = new ButtonHandlerInPortListener();
                if (buttonListener != null) {
                    addButtonListener(buttonListener);                    
                }
            }
        }
    }
    
    public void addButtonListener(InPortListener aButtonListenerIn) {
        if ((aButtonListenerIn != null) && (button != null)) {
            button.addListener(aButtonListenerIn);
        } else {
            System.out.println("[ButtonHandler]: addButtonListener() called but button or Listener was null. ");
        }
    }
    
//  Method returns the current actual state of the GPIO BUTTON_UP or BUTTON_DOWN
    public void getActualButtonState() {
        final boolean DEBUG = false;
        int i = -1;

        try {i = button.getValue();}
        catch (Exception e) {System.out.println("[ButtonHandler]: " + e.toString());}
		
        if (i > -1) {
            lastState = i;
        }

        if (DEBUG) {
            if (lastState == BUTTON_UP_OFF) {
                System.out.println("[ButtonHandler]: BUTTON_UP");
            }
            else {
                System.out.println("[ButtonHandler]: BUTTON_DOWN");
            }
        }
    }

//  Method returns last known state of the GPIO BUTTON_UP or BUTTON_DOWN
    public int getLastButtonState() {
        return lastState;
    }

//  Method allows InPort Listener to change "lastState" of the GPIO BUTTON_UP or BUTTON_DOWN
    public void notifyButtonChanged(int inVal) {
        final boolean DEBUG = false;

        lastState = inVal;
        if (lastState == BUTTON_DOWN_ON) {
            if (DEBUG) {System.out.println("[ButtonHandler]: button down caused LED state to toggle " + GpioHandler.getInstance().getLastLedState());}
        }

        if (lastState == BUTTON_UP_OFF) {
            if (DEBUG) {System.out.println("[ButtonHandler]: button up caused LED state to toggle " + GpioHandler.getInstance().getLastLedState());}
        }
    }
}

class ButtonHandlerInPortListener implements InPortListener {
    final boolean DEBUG = false;

    void ButtonHandlerInPortListener() {
    }

    public void portValueChanged(int changedInPortValue) {
        if (DEBUG) {System.out.println("[ButtonHandlerInPortListener]: " + changedInPortValue);}
        
        ButtonHandler.getInstance().notifyButtonChanged(changedInPortValue);
    }
}
