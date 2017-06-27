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
package com.silentcircle.messaging.task;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.list.ScDirectoryLoader;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Loader to show existing conversations/groups in search results.
 */
public class ScConversationLoader extends AsyncTaskLoader<Cursor> {

    private static final String TAG = ScConversationLoader.class.getSimpleName();

    public static final int MIN_SEARCH_LENGTH = 0;

    public static final int MAX_RECORDS = Integer.MAX_VALUE;

    public static final int NONE = 0;
    public static final int ALL = 1;
    public static final int CONVERSATIONS_ONLY = 2;
    public static final int GROUP_CONVERSATIONS_ONLY = 3;

    public static final String GROUP = "group";

    /* *** IMPORTANT: Keep in-sync with PhoneNumberListAdapter projections, see also addRow() below *** */
    private static final String[] PROJECTION = new String[]{
            ContactsContract.CommonDataKinds.Phone._ID,                          // 0
            ContactsContract.CommonDataKinds.Phone.TYPE,                         // 1
            ContactsContract.CommonDataKinds.Phone.LABEL,                        // 2
            ContactsContract.CommonDataKinds.Phone.NUMBER,                       // 3
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,                   // 4
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,                   // 5
            ContactsContract.CommonDataKinds.Phone.PHOTO_ID,                     // 6
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,         // 7
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,          // 8
            ScDirectoryLoader.SC_PRIVATE_FIELD,                                  // 9
            // must be at the end, otherwise the adapters/fragments get confused
            ScDirectoryLoader.SC_UUID_FIELD,                                    // 10
    };

    private int mFilterType = NONE;

    private String mSearchText = "";

    private static final List<Conversation> EMPTY_LIST = new ArrayList<>();

    private static final MatrixCursor EMPTY_CURSOR = new MatrixCursor(PROJECTION, 0);

    public ScConversationLoader(Context context) {
        super(context);
    }

