/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.silentphone2.passcode;

import android.animation.Animator;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.InCallActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.Utilities;

import java.lang.ref.WeakReference;

/**
 * Activity where the user can enter a passcode. It can be used from different actions like setting
 * a passcode, unlocking the app, validating the passcode, changing the passcode.
 */
public class PasscodeEnterActivity extends AppLifecycleNotifierBaseActivity
        implements FingerprintDialogFragment.FingerprintDialogCallback, View.OnClickListener {

    //TODO: perhaps make it adjustable?
    private static final int PASSCODE_SIZE = 4;
    private static final String TAG = PasscodeEnterActivity.class.getSimpleName();
    private static final int VIBRATION_DURTION_MS = 400;
    private static final int SHAKE_DURTION_MS = 400;
    private static final int CORSSFADE_DURATION_MS = 400;
    private static final int FADE_ANIM_DURATION_MS = 400;
    private static final int CHECK_LOCK_INTERVAL_MS = 10000;

    /*
     * These are the actions this activity handles. A different flow will be created for each of
     * them, by making use the internal states defined below.
     */
    public static final String ACTION_PASSCODE_SET = "passcode_set";
    public static final String ACTION_PASSCODE_CHANGE = "passcode_change";
    public static final String ACTION_PASSCODE_VALIDATE = "passcode_validate";
    public static final String ACTION_PASSCODE_UNLOCK = "passcode_unlock";

    /*
     * Each of these states adjusts the UI and the flow with respect to the current activity action.
     * One state may be used from one that 1 actions. For example, "STATE_PASSCODE_ENTER_ANY" is
     * used by both ACTION_PASSCODE_SET and ACTION_PASSCODE_CHANGE.
     */
    private static final int STATE_PASSCODE_ENTER_ANY = 1;
    private static final int STATE_PASSCODE_REENTER = 2;
    private static final int STATE_PASSCODE_VERIFY = 3;

    public static final String EXTRA_PASSCODE_KEY = "passcode_key";

    private static final String DIALOG_FRAGMENT_TAG = "fingerprintFragment";

    private PasscodeManager mPasscodeManager;
    private String mAction;
    private ImageButton mFingerprintButton;
    private View rootView;
    private ImageView[] mDotArray;
    private EditText mEditText;
    private boolean mEditingEnabled = true;
    private TextView mTextView;
    private TextView mTryAgainTextView;
    private ViewGroup mDotsContainer;
    private final int[] mCheckedState = new int[]{android.R.attr.state_checked};
    private final int[] mUncheckedState = new int[]{-android.R.attr.state_checked};
    private final int mPasscodeSize = PASSCODE_SIZE;
    private Handler handler;
    private CheckLockRunnable mCheckLockRunnable;
    Vibrator mVibrator;
    Animator mShakeAnimator;

    private int mState;
    private String mNewPasscode;
    private String mPreviousEnteredPasscode;
    private boolean mUILocked;

    private FingerprintManagerCompat mFingerprintManager;
    private boolean mHasFingerprintHW;
    private boolean mFingerprintUnlockEnabled;

    private boolean mIsDestroyed;
    private boolean mIsStopped;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        if (inCall()) {
//            int windowFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
//            getWindow().addFlags(windowFlags);
//        }

        setContentView(R.layout.activity_passcode_enter_screen);
        mAction = getIntent().getAction();

        mPasscodeManager = PasscodeManager.getSharedManager();
        handler = new Handler(Looper.getMainLooper());
        mCheckLockRunnable = new CheckLockRunnable(this);
        mVibrator =  (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mEditText = (EditText) findViewById(R.id.passcode_pin_edit_text);
        prepareTextInput();
        mTextView = (TextView) findViewById(R.id.passcode_message);
        mTryAgainTextView = (TextView) findViewById(R.id.passcode_try_again);

        rootView = findViewById(R.id.full_screen_view);
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mEditingEnabled)
                    return;
                showKeyboard();
            }
        });

        mFingerprintButton = (ImageButton) findViewById(R.id.fingerpring_button);
        mFingerprintButton.setVisibility(View.GONE);
        mFingerprintButton.setEnabled(false);

        if (mAction == ACTION_PASSCODE_UNLOCK) {
            mFingerprintManager = FingerprintManagerCompat.from(this);
            mHasFingerprintHW = mFingerprintManager.isHardwareDetected();
        }

        mDotsContainer = (ViewGroup) findViewById(R.id.dots_container);
        initializeDotArray();
        prepareShakeAnimation();
    }


    @Override
    protected void onStart() {
        super.onStart();
        mIsStopped = false;

        processIntent();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsStopped = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        mIsDestroyed = true;
    }

    private void processIntent() {
        mAction = getIntent().getAction();

        // Choose initial state according to the intent's action
        switch (mAction) {
            case ACTION_PASSCODE_SET: {
                mState = STATE_PASSCODE_ENTER_ANY;
            }
            break;
            case ACTION_PASSCODE_CHANGE:
            case ACTION_PASSCODE_VALIDATE:
            case ACTION_PASSCODE_UNLOCK: {
                mState = STATE_PASSCODE_VERIFY;
            }
            break;
        }

        initialize();
    }

    private void initialize() {
        boolean willShowFingerprintFragment = false;
        if (mAction == ACTION_PASSCODE_UNLOCK) {
            if (mHasFingerprintHW && mPasscodeManager.isFingerprintUnlockEnabled()
                    && mPasscodeManager.isFingerprintAllowed()) {
                willShowFingerprintFragment = true;
                mFingerprintUnlockEnabled = true;
                mFingerprintButton.setVisibility(View.VISIBLE);
                mFingerprintButton.setEnabled(true);
                mFingerprintButton.setOnClickListener(this);
            }
        }

        if (!willShowFingerprintFragment) {
            mFingerprintUnlockEnabled = false;
            mFingerprintButton.setVisibility(View.GONE);
            mFingerprintButton.setEnabled(false);
        }

        mTryAgainTextView.setVisibility(View.INVISIBLE);
        doUpdateDotsForCharCount(0);
        updateUIForCurrentState();

        mNewPasscode = null;
        handler.removeCallbacksAndMessages(null);

        boolean keyboadStartsHidden = false;
        if (mPasscodeManager.getRemainingAttemptsUntilWipe() == 0) {
            // This state can only happen if wipe is enabled and the user somehow enters the activity
            // after a failed wipe. This can happen if he uses a device older than Android 4.4
            mTextView.setText(getString(R.string.passcode_sp_locked));
            mDotsContainer.setVisibility(View.INVISIBLE);
            mEditingEnabled = false;
            mFingerprintButton.setVisibility(View.GONE);
            keyboadStartsHidden = true;
        }
        else {
            if (mPasscodeManager.getRemainingLockDownTime() != 0) {
                setUILocked(true, false);
                keyboadStartsHidden = true;
            }
            else {
                if (mUILocked) {
                    setUILocked(false, false);
                }
                if (willShowFingerprintFragment) {
                    // the default initialization w fingerprint
                    mEditingEnabled = false;
                    checkShowFingerprintFragment();
                    keyboadStartsHidden = true;
                }
            }
        }
        if (keyboadStartsHidden) {
//            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN |
//                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            hideKeyboard();
        }
        else {
            // the default initialization w/o fingerprint
            mEditingEnabled = true;
            showKeyboard();
        }
    }

