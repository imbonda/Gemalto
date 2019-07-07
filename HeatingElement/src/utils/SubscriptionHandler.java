package utils;

public class SubscriptionHandler {
    private static SubscriptionHandler instance = null;
    public static boolean continueEx = false;       // Thread.stop() is depreciated so use while() loop and boolean to stop
    private static Thread myRunTimeThread = null;

    public static final int MAX_subscribersBySMS = 32;
    private static String[] subscribersBySMS;       // an array of up to MAX_subscribersBySMS
    private static int[] topicsBySMS;               // an array of bitmasks of their interests
    private static int[] sendRateReqSMS;            // an array of counter of their rate requests (minutes)
    private static int[] sendRateCurSMS;            // an array of counters of time until next send (minutes)
    private static final int DEFAULT_SEND_RATE = 2; // number of minutes between each SMS send 

    private static String[] blacklistedNum;         // an array of phone numbers
    private static int[]    blacklistedTimer;       // an array of black list counters
    private static int BLACKLIST_THRESHOLD = 5;     // number of non-compliant SMSes we will accept before black listing
    
    private static final String footerText = ". To unsubscribe send STOP. For more info send HELP. ";

    // Requested SMSes for Subscription handler
    public static final String REQ_SMS1 = "SUB";
    public static final String REQ_SMS2 = "SUBSCRIBE";
    public static final String REQ_SMS3 = "START";
    public static final String REQ_SMS4 = "TOPIC";
    public static final String REQ_SMS5 = "VALUE";
    public static final String REQ_SMS6 = "HELP";
    public static final String REQ_SMS7 = "USAGE";
    public static final String REQ_SMS8 = "READ";
    public static final String REQ_SMS9 = "ADHOC";
    public static final String REQ_SMSA = "STOP";
    public static final String REQ_SMSB = "REMOVE";
    public static final String REQ_SMSC = "RATE";

    SubscriptionHandlerListener myExternalDeviceListening = null;  // Allow one Subscription listener to register with us
    
    // SensorLogic - read configs and create SL objects, boot SL agent
    //        MQTT - deviceID, brokerUrl, brokerPort, connectToBroker
    //               subscribe(Topic / deviceTypeID / deviceID / command, qos)
    //               publish(topic, qosIn, val), disconnect
    //         SMS - Keep a list of registered SMS subscribers
    //               Users ask for Help, Subscribe, pick Topics, pick Rate and Stop
    //               Send all users "their" SMS message on a global Scheduled timer
    //               Respond to adhoc incoming SMS pings with CSV list of readings
    
    private SubscriptionHandler() {
//  Constructor
    }
	
//  Method return the proper instance of the singleton class
    public static SubscriptionHandler getInstance() {
        if (instance == null) {
            instance = new SubscriptionHandler(); // if instance doesn't exist - create one
            instance.init();                      // initialize the instance
        }
        return instance;                          // returns the proper instance
    }

//  Initialises SubscriptionHandler object
    private void init() {
    }
	
    public boolean resourcesAllocatedSuccessfully() {
        boolean retVal = false;
        
        if ((subscribersBySMS != null) && (topicsBySMS != null) &&
            (sendRateReqSMS != null)   && (sendRateCurSMS != null)) {retVal = true;}
        
        return retVal;
    }
    
//  Releases resources	
    public void release() {
        continueEx = false;

        if (myRunTimeThread != null) {myRunTimeThread.interrupt();}

        blacklistedNum = null;
        blacklistedTimer = null;
        
        subscribersBySMS = null;
        topicsBySMS = null;
        instance = null;
        sendRateReqSMS = null;
        sendRateCurSMS = null;
    }

