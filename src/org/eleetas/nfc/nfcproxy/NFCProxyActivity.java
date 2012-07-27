package org.eleetas.nfc.nfcproxy;

import org.eleetas.nfc.nfcproxy.NFCVars;
import org.eleetas.nfc.nfcproxy.utils.CryptoHelper;
import org.eleetas.nfc.nfcproxy.utils.IOUtils;
import org.eleetas.nfc.nfcproxy.utils.TagHelper;
import org.eleetas.nfc.nfcproxy.utils.TextHelper;
import org.eleetas.nfc.nfcproxy.utils.LogHelper;
import org.eleetas.nfc.nfcproxy.utils.BasicTagTechnologyWrapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.Base64;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

public class NFCProxyActivity extends Activity {
    
	private static final int PROXY_MODE = 0;
	private static final int REPLAY_PCD_MODE = 1;
	private static final int REPLAY_TAG_MODE = 2;				
	private final int CONNECT_TIMEOUT = 5000;
	private final String DEFAULT_SALT = "kAD/gd6tvu8=";
	
	
	private ScrollView mStatusTab;
	private TextView mStatusView;
	private TextView mDataView;
	private ScrollView mDataTab;
	private TableLayout mDataTable;	
	private TabHost mTabHost;
	private ListView mSavedList;
	private Menu mOptionsMenu;
	private ActionMode mActionMode;
	
	private InetSocketAddress mSockAddr;
	private DBHelper mDBHelper;
	private SecretKey mSecret = null;
	private String mSalt = null;
		
	private View mSelectedSaveView;
	private int mSelectedId = 0;		
	private Bundle mSessions = new Bundle();
	private Bundle mReplaySession;		
	
	private WakeLock mWakeLock;
	private int mMode = PROXY_MODE;
	
	private boolean mDebugLogging = false;
	private int mServerPort;
	private String mServerIP;
	private boolean mEncrypt = true;
	private boolean mMask = false;
	
	private ActionMode.Callback mTransactionsActionModeCallback = new ActionMode.Callback() {

	    @Override
	    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	        MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.data_context_menu, menu);
	        return true;
	    }

	    @Override
	    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	        return false; // Return false if nothing is done
	    }

	    @Override
	    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
	    	mReplaySession = mSessions.getBundle(String.valueOf(mSelectedId));
	        switch (item.getItemId()) {
	            case R.id.replayPcd:
					enablePCDReplay();
	                mode.finish();
	                return true;
	            case R.id.replayTag:
	            	enableTagReplay();
	                mode.finish();
	                return true;	       
	            case R.id.delete:
					deleteRun();
					mode.finish();									
	            	return true;
	            case R.id.save:
	            	SaveDialogFragment.newInstance().show(getFragmentManager(), "savedialog");
	            	mode.finish();
	            	return true;	            	
	            case R.id.export:
	            	ExportDialogFragment.newInstance().show(getFragmentManager(), "exportdialog");
	            	mode.finish();
	            	return true;
	            default:
	                return false;
	        }
	    }

	    @Override
	    public void onDestroyActionMode(ActionMode mode) {
	        mActionMode = null;
	    }
	};		

	private ActionMode.Callback mSavedActionModeCallback = new ActionMode.Callback() {

	    @Override
	    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	        MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.saved_context_menu, menu);
	        return true;
	    }

	    @Override
	    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	        return false; // Return false if nothing is done
	    }

	    @Override
	    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
	        switch (item.getItemId()) {
	            case R.id.deleteSaved:
					deleteSaved();
					mode.finish();									
	            	return true;
	            default:
	                return false;
	        }
	    }

	    @Override
	    public void onDestroyActionMode(ActionMode mode) {
	        mActionMode = null;
	    }
	};		
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu, menu);
	    mOptionsMenu = menu;	    
	    return true;
	}	
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    		case R.id.settingsButton:
    			if (Build.VERSION.SDK_INT<Build.VERSION_CODES.HONEYCOMB) {
					Toast.makeText(this, "settings", Toast.LENGTH_SHORT).show();    				
    			}
    			else {
    				startActivity(new Intent(this, SettingsActivity.class));
    			}
    			return true;
    		case R.id.proxyButton:
    	        mMode = PROXY_MODE;
    	        mOptionsMenu.getItem(0).setVisible(false);
    	        mOptionsMenu.getItem(1).setVisible(false);
    	        mOptionsMenu.getItem(2).setVisible(false);
    	        Toast.makeText(this,getString(R.string.switching_to_proxy_mode), Toast.LENGTH_LONG).show();        
    			return true;
			default:
				return false;    		
    	} 
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	log("onCreate start");    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.proxy);
        getActionBar().setDisplayShowHomeEnabled(false);
