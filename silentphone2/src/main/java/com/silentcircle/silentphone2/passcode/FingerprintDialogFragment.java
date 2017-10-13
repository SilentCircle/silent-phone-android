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
import android.animation.AnimatorSet;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.TargetApi;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.os.CancellationSignal;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.R;

@TargetApi(Build.VERSION_CODES.M)
public class FingerprintDialogFragment extends DialogFragment{

    public interface FingerprintDialogCallback {
        void onFingerprintSuccess(DialogFragment dialogFragment);
        void onFingerprintFail();
    }

    public static final String TYPE_KEY = "TYPE";
    static final long ERROR_TIMEOUT_MILLIS = 1600;
    static final long SUCCESS_DELAY_MILLIS = 1000;
    static final long SUCCESS_FAST_DELAY_MILLIS = 300;

    public static final int TYPE_ENROLL = 0;
    public static final int TYPE_UNLOCK = 1;

    private final static String TAG = FingerprintDialogFragment.class.getSimpleName();
    private final static String KEY_FINISHED = "finished";

    int mType;
    TextView mFingerprintStatusView;
    private ImageView mIcon;
    Button mCancelButton;

    private FingerprintDialogCallback mCallback;
    private Handler mHandler;

    private FingerprintManagerCompat mFingerprintManager;
    private CancellationSignal mCancellationSignal;
    private MyAuthenticationCallback mAuthenticationCallback;
    private boolean mSelfCancelled;
    private boolean mDismissed;
    private boolean mFinished;

    private Handler mHandlerUI;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mType = TYPE_UNLOCK;
        Bundle arguments = getArguments();
        if (arguments != null && arguments.containsKey(TYPE_KEY)) {
            mType = arguments.getInt(TYPE_KEY);
        }

