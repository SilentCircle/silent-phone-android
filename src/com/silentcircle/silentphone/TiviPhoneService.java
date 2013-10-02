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


package com.silentcircle.silentphone;

import android.net.wifi.WifiManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.silentcircle.silentcontacts.ScCallLog;

import java.lang.Object;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import java.io.File;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.IBinder;
import android.os.Binder;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.Service;

import android.content.BroadcastReceiver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.media.ToneGenerator;
import android.media.AudioManager.OnAudioFocusChangeListener;

import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.provider.CallLog;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import android.util.Log;

import com.silentcircle.silentphone.activities.TMActivity;
import com.silentcircle.silentphone.activities.CallStateChangeListener;
import com.silentcircle.silentphone.activities.DeviceStateChangeListener;
import com.silentcircle.silentphone.activities.TCallWindow;
import com.silentcircle.silentphone.receivers.OCT;
import com.silentcircle.silentphone.utils.CTCall;
import com.silentcircle.silentphone.utils.CTCalls;
import com.silentcircle.silentphone.utils.TWake;
import com.silentcircle.silentphone.utils.Utilities;

public class TiviPhoneService extends Service {
   
    static public final boolean use_password_key = false;

    private static final String LOG_TAG = "SilentPhoneService";

    public enum CT_cb_msg {
        eReg, eError, eRinging, eSIPMsg, eCalling, eIncomCall, eNewMedia, eEndCall, eZRTPMsgA, eZRTPMsgV, eZRTP_sas, eZRTPErrA, eZRTPErrV, eZRTPWarn, eStartCall, eEnrroll, eZRTP_peer, eZRTP_peer_not_verifed, eMsg, eLast
    };

    static public final int CALL_TYPE_INAVLID = -1;
    static public final int CALL_TYPE_OUTGOING = 0;
    static public final int CALL_TYPE_INCOMING = 1;
    
    // Status codes for secure key processing
    static public final int SECURE_KEY_NOT_AVAILABE = 1;
    static public final int SECURE_KEY_AVAILABE = 2;
    static public final int SECURE_KEY_SET_TO_CPP = 3;
    
    // static because we process it before we instantiate and start the service
    private static int secretKeyStatus = SECURE_KEY_NOT_AVAILABE;
    
    // Message and event codes; see SilentPhoneAppBroadcastReceiver below.
    public static final int EVENT_WIRED_HEADSET_PLUG = 1;
    public static final int EVENT_DOCK_STATE_CHANGED = 2;

    // We got a media button event, send this to call window for further handling 
    public static final int EVENT_HEADSET_HOOK_PRESSED = 3;

    // Values and status code for keep-alive mechanism
    public static final int KEEP_ALIVE_WAKEUP = 1007;         // Message codes
    public static final int KEEP_ALIVE_RELEASE = 1013;
    public static final int KEEP_ALIVE_WAKEUP_TIME = 180000;  // 3 minutes, given in ms
    public static final int KEEP_ALIVE_GRACE_TIME = 2;        // wait time to allow for more internal housekeeping, in ms
    public static final String KEEP_ALIVE_ALARM = "com.silentcircle.silentphone.KEEP_ALIVE_ALARM";

    // Data for ring tone management
    private static final int VIBRATE_LENGTH = 1000; // ms
    private static final int PAUSE_LENGTH = 1000; // ms
    private volatile boolean mContinueVibrating;

    private Ringtone ringtone;
    private Uri defaultRingtone;
    private VibratorThread vibThread;
    private Vibrator vibrator;
    
    /** Set to true if GSM/CDMA phone state is off-hook. */
    private boolean otherPhoneOffHook;

    private AlarmManager alarmManager;
    
    OnAudioFocusChangeListener afChangeListener;
    /**
     * Restarted means that the main activity was restarted from Notification or
     * because user closed the Activity during the call and then clicked the icon to
     * get the call back. In this case the main activity detects this and calls
     * <code>showCallScreen</code> with this call type. This suppresses ringing.
     */
    static public final int CALL_TYPE_RESTARTED = 2;

    private final Collection<CallStateChangeListener> callStateListeners = new LinkedList<CallStateChangeListener>();
    private final Collection<DeviceStateChangeListener> deviceStateListeners = new LinkedList<DeviceStateChangeListener>();
    
    /*
     * Some counters to get statistics aboput partial wakelock usage
     */
    private int pwlWakeCallback;    // called from native libs
    private int pwlAlarmWake;       // via Alarm notification
    private int pwlNetworkState;    // via network state change
    private int pwlOnCreate;        // service onCreate() - should never > 1
    private int pwlShowScreen;      // triggered by call activity (outgoing, restart)

    /**
     * Name of the SilentContacts package
     */
    private static final String SC_CONTACTS_PKG = "com.silentcircle.silentcontacts";
    private PackageInfo scPkgInfo;
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access. Because we know this service always runs in the same process as its clients, we don't need to
     * deal with IPC.
     */
    public class LocalBinder extends Binder {
        public TiviPhoneService getService() {
            return TiviPhoneService.this;
        }
    }

