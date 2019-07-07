package utils;

import com.cinterion.io.*;

public class AtcHandler {
    private static AtcHandler instance = null;
    private static boolean continueEx = false;

    private static ATCommand atc = null;        // Normal 45s and 12" AT Commands
    private static ATCommand atcLP = null;      // Long Players
    private static ATCommandListener atcListener = null;
    private static final int MAXTIMEOUT = 20;   // in seconds
    private int timeOut = 0;
    
    private BearerControlListenerEx bcListenerEx = null;    // Gemalto class
    public  BearerControlExt bce = null;                    // Markus's extension

    // Requested SMSes for ATC handler
    public static final String REQ_SMS1 = "#AT";     
    public static final String REQ_SMS2 = "__SOMETHING__";
    public static final String REQ_SMS3 = "__SOMETHING__";

    private static final boolean ALLOW_SMS_AT_COMMANDS = true;      // true => potential security hole
    private static final boolean BLACKLIST_AT_COMMANDS = true;      // true => improves security:
    public  static final String DEFAULT_AT = "ATCommandIsBlackListed.";
    private static final String[] BLACKLISTED = {
//        "AT+CFUN=",   // avoid dead asset, but ConnectionManager.java should be able to handle recovery
//        "AT^SXRAT="   // avoid dead asset, but ConnectionManager.java should be able to handle recovery
        "AT^SMSO",      // avoid dead asset, through shutdown
        "AT+COPS=",     // avoid blocking
        "AT+CMUX",      // avoid MUXer
        "AT+CMGS",      // too hard to handle > and CTRL+Z logic
        "AT+CPIN=",     // avoid locking SIM cards
        "AT+CPIN2=",    // avoid locking SIM cards
        "AT^SCFG=",     // avoid dead asset, through misconfiguration
        "AT^SFDL",      // protect against Firmware download mode
        "AT^SFSA",      // protect against Flash File System Access
        "AT^SIC",       // too hard to handle this state machine - binary data transfer
        "AT^SIS",       // too hard to handle this state machine - binary data transfer
        "AT^SJRA",      // avoid legacy Java start/stop
        "AT^SJAM",      // avoid stopping or deleting Java Applications
        "AT^SJDL",      // protect FFS
        "AT^SMSEC"      // protect customer's exisxting security settings
    };
    
//  Constructor
    private AtcHandler() {
    }

//  Method return the proper instance of the singleton class
    public static AtcHandler getInstance() {
        if (instance == null) {
            instance = new AtcHandler();        // if instance doesn't exist - create one
            instance.init();                    // initialize the instance
        }
        return instance;                        // returns the proper instance
    }

//  Initiates AtcHandler object, creates an ATCommand instance and ATCommandListener
    private void init() {
        final boolean DEBUG = false;
        
        try {
            if (atc == null) {
                atc = new ATCommand(false);     // Create an instance of AT Command for 45s and 12"
            }
            
            if (atcLP == null) {
                atcLP = new ATCommand(false);   // Create an instance of AT Command for LPs
            }
        } catch (Exception e) {
            System.out.println("[AtcHandlerSms]: Error setting up atc - " + e.toString());
        }

        try {
            // Create a listener for receiving incoming SMS and answering Voice calls
            atcListener = new ATCommandListener() {
                public void RINGChanged(boolean SignalState) {
                    final boolean DEBUG = false;
                    if (DEBUG) {
                        if (SignalState) {      // Can do this for any incoming URC
                            System.out.println("[ATCommandListener]: RING0 asserted - true.");
                        } else {
                            System.out.println("[ATCommandListener]: RING0 asserted - false.");
                        }
                    }
                }

                public void DSRChanged (boolean SignalState) {
                    final boolean DEBUG = true;
                }
                
                public void DCDChanged (boolean SignalState) {
                    final boolean DEBUG = true;
                }
                
                public void CONNChanged(boolean SignalState) {
                    final boolean DEBUG = true;
                }

                public void ATEvent(String eventIn) {
                    final boolean DEBUG = false;
                    
                    if (eventIn != null) {
                        if (DEBUG) {System.out.println("[ATCommandListener]: '" + eventIn.trim() + "'");}

                        // Check if the event is an incoming SMS message
                        if (SmsHandler.getInstance().incomingSmsDetected(eventIn)) {
                            SmsHandler.getInstance().incomingSmsParse(eventIn);
                        } // end of is an SMS
                        
                        // Check if the event is an incoming phone call
                        if (ConnectionManager.getInstance().incomingCallDetected(eventIn)) {
                            ConnectionManager.getInstance().incomingCallParse(eventIn);
                        } // end of is a Voice/CSD call
                    
                    } else {
                        System.out.println("[ATCommandListener]: Warning: ATEvent() called with event == null.");
                    }
                }
            };  // ; is needed - end of atcListener = new ATCommandListener()

            // Create a listener for receiving Bearer Control State Changes
            bcListenerEx = new BearerControlListenerEx() {
                public void stateChanged(String APN, int stateIn, int PdpErrCause) {
                    final boolean DEBUG = true;

                    if (APN != null) {
                        // PdpErrCause - Any occuring Pdp reject code, coded as defined in 3GPP 24.008 10.5.6.6
                        // PdpErrorCause=33 => Wrong APN for SIM card (Requested service option not subscribed)
                        ConnectionManager.getInstance().setBearerConnectionState(stateIn);  // Allow CM to watch bearer state

                        if (BearerControlExt.stateDescription(stateIn) != null) {
                            System.out.println("[BearerStateChange]: APN=" + APN + " State=" + BearerControlExt.stateDescription(stateIn) + " PdpErrorCause=" + PdpErrCause);
                        } else {
                            System.out.println("[BearerStateChange]: APN=" + APN + " State=" + stateIn + " PdpErrorCause=" + PdpErrCause);
                        }
                    } else {
                        System.out.println("[BearerStateChange]: stateChanged() called with APN==null State=" + stateIn + " PdpErrorCause=" + PdpErrCause);
                    }
                }
            };      // ; is needed
//            }

            // Add the listener(s) to the ATCommand
            if (atc != null) {  // AT Command Listener
                if (atcListener != null) {
                    atc.addListener(atcListener);
                }
                
               /* if (bcListenerEx != null) {             // Bearer Control Listener Ex - Gemalto
                    bce = new BearerControlExt(atc);    // Bearer Control Ext - Markus
                    if (bce != null) {
                        bce.addListenerEx(bcListenerEx);
                    }
                } */
            }
        } catch (Exception e) {
            System.out.println("[AtcHandler]: Error setting up atcListener - " + e.toString());
        }
    }

