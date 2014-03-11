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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.utils.CTCall;
import com.silentcircle.silentphone.utils.CTCalls;
import com.silentcircle.silentphone.utils.Utilities;

import java.util.ArrayList;

public class CallManager extends Activity implements CallStateChangeListener {

    private static final String LOG_TAG = "CallManager";

    public static final String CALL_MGR_START = "com.silentcircle.silentphone.CALLMGR_START";
    public static final String CALL_MGR_STOP = "com.silentcircle.silentphone.CALLMGR_STOP";

    /**
     * Conference header is topmost title
     */
    private static final int CONF_HEADER_INDEX = 0;
    
    private LayoutInflater inflater;

    private LinearLayout callMngrContent;
 
    private TextView conferenceTitle;
    private TextView inOutTitle;
    private TextView privateTitle;

    private ArrayList<CTCall> confList = new ArrayList<CTCall>(15);
    private ArrayList<CTCall> inOutList = new ArrayList<CTCall>(15);
    private ArrayList<CTCall> privateList = new ArrayList<CTCall>(15);

    private ArrayList<View> contentViews = new ArrayList<View>(15);

    private Resources res;
    
    private boolean canUseZrtp;

    /* *********************************************************************
     * Functions and variables to bind this activity to the TiviPhoneService
     * This uses standard Android mechanism. 
     * ******************************************************************* */
    private boolean phoneIsBound;
    private TiviPhoneService phoneService;
    
