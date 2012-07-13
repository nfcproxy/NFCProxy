package org.eleetas.nfc.nfcproxy.utils;

import java.security.SecureRandom;

import android.util.Base64;

public class CryptoHelper {
	
    public static String generateSalt() {
        SecureRandom sr = new SecureRandom();
        byte[] salt = new byte[8];
        sr.nextBytes(salt);
        return Base64.encodeToString(salt, Base64.DEFAULT);        
        
    }    

}
