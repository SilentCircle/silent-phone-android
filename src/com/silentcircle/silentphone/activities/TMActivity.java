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
import android.widget.TextView;

import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.ComponentName;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.view.KeyEvent;
import android.app.Dialog;
import android.provider.CallLog;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import android.widget.Toast;

import android.database.Cursor;

import android.provider.ContactsContract.CommonDataKinds.Phone;

import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.utils.CTCall;
import com.silentcircle.silentphone.utils.TBuild;
import com.silentcircle.silentphone.utils.Utilities;
import com.silentcircle.silentphone.utils.FontFitTextView;
import com.silentcircle.silentphone.utils.CTStringBuffer;
import com.silentcircle.silentphone.utils.CTFlags;

import com.silentcircle.silentphone.views.CallScreen;
import com.silentcircle.silentphone.receivers.OCT;



import android.widget.ImageButton;

public class TMActivity extends FragmentActivity {

    private static final String LOG_TAG = "TMActivity";

    private FontFitTextView dialText;
    private static CTStringBuffer lastDialedNumber= new CTStringBuffer();

    private static final int CONTACT_PICKER_RESULT = 1001;
    private static final int PROVISIONING_RESULT = 47118;
    private static final int KEY_GEN_RESULT = 7118;

    /**
     * Play a DTMF tone for 200ms
     */
    private static int DTMF_TONE_DURATION = 200;


    /**
     * Set to true by onNewIntent if user adds a new call, reset in onPause
     */
    private boolean switchOnSpeaker;

    public static boolean SP_DEBUG;

    private boolean bShowCfg;
    private boolean phoneIsBound;
    private TiviPhoneService phoneService;
    private boolean provisioningDone;
    
    private TextView countryFlag;
    
    private ImageButton dialButton;
    private ImageButton backSpcButton;
    private ImageButton zeroKeyButton;
    
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
            if (provisioningDone) {
                provisioningDone = false;
                showThanks();
            }
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

    public void doLaunchHistory() {
        Intent showCallLog = new Intent();
        showCallLog.setAction(Intent.ACTION_VIEW);
        showCallLog.setType(CallLog.Calls.CONTENT_TYPE);
        startActivity(showCallLog);    
    }


    public void doLaunchContactPicker() {// View view) {
        //http://stackoverflow.com/questions/2383580/how-do-i-load-a-contact-photo
        //http://stackoverflow.com/questions/3509178/getting-a-photo-from-a-contact

        Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI);
        startActivityForResult(contactPickerIntent, CONTACT_PICKER_RESULT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PROVISIONING_RESULT) {
            if (resultCode != RESULT_OK) {
                exitApplication();
            }
            else {
                startS(this);
                provisioningDone = true;
            }
            return;
        }
        if (requestCode == CONTACT_PICKER_RESULT) {
            if (resultCode == RESULT_OK) {
                Uri result = data.getData();
                Cursor c = managedQuery(result, null, null, null, null);// c.moveToFirst();
                if (c.moveToFirst()) {
                    int index = c.getColumnIndexOrThrow(Phone.NUMBER);
                    if (index < 0)
                        return;// TODO check
                    String number = c.getString(index);
                    if(number.startsWith(OCT.prefix_check) && number.length() >= OCT.prefix_check.length() + 4){
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
            if (data != null) {
                setDialText(data.getAction());    
            }
        }
        if (requestCode == KEY_GEN_RESULT) {
            if (resultCode != RESULT_OK) {
                exitApplication();
            }
            else {
                startS(this);
            }
            return;            
        }
    }

    void setDialText(String s) {
//isPhoneNR(s) ??
        if(s.length() == 0 || s.indexOf("+") == 0 || (s.indexOf("1") == 0 && s.length()==11)) {
           setCountryFlag(s);
        }
        String nr = CTFlags.formatNumber(s);

        dialText.setText(nr);
        dialText.setSelection(dialText.getText().length());
    }


    public static final int FIRST_MENU_ID = Menu.FIRST;
    public static final int EXIT_MENU_ITEM = FIRST_MENU_ID + 1;
    public static final int LOGIN_MENU_ITEM = FIRST_MENU_ID + 2;
    public static final int CFG_MENU_ITEM = FIRST_MENU_ID + 3;
    public static final int UNREG_MENU_ITEM = FIRST_MENU_ID + 4;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        MenuItem m;

        m = menu.add(0, EXIT_MENU_ITEM, 0, getString(R.string.exit));
        m.setIcon(android.R.drawable.ic_menu_close_clear_cancel);// ok
        m = menu.add(0, UNREG_MENU_ITEM, 0, getString(R.string.logout));
        m.setIcon(R.drawable.logout);

        m = menu.add(0, CFG_MENU_ITEM, 0, getString(R.string.settings));
        m.setIcon(android.R.drawable.ic_menu_preferences);// ok

        m = menu.add(0, LOGIN_MENU_ITEM, 0, getString(R.string.login));
        m.setIcon(R.drawable.login);// ic_menu_set_as);


        return result;
    }

    static boolean bConectedToServ = false;
    static boolean bUserPressedOffline = false;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);