//        getActionBar().setDisplayShowTitleEnabled(false);
//        getActionBar().hide();
                
        mStatusView = (TextView) findViewById(R.id.statusView);
        mStatusTab = (ScrollView) findViewById(R.id.statusTab);

        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.addTab(mTabHost.newTabSpec("data_tab").setContent(R.id.dataTab).setIndicator(getString(R.string.data)));
        mTabHost.addTab(mTabHost.newTabSpec("status_tab").setContent(R.id.statusTab).setIndicator(getString(R.string.status)));        
        mTabHost.addTab(mTabHost.newTabSpec("saved_tab").setContent(R.id.saveTab).setIndicator(getString(R.string.saved)));
        mDataTab = (ScrollView) findViewById(R.id.dataTab);
        mDataTable = (TableLayout) findViewById(R.id.dataTable);
          
        mSavedList = (ListView) findViewById(R.id.savedListView);
        mDBHelper = new DBHelper(this);
        
        mSavedList.setAdapter(new CursorAdapter(this, mDBHelper.getReplays(), 0) {

			@Override
			public void bindView(View view, Context context, Cursor cursor) {
				TextView saveView = (TextView)view.findViewById(R.id.savedTextView);
				saveView.setOnLongClickListener(getSavedTextViewLongClickListener());
				int nameIdx = cursor.getColumnIndex("name");
				String name = cursor.getString(nameIdx);
				byte[] tBytes = cursor.getBlob(cursor.getColumnIndex("transactions"));
				byte[][] transactions = null;
				ByteArrayInputStream bais = new ByteArrayInputStream(tBytes);
				try {
					ObjectInputStream ois = new ObjectInputStream(bais);
					transactions = (byte[][])ois.readObject();
				} catch (StreamCorruptedException e) {
					log(e);
				} catch (IOException e) {
					log(e);
				} catch (ClassNotFoundException e) {
					log(e);
				}
				
				Bundle tag = new Bundle();
				tag.putSerializable("transactions", transactions);				
				tag.putString("name", name);
				saveView.setTag(tag);
				
				saveView.setText(getString(R.string.name) + ": " + name + "\n");
				int type = cursor.getInt(cursor.getColumnIndex("type"));				
				if (type == DBHelper.REPLAY_PCD) {
					saveView.append(getString(R.string.type_pcd));
					saveView.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							Bundle t = (Bundle)v.getTag();
							byte[][] tr = (byte[][])t.getSerializable("transactions");
							Bundle reqs = new Bundle(); 
							for (int i = 0; i < tr.length; i ++) {
								reqs.putByteArray(String.valueOf(i), tr[i]);
							}
							Bundle b = new Bundle();
							b.putBundle("requests", reqs);
							mReplaySession = b;
							enablePCDReplay();
						}					
					});									
				}
				else if (type == DBHelper.REPLAY_TAG) {
					saveView.append(getString(R.string.type_tag));
					saveView.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							Bundle t = (Bundle)v.getTag();
							byte[][] tr = (byte[][])t.getSerializable("transactions");
							Bundle resps = new Bundle(); 
							for (int i = 0; i < tr.length; i ++) {
								resps.putByteArray(String.valueOf(i), tr[i]);
							}
							Bundle b = new Bundle();
							b.putBundle("responses", resps);
							mReplaySession = b;
							enableTagReplay();
						}					
					});
				}				
				for (int i = 0; i < transactions.length; i++) {
					saveView.append(i + ": " + TextHelper.byteArrayToHexString(transactions[i]) + "\n");	
				}			
			}

			@Override
			public View newView(Context context, Cursor cursor, ViewGroup parent) {				
                final View view = LayoutInflater.from(NFCProxyActivity.this).inflate(R.layout.save_tab, parent, false);
                return view;						
			}
        });
        
        final SharedPreferences prefs = getSharedPreferences(NFCVars.PREFERENCES, MODE_PRIVATE);
        if (!prefs.contains("saltPref")) {
        	prefs.edit().putString("saltPref", CryptoHelper.generateSalt()).commit();
        }
        
        log("onCreate end");        
    }
    
	/* (non-Javadoc)
	 * @see android.app.Activity#onNewIntent(android.content.Intent)
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
	}	
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {		
		log("onResume start");		
		super.onResume();
		Intent intent = getIntent(); 
		
		SharedPreferences prefs = getSharedPreferences(NFCVars.PREFERENCES, Context.MODE_PRIVATE);		
		try {
			Class.forName(NFCVars.ISO_PCDA_CLASS);
		} catch (ClassNotFoundException e) {

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
				prefs.edit().putBoolean("relayPref", true).commit();
				Toast.makeText(this, getString(R.string.pcd_na_switch), Toast.LENGTH_LONG).show();
			}
			else {
				Toast.makeText(this, getString(R.string.pcd_na_unpredict), Toast.LENGTH_LONG).show();
			}
		}
		
        if (prefs.getBoolean("relayPref", false)) {
        	Intent forwardIntent = new Intent(intent);
        	forwardIntent.setClass(this, NFCRelayActivity.class);
        	startActivity(forwardIntent);
        	finish();
        }
        
        if (prefs.getBoolean("screenPref", true)) {
	        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, getString(R.string.app_name));
	        mWakeLock.acquire();
        }
        
        mDebugLogging = prefs.getBoolean("debugLogPref", false);
        mSalt = prefs.getString("saltPref", DEFAULT_SALT);        
        mServerPort = prefs.getInt("portPref", Integer.parseInt(getString(R.string.default_port)));
		mServerIP = prefs.getString("ipPref", getString(R.string.default_ip));
		mEncrypt = prefs.getBoolean("encryptPref", true);
               
		Tag extraTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);	//required
		Parcelable[] extraNdefMsg = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);	//optional
		byte[] extraId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);	//optional
		
        if (extraTag != null) {	
			updateStatus(getString(R.string.reader) + " " + extraTag.toString());
    		if (mDataView == null) {
    			//TODO: maybe convert table to ListView
				TableRow row = new TableRow(this);
				row.setLayoutParams(new TableRow.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				mDataView = new TextView(this);
				mDataView.setFreezesText(true);
				mDataView.setId(mSessions.size());
				row.addView(mDataView);
				mDataTable.addView(row);
    		}				
			
			if (mMode == REPLAY_PCD_MODE) {
				log("pcd mode");
				doReplayPCD(extraTag,  mReplaySession.getBundle("requests"), mReplaySession.getBundle("responses"));
			}
			else {
		    	boolean isPCD = false;
				String[] tech = extraTag.getTechList();	    	
		    	for (String s: tech) {
		    		//TODO: generify
		    		if (s.equals(NFCVars.ISO_PCDA_CLASS)) {
	    				isPCD = true;
	    				break;
			        }	            		            
		    		else if  (s.equals(NFCVars.ISO_PCDB_CLASS)) {
		    			Toast.makeText(this, getString(R.string.report_pcdb), Toast.LENGTH_LONG).show();
		    		}
	    		}    
		    	
		    	if (isPCD) {
	    			log("Found PCD");		    		
					if (mMode == REPLAY_TAG_MODE) {
						log("tag mode");					
						doReplayTag(extraTag, mReplaySession.getBundle("responses"), mReplaySession.getBundle("requests"));
					}
					else {
						log("proxy mode");						
			    		new ProxyTask().execute(extraTag);
					}	    		
		    	}
		    	else {
		    		log("no PCD tag");
		    	}
			}
        }		
        else {
        	log("no extratag");
        }
        log("onResume end");	        
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
	    
    private void addLineBreak(int id) {
		TableRow line = new TableRow(this);
		line.setLayoutParams(new TableRow.LayoutParams(LayoutParams.MATCH_PARENT, 1));
		line.setBackgroundColor(Color.GREEN);		
		TextView ltv = new TextView(this);				
		ltv.setHeight(1);
		line.addView(ltv);
		line.setTag(id);
		mDataTable.addView(line);
    }
    
	private void storeTransactionsAndBreak(Bundle requests, Bundle responses) {
		final Bundle session = new Bundle();
		session.putBundle("requests", requests);
		session.putBundle("responses", responses);
        mDataView.setOnLongClickListener(getTransactionsTextViewLongClickListener());
		mDataTable.post(new Runnable() {
			@Override
			public void run() {
				if (mDataView!= null && mDataView.getText().length() > 0) {
					//TODO: ...might be race condition here XXX
					mSessions.putBundle(String.valueOf(mSessions.size()), session);										
					addLineBreak(mSessions.size() - 1);
					mDataTab.fullScroll(ScrollView.FOCUS_DOWN);
					mDataView = null;
				}					
			}
		});			
	}
	
	private void updateStatus(CharSequence msg) {
		mStatusView.append(TextUtils.concat(msg, "\n"));
		mStatusTab.post(new Runnable() {
				@Override
				public void run() {					
					mStatusTab.fullScroll(ScrollView.FOCUS_DOWN);
				}    			
    		});
	}

	private void updateData(CharSequence msg) {
		mDataView.append(TextUtils.concat(msg, "\n"));
		mDataTab.post(new Runnable() {
				@Override
				public void run() {					
					mDataTab.fullScroll(ScrollView.FOCUS_DOWN);
				}    			
    		});
	}		
	
	private void enablePCDReplay() {
		mMode = REPLAY_PCD_MODE;
    	updateStatus(getString(R.string.waiting));
    	mOptionsMenu.getItem(0).setVisible(false);
    	mOptionsMenu.getItem(1).setVisible(true);
    	mOptionsMenu.getItem(2).setVisible(true);
		mTabHost.setCurrentTab(0); //Data tab
		Toast.makeText(this, getString(R.string.replay_pcd_on_next), Toast.LENGTH_LONG).show();		
	}
	
	private void enableTagReplay() {
    	mMode = REPLAY_TAG_MODE;
    	updateStatus("Waiting for PCD");
    	mOptionsMenu.getItem(0).setVisible(true);
    	mOptionsMenu.getItem(1).setVisible(false);
    	mOptionsMenu.getItem(2).setVisible(true);
		mTabHost.setCurrentTab(0); //Data tab    	
    	Toast.makeText(this, getString(R.string.replay_tag_on_next), Toast.LENGTH_LONG).show();	            			
	}
	
	//TODO: do in separate thread
	private void doReplayPCD(Tag tag, Bundle pcdRequests, Bundle tagTransactions) {
		Bundle responses = new Bundle();
		BasicTagTechnologyWrapper tagTech = null;
		try {
			//TODO:add support for more tag types
			Class[] supportedTags = new Class[] { IsoDep.class };			
			String[] tech = tag.getTechList();	    	
	    	for (String s: tech) {
	
	    		for(Class c: supportedTags) {
	    			if (s.equals(c.getName())) {
	    				try {
							tagTech = new BasicTagTechnologyWrapper(tag, c.getName());
							break;
						} catch (IllegalArgumentException e) {
							log(e);
						} catch (ClassNotFoundException e) {
							log(e);
						} catch (NoSuchMethodException e) {
							log(e);
						} catch (IllegalAccessException e) {
							log(e);
						} catch (InvocationTargetException e) {
							log(e);
						}    				
	    			}
	    		}    		
			}
	    	if (tagTech != null) {
	    		tagTech.connect();
	    		boolean connected = tagTech.isConnected(); 
	    		log("isConnected: " + connected);
	    		if (!connected) return;
	    		
	    		//first store ID
	    		responses.putByteArray(String.valueOf(0), tag.getId());
	    		String tagStr = getString(R.string.tag) + ": ";
	    		String pcdStr = getString(R.string.pcd) + ": ";
	    		SpannableString msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(tag.getId()));
		    	msg.setSpan(new UnderlineSpan(), 0, 4, 0);				    				    	
				updateData(msg);	    	
				boolean foundCC = false;
	    		for(int i=0; i < pcdRequests.size(); i++) {
	    			if (foundCC) {
	    				updateData(""); //print newline. this will probably cause formatting problems later
	    			}
	    			byte[] tmp = pcdRequests.getByteArray(String.valueOf(i));
					msg = new SpannableString(pcdStr + TextHelper.byteArrayToHexString(tmp));
			    	msg.setSpan(new UnderlineSpan(), 0, 4, 0);				    				    	
					updateData(msg);
	    			byte[] reply = tagTech.transceive(tmp);

	    			responses.putByteArray(String.valueOf(i+1), reply);
	    			if (mMask && reply != null && reply[0] == 0x70) {
	    				msg = new SpannableString(tagStr + getString(R.string.masked));
	    			}
	    			else {
	    				msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(reply));
	    			}
			    	msg.setSpan(new UnderlineSpan(), 0, 4, 0);				    				    	
					updateData(msg);	    				    		
	    			
					if (tagTransactions != null) {
						if (i + 1 < tagTransactions.size() ) {
							if (Arrays.equals(reply, tagTransactions.getByteArray(String.valueOf(i + 1)))) {
								log(getString(R.string.equal));
								updateStatus(getString(R.string.equal));
							}
							else {
								log(getString(R.string.not_equal));
log("org: " + TextHelper.byteArrayToHexString(tagTransactions.getByteArray(String.valueOf(i + 1))));
log("new : " + TextHelper.byteArrayToHexString(reply));
								updateStatus(getString(R.string.not_equal));
updateStatus("org: " + TextHelper.byteArrayToHexString(tagTransactions.getByteArray(String.valueOf(i + 1))));
updateStatus("new : " + TextHelper.byteArrayToHexString(reply));					
							}
						}
						else {
							log("index to responses out of bounds");
							updateStatus(getString(R.string.index_out_bounds));
						}
					}

	    			if (reply != null && reply[0] == 0x70) {
	    				updateData("\n" + TagHelper.parseCC(reply, pcdRequests.getByteArray(String.valueOf(i - 1)), mMask));
	    				foundCC = true;
	    				if (i == pcdRequests.size() - 1) {
		    				log(getString(R.string.finished_reading));
		    				updateStatus(getString(R.string.finished_reading));
	    				}
	    			}
					else if (reply != null && reply.length > 3 && reply[0] == 0x77 && reply[2] == (byte)0x9f) {
						updateData("\n" + TagHelper.parseCryptogram(reply, tmp)); //previous pcdRequest
	    				log(getString(R.string.finished_reading));
	    				updateStatus(getString(R.string.finished_reading));
					}

	    		}
	    		
	    	}
	    	else {
	    		log(getString(R.string.unsupported_tag));
	    		updateStatus(getString(R.string.unsupported_tag));
	    	}
		} catch (IllegalStateException e) {
			log(e);
			updateStatus(e.toString());
		} catch (IOException e) {
			log(e);
			updateStatus(e.toString());
		}
		finally {			
			storeTransactionsAndBreak(pcdRequests, responses);
			if (tagTech != null) {
				try {
					tagTech.close();
				} catch (IOException e) {
					log(e);
				}
			}
		}

		//log(getString(R.string.lost_connection));
		//updateStatus(getString(R.string.lost_connection));
	}	

	//TODO: do in separate thread
	private void doReplayTag(Tag tag, Bundle tagTransactions, Bundle pcdRequests) {
		Bundle requests = new Bundle();

		try {	    			
			//TODO:PCD hack. Add support for PCD B
			Class cls = Class.forName(NFCVars.ISO_PCDA_CLASS);    				
			Method meth = cls.getMethod("get", new Class[]{Tag.class});
			Object ipcd = meth.invoke(null, tag);
			meth = cls.getMethod("connect", null);
			meth.invoke(ipcd, null);
			meth = cls.getMethod("isConnected", null);
			boolean connected = (Boolean) meth.invoke(ipcd, null);
			log("isConnected: " + connected);
			if (!connected) {
				log("Not connected to PCD");
				//updateStatus("Not connected to PCD");
				//return;				
			}
			else {
				meth = cls.getMethod("transceive", new Class[]{byte[].class});
	    		String tagStr = getString(R.string.tag) + ": ";
	    		String pcdStr = getString(R.string.pcd) + ": ";					

				for (int i=0; i < tagTransactions.size(); i++) {
					byte []tmp = tagTransactions.getByteArray(String.valueOf(i));
					SpannableString msg;
	    			if (mMask && tmp != null && tmp[0] == 0x70) {
	    				msg = new SpannableString(tagStr + getString(R.string.masked));
	    			}
	    			else {
	    				msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(tmp));
	    			}
					
			    	msg.setSpan(new UnderlineSpan(), 0, 4, 0);				    				    	
					updateData(msg);
					byte[] reply = (byte[]) meth.invoke(ipcd, tmp);
					
					requests.putByteArray(String.valueOf(i), reply);
					msg = new SpannableString(pcdStr + TextHelper.byteArrayToHexString(reply));
			    	msg.setSpan(new UnderlineSpan(), 0, 4, 0);				    				    	
					updateData(msg);
					
					if (pcdRequests != null) {
						if (i < pcdRequests.size() ) {
							if (Arrays.equals(reply, pcdRequests.getByteArray(String.valueOf(i)))) {
								log(getString(R.string.equal));
								updateStatus(getString(R.string.equal));
							}
							else {
								log(getString(R.string.not_equal));
log("org: " + TextHelper.byteArrayToHexString(pcdRequests.getByteArray(String.valueOf(i))));
log("new : " + TextHelper.byteArrayToHexString(reply));								
								updateStatus(getString(R.string.equal));
updateStatus("org: " + TextHelper.byteArrayToHexString(pcdRequests.getByteArray(String.valueOf(i))));
updateStatus("new : " + TextHelper.byteArrayToHexString(reply));					
	
								//TODO: 
								//attempt to find a matching response if it exists. This probably doesn't work.
								//this will also break replay mode unless new sequences are added to tagTransactions
								for (int k = 0; k < pcdRequests.size(); k++ ) {
									if (k == i) continue;
									if (Arrays.equals(reply, pcdRequests.getByteArray(String.valueOf(k)))) {
										i = k;
										i = k - 1;
										log("found matching response. replay of this run is probably broken.");										
										break;
									}
								}
							}
						}
						else {
							log("index to requests out of bounds");
							updateStatus("index to requests out of bounds");
						}
					}
				}				
			} 
		}
		catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException && cause.getMessage() != null && cause.getMessage().equals("Transceive failed")) {
				log("transaction complete");
				updateStatus("transaction complete");
				return;
			}
			else {
				log(e);
			}
		}
		catch (Exception e) { //TODO:
			log(e);
			updateStatus(e.toString());	
		}
		finally { 			
			storeTransactionsAndBreak(requests, tagTransactions);
		}
		log("Lost connection to PCD?");
		updateStatus("Lost connection to PCD?");
	}	

	private class ProxyTask extends AsyncTask<Tag, Void, Void> {

		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
		}
		
		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onCancelled(java.lang.Object)
		 */
		@Override
		protected void onCancelled(Void result) {
			// TODO Auto-generated method stub
			super.onCancelled(result);
		}

		@Override
		protected Void doInBackground(Tag... params) {
			log("doInBackground start");				
			Socket clientSocket = null;
			BufferedOutputStream clientOS = null;
			BufferedInputStream clientIS = null;						
			long startTime = System.currentTimeMillis();
			
            try {   
            	
				log(getString(R.string.connecting_to_relay));
				updateStatusUI(getString(R.string.connecting_to_relay));				
				
		        mSockAddr = new InetSocketAddress(mServerIP, mServerPort);
        		clientSocket = new Socket();
				clientSocket.connect(mSockAddr, CONNECT_TIMEOUT);
				clientOS = new BufferedOutputStream (clientSocket.getOutputStream());
				clientIS = new BufferedInputStream(clientSocket.getInputStream());
				log(getString(R.string.connected_to_relay));
				updateStatusUI(getString(R.string.connected_to_relay));
			
				Bundle requests = new Bundle();
				Bundle responses = new Bundle();
				try {    			
					log("sending ready");
					IOUtils.sendSocket((NFCVars.READY + "\n").getBytes("UTF-8"), clientOS, null, false);
					String line = IOUtils.readLine(clientIS);
					log("command: " + line);        
					if (line.equals(NFCVars.NOT_READY)) {
			    		updateStatusUI(getString(R.string.nfcrelay_not_ready));
			    		log(getString(R.string.nfcrelay_not_ready));    		
			    		return null;    						
					} else if (!line.equals(NFCVars.OPTIONS)) {
			    		updateStatusUI(getString(R.string.unknown_command));
			    		log(getString(R.string.unknown_command));    		
						return null;
					}
					if (mEncrypt) {					
						
						IOUtils.sendSocket((NFCVars.ENCRYPT + "\n").getBytes("UTF-8"), clientOS, null, false);
						IOUtils.sendSocket(Base64.decode(mSalt, Base64.DEFAULT), clientOS, null, false);
						
						if (mSecret == null) {
							mSecret = generateSecretKey();
						}
		    	    	byte[] verify = IOUtils.readSocket(clientIS, mSecret, mEncrypt);
		    	    	if (verify == null) {
				    		updateStatusUI(getString(R.string.unexpected_response_encrypting));
				    		log(getString(R.string.unexpected_response_encrypting));
				    		log(TextHelper.byteArrayToHexString(verify));
		    				return null;		    	    		
		    	    	}
		    	    	else if (!new String(verify, "UTF-8").equals(NFCVars.VERIFY)) {
				    		updateStatusUI(getString(R.string.bad_password));
				    		log(getString(R.string.bad_password));
				    		log(TextHelper.byteArrayToHexString(verify));
				    		IOUtils.sendSocket(NFCVars.BAD_PASSWORD.getBytes("UTF-8"), clientOS, mSecret, mEncrypt);
				    		return null;
    	    	    	}	    	    	    	
		    	    	IOUtils.sendSocket(NFCVars.OK.getBytes("UTF-8"), clientOS, mSecret, mEncrypt);
						
					}
					else {
						IOUtils.sendSocket((NFCVars.CLEAR + "\n").getBytes("UTF-8"), clientOS, null, false);
					}
	    	    	
					log("getting id");				
					byte[] id = IOUtils.readSocket(clientIS, mSecret, mEncrypt);
					if (id == null) {
			    		updateStatusUI(getString(R.string.error_getting_id));
			    		log(getString(R.string.error_getting_id));    		
				    		return null;
				    	}    		
log("response: " + TextHelper.byteArrayToHexString(id));
log(new String(id));

					byte[] pcdRequest = null;
					byte[] cardResponse = null;
		    		String tagStr = getString(R.string.tag) + ": ";
		    		String pcdStr = getString(R.string.pcd) + ": ";					
			    	try {
			    		//TODO:PCD hack. Add support for PCD B
	    				Class cls = Class.forName(NFCVars.ISO_PCDA_CLASS);
		            	/*
		            	methods
	    		        05-14 16:49:03.229: D/NFCProxy(3642): close
	    		        05-14 16:49:03.229: D/NFCProxy(3642): connect
	    		        05-14 16:49:03.229: D/NFCProxy(3642): equals
	    		        05-14 16:49:03.229: D/NFCProxy(3642): get
	    		        05-14 16:49:03.229: D/NFCProxy(3642): getClass
	    		        05-14 16:49:03.229: D/NFCProxy(3642): getMaxTransceiveLength
	    		        05-14 16:49:03.229: D/NFCProxy(3642): getTag
	    		        05-14 16:49:03.229: D/NFCProxy(3642): hashCode
	    		        05-14 16:49:03.229: D/NFCProxy(3642): isConnected
	    		        05-14 16:49:03.229: D/NFCProxy(3642): notify
	    		        05-14 16:49:03.232: D/NFCProxy(3642): notifyAll
	    		        05-14 16:49:03.232: D/NFCProxy(3642): reconnect
	    		        05-14 16:49:03.232: D/NFCProxy(3642): toString
	    		        05-14 16:49:03.232: D/NFCProxy(3642): transceive
	    		        05-14 16:49:03.232: D/NFCProxy(3642): wait
	    		        05-14 16:49:03.232: D/NFCProxy(3642): wait
	    		        05-14 16:49:03.232: D/NFCProxy(3642): wait
	    		        
	    		        https://github.com/CyanogenMod/android_frameworks_base/blob/ics/core/java/android/nfc/tech/IsoPcdA.java
	    		        */	    				
		            	Method meth = cls.getMethod("get", new Class[]{Tag.class});
		            	Object ipcd = meth.invoke(null, params[0]);
		            	meth = cls.getMethod("connect", null);
		            	meth.invoke(ipcd, null);
		            	meth = cls.getMethod("isConnected", null);
		            	boolean connected = (Boolean) meth.invoke(ipcd, null);
	            		log("isConnected: " + connected);
						if (!connected) {
							log(getString(R.string.not_connected_to_pcd));
							updateStatusUI(getString(R.string.not_connected_to_pcd));
							return null;
						}
		            	
		            	meth = cls.getMethod("transceive", new Class[]{byte[].class});	//TODO: check against getMaxTransceiveLength()

	            		pcdRequest = (byte[])meth.invoke(ipcd, id);
	            		
						SpannableString msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(id));
				    	msg.setSpan(new UnderlineSpan(), 0, 4, 0);				    	
				    	responses.putByteArray(String.valueOf(responses.size()), id);
						updateDataUI(msg);
log("sent id to pcd: " + TextHelper.byteArrayToHexString(id));
												
						msg = new SpannableString(pcdStr + TextHelper.byteArrayToHexString(pcdRequest));
					    msg.setSpan(new UnderlineSpan(), 0, 4, 0);					    
					    requests.putByteArray(String.valueOf(requests.size()), pcdRequest);
						updateDataUI(msg);												
log("response from PCD: " + TextHelper.byteArrayToHexString(pcdRequest));
log(new String(pcdRequest));
						do {

							IOUtils.sendSocket(pcdRequest, clientOS, mSecret, mEncrypt);
log("sent response to relay/card");				

							cardResponse = IOUtils.readSocket(clientIS, mSecret, mEncrypt);
							if (cardResponse != null) {

								if (new String(cardResponse, "UTF-8").equals("Relay lost tag") ) {
									updateStatusUI(getString(R.string.relay_lost_tag));
									log(getString(R.string.relay_lost_tag));	
										break;										
									}
								}
								else {
									updateStatusUI(getString(R.string.bad_crypto));
									log(getString(R.string.bad_crypto));	
									break;
								}
								
log("relay/card response: " + TextHelper.byteArrayToHexString(cardResponse));						
log(new String(cardResponse));
								
								
							log("sending card response to PCD");
			    			if (mMask && cardResponse[0] == 0x70) {
			    				msg = new SpannableString(tagStr + getString(R.string.masked));
			    			}
			    			else {
			    				msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(cardResponse));
			    			}
							
						    msg.setSpan(new UnderlineSpan(), 0, 4, 0);					    
						    responses.putByteArray(String.valueOf(responses.size()), cardResponse);
						    updateDataUI(msg);										    						
							if (cardResponse[0] == 0x70 || cardResponse[0] == 0x77) {
								try {
									pcdRequest = (byte[])meth.invoke(ipcd, cardResponse);
								} catch (InvocationTargetException e) {
									if (e.getCause() instanceof IOException && e.getCause().getMessage().equals("Transceive failed")) {										
										//update UI only after sending cardResponse to PCD
										if (cardResponse[0] == 0x70) {
											updateDataUI("\n" + TagHelper.parseCC(cardResponse, requests.getByteArray(String.valueOf(requests.size() - 2)), mMask));
										}
										else if (cardResponse.length > 3 && cardResponse[0] == 0x77 && cardResponse[2] == (byte)0x9f) {
											updateDataUI("\n" + TagHelper.parseCryptogram(cardResponse, pcdRequest)); //previous pcdRequest
										}
										updateDataUI(getString(R.string.time) + ": " + (System.currentTimeMillis() - startTime));
										log(getString(R.string.transaction_complete));
										updateStatusUI(getString(R.string.transaction_complete));
										break;												
									}
									throw e;
								}
								if (cardResponse[0] == 0x70) {
									updateDataUI("\n" + TagHelper.parseCC(cardResponse, requests.getByteArray(String.valueOf(requests.size() - 2)), mMask) + "\n");
								}
							}		    						
							else {
								pcdRequest = (byte[])meth.invoke(ipcd, cardResponse);
							}					
							requests.putByteArray(String.valueOf(requests.size()), pcdRequest);
    						msg = new SpannableString(pcdStr + TextHelper.byteArrayToHexString(pcdRequest));
						    msg.setSpan(new UnderlineSpan(), 0, 4, 0);
							updateDataUI(msg);									

log("response from PCD: " + TextHelper.byteArrayToHexString(pcdRequest));									
							
						} while (pcdRequest != null);
	            	}
            		catch(ClassNotFoundException e) {
	                	log(e);    
	                	updateStatusUI("ClassNotFoundException");
            		}
            		catch(NoSuchMethodException e) {
            			log(e);
            			updateStatusUI("NoSuchMethodException");
            		}
            		catch (InvocationTargetException e) {
            			
						if (e instanceof InvocationTargetException) {									
							if (((InvocationTargetException) e).getCause() instanceof TagLostException) {
								log(getString(R.string.lost_pcd));
								updateStatusUI(getString(R.string.lost_pcd));
							}
						}
						else {
							log(e);
							updateStatusUI("InvocationTargetException");
						}	            			            			
            		}
            		catch(IllegalAccessException e) {
            			log(e);
            			updateStatusUI("IllegalAccessException");
            		} catch (IOException e) {
            			log(e);
            			updateStatusUI(getString(R.string.ioexception_error_writing_socket));
					}		    			
	            	finally {
	            		try {
	            			//TODO:PCD hack. Add support for PCD B
	            			Class cls = Class.forName(NFCVars.ISO_PCDA_CLASS);
	            			Method meth = cls.getMethod("get", new Class[]{Tag.class});
	            			Object ipcd = meth.invoke(null, params[0]);
			            	meth = cls.getMethod("close", null);
			            	meth.invoke(ipcd, null);
	            		}
	            		catch(ClassNotFoundException e) {
		                	log(e);            	
		                	updateStatusUI("ClassNotFoundException");
	            		}
	            		catch(NoSuchMethodException e) {
	            			log(e);
	            			updateStatusUI("NoSuchMethodException");
	            		}
	            		catch (InvocationTargetException e) {
	            			log(e);
	            			updateStatusUI("InvocationTargetException");
	            		}
	            		catch(IllegalAccessException e) {
	            			log(e);
	            			updateStatusUI("IllegalAccessException");
	            		}
	            	}    			    	
				}
				catch (UnsupportedEncodingException e) {
					log(e);
					updateStatusUI("UnsupportedEncodingException");
				}

				if (mDataView == null) { 
					log("mDataView null"); //??? happens on quick reads? activity is recreated with
				}	
				else {
					//Finish
					storeTransactionsAndBreak(requests, responses);
				}					
            }
            catch (SocketTimeoutException e) {
            	log(e);
            	updateStatusUI(getString(R.string.connection_to_relay_timed_out));
            }
            catch (ConnectException e) {
            	log(getString(R.string.connection_to_relay_failed));
            	updateStatusUI(getString(R.string.connection_to_relay_failed));            	
            } 
            catch (SocketException e) {
            	log(e);
            	updateStatusUI(getString(R.string.socket_error) + " " + e.getLocalizedMessage());            	
            }
            catch (UnknownHostException e) {
            	updateStatusUI(getString(R.string.unknown_host));
            }
            catch (IOException e) {
            	log(e);
            	updateStatusUI("IOException: " + e.getLocalizedMessage());            	
			}
            catch (final Exception e) 
            {
            	StringWriter sw = new StringWriter();
            	e.printStackTrace(new PrintWriter(sw));
                log(getString(R.string.something_happened) + ": " + e.toString() + " " + sw.toString());            	
                updateStatusUI(getString(R.string.something_happened) + ": " + e.toString() + " " + sw.toString());
            }
            finally {            	            	
        		try 
        		{
        			log ("Closing connection to NFCRelay...");
        			if (clientSocket != null)
        				clientSocket.close();
        		}
                catch (IOException e) 
                {
                	log("error closing socket: " + e);             
                }
            	log("doInBackground end");                
            }            
			return null;
		}
		
		private void updateStatusUI(final CharSequence msg) {
			mStatusView.post(new Runnable() {
				@Override
				public void run() {					
					mStatusView.append(TextUtils.concat( msg, "\n"));
					mStatusTab.post(new Runnable() {
						@Override
						public void run() {					
							mStatusTab.fullScroll(ScrollView.FOCUS_DOWN);
						}    			
		    		});
				}    			
    		});						
		}
		
		private void updateDataUI(final CharSequence msg) {				
			mDataView.post(new Runnable() {
				@Override
				public void run() {
					mDataView.append(TextUtils.concat(msg, "\n"));
					mDataTab.fullScroll(ScrollView.FOCUS_DOWN);
				}    			
    		});
		}		
	}
	
	private SecretKey generateSecretKey() throws IOException {
		try {
    		SharedPreferences prefs = getSharedPreferences(NFCVars.PREFERENCES, MODE_PRIVATE);
    		SecretKeyFactory f;
			f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			byte[] salt = Base64.decode(mSalt, Base64.DEFAULT);
	        KeySpec ks = new PBEKeySpec(prefs.getString("passwordPref", getString(R.string.default_password)).toCharArray(), salt, 2000, 256);
	        SecretKey tmp = f.generateSecret(ks);
	        return new SecretKeySpec(tmp.getEncoded(), "AES");
		} catch (Exception e) {
			log(e);
			throw new IOException(e);
		}        			
	}
	
	protected void saveRun(String name, int type) {
		Bundle transactions = null;
		if (type == DBHelper.REPLAY_PCD) {
			transactions = mReplaySession.getBundle("requests");
		}
		else { //if (type == DBHelper.REPLAY_TAG) {
			transactions = mReplaySession.getBundle("responses");
		}		
		byte[][] data = new byte[transactions.size()][];
		for (int i = 0; i < transactions.size(); i ++) {
			data[i] = transactions.getByteArray(String.valueOf(i));
		}
		long inserted = mDBHelper.saveTransactions(name, data, type );
		
		if (inserted != -1) {
			//TODO: should be done in new thread
			((CursorAdapter)mSavedList.getAdapter()).swapCursor(mDBHelper.getReplays());
			Toast.makeText(this, "Saved", Toast.LENGTH_LONG).show();
		}
		else {
			Toast.makeText(this, "Not saved. Duplicate name.", Toast.LENGTH_LONG).show();
		}
	}
	
	private void deleteSaved() {
		String name = ((Bundle)mSelectedSaveView.getTag()).getString("name");
		int num = mDBHelper.deleteReplay(name);
		if (num != 1) {
			Toast.makeText(this, "Error deleting replay", Toast.LENGTH_SHORT).show();
		}
		//TODO: should be done in new thread
		((CursorAdapter)mSavedList.getAdapter()).swapCursor(mDBHelper.getReplays());
	}	
	
	protected void exportRun(String filename) {
		try {					 
		    String state = Environment.getExternalStorageState();
		    if (Environment.MEDIA_MOUNTED.equals(state)) {
		        File dir = Environment.getExternalStorageDirectory();		       
		        String dirPath = dir.getAbsolutePath();
		        File exportPath = new File(dirPath + File.separator + NFCVars.STORAGE_PATH);
		        if (!exportPath.exists()) {
		        	if (!exportPath.mkdir()) {
		        		Toast.makeText(this, "Error creating storage directory", Toast.LENGTH_LONG).show();		        		
		        		return;
		        	}
		        }
		        //let user store where ever they want
		        File exportFile = new File(exportPath + File.separator + filename);
		        //TODO: make sure filename is valid filename. also warn if file exists.
				FileWriter writer = new FileWriter(exportFile);
				
				Bundle session = mSessions.getBundle(String.valueOf(mSelectedId));
				Bundle requests = session.getBundle("requests");
				Bundle responses = session.getBundle("responses");

				StringBuilder sbRequests = new StringBuilder("byte[][] pcdRequests = new byte[][] {");
				StringBuilder sbResponses = new StringBuilder("byte[][] tagResponses = new byte[][] {");
				for(int i = 0; i < requests.size(); i ++) {
					byte req[] = requests.getByteArray(String.valueOf(i));
					sbRequests.append("{").append(TextHelper.byteArrayToHexString(req, "0x", ", ", true)).append("}");
					if (i +1 != requests.size()) {
						sbRequests.append(", ");
					}
				}
				sbRequests.append("};\n");
				for(int i = 0; i < responses.size(); i ++) {
					byte resp[] = responses.getByteArray(String.valueOf(i));
					sbResponses.append("{").append(TextHelper.byteArrayToHexString(resp, "0x", ", ", true)).append("}");
					if (i +1 != responses.size()) {
						sbResponses.append(", ");
					}
				}
				sbResponses.append("};\n");
				writer.write(sbRequests.toString());
				writer.write(sbResponses.toString());
				writer.close();						
            	Toast.makeText(this, "Saved to:\n" + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();						
				return;
		        
		    } else {
		        Toast.makeText(this, "Error writing to external storage", Toast.LENGTH_LONG).show();
		    }						
		} catch (IOException e) {
			log(e);
		}			
	}

	private void deleteRun() {	
			View v = mDataTable.findViewById(mSelectedId);
			TableRow row = (TableRow)v.getParent();
			row.setVisibility(View.GONE);		
			TableRow line = (TableRow)mDataTable.findViewWithTag(mSelectedId);
			line.setVisibility(View.GONE);
			Toast.makeText(this, "Deleted", Toast.LENGTH_LONG).show();
			//don't re-adjust IDs
	}
	
	private View.OnLongClickListener getTransactionsTextViewLongClickListener() {
		return new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View view) {
		        if (mActionMode != null) {
		            return false;
		        }
		        view.setSelected(true);		        
		        mSelectedId = view.getId();
log("selectedID: " + mSelectedId);		        
		        mActionMode = NFCProxyActivity.this.startActionMode(mTransactionsActionModeCallback);
		        return true;
			}
		};
	}

	private View.OnLongClickListener getSavedTextViewLongClickListener() {
		return new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View view) {
		        if (mActionMode != null) {
		            return false;
		        }
		        
		        view.setSelected(true);
		        mSelectedSaveView = view; 
		        mActionMode = NFCProxyActivity.this.startActionMode(mSavedActionModeCallback);
		        return true;
			}
		};
	}
	
	private void cutSessionAt(int id) {
	
		int size = mSessions.size();
		for(int i = id; i < size; i++) {
			if (i == id) {
				mSessions.remove(String.valueOf(id));
			}
			else {
				Bundle b = mSessions.getBundle(String.valueOf(i));
				mSessions.remove(String.valueOf(i));
				mSessions.putBundle(String.valueOf(i - 1), b);	//i will always be > 0
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);		

		ArrayList<CharSequence> rows = new ArrayList<CharSequence>(); 
		for (int i = 0; i < mDataTable.getChildCount(); i++) {
			TableRow tr = (TableRow)mDataTable.getChildAt(i);
			if (tr.getVisibility() == View.GONE) {
				cutSessionAt(i);
				continue;
			}
			TextView tv = (TextView)(tr).getChildAt(0);
			if (tv.getText().length() > 0 ) {
				rows.add(tv.getText());
			}
						
		}
		outState.putCharSequenceArray("rows", rows.toArray(new CharSequence[rows.size()]));	//TODO: this is not encrypted
		outState.putInt("tab", mTabHost.getCurrentTab());
		outState.putBundle("sessions", mSessions);	//TODO: this is not encrypted
		outState.putBundle("replaySession", mReplaySession);	//TODO: this is not encrypted
		outState.putInt("mode", mMode);
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		CharSequence[] rows = savedInstanceState.getCharSequenceArray("rows");
		if (rows != null) {
			for(int i = 0 ; i < rows.length; i++) {			
				TableRow row = new TableRow(this);
				row.setLayoutParams(new TableRow.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				TextView tv = new TextView(this);				
				tv.setFreezesText(true);
				tv.setText(rows[i]);								
				tv.setOnLongClickListener(getTransactionsTextViewLongClickListener());
				tv.setId(i);
				row.addView(tv);
				mDataTable.addView(row);

				addLineBreak(i);
			}
		}
		mTabHost.setCurrentTab(savedInstanceState.getInt("tab"));
        mSessions = savedInstanceState.getBundle("sessions");
        mReplaySession = savedInstanceState.getBundle("replaySession");
        mMode = savedInstanceState.getInt("mode");
        if (mMode == PROXY_MODE) {
        	mOptionsMenu.getItem(0).setVisible(false);
	        mOptionsMenu.getItem(1).setVisible(false);
	        mOptionsMenu.getItem(2).setVisible(false);        
        }
        else if (mMode == REPLAY_PCD_MODE) {
        	mOptionsMenu.getItem(0).setVisible(false);
	        mOptionsMenu.getItem(1).setVisible(true);
	        mOptionsMenu.getItem(2).setVisible(true);        	
        }
        else if (mMode == REPLAY_TAG_MODE) {
        	mOptionsMenu.getItem(0).setVisible(true);
	        mOptionsMenu.getItem(1).setVisible(false);
	        mOptionsMenu.getItem(2).setVisible(true);        	
        	
        }
	}
	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mDBHelper != null) {
			mDBHelper.close();
		}
	}

    private void log(Object msg) {    	
    	if (mDebugLogging) {
    		LogHelper.log(this, msg);
    	}
    }	
}
