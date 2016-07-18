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

package com.silentcircle.silentphone2.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.RingtoneUtils;
import com.silentcircle.contacts.ScCallLog;
import com.silentcircle.contacts.UpdateScContactDataService;
import com.silentcircle.contacts.utils.LocaleChangeReceiver;
import com.silentcircle.googleservices.RegistrationIntentService;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.InCallActivity;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.receivers.AutoStart;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.ManageCallStates;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.util.WakeLockHelper;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class TiviPhoneService extends PhoneServiceNative {

    private static final String LOG_TAG = "SilentPhoneService";

    public interface ServiceStateChangeListener {

        void zrtpStateChange(CallState call, CT_cb_msg msg);

        void callStateChange(CallState call, CT_cb_msg msg);
    }

    public interface DeviceStateChangeListener {

        void deviceStateChange(int changedState);
    }

    public enum CT_cb_msg {
        eReg, eError, eRinging, eSIPMsg, eCalling, eIncomingCall, eNewMedia, eEndCall, eZRTPMsgA, eZRTPMsgV,
        eZRTP_sas, eZRTPErrA, eZRTPErrV, eZRTPWarn, eStartCall, eEnroll, eZRTP_peer, eZRTP_peer_not_verified,
        eMsg, eLast
    }

    public static final String CALL_TYPE = "call_type";
    public static final String CALL_NAME = "name";
    public static final String IS_OCA_CALL = "is_oca_call";
    public static final String ERROR_MSG_PREFIX = "!!--!!";

    static public final int CALL_TYPE_OUTGOING = 0;
    static public final int CALL_TYPE_INCOMING = 1;
    static public final int CALL_TYPE_RESTART = 2;

    // Status codes for secure key processing
    static public final int SECURE_KEY_NOT_AVAILABLE = 1;
    static public final int SECURE_KEY_AVAILABLE = 2;
    // static because we process it before we instantiate and start the service
    private static int secretKeyStatus = SECURE_KEY_NOT_AVAILABLE;

    // Message and event codes; see SilentPhoneAppBroadcastReceiver below.
    public static final int EVENT_WIRED_HEADSET_PLUG = 1;
    public static final int EVENT_DOCK_STATE_CHANGED = 2;

    // We got a media button event, send this to call window for further handling
    public static final int EVENT_BT_HEADSET_ADDED = 4;
    public static final int EVENT_BT_HEADSET_REMOVED = 5;
    public static final int EVENT_BT_HEADSET_SCO_ON = 6;
    public static final int EVENT_BT_HEADSET_SCO_OFF = 7;


    // Values and status code for keep-alive mechanism
    public static final int KEEP_ALIVE_WAKE_UP = 1007;        // Message codes
    public static final int KEEP_ALIVE_RELEASE = 1013;
    public static final int KEEP_ALIVE_WAKE_UP_TIME = 180000; // 3 minutes, given in ms
    public static final int KEEP_ALIVE_GRACE_TIME = 200;      // wait time for internal housekeeping, in ms
    public static final String KEEP_ALIVE_ALARM = BuildConfig.APPLICATION_ID + ".KEEP_ALIVE_ALARM";

    public static final int TERMINATE_CALL = 1019;
    public static final int CHECK_NET = 1021;
    public static final int START_MESSAGING = 1023;
    public static final int RESCHEDULE_GCM = 1029;

    // Data for ring tone management
    private static final int VIBRATE_LENGTH = 1000; // ms
    private static final int PAUSE_LENGTH = 1000;   // ms

    private Ringtone ringtone;
    private Vibrator vibrator;
    private SharedPreferences prefs;

    private Bitmap notifyLargeIcon;

    private AlarmManager alarmManager;

    private static String mInstanceDeviceId;

    OnAudioFocusChangeListener afChangeListener;

    private final Collection<ServiceStateChangeListener> callStateListeners = new LinkedList<>();
    private final Collection<DeviceStateChangeListener> deviceStateListeners = new LinkedList<>();

    private Class notificationReceiver = DialerActivity.class;
    /*
     * Some counters to get statistics about partial wakelock usage
     */
    private int pwlAlarmWake;       // via Alarm notification
    private int pwlNetworkState;    // via network state change
    private int pwlOnCreate;        // service onCreate() - should never > 1
    private int pwlShowScreen;      // triggered by call activity (outgoing, restart)

    // keep track of one-time initialization
    private static boolean iInitialized;

    private static final int NETWORK_TRACE_LENGTH = 50;
    public static ArrayList<String> mNetworkStatusTrace = new ArrayList<>(NETWORK_TRACE_LENGTH);

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
    private final RunTimerReceiver runTimerReceiver = new RunTimerReceiver();

    public static ManageCallStates calls = new ManageCallStates();

    public static TiviPhoneService phoneService = null;

    private boolean mIsReady;

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

    private NotificationManager notificationManager;
    private WakeLockHelper mWakeLock;
    private WakeLockHelper mSipWakeLock;        // used during SIP processing to keep app up

    // Data for WiFi lock handling
    private static final String key_wifi_lock = "wifi_lock";
    private WifiManager.WifiLock wifiLock = null;

    // Date format to use in traces
    private SimpleDateFormat mTraceDateFormat;

    // set up a file observer to watch changes in the sys network directory
    private int mRunOnZero;

    protected ContentObserver mChangeObserver = new ContentObserver(new Handler()) {

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            runContactsUpdater(false);
        }
    };

    private final FileObserver mSysNetObserver = new FileObserver("/sys/class/net", FileObserver.ALL_EVENTS) {
        @Override
        public void onEvent(int event, String file) {
            event &= FileObserver.ALL_EVENTS;
            if (event == 0) {
                if (mRunOnZero > 0)
                    return;
                mRunOnZero++;
            }
            else
                mRunOnZero = 0;

            traceNetworkState("FileObserver event: " + (event & FileObserver.ALL_EVENTS));
            mHandler.removeMessages(CHECK_NET);
            mHandler.sendEmptyMessageDelayed(CHECK_NET, 3000);
        }
    };

    //Callback
    private final BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null) {
                Log.e(LOG_TAG, "No active network!");
                traceNetworkState("No active network");
                setNewInfo(getString(R.string.no_internet));
            }
            else {
                traceNetworkState(ni.toString() + ", type (int): " + ni.getType());
//            Log.d(LOG_TAG, "++++ network state event, network type: " + ni.getType());
//            Log.d(LOG_TAG, "++++ network name: " + ni.getTypeName() + ", connected: " + ni.isConnected() + " (" + ni.isConnectedOrConnecting() +")");
//            Log.d(LOG_TAG, "++++ network state: " + ni.getState() + ", network fine state: " + ni.getDetailedState());

                String status = null;
                if (ni.getType() == ConnectivityManager.TYPE_MOBILE && ni.isConnected())
                    status = getString(R.string.mobile_internet_available);
                setNewInfo(status);
            }
            // Acquire and release a wakelock only if we have no calls and no sessions (see native code)
            // Otherwise acquire a lock and release it after a second to provide some time for native
            // code to check the network conditions
            if (TiviPhoneService.doCmd("getint.ReqTimeToLive") <= 0) {
                mWakeLock.start();
                pwlNetworkState++;
                if (ConfigurationUtilities.mTrace)
                    Log.i(LOG_TAG, "PartialWake - network state. Count: " + pwlNetworkState + ", at: " + System.currentTimeMillis());
                mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, 1000);
            }
            TiviPhoneService.checkNet(context);

            // Run handler for dealing with failed attachment operations
            if(AxoMessaging.repoIsOpen()) {
                Intent serviceIntent = Action.RUN_ATTACHMENT_HANDLER.intent(context, SCloudService.class);
                serviceIntent.putExtra("FROM_NETWORK", true);
                startService(serviceIntent);
            }
        }
    };

    static void checkNet(Context context){
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean wifiStateOn = ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI && ni.isConnectedOrConnecting();
        checkNetState(wifiStateOn ? 1 : 0, 0, 0);
    }

    private void registerReceivers() {
        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkStateReceiver, intentFilter);

        intentFilter = new IntentFilter();
        intentFilter.addAction(KEEP_ALIVE_ALARM);
        registerReceiver(runTimerReceiver, intentFilter);

        // Register for misc other intent broadcasts.
        intentFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
        intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        intentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(variousReceiver, intentFilter);

        intentFilter = new IntentFilter();
        intentFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        registerReceiver(headsetAudioState, intentFilter);

        intentFilter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(bluetoothState, intentFilter);

        checkForTun();
        mSysNetObserver.startWatching();
    }

    private void unregisterReceivers() {
        unregisterReceiver(mNetworkStateReceiver);
        unregisterReceiver(runTimerReceiver);
        unregisterReceiver(variousReceiver);
        unregisterReceiver(headsetAudioState);
        unregisterReceiver(bluetoothState);
        mSysNetObserver.stopWatching();
    }

    private void traceNetworkState(String info) {
        if (mTraceDateFormat == null) {
            mTraceDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            mTraceDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        info = mTraceDateFormat.format(new Date()) + " - " + info;
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, info);
        mNetworkStatusTrace.add(info);
        if (mNetworkStatusTrace.size() > NETWORK_TRACE_LENGTH)
            mNetworkStatusTrace.remove(0);
    }

    @Override
    public void onCreate() {

        mWakeLock = new WakeLockHelper(this, LOG_TAG);
        mSipWakeLock = new WakeLockHelper(this, LOG_TAG + "_Sip");
        phoneService = this;

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        registerReceivers();

        // Empty audio focus change listener: we request audio focus on incoming call for STREAM_MUSIC
        // and we will not stop the call on focus loss in this case. Comments left in as reminder.
        afChangeListener = new OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    // Pause
                    if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "AudionFocusChange Loss Transient");
                }
                else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "AudionFocusChange Gain");
                    // Resume
                }
                else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "AudionFocusChange Loss");
                    // Stop playback
