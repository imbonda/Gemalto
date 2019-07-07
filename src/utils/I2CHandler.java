package utils;

import com.cinterion.io.I2cBusConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.io.Connector;

// From EHSx AT Command guide
// 19.7 AT^SSPI Serial Protocol Interface - Caution:
// If I²C or SPI are opened from a Java application then
// be sure to close the I²C or SPI channels before Java terminates.

// From EHSx Hardware Interface Description
// 2.1.11 I2C Interface - Note 2: The unique device address 34h is reserved and
// should not be used by other external devices connected to the I2C bus.

// With an active state of the ASC0 interface (i.e. CTS0 is at low level)
// the initialization of the I2C interface is also finished.
        
public class I2CHandler {
    private static I2CHandler instance = null;
    private static boolean continueEx = false;
    
    private static final int USE_WRITE_DELAY = 0;       // set to 0 = "no write delay" or 1 = "write delay"
        
    private static final int I2CTO = 4;     // number of seconds of non-use, before I2C port is closed
    private static int I2CtoTimer = 0;      // timer to closure
    private static Timer toTimer = null;
    private static I2cBusConnection cc = null;
    private static InputStream inStream = null;
    private static OutputStream outStream = null;

    public static final int  PAUSE11 =  11; // 11ms pause
    public static final int  PAUSE51 =  51; // 51ms pause
    public static final int  PAUSE65 =  65; // 65ms pause
    public static final int PAUSE250 = 250; // 250ms pause
    public static final int PAUSE500 = 500; // 500ms pause
    
    private I2CHandler() {
//      Constructor
    }
	
//  Method return the proper instance of the singleton class
    public static I2CHandler getInstance() {
        if (instance == null) {
            instance = new I2CHandler();    // if instance doesn't exist - create one
            instance.init();                // initialize the instance
        }
        return instance;                    // returns the proper instance
    }

//  Initialises I2CHandler object
    private void init() {        
    }
    
    public boolean resourcesAllocatedSuccessfully() {
        return true;
    }
    
    public void release() {
        continueEx = false;

        // stop the I2C time-out tmer
        if (toTimer != null) {toTimer.cancel();}

        // force immediate I2C bus closure
        I2CtoTimer = 0;         // set timer to 0
        checkTimeOutStatus();   // this will assume a timeout and close the bus for everyone else to use
        
        instance = null;
    }

    public void start() {
        continueEx = true;

        startTimeOutTimerTask();
    }    
    
    public int I2CreadSigned24bit(int addressIn, int regIn, String helpfulTextIn) {
        byte retVal_XL = 0;
        byte retVal_L = 0;
        byte retVal_H = 0;
        int retVal;
        
        try {retVal_XL = (byte)Integer.parseInt(I2Cread(addressIn, regIn, 1), 16);}
        catch (Exception e) {System.out.println("[I2CHandler]: Exception reading (" + helpfulTextIn + ")XLow " + e.toString());}

        try {retVal_L = (byte)Integer.parseInt(I2Cread(addressIn, regIn + 1, 1), 16);}
        catch (Exception e) {System.out.println("[I2CHandler]: Exception reading (" + helpfulTextIn + ")Low " + e.toString());}

        try {retVal_H = (byte)Integer.parseInt(I2Cread(addressIn, regIn + 2, 1), 16);}
        catch (Exception e) {System.out.println("[I2CHandler]: Exception reading (" + helpfulTextIn + ")High " + e.toString());}

        retVal = (retVal_H << 16) | ((retVal_L & 0xff) << 8) | (retVal_XL & 0xff) ;
        
        return retVal;
    }
    
    public int I2CreadSigned16bit(int addressIn, int regIn, String helpfulTextIn) {
        byte retVal_L = 0;
        byte retVal_H = 0;
        int retVal;
        
        try {retVal_L = (byte)Integer.parseInt(I2Cread(addressIn, regIn, 1), 16);}
        catch (Exception e) {System.out.println("[I2CHandler]: Exception reading (" + helpfulTextIn + ")Low " + e.toString());}

        try {retVal_H = (byte)Integer.parseInt(I2Cread(addressIn, regIn + 1, 1), 16);}
        catch (Exception e) {System.out.println("[I2CHandler]: Exception reading (" + helpfulTextIn + ")High " + e.toString());}

        retVal = (retVal_H << 8) | (retVal_L & 0xff);
        
        return retVal;
    }

    public int I2CreadUnsigned8bit(int addressIn, int regIn, String helpfulTextIn) {
        int retVal = 0;
        
        try {retVal = Integer.parseInt(I2Cread(addressIn, regIn, 1), 16);}
        catch (Exception e) {System.out.println("[I2CHandler]: Exception reading (" + helpfulTextIn + ")8bit " + e.toString());}
        
        return retVal;
    }