//    @Override
//    protected void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//        Log.d(TAG, "onNewIntent");
//        setIntent(intent);
//        processIntent();
//    }

    @Override
    public void onBackPressed() {
        if (mAction.equals(ACTION_PASSCODE_UNLOCK)) {
            /*
             * Normally, we don't want the back button to operate. If it does, it will try to go to the
             * previous activity, which will launch this again. So, the user will
             * still get back here after a small animation transition.
             */
            if (inCall()) {
                // bring the InCallActivity to the top
                Intent forward = new Intent();
                Bundle bundle = new Bundle();
                bundle.putInt(TiviPhoneService.CALL_TYPE, TiviPhoneService.CALL_TYPE_RESTART);
                forward.putExtras(bundle);
                forward.setClass(this, InCallActivity.class);
                forward.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(forward);
            }
        }
        else {
            super.onBackPressed();
        }
    }


    /**
     * Callback that processes the inserted passcode and manages state transitions and activity flow.
     *
     * @param passcode the passcode entered by the user
     */
    private void onPasscodeInserted(String passcode) {
        switch (mState) {
            case STATE_PASSCODE_ENTER_ANY: {
                mNewPasscode = passcode;
                // transition to STATE_PASSCODE_REENTER
                mState = STATE_PASSCODE_REENTER;
                updateUIForCurrentState();
            }
            break;
            case STATE_PASSCODE_REENTER: {
                if (mNewPasscode.equals(passcode)) {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_PASSCODE_KEY, passcode);
                    setResult(RESULT_OK, result);
                    callFinish();
                }
                else {
                    // transition to STATE_PASSCODE_ENTER_ANY
                    showToast(getString(R.string.passcode_mismatch)
                            + " " + getString(R.string.passcode_try_again));
                    mState = STATE_PASSCODE_ENTER_ANY;
                    updateUIForCurrentState();
                }
            }
            break;
            case STATE_PASSCODE_VERIFY: {
                if (mPasscodeManager.isPasscodeCorrect(passcode)) {
                    mPasscodeManager.resetFailedAttemptsCount();
                    switch (mAction) {
                        case ACTION_PASSCODE_UNLOCK: {
                            mPasscodeManager.authorize(passcode);
                            callFinish();
                        }
                        break;
                        case ACTION_PASSCODE_VALIDATE: {
                            setResult(RESULT_OK);
                            callFinish();
                        }
                        break;
                        case ACTION_PASSCODE_CHANGE: {
                            // transition to STATE_PASSCODE_ENTER_ANY
                            mState = STATE_PASSCODE_ENTER_ANY;
                            updateUIForCurrentState();
                        }
                        break;
                    }
                }
                else {
                    mPasscodeManager.increaseFailedAttemptsCount();
                    int lockTimeRemain = mPasscodeManager.startLockdownTimer();

                    String warningString = getString(R.string.passcode_wrong) + " " + getString(R.string.passcode_try_again);
                    if (mPasscodeManager.isWipeEnabled()) {
                        int remainingAttempts = mPasscodeManager.getRemainingAttemptsUntilWipe();
                        if (remainingAttempts == 0) {
                            mVibrator.vibrate(VIBRATION_DURTION_MS);
                            callFinish();
                            startWipe();
                            return;
                        } else if (remainingAttempts <= 3) {
                            warningString = getWipeWarnMessageString(remainingAttempts);
                        }
                    }

                    if (lockTimeRemain == 0) {
                        updateUIForCurrentState();
                    }
                    else {
                        // disable fingerprint unlock when lockdown is engaged
                        if (mFingerprintUnlockEnabled) {
                            mFingerprintUnlockEnabled = false;
                            hideFingerprintButton();
                        }
                        setUILocked(true, true);
                    }
                    showToast(warningString);
                    mVibrator.vibrate(VIBRATION_DURTION_MS);
                    mShakeAnimator.start();
                }
            }
            break;
        }
    }

    private void startWipe() {
        Utilities.wipePhone(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fingerpring_button: {
                hideKeyboard();
                mEditingEnabled = false;
                checkShowFingerprintFragment();
            }
            break;
        }
    }

    @Override
    public void onFingerprintSuccess(DialogFragment dialogFragment) {
        if (isFinishing()) {
            return;
        }
        mPasscodeManager.resetFailedAttemptsCount();
        mPasscodeManager.authorizeFingerprint();
        callFinish();
    }

    @Override
    public void onFingerprintFail() {
        mEditingEnabled = true;
        showKeyboard();
    }

    //region UI methods
    //----------------------------------------------------
    ObjectAnimator loo;
    private void prepareShakeAnimation() {
        float density = ViewUtil.density(this);
        float[] offsets = {25 * density, 15 * density, 6 * density} ;
        mShakeAnimator = ObjectAnimator.ofFloat(mDotsContainer, "translationX", 0,
                offsets[0], - offsets[0],
                offsets[1], - offsets[1], offsets[2], - offsets[2], 0);
        mShakeAnimator.setDuration(SHAKE_DURTION_MS);
        mShakeAnimator.setInterpolator(new LinearInterpolator());
        mShakeAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Makes sure the dots are updated to the correct number, because we have disabled
                // the update during animation so that all of them are filled.
                updateDotsForCharCount(mEditText.length());
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    private void initializeDotArray() {
        mDotArray = new ImageView[mDotsContainer.getChildCount()];
        for (int index = 0; index < mDotsContainer.getChildCount(); index++) {
            mDotArray[index] = (ImageView) mDotsContainer.getChildAt(index);
            mDotArray[index].setImageState(mCheckedState, true);
        }
    }

    /**
     * Calls {@link #doUpdateDotsForCharCount(int)} with a small delay.
     *
     * This is because if we updated the dots instantly, the user would not see some intermediate
     * states, like when re-entering the passcode the dots would go from 3 to 0. Instead from 3 to 4
     * to 0.
     * @param charCount
     */
    private void updateDotsForCharCount(final int charCount) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // Don't clear the dots while animating.
                if (mShakeAnimator.isRunning() && charCount == 0) {
                    return;
                }

                doUpdateDotsForCharCount(charCount);
            }
        });
    }

    /**
     * Marks the first charCounts dots as checked and the rest of them unchecked.
     * @param charCount the number of dots that should be checked
     */
    private void doUpdateDotsForCharCount(int charCount) {
        for (int i = 0; i < mDotArray.length; i++) {
            boolean enteredChar = i < charCount;
            final int[] state = (enteredChar) ? mCheckedState : mUncheckedState;
            mDotArray[i].setImageState(state, true);
        }
    }

    private void showKeyboard() {
        showWipeWarnMessage();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN |
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        mEditText.requestFocus();
        DialerUtils.showInputMethod(mEditText);
    }

    private void hideKeyboard() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        DialerUtils.hideInputMethod(mEditText);
    }

    private void prepareTextInput() {
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // prevent pressing the "enter" key to hide the keyboard
                    return true;
                } else {
                    return false;
                }
            }
        });

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!mEditingEnabled) {
                    if (s.length() != 0) {
                        s.clear();
                    }
                    return;
                }

                // delete more than mPasscodeSize characters
                if (s.length() > mPasscodeSize) {
                    s.delete(mPasscodeSize, s.length());
                }
                String passcode = s.toString();

                updateDotsForCharCount(s.length());

                // prevent calling the onPasscodeInserted twice in a row for the same passcode
                if (passcode.length() == mPasscodeSize) {
                    if (!passcode.equals(mPreviousEnteredPasscode)) {
                        mPreviousEnteredPasscode = passcode;
                        onPasscodeInserted(passcode);
                    }
                }
                else {
                    mPreviousEnteredPasscode = null;
                }
            }
        });
    }

    private boolean inCall() {
        return TiviPhoneService.calls.getCallCount() > 0 ;
    }

    private void updateUIForCurrentState() {
        switch (mState) {
            case STATE_PASSCODE_ENTER_ANY: {
                if (ACTION_PASSCODE_SET.equals(mAction)) {
                    mTextView.setText(getString(R.string.passcode_enter_any));
                }
                else if (ACTION_PASSCODE_CHANGE.equals(mAction)) {
                    mTextView.setText(getString(R.string.passcode_enter_new));
                }
            }
            break;
            case STATE_PASSCODE_REENTER: {
                mTextView.setText(getString(R.string.passcode_reenter));
            }
            break;
            case STATE_PASSCODE_VERIFY: {
                if (ACTION_PASSCODE_CHANGE.equals(mAction)) {
                    mTextView.setText(getString(R.string.passcode_enter_old));
                }
                else {
                    mTextView.setText(getString(R.string.passcode_enter_your));
                }
            }
        }
        mEditText.getText().clear();
    }

    private String getWipeWarnMessageString(int remainingAttempts) {
        int stringId = (remainingAttempts == 1) ? R.string.passcode_try_left :
                R.string.passcode_tries_left;
        String warningString = getString(stringId, remainingAttempts);
        warningString += " " + getString(R.string.passcode_data_wiped_warn);
        return warningString;
    }

    private void showWipeWarnMessage() {
        // show the wipe warning if the user is not locked out of SP and has 3 or less remaining
        // attempts
        if (mPasscodeManager.isWipeEnabled() && mPasscodeManager.getRemainingLockDownTime() == 0) {
            int remainingAttempts = mPasscodeManager.getRemainingAttemptsUntilWipe();
            if (remainingAttempts != 0 && remainingAttempts <= 3) {
                String warningString = getWipeWarnMessageString(remainingAttempts);
                showToast(warningString);
            }
        }
    }

    private static class CheckLockRunnable implements Runnable {

        private WeakReference<PasscodeEnterActivity> mWeakActivity;

        CheckLockRunnable(PasscodeEnterActivity activity) {
            mWeakActivity = new WeakReference<PasscodeEnterActivity>(activity);
        }

        @Override
        public void run() {
            PasscodeEnterActivity activity = mWeakActivity.get();
            if (activity == null || activity.mIsDestroyed) {
                return;
            }
            long remainTime = activity.mPasscodeManager.getRemainingLockDownTime();
            if (remainTime != 0) {
                activity.handler.postDelayed(this, CHECK_LOCK_INTERVAL_MS);
                activity.updateUILockedMessage(remainTime);
            }
            else {
                boolean animated = !activity.mIsStopped;
                activity.setUILocked(false, animated);
            }
        }
    }

    private void setUILocked(boolean locked, boolean animated) {
        if (locked) {
            mUILocked = true;
            handler.removeCallbacks(mCheckLockRunnable);
            handler.postDelayed(mCheckLockRunnable, CHECK_LOCK_INTERVAL_MS);
            mEditingEnabled = false;
            hideKeyboard();
            mEditText.getText().clear();
            mEditText.setEnabled(false);
            mEditText.setFocusable(false);
            if (mAction.equals(ACTION_PASSCODE_UNLOCK))
                mTextView.setText(getString(R.string.passcode_sp_locked));
            updateUILockedMessage(mPasscodeManager.getRemainingLockDownTime());
            if (animated) {
                AnimUtils.crossFadeViews(mTryAgainTextView, mDotsContainer, CORSSFADE_DURATION_MS);
                if (mFingerprintUnlockEnabled) {
                    AnimUtils.fadeOut(mFingerprintButton, FADE_ANIM_DURATION_MS);
                }
            }
            else {
                mDotsContainer.setVisibility(View.INVISIBLE);
                mTryAgainTextView.setVisibility(View.VISIBLE);
                if (mFingerprintUnlockEnabled) {
                    mFingerprintButton.setVisibility(View.GONE);
                    mFingerprintButton.setEnabled(false);
                }
            }
        }
        else {
            mUILocked = false;
            handler.removeCallbacks(mCheckLockRunnable);
            mEditingEnabled = true;
            mEditText.setEnabled(true);
            mEditText.setFocusableInTouchMode(true);
            showKeyboard();
            updateUIForCurrentState();
            if (animated) {
                AnimUtils.crossFadeViews(mDotsContainer, mTryAgainTextView, CORSSFADE_DURATION_MS);
                if (mFingerprintUnlockEnabled) {
                    AnimUtils.fadeIn(mFingerprintButton, FADE_ANIM_DURATION_MS);
                    mFingerprintButton.setEnabled(true);
                }
            }
            else {
                mDotsContainer.setVisibility(View.VISIBLE);
                mDotsContainer.setAlpha(1);
                mTryAgainTextView.setVisibility(View.INVISIBLE);
                if (mFingerprintUnlockEnabled) {
                    mFingerprintButton.setVisibility(View.VISIBLE);
                    mFingerprintButton.setAlpha(1);
                    mFingerprintButton.setEnabled(true);
                }
            }
        }
    }

    private void updateUILockedMessage(long seconds) {
        Log.d(TAG, seconds + " seconds until SP unlock.");
        long minutes = (seconds + 59) / 60; // ceil division
        int stringId = (minutes == 1) ? R.string.passcode_minute : R.string.passcode_minutes;
        mTryAgainTextView.setText(getString(R.string.passcode_locked_try_again)
                + " " + getString(stringId, minutes));
    }

    private void showToast(String string) {
        Toast toast = Toast.makeText(this, string, Toast.LENGTH_SHORT);
        if (mDotsContainer.getY() != 0) {
            int[] dotsPosition = new int[2];
            mDotsContainer.getLocationInWindow(dotsPosition);
            int[] rootPosition = new int[2];
            rootView.getLocationInWindow(rootPosition);
            int mDotsContainerY = dotsPosition[1] - rootPosition[1];
            // Places the toast below the dots
            int offset = ViewUtil.dp(60, this);
            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, mDotsContainerY + offset);
        }
        toast.show();
    }

    private void callFinish() {
        mTextView.setFocusable(false);
        mEditingEnabled = false;
        hideKeyboard();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 30);
    }

    private void checkShowFingerprintFragment() {
        if (!mFingerprintManager.hasEnrolledFingerprints()) {
            // this can happen if the user changed the fingerprints since the time he
            // enabled Fingerprint unlock in SP
            mPasscodeManager.setFingerprintUnlockEnabled(false);
            mFingerprintButton.setVisibility(View.GONE);
            mFingerprintButton.setEnabled(false);
            showFingerprintChangedWarning();
            return;
        }

        showFingerprintFragment();
    }
    private void showFingerprintFragment() {
        FragmentManager fragmentManager = getFragmentManager();
        Fragment dialogFragment = fragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG);
        if (dialogFragment != null) {
            return;
        }
        FingerprintDialogFragment fingerprintDialog = new FingerprintDialogFragment();
        Bundle args = new Bundle();
        args.putInt(FingerprintDialogFragment.TYPE_KEY,
                FingerprintDialogFragment.TYPE_UNLOCK);
        fingerprintDialog.setArguments(args);
        fingerprintDialog.show(fragmentManager, DIALOG_FRAGMENT_TAG);
    }

    private void showFingerprintChangedWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.fingerprint_changed_title)
                .setMessage(R.string.fingerprint_changed_message)
                .setCancelable(true)
                .setPositiveButton(R.string.dialog_button_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                                mEditingEnabled = true;
                                showKeyboard();
                            }
                        });
        builder.create().show();
    }

    private void hideFingerprintButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            AnimUtils.fadeOut(mFingerprintButton, FADE_ANIM_DURATION_MS);
            return;
        }
        AnimUtils.startLayoutTransition((ViewGroup) findViewById(R.id.full_screen_view), LayoutTransition.CHANGE_DISAPPEARING);
        mFingerprintButton.setVisibility(View.GONE);
    }

    //endregion
    //----------------------------------------------------

}
