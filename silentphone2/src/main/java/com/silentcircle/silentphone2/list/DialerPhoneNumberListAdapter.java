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
package com.silentcircle.silentphone2.list;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.list.PhoneNumberListAdapter;
import com.silentcircle.contacts.list.ScDirectoryLoader;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import java.util.List;

/**
 * {@link PhoneNumberListAdapter} with the following added shortcuts, that are displayed as list
 * items:
 * 1) Directly calling the phone number query
 * 2) Adding the phone number query to a contact
 *
 * These shortcuts can be enabled or disabled to toggle whether or not they show up in the
 * list. The shortcuts are placed at the top of the list items before the super's entries.
 */
public class DialerPhoneNumberListAdapter extends PhoneNumberListAdapter {

    @SuppressWarnings("unused")
    private static final String TAG = "DialerPhoneNumberListAdapter";
    private String mFormattedQueryString;
    private String mCountryIso;

    public final static int SHORTCUT_INVALID = -1;
    public final static int SHORTCUT_DIRECT_CALL = 0;
    public final static int SHORTCUT_DIRECT_CONVERSATION = 1;
    public final static int SHORTCUT_ADD_NUMBER_TO_CONTACTS = 2;
//    public final static int SHORTCUT_MAKE_VIDEO_CALL = 2;
    public final static int SHORTCUT_START_GROUP_CHAT = 3;
    public final static int SHORTCUT_ADD_TO_GROUP_CHAT = 4;

    public final static int SHORTCUT_COUNT = 5;

    private final boolean[] mShortcutEnabledMatrix = new boolean[SHORTCUT_COUNT];
    private int mShortcutEnabledCount;

    public DialerPhoneNumberListAdapter(Context context, boolean enablePhoneDir) {
        super(context, true /* SC dir search */, enablePhoneDir);

        mCountryIso = ContactsUtils.getCurrentCountryIso(context);

        // Enable all shortcuts by default
        for (int i = 0; i < SHORTCUT_COUNT; i++) {
            setShortcutEnabled(i, true);
        }
    }

    @Override
    public int getCount() {
        return super.getCount() + getEnabledShortcutCount();
    }

    /**
     * Returns the position of an element with the proper offset so that it can be used by the super
     * class.
     */
    public int getSuperPosition(int position) {
        return position - getEnabledShortcutCount();
    }

    /**
     * @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT
     */
    public int getEnabledShortcutCount() {
        return mShortcutEnabledCount;
    }

    @Override
    public int getItemViewType(int position) {
        final int shortcut = getShortcutTypeFromPosition(position);
        if (shortcut >= 0) {
            // shortcutPos should always range from 1 to SHORTCUT_COUNT
            return super.getViewTypeCount() + shortcut;
        } else {
            return super.getItemViewType(getSuperPosition(position));
        }
    }