    public boolean resourcesAllocatedSuccessfully() {
        final boolean DEBUG = false;
        boolean retVal = false;
        
        if ((atc != null) && (atcListener != null) && (atcLP != null)) {
            retVal = true;
        }
        
        return retVal;
    }
    
//  Releases the ATCommand resources
    public void release() {
        continueEx = false;

        // Release the 45s and 12"
        if (atc != null) {
            try {
                if (atcListener != null) {
                    atc.removeListener(atcListener);
                    atcListener = null;
                }
                
                if (bce != null) {
                    // Release and Deactivate all PDP Contexts using BearerControl
                    bce.clearListener();
                    bce.hangUp();       // Triggers the closure of all Java networking bearers
                    bcListenerEx = null;
                    bce = null;
                }
                
                atc.release();
                atc = null;
            } catch (Exception e) {
                System.out.println("[AtcHandler]: Exception when releasing atc resource - " + e.toString());
            }
        }

        // Release the LPs
        if (atcLP != null) {
            try {
                atcLP.release();
                atcLP = null;
            } catch (Exception e) {
                System.out.println("[AtcHandler]: Exception when releasing atcLP resource - " + e.toString());
            }
        }
        
        instance = null;
    }
    
    public void start() {
        continueEx = true;
        
        AtcHandler.getInstance().sendAllModuleInitCommands(); // Set up module URC reporting (also incoming SMS)
    }
    
    public boolean parseCommandsInSMS(String oa, String responseIn) {
        final boolean DEBUG = false;
        boolean retVal = false;
        String result;
        
        if ((ALLOW_SMS_AT_COMMANDS) && (responseIn != null) && (responseIn.toUpperCase().startsWith("#AT"))) {
            System.out.println("[AtcHandler]: WARNING - allowing SMS based AT COMMAND control of the module.");
            if (DEBUG) {System.out.println("[AtcHandler]: AT Command as received: " + responseIn.substring(1));}

            try {
                // a ^ character seems to come in as "\1B\14"
                while (responseIn.indexOf("\\1B\\14") > -1) {
                    result = responseIn.substring(0, responseIn.indexOf("\\1B\\14")) + "^" +
                             responseIn.substring(responseIn.indexOf("\\1B\\14") + "\\1B\\14".length());
                    responseIn = result.trim();
                }

                // a " character seems to come in as "\22"
                while (responseIn.indexOf("\\22") > -1) {
                    result = responseIn.substring(0, responseIn.indexOf("\\22")) + "\"" +
                             responseIn.substring(responseIn.indexOf("\\22") + "\\22".length());
                    responseIn = result.trim();
                }
                System.out.println("[AtcHandler]: AT Command to send: '" + responseIn.substring(1) + "'");

                result = AtcHandler.getInstance().sendATC(ensureNonBlackListedATCommand(responseIn.substring(1), true));
                if (DEBUG) {System.out.println("[AtcHandler]: AT Command response: '" + result + "'");}

                if ((oa != null) && (result != null)) {
                    SmsHandler.getInstance().smsSend(oa, result, SmsHandler.CLASS1);     // send the output of the AT Command to the Originating Address
                    retVal = true;
                }
            } catch (Exception e) {System.out.println("[AtcHandler]: Error parsing SMS AT Command - "  + e.toString());}
        } else {
            System.out.println("[AtcHandler]: Warning parseCommandsInSMS() called but responseIn == null");
        }
        
        return retVal;
    }

