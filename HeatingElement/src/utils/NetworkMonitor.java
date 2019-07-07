package utils;

import java.util.Timer;
import java.util.TimerTask;

public class NetworkMonitor {
    private static NetworkMonitor instance = null;
    private static boolean continueEx = false;

    public static final int rat2G = 0;              // Siemens / Cinterion / Gemalto module RaT number for 2G
    public static final int rat3G = 2;
    public static final int rat4G = 7;
    
    private static String mobile_Network_Name = ""; public static final int OPERATOR = 0;
    private static int    networkRaT = 0;           public static final int RAT = OPERATOR + 1;
    private static String signal_Strength_pc = "";  public static final int SIGNAL = RAT + 1;
    private static String mobile_Country_Code = ""; public static final int MCC = SIGNAL + 1;
    private static String mobile_Network_Code = ""; public static final int MNC = MCC + 1;
    private static String location_Area_Code = "";  public static final int LAC = MNC + 1;
    private static String cell_ID = "";             public static final int CID = LAC + 1;

    private static final int MAX_numCELLs = 32;     // Number of, cell related, information items we will cache
    private static int[] cellRAT;                   // an array of Radio Access Technology
    private static int[] cellMCC;                   // an array of Mobile Country Code
    private static int[] cellMNC;                   // an array of Mobile Network Code
    private static int[] cellLAC;                   // an array of Location Area Code
    private static int[] cellID;                    // an array of Cell ID
    private static int[] cellSignal;                // an array of Cell Signal Strengths
    private static long[] agingVal;                 // an array of systemTime() (ms) to aid with aging data
        
    public static int monitoringTimerTicks = 0;
    private static Timer monitoringTimer = null;
    private static final int MONITORING_DUTY_CYCLE = 84977;  // milliseconds between checks for net status
    
    NetworkMonitor () {
        // ignore
    }

