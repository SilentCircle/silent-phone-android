/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.activities;

import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class ZrtpMessages extends FragmentActivity {
    
    public static final String MESSAGES = "messages";
    public static final String CALL_ID = "call_id";
    public static final String MSG_TYPE = "MSG_TYPE";
    
    private ZrtpMessageDialog zrtpDialog;
    
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Gives more space on smaller screens to fill in the data fields
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // This is a warning/error message screen, thus perform some specific handling
        int wflags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        wflags |= WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;
        wflags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
        wflags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

        getWindow().addFlags(wflags);

        setContentView(R.layout.activity_zrtp_message);
        
        Intent intent = getIntent();
        String[] messages = intent.getStringArrayExtra(MESSAGES);        

        int callId = intent.getIntExtra(CALL_ID, 0);
        int type = intent.getIntExtra(MSG_TYPE, TiviPhoneService.CT_cb_msg.eZRTPErrA.ordinal());
        
        zrtpDialog = ZrtpMessageDialog.newInstance(messages, type,  callId);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    /* *********************************************************************
     * Activity lifecycle methods
     * ******************************************************************* */  

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FragmentManager fragmentManager = getSupportFragmentManager();
        zrtpDialog.show(fragmentManager, "SilentPhoneZrtpMessage");
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Close ZRTP Message activity.
     */
    public void zrtpMessageDialogClose() {
        setResult(Activity.RESULT_OK);
        finish();
    }
    
    /**
     * Close ZRTP Message activity and end the call via parent activity.
     */
    public void zrtpMessageDialogCloseEndCall(int callId) {
        Intent intent = new Intent();
        intent.putExtra(CALL_ID, callId);
        setResult(Activity.RESULT_CANCELED, intent);
        finish();
    }

    
    /*
     * Mapping of ZRTP and other warning and error message to common message
     */
    private static boolean initialized = false;
    
    private static String zrtpCommonCmp;
    private static String zrtpCommonCls;
    private static String zrtpCommonUpd;
    private static String zrtpCommonApp;
    
    private static SparseArray<String> messageMap = new SparseArray<String>(40);
    
    public static String getCommonMessage(Context ctx, int msgId) {
        
        if (!initialized) {
            initialized = true;
            initializeMap(ctx);
        }
        return messageMap.get(msgId);
    }

    private static boolean showEndCallButton(String msg) {
        return (zrtpCommonCls != null && zrtpCommonCls.equals(msg));    
    }

    private static void initializeMap(Context ctx) {
        zrtpCommonCmp = ctx.getString(R.string.zrtp_common_cmp);
        zrtpCommonCls = ctx.getString(R.string.zrtp_common_cls);
        zrtpCommonUpd = ctx.getString(R.string.zrtp_common_upd);
        zrtpCommonApp = ctx.getString(R.string.zrtp_common_app);

        messageMap.put(ctx.getResources().getIdentifier("s2_c002", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c004", "string", ctx.getPackageName()), zrtpCommonCmp);
        messageMap.put(ctx.getResources().getIdentifier("s2_c005", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c006", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c007", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s2_c008", "string", ctx.getPackageName()), zrtpCommonCmp);
        messageMap.put(ctx.getResources().getIdentifier("s2_c050", "string", ctx.getPackageName()), zrtpCommonCmp);
        messageMap.put(ctx.getResources().getIdentifier("s2_c051", "string", ctx.getPackageName()), zrtpCommonCls);

        messageMap.put(ctx.getResources().getIdentifier("s3_c001", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c002", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c003", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c004", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c005", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c006", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c007", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s3_c008", "string", ctx.getPackageName()), zrtpCommonCls);

        messageMap.put(ctx.getResources().getIdentifier("s4_c016", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c020", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c048", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c064", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c081", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c082", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c083", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c084", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c085", "string", ctx.getPackageName()), zrtpCommonUpd);
        messageMap.put(ctx.getResources().getIdentifier("s4_c097", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c098", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c099", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c112", "string", ctx.getPackageName()), zrtpCommonCls);
        messageMap.put(ctx.getResources().getIdentifier("s4_c144", "string", ctx.getPackageName()), zrtpCommonApp);
    }

    /*
     * The ZRTP message dialog
     */
    public static class ZrtpMessageDialog extends DialogFragment {
        private boolean showDetail = false;
        
        private TextView errorMessage;
        private TextView explanation;
        private int callId;
        
        
        private static String SHORT_TEXT = "short";
        private static String ERROR_TEXT = "error";
        private static String EXPLAIN_TEXT = "explanation";
        private static String ERROR_TYPE = "type";
        private static String CALL_ID = "callid";

        /**
         * Create a ZrtpMessageDialog instance and set the arguments.
         * 
         * This method knows about the String array offsets that the caller (TCallWindow#getTranslatedMessage)
         * uses. Keep in sync. 
         * 
         * @param texts String array that contains the messages to display
         * @param type  Error or Warning type
         * @param callId which call triggered the problem.
         * @return
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

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            int type = getArguments().getInt(ERROR_TYPE);
            callId = getArguments().getInt(CALL_ID);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();

            // Inflate and set the layout for the dialog
            // set the SAS string to compare
            // Pass null as the parent view because its going in the dialog layout
            View view = inflater.inflate(R.layout.dialog_zrtp_we, null);
            TextView weType = (TextView)view.findViewById(R.id.ZrtpWEType);

            TextView shortText = (TextView)view.findViewById(R.id.ZrtpWEShortText);
            String shortTxt = getArguments().getString(SHORT_TEXT);
            
            boolean showEndButton = showEndCallButton(shortTxt);
            if (shortTxt == null) {
                shortTxt = getArguments().getString(ERROR_TEXT);
                showEndButton = true;
            }
            shortText.setText(shortTxt);

            errorMessage = (TextView)view.findViewById(R.id.ZrtpWEText);
            errorMessage.setText(getArguments().getString(ERROR_TEXT));
            errorMessage.setVisibility(View.INVISIBLE);

            explanation = (TextView)view.findViewById(R.id.ZrtpWEExplanation);
            explanation.setText(getArguments().getString(EXPLAIN_TEXT));
            explanation.setVisibility(View.INVISIBLE);
            
            Button showHide = (Button)view.findViewById(R.id.ZrtpDetailsButton);
            showHide.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    if (!showDetail) {
                        errorMessage.setVisibility(View.VISIBLE);
                        explanation.setVisibility(View.VISIBLE);
                        ((Button)view).setText(getActivity().getString(R.string.zrtp_hide_details));
                    }
                    else {
                        errorMessage.setVisibility(View.INVISIBLE);
                        explanation.setVisibility(View.INVISIBLE);
                        ((Button)view).setText(getActivity().getString(R.string.zrtp_show_details));
                    }
                    showDetail =!showDetail;
                }
            });

            if (type == TiviPhoneService.CT_cb_msg.eZRTPErrA.ordinal()) {
                weType.setText(R.string.zrtp_we_error);
                shortText.setTextColor(getResources().getColor(R.color.solid_red));
            }
            else {
                String errTxt = getArguments().getString(ERROR_TEXT);
                if (errTxt != null && errTxt.startsWith("s2_c006"))
                    weType.setText(R.string.srtp_we_warn);
                else
                    weType.setText(R.string.zrtp_we_warn);
                shortText.setTextColor(getResources().getColor(R.color.solid_yellow));                
            }
            
            // Add inflated view and action buttons
            if (showEndButton) {
                builder.setView(view).setPositiveButton(R.string.confirm_dialog, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ((ZrtpMessages) getActivity()).zrtpMessageDialogClose();
                    }
                }).setNegativeButton(R.string.hangup_call, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ((ZrtpMessages) getActivity()).zrtpMessageDialogCloseEndCall(callId);
                    }
                });
            }
            else {
                builder.setView(view).setPositiveButton(R.string.confirm_dialog, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ((ZrtpMessages) getActivity()).zrtpMessageDialogClose();
                    }
                });
            }
            return builder.create();
        }
    }
}
