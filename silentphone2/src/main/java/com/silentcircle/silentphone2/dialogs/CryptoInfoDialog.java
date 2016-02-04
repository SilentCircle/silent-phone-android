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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.InCallActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.CompatibilityHelper;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Creates the detailed security and crypto info panel.
 * <p/>
 * Created by werner on 01.04.14.
 */
// ---LICENSE_BEGIN---
/*
 * Copyright © 2014, Silent Circle
 * All rights reserved.
 */
// ---LICENSE_END---

public class CryptoInfoDialog extends DialogFragment implements View.OnClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = CryptoInfoDialog.class.getSimpleName();

    /* *********************************************************************
     * Handling of the crypto panel display. Triggered by a click on the SAS
     * text button.
     * ******************************************************************* */
    private static final String sdp_hash = "media.zrtp.sdp_hash";
    private static final String lbClient = "media.zrtp.lbClient";
    private static final String lbVersion = "media.zrtp.lbVersion";
    private static final String lbCipher = "media.zrtp.lbChiper";
    private static final String lbAuthTag = "media.zrtp.lbAuthTag";
    private static final String lbHash = "media.zrtp.lbHash";
    private static final String lbKeyExchange = "media.zrtp.lbKeyExchange";
    private static final String socket = ".sock";      //socket info, tls ciphers or udp,tcp
    private static final String lbBuildInfo = "media.zrtp.buildInfo";
    private static final String secureSince = "media.zrtp.sec_since";

    private InCallActivity mParent;

    public static CryptoInfoDialog newInstance() {
        return new CryptoInfoDialog();
    }

    public CryptoInfoDialog() {
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

        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return null;

        String buildInfo = TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbBuildInfo);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "ZRTP build information: " + buildInfo);

        // Inflate and set the layout for the dialog
        // set the SAS string to compare
        // Pass null as the parent view because its going in the dialog layout
        View view = inflater.inflate(R.layout.dialog_crypto_info, null, false);
        if (view == null)
            return null;

        TextView txtView = (TextView) view.findViewById(R.id.CryptoCryptoComponentsCipherInfo);
        txtView.setText(TiviPhoneService.getInfo(-1, call.iCallId, lbCipher));

        txtView = (TextView) view.findViewById(R.id.CryptoCryptoComponentsHashInfo);
        txtView.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbHash));

        txtView = (TextView) view.findViewById(R.id.CryptoCryptoComponentsPkInfo);
        txtView.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbKeyExchange));

        txtView = (TextView) view.findViewById(R.id.CryptoCryptoComponentsSauthInfo);
        txtView.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbAuthTag));

        txtView = (TextView) view.findViewById(R.id.CryptoCryptoComponentsZhashInfo);
        txtView.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, sdp_hash));

        txtView = (TextView) view.findViewById(R.id.CryptoPeerClientIdInfo);
        txtView.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbClient));

        txtView = (TextView) view.findViewById(R.id.CryptoPeerClientProtoInfo);
        txtView.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbVersion));

        txtView = (TextView) view.findViewById(R.id.CryptoCryptoComponentsTlsInfo);
        String tls = TiviPhoneService.getInfo(call.iEngID, -1, socket);
        txtView.setText(tls);
        txtView.setSelected(true);

        txtView = (TextView) view.findViewById(R.id.CryptoPeerSecureSinceInfo);
        try {
            long since = Long.valueOf(TiviPhoneService.getInfo(call.iEngID, call.iCallId, secureSince));
            if (since <= 0) {
                txtView.setVisibility(View.GONE);
                view.findViewById(R.id.CryptoPeerSecureSince).setVisibility(View.GONE);
            }
            else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                txtView.setText(sdf.format(new Date(since * 1000)));
            }
        } catch (NumberFormatException ignore) {
            txtView.setVisibility(View.GONE);
            view.findViewById(R.id.CryptoPeerSecureSince).setVisibility(View.GONE);
        }

        ImageView imgView = (ImageView) view.findViewById(R.id.CryptoSharedSecretsRs1Info);
        CompatibilityHelper.setBackground(imgView, getSharedSecretStatus(mParent, "rs1"));

        imgView = (ImageView) view.findViewById(R.id.CryptoSharedSecretsRs2Info);
        CompatibilityHelper.setBackground(imgView, getSharedSecretStatus(mParent, "rs2"));

        imgView = (ImageView) view.findViewById(R.id.CryptoSharedSecretsAuxInfo);
        CompatibilityHelper.setBackground(imgView, getSharedSecretStatus(mParent, "aux"));

        imgView = (ImageView) view.findViewById(R.id.CryptoSharedSecretsPbxInfo);
        CompatibilityHelper.setBackground(imgView, getSharedSecretStatus(mParent, "pbx"));

        txtView = (TextView) view.findViewById(R.id.CryptoPeerName);
        txtView.setText(call.zrtpPEER.toString());

        txtView = (TextView) view.findViewById(R.id.CryptoSasText);
        txtView.setText(call.bufSAS.toString());

        // if show verify SAS then SAS was not verified
        imgView = (ImageView) view.findViewById(R.id.CryptoPanelPadlock);
        if (call.iShowVerifySas)
            imgView.setImageResource(R.drawable.main_lock_locked);
        else
            imgView.setImageResource(R.drawable.main_lock_verified);
        imgView.setOnClickListener(this);

        // Add inflated view and action buttons
        builder.setView(view)
                .setPositiveButton(R.string.close_dialog, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mParent.dialogClosed();
                    }
                });
        return builder.create();
    }

    @Override
    public void onClick(View v) {
        mParent.resetVerify((ImageView)v);
    }

    /**
     * Get status of shared secrets and assign color according to state.
     *
     * @param secret which shared secret to check
     * @return the color to the status
     */
    private static Drawable getSharedSecretStatus(Context ctx, String secret) {

        String cmd = "media.zrtp." + secret;

        CallState call = TiviPhoneService.calls.selectedCall;
        String res = TiviPhoneService.getInfo(call.iEngID, call.iCallId, cmd);

        if (TextUtils.isEmpty(res))
            return null;

        if ("0".compareTo(res) == 0)
            return ctx.getResources().getDrawable(R.drawable.indicator_gray);            // Gray

        if ("1".compareTo(res) == 0)
            return ctx.getResources().getDrawable(R.drawable.indicator_red);            // Red

        if ("2".compareTo(res) == 0)
            return ctx.getResources().getDrawable(R.drawable.indicator_green);          // Green

        return null;
    }
}