    // Method to return the proper instance of the singleton class
    public static NetworkMonitor getInstance() {
        if (instance == null) {
            instance = new NetworkMonitor();    // if instance doesn't exist - create one
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
        
        if (monitoringTimer != null) {monitoringTimer.cancel();}
        
        cellRAT = null;
        cellMCC = null;
        cellMNC = null;
        cellLAC = null;
        cellID = null;
        agingVal = null;
    }
    
    public void start() {
        final boolean DEBUG = false;
        continueEx = true;

        // We need to tell AT^SNMON to start monitoring up to 6 neighbourhood cells for all RaTs
        String response = AtcHandler.getInstance().sendATC("AT^SNMON=\"NBC/CFG/3GPP\",1,\"ALL\",6\r");
        if (DEBUG) {
            System.out.println("[NetworkMonitor]: start() AT^SNMON=\"NBC/CFG/3GPP\",1,\"ALL\",6");
            System.out.println(response);
        }
                
        try {
            cellRAT = new int[MAX_numCELLs];
            cellMCC = new int[MAX_numCELLs];
            cellMNC = new int[MAX_numCELLs];
            cellLAC = new int[MAX_numCELLs];
            cellID = new int[MAX_numCELLs];
            agingVal = new long[MAX_numCELLs];
        } catch (Exception e) {System.out.println("[NetworkMonitor]: start() " + e.toString());}
                
        startMonitoringTimerTask();
    }
    
    private void startMonitoringTimerTask() {
        if (monitoringTimer == null) {        // no house keeping task has been defined yet
            monitoringTimer = new Timer();
            
            if (monitoringTimer != null) {    // Start HouseKeeping thread after one more minute
                monitoringTimer.schedule(new NetworkMonitorTimerTask(), 65239, MONITORING_DUTY_CYCLE);
                System.out.println("[NetworkMonitor]: monitoringTimer started.");
            } else {
                System.out.println("[NetworkMonitor]: Warning: startMonitoringThread() unable to create monitoringTimer.");
            }
        } else {
            System.out.println("[NetworkMonitor]: Warning: startMonitoringThread() called but monitoringTimer already running.");
        }
    }

    public static String getOperator() {
        String retVal = getNetworkDetails(OPERATOR);
        return retVal;
    }
    
    // Attempts to read fresh values - used by own WatchDog thread below
    public static String readEHSxOperator() {
        final boolean DEBUG = false;        
        String retVal = mobile_Network_Name;        // If we can't get new value then reuse old value
        String response;
        
        response = AtcHandler.getInstance().sendATC("AT+COPS?\r");
        
        if ((response.indexOf("+COPS: 0,0,") > -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[NetworkMonitor]: AT+COPS? - " + response);}
            
            try {
                mobile_Network_Name = response.substring(response.indexOf("+COPS: 0,0,") + "+COPS: 0,0,".length(), response.indexOf("OK")-2).trim();
                retVal = mobile_Network_Name;
            }
            catch (Exception e) {System.out.println("[NetworkMonitor]: Exception in readEHSxOperator() " + e.toString());}
        }
        
        if (DEBUG) {System.out.println("[NetworkMonitor]: mobile_Network_Name = " + mobile_Network_Name);}
        
        return retVal;
    }

    // Nice lightweight return of cached values
    public static String getNetworkDetails(int detailIn) {
        final boolean DEBUG = false;
        String retVal = "";
        
        switch(detailIn) {
            case OPERATOR: retVal = mobile_Network_Name;
                break;
            case RAT:    retVal = "" + networkRaT;
                break;
            case SIGNAL: retVal = signal_Strength_pc;
                break;
            case MCC:    retVal = mobile_Country_Code;
                break;
            case MNC:    retVal = mobile_Network_Code;
                break;
            case LAC:    retVal = location_Area_Code;
                break;
            case CID:    retVal = cell_ID;
                break;
        }
        
        return retVal;
    }
    
    // Attempts to read fresh values - used by own WatchDog thread below
    public static String readEHSxNetworkDetails() {
        String retVal;
        
        retVal = readEHSxSMONI();   // Serving cell
        readEHSxSMONP();            // Old way to get neighbourhood cells, but not working in 3G
        readEHSxSNMON_NBC();        // New way (since EHSx rel 3 arn33) to get neighbourhood cells

        return retVal;
    }
    
    // AT^SMONI and AT^SMONP can clash (causing a hang) if issued concurrently on different AT command instances
    public static String readEHSxSMONI() {
        final boolean DEBUG = false;
        String retVal = "";
        String response;
        
        response = AtcHandler.getInstance().sendATC("AT^SMONI\r");

        if ((response.indexOf("^SMONI: ") > -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[NetworkMonitor]: AT^SMONI - " + response);}

            if ((response.indexOf(" 2G,") > -1) && (response.indexOf("NOCONN") > -1)) {
                // ^SMONI: ACT,ARFCN,BCCH,MCC,MNC,LAC,cell,C1,C2,NCC,BCC,GPRS,ARFCN,TS,timAdv,dBm,Q,ChMod
                // ^SMONI: 2G,71,-61,262,02,0143,83BA,33,33,3,6,G,NOCONN
                try {
                    response = response.substring(response.indexOf(",") + 1);
                    signal_Strength_pc = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: signal_Strength_pc (BCCH) = " + signal_Strength_pc);}

                    networkRaT = rat2G;
                    
                    response = response.substring(response.indexOf(",") + 1);
                    mobile_Country_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: mobile_Country_Code = " + mobile_Country_Code);}

                    response = response.substring(response.indexOf(",") + 1);
                    mobile_Network_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: mobile_Network_Code = " + mobile_Network_Code);}

                    response = response.substring(response.indexOf(",") + 1);
                    location_Area_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: location_Area_Code = " + location_Area_Code);}

                    response = response.substring(response.indexOf(",") + 1);
                    cell_ID = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: cell_ID = " + cell_ID);}
                }
                catch (Exception e) {System.out.println("[NetworkMonitor]: Exception in readEHSxNetworkDetails(2G) " + e.toString());}
            }
            
            if ((response.indexOf(" 3G,") > -1) && 
                ((response.indexOf("NOCONN") > -1) || (response.indexOf(",--,--,----,---,-,") > -1))) {
                // Columns for UMTS (3G) Serving Cell parameters:
                // EC/n0 Carrier to noise ratio in dB
                // RSCP Received Signal Code Power in dBm
                // ^SMONI: ACT,UARFCN,PSC,EC/n0,RSCP,MCC,MNC,LAC,cell,SQual,SRxLev,PhysCh, SF,Slot,EC/n0,RSCP,ComMod,HSUPA,HSDPA
                // ^SMONI: 3G,10564,296,-7.5,-79,262,02,0143,00228FF,-92,-78,NOCONN
                // ^SMONI: 3G,10661,3,-3.0,-73,234,10,0713,3D60F79,--,--,----,---,-,-3.0,-73,0,11,11 - also seen in EHSx rel 3 arn51
                try {
                    response = response.substring(response.indexOf(",") + 1);
                    response = response.substring(response.indexOf(",") + 1);
                    signal_Strength_pc = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));

                    response = response.substring(response.indexOf(",") + 1);
                    signal_Strength_pc = signal_Strength_pc + "," + response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: signal_Strength_pc (EC/n0,RSCP) = " + signal_Strength_pc);}

                    networkRaT = rat3G;
                    
                    response = response.substring(response.indexOf(",") + 1);
                    mobile_Country_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: mobile_Country_Code = " + mobile_Country_Code);}

                    response = response.substring(response.indexOf(",") + 1);
                    mobile_Network_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: mobile_Network_Code = " + mobile_Network_Code);}

                    response = response.substring(response.indexOf(",") + 1);
                    location_Area_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: location_Area_Code = " + location_Area_Code);}

                    response = response.substring(response.indexOf(",") + 1);
                    cell_ID = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: cell_ID = " + cell_ID);}
                }
                catch (Exception e) {System.out.println("[NetworkMonitor]: Exception in readEHSxNetworkDetails(3G) " + e.toString());}
            }
            
            if ((response.indexOf(" 4G,") > -1) && (response.indexOf("NOCONN") > -1)) {
                // TAC Tracking Area Code (see 3GPP 23.003 Section 19.4.2.3)
                // Global Cell ID - identifies a cell anywhere in the world - always the 28-bits. max 9 digits.
                // Physical Cell ID - a number from 0 to 503 and it distinguishes a cell from its immediate neighbours
                // RSRP Reference Signal Received Power (see 3GPP 36.214 Section 5.1.1.)
                // RSRQ Reference Signal Received Quality (see 3GPP 36.214 Section 5.1.2.)
                // ^SMONI: ACT,EARFCN,Band,DL bandwidth,UL bandwidth,Mode,MCC,MNC,TAC,Global Cell ID,Physical Cell ID,Srxlev,RSRP,RSRQ,Conn_state
                // ^SMONI: 4G,6300,20,10,10,FDD,262,02,BF75,0345103,350,33,-94,-7,NOCONN
                try {
                    response = response.substring(response.indexOf(",") + 1);
                    response = response.substring(response.indexOf(",") + 1);
                    response = response.substring(response.indexOf(",") + 1);
                    response = response.substring(response.indexOf(",") + 1);

                    networkRaT = rat4G;
                    
                    response = response.substring(response.indexOf(",") + 1);
                    mobile_Country_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: mobile_Country_Code = " + mobile_Country_Code);}

                    response = response.substring(response.indexOf(",") + 1);
                    mobile_Network_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: mobile_Network_Code = " + mobile_Network_Code);}

                    response = response.substring(response.indexOf(",") + 1);   // Tracking Area Code
                    location_Area_Code = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: location_Area_Code = " + location_Area_Code);}

                    response = response.substring(response.indexOf(",") + 1);   // Global Cell ID
                    cell_ID = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: cell_ID = " + cell_ID);}

                    response = response.substring(response.indexOf(",") + 1);   // skip Physical Cell ID
                    response = response.substring(response.indexOf(",") + 1);   // skip srxlev

                    response = response.substring(response.indexOf(",") + 1);
                    signal_Strength_pc = response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));

                    response = response.substring(response.indexOf(",") + 1);
                    signal_Strength_pc = signal_Strength_pc + "," + response.substring(response.indexOf(",") + 1, response.indexOf(",", response.indexOf(",") + 1));
                    if (DEBUG) {System.out.println("[NetworkMonitor]: signal_Strength_pc (EC/n0,RSCP) = " + signal_Strength_pc);}

                }
                catch (Exception e) {System.out.println("[NetworkMonitor]: Exception in readEHSxNetworkDetails(4G) " + e.toString());}
            }
            
            retVal = networkRaT + "," + signal_Strength_pc + "," + mobile_Country_Code + "," +
                     mobile_Network_Code + "," + location_Area_Code + "," + cell_ID;
        }
        
