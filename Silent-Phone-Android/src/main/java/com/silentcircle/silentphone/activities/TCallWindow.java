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

package com.silentcircle.silentphone.activities;//TCallWindow;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.utils.CTCall;
import com.silentcircle.silentphone.utils.CTCalls;
import com.silentcircle.silentphone.utils.CTFlags;
import com.silentcircle.silentphone.utils.DeviceHandling;
import com.silentcircle.silentphone.utils.Utilities;
import com.silentcircle.silentphone.views.CallScreen;

import java.lang.ref.WeakReference;

public class TCallWindow  extends SherlockFragmentActivity implements CallStateChangeListener, DeviceStateChangeListener,
    SensorEventListener {
    
    private static final String LOG_TAG = "TCallWindow";

    private static final int IN_CALL_DIALPAD_HIDE_TIMER = 1;
    private static final int IN_CALL_DIALPAD_HIDE_TIME_DEFAULT = 6000;   // given in ms

    private static final int SWITCH_SECURITY_STATE_TEXT_AUDIO = 2;
    private static final int SWITCH_SECURITY_STATE_TEXT_VIDEO = 3;
    private static final int SWITCH_SECURITY_STATE_TEXT_TIME_DEFAULT = 5000;   // given in ms

    private static final int ZRTP_MESSAGE_RESULT = 101;
    
    /**
     * The static variables and flags have a global meaning and are used even if
     * this object was destroyed
     */
    private  static boolean bIsVideo = false;
    
    /**
     * Stores the call type of the last/current call shown on this screen. Used to
     * set up data if user re-activates the call screen during an ongoing call.
     */
    private static int iLastiIsIncoming = TiviPhoneService.CALL_TYPE_INAVLID;;
    
    private static boolean isMuted = false;
    private static boolean showAntennaInfo = false;// static -> must be sticky

    private Resources resources;
    private Thread monitoring;
    private ActionBar actionBar;

    private int iIsIncoming = TiviPhoneService.CALL_TYPE_INAVLID;
    
    private boolean  iCanUseZRTP = false;
    /**
     * Variable controls the delay when to close the call window after a call ended.
     * If set to -1 then call is still active. The endCall functions ets this variable
     * to some dealy value greater zero to control the delay. The time monitoring thread
     * performs delay handling 
     */
    private int iHideAfter = -1;

    /**
     * Will become true if proximity detector reports that phone is "near"
     */
    private boolean isNear = false;
    
    private int iForeground = 1;

    private CallScreen videoScreen = null;
   
    private char prevAnntenaValue = 0;

    /* *********************************************************************
     * Buttons and text fields of this activity. onCreate initializes most of
     * them.
     * ******************************************************************* */
    private ImageButton muteButton;
    private ImageButton speakerButton;
    private ImageButton headsetBtButton;
    private ImageButton showInCallDialer;
    private ImageButton addNewCall;
    private ImageButton startVideoCall;
    private Button startCallManager;

    private Button dismissCall;
    private Button answerCall;
    private Button hangupCall;
    
    private TextView callNumber;
    private TextView callDuration;
    private TextView callingState;
    private TextView sipCallerName;
    private TextView antennaInfo;
    private ImageView countryFlag;
    private ImageView callerImage;
    
    private TextView sasText;
    private TextView additionalInfo;
    private TextView secureState;
    private Button   verifyButton;
    private TextView zrtpPeerName;

    /*
     * Buttons in Video overlay
     */
    private TextView videoMuteButton;
    private TextView videoAccept;
    private TextView videoDecline;
    private TextView videoEndCall;
    private ImageButton videoAudioMute;
    private ImageButton videoSwitchCamera;
    private ImageButton antennaButton;
    
    private DialogFragment inCallDialer;

    private RelativeLayout rootLayout;
    
    
    /**
     * Holds the layout view returned by the Video overlay layout inflate
     */
    private View videoOverlay;
    
    /**
     * Holds the RelativeLayout inside the inflated video overlay layout
     */
    private RelativeLayout videoOverlayView;
    private SurfaceView previewSurface;
    
    private boolean zrtpErrorShowing = false;

    // Some hardware stuff
    private SensorManager mSensorManager;
    private Sensor mProximity;
    private PowerManager powerManager;
    private PowerManager.WakeLock mProximityWakeLock;
    
    /*
     * This handler currently just controls display timeouts, maybe we can use it
     * for other actions as well (later)
     */
    private Handler mHandler = new InternalHandler(this); 
    private boolean dialpadAutoHide = true;
    
    private static class InternalHandler extends Handler {
        private final WeakReference<TCallWindow> mTarget;
        
        InternalHandler(TCallWindow parent) {
            mTarget = new WeakReference<TCallWindow>(parent); 
        }

        @Override
        public void handleMessage(Message msg) {
            TCallWindow parent = mTarget.get(); 
            if (parent == null) 
                return; 

            switch (msg.what) {
            case IN_CALL_DIALPAD_HIDE_TIMER:
                if (parent.inCallDialer != null && parent.inCallDialer.isVisible())
                    parent.hideInCallDialog(null);     // Method does not use the View parameter, thus null is OK
                break;

                case SWITCH_SECURITY_STATE_TEXT_AUDIO:
                case SWITCH_SECURITY_STATE_TEXT_VIDEO:
                    CTCall call = TiviPhoneService.calls.selectedCall;
                    String askState = "media.audio.zrtp.sec_state";
                    if (msg.what == SWITCH_SECURITY_STATE_TEXT_VIDEO)
                        askState = "media.video.zrtp.sec_state";
                    if ("ZRTP Error".equals(call.bufSecureMsg.toString())) {
                        int secState = Integer.parseInt(TiviPhoneService.getInfo(call.iEngID, call.iCallId, askState));
                        if ((secState & 0x100) == 0x100) {      // We have SDES security
                            parent.secureState.setText(R.string.secstate_secure);
                            parent.secureState.setTextColor(parent.resources.getColor(R.color.solid_yellow));
                            parent.additionalInfo.setText(R.string.to_server_only);
                            parent.additionalInfo.setVisibility(View.VISIBLE);
                        }
                        else {
                            parent.secureState.setText(R.string.zrtp_we_error);
                            parent.secureState.setTextColor(parent.resources.getColor(R.color.solid_red));
                            parent.additionalInfo.setVisibility(View.INVISIBLE);
                        }
                    }
                break;
            }
        }
    }

    // In-call dial pad uses it to generate DTMF tones.
    private ToneGenerator toneGenerator;


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
            phoneService.addStateChangeListener(TCallWindow.this);
            phoneService.addDeviceChangeListener(TCallWindow.this);
            serviceBound();
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

    private void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(TCallWindow.this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE);
    }
    
    private void doUnbindService() {
        if (phoneIsBound) {
            // Detach our existing connection.
            unbindService(phoneConnection);
            phoneIsBound = false;
        }
    }

    private String doX(int z) {
        if (phoneService != null)
            return phoneService.doX(z);
        return "test";
    }

    private int doCmd(String s) {
        return TiviPhoneService.doCmd(s);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ZRTP_MESSAGE_RESULT) {
            if (resultCode != RESULT_OK) {
                int callId = data.getIntExtra(ZrtpMessages.CALL_ID, 0);
                zrtpMessageDialogCloseEndCall(callId);
            }
            else {
                zrtpMessageDialogClose();
            }
            return;
        }
    }

    /*
     * Switch audio between normal and in-call mode.
     * 
     * This is a static wrapper function to simplify thread/context handling. 
     */
    public static void am(Context ctx, boolean mode) {
        if (!DeviceHandling.switchAudioMode())
            return;

        AudioManager am = (AudioManager) ctx.getSystemService(AUDIO_SERVICE);

        int modeInCall =  Build.VERSION.SDK_INT < 11 ?  AudioManager.MODE_IN_CALL : AudioManager.MODE_IN_COMMUNICATION;
        am.setMode(mode ? modeInCall : AudioManager.MODE_NORMAL);
        if (mode) {
            am.setMicrophoneMute(false);
        }
    }

    /* *********************************************************************
     * Call handling functions 
     * ******************************************************************* */

    void answerCall(boolean userPressed) {

        if (userPressed) {
            // this should be called only before starting audio engine(recording, playback)
            // or it can brake audio playback (Samsung S3)
            am(getBaseContext(), true);
        }
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        runOnUiThread(new Runnable() {
            public void run() {
                phoneService.stopRT();
                speakerButton.setPressed(Utilities.isSpeakerOn(getBaseContext()));
                dismissCall.setVisibility(View.INVISIBLE);
                dismissCall.setEnabled(false);

                answerCall.setVisibility(View.INVISIBLE);
                answerCall.setEnabled(false);

                hangupCall.setVisibility(View.VISIBLE);
                hangupCall.setEnabled(true);

            }
        });
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (!userPressed) {
            setCallState(getDur(0));
        }
        else {
            if (call != null) {
                doCmd("*a" + call.iCallId);
            }
            else
                doCmd(":a");
        }

        if (!userPressed) {
            // this must be called after audio (playback,recording) starts
            // if not then mic is not working on some devices.
            // After togling the speaker to enable microphone on some devices reset
            // speaker to user selected state.
            Utilities.turnOnSpeaker(getBaseContext(), true, false);
            Utilities.turnOnSpeaker(getBaseContext(), false, false);
            Utilities.restoreSpeakerMode(getBaseContext());
        }

    }
    
    synchronized void endCall(boolean userPressed, final CTCall call) {
        if (call == null) {
            Log.w(LOG_TAG, "Null call during end call processing: " + userPressed);
            runOnUiThread(new Runnable() {
                public void run() {
                    phoneService.stopRT();
                    am(getBaseContext(), false);
                    finish();
                }
            });            
            return;
        }

        // Our user pressed the end-call button.
        if (userPressed) {
            doCmd("*e" + call.iCallId);
            return;
        }
        runOnUiThread(new Runnable() {
            public void run() {
                phoneService.stopRT();
                hangupCall.setVisibility(View.INVISIBLE);
                hangupCall.setEnabled(false);
                
                // Don't use "switchVideo": call is already terminated, just release Camera resources
                if (bIsVideo) {
                    bIsVideo = false;
                    CallScreen.stopCamera();
                    rootLayout.removeView(videoOverlay);
                }
                am(getBaseContext(), false);
                call.secExceptionMsg = null;
            }
        });
        Utilities.turnOnSpeaker(this, false, true); // Set speaker to off at end of call and remember this

        iHideAfter = userPressed ? 1 : 3;

        // If the call never started and user pressed hangup - this call is not a missed call
        if (!userPressed) {
            String str = call != null ? call.bufMsg.toString() : doX(8);
            str = str.replace('\r', ' ');
            str = str.replace((char) 3, ' ');
            str = str.replace((char) 1, ' ');
            setCallState(str);
        }
        else
            setCallState(getDur(1));
    }

    private String getDur(int iAddDur) {
        CTCall call = TiviPhoneService.calls.selectedCall;

        if (call == null || call.uiStartTime == 0) {
            return "";
        }

        long dur = (System.currentTimeMillis() - call.uiStartTime + 500) / 1000;
        StringBuilder s = new StringBuilder(40);
        if (iAddDur != 0) {
            s.append(getString(R.string.call_duration));
            s.append(' ');
        }
        long min = dur / 60;
        long sec = dur - min * 60;
        if (min < 10)
            s.append('0');
        s.append(min);
        s.append(':');
        if (sec < 10)
            s.append('0');
        s.append(sec);
        return s.toString();
    }

    private void setAntennaInfo() {
        CTCall call = TiviPhoneService.calls.selectedCall;

        if (call == null || call.uiStartTime == 0) {
            return;
        }
        String info = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.bars");
        if (info != null && info.length() <= 0)
            return;

        if (showAntennaInfo) {
            antennaInfo.setText(TiviPhoneService.getInfo(call.iEngID,call.iCallId,"media.codecs"));
        }

        char antenna = info.charAt(0);
        if (prevAnntenaValue == antenna)
            return;
        prevAnntenaValue = antenna;

        switch (antenna) {
        case '0':
            antennaButton.setImageResource(R.drawable.ico_antena_0);
            break;
        case '1':
            antennaButton.setImageResource(R.drawable.ico_antena_1);
            break;
        case '2':
            antennaButton.setImageResource(R.drawable.ico_antena_2);
            break;
        case '3':
            antennaButton.setImageResource(R.drawable.ico_antena_3);
            break;
        case '4':
            antennaButton.setImageResource(R.drawable.ico_antena_4);
            break;
        }
    }

    /* *********************************************************************
     * Methods and private class that handle ZRTP state changes
     * ******************************************************************* */
    
    /**
     * Method to handle end of the ZRTP Message activity
     */
    public void zrtpMessageDialogClose() {
        zrtpErrorShowing = false;
    }
    
    /**
     * Method to close the ZRTP Warning/Error and end the call overlay
     */
    public void zrtpMessageDialogCloseEndCall(int callId) {
        CTCall call = TiviPhoneService.calls.findCallById(callId);
        endCall(true, call);
        zrtpErrorShowing = false;
    }

    private void showZrtpErrorWarning(String[] message, int type, CTCall call) {
        if (message == null)
            return;

        // IF error dialog already on display don't show a new one to avoid stacking
        if (!zrtpErrorShowing) {
            Intent intent = new Intent();
            intent.setClass(this, ZrtpMessages.class);
            intent.putExtra(ZrtpMessages.MESSAGES, message);
            intent.putExtra(ZrtpMessages.CALL_ID, call.iCallId);
            intent.putExtra(ZrtpMessages.MSG_TYPE, type);
            startActivityForResult(intent, ZRTP_MESSAGE_RESULT);
        }
        zrtpErrorShowing = true;
        return;
    }

    /**
     * Setup new messages based on warning/err message classification.
     * 
     * The ZrtpMessageDialog.newInstance method knows about the String array offsets. If this gets changed
     * you must change it there as well.
     * 
     * @param msg original warning/error message
     * @return String array with new, possibly translated messages
     */
    private String[] getTranslatedMessage(String msg, CTCall call) {
        if (msg == null)
            return null;

        int index = msg.indexOf(':');   // message classification is sn_cmmm: - see strings.xml
        String msgIdString = msg.substring(0, index);

        // These two messages are negligible (replay, parse problem), thus ignore them completely
        if ("s2_c007".equals(msgIdString) || "s2_c051".equals(msgIdString))
            return null;

        int secState = 0;
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
        boolean showError = false;

        if ((secState & 0x100) == 0x100 && !(showWarn || showError))
            return null;

        String[] retData = new String[3];
        retData[0] = null;
        retData[1] = msg;
        retData[2] = null;
        if (index == -1) {
             return retData;
        }
        int msgId = this.getResources().getIdentifier(msgIdString, "string", this.getPackageName());
        if (msgId == 0) {
            return retData;
        }
        retData[0] = ZrtpMessages.getCommonMessage(this, msgId);
        retData[1] = msgIdString + ": " + getString(msgId);  // prepend id-code to message from resource that maybe translated 
        msgId = this.getResources().getIdentifier(msgIdString + "_explanation", "string", this.getPackageName());
        if (msgId != 0)
            retData[2] = getString(msgId);
        return retData;
    }

    private void setSecurityStateField(CTCall call, TiviPhoneService.CT_cb_msg msg) {
        additionalInfo.setVisibility(View.INVISIBLE);
        if ("SECURE SDES".equals(call.bufSecureMsg.toString())) {
            call.sdesActive = true;
            secureState.setTextColor(resources.getColor(R.color.solid_yellow));
            secureState.setText(R.string.secstate_secure);
            additionalInfo.setText(R.string.to_server_only);
            additionalInfo.setVisibility(View.VISIBLE);
        }
        else if ("SECURE".equals(call.bufSecureMsg.toString())) {
            secureState.setText(R.string.secstate_secure);
        }
        else if ("ZRTP Error".equals(call.bufSecureMsg.toString())) {
            String askState = "media.audio.zrtp.sec_state";
            if (msg == TiviPhoneService.CT_cb_msg.eZRTPMsgV)
                askState = "media.video.zrtp.sec_state";
            int secState = 0;
            try {
                secState = Integer.parseInt(TiviPhoneService.getInfo(call.iEngID, call.iCallId, askState));
            } catch (NumberFormatException e) {}
            secureState.setText(R.string.zrtp_we_error);
            if ((secState & 0x100) == 0x100) {   // SDES active: tell Huston we have a problem, then show SECURITY again
                secureState.setTextColor(resources.getColor(R.color.solid_yellow));
                mHandler.sendEmptyMessageDelayed(SWITCH_SECURITY_STATE_TEXT_AUDIO, SWITCH_SECURITY_STATE_TEXT_TIME_DEFAULT);
            }
            else {
                secureState.setTextColor(resources.getColor(R.color.solid_red));
            }
        }
        else  if ("Not SECURE".equals(call.bufSecureMsg.toString())) {
            secureState.setText(R.string.secstate_not_secure);
        }
        else  if ("Looking for peer".equals(call.bufSecureMsg.toString())) {
            secureState.setText(R.string.secstate_looking);
        }
        else  if ("Going secure".equals(call.bufSecureMsg.toString())) {
            secureState.setText(R.string.secstate_going_sec);
        }
        else  if ("Not SECURE no crypto enabled".equals(call.bufSecureMsg.toString())) {
            secureState.setText(R.string.secstate_going_sec);
        }
        else {
            secureState.setText(call.bufSecureMsg.toString());
        }
    }

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
            if (iCanUseZRTP && call.iActive) {
                callingState.setVisibility(View.INVISIBLE);
                secureState.setVisibility(View.VISIBLE);

                switch (msg) {
                case eZRTPErrV:
                case eZRTPErrA:
                    if (call.zrtpWarning.getLen() > 0) {
                        showZrtpErrorWarning(getTranslatedMessage(call.zrtpWarning.toString(), call),
                                TiviPhoneService.CT_cb_msg.eZRTPErrA.ordinal(), call);
                    }
                    break;

                case eZRTPWarn:                    
                    if (call.zrtpWarning.getLen() > 0) {
                        showZrtpErrorWarning(getTranslatedMessage(call.zrtpWarning.toString(), call),
                                TiviPhoneService.CT_cb_msg.eZRTPWarn.ordinal(), call);
                    }
                    break;

                case eZRTP_sas:
                    sasText.setText(call.bufSAS.toString());
                    sasText.setVisibility(View.VISIBLE);
                    secureState.setTextColor(Color.WHITE);
                    
                    if (!call.iShowVerifySas) {  // Secure state to green only if SAS was verified
                        secureState.setTextColor(resources.getColor(R.color.solid_green));
                        changeSasBackground();
                    }
                    secureState.invalidate();
                    sasText.invalidate();
                    
                    call.sdesActive = false;
                    additionalInfo.setVisibility(View.INVISIBLE);
                    break;

                case eZRTP_peer:
                case eZRTP_peer_not_verifed:
                    if (call.iShowVerifySas) {
                        verifyButton.setVisibility(View.VISIBLE);
                        verifyButton.setEnabled(true);
                    }
                    if (call.zrtpPEER.getLen() > 0) {
                        zrtpPeerName.setText(call.zrtpPEER.toString());
                        handlePeerCallerName();
                    }

                case eZRTPMsgV:
                case eZRTPMsgA:
                default:
                    setSecurityStateField(call, msg);
                    secureState.invalidate();
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

    /* *********************************************************************
     * Methods and private class that handle call state changes
     * ******************************************************************* */  

     private void checkMedia(CTCall call) {

        // If video currently active check if we need to switch off video
        if (bIsVideo) {
            if (!call.bIsVideoActive) {
                switchVideo(call, false);
                return;
            }
            return;
        }
        
        // If video is currently not active and we got a new video media then
        // show video overlay with buttons, user must accept the video stream
        if (!bIsVideo && call.bIsVideoActive) {
            if (call.iShowVerifySas) {
                return;
            }
            activateVideo();
            bIsVideo = true;
            return;
        }
        return;
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

            switch (msg) {
            case eStartCall:
                updateProximitySensorMode(true); 
                answerCall(false);
                break;

            case eIncomCall:
                if (call != TiviPhoneService.calls.selectedCall && TiviPhoneService.calls.getCallCnt() > 1) {
                    TiviPhoneService.doCmd("*r" + call.iCallId); // switch on secondary ring tone
                    Intent intent = new Intent(CallManager.CALL_MGR_START);
                    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.setType("text/plain");
                    startActivity(intent);
                }
                break;

            case eRinging:
                setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);  
                break;

            case eEndCall:
                // Handled in monitoring thread to delay end-call handling by a few seconds.
                break;

            case eNewMedia:
                checkMedia(call);
                break;

            case eCalling:
                updateProximitySensorMode(true); 
                setCallNumberField(call.bufPeer.toString());
                sasText.setVisibility(View.INVISIBLE);      // in case this is a second/added call this clears old data
                zrtpPeerName.setVisibility(View.INVISIBLE);
                secureState.setVisibility(View.INVISIBLE);
                verifyButton.setVisibility(View.INVISIBLE);
                callingState.setVisibility(View.VISIBLE);
                break;

            default:
                break;
            }
            // No need to update the view if this is not the selected call
            if (call != TiviPhoneService.calls.selectedCall)
                return;

            if ((!call.iActive || call.iEnded != 0)) {
                callingState.setText(call.bufMsg.toString());
                callingState.invalidate();
            }
            if (call.iActive) {
                enableFunctionButtons(true); // enable the functions now (call manager, dial pad, video, contacts)
            }
            // Get the SIP display name if not already done
            String s = call.getNameFromAB();
            if (s != null) {
                sipCallerName.setText(s);
                if (s.equals(call.bufPeer.toString())) {
                    callNumber.setText(null);
                }
            }
            Utilities.setCallerImage(call, callerImage);
            if (call.secExceptionMsg != null) {
                Toast.makeText(TCallWindow.this, call.secExceptionMsg, Toast.LENGTH_LONG).show();
                call.secExceptionMsg = null;
            }
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


    private int changedState;
    /**
     * Device state change listener.
     * 
     * Currently handles wired head set, later we add docking, bluetooth handling.
     */
    public void deviceStateChange(int state) {
        changedState = state;
        runOnUiThread(new Runnable() {
            public void run() {
                switch (changedState) {

                    case TiviPhoneService.EVENT_WIRED_HEADSET_PLUG:
                        // inCall dialer manages speaker state if it is visible, thus don't restore but switch on
                        // without storing state
                        if (!phoneService.isHeadsetPlugged()) {
                            if (inCallDialer != null && inCallDialer.isVisible()) {
                                switchSpeaker(true, false);
                                return;
                            }
                            // if the state is "not connected", restore the speaker state.
                            Utilities.restoreSpeakerMode(getBaseContext());
                            speakerButton.setPressed(Utilities.isSpeakerOn(getBaseContext()));
                            updateProximitySensorMode(true);
                        }
                        else {
                            // if the state is "connected", force the speaker off without storing the state.
                            switchSpeaker(false, false);
                        }
                        break;

                    case TiviPhoneService.EVENT_HEADSET_HOOK_PRESSED:
                        answerCall(true);
                        break;

                    case TiviPhoneService.EVENT_BT_HEADSET_SCO_OFF:
                        int v = TiviPhoneService.doCmd("set.samplerate=48000");
                        if(TMActivity.SP_DEBUG) Log.d(LOG_TAG, "BT off event - set_rate 48000: " + v);
                        headsetBtButton.setPressed(false);
                        if(!phoneService.hasBtHeadSet()) {      // check for lost headset/connection
                            headsetBtButton.setVisibility(View.GONE);
                        }
                        break;

                    case TiviPhoneService.EVENT_BT_HEADSET_ADDED:
                        headsetBtButton.setVisibility(View.VISIBLE);
                        break;

                    case TiviPhoneService.EVENT_BT_HEADSET_SCO_ON:
                    case TiviPhoneService.EVENT_BT_HEADSET_REMOVED:
                    default:
                        break;
                }
            }
        });
    }

    /**
     * Called by onResume to restore the UI display according to the selected call's state.
     * 
     * The selected call may have changed while this Activity was paused, for example 
     * by CallManager.
     */
    private void restoreCallInfoState() {

        CTCall call = TiviPhoneService.calls.selectedCall;

        if (call == null)
            return;

        // On active call and ZRTP enabled: reorganize visible fields and set new ZRTP information
        if (iCanUseZRTP && call.iActive) {
            callingState.setVisibility(View.INVISIBLE);
            secureState.setVisibility(View.VISIBLE);

            TiviPhoneService.CT_cb_msg msg = bIsVideo ? TiviPhoneService.CT_cb_msg.eZRTPMsgV : TiviPhoneService.CT_cb_msg.eZRTPMsgA;
            setSecurityStateField(call, msg);
            if (!call.iShowVerifySas) {
                secureState.setTextColor(resources.getColor(R.color.solid_green));
            }
            secureState.invalidate();

            if (call.bufSAS.getLen() > 0) {
                sasText.setText(call.bufSAS.toString());
                sasText.setVisibility(View.VISIBLE);
                sasText.invalidate();

                if (call.iShowVerifySas) {
                    verifyButton.setVisibility(View.VISIBLE);
                    verifyButton.setEnabled(true);
                }
                else {
                    verifyButton.setVisibility(View.INVISIBLE);
                    verifyButton.setEnabled(false);
                    changeSasBackground();
                }
                if (call.zrtpPEER.getLen() > 0) {
                    zrtpPeerName.setText(call.zrtpPEER.toString());
                    handlePeerCallerName();
                }
                else {
                    zrtpPeerName.setEnabled(false);
                    zrtpPeerName.setVisibility(View.INVISIBLE);                    
                }
            }
            else {
                sasText.setVisibility(View.INVISIBLE);
                zrtpPeerName.setVisibility(View.INVISIBLE);
            }
        }
        if (!call.iActive) {
            // Security/ZRTP fields are invisible during SIP call setup
            secureState.setVisibility(View.INVISIBLE);
            sasText.setVisibility(View.INVISIBLE);
            verifyButton.setVisibility(View.INVISIBLE);
            zrtpPeerName.setVisibility(View.INVISIBLE);

            callingState.setVisibility(View.VISIBLE);
            callingState.setText(call.bufMsg.toString());
            enableFunctionButtons(false);
        }
        else {
            callDuration.setText(getDur(0));
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
            enableFunctionButtons(true);
            updateProximitySensorMode(true);
        }

        setCallNumberField(call.bufPeer.toString());

        String s = call.getNameFromAB();
        if (s != null) {
            sipCallerName.setText(s);
            if (s.equals(call.bufPeer.toString())) {
                callNumber.setText(null);
            }
        }
        Utilities.setCallerImage(call, callerImage);
        setAnswerEndCallButton(call);
        setToggleButtons();
        if (bIsVideo) {
            activateVideo();
        }
    }
    
    /**
     * Switch the call handling buttons.
     * 
     * @param call the call information.
     */
    private void setAnswerEndCallButton(CTCall call) {
        if (call != null && call.mustShowAnswerBT()) {
            dismissCall.setVisibility(View.VISIBLE);
            dismissCall.setEnabled(true);

            answerCall.setVisibility(View.VISIBLE);
            answerCall.setEnabled(true);

            hangupCall.setVisibility(View.INVISIBLE);
            hangupCall.setEnabled(false);

        }
        else {
            dismissCall.setVisibility(View.INVISIBLE);
            dismissCall.setEnabled(false);

            answerCall.setVisibility(View.INVISIBLE);
            answerCall.setEnabled(false);

            hangupCall.setVisibility(View.VISIBLE);
            hangupCall.setEnabled(true);
        }        
    }
    
    /**
     * Check status of some toggle states and set their buttons to correct state.
     * 
     * Current toggle buttons/states are: mute and speaker.
     */
    private void setToggleButtons() {
        headsetBtButton.setPressed(phoneService.btHeadsetScoActive());
        muteButton.setPressed(isMuted);
        speakerButton.setPressed(Utilities.isSpeakerOn(getBaseContext()));
    }

    private void enableFunctionButtons(boolean enable) {
        if (!enable) {
            showInCallDialer.setVisibility(View.INVISIBLE);
            addNewCall.setVisibility(View.INVISIBLE);
            startVideoCall.setVisibility(View.INVISIBLE);
            startCallManager.setVisibility(View.INVISIBLE);

        }
        else {
            showInCallDialer.setVisibility(View.VISIBLE);
            addNewCall.setVisibility(View.VISIBLE);
            startVideoCall.setVisibility(View.VISIBLE);
            startCallManager.setVisibility(View.VISIBLE);
            showInCallDialer.setEnabled(enable);
            addNewCall.setEnabled(enable);
            startVideoCall.setEnabled(enable);
            startCallManager.setEnabled(enable);
        }
    }


    private void setCallNumberField(String number) {
        CTFlags f = new CTFlags();
        if (f.getNumberInfo(number) >= 0) {
            countryFlag.setImageResource(f.ret.iResID);
        }
        String formattedNumber = CTFlags.formatNumber(number);

        callNumber.setText(formattedNumber);
  
        
    }

    /* *********************************************************************
     * Activity lifecycle methods, menu handling
     * ******************************************************************* */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.activity_call_screen, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {

            case R.id.call_menu_info:
                showInCallInfo();
                break;
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Utilities.setCallerImage(TiviPhoneService.calls.selectedCall, callerImage);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (phoneService == null)
           phoneService = TiviPhoneService.mc;

        iForeground = 1;
        restoreCallInfoState();
    }

    @Override
    protected void onPause() {
        super.onPause();

        iForeground = 0;
        if (bIsVideo)
            deactivateVideo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (phoneIsBound) {
            phoneService.removeStateChangeListener(this);
            phoneService.removeDeviceChangeListener(this);
        }
        if (toneGenerator != null)
            toneGenerator.release();
        mSensorManager.unregisterListener(this);
        Utilities.restoreSpeakerMode(getBaseContext());
        updateProximitySensorMode(false);
        doUnbindService();
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY)
            return;

        isNear = false;

        switch (event.accuracy) {
        case SensorManager.SENSOR_STATUS_UNRELIABLE:
        case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
            if (event.values[0] < event.sensor.getMaximumRange()) {
                isNear = true;
            }
            break;
        case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
        case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
            if (event.values[0] < 5.0f) {
                isNear = true;
            }
            break;
        }
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        if (bIsVideo) {
            if (isNear) {
                deactivateVideo();
            }
            else {
                activateVideo();
            }
            // This only works if this is a system application signed with special keys.
            // powerManager.goToSleep(SystemClock.uptimeMillis());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        resources = getResources();        
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // This is a phone call screen, thus perform some specific handling
        int wflags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        wflags |= WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;
        wflags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
        wflags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

        getWindow().addFlags(wflags);

        setupActionBar();
        setContentView(R.layout.activity_call_screen);

        // Get all necessary buttons and text fields. The code uses them everywhere.
        callNumber = (TextView)findViewById(R.id.CallerNumber);
        callDuration = (TextView)findViewById(R.id.CallDuration);
        callingState = (TextView)findViewById(R.id.CallingState);
        sipCallerName = (TextView)findViewById(R.id.CallerName);
        antennaInfo = (TextView)findViewById(R.id.AntennaInfo);
        
        showInCallDialer = (ImageButton)findViewById(R.id.DialButton);
        addNewCall = (ImageButton)findViewById(R.id.AddCallButton);
        startVideoCall = (ImageButton)findViewById(R.id.VideoButton);
        startCallManager = (Button)findViewById(R.id.CallMngrButton);

        sasText = (TextView)findViewById(R.id.SAS);
        secureState = (TextView)findViewById(R.id.SecureState);
        dismissCall = (Button)findViewById(R.id.DismissCall);
        hangupCall = (Button)findViewById(R.id.HangupCall);
        answerCall = (Button)findViewById(R.id.AnswerCall);
        verifyButton = (Button)findViewById(R.id.VerifyButton);
        verifyButton.setEnabled(false);
        antennaButton = (ImageButton)findViewById(R.id.Antenna);
        countryFlag = (ImageView)findViewById(R.id.CallFlagField);
        callerImage = (ImageView)findViewById(R.id.CallerImage);
        additionalInfo = (TextView) findViewById(R.id.AdditionalInfo);

        zrtpPeerName = (TextView)findViewById(R.id.ZrtpPeerName);
        zrtpPeerName.setEnabled(false);
        zrtpPeerName.setVisibility(View.INVISIBLE);

        
        // Prepare setup for video overlay. 
        // First inflate video overlay layout, get the views inside it
        rootLayout = (RelativeLayout)findViewById(R.id.CallScreenLayout);
        videoOverlay = getLayoutInflater().inflate(R.layout.video_overlay, null);
        videoOverlayView = (RelativeLayout)videoOverlay.findViewById(R.id.VideoOverlayLayout);
        previewSurface = (SurfaceView)videoOverlay.findViewById(R.id.VideoSurfacePreview);
        
        // get buttons inside the Video overlay
        videoMuteButton = (TextView)videoOverlay.findViewById(R.id.VideoMuteCamera);
        videoAccept = (TextView)videoOverlay.findViewById(R.id.VideoAccept);
        videoDecline = (TextView)videoOverlay.findViewById(R.id.VideoDecline);
        videoAudioMute = (ImageButton)videoOverlay.findViewById(R.id.VideoAudioMute);
        videoSwitchCamera = (ImageButton)videoOverlay.findViewById(R.id.VideoSwitchCamera);
        videoEndCall = (TextView)videoOverlay.findViewById(R.id.VideoEndCall);
        
        // handles camera and video stuff.
        videoScreen = new CallScreen(videoOverlayView.getContext(), previewSurface);

        RelativeLayout.LayoutParams rlParam = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        rlParam.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        rlParam.addRule(RelativeLayout.ABOVE, R.id.VideoDivider);
        rlParam.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        videoScreen.setLayoutParams(rlParam);
        videoOverlayView.addView(videoScreen);

        // catch clicks on video overlay here. Avoids that they fall through to call
        // screen and trigger unwanted actions.
        videoOverlayView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            }
        });
        
        // Specific handling for mute and speaker image buttons: this makes them to behave
        // like toggle buttons. Real Android toggle buttons do not support images. 
        muteButton = (ImageButton)findViewById(R.id.MuteButton);
        speakerButton = (ImageButton)findViewById(R.id.SpeakerButton);
        headsetBtButton = (ImageButton)findViewById(R.id.HeadsetBtButton);
        setTouchHandlers();

        iCanUseZRTP = Utilities.checkZRTP();

        // Proximity sensor to switch off video
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Register here, not on onStop or onPause, deregister on onDestroy. Otherwise Video control
        // does not work correctly.
        mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);

        /*
         * This is a setting to enable this for earlier Android versions, this includes
         * the 3 commented code lines. We may enhance this after Gingerbread support is gone.
         */
        int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
