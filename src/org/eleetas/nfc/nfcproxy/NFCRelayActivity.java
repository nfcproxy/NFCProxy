package org.eleetas.nfc.nfcproxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.spec.KeySpec;
import java.util.Enumeration;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.eleetas.nfc.nfcproxy.NFCVars;
import org.eleetas.nfc.nfcproxy.utils.IOUtils;
import org.eleetas.nfc.nfcproxy.utils.LogHelper;
import org.eleetas.nfc.nfcproxy.utils.TextHelper;
import org.eleetas.nfc.nfcproxy.utils.BasicTagTechnologyWrapper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class NFCRelayActivity extends Activity {

	private static BasicTagTechnologyWrapper mTagTech = null;
	private TabHost mTabHost;
	private TextView mStatusView;
	private ScrollView mStatusTab;
	
	private static ServerSocket mServerSocket = null;	
	private SecretKey mSecret = null;

	private WakeLock mWakeLock;
	
	private boolean mDebugLogging = false;
	private int mPort = NFCVars.DEFAULT_PORT;
	private boolean mEncrypt = true;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu, menu);	    
	    return true;
	}	
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    		case R.id.settingsButton:
    			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
    				startActivity(new Intent(this, SettingsActivityCompat.class));
    			}
    			else {    				
    				startActivity(new Intent(this, SettingsActivity.class));
    			}
    			return true;
			default:
				return false;
    		
    	} 
    }	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.relay);
        
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.addTab(mTabHost.newTabSpec("status_tab").setContent(R.id.statusTab).setIndicator(getString(R.string.status)));        
        //mTabHost.addTab(mTabHost.newTabSpec("config_tab").setContent(R.id.configTab).setIndicator(getString(R.string.config)));        
        mStatusView = (TextView) findViewById(R.id.statusView);
        mStatusTab = (ScrollView) findViewById(R.id.statusTab);
        
        if (mServerSocket == null || (mServerSocket != null && mServerSocket.isBound() == false)) {
        	log("Starting Server...");
        	Thread th = new Thread(new ServerThread());
        	th.start();
        } 

        mStatusView.setText(getString(R.string.waiting) + "\n");		
		
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
			log("Error getting local IPs: " + e.toString());
		}
		if (ipAddr.length() == 0) {
			updateUIandScroll(getString(R.string.enable_wifi));
		}
		else {
			updateUIandScroll(ipAddr);
		}		
    }
    
    public void updateUIandScroll(CharSequence msg) {
    	mStatusView.append(TextUtils.concat(msg, "\n"));

    	//use post so that recent update from setText/append takes effect first (at least a better chance of updating)
    	mStatusTab.post(new Runnable(){
			@Override
			public void run() {
				mStatusTab.fullScroll(ScrollView.FOCUS_DOWN);		
			}    		
    	});
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
		Intent intent = getIntent();

		SharedPreferences prefs = getSharedPreferences(NFCVars.PREFERENCES, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("relayPref", false)) {
        	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
        		prefs.edit().putBoolean("relayPref", true).commit();
				Toast.makeText(this, getString(R.string.proxy_mode_unsupported), Toast.LENGTH_LONG).show();        		
        	}
        	else {
	        	Intent forwardIntent = new Intent(intent);
	        	forwardIntent.setClass(this, NFCProxyActivity.class);
	        	startActivity(forwardIntent);
	        	finish();
        	}
        }
        
        if (prefs.getBoolean("screenPref", true)) {
	        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, getString(R.string.app_name));
	        mWakeLock.acquire();
        }        
        
    	mPort = prefs.getInt("portPref", NFCVars.DEFAULT_PORT);
    	mEncrypt = prefs.getBoolean("encryptPref", true);
    	mDebugLogging = prefs.getBoolean("debugLogPref", false);        
        
		String text = "";
		
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
			Toast.makeText(this, getString(R.string.ndef_discovered), Toast.LENGTH_SHORT).show();
			//TODO			
		}
		else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
			Toast.makeText(this, getString(R.string.tech_discovered), Toast.LENGTH_SHORT).show();
			//TODO
		}		
		else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			Toast.makeText(this, getString(R.string.tag_discovered), Toast.LENGTH_SHORT).show();
			text = getTagInfo(intent);
		}
		updateUIandScroll(text);
    }        
    
	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		super.onPause();
		
		if (mWakeLock != null) {
			mWakeLock.release();
		}		
	}

	private String getTagInfo(Intent intent)
	{
		Tag extraTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);	//required
		Parcelable[] extraNdefMsg = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);	//optional
		byte[] extraID = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);	//optional
		
		extraTag.getId();
		
		String text = "";	//TODO: make this StringBuilder
		//text = extraTag.toString();
		
		String[] techList = extraTag.getTechList();
    	if (techList.length > 0 ) {
    		text += "TechList: ";    	
	    	for (String s: techList) {
	    		text += s + ", "; 	    	
	    	}
			//for now, just choose the first tech in the list
    		String tech = techList[0];
    	
    		try {
    			mTagTech = new BasicTagTechnologyWrapper(extraTag, tech);
    		} catch (NoSuchMethodException e) {
    			mTagTech = null;
    			log("Unsupported tag type: " + e.toString());
    		} catch (IllegalArgumentException e) {
    			mTagTech = null;
    			log("Unsupported tag type: " + e.toString());
			} catch (ClassNotFoundException e) {
    			mTagTech = null;
    			log("Unsupported tag type: " + e.toString());
			} catch (IllegalAccessException e) {
    			mTagTech = null;
    			log("Unsupported tag type: " + e.toString());
			} catch (InvocationTargetException e) {
    			mTagTech = null;
    			log("Unsupported tag type: " + e.toString());
			}
			
			if (mTagTech != null) {
				
			}
    	}		
    	    	    	
		text += "\nNDEF Messages: ";
        if (extraNdefMsg != null) {        	
        	NdefMessage[] msgs = new NdefMessage[extraNdefMsg.length];
            for (int i = 0; i < extraNdefMsg.length; i++) {
                msgs[i] = (NdefMessage) extraNdefMsg[i];
                text += msgs[i].toString() + ", ";
            }
        }
        else
        	text += "null";
        
        text += "\nExtra ID: ";
        if (extraID != null) {
        	text += TextHelper.byteArrayToHexString(extraID);
        }
        else
        	text += "null";        
      
        text += "\nUID: " + TextHelper.byteArrayToHexString(extraTag.getId()) + "\n";
		return text;
        
	}     

    /* (non-Javadoc)
	 * @see android.app.Activity#onNewIntent(android.content.Intent)
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
	}
	
    public class ServerThread implements Runnable 
    {
    	private void updateUI(final CharSequence msg) {
    		mStatusView.post(new Runnable() {

				@Override
				public void run() {
					mStatusView.append(TextUtils.concat(msg, "\n"));					
				}    			
    		});
    		mStatusTab.post(new Runnable() {

				@Override
				public void run() {
					mStatusTab.fullScroll(ScrollView.FOCUS_DOWN);					
				}    			
    		});
    		
    	}
        public void run() {        	
    		try {    			
    			mServerSocket = new ServerSocket(mPort);
    		}
    		catch(final IOException e) {
    			log(e);
    			updateUI(e.toString());
    			return;
    		}

    		Socket clientSocket = null;
    		byte[] salt = new byte[8];
            log("Listening...");
            while (true) {                    
            	log("Waiting for connection...");
                try {
                	
					clientSocket = mServerSocket.accept();
					log("Connected.");
                    
                	if (mTagTech != null && (clientSocket != null && clientSocket.isConnected())) {

                		BufferedInputStream is = new BufferedInputStream(clientSocket.getInputStream());
                		BufferedOutputStream os = new BufferedOutputStream(clientSocket.getOutputStream());
                	
                		String line = null;
        				line = IOUtils.readLine(is);                    			
log("command: " + line);        
    					if (line.equals(NFCVars.READY)) {
         						
    						IOUtils.sendSocket((NFCVars.OPTIONS + "\n").getBytes("UTF-8"), os, null, false);
         						
    						line = IOUtils.readLine(is);
         						
    						if (line.equals(NFCVars.ENCRYPT)) {
	    						//TODO: move to IOUtils
	    						int n = is.read(salt, 0, 8);
	    						//lazy
	    						if (n != 8)	{
	    							log("meh...expected 8 bytes. got: " + n);
	    							updateUI(getString(R.string.connection_funny));
	    							if (clientSocket != null) {
	    								clientSocket.close();
	    								continue;
	    							}
	    						}
	    						
	    						if (mEncrypt) {
	    							//Have to do this every time...TODO: notify ends upon password change?
	    							mSecret = generateSecretKey(salt);
	    						}
	    						
	    						IOUtils.sendSocket(NFCVars.VERIFY.getBytes("UTF-8"), os, mSecret, mEncrypt);
	        					
	        					byte[] ok = IOUtils.readSocket(is, mSecret, mEncrypt);
	        					if (ok == null) {
	        						updateUI(getString(R.string.mismatched_protocol));
	        						log(getString(R.string.mismatched_protocol));
	        						continue;
	        					}
	        					String response = new String(ok, "UTF-8");
	        	    	    	if (!response.equals(NFCVars.OK)) {
	    				    		if (response.equals(NFCVars.BAD_PASSWORD)) {
	    				    			log(getString(R.string.bad_password));
	    				    			updateUI(getString(R.string.bad_password));
	    				    		}
	    				    		log(getString(R.string.unexpected_response));
	    				    		updateUI(getString(R.string.unexpected_response));
log(TextHelper.byteArrayToHexString(ok));				    		
	    				    		continue;
	        	    	    	}	        					
    						}
    						else if (!line.equals(NFCVars.CLEAR)){
    				    		log(getString(R.string.unexpected_response));
    				    		updateUI(getString(R.string.unexpected_response));
        						continue;    							
    						}
log("clear!");    						
    					}
    					else continue; //unsupported
        					
    					
    					try {
				            if (!mTagTech.isConnected()) {
				            	mTagTech.connect();
				            }
				            ////////////////////////////////////
				            //Start sending tag data
    						
							//From IsoPcdA doc	
        				     //@param data  - on the first call to transceive after PCD activation, the data sent to the method will be ignored				            
				            IOUtils.sendSocket(mTagTech.getTag().getId(), os, mSecret, mEncrypt);

				            byte[] response = null;
				        	do {                
				        		response = IOUtils.readSocket(is, mSecret, mEncrypt);
				        		if (response == null)
				            	{
				        			log("no response from PCD");                            		
				    				break;
				            	}                            	
log("response from PCD: " + TextHelper.byteArrayToHexString(response));
				
log("sending response to card");
				                response = mTagTech.transceive(response);
log("response from card: " +  TextHelper.byteArrayToHexString(response));
				          
log("sending card response to PCD");

								IOUtils.sendSocket(response, os, mSecret, mEncrypt);
log("wrote: " + new String(response));
				    		} while (response != null);
                		}
    	            	catch (final IllegalStateException e) {
    	            		log(e);
    						updateUI(getString(R.string.lost_tag));          
    	                	if (mTagTech != null) {
    	                		try {
    								mTagTech.close();
    							} catch (IOException e2) {
    								log(e);
    							}
    							finally {
    								mTagTech = null;
    							}
    	                		log("mTagTech closed1");
    	                	}
    						if (clientSocket != null) {
    							try {
    		            			if (!clientSocket.isClosed()) {
    		                			//clientSocket.getOutputStream().write("Relay lost tag".getBytes("UTF-8"));
    		            				IOUtils.sendSocket("Relay lost tag".getBytes("UTF-8"), clientSocket.getOutputStream(), mSecret, mEncrypt);
    	    	                	}								
    							} catch (IOException e3) {
    								log(e3);
    							}
    	                	}	                	                		
    	            	}
    	                catch (final IOException e) {
    	                	log(e);
    	                	
    	                	if (e.getCause() instanceof IllegalBlockSizeException) {
    	                		updateUI(getString(R.string.crypto_error));
    	                		log(getString(R.string.crypto_error));    	                		
    	                	}
    	                	else {
	    	                	updateUI(getString(R.string.lost_tag));
	    	                	if (mTagTech != null) {
	    	                		try {
	    								mTagTech.close();
	    							} catch (IOException e2) {
	    								log(e);
	    							}
	    							finally {
	    								mTagTech = null;
	    							}
	    	                		log("mTagTech closed2");
	    	                	}
	    	                	if (clientSocket != null) {
	    	                		try {
	    	    	                	//if (e.getMessage().contains("Transceive failed") && !clientSocket.isClosed()) {
	    	                			if (!clientSocket.isClosed()) {
	    		                			//clientSocket.getOutputStream().write("Relay lost tag".getBytes("UTF-8"));
	    	                				IOUtils.sendSocket("Relay lost tag".getBytes("UTF-8"), clientSocket.getOutputStream(), mSecret, mEncrypt);        		                			
	    	    	                	}								
	    							} catch (IOException e2) {
	    								log(e2);
	    							}
	    	                	}	  
    	                	}
    	                }		        					
log("done reading...");
                	}
                	else {
                		if (mTagTech == null) {
                			updateUI(getString(R.string.nfcproxy_connected_no_tag));
                			log("Closed connection to NFCRelay. mTagTech null");
	                		if (clientSocket != null) {
	                    		//OutputStream os = clientSocket.getOutputStream();
	                    		//os.write("NOT READY".getBytes("UTF-8"));
	                    		//os.flush();
	                    		//sendEncrypted("NOT READY".getBytes("UTF-8"), clientSocket.getOutputStream(), salt);
	                			IOUtils.sendSocket("NOT READY\n".getBytes("UTF-8"), clientSocket.getOutputStream(), null, false);
		                		try {
									clientSocket.close();
								} catch (IOException e2) {
									log(e2);
								}
	                		}		                	
                		}
                		else {
                			log("clientSocket null?");
                		}
                	}
                } 
                catch(IOException e) {
                	log(e);
                	updateUI(getString(R.string.ioexception));
                	
                }
                finally {
                	if (clientSocket != null) {
                		try {
                			clientSocket.close();
                			log("Closed client connection");
                		} catch (IOException e2) {
                			log(e2);
                		}
                		
                	}
                }                    
            }//while
        }
    }        
	private SecretKey generateSecretKey(byte[] salt) throws IOException {
		try {
    		SharedPreferences prefs = getSharedPreferences(NFCVars.PREFERENCES, Context.MODE_PRIVATE);
    		SecretKeyFactory f;
			f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");			
	        KeySpec ks = new PBEKeySpec(prefs.getString("passwordPref", getString(R.string.default_password)).toCharArray(), salt, 2000, 256);
	        SecretKey tmp = f.generateSecret(ks);
	        return new SecretKeySpec(tmp.getEncoded(), "AES");
		} catch (Exception e) {
			log(e);
			throw new IOException(e);
		}        			
	}
    
    private void log(Object msg) {
    	if (mDebugLogging) {
    		LogHelper.log(this, msg);
    	}
    }
 
	/* (non-Javadoc)
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);				
		outState.putInt("tab", mTabHost.getCurrentTab());
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		mTabHost.setCurrentTab(savedInstanceState.getInt("tab"));
	}
}
