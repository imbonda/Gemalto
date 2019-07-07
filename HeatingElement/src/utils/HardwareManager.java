package utils;

import java.util.Hashtable;


// A Classic Singleton Class - see Google
public class HardwareManager {
    private static HardwareManager instance = null;
    public static boolean continueEx = false;
    private static Thread myHardwareStatusThread = null;   // Handles hardware status lookups

    // Data, related to the module, collected by this class
    private static String module_Temperature_degC = "";    // TEMP:
    private static String battery_Voltage_mV = "";         // BATT:
    private static String firmware_Version = "";           // VER:
    private static String moduleIMEI = "";                 // IMEI
    private static String simIMSI = "";                    // IMSI

    private static String memoryMaximum = "";              // Module RAM memory size
    private static String memoryFree = "";                 // Module RAM currently free, before gc()
    private static String memoryFreePC = "";               // Module RAM currently free, as percentage
    private static String threadsRunning = "";             // Whole JVM how many threads are currently running
    private static String ffsMaximum = "";                 // Module Flash File System size
    private static String ffsFree = "";                    // Module FFS currently free, in 64KB blocks
    private static String ffsFreePC = "";                  // Module FFS currently free, as Percentage

    // Requested SMSes for Hardware Manager
    public static final String REQ_SMS1 = "MOD";           // Request module status
    public static final String REQ_SMS2 = "__SOMETHING__";
    public static final String REQ_SMS3 = "__SOMETHING__";

    // GPIO direction setting
    public static final int HW_UNKNOWN = -1;
    public static final int TERMINAL = 0;
    public static final int CONCEPT_BOARD = 1;
    public static final int CONNECT_SHIELD = 2;
    private static int hardwareType = HW_UNKNOWN;
    public static final int DIR_INPUT = 0;              // GPIO level shifter direction on Terminals / Concept board
    public static final int DIR_OUTPUT = 1;
    public static final int DIR_RELEASED = 255;         // HiD doc "7.4 Register Table" says 0xFF: Release concept for manual configuration
    private static final String I2C_W_ADDRESS_BYTE_CONCEPT  = "D2";     // 0xD2
    private static final String I2C_R_ADDRESS_BYTE_CONCEPT  = "D3";     // 0xD3
    private static final String I2C_W_ADDRESS_BYTE_TERMINAL = "D4";     // 0xD4 - HiD v7 Figure 23: Hardware watchdog configuration
    private static final String I2C_R_ADDRESS_BYTE_TERMINAL = "D5";     // 0xD5
    
    // Constructor
    private HardwareManager() {
    }
	
    // Method return the proper instance of the singleton class
    public static HardwareManager getInstance() {
        if (instance == null) {
            instance = new HardwareManager();       // if instance doesn't exist - create one
            instance.init();                        // initialize the instance
        }
        return instance;                            // returns the proper instance
    }

    // Initialise	
    private void init() {
        final boolean DEBUG = false;
    }

    // Main, ButtonHandler, GpioHandler, ASC0Handler, ASC1Handler can all call this to set hardware up    
    public boolean resourcesAllocatedSuccessfully() {
        boolean retVal = true;
        
        checkInterfaces();      // BGS5 and EHSx series devices ship with ASC1 disabled.

        return retVal;
    }

    // Releases resources	
    public void release() {
        final boolean DEBUG = false;
        continueEx = false;

        if (myHardwareStatusThread != null) {myHardwareStatusThread.interrupt();}
        
        if (hardwareType == CONCEPT_BOARD) {
            if (DEBUG) {
                System.out.println("[HardwareManager]: Concept Board clean-up.");
                System.out.println("[HardwareManager]: In Main.java - Don't forget to release your GPIOs too - setGPIOasReleased(gpioOutput);");
            }

            I2CHandler.getInstance().I2Cwrite(I2C_W_ADDRESS_BYTE_CONCEPT, "FF", "30");  // 0xD2FF30
            I2CHandler.getInstance().I2Cpause(I2CHandler.PAUSE250, "[HardwareManager]: release() 250ms delay after using I2C");
            I2CHandler.getInstance().I2Cwrite(I2C_R_ADDRESS_BYTE_CONCEPT, "00", "01");  // 0xD30001
            I2CHandler.getInstance().I2Cpause(I2CHandler.PAUSE250, "[HardwareManager]: release() 250ms delay after using I2C");
        }

        instance = null;
    }

