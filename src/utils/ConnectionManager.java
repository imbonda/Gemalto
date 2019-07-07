package utils;

import com.cinterion.io.BearerControl;
import com.cinterion.misc.Watchdog2;
import java.util.Timer;
import java.util.TimerTask;

public class ConnectionManager implements BearerControlStatesExt {
    private static ConnectionManager instance = null;
    private static boolean continueEx = false;

    public static int houseKeepingTimerTicks = 0;
    private static Timer houseKeepingTimer = null;
    private static final int HOUSEKEEPING_DUTY_CYCLE = 58687;  // milliseconds between checks for House Keeping status
    private static boolean houseKeepingIsHappy = false;        // The Watchdog kicking process watches this closely
    public static final int RECOVERY_TIME = 37117;             // milliseconds for C(G)REG state to recover, once we detect it is bad

    public static Watchdog2 wd = null;                         // EHSx series watchdog; must be stopped in destroyApp
    public static final int WATCHDOG2_DELAY = 239;             // maximum seconds allowed between "kicks". 0 = disabled    
    private static Timer watchdogTimer = null;
    private static final int WATCHDOG2_KICK_CYCLE = 31357;     // milliseconds between checks for whether or not to "kick"
//  private final int WD_ACTION = 0;    // The Watchdog2 would do nothing
    private final int WD_ACTION = 1;    // The Watchdog2 will restart the system
//  private final int WD_ACTION = 2;    // The Watchdog2 would shutdown the system

    private static final boolean ACCEPT_CREG3 = false;      // network registration (true accepts CREG=3 state as OK)
    private static final int NUMATTEMPTSFIRSTREG = 23;      // network registration attempts when booting
    private static final int GRACEPERIODFIRSTREG = 7229;    // network registration throttling (ms)
    
    public static final String preferedSXRATsetting = "1,2";   // for EHS5, EHS6 and EHS8    (GSM/UMTS, prefer UMTS)
//  public static final String preferedSXRATsetting = "5,3";   // for ELS61-E  and ELS61-E2  (GSM/LTE, prefer LTE)
//  public static final String preferedSXRATsetting = "4,3";   // for ELS61-US and ELS61-AUS (UMTS/LTE, prefer LTE)
    
    public static final boolean CHECK_CREG  = true;        // runtime check for valid AT+CREG status
    public static final boolean CHECK_CGREG = true;        // runtime check for valid AT+CGREG status
    public static final boolean CHECK_SMONI = false;       // runtime check for valid AT^SMONI status
    public static final boolean CHECK_BEARER = false;      // runtime check for valid BearerConnection UP status
    public static final boolean CHECK_EXTDEV = true;       // runtime check for valid ExternalDevice status

    private static int bearerConnectionState = BearerControl.BEARER_STATE_UNKNOWN;
    public static ConnectionManagerListener myExternalDeviceListening = null;  // Allow one ConnectionManager listener to register with us
    
    // Requested SMSes for Connection Manager
    public static final String REQ_SMS1 = "CONNMAN ON";     
    public static final String REQ_SMS2 = "CONNMAN OFF";
    public static final String REQ_SMS3 = "__SOMETHING__";
    
    ConnectionManager () {
        // ignore
    }