    public synchronized String I2Cread(int addressIn, int regIn, int lengthIn) {
        final boolean DEBUG = false;
        String retVal = "0";
        String response;
        
        if (continueEx) {
            if (DEBUG) {
                System.out.println("[I2CHandler]: I2Cread() sending " + "<a" + intToHex(addressIn) + intToHex(regIn) + ">");
                System.out.println("[I2CHandler]: I2Cread() sending " + "<a" + intToHex(addressIn | 1) + intToHex(lengthIn) + ">");
            }
            
            sendCommand("<a" + intToHex(addressIn) + intToHex(regIn) + ">", USE_WRITE_DELAY);
            response = sendCommand("<a" + intToHex(addressIn | 1) + "00" + intToHex(lengthIn) + ">", USE_WRITE_DELAY);

            if ((checkResponseAck(response)) && (response.length() >= ("{a+".length() + (lengthIn * 2)))) {
                try {retVal = response.substring("{a+".length(), "{a+".length() + (lengthIn * 2));}
                catch (Exception e) {System.out.println("[I2CHandler]: Exception I2Cread() response='" + response + "' " + e.toString());}
            }
        } else {
            if (DEBUG) {System.out.println("[I2CHandler]: I2Cread(ints) called but continueEx == false");}
        }
            
        return retVal;
    }

    public synchronized String I2Cwrite(String addressIn, String regIn, String dataIn) {
        final boolean DEBUG = false;
        String retVal = "";
        String response;

        if (continueEx) {
            if (DEBUG) {
                System.out.println("[I2CHandler]: I2Cwrite(Strings) sending " + "<a" + addressIn + regIn + dataIn + ">");
            }
            
            response = sendCommand("<a" + addressIn + regIn + dataIn + ">", USE_WRITE_DELAY);

            if (checkResponseAck(response)) {
                retVal = response;
            }
        } else {
            if (DEBUG) {System.out.println("[I2CHandler]: I2Cwrite(Strings) called but continueEx == false");}
        }
        
        return retVal;
    }
    
    public synchronized String I2Cwrite(int addressIn, int regIn, int dataIn) {
        final boolean DEBUG = false;
        String retVal = "";
        String response;

        if (continueEx) {
            if (DEBUG) {
                System.out.println("[I2CHandler]: I2Cwrite(Strings) sending " + "<a" + intToHex(addressIn) + intToHex(regIn) + intToHex(dataIn) + ">");
            }
            
            response = sendCommand("<a" + intToHex(addressIn) + intToHex(regIn) + intToHex(dataIn) + ">", USE_WRITE_DELAY);

            if (checkResponseAck(response)) {
                retVal = response;
            }
        } else {
            if (DEBUG) {System.out.println("[I2CHandler]: I2Cwrite(ints) called but continueEx == false");}
        }
        
        return retVal;
    }

    public boolean I2Cpause(int sleepDelayIn, String helpfulTextIn) {
        final boolean DEBUG = false;
        boolean retVal = true;

        try {Thread.sleep(sleepDelayIn);} catch (Exception e) {System.out.println(helpfulTextIn + " Exception: " + e.toString());}
        if (DEBUG) {System.out.println("[I2CHandler]: I2Cpause(" + sleepDelayIn + ") " + helpfulTextIn);}

        return retVal;
    }

    // private because we don't really want anyone not using delays
    private String sendCommand(String commandIn) {
        final boolean DEBUG = false;
        String retVal = "";

        if (commandIn != null) {
            if (DEBUG) {System.out.println("[I2CHandler]: sendCommand: " + commandIn);}
            try {
                retVal = sendCommand(commandIn, 0);         // ignore USE_WRITE_DELAY
            } catch (Exception e) {
                System.out.println("[I2CHandler]: Exception " + e.toString());
                    retVal = "[I2CHandler]: ERROR";
            }
        } else {
            System.out.println("[I2CHandler]: Warning sendCommand() called but commandIn == null");
        }
        
        return retVal;
    }
    
    // The OutputStream objects block the thread until all bytes have been written.
    // The InputStream objects block the thread until at least one byte has been read.
    // To avoid blocking infinitely in case the I2C bus does not respond, check please first if there is data available.

