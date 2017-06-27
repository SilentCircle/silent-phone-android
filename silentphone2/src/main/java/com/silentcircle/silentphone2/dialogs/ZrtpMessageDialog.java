/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

import android.annotation.TargetApi;
import android.app.Activity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.InCallActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;

/**
 * Contains ZRTP message mapping and the ZRTP message dialog.
 * <p/>
 * Created by werner on 31.03.14.
 */
public class ZrtpMessageDialog extends DialogFragment {

    @SuppressWarnings("unused")
    private static final String TAG = "ZrtpMessageDialog";
    /*
     * Mapping of ZRTP and other warning and error message to common message
     */
    private static boolean initialized = false;

    private static String mZrtpCommonCls;

    private static SparseArray<String> messageMap = new SparseArray<>(40);

    private boolean showDetail = false;

    private TextView errorMessage;
    private TextView explanation;
    private int callId;

    private InCallActivity mParent;

    private static String SHORT_TEXT = "short";
    private static String ERROR_TEXT = "error";
    private static String EXPLAIN_TEXT = "explanation";
    private static String ERROR_TYPE = "type";
    private static String CALL_ID = "call_id";

    /**
     * Setup new messages based on warning/err message classification.
     * <p/>
     * The ZrtpMessageDialog.newInstance method knows about the String array offsets. If this gets changed
     * you must change it there as well.
     *
     * @param msg original warning/error message
     * @return String array with new, possibly translated messages
     */
    public static String[] getTranslatedMessage(Context ctx, String msg, CallState call) {
        if (TextUtils.isEmpty(msg))
            return null;

        int index = msg.indexOf(':');   // message classification is sn_cmmm: - see strings.xml
        String msgIdString = msg.substring(0, index);

        // These two messages are negligible (replay, parse problem), thus ignore them completely
        if ("s2_c007".equals(msgIdString) || "s2_c051".equals(msgIdString))
            return null;

        int secState;
        try {
            secState = Integer.parseInt(TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.zrtp.sec_state"));
        } catch (NumberFormatException e) {
            return null;
        }

        // The 0x100 signals that SDES is active and thus we are in a secure state. No need to
        // display scary ZRTP messages. Even if ZRTP fails we stay secure with SDES - at least
        // in SilentCircle environment.
        //
        // Now check which messages we must show in any case.
        // Display the following warning messages because they either require user attention or give some
        // information about bad network/SRTP conditions.
        boolean showWarn = "s2_c004".equals(msgIdString) || "s2_c006".equals(msgIdString) || "s2_c008".equals(msgIdString) ||
                "s2_c050".equals(msgIdString);

        // Which error messages to show in case SDES is active. Currently none.
        boolean showError = "s3_c008".equals(msgIdString);

        if ((secState & 0x100) == 0x100 && !(showWarn || showError))
            return null;

        String[] retData = new String[3];
        retData[0] = null;
        retData[1] = msg;
        retData[2] = null;
        if (index == -1) {
            return retData;
        }
        int msgId = ctx.getResources().getIdentifier(msgIdString, "string", ctx.getPackageName());
        if (msgId == 0) {
            return retData;
        }
        retData[0] = getCommonMessage(ctx, msgId);
        retData[1] = msgIdString + ": " + ctx.getString(msgId);  // prepend id-code to message from resource that maybe translated
        msgId = ctx.getResources().getIdentifier(msgIdString + "_explanation", "string", ctx.getPackageName());
        if (msgId != 0)
            retData[2] = ctx.getString(msgId);
        return retData;
    }

    /**
     * Create a ZrtpMessageDialog instance and set the arguments.
     * <p/>
     * This method knows about the String array offsets that the caller (TCallWindow#getTranslatedMessage)
     * uses. Keep in sync.
     *
     * @param texts  String array that contains the messages to display
     * @param type   Error or Warning type
     * @param callId which call triggered the problem.
     */
    public static ZrtpMessageDialog newInstance(String[] texts, int type, int callId) {
        ZrtpMessageDialog f = new ZrtpMessageDialog();

        Bundle args = new Bundle();
        args.putString(SHORT_TEXT, texts[0]);
        args.putString(ERROR_TEXT, texts[1]);
        args.putString(EXPLAIN_TEXT, texts[2]);
        args.putInt(ERROR_TYPE, type);
        args.putInt(CALL_ID, callId);
        f.setArguments(args);
        return f;
    }

    public ZrtpMessageDialog() {
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        commonOnAttach(getActivity());
    }

    /*
     * Deprecated on API 23
     * Use onAttachToContext instead
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            commonOnAttach(activity);
        }
    }

    private void commonOnAttach(Activity activity) {
        mParent = (InCallActivity) activity;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @SuppressWarnings("deprecation")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Bundle args = getArguments();
        if (args == null)
            return null;

        int type = args.getInt(ERROR_TYPE);
        callId = args.getInt(CALL_ID);

        AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
        // Get the layout inflater
        LayoutInflater inflater = mParent.getLayoutInflater();

        // Inflate and set the layout for the dialog
        // set the SAS string to compare
        // Pass null as the parent view because its going in the dialog layout
        View view = inflater.inflate(R.layout.dialog_zrtp_message, null);
        if (view == null)
            return null;
        TextView weType = (TextView) view.findViewById(R.id.ZrtpWEType);

        TextView shortText = (TextView) view.findViewById(R.id.ZrtpWEShortText);
        String shortTxt = getArguments().getString(SHORT_TEXT);

        boolean showEndButton = showEndCallButton(shortTxt);
        if (shortTxt == null) {
            shortTxt = getArguments().getString(ERROR_TEXT);
            showEndButton = true;
        }
        shortText.setText(shortTxt);

        errorMessage = (TextView) view.findViewById(R.id.ZrtpWEText);
        errorMessage.setText(getArguments().getString(ERROR_TEXT));
        errorMessage.setVisibility(View.GONE);

        explanation = (TextView) view.findViewById(R.id.ZrtpWEExplanation);
        explanation.setText(getArguments().getString(EXPLAIN_TEXT));
        explanation.setVisibility(View.GONE);

        Button showHide = (Button) view.findViewById(R.id.ZrtpDetailsButton);
        showHide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (!showDetail) {
                    errorMessage.setVisibility(View.VISIBLE);
                    explanation.setVisibility(View.VISIBLE);
                    ((Button) view).setText(getString(R.string.zrtp_hide_details));
                }
                else {
                    errorMessage.setVisibility(View.GONE);
                    explanation.setVisibility(View.GONE);
                    ((Button) view).setText(getString(R.string.zrtp_show_details));
                }
                showDetail = !showDetail;
            }
        });

        if (type == TiviPhoneService.CT_cb_msg.eZRTPErrA.ordinal()) {
            weType.setText(R.string.zrtp_we_error);
            shortText.setTextColor(ContextCompat.getColor(mParent, R.color.solid_red));
        }
        else {
            String errTxt = getArguments().getString(ERROR_TEXT);
            if (errTxt != null && errTxt.startsWith("s2_c006"))
                weType.setText(R.string.srtp_we_warn);
            else
                weType.setText(R.string.zrtp_we_warn);
        }

        // Add inflated view and action buttons
        if (showEndButton) {
            builder.setView(view).setPositiveButton(R.string.confirm_dialog, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    mParent.zrtpMessageDialogClose();
                }
            }).setNegativeButton(R.string.end_call_button, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    mParent.zrtpMessageDialogCloseEndCall(callId);
                }
            });
        }
        else {
            builder.setView(view).setPositiveButton(R.string.confirm_dialog, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    mParent.zrtpMessageDialogClose();
                }
            });
        }
        return builder.create();
    }

    private static String getCommonMessage(Context ctx, int msgId) {

        if (!initialized) {
            initialized = true;
            initializeMap(ctx);
        }
        return messageMap.get(msgId);
    }

    private static boolean showEndCallButton(String msg) {
        return (mZrtpCommonCls != null && mZrtpCommonCls.equals(msg));
    }

    private static void initializeMap(Context ctx) {
        String zrtpCommonCmp = ctx.getString(R.string.zrtp_common_cmp);
        mZrtpCommonCls = ctx.getString(R.string.zrtp_common_cls);
        String zrtpCommonUpd = ctx.getString(R.string.zrtp_common_upd);
        String zrtpCommonApp = ctx.getString(R.string.zrtp_common_app);

        messageMap.put(ctx.getResources().getIdentifier("s2_c002", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c004", "string", ctx.getPackageName()), zrtpCommonCmp);
        messageMap.put(ctx.getResources().getIdentifier("s2_c005", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c006", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c007", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c008", "string", ctx.getPackageName()), zrtpCommonCmp);
        messageMap.put(ctx.getResources().getIdentifier("s2_c050", "string", ctx.getPackageName()), zrtpCommonCmp);
        messageMap.put(ctx.getResources().getIdentifier("s2_c051", "string", ctx.getPackageName()), mZrtpCommonCls);

        messageMap.put(ctx.getResources().getIdentifier("s3_c001", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c002", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c003", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c004", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c005", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c006", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c007", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c008", "string", ctx.getPackageName()), mZrtpCommonCls);

        messageMap.put(ctx.getResources().getIdentifier("s4_c016", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c020", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c048", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c064", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c081", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c082", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c083", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c084", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c085", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c097", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c098", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c099", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c112", "string", ctx.getPackageName()), mZrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c144", "string", ctx.getPackageName()), zrtpCommonApp);
    }
}