    public String ensureNonBlackListedATCommand(String atcIn) {
        String retVal;
        
        retVal = ensureNonBlackListedATCommand(atcIn, false);
        
        return retVal;
    }
    
    // Use this method to pre-process AT Commands for example received via SMS or sent over AT Command interface
    public String ensureNonBlackListedATCommand(String atcIn, boolean verbose) {
        String retVal = "";
        int i;
        
        if (atcIn != null) {
            retVal = atcIn;     // let us start off assuming all is OK

            for (i = 0; i < BLACKLISTED.length; i++) {
                if (atcIn.trim().toUpperCase().indexOf(BLACKLISTED[i]) != -1) {

                    
                    i = BLACKLISTED.length;     // break
                }
            }
        }
        
        return retVal;
    }
    
//  Method sends the command and prints the module's response
    public String sendATC(String atcIn) {
        final boolean DEBUG = false;
        String retVal = "";
        String atcDebug;
        
        if ((atc != null) && (atcIn != null)) {
            if (atcIn.endsWith("\r") == false) {
                atcIn = atcIn + "\r";
            }
        
            // EHSx rel 3 arn 51 - AT^SMONI will hang if a second AT^SMONI is issued, on another interface, during exec.
            if ((atcIn.indexOf("AT^SNMON=")  > -1) || (atcIn.indexOf("AT^SCFGS?") > -1) ||
                (atcIn.indexOf("AT+COPS=")  > -1) || (atcIn.indexOf("AT+CGATT=") > -1)||
                (atcIn.indexOf("AT^SMON") > -1)) {
                if (DEBUG) {System.out.println("[AtcHandler]: Automatic ATC change to using non-blocking ResponseListener.");}
                retVal = sendATCwithListener(atcIn, MAXTIMEOUT);
            } else {
                try {
                    if (DEBUG) {
                        System.out.println("[AtcHandler]: " + System.currentTimeMillis());  // print system time since boot
                        System.out.println("[AtcHandler]: " + atcIn);      // print the command to send to the module
                    }
                    
                    retVal = atc.send(atcIn);           // Send the command and save it's response
                    
                    if (DEBUG) {System.out.println("[AtcHandler]: " + retVal);}    // print the module's response
                } catch (Exception e) {
                    // clean up a copy of atcIn for debugging
                    if (atcIn.endsWith("\r") == false) {atcDebug = atcIn;}
                    else {atcDebug = atcIn.substring(atcIn.indexOf("AT"), atcIn.indexOf("\r")).trim();}
                    System.out.println("[AtcHandler]: Exception occurred during sendATC: (" + atcDebug + ")");
                    System.out.println("[AtcHandler]: " + e.toString());
                    retVal = "ERROR!";
                }
            }
        }
        
        try {Thread.sleep(100);} catch (Exception e) {System.out.println("[AtcHandler]: Exception during ATC 100ms sleep: " + e.toString());}
        
        return retVal;
    }
    
// We have a non-blocking AT command thread and a timer here
// to catch too long time or freezing AT Commands and
// to catch longer responses than 1024 characters such as AT^SNMON="INS",2...