    public String sendCommand(String commandIn, int writeDelay) {
        final boolean DEBUG = false;
        String retVal = null;
        StringBuffer sb;
        char ch;
        
        if (DEBUG) {System.out.println("\n[I2CHandler]: +sendCommand() " + commandIn);}
        
        if (commandIn != null) {
            if (openI2Cbus() == true) {
                if (DEBUG) {System.out.println("[I2CHandler]: sendCommand() openI2Cbus() == true");}
                try {
                    if (DEBUG) {System.out.println("[I2CHandler]: sendCommand() writeDelay == " + writeDelay);}
                    if (writeDelay == 0) {
                        if (outStream != null) {
                            if (DEBUG) {System.out.println("[I2CHandler]: sendCommand() outStream.write(commandIn.getBytes(), 0, commandIn.length()...");}
                            outStream.write(commandIn.getBytes(), 0, commandIn.length());
                            if (DEBUG) {System.out.println("[I2CHandler]: sendCommand() outStream.write(commandIn.getBytes(), 0, commandIn.length() done");}
                        }
                    } else {
                        byte[] bytes = commandIn.getBytes();
                        
                        if (DEBUG) {System.out.print("[I2CHandler]: sendCommand() outStream.write(bytes["+ bytes.length +"]) - ");}
                        for (int i = 0; i < bytes.length; i++) {
                            if (outStream != null) {
                                if (DEBUG) {System.out.print((char) bytes[i]);}
                                outStream.write(bytes[i]);
                                if (writeDelay > 0) {
                                    if (DEBUG) {System.out.print(".");}

                                    try {Thread.sleep(writeDelay);} catch (Exception e) {System.out.println("[I2CHandler]: Exception " + e.toString());}
                                }
                            } else {
                                if (DEBUG) {System.out.println(" i2c outStream==null!");}
                            }
                        }
                        if (DEBUG) {System.out.println(" done.");}
                    }
                } catch (Exception e) {
                    System.out.println("[I2CHandler]: Exception write() " + e.toString());
                    retVal = "[I2CHandler]: ERROR";
                }
                    
                try {
                    if (DEBUG) {System.out.println("[I2CHandler]: sendCommand() flush()");}
                    if (outStream != null) {outStream.flush();}
                } catch (Exception e) {
                    System.out.println("[I2CHandler]: Exception flush() " + e.toString());
                    retVal = "[I2CHandler]: ERROR";
                }

                try {
                    sb = new StringBuffer();
                    if (sb != null) {
                        if (DEBUG) {System.out.print("[I2CHandler]: sendCommand() sb is OK; reading from inStream - ");}
                        while ((inStream != null) && (inStream.available() != -1)) {
                            ch = (char) inStream.read();
                            if (DEBUG) {System.out.print(ch);}
                            sb.append(ch);
                            if (ch == '}') {break;}
                        }
                        retVal = sb.toString();
                        if (DEBUG) {System.out.println("");}
                    }

// Can close the I2C bus here, after every read as per DIYP, but this is not an efficient usage model...
//                    closeI2Cbus();        // this also sets cc and the streams to null which we now need.

//                    if (inStream != null) {inStream.close();}         // don't use these anymore
//                    if (outStream != null) {outStream.close();}       // as they will break timeOut logic
//                    if (cc != null) {cc.close();}                     // which looks for null objects
                } catch (Exception e) {
                    System.out.println("[I2CHandler]: Exception read() response to write() " + e.toString());
                    retVal = "[I2CHandler]: ERROR";
                }
            } else {
                System.out.println("[I2CHandler]: Warning sendCommand() could not open I2C bus");
            }
        } else {
            System.out.println("[I2CHandler]: Warning sendCommand() called but commandIn == null");
        }
        
        if (DEBUG) {
            System.out.println("[I2CHandler]: sendCommand() retVal == '" + retVal + "'");
            System.out.println("[I2CHandler]: -sendCommand() " + commandIn);
        }
        
        return retVal;
    }
    
    private boolean openI2Cbus() {
        final boolean DEBUG = false;
        boolean  retVal = false;
        
        if (DEBUG) {System.out.println("[I2CHandler]: +openI2Cbus()");}

        // A URI with the type and parameters is used to open the connection.
        // The scheme must be: i2c:<bus identifier>[<optional parameters>]
        // Please don't use 'writeDelay' and 'readDelay' parameters in 'Connector.open()' function!

        // [Cinterion WM]
        // The current platform supports only one I2C connection, therefore the default bus identifier "0" has to be used.

        if (cc == null) {
            try {
                cc = (I2cBusConnection) Connector.open("i2c:0;baudrate=400");
            }
            catch (Exception e) {System.out.println("[I2CHandler]: Exception (cc) when opening I2C bus " + e.toString());}
        }
        
        if (cc != null) {
            if ((inStream == null) && (outStream == null)) {
                try {
                    inStream = cc.openInputStream();
                    outStream = cc.openOutputStream();
                }
                catch (Exception e) {System.out.println("[I2CHandler]: Exception (streams) when opening I2C bus " + e.toString());}
            }
        }
        
        if ((inStream != null) && (outStream != null)) {
            if (DEBUG) {if (I2CtoTimer == 0) {System.out.println("[I2CHandler]: I2C bus opened.");}}

            I2CtoTimer = I2CTO;         // reset timeout timer
            retVal = true;
        } else {
            closeI2Cbus();
        }

        if (DEBUG) {System.out.println("[I2CHandler]: -openI2Cbus()");}

        return retVal;
    }