    public void start() {
        continueEx = true;

        try {
            subscribersBySMS = new String[MAX_subscribersBySMS];
            topicsBySMS = new int[MAX_subscribersBySMS];
            sendRateReqSMS  = new int[MAX_subscribersBySMS];
            sendRateCurSMS  = new int[MAX_subscribersBySMS];
        } catch (Exception e) {System.out.println("[SubscriptionHandler]: start() building subscriber DB " + e.toString());}

        try {
            blacklistedNum = new String[MAX_subscribersBySMS];
            blacklistedTimer = new int[MAX_subscribersBySMS];
        } catch (Exception e) {System.out.println("[SubscriptionHandler]: start() building blacklist " + e.toString());}

        if (myRunTimeThread == null) {
            myRunTimeThread = new MyPublishingThread();
            myRunTimeThread.start();
        }
    }
    
    public void addSubListener(SubscriptionHandlerListener aSubListenerIn) {
        if (aSubListenerIn != null) {
            myExternalDeviceListening = aSubListenerIn;
            System.out.println("[SubscriptionHandler]: addSubListener() ExternalDevice listener defined.");
        }
    }

    private int findSubscriberBySMS(String oaIn) {
        final boolean DEBUG = false;
        int retVal = MAX_subscribersBySMS;      // "not found yet"
        int i;
        
        if (DEBUG) {System.out.println("[SubscriptionHandler]: +findSubscriberBySMS()");}

        if (oaIn != null) {
            for (i = 0; i < MAX_subscribersBySMS; i++) {
                if (subscribersBySMS[i] != null) {
                    if (oaIn.compareTo(subscribersBySMS[i]) == 0) {
                        retVal = i;                 // found it at index #i
                        i = MAX_subscribersBySMS;   // break
                    }
                }
            }
        } else {
            System.out.println("[SubscriptionHandler]: findSubscriberBySMS() called but oaIn==null");
        }

        if (DEBUG) {System.out.println("[SubscriptionHandler]: -findSubscriberBySMS()");}

        return retVal;      // index matching "oaIn" phone number
    }
    
    public boolean sendUsageInformationBySMS(String oaIn) {
        boolean retVal = false;
        StringBuffer tmpMsg;
        
        if (oaIn != null) {
            tmpMsg = new StringBuffer();
            if ((tmpMsg != null) && (myExternalDeviceListening != null)) {

                tmpMsg.append(myExternalDeviceListening.getBannerText());
                tmpMsg.append("SUBscribe, READ, TOPICs ");
                
                tmpMsg.append(myExternalDeviceListening.getShortTopicsList());
                tmpMsg.append(", ");
                
                tmpMsg.append(myExternalDeviceListening.getAdditionalCommandsList());
                tmpMsg.append(", ");

                tmpMsg.append("RATE, HELP, STOP. ");
                tmpMsg.append(myExternalDeviceListening.getAdditionalTempText());

                registerInBlackList(oaIn);      // Try to prevent cyclic behaviour from other automated services
                if (notBlackListed(oaIn)) {
                    SmsHandler.getInstance().smsSend(oaIn, tmpMsg.toString(), SmsHandler.CLASS1);
                    System.out.println("[SubscriptionHandler]: Responded to SMS Help request from " + oaIn);
                }
            }
        } else {
            System.out.println("[SubscriptionHandler]: sendUsageInformationBySMS() called but oaIn == null");
        }

        return retVal;
    }
    