//                    am.unregisterMediaButtonEventReceiver(RemoteControlReceiver);
//                    am.abandonAudioFocus(afChangeListener);
                }
            }
        };
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateReceiver, PhoneStateListener.LISTEN_CALL_STATE);

        alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        if (checkBluetoothDeviceAvailable()) {
            addBtProfileListener();
        }
        mWakeLock.start();
        pwlOnCreate++;
        if (ConfigurationUtilities.mTrace) Log.i(LOG_TAG, "PartialWake - onCreate. Count: " + pwlOnCreate +", at: " + System.currentTimeMillis());
        initWifiLock();
        mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, KEEP_ALIVE_GRACE_TIME);
    }


    public void enableDisableWifiLock(final boolean b) {
        final SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(key_wifi_lock, b).apply();
        if (b)
            initWifiLock();
        else
            stopWifiLock();
    }

    synchronized private void initWifiLock() {
        if (wifiLock != null)
            return;
        final boolean b = prefs.getBoolean(key_wifi_lock, true);
        if (!b)
            return;

        final WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Silent Phone wifi lock");
            wifiLock.acquire();
            if (ConfigurationUtilities.mTrace)
                Log.i("wifi-tivi", "wifiLock.isHeld() = " + wifiLock.isHeld());
        }
    }

    synchronized private void stopWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    public boolean isReady() {
        return mIsReady;
    }

    @Override
    public void onTaskRemoved (Intent rootIntent) {
        if (ConfigurationUtilities.mTrace) {
            Log.d(LOG_TAG, "Got a 'onTaskRemoved', root intent: " + rootIntent);
        }
        // Send this Intent to circumvent a bug in Android Lollipop and a few
        // version before Lollipop, Android issue #53313 . If necessary we can
        // send this Intent even on M and above, it doesn't do any harm, just
        // doing the normal ON_BOOT processing which is sort of a reduced "launch"
        // and does not even bind DialerActivity to the service. Looks like just
        // sending the Intent and starting the DialerActivity again prevents the
        // erroneous behavior of Android L and below.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            final Intent i = new Intent(this, DialerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setAction(AutoStart.ON_BOOT);
            startActivity(i);
        }
    }

    @Override
    public void onDestroy() {

        telephonyManager.listen(phoneStateReceiver, PhoneStateListener.LISTEN_NONE);

        // Make sure our notification is gone.
        stopForeground(true);
        unregisterReceivers();
        contactObserverUnregister();
        LocaleChangeReceiver.setPhoneService(null);
        if (headsetProxyListenerAdded)
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadsetProxy);
        stopWifiLock();
        phoneService = null;
        iInitialized = false;
    }

    @Override
    public synchronized int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (ConfigurationUtilities.mTrace)Log.d(LOG_TAG, "onStart intent: " + intent + ", startId: " + startId);

        // If this is a restart then send an start intent to our main activity and stop this service.
        // DialerActivity performs all necessary actions to perform the re-start
        if (intent == null) {
            final Intent i = new Intent(this, DialerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.w(LOG_TAG, "Restarting phone service due to unknown reasons");
            startActivity(i);
            return START_STICKY;
        }
        if (iInitialized)
            return START_STICKY;
        iInitialized = true;
//        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean("ON_REMOVED", false).apply();

        doInit(ConfigurationUtilities.mTrace ? 1 : 0);
        initJNI(getBaseContext());
        checkNet(getBaseContext());
        notificationReceiver = DialerActivity.class;
        if (!mIsReady)
            startForeground(R.drawable.ic_launcher_sp, setNewInfo());
        getPhoneState();
        LocaleChangeReceiver.setPhoneService(this);

        // The start messaging handler registers once AxoMessaging is ready and also
        // sets the ready flag.
        mHandler.sendEmptyMessage(START_MESSAGING);
        reScheduleGcmRegistration(200);

        return START_STICKY;
    }

    public static void reScheduleGcmRegistration(int delay) {
        if (phoneService != null)
            phoneService.mHandler.sendEmptyMessageDelayed(RESCHEDULE_GCM, delay);
    }

    private void setupMessaging() {
        try {
            AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
            axoMessaging.initialize();
            if (!axoMessaging.isReady()) {
                mHandler.sendEmptyMessageDelayed(START_MESSAGING, 1000);
            }
            else {
                AsyncTasks.asyncCommand(":reg"); // doCmd(":reg"); - do it async to avoid ANR in case network is slow
                mIsReady = true;
            }
        } catch (Exception e) {
            // If something happens during Axo initialization then at least register
            // to be able to receive calls.
            AsyncTasks.asyncCommand(":reg"); // doCmd(":reg"); - do it async to avoid ANR in case network is slow
            mIsReady = true;
            Log.e(LOG_TAG, "Initialization of messaging failed", e);
        }
    }

    public void contactObserverRegister() {
        getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, mChangeObserver);
    }

    public void contactObserverUnregister() {
        getContentResolver().unregisterContentObserver(mChangeObserver);
    }

    public void runContactsUpdater(boolean force) {
        Intent contactsUpdate = new Intent(TiviPhoneService.this, UpdateScContactDataService.class);
        if (force) {
            contactsUpdate.setAction(UpdateScContactDataService.ACTION_UPDATE_FORCE);
        }
        else
            contactsUpdate.setAction(UpdateScContactDataService.ACTION_UPDATE);

        TiviPhoneService.this.startService(contactsUpdate);
    }

    /**
     * Cleanup if a call ends.
     */
    public void onStopCall() {
        final CallState call = calls.getLastCall();
        if (call == null) {      // No more active calls
            mHandler.sendEmptyMessage(KEEP_ALIVE_WAKE_UP);
            ((AudioManager) getSystemService(AUDIO_SERVICE)).abandonAudioFocus(afChangeListener);
        }
    }

    public void onLocaleChanged() {
        setNewInfo();
    }

    /**
     * Reschedule a keep alive alarm.
     *
     * @param delay millisecond to get the next alarm
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void rescheduleWakeAlarm(final int delay) {
        final Intent intent = new Intent();
        intent.setAction(KEEP_ALIVE_ALARM);
        final PendingIntent pending = PendingIntent.getBroadcast(this, KEEP_ALIVE_WAKE_UP, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pending);
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            alarmManager.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, 1000 * 60, pending);
        else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pending);
        }
    }

    private void updateRecents(final CallState c) {
        calls.onUpdateRecents(c);
        insertCallLog(c);
    }

    /**
     * Notifies all registered {@link CallStateChangeListener} that ZRTP state changed.
     *
     * @param call  the  CallState
     * @param msg the new ZRTP call state
     */
    private void zrtpStateChanged(final CallState call, final TiviPhoneService.CT_cb_msg msg) {
        synchronized (callStateListeners) {
            for (ServiceStateChangeListener l : callStateListeners)
                l.zrtpStateChange(call, msg);
        }
    }

    /**
     * Notifies all registered {@link CallStateChangeListener} that Call state changed.
     *
     * @param call the CallState
     * @param msg the new SIP call state
     */
    private void callStateChanged(final CallState call, final TiviPhoneService.CT_cb_msg msg) {
        synchronized (callStateListeners) {
            for (ServiceStateChangeListener l : callStateListeners)
                l.callStateChange(call, msg);
        }
    }

    /**
     * Notifies all registered {@link DeviceStateChangeListener} that a device state changed.
     */
    private void deviceStateChanged(final int stateChanged) {
        synchronized (deviceStateListeners) {
            for (DeviceStateChangeListener l : deviceStateListeners)
                l.deviceStateChange(stateChanged);
        }
    }

    private void checkMedia(final CallState call, final String str) {
        // start stop video
       call.videoMediaActive = !str.equalsIgnoreCase("audio");
       callStateChanged(call, CT_cb_msg.eNewMedia);
    }

    // Callback from native code to set or release a WakeLock during SIP processing
    // to avoid deep sleep if SIP threads call system functions that could cause to
    // enter CPU sleep mode. The SIP receiver thread should always run to it's end.
    void wakeCallback(int iLock) {
        // 1 - Lock, 0 - unlock
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "WakeCallback: " + (iLock > 0 ? "start" : "stop"));
        if (iLock > 0) {
            mSipWakeLock.start();
        }
        else {
            mSipWakeLock.stop();
        }
    }

    /**
     * Callback from C/C++ code on status changes.
     *
     * @param iEngID  the Tivi engine id
     * @param iCallID the internal call id
     * @param messageId   the new status message/id
     * @param str     an optional string for the new status
     */
    void stateChangeCallback(final int iEngID, final int iCallID, int messageId, final String str) {

        if (messageId < 0 || messageId >= CT_cb_msg.eLast.ordinal()) {
            Log.w(LOG_TAG, "Error: messageId < 0 || messageId >= eLast.ordinal(): " + messageId + ", ignored");
            return;
        }
        CT_cb_msg en = CT_cb_msg.values()[messageId];
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "callback_msg=" + en.name() + " msg=" + str + " iCallID=" + iCallID);

        // Try to get the currently active call. For outgoing call it may return 'null' if
        // we not yet saw a eCalling state.
        CallState call = calls.findCallById(iCallID);

        String p = null;

        // If no active call, check if we just got a REGISTER notification or some error indication
        if (call == null && (en == CT_cb_msg.eReg || (iCallID == 0 && en == CT_cb_msg.eError))) {
            String msg = null;
            if (str != null) {
                msg = str.trim();
                if (!TextUtils.isEmpty(msg)) {
                    if ("Network not available".compareToIgnoreCase(msg) == 0)
                        msg = getString(R.string.no_internet);
                    else if (msg.contains("under MITM attack")) {
                        msg = getString(R.string.server_cert_failure);
                    }
                }
                msg = TextUtils.isEmpty(msg) ? null : msg;
            }
            setNewInfo(msg);
            callStateChanged(null, en);
            return;
        }

        // prepare ste call state for an incoming call
        if (en == CT_cb_msg.eIncomingCall) {
            call = calls.getEmptyCall();
            if (call == null) {
                return;
            }
            p = getString(R.string.type_incoming);
            call.iEngID = iEngID;
            call.iCallId = iCallID;
            call.iIsIncoming = true;
            call.iShowVideoSrcWhenAudioIsSecure = false;
            call.initialStates = CT_cb_msg.eIncomingCall;

            String priority = TiviPhoneService.getInfo(iEngID, iCallID, "getPriority"); //can call this fnc after receiving eIncomingCall
            if (!TextUtils.isEmpty(priority)) {
                if ("urgent".compareToIgnoreCase(priority) == 0)
                    call.mPriority = CallState.URGENT;
                if ("emergency".compareToIgnoreCase(priority) == 0)
                    call.mPriority = CallState.EMERGENCY;
            }

            String[] fromHeaderFields = Utilities.splitFields(str, ";");
            if (fromHeaderFields != null)
                // Set caller's name / number in call info data
                call.setPeerName(fromHeaderFields[0]);

            String assertedId = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "AssertedId");

            // If an incoming call does not have an PAI header then this is an OCA call
            if (!TextUtils.isEmpty(assertedId)) {
                String[] aiFields = Utilities.splitFields(assertedId, ";");
                if (aiFields != null)
                    call.mAssertedName.setText(aiFields[0]);
            }
            else {
                call.isOcaCall = true;
            }
            call.fillDataFromContacts(this);

            // if this is the first (or only) incoming call then this is the active call too
            if (calls.getCallCount() == 1) {
                calls.setCurCall(call);
                showCallScreen(CALL_TYPE_INCOMING, str, false);
            }
        }
        // User triggered an outgoing call. The Call engine reports this back:
        // - lookup the call state object for this dialed number
        // - if not such call state found terminate the call id, illegal call
        // - DialpadFragment initializes a call structure and fills is data, mainly
        //   bufDialed which is the data we use to dial. This is usually shorter than
        //   the 'str' we get from SIP stack which has a "sip:" scheme. findCallByNumber
        //   locates the best match of 'bufDialed' inside "str".
        if (en == CT_cb_msg.eCalling) {
            if (call == null || call.iCallId != 0) {
                call = calls.findCallByNumberAndNoCallID(str);
                if (call == null) {                  // an unknown call, terminate it
                    Log.w(LOG_TAG, "Terminate an unknown/unexpected call (Calling), call id: " + iCallID);
                    Message msg = mHandler.obtainMessage(TERMINATE_CALL, iCallID);
                    mHandler.sendMessageDelayed(msg, 100);
                    return;
                }
            }
            p = getString(R.string.sip_state_calling);
            call.iEngID = iEngID;
            call.iCallId = iCallID;
            call.initialStates = CT_cb_msg.eCalling;

            // Set caller's name / number in call info data. On outgoing call usually not
            // necessary, but better safe than sorry :-)
            String[] fromHeaderFields = Utilities.splitFields(str, ";");
            if (fromHeaderFields != null)
                // Set caller's name / number in call info data
                call.setPeerName(fromHeaderFields[0]);
            call.fillDataFromContacts(this);
        }

        if (call == null) {
            if (en == CT_cb_msg.eRinging || en == CT_cb_msg.eStartCall) {
                Log.w(LOG_TAG, "Terminate an unknown/unexpected call, call id: " + iCallID);
                doCmd("*e" + iCallID);
            }
            return;
        }
        call.iUpdated++;

        switch (en) {
        case eNewMedia:
            checkMedia(call, str);
            break;

        case eEnroll:
            call.iShowVerifySas = false;
            call.iShowEnroll = true;
            zrtpStateChanged(call, en);
            break;

        case eZRTP_peer_not_verified:
            call.iShowVerifySas = true;
            if (str != null)
                call.zrtpPEER.setText(str);
            zrtpStateChanged(call, en);
            break;

        case eZRTP_peer:
            if (str == null) {
                call.iShowVerifySas = true;
            }
            else {
                call.zrtpPEER.setText(str);
                call.iShowVerifySas = false;
            }
            zrtpStateChanged(call, en);
            break;

        case eZRTPMsgV:
            if (str != null)
                call.bufSecureMsgV.setText(str);
            zrtpStateChanged(call, en);
            break;

        case eZRTPMsgA:
            if (str != null)
                call.bufSecureMsg.setText(str);
            zrtpStateChanged(call, en);
            break;

        case eZRTPErrV:
            call.bufSecureMsgV.setText("ZRTP Error");
            if (str != null)
                call.zrtpWarning.setText(str);
            zrtpStateChanged(call, en);
            break;

        case eZRTPErrA:
            call.bufSecureMsg.setText("ZRTP Error");

        case eZRTPWarn:
            if (str != null)
                call.zrtpWarning.setText(str);
            zrtpStateChanged(call, en);
            break;

        case eZRTP_sas:
            if (str != null)
                call.bufSAS.setText(Utilities.formatSas(str));
            final String flag = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.zrtp.peerDisclosureFlag");
            call.mPeerDisclosureFlag = ("1".equals(flag));
            zrtpStateChanged(call, en);
            break;

        case eSIPMsg:
            p = str;
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "SIP message: '" + p);
            break;

            // This case is for outgoing calls
        case eRinging:
            p = getString(R.string.sip_state_ringing);
            call.mAssertedName.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, "AssertedId"));
            call.initialStates = CT_cb_msg.eRinging;
            break;

            // The engine sends eEndCall on outgoing and incoming calls. Setting callEnded to
            // true also removes the call from "private" or conference" state {@link ManageCallStates.isCallType()}
        case eEndCall:
            if (call.mAssertedName.getLen() == 0) {
                call.mAssertedName.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, "AssertedId"));
            }
            if (!call.hasSipErrorMessage) {
                if (str != null && "Call completed elsewhere".compareToIgnoreCase(str) == 0)
                    call.mAnsweredElsewhere = true;
                p = getString(R.string.sip_state_ended);
            }
            break;

            // The engine sends eStartCall on outgoing and incoming calls
        case eStartCall:
            p = " ";                // Call is active but not yet "answered" ;
            call.iActive = true;
            if (call.mAssertedName.getLen() == 0) {
                call.mAssertedName.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, "AssertedId"));
            }
            if (call.uiStartTime == 0)
                call.uiStartTime = System.currentTimeMillis();

            if (call.bufSecureMsg.getLen() == 0) {
                call.bufSecureMsg.setText(getString(R.string.sip_state_connecting));
                zrtpStateChanged(call, en);
            }
            break;

        case eError:
            if (str != null)
                p = translateSipErrorMsg(str);
            else {
                p = getString(R.string.call_error);
            }
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Some SIP error message: '" + p);
            call.hasSipErrorMessage = true;
            break;

        default:
            break;
        }

        if (p != null) {
            call.bufMsg.setText(p);
        }
        callStateChanged(call, en);

        // Delay end-call processing after UI update to avoid inconsistency with monitoring
        // thread of InCall activity. The first part just displays the end call message and the
        // following part triggers the real end-call processing by the monitoring thread.
        if (en == CT_cb_msg.eEndCall) {
                call.callEnded = true;
                updateRecents(call);
                onStopCall();
        }
    }

    @SuppressLint("DefaultLocale")
    private String translateSipErrorMsg(final String msg) {

        // Trim string and force to lowercase - easier to match
        String trimmed = msg.trim().toLowerCase();

        if (trimmed.startsWith("user not found"))
            return ERROR_MSG_PREFIX + getString(R.string.sip_error_no_user);

        if (trimmed.startsWith("cannot connect")) {
            if (trimmed.contains("decline"))
                return ERROR_MSG_PREFIX + getString(R.string.sip_error_decline);

            // Add more specific messages if necessary, based on string parsing
            return ERROR_MSG_PREFIX  + getString(R.string.sip_error_generic);

        }
        if (trimmed.startsWith("remote party is out of coverage"))
            return ERROR_MSG_PREFIX + getString(R.string.sip_error_no_cover);
        if (trimmed.startsWith("could not reach server"))
            return ERROR_MSG_PREFIX + getString(R.string.sip_error_no_server);
        if (trimmed.startsWith("cannot register"))
            return ERROR_MSG_PREFIX + getString(R.string.sip_error_register);
        if (trimmed.startsWith("user not online"))
            return ERROR_MSG_PREFIX + getString(R.string.sip_error_not_online);

        return ERROR_MSG_PREFIX + msg;
    }

    public static boolean isInitialized() {
        return iInitialized;
    }

    public static int initJNI(final Context ctx) {
        final String instanceDevId = getInstanceDeviceId(ctx, false);
        saveImei(instanceDevId);

        final String hwDevId = getHwDeviceId(ctx);
        final String hwMd5 = Utilities.hashMd5(hwDevId);
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Hardware device id: " + hwDevId + ", hash: " + hwMd5);

        final String instanceMd5 = Utilities.hashMd5(instanceDevId);
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Instance device id: " + instanceDevId + ", hash: " + instanceMd5);

        File f = ctx.getFilesDir();
        if (f != null)
            savePath(f.toString());
        // safety: if not use develop config force network config to 0
        int configuration = ConfigurationUtilities.mUseDevelopConfiguration ? ConfigurationUtilities.mNetworkConfiguration : 0;
        initPhone(configuration, ConfigurationUtilities.mTrace ? 1 : 0, BuildConfig.VERSION_NAME);
        doCmd("g.setLevel " + Build.VERSION.SDK_INT);
        return 0;
    }

    @NonNull
    public static String getInstanceDeviceId(Context ctx, boolean newId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        String devIdPref;
        if (newId) {
            mInstanceDeviceId = null;
            devIdPref = "dev_" + System.currentTimeMillis();
            pref.edit().putString(ConfigurationUtilities.getDevIdKey(), devIdPref).apply();
        }
        if (mInstanceDeviceId == null) {
            String idData = getHwDeviceId(ctx);
            devIdPref = pref.getString(ConfigurationUtilities.getDevIdKey(), null);
            mInstanceDeviceId = (devIdPref == null) ? idData : idData + devIdPref;
        }
        return mInstanceDeviceId;
    }

    @NonNull
    public static String getHwDeviceId(Context ctx) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        String devIdPref = pref.getString(ConfigurationUtilities.getHwDevIdSaveKey(), null);

        // Event if we got the stored HW device id create it from the system data. We
        // just do some consistency checks to report problems.
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        PackageManager pm = ctx.getPackageManager();
        boolean hasPhone = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        String idData = (hasPhone) ? tm.getDeviceId() : null;

        if (idData == null) {
            idData = Build.SERIAL
                    + " "
                    + android.provider.Settings.Secure.getString(ctx.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);
        }
        else {
            final String add = Build.SERIAL
                    + " "
                    + android.provider.Settings.Secure.getString(ctx.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);

            idData += " " + add;
        }
        if (devIdPref == null) {
            devIdPref = idData;
            pref.edit().putString(ConfigurationUtilities.getHwDevIdSaveKey(), devIdPref).apply();
        }
        else if (!devIdPref.equals(idData)) {
            final String msg = ctx.getString(R.string.hw_device_id_mismatch, devIdPref, idData);
            Log.e(LOG_TAG, msg);
        }
        return devIdPref;
    }

    public void setNotificationToDialer() {
        notificationReceiver = DialerActivity.class;
        setNewInfo();
    }

    public void setNotificationToIncall() {
        notificationReceiver = InCallActivity.class;
        setNewInfo();
    }

    private Notification setNewInfo() {
        return setNewInfo(null);
    }

    /*
         String alias_list = TiviPhoneService.getInfo(call.iEngID, -1, "AssociatedURI");
     this will return a ',' separated list from 200Ok_Reg.

     It is possible to set using
     TiviPhoneService.getInfo(iEngID, -1, "AssociatedURI=sip:whatever_alias@example.com");
     where iEngID is 0

     //c, obj-c similar to java
     void *eng = getAccountByID(0,1);
     const char *alias_list = sendEngMsg(eng, "AssociatedURI");
     //or
     sendEngMsg(eng, "AssociatedURI=sip:whatever_alias@example.com");

     */
    private synchronized Notification setNewInfo(final String info) {
        CharSequence text;
        final int ico = R.drawable.ic_notification;

        int i = getPhoneState();
        if (i == 2) {
            text = getString(R.string.sip_state_online);
            if (info != null) {
                text = text + " - " + info;
            }
            AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
            if (axoMessaging.isRegistered())
                axoMessaging.sendEmptySyncToSiblings();

//            String aliases = TiviPhoneService.getInfo(0, -1, "AssociatedURI");
//            parseAndStoreAssociatedUris(aliases);
        }
        else if (i == 1) {
            text = getString(R.string.sip_state_connecting);
        }
        else {
            text = getString(R.string.sip_state_offline);
            if (info != null) {
                text = text + " - " + info;
            }
        }

        Intent intent = new Intent(this, notificationReceiver);
        intent.setAction("notification");
        Bundle bundle = new Bundle();
        bundle.putInt(TiviPhoneService.CALL_TYPE, TiviPhoneService.CALL_TYPE_RESTART);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (notifyLargeIcon == null)
            notifyLargeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_sp);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(ico)
                .setLargeIcon(notifyLargeIcon)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        notificationManager.notify(R.drawable.ic_launcher_sp, notification);

        return notification;
    }

    public void showCallScreen(final int callType, final String destination, final boolean isOcaCall) {
        mHandler.removeMessages(KEEP_ALIVE_WAKE_UP);
        mWakeLock.start();
        pwlShowScreen++;
        if (ConfigurationUtilities.mTrace) Log.i(LOG_TAG, "PartialWake - showCallScreen. Count: " + pwlShowScreen +", at: " + System.currentTimeMillis());

        // The Intent uses the service's context (this), thus we need to set NEW_TASK.
        // A Service is not an Activity context
        final Intent intent = new Intent();
        intent.setClass(this, InCallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final Bundle bundle = new Bundle();
        bundle.putString(CALL_NAME, destination);
        bundle.putInt(CALL_TYPE, callType);
        bundle.putBoolean(IS_OCA_CALL, isOcaCall);
        intent.putExtras(bundle);

        setNotificationToIncall();
        startActivity(intent);

        // Silence any music playing applications. If they are well behaved the stop playing, resume after
        // we release the audio focus after all calls are terminated.
        final int result = ((AudioManager) getSystemService(AUDIO_SERVICE)).requestAudioFocus(afChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "audio focus result: " + result);
    }

    /**
     * Adds the given <tt>CallStateChangeListener</tt> to the list of call state change listeners.
     *
     * @param l
     *            the <tt>CallStateChangeListener</tt> to add
     */
    public void addStateChangeListener(final ServiceStateChangeListener l) {
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
    public void removeStateChangeListener(final ServiceStateChangeListener l) {
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
    public void addDeviceChangeListener(final DeviceStateChangeListener l) {
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
    public void removeDeviceChangeListener(final DeviceStateChangeListener l) {
        synchronized (deviceStateListeners) {
            deviceStateListeners.remove(l);
        }
    }

    public void onIncomingCall(Context ctx) {
        startRinger(ctx);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    private void startRinger(Context ctx) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        final int ringerMode = am.getRingerMode();

        if (ringerMode != AudioManager.RINGER_MODE_SILENT) {

            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                String ringtonePreferenceKey = SettingsFragment.RINGTONE_KEY;
                if (calls.selectedCall.mPriority == CallState.EMERGENCY) {
                    ringtonePreferenceKey = SettingsFragment.RINGTONE_EMERGENCY_KEY;
                }
                String toneString = prefs.getString(ringtonePreferenceKey, null);

                Uri defaultRingtone;
                if (TextUtils.isEmpty(toneString))
                    defaultRingtone = RingtoneUtils.getDefaultRingtoneUri(ctx);
                else
                    defaultRingtone = Uri.parse(toneString);
                final Uri tone = calls.selectedCall.customRingtoneUri == null ? defaultRingtone : calls.selectedCall.customRingtoneUri;

                ringtone = RingtoneManager.getRingtone(TiviPhoneService.this, tone);
                if (ringtone != null) {
                    if (!isHeadsetPlugged())
                        Utilities.turnOnSpeaker(this, true, false);    // Speaker on, but don't set state
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
                    }
                    else {
                        ringtone.setStreamType(AudioManager.STREAM_RING);
                    }
                    ringtone.play();
                }
            }
            if ((ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_NORMAL)) {
                if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    int vib = Settings.System.getInt(getContentResolver(), "vibrate_when_ringing", 0);
                    if (vib == 0)
                        return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    vibrator.vibrate(new long[]{VIBRATE_LENGTH, PAUSE_LENGTH}, 0, new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
                }
                else {
                    vibrator.vibrate(new long[]{VIBRATE_LENGTH, PAUSE_LENGTH}, 0);
                }
            }
        }
    }

    public void stopRinger() {
        if (ringtone != null && ringtone.isPlaying()) {
            Utilities.restoreSpeakerMode(this);
            ringtone.stop();
        }
        // Also immediately cancel any vibration in progress.
        vibrator.cancel();
        ringtone = null;
    }

    public boolean isHeadsetPlugged() {
        return isHeadsetPlugged;
    }

    @SuppressWarnings("unused")
    public int getDockingState() {
        return dockingState;
    }

    /**
     * Get status of secret key processing.
     *
     * @return secret key processing status
     * @see #SECURE_KEY_NOT_AVAILABLE
     * @see #SECURE_KEY_AVAILABLE
     */
    public static int getSecretKeyStatus() {
        return secretKeyStatus;
    }

    /**
     * Hand over the computed secret key to C++ functions.
     *
     * The method clears the data as soon as it was handed over to the C++ functions.
     *
     * @param secKey the secret key.
     */
    public static void setSecretKey(final byte[] secKey) {
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "set secret key to C++");

        // call C++ (native) function here to hand over the key
        setKeyData(secKey);
        secretKeyStatus = SECURE_KEY_AVAILABLE;

        Arrays.fill(secKey, (byte) 0);
    }

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
            if (action == null)
                return;
            switch (action) {
                case Intent.ACTION_HEADSET_PLUG:
                    isHeadsetPlugged = (intent.getIntExtra("state", 0) == 1);
                    deviceStateChanged(EVENT_WIRED_HEADSET_PLUG);
                    break;
                case Intent.ACTION_BATTERY_LOW:
                    // notifier.sendBatteryLow(); // Play a warning tone if in-call
                    break;
                case Intent.ACTION_DOCK_EVENT:
                    dockingState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED);
                    deviceStateChanged(EVENT_DOCK_STATE_CHANGED);
                    break;
                case AudioManager.RINGER_MODE_CHANGED_ACTION:
                    int ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
                    if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                        stopRinger();
                    }
                    break;
            }
        }
    }

    /* ********************************************************************
     * Handle GSM/CDMA call state changes.
     * The policy we use here:
     * - If GSM/CDMA state changes and no active SP call - just ignore
     * - if GSM/CDMA state changes and we have one or more active SP calls:
     *   = state RINGING: set Ringer to silent mode, play notification tone to SP call
     *   = state OFF_HOOK: stop notification tone, set active SP calls to Hold mode
     *   = state IDLE: restore ringing mode, restore SP calls to previous mode
     *
     ******************************************************************** */
    private Integer savedRingerMode;
    private ToneGenerator mToneGenerator;
    private ArrayList<CallState> callOnHoldList = new ArrayList<>(15);

    private void playInCallTone() {
        if (mToneGenerator == null) {
            try {
                mToneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);
                mToneGenerator.startTone(ToneGenerator.TONE_SUP_CALL_WAITING);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Exception caught while creating local tone generator.");
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
        CallState call;
        int cnt = calls.getCallCount();
        if (cnt > 0) {
            for (int i = 0; i < ManageCallStates.MAX_GUI_CALLS; i++) {
                call = TiviPhoneService.calls.getCall(i);
                if (call != null) {
                    if (ManageCallStates.isCallType(call, ManageCallStates.eStartupCall)) {
                        doCmd("*e" + call.iCallId);
                        continue;
                    }
                    if (!call.iIsOnHold) {
                        callOnHoldList.add(call);
                        doCmd("*h" + call.iCallId);
                        call.iIsOnHold = true;
                    }
                }
            }
        }
    }

    private void insertCallLog(CallState call){

        if(call == null || call.iRecentsUpdated)
            return;
        call.iRecentsUpdated = true;

        final Uri uri = ScCallLog.CONTENT_URI.buildUpon().appendQueryParameter(ScCallLog.NON_BLOCKING, "true").build();
        if (uri == null)
            return;
        InsertCallLogHelper loaderTask = new InsertCallLogHelper(this, call);
        loaderTask.execute(uri);
    }

    /**
     * Sets all calls to no-hold mode if not already in hold mode.
     *
     * This method sets all calls in <code>callOnHoldList</code>to no-hold mode.
     */
    private void setCallsNoHold() {
        int cnt = callOnHoldList.size();

        for (int i = 0; i < cnt; i++) {
            CallState call = callOnHoldList.get(0);
            if (call == null)
                continue;
            doCmd("*u" + call.iCallId);
            callOnHoldList.remove(0);
            call.iIsOnHold = false;
        }
    }

    private boolean mTunFound;
    private void checkForTun() {
        File sysDir = new File("/sys/class/net");
        if (sysDir.isDirectory()) {
            File[] sysNetFiles = sysDir.listFiles();
            mTunFound = false;
            if (sysNetFiles == null)
                return;
            for (File netFile : sysNetFiles) {
                if (netFile.getName().startsWith("tun")) {
                    mTunFound = true;
                    break;
                }
            }
        }
    }

    private void handleSysNetChange() {
        boolean oldTunFound = mTunFound;
        checkForTun();
        if (oldTunFound != mTunFound) {
            traceNetworkState("FileObserver event: network reset command");
            doCmd(":force.network_reset");
        }
    }

    private class SilentPhoneStateReceiver extends PhoneStateListener {

        @Override
        public void onCallStateChanged(final int state, final String incomingNumber) {
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Call state has changed !" + state + " : " + incomingNumber);
            if (state != TelephonyManager.CALL_STATE_IDLE) {

                // If we have active SP calls then manage Ringing and notification tones, otherwise just do nothing
                if (calls.getCallCount() >= 1) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Call state has changed - ringing!");
                        // Avoid ringing, play a notification tone
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        savedRingerMode = am.getRingerMode();
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                        playInCallTone();
                    }
                    else {
                        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Call state has changed - off-hook!");
                        stopInCallTone();
                        setCallsHold();
                    }
                }
                else {
                    TiviPhoneService.doCmd(":GSMactive 1");
                }
            }
            else {
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Call state has changed - idle!");
                // In case we not had an active SP call when GSM/CDMA call happened the most of the following
                // action are no-ops
                // Normal phone is back in IDLE state, reset ringerMode if it was changed
                if(savedRingerMode != null) {
                    AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                    am.setRingerMode(savedRingerMode);
                    savedRingerMode = null;
                }
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

        private long prevTime;
        private int mShortReleases;

        InternalHandler(TiviPhoneService parent) {
            mTarget = new WeakReference<>(parent);
        }

        @Override
        public void handleMessage(Message msg) {
            TiviPhoneService parent = mTarget.get();
            if (parent == null)
                return;

            if (msg.what == TERMINATE_CALL) {
                doCmd("*e" + msg.arg1);
                return;
            }

            if (msg.what == CHECK_NET) {
                parent.mSysNetObserver.stopWatching();
                parent.handleSysNetChange();
                // This is a hack: if we install multiple APK for testing purposes the restart
                // only for the main APK, for other APKs run it only once
                if (BuildConfig.MAIN_PACKAGE)
                    parent.mSysNetObserver.startWatching();
                return;
            }

            if (msg.what == START_MESSAGING) {
                parent.setupMessaging();
                return;
            }
            if (msg.what == RESCHEDULE_GCM) {
                Intent gcmIntent = new Intent(parent, RegistrationIntentService.class);
                parent.startService(gcmIntent);
                return;
            }
            boolean cc = TiviPhoneService.calls.getLastCall() != null;
            int ret = 10;
            if (!cc)
                ret = TiviPhoneService.doCmd("getint.ReqTimeToLive");

            long now = SystemClock.elapsedRealtime();
            long diff = now - prevTime;

            switch (msg.what) {
            case KEEP_ALIVE_WAKE_UP:
                if (ret <= 0) {
                    if (ConfigurationUtilities.mTrace)
                        Log.d("KEEP_ALIVE",
                                "keep-alive wake-up at: " + now + ", diff: " + diff + ", sys-time: " + System.currentTimeMillis());
                    TiviPhoneService.doCmd(":X");
                    parent.mHandler.sendEmptyMessage(KEEP_ALIVE_RELEASE);
                }
                else {
                    if (ConfigurationUtilities.mTrace) Log.d("KEEP_ALIVE", "delay keep alive-0 in sec: " + ret);
                    parent.mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, 1000);
                }
                break;
            case KEEP_ALIVE_RELEASE:
                removeMessages (msg.what);
                if (ret <= 0) {
                    if (ConfigurationUtilities.mTrace) Log.d("KEEP_ALIVE", "sleeping at: " + now);
                    parent.rescheduleWakeAlarm(KEEP_ALIVE_WAKE_UP_TIME);
                    mShortReleases = 0;
                    parent.mWakeLock.stop();
                }
                else {
                    if (ConfigurationUtilities.mTrace) Log.d("KEEP_ALIVE", "delay keep alive-1 in sec: " + ret);
                    parent.mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_RELEASE, 1000);
                    mShortReleases++;
                    if (mShortReleases > 120) {
                        mShortReleases = 0;
                        Log.w(LOG_TAG, "Force network reset after continuous retries");
                        doCmd(":force.network_reset");
                    }
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
            if (!mWakeLock.isHeld()) {
                mWakeLock.start();
                pwlAlarmWake++;
                if (ConfigurationUtilities.mTrace) Log.i(LOG_TAG, "PartialWake - alive alarm. Count: " + pwlAlarmWake +", at: " + System.currentTimeMillis());
                mHandler.sendEmptyMessageDelayed(KEEP_ALIVE_WAKE_UP, 2);
            }
        }
    }

    private BluetoothHeadset bluetoothHeadsetProxy;
    private BluetoothAdapter bluetoothAdapter;
    private boolean headsetProxyListenerAdded;
    private BluetoothProfile.ServiceListener mProfileListener;
    private boolean btHeadsetScoActive;

    /* Broadcast receiver for the SCO State broadcast intent.*/
    private final BroadcastReceiver headsetAudioState = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            if(ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "BT HeadsetAudioState onReceive");

            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);

            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "BT SCO is connected");
                btHeadsetScoActive = true;
                deviceStateChanged(EVENT_BT_HEADSET_SCO_ON);
            }
            else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "BT SCO is disconnected");
                btHeadsetScoActive = false;
                deviceStateChanged(EVENT_BT_HEADSET_SCO_OFF);
            }
        }
    };

    public boolean btHeadsetScoActive() {
        return btHeadsetScoActive;
    }

    public void bluetoothHeadset(boolean on) {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (on) {
            audioManager.startBluetoothSco();
        }
        else {
            audioManager.stopBluetoothSco();
        }
    }

    public boolean checkBluetoothDeviceAvailable() {
        // Get the local Bluetooth adapter
        if (bluetoothAdapter == null)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is still null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            return false;
        }
        // Check whether BT is enabled
        if (!bluetoothAdapter.isEnabled()) {
            return false;
        }
        // Check list of bonded devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices != null && pairedDevices.size() > 0) {
            if(ConfigurationUtilities.mTrace)                 // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {
                    Log.d(LOG_TAG, "BT Device: "+ device.getName());
                }
        }
        else {
            Log.i(LOG_TAG, "No paired device.");
            return false;
        }
        return true;
    }

    private void addBtProfileListener() {
        // Establish connection to the BT Headset proxy.
        if (mProfileListener == null) {
            mProfileListener = new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile != BluetoothProfile.HEADSET)
                        return;

                    if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Got BT headset profile proxy");
                    bluetoothHeadsetProxy = (BluetoothHeadset) proxy;

                    // Now check if we have a headset. If yes -> check if the first supports SCO audio
                    List<BluetoothDevice> headSets = bluetoothHeadsetProxy.getConnectedDevices();
                    if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Number of BT headsets (P): " + headSets.size());
                    deviceStateChanged(EVENT_BT_HEADSET_ADDED);
                }

                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Remove BT headset profile proxy");
                        bluetoothHeadsetProxy = null;
                        deviceStateChanged(EVENT_BT_HEADSET_REMOVED);
                    }
                }
            };
        }
        bluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);
        headsetProxyListenerAdded = true;

    }

    public boolean hasBtHeadSet() {
        if (bluetoothHeadsetProxy == null)
            return false;
        List<BluetoothDevice> headSets = bluetoothHeadsetProxy.getConnectedDevices();
        if(ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Number of available BT headsets (C): " + headSets.size());
        return (headSets.size() > 0);
    }

    /* Broadcast receiver for the SCO State broadcast intent.*/
    private final BroadcastReceiver bluetoothState = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1);
            if(ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "BT connection state: " + state);

            if (state == BluetoothAdapter.STATE_CONNECTED) {
                addBtProfileListener();
            }
            else if (state == BluetoothAdapter.STATE_DISCONNECTING || state == BluetoothAdapter.STATE_DISCONNECTED) {
                if (headsetProxyListenerAdded) {
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadsetProxy);
                    headsetProxyListenerAdded = false;
                    bluetoothHeadsetProxy = null;
                }
                btHeadsetScoActive = false;
                deviceStateChanged(EVENT_BT_HEADSET_SCO_OFF);   // make sure call window knows it
            }
        }
    };
}