        return retVal;
    }
    
    // Neighbour_cell
    
    // at^smoni
    // ^SMONI: 2G,111,-89,234,10,5448,5931,17,17,0,6,E,NOCONN
    // OK
    
    // at^smonp
    // 2G:
    // 120,21,-90,234,10,0,7,16,16,5448,64A4
    // 101,9,-102,234,10,6,1,4,4,5448,FFFF
    // 105,8,-103,234,10,7,3,3,3,5448,FFFF
    // 115,6,-105,234,10,4,6,1,1,5448,6EED
    // 3G:
    // 10637,411,-5.5,-121
    // 2963,448,-13.0,-15
    // 2963,299,-5.5,-8
    // OK
    
    // at^smonp=255
    // 4G:
    // 6400,-12.0,-114,7,275,-86,7F18678,234,10,0460
    // 6400,-18.0,-117,--,36,--,--,--,--,-- 
 
    // AT^SMONI and AT^SMONP can clash (causing a hang) if issued concurrently on different AT command instances
    public static String readEHSxSMONP() {
        final boolean DEBUG = false;
        final boolean DEBUG2 = true;
        String retVal = "";
        String response;
        String snipit;
        String tmpStr;
        int tmpRaT = rat2G;
        
        response = AtcHandler.getInstance().sendATC("AT^SMONP=255\r");
        if (DEBUG) {System.out.println("[NetworkMonitor]: AT^SMONP - " + response);}
        
        if (((response != null) && response.indexOf("^SMONP=255") > -1) && (response.indexOf(":") > -1) && (response.indexOf("OK") > -1)) {

            while (response.startsWith("OK") != true) {
                if ((response.startsWith("AT^S") == true) || (response.startsWith("--") == true) || 
                    (response.startsWith("\r\n") == true) || (response.endsWith("--\r\n") == true)) {
                    if (DEBUG) {
                        System.out.println("[NetworkMonitor]: readEHSxSMONP() invalid line ignored.");
                        snipit = response.substring(0, response.indexOf("\r\n"));
                        System.out.println("[NetworkMonitor]: '" + snipit + "'");
                        System.out.println("[NetworkMonitor]: '" + byteArrayToHexString(snipit.getBytes()) + "'");
                    }
                } else {
                    if (response.startsWith("2G:") == true) {
                        if (DEBUG) {System.out.println("[NetworkMonitor]: readEHSxSMONP() 2G block found.");}
                        tmpRaT = rat2G;
                    }

                    if (response.startsWith("3G:") == true) {
                        if (DEBUG) {System.out.println("[NetworkMonitor]: readEHSxSMONP() 3G block found.");}
                        tmpRaT = rat3G;
                    }

                    if (response.startsWith("4G:") == true) {
                        if (DEBUG) {System.out.println("[NetworkMonitor]: readEHSxSMONP() 4G block found.");}
                        tmpRaT = rat4G;
                    }

                    // So it looks like a valid line and is not a RaT marker line
                    if (response.startsWith("G:", 1) == false) {
                        snipit = response.substring(0, response.indexOf("\r\n") + 2);   // we need the "\r\n" for 4G

                        // So last thing to try to avoid is a line with undecoded values "--,"
                        if (snipit.indexOf("--,") == -1) {
                            if (DEBUG || DEBUG2) {
                                System.out.println("[NetworkMonitor]: readEHSxSMONP() normal CSV line found.");
                                System.out.println("[NetworkMonitor]: (" + tmpRaT + ") '" + snipit.trim() + "'");
                            }

                            if (tmpRaT == rat2G) {
                                try {
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf(",", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.print("[NetworkMonitor]: (" + tmpRaT + ") MCC = " + tmpStr);}

                                    snipit = snipit.substring(snipit.indexOf(",") + 1);

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf(",", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.print(" MNC = " + tmpStr);}

                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);                                

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf(",", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.print(" LAC = 0x" + tmpStr);}

                                    snipit = snipit.substring(snipit.indexOf(",") + 1);

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf(",", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.println(" CELLID = 0x" + tmpStr + ".");}
                                }
                                catch (Exception e) {System.out.println("[NetworkMonitor]: Exception in readEHSxSMONP(2G) " + e.toString());}
                            }
                            
                            if (tmpRaT == rat4G) {
                                try {
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);
                                    snipit = snipit.substring(snipit.indexOf(",") + 1);

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf(",", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.print("[NetworkMonitor]: (" + tmpRaT + ") CELLID = 0x" + tmpStr);}

                                    snipit = snipit.substring(snipit.indexOf(",") + 1);

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf(",", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.print(" MCC = " + tmpStr);}

                                    snipit = snipit.substring(snipit.indexOf(",") + 1);

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf(",", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.print(" MNC = " + tmpStr);}

                                    snipit = snipit.substring(snipit.indexOf(",") + 1);

                                    tmpStr = snipit.substring(snipit.indexOf(",") + 1, snipit.indexOf("\r\n", snipit.indexOf(",") + 1));
                                    if (DEBUG2) {System.out.println(" LAC = 0x" + tmpStr + ".");}
                                }
                                catch (Exception e) {System.out.println("[NetworkMonitor]: Exception in readEHSxSMONP(4G) " + e.toString());}
                            }
                        }
                    }
                }
                
                response = response.substring(response.indexOf("\r\n") + 2).trim();
            } // while
        } // response sanity check
        
        return retVal;
    }
    
    // AT^SNMON="NBC",2
    // ----------------
