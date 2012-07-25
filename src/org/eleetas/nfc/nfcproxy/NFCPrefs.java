package org.eleetas.nfc.nfcproxy;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.eleetas.nfc.nfcproxy.NFCVars;
import org.eleetas.nfc.nfcproxy.utils.CryptoHelper;
import org.eleetas.nfc.nfcproxy.utils.LogHelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

public class NFCPrefs extends PreferenceFragment {

	/* (non-Javadoc)
	 * @see android.preference.PreferenceFragment#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		
		PreferenceManager.setDefaultValues(getActivity(), NFCVars.PREFERENCES, Context.MODE_PRIVATE, R.xml.preferences, true);
		PreferenceManager pMan = getPreferenceManager();		
		pMan.setSharedPreferencesName(NFCVars.PREFERENCES);
		final SharedPreferences prefs = pMan.getSharedPreferences();
		
		//need to call this AFTER setting PreferenceManger stuff.
		addPreferencesFromResource(R.xml.preferences);
		
		final EditTextPreference password = (EditTextPreference) findPreference("passwordPref");
		password.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (newValue.toString().length() < 8) {
					password.getEditText().setError(NFCPrefs.this.getActivity().getString(R.string.min_password_length));
					SpannableString msg = new SpannableString(NFCPrefs.this.getActivity().getString(R.string.min_password_length));
					msg.setSpan(new ForegroundColorSpan(Color.RED) , 0, msg.length(), 0);
					password.setSummary(msg);
					Toast.makeText(NFCPrefs.this.getActivity().getBaseContext(), NFCPrefs.this.getActivity().getString(R.string.password_not_saved), Toast.LENGTH_LONG).show();
					return false;
				}
				password.getEditText().setError("");
				password.setSummary(getString(R.string.password_desc));
				
				//TODO: Not sure that salting is necessary since we're generating a key from the password and sending the salt in the clear
				prefs.edit().putString("saltPref", CryptoHelper.generateSalt()).commit();
				return true;
			}
			
		});
	}

	/* (non-Javadoc)
	 * @see android.preference.PreferenceFragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			
		PreferenceManager.setDefaultValues(getActivity(), NFCVars.PREFERENCES, Context.MODE_PRIVATE, R.xml.preferences, true);
		PreferenceManager pMan = getPreferenceManager();		
		pMan.setSharedPreferencesName(NFCVars.PREFERENCES);
		SharedPreferences prefs = pMan.getSharedPreferences();
		EditTextPreference ip = (EditTextPreference) findPreference("ipPref");
		if (prefs.getBoolean("relayPref", true)) {
			ip.setEnabled(false);
			String ipAddr = "";
	        try {
				//assume IP is on wlan0 interface
				NetworkInterface net = NetworkInterface.getByName("wlan0");
				if (net != null) {
					for (Enumeration<InetAddress> enumIpAddr = net.getInetAddresses(); enumIpAddr.hasMoreElements();) {	
						InetAddress inetAddress = enumIpAddr.nextElement();					 
							if (inetAddress instanceof Inet4Address) {
								ipAddr = inetAddress.getHostAddress().toString();
								break;
							}
					}
				}
			} catch (SocketException e) {
				LogHelper.log(getActivity(), "Error getting local IPs: " + e.toString());
			}
			
			if (ipAddr.length() == 0) {
				ip.setSummary(getString(R.string.enable_wifi));
			}
			else {
				ip.setSummary(ipAddr);
			}
		}
		else {
			ip.setEnabled(true);
			ip.setSummary(getString(R.string.ip_desc));			
		}		
		
		return super.onCreateView(inflater, container, savedInstanceState);
	}
	
	
}
