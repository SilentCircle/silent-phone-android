/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.contacts.quickcontact;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.model.account.LabelHelper;
import com.silentcircle.contacts.model.dataitem.DataItem;
import com.silentcircle.contacts.model.dataitem.DataKind;
import com.silentcircle.contacts.model.dataitem.EmailDataItem;
import com.silentcircle.contacts.model.dataitem.ImDataItem;
import com.silentcircle.contacts.model.dataitem.PhoneDataItem;
import com.silentcircle.contacts.model.dataitem.SipAddressDataItem;
import com.silentcircle.contacts.model.dataitem.StructuredPostalDataItem;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.contacts.utils.PhoneCapabilityTester;
import com.silentcircle.contacts.utils.StructuredPostalUtils;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.Data;

/**
 * Description of a specific {@link com.silentcircle.silentcontacts.ScContactsContract.Data#_ID} item, with style information
 * defined by a {@link com.silentcircle.contacts.model.dataitem.DataKind}.
 */
public class DataAction implements Action {
    private static final String TAG = "DataAction";

    private final Context mContext;
    private final DataKind mKind;
    private final String mMimeType;

    private CharSequence mBody;
    private CharSequence mSubtitle;
    private Intent mIntent;
    private Intent mAlternateIntent;
    private int mAlternateIconDescriptionRes;
    private int mAlternateIconRes;
    private int mPresence = -1;

    private Uri mDataUri;
    private long mDataId;
    private boolean mIsPrimary;

    /**
     * Create an action from common {@link com.silentcircle.silentcontacts.ScContactsContract.Data} elements.
     */
    @SuppressLint("DefaultLocale")
    public DataAction(Context context, DataItem item) {
        mContext = context;
        mKind = item.getDataKind();
        mMimeType = item.getMimeType();

        // Determine type for subtitle
        mSubtitle = "";
        if (item.hasKindTypeColumn()) {
            final int typeValue = item.getKindTypeColumn();

            // get type string
            for (AccountType.EditType type : item.getDataKind().typeList) {
                if (type.rawValue == typeValue) {
                    if (type.customColumn == null) {
                        // Non-custom type. Get its description from the resource
                        mSubtitle = context.getString(type.labelRes);
                    } else {
                        // Custom type. Read it from the database
                        mSubtitle = item.getContentValues().getAsString(type.customColumn);
                    }
                    break;
                }
            }
        }

        mIsPrimary = item.isSuperPrimary();
        mBody = item.buildDataStringForDisplay();

        mDataId = item.getId();
        mDataUri = ContentUris.withAppendedId(Data.CONTENT_URI, mDataId);

        final boolean hasPhone = true;  // PhoneCapabilityTester.isPhone(mContext);
        final boolean hasSms = false;   // PhoneCapabilityTester.isSmsIntentRegistered(mContext);

        // Handle well-known MIME-types with special care
        if (item instanceof PhoneDataItem) {
            if (hasPhone) {
                PhoneDataItem phone = (PhoneDataItem) item;
                final String number = phone.getNumber();
                if (!TextUtils.isEmpty(number)) {

                    final Intent phoneIntent = hasPhone ? ContactsUtils.getCallIntent(number) : null;
                    final Intent smsIntent = hasSms ? new Intent(Intent.ACTION_SENDTO, Uri.fromParts(Constants.SCHEME_SMSTO,
                            number, null)) : null;

                    // Configure Icons and Intents. Notice actionIcon is already set to the phone
                    if (hasPhone && hasSms) {
                        mIntent = phoneIntent;
                        mAlternateIntent = smsIntent;
                        mAlternateIconRes = phone.getDataKind().iconAltRes;
                        mAlternateIconDescriptionRes = phone.getDataKind().iconAltDescriptionRes;
                    }
                    else if (hasPhone) {
                        mIntent = phoneIntent;
                    }
                    else if (hasSms) {
                        mIntent = smsIntent;
                    }
                }
            }
        }
        else if (item instanceof SipAddressDataItem) {
            if (PhoneCapabilityTester.isSipPhone(mContext)) {
                final SipAddressDataItem sip = (SipAddressDataItem) item;
                final String address = sip.getSipAddress();
                if (!TextUtils.isEmpty(address)) {
                    final Uri callUri = Uri.fromParts(Constants.SCHEME_SIP, address, null);
                    mIntent = ContactsUtils.getCallIntent(callUri);
                    // Note that this item will get a SIP-specific variant
                    // of the "call phone" icon, rather than the standard
                    // app icon for the Phone app (which we show for
                    // regular phone numbers.)  That's because the phone
                    // app explicitly specifies an android:icon attribute
                    // for the SIP-related intent-filters in its manifest.
                }
            }
        }
        else if (item instanceof EmailDataItem) {
            final EmailDataItem email = (EmailDataItem) item;
            final String address = email.getData();
            if (!TextUtils.isEmpty(address)) {
                final Uri mailUri = Uri.fromParts(Constants.SCHEME_MAILTO, address, null);
                mIntent = new Intent(Intent.ACTION_SENDTO, mailUri);
            }

        }
//        else if (item instanceof WebsiteDataItem) {
//            final WebsiteDataItem website = (WebsiteDataItem) item;
//            final String url = website.getUrl();
//            if (!TextUtils.isEmpty(url)) {
//                WebAddress webAddress = new WebAddress(url);
//                mIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webAddress.toString()));
//            }
//
//        } 
        else if (item instanceof ImDataItem) {
            ImDataItem im = (ImDataItem) item;
            final boolean isEmail = im.isCreatedFromEmail();
            if (isEmail || im.isProtocolValid()) {
                final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK : im.getProtocol();

                if (isEmail) {
                    // Use Google Talk string when using Email, and clear data
                    // Uri so we don't try saving Email as primary.
                    mSubtitle = LabelHelper.ImLabel.getProtocolLabel(context.getResources(), Im.PROTOCOL_GOOGLE_TALK, null);
                    mDataUri = null;
                }

                String host = im.getCustomProtocol();
                String data = im.getData();
                if (protocol != Im.PROTOCOL_CUSTOM) {
                    // Try bringing in a well-known host for specific protocols
                    host = ContactsUtils.lookupProviderNameFromId(protocol);
                }

                if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(data)) {
                    final String authority = host.toLowerCase();
                    final Uri imUri = new Uri.Builder().scheme(Constants.SCHEME_IMTO).authority(authority).appendPath(data).build();
                    mIntent = new Intent(Intent.ACTION_SENDTO, imUri);

                    // If the address is also available for a video chat, we'll show the capability
                    // as a secondary action.
//                    final int chatCapability = im.getChatCapability();
//                    final boolean isVideoChatCapable = (chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0;
//                    final boolean isAudioChatCapable = (chatCapability & Im.CAPABILITY_HAS_VOICE) != 0;
//                    if (isVideoChatCapable || isAudioChatCapable) {
//                        Log.i(TAG, "Check chat capabilities and setup Intent");
//                        mAlternateIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
//                        if (isVideoChatCapable) {
//                            mAlternateIconRes = R.drawable.sym_action_videochat_holo_light;
//                            mAlternateIconDescriptionRes = R.string.video_chat;
//                        }
//                        else {
//                            mAlternateIconRes = R.drawable.sym_action_audiochat_holo_light;
//                            mAlternateIconDescriptionRes = R.string.audio_chat;
//                        }
//                    }
                }
            }
        }
        else if (item instanceof StructuredPostalDataItem) {
            StructuredPostalDataItem postal = (StructuredPostalDataItem) item;
            final String postalAddress = postal.getFormattedAddress();
            if (!TextUtils.isEmpty(postalAddress)) {
                mIntent = StructuredPostalUtils.getViewPostalAddressIntent(postalAddress);
            }
        }