    /**
     * Internal handler to receive and process keep-alive messages.
     */
    private final InternalHandler mHandler = new InternalHandler(this);
    
    /**
     * The receiver receives the alarms that trigger keep-alive messages.
     */
    private final RunTimerReceiver runTimeRecv = new RunTimerReceiver();

    static public CTCalls calls = new CTCalls();

    public static TiviPhoneService mc = null;

    static final String tivi_app_name = "SilentCircle";// move to cfg class

    static final String ACTION_FOREGROUND = "com.tivi.tiviphone.FOREGROUND";
    static final String ACTION_BACKGROUND = "com.tivi.tiviphone.BACKGROUND";

    private static final Class<?>[] mSetForegroundSignature = new Class[] { boolean.class };
    private static final Class<?>[] mStartForegroundSignature = new Class[] { int.class, Notification.class };
    private static final Class<?>[] mStopForegroundSignature = new Class[] { boolean.class };

    private Method mSetForeground;
    private Method mStartForeground;
    private Method mStopForeground;
    private Object[] mSetForegroundArgs = new Object[1];
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];

    private boolean bStarted = false;


    /**
     *  Broadcast receiver for various intent broadcasts (see onCreate()), mainly to keep track of device status.
     */
    private final BroadcastReceiver variousReceiver = new SilentPhoneAppBroadcastReceiver();
    private boolean isHeadsetPlugged;
    private int dockingState = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    
    /**
     * These handle state changes of the native GSM/CDMA phone.
     */
    private TelephonyManager telephonyManager;
    private SilentPhoneStateReceiver phoneStateReceiver = new SilentPhoneStateReceiver();

    private NotificationManager mNM;
    private TWake wk;
    
    /* **********************************************************
     * Initialization and definition of the native interfaces to the
     * Tivi SIP/RTP/Codec engine that is implemented in C and C++
     * ******************************************************** */
    static {
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("tina");
        System.loadLibrary("aec");
        System.loadLibrary("tivi");
    }
   
   //Callback
    private final BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {
       @Override
       public void onReceive(Context context, Intent intent) {
           wk.start();
           pwlNetworkState++;
           if (TMActivity.SP_DEBUG) Log.i(LOG_TAG, "PartialWake - network state. Count: " + pwlNetworkState +", at: " + System.currentTimeMillis());
           TiviPhoneService.checkNet(context);
           mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, 1000);
       }
    };
    
    static void checkNet(Context context){
         ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
         NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
         boolean wifiStateOn = ni != null && ni.isConnectedOrConnecting();
         //TODO test NetworkInfo cm.getActiveNetworkInfo ()
         checkNetState(wifiStateOn ? 1: 0, 0, 0);
    }

    void startNetworkMonitor() {
        // registering receiver in activity or in service
        IntentFilter nf = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkStateReceiver, nf);
        checkNet(getBaseContext());
        // setAddrNow

        nf = new IntentFilter();
        nf.addAction(KEEP_ALIVE_ALARM);
        registerReceiver(runTimeRecv, nf);
    }

    void stopNetworkMonitor() {
        // IntentFilter nf = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        unregisterReceiver(mNetworkStateReceiver);
        unregisterReceiver(runTimeRecv);

    }

    /*
     * The following native interface MUST NOT be static because their native implementation use the "this" object.
     */
    public native int doInit(int iDebugFlag);

    public native String doX(int z, String s);// deprecated use getInfo, doCmd

    // can pass command to engine
    public static native String getInfo(int iEngineID, int iCallID, String z);// if iEngineID==-1 || iCallID==-1 then it is not
                                                                              // used
    public static native int setKeyData(byte[] key);

    private static native int initPhone();

    private static native int savePath(byte[] z);

    private static native int saveImei(byte[] z);

    private static native int checkNetState(int iIsWifi, int IP, int iIsIpValid);

    public static native int doCmd(String z);

    public static native int getPhoneState();// deprecated, use getInfo

    public static native int getSetCfgVal(int iGet, byte[] k, int iKeyLen, byte[] v);// TODO we need int engine_id

    public native static void nv21ToRGB32(byte[] b, int[] idata, short[] sdata, int w, int h, int angle);

    public native static int getVFrame(int iPrevID, int[] idata, int[] sxy);

    void invokeMethod(Method method, Object[] args) {
        try {
            mStartForeground.invoke(this, mStartForegroundArgs);
        }
        catch (InvocationTargetException e) {
            // Should not happen.
            Log.w(LOG_TAG, "Unable to invoke method", e);
        }
        catch (IllegalAccessException e) {
            // Should no happen.
            Log.w(LOG_TAG, "Unable to invoke method", e);
        }
    }

    /**
     * This is a wrapper around the new startForeground method, using the older APIs if it is not available.
     */
    void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            invokeMethod(mStartForeground, mStartForegroundArgs);
            return;
        }

        // Fall back on the old API.
        // mSetForegroundArgs[0] = Boolean.TRUE;
        // invokeMethod(mSetForeground, mSetForegroundArgs);
        mNM.notify(id, notification);

    }

    /**
     * This is a wrapper around the new stopForeground method, using the older APIs if it is not available.
     */
    void stopForegroundCompat(int id) {
        bStarted = false;
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            try {
                mStopForeground.invoke(this, mStopForegroundArgs);
            }
            catch (InvocationTargetException e) {
                // Should not happen.
                Log.w(LOG_TAG, "Unable to invoke stopForeground", e);
            }
            catch (IllegalAccessException e) {
                // Should not happen.
                Log.w(LOG_TAG, "Unable to invoke stopForeground", e);
            }
            return;
        }

        // Fall back on the old API. Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        mNM.cancel(id);
        // mSetForegroundArgs[0] = Boolean.FALSE;
        // invokeMethod(mSetForeground, mSetForegroundArgs);
