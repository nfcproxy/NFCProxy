package org.eleetas.nfc.nfcproxy;

import android.app.Activity;
import android.os.Bundle;

public class SettingsActivity extends Activity {

	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getFragmentManager().beginTransaction().replace(android.R.id.content, new NFCPrefs()).commit();
	}
	
}

