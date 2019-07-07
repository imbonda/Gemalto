package utils;

import com.cinterion.io.*;

public class AtcHandlerSms {
    private static AtcHandlerSms instance = null;
    private static boolean continueEx = false;

    private static ATCommand atcSms = null;        // Only to be used for SMS sending AT Commands

    private static final int MAXTIMEOUT = 20;      // in seconds
    private int timeOut = 0;
    
//  Constructor
    private AtcHandlerSms() {
    }

//  Method return the proper instance of the singleton class
    public static AtcHandlerSms getInstance() {
        if (instance == null) {
            instance = new AtcHandlerSms();     // if instance doesn't exist - create one
            instance.init();                    // initialize the instance
        }
        return instance;                        // returns the proper instance
    }

//  Initiates AtcHandlerSms object, creates a special SMS ATCommand instance
    private void init() {
        final boolean DEBUG = false;
        
        try {
            if (atcSms == null) {
                atcSms = new ATCommand(false);         // Create an SMS only instance of ATCommand
            }
        } catch (Exception e) {
            System.out.println("[AtcHandlerSms]: Error setting up atcSms - " + e.toString());
        }
    }
    
    public boolean resourcesAllocatedSuccessfully() {
        final boolean DEBUG = false;
        boolean retVal = false;
        
        if (atcSms != null) {
		    retVal = true;
        }
        
        return retVal;
    }
    
//  Releases the atCommand resource
    public void release() {
        continueEx = false;

        if (atcSms != null) {
            try {
                atcSms.release();
                atcSms = null;
            } catch (Exception e) {
                System.out.println("[AtcHandlerSms]: Exception when releasing atc resource. " + e.toString());
            }
        }
        
        instance = null;
    }
    
    public void start() {
        continueEx = true;

        AtcHandlerSms.getInstance().sendAllModuleInitCommands(); // Set up module SMS sending
    }
    
//  Method sends the command and prints the module's response
    public String sendATC(String atcIn) {
        final boolean DEBUG = false;
        String retVal = "";
        String atcDebug;
        
        if ((atcSms != null) && (atcIn != null)) {
            if (atcIn.endsWith("\r") == false) {
                atcIn = atcIn + "\r";
            }
        
            // EHSx rel 3 arn 51 - AT^SMONI will hang if a second AT^SMONI is issued, on another interface, during exec.
            if ((atcIn.indexOf("AT^SNMON=")  > -1) || (atcIn.indexOf("AT^SCFGS?") > -1) ||
                (atcIn.indexOf("AT+COPS=")  > -1) || (atcIn.indexOf("AT+CGATT=") > -1)||
                (atcIn.indexOf("AT^SMON") > -1)) {
                if (DEBUG) {System.out.println("[AtcHandlerSms]: Automatic ATC change to using non-blocking ResponseListener.");}
                retVal = sendATCwithListener(atcIn, MAXTIMEOUT);
            } else {
                try {
                    if (DEBUG) {
                        System.out.println("[AtcHandlerSms]: " + System.currentTimeMillis());  // print system time since boot
                        System.out.println("[AtcHandlerSms]: " + atcIn);      // print the command to send to the module
                    }
                    
                    retVal = atcSms.send(atcIn);           // Send the command and save it's response
                    
                    if (DEBUG) {System.out.println("[AtcHandlerSms]: " + retVal);}    // print the module's response
                } catch (Exception e) {
                    // clean up a copy of atcIn for debugging
                    if (atcIn.endsWith("\r") == false) {atcDebug = atcIn;}
                    else {atcDebug = atcIn.substring(atcIn.indexOf("AT"), atcIn.indexOf("\r")).trim();}
                    System.out.println("[AtcHandlerSms]: Exception occurred during sendATC: (" + atcDebug + ")");
                    System.out.println("[AtcHandlerSms]: " + e.toString());
                    retVal = "ERROR!";
                }
            }
        }
        
        try {Thread.sleep(100);} catch (Exception e) {System.out.println("[AtcHandlerSms]: Exception during ATC 100ms sleep: " + e.toString());}
        
        return retVal;
    }
    
// We have a non-blocking AT command thread and a timer here
// to catch too long time or freezing AT Commands and
// to catch longer responses than 1024 characters such as AT^SNMON="INS",2...

