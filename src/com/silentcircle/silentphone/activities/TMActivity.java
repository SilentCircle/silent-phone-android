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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.silentcircle.keymngrspa.KeyManagerSupport;
import com.silentcircle.silentcontacts.ScCallLog;
import com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.fragments.InfoFragment;
import com.silentcircle.silentphone.preferences.SpPreferenceActivity;
import com.silentcircle.silentphone.preferences.SpPreferenceFragment;
import com.silentcircle.silentphone.receivers.OCT;
import com.silentcircle.silentphone.utils.CTFlags;
import com.silentcircle.silentphone.utils.CTStringBuffer;
import com.silentcircle.silentphone.utils.DeviceDetectionVertu;
import com.silentcircle.silentphone.utils.DeviceHandling;
import com.silentcircle.silentphone.utils.FontFitTextView;
import com.silentcircle.silentphone.utils.Utilities;
import com.silentcircle.silentphone.views.CallScreen;

public class TMActivity extends SherlockFragmentActivity {

    private static final String LOG_TAG = "TMActivity";

    private static String SILENT_CALL_ACTION = "com.silentcircle.silentphone.action.NEW_OUTGOING_CALL";
    private static String SILENT_EDIT_BEFORE_CALL_ACTION = "com.silentcircle.silentphone.action.EDIT_BEFORE_CALL";
    private static String SILENT_PHONE_CHECK_ACCOUNT = "com.silentcircle.silentphone.action.CHECK_ACCOUNT";
    private static String CALL_PRIVILEGED = "android.intent.action.CALL_PRIVILEGED";

    private FontFitTextView dialText;
    public static CTStringBuffer lastDialedNumber= new CTStringBuffer();

    private static final int CONTACT_PICKER_RESULT = 1001;
    private static final int CONTACT_PICKER_RESULT_SILENT = 1002;
    private static final int PROVISIONING_RESULT = 47118;
    private static final int KEY_MANAGER_READY = 7119;
    private static final int DUMMY_RESULT = 11;

    private static boolean keyManagerInfoShown;

    /**
     * Play a DTMF tone for 200ms
     */
    private static int DTMF_TONE_DURATION = 200;

    /**
     * Set to true by onNewIntent if user adds a new call, reset in onPause
     */
    private boolean switchOnSpeaker;

    public static boolean SP_DEBUG;

    public boolean bShowCfg;
    private boolean phoneIsBound;
    private TiviPhoneService phoneService;
    private boolean provisioningDone;
    
    private TextView countryFlag;

    private InfoFragment infoFragment;

    // If true then we know this phone is provisioned. During onCreate we may set this to false.
    private boolean isProvisioned = true;
    private boolean isActionCheckAccount;

    private boolean hasKeyManager;
    private boolean noNumber;       // will be true if this account has name only (no phone number)


    private ToneGenerator mToneGenerator;

    private ServiceConnection phoneConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            phoneService = ((TiviPhoneService.LocalBinder) service).getService();
            phoneIsBound = true;
            serviceBound();
            if (provisioningDone) {
                provisioningDone = false;
                doLaunchInfo();
                // Another application (SilentText mainly) triggered the provisioning. Display info
                // for a few seconds, then return result to Intent sender.
                if (isActionCheckAccount) {
                    Utilities.Sleep(5000);
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            phoneIsBound = false;
            phoneService = null;
        }
    };