    // Method to return the proper instance of the singleton class
    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager(); // if instance doesn't exist - create one
            instance.init();                    // initialize the instance
        }
        return instance;                        // returns the proper instance
    }

    // Initialises ConnectionManager object
    private void init() {
        // ignore
    }
    
    public void release() {
        continueEx = false;

        NetworkMonitor.getInstance().release();

        if     (watchdogTimer != null) {watchdogTimer.cancel();}
        if (houseKeepingTimer != null) {houseKeepingTimer.cancel();}
        houseKeepingIsHappy = false;

        if (wd != null) {       // EHSx series Watchdog is defined, so assume it is enabled
            AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
            AtcHandler.getInstance().sendATC("AT^SCFG=\"Userware/Watchdog\",\"0\"\r");
            System.out.println("[ConnectionManager]: Watchdog will do nothing if triggered.");
            
            // The Watchdog2 will do nothing now, but don't forget also:
            // The application has to disable the watchdog when closing down (i.e. in its destroyApp method.)
            // This is especially necessary for OTAP to work correctly.
            try {wd.start(0);}  // Value of 0 to switch off...
            catch (Exception e) {System.out.println("[ConnectionManager]: Watchdog Stop Exception " + e.toString());}
        }
    }
    
    // if (ConnectionManager.getInstance().succesfulNetworkRegistration()) {
    // ConnectionManager.getInstance().start();}
    public void start() {
        continueEx = true;
        
        startWatchDogThread();
        startHouseKeepingThread();
        
        NetworkMonitor.getInstance().start();   // Reporting on network cells, operator signal strength etc.
    }
    
    public boolean incomingCallDetected(String callIn) {
        boolean retVal = false;
        
        if (callIn != null) {
            if (callIn.toUpperCase().indexOf("RING") > -1) {
                retVal = true;
            }
        }
        
        return retVal;
    }

    public boolean incomingCallParse(String callIn) {
        final boolean DEBUG = false;
        String response;
        String clid = null;
        boolean retVal = false;
        
        // Incoming phone call - '+CIEV: ciphcall,1' / '+CRING: VOICE'
        // +CLCC: 1,1,4,0,0,"07918940xxx",129
        
        response = AtcHandler.getInstance().sendATC("AT+CLCC\r");
        if ((response != null) && (response.indexOf("+CLCC") > -1) && (response.indexOf(",\"") > -1) && (response.indexOf("\",") > -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {
                System.out.println("[ConnectionManager]: incomingCallParse() " + response.indexOf(",\""));
                System.out.println("[ConnectionManager]: incomingCallParse() " + response.indexOf("\","));
            }
            try {
                clid = response.substring(response.indexOf(",\"") + (",\"").length(), response.indexOf("\","));
                System.out.println("[ConnectionManager]: RING incoming call from '" + clid.trim() + "'");
                if (ConnectionManager.myExternalDeviceListening != null) {
                    ConnectionManager.myExternalDeviceListening.incomingCallDetected(clid.trim());
                } else {
                    System.out.println("[ConnectionManager]: Warning: no External Device listener defined - RING.");
                }
                retVal = true;
            } catch (Exception e) {System.out.println("[ConnectionManager]: Exception in incomingCallParse() " + e.toString());}

            // We could start answering calls and so on here...
            // sendATC("ATA\r");
        }

        return retVal;
    }
    
    //	Method waits for a meaningful network registration or failure
    public boolean succesfulNetworkRegistration() {
        final boolean DEBUG = false;
        boolean retVal;
        String response = "";
        
        // First let us check that we have a SIM inserted and can read the IMSI from it, else STOP
        // We assume that AT+CMEE=2 was already sent elsewhere before we start the AT+CIMI request
        response = AtcHandler.getInstance().sendATC("AT+CIMI\r");
        if ((response.indexOf("ERROR") == -1) && (response.indexOf("OK") > -1)) {
            response = response.substring(response.indexOf('\n'), response.indexOf("OK")-2).trim();
            if (DEBUG) {System.out.println("[ConnectionManager]: IMSI " + response);}
            
            // Next let us check that we are not in AIRPLANE MODE
            response = AtcHandler.getInstance().sendATC("AT+CFUN?\r");
            if ((response.indexOf("+CFUN: 4") > -1) && (response.indexOf("OK") > -1)) {
                response = AtcHandler.getInstance().sendATC("AT+CFUN=1\r");
                if (DEBUG) {System.out.println("[ConnectionManager]: Switch off Airplane Mode requested " + response);}
            }
            
            // Next let us start network registration waiting loop
            System.out.println("[ConnectionManager]: Waiting for Network Registration...");
            AtcHandler.getInstance().sendATC("AT+CREG=2\r");
            for (int i = 0; i < NUMATTEMPTSFIRSTREG; i++) {
                //GpioHandler.getInstance().setLedState(GpioHandler.LED_ON);

                response = AtcHandler.getInstance().sendATC("AT+CREG?\r");
                if ((response.indexOf("+CREG: ") > -1) && (response.indexOf("OK") > -1)) {
                    response = response.substring(response.indexOf("+CREG:"), response.indexOf("OK")-2).trim();
                }
                if (DEBUG) {System.out.println("[ConnectionManager]: Network registration status " + response);
                } else {
                    System.out.print(".");
                }

                if ((response.indexOf(" 2,1") > -1) || (response.indexOf(" 2,5") > -1) ||
                   ((response.indexOf(" 2,3") > -1) && ACCEPT_CREG3) ) {
                    i = NUMATTEMPTSFIRSTREG;            // break out of loop
                } else {
                    //GpioHandler.getInstance().setLedState(GpioHandler.LED_OFF);
                    try {Thread.sleep(GRACEPERIODFIRSTREG);} catch (Exception e) {System.out.println("[ConnectionManager]: Exception " + e.toString());}
                }
            }
            System.out.println(".");
            
            // Now let us check if any of the BAD KNOWN states are in AT+CREG? response
            if  (((response.indexOf(" 2,0") >  -1) || (response.indexOf(" 2,2")  > -1) ||    // Given up or still searching or
                  (response.indexOf(" 2,3") >  -1) || (response.indexOf("ERROR") > -1) ||    // Barred or ERROR state and
                 ((response.indexOf(" 2,1") == -1) && (response.indexOf(" 2,5") == -1)))) {  // Not in a home or roaming registered state

                // We are in a bad state so print the CREG state to stdio
                response = AtcHandler.getInstance().sendATC("AT+CREG?\r");
                if ((response.indexOf("+CREG: ") > -1) && (response.indexOf("OK") > -1)) {
                    response = response.substring(response.indexOf("+CREG:"), response.indexOf("OK")-2).trim();
                }
                System.out.println("[ConnectionManager]: Network registration failed " + response);
                //GpioHandler.getInstance().setLedState(GpioHandler.LED_OFF);
                
                // We are in a bad state so ask "Why?"
                response = AtcHandler.getInstance().sendATC("AT+CEER\r");
                if ((response.indexOf("+CEER: ") > -1) && (response.indexOf("OK") > -1)) {
                    response = response.substring(response.indexOf("+CEER: "), response.indexOf("OK")-2).trim();
                }
                System.out.println("[ConnectionManager]: " + response);
                
                // We are in a bad state so set AT+COPS to auto operator selection
                // Note that Intel platform (BGS5/EHSx/ELS61-xs) returns AT+COPS=2 when off network
                response = AtcHandler.getInstance().sendATC("AT+COPS?\r");
                if ((response.indexOf("+COPS: 2") == -1) && (response.indexOf("OK") > -1)) {
                    response = AtcHandler.getInstance().sendATC("AT+COPS=0\r");
                    if (DEBUG) {System.out.println("[ConnectionManager]: Auto network operator selection requested " + response);}
                }

                retVal = false;     // show a failed network registration
            } else {
                System.out.println("[ConnectionManager]: Network registration successful.");
                //GpioHandler.getInstance().setLedState(GpioHandler.LED_ON);

                retVal = true;      // not in a bad registration state so must be good
            }
        } else {
            System.out.println("[ConnectionManager]: Reading of SIM's IMSI failed " + response);
            //GpioHandler.getInstance().setLedState(GpioHandler.LED_OFF);
            retVal = false;         // SIM error; show a failed network registration
        }
        
        return retVal;
    }    

    public void addConnManListener(ConnectionManagerListener aConnManListenerIn) {
        if (aConnManListenerIn != null) {
            myExternalDeviceListening = aConnManListenerIn;
            System.out.println("[ConnectionManager]: addConnManListener() ExternalDevice listener defined.");
        }
    }
    
    public static void setHouseKeepingState(boolean stateIn) {
        houseKeepingIsHappy = stateIn;
    }
    
    public static boolean houseKeepingIsHappy() {
        return (houseKeepingIsHappy);
    }

    private void startWatchDogThread() {
        if (wd == null) {                   // no watch dog has been defined yet
            if (WATCHDOG2_DELAY != 0) {
                AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
                AtcHandler.getInstance().sendATC("AT^SCFG=\"Userware/Watchdog\",\"" + WD_ACTION + "\"\r");
                System.out.println("[ConnectionManager]: Watchdog will restart the system if triggered.");

                wd = new Watchdog2();

                if (wd != null) {                           // Valid values are 0 to switch off, or between 10 and 300 to set watchdog time-out.
                    try {wd.start(WATCHDOG2_DELAY);}        // The number of seconds after which the watchdog will trigger.
                    catch (Exception e) {System.out.println("[ConnectionManager]: Watchdog Start Exception " + e.toString());}
                } else {
                    System.out.println("[ConnectionManager]: Warning: startWatchDogThread() unable to create Watchdog2.");
                }

                watchdogTimer = new Timer();
                
                if (watchdogTimer != null) {
                    watchdogTimer.schedule(new WatchdogKickingTimerTask(), 1000, WATCHDOG2_KICK_CYCLE);
                } else {
                    System.out.println("[ConnectionManager]: Warning: startWatchDogThread() unable to create watchdogTimer.");
                }
            } else {
                System.out.println("[ConnectionManager]: Warning: startWatchDogThread() called but WATCHDOG2_DELAY==0.");
            }
        } else {
            System.out.println("[ConnectionManager]: Warning: startWatchDogThread() called but WatchDog already running.");
        }
    }
    
    private void startHouseKeepingThread() {
        if (houseKeepingTimer == null) {        // no house keeping task has been defined yet
            houseKeepingTimer = new Timer();
            
            if (houseKeepingTimer != null) {    // Start HouseKeeping thread after one more minute
                houseKeepingTimer.schedule(new HouseKeepingTimerTask(), 63533, HOUSEKEEPING_DUTY_CYCLE);
                houseKeepingIsHappy = true;
                System.out.println("[ConnectionManager]: houseKeepingTimer started.");
            } else {
                System.out.println("[ConnectionManager]: Warning: startHouseKeepingThread() unable to create houseKeepingTimer.");
            }
        } else {
            System.out.println("[ConnectionManager]: Warning: startHouseKeepingThread() called but houseKeepingTimer already running.");
        }
    }

    public void setBearerConnectionState(int stateIn) {
        if ((stateIn == BearerControl.BEARER_STATE_CLOSING) || 
            (stateIn == BearerControl.BEARER_STATE_CONNECTING) || 
            (stateIn == BearerControl.BEARER_STATE_DOWN) || 
            (stateIn == BearerControl.BEARER_STATE_LIMITED_UP) || 
            (stateIn == BearerControl.BEARER_STATE_UNKNOWN) || 
            (stateIn == BearerControl.BEARER_STATE_UP)) {
            bearerConnectionState = stateIn;
        }
    }

    public static boolean bearerConnectionStateIsUP() {
        boolean retVal = false;

        if (bearerConnectionState == BearerControl.BEARER_STATE_UP) {
            retVal = true;
        } else {
            System.out.println("[ConnectionManager]: BearerControl - BEARER_STATE is not UP.");
        }

        return retVal;            
    }
}

