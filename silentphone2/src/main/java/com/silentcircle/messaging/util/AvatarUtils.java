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
package com.silentcircle.messaging.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.QuickContactBadge;

import com.google.gson.Gson;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Utilities for avatars.
 */
public class AvatarUtils {

    private AvatarUtils() {
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            QuickContactBadge quickContactView, ContactEntry contactEntry) {
        setPhoto(contactPhotoManager, quickContactView, contactEntry, true /* isCircular */);
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            QuickContactBadge quickContactView, ContactEntry contactEntry, boolean isCircular) {

        int contactType = ContactPhotoManagerNew.TYPE_DEFAULT;

        long photoId = 0;
        Uri photoUri = null;
        Uri contactUri = null;
        String displayName = null;
        String lookupKey = null;
        if (contactEntry != null) {
            photoId = contactEntry.photoId;
            photoUri = contactEntry.photoUri;
            contactUri = contactEntry.lookupUri;
            lookupKey = contactEntry.lookupKey;
            displayName = contactEntry.name;
        }

        setPhoto(contactPhotoManager, quickContactView, photoId, photoUri, contactUri, displayName,
                lookupKey, contactType, isCircular);
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            QuickContactBadge quickContactView, long photoId, Uri photoUri, Uri contactUri,
            String displayName, String identifier, int contactType, boolean circular) {

        quickContactView.assignContactUri(contactUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            quickContactView.setOverlay(null);
        }
        setPhoto(contactPhotoManager, quickContactView, photoId, photoUri, displayName, identifier, contactType, circular);
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            ImageView quickContactView, ContactEntry contactEntry, boolean isCircular) {

        int contactType = ContactPhotoManagerNew.TYPE_DEFAULT;

        long photoId = 0;
        Uri photoUri = null;
        String displayName = null;
        String lookupKey = null;
        if (contactEntry != null) {
            photoId = contactEntry.photoId;
            photoUri = contactEntry.photoUri;
            lookupKey = contactEntry.lookupKey;
            displayName = contactEntry.name;
        }

        setPhoto(contactPhotoManager, quickContactView, photoId, photoUri, displayName,
                lookupKey, contactType, isCircular);
    }