    private boolean closeI2Cbus() {
        final boolean DEBUG = false;
        boolean retVal = false;

        if (DEBUG) {System.out.println("[I2CHandler]: +closeI2Cbus()");}

        try {
            if (inStream != null)  {inStream.close(); inStream = null;}
            if (outStream != null) {outStream.close(); outStream = null;}
            if (cc != null) {cc.close(); cc = null;}
            
            if (DEBUG) {System.out.println("[I2CHandler]: I2C bus closed");}
            retVal = true;
        }
        catch (Exception e) {System.out.println("[I2CHandler]: Exception when closing I2C bus " + e.toString());}

        if (DEBUG) {System.out.println("[I2CHandler]: -closeI2Cbus()");}

        return retVal;
    }

    public void checkTimeOutStatus() {
        final boolean DEBUG = false;

        if (DEBUG) {System.out.println("[I2CHandler]: +checkTimeOutStatus(" + I2CtoTimer + ")");}
        
        if (I2CtoTimer > 0) {
            I2CtoTimer = I2CtoTimer - 1;
        } else {
            if ((cc != null) || (outStream != null) || (inStream != null)) {
                if (DEBUG) {System.out.println("[I2CHandler]: I2C bus closing, due to inactivity timeout...");}
                closeI2Cbus();
                if (DEBUG) {System.out.println("[I2CHandler]: I2C bus closed, due to inactivity timeout.");}
            }
        }

        if (DEBUG) {System.out.println("[I2CHandler]: -checkTimeOutStatus(" + I2CtoTimer + ")");}
    }
    
    private void startTimeOutTimerTask() {
        if (toTimer == null) {
            toTimer = new Timer();
            if (toTimer != null) {
                toTimer.schedule(new I2CTimeOutTimerTask(), 10000, 1000);
                System.out.println("[I2CHandler]: toTimer started.");
            } else {
                System.out.println("[I2CHandler]: Warning: startTimeOutThread() unable to create toTimer.");
            }
        } else {
            System.out.println("[ConnManager]: Warning: startTimeOutThread() called but toTimer already running.");
        }
    }

    private boolean checkResponseAck(String responseIn) {
        final boolean DEBUG = false;
        boolean retVal = false;

        if (DEBUG) {System.out.println("[I2CHandler]: I2C response = " + responseIn);}
        
        if (responseIn != null) {
            retVal = true;

            // Response Message: (AT Command guide - Table 19.3)
            // {ID + }         Write OK
            // {ID + Data }    Read of x bytes OK
            // {ID - xxxx }    NAK for xth byte if Read or Write
            // {ID ! xxxx }      Protocol error in xth byte

            if (responseIn.indexOf("{a+") == -1) {
                retVal = false;
                
                if (responseIn.indexOf("{a-") > -1) {
                    System.out.println("[I2CHandler]: I2C command not acknowledged: '" + responseIn + "'");
                }
                
                if (responseIn.indexOf("{a!") > -1) {
                    System.out.println("[I2CHandler]: I2C command protocol error: '" + responseIn + "'");
                }
            }
        } else {
            System.out.println("[I2CHandler]: Warning checkResponseAck() called but responseIn == null");
        }
        
        return retVal;
    }
    
    public void throttleI2Caccess(String requesterIn) {
        final boolean DEBUG = false;
        final int NOTFOUND = 5007;           // ms to wait if not found on I2C bus
        
        if (continueEx == true) {
            try {Thread.sleep(NOTFOUND);} catch (Exception e) {System.out.println("[I2CHandler]: Exception " + e.toString());}
        } else {
            if (DEBUG) {
                System.out.println("[I2CHandler]: No further access to I2C, but still trying is: " + requesterIn);
            }
        }
    }
    
    // This will give two-char length string for an int.
    private String intToHex(int i){
        final char[] Hex = "0123456789ABCDEF".toCharArray();
        
        return  "" + Hex[(i & 0xF0) >> 4] + Hex[(i & 0x0F)];
    }
}

// --------------------------------------------------------------------------- //

class I2CTimeOutTimerTask extends TimerTask {
    private final boolean DEBUG = false;

    public void run() {
        if (DEBUG) {System.out.println("[I2CTimeOutTimerTask]: +timeOutTimerTaskThread()");}

        I2CHandler.getInstance().checkTimeOutStatus();

        if (DEBUG) {System.out.println("[I2CTimeOutTimerTask]: -timeOutTimerTaskThread()");}
    }
}    
