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

import android.provider.ContactsContract;
import android.widget.TextView;

import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.ComponentName;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.view.KeyEvent;
import android.provider.CallLog;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;

import android.content.Intent;
import android.content.pm.ApplicationInfo;

import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import android.widget.Toast;

import android.database.Cursor;

import android.provider.ContactsContract;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import com.silentcircle.silentcontacts.ScCallLog;
import com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.utils.DeviceDetectionVertu;
import com.silentcircle.silentphone.utils.DeviceHandling;
import com.silentcircle.silentphone.utils.Utilities;
import com.silentcircle.silentphone.utils.FontFitTextView;
import com.silentcircle.silentphone.utils.CTStringBuffer;
import com.silentcircle.silentphone.utils.CTFlags;

import com.silentcircle.silentphone.views.CallScreen;
import com.silentcircle.silentphone.fragments.InfoFragment;
import com.silentcircle.silentphone.receivers.OCT;



import android.widget.ImageButton;

public class TMActivity extends SherlockFragmentActivity {

    private static final String LOG_TAG = "TMActivity";

    private static String SILENT_CALL_ACTION = "com.silentcircle.silentphone.action.NEW_OUTGOING_CALL";
    private static String SILENT_EDIT_BEFORE_CALL_ACTION = "com.silentcircle.silentphone.action.EDIT_BFORE_CALL";
    private static String CALL_PRIVILEGED = "android.intent.action.CALL_PRIVILEGED";

    private FontFitTextView dialText;
    public static CTStringBuffer lastDialedNumber= new CTStringBuffer();

    private static final int CONTACT_PICKER_RESULT = 1001;
    private static final int CONTACT_PICKER_RESULT_SILENT = 1002;
    private static final int PROVISIONING_RESULT = 47118;
    private static final int KEY_GEN_RESULT = 7118;
    private static final int DUMMY_RESULT = 11;

    private static final int SPA_HOME_POSITION = 0;

    private static final int INFO_POSITION = 1;
    private static final String INFO_TAG = "spa_info_fragment";

    private static final int CONTACTS_POSITION = 2;
    private static final int RECENTS_POSITION = 3;

    
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
    
    private ImageButton dialButton;
    private ImageButton backSpcButton;
    private ImageButton zeroKeyButton;
    
    private ActionBar actionBar;
    private InfoFragment infoFragment;

    // some navigation tab store their position here, used to restore tab selection 
    // after calling an activity
    private int savedPosition;
    
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

