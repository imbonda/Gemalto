package utils;

import com.cinterion.io.ATCommand;
import com.cinterion.io.ATCommandFailedException;
import com.cinterion.io.ATCommandListener;
import com.cinterion.io.BearerControl;
import com.cinterion.io.BearerControlListener;
import com.cinterion.io.BearerControlListenerEx;
import com.cinterion.io.BearerControlStates;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

/*
 * Extended BearerControl adding CS and PS registration events
 *
 * Added two things (new states) to the BearerControlListener.
 * CS attached (+CREG: )
 * PS attached (+CGREG: )
 *
 * Therefore the BearerControl.java has to be overwritten.
 *
 * @author menck
 * 
 */

public class BearerControlExt implements ATCommandListener {
    
    private static Vector extListners = new Vector();
    private static Vector normalListener = new Vector();

    /*
     * Overwritten methods
     */
    
    public void addListener(BearerControlListener listener) {
        BearerControl.addListener(listener);
        normalListener.addElement(listener);
    }

    public void addListenerEx(BearerControlListenerEx listener) {
        BearerControl.addListenerEx(listener);
        extListners.addElement(listener);
    }

    public void clearListener() {
        BearerControl.clearListener();
        extListners.removeAllElements();
        normalListener.removeAllElements();
    }

    public String[] GetBearerList() {
        return BearerControl.GetBearerList();
    }

    public int GetBearerState(String APN) {
        return BearerControl.GetBearerState(APN);
    }

    public String GetBearerV4Addr(String APN) throws IOException {
        return BearerControl.GetBearerV4Addr(APN);
    }

    public String GetBearerV4Dns1(String APN) throws IOException {
        return BearerControl.GetBearerV4Dns1(APN);
    }

    public String GetBearerV4Dns2(String APN) throws IOException {
        return BearerControl.GetBearerV4Dns2(APN);
    }

    public String GetBearerV6Addr(String APN) throws IOException {
        return BearerControl.GetBearerV6Addr(APN);
    }

    public String GetBearerV6Dns1(String APN) throws IOException {
        return BearerControl.GetBearerV6Dns1(APN);
    }

    public String GetBearerV6Dns2(String APN) throws IOException {
        return BearerControl.GetBearerV6Dns2(APN);
    }

    public void hangUp() {
        BearerControl.hangUp();
    }

    public int hangUp(String APN) {
        return BearerControl.hangUp(APN);
    }

    public void removeListener(BearerControlListener listener) {
        BearerControl.removeListener(listener);
        normalListener.removeElement(listener);
    }

    public void removeListenerEx(BearerControlListenerEx listener) {
        BearerControl.removeListenerEx(listener);
        extListners.removeElement(listener);
    }
    
    public static String stateDescription(int stateIn) {
        String retVal = null;

        switch(stateIn) {
            case BearerControl.BEARER_STATE_CLOSING: retVal = "BEARER_STATE_CLOSING.";
                break;
            case BearerControl.BEARER_STATE_CONNECTING: retVal = "BEARER_STATE_CONNECTING.";
                break;
            case BearerControl.BEARER_STATE_DOWN: retVal = "BEARER_STATE_DOWN.";
                break;
            case BearerControl.BEARER_STATE_LIMITED_UP: retVal = "BEARER_STATE_LIMITED_UP currently no network coverage.";
                break;
            case BearerControl.BEARER_STATE_UNKNOWN: retVal = "BEARER_STATE_UNKNOWN.";
                break;
            case BearerControl.BEARER_STATE_UP: retVal = "BEARER_STATE_UP.";
                break;
        } 
        
        return retVal;
    }

    /*
     * Constructor
     */

    public BearerControlExt(ATCommand atc) throws IllegalStateException, IllegalArgumentException, ATCommandFailedException {
        String response;

        if (atc != null) {
            atc.addListener(this);
        
            response = AtcHandler.getInstance().sendATC("AT+CIMI\r");
            if ((response.indexOf("AT+CIMI") > -1) && (response.indexOf("OK") > -1)) {
                response = atc.send("AT+CREG=1\r");
                if (response.indexOf("OK") < 0) {throw new ATCommandFailedException("[BearerControlExt]: " + response);}

                response = atc.send("AT+CGREG=1\r");
                if (response.indexOf("OK") < 0) {throw new ATCommandFailedException("[BearerControlExt]: " + response);}
            }
        }
    }

    public void ATEvent(String arg0) {
        int pos;
        
        if (arg0.indexOf("+CREG:") > 0) {
            pos = arg0.indexOf(':');
            int c = arg0.charAt(pos + 2);
            c -= 38;
            
            for (Enumeration e = normalListener.elements(); e.hasMoreElements();) {
                BearerControlListener bl = (BearerControlListener) e.nextElement();
                bl.stateChanged(c);
            }
            
            for (Enumeration e = extListners.elements(); e.hasMoreElements();) {
                BearerControlListenerEx bl = (BearerControlListenerEx) e.nextElement();
                bl.stateChanged("n/a", c, 0);
            }
        }
        
        if (arg0.indexOf("+CGREG:") > 0) {
            pos = arg0.indexOf(':');
            int c = arg0.charAt(pos + 2);
            c -= 28;
            
            for (Enumeration e = normalListener.elements(); e.hasMoreElements();) {
                BearerControlListener bl = (BearerControlListener) e.nextElement();
                bl.stateChanged(c);
            }
            
            for (Enumeration e = extListners.elements(); e.hasMoreElements();) {
                BearerControlListenerEx bl = (BearerControlListenerEx) e.nextElement();
                bl.stateChanged("n/a", c, 0);
            }
        }
    }

    public void CONNChanged(boolean arg0) {
    }

    public void DCDChanged(boolean arg0) {
    }

    public void DSRChanged(boolean arg0) {
    }

    public void RINGChanged(boolean arg0) {
    }
}

interface BearerControlStatesExt extends BearerControlStates {	
    public static final int	BEARER_STATE_CS_NOT_REGISTERED = 10;	
    public static final int	BEARER_STATE_CS_REGISTERED_HOME = 11;
    public static final int	BEARER_STATE_CS_SEARCHING = 12;
    public static final int	BEARER_STATE_CS_REGISTRATION_DENIED = 13;
    public static final int	BEARER_STATE_CS_REGISTRATION_UNKNOWN = 14;
    public static final int	BEARER_STATE_CS_ROAMING = 15;
    
    public static final int	BEARER_STATE_PS_NOT_REGISTERED = 20;	
    public static final int	BEARER_STATE_PS_REGISTERED_HOME = 21;
    public static final int	BEARER_STATE_PS_SEARCHING = 22;
    public static final int	BEARER_STATE_PS_REGISTRATION_DENIED = 23;
    public static final int	BEARER_STATE_PS_REGISTRATION_UNKNOWN = 24;
    public static final int	BEARER_STATE_PS_ROAMING = 25;
}