// --------------------------------------------------------------------------- //

class WatchdogKickingTimerTask extends TimerTask {
    private final boolean DEBUG = true;
    private final boolean VERBOSE = true;

    public void run() {
        if ((ConnectionManager.wd != null) &&
            (ConnectionManager.WATCHDOG2_DELAY != 0) &&
            (ConnectionManager.houseKeepingIsHappy())) {
            
            try {ConnectionManager.wd.kick();} catch (Exception e) {System.out.println("[ConnectionManager]: Watchdog Kick Exception " + e.toString());}

            System.out.println("[ConnectionManager]: Kicked watchdog. Running threads: " + HardwareManager.readEHSxThreadsRunning());
        } else {
            System.out.println("[ConnectionManager]: Warning: houseKeeping not happy so watchdog not kicked. " + HardwareManager.readEHSxThreadsRunning());
        }
    }
}

// --------------------------------------------------------------------------- //

class HouseKeepingTimerTask extends TimerTask {
    private final boolean DEBUG = false;
    private String response = null;

    public void run() {
        long freeMem;
        final boolean RUN_DEBUG = false;

        // Update the "up time", it will be zeroed if anything below leads to network re-registration
        ConnectionManager.houseKeepingTimerTicks++;
        
        // Moved, from SMS handler, to here to be a little less chatty on AT Command frequent usage
        SmsHandler.checkSMSnotificaitons();         // ensure we are ready to receive Text based SMSes

        // Optionally report on Free memory and AT^SMONI repsonse
        if (RUN_DEBUG) {
            freeMem = (Runtime.getRuntime().freeMemory() / 1024 / 1024);
            System.out.println("[ConnectionManager]: Free memory = " + freeMem + "MB");
            
            System.out.println("[ConnectionManager]: " + NetworkMonitor.readEHSxSMONI());
        }

        // AT^SMONI - check that we do not have SEARCH and do have NOCONN, else wait a few seconds and ask again
        // AT+CREG? - check that we have a 1 or 5 state, else wait a few seconds and ask again
        // AT+CGREG? - check that we have a 1 or 5 state, else wait a few seconds and ask again

        ConnectionManager.setHouseKeepingState(true);
        if (ConnectionManager.CHECK_SMONI)  {if (smoniStateNoConnection() == false) {ConnectionManager.setHouseKeepingState(false);}}
        if (ConnectionManager.CHECK_CREG)   {if (cregStateRegistered() == false) {ConnectionManager.setHouseKeepingState(false);}}
        if (ConnectionManager.CHECK_CGREG)  {if (cgregStateAttached() == false) {ConnectionManager.setHouseKeepingState(false);}}
        if (ConnectionManager.CHECK_BEARER) {if (ConnectionManager.bearerConnectionStateIsUP() == false) {ConnectionManager.setHouseKeepingState(false);}}

        // We will not react immediately, instead wait to see if the situation passes
        if (ConnectionManager.houseKeepingIsHappy() == false) {
            try {Thread.sleep(ConnectionManager.RECOVERY_TIME);} catch (Exception e) {System.out.println("[ConnectionManager]: Exception " + e.toString());}

            ConnectionManager.setHouseKeepingState(true);
            if (ConnectionManager.CHECK_SMONI)  {if (smoniStateNoConnection() == false) {ConnectionManager.setHouseKeepingState(false);}}
            if (ConnectionManager.CHECK_CREG)   {if (cregStateRegistered() == false) {ConnectionManager.setHouseKeepingState(false);}}
            if (ConnectionManager.CHECK_CGREG)  {if (cgregStateAttached() == false) {ConnectionManager.setHouseKeepingState(false);}}
            if (ConnectionManager.CHECK_BEARER) {if (ConnectionManager.bearerConnectionStateIsUP() == false) {ConnectionManager.setHouseKeepingState(false);}}
        }

        // If the situation continues to be bad then ask AT+CEER why and try to re-register to network
        if (ConnectionManager.houseKeepingIsHappy() == false) {
            askWhy();
            retryNetworkRegistration();
            System.gc();

            ConnectionManager.setHouseKeepingState(true);
            if (ConnectionManager.CHECK_SMONI)  {if (smoniStateNoConnection() == false) {ConnectionManager.setHouseKeepingState(false);}}
            if (ConnectionManager.CHECK_CREG)   {if (cregStateRegistered() == false) {ConnectionManager.setHouseKeepingState(false);}}
            if (ConnectionManager.CHECK_CGREG)  {if (cgregStateAttached() == false) {ConnectionManager.setHouseKeepingState(false);}}
            if (ConnectionManager.CHECK_BEARER) {if (ConnectionManager.bearerConnectionStateIsUP() == false) {ConnectionManager.setHouseKeepingState(false);}}
        }

        if (ConnectionManager.houseKeepingIsHappy() == true) {
            if (ConnectionManager.CHECK_EXTDEV) {
                if (ConnectionManager.myExternalDeviceListening != null) {
                    // If all our required network checks are OK, check to see if SensorLogic / SocketListener / etc. are OK
                    ConnectionManager.setHouseKeepingState(ConnectionManager.myExternalDeviceListening.dependentDeviceIsHappy());
                } else {
                    // ExternalDevice.java can register it's ConnectionManagerListener{} interface using ConnMan's addConnManListener() method
                    System.out.println("[ConnectionManager]: Warning: no External Device listener defined.");
                }
            }

            if (ConnectionManager.houseKeepingIsHappy() == false) {
                System.out.println("[ConnectionManager]: Warning: External Device not happy so houseKeeping not happy.");
            }
        }

        if (RUN_DEBUG) {System.out.println("");}
    }

