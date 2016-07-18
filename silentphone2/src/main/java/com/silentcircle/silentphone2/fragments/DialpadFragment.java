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

package com.silentcircle.silentphone2.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.silentcircle.common.testing.NeededForReflection;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.common.widget.FloatingActionButtonController;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

/**
 * This Fragment handles the dial pad UI.
 *
 * Created by werner on 07.02.14.
 */
public class DialpadFragment extends Fragment implements View.OnClickListener,
        View.OnLongClickListener, TextWatcher {

    private static final String TAG = DialpadFragment.class.getSimpleName();


    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 200;

    // Pick contact intent request code
    private static final int PICK_CONTACT = 1;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    private EditText mDestination;
    private ImageButton mInputSwitcher;

    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();

    private View mDelete;
    private View mDialPad;
    private View mPickContact;
    private View mRequestHelper;
    private LinearLayout mTopLevel;
    private View mDialPadBottomHeight;
    private FloatingActionButtonController mFloatingActionButtonController;

    private static String mLastDestination = "";
    private static String mSaveUserInput;
    private String mPresetDestination;

    private boolean mDialpadInvisible;

    private Activity mParent;
    private Drawable mDialPadIcon;
    private Drawable mKeyboardIcon;

    private OnDialpadQueryChangedListener mDialpadQueryListener;

    private int mDialpadSlideInDuration;
    
    private boolean mWasEmptyBeforeTextChange;
    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public interface DialpadCallbacks {
        /**
         * Call a destination.
         *
         * @param callCommand the command to perform call
         * @param destination the destination, could be formatted number or a name
         * @param isOcaCall if the destination is over OCA
         */
        void doCall(String callCommand, String destination, boolean isOcaCall);

        /**
         * Handle an internal command code.
         *
         * @param code the command code
         */
        void internalCall(String code);
        /**
         * If activity support a drawer layout this reports the drawer status
         *
         * @return true is a drawer is open
         */
        boolean isDrawerOpen();

        /**
         * Call to start animation.
         */
        void onDialpadShown();
    }

    public interface OnDialpadQueryChangedListener {
        void onDialpadQueryChanged(String query);
    }

    /**
     * LinearLayout with getter and setter methods for the translationY property using floats,
     * for animation purposes.
     */
    public static class DialpadSlidingRelativeLayout extends RelativeLayout {

        public DialpadSlidingRelativeLayout(Context context) {
            super(context);
        }

        public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @NeededForReflection
        @SuppressWarnings("unused")
        public float getYFraction() {
            final int height = getHeight();
            if (height == 0) return 0;
            return getTranslationY() / height;
        }

        @NeededForReflection
        @SuppressWarnings("unused")
        public void setYFraction(float yFraction) {
            setTranslationY(yFraction * getHeight());
        }
    }

    public DialpadFragment() {
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mDialpadSlideInDuration = getResources().getInteger(R.integer.dialpad_slide_in_duration);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final Resources.Theme theme = mParent.getTheme();
        final TypedArray array = theme != null ? theme.obtainStyledAttributes(new int[]{
                R.attr.sp_ic_dial_pad,
                R.attr.sp_ic_keyboard,
        }) : null;

        if (array != null) {
            mDialPadIcon = array.getDrawable(0);
            mKeyboardIcon = array.getDrawable(1);
            array.recycle();
        }
        else {
            mDialPadIcon = ContextCompat.getDrawable(mParent, R.drawable.ic_action_dial_pad_light);
            mKeyboardIcon = ContextCompat.getDrawable(mParent, R.drawable.ic_action_keyboard_light);
        }

        final View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container, false);
        if (fragmentView == null)
            return null;
        fragmentView.buildLayer();

        mDestination = (EditText) fragmentView.findViewById(R.id.digits);
        mDestination.setOnClickListener(this);
        mDestination.setOnLongClickListener(this);
        mDestination.addTextChangedListener(this);
        mDestination.setFilters(new InputFilter[]{SearchUtil.LOWER_CASE_INPUT_FILTER});
        /**
         * This async task is no longer used, because it will not format numbers added instantly
         * using {@link #setDestination} from the {@link DialerActivity}. Instead, we will set
         * the {@link PhoneNumberFormattingTextWatcher} sequentially.
         */
        // PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(mDestination);
        mDestination.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        mDestination.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_NEXT) {
                    dialButtonPressed();
                    return true;
                }
                return false;
            }
        });
        mDestination.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                setImeVisibility(hasFocus);
            }
        });

        mTopLevel = (LinearLayout)fragmentView.findViewById(R.id.top);
        // Check for the presence of the keypad
        View oneButton = fragmentView.findViewById(R.id.one);
        if (oneButton != null) {
            setupKeypad(fragmentView);
        }
        final View floatingActionButtonContainer = fragmentView.findViewById(R.id.dialpad_floating_action_button_container);
        final View dialButton = fragmentView.findViewById(R.id.dialButton);
        dialButton.setOnClickListener(this);
        dialButton.setOnLongClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                floatingActionButtonContainer, dialButton);

        mDelete = fragmentView.findViewById(R.id.deleteButton);
        if (mDelete != null) {
            mDelete.setOnClickListener(this);
            mDelete.setOnLongClickListener(this);
        }

        mPickContact = fragmentView.findViewById(R.id.pickContact);
        if (mPickContact != null) {
            mPickContact.setOnClickListener(this);
        }

        mInputSwitcher = (ImageButton)fragmentView.findViewById(R.id.input_type_switch);
        mInputSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inputSwitchClick();
            }
        });

        View spacer = fragmentView.findViewById(R.id.spacer);
        spacer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mDestination.length() == 0) {
                    if (mParent instanceof DialerActivity) {
                        if (mDestination.hasFocus())
                            mRequestHelper.requestFocus();
                        ((DialerActivity) mParent).hideDialpadFragment(true, true);
                        return true;
                    }
                }
                return false;
            }
        });

        mRequestHelper = fragmentView.findViewById(R.id.request_helper);

        mDialPad = fragmentView.findViewById(R.id.dialpad);  // This is null in landscape mode.
        mDialPadBottomHeight = fragmentView.findViewById(R.id.dialpad_bottom_height); // Also null
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Some explanation: if we have only dial-with-name then display the soft keyboard.
        // To do this we may need to toggle the focus because mDestination may still have
        // the focus. The soft keyboard slides in only on focus change.
        DialpadCallbacks dialActivity = (DialpadCallbacks)getActivity();
        mDialpadQueryListener = (OnDialpadQueryChangedListener)dialActivity;

        boolean drawerOpen = dialActivity != null && dialActivity.isDrawerOpen();
        if (mDestination != null && !isHidden()) {
            if (mDestination.hasFocus())
                mRequestHelper.requestFocus();
            if (mDialpadInvisible && !drawerOpen)
                mDestination.requestFocus();
        }
        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = (Settings.System.getInt(mParent.getContentResolver(), Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1);

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
        if (mSaveUserInput != null) {
            setDestination(mSaveUserInput);
            mSaveUserInput = null;
        }
        // Check if the dialer got an Intent to set the destination and
        // perform auto-dialing. This overwrites a saved user input
        if (mPresetDestination != null) {
            setDestination(mPresetDestination);
            mPresetDestination = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        String userInput = mDestination.getText().toString();
        mSaveUserInput = TextUtils.isEmpty(userInput) ? null : userInput;

        // Make sure we don't leave this fragment with a tone still playing.
        stopTone();

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
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
        commonOnAttach(activity);
    }

    private void commonOnAttach(Activity activity) {
        mParent = activity;
    }

    @Override
    public void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("NO_NUMBER", mDialpadInvisible);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        DialpadCallbacks activity = (DialpadCallbacks)getActivity();
        if (activity == null)
            return;
        if (!hidden && mDestination != null) {
            mDialpadInvisible = true;

            // In landscape mode we force to show the keyboard
            if (mDialPad == null) {
                mInputSwitcher.setVisibility(View.INVISIBLE);   // don't show the mode switch
                mInputSwitcher.setEnabled(false);
            }
            else {
                mDestination.setCursorVisible(false);
                mDialPad.setVisibility(View.GONE);
            }
            mFloatingActionButtonController.scaleIn(mDialpadSlideInDuration);
            activity.onDialpadShown();
            mDestination.postDelayed(mShowKeyboard, 200); // show IME after all views are settled
        }
        if (hidden && mFloatingActionButtonController != null)
            mFloatingActionButtonController.scaleOut();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                if (mDialPad != null && mDialPad.getVisibility() == View.VISIBLE) {
                    playTone(ToneGenerator.TONE_DTMF_B, TONE_LENGTH_MS);
                }
                return;
            }
            case R.id.pickContact: {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(intent, PICK_CONTACT);

                return;
            }
            case R.id.dialButton: {
                dialButtonPressed();
                return;
            }
            case R.id.dialpad_rnd_btn: {
                onPressed(view, true);
                return;
            }
            case R.id.digits: {
                return;
            }
            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        final int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
                final Editable digits = mDestination.getText();
                if (digits != null)
                    digits.clear();
                mDelete.setPressed(false);
                FindDialHelper.getDialHelper().resetAnalyser();
                return true;
            }
            case R.id.dialpad_rnd_btn: {
                int subId = (Integer)view.getTag();
                if (subId == R.id.zero) {
                    keyPressed(KeyEvent.KEYCODE_PLUS);
                    stopTone();
                }
                return true;
            }
            case R.id.digits: {
                // Right now EditText does not show the "paste" option when cursor is not visible.
                // To show that, make the cursor visible, and return false, letting the EditText
                // show the option by itself.
                mDestination.setCursorVisible(true);
                return false;
            }
            case R.id.dialButton: {
                if (isDestinationEmpty()) {
                    handleDialButtonClickWithEmptyDigits();
                    // This event should be consumed so that onClick() won't do the exactly same
                    // thing.
                    return true;
                }
                else {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case PICK_CONTACT: {
                if (resultCode == Activity.RESULT_OK) {
                    Uri contactUri = data.getData();

                    String[] projection = {
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    };

                    Cursor cursor = mParent.getContentResolver().query(contactUri, projection, null, null, null);
                    if (cursor == null)
                        break;
                    cursor.moveToFirst();

                    int column = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    String number = cursor.getString(column);
                    cursor.close();

                    // Apply the dial helper and format the number if necessary
                    boolean wasModified = DialerActivity.checkCallToNr(number, this);

                    if (!wasModified) {
                        // Automatically call if the number was not modified, which basically
                        // means we want the user to automatically call only what they expect to call (WYSIWYG)
                        dialButtonPressed();
                    }
                }

                break;
            }
        }

    }

    public void dialButtonPressed() {
        FindDialHelper.getDialHelper().resetAnalyser();

        if (isDestinationEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
//            if (!LoadUserInfo.canCallOutbound(mParent)) {
//                showDialog(R.string.information_dialog, R.string.basic_account_info,
//                        android.R.string.ok, -1);
//
//                return;
//            }

            final Editable text = mDestination.getText();
            if (text != null) {
                final String destination = text.toString();
                makeCall(destination);
            }
        }
    }

    public void setDestination(String destination) {
        mSaveUserInput = null;
        if (mDestination == null) {
            mPresetDestination = destination;
            return;
        }
        mDestination.setText(destination);
        final Editable text = mDestination.getText();
        if (text != null)
            mDestination.setSelection(text.length());
    }

    /*
     * The host activity calls this function before it will open the content drawer.
     * This function removes focus from mDestination edit text field.
     *
     * The dial pad activity delays the call to open which gives some time to remove
     * the keyboard (slide in), perform necessary layout changes, before the drawer
     * opens.
     *
     */
    public void removeDestinationFocus() {
        if (mDestination.hasFocus()) {
//            mTopLevel.setVisibility(View.INVISIBLE);
            mRequestHelper.requestFocus();
        }
    }

    public void onDrawerOpen() {
        mTopLevel.setVisibility(View.VISIBLE);
    }

    /**
     * Shows the keyboard if necessary.
     *
     * Shows the keyboard if the dial-pad is not active.
     */
    public void showKeyboard() {
        if (mDialpadInvisible) {
            if (mDestination.hasFocus())
                mRequestHelper.requestFocus();
            mDestination.requestFocus();
            mDestination.setCursorVisible(true);
        }
    }

    public void clearDialpad() {
        mDestination.setText(null);
        mSaveUserInput = null;
        FindDialHelper.getDialHelper().resetAnalyser();
    }

    private boolean isDestinationEmpty() {
        return mDestination.length() == 0;
    }

    private void makeCall(String dst) {
        final DialpadCallbacks activity = (DialpadCallbacks)getActivity();
        if (activity == null)
            return;

        if (dst.startsWith("*##*")) {
            activity.internalCall(dst);
            mDestination.setText("");
            mDestination.setCursorVisible(false);
            return;
        }

        final boolean specialNumber = dst.startsWith("*");

        // Remember the last destination dialed:
        // - store URIs as we get them
        // - if a "destination" starts with a letter then treat it as "name"
        // - treat other input as numbers and store without separators
        final int priority;
        if (dst.endsWith("!!")) {
            priority = CallState.EMERGENCY;
            dst = dst.replace("!!", "");
        }
        else if (dst.endsWith("!")) {
            priority = CallState.URGENT;
            dst = dst.replace("!", "");
        }
        else
            priority = CallState.NORMAL;

        // Beware of ";" character as "wait indicator" in some phone numbers, should
        // not happen on SIP dialing at all. Part after ';' is the destination device id
        // to address a specific device on the user's account.
        String numberParts[] = dst.split(";");
        if (numberParts.length < 1) {
            return;
        }
        dst = numberParts[0];
        final String deviceId = numberParts.length >= 2 ? ";" + numberParts[1] : null;
        final String formattedNumber;
        if (Character.isLetter(dst.charAt(0)) || Utilities.isUriNumber(dst) || dst.charAt(0) == '*') {
            formattedNumber = mLastDestination = dst;
        }
        else {
            String normalizedNumber = mLastDestination = PhoneNumberHelper.normalizeNumber(dst.trim());
            formattedNumber = Utilities.getValidPhoneNumber(normalizedNumber);
            if (formattedNumber != null)
                mLastDestination = PhoneNumberUtils.stripSeparators(formattedNumber);
        }

        // The getUid _must not_ run on UI thread because it may trigger a network
        // activity to do a lookup on provisioning server.
        AsyncTasks.UserDataBackgroundTask getUidTask = new AsyncTasks.UserDataBackgroundTask() {

            @Override
            protected void onPostExecute(Integer time) {
                super.onPostExecute(time);

                // If we could not find a UID then use the data as typed by the user and
                // create the call command. Otherwise use the UID.
                String callCommand;
                String calledDestination;
                boolean isOcaCall = false;

                if (mUserInfo == null || mUserInfo.mUuid == null) {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "The UUID for " + mLastDestination + " is null");
                    calledDestination = mLastDestination;

                    if (!specialNumber)
                        isOcaCall = Utilities.isValidPhoneNumber(calledDestination);

                    // In case we have no user info and this is not a valid number - then this is no
                    // SC user - show this in the same way as if the server would return this message.
                    if (!specialNumber && !isOcaCall) {
                        showDialog(R.string.information_dialog, R.string.sip_error_no_user,
                                android.R.string.ok, -1);
                        return;
                    }
                    // This number did not resolve to a SIP number or a special number, and will cost
                    // money if connected (OCA call)

                    if(isOcaCall) {
//                        if(LoadUserInfo.checkIfUsesMinutes() == LoadUserInfo.VALID &&
//                                LoadUserInfo.checkIfLowMinutes(0) == LoadUserInfo.VALID) {
//                            showDialog(R.string.information_dialog, R.string.minutes_gone_info,
//                                    android.R.string.ok, -1);
//
//                            return;
//                        } else if(LoadUserInfo.checkIfUsesCredit() == LoadUserInfo.VALID &&
//                                LoadUserInfo.checkIfLowCredit(0.00) == LoadUserInfo.VALID) {
//                            showDialog(R.string.information_dialog, R.string.credit_gone_info,
//                                    android.R.string.ok, -1);
//
//                            return;
//                        }
//
//                        if(!LoadUserInfo.canCallOca(mParent)) {
//                            showDialog(R.string.information_dialog, R.string.basic_account_info,
//                                    android.R.string.ok, -1);
//
//                            return;
//                        }
                        // For OCA calls set the display name to the formatted number because
                        // no other information is available :-) .
                        mUserInfo = new AsyncTasks.UserInfo();
                        mUserInfo.mDisplayName = formattedNumber;
                    }

                    // The validation and formatting above may remove the starting '*' character. If that's
                    // the case add it again
                    if (specialNumber) {
                        if (mLastDestination.charAt(0) != '*')
                            mLastDestination = "*" + mLastDestination;
                        mUserInfo = new AsyncTasks.UserInfo();
                        mUserInfo.mDisplayName = mLastDestination;
                    }

                    if (priority == CallState.NORMAL)
                        callCommand = ":c " + mLastDestination + (deviceId == null ? "" : deviceId); // Command to phone service
                    else {
                        callCommand = ":c " + mLastDestination + ((priority == CallState.URGENT) ? "!" : "!!");
                    }
                }
                else {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "The UUID for " + mLastDestination + " is: " + mUserInfo.mUuid);
                    calledDestination = mUserInfo.mUuid;
                    if (priority == CallState.NORMAL)
                        callCommand = ":c " + mUserInfo.mUuid + (deviceId == null ? "" : deviceId);    // Command to phone service
                    else {
                        callCommand = ":c " + mUserInfo.mUuid + ((priority == CallState.URGENT) ? "!" : "!!");
                    }
                }
                mDestination.setText("");
                mDestination.setCursorVisible(false);

                CallState call = TiviPhoneService.calls.getEmptyCall();
                if (call == null) {
                    return;
                }
                call.bufDialed.setText(calledDestination);      // bufDialed holds string we use to dial, the UUID
                call.setPeerName(mUserInfo.mDisplayName);       // Set peername to have a name to show until call setup
                call.mDefaultDisplayName.setText(mUserInfo.mDisplayName);
                call.bufMsg.setText(getString(R.string.sip_state_calling));
                call.isOcaCall = isOcaCall;
                TiviPhoneService.calls.setCurCall(call);
                activity.doCall(callCommand, mUserInfo.mDisplayName, isOcaCall);
            }
        };
        AsyncUtils.execute(getUidTask, mLastDestination);
    }

    private void handleDialButtonClickWithEmptyDigits() {
        if (!TextUtils.isEmpty(mLastDestination)) {
            // Recall the last number dialed.
            mDestination.setText(mLastDestination);

            // ...and move the cursor to the end of the digits string,
            // so you'll be able to delete digits using the Delete
            // button (just as if you had typed the number manually.)
            //
            // Note we use mDestination.getText().length() here, not
            // mLastDestination.length(), since the EditText widget now
            // contains a *formatted* version of mLastDestination (due to
            // mTextWatcher) and its length may have changed.
            final Editable text = mDestination.getText();
            if (text != null)
                mDestination.setSelection(text.length());
        }
    }

    /**
     * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
     * immediately.
     */
    public void onPressed(View view, boolean pressed) {
        if (pressed) {
            switch ((Integer)view.getTag()) {
                case R.id.one: {
                    keyPressed(KeyEvent.KEYCODE_1);
                    break;
                }
                case R.id.two: {
                    keyPressed(KeyEvent.KEYCODE_2);
                    break;
                }
                case R.id.three: {
                    keyPressed(KeyEvent.KEYCODE_3);
                    break;
                }
                case R.id.four: {
                    keyPressed(KeyEvent.KEYCODE_4);
                    break;
                }
                case R.id.five: {
                    keyPressed(KeyEvent.KEYCODE_5);
                    break;
                }
                case R.id.six: {
                    keyPressed(KeyEvent.KEYCODE_6);
                    break;
                }
                case R.id.seven: {
                    keyPressed(KeyEvent.KEYCODE_7);
                    break;
                }
                case R.id.eight: {
                    keyPressed(KeyEvent.KEYCODE_8);
                    break;
                }
                case R.id.nine: {
                    keyPressed(KeyEvent.KEYCODE_9);
                    break;
                }
                case R.id.zero: {
                    keyPressed(KeyEvent.KEYCODE_0);
                    break;
                }
                case R.id.pound: {
                    keyPressed(KeyEvent.KEYCODE_POUND);
                    break;
                }
                case R.id.star: {
                    keyPressed(KeyEvent.KEYCODE_STAR);
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected onTouch(ACTION_DOWN) event from: " + view);
                    break;
                }
            }
        }
    }

    private void keyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_2:
                playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_3:
                playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_4:
                playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_5:
                playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_6:
                playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_7:
                playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_8:
                playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_9:
                playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_0:
                playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_POUND:
                playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_MS);
                break;
            case KeyEvent.KEYCODE_STAR:
                playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_MS);
                break;
            default:
                break;
        }