//        if ((pm.getSupportedWakeLockFlags() & PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) != 0x0) {
            /** To see if the wake-up is really used refer to {@link #proximitySensorModeEnabled()} */
            mProximityWakeLock =
                    powerManager.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "silentphone_proximity_off_wake");
//        }

        doBindService();        // Bind the TiviPhoneService, only locally
    }
    
    // Called after phone service was bound - actually part of onCreate that needs the phone service
    private void serviceBound() {

        // On outgoing call, we may not yet have a selected call
        CTCall call = TiviPhoneService.calls.selectedCall;

        Bundle bundle = getIntent().getExtras();

        if (bundle != null) {
            int callCnt = TiviPhoneService.calls.getCallCnt();
            iIsIncoming = bundle.getInt("incoming");

            if (iIsIncoming == TiviPhoneService.CALL_TYPE_RESTARTED) {
                iIsIncoming = iLastiIsIncoming;
                // may happen if user selected to add another call but just returned to call window
                // without placing a new call.
                if (call != null && call.iIsOnHold) {
                    call.iIsOnHold = false;
                    TiviPhoneService.doCmd("*u" + call.iCallId);
                }
            }
            // Reset mute and speaker only if this is the first /only incoming or outgoing call
            else if ((iIsIncoming == TiviPhoneService.CALL_TYPE_INCOMING && callCnt == 1)
                    || (iIsIncoming == TiviPhoneService.CALL_TYPE_OUTGOING && callCnt < 1)) {
                muteButton.setPressed(false);
                doCmd(":mute 0");           // un-mute if we are not restarting during a call
                isMuted = false;
                // Disable buttons: call manager, dial-pad, video, Contact until call becomes active
                enableFunctionButtons(false);
            }
            if (iIsIncoming != TiviPhoneService.CALL_TYPE_OUTGOING) {
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            }
        }
        iLastiIsIncoming = iIsIncoming;

        if (phoneService.hasBtHeadSet())
            headsetBtButton.setVisibility(View.VISIBLE);
        speakerButton.setPressed(Utilities.isSpeakerOn(getBaseContext()));
        updateProximitySensorMode(true);

        // Because phone service starts this activity it may happen that call is null,
        // depending on the thread scheduling. In any case try to get some information to
        // display to user. 
        // In case of a restart we always have an active call.
        String caller = null;
        if (call != null) {
            if (call.bufDialed.getLen() > 0)
                caller = call.bufDialed.toString();
            else if (call.bufPeer.getLen() > 0)
                caller = call.bufPeer.toString();
        }

        if (caller == null) {
            caller = iIsIncoming == TiviPhoneService.CALL_TYPE_OUTGOING ? TMActivity.lastDialedNumber.toString() : doX(6);
        }
        caller = Utilities.removeSipParts(caller);

        // These two calls set UI in case of outgoing calls. May be overwritten during
        // onResume for incoming calls.
        setCallNumberField(caller);
        setAnswerEndCallButton(call);

        /* *********************************************************************
         * Monitoring thread: updates the call duration time and checks for some
         * other periodic activities to happen. It also controls the delay when
         * to close the call window after a call ended.
         * ******************************************************************* */
        monitoring = new Thread(new Runnable() {
            public void run() {

                int iCnt = 0;

                boolean bSleepModeIsFast = false;
                boolean bActivePrev = false;

                videoScreen.clear();

                while (true) {
                                        
                    bSleepModeIsFast = bActivePrev && iForeground == 1 && bIsVideo;
                    Utilities.Sleep(bSleepModeIsFast ? 12 : 1000);

                    // selectedCall is the "active" call from call window point of view
                    CTCall call = TiviPhoneService.calls.selectedCall;

                    if (call != null && call.iUpdated != 0) {
                        call.iUpdated = 0;
                    }
                    boolean bActive = call != null
                            && (CTCalls.isCallType(call, CTCalls.ePrivateCall) || CTCalls.isCallType(call, CTCalls.eConfCall));

                    iCnt++;
                    if (iHideAfter > 0) {
                        iHideAfter--;
                    }

                    if (call != null && iHideAfter == -1) {
                        if (!bIsVideo && bActive && (!bSleepModeIsFast || (iCnt >= 83))) {
                            iCnt = 0;
                            setCallState(getDur(0));
                        }
                        else if (!bActive && call.iEnded != 0) {
                            endCall(false, TiviPhoneService.calls.selectedCall);
                        }
                        else if (bActive) {
                            videoScreen.check(bActive);
                        }
                    }
                    bActivePrev = bActive;
                    
                    // Check if the selected call is the only active call and is still in conference.
                    // May happen if user ends some other conf calls and only the selected call remains.
                    // in this case remove it from conference.
                    if (bActive && TiviPhoneService.calls.getCallCnt() == 1 && call.iIsInConferece) {
                        call.iIsInConferece = false;
                        TiviPhoneService.doCmd("*-" + call.iCallId);
                    }

                    // hideAfter goes to zero if the selected ends. endCall() sets hideAfter to some
                    // positive value and this monitoring loop conts down the value to delay removal
                    // of call window. Just check if we have some other call that we may take. If only
                    // one call remains take it, otherwise just re-trigger call manager.
                    if (iHideAfter == 0) {
                        /*
                         *  If no more calls to handle - finish Call window. Otherwise get an active
                         *  call, set it as selectedCall, set un-hold mode if necessary and let 
                         *  call manager handle it.
                         */
                        int numCalls = TiviPhoneService.calls.getCallCnt();
                        if (numCalls < 1) {
                            break;
                        }
                        else {
                            CTCall nextCall = TiviPhoneService.calls.getLastCall();
                            if (nextCall != null) {
                                TiviPhoneService.calls.selectedCall = nextCall;
                                if (nextCall.iIsOnHold) {
                                    TiviPhoneService.doCmd("*u" + nextCall.iCallId);
                                    nextCall.iIsOnHold = false;
                                }
                                iHideAfter = -1;
                                
                                Intent intent;
                                // Just one call left, handle it here, terminate call manager if running
                                if (numCalls == 1) {
                                    if (nextCall.iIsInConferece) {   // no need to have the last call in a conference
                                        nextCall.iIsInConferece = false;
                                        TiviPhoneService.doCmd("*-" + nextCall.iCallId);
                                    }
                                    intent = new Intent(CallManager.CALL_MGR_STOP);
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            restoreCallInfoState();
                                        }
                                    });
                                }
                                // More than one call left - start call manager if necessary
                                else {
                                    intent = new Intent(CallManager.CALL_MGR_START);
                                }
                                // Set Audio to IN_CALL because we just took over another call
                                // endCall processing sets audio to NORMAL
                                am(getBaseContext(), true);
                                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                intent.setType("text/plain");
                                startActivity(intent);
                            }
                            else {
                                break;
                            }
                        }
                    }
                }
                phoneService.bluetoothHeadset(false);
                TCallWindow.this.finish();
            }
        });
        monitoring.start();
    }

    private void setupActionBar() {
        actionBar = getSupportActionBar();
        if (actionBar == null)
            return;
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setHomeButtonEnabled(false);

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }

    @Override
    public boolean dispatchKeyEvent (KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        CTCall call = TiviPhoneService.calls.selectedCall;

        if (call == null)
            return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN && !call.iActive) {
                    phoneService.stopRT();
                    return true;
                }
                return false;

            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (call.mustShowAnswerBT())
                        answerCall(true);
                    else
                        endCall(true, call);
                }
                return true;

            default:
                return false;
        }
    }

    /*
     * Store some data here to enable access from UI thread
     */
    private String lastMsg = "";

    private void setCallState(String s) {
        lastMsg = s;
        runOnUiThread(new Runnable() {
            public void run() {
                setAntennaInfo();
                callDuration.setText(TCallWindow.this.lastMsg);
                callDuration.invalidate();
            }
        });
    }

    /*
     * Simplify handling of speaker switching
     */
    private void switchSpeaker(boolean onOff, boolean storeState) {
        if (phoneService.btHeadsetScoActive())              // Don't switch to speaker if BT is active
            return;
        Utilities.turnOnSpeaker(getBaseContext(), onOff, storeState);
        speakerButton.setPressed(onOff);
        updateProximitySensorMode(true);
    }

    /*
     * Simplify handling of Bluetooth headset switching
     */
    private void switchBtHeadset(boolean on) {

        if (!phoneService.hasBtHeadSet())
            return;

        if (on) {
            switchSpeaker(false, false);            // Switch off speaker, don't store this state wo we can restore
            int v = TiviPhoneService.doCmd("set.samplerate=16000");
            if(TMActivity.SP_DEBUG) Log.d(LOG_TAG, "BT on - set_rate 16000: " + v);
            phoneService.bluetoothHeadset(true);
        }
        else {
            phoneService.bluetoothHeadset(false);
            int v = TiviPhoneService.doCmd("set.samplerate=48000");
            if(TMActivity.SP_DEBUG)Log.d(LOG_TAG, "BT off - set_rate 48000: " + v);
            Utilities.restoreSpeakerMode(getBaseContext());
            speakerButton.setPressed(Utilities.isSpeakerOn(getBaseContext()));
        }
        headsetBtButton.setPressed(on);
        updateProximitySensorMode(true);
    }

    /* *********************************************************************
     * Methods to setup and handle call window buttons. Some buttons need
     * specific touch handler.
     * ******************************************************************* */

    private void setTouchHandlers() {
        muteButton.setOnTouchListener(new MuteButtons());
        videoAudioMute.setOnTouchListener(new MuteButtons());

        headsetBtButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    return true;
                if (event.getAction() != MotionEvent.ACTION_UP)
                    return false;
                switchBtHeadset(!phoneService.btHeadsetScoActive());
                return true;
            }
        });

        speakerButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    return true;
                if (event.getAction() != MotionEvent.ACTION_UP)
                    return false;

                boolean speakerState = !Utilities.isSpeakerOn(getBaseContext());
                switchSpeaker(speakerState, true);
                return true;
            }
        });
        videoMuteButton.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN)
                    return true;
                if (event.getAction() != MotionEvent.ACTION_UP)
                    return false;
                if (videoMute) {
                    videoOverlayView.removeView(previewSurface);
                    videoOverlayView.addView(previewSurface);
                    videoMute = false;
                    videoScreen.startStopCamera();
                }
                else {
                    CallScreen.stopCamera();
                    videoMute = true;
                }
                setVideoToggleButtons();
                return true;
            }
        });
    }

    /**
     * Called when the user answers the call.
     */
    public void answerCall(View view) {
        answerCall(true);
    }
    
    /**
     * Add call to existing phone session.
     * 
     * The method sets the current (selected) call to hold, starts the start screen (TMActivity)
     * activity to display the dial pad and other tools and then finishes this activity.
     * 
     * TMActivity sets up the new call and starts this TCallWindow again. This action sequence keeps
     * the activities in the right order. TiviPhoneService sets the new call as selected call, thus
     * the new TCallWindow displays the correct data. 
     * 
     * @param view The button view
     */
    public void addCall(View view) {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        call.iIsOnHold = true;
        TiviPhoneService.doCmd("*h" + call.iCallId);

        Intent i = new Intent();
        i.setClass(this, TMActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);

        finish();
    }

    /**
     * Switch on or off verbose Antenna display.
     * 
     * @param view
     */
    public void antennaButton(View view) {
        showAntennaInfo = !showAntennaInfo;
        
        if (showAntennaInfo) {
            antennaInfo.setVisibility(View.VISIBLE);
            antennaInfo.setText(R.string.prepare_antenna);
        }
        else
            antennaInfo.setVisibility(View.INVISIBLE);
    }

    /** 
     * Called when the user dismisses/ends the call.
     */
    public void hangupCall(View view) {
       endCall(true, TiviPhoneService.calls.selectedCall);
    }

    public void onCallMgr(View view) {
        Intent intent = new Intent(CallManager.CALL_MGR_START);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setType("text/plain");
        startActivity(intent);        
    }

    /**
     * Display crypto information screen if user clicks on secure state text field.
     * 
     * @param view the current view.
     */
    public void secStateTextClick(View view) {
        Bundle args = readCryptoInfo();
        if (args == null)
            return;

        FragmentManager fragmentManager = getSupportFragmentManager();
        CryptoDialog cryptoScreen = CryptoDialog.newInstance(args);
        cryptoScreen.show(fragmentManager, "crypto_dialog");        
    }
    
    /**
     * Show SAS explanation if user clicks on SAS text field.
     * 
     * @param view the current view.
     */
    public void sasTextClick(View view) {
        showInputInfo(R.string.sas_explanation);
    }

    private void changeSasBackground() {
        int padRight = sasText.getPaddingRight();
        int padLeft = sasText.getPaddingLeft();
        sasText.setBackgroundResource(R.drawable.rounded_gray);
        sasText.setPadding(padLeft, 0, padRight, 0);
    }

    /* *********************************************************************
     * Small private class to handle mute toggle buttons
     * ******************************************************************* */

    private class MuteButtons implements OnTouchListener {
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN)
                return true;
            if (event.getAction() != MotionEvent.ACTION_UP)
                return false;
            ImageButton btn = (ImageButton)view;
            if (!btn.isPressed()) {
                btn.setPressed(true);
                isMuted = true;
                doCmd(":mute 1");       // send mute command to phone service
            }
            else {
                btn.setPressed(false);
                isMuted = false;
                doCmd(":mute 0");       // send un-mute command to phone service
            }
            return true;
        }
    }

    /**
     * Comment and code taken from Phone app - will use some of the commented
     * code/features sometime later when looking BT, speaker, etc 
     * 
     * Updates the wake lock used to control proximity sensor behavior,
     * based on the current state of the phone.  This method is called
     * from the CallNotifier on any phone state change.
     *
     * On devices that have a proximity sensor, to avoid false touches
     * during a call, we hold a PROXIMITY_SCREEN_OFF_WAKE_LOCK wake lock
     * whenever the phone is off hook.  (When held, that wake lock causes
     * the screen to turn off automatically when the sensor detects an
     * object close to the screen.)
     *
     * This method is a no-op for devices that don't have a proximity
     * sensor.
     *
     * Note this method doesn't care if the InCallScreen is the foreground
     * activity or not.  That's because we want the proximity sensor to be
     * enabled any time the phone is in use, to avoid false cheek events
     * for whatever app you happen to be running.
     *
     * Proximity wake lock will *not* be held if any one of the
     * conditions is true while on a call:
     * 1) If the audio is routed via Bluetooth
     * 2) If a wired headset is connected
     * 3) if the speaker is ON
     * 4) If the slider is open(i.e. the hardkeyboard is *not* hidden)
     *
     */
    private void updateProximitySensorMode(boolean enable) {

        if (proximitySensorModeEnabled()) {
            synchronized (mProximityWakeLock) {
                // turn proximity sensor off and turn screen on immediately if
                // we are using a headset, the keyboard is open, or the device
                // is being held in a horizontal position.
//                boolean screenOnImmediately = (isHeadsetPlugged()
//                            || PhoneUtils.isSpeakerOn(this)
//                            || ((mBtHandsfree != null) && mBtHandsfree.isAudioOn())
//                            || mIsHardKeyboardOpen);
                // We do not keep the screen off when we are horizontal, but we do not force it
                // on when we become horizontal until the proximity sensor goes negative.
//                boolean horizontal = (mOrientation == AccelerometerListener.ORIENTATION_HORIZONTAL);

                boolean keepScreenOn = Utilities.isSpeakerOn(getBaseContext()) || phoneService.isHeadsetPlugged() ||
                        phoneService.btHeadsetScoActive();
                if (enable && !keepScreenOn) {
                    // Phone is in use!  Arrange for the screen to turn off
                    // automatically when the sensor detects a close object.
                    if (!mProximityWakeLock.isHeld()) {
                        mProximityWakeLock.acquire();
                    } else {
                        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "updateProximitySensorMode: lock already held.");
                    }
                } else {
                    if (mProximityWakeLock.isHeld()) {
                        // Wait until user has moved the phone away from his head if we are
                        // releasing due to the phone call ending.
                        // Qtherwise, turn screen on immediately - not available in API 10 
//                        int flags =
//                            (screenOnImmediately ? 0 : PowerManager.WAIT_FOR_PROXIMITY_NEGATIVE);
//                        mProximityWakeLock.release(flags);
                        mProximityWakeLock.release();
                    } 
                }
            }
        }
    }

    /**
     * Check if this device uses the ProximitySensorWakeLock.
     *
     * @return true if this device supports the "proximity sensor auto-lock" feature while
     *              in-call (see updateProximitySensorMode()).
     */
    private boolean proximitySensorModeEnabled() {
        return (mProximityWakeLock != null && DeviceHandling.useProximityWakeup());
    }

    /* *********************************************************************
     * Methods to handle the video switching 
     * ******************************************************************* */
    private boolean videoMute = false;
    private boolean frontCamera = true;
    
    private void activateVideo() {
        View parent = (View)videoOverlay.getParent();
        if (parent == rootLayout) {
            return;
        }
        rootLayout.addView(videoOverlay);
        if (!Utilities.isSpeakerOn(getBaseContext()) && !phoneService.isHeadsetPlugged()) {
            switchSpeaker(true, false);
        }
        if (videoScreen.getNumCameras() <= 1)
            frontCamera = false;
        if (!videoAccept.isEnabled()) {     // If video accept button is _not_ enabled then user accepted the video
            videoScreen.setFrontCamera(frontCamera);
            videoScreen.startStopCamera();
            videoMute = false;
        }
    }

    private void deactivateVideo() {
        CallScreen.stopCamera();
        if (!phoneService.btHeadsetScoActive()) {              // Don't switch to speaker if BT is active
            Utilities.restoreSpeakerMode(getBaseContext());
            speakerButton.setPressed(Utilities.isSpeakerOn(getBaseContext()));
        }
        updateProximitySensorMode(true);
        rootLayout.removeView(videoOverlay);
    }

    /**
     * Switch video connection on or off depending on user actions.
     * 
     * This method gets called only if
     * - the user touches the video button (on = true)
     * - the user declines the video offer (on = false)
     * - the user switches back to audio only (on = false)
     * - or if video is active and a call has no video channel anymore (on = false)
     * 
     * 
     * @param call     the call that controls the video stream
     * @param on       if true switch video on, switch off otherwise
     */
    synchronized public void switchVideo(CTCall call, boolean on) {
        // without synchronized can crash when pressing decline if checkMedia call happens very fast.
        if (on) {
            if (bIsVideo)
                return;

            if (call.iShowVerifySas)    // Switch on video only if the user(s) verified the SAS: verification button not shown
                return;
            switchButtonsVideoActive();
            activateVideo();
            doCmd("*C" + call.iCallId); // send re-invite for video on
        }
        else {
            if (!bIsVideo)
                return;
            doCmd("*c" + call.iCallId); // send re-invite for video off
            deactivateVideo();
            switchButtonsVideoInactive();
        }
        bIsVideo = on;
        return;
    }

    /**
     * Triggered by Video button in call screen.
     * 
     * @param view Button that triggered this action.
     */
    public void videoButton(View view) {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        switchVideo(call, true);
    }

    /**
     * Triggered by End Call button in video overlay screen.
     * 
     * @param view Button that triggered this action.
     */
    public void videoEndCallButton(View view) {
        endCall(true, TiviPhoneService.calls.selectedCall);
    }

    /**
     * Triggered by Decline button in video overlay screen.
     * 
     * @param view Button that triggered this action.
     */
    public void videoDeclineButton(View view) {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        switchVideo(call, false);
    }

    /**
     * Check status of some video toggle states and set their buttons to correct state.
     * 
     * Current toggle buttons/states are: video mute and audio mute.
     */
    private void setVideoToggleButtons() {
        videoAudioMute.setPressed(isMuted);
        videoMuteButton.setPressed(videoMute);
    }

    /**
     * Change buttons to video not accepted state
     */
    private void switchButtonsVideoInactive() {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        videoAccept.setEnabled(true);
        videoAccept.setVisibility(View.VISIBLE);
        videoDecline.setEnabled(true);
        videoDecline.setVisibility(View.VISIBLE);
        call.videoAccepted = false;

        videoAudioMute.setEnabled(false);
        videoAudioMute.setVisibility(View.INVISIBLE);
        videoSwitchCamera.setEnabled(false);
        videoSwitchCamera.setVisibility(View.INVISIBLE);
        videoEndCall.setEnabled(false);
        videoEndCall.setVisibility(View.INVISIBLE);
    }

    /**
     * Change buttons to video accepted state.
     * 
     * Video is accepted automatically if user started video.
     */
    private void switchButtonsVideoActive() {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        videoAccept.setEnabled(false);
        videoAccept.setVisibility(View.INVISIBLE);
        videoDecline.setEnabled(false);
        videoDecline.setVisibility(View.INVISIBLE);
        call.videoAccepted = true;

        videoAudioMute.setEnabled(true);
        videoAudioMute.setVisibility(View.VISIBLE);
        videoSwitchCamera.setEnabled(true);
        videoSwitchCamera.setVisibility(View.VISIBLE);
        videoEndCall.setEnabled(true);
        videoEndCall.setVisibility(View.VISIBLE);        

        setVideoToggleButtons();
    }

    /**
     * Triggered by Accept button in video overlay screen.
     * 
     * @param view Button that triggered this action.
     */
    public void videoAcceptButton(View view) {
        // Some sort of dirty trick to get the camera working in preview surface
        // Don't know why preview is not working if camera is started some time after
        // I added the video overlay.
        videoOverlayView.removeView(previewSurface);
        videoOverlayView.addView(previewSurface);
        videoMute = false;
        switchButtonsVideoActive();
        if (videoScreen.getNumCameras() <= 1)
            frontCamera = false;
        videoScreen.setFrontCamera(frontCamera);
        videoScreen.startStopCamera();
        if (!Utilities.isSpeakerOn(getBaseContext()) && !phoneService.isHeadsetPlugged()) {
            switchSpeaker(true, false);
        }
    }

    /**
     * Triggered by AudioOnly button in video overlay screen.
     * 
     * @param view Button that triggered this action.
     */
    public void videoAudioOnlyButton(View view) {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        switchVideo(call, false);
    }

    /**
     * Triggered by switch camera button in video overlay screen.
     * 
     * @param view Button that triggered this action.
     */
    public void videoSwitchCameraButton(View view) {
        if (videoScreen.getNumCameras() <= 1) 
            return;
        // Don't switch cameras if video is set to mute
        if (videoMute)
            return;
        
        CallScreen.stopCamera();
        frontCamera = !frontCamera;
        videoScreen.setFrontCamera(frontCamera);
        videoOverlayView.removeView(previewSurface);
        videoOverlayView.addView(previewSurface);
        videoScreen.startStopCamera();
        setVideoToggleButtons();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    

    /* *********************************************************************
     * Handling of the Verify panel display. Triggered by a click on the Verify
     * text button.
     * ******************************************************************* */

    /**
     * Display the verify SAS dialog.
     * 
     * @param view the current view.
     */
    public void verifyButton(View view) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        VerifyDialog verify = VerifyDialog.newInstance(sasText.getText().toString());
        verify.show(fragmentManager, "verify_dialog");
    }

    public void zrtpPeerClick(View view) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        VerifyDialog verify = VerifyDialog.newInstance(sasText.getText().toString());
        verify.show(fragmentManager, "verify_dialog");
    }

    /**
     * The VerifyDialog calls this method to store the peer name.
     * 
     * @param peerName The peer name that the user typed.
     */
    private void storePeerAndVerify(String peerName) {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call != null) {
            String cmd = "*z" + call.iCallId + " " + peerName;
            TiviPhoneService.doCmd(cmd);
            call.zrtpPEER.setText(peerName);
            call.iShowVerifySas = false;
            changeSasBackground();
            secureState.setTextColor(resources.getColor(R.color.solid_green));
            verifyButton.setEnabled(false);
            verifyButton.setVisibility(View.INVISIBLE);
            zrtpPeerName.setText(peerName);
            handlePeerCallerName();
        }
        setToggleButtons();
    }

    private void handlePeerCallerName() {
        String peer = zrtpPeerName.getText().toString();
        String caller = sipCallerName.getText().toString();
        
        if (peer != null && peer.equals(caller)) {
            sipCallerName.setTextColor(resources.getColor(R.color.solid_green));
            sipCallerName.setEnabled(true);
            zrtpPeerName.setEnabled(false);
            zrtpPeerName.setVisibility(View.INVISIBLE);
        }
        else {
            sipCallerName.setTextColor(0xffffffff);   // This is white
            sipCallerName.setEnabled(false);            
            zrtpPeerName.setEnabled(true);
            zrtpPeerName.setVisibility(View.VISIBLE);
        }
    }

    /**
     * The Dialog modifies button states/views and his method sets them to correct state.
     */
    private void verifyLater() {    
        setToggleButtons();
    }
    
    /**
     * Private class to render the Alert panel for the verify functions.
     * 
     * @author werner
     *
     */
    public static class VerifyDialog extends DialogFragment {
        private EditText peerName;
        
        private static String SAS_TEXT = "sas_text";

        public static VerifyDialog newInstance(String sasText) {
            VerifyDialog f = new VerifyDialog();
            
            Bundle args = new Bundle();
            args.putString(SAS_TEXT, sasText);
            f.setArguments(args);
            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();

            // Inflate and set the layout for the dialog
            // set the SAS string to compare
            // Pass null as the parent view because its going in the dialog layout
            View view = inflater.inflate(R.layout.dialog_verify, null);
            TextView sas = (TextView)view.findViewById(R.id.VerifyInfoTextSas);
            peerName = (EditText)view.findViewById(R.id.VerifyPeerName);
            sas.setText("\"" + getArguments().getString(SAS_TEXT) + "\"");
            
            CTCall call = TiviPhoneService.calls.selectedCall;
            
            // preset peer name edit field if we have some info available
            if (call != null) {
                if (call.zrtpPEER.getLen() > 0) {
                    peerName.setText(call.zrtpPEER.toString());
                }
                else {
                    peerName.setText(call.getNameFromAB());
                }
            }
            
            // Add inflated view and action buttons
            builder.setView(view)
                   .setPositiveButton(R.string.confirm_dialog, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           ((TCallWindow)getActivity()).storePeerAndVerify(peerName.getText().toString());
                           // Set peer name via phone service
                       }
                   })
                   .setNegativeButton(R.string.provision_later, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           ((TCallWindow)getActivity()).verifyLater();
                       }
                   });      
            return builder.create();
        }
    }

    /* *********************************************************************
     * Handling of the in-call dialer. Triggered by a click on the start in
     * call dialer button.
     * ******************************************************************* */

    /**
     * Play a DTMF tone for 200ms
     */
    private static int DTMF_TONE_DURATION = 200;

    /**
     * Refresh and queue a new in-call dial pad hide message.
     */
    private void resetAutoHideTimer() {
        if (!dialpadAutoHide)
            return;
        mHandler.removeMessages(IN_CALL_DIALPAD_HIDE_TIMER);
        if (inCallDialer.isVisible()) {
            mHandler.sendEmptyMessageDelayed(IN_CALL_DIALPAD_HIDE_TIMER, IN_CALL_DIALPAD_HIDE_TIME_DEFAULT);
        }
    }

    /**
     * Show the in-call dial pad dialog to send DTMF signals.
     * 
     * @param view the button's view
     */
    public void startInCallDialer(View view) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (inCallDialer == null) 
            inCallDialer = new InCallDialDialog();

        toneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.CallScreenLayout, inCallDialer);
        fragmentTransaction.commit();
        if (!phoneService.isHeadsetPlugged())
            switchSpeaker(true, false);

        if (dialpadAutoHide)
            mHandler.sendEmptyMessageDelayed(IN_CALL_DIALPAD_HIDE_TIMER, IN_CALL_DIALPAD_HIDE_TIME_DEFAULT);
    }

    /**
     * Dial pad dialog to handle in-call dialog.
     *  
     * @author werner
     *
     */
    public static class InCallDialDialog extends DialogFragment {
        /** The system calls this to get the DialogFragment's layout, regardless
            of whether it's being displayed as a dialog or an embedded fragment. */
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Inflate the layout to use as dialog or embedded fragment
            View view = inflater.inflate(R.layout.dialog_dialer, container, false);
            return view;
        }
      
        /** The system calls this only when creating the layout in a dialog. */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // The only reason you might override this method when using onCreateView() is
            // to modify any dialog characteristics. For example, the dialog includes a
            // title by default, but your custom layout might not need it. So here you can
            // remove the dialog title, but you must call the superclass to get the Dialog.
            Dialog dialog = super.onCreateDialog(savedInstanceState);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            return dialog;
        }
    }

    public void onNumberClickDialog(View view) {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        // Currently the DTMF tone duration is fixed, if variable time is required
        // than more complex handling of key press, key release is needed
        resetAutoHideTimer();
        switch (view.getId()) {
        case R.id.DialogNumber_1:
            TiviPhoneService.doCmd(":D1");
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_1, DTMF_TONE_DURATION);
            break;

        case R.id.DialogNumber_2:
            TiviPhoneService.doCmd(":D2");
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_2, DTMF_TONE_DURATION);
            break;

        case R.id.DialogNumber_3:
            TiviPhoneService.doCmd(":D3");
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_3, DTMF_TONE_DURATION);
            break;

        case R.id.DialogNumber_4:
            TiviPhoneService.doCmd(":D4");
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_4, DTMF_TONE_DURATION);
            break;

        case R.id.DialogNumber_5:
            TiviPhoneService.doCmd(":D5");
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_5, DTMF_TONE_DURATION);
            break;

        case R.id.DialogNumber_6:
            TiviPhoneService.doCmd(":D6");
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_6, DTMF_TONE_DURATION);
            break;

        case R.id.DialogNumber_7:
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_7, DTMF_TONE_DURATION);
            TiviPhoneService.doCmd(":D7");
            break;

        case R.id.DialogNumber_8:
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_8, DTMF_TONE_DURATION);
            TiviPhoneService.doCmd(":D8");
            break;

        case R.id.DialogNumber_9:
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_9, DTMF_TONE_DURATION);
            TiviPhoneService.doCmd(":D9");
            break;

        case R.id.DialogNumber_0:
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_0, DTMF_TONE_DURATION);
            TiviPhoneService.doCmd(":D0");
            break;

        case R.id.DialogNumber_hash:
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_P, DTMF_TONE_DURATION);
            TiviPhoneService.doCmd(":D#");
            break;

        case R.id.DialogNumber_star:
            Utilities.playTone(this, toneGenerator, ToneGenerator.TONE_DTMF_S, DTMF_TONE_DURATION);
            TiviPhoneService.doCmd(":D*");
            break;
        }
    }
    
    public void endCallDialog(View view) {
        endCall(true, TiviPhoneService.calls.selectedCall);
        Utilities.restoreSpeakerMode(getBaseContext());
        inCallDialer.dismiss();
        if (toneGenerator != null)
            toneGenerator.release();
        toneGenerator = null;
    }
    
    public void hideInCallDialog(View view) {
        Utilities.restoreSpeakerMode(getBaseContext());
        speakerButton.setPressed(Utilities.isSpeakerOn(getBaseContext()));
        updateProximitySensorMode(true);
        inCallDialer.dismiss();
        if (toneGenerator != null)
            toneGenerator.release();
        toneGenerator = null;
    }

    /* *********************************************************************
     * Handling of the crypto panel display. Triggered by a click on the SAS
     * text button.
     * ******************************************************************* */
    private static final String sdp_hash = "media.zrtp.sdp_hash";
    private static final String lbClient = "media.zrtp.lbClient";
    private static final String lbVersion = "media.zrtp.lbVersion";
    private static final String lbChiper = "media.zrtp.lbChiper";
    private static final String lbAuthTag = "media.zrtp.lbAuthTag";
    private static final String lbHash = "media.zrtp.lbHash";
    private static final String lbKeyExchange = "media.zrtp.lbKeyExchange";
    private static final String socket = ".sock";      //socket info, tls ciphers or udp,tcp
    private static final String lbBuildInfo = "media.zrtp.buildInfo";

    /**
     * Reads the crypto information from the Tivi engine and prepares the bundle.
     */
    private Bundle readCryptoInfo() {
        CTCall call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return null;

        Bundle args = new Bundle();

        String buildInfo = TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbBuildInfo);
        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "ZRTP build information: " + buildInfo);