//        setForeground(false);

    }

    @Override
    public void onCreate() {

        wk = new TWake(this, LOG_TAG);
        mc = this;

        defaultRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        vibrator = (Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        try {
            mStartForeground = getClass().getMethod("startForeground", mStartForegroundSignature);
            mStopForeground = getClass().getMethod("stopForeground", mStopForegroundSignature);
        }
        catch (NoSuchMethodException e) {
            // Running on an older platform.
            mStartForeground = mStopForeground = null;
            return;
        }
        try {
            mSetForeground = getClass().getMethod("setForeground", mSetForegroundSignature);
        }
        catch (NoSuchMethodException e) {
            throw new IllegalStateException("OS  doesn't have Service.startForeground OR Service.setForeground!");
        }
        initJNI(getBaseContext());
        doInit(TMActivity.SP_DEBUG ? 1 : 0);
        startNetworkMonitor();
        doCmd(":reg");

        // Register for misc other intent broadcasts.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
        intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        intentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(variousReceiver, intentFilter);
        
        // Empty audio focus change listener: we request audio focus on incoming call for STREAM_MUSIC
        // and we will not stop the call on focus loss in this case. Comments left in as reminder.
        afChangeListener = new OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    // Pause
                } 
                else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    // Resume
                } 
                else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
//                    am.unregisterMediaButtonEventReceiver(RemoteControlReceiver);
//                    am.abandonAudioFocus(afChangeListener);
                    // Stop playback
                }
            }
        };
        // Check availability of SilentContacts
        PackageManager pm = this.getPackageManager();
        try {
            scPkgInfo = pm.getPackageInfo(SC_CONTACTS_PKG, 0);
        }
        catch (NameNotFoundException e) {
            // TODO start Activity to inform user about missing SilentContacts and ask to install the package
//            e.printStackTrace();
        }

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateReceiver, PhoneStateListener.LISTEN_CALL_STATE );

        alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);

        wk.start();
        pwlOnCreate++;
        if (TMActivity.SP_DEBUG) Log.i(LOG_TAG, "PartialWake - onCreate. Count: " + pwlOnCreate +", at: " + System.currentTimeMillis());
        mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, KEEP_ALIVE_GRACE_TIME);
        initWifiLock();
    }

    private static final String key_wifi_lock = "wifi_lock";
    WifiManager.WifiLock wifiLock = null;

    public void enableDisableWifiLock(boolean b) {
        Context c = getBaseContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(key_wifi_lock, b);
        e.commit();
        if (b)
            initWifiLock();
        else
            stopWifiLock();
    }

    synchronized private void initWifiLock() {
        if (wifiLock != null)
            return;

        Context c = getBaseContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        boolean b = prefs.getBoolean(key_wifi_lock, false);
        if (!b)
            return;

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Tivi wifi lock");
            wifiLock.acquire();
            if (TMActivity.SP_DEBUG)
                Log.i("wifi-tivi", "wifiLock.isHeld() = " + wifiLock.isHeld());
        }
    }

    synchronized private void stopWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    @Override
    public void onDestroy() {

        telephonyManager.listen(phoneStateReceiver, PhoneStateListener.LISTEN_NONE);

        // Make sure our notification is gone.
        stopForegroundCompat(R.drawable.online);
        stopNetworkMonitor();

        stopWifiLock();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);

        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return START_STICKY;
    }

    void handleCommand(Intent intent) {
        Notification notification = setNewInfo();
        if (!bStarted)
            startForegroundCompat(R.drawable.online, notification);
        getPhoneState();

        bStarted = true;
    }

    /**
     * Cleanup if a call ends.
     * 
     * @param c call that ended.
     */
    void onStopCall(CTCall c) {
        if (calls.getLastCall() == null) {      // No more active calls
            mHandler.sendEmptyMessage(KEEP_ALIVE_WAKEUP);
            ((AudioManager) getSystemService(AUDIO_SERVICE)).abandonAudioFocus(afChangeListener);
        } 
    }

    /**
     * Reschedule a keep alive alarm.
     * 
     * @param delay millisecond to get the next alarm 
     */
    private void rescheduleWakeAlarm(int delay) {
        Intent i = new Intent();
        i.setAction(KEEP_ALIVE_ALARM);
        PendingIntent pending = PendingIntent.getBroadcast(this, KEEP_ALIVE_WAKEUP, i, PendingIntent.FLAG_CANCEL_CURRENT);

        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pending);
    }

    void updateRecents(CTCall c) {
        calls.onUpdateRecents(c);
        insertCallLog(c);
    }

    /**
     * Notifies all registered <tt>CallStateChangeListener</tt>s that ZRTP state changed.
     * 
     * @param call
     *            the <tt>the CTCall information</tt>
     */
    private void zrtpStateChanged(CTCall call, TiviPhoneService.CT_cb_msg msg) {
        synchronized (callStateListeners) {
            for (CallStateChangeListener l : callStateListeners)
                l.zrtpChange(call, msg);
        }
    }

    /**
     * Notifies all registered <tt>CallStateChangeListener</tt>s that Call state changed.
     * 
     * @param call
     *            the <tt>the CTCall information</tt>
     */
    private void callStateChanged(CTCall call, TiviPhoneService.CT_cb_msg msg) {
        synchronized (callStateListeners) {
            for (CallStateChangeListener l : callStateListeners)
                l.stateChange(call, msg);
        }
    }

    /**
     * Notifies all registered <tt>DeviceStateChangeListener</tt>s that a device state changed.
     */
    private void deviceStateChanged(int stateChanged) {
        synchronized (deviceStateListeners) {
            for (DeviceStateChangeListener l : deviceStateListeners)
                l.deviceStateChange(stateChanged);
        }
    }

    /**
     * Check if SilentContacts package is available.
     * 
     * @return {@code true} if SilentContacts package is available
     */
    public boolean hasSilentContacts() {
        PackageManager pm = getPackageManager();
        try {
            scPkgInfo = pm.getPackageInfo(SC_CONTACTS_PKG, 0);
        }
        catch (NameNotFoundException e) {
        }
        return scPkgInfo != null;
    }

    void checkMedia(CTCall c, String str) {
        // start stop video
       c.bIsVideoActive = !str.equalsIgnoreCase("audio");
       callStateChanged(c, CT_cb_msg.eNewMedia);
    }

    private void wakeCallback(int iLock){
//        if (TMActivity.SP_DEBUG) Log.i(LOG_TAG,"sip lock "+ iLock);
    }

    /**
     * Callback from C/C++ code on status changes.
     * 
     * @param iEngID  the Tivi engine id
     * @param iCallID the internal call id
     * @param msgid   the new status message/id
     * @param str     an optional string for the new status
     */
    private void callback(int iEngID, int iCallID, int msgid, String str) {

        if (msgid < 0 || msgid >= CT_cb_msg.eLast.ordinal()) {
            if (TMActivity.SP_DEBUG) Log.i(LOG_TAG, "ERR: msgid<0 || msgid>=eLast.ordinal()");
            return;
        }
        CT_cb_msg en = CT_cb_msg.values()[msgid];
        // EnumType.values()[someInt]
        if (TMActivity.SP_DEBUG) Log.i(LOG_TAG, "callback_msg=" + en.name() + " msg=" + str + " iCallID=" + iCallID);

        CTCall c = calls.findCallById(iCallID);

        String p = null;

        if (c == null && (en == CT_cb_msg.eReg || (iCallID == 0 && en == CT_cb_msg.eError))) {
            setNewInfo();
            return;
        }
        if (en == CT_cb_msg.eIncomCall) {
            c = calls.getEmptyCall(false);
            if (c == null) {
                return;
            }
            boolean vc = false;
            if (vc)
                p = getString(R.string.call_type_video);
            else
                p = getString(R.string.call_type_audio);
            c.iEngID = iEngID;
            c.iCallId = iCallID;
            c.iIsIncoming = true;
            c.iShowVideoSrcWhenAudioIsSecure = vc;
            
            // Set caller's name / number in call info data
            c.setPeerName(str);
            
            c.fillDataFromContacts(this);
            
            if (calls.getCallCnt() == 1) {
                calls.setCurCall(c);
                showCallScreen(CALL_TYPE_INCOMING);
            }
        }
        // On outgoing call: get a free call-info object, further info set in case handling below 
        if (en == CT_cb_msg.eCalling) {
            c = calls.getEmptyCall(false);
            if (c == null) {
                if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "eCalling: cannot get empty call info");
                return;
            }
            calls.setCurCall(c);
        }

        if (c == null) {
            return;
        }
        c.iUpdated++;

        switch (en) {
        case eNewMedia:
            checkMedia(c, str);
            break;

        case eEnrroll:
            c.iShowVerifySas = false;
            c.iShowEnroll = true;
            zrtpStateChanged(c, en);
            break;

        case eZRTP_peer_not_verifed:
            c.iShowVerifySas = true;
            if (str != null)
                c.zrtpPEER.setText(str);
            zrtpStateChanged(c, en);
            break;

        case eZRTP_peer:
            if (str == null) {
                c.iShowVerifySas = true;
            }
            else {
                c.zrtpPEER.setText(str);
            }
            zrtpStateChanged(c, en);
            break;

        case eZRTPMsgV:
            if (str != null)
                c.bufSecureMsgV.setText(str);
            zrtpStateChanged(c, en);
            break;

        case eZRTPMsgA:
            if (str != null)
                c.bufSecureMsg.setText(str);
            zrtpStateChanged(c, en);
            break;

        case eZRTPErrV:
            c.bufSecureMsgV.setText("ZRTP Error");
            if (str != null)
                c.zrtpWarning.setText(str);
            zrtpStateChanged(c, en);
            break;

        case eZRTPErrA:
            c.bufSecureMsg.setText("ZRTP Error");

        case eZRTPWarn:
            if (str != null)
                c.zrtpWarning.setText(str);
            zrtpStateChanged(c, en);
            break;

        case eZRTP_sas:
            if (str != null)
                c.bufSAS.setText(str);
            zrtpStateChanged(c, en);
            break;

        case eSIPMsg:
            p = str;
            break;

            // This case is for outgoing calls
        case eRinging:
            p = getString(R.string.sip_state_ringing);
            break;

            // This case is for outgoing calls
        case eCalling:
            p = getString(R.string.sip_state_calling);
            c.iEngID = iEngID;
            c.iCallId = iCallID;
            c.setPeerName(str);
            c.fillDataFromContacts(this);
            break;

            // The engine sends eEndCall on outgoing and incoming calls 
        case eEndCall:
            if (!c.iSipHasErrorMessage)
                p = getString(R.string.sip_state_ended);
            if (c.iEnded == 0)
                c.iEnded = 2;
            updateRecents(c);
            onStopCall(c);
            break;

            // The engine sends eStartCall on outgoing and incoming calls 
        case eStartCall:
            p = " ";// Call is active but not yet "answered" ;
            c.iActive = true;
            if (c.uiStartTime == 0)
                c.uiStartTime = System.currentTimeMillis();

            if (c.bufSecureMsg.getLen() == 0) {
                c.bufSecureMsg.setText(getString(R.string.sip_state_connecting));
                zrtpStateChanged(c, en);
            }
            break;

        case eError:
            if (str != null)
                p = str;
            else {
                p = getString(R.string.call_error);
            }
            c.iSipHasErrorMessage = true;
            break;
        
        default:
            break;
        }

        if (p != null) {
            c.bufMsg.setText(p);
        }
        callStateChanged(c, en);
    }

    public String doX(int z) {
        return doX(z, null);
    }

    public String doX(String s) {
       return doX(10, s);
    }

    static boolean iInitialized=false;

    public static boolean isInitialized() {
        return iInitialized;
    }

    public static int initJNI(Context ctx) {

        if (iInitialized)
            return 0;
        iInitialized = true;

        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = tm.getDeviceId();
        if (imei == null) {
            imei = android.os.Build.SERIAL
                    + " "
                    + android.provider.Settings.Secure.getString(ctx.getContentResolver(),
                            android.provider.Settings.Secure.ANDROID_ID);
        }
        else {
            String add = android.os.Build.SERIAL
                    + " "
                    + android.provider.Settings.Secure.getString(ctx.getContentResolver(),
                            android.provider.Settings.Secure.ANDROID_ID);

            imei += " " + add;
        }
        saveImei(imei.getBytes());

        File f = ctx.getFilesDir();
        savePath(f.toString().getBytes());
        initPhone();
        doCmd("g.setLevel " + android.os.Build.VERSION.SDK_INT);
        return 0;
    }

    synchronized private Notification setNewInfo() {

        int ico;
        CharSequence text;

        int i = getPhoneState();
        if (i == 2) {
            text = getString(R.string.sip_state_online);
            ico = R.drawable.online;
        }
        else if (i == 1) {
            text = getString(R.string.sip_state_connecting);
            ico = R.drawable.connecting;
        }
        else {
            text = getString(R.string.sip_state_offline);
            ico = R.drawable.offline;
        }

        Notification notification = new Notification(ico, text, System.currentTimeMillis());

        Intent intent = new Intent(this, TMActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, tivi_app_name, text, contentIntent);
        mNM.notify(R.drawable.online, notification);
        return notification;

    }

    public void showCallScreen(int callType) {
        mHandler.removeMessages(KEEP_ALIVE_WAKEUP);
        wk.start();
        pwlShowScreen++;
        if (TMActivity.SP_DEBUG) Log.i(LOG_TAG, "PartialWake - showCallScreen. Count: " + pwlShowScreen +", at: " + System.currentTimeMillis());            
        Intent intent = new Intent();
        intent.setClass(this, TCallWindow.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Bundle bundle = new Bundle();
        bundle.putString("name", doX(6));
        bundle.putInt("incoming", callType);
        intent.putExtras(bundle);

        startActivity(intent);

        if (callType == CALL_TYPE_INCOMING) {
            onIncomingCall();
        }

        // Silence any music playing applications. If they are well behaved the stop playing, resume after
        // we release the audio focus after all calls are terminated.
        int result = ((AudioManager) getSystemService(AUDIO_SERVICE)).requestAudioFocus(afChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "audio focus result: " + result);
    }

    /**
     * Adds the given <tt>CallStateChangeListener</tt> to the list of call state change listeners.
     * 
     * @param l
     *            the <tt>CallStateChangeListener</tt> to add
     */
    public void addStateChangeListener(CallStateChangeListener l) {
        synchronized (callStateListeners) {
            if (callStateListeners.contains(l))     // don't add twice
                return;
            callStateListeners.add(l);
        }
    }

    /**
     * Removes the given <tt>CallStateChangeListener</tt> from the list of call state change listeners.
     * 
     * @param l
     *            the <tt>CallStateChangeListener</tt> to remove
     */
    public void removeStateChangeListener(CallStateChangeListener l) {
        synchronized (callStateListeners) {
            callStateListeners.remove(l);
        }
    }

    /**
     * Adds the given <tt>DeviceStateChangeListener</tt> to the list of device state change listeners.
     * 
     * @param l
     *            the <tt>DeviceStateChangeListener</tt> to add
     */
    public void addDeviceChangeListener(DeviceStateChangeListener l) {
        synchronized (deviceStateListeners) {
            if (deviceStateListeners.contains(l))   // don't add twice
                return;
            deviceStateListeners.add(l);
        }
    }

    /**
     * Removes the given <tt>DeviceStateChangeListener</tt> from the list of device state change listeners.
     * 
     * @param l
     *            the <tt>DeviceStateChangeListener</tt> to remove
     */
    public void removeDeviceChangeListener(DeviceStateChangeListener l) {
        synchronized (deviceStateListeners) {
            deviceStateListeners.remove(l);
        }
    }    
    
    public void onIncomingCall() {
        startRT();
    }

    private void startRT() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int iRM = am.getRingerMode();

        if (iRM != AudioManager.RINGER_MODE_SILENT) {

            if (iRM == AudioManager.RINGER_MODE_NORMAL) {
                Uri tone = calls.selectedCall.customRingtoneUri == null ? defaultRingtone
                        : calls.selectedCall.customRingtoneUri;
                ringtone = RingtoneManager.getRingtone(TiviPhoneService.this, tone);
                if (ringtone != null) {
                    if (!isHeadsetPlugged())
                        Utilities.turnOnSpeaker(this, true, false);    // Speaker on, but don't set state
                    ringtone.setStreamType(AudioManager.STREAM_RING);
                    ringtone.play();
                }
            }
            if ((iRM == AudioManager.RINGER_MODE_VIBRATE || iRM == AudioManager.RINGER_MODE_NORMAL) && vibThread == null) {
                mContinueVibrating = true;
                vibThread = new VibratorThread(); // simple private class - see below
                vibThread.start();
            }
        }
    }
    
    public void stopRT() {

        if (ringtone != null && ringtone.isPlaying()) {
            Utilities.restoreSpeakerMode(this);
            ringtone.stop();
        }
        if (vibThread != null) {
            mContinueVibrating = false;
            vibThread = null;
        }
        // Also immediately cancel any vibration in progress.
        vibrator.cancel();
        ringtone = null;
    }
    
    public boolean isHeadsetPlugged() {
        return isHeadsetPlugged;
    }

    public int getDockingState() {
        return dockingState;
    }

    /**
     * Get status of secret key processing.
     *  
     * @return secret key processing status
     * @see SECURE_KEY_NOT_AVAILABE
     * @see SECURE_KEY_AVAILABE
     * @see SECURE_KEY_SET_TO_CPP
     */
    public static int getSecretKeyStatus() {
        return secretKeyStatus;
    }
    
    /**
     * Hand over the computed secret key to C++ functions.
     * 
     * NOTE: only used if we enable the KeyChain secure key meachanism.
     * 
     * The method clears the data as soon as it was handed over to the C++ functions.
     * 
     * @param secKey the secret key.
     */