    private boolean cregStateRegistered() {
        boolean retVal;

        AtcHandler.getInstance().sendATC("AT+CREG=2\r");
        response = AtcHandler.getInstance().sendATC("AT+CREG?\r");
        if ((response.indexOf("+CREG: ") > -1) && (response.indexOf("OK") > -1)) {
            response = response.substring(response.indexOf("+CREG:"), response.indexOf("OK")-2).trim();
        }
        if (DEBUG) {System.out.println("[ConnectionManager]: Network registration status " + response);}

        // AT+CREG? - check that we have a 1 or 5 state
        if (((response.indexOf(" 2,0") > -1)  || (response.indexOf(" 2,2") > -1) ||     // Given up or still searching or
             (response.indexOf(" 2,3") > -1)  || (response.indexOf(" 2,4") > -1) ||     // Barred or Unknown or 
             (response.indexOf("ERROR") > -1) ||                                        // ERROR state and
            ((response.indexOf(" 2,1") == -1) && (response.indexOf(" 2,5") == -1)))) {  // Not in a home or roaming registered state
            retVal = false;                 // Not in a home or roaming registered state
            System.out.println("[ConnectionManager]: CS Network - Not in a home or roaming registered state.");
        } else {
            retVal = true;                  // In a home or roaming registered state
        }

        return retVal;            
    }

