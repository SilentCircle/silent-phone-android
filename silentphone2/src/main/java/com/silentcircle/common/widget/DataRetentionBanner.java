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
package com.silentcircle.common.widget;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.silentcircle.common.util.DRUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;

import java.util.HashSet;
import java.util.Set;

/**
 * Widget to show at the top of conversation views when data retention is enabled for one of
 * the parties participating in conversation.
 */
public class DataRetentionBanner extends LinearLayout implements View.OnClickListener {

    private static final String TAG = DataRetentionBanner.class.getSimpleName();

    private Set<String> mPartners = new HashSet<>();

    private TextView mBannerText;
    private String mDataRetentionOn;
    private String mCommunicationBlocked;
    private String mCallsBlocked;
    private String mMessagingBlocked;

    public DataRetentionBanner(Context context) {
        this(context, null);
    }

    public DataRetentionBanner(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DataRetentionBanner(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inflate(context, R.layout.widget_data_retention_banner, this);

        mDataRetentionOn = context.getString(R.string.data_retention_on);
        mCommunicationBlocked = context.getString(R.string.data_retention_communication_blocked);
        mCallsBlocked = context.getString(R.string.data_retention_calls_blocked);
        mMessagingBlocked = context.getString(R.string.data_retention_messaging_blocked);
        mBannerText = (TextView) findViewById(R.id.dr_banner_text);
        View rootView = findViewById(R.id.data_retention_status_root);
        rootView.setOnClickListener(this);

        refreshBannerTitle();
    }

    @Override
    public void onClick(View v) {
        // show information about who retains what
        DRUtils.DRMessageHelper messageHelper = new DRUtils.DRMessageHelper(getContext());
        StringBuilder sb = new StringBuilder();

        String[] partners = mPartners.toArray(new String[mPartners.size()]);
        messageHelper.getRetainingOrganizationHeader(sb, partners);
        messageHelper.getRetainingOrganizationDescription(sb, partners);
        messageHelper.getRetentionDescriptionAsList(sb, partners);
        sb.append("\n\n");

        String info = sb.toString();
        if (!TextUtils.isEmpty(info)) {
            InfoMsgDialogFragment.showDialog((Activity) getContext(), R.string.dialog_title_data_retention,
                    info, android.R.string.ok, -1);
        }

        /*
        String info = messageHelper.getLocalRetentionInformation();
        if (!TextUtils.isEmpty(info)) {
            sb.append(info).append("\n\n");
        }
        for (String partner : mPartners) {
            info = messageHelper.getRemoteRetentionInformation(partner);
            if (!TextUtils.isEmpty(info)) {
                sb.append(info).append("\n\n");
            }
        }
        info = sb.toString();
        if (!TextUtils.isEmpty(info)) {
            InfoMsgDialogFragment.showDialog((Activity) getContext(), R.string.dialog_title_data_retention,
                    info, android.R.string.ok, -1);
        }
         */
    }

    public void addConversationPartner(final String partner) {
        if (!TextUtils.isEmpty(partner)) {
            mPartners.add(partner);
        }
    }

    public void removeConversationPartner(final String partner) {
        if (!TextUtils.isEmpty(partner)) {
            mPartners.remove(partner);
        }
    }

    public void clearConversationPartners() {
        mPartners.clear();
    }

    public void refreshBannerTitle() {
        String titleText = mDataRetentionOn;
        titleText = DRUtils.isDrBlockedAndEnabled()
                ? mCommunicationBlocked
                : DRUtils.isLocalMessagingDrBlockedAndEnabled()
                        ? mMessagingBlocked
                        : DRUtils.isLocalCallDrBlockedAndEnabled() ? mCallsBlocked : titleText;
        mBannerText.setText(titleText);
    }

}
