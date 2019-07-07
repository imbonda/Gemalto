package utils;

// import com.cinterion.io.ATStringConverter;

public class SmsHandler {
    private static SmsHandler instance = null;
    public static boolean continueEx = false;       // Thread.stop() is depreciated so use while() loop and boolean to stop
    private static Thread myRunTimeThread = null;

    public static final int MAX_numSMS = 32;        // Keep >= SubscriptionHandler.MAX_subscribersBySMS
    public static int[]    slot;       // an array of slot statuses
    public static String[] daSMS;      // an array of phone numbers
    public static String[] msgSMS;     // an array of messages
    public static int[]    clSMS;      // an array of classes
    
    public static final int CLASS0 = 0;     // MS shall display the message immediately, message not automatically stored in the SIM or ME and acknowledgement sent
    public static final int CLASS1 = 1;     // Normal SMS
    
    // Requested SMSes for ExternalDevice
    private static String externalDeviceReqSMS1 = "__SOMETHING__";  // Requested SMS to ExternalDevice handler
    private static String externalDeviceReqSMS2 = "__SOMETHING__";  // Requested SMS to ExternalDevice handler
    private static String externalDeviceReqSMS3 = "__SOMETHING__";  // Requested SMS to ExternalDevice handler

	// --------------------------------------------------------------------------- //
	// https://www.ibm.com/developerworks/library/j-jtp07265/
	// Guidelines for writing and supporting event listeners
	// --------------------------------------------------------------------------- //
    public interface SmsHandlerListener {    
    	boolean parseCommandsInSMS(String oaIn, String responseIn);
    }
 
    SmsHandlerListener myExternalDeviceListening = null;  // Allow one SMS parsing listener to register with us
    
    private SmsHandler() {
//  Constructor
    }
	
//  Method return the proper instance of the singleton class
    public static SmsHandler getInstance() {
        if (instance == null) {
            instance = new SmsHandler();        // if instance doesn't exist - create one
            instance.init();                    // initialize the instance
        }
        return instance;                        // returns the proper instance
    }

//  Initialises SmsHandler object
    private void init() {
    }
	
    public boolean resourcesAllocatedSuccessfully() {
        boolean retVal = false;
        
    // As sending text SMSes is a two part operation and can take 10s of seconds
    // We allow some slow smsSend methods to use their own exclusive AT Command Handler "AtcHandlerSms"
        
        if ((slot != null) && (daSMS != null) && (msgSMS != null) && (clSMS != null) &&
            AtcHandlerSms.getInstance().resourcesAllocatedSuccessfully()) {
            AtcHandlerSms.getInstance().start();

            retVal = true;
        }
        
        return retVal;
    }
    
//  Releases resources	
    public void release() {
        continueEx = false;

        if (myRunTimeThread != null) {myRunTimeThread.interrupt();}

        slot = null;
        daSMS = null;
        msgSMS = null;
        clSMS = null;
        
        AtcHandlerSms.getInstance().release();
        instance = null;
    }

    public void start() {
        continueEx = true;
        
        try {
            slot = new int[MAX_numSMS];
            daSMS  = new String[MAX_numSMS];
            msgSMS  = new String[MAX_numSMS];
            clSMS  = new int[MAX_numSMS];
        } catch (Exception e) {System.out.println("[SmsHandler]: start() " + e.toString());}
        
        if (myRunTimeThread == null) {
            myRunTimeThread = new MySmsSendingThread();
            myRunTimeThread.start();
        }
    }

    // Allows External Device to define up to three SMS strings that it is "interested to parse"
    public void setExternalDeviceSMSReqWords(String ext1, String ext2, String ext3) {
        if (ext1 != null) {externalDeviceReqSMS1 = ext1;}
        if (ext2 != null) {externalDeviceReqSMS2 = ext2;}
        if (ext3 != null) {externalDeviceReqSMS3 = ext3;}
    }
    
    // Allows External Device to register itself and say that it is "interested to parse" SMSes
    public void addSmsListener(SmsHandlerListener anSmsListenerIn) {
        if (anSmsListenerIn != null) {
            myExternalDeviceListening = anSmsListenerIn;
            System.out.println("[SmsHandler]: addSmsListener() ExternalDevice listener defined.");
        }
    }

    public boolean incomingSmsDetected(String smsIn) {
        boolean retVal = false;
        
        if (smsIn != null) {
            if (smsIn.toUpperCase().indexOf("+CMTI") > -1) {
                retVal = true;
            }
        }
        
        return retVal;
    }

