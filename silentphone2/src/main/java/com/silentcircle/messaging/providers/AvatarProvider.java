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
package com.silentcircle.messaging.providers;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;

import com.silentcircle.common.util.StringUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.utils.BitmapUtil;
import com.silentcircle.contacts.widget.LetterTileDrawable;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.CryptoUtil;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Provider to download conversation partner avatars from SC web page.
 */
public class AvatarProvider extends ContentProvider {

    private static final String TAG = AvatarProvider.class.getSimpleName();
    private static final boolean DEBUG = false; // Don't submit with true

    public static final String MIME_TYPE = "image/png";
    public static final String AUTHORITY = BuildConfig.AUTHORITY_BASE + ".messaging.provider.avatar";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String PARAM_AVATAR_URL = "avatar_url";
    public static final String PARAM_AVATAR_SIZE = "size";
    public static final String PARAM_DEFAULT_AVATAR = "default_avatar";
    public static final String PARAM_AVATAR_ID = "avatar_id";

    public static final String DEFAULT_AVATAR_PHONE = "phone";

    public static final String PATH_AVATARS = "avatars";
    public static final String SMALL_AVATAR_SUFFIX = "_small";

    /* (group) avatar types */
    public static final String AVATAR_TYPE_DEFAULT = "placeholder";
    public static final String AVATAR_TYPE_GENERATED = "generated";
    public static final String AVATAR_TYPE_DOWNLOADED = "downloaded";

    public static int DEFAULT_AVATAR_SIZE = 50;
    public static int UNKNOWN_AVATAR_SIZE = 0;

    /* server will never give us images larger than 512x512 as well */
    public static int MAX_AVATAR_SIZE = 512;
    /* do not perform any resize operations on image if this size is provided */
    public static int LOADED_AVATAR_SIZE = -1;