//
//        mHaptic.vibrate();
//        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
//        mDestination.onKeyDown(keyCode, event);

        FindDialHelper.getDialHelper().analyseCharModifyEditText(keyCode, mDestination);
    }


    private void setupKeypad(View fragmentView) {
        final int[] buttonIds = new int[] {R.id.zero, R.id.one, R.id.two, R.id.three, R.id.four,
                R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star, R.id.pound};

        final int[] numberIds = new int[] {R.string.dialpad_0_number, R.string.dialpad_1_number,
                R.string.dialpad_2_number, R.string.dialpad_3_number, R.string.dialpad_4_number,
                R.string.dialpad_5_number, R.string.dialpad_6_number, R.string.dialpad_7_number,
                R.string.dialpad_8_number, R.string.dialpad_9_number, R.string.dialpad_star_number,
                R.string.dialpad_pound_number};

        final int[] letterIds = new int[] {R.string.dialpad_0_letters, R.string.dialpad_1_letters,
                R.string.dialpad_2_letters, R.string.dialpad_3_letters, R.string.dialpad_4_letters,
                R.string.dialpad_5_letters, R.string.dialpad_6_letters, R.string.dialpad_7_letters,
                R.string.dialpad_8_letters, R.string.dialpad_9_letters,
                R.string.dialpad_star_letters, R.string.dialpad_pound_letters};

        final Resources resources = getResources();

        FrameLayout dialpadKey;
        TextView numberView;
        TextView lettersView;
        View roundButton;

        for (int i = 0; i < buttonIds.length; i++) {
            dialpadKey = (FrameLayout) fragmentView.findViewById(buttonIds[i]);
            roundButton = dialpadKey.findViewById(R.id.dialpad_rnd_btn);
            numberView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_number);
            lettersView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_letters);
            final String numberString = resources.getString(numberIds[i]);
            numberView.setText(numberString);
            roundButton.setOnClickListener(this);
            roundButton.setTag(buttonIds[i]);
            roundButton.setContentDescription(numberString);
            if (lettersView != null) {
                lettersView.setText(resources.getString(letterIds[i]));
                if (buttonIds[i] == R.id.zero) {
                    lettersView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(
                            R.dimen.dialpad_key_plus_size));
                }
            }
        }
        // Long-pressing zero button will enter '+' instead.
        fragmentView.findViewById(R.id.zero).findViewById(R.id.dialpad_rnd_btn).setOnLongClickListener(this);

    }

    /**
     * Play the specified tone for the specified milliseconds
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
     * call stopTone() afterward.
     *
     * @param tone a tone code from {@link ToneGenerator}
     * @param durationMs tone length.
     */
    private void playTone(int tone, int durationMs) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity.
        AudioManager audioManager = (AudioManager)mParent.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT) || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }
            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, durationMs);
        }
    }

    /**
     * Stop the tone if it is played.
     */
    private void stopTone() {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
        }
    }

    private void inputSwitchClick() {
        if (mDialPad == null)                   // we are in landscape mode, we always show keyboard
            return;
        if (mDialpadInvisible) {
            mDialpadInvisible = false;
            dialPadLayout();
        }
        else {
            mDialpadInvisible = true;
            keyboardLayout();
        }
    }

    // TODO: Merge some of this logic and keyboardLayout()
    private Runnable mShowImeRunnable = new Runnable() {
        public void run() {
            // Check if the mDestination field receives focus while showing the number dial pad. If
            // this is the case switch over to keyboard input. The code simulate a switch to keyboard
            // layout, but places the cursor always at the end.
            if (!mDialpadInvisible) {
                mDialpadInvisible = true;
                if (mDialPad != null) {                       // no dialpad in landscape mode anyway
                    setDialpadVisible(false);
                    mDialPadBottomHeight.getLayoutParams().height =
                            (int) getResources().getDimension(R.dimen.dialpad_bottom_key_height_larger);
                }
                mInputSwitcher.setImageDrawable(mDialPadIcon);
                final Editable text = mDestination.getText();
                if (text != null)
                    mDestination.setSelection(text.length());
                mDestination.setCursorVisible(true);
            }
            DialerUtils.showInputMethod(mDestination);
        }
    };

    private Runnable mShowKeyboard = new Runnable() {
        @Override
        public void run() {
            keyboardLayout();
        }
    };
    // Use the dialText handler to trigger the soft keyboard display. Without delay this does not
    // work reliably.
    private void setImeVisibility(final boolean makeVisible) {
        if (makeVisible) {
            mDestination.postDelayed(mShowImeRunnable, 200);
        }
        else {
            mDestination.removeCallbacks(mShowImeRunnable);
            DialerUtils.hideInputMethod(mDestination);
        }
    }

    // Some explanation regarding focus handling:
    // if we have only dial-with-name then or if the user switches to keyboard entry method then
    // display the soft keyboard.  To do this we may need to toggle the focus if mDestination has
    // the focus, otherwise just request focus for mDestination. This triggers the focus change listener
    // which will request the soft keyboard.
    private void keyboardLayout() {
        if (mDestination.hasFocus())
            mRequestHelper.requestFocus();
        if (!mDestination.hasFocus()) {
            mDestination.requestFocus();
        }
        mDestination.setCursorVisible(true);
        if (mDialPad != null) {                        // no dialpad in landscape mode anyway
            setDialpadVisible(false);
            mDialPadBottomHeight.getLayoutParams().height =
                    (int) getResources().getDimension(R.dimen.dialpad_bottom_key_height_larger);
        }

        mInputSwitcher.setImageDrawable(mDialPadIcon);
    }

    private void dialPadLayout() {
//        mTopLevel.setVisibility(View.INVISIBLE);
        setDialpadVisible(true);
        mDialPadBottomHeight.getLayoutParams().height =
                (int) getResources().getDimension(R.dimen.dialpad_bottom_key_height);
        mInputSwitcher.setImageDrawable(mKeyboardIcon);
        mRequestHelper.requestFocus();          // remove focus from other input fields
    }

    public boolean isDialpadVisible() {
        if (mDialPad != null) {
            return mDialPad.getVisibility() == View.VISIBLE;
        }

        return false;
    }

    public void setDialpadVisible(boolean visible) {
        if (mDialPad != null) {
            mDialPad.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
    }

    @Override
    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.invalidateOptionsMenu();
            }
        }
    }

    @Override
    public void afterTextChanged(Editable input) {
        if (isDestinationEmpty()) {
            mDestination.setCursorVisible(false);
        }
        else
            mDestination.setCursorVisible(true);

        if (mDialpadQueryListener != null) {
            mDialpadQueryListener.onDialpadQueryChanged(mDestination.getText().toString());
        }
    }

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, TAG);

        // Make possible links clickable
        fragmentManager.executePendingTransactions();
        ((TextView) infoMsg.getDialog().findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }
}