    public boolean incomingSmsParse(String smsIn) {
        final boolean DEBUG = false;
        boolean retVal = false;
        int start;          // volatile reuse below
        int smsNumber = 0;
        String response;
        String oa = null;   // Originating Address of SMS

        if (DEBUG) {System.out.println("[SmsHandler]: " + smsIn);}

//      Identify the local SMS message's storage location
//      +CMTI: "SM",1

// this assumes that the command is at least substring(x) characters in length!
//   "unhappy".substring(2)    returns "happy"
//  "Harbison".substring(3)    returns "bison"
// "hamburger".substring(4, 8) returns "urge"
//    "smiles".substring(1, 5) returns "mile"
    
        if (smsIn != null) {
            start = smsIn.indexOf(',') + 1;
            try {smsNumber = Integer.parseInt(smsIn.substring(start, smsIn.length()).trim());}
            catch (Exception e) {System.out.println("[SmsHandler]: Error parsing SMS sequence number - "  + e.toString());}
        }
        
//      Read the SMS message
//      AT+CMGR=1
//      +CMGR: "REC UNREAD","+44776623xxxx",,"15/04/13,18:12:55+04"
//      Hello

        if (smsNumber > 0) {
            response = AtcHandler.getInstance().sendATC("AT+CMGR=" + smsNumber + "\r");
            if ((response.indexOf("+CMGR: ") > -1) && (response.indexOf("\n") > -1) && (response.indexOf("OK") > -1)) {
                if (DEBUG) {System.out.println("[SmsHandler]: " + response);}
                oa = smsExtractOriginatingAddress(response);
                response = response.substring(response.indexOf("+CMGR: ") + 1);
                response = response.substring(response.indexOf('\n'), response.indexOf("OK")-2).trim();
//                response = response.toUpperCase();    // a catch-all but changes semantics of case-sensitive messages
            } else {
                response = "[SmsHandler]: error";
            }
        } else {
            response = "[SmsHandler]: unknown";
        }

        // Pass to ExternalDevice handler
        if (DEBUG) {System.out.println("[SmsHandler]: passing SMS to ExternalDevice: " + response);}
        if (myExternalDeviceListening != null) {
            retVal = myExternalDeviceListening.parseCommandsInSMS(oa, response);
        } else {
            System.out.println("[SmsHandler]: Warning no ExternalDevice SMS listener defined.");
        }

        if (retVal == false) {
            SubscriptionHandler.getInstance().sendUsageInformationBySMS(oa);
            System.out.println("[SmsHandler]: SMS - Usage Information Sent. ('" + response + "')");
        }
        
        if (DEBUG) {System.out.println("[SmsHandler]: Deleting incoming SMS #" + smsNumber);}
        smsDelete(smsNumber);

        System.out.println("[SmsHandler]: Waiting for next SMS...");

        return retVal;
    }
    
    public boolean smsSend(String daIn, String messageIn, int classIn) {
        final boolean DEBUG = false;
        boolean retVal = false;
        int i;
        
        if (DEBUG) {System.out.println("[SmsHandler]: +smsSend()");}
        
        // Handle some obvious bad ASCII characters that mess up the GSM alphabet
        if (messageIn != null) {
            if (messageIn.indexOf("^") > -1) {
                messageIn = messageIn.replace('^', (char)20);
                if (DEBUG) {System.out.println("[SmsHandler]: removed ^ character(s) from outgoing message.");}
            }
            if (messageIn.indexOf("\\") > -1) {
                messageIn = messageIn.replace('\\', (char)47);
                if (DEBUG) {System.out.println("[SmsHandler]: removed \\ character(s) from outgoing message.");}
            }
            // If you send the word ERROR in capitals then the SMS sending logic will think there was an error
            if (messageIn.indexOf("+CME ERROR:") > -1) {
                messageIn = messageIn.replace('R', 'r');
                if (DEBUG) {System.out.println("[SmsHandler]: removed ERROR message from outgoing message.");}
            }
        }
        
        for (i = 0; i < MAX_numSMS; i++) {
            if (slot[i] == 0) {
                slot[i] = 1; daSMS[i] = daIn;
                msgSMS[i] = messageIn;
                clSMS[i] = classIn;
                
                retVal = true;      // success - queued the SMS send request
                System.out.println("[SmsHandler]: Queued outgoing SMS as item #" + i);
                i = MAX_numSMS;     // break
            }
        }

        if (retVal == false) {System.out.println("[SmsHandler]: Unabled to add SMS to outgoing Queue.");}
        if (DEBUG) {System.out.println("[SmsHandler]: -smsSend()");}

        return retVal;
    }