    void doBindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(TMActivity.this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE);
    }

    void doUnbindService() {
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

        infoFragment = (InfoFragment)fragmentManager.findFragmentByTag(INFO_TAG);
        if (infoFragment == null) {
            infoFragment = new InfoFragment();
            transaction.add(R.id.start_screen, infoFragment, INFO_TAG);            
        }
        transaction.show(infoFragment);
        transaction.commitAllowingStateLoss();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == DUMMY_RESULT)
            return;

        if (requestCode == PROVISIONING_RESULT) {
            if (resultCode != RESULT_OK) {
                exitApplication();
            }
            else {
                startService(this);
                provisioningDone = true;
            }
            return;
        }
        if (requestCode == CONTACT_PICKER_RESULT) {
            if (resultCode == RESULT_OK) {
                Uri result = data.getData();
                Cursor c = managedQuery(result, null, null, null, null);
                if (c != null && c.moveToFirst()) {
                    int index = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    if (index < 0)
                        return;
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
                Cursor c = managedQuery(result, null, null, null, null);
                if (c != null && c.moveToFirst()) {
                    int index = c.getColumnIndexOrThrow(Phone.NUMBER);
                    if (index < 0)
                        return;

                    String number = c.getString(index);
                    number = number.trim();
                    if (number != null) {
                        setDialText(number);
                    }
                    return;
                }
            }
        }
        if (requestCode == KEY_GEN_RESULT) {
            if (resultCode != RESULT_OK) {
                exitApplication();
            }
            else {
                startService(this);
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
        menu.findItem(R.id.start_menu_login).setVisible((!bServiceStarted || iPhoneState == 0));
        menu.findItem(R.id.start_menu_settings).setVisible(bShowCfg);
        menu.findItem(R.id.start_menu_logout).setVisible(bServiceStarted && iInCall == 0 && iPhoneState != 0);

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
                }
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
                showCfg();
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
            return;
        }
        String s;
        s = b ? ":v " : ":c ";
        s += dst;

        // remembering the last number dialed
        lastDialedNumber.setText(android.telephony.PhoneNumberUtils.stripSeparators(dst));

        if (phoneIsBound) {
            TCallWindow.am(getBaseContext(), true);

            TiviPhoneService.doCmd(s);
            setDialText("");

            phoneService.showCallScreen(TiviPhoneService.CALL_TYPE_OUTGOING);
        }
    }

    public void startService(Context c) {
        // Check and set the secret key

//        if (TiviPhoneService.use_password_key && TiviPhoneService.getSecretKeyStatus() == TiviPhoneService.SECURE_KEY_NOT_AVAILABE) {
//            Intent i = new Intent(this, KeyGeneration.class);
//            i.putExtra(KeyGeneration.NEW_PROVISIONING, false);
//            startActivityForResult(i, KEY_GEN_RESULT);
//            return;
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

    void showCfg() {
        if (bConnectedToServ)
            TiviPhoneService.doCmd(":unreg");
        Intent intent = new Intent();
        intent.setClass(this, TAdvCfg.class);
        startActivity(intent);
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
        processIntent(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SP_DEBUG = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        Log.d(LOG_TAG, "Debuggable: " + SP_DEBUG);

        TiviPhoneService.initJNI(getBaseContext());
        setupActionBar();

        if (TiviPhoneService.mc==null && TiviPhoneService.doCmd("isProv") == 0) {
            Intent provisionigIntent = new Intent(this, Provisioning.class);
            provisionigIntent.putExtra("IMEI", "empty");
            startActivityForResult(provisionigIntent, PROVISIONING_RESULT);
        } 
        else if (!bUserPressedOffline) {
            startService(this);             // start phone service and bind it
        }
        if (TiviPhoneService.calls.getCallCnt() != 0) {
            TiviPhoneService.mc.showCallScreen(TiviPhoneService.CALL_TYPE_RESTARTED);
        }
        setContentView(R.layout.activity_start_screen);

        dialText = (FontFitTextView)findViewById(R.id.DialNumberText);
        dialButton = (ImageButton)findViewById(R.id.Number_call);
        countryFlag = (TextView)findViewById(R.id.StartFlagField);
        backSpcButton = (ImageButton)findViewById(R.id.Number_backspace);

        zeroKeyButton = (ImageButton)findViewById(R.id.Number_0);
        dialButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                phoneService.showCallScreen(TiviPhoneService.CALL_TYPE_RESTARTED);
                return true;
            }
        });
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
    }
    
    /**
     * Called after service was connected
     */
    private void serviceBound() {
        dialButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (!bUserPressedOffline)
                    makeCall(false);
            }
        });
        DeviceHandling.checkAndSetAec();
        processIntent(getIntent());
        checkCallToNr(OCT.getCallToNumber(getBaseContext()));
    }

    private void setupActionBar() {
        actionBar = getSupportActionBar();
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

    private void processIntent(Intent intent) {
        if (TMActivity.SP_DEBUG) Log.v(LOG_TAG, "Received intent: " + intent);

        if (intent == null)
            return;

        String action = intent.getAction();
        if (action == null)
            return;

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
        CTFlags f = new CTFlags(getBaseContext());
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

    private void showToastLong(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
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
    
    /**
     * Perform an orderly application shutdown and exit.
     */
    private void exitApplication() {
        TiviPhoneService.doCmd(":unreg");

        new Thread(new Runnable() {
            public void run() {
                Utilities.Sleep(300);
                TiviPhoneService.doCmd(".exit");
            }
        }).start();

        new Thread(new Runnable() {
            public void run() {
                Utilities.Sleep(500);
                TMActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        stopService(new Intent(TMActivity.this, TiviPhoneService.class));
                    }
                });
                Utilities.Sleep(200);
                System.exit(0);
            }
        }).start();        
    }
    
    protected static void hideFragment(FragmentTransaction ft, Fragment f) {
        if ((f != null) && !f.isHidden()) ft.hide(f);
    }
}