    private static final String AVATAR_STORE_KEY = "avatar_store_key";

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null)
            return false;
        context.getContentResolver().notifyChange(CONTENT_URI, null);
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return MIME_TYPE;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull final Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (DEBUG) {
            Log.d(TAG, "openFile : " + uri + ", mode: " + mode);
        }

        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    fetchAvatar(uri, pipe[1]);
                }
            });
            thread.setPriority(Math.max(Thread.NORM_PRIORITY - 1, Thread.MIN_PRIORITY));
            thread.start();

            return pipe[0];
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to make pipe for avatar");
        }
    }

    private void fetchAvatar(@NonNull Uri uri, ParcelFileDescriptor pipe) {
        InputStream is = null;
        OutputStream os = null;
        try {
            Context context = getContext();
            String avatarUrl = getAvatarUrl(uri);
            String conversationId = getConversationId(uri);
            String defaultAvatar = getDefaultAvatar(uri);
            int size = getAvatarSize(uri);
            int avatarResourceId = getAvatarResourceId(uri);
            if (avatarResourceId == 0 && DEFAULT_AVATAR_PHONE.equals(defaultAvatar)) {
                avatarResourceId = R.drawable.ic_profile_device;
            }
            if (DEBUG) {
                Log.d(TAG, "Conversation id: " + conversationId
                        + ", avatar url: " + avatarUrl + ", size: " + size
                        + ", resource id: " + avatarResourceId);
            }

            Bitmap bitmap = null;

            Conversation conversation = ConversationUtils.getOrCreateConversation(conversationId);
            if (conversation != null) {
                boolean isGroup = conversation.getPartner().isGroup();
                String url = conversation.getAvatarUrl();
                /*
                 * For groups always use cached avatar.
                 *
                 * For regular conversations check that avatar url has not changed,
                 * trigger download of new avatar if it has
                 */
                if (isGroup || TextUtils.isEmpty(avatarUrl)
                        || (!TextUtils.isEmpty(avatarUrl) && avatarUrl.equals(url))) {
                    if (size == LOADED_AVATAR_SIZE || size <= getDefaultAvatarSize()) {
                        is = getConversationAvatarStream(conversation, size);
                    }
                    if (is == null) {
                        bitmap = getConversationAvatar(conversation, size);
                        // fall back to stream, bitmap may not be loaded due to OOM
                        if (bitmap == null) {
                            is = getConversationAvatarStream(conversation, LOADED_AVATAR_SIZE);
                        }
                    }
                }
            }

            if (is != null || bitmap != null) {
                // use previously downloaded avatar
                if (DEBUG) {
                    Log.d(TAG, "Use previously downloaded avatar");
                }
                if (size != LOADED_AVATAR_SIZE && size <= getDefaultAvatarSize()) {
                    ensureSmallConversationAvatar(context, conversation, bitmap);
                }
            } else if (!TextUtils.isEmpty(avatarUrl)) {
                if (conversation == null) {
                    conversation = ConversationUtils.getConversation(conversationId);
                }
                // download avatar from server
                bitmap = downloadAvatar(Uri.parse(
                        ConfigurationUtilities.getProvisioningBaseUrl(context) + avatarUrl));
                updateConversationAvatar(context, conversation, avatarUrl, bitmap);
            } else if (avatarResourceId != 0 && context != null) {
                // use a resource image
                bitmap = size == LOADED_AVATAR_SIZE
                        ? BitmapFactory.decodeResource(
                        context.getResources(), avatarResourceId)
                        : BitmapUtil.loadOptimalBitmapFromResources(
                        context.getResources(), avatarResourceId, size, size);
            }

            // try to get a default avatar if no bitmap at this point
            if (is == null && bitmap == null && context != null) {
                bitmap = getDefaultAvatar(context, size,
                        avatarResourceId,
                        conversation != null ? conversation.getPartner().getDisplayName() : null,
                        conversationId);
            }

            if (is != null) {
                if (DEBUG) {
                    Log.e(TAG, "Using stream for " + uri);
                }
                os = new ParcelFileDescriptor.AutoCloseOutputStream(pipe);
                IOUtils.pipe(is, os, new byte [16 * 1024]);
                IOUtils.flush(os);
            } else if (bitmap != null) {

                if (DEBUG) {
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    Log.d(TAG, "Loaded bitmap wh: " + width + ", " + height);
                }
                os = new ParcelFileDescriptor.AutoCloseOutputStream(pipe);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                bitmap.recycle();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not load avatar from " + uri + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            IOUtils.close(is, os, pipe);
        }
    }

    @Nullable
    private String getConversationId(@Nullable Uri uri) {
        String result = uri != null ? uri.getLastPathSegment() : null;
        if (!TextUtils.isEmpty(result)) {
            result = StringUtils.trim(result, '/');
        }
        if (DEBUG) Log.d(TAG, "getConversationId " + result + " for " + uri);
        return result;

    }

    @Nullable
    private String getDefaultAvatar(@Nullable Uri uri) {
        return uri != null
                ? uri.getQueryParameter(PARAM_DEFAULT_AVATAR)
                : null;
    }

    private int getAvatarResourceId(@Nullable Uri uri) {
        int result = 0;
        String resourceId = uri != null ? uri.getQueryParameter(PARAM_AVATAR_ID) : null;
        if (!TextUtils.isEmpty(resourceId)) {
            try {
                result = Integer.parseInt(resourceId);
            } catch (NumberFormatException e) {
                // no avatar id if not parsable
            }
        }
        return result;
    }

    @Nullable
    public static String getAvatarUrl(@Nullable Uri uri) {
        String result = null;
        try {
            String avatarUrl = uri != null ? uri.getQueryParameter(PARAM_AVATAR_URL) : null;
            if (!TextUtils.isEmpty(avatarUrl)) {
                result = URLDecoder.decode(avatarUrl, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            // return null if unable to decode avatar url
        }
        return result;
    }

    private int getAvatarSize(@Nullable Uri uri) {
        int result = getDefaultAvatarSize();
        String size = uri != null ? uri.getQueryParameter(PARAM_AVATAR_SIZE) : null;
        if (!TextUtils.isEmpty(size)) {
            try {
                result = Integer.parseInt(size);
            } catch (NumberFormatException e) {
                // leave default size, if size not parsable
            }
        }
        return result;
    }

    private Bitmap getDefaultAvatar(final Context context, final int size, final int resourceId,
            final String displayName, final String conversationId) {
        if (context == null) {
            return null;
        }

        Bitmap bitmap = null;
        try {
            // first try a resource if valid value passed
            if (resourceId != 0) {
                bitmap = ViewUtil.getBitmapForDrawable(context, resourceId);
            }
            // fall back to letter drawable photo manager is using
            if (bitmap == null) {
                final LetterTileDrawable drawable = new LetterTileDrawable(context.getResources());
                drawable.setContactDetails(displayName, conversationId);
                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not load default avatar image");
        }
        return bitmap;
    }

    @Nullable
    private Bitmap downloadAvatar(Uri uri) {
        Log.d(TAG, "downloadAvatar for " + uri);
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            HttpsURLConnection urlConnection =
                    (HttpsURLConnection) new URL(uri.toString()).openConnection();
            SSLContext context = PinnedCertificateHandling.getPinnedSslContext(
                    ConfigurationUtilities.mNetworkConfiguration);
            if (context != null) {
                urlConnection.setSSLSocketFactory(context.getSocketFactory());
            } else {
                Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                throw new IllegalStateException("Failed to get pinned SSL context");
            }

            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                is = urlConnection.getInputStream();
            }
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    IOUtils.readFullyAsByteStream(is, baos);
                } finally {
                    IOUtils.close(is);
                }

                if (baos.size() > 16 * 1024) {
                    baos = getResizedBitmap(baos);
                }

                bitmap = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size());
            } else {
                Log.v(TAG, "Cannot load photo " + uri);
            }
        } catch (final Exception e) {
            // return null, contact photo manager will use default avatar
        }
        return bitmap;
    }

    @NonNull
    private ByteArrayOutputStream getResizedBitmap(ByteArrayOutputStream baos) {
        byte[] bitmapBytes = baos.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
        if (bitmap != null) {
            if (bitmap.getHeight() > MAX_AVATAR_SIZE || bitmap.getWidth() > MAX_AVATAR_SIZE) {
                Bitmap resizedBitmap = getResizedBitmap(bitmap, MAX_AVATAR_SIZE);
                if (resizedBitmap != null && bitmap != resizedBitmap) {
                    bitmap.recycle();
                    bitmap = resizedBitmap;

                    baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                }
            }
            bitmap.recycle();
        }
        return baos;
    }

    @Nullable
    private Bitmap getResizedBitmap(@Nullable Bitmap bitmap, int size) {
        Bitmap result = null;
        if (bitmap != null) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (size < Math.max(width, height)) {
                float aspectRatio = ((float) height) / ((float) width);
                if (aspectRatio > 1.0f) {
                    width = size;
                    height = (int) (width * aspectRatio);
                } else {
                    height = size;
                    width = (int) (height * aspectRatio);
                }
                result = Bitmap.createScaledBitmap(bitmap, width, height, false);
                bitmap.recycle();
            } else {
                result = bitmap;
            }
        }
        return result;
    }

    @Nullable
    private InputStream getConversationAvatarStream(@NonNull Conversation conversation, int size) {
        Context context = getContext();
        if (context == null) {
            return null;
         }

        InputStream is = null;
        String avatar = conversation.getAvatar();
        if (!TextUtils.isEmpty(avatar)) {
            File file = new File(context.getFilesDir() + "/" + PATH_AVATARS + "/" + avatar);
            File fileSmall = new File(context.getFilesDir() + "/" + PATH_AVATARS + "/" + avatar + SMALL_AVATAR_SUFFIX);
            byte[] key = getAvatarKey(context);
            byte[] iv = conversation.getAvatarIvAsByteArray();
            /*
             * Try to load thumbnail version and do not fall back to large version on failure.
             * On failure small version will be created from avatar sampled for given size.
             */
            if (size != LOADED_AVATAR_SIZE && size <= getDefaultAvatarSize()) {
                if (fileSmall.exists()) {
                    is = CryptoUtil.getCipherInputStream(fileSmall, key, iv);
                }
            }
            else if (file.exists()) {
                is = CryptoUtil.getCipherInputStream(file, key, iv);
            }
        }
        return is;
    }

    @Nullable
    private Bitmap getConversationAvatar(@NonNull Conversation conversation, int size) {
        Context context = getContext();
        if (context == null) {
            return null;
        }

        Bitmap bitmap = null;
        String avatar = conversation.getAvatar();
        if (!TextUtils.isEmpty(avatar)) {
            File file = new File(context.getFilesDir() + "/" + PATH_AVATARS + "/" + avatar);
            byte[] key = getAvatarKey(context);
            byte[] iv = conversation.getAvatarIvAsByteArray();
            if (file.exists()) {
                InputStream is = CryptoUtil.getCipherInputStream(file, key, iv);
                try {
                    if (size == LOADED_AVATAR_SIZE) {
                        // don't adjust size
                        bitmap = BitmapFactory.decodeStream(is);
                    } else {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        IOUtils.readFullyAsByteStream(is, baos);
                        bitmap = BitmapUtil.loadOptimalBitmapFromBytes(baos.toByteArray(), size, size);
                    }
                } catch (OutOfMemoryError | NullPointerException | IOException e) {
                    Log.e(TAG, "Could not load saved avatar");
                    e.printStackTrace();
                } finally {
                    IOUtils.close(is);
                }
            } else {
                bitmap = getBase64Bitmap(avatar);
                if (bitmap != null) {
                    /*
                     * try to preserve already downloaded avatar which previously was stored as
                     * base64 string within conversation
                     */
                    Bitmap resizedBitmap = getResizedBitmap(bitmap, MAX_AVATAR_SIZE);
                    if (resizedBitmap != null && bitmap != resizedBitmap) {
                        bitmap.recycle();
                        bitmap = resizedBitmap;
                    }
                    updateConversationAvatar(context, conversation, conversation.getAvatarUrl(), bitmap);
                } else {
                    /*
                     * previously downloaded avatar exists(?), drop it, re-download
                     * and clear conversation's avatar tag
                     */
                    setConversationAvatar(conversation, null, null, null);
                }
            }
        }
        return bitmap;
    }

    private boolean setConversationAvatar(@Nullable Conversation conversation, @Nullable String avatarUrl,
            @Nullable String avatarFileName, @Nullable byte[] iv) {
        if (conversation == null) {
            return false;
        }

        Context context = getContext();
        if (context == null) {
            return false;
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return false;
        }

        ConversationRepository repository = axoMessaging.getConversations();
        // when updating, remove previous file
        if (TextUtils.isEmpty(avatarFileName) || !avatarFileName.equals(conversation.getAvatar())) {
            deleteConversationAvatar(context, conversation.getAvatar());
        }

        conversation.setAvatar(avatarFileName);
        conversation.setAvatarUrl(avatarUrl);
        conversation.setAvatarIv(iv);
        repository.save(conversation);
        return true;
    }

    private int getDefaultAvatarSize() {
        return getDefaultAvatarSize(getContext());
    }

    private static int getDefaultAvatarSize(@Nullable Context context) {
        int result = DEFAULT_AVATAR_SIZE;
        if (context != null) {
            result = (int) context.getResources().getDimension(R.dimen.default_avatar_size);
        }
        return result;
    }

    @Nullable
    private Bitmap getBase64Bitmap(@NonNull String avatar) {
        Bitmap bitmap = null;
        try {
            byte[] bitmapBytes = Base64.decode(avatar, Base64.NO_WRAP);
            bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
        } catch (Throwable t) {
            //
        }
        return bitmap;
    }

    private void updateConversationAvatar(Context context, Conversation conversation, String avatarUrl,
            Bitmap bitmap) {
        if (context == null || conversation == null) {
            return;
        }
        // remove previously saved avatar file
        deleteConversationAvatar(context, conversation.getAvatar());

        // save bitmap and try to update to new one
        saveConversationAvatar(context, conversation, bitmap, avatarUrl);

        ZinaMessaging zina = ZinaMessaging.getInstance();
        if (zina.isRegistered()) {
            ConversationRepository repository = zina.getConversations();
            repository.save(conversation);
        }
    }

    @Nullable
    private static byte[] getAvatarKey(@Nullable final Context context) {
        if (context == null) {
            return null;
        }

        ContentResolver resolver = context.getContentResolver();
        byte[] keyData = KeyManagerSupport.getPrivateKeyData(resolver, AVATAR_STORE_KEY);
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(resolver, AVATAR_STORE_KEY, 32);
        }
        return keyData;
    }

    public static void saveConversationAvatar(@Nullable Context context,
            @Nullable Conversation conversation, @Nullable Bitmap bitmap, String avatarUrl) {
        if (context == null || conversation == null || bitmap == null) {
            return;
        }

        File avatarsFolder = new File(context.getFilesDir() + "/" + PATH_AVATARS + "/");
        if (!avatarsFolder.exists()) {
            if (!avatarsFolder.mkdir()) {
                return;
            }
        }

        String fileName = UUIDGen.makeType1UUID().toString();
        File file = new File(avatarsFolder, fileName);
        File fileSmall = new File(avatarsFolder, fileName + SMALL_AVATAR_SUFFIX);
        OutputStream fos = null;
        try {
            byte[] key = getAvatarKey(context);
            byte[] iv = CryptoUtil.getIV();
            if (key != null) {
                fos = CryptoUtil.getCipherOutputStream(file, key, iv);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                // update conversation object with new values
                conversation.setAvatar(fileName);
                conversation.setAvatarIv(iv);
                conversation.setAvatarUrl(avatarUrl);

                // save small version if avatar as well
                int defaultSize = getDefaultAvatarSize(context);
                bitmap = Bitmap.createScaledBitmap(bitmap, defaultSize, defaultSize, false);
                ensureSmallConversationAvatar(context, conversation, bitmap);
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to save avatar to file " + e.getMessage());
            e.printStackTrace();
            file.delete();
        } finally {
            IOUtils.close(fos);
        }
    }

    private static void ensureSmallConversationAvatar(@Nullable Context context,
            @Nullable Conversation conversation, @Nullable Bitmap bitmap) {
        if (context == null || conversation == null || bitmap == null) {
            return;
        }

        String fileName = conversation.getAvatar();
        File file = new File(context.getFilesDir() + "/" + PATH_AVATARS + "/" + fileName + SMALL_AVATAR_SUFFIX);
        if (file.exists()) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "ensureSmallConversationAvatar: creating small version of avatar for "
                    + conversation.getPartner().getUserId());
        }

        OutputStream fos = null;
        try {
            byte[] key = getAvatarKey(context);
            byte[] iv = conversation.getAvatarIvAsByteArray();
            if (key != null) {
                fos = CryptoUtil.getCipherOutputStream(file, key, iv);
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.flush();
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to save avatar to file " + e.getMessage());
            e.printStackTrace();
            file.delete();
        } finally {
            IOUtils.close(fos);
        }
    }

    public static void deleteConversationAvatar(final Context context, final String avatarFileName) {
        if (context == null || TextUtils.isEmpty(avatarFileName)) {
            return;
        }

        File avatarsFolder = new File(context.getFilesDir() + "/" + PATH_AVATARS + "/");
        File file = new File(avatarsFolder, avatarFileName);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete avatar " + file.getName());
        }
        file = new File(avatarsFolder, avatarFileName + SMALL_AVATAR_SUFFIX);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete thumbnail avatar " + file.getName());
        }
    }

    public static void deleteAllAvatars(final Context context) {
        if (context == null) {
            return;
        }

        File avatarsFolder = new File(context.getFilesDir() + "/" + PATH_AVATARS + "/");
        IOUtils.deleteRecursive(avatarsFolder);
    }
}
