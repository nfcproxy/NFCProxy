package org.eleetas.nfc.nfcproxy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

public class ExportDialogFragment extends DialogFragment {
	static ExportDialogFragment newInstance() {
        return new ExportDialogFragment();
    }
	
	/* (non-Javadoc)
	 * @see android.app.DialogFragment#onCreateDialog(android.os.Bundle)
	 */
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		final EditText input = new EditText(getActivity());
		input.setInputType(InputType.TYPE_CLASS_TEXT);

		return new AlertDialog.Builder(getActivity())
     	.setTitle(R.string.export_title)
     	.setMessage(R.string.warning_plaintext)
     	.setView(input)
     	.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener () {

 			public void onClick(DialogInterface dialog, int which) {
 		    	String filename = input.getText().toString();
				((NFCProxyActivity)getActivity()).exportRun(filename);
				}	 			
			})
         .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener () {
             public void onClick(DialogInterface dialog, int which) {
             }
			}).create();
	}
}
