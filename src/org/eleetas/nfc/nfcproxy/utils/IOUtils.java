package org.eleetas.nfc.nfcproxy.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AlgorithmParameters;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class IOUtils {
    public static byte[] readSocket(InputStream is, SecretKey secret, boolean encrypt) throws IOException {
		if (!encrypt) {
			return readUnencrypted(is);	
		}
		else {
			if (secret == null) {
				return null;
			}
	        
		   try{
	
		        byte[] iv = new byte[16];	        
	        	byte[] buffer = new byte[1024];        	
	        	int num = is.read(buffer);
	        	if (num < 0 || num <= iv.length) {
	        		return null;
	        	}
	        	byte[] cipherText = new byte[num - iv.length];
	        	System.arraycopy(buffer, 0, iv, 0, iv.length);
	        	System.arraycopy(buffer, iv.length, cipherText, 0, cipherText.length);
	        	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		        cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
		        return cipher.doFinal(cipherText);
			} catch (Exception e) {
				e.printStackTrace();
				throw new IOException(e);
			}
		}			
	}
    		    
    public static String readLine(InputStream is) {
    	if(is == null) { return null;}
    	
    	byte[] c = new byte[1];
    	byte[] str = new byte[1024];
    	int i = 0;
    	try {
	    	while ( is.read(c) != -1 && c[0] != 10 && i < 1024) {	
	    		str[i] = c[0];
	    		i++;
	    	}
	    	if (c[0] == 10)
	    		return new String(str, 0, i, "UTF-8");
	    	else
	    		return null;
    	}
    	catch (IOException e) {
    		e.printStackTrace();
    	}
    	return null;
    }	    
    
	private static byte[] readUnencrypted(InputStream is) {			
    	if (is == null ) { return null;	}
    	byte[] buf = new byte[1024];
    	try {
    		int numBytes = is.read(buf);
    		if (numBytes != -1) {
    			return Arrays.copyOf(buf, numBytes);
    		}
    		else {    			
    			return null;
    		}
    	}
    	catch (IOException e) {
    		e.printStackTrace();
    	}			
		return null;
	}
	
	private static void sendUnencrypted(byte[] data, OutputStream os) throws IOException {
		os.write(data);
		os.flush();			
	}
	
    public static void sendSocket(byte[] data, OutputStream os, SecretKey secret, boolean encrypt) throws IOException {
    	if (!encrypt) {
    		sendUnencrypted(data, os);
    	}
    	else
    	{	    	
	    	if (secret == null) {
	    		return;
	    	}
	        try {	        
		        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		        cipher.init(Cipher.ENCRYPT_MODE, secret);
		        AlgorithmParameters params = cipher.getParameters();
		        byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();	//16 bytes
		        byte[] cipherText = cipher.doFinal(data);	
				//for each piece of data send: iv + ciphertext
		        byte[] send = new byte[iv.length + cipherText.length];
		        System.arraycopy(iv, 0, send, 0, iv.length);
		        System.arraycopy(cipherText, 0, send, iv.length, cipherText.length);
		        os.write(send);
		        os.flush();
		        
			} catch (Exception e) {
				e.printStackTrace();
				throw new IOException(e);
			}
    	}			
    }	
}