    public void start() {
        continueEx = true;
        
        if (myHardwareStatusThread == null) {
            myHardwareStatusThread = new HardwareStatusThread();
            myHardwareStatusThread.start();
        }
    }
    
    public boolean parseCommandsInSMS(String oaIn, String responseIn) {
        final boolean DEBUG = false;
        boolean retVal = false;
        String moduleResponse;
        StringBuffer moduleValues;

        if (responseIn != null) {
            if (DEBUG) {System.out.println("[HardwareManager]: SMS received: " + responseIn);}
            
            moduleValues = new StringBuffer();

            if (responseIn.toUpperCase().indexOf(REQ_SMS1) > -1) {
                try {
                    moduleValues.append(NetworkMonitor.getOperator()); moduleValues.append(",(");
                    moduleValues.append(NetworkMonitor.getNetworkDetails(NetworkMonitor.SIGNAL)); moduleValues.append("),");
                    moduleValues.append(module_Temperature_degC); moduleValues.append("oC,");
                    moduleValues.append(battery_Voltage_mV); moduleValues.append("mV,");
                    moduleValues.append("(");
                    moduleValues.append(NetworkMonitor.getNetworkDetails(NetworkMonitor.MCC)); moduleValues.append(",");
                    moduleValues.append(NetworkMonitor.getNetworkDetails(NetworkMonitor.MNC)); moduleValues.append(",");
                    moduleValues.append(NetworkMonitor.getNetworkDetails(NetworkMonitor.LAC)); moduleValues.append(",");
                    moduleValues.append(NetworkMonitor.getNetworkDetails(NetworkMonitor.CID)); moduleValues.append("),");

                    moduleValues.append(memoryFreePC); moduleValues.append("%,");
                    moduleValues.append(threadsRunning); moduleValues.append(",");
                    moduleValues.append(ffsFreePC); moduleValues.append("%,");

                    if (moduleIMEI.length() > 4) {
                        moduleValues.append(moduleIMEI.substring(moduleIMEI.length() - 4, moduleIMEI.length()));
                    } else {
                        moduleValues.append(moduleIMEI);
                    }
                    moduleValues.append(",");

                    if (simIMSI.length() > 4) {
                        moduleValues.append(simIMSI.substring(simIMSI.length() - 4, simIMSI.length()));
                    } else {
                        moduleValues.append(simIMSI);
                    }
                    moduleValues.append(",");

                    moduleValues.append(firmware_Version); moduleValues.append(".");
                    moduleResponse = moduleValues.toString();
                    
                    System.out.println("[HardwareManager]: Module Status: " + moduleResponse);
                    SmsHandler.getInstance().smsSend(oaIn, moduleResponse, SmsHandler.CLASS1);

                    retVal = true;
                } catch (Exception e) {System.out.println("[HardwareManager]: Exception when parsing ... " + e.toString());}
            }
        } else {
            System.out.println("[HardwareManager]: Warning parseCommandsInSMS() called but responseIn == null");
        }
        
        return retVal;
    }

    public void immediateShutdownUsingATSMSO(boolean restartNeededIn) {
        final boolean DEBUG = false;
        
        if (restartNeededIn == true) {
            System.out.println("");
            System.out.println("[HardwareManager]: Module will now be switched off using AT^SMSO.");
            System.out.println("[HardwareManager]: Please re-start module manually after this.");
            System.out.println("");

            AtcHandler.getInstance().sendATC("AT^SMSO\r");        // Could lead to "lost asset" situation
//            AtcHandler.getInstance().sendATC("AT+CFUN=1,1\r");      // Will always restart back to life
            
            try {Thread.currentThread().join();}  // wait for this thread to die
            catch (Exception e) {System.out.println("[HardwareManager]: immediateShutdownUsingATSMSO() " + e.toString());}
        } else {
            System.out.println("[HardwareManager]: No immediate shutdown using AT^SMSO required.");
        }
    }