//        String[] info = buildInfo.split(":");

        args.putString(sdp_hash, TiviPhoneService.getInfo(call.iEngID, call.iCallId, sdp_hash));
        args.putString(lbClient, TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbClient));
        args.putString(lbVersion, TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbVersion));
        args.putString(lbChiper, TiviPhoneService.getInfo(-1, call.iCallId, lbChiper));
        args.putString(lbAuthTag, TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbAuthTag));
        args.putString(lbHash, TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbHash));
        args.putString(lbKeyExchange, TiviPhoneService.getInfo(call.iEngID, call.iCallId, lbKeyExchange));
        args.putString(socket, TiviPhoneService.getInfo(call.iEngID, -1, socket));

        args.putInt("rs1", getSharedSecretStatus("rs1"));
        args.putInt("rs2", getSharedSecretStatus("rs2"));
        args.putInt("aux", getSharedSecretStatus("aux"));
        args.putInt("pbx", getSharedSecretStatus("pbx"));
        return args;
    }

    /**
     * Get status of shared secrets and assign color according to state.
     * 
     * @param secret which shared secret to check
     * @return the color to the status
     */
    private int getSharedSecretStatus(String secret) {
        
        String cmd = "media.zrtp." + secret;

        CTCall call = TiviPhoneService.calls.selectedCall;
        String res = TiviPhoneService.getInfo(call.iEngID, call.iCallId, cmd);
        
        if ("0".compareTo(res) == 0)
            return Color.GRAY;            // Gray
        
        if ("1".compareTo(res) == 0)
            return Color.RED;             // RED

        if ("2".compareTo(res) == 0)
            return Color.GREEN;           // GREEN

        return Color.WHITE;               // Nothing? White!
    }

    public static class CryptoDialog extends DialogFragment {

        public static CryptoDialog newInstance(Bundle args) {
            CryptoDialog f = new CryptoDialog();
            f.setArguments(args);
            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();

            Bundle args = getArguments();
            // Inflate and set the layout for the dialog
            // set the SAS string to compare
            // Pass null as the parent view because its going in the dialog layout
            View view = inflater.inflate(R.layout.dialog_crypto_info, null);
            TextView txtv = (TextView)view.findViewById(R.id.CryptoCryptoComponentsCipherInfo);
            txtv.setText(args.getString(lbChiper));
            txtv = (TextView)view.findViewById(R.id.CryptoCryptoComponentsHashInfo);
            txtv.setText(args.getString(lbHash));
            txtv = (TextView)view.findViewById(R.id.CryptoCryptoComponentsPkInfo);
            txtv.setText(args.getString(lbKeyExchange));
            txtv = (TextView)view.findViewById(R.id.CryptoCryptoComponentsSauthInfo);
            txtv.setText(args.getString(lbAuthTag));
            txtv = (TextView)view.findViewById(R.id.CryptoCryptoComponentsZhashInfo);
            txtv.setText(args.getString(sdp_hash));
            txtv = (TextView)view.findViewById(R.id.CryptoPeerClientIdInfo);
            txtv.setText(args.getString(lbClient));
            txtv = (TextView)view.findViewById(R.id.CryptoPeerClientProtoInfo);
            txtv.setText(args.getString(lbVersion));
            txtv = (TextView)view.findViewById(R.id.CryptoCryptoComponentsTlsInfo);
            txtv.setText(args.getString(socket));

            txtv = (TextView)view.findViewById(R.id.CryptoSharedSecretsRs1Info);
            txtv. setBackgroundColor(args.getInt("rs1"));
            txtv = (TextView)view.findViewById(R.id.CryptoSharedSecretsRs2Info);
            txtv. setBackgroundColor (args.getInt("rs2"));
            txtv = (TextView)view.findViewById(R.id.CryptoSharedSecretsAuxInfo);
            txtv. setBackgroundColor (args.getInt("aux"));
            txtv = (TextView)view.findViewById(R.id.CryptoSharedSecretsPbxInfo);
            txtv. setBackgroundColor (args.getInt("pbx"));

            CTCall call = TiviPhoneService.calls.selectedCall;
            txtv = (TextView)view.findViewById(R.id.CryptoPeerName);
            txtv.setText(call.zrtpPEER.toString());

            txtv = (TextView)view.findViewById(R.id.CryptoSasText);
            txtv.setText(call.bufSAS.toString());

            // if show verify SAS then SAS was not verified
            ImageView imgv = (ImageView)view.findViewById(R.id.CryptoPanelPadlock);
            if (call.iShowVerifySas)
                imgv.setImageResource(R.drawable.main_lock_locked);
            else
                imgv.setImageResource(R.drawable.main_lock_verified);

            // Add inflated view and action buttons
            builder.setView(view)
                   .setPositiveButton(R.string.close_dialog, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           ((TCallWindow)getActivity()).setToggleButtons();
                       }
                   });
            return builder.create();
        }
    }
    
    private void showInputInfo(int msgId) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(msgId);
        FragmentManager fragmentManager = getSupportFragmentManager();
        infoMsg.show(fragmentManager, "SilentPhoneCallInfo");
    }

    public static class InfoMsgDialogFragment extends DialogFragment {
        private static String MESSAGE_ID = "messageId";

        public static InfoMsgDialogFragment newInstance(int msgId) {
            InfoMsgDialogFragment f = new InfoMsgDialogFragment();

            Bundle args = new Bundle();
            args.putInt(MESSAGE_ID, msgId);
            f.setArguments(args);

            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.provisioning_info)
                .setMessage(getArguments().getInt(MESSAGE_ID))
                .setPositiveButton(R.string.close_dialog, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                       }
                   });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    private void showInCallInfo() {
        InCallInfo infoMsg = InCallInfo.newInstance();
        FragmentManager fragmentManager = getSupportFragmentManager();
        infoMsg.show(fragmentManager, "SilentPhoneInCallInfo");
    }

    public static class InCallInfo extends DialogFragment {

        public static InCallInfo newInstance() {
            InCallInfo f = new InCallInfo();
            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LayoutInflater inflater = getActivity().getLayoutInflater();

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            View view = inflater.inflate(R.layout.dialog_incall_info, null);

            // We always have a name
            TextView txtv = (TextView)view.findViewById(R.id.InCallNameInfo);
            txtv.setText(TiviPhoneService.getInfo(0, -1, "cfg.un"));

            String nr = TiviPhoneService.getInfo(0, -1, "cfg.nr");
            txtv = (TextView)view.findViewById(R.id.InCallNumberInfo);

            if (TextUtils.isEmpty(nr)) {
                view.findViewById(R.id.InCallNumber).setVisibility(View.GONE);
                txtv.setVisibility(View.GONE);
            }
            else
                txtv.setText(CTFlags.formatNumber(nr));

            // Add inflated view and action buttons
            builder.setView(view)
                    .setTitle(R.string.thanks_top_line)
                    .setPositiveButton(R.string.close_dialog, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {}
                    });

            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