    private ServiceConnection phoneConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            phoneService = ((TiviPhoneService.LocalBinder)service).getService();
            phoneIsBound = true;
            serviceBound();
            phoneService.addStateChangeListener(CallManager.this);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            phoneService = null;
            phoneIsBound = false;
        }
    };
    
    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(CallManager.this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE);
    }
    
    void doUnbindService() {
        if (phoneIsBound) {
            // Detach our existing connection.
            unbindService(phoneConnection);
            phoneIsBound = false;
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String action = getIntent().getAction();
        
        if (CALL_MGR_STOP.equals(action))
                finish();

        // This is a phone call screen, thus perform some specific handling
        // TODO: Proximity handling similar to CallWindow?
        int wflags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        wflags |= WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;
        wflags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;

        getWindow().addFlags(wflags);

        setContentView(R.layout.activity_call_manager);

        canUseZrtp = Utilities.checkZRTP();

        res = getResources();
        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        callMngrContent = (LinearLayout) findViewById(R.id.CallManagerContent);

        float fontSize = res.getDimension(R.dimen.call_manager_header_text_size);

        conferenceTitle = new TextView(this);
        conferenceTitle.setText(getString(R.string.call_mng_conference));
        conferenceTitle.setBackgroundResource(R.drawable.text_custom);
        conferenceTitle.setTextSize(fontSize);

        inOutTitle = new TextView(this);
        inOutTitle.setText(getString(R.string.call_mng_in_out));
        inOutTitle.setBackgroundResource(R.drawable.text_custom);
        inOutTitle.setTextSize(fontSize);

        privateTitle = new TextView(this);
        privateTitle.setText(getString(R.string.call_mng_private));
        privateTitle.setBackgroundResource(R.drawable.text_custom);
        privateTitle.setTextSize(fontSize);

        // Local bind the TiviPhoneService
        doBindService();
        setupCallLists();
    }

    /**
     * Called after service was connected
     */
    private void serviceBound() {
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    /* *********************************************************************
     * Activity lifecycle methods
     * ******************************************************************* */  

    @Override
    protected void onNewIntent (Intent intent) {

        String action = getIntent().getAction();
        if (CALL_MGR_STOP.equals(action))
                finish();

        updateContentView();        
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateContentView();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (phoneIsBound) {
            phoneService.removeStateChangeListener(this);
        }
        doUnbindService();
    }


    private void setupCallLists() {
        CTCall call;
        
        // Populate the list with active conference calls
        int cnt = TiviPhoneService.calls.getCallCnt(CTCalls.eConfCall);
        int idx = 0;
        if (cnt > 0) {
            for (int i = 0; i < CTCalls.MAX_GUI_CALLS; i++) {
                call = TiviPhoneService.calls.getCall(CTCalls.eConfCall, i);
                if (call != null) {
                    confList.add(idx, call);
                    idx++;
                }
            }
        }
        // Populate the list with active private calls
        cnt = TiviPhoneService.calls.getCallCnt(CTCalls.ePrivateCall);
        idx = 0;
        if (cnt > 0) {
            for (int i = 0; i < CTCalls.MAX_GUI_CALLS; i++) {
                call = TiviPhoneService.calls.getCall(CTCalls.ePrivateCall, i);
                if (call != null) {
                    privateList.add(idx, call);
                    idx++;
                }
            }
        }
        // Populate the list with calls in startup state
        cnt = TiviPhoneService.calls.getCallCnt(CTCalls.eStartupCall);
        idx = 0;
        if (cnt > 0) {
            for (int i = 0; i < CTCalls.MAX_GUI_CALLS; i++) {
                call = TiviPhoneService.calls.getCall(CTCalls.eStartupCall, i);
                if (call != null) {
                    inOutList.add(idx, call);
                    idx++;
                }
            }
        }        
    }

    private void updateContentView() {       
        callMngrContent.removeAllViews();

        contentViews.clear();
        contentViews.add(CONF_HEADER_INDEX, conferenceTitle);

        int idx = CONF_HEADER_INDEX + 1;
        
        if (!confList.isEmpty()) {
            for (CTCall call : confList) {
                contentViews.add(idx, inflateCallInfoLine(call));
                idx++;                
            }
        }
        if (!privateList.isEmpty()) {
            contentViews.add(idx, privateTitle);
            idx++;
            for (CTCall call : privateList) {
                contentViews.add(idx, inflateCallInfoLine(call));
                idx++;                
            }
        }
        if (!inOutList.isEmpty()) {
            contentViews.add(idx, inOutTitle);
            idx++;
            for (CTCall call : inOutList) {
                contentViews.add(idx, inflateCallInfoLine(call));
                idx++;                
            }
        }
        for (int i = CONF_HEADER_INDEX; i < idx; i++) {
            callMngrContent.addView(contentViews.get(i), i);
        }
        callMngrContent.invalidate();
    }
    

    private View inflateCallInfoLine(CTCall call) {
        View rowLayout = inflater.inflate(R.layout.call_manager_line, null, false);
        View rowView = (RelativeLayout) rowLayout.findViewById(R.id.CallMngLine);
        rowView.setTag(call);
        if (call.iIsOnHold)
            rowView.setBackgroundResource(R.drawable.call_mng_hold);

        rowView.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                addRemoveConf(view);
                return true;
            }
        });
        rowView.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                CTCall call = (CTCall)view.getTag();
                switchCallHold(call, view);
                view.invalidate();
            }
        });
        TextView textView = (TextView) rowLayout.findViewById(R.id.CallMngCallerName);
        textView.setText(call.getNameFromAB());

        textView = (TextView) rowLayout.findViewById(R.id.CallMngCallerNumber);
        textView.setText(call.bufPeer.toString());

        textView = (TextView) rowLayout.findViewById(R.id.CallMngSecState);
        textView.setText(call.bufSecureMsg.toString());

        textView = (TextView) rowLayout.findViewById(R.id.CallMngSas);
        if (call.bufSAS.toString().isEmpty())
            textView.setVisibility(View.INVISIBLE);
        else
            textView.setText(call.bufSAS.toString());

        ImageButton btn = (ImageButton) rowLayout.findViewById(R.id.CallMngEndCall);
        Utilities.setCallerImage(call, (ImageView)rowLayout.findViewById(R.id.CallMngImage));
        btn.setTag(call);
        btn.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                endCall(true, (CTCall)view.getTag());
            }
        });
        if (call.mustShowAnswerBT()) {
            btn = (ImageButton) rowLayout.findViewById(R.id.CallMngAnswerCall);
            btn.setTag(call);
            btn.setVisibility(View.VISIBLE);
            btn.setEnabled(true);
            btn.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    answerCall(true, (CTCall)view.getTag());
                }
            });
        }
        return rowView;
    }


    private View findContentViewByTag(CTCall tag) {
        View result = null;

        for (View v: contentViews) {
            if (v.getTag() == tag) {
                result = v;
                break;
            }
        }
        return result;
    }

    private void switchCallHold(CTCall call, View view) {

        // If call is currently not on hold, try to set it to hold
        if (!call.iIsOnHold) {
            if (call == TiviPhoneService.calls.selectedCall) // Never put selected call in hold mode
                return;

            if (inOutList.contains(call))
                return;

            // handle conference calls only: we can set a call to hold mode without switching selected call
            if (confList.contains(call)) {
                if (isConfOnHold()) // if conference is on hold, no need to process
                    return;
            }
            view.setBackgroundResource(R.drawable.call_mng_hold);
            call.iIsOnHold = true;
            TiviPhoneService.doCmd("*h" + call.iCallId);
        }
        // Try to set to non-hold mode
        else {
            CTCall otherCall = TiviPhoneService.calls.selectedCall;

            if (confList.contains(call)) {
                // empty - may be we need some code later
            }
            // try to set the clicked call to un-hold state
            else if (privateList.contains(call)) {

                // The clicked call will become the selected call, thus if the current selected
                // call is a conference call we need to set conference to hold first.
                if (confList.contains(otherCall)) {
                    setConfOnHold();
                }
                else {
                    View otherView = findContentViewByTag(otherCall);
                    otherCall.iIsOnHold = true;
                    otherView.setBackgroundResource(R.drawable.call_mng_hold);
                    TiviPhoneService.doCmd("*h" + otherCall.iCallId);
                }
            }
            else
                return;

            // un-hold the clicked call and set it as new current selected call
            call.iIsOnHold = false;
            view.setBackgroundResource(R.drawable.call_mng_normal);
            TiviPhoneService.calls.setCurCall(call);
            TiviPhoneService.doCmd("*u" + call.iCallId);
        }
    }

    /**
     * Check if conference is on hold.
     * 
     * A conference call is on hold if the selected call is not part of the
     * conference call.
     * 
     * @return true if conference is hold, false otherwise. 
     */
    private boolean isConfOnHold() {
        for (CTCall c : confList) {
            if (c == TiviPhoneService.calls.selectedCall)
                return false;
        }
        return true;
    }
    
    /**
     * Set all call in conference list to hold mode.
     */
    private void setConfOnHold() {
        for (CTCall c : confList) {
            if (c.iIsOnHold)
                continue;
            View view = findContentViewByTag(c);
            c.iIsOnHold = true;
            view.setBackgroundResource(R.drawable.call_mng_hold);
            TiviPhoneService.doCmd("*h" + c.iCallId);
        }
    }

    private void addRemoveConf(View callView) {
        CTCall call = (CTCall) callView.getTag(); // get the call to add/remove
        if (confList.contains(call)) {
            // Move call to private list. If selected call was moved set conference to hold
            confList.remove(call);
            privateList.add(call);
            call.iIsInConferece = false;
            TiviPhoneService.doCmd("*-" + call.iCallId);
            if (call == TiviPhoneService.calls.selectedCall)
                setConfOnHold();
        }
        else { 
            // move from private to conference. If the conference is on hold (does not
            // contains selected call) then new call will be set to hold also if it is
            // not the selected call.
            privateList.remove(call);
            confList.add(call);
            call.iIsInConferece = true;
            if (call != TiviPhoneService.calls.selectedCall && !call.iIsOnHold && isConfOnHold() ) {
                call.iIsOnHold = true;
                TiviPhoneService.doCmd("*h" + call.iCallId);
            }
            TiviPhoneService.doCmd("*+" + call.iCallId);
        }
        if (call.iIsOnHold) {
            callView.setBackgroundResource(R.drawable.call_mng_hold);
        }
        else {
            callView.setBackgroundResource(R.drawable.call_mng_normal);
        }
        updateContentView();
    }
        
    /* *********************************************************************
     * Call handling functions 
     * ******************************************************************* */

    synchronized void answerCall(boolean userPressed, CTCall call) {

        View view = findContentViewByTag(call);

        if (userPressed) {
            TiviPhoneService.doCmd("*a" + call.iCallId);
            return;
        }
        inOutList.remove(call);
        privateList.add(call);

        // user answered call, disable answer button, move call to private call list, update display
        ImageButton btn = (ImageButton) view.findViewById(R.id.CallMngAnswerCall);
        btn.setVisibility(View.INVISIBLE);
        btn.setEnabled(false);

        // If users answers a call and another call is selected then put that
        // call on hold and set answered call as selected call. 
        if (call != TiviPhoneService.calls.selectedCall) {
            CTCall otherCall = TiviPhoneService.calls.selectedCall;
            TiviPhoneService.calls.setCurCall(call);
            switchCallHold(otherCall, view);
        }
    }

    synchronized void endCall(boolean userPressed, CTCall call) {
        if (call == null) {
            Log.w(LOG_TAG, "Call Manager: Null call during end call processing: " + userPressed);
            return;
        }
        // We need these counts to decide what to do
        int confCnt = confList.size();      // same as TiviPhoneService.calls.getCallCnt(CTCalls.eConfCall);
        int privCnt = privateList.size();   // same as TiviPhoneService.calls.getCallCnt(CTCalls.ePrivateCall);
        int inOutCnt = inOutList.size();    // same as TiviPhoneService.calls.getCallCnt(CTCalls.eStartupCall);

        // Check if this is the only/last call - that's the easy part
        if ((confCnt + privCnt + inOutCnt) <= 1) {
            // Our user pressed the end call button, end our call, otherwise just terminate this Activity
            if (userPressed) {
                TiviPhoneService.doCmd("*e" + call.iCallId);
            }
            finish();
            return;
        }
    
        /*
         *  The following code handles cases with more then one call.
         */
        int callType;
        if (inOutList.contains(call))
            callType = CTCalls.eStartupCall;
        else if (privateList.contains(call)) 
            callType = CTCalls.ePrivateCall;
        else if (confList.contains(call))
            callType = CTCalls.eConfCall;
        else
            return;

        switch (callType) {
        /*
         * If the startup call is not the selected call then just terminate the call and
         * handle call log.
         * 
         * Otherwise check if we have another call that we can set as new selected call.
         */
        case CTCalls.eStartupCall:
            if (!inOutList.remove(call))
                return;                     // already handled

            inOutCnt--;
            switchToNextCall(call, userPressed);
            call.iEnded = 2;
            break;
        
         /*
          * If the private call is not the selected call then just terminate the call and
          * handle call log.
          * 
          * Otherwise check if we have another call that we can set as new selected call.
          */
        case CTCalls.ePrivateCall:
            if (!privateList.remove(call))
                return;                     // already handled

            privCnt--;
            switchToNextCall(call, userPressed);
            call.iEnded = 2;
            break;
        
        /*
         * Same handling as private calls.
         */
        case CTCalls.eConfCall:
            if (!confList.remove(call))
                return;                     // already handled

            confCnt--;
            switchToNextCall(call, userPressed);
            call.iEnded = 2;
            break;        
        }
    }

    /**
     * Look for a new call in the lists and set it as selected (active) call.
     *  
     * @param call the call to terminate
     * @return the new selected call or null if call was not the selected (active) call
     */
    private CTCall switchToNextCall(CTCall call, boolean userPressed) {
        CTCall nextCall = null;

        // If this is not the selected call, just terminate and handle call log.
        if (call != TiviPhoneService.calls.selectedCall) {
            if (userPressed) {
                TiviPhoneService.doCmd("*e" + call.iCallId);
            }
            return null;
        }

        if (confList.size() >= 1) 
            nextCall = confList.get(0);
        else if (privateList.size() >= 1)
            nextCall = privateList.get(0);
        else if (inOutList.size() >= 1)
            nextCall = inOutList.get(0);

        // Terminate current call, switch to next.
        TiviPhoneService.doCmd("*e" + call.iCallId);

        if (nextCall != null) {
            TiviPhoneService.calls.setCurCall(nextCall);    // switch selected call
            if (nextCall.iIsOnHold) {
                nextCall.iIsOnHold = false;
                TiviPhoneService.doCmd("*u" + nextCall.iCallId);
            }
        }
        return nextCall;
    }

    /* *********************************************************************
     * Methods and private class that handle ZRTP state changes
     * ******************************************************************* */  

    /**
     * Private Helper class that implements the UI for ZRTP state changes.
     * @author werner
     *
     */
    private class zrtpChangeWrapper implements Runnable {
        
        private final CTCall call;
        private final TiviPhoneService.CT_cb_msg msg;
        
        zrtpChangeWrapper(CTCall _call, TiviPhoneService.CT_cb_msg _msg) {
            call = _call;
            msg = _msg;
        }

        public void run() {
            if (canUseZrtp && call.iActive) {
                View view = findContentViewByTag(call);
                TextView textView;

                switch (msg) {

                case eZRTP_sas:
                    textView = (TextView) view.findViewById(R.id.CallMngSas);
                    textView.setText(call.bufSAS.toString());
                    break;
                    
                default:
                    textView = (TextView) view.findViewById(R.id.CallMngSecState);
                    textView.setText(call.bufSecureMsg.toString());
                 }
            }
        }
    }

    /**
     * The ZRTP state change listener.
     * 
     * This listener runs in the context of the TiviPhoneService thread. To perform some
     * activities on the UI you must use <code>runOnUiThread(new Runnable() { ... }</code>
     * 
     * @param call the call that changed its ZRTP state.
     * @param msg  the message id of the ZRTP status change. 
     */ 
    public void zrtpChange(CTCall call, TiviPhoneService.CT_cb_msg msg) {
        runOnUiThread(new zrtpChangeWrapper(call, msg));
    }

    /**
     * Private Helper class that implements the UI for call state changes.
     * 
     * @author werner
     *
     */
    private class callChangeWrapper implements Runnable {

        private final CTCall call;
        private final TiviPhoneService.CT_cb_msg msg;

        callChangeWrapper(CTCall _call, TiviPhoneService.CT_cb_msg _msg) {
            call = _call;
            msg = _msg;
        }

        public void run() {           
            View view = findContentViewByTag(call);

            // If unknown call, classify it and add to correct list
            if (view == null) {
                if (CTCalls.isCallType(call, CTCalls.eStartupCall))
                    inOutList.add(call);
                else if (CTCalls.isCallType(call, CTCalls.ePrivateCall))
                    privateList.add(call);
                else if (CTCalls.isCallType(call, CTCalls.eConfCall))
                    confList.add(call);
                else
                    return;                 // unknown type
                // Added a new call, update view
                updateContentView();
                view = findContentViewByTag(call);
            }
            TextView textView;

            switch (msg) {
            case eStartCall:
                answerCall(false, call);
                break;
                
            case eEndCall:
                endCall(false, call); 
                break;
                
            case eNewMedia:
                break;

            case eCalling:
                textView = (TextView) view.findViewById(R.id.CallMngCallerNumber);
                textView.setText(call.bufPeer.toString());
                break;

            default:
                break;
            }
            // Get the SIP display name it not already done
           String s = call.getNameFromAB();
           if (s != null) {
              textView = (TextView) view.findViewById(R.id.CallMngCallerName);
              textView.setText(s);
           }
           updateContentView();
        }
    }

    /**
     * The Call state change listener.
     * 
     * This listener runs in the context of the TiviPhoneService thread. To perform some
     * activities on the UI you must use <code>runOnUiThread(new Runnable() { ... }</code>
     * 
     * @param call the call that changed its state.
     * @param msg  the message id of the status change. 
     */ 
    public void stateChange(CTCall call, TiviPhoneService.CT_cb_msg msg) {
        runOnUiThread(new callChangeWrapper(call, msg));
    }

}