    public void immediateRestartUsingATCFUN(int funLevel) {
        final boolean DEBUG = false;
        
        if ((funLevel > -1) && (funLevel < 9)){
            if ((funLevel != 0) && (funLevel != 1) && (funLevel != 4)) {
                funLevel = 1;
            }
            
            System.out.println("");
            System.out.println("[HardwareManager]: Module will now be restarted using AT+CFUN=" + funLevel + ",1.");
            System.out.println("");

            AtcHandler.getInstance().sendATC("AT+CFUN=" + funLevel + ",1\r");
            
            try {Thread.currentThread().join();}  // wait for this thread to die
            catch (Exception e) {System.out.println("[HardwareManager]: immediateRestartUsingATCFUN() " + e.toString());}
        } else {
            System.out.println("[HardwareManager]: immediateRestartUsingATCFUN() called with invalid parameters.");
        }
    }
    
    private void checkInterfaces() {
        final boolean DEBUG = false;
        boolean restartNeeded = false;
        final boolean ALLOW_AUTO_RESTART = true;
        
        if (DEBUG) {System.out.println("[HardwareManager]: Checking hardware interface setup.");}
        
        // Ensure correct character set
        AtcHandler.getInstance().sendATC("AT+CSCS=\"GSM\"\r");
        
        // check ASCx power saving is OFF
        checkAnInterface("Serial port power saving mode switched off", "AT^SPOW?", "1,0,0", "AT^SPOW=1,0,0");
        // turn this off for BGS5 rel 1 arn 10 as it fails every time
        // turn this off for EHS6 rel 3 arn 37 after ASC1 usage

        // Configure allocation of serial interfaces
        restartNeeded |= checkAnInterface("Reconfiguring port allocation", "AT^SCFG=\"Serial/Interface/Allocation\"", "\"1\",\"1\"", "AT^SCFG=\"Serial/Interface/Allocation\",\"1\"");
        
        // Configure ASC1 interface lines RXD1, TXD1, RTS1, CTS1 shared with
        // GPIO16 - GPIO19 lines and SPI lines MOSI, MISO, SPI_CS
        restartNeeded |= checkAnInterface("Reconfiguring ASC1 allocation", "AT^SCFG=\"GPIO/mode/ASC1\"", "\"std\"", "AT^SCFG=\"GPIO/mode/ASC1\",\"std\"");

        // Configure DTR0 line of ASC0 interface shared with GPIO1 line
        restartNeeded |= checkAnInterface("Reconfiguring DTR0/GPIO1 allocation", "AT^SCFG=\"GPIO/mode/DTR0\"", "\"gpio\"", "AT^SCFG=\"GPIO/mode/DTR0\",\"gpio\"");

        // Configure Status LED line shared with GPIO5 line
        restartNeeded |= checkAnInterface("Reconfiguring SYNC/GPIO5 allocation", "AT^SCFG=\"GPIO/mode/SYNC\"", "\"gpio\"", "AT^SCFG=\"GPIO/mode/SYNC\",\"gpio\"");

        // Configure PWM lines PWM2 and PWM1, shared with GPIO lines GPIO6 and GPIO7 respectively
        restartNeeded |= checkAnInterface("Reconfiguring PWM allocations", "AT^SCFG=\"GPIO/mode/PWM\"", "\"std\"", "AT^SCFG=\"GPIO/mode/PWM\",\"std\"");
        if (restartNeeded == false) {
            AtcHandler.getInstance().sendATC("AT^SWDAC=" + 0 + "," + 0 + ",0\r");   // PWM turn off / close first  PWM channel (GPIO7 of EHS6 module) output
            AtcHandler.getInstance().sendATC("AT^SWDAC=" + 1 + "," + 0 + ",0\r");   // PWM turn off / close second PWM channel (GPIO6 of EHS6 module) output
        }
       
        // Configure Digital Audio Interface which is shared with lines GPIO 20, 21, 22 and 23
        restartNeeded |= checkAnInterface("Reconfigured DAI / GPIO allocations", "AT^SCFG=\"GPIO/mode/DAI\"", "\"gpio\"", "AT^SCFG=\"GPIO/mode/DAI\",\"gpio\"");
        
        // Configure RING0 which is shared with GPIO line GPIO24
        restartNeeded |= checkAnInterface("Reconfigured RING0 / GPIO24 allocation", "AT^SCFG=\"GPIO/mode/RING0\"", "\"std\"", "AT^SCFG=\"GPIO/mode/RING0\",\"std\"");
        if (restartNeeded == false) { // ensure that the RING0 line is active, not "local"
            AtcHandler.getInstance().sendATC("AT^SCFG=\"URC/Ringline\",\"asc0\"\r");
//            AtcHandler.getInstance().sendATC("AT^SCFG=\"URC/Ringline\",\"local\"\r");   // bug bug - Case00002463
        }
        
        System.out.println("[HardwareManager]: Configuration checks completed.");
        if (restartNeeded == true) {
            if (ALLOW_AUTO_RESTART) {
                immediateRestartUsingATCFUN(1);
            } else {
                immediateShutdownUsingATSMSO(restartNeeded);
            }
        }  // reset was needed
        
        if (DEBUG) {System.out.println("[HardwareManager]: Checking Interface setup completed with no reconfigurations.");}
    }
    