    private boolean cgregStateAttached() {
        boolean retVal;

        AtcHandler.getInstance().sendATC("AT+CGREG=2\r");
        response = AtcHandler.getInstance().sendATC("AT+CGREG?\r");
        if ((response.indexOf("+CGREG: ") > -1) && (response.indexOf("OK") > -1)) {
            response = response.substring(response.indexOf("+CGREG:"), response.indexOf("OK")-2).trim();
        }
        if (DEBUG) {System.out.println("[ConnectionManager]: Network attach status " + response);}

        // AT+CREG? - check that we have a 1 or 5 state
        if (((response.indexOf(" 2,0") > -1)  || (response.indexOf(" 2,2") > -1) ||      // Given up or still searching or
             (response.indexOf(" 2,3") > -1)  || (response.indexOf(" 2,4") > -1) ||      // Barred or Unknown or 
             (response.indexOf("ERROR") > -1) ||                                         // ERROR state and
            ((response.indexOf(" 2,1") == -1) && (response.indexOf(" 2,5") == -1)))) {   // Not in an attached state                
            retVal = false;                 // Not in attached state
            System.out.println("[ConnectionManager]: PS Network - Not in attached state.");
        } else {
            retVal = true;                  // Attached state
        }

        return retVal;            
    }

    // AT^SMONI and AT^SMONP can clash (causing a hang) if issued concurrently on different AT command instances
    private boolean smoniStateNoConnection() {
        boolean retVal;

        response = AtcHandler.getInstance().sendATC("AT^SMONI\r");
        if ((response.indexOf("^SMONI: ") > -1) && (response.indexOf("OK") > -1)) {
            response = response.substring(response.indexOf("^SMONI:"), response.indexOf("OK")-2).trim();
        }
        if (DEBUG) {System.out.println("[ConnectionManager]: AT^SMONI status " + response);}

        // AT^SMONI - check that we do not have SEARCH and we do have NOCONN
        if ((response.indexOf("SEARCH") > -1) || (response.indexOf("ERROR") > -1) ||    // SEARCH or ERROR state and
            (response.indexOf("NOCONN") == -1)) {                                       // Not in an attached state                
            retVal = false;                 // Not in NOCONN state
            System.out.println("[ConnectionManager]: AT^SMONI - Not in NOCONN state.");
        } else {
            retVal = true;                  // Attached state
        }

        return retVal;            
    }