        int iInCall = TiviPhoneService.calls.getCallCnt();// getCallState();

        boolean bServStarted = bConectedToServ && TiviPhoneService.mc != null;
        int iPhoneState = TiviPhoneService.getPhoneState();

        menu.findItem(EXIT_MENU_ITEM).setVisible(iInCall == 0);
        menu.findItem(LOGIN_MENU_ITEM).setVisible((!bServStarted || iPhoneState == 0));
        menu.findItem(CFG_MENU_ITEM).setVisible(bShowCfg);
        menu.findItem(UNREG_MENU_ITEM).setVisible(bServStarted && iInCall == 0 && iPhoneState != 0);

        return result;
    }

    static public void insertCallLog(Activity ctx, CTCall call){

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

       TMActivity.insertCallIntoCallLog(ctx, caller, iType, duration);
    }

    // do not use this directly
    private static void insertCallIntoCallLog(Activity ctx, String dst, int iType, long dur) {
        // TODO cfg
        final android.content.ContentResolver resolver = ctx.getBaseContext().getContentResolver();
        android.content.ContentValues values = new android.content.ContentValues(5);

        // strip @ and chars
        StringBuffer strDst = new StringBuffer(20);
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

    public void makeCall(boolean b) {

        makeCall(b, dialText.getText().toString());
    }

    void makeCall(boolean b, String dst) {
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
               TiviPhoneService.doCmd(":s");showToast("save");
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

    public void startS(Context c) {
        // Check and set the secret key

        if (TiviPhoneService.use_password_key && TiviPhoneService.getSecretKeyStatus() == TiviPhoneService.SECURE_KEY_NOT_AVAILABE) {
            Intent i = new Intent(this, KeyGeneration.class);
            i.putExtra(KeyGeneration.NEW_PROVISIONING, false);
            startActivityForResult(i, KEY_GEN_RESULT);
            return;
        }
 
        if (!bConectedToServ) {
            c.startService(new Intent(c, TiviPhoneService.class));
            bConectedToServ = true;
            // service will send ":reg"
        }
        else if (TiviPhoneService.mc != null)
            TiviPhoneService.doCmd(":reg");
        doBindService();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        case EXIT_MENU_ITEM:
            exitApplication();
            break;

        case UNREG_MENU_ITEM:
            bUserPressedOffline = true;
            TiviPhoneService.doCmd(":unreg");
            break;

        case LOGIN_MENU_ITEM: {
            bUserPressedOffline = false;
            startS(this);
            break;
        }
        case CFG_MENU_ITEM:
            showCfg();
            break;
        }
        return true;
    }

    void showCfg() {
        if (bConectedToServ)
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
        if (!phoneService.isHeadsetPlugged()) {
            switchOnSpeaker = true;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SP_DEBUG = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        Log.d(LOG_TAG, "Debuggable: " + SP_DEBUG);

        TiviPhoneService.initJNI(getBaseContext());

        if (TiviPhoneService.mc==null && TiviPhoneService.doCmd("isProv") == 0) {
            Intent provisionigIntent = new Intent(this, Provisioning.class);
            provisionigIntent.putExtra("IMEI", "empty");
            startActivityForResult(provisionigIntent, PROVISIONING_RESULT);
        } 
        else if (!bUserPressedOffline) {
            startS(this);
        }
        // Don't use phoneService here: service binding usually delayed until Activity startup
        // is complete.
        if (TiviPhoneService.mc != null && TiviPhoneService.calls.getCallCnt() != 0) {
            TiviPhoneService.mc.showCallScreen(TiviPhoneService.CALL_TYPE_RESTARTED);
        }
        setContentView(R.layout.activity_start_screen);

        dialText = (FontFitTextView)findViewById(R.id.DialNumberText);
        dialButton = (ImageButton)findViewById(R.id.Number_call);
        dialButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                makeCall(false);
            }
        });
        dialButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                phoneService.showCallScreen(TiviPhoneService.CALL_TYPE_RESTARTED);
                return true;
            }
        });

        countryFlag = (TextView)findViewById(R.id.StartFlagField);
        backSpcButton = (ImageButton)findViewById(R.id.Number_backspace);

        // Long click on backspace clears the dial test field.
        backSpcButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                setDialText("");

                return true;
            }
        });
        zeroKeyButton = (ImageButton)findViewById(R.id.Number_0);

        // Long click on backspace clears the dial test field.
        zeroKeyButton.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View view) {
                keyPressed(KeyEvent.KEYCODE_PLUS);
                return true;
            }
        });
        
        checkCallToNr();
    }

    private void checkCallToNr(){
        String s = OCT.getCallToNumber(getBaseContext());
        if (s != null) {
            setDialText(s);
            if(TiviPhoneService.mc != null && TiviPhoneService.getPhoneState() == 2){
               makeCall(false);
            }
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
        case R.id.Number_contact:
            doLaunchContactPicker();
            break;
        }
        return;
    }

    /**
     * User pressed the Recent calls button.
     * 
     * @param view the view that contains the button.
     */
    public void onNumberRecent(View view) {
        doLaunchHistory();
    }

    private void showToastLong(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void showNotification() {
        return;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        checkCallToNr();
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
    
    /* *********************************************************************
     * Handle information and thanks dialog. Maybe we need to extend this to implement
     * account selection - later. 
     * ******************************************************************* */
    private static String internalInfo = TBuild.T_BUILD_NR;
    private static String deviceInfo = android.os.Build.BRAND + " " + android.os.Build.DEVICE;
    
    private static String[] screenSizes = {"Undefined", "Small", "Normal", "Large", "XLarge"};

    private static ThanksInfoDialog thanks;

    public void onSilentCircle(View view) {
        showThanks();
    }

    public void showThanks() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        thanks = new ThanksInfoDialog();
        thanks.show(fragmentManager, "SCPhoneThanks_dialog");
    }

    public static class ThanksInfoDialog extends DialogFragment {
        private TextView numberText;
        private TextView infoText;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();
           

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            View view = inflater.inflate(R.layout.dialog_thanks_info, null);
            numberText = (TextView)view.findViewById(R.id.ThanksSCnumber);
            numberText.setText(CTFlags.formatNumber(Utilities.getTCValue("edNR")));

            infoText = (TextView)view.findViewById(R.id.InfoBuildNumberInfo);
            infoText.setText(internalInfo);
            infoText = (TextView)view.findViewById(R.id.InfoDeviceInfoInfo);
            infoText.setText(deviceInfo);

            Resources res = getResources();
            Configuration config = res.getConfiguration();
            int idx = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
            String classification = getString(R.string.dpi_classified_as) + ", " + screenSizes[idx] + ", API: " + android.os.Build.VERSION.SDK_INT;
            infoText = (TextView)view.findViewById(R.id.InfoDeviceClassInfo);
            infoText.setText(classification);
            
                                
            // Add inflated view and action buttons
            builder.setView(view)
                   .setPositiveButton(getString(R.string.close_dialog), new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                       }
                   });
            return builder.create();
        }
    }
}
