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
package com.silentcircle.silentphone2.dialogs;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.widget.TextView;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;

/**
 * Simple dialog fragment.
 *
 * Created by rli on 10/29/14.
 */
public class InfoMsgDialogFragment extends DialogFragment {

    public static final String TAG_INFO_MESSAGE_DIALOG =
            "com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment.dialog";

    public static String MESSAGE = "message";
    public static String HTML_MESSAGE = "html_message";
    public static String TITLE = "title";
    public static String POSITIVE_BTN_LABEL = "positive_button_label";
    public static String NEGATIVE_BTN_LABEL = "negative_button_label";
    private Activity mParent;

    InfoDialogCallback mCallback;

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public interface InfoDialogCallback {
        /**
         * Callback if user clicks positive button
         */
        void onClickedPositive();

        /**
         * Callback if user clicks negative button
         */
        void onClickedNegative();

        /**
         * Callback if user cancels (with back button) the dialog
         */
        void onClickedCancel();
    }

    /**
     * Standard function to get a DialogFragment instance, requires a bundle only,
     *
     * @param args Bundle containing the arguments for the dialog fragment
     * @return A InfoMsgDialogFragment instance
     */
    public static InfoMsgDialogFragment newInstance(Bundle args) {
        InfoMsgDialogFragment f = new InfoMsgDialogFragment();
        f.setArguments(args);

        return f;
    }

    // There are 4 constructors because of an issue with AlertDialog.Builder#set*(String) stripping links
    public static InfoMsgDialogFragment newInstance(String title, String msg, int positiveBtnLabel, int negativeBtnLabel) {

        Bundle args = new Bundle();
        args.putString(TITLE, title);
        args.putString(MESSAGE, msg);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        return newInstance(args);
    }

    public static InfoMsgDialogFragment newInstance(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {

        Bundle args = new Bundle();
        args.putInt(TITLE, titleResId);
        args.putInt(MESSAGE, msgResId);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        return newInstance(args);
    }

    public static InfoMsgDialogFragment newInstance(String title, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {

        Bundle args = new Bundle();
        args.putString(TITLE, title);
        args.putInt(MESSAGE, msgResId);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        return newInstance(args);
    }

    public static InfoMsgDialogFragment newInstance(int titleResId, String msg, int positiveBtnLabel, int negativeBtnLabel) {

        Bundle args = new Bundle();
        args.putInt(TITLE, titleResId);
        args.putString(MESSAGE, msg);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        return newInstance(args);
    }

    public static InfoMsgDialogFragment newInstance(int titleResId, Spanned msg, int positiveBtnLabel, int negativeBtnLabel) {

        Bundle args = new Bundle();
        args.putInt(TITLE, titleResId);
        args.putCharSequence(HTML_MESSAGE, msg);
        args.putInt(POSITIVE_BTN_LABEL, positiveBtnLabel);
        args.putInt(NEGATIVE_BTN_LABEL, negativeBtnLabel);

        return newInstance(args);
    }

    public static void showDialog(@Nullable Activity activity, int titleResId, int msgResId,
            int positiveBtnLabel, int negativeBtnLabel) {
        if (activity == null) {
            return;
        }
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msgResId,
                positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = activity.getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                    .add(infoMsg, InfoMsgDialogFragment.TAG_INFO_MESSAGE_DIALOG)
                    .commitAllowingStateLoss();
        }
    }

    public static void showDialog(@Nullable Activity activity, int titleResId, String msg,
                                  int positiveBtnLabel, int negativeBtnLabel) {
        if (activity == null) {
            return;
        }
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msg,
                positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = activity.getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                    .add(infoMsg, InfoMsgDialogFragment.TAG_INFO_MESSAGE_DIALOG)
                    .commitAllowingStateLoss();
        }
    }

    public InfoMsgDialogFragment() {
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        commonOnAttach(getActivity());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            commonOnAttach(activity);
        }
    }

    private void commonOnAttach(Activity activity) {
        mParent = activity;
        Fragment target = getTargetFragment();
        mCallback = (target instanceof InfoDialogCallback) ? (InfoDialogCallback)target : null;

    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (mCallback != null) {
            mCallback.onClickedPositive();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
        Bundle args = getArguments();
        if (args == null)
            return null;

        boolean helperActivity = args.getBoolean("helper_activity", false);
        if (helperActivity) {
            mCallback = (InfoDialogCallback)mParent;
        }
        Object title = args.get(TITLE);
        Object message = args.get(MESSAGE);
        CharSequence htmlMessage = args.getCharSequence(HTML_MESSAGE);
        boolean asHtml = false;

        if(title instanceof String) {
            builder.setTitle((String) title);
        } else {
            if(args.getInt(TITLE, -1) > 0) {
                builder.setTitle(args.getInt(TITLE));
            }
        }

        TextView messageView = new TextView(new ContextThemeWrapper(mParent, R.style.InfoDialogMessage));
        final SpannableString spannableString;
        if (message instanceof String) {
            spannableString = new SpannableString((String) message);
        } else if (args.getInt(MESSAGE, -1) > 0) {
            spannableString = null;
            messageView.setText(args.getInt(MESSAGE));
        } else if (!TextUtils.isEmpty(htmlMessage)) {
            asHtml = true;
            spannableString = new SpannableString(htmlMessage);
        } else {
            if (BuildConfig.DEBUG) {
                throw new IllegalArgumentException("No message provided for dialog.");
            } else {
                Log.wtf("InfoMsgDialogFragment", "No message provided for dialog.");
                spannableString = new SpannableString("");
            }
        }

        if (spannableString != null) {
            // messageView.setAutoLinkMask(Linkify.WEB_URLS ... ) may crash(?)  with absent web view
            try {
                Linkify.addLinks(spannableString, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.MAP_ADDRESSES);
            } catch (Throwable t) {
                // ignore error to linkify dialog text to be able to show it
                t.printStackTrace();
            }

            if (asHtml) {
                // hack
                // TODO are there some more messages with html inside. maybe all. then linkify can be abandoned.
                messageView.setText(Html.fromHtml(spannableString.toString()));
            } else {
                messageView.setText(spannableString);
            }
        }

        messageView.setLinkTextColor(ContextCompat.getColor(SilentPhoneApplication.getAppContext(),
                R.color.chat_message_text_link_color));
        messageView.setMovementMethod(
                new ViewUtil.MovementCheck(mParent, messageView, R.string.toast_no_browser_found));
        builder.setView(messageView);

        if(args.getInt(POSITIVE_BTN_LABEL, -1) > 0) {
            builder.setPositiveButton(args.getInt(POSITIVE_BTN_LABEL), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (mCallback != null) {
                        mCallback.onClickedPositive();
                    }
                }
            });
        }
        if(args.getInt(NEGATIVE_BTN_LABEL, -1) > 0) {
            builder.setNegativeButton(args.getInt(NEGATIVE_BTN_LABEL), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (mCallback != null) {
                        mCallback.onClickedNegative();
                    }
                    else {
                        mParent.finish();
                    }
                }
            });
        }
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