    private boolean checkAnInterface(String helpfulTextIn, String queryIn, String requiredIn, String setItIn) {
        final boolean DEBUG = false;
        boolean retVal = false;
        String response;

        if ((helpfulTextIn != null) && (queryIn != null) && (requiredIn != null) && (setItIn != null)) {
            response = AtcHandler.getInstance().sendATC(queryIn + "\r");
            if (DEBUG) {System.out.println("[HardwareManager]: Status query: " + response);}

            if (response.indexOf(requiredIn) == -1) {
                System.out.println("[HardwareManager]: " + helpfulTextIn + " by: " + setItIn);
                response = AtcHandler.getInstance().sendATC(setItIn + "\r");

                if (response.indexOf("OK") == -1) {
                    System.out.println("[HardwareManager]: Reconfiguration error: " + response);
                } else {
                    retVal = true;
                }
            }
        } else {
            System.out.println("[HardwareManager]: Warning checkAnInterface() called with a null parameter.");
        }
        
        return retVal;
    }
    
// --------------------------------------------------------------------------- //

    // Nice lightweight return of cached values
    public static String getIMEI() {
        String retVal = moduleIMEI;
        return retVal;
    }
    
    public static String getIMSI() {
        String retVal = simIMSI;
        return retVal;
    }
    
    public static String getTemp() {
        String retVal = module_Temperature_degC;
        return retVal;
    }

    public static String getVolt() {
        String retVal = battery_Voltage_mV;
        return retVal;
    }
    
    public static String getFirm() {
        String retVal = firmware_Version;
        return retVal;
    }
    
    public static String getMaxMemory() {
        String retVal = memoryMaximum;
        return retVal;
    }
    
    public static String getFreeMemory() {
        String retVal = memoryFree;
        return retVal;
    }
    
    public static String getThreadsRunning() {
        String retVal = threadsRunning;
        return retVal;
    }
    
    public static String getMaxFFS() {
        String retVal = ffsMaximum;
        return retVal;
    }
    
    public static String getFreeFFS() {
        String retVal = ffsFree;
        return retVal;
    }
    
