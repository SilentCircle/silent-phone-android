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
package com.silentcircle.messaging.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.logs.Log;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;

public abstract class BaseFragment extends UserInfoListenerFragment {

    private static final String WRAPPER_CLASS = "android.support.v4.app.NoSaveStateFrameLayout";

    private static boolean isWrapped(View view) {
        return view != null && WRAPPER_CLASS.equals(view.getClass().getName());
    }

    protected static View unwrap(View view) {
        if (isWrapped(view)) {
            ViewGroup parent = (ViewGroup) view;
            if (parent.getChildCount() > 0) {
                return parent.getChildAt(0);
            }
        }
        return view;
    }

    protected View findViewById(int viewResourceID) {
        View parent = getView();
        if (parent != null) {
            return parent.findViewById(viewResourceID);
        }
        return null;
    }

    protected View getUnwrappedView() {
        return unwrap(getView());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected void invalidateOptionsMenu() {
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void registerMessagingReceiver(@NonNull Context context, MessagingBroadcastReceiver receiver,
            IntentFilter filter, int priority) {
        filter.setPriority(priority);
        MessagingBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    protected void unregisterMessagingReceiver(MessagingBroadcastReceiver receiver) {
        try {
            Activity activity = getActivity();
            if (receiver != null && activity != null) {
                MessagingBroadcastManager.getInstance(activity).unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.e("BaseFragment", "Failed to unregister view update broadcast receiver.");
            e.printStackTrace();
        }
    }

    protected void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            Activity activity = getActivity();
            if (receiver != null && activity != null) {
                activity.unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.e("BaseFragment", "Failed to unregister view update broadcast receiver.");
            e.printStackTrace();
        }
    }
}