    private void doBindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(TMActivity.this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE);
    }

    private void doUnbindService() {
        if (phoneIsBound) {
            // Detach our existing connection.
            unbindService(phoneConnection);
            phoneIsBound = false;
        }
    }

    private void doLaunchHistory() {
        Intent showCallLog = new Intent();
        showCallLog.setAction(Intent.ACTION_VIEW);

        if (phoneService.hasSilentContacts())
            showCallLog.setType(ScCallLog.ScCalls.CONTENT_TYPE);
        else
            showCallLog.setType(CallLog.Calls.CONTENT_TYPE);

        startActivityForResult(showCallLog, DUMMY_RESULT);    
    }

    private void doLaunchContactPicker() {
        Intent contactPickerIntent;

        if (phoneService.hasSilentContacts()) {
            contactPickerIntent = new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI);
            startActivityForResult(contactPickerIntent, CONTACT_PICKER_RESULT_SILENT);
        }
        else {
            contactPickerIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(contactPickerIntent, CONTACT_PICKER_RESULT);
        }
    }

    private void doLaunchInfo() {
        final FragmentManager fragmentManager = this.getSupportFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (dialText != null) {
            if (dialText.hasFocus())
                countryFlag.requestFocus();
        }
        final String INFO_TAG = "spa_info_fragment";
        infoFragment = (InfoFragment)fragmentManager.findFragmentByTag(INFO_TAG);
        if (infoFragment == null) {
            infoFragment = new InfoFragment();
            transaction.add(R.id.start_screen, infoFragment, INFO_TAG);            
        }
        if (hasKeyManager) {
            infoFragment.setKeyManagerStatus(getString(R.string.key_manager_available));
        }
        else {
            infoFragment.setKeyManagerStatus(null);
        }
        transaction.show(infoFragment);
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == DUMMY_RESULT)
            return;

        if (requestCode == PROVISIONING_RESULT) {
            if (resultCode != RESULT_OK) {
                stopServiceAndExit();
            }
            else {
                provisioningDone = true;
                isProvisioned = true;
                keyManagerChecked();
            }
            return;
        }
        if (requestCode == CONTACT_PICKER_RESULT) {
            if (resultCode == RESULT_OK) {
                Uri result = data.getData();
                Cursor c = getContentResolver().query(result, null, null, null, null);
                if (c != null && c.moveToFirst()) {
                    int index = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    if (index < 0) {
                        c.close();
                        return;
                    }
                    String number = c.getString(index);
                    if(number.startsWith(OCT.prefix_check) && (number.length() >= (OCT.prefix_check.length() + 4))){
                        number = number.substring(OCT.prefix_check.length());
                    }
                    else if (number.startsWith(OCT.prefix_add) && number.length() >= OCT.prefix_check.length() + 4) {
                        number = number.substring(OCT.prefix_add.length());
                    }
                    number = number.trim();
                    if (number != null) {
                        setDialText(number);
                    }
                    return;
                }
            }
        }
        if (requestCode == CONTACT_PICKER_RESULT_SILENT) {
            if (resultCode == RESULT_OK) {
                Uri result = data.getData();
                Cursor c = null;
                try {
                    c = getContentResolver().query(result, null, null, null, null);
                } catch (Exception e) {
                    Log.w(LOG_TAG, "Contacts picker query Exception, cannot use contacts data.");
                }
                if (c != null && c.moveToFirst()) {
                    int index = c.getColumnIndexOrThrow(Phone.NUMBER);
                    if (index < 0) {
                        c.close();
                        return;
                    }

                    String number = c.getString(index);
                    number = number.trim();
                    if (number != null) {
                        setDialText(number);
                    }
                    c.close();
                    return;
                }
            }
        }
        if (requestCode == KEY_MANAGER_READY) {
            if (resultCode != RESULT_OK) {
                Log.w(LOG_TAG, "KeyManager READY request failed - exit.");
                stopServiceAndExit();
            }
            else {
                keyManagerChecked();
            }
        }
    }

    void setDialText(String s) {
        if(s.length() == 0 || s.indexOf("+") == 0 || (s.indexOf("1") == 0 && s.length()==11)) {
           setCountryFlag(s);
        }
        String nr = CTFlags.formatNumber(s);

        dialText.setText(nr);
        dialText.setSelection(dialText.getText().length());
    }

    public static boolean bConnectedToServ = false;
    static boolean bUserPressedOffline = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.activity_start_screen, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int iInCall = TiviPhoneService.calls.getCallCnt();

        boolean bServiceStarted = TMActivity.bConnectedToServ && TiviPhoneService.mc != null;
        int iPhoneState = TiviPhoneService.getPhoneState();

        menu.findItem(R.id.start_menu_exit).setVisible(iInCall == 0);
        menu.findItem(R.id.start_menu_login).setVisible(false /*(!bServiceStarted || iPhoneState == 0) */);
        menu.findItem(R.id.start_menu_settings).setVisible(true /*bShowCfg*/);
        menu.findItem(R.id.start_menu_logout).setVisible(false /*bServiceStarted && iInCall == 0 && iPhoneState != 0*/);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                if (infoFragment != null && !infoFragment.isHidden()) {
                    FragmentManager fragmentManager = getSupportFragmentManager();
                    FragmentTransaction ft = fragmentManager.beginTransaction();
                    ft.hide(infoFragment);
                    ft.commitAllowingStateLoss();
                    fragmentManager.executePendingTransactions();
                    if (noNumber && dialText != null) {
                        if (dialText.hasFocus())
                            countryFlag.requestFocus();
                        dialText.requestFocus();
                    }
                }
                if (TiviPhoneService.calls.getCallCnt() >= 1)
                    phoneService.showCallScreen(TiviPhoneService.CALL_TYPE_RESTARTED);
                break;

            case R.id.start_menu_info:
                doLaunchInfo();
                break;

            case R.id.start_menu_contacts:
                doLaunchContactPicker();
                break;

            case R.id.start_menu_recents:
                doLaunchHistory();
                break;

            case R.id.start_menu_exit:
                exitApplication();
                break;

            case R.id.start_menu_logout:
                bUserPressedOffline = true;
                TiviPhoneService.doCmd(":unreg");
                break;

            case R.id.start_menu_login: {
                bUserPressedOffline = false;
                startService(this);
                break;
            }
            case R.id.start_menu_settings:
                final Intent intent = new Intent(this, SpPreferenceActivity.class);
                // as there is only one section right now, make sure it is selected
                // on small screens, this also hides the section selector
                // Due to b/5045558, this code unfortunately only works properly on phones
                boolean settingsAreMultiPane = false; // getResources().getBoolean(com.android.internal.R.bool.preferences_prefer_dual_pane);
                if (!settingsAreMultiPane) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SpPreferenceFragment.class.getName());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE, R.string.preference_options);
                }
                startActivity(intent);
                break;
        }
        return true;
    }

    private void makeCall(boolean b) {

        makeCall(b, dialText.getText().toString());
    }

    private void makeCall(boolean b, String dst) {
        if (dst == null || dst.length() < 1) {
            setDialText(lastDialedNumber.toString());
            return;
        }
        if (dst.indexOf("*##*") == 0) {
            if ("*##*324*".compareTo(dst) == 0) {
                CallScreen.bDbg = !CallScreen.bDbg;
                showToast(CallScreen.bDbg ? "dbg on" : "dbg off");
                return;
            }

            if ("*##*23466*".compareTo(dst) == 0){
               bShowCfg = true;
               return;
            }

            if ("*##*1*".compareTo(dst) == 0){
                showToast(android.os.Build.BRAND+" "+ android.os.Build.MANUFACTURER);
                return;
            }

            if ("*##*8*".compareTo(dst) == 0){
               TiviPhoneService.doCmd(":s");showToast("Save");
               return;
            }

            if(phoneService!=null && "*##*71*".compareTo(dst) == 0){
               phoneService.enableDisableWifiLock(true);
               showToast("Wifi on");
               return;
            }

            if(phoneService!=null && "*##*70*".compareTo(dst) == 0){
               phoneService.enableDisableWifiLock(false);
               showToast("Wifi off");
               return;
            }

            TiviPhoneService.doCmd(dst);
            if("*##*3357768*".compareTo(dst) == 0)
                stopServiceAndExit();

            return;
        }
        String s;
        s = b ? ":v " : ":c ";
        s += dst;

        // remembering the last number dialed, store URIs as we get them, other numbers without separators
        // if a "number" starts with a letter then treat it as "name"
        if (Character.isLetter(dst.charAt(0)) || !PhoneNumberUtils.isGlobalPhoneNumber(dst)) {
            lastDialedNumber.setText(dst);
        }
        else {
            lastDialedNumber.setText(PhoneNumberUtils.stripSeparators(dst));
        }

        if (phoneIsBound) {
            TCallWindow.am(getBaseContext(), true);

            TiviPhoneService.doCmd(s);
            setDialText("");

            phoneService.showCallScreen(TiviPhoneService.CALL_TYPE_OUTGOING);
        }
    }

    public void startService(Context c) {
        // Check and set the secret key

//        if (TiviPhoneService.use_password_key && keymanager....) {
//        }

        if (!bConnectedToServ) {
            c.startService(new Intent(c, TiviPhoneService.class));
            bConnectedToServ = true;
            // service will send ":reg"
        }
        else if (TiviPhoneService.mc != null)
            TiviPhoneService.doCmd(":reg");
        doBindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }
    
    @Override
    protected void onNewIntent (Intent intent) {
        if (phoneIsBound && !phoneService.isHeadsetPlugged()) {
            switchOnSpeaker = true;
        }
        if (TMActivity.SP_DEBUG) Log.v(LOG_TAG, "Received new intent: " + intent);
        processIntent(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SP_DEBUG = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        Log.d(LOG_TAG, "Debug: " + SP_DEBUG);

        TiviPhoneService.initJNI(getBaseContext());
        isProvisioned = !(TiviPhoneService.mc == null && TiviPhoneService.doCmd("isProv") == 0);

        Log.d(LOG_TAG, "received intent: " + getIntent());
        checkAndSetKeyManager();
    }

    // This is the second half of onCreate after check of key manager availability etc.
    //
    // At this point the phone service is not yet active. This function starts the service
    // if phone is provisioned. Otherwise the result of the provisioning activity determines
    // if we can start the service.
    private void keyManagerChecked() {
        setupActionBar();
        if (!isProvisioned) {
            Intent provisioningIntent = new Intent(this, Provisioning.class);
            provisioningIntent.putExtra("KeyManager", hasKeyManager);
            provisioningIntent.putExtra("DeviceId", TiviPhoneService.getDeviceId());
            startActivityForResult(provisioningIntent, PROVISIONING_RESULT);
            return;
        } 
        if (!bUserPressedOffline) {
            startService(this);             // start phone service and bind it
        }
        if (TiviPhoneService.calls.getCallCnt() != 0) {
            TiviPhoneService.mc.showCallScreen(TiviPhoneService.CALL_TYPE_RESTARTED);
        }
    }

    private FrameLayout dialFrame;

    // Called after service was connected, actually the third part of onCreate
    private void serviceBound() {
        setContentView(R.layout.activity_start_screen);

        dialFrame = (FrameLayout)findViewById(R.id.DialPadFrame);
        dialText = (FontFitTextView) findViewById(R.id.DialNumberText);
        ImageButton dialButton = (ImageButton) findViewById(R.id.Number_call);
        countryFlag = (TextView) findViewById(R.id.StartFlagField);
        ImageButton backSpcButton = (ImageButton) findViewById(R.id.Number_backspace);

        ImageButton zeroKeyButton = (ImageButton) findViewById(R.id.Number_0);
        // Long click on backspace clears the dial test field.
        backSpcButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                setDialText("");
                return true;
            }
        });
        // Long click on zero sets the plus sign.
        zeroKeyButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                keyPressed(KeyEvent.KEYCODE_PLUS);
                return true;
            }
        });
        dialButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                phoneService.showCallScreen(TiviPhoneService.CALL_TYPE_RESTARTED);
                return true;
            }
        });
        dialButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (!bUserPressedOffline)
                    makeCall(false);
            }
        });
        dialText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    makeCall(false);
                    return true;
                }
                return false;
            }
        });

        String nr = TiviPhoneService.getInfo(0, -1, "cfg.nr");
        noNumber = TextUtils.isEmpty(nr);
        if (noNumber) {
            keyboardLayout();
        }
        else {
            dialPadLayout();
        }
        DeviceHandling.checkAndSetAec();
        if (TMActivity.SP_DEBUG) Log.v(LOG_TAG, "Received intent - onCreate: " + getIntent());
        processIntent(getIntent());
        checkCallToNr(OCT.getCallToNumber(getBaseContext()));
    }

    private Runnable mShowImeRunnable = new Runnable() {
        public void run() {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                if (!imm.showSoftInput(dialText,0))
                    Log.w(LOG_TAG, "Failed to show soft input method-1.");
            }
        }
    };

    // Use the dialText handler to trigger the soft keyboard display. Without delay this does not
    // work reliably.
    private void setImeVisibility(final boolean visible) {
        if (visible) {
            dialText.postDelayed(mShowImeRunnable, 200);
        } else {
            dialText.removeCallbacks(mShowImeRunnable);
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.hideSoftInputFromWindow(dialText.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Some explanation: if we have only dial-with-name then display the soft keyboard.
        // To do this we may need to toggle the focus to because dialText may still have
        // the focus. The soft keyboard slides in only on focus change.
        if (noNumber && dialText != null) {
            if (dialText.hasFocus())
                countryFlag.requestFocus();
            dialText.requestFocus();
        }
        checkCallToNr(OCT.getCallToNumber(getBaseContext()));
        if (mToneGenerator == null) {
            try {
                mToneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Exception caught while creating local tone generator: " + e);
                mToneGenerator = null;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mToneGenerator != null) {
            mToneGenerator.release();
            mToneGenerator = null;
        }
        switchOnSpeaker = false;
    }

    private void keyboardLayout() {
        countryFlag.requestFocus();
        dialText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                setImeVisibility(hasFocus);
            }
        });
        if ((infoFragment == null || infoFragment.isHidden()) && !dialText.hasFocus()) {
            dialText.requestFocus();
        }
        dialFrame.setVisibility(View.GONE);
        findViewById(R.id.StartDialPad).setVisibility(View.INVISIBLE);
        findViewById(R.id.StartKeyboard).setVisibility(View.VISIBLE);
        findViewById(R.id.StartTopLogo).setVisibility(View.VISIBLE);
    }

    private void dialPadLayout() {
        countryFlag.requestFocus();
        dialFrame.setVisibility(View.VISIBLE);
        findViewById(R.id.StartDialPad).setVisibility(View.VISIBLE);
        findViewById(R.id.StartKeyboard).setVisibility(View.INVISIBLE);
        findViewById(R.id.StartTopLogo).setVisibility(View.GONE);
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }

    private void checkCallToNr(String s) {
        if (s != null) {
            setDialText(s);
            if (TiviPhoneService.getPhoneState() != 2)
                TiviPhoneService.doCmd(":reg");
            makeCall(false);
        }
    }

    // We may need to enhance the "checkKeyManager" process in case we use keys to encrypt
    // the config files. Then we may need to check more often and at different places, needs to
    // be investigated.
    private void checkAndSetKeyManager() {
        if (!KeyManagerSupport.hasKeyManager(getPackageManager())) {
            if (!keyManagerInfoShown) {
                keyManagerInfoShown = true;
                showKeyManagerInfo(R.string.sca_info_install);
                return;
            }
        }
        else if (!KeyManagerSupport.signaturesMatch(getPackageManager(), getPackageName())) {
            Log.w(LOG_TAG,  "KeyManager signature does not match.");
        }
        else {
            hasKeyManager = true;
            final long token = KeyManagerSupport.registerWithKeyManager(getContentResolver(), getPackageName(), getString(R.string.app_name));
            if (token == 0) {
                Log.w(LOG_TAG, "Cannot register with KeyManager.");
                hasKeyManager = false;
            }
        }
        // the onActivityResult calls keyManagerChecked if SKA is ready to use. Otherwise we proceed
        // without key manager
        if (hasKeyManager) {
            startActivityForResult(KeyManagerSupport.getKeyManagerReadyIntent(), KEY_MANAGER_READY);
        }
        else {
            keyManagerChecked();
        }
    }

    private Intent intentProcessed;
    private void processIntent(Intent intent) {
        if (TMActivity.SP_DEBUG) Log.v(LOG_TAG, "Received intent: " + intent);

        if (intentProcessed == intent) {
            if (TMActivity.SP_DEBUG) Log.v(LOG_TAG, "Same intent received: " + intent);
            return;
        }
        intentProcessed = intent;
        if (intent == null)
            return;

        String action = intent.getAction();
        if (action == null)
            return;

        if (SILENT_PHONE_CHECK_ACCOUNT.equals(action)) {
            if (isProvisioned) {
                setResult(Activity.RESULT_OK);
                finish();
            }
            else
                isActionCheckAccount = true;
        }

        Uri numberUri = intent.getData();
        if (numberUri == null)
            return;

        String number = numberUri.getSchemeSpecificPart();
        if (SILENT_CALL_ACTION.equals(action) || SILENT_EDIT_BEFORE_CALL_ACTION.equals(action)) {
            number = number.trim();
            if (SILENT_CALL_ACTION.equals(action))
                checkCallToNr(number);
            else {
                if (number != null) {
                    setDialText(number);
                }
            }
            return;
        }
        if (CALL_PRIVILEGED.equals(action)) {
            if (DeviceDetectionVertu.isVertu() && number.startsWith("+1")) {
                number = number.trim();
            }
            if(number.startsWith(OCT.prefix_check) && number.length() >= OCT.prefix_check.length() + 4) {
                number = number.substring(OCT.prefix_check.length()).trim();
            }
            if(number.startsWith(OCT.prefix_add) && number.length() >= OCT.prefix_add.length() + 4) {
                number = number.substring(OCT.prefix_add.length()).trim();
            }
            checkCallToNr(number);
        }
    }

    private void setCountryFlag(String number) {
        if (number == null || number.length() == 0) {
            countryFlag.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            countryFlag.setText("");
            return;
        }
        CTFlags f = new CTFlags();
        if (f.getNumberInfo(number) >= 0) {
            countryFlag.setCompoundDrawablesWithIntrinsicBounds(0, f.ret.iResID, 0, 0);
            countryFlag.setText(f.ret.countryCode.toUpperCase());
        }
    }

    private boolean isPhoneNR(String s) {

        final int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c) || c == ':' || c == '@' || c == '.')
                return false;
        }
        return true;
    }

    private void keyPressed(int keyCode) {

        boolean bNumberIsFormated = false;

        if(keyCode == KeyEvent.KEYCODE_DEL && dialText.length() > 0){
            String res = dialText.getText().toString();

            boolean nr = isPhoneNR(res);

            if(nr)
               res = android.telephony.PhoneNumberUtils.stripSeparators(res);

            if(res.length()>0)
               res = res.substring(0, res.length()-1);

            setDialText(res);
            bNumberIsFormated = true;
        }
        else {
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            dialText.onKeyDown(keyCode, event);
        }

        final int length = dialText.length();
        if (length == dialText.getSelectionStart() && length == dialText.getSelectionEnd()) {
            dialText.setCursorVisible(false);
        }
        String dst = dialText.getText().toString();

        if(!bNumberIsFormated){
            setDialText(dst);
        }
    }

    public void dialPadClick(View view) {
        noNumber = false;
        dialPadLayout();
    }

    public void keyboardClick(View view) {
        noNumber = true;
        keyboardLayout();
    }

    public void onNumberClick(View view) {
        if (switchOnSpeaker) {
            Utilities.turnOnSpeaker(getBaseContext(), true, false);
            switchOnSpeaker = false;
        }

        switch (view.getId()) {
        case R.id.Number_1:
            keyPressed(KeyEvent.KEYCODE_1);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_1, DTMF_TONE_DURATION);
            break;
        case R.id.Number_2:
            keyPressed(KeyEvent.KEYCODE_2);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_2, DTMF_TONE_DURATION);
            break;
        case R.id.Number_3:
            keyPressed(KeyEvent.KEYCODE_3);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_3, DTMF_TONE_DURATION);
            break;
        case R.id.Number_4:
            keyPressed(KeyEvent.KEYCODE_4);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_4, DTMF_TONE_DURATION);
            break;
        case R.id.Number_5:
            keyPressed(KeyEvent.KEYCODE_5);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_5, DTMF_TONE_DURATION);
            break;
        case R.id.Number_6:
            keyPressed(KeyEvent.KEYCODE_6);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_6, DTMF_TONE_DURATION);
            break;
        case R.id.Number_7:
            keyPressed(KeyEvent.KEYCODE_7);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_7, DTMF_TONE_DURATION);
            break;
        case R.id.Number_8:
            keyPressed(KeyEvent.KEYCODE_8);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_8, DTMF_TONE_DURATION);
            break;
        case R.id.Number_9:
            keyPressed(KeyEvent.KEYCODE_9);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_9, DTMF_TONE_DURATION);
            break;
        case R.id.Number_0:
            keyPressed(KeyEvent.KEYCODE_0);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_0, DTMF_TONE_DURATION);
            break;
        case R.id.Number_hash:
            keyPressed(KeyEvent.KEYCODE_POUND);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_P, DTMF_TONE_DURATION);
            break;
        case R.id.Number_star:
            keyPressed(KeyEvent.KEYCODE_STAR);
            Utilities.playTone(this, mToneGenerator, ToneGenerator.TONE_DTMF_S, DTMF_TONE_DURATION);
            break;
        case R.id.Number_backspace:
            keyPressed(KeyEvent.KEYCODE_DEL);
            break;
        }
    }

    private void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * Perform an orderly application shutdown and exit.
     */
    private void exitApplication() {
        TiviPhoneService.doCmd(":unreg");

        new Thread(new Runnable() {
            public void run() {
                Utilities.Sleep(300);
                TiviPhoneService.doCmd(".exit");
                Utilities.Sleep(500);
            }
        }).start();

        stopServiceAndExit();
    }

    private void stopServiceAndExit() {
        if (hasKeyManager)
            KeyManagerSupport.unregisterFromKeyManager(getContentResolver());
        new Thread(new Runnable() {
            public void run() {
//                Utilities.Sleep(500);
                TMActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        stopService(new Intent(TMActivity.this, TiviPhoneService.class));
                    }
                });
                doUnbindService();
                Utilities.Sleep(200);
                System.exit(0);
            }
        }).start();
    }

    private void lookupScaApp() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("market://details?id=com.silentcircle.contacts"));
        startActivity(intent);
        stopServiceAndExit();
    }

    private void dismissScaApp() {
        checkAndSetKeyManager();
    }

    private void showKeyManagerInfo(int msgId) {
        InfoKeyManagerDialog infoMsg = InfoKeyManagerDialog.newInstance(msgId);
        FragmentManager fragmentManager = getSupportFragmentManager();
        infoMsg.show(fragmentManager, "InfoKeyManagerDialog");
    }

    public static class InfoKeyManagerDialog extends DialogFragment {
        private static String MESSAGE_ID = "messageId";

        public static InfoKeyManagerDialog newInstance(int msgId) {
            InfoKeyManagerDialog f = new InfoKeyManagerDialog();

            Bundle args = new Bundle();
            args.putInt(MESSAGE_ID, msgId);
            f.setArguments(args);

            return f;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            ((TMActivity)getActivity()).dismissScaApp();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.sca_add_security)
                    .setMessage(getArguments().getInt(MESSAGE_ID))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ((TMActivity) getActivity()).lookupScaApp();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((TMActivity)getActivity()).dismissScaApp();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