    // Attempts to read fresh values - used by own status thread below with update every 65 seconds
    public static String readEHSxIMEI() {
        final boolean DEBUG = false;
        String retVal = moduleIMEI;        // If we can't get new value then reuse old value
        String response;
        
        response = AtcHandler.getInstance().sendATC("AT+CGSN\r");
        
        if ((response.indexOf("ERROR") == -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[HardwareManager]: IMEI - " + response);}
            
            try {
                moduleIMEI = response.substring(response.indexOf('\n'), response.indexOf("OK")-2).trim();
                retVal = moduleIMEI;
            }
            catch (Exception e) {System.out.println("[HardwareManager]: Exception in readEHSxIMEI() " + e.toString());}
        }
        
        if (DEBUG) {System.out.println("[HardwareManager]: moduleIMEI = " + moduleIMEI);}
        
        return retVal;
    }

    public static String readEHSxIMSI() {
        final boolean DEBUG = false;        
        String retVal = simIMSI;        // If we can't get new value then reuse old value
        String response;
        
        response = AtcHandler.getInstance().sendATC("AT+CIMI\r");
        
        if ((response.indexOf("ERROR") == -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[HardwareManager]: IMSI - " + response);}
            
            try {
                simIMSI = response.substring(response.indexOf('\n'), response.indexOf("OK")-2).trim();
                retVal = simIMSI;
            }
            catch (Exception e) {System.out.println("[HardwareManager]: Exception in readEHSxIMSI() " + e.toString());}
        }
        
        if (DEBUG) {System.out.println("[HardwareManager]: simIMSI = " + simIMSI);}
        
        return retVal;
    }
    
    public static String readEHSxTemp() {
        final boolean DEBUG = false;
        String retVal = module_Temperature_degC;        // If we can't get new value then reuse old value
        String response;
        
        AtcHandler.getInstance().sendATC("AT^SCTM=1,1\r");   // request that we see TEMP in next AT Command
        response = AtcHandler.getInstance().sendATC("AT^SCTM?\r");

        if ((response.indexOf("^SCTM: 1,0,") > -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[HardwareManager]: AT^SCTM - " + response);}
            
            try {
                module_Temperature_degC = response.substring(response.indexOf("^SCTM: 1,0,") + "^SCTM: 1,0,".length(), response.indexOf("OK")-2).trim();
                retVal = module_Temperature_degC;
            }
            catch (Exception e) {System.out.println("[HardwareManager]: Exception in readEHSxTemp() " + e.toString());}
        }
        
        if (DEBUG) {System.out.println("[HardwareManager]: module_Temperature_degC = " + module_Temperature_degC);}
        
        return retVal;
    }
    
    public static String readEHSxVolt() {
        final boolean DEBUG = false;        
        String retVal = battery_Voltage_mV;        // If we can't get new value then reuse old value
        String response;
        
        response = AtcHandler.getInstance().sendATC("AT^SBV\r"); 
                
        if ((response.indexOf("^SBV: ") > -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[HardwareManager]: AT^SBV - " + response);}
            
            try {
                battery_Voltage_mV = response.substring(response.indexOf("^SBV: ") + "^SBV: ".length(), response.indexOf("OK")-2).trim();
                retVal = battery_Voltage_mV;
            }
            catch (Exception e) {System.out.println("[HardwareManager]: Exception in readEHSxVolt() " + e.toString());}
        }
        
        if (DEBUG) {System.out.println("[HardwareManager]: battery_Voltage = " + battery_Voltage_mV);}
        
        return retVal;
    }

    public static String readEHSxFirm() {
        final boolean DEBUG = false;        
        String retVal = firmware_Version;        // If we can't get new value then reuse old value
        String response;
        
        response = AtcHandler.getInstance().sendATC("ATI1\r");
        
        if ((response.indexOf("Cinterion") > -1) && (response.indexOf("REVISION") > -1) &&
            (response.indexOf("A-REVISION") > -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[HardwareManager]: ATI1 - " + response);}
            
            try {
                firmware_Version = response.substring(response.indexOf("Cinterion") + "Cinterion".length() + 2, response.indexOf("REVISION")-2).trim() +
                    " " + response.substring(response.indexOf("REVISION") + "REVISION".length(), response.indexOf("A-REVISION")-2).trim() +
                    " arn " + response.substring(response.indexOf("A-REVISION") + 10, response.indexOf("OK")-2).trim();
                retVal = firmware_Version;
            }
            catch (Exception e) {System.out.println("[HardwareManager]: Exception in readEHSxFirm() " + e.toString());}
        }
        
        if (DEBUG) {System.out.println("[HardwareManager]: firmware_Version = " + firmware_Version);}
        
        return retVal;
    }

    public static String readEHSxMaxMemory() {
        final boolean DEBUG = false;        
        String retVal = memoryMaximum;        // If we can't get new value then reuse old value
        long response;
        
        response = Runtime.getRuntime().totalMemory();
        if (response > 0) {
            memoryMaximum = ("" + response);
            retVal = memoryMaximum;
        }   // return the (String) equivalent of the (long) response

        if (DEBUG) {System.out.println("[HardwareManager]: memoryMaximum = " + memoryMaximum);}

        return retVal;
    }
    
    public static String readEHSxFreeMemory() {
        final boolean DEBUG = false;        
        String retVal = memoryFree;        // If we can't get new value then reuse old value
        long response;
        
        response = Runtime.getRuntime().freeMemory();
        if (response > 0) {
            memoryFree = ("" + response);
            memoryFreePC = percentage(memoryMaximum, memoryFree);
            retVal = memoryFree;
        }   // return the (String) equivalent of the (long) response

        if (DEBUG) {System.out.println("[HardwareManager]: memoryFree = " + memoryFree);}

        return retVal;
    }
    
    public static String readEHSxThreadsRunning() {
        final boolean DEBUG = false;        
        String retVal = threadsRunning;        // If we can't get new value then reuse old value
        int response;
        
        response = Thread.activeCount();
        if (response > 0) {
            threadsRunning = ("" + response);
            retVal = threadsRunning;
        }   // return the (String) equivalent of the (int) response

        if (DEBUG) {System.out.println("[HardwareManager]: threadsRunning = " + threadsRunning);}

        return retVal;
    }
    
    public static String readEHSxFFS() {
        final boolean DEBUG = false;        
        String retVal = ffsFree;        // If we can't get new value then reuse old value
        String response;
        
        response = AtcHandler.getInstance().sendATC("AT^SFSA=\"gstat\"\r"); 

        if ((response.indexOf("^SFSA: 0") > -1) && (response.indexOf("\r") > -1) && (response.indexOf("OK") > -1)) {
            if (DEBUG) {System.out.println("[HardwareManager]: AT^SFSA=\"gstat\" - " + response);}

            try {
                ffsMaximum = response.substring(response.indexOf("^SFSA: ") + "^SFSA: ".length(), response.indexOf("\r", response.indexOf("^SFSA: "))).trim();
                response = response.substring(response.indexOf("\r", response.indexOf("^SFSA: ")) + 2);
                ffsFree = response.substring(response.indexOf("^SFSA: ") + "^SFSA: ".length(), response.indexOf("\r", response.indexOf("^SFSA: "))).trim();
                ffsFreePC = percentage(ffsMaximum, ffsFree);
                retVal = ffsFree;
            }
            catch (Exception e) {System.out.println("[HardwareManager]: Exception in readEHSxFFS() " + e.toString());}
        }
        
        if (DEBUG) {
            System.out.println("[HardwareManager]: ffsMax  = " + ffsMaximum);
            System.out.println("[HardwareManager]: ffsFree = " + ffsFree);
        }
        
        return retVal;
    }

    public static String percentage(String wholeIn, String partIn) {
        String retVal = "";
        float wholeF;   // starts as 0
        float partF;
        
        try {
            wholeF = Float.parseFloat(wholeIn);
            partF = Float.parseFloat(partIn);
            if (wholeF > 0) {
                retVal = "" + (int) ((partF / wholeF) * 100.0);
            }
        } catch (Exception e) {
            System.out.println("[HardwareManager]: percentage(S,S) exception " + e.toString());
        }
        
        return retVal;
    }
    
// --------------------------------------------------------------------------- //

    public static int readEHSxHardwareType() {
        final boolean DEBUG = false;
        int retVal = hardwareType;        // If we can't get new value then reuse old value
        int who_am_i;
      
        if (DEBUG) {System.out.println("[HardwareManager]: detectHardwareType()+");}
        
        // Try to find out what we are using...
        try {
            // First let us try to get the firmware version of the TERMINAL's I2C driver:
            // See Hardware Interface Descrip[tion - Section 8.4, Example 2, Figure 25
            // However, except for the status address register (SR), no information can be directly retrived from an address register itself,
            // but only indirectly by means of a so-called read-address-register (RAR).
            // An initial WRITE command has to link the register to be read to the RAR first.
            
            // Write: Set RAR (read-address-register) to VER
            I2CHandler.getInstance().I2Cwrite(Integer.parseInt(I2C_W_ADDRESS_BYTE_TERMINAL, 16), 0xFF, 0xFD);  // 0xD4 Select firmware version to be read
            I2CHandler.getInstance().I2Cpause(I2CHandler.PAUSE250, "[HardwareManager]: WHO AM I ");
            
            // Now the RAR is linked to the register to be read, and the content of this register can be read from the SR.
            // Read Terminal I2C address register SR "last status" - or linked RAR values...
            
            // Read from status register (SR)
            who_am_i = I2CHandler.getInstance().I2CreadUnsigned8bit(Integer.parseInt(I2C_R_ADDRESS_BYTE_TERMINAL, 16), 0x00, "[HardwareManager]: read (8 bit)");    // 0xD5
            I2CHandler.getInstance().I2Cpause(I2CHandler.PAUSE250, "[HardwareManager]: WHO AM I ");
            if (DEBUG) {System.out.println("[HardwareManager]: who_am_i (T) " + who_am_i);}
          
            if (who_am_i > 1) {
                hardwareType = TERMINAL;
                retVal = hardwareType;
                System.out.println("[HardwareManager]: Hardware type set to TERMINAL (#" + TERMINAL + ").");
            } else {
                hardwareType = CONCEPT_BOARD;
                retVal = hardwareType;
                System.out.println("[HardwareManager]: Hardware type set to CONCEPT_BOARD (#" + CONCEPT_BOARD + ").");                
            }

            // Put RAR (read-address-register) back to correct 0x00 SR value
            I2CHandler.getInstance().I2Cwrite(Integer.parseInt(I2C_W_ADDRESS_BYTE_TERMINAL, 16), 0xFF, 0x00);   // 0xD4
            I2CHandler.getInstance().I2Cpause(I2CHandler.PAUSE250, "[HardwareManager]: WHO AM I ");
        } catch (Exception e) {System.out.println("[HardwareManager]: detectHardwareType() - " + e.toString());}

        // Do not release I2C here; allow Main.java to do I2CHandler.getInstance().release(); in destroyApp()
        
        if (DEBUG) {System.out.println("[HardwareManager]: detectHardwareType()-");}
        
        return retVal;
    }

    public static void setGPIOasInput(String gpioIn) {
        setDirection(gpioIn, DIR_INPUT);
    }

    public static void setGPIOasOutput(String gpioOut) {
        setDirection(gpioOut, DIR_OUTPUT);
    }

    public static void setGPIOasReleased(String gpioIn) {
        if (hardwareType == CONCEPT_BOARD) {
            setDirection(gpioIn, DIR_RELEASED);    // Use this on the Concept Board to go back to dip-switch settings
        }
    }

    private static void setDirection(String gpioName, int direction) {
        final boolean DEBUG = false;
        String addressByteString = null;
        String pinString = null;
        String directionString;
        
        Hashtable mapLuna = new Hashtable();    // Terminal - see HiD section 8.4.1.4 I2C Commands
        mapLuna.put("GPIO6",  "10");            // Register address in hex 0x10 = GPIO6
        mapLuna.put("GPIO7",  "11");
        mapLuna.put("GPIO8",  "12");
        mapLuna.put("GPIO11", "13");
        mapLuna.put("GPIO12", "14");            // Used in the Terminal HiD examples section 8.4.1.4 (Fig. 24)
        mapLuna.put("GPIO13", "15");
        mapLuna.put("GPIO22", "16");            // was GPIO14 which does not appear in hid
        mapLuna.put("GPIO23", "17");            // was GPIO15 which does not appear in hid
        mapLuna.put("GPIO21", "18");
        mapLuna.put("GPIO20", "19");
        
        Hashtable mapConcept = new Hashtable(); // Concept Board - see HiD section 7.4
        mapConcept.put("GPIO5",  "10");         // Register address in hex 0x10 = GPIO5
        mapConcept.put("GPIO6",  "11");
        mapConcept.put("GPIO8",  "12");
        mapConcept.put("GPIO7",  "13");
        mapConcept.put("GPIO20", "14");         // Register address in hex 0x14 = GPIO20
        mapConcept.put("GPIO21", "15");
        mapConcept.put("GPIO22", "16");
        mapConcept.put("GPIO23", "17");
        
        if (hardwareType == HW_UNKNOWN) {
            readEHSxHardwareType();             // See if we are on a Terminal or Concept Board
        }
        
        if (gpioName.startsWith("GPIO") == true) {
            if (hardwareType == TERMINAL) {
                addressByteString = I2C_W_ADDRESS_BYTE_TERMINAL;
                try {pinString = mapLuna.get(gpioName).toString();} catch (Exception e) { /* was not found in the list */ }
            } else {
                addressByteString = I2C_W_ADDRESS_BYTE_CONCEPT;
                try {pinString = mapConcept.get(gpioName).toString();} catch (Exception e) { /* was not found in list */ }
            }
        }
        
        if ((direction == DIR_INPUT) || (direction == DIR_OUTPUT)) {
            directionString = "0" + direction;
        } else {
            directionString = "FF";     // for DIR_RELEASED
        }
        
        // In write mode (i.e., slave address “0xD4“), one address byte and one data byte is sent to the Java Terminal/Watchdog.
        // The address byte specifies a register to write the data byte to. This code des not check for "OK"; just fire and forget...
        // Use I2CHandler instead of driving I2C port here ourselves...
        if (pinString != null) {
            I2CHandler.getInstance().I2Cwrite(addressByteString, pinString, directionString);
            if (DEBUG) {System.out.println("[HardwareManager]: setDirection I2C (" + addressByteString + ", " + pinString + ", " + directionString + ")");}
            I2CHandler.getInstance().I2Cpause(I2CHandler.PAUSE500, "[HardwareManager]: setDirection() 500ms delay after using I2C");
        } else {
            System.out.println("[HardwareManager]: setDirection() gpioName = '" + gpioName + "', hardwareType = #" + hardwareType + " FAILED to resolve.");
        }
    }    
}

// --------------------------------------------------------------------------- //

class HardwareStatusThread extends Thread {
    final boolean DEBUG = false;
    
    public void run() {
        System.out.println("[HardwareStatusThread]: HardwareStatusThread started.");
        
        if (HardwareManager.getIMEI().equals("") == true) {HardwareManager.readEHSxIMEI();}
        if (HardwareManager.getMaxMemory().equals("") == true) {HardwareManager.readEHSxMaxMemory();}
        
        while (HardwareManager.continueEx == true) {
            if (DEBUG) {System.out.println("[HardwareStatusThread]: +HardwareStatusThread()");}

            // Attempt status update
            HardwareManager.readEHSxTemp();   // populate public Temp variable
            HardwareManager.readEHSxVolt();   // populate public Volt variable
            HardwareManager.readEHSxFirm();   // populate public Firm variable
            HardwareManager.readEHSxIMSI();   // populate public IMSI variable
            HardwareManager.readEHSxFreeMemory();       // populate public freeMem variable
            HardwareManager.readEHSxThreadsRunning();   // populate public freeMem variable
            HardwareManager.readEHSxFFS();              // populate public freeFFS variable

            // This is a thread, not a timer task, so it really ought to sleep here, for a prime number of milli-seconds
            try {Thread.sleep(65077);} catch (Exception e) {System.out.println("[LocationRequestingThread]: Exception " + e.toString());}

            if (DEBUG) {System.out.println("[HardwareStatusThread]: -HardwareStatusThread()");}
        }
        System.out.println("[HardwareStatusThread]: continueEx is 'false' so HardwareStatusThread has finished.");
    }
}