    private void retryNetworkRegistration() {
        final boolean useAirplaneMode = true;

        System.out.println("[ConnectionManager]: Network registration retry in progress... (" + ConnectionManager.houseKeepingTimerTicks + ")");

        // First let us check that we have a SIM inserted and can read the IMSI from it, else STOP
        response = AtcHandler.getInstance().sendATC("AT+CIMI\r");
        if ((response.indexOf("ERROR") == -1) && (response.indexOf("OK") > -1)) {
            response = response.substring(response.indexOf('\n'), response.indexOf("OK")-2).trim();
            if (DEBUG) {System.out.println("[ConnectionManager]: IMSI " + response);}

            // Toggle AirplaneMode ON or simply detatch from network
            if (useAirplaneMode) {
                response = AtcHandler.getInstance().sendATC("AT+CFUN=4\r");
                if ((response.indexOf("AT+CFUN") > -1) && (response.indexOf("OK") > -1)) {
                    response = response.substring(response.indexOf("AT+CFUN"), response.indexOf("OK")-2).trim();
                }
            } else {
                response = AtcHandler.getInstance().sendATC("AT+COPS=2\r");
                if ((response.indexOf("AT+COPS") > -1) && (response.indexOf("OK") > -1)) {
                    response = response.substring(response.indexOf("AT+COPS"), response.indexOf("OK")-2).trim();
                }
            }            
            if (DEBUG) {System.out.println("[ConnectionManager]: " + response);}
            try {Thread.sleep(2007);} catch (Exception e) {System.out.println("[ConnectionManager]: Exception " + e.toString());}

            // Toggle AirplaneMode back OFF
            if (useAirplaneMode) {
                response = AtcHandler.getInstance().sendATC("AT+CFUN=1\r");
                if ((response.indexOf("AT+CFUN") > -1) && (response.indexOf("OK") > -1)) {
                    response = response.substring(response.indexOf("AT+CFUN"), response.indexOf("OK")-2).trim();
                }
                if (DEBUG) {System.out.println("[ConnectionManager]: " + response);}
            }

            // In Airplane mode (AT+CFUN=4) the AT^SXRAT read and test command can be used, but not the write command.

            // Check that a bad RaT setting has not crippled us
            response = AtcHandler.getInstance().sendATC("AT^SXRAT?\r");
            if ((response.indexOf("^SXRAT: ") > -1) && (response.indexOf("OK") > -1)) {
                response = response.substring(response.indexOf("^SXRAT:"), response.indexOf("OK")-2).trim();
            }
            if (DEBUG) {System.out.println("[ConnectionManager]: RaT status (AT^SXRAT) " + response);}

            // AT^SXRAT - check that we have a 1,2 state else reset AT^SXRAT to 1,2 which is 2G or 3G with a preference for 3G
            if (response.indexOf(" " + ConnectionManager.preferedSXRATsetting) == -1) {
                AtcHandler.getInstance().sendATC("AT^SXRAT=" + ConnectionManager.preferedSXRATsetting + "\r");
            }

            // Allow the module to do an automatic Operator Selection
            response = AtcHandler.getInstance().sendATC("AT+COPS=0\r");
            if ((response.indexOf("AT+COPS") > -1) && (response.indexOf("OK") > -1)) {
                response = response.substring(response.indexOf("AT+COPS"), response.indexOf("OK")-2).trim();
            }
            if (DEBUG) {System.out.println("[ConnectionManager]: " + response);}
            System.out.println("[ConnectionManager]: Network registration retry complete.");
        } else {
            //SIM error; no point in trying re-registration
            System.out.println("[ConnectionManager]: Reading of SIM's IMSI failed " + response);
        }

        ConnectionManager.houseKeepingTimerTicks = 0;
    }

    private void askWhy() {
        response = AtcHandler.getInstance().sendATC("AT+CEER\r");
        if ((response.indexOf("+CEER: ") > -1) && (response.indexOf("OK") > -1)) {
            response = response.substring(response.indexOf("+CEER: "), response.indexOf("OK")-2).trim();
        }
        if (DEBUG) {System.out.println("[ConnectionManager]: " + response);}            
    }
}

// --------------------------------------------------------------------------- //
// https://www.ibm.com/developerworks/library/j-jtp07265/
// Guidelines for writing and supporting event listeners
// --------------------------------------------------------------------------- //

interface ConnectionManagerListener {
    public boolean dependentDeviceIsHappy();            // External device can veto the IsHappy system status
    public boolean incomingCallDetected(String clid);   // ConnectionManager can tell ExternalDevice that there is an incoming phone call
}
