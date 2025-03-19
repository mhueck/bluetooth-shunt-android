package net.mhu.home.blat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

public class ConnectFailedFragment extends DialogFragment {

    public interface NoticeDialogListener {
        public void onDialogPositiveClick(DialogFragment dialog);
        public void onDialogNegativeClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events.
    NoticeDialogListener listener;
    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener.
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface.
        try {
            // Instantiate the NoticeDialogListener so you can send events to
            // the host.
            listener = (NoticeDialogListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface. Throw exception.
            throw new ClassCastException(context.toString()
                    + " must implement NoticeDialogListener");
        }
    }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction.
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.dialog_connect_timeout)
               .setPositiveButton(R.string.connect_retry, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                    listener.onDialogPositiveClick(ConnectFailedFragment.this);
                }
               })
               .setNegativeButton(R.string.application_quit, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                    listener.onDialogNegativeClick(ConnectFailedFragment.this);
                }
               });
        // Create the AlertDialog object and return it.
        return builder.create();
    }
}