    public void setQueryString(String query) {
        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "setQueryString query: " + query);
        }
        mSearchText = TextUtils.isEmpty(query) ? "" : query.toLowerCase(Locale.getDefault());
    }

    public void setFilterType(final int filterType) {
        mFilterType = filterType;
    }

    @Override
    protected void onStartLoading() {
        Log.d(TAG, "onStartLoading query: " + mSearchText + ", filter type: " + mFilterType);
        if (mFilterType == NONE) {
            deliverResult(EMPTY_CURSOR);
        }
        else {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
    }

    @Override
    public void onCanceled(Cursor data) {
        if (data != null) {
            data.close();
        }
    }

    @Override
    public Cursor loadInBackground() {
        Log.d(TAG, "loadInBackground query: " + mSearchText);

        List<Conversation> conversations;
        try {
            conversations = getConversations(mSearchText, mFilterType);
        }
        catch (Throwable t) {
            return EMPTY_CURSOR;
        }
        return createCursor(conversations);
    }

    @Override
    protected void onReset() {
        stopLoading();
    }

    private Cursor createCursor(@Nullable final List<Conversation> conversations) {
        int size;
        if (conversations == null || (size = conversations.size()) <= 0) {
            return EMPTY_CURSOR;
        }

        MatrixCursor cursor = new MatrixCursor(PROJECTION, size);

        int _id = 3;
        for (Conversation conversation : conversations) {
            addRow(cursor, conversation, _id);
            _id++;
        }
        return cursor;
    }

    private void addRow(MatrixCursor cursor, Conversation conversation, long _id) {

/*
                Phone._ID,                          // 0
                Phone.TYPE,                         // 1
                Phone.LABEL,                        // 2
                Phone.NUMBER,                       // 3
                Phone.CONTACT_ID,                   // 4
                Phone.LOOKUP_KEY,                   // 5
                Phone.PHOTO_ID,                     // 6
                Phone.DISPLAY_NAME_PRIMARY,         // 7
                Phone.PHOTO_THUMBNAIL_URI,          // 8
                SC_PRIVATE_FIELD,                   // 9
                SC_UUID_FIELD,                      // 10
*/

        MatrixCursor.RowBuilder row;

        String uuid = conversation.getPartner().getUserId();
        String alias = conversation.getPartner().getAlias();
        String displayName = conversation.getPartner().getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = conversation.getPartner().getAlias();
        }
        if (TextUtils.isEmpty(displayName)) {
            displayName = uuid;
        }
        if (TextUtils.isEmpty(alias) && !conversation.getPartner().isGroup()) {
            alias = uuid;
        }
        Uri photoUri = conversation.getPartner().isGroup()
                ? AvatarUtils.getAvatarProviderUriGroup(null, uuid)
                : AvatarUtils.getAvatarProviderUri(conversation.getAvatarUrl(), uuid);
        // hack to override circular avatar for generated group images
        if (conversation.getPartner().isGroup() && photoUri != null) {
            String avatarUrl = conversation.getAvatarUrl();
            final boolean isGeneratedAvatar = TextUtils.isEmpty(avatarUrl)
                    || AvatarProvider.AVATAR_TYPE_GENERATED.equals(avatarUrl);
            if (isGeneratedAvatar) {
                photoUri = photoUri.buildUpon()
                        .appendQueryParameter(ContactPhotoManagerNew.FORCE_SQUARE_AVATAR_KEY, "true")
                        .build();
            }
        }

        /*
        Log.d(TAG, "addRow " + _id + ", alias: " + alias + ", displayName: " + displayName
            + ", photoUri: " + photoUri + ", uuid: " + uuid);
         */

        row = cursor.newRow();
        row.add(_id);                               // _ID
        // TYPE, must be an in-circle number
        row.add(ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM);
        row.add(getContext().getString(R.string.phoneTypeSilent));
                                                    // NUMBER
        row.add(conversation.getPartner().isGroup() ? null : alias);
        row.add(_id);                               // CONTACT_ID
        row.add(null);                              // LOOKUP-KEY
        row.add(null);                              // PHOTO_ID
        row.add(displayName);
        row.add(photoUri);                          // Phone.PHOTO_THUMBNAIL_URI
        // Use private field to indicate that record describes a group conversation
        row.add(                                    // SC_PRIVATE_FIELD
                conversation.getPartner().isGroup() ? GROUP : null);
        row.add(uuid);                              // SC_UUID_FIELD
    }

    @NonNull
    private List<Conversation> getConversations(final String query, final int filter) {
        ZinaMessaging zina = ZinaMessaging.getInstance();
        boolean isRegistered = zina.isRegistered();

        if (!isRegistered) {
            // return empty list if Zina has not been registered yet
            return EMPTY_LIST;
        }

        ConversationRepository repository = zina.getConversations();
        List<Conversation> conversations = new ArrayList<>(repository.listCached());
        Iterator<Conversation> iterator = conversations.iterator();
        while (iterator.hasNext()) {
            Conversation conversation = iterator.next();

            if (conversation == null || ((conversation.getLastModified() == 0
                    || conversation.getPartner().getUserId() == null))
                    || (filter == CONVERSATIONS_ONLY && conversation.getPartner().isGroup())
                    || (filter == GROUP_CONVERSATIONS_ONLY && !conversation.getPartner().isGroup())) {
                iterator.remove();
            }
            else {
                String displayName = conversation.getPartner().getDisplayName();
                if (!TextUtils.isEmpty(displayName)) {
                    displayName = displayName.toLowerCase();
                }
                if (TextUtils.equals(displayName, query)) {
                    continue;
                }
                if (TextUtils.isEmpty(displayName)
                        || (!TextUtils.isEmpty(query) && !displayName.startsWith(query))) {
                    iterator.remove();
                }
            }
        }

        Collections.sort(conversations, Conversation.CONVERSATION_NAME_COMPARATOR);
        return (conversations.size() > MAX_RECORDS)
                ? conversations.subList(0, MAX_RECORDS)
                : conversations;
    }

}
