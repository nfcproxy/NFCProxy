package org.eleetas.nfc.nfcproxy.utils;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Formatter;

public class TagHelper {

    public static String parseCC(byte []data) {
    	//check if data is an EMV Record Template and Track 2 equivalent data is present
    	if (data.length > 3 && data[0] == 0x70 && data[2] == 0x57) {
    		//TODO: Length error checking

            int PANOffset= 4;
            int nameOffset= 23;
    		
	        StringBuilder sb = new StringBuilder();	        
	        sb.append("Name: ");   
	        int length = data[nameOffset + 2];
	        try {
				sb.append(new String(data, nameOffset+3, length, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
	
			sb.append(parseTrack2(data, 4));
	        
	        sb.append("\niCVV: ");
	        Formatter format = new Formatter();					//print actual byte order. differs from readable format.
	        sb.append(format.format("%02x%1x%02x %02x (0x%02x 0x%02x 0x%02x 0x%02x)", data[PANOffset + 8 + 8], data[PANOffset + 8 + 6], data[PANOffset + 8 + 7], data[PANOffset + 8 + 9], data[PANOffset + 8 + 6], data[PANOffset + 8 + 7], data[PANOffset + 8 + 8], data[PANOffset + 8 + 9]).toString());
	        //format = new Formatter();
	        //sb.append(format.format("%02x%1x%1x%1x", data[OFFSET_CC + 8 + 8], data[OFFSET_CC + 8 + 6], data[OFFSET_CC + 8 + 7], data[OFFSET_CC + 8 + 9]));
	        return sb.toString();
    	}
		//TODO: HACK. response looks like: 0x70 0x81 0x9e 0x9f 0x6c 0x02 0x00 0x01 0x56 0x4c 0x42
    	else if (data.length > 9 && data[0] == 0x70 && data[8] == 0x56 ) {
    		//TODO: Length error checking
    		/*
    		 * PayPass Ð M/ChipTechnical Specifications (https://docs.google.com/viewer?a=v&q=cache:O0rYe0zxyegJ:read.pudn.com/downloads161/doc/725864/PayPass%2520-%2520MChip%2520(V1.3).pdf+mastercard+mchip+technical+specification&hl=en&gl=us&pid=bl&srcid=ADGEESjl5lr24scpx8am0GSqalJj0iIa7NZNK1_XGsjw0pUqBnhIlCH4ZLu4UcbQKHP3IQxTNDbcWRSeu0sSbShZ7SInM0afHwwO6S5VOgrqvj4l44mO9UeltVVreEfRyCMUM8sLIqWm&sig=AHIEtbTWQac_EGUzlfP4hZiFavpSJnmsCw)
    		 * 
    		 * Tag: Ô56Õ
    		 * ans, variable length up to 76 bytes
    		 * The Track 1 Data contains the data elements of the track 1 according
    		 * to ISO/IEC 7813 Structure B, excluding start sentinel, end sentinel
    		 * and LRC.
    		 * Format Code (hex Ô42Õ (B))
    		 * 1 byte
    		 * Identification Number (PAN)
    		 * var. up to 19 bytes
    		 * Field Separator (hex. Ô5EÕ (^))
    		 * 1 byte
    		 * Name (see ISO/IEC 7813)
    		 * 2 to 26 bytes
    		 * Field Separator (hex. Ô5EÕ (^))
    		 * 1 byte
    		 * Expiry Date (YYMM)
    		 * 4 bytes
    		 * Service Code
    		 * 3 bytes
    		 * Discretionary Data
    		 * balance of available bytes
    		 */

    		return parseTrack1(data, 11);
    	}
    	else {
    		return "Unsupported CC format";
    	}
    
    }       
 
	//TODO: Length error checking
    public static String parseTrack2(byte[] track2, int offset) {
    	int PANLength = 8;
    	int expOffset = offset + PANLength + 1;
    	int svcOffset = offset + PANLength + 3;
    	
    	StringBuilder sb = new StringBuilder();
        sb.append("\nCard Number: ");        
        String ccnum="";
        
    	for(int i = 0; i < PANLength; i++) {                
			Formatter format = new Formatter();
            ccnum += format.format("%02x", track2[offset + i]).toString();	                        
    	}
        sb.append(ccnum);
        
        sb.append("\nExpiration Date: ");
        String exp="";
        Formatter format = new Formatter();        
        short high = 0;
        high |= track2[expOffset] & 0xFF;               
        short low = 0;
        low |= track2[expOffset + 1] & 0xFF;
        short full = (short) ((high << 12) + (low << 4) | (high >>> 4));

        //TODO: internationalize...right now mm/yy
        exp += format.format("%02x/%02x", (byte)(full >>> 8), (byte)((full << 8) >>> 8) ).toString();
        sb.append(exp);
        
        sb.append("\nService Code: ");
        String scode = "";
        format = new Formatter();        
        high = 0;
        high |= track2[svcOffset];// & 0xFF;               

        byte low_low = (byte) (track2[svcOffset + 1] >> 4);
        byte low_hi = (byte) (track2[svcOffset + 1] << 4);
        low = 0;
        low |= low_hi | low_low;
        //TODO: is this right? check order
        scode += format.format("%02x%02x", high, low).toString();
        sb.append(scode);
        
        return sb.toString();    	
    }
    
    public static String parseTrack1(byte[] track1, int offset) {
    	//TODO: Length error checking
    	int PANLength = 16;
    	int nameOffset = offset + PANLength + 1;
    	
    	StringBuilder sb = new StringBuilder();
    	
        sb.append("Name: ");
        String name = "";
        int dIndex = findDelimiterIndex(track1, nameOffset);
    	int expOffset = dIndex + 1;
    	int svcOffset = dIndex + 5;
        
        if (dIndex != -1) {
        	name = new String(track1, nameOffset, dIndex - nameOffset); //add "UTF-8"?
        }
        else {
        	name = "(No name)";
        }
        sb.append(name.trim());
        
        sb.append("\nCard Number: ");        
        String ccnum="";
        
        ccnum = new String(track1, offset, PANLength);        
        sb.append(ccnum);
        
        sb.append("\nExpiration Date: ");
        
        String yr = new String(track1, expOffset, 2);
        String mo = new String(track1, expOffset + 2, 2);
        //TODO: internationalize...right now mm/yy
        String exp = mo + "/" + yr; 
        sb.append(exp);
        
        sb.append("\nService Code: ");
        String scode = new String(track1, svcOffset, 3);        
        sb.append(scode);
        
        return sb.toString();    	
    	
    }
    
    private static int findDelimiterIndex(byte[] data, int nameOffset) {
    	for (int i = nameOffset; i < data.length; i++) {
    		              //'^'
    		if (data[i] == 0x5e) return i;
    	}
    	return -1;
    }
    
    public static String parseCryptogram(byte cardData[], byte[] pcdData) {
    	byte[] cvc3Track1 = new byte[2];
    	byte[] cvc3Track2 = new byte[2];
    	byte[] counter = new byte[2];
    	
    	if (cardData.length < 16) return "Unsupported cryptogram";
    	
    	//assume first 2 bytes are 0x77 and length. should have 3 tags 9f60, 9f61, and 9f36. 
    	for (int i = 3; i < 14; i += 5) { //3, 8 ,13
    		switch (cardData[i]) {
    		case 0x61:
    			cvc3Track2[0] = cardData[i + 2]; 
				cvc3Track2[1] = cardData[i + 3];
    			break;
    		case 0x60:
    			cvc3Track1[0] = cardData[i + 2];
				cvc3Track1[1] = cardData[i + 3];
    			break;
    		case 0x36:
    			counter[0] = cardData[i + 2];
				counter[1] = cardData[i + 3];
    			break;
    		}
    	}
    	
    	//Unpredictable Number Data Object List (UDOL)
    	//80 2a 8e 80 [len] [data] 00
    	byte[] udol = Arrays.copyOfRange(pcdData, 5, pcdData.length - 1);
    	
    	
    	StringBuilder sb = new StringBuilder();
    	sb.append("CVC3 Track1: ").append(TextHelper.byteArrayToHexString(cvc3Track1)).append("\n");
    	sb.append("CVC3 Track2: ").append(TextHelper.byteArrayToHexString(cvc3Track2)).append("\n");
    	sb.append("UDOL: ").append(TextHelper.byteArrayToHexString(udol)).append("\n");
    	sb.append("Counter: ").append(TextHelper.byteArrayToHexString(counter));    	
    	return sb.toString();
    	
/*    	
    	0x77 0x0f 
    	0x9f 0x61 0x02 0x7f 0x48
    	0x9f 0x60 0x02 0x0e 0xb7
    	0x9f 0x36 0x02 0x00 0x12
    	
cardData[2] cardData[3]
cardData[7] cardData[8]
cardData[12] cardData[13]
*/    	
          
                      
    }
}