        // Do not create a new Fragment when the Activity is re-created such as orientation changes.
        setRetainInstance(true);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FingerprintDialog);

        mFingerprintManager = FingerprintManagerCompat.from(getActivity());
        mAuthenticationCallback = new MyAuthenticationCallback();

        mHandler = new Handler(Looper.getMainLooper());
        mHandlerUI = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mCallback = (FingerprintDialogCallback) getActivity();
    }

    @Override
    public void onDestroyView() {
        // http://stackoverflow.com/questions/12433397/android-dialogfragment-disappears-after-orientation-change
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        mHandlerUI.removeCallbacks(mResetErrorTextRunnable);
        super.onDestroyView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog().setCanceledOnTouchOutside(false);

        View v = inflater.inflate(R.layout.fingerprint_dialog_fragment, container, false);

        mCancelButton = (Button) v.findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        mFingerprintStatusView = (TextView) v.findViewById(R.id.fingerprint_status);
        mIcon = (ImageView) v.findViewById(R.id.fingerprint_icon);

        if (mType == TYPE_ENROLL) {
            getDialog().setTitle(R.string.fingerprint_dialog_enroll_title);
            mCancelButton.setText(getString(R.string.cancel_dialog));
        }
        else if (mType == TYPE_UNLOCK) {
            getDialog().setTitle(getString(R.string.fingerprint_dialog_unlock_title));
        }

        setDefaultStateUI();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFinished)
            return;
        startListening(null);
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mFinished)
            return;
        stopListening();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        mDismissed = true;
    }

    //region Fingerprint handling
    //----------------------------------------------------

    private class MyAuthenticationCallback extends FingerprintManagerCompat.AuthenticationCallback {

        public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
            mFinished = true;
            mCancelButton.setOnClickListener(null);
            showSuccess();
            long delay = (mType == TYPE_UNLOCK) ? SUCCESS_FAST_DELAY_MILLIS : SUCCESS_DELAY_MILLIS;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mCallback != null) {
                        mCallback.onFingerprintSuccess(FingerprintDialogFragment.this);
                    }
                }
            }, delay);
        }

        public void onAuthenticationError(int errMsgId, CharSequence errString) {
//            Log.d(TAG, "onAuthenticationError " + errString + " " + errMsgId + " " + mSelfCancelled);
            /*
             * This can be called when an unrecoverable error occurs or when we stop listening. In the
             * first case, we show the error and call the callback. If we stop listening on purpose,
             * we don't do anything. In case we dismissed the dialog, we just call the callback.
             */
            if (!mSelfCancelled) {
                // If the activity changes orientation, we stop listening and start listening again.
                // Thus, we get a cancel error after starting listening again. We ignore it.
                if (errMsgId == FingerprintManager.FINGERPRINT_ERROR_CANCELED) {
                    return;
                }
                mFinished = true;
                showError(errString, false);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mCallback != null) {
                            mCallback.onFingerprintFail();
                        }
                        dismiss();
                    }
                }, ERROR_TIMEOUT_MILLIS);
            }
            else if (mDismissed) {
                if (mCallback != null) {
                    mCallback.onFingerprintFail();
                }
            }
        }

        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            showError(helpString, true);
        }

        public void onAuthenticationFailed() {
            showError(mIcon.getResources().getString(R.string.fingerprint_not_recognized), true);
        }
    }

    public void startListening(FingerprintManager.CryptoObject cryptoObject) {
        mCancellationSignal = new CancellationSignal();
        mSelfCancelled = false;
        mFingerprintManager.authenticate(null, 0, mCancellationSignal, mAuthenticationCallback, null);
    }

    private void stopListening() {
        if (mCancellationSignal != null) {
            mSelfCancelled = true;
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    //endregion
    //----------------------------------------------------

    //region UI helpers
    //----------------------------------------------------

    private void setDefaultStateUI() {
        mIcon.setImageResource(R.drawable.ic_fp_40px);
        mFingerprintStatusView.setText(
                mFingerprintStatusView.getResources().getString(R.string.fingerprint_touch));
        mFingerprintStatusView.setTextColor(
                getResources().getColor(R.color.fingerprint_hint_color, null));
    }

    final Runnable mResetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            if (getActivity() == null) {
                return;
            }
            setDefaultStateUI();
        }
    };


    private void showError(CharSequence error, boolean reset) {
        mIcon.setImageResource(R.drawable.ic_fingerprint_error);
        mFingerprintStatusView.setText(error);
        mFingerprintStatusView.setTextColor(
                getResources().getColor(R.color.fingerprint_warning_color, null));
        runErrorAnimation();

        mHandlerUI.removeCallbacks(mResetErrorTextRunnable);
        if (reset) {
            mHandlerUI.postDelayed(mResetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
        }
    }

    private void showSuccess() {
        mIcon.setImageResource(R.drawable.ic_fingerprint_success);
        mFingerprintStatusView.setText(getString(R.string.fingerprint_recognized));
        mFingerprintStatusView.setTextColor(
                getResources().getColor(R.color.fingerprint_success_color, null));
        mHandlerUI.removeCallbacks(mResetErrorTextRunnable);

        runSuccessAnimation();
    }

    private void runSuccessAnimation() {
        float[] animationValues = new float[] {1, 0.9f, 1, 1.1f, 1.2f, 1.3f
                , 1.4f, 1.45f, 1.4f, 1.3f, 1.2f, 1.1f, 1.05f, 1};
        ObjectAnimator anim1 = ObjectAnimator.ofFloat(mIcon, "scaleX", animationValues);
        ObjectAnimator anim2 = ObjectAnimator.ofFloat(mIcon, "scaleY", animationValues);
        AnimatorSet animSet = new AnimatorSet();

        animSet.playTogether(anim1, anim2);
        animSet.start();
    }

    private void runErrorAnimation() {
        float density = ViewUtil.density(getContext());
        float[] offsets = {8 * density, 6 * density, 6 * density} ;
        ObjectAnimator mShakeAnimator = ObjectAnimator.ofFloat(mIcon, "translationX",
                0,
                offsets[0], - offsets[0],
                offsets[1], - offsets[1],
                0);
        mShakeAnimator.setDuration(200);
        mShakeAnimator.setInterpolator(new LinearInterpolator());
        mShakeAnimator.start();
    }


    //endregion
    //----------------------------------------------------

}