    public boolean parseCommandsInSMS(String oaIn, String responseIn) {
        boolean retVal = false;

        if ((oaIn != null) && (responseIn != null)) {
            if ((responseIn.toUpperCase().indexOf(REQ_SMS1) > -1) ||
                (responseIn.toUpperCase().indexOf(REQ_SMS2) > -1) ||
                (responseIn.toUpperCase().indexOf(REQ_SMS3) > -1)) {
                SubscriptionHandler.getInstance().addSubscriberBySMS(oaIn);
                retVal = true;
            }

            if ((responseIn.toUpperCase().indexOf(REQ_SMS4) > -1) || (responseIn.toUpperCase().indexOf(REQ_SMS5) > -1)) {
                SubscriptionHandler.getInstance().editSubscriberTopics(oaIn, responseIn.toUpperCase());
                retVal = true;
            }

            if ((responseIn.toUpperCase().indexOf(REQ_SMS6) > -1) || (responseIn.toUpperCase().indexOf(REQ_SMS7) > -1)) {
                SubscriptionHandler.getInstance().sendUsageInformationBySMS(oaIn);
                retVal = true;
            }

            if ((responseIn.toUpperCase().indexOf(REQ_SMS8) > -1) || (responseIn.toUpperCase().indexOf(REQ_SMS9) > -1)) {
                SubscriptionHandler.getInstance().sendReadingsBySMS(oaIn);
                retVal = true;
            }

            if ((responseIn.toUpperCase().indexOf(REQ_SMSA) > -1) || (responseIn.toUpperCase().indexOf(REQ_SMSB) > -1)) {
                SubscriptionHandler.getInstance().removeSubscriberBySMS(oaIn);
                retVal = true;
            }
            
            if (responseIn.toUpperCase().indexOf(REQ_SMSC) > -1) {
                SubscriptionHandler.getInstance().editSubscriberRate(oaIn, responseIn.toUpperCase());
                retVal = true;
            }

            if ((retVal == true) && (responseIn.indexOf(".") == -1) && (responseIn.indexOf(",") == -1)) {
                removeFromBlackList(oaIn);      // Assume we have a regular human subscriber, not a machine
            }
        } else {
            System.out.println("[SubscriptionHandler]: parseCommandsInSMS() called but a parameter == null");
        }
        
        return retVal;
    }
    