    public boolean purgeSubsQueuedSmsMessages(String daIn) {
        final boolean DEBUG = false;
        boolean retVal = false;
        int i;
        
        if (DEBUG) {System.out.println("[SmsHandler]: +purgeSubsQueuedSmsMessages()");}
        
        if (daIn != null) {
            for (i = 0; i < MAX_numSMS; i++) {
                if (daSMS[i] != null) {
                    if (daIn.compareTo(daSMS[i]) == 0) {
                        slot[i] = 0;
                        daSMS[i] = null;    // so MySmsSendingThread() must check for nulls when building an SMS to send
                        msgSMS[i] = null;
                        clSMS[i] = CLASS0;
                        retVal = true;      // success - found and cleaned at least one message from the queue

                        if (DEBUG) {System.out.println("[SmsHandler]: Unsubscribed user; removed outgoing SMS item #" + i);}
                    }
                }
            }
            if (retVal == false) {System.out.println("[SmsHandler]: No Unsubscribed user messages in outgoing Queue.");}
        } else {
            System.out.println("[SmsHandler]: purgeSubsQueuedSmsMessages() called but daIn==null");
        }

        if (DEBUG) {System.out.println("[SmsHandler]: -purgeSubsQueuedSmsMessages()");}

        return retVal;
    }
    
    private boolean smsDelete(int smsNumberIn) {
        boolean retVal = false;

        AtcHandler.getInstance().sendATC("AT+CMGD=" + smsNumberIn + "\r");

        return retVal;
    }
    
    public void purgeAllSavedSmsMessages() {
        final boolean DEBUG = false;
        final int DELAY = 277;
        int start;
        int end;
        int smsNumber;
        int numMaxSms = 0;
        String response;

// at+cpms?
// +CPMS: "SM",xx,30,"SM",xx,30,"SM",xx,30
// OK
        System.out.println("[SmsHandler]: Purging stored SMS messages.");
        response = AtcHandler.getInstance().sendATC("AT+CPMS?\r");

        if ((response.indexOf("+CPMS: ") > -1) && (response.indexOf(",") > -1)) {
            if (DEBUG) {System.out.println("[SmsHandler]: " + response);}
            start = response.indexOf(',') + 1;          // skip used slots
            start = response.indexOf(',', start) + 1;   // start of max slots
            end = response.indexOf(',', start);         // end of max slots
            
            try {numMaxSms = Integer.parseInt(response.substring(start, end).trim());}
            catch (Exception e) {System.out.println("[SmsHandler]: purgeAllSavedSmsMessages() " + e.toString());}
        }
        
        if (DEBUG) {System.out.println("[SmsHandler]: AT+CPMS? has " + numMaxSms + " slots.");}

        if ((numMaxSms < 0) || (numMaxSms > 50)) {
            numMaxSms = 50;
        }

        for (smsNumber = 1; smsNumber < numMaxSms; smsNumber++) {
            smsDelete(smsNumber);
            try {Thread.sleep(DELAY);} catch (Exception e) {System.out.println("[SmsHandler]: Exception " + e.toString());}
        }
    }
    