    public String sendATCwithListener(String atcIn, int timeOutIn) {
        final boolean DEBUG = false;
        String retVal = "";
        MySmsATCommandResponseListener mySmsATListener;

        if ((atcSms != null) && (atcIn != null)) {
            mySmsATListener = new MySmsATCommandResponseListener();
            timeOut = timeOutIn;

            try {
                atcSms.send(atcIn, mySmsATListener);
                if (DEBUG) {System.out.println("[AtcHandlerSms]: sendATCwithListener started non-blocking ATCommand '" + atcIn.trim() + "'.");}
            } catch (Exception e) {
                System.out.println("[AtcHandlerSms]: Exception occurred when issuing non-blocking ATCommand: " + e.toString());
                retVal = "ERROR!";
                timeOut = 0;
            }

            try {   // Wait for response, kind of blocking really...
                while ((timeOut > 0) &&   // MyATCommandResponseListener ATResponse keeps this alive
                       (mySmsATListener.getResponse().indexOf("> ") == -1) &&
                       (mySmsATListener.getResponse().indexOf("OK\r\n") == -1) &&
                       (mySmsATListener.getResponse().indexOf("ERROR")  == -1)) {

                    if (timeOut < (MAXTIMEOUT - 5)) {
                        if (DEBUG) {System.out.print(".");}     // here we have been waiting for more than 5 seconds for a response
                    }

                    try {Thread.sleep(1007);} catch (Exception e) {System.out.println("[AtcHandlerSms]: Exception during non-blocking response 1007ms sleep: " + e.toString());}
                    timeOut--;
                } 
            } catch (Exception e) {
                System.out.println("[AtcHandlerSms]: Exception occurred when waiting for non-blocking ATCommand response: " + e.toString());
                retVal = "ERROR!";
                timeOut = 0;
            }

            if (DEBUG) {System.out.println("\n[AtcHandlerSms]: [" + atcIn.trim() + "] " + mySmsATListener.getResponse());}

            // If atc.send "timed out" - kill any remaining AT Command
            if (timeOut <= 0) {
                System.out.println("[AtcHandlerSms]: sendATCwithListener() WARNING: AT Command '" + atcIn.trim() + "' has timed out.");
                try { atcSms.cancelCommand(); } catch (Exception e) {System.out.println("[AtcHandlerSms]: Exception occurred when cancelling non-blocking ATCommand: " + e.toString());}
            }

            if (retVal.compareTo("") == 0) {
                retVal = mySmsATListener.getResponse();
            }
            
            mySmsATListener = null;
        }
        
        try {Thread.sleep(100);} catch (Exception e) {System.out.println("AtcHandlerSms]: Exception during non-blocking ATC 100ms sleep: " + e.toString());}
        
        return retVal;
    }
    
    private void resetTimeout() {
        timeOut = MAXTIMEOUT;
    }

    public void sendAllModuleInitCommands() {
        final boolean DEBUG = false;
 
        System.out.println("[AtcHandlerSms]: Initialising state of module SMS.");

        sendATC("AT+CMEE=2\r");         // Enable verbose error reporting    (only this ATC Instance)
        sendATC("AT+CMGF=1\r");         // Text SMS, not PDU SMS             (only this ATC instance)
    }

    private class MySmsATCommandResponseListener implements ATCommandResponseListener {
        final boolean DEBUG = false;
        private StringBuffer tempText = null;

        MySmsATCommandResponseListener () {
            tempText = new StringBuffer();
            
            if (tempText == null) {
                System.out.println("[AtcHandlerSms]: Warning tempText == null.");
            }
        }

        public void ATResponse(String responseIn) {
            if (tempText != null) {
                if (responseIn != null) {
                    tempText.append(responseIn);
                    resetTimeout(); // keep the AT Command alive as we just got something
                    if (DEBUG) {System.out.print(".");}
                } else {
                    if (tempText != null) {tempText.append("[AtcHandlerSms]: ERROR!");}
                }
            }
        }

        public String getResponse() {
            String retVal = "[AtcHandlerSms]: ERROR!";
            
            if (tempText != null) {retVal = tempText.toString();}
            
            return retVal;
        }
    }
}