    public String sendATCwithListener(String atcIn, int timeOutIn) {
        final boolean DEBUG = false;
        String retVal = "";
        MyATCommandResponseListener myATListener;

        if ((atcLP != null) && (atcIn != null)) {
            myATListener = new MyATCommandResponseListener();                
            timeOut = timeOutIn;

            try {
                atcLP.send(atcIn, myATListener);
                if (DEBUG) {System.out.println("[AtcHandler]: sendATCwithListener started non-blocking ATCommand '" + atcIn.trim() + "'.");}
            } catch (Exception e) {
                System.out.println("[AtcHandler]: Exception occurred when issuing non-blocking ATCommand: " + e.toString());
                retVal = "ERROR!";
                timeOut = 0;
            }

            try {   // Wait for response, kind of blocking really...
                while ((timeOut > 0) &&   // MyATCommandResponseListener ATResponse keeps this alive
                       (myATListener.getResponse().indexOf("> ") == -1) &&
                       (myATListener.getResponse().indexOf("OK\r\n") == -1) &&
                       (myATListener.getResponse().indexOf("ERROR")  == -1)) {

                    if (timeOut < (MAXTIMEOUT - 5)) {
                        if (DEBUG) {System.out.print(".");}     // here we have been waiting for more than 5 seconds for a response
                    }

                    try {Thread.sleep(1007);} catch (Exception e) {System.out.println("[AtcHandler]: Exception during non-blocking response 1007ms sleep: " + e.toString());}
                    timeOut--;
                } 
            } catch (Exception e) {
                System.out.println("[AtcHandler]: Exception occurred when waiting for non-blocking ATCommand response: " + e.toString());
                retVal = "ERROR!";
                timeOut = 0;
            }

            if (DEBUG) {System.out.println("\n[AtcHandler]: [" + atcIn.trim() + "] " + myATListener.getResponse());}

            // If atc.send "timed out" - kill any remaining AT Command
            if (timeOut <= 0) {
                System.out.println("[AtcHandler]: sendATCwithListener() WARNING: AT Command '" + atcIn.trim() + "' has timed out.");
                try { atcLP.cancelCommand(); } catch (Exception e) {System.out.println("[AtcHandler]: Exception occurred when cancelling non-blocking ATCommandLP: " + e.toString());}
            }

            if (retVal.compareTo("") == 0) {
                retVal = myATListener.getResponse();
            }
            
            myATListener = null;
        }
        
        try {Thread.sleep(100);} catch (Exception e) {System.out.println("AtcHandler]: Exception during non-blocking ATC 100ms sleep: " + e.toString());}
        
        return retVal;
    }
    
    private void resetTimeout() {
        timeOut = MAXTIMEOUT;
    }

    public void sendAllModuleInitCommands() {
        // if you want to see debug then turn on DEBUG=true in sendATC(String atcIn) {...}
 
        System.out.println("[AtcHandler]: Initialising state of module URCs.");

        sendATC("AT+CSCS=\"GSM\"\r");           // Ensure no "/" problems
        sendATC("ATE1\r");                      // Turn on Echo
        
        sendATC("AT+CMEE=2\r");                 // Enable verbose error reporting    (only this ATC Instance)
        sendATC("AT+CMGF=1\r");                 // Text SMS, not PDU SMS             (only this ATC instance)
        sendATC("AT+CNMI=2,1\r");               // Enable incoming SMS URCs          (only this ATC instance)
        sendATC("AT+CMMS=2\r");                 // More Messages to Send - keep link open (all ATC instances)
        sendATC("AT+CPMS=\"SM\",\"SM\",\"SM\"\r"); // Save to SIM card                    (all ATC instances)

        sendATC("AT+CREG=2\r");                 // Enable CREG URCs
        sendATC("AT+CGREG=2\r");                // Enable CGREG URCs

        sendATC("AT^SIND=\"service\",1\r");     // Enable Service State URCs
        sendATC("AT^SIND=\"roam\",1\r");        // Enable Roaming State URCs
        sendATC("AT^SIND=\"smsfull\",1\r");     // Enable SIM SMS full URCs
        sendATC("AT^SIND=\"nitz\",1\r");        // Enable NITZ URCs
        sendATC("AT^SIND=\"psinfo\",1\r");      // Enable PS State URCs
        sendATC("AT^SIND=\"lsta\",1\r");        // Enable Link State URCs
        
        sendATC("AT+CR=1\r");                   // Service reporting control
        sendATC("AT+CRC=1\r");                  // Incoming Call Indication Format

        sendATC("AT^SIND=message,1\r");
        sendATC("AT^SIND=call,1\r");
        sendATC("AT^SIND=rssi,0\r");            // turn RSSI off as it is too chatty in 3G
        sendATC("AT^SIND=ciphcall,1\r");
        sendATC("AT^SIND=simdata,1\r");
        sendATC("AT^SIND=eons,1\r");
        sendATC("AT^SIND=pacsp,1\r");
        sendATC("AT^SIND=vmwait,1\r");
    }
    
    private class MyATCommandResponseListener implements ATCommandResponseListener {
        final boolean DEBUG = false;
        private StringBuffer tempText = null;

        MyATCommandResponseListener () {
            tempText = new StringBuffer();
            
            if (tempText == null) {
                System.out.println("[AtcHandler]: Warning tempText == null.");
            }
        }

        public void ATResponse(String responseIn) {
            if (tempText != null) {
                if (responseIn != null) {
                    tempText.append(responseIn);
                    resetTimeout(); // keep the AT Command alive as we just got something
                    if (DEBUG) {System.out.print(".");}
                } else {
                    if (tempText != null) {tempText.append("[AtcHandler]: ERROR!");}
                }
            }
        }

        public String getResponse() {
            String retVal = "[AtcHandler]: ERROR!";
            
            if (tempText != null) {retVal = tempText.toString();}
            
            return retVal;
        }
    }
}