    public static void checkSMSnotificaitons() {
        final boolean DEBUG = false;
        String response;
        boolean reInitialiseNeeded = false;
        
        if (ConnectionManager.houseKeepingIsHappy()) {
            // We are dependent on AT+CMGF=1 - ensure this is set in AtcHandler.java which has the URC listener logic
            response = AtcHandler.getInstance().sendATC("AT+CMGF?\r");
            if ((response.indexOf("+CMGF: ") > -1) && (response.indexOf("OK") > -1)) {
                response = response.substring(response.indexOf("+CMGF:"), response.indexOf("OK")-2).trim();

                // AT+CMGF? - check that we have a 1 state
                if (response.indexOf(" 1") == -1) {reInitialiseNeeded = true;}
            }
            if (DEBUG) {System.out.println("[SmsManager]: SMS format PDU(0) or Text(1) (AT+CMGF) " + response);}

            // We are also dependent on AT+CNMI=2,1 - ensure this is set, AT&F from any interface can reset it to defaults
            response = AtcHandler.getInstance().sendATC("AT+CNMI?\r");
            if ((response.indexOf("+CNMI: ") > -1) && (response.indexOf("OK") > -1)) {
                response = response.substring(response.indexOf("+CNMI:"), response.indexOf("OK")-2).trim();

                // AT+CNMI? - check that we have a 2,1,0,0,0 state
                if (response.indexOf(" 2,1,0,0,0") == -1) {reInitialiseNeeded = true;}
            }
            if (DEBUG) {System.out.println("[SmsManager]: SMS reporting status (AT+CNMI) " + response);}
            
            // We benefit from AT+CMMS=2 - ensure this is set (More Messages to Send link stays open)
            response = AtcHandler.getInstance().sendATC("AT+CMMS?\r");
            if ((response.indexOf("+CMMS: ") > -1) && (response.indexOf("OK") > -1)) {
                response = response.substring(response.indexOf("+CMMS:"), response.indexOf("OK")-2).trim();

                // AT+CMMS? - check that we have a 2 state
                if (response.indexOf(" 2") == -1) {reInitialiseNeeded = true;}
            }
            if (DEBUG) {System.out.println("[SmsManager]: More Messages to Send (2 - keep link open) (AT+CMMS) " + response);}

            if (DEBUG) {
                response = AtcHandlerSms.getInstance().sendATC("AT+CMEE=2\r");
                System.out.println("[SmsManager]: " + response);
            }
        }
        
        if (reInitialiseNeeded == true) {
            SmsHandler.getInstance().purgeAllSavedSmsMessages();        // If AT+CMGF has been wrong we may have missed some SMSes
            AtcHandler.getInstance().sendAllModuleInitCommands();       // Set up module URC reporting (again)
            AtcHandlerSms.getInstance().sendAllModuleInitCommands();    // Set up module SMS sending (again)
        }
    }

    
    private String smsExtractOriginatingAddress(String smsIn) {
        final boolean DEBUG = false;
        String retVal = null;
        int start;
        int end;
       
        if (smsIn != null) {
            try {
//  +CMGR: "REC UNREAD","+44776623xxxx",,"15/04/13,18:12:55+04"
                start = smsIn.indexOf(",\"") + 2;          // skip "REC UNREAD","
                end = smsIn.indexOf('"', start + 1);       // end of oa
                retVal = smsIn.substring(start, end).trim();
            } catch (Exception e) {
                System.out.println("[SmsHandler]: smsExtractOriginatingAddress() " + e.toString());
            }
// TP-Originating-Address Address-Value field in string format
        } else {
            System.out.println("[SmsHandler]: Warning smsExtractOriginatingAddress() called but smsIn == null");
        }
        
        if (DEBUG) {System.out.println("[SmsHandler]: oa='" + retVal + "'");}
        return retVal;
    }
}

// --------------------------------------------------------------------------- //

class MySmsSendingThread extends Thread {
    final boolean DEBUG = false;
    final boolean VERBOSE = false;
    final int MAXSMSLEN = 139;
    String response;
    boolean initialiedSMS;
    int i;

