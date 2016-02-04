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

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.common.widget.FloatingActionButtonController;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.PhoneNumberFormatter;
import com.silentcircle.silentphone2.util.Utilities;

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

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    private EditText mDestination;
    private ImageButton mInputSwitcher;

    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();

    private View mDelete;
    private View mDialPad;
    private View mRequestHelper;
    private LinearLayout mTopLevel;
    private FloatingActionButtonController mFloatingActionButtonController;

    private static String mLastDestination = "";
    private static String mSaveUserInput;
    private static boolean mSavePstnViaOca;
    private String mPresetDestination;
    private boolean mPstnViaOca;

    private boolean mNoNumber;

    private Activity mParent;
    private Drawable mDialPadIcon;
    private Drawable mKeyboardIcon;

    private OnDialpadQueryChangedListener mDialpadQueryListener;

    private int mDialpadSlideInDuration;
    
    private boolean mWasEmptyBeforeTextChange;
    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public static interface DialpadCallbacks {
        /**
         * Call a destination.
         *
         * @param callCommand the command to perform call
         * @param destination the destination, could be formatted number or a name
         */
        void doCall(String callCommand, String destination);

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
        Resources.Theme theme = mParent.getTheme();
        if (theme != null) {
            TypedArray array = theme.obtainStyledAttributes(new int[]{
                    R.attr.sp_ic_dial_pad,
                    R.attr.sp_ic_keyboard,
            });
            if (array != null) {
                mDialPadIcon = array.getDrawable(0);
                mKeyboardIcon = array.getDrawable(1);
                array.recycle();
            }
        }
        else {
            mDialPadIcon = getResources().getDrawable(R.drawable.ic_action_dial_pad_light);
            mKeyboardIcon = getResources().getDrawable(R.drawable.ic_action_keyboard_light);
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
        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(mDestination);
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
            if (mNoNumber && !drawerOpen)
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
            setDestination(mSaveUserInput, mSavePstnViaOca);
            mSaveUserInput = null;
        }
        // Check if the dialer got an Intent to set the destination and
        // perform auto-dialing. This overwrites a saved user input
        if (mPresetDestination != null) {
            setDestination(mPresetDestination, mPstnViaOca);
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

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = activity;
    }

    @Override
    public void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("NO_NUMBER", mNoNumber);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        DialpadCallbacks activity = (DialpadCallbacks)getActivity();
        if (activity == null)
            return;
        if (!hidden && mDestination != null) {
            mNoNumber = TextUtils.isEmpty(DialerActivity.mNumber);

            // In landscape mode we force the "noNumber mode to show the keyboard
            if (mDialPad == null) {
                mNoNumber = true;
                mInputSwitcher.setVisibility(View.INVISIBLE);   // don't show the mode switch
                mInputSwitcher.setEnabled(false);
            }
            else {
                mDestination.setCursorVisible(false);
            }
            mFloatingActionButtonController.scaleIn(mDialpadSlideInDuration);
            activity.onDialpadShown();
            if (mNoNumber) {
                mDestination.postDelayed(mShowKeyboard, 200); // show IME after all views are settled
            }
            else {
                dialPadLayout();
            }
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

    public void dialButtonPressed() {
        FindDialHelper.getDialHelper().resetAnalyser();
        if (isDestinationEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        }
        else {
            final Editable text = mDestination.getText();
            if (text != null) {
                final String destination = text.toString();
                makeCall(destination);
            }
        }
    }

    public void setDestination(String destination, boolean pstnViaOca) {
        mSaveUserInput = null;
        mSavePstnViaOca = false;
        mPstnViaOca = pstnViaOca;
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
        if (mNoNumber) {
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

    /*
     * Below are private helper functions
     */
    private boolean isDestinationEmpty() {
        return mDestination.length() == 0;
    }

    private void makeCall(String dst) {
        DialpadCallbacks activity = (DialpadCallbacks)getActivity();
        if (activity == null)
            return;

        if (dst.indexOf("*##*") == 0) {
            activity.internalCall(dst);
            mDestination.setText("");
            mDestination.setCursorVisible(false);
            return;
        }

        // Remember the last destination dialed:
        // - store URIs as we get them
        // - if a "destination" starts with a letter then treat it as "name"
        // - treat other input as numbers and store without separators
        int priority = CallState.NORMAL;
        if (dst.endsWith("!!")) {
            priority = CallState.EMERGENCY;
            dst = dst.replace("!!", "");
        }
        else if (dst.endsWith("!")) {
            priority = CallState.URGENT;
            dst = dst.replace("!", "");
        }

        if (Character.isLetter(dst.charAt(0)) || Utilities.isUriNumber(dst)) {
            mLastDestination = dst;
        }
        else {
            mLastDestination = PhoneNumberUtils.stripSeparators(dst);
        }
        String callCommand;
        if (priority == CallState.NORMAL)
            callCommand = ":c " + mLastDestination;        // Command to phone service
        else {
            callCommand = ":c " + mLastDestination + ((priority == CallState.URGENT) ? "!" : "!!");
        }
        mDestination.setText("");
        mDestination.setCursorVisible(false);

        CallState call = TiviPhoneService.calls.getEmptyCall();
        if (call == null) {
            return;
        }
        int idx = mLastDestination.indexOf(';');
        String dest = mLastDestination;
        if (idx > 0) {
            dest = dest.substring(0, idx);
        }
        call.bufDialed.setText(dest);           // bufDialed holds normalized name/number
        call.bufMsg.setText(getString(R.string.sip_state_calling));
        call.mPstnViaOca = mPstnViaOca;
        TiviPhoneService.calls.setCurCall(call);
        activity.doCall(callCommand, mLastDestination);
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
        if (mNoNumber) {
            mNoNumber = false;
            dialPadLayout();
        }
        else {
            mNoNumber = true;
            keyboardLayout();
        }
    }

    private Runnable mShowImeRunnable = new Runnable() {
        public void run() {
            // Check if the mDestination field receives focus while showing the number dial pad. If
            // this is the case switch over to keyboard input. The code simulate a switch to keyboard
            // layout, but places the cursor always at the end.
            if (!mNoNumber) {
                mNoNumber = true;
                if (mDialPad != null)                       // no dialpad in landscape mode anyway
                    mDialPad.setVisibility(View.GONE);
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
        if (mDialPad != null)                       // no dialpad in landscape mode anyway
            mDialPad.setVisibility(View.GONE);
        mInputSwitcher.setImageDrawable(mDialPadIcon);
    }

    private void dialPadLayout() {
//        mTopLevel.setVisibility(View.INVISIBLE);
        mDialPad.setVisibility(View.VISIBLE);
        mInputSwitcher.setImageDrawable(mKeyboardIcon);
        mRequestHelper.requestFocus();          // remove focus from other input fields
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
}
