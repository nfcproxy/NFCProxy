package org.eleetas.nfc.nfcproxy.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eleetas.nfc.nfcproxy.R;

import android.content.Context;
import android.util.Log;

public class LogHelper {
    public static void log(Context c, Object msg)
    {
    	if (msg instanceof Exception) {
    		Exception e = (Exception)msg;
        	StringWriter sw = new StringWriter();
        	e.printStackTrace(new PrintWriter(sw));
        	Log.d(c.getString(R.string.app_name), e.toString() + "\n" + sw.toString());            	
    	}
    	else {
    		Log.d(c.getString(R.string.app_name), String.valueOf(msg));
    	}
    }
}