/*  at^snmon="NBC",2
    ^SNMON: "NBC",0,2G,,,,,,,,
    ^SNMON: "NBC",0,3G,,,,,,,,
    OK 

    at^snmon="NBC",2
    ^SNMON: "NBC",0,2G,120,11,0,7,234,10,5448,64A4
    ^SNMON: "NBC",0,2G,102,0,7,7,234,10,1023,57B2
    ^SNMON: "NBC",0,2G,107,10,5,4,234,10,5448,FFFF
    ^SNMON: "NBC",0,2G,103,7,7,4,234,10,5448,FFFF
    ^SNMON: "NBC",0,2G,106,0,7,7,234,10,5448,FFFF
    ^SNMON: "NBC",0,3G,,,,,,,,
    OK 

    at^snmon="NBC",2
    ^SNMON: "NBC",0,2G,,,,,,,,
    ^SNMON: "NBC",0,3G,2963,448,-24.0,-17,234,10,5242,2855FFB
    ^SNMON: "NBC",0,3G,2963,289,-24.0,-16,234,10,5242,28543C0
    ^SNMON: "NBC",0,3G,2963,299,-9.0,-9,,,,
    OK   */
        public static String readEHSxSNMON_NBC() {
        final boolean DEBUG = false;
        String retVal = "";
        String response;
        
        response = AtcHandler.getInstance().sendATC("AT^SNMON=\"NBC\",2\r");
        if (DEBUG) {System.out.println("[NetworkMonitor]: AT^SNMON=\"NBC\",2 - " + response);}
        
        if ((response != null) && (response.indexOf("^SNMON=\"NBC\"") > -1) && (response.indexOf("OK") > -1)) {

        } // response sanity check
        
        return retVal;
    }

    // --------------------------------------------------------------------------- //

// This will give multiple two-char pairs string for a byte array.
    private static String byteArrayToHexString(byte[] bytesIn) {
        final char[] HEX_ARRAY = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars;
        int j;
        int v;
        
        if (bytesIn != null) {
            hexChars = new char[bytesIn.length * 2];

            for (j = 0; j < bytesIn.length; j++ ) {
                v = bytesIn[j] & 0xFF;
                hexChars[j*2] = HEX_ARRAY[v/16];             // method 1
                hexChars[j*2 + 1] = HEX_ARRAY[v%16];         // method 1
            }
            return new String(hexChars);
        } else {
            System.out.println("[NetworkMonitor]: byteArrayToHexString() called with null pointer.");
            return null;
        }
    }
}

// --------------------------------------------------------------------------- //

class NetworkMonitorTimerTask extends TimerTask {
    public void run() {
        final boolean RUN_DEBUG = false;

        NetworkMonitor.readEHSxOperator();
        NetworkMonitor.readEHSxNetworkDetails();
    }
}

// --------------------------------------------------------------------------- //