        if (mIntent == null) {
            // Otherwise fall back to default VIEW action
            mIntent = new Intent(Intent.ACTION_VIEW);
            mIntent.setDataAndType(mDataUri, item.getMimeType());
        }

        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
    }

    @Override
    public int getPresence() {
        return mPresence;
    }

    public void setPresence(int presence) {
        mPresence = presence;
    }

    @Override
    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    @Override
    public CharSequence getBody() {
        return mBody;
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public Uri getDataUri() {
        return mDataUri;
    }

    @Override
    public long getDataId() {
        return mDataId;
    }

    @Override
    public Boolean isPrimary() {
        return mIsPrimary;
    }

    @Override
    public Drawable getAlternateIcon() {
        if (mAlternateIconRes == 0)
            return null;

        final String resourcePackageName = mKind.resourcePackageName;
        if (resourcePackageName == null) {
            return mContext.getResources().getDrawable(mAlternateIconRes);
        }

        final PackageManager pm = mContext.getPackageManager();
        return pm.getDrawable(resourcePackageName, mAlternateIconRes, null);
    }

    @Override
    public String getAlternateIconDescription() {
        if (mAlternateIconDescriptionRes == 0)
            return null;
        return mContext.getResources().getString(mAlternateIconDescriptionRes);
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public Intent getAlternateIntent() {
        return mAlternateIntent;
    }

    @Override
    public boolean collapseWith(Action other) {
        if (!shouldCollapseWith(other)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldCollapseWith(Action t) {
        if (t == null) {
            return false;
        }
        if (!(t instanceof DataAction)) {
            Log.e(TAG, "t must be DataAction");
            return false;
        }
        DataAction that = (DataAction)t;
        if (!ContactsUtils.shouldCollapse(mMimeType, mBody, that.mMimeType, that.mBody)) {
            return false;
        }
        if (!TextUtils.equals(mMimeType, that.mMimeType)
                || !ContactsUtils.areIntentActionEqual(mIntent, that.mIntent)) {
            return false;
        }
        return true;
    }
}
