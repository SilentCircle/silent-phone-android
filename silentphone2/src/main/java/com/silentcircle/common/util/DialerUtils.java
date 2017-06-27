/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.common.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.silentphone2.R;

/**
 * Created by werner on 03.02.15.
 */
public class DialerUtils {
    /**
     * Attempts to start an activity and displays a toast with the default error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent) {
        startActivityWithErrorToast(context, intent, R.string.activity_not_available);
    }

    /**
     * Attempts to start an activity and displays a toast with a provided error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     * @param msgId Resource ID of the string to display in an error message if the activity is
     *              not found.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent, int msgId) {
        try {
//            if (Intent.ACTION_CALL.equals(intent.getAction())) {
//                // All dialer-initiated calls should pass the touch point to the InCallUI
//                Point touchPoint = TouchPointManager.getInstance().getPoint();
//                if (touchPoint.x != 0 || touchPoint.y != 0) {
//                    Bundle extras = new Bundle();
//                    extras.putParcelable(TouchPointManager.TOUCH_POINT, touchPoint);
//                    intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
//                }
//
//                ((Activity) context).startActivityForResult(intent, 0);
//            } 
//            else {
                context.startActivity(intent);
//            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by a
     * localized delimiter such as ", ".
     *
     * @param resources Resources used to get list delimiter.
     * @param list List of char sequences to join.
     * @return Joined char sequences.
     */
    public static CharSequence join(Resources resources, Iterable<CharSequence> list) {
        final CharSequence separator = resources.getString(R.string.list_delimeter);
        return TextUtils.join(separator, list);
    }

    /**
     * Sets the image asset and text for an empty list view (see empty_list_view.xml).
     *
     * @param emptyListView The empty list view.
     * @param imageResId The resource id for the drawable to set as the image.
     * @param strResId The resource id for the string to set as the message.
     * @param res The resources to obtain the image and string from.
     */
    public static void configureEmptyListView(
            View emptyListView, int imageResId, int strResId, Resources res) {
        configureEmptyListView(
                emptyListView, imageResId, strResId, -1, res);
    }

    public static void configureEmptyListView(
            View emptyListView, int imageResId, int strResId, int headerResId, Resources res) {
        ImageView emptyListViewImage =
                (ImageView) emptyListView.findViewById(R.id.emptyListViewImage);

        emptyListViewImage.setImageDrawable(
                ContextCompat.getDrawable(SilentPhoneApplication.getAppContext(), imageResId));
        emptyListViewImage.setContentDescription(res.getString(strResId));

        if (headerResId != -1) {
            TextView emptyListViewHeader =
                    (TextView) emptyListView.findViewById(R.id.emptyListViewHeader);
            emptyListViewHeader.setText(res.getString(headerResId));
            emptyListViewHeader.setVisibility(View.VISIBLE);
        }

        TextView emptyListViewMessage =
                (TextView) emptyListView.findViewById(R.id.emptyListViewMessage);
        emptyListViewMessage.setText(res.getString(strResId));
    }

    /**
     * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
     * null.
     *
     * @param closeable to close.
     */
//    public static void closeQuietly(AutoCloseable closeable) {
//        if (closeable != null) {
//            try {
//                closeable.close();
//            } catch (RuntimeException rethrown) {
//                throw rethrown;
//            } catch (Exception ignored) {
//            }
//        }
//    }

    public static void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    public static void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void showInputMethod(Context ctx) {
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
        }
    }

    public static void hideInputMethod(Activity activity) {
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
