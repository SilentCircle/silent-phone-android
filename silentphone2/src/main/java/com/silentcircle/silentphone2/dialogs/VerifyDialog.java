/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.silentphone2.dialogs;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.InCallActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ManageCallStates;

/**
 * Display dialog to verify ZRTP SAS and set ZRTP peername.
 * <p/>
 * Created by werner on 02.04.14.
 */
public class VerifyDialog extends DialogFragment {
    private static String SAS_TEXT = "sas_text";
    private static String CALL_ID = "call_id";

    private InCallActivity mParent;

    public static VerifyDialog newInstance(String sasText, int callId) {
        VerifyDialog f = new VerifyDialog();

        Bundle args = new Bundle();
        args.putString(SAS_TEXT, sasText);
        args.putInt(CALL_ID, callId);
        f.setArguments(args);
        return f;
    }

    public VerifyDialog() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (InCallActivity) activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
        // Get the layout inflater
        LayoutInflater inflater = mParent.getLayoutInflater();

        // Inflate and set the layout for the dialog
        // set the SAS string to compare
        // Pass null as the parent view because its going in the dialog layout
        View view = inflater.inflate(R.layout.incall_sas_verify, null, false);
        if (view == null)
            return null;

        Bundle args = getArguments();
        if (args == null)
            return null;

        final CallState call = TiviPhoneService.calls.findCallById(args.getInt(CALL_ID));
        if (call == null)
            return null;

        TextView sas = (TextView) view.findViewById(R.id.VerifyInfoTextSas);
        sas.setText("\"" + args.getString(SAS_TEXT) + "\"");

        // preset peer name edit field if we have some info available
        final EditText peerName = (EditText) view.findViewById(R.id.VerifyPeerName);
        if (call.zrtpPEER.getLen() > 0) {
            peerName.setText(call.zrtpPEER.toString());
        }
        else {
            peerName.setText(call.getNameFromAB());
        }

        // Add inflated view and action buttons
        builder.setView(view)
                .setPositiveButton(R.string.confirm_dialog, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Editable pn = peerName.getText();
                        mParent.storePeerAndVerify(pn == null ? "" : pn.toString(), call); // Set peer name via phone service
                    }
                })
                .setNegativeButton(R.string.provision_later, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mParent.dialogClosed();
                    }
                });
        return builder.create();
    }
}