    public boolean addSubscriberBySMS(String oaIn) {
        boolean retVal = false;
        int i = 0;

        if ((oaIn != null) && (oaIn.equals("") != true) && (oaIn.indexOf("+") != -1)) {
            if (findSubscriberBySMS(oaIn) != MAX_subscribersBySMS) {
                SmsHandler.getInstance().smsSend(oaIn, "Already Subscribed! To unsubscribe, send STOP.", SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Ignored duplicate SMS Subscription request from " + oaIn);
            } else {
                for (i = 0; i < MAX_subscribersBySMS; i++) {
                    if (subscribersBySMS[i] == null) {
                        SmsHandler.getInstance().smsSend(oaIn, "Subscribed. To unsubscribe, send STOP.", SmsHandler.CLASS0);
                        System.out.println("[SubscriptionHandler]: Added new SMS subscriber: #" + i + " on " + oaIn);
                        subscribersBySMS[i] = oaIn;
                        if (myExternalDeviceListening != null) {
                            topicsBySMS[i] = myExternalDeviceListening.getTopicBitMaskALL();
                        } else {
                            // ExternalDevice.java must register it's SubscriptionHandlerListener{} interface using SubHand's addSubManListener() method
                            System.out.println("[SubscriptionHandler]: Warning: no External Device listener defined.");
                        }
                        sendRateReqSMS[i] = DEFAULT_SEND_RATE;
                        sendRateCurSMS[i] = DEFAULT_SEND_RATE;
                        i = MAX_subscribersBySMS;   // break
                        retVal = true;
                    }
                }
            }

            if ((retVal == false) && (i == MAX_subscribersBySMS)) {
                SmsHandler.getInstance().smsSend(oaIn, "Unable to accept your SUBscription request at this time.", SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Rejected SMS subscription request from " + oaIn);
            }
        } else {
            System.out.println("[SubscriptionHandler]: addSubscriberBySMS() called but oaIn == null or was invalid");
        }

        return retVal;
    }

    private void registerInBlackList(String oaIn) {
        final boolean DEBUG = false;
        int tmpIndex = MAX_subscribersBySMS;      // "not found yet"
        int i;
        
        if (oaIn != null) {
            // Check to see if this SMS phone number already exists in Black List - save in tmpIndex
            for (i = 0; i < MAX_subscribersBySMS; i++) {
                if (blacklistedNum[i] != null) {
                    if (oaIn.compareTo(blacklistedNum[i]) == 0) {
                        tmpIndex = i;                 // found it at index #i
                        i = MAX_subscribersBySMS;   // break
                    }
                }
            }

            // If it does not exist then add it to the list, just until it does something we expect READ/TOPIC/RATE
            if (tmpIndex == MAX_subscribersBySMS) {
                for (i = 0; i < MAX_subscribersBySMS; i++) {
                    if (blacklistedNum[i] == null) {
                        if (DEBUG) {System.out.println("[SubscriptionHandler]: BlackBlisted pending proper usage: " + oaIn);}
                        blacklistedNum[i] = oaIn;
                        blacklistedTimer[i] = 0;    // Not black listed
                        tmpIndex = i;               // save the index of the new Black Listed subscriber in tmpIndex
                        i = MAX_subscribersBySMS;   // break
                    }
                }
            }

            // The subscriber is now definately in the list and has BLACKLIST_THRESHOLD "strikes" until it is black listed
            if (tmpIndex != MAX_subscribersBySMS) {
                if (blacklistedTimer[tmpIndex] == 0) {blacklistedTimer[tmpIndex] = 0;}
                blacklistedTimer[tmpIndex] = blacklistedTimer[tmpIndex] + 1;  // Increase counter until black list threashold reached
            }
        } else {
            System.out.println("[SubscriptionHandler]: registerInBlackList() called but oaIn == null");
        }
    }

    private boolean notBlackListed(String oaIn) {
        final boolean DEBUG = false;
        boolean retVal = true;
        int i;
        
        if (oaIn != null) {
            // Check to see if this SMS phone number already exists in Black List
            for (i = 0; i < MAX_subscribersBySMS; i++) {
                if (blacklistedNum[i] != null) {
                    if (oaIn.compareTo(blacklistedNum[i]) == 0) {          // found it at index #i
                        if (DEBUG) {System.out.println("[SubscriptionHandler]: notBlackListed found: " + oaIn);}
                        if (blacklistedTimer[i] > BLACKLIST_THRESHOLD) {   // but too many non human SMSes received
                            System.out.println("[SubscriptionHandler]: Black Listed number: " + oaIn);
                            retVal = false;
                        }
                        i = MAX_subscribersBySMS;   // break
                    }
                }
            }
        } else {
            System.out.println("[SubscriptionHandler]: notBlackListed() called but oaIn == null");
        }

        return retVal;
    }

    private void removeFromBlackList(String oaIn) {
        final boolean DEBUG = false;
        int i;
        
        if (oaIn != null) {
            // Check to see if this SMS phone number already exists in Black List
            for (i = 0; i < MAX_subscribersBySMS; i++) {
                if (blacklistedNum[i] != null) {
                    if (oaIn.compareTo(blacklistedNum[i]) == 0) {          // found it at index #i
                        if (DEBUG) {System.out.println("[SubscriptionHandler]: removeFromBlackList found: " + oaIn);}
                            blacklistedNum[i] = null;
                            blacklistedTimer[i] = 0;
                        }
                        i = MAX_subscribersBySMS;   // break
                    }
                }
        } else {
            System.out.println("[SubscriptionHandler]: removeFromBlackList() called but oaIn == null");
        }
    }
    
    private boolean sendReadingsBySMS(String oaIn) {
        final boolean DEBUG = false;
        boolean retVal = false;
        StringBuffer tmpMsg;
        int i;
        int requestedTopicMask;
        
        if (DEBUG) {System.out.println("[SubscriptionHandler]: +sendReadingsBySMS()");}

        if (oaIn != null) {
            if ((findSubscriberBySMS(oaIn) != MAX_subscribersBySMS) && (notBlackListed(oaIn))) {
                i = findSubscriberBySMS(oaIn);
                requestedTopicMask = topicsBySMS[i];
                tmpMsg = new StringBuffer();

                if (myExternalDeviceListening != null) {
                    tmpMsg.append(myExternalDeviceListening.getBannerText());
                    
                    if (requestedTopicMask != myExternalDeviceListening.getTopicBitMaskALL()) {
                        tmpMsg.append(myExternalDeviceListening.getReadingsTextByTopic(requestedTopicMask));
                    } else {
                        if (requestedTopicMask != myExternalDeviceListening.getTopicBitMaskNONE()) {
                            tmpMsg.append(myExternalDeviceListening.getReadingsTextAll());
                        }
                    }
                    tmpMsg.append(footerText);
                    tmpMsg.append(myExternalDeviceListening.getAdditionalTempText());
                } else {
                    System.out.println("[SubscriptionHandler]: Warning no ExternalDevice Subscription listener defined.");
                }

                SmsHandler.getInstance().smsSend(oaIn, tmpMsg.toString(), SmsHandler.CLASS1);

                System.out.println("[SubscriptionHandler]: Responded to SMS Read request from " + oaIn);
                System.out.println("[SubscriptionHandler]: '" + tmpMsg.toString() + "'");
            } else {
                SmsHandler.getInstance().smsSend(oaIn, "You need to SUBscribe before requesting readings.", SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Ignored SMS Read request from " + oaIn);
            }
        } else {
            System.out.println("[SubscriptionHandler]: sendReadingsBySMS() called but oaIn == null");
        }

        if (DEBUG) {System.out.println("[SubscriptionHandler]: -sendReadingsBySMS()");}
        
        return retVal;
    }

    public boolean editSubscriberTopics(String oaIn, String topicsIn) {
        boolean retVal = false;
        final boolean DEBUG = false;
        String topicsList;
        StringBuffer tmpMsg;
        int i;
        int requestedTopicMask;
        int start;

        if ((oaIn != null) && (topicsIn != null)) {
            if (findSubscriberBySMS(oaIn) != MAX_subscribersBySMS) {
                i = findSubscriberBySMS(oaIn);
                try {
                    start = topicsIn.indexOf("TOPIC") + 5;     // skip TOPIC key word
                    topicsList = topicsIn.substring(start).trim();
                        if (myExternalDeviceListening != null) {
                            requestedTopicMask = myExternalDeviceListening.parseTopicsText(topicsList);
                        } else {
                            System.out.println("[SubscriptionHandler]: Warning no ExternalDevice Subscription listener defined.");
                            requestedTopicMask = 0;
                        }
                    if (DEBUG) {System.out.println("[SubscriptionHandler]: requestedTopicMask = " + requestedTopicMask);}

                    topicsBySMS[i] = requestedTopicMask;
                    tmpMsg = new StringBuffer();
                    tmpMsg.append("Topics updated: ");

                    if (DEBUG) {System.out.println("[SubscriptionHandler]: i = " + i);}
                    if (DEBUG) {System.out.println("[SubscriptionHandler]: topicsBySMS[i] = " + topicsBySMS[i]);}

                    if ((myExternalDeviceListening != null) && (topicsBySMS[i] != myExternalDeviceListening.getTopicBitMaskALL())) {
                        tmpMsg.append(myExternalDeviceListening.listTopicDescripitiveTextByMask(topicsBySMS[i]));
                    } else {
                        tmpMsg.append(myExternalDeviceListening.listTopicDescripitiveTextAll());
                    }

                    tmpMsg.append(footerText);

                    SmsHandler.getInstance().smsSend(oaIn, tmpMsg.toString(), SmsHandler.CLASS0);
                    System.out.println("[SubscriptionHandler]: Accepted SMS Topic edit request from " + oaIn);
                } catch (Exception e) {System.out.println("[SubscriptionHandler]: " + e.toString());}
            } else {
                SmsHandler.getInstance().smsSend(oaIn, "You need to SUBscribe before editing topics.", SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Ignored SMS Topic editing request from " + oaIn);
            }
        } else {
            System.out.println("[SubscriptionHandler]: editSubscriberTopics() called but a parameter == null");
        }

        return retVal;
    }

    public boolean editSubscriberRate(String oaIn, String rateIn) {
        boolean retVal;
        
        // default behaviour is to reset the SMS send timer too, a 'false' below would not reset it
        retVal = editSubscriberRate(oaIn, rateIn, true);
        
        return retVal;
    }
    
    public boolean editSubscriberRate(String oaIn, String rateIn, boolean resetSMSsendTimer) {
        boolean retVal = false;
        final boolean DEBUG = false;
        StringBuffer tmpMsg;
        int i;
        int requestedRate;
        int start;
        String requestedRateTxt;

        if ((oaIn != null) && (oaIn.equals("") != true) && (oaIn.indexOf("+") != -1) && (rateIn != null)) {
            if (findSubscriberBySMS(oaIn) != MAX_subscribersBySMS) {
                i = findSubscriberBySMS(oaIn);
                requestedRate = DEFAULT_SEND_RATE;

                try {
                    start = rateIn.indexOf(REQ_SMSC + " ") + (REQ_SMSC + " ").length();     // skip RATE key word and space
                    requestedRateTxt = rateIn.substring(start).trim();

                    if (DEBUG) {System.out.println("[SubscriptionHandler]: requestedRateTxt = '" + requestedRateTxt + "'");}
                    requestedRate = Integer.parseInt(requestedRateTxt);
                } catch (Exception e) {System.out.println("[SubscriptionHandler]: Exception when parsing Rate value " + e.toString());}

                if (DEBUG) {System.out.println("[SubscriptionHandler]: requestedRate = " + requestedRate);}

                sendRateReqSMS[i] = requestedRate;
                if (resetSMSsendTimer == true) {sendRateCurSMS[i] = requestedRate;}

                tmpMsg = new StringBuffer();
                tmpMsg.append("Rate updated: every ");
                tmpMsg.append(sendRateReqSMS[i]);
                tmpMsg.append(" minutes ");

                if (DEBUG) {
                    System.out.println("[SubscriptionHandler]: i = " + i);
                    System.out.println("[SubscriptionHandler]: sendRateReqSMS[i] = " + sendRateReqSMS[i]);
                    System.out.println("[SubscriptionHandler]: sendRateCurSMS[i] = " + sendRateCurSMS[i]);
                }

                tmpMsg.append(footerText);

                SmsHandler.getInstance().smsSend(oaIn, tmpMsg.toString(), SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Accepted SMS Rate edit request from " + oaIn);
            } else {
                SmsHandler.getInstance().smsSend(oaIn, "You need to SUBscribe before editing send rate.", SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Ignored SMS Rate editing request from " + oaIn);
            }
        } else {
            System.out.println("[SubscriptionHandler]: editSubscriberRate() called but a parameter == null");
        }

        return retVal;
    }
    
    public boolean removeSubscriberBySMS(String oaIn) {
        boolean retVal = false;
        int i;

        if (oaIn != null) {
            if (findSubscriberBySMS(oaIn) != MAX_subscribersBySMS) {
                i = findSubscriberBySMS(oaIn);
                SmsHandler.getInstance().purgeSubsQueuedSmsMessages(oaIn);  // STOP => try to clear the outbound SMS queue
                SmsHandler.getInstance().smsSend(oaIn, "Removed.", SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Removed existing SMS subscriber: #" + i + " from " + oaIn);
                subscribersBySMS[i] = null;
                topicsBySMS[i] = 0;         // 0 => NONE
                retVal = true;
            } else {
                SmsHandler.getInstance().smsSend(oaIn, "Already removed.", SmsHandler.CLASS0);
                System.out.println("[SubscriptionHandler]: Ignored SMS Removal request from " + oaIn);
            }
        } else {
            System.out.println("[SubscriptionHandler]: removeSubscriberBySMS() called but oaIn==null");
        }

        return retVal;
    }

    public void sendReadingsToAllSubscribersBySMS() {
        final boolean DEBUG = false;
        int i;
        
        if (DEBUG) {System.out.println("[SubscriptionHandler]: +sendReadingsToAllSubscribersBySMS()");}
        
        if ((subscribersBySMS != null) && (sendRateReqSMS != null) && (sendRateCurSMS != null)) {
            for (i = 0; i < MAX_subscribersBySMS; i++) {        // loop through subscribers
                if ((subscribersBySMS[i] != null) && (sendRateReqSMS[i] > 0)) { // check if this slot has a listening subscriber
                    if (sendRateCurSMS[i] == 0) {               // check if his RATE timer has exired
                        sendReadingsBySMS(subscribersBySMS[i]); // if so then send requested TOPIC data
                        sendRateCurSMS[i] = sendRateReqSMS[i];  // reset his rate timer for next send
                    } else {
                        sendRateCurSMS[i] = sendRateCurSMS[i] - 1;  // else wait another minute
                        if (sendRateCurSMS[i] < 0) {sendRateCurSMS[i] = 0;} // sanity check, don't miss 0
                    }
                }
            }
        } else {
            System.out.println("[SubscriptionHandler]: sendReadingsToAllSubscribersBySMS() called but subscribersBySMS, sendRateReqSMS or sendRateCurSMS == null");
        }

        if (DEBUG) {System.out.println("[SubscriptionHandler]: -sendReadingsToAllSubscribersBySMS()");}
    }

    public void sendAdhocReadingsToAllSubscribersBySMS() {
        final boolean DEBUG = false;
        int i;
        
        System.out.println("[SubscriptionHandler]: Sending adhoc readings to all subscribers by SMS.");

        if (subscribersBySMS != null) {
            for (i = 0; i < MAX_subscribersBySMS; i++) {
                if (subscribersBySMS[i] != null) {
                    sendReadingsBySMS(subscribersBySMS[i]); // if so then send requested TOPIC data
                }
            }
        }
    }

    private void sendAdhocBroadcastToAllSubscribersBySMS(String msgIn) {
        sendAdhocBroadcastToAllSubscribersBySMS(msgIn, SmsHandler.CLASS0);
    }

    public void sendAdhocBroadcastToAllSubscribersBySMS(String msgIn, int classIn) {
        int i;
        
        if (msgIn != null) {
          for (i = 0; i < MAX_subscribersBySMS; i++) {
                if (subscribersBySMS[i] != null) {
                    SmsHandler.getInstance().smsSend(subscribersBySMS[i], msgIn, classIn);
                }
            }
        } else {
            System.out.println("[SubscriptionHandler]: sendAdhocBroadcastToAllSubscribersBySMS() called but msgIn==null");
        }
    }
    
    class MyPublishingThread extends Thread {
        final boolean DEBUG = false;

        public void run() {
            System.out.println("[SubscriptionHandler]: Publishing thread waiting to start.");
            
            while (SubscriptionHandler.continueEx == false) {
                try {Thread.sleep(5077);} catch (Exception e) {System.out.println("[SubscriptionHandler]: Exception " + e.toString());}
            }

            System.out.println("[SubscriptionHandler]: Publishing thread started.");
            
            while (SubscriptionHandler.continueEx == true) {
                if (DEBUG) {System.out.println("[SubscriptionHandler]: +MyPublishingThread()");}
                
                sendReadingsToAllSubscribersBySMS();

                // This is a thread, not a timer task, so it really ought to sleep here, for a prime number of milli-seconds
                try {Thread.sleep(60007);} catch (Exception e) {System.out.println("[SubscriptionHandler]: Exception " + e.toString());}

                if (DEBUG) {System.out.println("[SubscriptionHandler]: -MyPublishingThread()");}
            }
            
            System.out.println("[SubscriptionHandler]: continueEx is 'false' so MyPublishingThread() has finished.");
        }
    }        
}

// --------------------------------------------------------------------------- //
// https://www.ibm.com/developerworks/library/j-jtp07265/
// Guidelines for writing and supporting event listeners
// --------------------------------------------------------------------------- //

interface SubscriptionHandlerListener {
    public int getTopicBitMaskALL();
    public int getTopicBitMaskNONE();
    public String getBannerText();
    public String getShortTopicsList();
    public String getAdditionalCommandsList();
    public String getAdditionalTempText();
    
    public String getReadingsTextByTopic(int requestedTopicMask);
    public String getReadingsTextAll();
    public int parseTopicsText(String topicsList);
    public String listTopicDescripitiveTextByMask(int topicsBySMS);
    public String listTopicDescripitiveTextAll();
}