    private static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            ImageView quickContactView, long photoId, Uri photoUri, String displayName,
            String identifier, int contactType, boolean circular) {
        ContactPhotoManagerNew.DefaultImageRequest request =
                new ContactPhotoManagerNew.DefaultImageRequest(displayName, identifier, contactType,
                        true /* isCircular */);
        if (photoId == 0 && photoUri != null) {
            contactPhotoManager.loadDirectoryPhoto(quickContactView, photoUri,
                    false /* darkTheme */, circular /* isCircular */, request);
        }
        else {
            contactPhotoManager.loadThumbnail(quickContactView, photoId, false /* darkTheme */,
                    circular /* isCircular */, request);
        }
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            ImageView quickContactView, Uri photoUri, int contactType) {
        setPhoto(contactPhotoManager, quickContactView, photoUri, contactType, true /* isCircular */);
    }

    public static void setPhoto(ContactPhotoManagerNew contactPhotoManager,
            ImageView quickContactView, Uri photoUri, int contactType, boolean circular) {

        ContactPhotoManagerNew.DefaultImageRequest request =
                new ContactPhotoManagerNew.DefaultImageRequest(null, null, contactType,
                        circular);
        if (photoUri != null) {
            contactPhotoManager.loadDirectoryPhoto(quickContactView, photoUri,
                    circular /* darkTheme */, circular, request);
        }
        else {
            contactPhotoManager.loadThumbnail(quickContactView, 0, false /* darkTheme */,
                    circular /* isCircular */, request);
        }
    }

    public static Uri getAvatarProviderUri(String name, String avatarUrl) {
        return getAvatarProviderUri(name, avatarUrl, AvatarProvider.UNKNOWN_AVATAR_SIZE, 0);
    }

    public static Uri getAvatarProviderUri(String name, String avatarUrl, int size, int defaultAvatarId) {
        if (!TextUtils.isEmpty(name) && PhoneNumberUtils.isGlobalPhoneNumber(name.replaceAll("\\s",""))) {
            /* use resource as default avatar for phone numbers, photo manager will cache it */
            return Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/"
                    + R.drawable.ic_profile_device_red_120dp);
        }
        /* add a default icon for phone numbers */
        try {
            Uri.Builder builder = AvatarProvider.CONTENT_URI
                    .buildUpon()
                    .appendPath(name);
            if (!TextUtils.isEmpty(avatarUrl)) {
                    builder.appendQueryParameter(AvatarProvider.PARAM_AVATAR_URL,
                        URLEncoder.encode(avatarUrl, "UTF-8"));
            }
            if (size != AvatarProvider.UNKNOWN_AVATAR_SIZE) {
                builder.appendQueryParameter(AvatarProvider.PARAM_AVATAR_SIZE, String.valueOf(size));
            }
            if (defaultAvatarId > 0) {
                builder.appendQueryParameter(AvatarProvider.PARAM_AVATAR_ID,
                        String.valueOf(R.drawable.ic_avatar_placeholder_circular));
            }
            return builder.build();
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static Uri getAvatarProviderUriGroup(String name) {
        return AvatarProvider.CONTENT_URI
                .buildUpon()
                .appendPath(name)
                .appendQueryParameter(AvatarProvider.PARAM_AVATAR_ID,
                        String.valueOf(R.drawable.ic_profile_group))
                .build();
    }

    public static Uri getAvatarProviderUriGroup(String name, int placeholder, int size) {
        return AvatarProvider.CONTENT_URI
                .buildUpon()
                .appendPath(name)
                .appendQueryParameter(AvatarProvider.PARAM_AVATAR_ID,
                        String.valueOf(placeholder))
                .appendQueryParameter(AvatarProvider.PARAM_AVATAR_SIZE,
                        String.valueOf(size))
                .build();
    }

    public static Uri getAvatarProviderUriForIcon(int resourceId) {
        return AvatarProvider.CONTENT_URI
                .buildUpon()
                .appendPath("")
                .appendQueryParameter(AvatarProvider.PARAM_AVATAR_ID,
                        String.valueOf(resourceId))
                .build();
    }

    public static void setGeneratedGroupAvatar(@Nullable Context context,
            @Nullable String conversationId) {
        if (context == null || TextUtils.isEmpty(conversationId)) {
            return;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        Conversation conversation = repository.findByPartner(conversationId);
        if (conversation != null) {
            Bitmap bitmap = generateGroupAvatar(context, conversationId);
            ConversationUtils.setConversationAvatar(context, repository, conversation,
                    AvatarProvider.AVATAR_TYPE_GENERATED, bitmap);
        }
    }

    @Nullable
    public static byte[] getConversationAvatarAsByteArray(Context context, @NonNull Uri photoUri) {
        byte[] avatar = null;
        try {
            InputStream is = context.getContentResolver().openInputStream(photoUri);
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    int size;
                    byte[] buffer = new byte[16 * 1024];
                    while ((size = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, size);
                    }

                    avatar = baos.toByteArray();
                } finally {
                    IOUtils.close(is);
                }
            }
        } catch (final Exception | OutOfMemoryError ex) {
            // could not read avatar, caller should use letter drawable
        }
        return avatar;
    }

    @Nullable
    public static Bitmap getConversationAvatar(@Nullable Context context, @NonNull Uri photoUri) {
        if (context == null) {
            return null;
        }
        Bitmap avatar = null;
        try {
            InputStream is = context.getContentResolver().openInputStream(photoUri);
            if (is != null) {
                try {
                    avatar = BitmapFactory.decodeStream(is);
                } finally {
                    IOUtils.close(is);
                }
            }
        } catch (final Exception | OutOfMemoryError ex) {
            // could not read avatar, caller should use letter drawable
        }
        return avatar;
    }

    private static int QUAD_SIZE = AvatarProvider.MAX_AVATAR_SIZE;

    private static final RectF[] TWO_POSITIONS = new RectF[] {
            new RectF(0.0f, QUAD_SIZE / 4, QUAD_SIZE / 2,
                    QUAD_SIZE - QUAD_SIZE / 4),
            new RectF(QUAD_SIZE / 2, QUAD_SIZE / 4,
                    QUAD_SIZE, QUAD_SIZE - QUAD_SIZE / 4)};

    private static final float MARGIN = QUAD_SIZE / 29;
    private static final RectF[] THREE_POSITIONS = new RectF[] {
            new RectF(0.0f, 0.0f + MARGIN, QUAD_SIZE / 2, QUAD_SIZE / 2  + MARGIN),
            new RectF(QUAD_SIZE / 2, 0.0f + MARGIN, QUAD_SIZE,
                    QUAD_SIZE / 2 + MARGIN),
            new RectF(QUAD_SIZE / 4, QUAD_SIZE / 2 - MARGIN,
                    QUAD_SIZE - QUAD_SIZE / 4,
                    QUAD_SIZE - MARGIN)};

    private static final RectF[] FOUR_POSITIONS = new RectF[] {
            new RectF(0.0f, 0.0f, QUAD_SIZE / 2, QUAD_SIZE / 2),
            new RectF(QUAD_SIZE / 2, 0.0f, QUAD_SIZE,
                    QUAD_SIZE / 2),
            new RectF(0.0f, QUAD_SIZE / 2, QUAD_SIZE / 2,
                    QUAD_SIZE),
            new RectF(QUAD_SIZE / 2, QUAD_SIZE / 2,
                    QUAD_SIZE, QUAD_SIZE)};

    /*
     * This is a very network and other resources intensive operation.
     */
    @Nullable
    @WorkerThread
    public static Bitmap generateGroupAvatar(final Context context, final String groupId) {
        if (context == null || TextUtils.isEmpty(groupId)) {
            return null;
        }

        ZinaMessaging zina = ZinaMessaging.getInstance();
        if (!zina.isReady()) {
            return null;
        }

        ConversationUtils.GroupData groupData = ConversationUtils.getGroup(groupId);
        int participantCount = 0;
        if (groupData != null) {
            participantCount = groupData.getMemberCount();
        }

        if (participantCount <= 1) {
            return null;
        }

        Resources resources = context.getResources();
        Bitmap bitmap = Bitmap.createBitmap(AvatarProvider.MAX_AVATAR_SIZE,
                AvatarProvider.MAX_AVATAR_SIZE, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        final Paint tilePaint = new Paint();
        tilePaint.setTypeface(Typeface.create(
                resources.getString(R.string.letter_tile_letter_font_family), Typeface.NORMAL));
        tilePaint.setTextAlign(Paint.Align.CENTER);
        tilePaint.setAntiAlias(true);

        RectF[] positions = participantCount == 2
                ? TWO_POSITIONS
                : (participantCount == 3 ? THREE_POSITIONS : FOUR_POSITIONS);
        byte[] buffer = new byte[16 * 1024];

        final int[] code = new int[1];
        final Rect textBounds = new Rect();
        final int textColor = ContextCompat.getColor(context, R.color.sc_ng_text_grey);
        final int defaultBackgroundColor = ContextCompat.getColor(context, R.color.sc_ng_background_1);
        final TypedArray backgroundColors = resources.obtainTypedArray(R.array.letter_tile_colors);
        byte[][] groupMembers = ZinaMessaging.getAllGroupMembers(groupId, code);
        if (groupMembers != null) {
            int position = 0;
            Gson gson = new Gson();
            for (byte[] member : groupMembers) {
                ConversationUtils.MemberData memberData = gson.fromJson(new String(member),
                        ConversationUtils.MemberData.class);
                if (position == 3 && participantCount > 4) {
                    // special case where a number indicating participant should should be drawn
                    RectF bounds = positions[position++];
                    String text = String.format(Locale.getDefault(), "+%d", participantCount - 3);
                    int background = getBackgroundColor(text, backgroundColors, defaultBackgroundColor);
                    drawTile(canvas, tilePaint, background, textColor, bounds, textBounds,
                            text.toCharArray());
                }
                else {
                    ContactEntry contactEntry = ContactsCache.getContactEntry(memberData.getMemberId());
                    Bitmap avatar = null;
                    if (contactEntry != null) {
                        Uri uri = contactEntry.photoUri;
                        if (uri != null) {
                            uri = uri.buildUpon()
                                    .appendQueryParameter(AvatarProvider.PARAM_AVATAR_SIZE,
                                            String.valueOf(QUAD_SIZE))
                                    .build();
                            avatar = getConversationAvatar(context, buffer, uri);
                        }
                    }
                    avatar = ViewUtil.getCircularBitmap(avatar);
                    if (avatar != null) {
                        canvas.drawBitmap(avatar, null, positions[position++], null);
                    }
                    else {
                        String displayName = memberData.getGroupId();
                        byte[] dpName = ZinaMessaging.getDisplayName(memberData.getMemberId());
                        if (dpName != null) {
                            displayName = new String(dpName);
                        }

                        RectF bounds = positions[position++];
                        int background = getBackgroundColor(displayName, backgroundColors,
                                defaultBackgroundColor);
                        char[] firstLetter = new char[] {Character.toUpperCase(displayName.charAt(0))};
                        drawTile(canvas, tilePaint, background, textColor, bounds, textBounds,
                                firstLetter);
                    }
                }
                if (position == positions.length) {
                    break;
                }
            }
        }
        backgroundColors.recycle();

        return bitmap;
    }

    @Nullable
    private static Bitmap getConversationAvatar(Context context, byte[] buffer, @NonNull Uri photoUri) {
        Bitmap avatar = null;
        try {
            InputStream is = context.getContentResolver().openInputStream(photoUri);
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    int size;
                    while ((size = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, size);
                    }

                    byte[] bitmapBytes = baos.toByteArray();
                    avatar = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);

                } finally {
                    IOUtils.close(is);
                }
            }
        } catch (final Exception | OutOfMemoryError ex) {
            // will fall back to letter drawable
        }
        return avatar;
    }

    private static int getBackgroundColor(@NonNull String text, @NonNull TypedArray backgroundColors,
            int defaultBackgroundColor) {
        int colorIndex = Math.abs(text.hashCode()) % backgroundColors.length();
        return backgroundColors.getColor(colorIndex, defaultBackgroundColor);
    }

    private static void drawTile(Canvas canvas, Paint sPaint, int backgroundColor, int textColor,
            RectF bounds, Rect textBounds, char[] text) {
        final int minDimension = (int) Math.min(bounds.width(), bounds.height());

        // draw background circle
        sPaint.setColor(backgroundColor);
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), minDimension / 2, sPaint);

        // draw text
        sPaint.setColor(textColor);
        sPaint.setTextSize(0.6f * minDimension);
        sPaint.getTextBounds(text, 0, text.length, textBounds);
        canvas.drawText(text, 0, text.length, bounds.centerX(),
                bounds.centerY() + textBounds.height() / 2,
                sPaint);
    }

}
