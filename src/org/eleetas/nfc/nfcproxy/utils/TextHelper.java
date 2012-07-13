package org.eleetas.nfc.nfcproxy.utils;

public class TextHelper {
    public static String byteArrayToHexString(byte[] b) {
    	return byteArrayToHexString(b, "0x", " ", false);    	
    }
    
	public static String byteArrayToHexString(byte[] b, String hexPrefix, String hexSuffix, boolean cast) {
		if (b == null) return null;
		StringBuffer sb = new StringBuffer(b.length * 2);
		for (int i = 0; i < b.length; i++) {			
			int v = b[i] & 0xff;
			if (cast && v > 0x7f) {
				sb.append("(byte)");
			}
			sb.append(hexPrefix);
			if (v < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
			if (i + 1 != b.length) {
				sb.append(hexSuffix);
			}
		}
		return sb.toString();
	}
}