    @Override
    public int getViewTypeCount() {
        // Number of item view types in the super implementation + 2 for the 2 new shortcuts
        return super.getViewTypeCount() + SHORTCUT_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            if (convertView != null) {
                assignShortcutToView((ContactListItemView) convertView, shortcutType);
                return convertView;
            } else {
                final ContactListItemView v = new ContactListItemView(getContext(), null);
                assignShortcutToView(v, shortcutType);
                return v;
            }
        } else {
            return super.getView(getSuperPosition(position), convertView, parent);
        }
    }

    /**
     * @param position The position of the item
     * @return The enabled shortcut type matching the given position if the item is a
     * shortcut, -1 otherwise
     */
    public int getShortcutTypeFromPosition(int position) {
        if(getEnabledShortcutCount() > 0 && position < SHORTCUT_COUNT) {
            // Iterate through the array of shortcuts, looking only for shortcuts where
            // mShortcutEnabledMatrix[i] is true
            int positionIterator = 0;
            for (int i = 0; i < SHORTCUT_COUNT; i++) {
                if (mShortcutEnabledMatrix[i]) {
                    if (positionIterator == position) {
                        return i;
                    }
                    positionIterator++;
                }
            }
        }
        return SHORTCUT_INVALID;
    }

    @Override
    public boolean isEmpty() {
        return getEnabledShortcutCount() == 0 && super.isEmpty();
    }

    @Override
    public boolean isEnabled(int position) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        return shortcutType >= 0 || super.isEnabled(getSuperPosition(position));
    }

    private void assignShortcutToView(ContactListItemView v, int shortcutType) {
        final CharSequence text;
        final int drawableId;
        int bgDrawable = 0;
        final Resources resources = getContext().getResources();
//        final String number = getFormattedQueryString();
        switch (shortcutType) {
            case SHORTCUT_DIRECT_CALL:
                text = resources.getString(R.string.search_shortcut_call_number, getQueryString());
                drawableId = R.drawable.ic_search_phone;
                break;
            case SHORTCUT_ADD_NUMBER_TO_CONTACTS:
                text = resources.getString(R.string.search_shortcut_add_to_contacts);
                drawableId = R.drawable.ic_search_add_contact;
                break;
//            case SHORTCUT_MAKE_VIDEO_CALL:
//                text = resources.getString(R.string.search_shortcut_make_video_call);
//                drawableId = R.drawable.ic_videocam;
//                break;
            case SHORTCUT_DIRECT_CONVERSATION:
                text = resources.getString(R.string.search_shortcut_write_to, getQueryString());
                drawableId = R.drawable.ic_chat_holo_dark;
                break;
            case SHORTCUT_START_GROUP_CHAT:
                text = resources.getString(R.string.search_shortcut_group_chat);
                drawableId = R.drawable.ic_group_red_24px;
                break;
            case SHORTCUT_ADD_TO_GROUP_CHAT:
                text = resources.getString(R.string.search_shortcut_add_to_group_chat, getQueryString());
                drawableId = R.drawable.ic_group_add_white_24px;
                break;
            default:
                throw new IllegalArgumentException("Invalid shortcut type");
        }
        v.setDrawableResource(R.drawable.search_shortcut_background_light, drawableId,
                ContextCompat.getColor(getContext(), R.color.sc_ng_text_red_dark_actionbar));
        v.setDisplayName(text);
        v.setPhotoPosition(super.getPhotoPosition());
        v.setAdjustSelectionBoundsEnabled(false);
        v.setBackgroundResource(R.drawable.bg_action);
    }

    /**
     * @return True if the shortcut state (disabled vs enabled) was changed by this operation
     */
    public boolean setShortcutEnabled(int shortcutType, boolean visible) {
        final boolean changed = mShortcutEnabledMatrix[shortcutType] != visible;
        mShortcutEnabledMatrix[shortcutType] = visible;
        if (changed) {
            mShortcutEnabledCount += (visible) ? 1 : -1;
        }
        return changed;
    }

    public String getFormattedQueryString() {
        return mFormattedQueryString;
    }

    @Override
    public void setQueryString(String queryString) {
        if (queryString != null) {
            queryString = mFormattedQueryString = Utilities.isRtl() ? StringUtils.rtrim(queryString) :
                    StringUtils.ltrim(queryString);

            if (!TextUtils.isEmpty(queryString) && Utilities.isValidPhoneNumber(queryString)) {
                String e164Formatted = PhoneNumberHelper.formatNumberToE164(queryString, "ZZ");

                if (!TextUtils.isEmpty(e164Formatted)) {
                    queryString = mFormattedQueryString = e164Formatted;
                }
            }
        }

        CharSequence formatted = Utilities.formatNumberAssisted(queryString);
        if (formatted != null) {
            queryString = formatted.toString();
        }

        mFormattedQueryString = PhoneNumberHelper.formatNumber(PhoneNumberHelper.normalizeNumber(queryString), mCountryIso);
        super.setQueryString(queryString);
    }

}