//    public static void setSecretKey(byte[] secKey) {
//        secretKeyStatus = SECURE_KEY_AVAILABE;
//        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "set secret key to C++");
//
//        // call C++ (native) function here to hand over the key
//        setKeyData(secKey);
//
//        secretKeyStatus = SECURE_KEY_SET_TO_CPP;
//        Arrays.fill(secKey, (byte)0);
//    }

    /* *******************************************************************************************
     * The following section mainly contains privates that handle specific tasks or are receivers
     * for various events and state changes. The section also contains some helper functions used 
     * by the private classes only.
     ****************************************************************************************** */
    
    /**
     * Receiver for intent broadcasts the SilentPhone client cares about.
     */
    private class SilentPhoneAppBroadcastReceiver extends BroadcastReceiver {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                isHeadsetPlugged = (intent.getIntExtra("state", 0) == 1);
                deviceStateChanged(EVENT_WIRED_HEADSET_PLUG);
            }
            else if (action.equals(Intent.ACTION_BATTERY_LOW)) {
                // notifier.sendBatteryLow(); // Play a warning tone if in-call
            }
            else if (action.equals(Intent.ACTION_DOCK_EVENT)) {
                dockingState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED);
                deviceStateChanged(EVENT_DOCK_STATE_CHANGED);
            }
            else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                int ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
                if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                    stopRT();
                }
            }
        }
    }


    private class VibratorThread extends Thread {
        public void run() {
            while (mContinueVibrating) {
                vibrator.vibrate(VIBRATE_LENGTH);
                SystemClock.sleep(VIBRATE_LENGTH + PAUSE_LENGTH);
            }
        }
    }

    /* ********************************************************************
     * Handle GSM/CDMA call state changes.
     * The policy we use here:
     * - If GSM/CDMA state changes and no active SP call - just ignore
     * - if GSM/CDMA state changes and we have one or more active SP calls:
     *   = state RINGING: set Ringer to silent mode, play notification tone
     *   = state OFF_HOOK: stop notification tone, set active SP calls to
     *     Hold mode
     *   = state IDLE: restore ringing mode, restore SP calls to previous
     *     mode
     * 
     ******************************************************************** */
    private Integer savedRingerMode;
    private ToneGenerator mToneGenerator;
    private ArrayList<CTCall> callOnHoldList = new ArrayList<CTCall>(15);

    private void playInCallTone(int toneId) {       
        if (mToneGenerator == null) {
            try {
                mToneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);
                mToneGenerator.startTone(ToneGenerator.TONE_SUP_CALL_WAITING);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Exception caught while creating local tone generator: " + e);
                mToneGenerator = null;
            }
        }
    }
    
    private void stopInCallTone() {
        if (mToneGenerator != null) {
            mToneGenerator.stopTone();
            mToneGenerator.release();
            mToneGenerator = null;
        }
    }

    /**
     * Sets all calls to hold mode if not already in hold mode.
     * 
     * This method stores all calls it sets to hold in <code>callOnHoldList</code>.
     */
    private void setCallsHold() {
        CTCall call;
        int cnt = calls.getCallCnt();
        if (cnt > 0) {
            for (int i = 0; i < CTCalls.MAX_GUI_CALLS; i++) {
                call = TiviPhoneService.calls.getCall(i);
                if (call != null && !call.iIsOnHold) {
                    callOnHoldList.add( call);
                    doCmd("*h" + call.iCallId);
                }
            }
        }
    }

    private void insertCallLog(CTCall call){

        long duration = 0;
        String caller = null;

        if(call==null || call.iRecentsUpdated)
            return;
        call.iRecentsUpdated = true;

        if (call.uiStartTime != 0) {
            duration = System.currentTimeMillis() - call.uiStartTime;
            call.uiStartTime = 0;
        }
        int iType = call.iIsIncoming ? android.provider.CallLog.Calls.INCOMING_TYPE
                : android.provider.CallLog.Calls.OUTGOING_TYPE;

        if (duration == 0 && iType != TiviPhoneService.CALL_TYPE_OUTGOING) {
            iType = android.provider.CallLog.Calls.MISSED_TYPE;
        }

        if (call.bufDialed.getLen() > 0)
            caller = call.bufDialed.toString();
        else if (call.bufPeer.getLen() > 0)
            caller = call.bufPeer.toString();
        else caller = "";                    // must not be here

        if (hasSilentContacts()) {
            insertCallIntoSilentCallLog(getBaseContext(), caller, iType, duration);
        }
        else {
            insertCallIntoCallLog(getBaseContext(), caller, iType, duration);
        }
    }

    // do not use this directly
    private static void insertCallIntoCallLog(Context ctx, String dst, int iType, long dur) {
        final android.content.ContentResolver resolver = ctx.getContentResolver();
        android.content.ContentValues values = new android.content.ContentValues(5);

        // strip @ and chars
        StringBuilder strDst = new StringBuilder(32);
        int i = 0;
        int dL = dst.length();
        while (i < dL && ((dst.charAt(i) <= '9' && dst.charAt(i) >= '0') || (i == 0 && dst.charAt(0) == '+'))) {
            strDst.append(dst.charAt(i));
            i++;
        }
        if (i != 0 && i < dL && dst.charAt(i) == '@') {
            values.put(CallLog.Calls.NUMBER, OCT.prefix_add +" "+ strDst);
        }
        else
            values.put(CallLog.Calls.NUMBER, OCT.prefix_add +" "+ dst);

        values.put(CallLog.Calls.TYPE    , Integer.valueOf(iType));
        values.put(CallLog.Calls.DATE    , Long.valueOf(System.currentTimeMillis() - dur));
        values.put(CallLog.Calls.DURATION, Long.valueOf(dur/1000));
        values.put(CallLog.Calls.NEW     , Boolean.valueOf(true));

        try {
            resolver.insert(CallLog.CONTENT_URI, values);
        }
        catch (IllegalArgumentException ex) {
            return;
        }
    }

    private static void insertCallIntoSilentCallLog(Context ctx, String dst, int iType, long dur) {
        final android.content.ContentResolver resolver = ctx.getContentResolver();
        android.content.ContentValues values = new android.content.ContentValues(5);

        // strip @ and chars
        StringBuffer strDst = new StringBuffer(32);
        int i = 0;
        int dL = dst.length();
        while (i < dL && ((dst.charAt(i) <= '9' && dst.charAt(i) >= '0') || (i == 0 && dst.charAt(0) == '+'))) {
            strDst.append(dst.charAt(i));
            i++;
        }
        if (i != 0 && i < dL && dst.charAt(i) == '@') {
            values.put(ScCallLog.ScCalls.NUMBER, strDst.toString());
        }
        else
            values.put(ScCallLog.ScCalls.NUMBER, dst);

        values.put(ScCallLog.ScCalls.TYPE    , Integer.valueOf(iType));
        values.put(ScCallLog.ScCalls.DATE    , Long.valueOf(System.currentTimeMillis() - dur));
        values.put(ScCallLog.ScCalls.DURATION, Long.valueOf(dur/1000));
        values.put(ScCallLog.ScCalls.NEW     , Boolean.valueOf(true));

        try {
            resolver.insert(ScCallLog.CONTENT_URI, values);
        }
        catch (IllegalArgumentException ex) {
            return;
        }
    }

    /**
     * Sets all calls to no-hold mode if not already in hold mode.
     * 
     * This method sets all calls in <code>callOnHoldList</code>to no-hold mode. 
     */
    private void setCallsNoHold() {
        int cnt = callOnHoldList.size();
        
        for (int i = 0; i < cnt; i++) {
            CTCall call = callOnHoldList.get(0);
            if (call == null)
                continue;
            doCmd("*u" + call.iCallId);            
            callOnHoldList.remove(0);
        }
    }

    private class SilentPhoneStateReceiver extends PhoneStateListener {

        @Override
        public void onCallStateChanged(final int state, final String incomingNumber) {
            if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "Call state has changed !" + state + " : " + incomingNumber);
            if (state != TelephonyManager.CALL_STATE_IDLE) {

                // If we have active SP calls then manage Ringing and notification tones, otherwise just do nothing
                if (calls.getCallCnt() >= 1) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "Call state has changed - ringing!");
                        // Avoid ringing, play a notification tone
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        savedRingerMode = am.getRingerMode();
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                        playInCallTone(ToneGenerator.TONE_SUP_CALL_WAITING);
                    }
                    else {
                        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "Call state has changed - offhook!");
                        otherPhoneOffHook = true;
                        stopInCallTone();
                        setCallsHold();
                    }
                }
                else {
                    TiviPhoneService.doCmd(":GSMactive 1");
                }
            }
            else {
                if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "Call state has changed - idle!");
                // In case we not had an active SP call when GSM/CDMA call happend the most of the following
                // action are no-ops
                // Normal phone is back in IDLE state, reset ringerMode if it was changed
                if(savedRingerMode != null) {
                    AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                    am.setRingerMode(savedRingerMode);
                    savedRingerMode = null;
                }
                otherPhoneOffHook = false;
                TiviPhoneService.doCmd(":GSMactive 0");
                stopInCallTone();       // no-op if GSM/CDMA call was answered or not handled
                setCallsNoHold();       // no-op if GSM/CDMA call was not answered or not handled
            }
            super.onCallStateChanged(state, incomingNumber);
        }
    }
    
    /**
     * Internal message handler class to wake-up the keep-alive function.
     * 
     * @author werner
     *
     */
    private static class InternalHandler extends Handler {
        private final WeakReference<TiviPhoneService> mTarget;

        private long prevTime = 0;

        InternalHandler(TiviPhoneService parent) {
            mTarget = new WeakReference<TiviPhoneService>(parent);
        }

        @Override
        public void handleMessage(Message msg) {
            TiviPhoneService parent = mTarget.get();
            if (parent == null)
                return;

            boolean cc = TiviPhoneService.calls.getLastCall() == null ? false : true;
            int ret = 10;
            if (!cc)
                ret = TiviPhoneService.doCmd("getint.ReqTimeToLive");
            
            long now = SystemClock.elapsedRealtime();
            long diff = now - prevTime;

            switch (msg.what) {
            case KEEP_ALIVE_WAKEUP:
                if (ret <= 0) {
                    if (TMActivity.SP_DEBUG)
                        Log.d("TIMERNEW",
                                "keep-alive wakeup at: " + now + ", diff: " + diff + ", systime: " + System.currentTimeMillis());
                    TiviPhoneService.doCmd(":X");
                    parent.mHandler.sendEmptyMessage(KEEP_ALIVE_RELEASE);
                }
                else {
                    if (TMActivity.SP_DEBUG) Log.d("TIMERNEW", "delay keep alive-0 in sec: " + ret);
                    parent.mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, 1000);
                }
                break;
            case KEEP_ALIVE_RELEASE:
                removeMessages (msg.what);
                if (ret <= 0) {
                    if (TMActivity.SP_DEBUG) Log.d("TIMERNEW", "sleeping at: " + now);
                    parent.rescheduleWakeAlarm(KEEP_ALIVE_WAKEUP_TIME);
                    parent.wk.stop();
                }
                else {
                    if (TMActivity.SP_DEBUG) Log.d("TIMERNEW", "delay keep alive-1 in sec: " + ret);
                    parent.mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, 1000);
                }
                break;
            }
            prevTime = now;
        }
    }

    /**
     * Class to handle the keep alive alarms.
     * 
     * @author werner
     *
     */
    private class RunTimerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!wk.isHeld()) {
                wk.start();
                pwlAlarmWake++;
                if (TMActivity.SP_DEBUG) Log.i(LOG_TAG, "PartialWake - alive alaram. Count: " + pwlAlarmWake +", at: " + System.currentTimeMillis());
                mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_WAKEUP, 2);
            }
        }
    }
}