    public void run() {
        System.out.println("[SmsHandler]: SMS sending thread waiting to start.");

        while (SmsHandler.continueEx == false) {
            try {Thread.sleep(5077);} catch (Exception e) {System.out.println("[SmsHandler]: Exception " + e.toString());}
        }

        System.out.println("[SmsHandler]: SMS sending thread started.");

        while (SmsHandler.continueEx == true) {
            if (DEBUG) {System.out.println("[SmsHandler]: +MySmsSendingThread()");}

            if (ConnectionManager.houseKeepingIsHappy()) {
                if (DEBUG) {
                    response = AtcHandlerSms.getInstance().sendATC("AT+CMEE=2\r");
                    System.out.println("[SmsHandler]: " + response);
                }

                initialiedSMS = false;      // keep track of status of SMS sending AT Commands
                
                for (i = 0; i < SmsHandler.MAX_numSMS; i++) {
                    if (SmsHandler.slot[i] > 0) {
                        // We have a queued SMS message waiting patiently to go
                        
                        if (initialiedSMS == false) {
                            // Set up module SMS sending
                            AtcHandlerSms.getInstance().sendAllModuleInitCommands();
                            initialiedSMS = true;
                        }

                        // Set it's SMS DCS class
                        if (SmsHandler.clSMS[i] == SmsHandler.CLASS0) {
                            response = AtcHandlerSms.getInstance().sendATCwithListener("AT+CSMP=17,167,0,240\r", 20);
                        } else {
                            response = AtcHandlerSms.getInstance().sendATCwithListener("AT+CSMP=17,167,0,0\r", 20);
                        }

                        // Start sending - be careful to avoid address null pointer errors
                        if (SmsHandler.daSMS[i] != null) {
                            response = AtcHandlerSms.getInstance().sendATCwithListener("AT+CMGS=\"" + SmsHandler.daSMS[i] + "\"\r", 20);
                        } else {
                            response = "ERROR daSMS[i] == null";
                        }

                        if (DEBUG) {
                            System.out.println(response);
                            System.out.println("[SmsHandler]: >CMGS");
                        }

                        if (response.indexOf(">") > -1) {
                            try {Thread.sleep(100);} catch (Exception e) {System.out.println("[SmsHandler]: Exception " + e.toString());}

                            // Continue sending from ">" prompt - be careful to avoid message null pointer errors
                            if (SmsHandler.msgSMS[i] != null) {
                                if (SmsHandler.msgSMS[i].length() > MAXSMSLEN) {
                                    // How to handle SMS messages that are longer than 140 characters?
                                    System.out.println("[SmsHandler]: Warning msgSMS[" + i + "]'s length is " + SmsHandler.msgSMS[i].length() + " chars.");
                                    System.out.println("[SmsHandler]: Warning msgSMS[" + i + "]: " + SmsHandler.msgSMS[i]);
                                    SmsHandler.msgSMS[i] = SmsHandler.msgSMS[i].substring(0, MAXSMSLEN - 1);
                                }
                                response = AtcHandlerSms.getInstance().sendATCwithListener(SmsHandler.msgSMS[i] + (char)0x1a, 20);  // SMS text and end
//                                    response = AtcHandlerSms.getInstance().sendATCwithListener((ATStringConverter.Java2GSM(msgSMS[i]) + (char)0x1a), 20);  // SMS text and end
                            } else {response = "ERROR msgSMS[i] == null";}

                            if (DEBUG) {System.out.println(response);}
                            if (response.indexOf("ERROR") > -1) {
                                // send failed
                                if (SmsHandler.slot[i] != 0) {SmsHandler.slot[i] = SmsHandler.slot[i] + 1;}
                                System.out.println("[SmsHandler]: Warning: SMS send error, retrying.");
                            } else {
                                // send succeeded
                                SmsHandler.slot[i] = 0;
                                SmsHandler.daSMS[i] = null;
                                SmsHandler.msgSMS[i] = null;
                                SmsHandler.clSMS[i] = SmsHandler.CLASS0;
                            }
                        } else {
                            // send failed
                            if (SmsHandler.slot[i] != 0) {SmsHandler.slot[i] = SmsHandler.slot[i] + 1;}
                            System.out.println("[SmsHandler]: Warning: SMS prompt > missing, retrying.");
                        }

                        if (DEBUG) {System.out.println("[SmsHandler]: -CMGS");}

                        if (SmsHandler.slot[i] == 0) {System.out.println("[SmsHandler]: Sent item #" + i + " from outgoing SMS queue.");}
                        try {Thread.sleep(107);} catch (Exception e) {System.out.println("[SmsHandler]: Exception " + e.toString());}

                        // leave slot dirty 3 times, to try again, then give up and wipe it
                        if (SmsHandler.slot[i] > 3) {
                            SmsHandler.slot[i] = 0;
                            SmsHandler.daSMS[i] = null;
                            SmsHandler.msgSMS[i] = null;
                            SmsHandler.clSMS[i] = SmsHandler.CLASS0;
                            System.out.println("[SmsHandler]: Failed to send item #" + i + " from outgoing SMS queue. Discarded.");
                        }
                    } // if
                } // for
            } else { // houseKeepingHappy
                if (VERBOSE) {System.out.println("[SmsHandler]: Warning: houseKeeping not happy. No SMS service.");}
            }
            
            // Check that we are in text SMS mode and ready to receive.
            // SmsHandler.checkSMSnotificaitons();      // every 4 seconds is too chatty -> move to ConnectionManager
 
            // This is a thread, not a timer task, so it really ought to sleep here, for a prime number of milli-seconds
            try {Thread.sleep(4077);} catch (Exception e) {System.out.println("[SmsHandler]: Exception " + e.toString());}

            if (DEBUG) {System.out.println("[SmsHandler]: -MySmsSendingThread()");}
        }

        System.out.println("[SmsHandler]: continueEx is 'false' so SMS sending thread has finished.");
    }
}

