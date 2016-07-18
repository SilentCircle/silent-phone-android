/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.location.LocationObserver;
import com.silentcircle.messaging.location.OnLocationReceivedListener;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.SCloudObject;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.PictureProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.DbRepository.DbObjectRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.thread.CreateThumbnail;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.CryptoUtil;
import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.UTI;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.Manifest;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import axolotl.AxolotlNative;

/**
 Everything TODO regarding attachments

 axolotl error handling (pretty rare but still should)
 cancelling
 */

public class SCloudService extends Service {
    private static final String TAG = SCloudService.class.getSimpleName();

    private Handler mRecoveryHandler = new Handler();

    AtomicBoolean atomicHasARecoveryRunning = new AtomicBoolean(false);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null) {
            String action = intent.getAction();

            switch(Action.from(action)) {
                case UPLOAD: {
                    Uri uri = intent.getData();

                    String partner = Extra.PARTNER.from(intent);
                    String messageId = Extra.ID.from(intent);

                    boolean isUnique = intent.getBooleanExtra("IS_UNIQUE", false);

                    SCloudUploadTask uploadTask = new SCloudUploadTask(partner, messageId, uri, isUnique, this);

                    uploadTask.execute();

                    break;
                }

                case DOWNLOAD: {
                    String partner = Extra.PARTNER.from(intent);
                    String messageId = Extra.ID.from(intent);

                    SCloudDownloadTask downloadTask = new SCloudDownloadTask(partner, messageId, this);

                    downloadTask.execute();

                    break;
                }

                case DOWNLOAD_THUMBNAIL: {
                    String partner = Extra.PARTNER.from(intent);
                    String messageId = Extra.ID.from(intent);

                    SCloudDownloadThumbnailTask thumbnailDownloadTask = new SCloudDownloadThumbnailTask(partner, messageId, this);

                    thumbnailDownloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    break;
                }

                case RUN_ATTACHMENT_HANDLER: {
                    if(Looper.myLooper() == null) {
                        Looper.prepare();
                    }

                    boolean fromBoot = intent.getBooleanExtra("FROM_BOOT", false);
                    boolean fromNetworkChange = intent.getBooleanExtra("FROM_NETWORK", false);

                    SCloudRecoveryRunnable scloudRecoveryRunnable = new SCloudRecoveryRunnable();
                    scloudRecoveryRunnable.setFromBoot(fromBoot);
                    scloudRecoveryRunnable.setFromNetworkChange(fromNetworkChange);
                    mRecoveryHandler.post(scloudRecoveryRunnable);
                }
            }
        }

        return START_STICKY;
    }

    private class SCloudUploadTask extends AsyncTask<Void, Void, Void> {

        private AttachmentManager mAttachmentManager;

        private String mPartner;
        private String mMessageId;

        protected Uri mUri;

        protected boolean mIsUnique;

        private Context mContext;

        public SCloudUploadTask(String partner, String messageId, Uri uri, boolean isUnique, Context context) {
            this.mPartner = partner;
            this.mMessageId = messageId;

            this.mUri = uri;

            this.mIsUnique = isUnique;

            this.mContext = context;

            if(mMessageId != null) {
                this.mAttachmentManager = new AttachmentManager(mPartner, mMessageId, mContext);
            } else {
                this.mAttachmentManager = new AttachmentManager(mPartner, mContext);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            mAttachmentManager.setEncryptionContext();      // Don't comment this, we need the context
            mAttachmentManager.upload(mUri, mIsUnique);

            return null;
        }
    }

    private class SCloudDownloadTask extends AsyncTask<Void, Void, Void> {

        private AttachmentManager mAttachmentManager;

        private String mPartner;
        private String mMessageId;

        private String mTocCloudLocator;
        private String mTocCloudKey;

        private Context mContext;

        public SCloudDownloadTask(String partner, String messageId, Context context) {
            this.mPartner = partner;
            this.mMessageId = messageId;

            this.mContext = context;

            if(mMessageId != null) {
                this.mAttachmentManager = new AttachmentManager(mPartner, mMessageId, mContext);
            } else {
                this.mAttachmentManager = new AttachmentManager(mPartner, mContext);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            mAttachmentManager.download();

            return null;
        }
    }

    private class SCloudDownloadThumbnailTask extends AsyncTask<Void, Void, Void> {

        private AttachmentManager mAttachmentManager;

        private String mPartner;
        private String mMessageId;

        private String mTocCloudLocator;
        private String mTocCloudKey;

        private Context mContext;

        public SCloudDownloadThumbnailTask(String partner, String messageId, Context context) {
            this.mPartner = partner;
            this.mMessageId = messageId;

            this.mContext = context;

            if(mMessageId != null) {
                this.mAttachmentManager = new AttachmentManager(mPartner, mMessageId, mContext);
            } else {
                this.mAttachmentManager = new AttachmentManager(mPartner, mContext);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            mAttachmentManager.downloadThumbnail();

            return null;
        }
    }

    public static Intent getDownloadThumbnailIntent(Event message, String partner, Context context) {
        Intent serviceIntent = Action.DOWNLOAD_THUMBNAIL.intent(context, SCloudService.class);
        Extra.PARTNER.to(serviceIntent, partner);
        Extra.ID.to(serviceIntent, message.getId());
        return serviceIntent;
    }

    public enum AttachmentState {
        UPLOAD_PROCESSING(0),
        UPLOAD_PROCESSING_ERROR(1),
        UPLOAD_PROCESSED(2),
        UPLOADING(3),
        UPLOADING_ERROR(4),
        UPLOADED(5),
        DOWNLOADING(6),
        DOWNLOADING_ERROR(7),
        DOWNLOADED(8),
        NOT_AVAILABLE(9);

        int state;

        AttachmentState(int state) {
            this.state = state;
        }

        public static AttachmentState fromState(int state) {
            for(AttachmentState attachmentState : AttachmentState.values()) {
                if (attachmentState.state == state) {
                    return attachmentState;
                }
            }

            return null;
        }

        public boolean isUpload() {
            return state >= 0 && state <= 5;

        }

        public boolean isDownload() {
            return state > 5 && state <= 8;

        }
    }

    public class AttachmentManager implements AxoMessaging.AxoMessagingStateCallback {

        private Context mContext;
        private String mPartner;
        private String mMessageId;

        private static final int DEFAULT_CHUNK_SIZE = 256 * 1024;

        private int mChunkSize;
        private byte[] mEncryptionContext;

        public AttachmentManager(String partner, String messageId, Context context) {
            this.mContext = context;

            this.mPartner = partner;
            this.mMessageId = messageId;
        }

        public AttachmentManager(String partner, Context context) {
            this.mContext = context;

            this.mPartner = partner;
            this.mMessageId = UUIDGen.makeType1UUID().toString();
        }

        public void upload(Uri file, boolean isUnique) {
            Uploader uploader = new Uploader();

            Uploader.UploadProcessor uploadProcessor = uploader.new UploadProcessor();
            uploadProcessor.process(file);

            uploader.upload(isUnique);
        }

        public void download() {
            Downloader downloader = new Downloader();

            Downloader.DownloadProcessor downloadProcessor = downloader.new DownloadProcessor();
            byte[][] decryptedToCSCloudObject = downloadProcessor.process();

            downloader.download(decryptedToCSCloudObject);
        }

        public void downloadThumbnail() {
            Downloader processor = new Downloader();

            Downloader.ThumbnailDownloadProcessor thumbnailDownloadProcessor = processor.new ThumbnailDownloadProcessor();
            thumbnailDownloadProcessor.process();
        }

        // Populates presigned urls, uploads, and saves SCloudObjects
        private class Uploader {
            public void upload(boolean isUnique) {
                DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                if(scloudObjectRepository == null) {
                    setAttachmentState(AttachmentState.UPLOADING_ERROR);

                    DB.deleteAttachmentState(mMessageId, mPartner);

                    runRecoveryHandler();

                    return;
                }

                setAttachmentState(AttachmentState.UPLOADING);

                List<SCloudObject> scloudObjects = scloudObjectRepository.list();

                // Filter out already uploaded chunks
                int count = 0;

                Iterator<SCloudObject> i = scloudObjects.iterator();
                while (i.hasNext()) {
                    SCloudObject scloudObject = i.next();

                    if (scloudObject.isUploaded()) {
                        // Local chunk already flagged as uploaded
                        i.remove();

                        onProgressUpdate(R.string.optimizing, count + 1, scloudObjects.size());
                    } else if(!isUnique) {
                        HttpResponse<String> amazonExistsResponse = AmazonS3.exists(scloudObject.getLocator().toString());

                        if(!amazonExistsResponse.hasError()) {
                            // Server already has the chunk
                            i.remove();

                            onProgressUpdate(R.string.optimizing, count + 1, scloudObjects.size());
                        }
                    }

                    count++;
                }

                if(scloudObjects.isEmpty()) {
                    Log.i(TAG, "File already uploaded");

                    setAttachmentState(AttachmentState.UPLOADED);

                    removeChunks();

                    sendTocMessage();

                    runRecoveryHandler();

                    return;
                }


                // Request presigned urls from the broker per SCloudObject
                Map<String, String> preSignedUrls = null;

                HttpResponse<String> preSignedUrlsResponse = Broker.requestPresignedUrls(scloudObjects, mContext);

                if(!preSignedUrlsResponse.hasError()) {
                    preSignedUrls = parseRequestPresignedUrlsResponse(preSignedUrlsResponse.response, scloudObjects);

                    if(preSignedUrls == null) {
                        setAttachmentState(AttachmentState.NOT_AVAILABLE);

                        return;
                    }
                } else if(Broker.isIgnoredError(preSignedUrlsResponse.error)) {
                    Log.i(TAG, "File already uploaded");

                    setAttachmentState(AttachmentState.UPLOADED);

                    removeChunks();

                    sendTocMessage();

                    runRecoveryHandler();

                    return;
                } else {
                    if(!Utilities.isNetworkConnected(getApplicationContext())) {
                        setAttachmentState(AttachmentState.UPLOADING_ERROR);
                        onError(R.string.connected_to_network);

                        runRecoveryHandler();
                    } else {
                        setAttachmentState(AttachmentState.NOT_AVAILABLE);
                    }

                    onProgressCancel(R.string.uploading);

                    return;
                }

                if(preSignedUrls != null) {
                    // Upload SCloudObjects
                    if (uploadScloudObjects(scloudObjects, preSignedUrls) == true) {
                        Log.i(TAG, String.format("Uploaded %d chunks", scloudObjects.size()));

                        setAttachmentState(AttachmentState.UPLOADED);

                        removeChunks();

                        sendTocMessage();

                        runRecoveryHandler();
                    } else {
                        if(!Utilities.isNetworkConnected(getApplicationContext())) {
                            setAttachmentState(AttachmentState.UPLOADING_ERROR);
                            onError(R.string.connected_to_network);

                            runRecoveryHandler();
                        }
                        else {
                            setAttachmentState(AttachmentState.NOT_AVAILABLE);
                        }

                        onProgressCancel(R.string.uploading);

                        return;
                    }
                }
            }

            public boolean uploadScloudObjects(List<SCloudObject> scloudObjects, Map<String, String> presignedUrls) {
                boolean fullyUploaded = true;

                DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                if(scloudObjectRepository == null) {
                    return false;
                }

                for(int i = 0; i < scloudObjects.size(); i++) {
                    SCloudObject scloudObject = scloudObjects.get(i);

                    if(scloudObject == null) {
                        return false;
                    }

                    onProgressUpdate(R.string.uploading, i + 1, scloudObjects.size());

                    FileInputStream fis;
                    try {
                        fis = new FileInputStream(scloudObjectRepository.getDataFile(scloudObject));
                    } catch (FileNotFoundException exception) {
                        Log.e(TAG, "Error in reading SCloudObject data", exception);

                        return false;
                    }

                    HttpResponse<String> presignedUrlUploadResponse = AmazonS3.presignedUrlUpload(presignedUrls.get(scloudObject.getLocator().toString()), fis);

                    if(!presignedUrlUploadResponse.hasError()) {
                        scloudObject.setUploaded(true);
                        scloudObjectRepository.save(scloudObject);
                    } else {
                        if(!TextUtils.isEmpty(presignedUrlUploadResponse.error)) {
                            Log.i(TAG, "Presigned upload error: " + presignedUrlUploadResponse.error +  " code: " + presignedUrlUploadResponse.responseCode);
                        } else {
                            Log.i(TAG, "Presigned upload error code: " + presignedUrlUploadResponse.responseCode);
                        }

                        fullyUploaded = false;

                        break;
                    }
                }

                return fullyUploaded;
            }

            private Map<String, String> parseRequestPresignedUrlsResponse(String response, List<SCloudObject> requestedScloudObjects) {
                Map<String, String> urlHashMap = new HashMap<>();

                try {
                    JSONObject responseJSON = new JSONObject(response);

                    for(SCloudObject scloudObject : requestedScloudObjects) {
                        String scloudObjectLocator = scloudObject.getLocator().toString();

                        JSONObject scloudObjectDatum = responseJSON.getJSONObject(scloudObjectLocator);

                        if(scloudObjectDatum == null) {
                            Log.e(TAG, "Presigned url integrity error");

                            return null;
                        }

                        String scloudObjectUrl = scloudObjectDatum.getString("url");

                        if(scloudObjectUrl == null) {
                            Log.e(TAG, "Broker missing presigned url");

                            return null;
                        }

                        urlHashMap.put(scloudObjectLocator, scloudObjectUrl);
                    }

                    return urlHashMap;
                } catch (JSONException exception) {
                    Log.e(TAG, "Presigned url parsing json exception (rare)", exception);

                    return null;
                }
            }


            // Returns processed, encrypted chunks translated into SCloudObjects
            private class UploadProcessor {
                private static final String TAG = "SCloudUploadProcessor";

                public boolean shouldProcess() {
                    AttachmentState attachmentState = getAttachmentState();

                    if(attachmentState != null) {
                        return attachmentState.equals(AttachmentState.UPLOAD_PROCESSING_ERROR);

                    }

                    return true;
                }

                public void process(Uri file) {
                    if(!shouldProcess()) {
                        return;
                    }

                    if(file == null) {
                        return;
                    }

                    setAttachmentState(AttachmentState.UPLOAD_PROCESSING);

                    if(file.equals(PictureProvider.CONTENT_URI)) {
                        InputStream in = null;
                        OutputStream out = null;
                        try {
                            in = mContext.getContentResolver().openInputStream(file);
                            BitmapFactory.Options bitmapOpts = new BitmapFactory.Options();
                            bitmapOpts.inSampleSize = 8;
                            Bitmap bitmap = BitmapFactory.decodeStream(in, null, bitmapOpts);
                            in.close();

                            /*
                             * Pre-rotate bitmap as with this operation we are loosing Exif information.
                             * Exif has to be explicitly added to end file:
                             *
                             *
                             * ExifInterface exif = new ExifInterface(<file>);
                             * exif.setAttribute(ExifInterface.TAG_ORIENTATION, <original orientation>);
                             * exif.saveAttributes();
                             */
                            Matrix matrix = ViewUtil.getRotationMatrixFromExif(mContext, file);
                            if (matrix != null) {
                                Bitmap recyclable = bitmap;
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                                        bitmap.getHeight(), matrix, false);
                                recyclable.recycle();
                            }

                            out = mContext.getContentResolver().openOutputStream(file);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out);
                            out.flush();
                            out.close();
                        } catch(IOException exception) {
                            Log.e(TAG, "Image resizing exception (ignoring)", exception);
                        }
                    }

                    // Prepare the metadata
                    JSONObject metaData = Util.getMetaData(file, mContext);

                    // Chunk, encrypt and save the file
                    boolean chunkingSuccessful = chunkEncryptAndSave(file, metaData);

                    if(!chunkingSuccessful) {
                        setAttachmentState(AttachmentState.UPLOAD_PROCESSING_ERROR);

                        removeMessage();

                        onProgressCancel(R.string.encrypting);

                        return;
                    }

                    onProgressStart(R.string.processing);

                    // Encrypt and save ToC for file
                    SCloudObject tocScloudObject = createEncryptAndSaveToc(file, metaData);

                    if(tocScloudObject == null) {
                        setAttachmentState(AttachmentState.UPLOAD_PROCESSING_ERROR);

                        removeMessage();

                        onProgressCancel(R.string.processing);

                        return;
                    }

                    if(AttachmentUtils.fromOurMediaProvider(file)) {
                        mContext.getContentResolver().delete(file, null, null);
                    }

                    setAttachmentState(AttachmentState.UPLOAD_PROCESSED);

                    onMetaDataAvailable(metaData);

                    onTocAvailable(tocScloudObject.getLocator().toString(), tocScloudObject.getKey().toString());
                }

                private boolean chunkEncryptAndSave(Uri uri, final JSONObject metaData) {
                    boolean showEncryptionProgress = false;

                    try {
                        InputStream fileInputStream = mContext.getContentResolver().openInputStream(uri);
                        long fileSize = AttachmentUtils.getFileSize(mContext, uri);
                        int numChunks = (int) Math.ceil(fileSize/getChunkSize());

                        if(fileSize > 2 * 1024 * 1024) {
                            showEncryptionProgress = true;
                        }

                        try {
                            BufferedInputStream bis = new BufferedInputStream(fileInputStream);
                            SCloudOutputStream bos = new SCloudOutputStream() {
                                @Override
                                public void onChunk(byte[] chunk) {
                                    createEncryptAndSaveSCloudObject(chunk, metaData);
                                }
                            };

                            byte[] buffer = new byte[fileSize < getChunkSize() ? (int) fileSize : getChunkSize()];

                            int read;
                            int count = 0;

                            while((read = bis.read(buffer)) != -1) {
                                bos.write(buffer, 0, read);

                                if(showEncryptionProgress) {
                                    onProgressUpdate(R.string.encrypting, count + 1, numChunks);
                                }

                                count++;
                            }

                            bos.flush();

                        } catch (IOException | IllegalStateException exception) {
                            Log.e(TAG, "Error in chunking");

                            return false;
                        } finally {
                            IOUtils.close(fileInputStream);
                            if (AttachmentUtils.matchAttachmentUri(uri) == AttachmentUtils.MATCH_VCARD_URI) {
                                mContext.getContentResolver().delete(uri, null, null);
                            }
                        }
                    } catch (FileNotFoundException exception) {
                        Log.e(TAG, "Error in chunking");

                        return false;
                    }

                    return true;
                }

                private SCloudObject createEncryptAndSaveSCloudObject(byte[] chunkDatum, JSONObject metaData) {
                    DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                    if(scloudObjectRepository == null) {
                        return null;
                    }

                    SCloudObject encryptedSCloudObject;

                    try {
                        int[] code = new int[1];

                        long cloudRef = AxolotlNative.cloudEncryptNew(getEncryptionContext(), chunkDatum, metaData.toString().getBytes("UTF-8"), code);
                        AxolotlNative.cloudCalculateKey(cloudRef);

                        byte[] dataBlob = AxolotlNative.cloudEncryptNext(cloudRef, code);
                        byte[] keyBlob = AxolotlNative.cloudEncryptGetKeyBLOB(cloudRef, code);
                        byte[] locatorBlob = AxolotlNative.cloudEncryptGetLocatorREST(cloudRef, code);

                        encryptedSCloudObject = new SCloudObject(keyBlob, locatorBlob, dataBlob);

                        AxolotlNative.cloudFree(cloudRef);
                    } catch (UnsupportedEncodingException exception) {
                        Log.e(TAG, "SCloud exception", exception);

                        return null;
                    }

                    try {
                        scloudObjectRepository.write(encryptedSCloudObject);
                    } catch(IOException exception) {
                        Log.e(TAG, "Error in and saving SCloudObject", exception);

                        return null;
                    }

                    scloudObjectRepository.save(encryptedSCloudObject);

                    return encryptedSCloudObject;
                }

                private SCloudObject createEncryptAndSaveToc(Uri file, JSONObject metaData) {
                    DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                    if(scloudObjectRepository == null) {
                        return null;
                    }

                    List<SCloudObject> scloudObjects = scloudObjectRepository.list();

                    try {
                        CreateThumbnail createThumbnail = new CreateThumbnail(mContext, new Intent().setDataAndType(file, metaData.getString("MimeType")), 240, 320);
                        String encodedThumbnail = Util.encodeThumbnail(createThumbnail.getThumbnail(), metaData.getString("MimeType"));

                        metaData.put("Scloud_Segments", scloudObjects.size());
                        metaData.put("preview", encodedThumbnail);
                        metaData.put("SHA256", Utilities.hash(getContentResolver().openInputStream(file), "SHA256"));

                        onMetaDataAvailable(metaData);

                        return createEncryptAndSaveSCloudObject(getTableOfContents(scloudObjects).toString().getBytes("UTF-8"), metaData);
                    } catch (FileNotFoundException exception) {
                        Log.e(TAG, "SCloud exception", exception);

                        return null;
                    } catch (JSONException exception) {
                        Log.e(TAG, "SCloud json exception (rare)", exception);

                        return null;
                    } catch (UnsupportedEncodingException exception) {
                        Log.e(TAG, "SCloud exception", exception);

                        return null;
                    }
                }

                private JSONArray getTableOfContents(List<SCloudObject> SCloudObjects) {
                    JSONArray tableOfContentsJsonArray = new JSONArray();

                    for(int i = 0; i < SCloudObjects.size(); i++) {
                        JSONArray SCloudObjectEntryJsonArray = new JSONArray();

                        SCloudObjectEntryJsonArray.put(i + 1);
                        SCloudObjectEntryJsonArray.put(SCloudObjects.get(i).getLocator());
                        SCloudObjectEntryJsonArray.put(SCloudObjects.get(i).getKey());

                        tableOfContentsJsonArray.put(SCloudObjectEntryJsonArray);
                    }

                    return tableOfContentsJsonArray;
                }
            }
        }

        private class Downloader {

            public void download(byte[][] decryptedTocSCloudObject) {
                if(decryptedTocSCloudObject == null) {
                    return;
                }

                byte[] decryptedTocSCloudObjectData = decryptedTocSCloudObject[0];
                byte[] decryptedTocScloudObjectMetaData = decryptedTocSCloudObject[1];

                if(decryptedTocSCloudObjectData == null) {
                    onProgressCancel(R.string.downloading);

                    return;
                }

                List<SCloudObject> tocScloudObjects = parseTocScloudObjectData(decryptedTocSCloudObjectData);

                DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                if(scloudObjectRepository != null && tocScloudObjects != null) {
                    if(!tocScloudObjects.isEmpty()) {
                        // Filter out already downloaded chunks
                        Iterator<SCloudObject> i = tocScloudObjects.iterator();
                        while(i.hasNext()) {
                            SCloudObject scloudObject = i.next();

                            if(scloudObjectRepository.exists(scloudObject.getLocator().toString())) {
                                SCloudObject existingScloudObject = scloudObjectRepository.findById(scloudObject.getLocator().toString());

                                if(existingScloudObject != null && existingScloudObject.isDownloaded()) {
                                    // Local chunk already exists
                                    i.remove();
                                }
                            }
                        }

                        if(tocScloudObjects.isEmpty()) {
                            // Everything has already been downloaded
                            setAttachmentState(AttachmentState.DOWNLOADED);

                            decryptAndCombineScloudObjectData(decryptedTocSCloudObjectData, decryptedTocScloudObjectMetaData);

                            onProgressFinish(R.string.downloading);

                            runRecoveryHandler();
                        }
                    }
                }

                if(createDownloadAndSaveScloudObjects(tocScloudObjects)) {
                    setAttachmentState(AttachmentState.DOWNLOADED);

                    decryptAndCombineScloudObjectData(decryptedTocSCloudObjectData, decryptedTocScloudObjectMetaData);

                    onProgressFinish(R.string.downloading);

                    runRecoveryHandler();
                } else {
                    if(!Utilities.isNetworkConnected(getApplicationContext())) {
                        onError(R.string.connected_to_network);
                    }

                    setAttachmentState(AttachmentState.DOWNLOADING_ERROR);

                    onProgressCancel(R.string.downloading);
                }
            }

            public boolean download(SCloudObject scloudObject) {
                return downloadSCloudObjectData(scloudObject.getLocator().toString());
            }

            public boolean download(String locator) {
                return downloadSCloudObjectData(locator);
            }

            // Downloads SCloudObject data given the locator
            public boolean downloadSCloudObjectData(String locator) {
                DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                if(scloudObjectRepository == null) {
                    return false;
                }

                File outputFile = scloudObjectRepository.getDataFile(locator);

                if(outputFile == null) {
                    return false;
                }

                HttpResponse<Boolean> scloudDownloadResponse = AmazonS3.scloudDownload(locator, outputFile);

                return !scloudDownloadResponse.hasError();

            }

            private SCloudObject createDownloadAndSaveScloudObject(String scloudLocator) {
                DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                if(scloudObjectRepository == null) {
                    return null;
                }

                Downloader downloader = new Downloader();

                boolean scloudDownloadResponse = downloader.downloadSCloudObjectData(scloudLocator);

                if(!scloudDownloadResponse) {
                    return null;
                }

                SCloudObject scloudObject = new SCloudObject();
                scloudObject.setLocator(scloudLocator);
//                scloudObject.setData(scloudObjectData);
                scloudObject.setDownloaded(true);

//                try {
//                    scloudObjectRepository.write(scloudObject);
//                } catch(IOException exception) {
//                    Log.e(TAG, "Error in saving SCloudObject", exception);
//
//                    return null;
//                }

                scloudObjectRepository.save(scloudObject);

                return scloudObject;
            }

            private boolean createDownloadAndSaveScloudObjects(List<SCloudObject> tocScloudObjects) {
                boolean fullyDownloaded = true;

                for(int i = 0; i < tocScloudObjects.size(); i++) {
                    onProgressUpdate(R.string.downloading, i + 1, tocScloudObjects.size() );

                    if(createDownloadAndSaveScloudObject(tocScloudObjects.get(i).getLocator().toString()) == null) {
                        fullyDownloaded = false;
                    }
                }

                return fullyDownloaded;
            }

            private class DownloadProcessor {
                public boolean mayAlreadyHaveFiles() {
                    AttachmentState attachmentState = getAttachmentState();

                    return attachmentState != null &&
                            (attachmentState.equals(AttachmentState.UPLOADED) || attachmentState.equals(AttachmentState.DOWNLOADED));

                }

                public boolean shouldProcess() {
                    AttachmentState attachmentState = getAttachmentState();

                    return attachmentState == null ||
                            attachmentState.equals(AttachmentState.DOWNLOADING_ERROR) ||
                            attachmentState.equals(AttachmentState.DOWNLOADED) ||
                            attachmentState.equals(AttachmentState.UPLOADED) ||
                            attachmentState.equals(AttachmentState.NOT_AVAILABLE);

                }

                public byte[][] process() {
                    JSONObject attachment = getAttachment();

                    if(attachment == null) {
                        return null;
                    }

                    String tocScloudLocator = null;
                    String tocScloudKey = null;

                    if(!attachment.has("cloud_url") || !attachment.has("cloud_key")) {
                        return null;
                    }

                    try {
                        tocScloudLocator = attachment.getString("cloud_url");
                        tocScloudKey = attachment.getString("cloud_key");
                    } catch (JSONException exception) {
                        Log.e(TAG, "Download process JSON exception", exception);

                        return null;
                    }

                    if((tocScloudLocator == null || tocScloudKey == null)) {
                        return null;
                    }

                    if(!shouldProcess()) {
                        return null;
                    }

                    DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                    if(scloudObjectRepository == null) {
                        return null;
                    }

                    // Check for already existing files, whether from a previous upload or a previous download
                    if(mayAlreadyHaveFiles()) {
                        JSONObject metaData = getMetaData();

                        if(metaData != null) {
                            String exportedFileName = metaData.optString("ExportedFileName");
                            String fileName = metaData.optString("FileName");
                            String hash = metaData.optString("SHA256");

                            if (!TextUtils.isEmpty(exportedFileName)) {
                                fileName = exportedFileName;
                            }

                            if(!TextUtils.isEmpty(fileName)) {
                                if(AttachmentUtils.isExported(fileName, hash)) {
                                    setAttachmentState(AttachmentState.DOWNLOADED);

                                    onAttachmentAvailable();

                                    removeChunks();

                                    runRecoveryHandler();

                                    return null;
                                }
                            }
                        }

                        onProgressStart(R.string.downloading);

                        SCloudObject tocScloudObject = getScloudObject(tocScloudLocator);

                        if(tocScloudObject != null) {
                            byte[] tocScloudObjectData = null;
                            try {
                                tocScloudObjectData = scloudObjectRepository.read(tocScloudObject);
                            } catch(IOException exception) {
                                Log.e(TAG, "Error in reading ToC SCloudObject data", exception);
                            }

                            byte[][] decryptedTocScloudObject = null;
                            if(tocScloudObjectData != null) {
                                decryptedTocScloudObject = decryptScloudObjectData(tocScloudObjectData, tocScloudKey);
                            }

                            if(decryptedTocScloudObject != null) {
                                byte[] decryptedTocScloudObjectData = decryptedTocScloudObject[0];
                                byte[] decryptedTocScloudObjectMetaData = decryptedTocScloudObject[1];

                                onMetaDataAvailable(decryptedTocScloudObjectMetaData);

                                List<SCloudObject> tocScloudObjects = parseTocScloudObjectData(decryptedTocScloudObjectData);

                                boolean allDownloaded = true;

                                if(tocScloudObjects == null) {
                                    allDownloaded = false;
                                } else {
                                    if(tocScloudObjects.isEmpty()) {
                                        allDownloaded = false;
                                    } else {
                                        for(int i = 0; i < tocScloudObjects.size(); i++) {
                                            if(!scloudObjectRepository.exists(tocScloudObjects.get(i).getLocator().toString())) {
                                                allDownloaded = false;
                                                break;
                                            } else {
                                                SCloudObject existingTocScloudObject = scloudObjectRepository.findById(tocScloudObjects.get(i).getLocator().toString());

                                                if(existingTocScloudObject == null || !existingTocScloudObject.isDownloaded()) {
                                                    allDownloaded = false;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }

                                if(allDownloaded) {
                                    setAttachmentState(AttachmentState.DOWNLOADED);

                                    onMetaDataAvailable(decryptedTocScloudObjectMetaData);
                                    decryptAndCombineScloudObjectData(decryptedTocScloudObjectData, decryptedTocScloudObjectMetaData);

                                    onProgressFinish(R.string.downloading);

                                    runRecoveryHandler();

                                    return null;
                                }
                            }
                        }
                    }

                    setAttachmentState(AttachmentState.DOWNLOADING);

                    SCloudObject tocScloudObject = createDownloadAndSaveScloudObject(tocScloudLocator);

                    if(tocScloudObject == null) {
                        if(!Utilities.isNetworkConnected(getApplicationContext())) {
                            removeAttachmentState();
                            onError(R.string.connected_to_network);
                        } else {
                            setAttachmentState(AttachmentState.NOT_AVAILABLE);
                            onError(R.string.error_attachment_unavailable);
                        }

                        onProgressCancel(R.string.downloading);

                        return null;
                    }

                    // Decrypt ToC and prepare and save SCloudObjects for downloading
                    byte[] tocScloudObjectData = null;
                    try {
                        tocScloudObjectData = scloudObjectRepository.read(tocScloudObject);
                    } catch(IOException exception) {
                        Log.e(TAG, "Error in reading ToC SCloudObject data", exception);
                    }

                    byte[][] decryptedTocScloudObject = null;
                    if(tocScloudObjectData != null) {
                        decryptedTocScloudObject = decryptScloudObjectData(tocScloudObjectData, tocScloudKey);
                    }

                    if(decryptedTocScloudObject == null) {
                        setAttachmentState(AttachmentState.DOWNLOADING_ERROR);

                        onProgressCancel(R.string.downloading);

                        return null;
                    }

                    byte[] decryptedToCSCloudObjectData = decryptedTocScloudObject[0];
                    byte[] decryptedToCSCloudObjectMetaData = decryptedTocScloudObject[1];

                    if(decryptedToCSCloudObjectData == null || decryptedToCSCloudObjectMetaData == null) {
                        setAttachmentState(AttachmentState.DOWNLOADING_ERROR);

                        onProgressCancel(R.string.downloading);

                        return null;
                    }

                    onMetaDataAvailable(decryptedToCSCloudObjectMetaData);

                    return decryptedTocScloudObject;
                }
            }

            private class ThumbnailDownloadProcessor extends DownloadProcessor {
                @Override
                public byte[][] process() {
                    JSONObject attachment = getAttachment();

                    if(attachment == null) {
                        return null;
                    }

                    String tocScloudLocator = null;
                    String tocScloudKey = null;

                    if(!attachment.has("cloud_url") || !attachment.has("cloud_key")) {
                        return null;
                    }

                    try {
                        tocScloudLocator = attachment.getString("cloud_url");
                        tocScloudKey = attachment.getString("cloud_key");
                    } catch (JSONException exception) {
                        Log.e(TAG, "Download process JSON exception", exception);

                        return null;
                    }

                    if(tocScloudLocator == null || tocScloudKey == null) {
                        return null;
                    }

                    DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                    if(scloudObjectRepository == null) {
                        return null;
                    }

                    SCloudObject tocScloudObject = createDownloadAndSaveScloudObject(tocScloudLocator);

                    if(tocScloudObject == null) {
                        return null;
                    }

                    // Decrypt ToC and prepare and save SCloudObjects for downloading
                    byte[] tocScloudObjectData = null;
                    try {
                        tocScloudObjectData = scloudObjectRepository.read(tocScloudObject);
                    } catch(IOException exception) {
                        Log.e(TAG, "Error in reading ToC SCloudObject data", exception);
                    }

                    byte[][] decryptedTocScloudObject = null;
                    if(tocScloudObjectData != null) {
                        decryptedTocScloudObject = decryptScloudObjectData(tocScloudObjectData, tocScloudKey);
                    }

                    if(decryptedTocScloudObject == null) {
                        return null;
                    }

                    byte[] decryptedToCSCloudObjectMetaData = decryptedTocScloudObject[1];

                    onMetaDataAvailable(decryptedToCSCloudObjectMetaData);

                    return decryptedTocScloudObject;
                }
            }

        }

        private int getChunkSize() {
            return mChunkSize <= 0 ? DEFAULT_CHUNK_SIZE : mChunkSize;
        }

        private void setChunkSize(int chunkSize) {
            this.mChunkSize = chunkSize;
        }

        private byte[] getEncryptionContext() {
            return mEncryptionContext != null ? mEncryptionContext : null;
        }

        private void setEncryptionContext(byte[] encryptionContext) {
            this.mEncryptionContext = encryptionContext;
        }

        private void setEncryptionContext() {
            this.mEncryptionContext = CryptoUtil.randomBytes(16);
        }

        private SCloudObject getScloudObject(String scloudLocator) {
            DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

            if(scloudObjectRepository == null) {
                return null;
            }

            return scloudObjectRepository.findById(scloudLocator);
        }

        private byte[][] decryptScloudObjectData(byte[] scloudObjectData, String scloudKey) {
            byte[][] returnByteArray = new byte[2][];

            int[] code = new int[1];

            long cloudRef = AxolotlNative.cloudDecryptNew(IOUtils.toByteArray(scloudKey), code);

            AxolotlNative.cloudDecryptNext(cloudRef, scloudObjectData);

            byte[] dataBlob = AxolotlNative.cloudGetDecryptedData(cloudRef);
            byte[] metaDataBlob = AxolotlNative.cloudGetDecryptedMetaData(cloudRef);

            AxolotlNative.cloudFree(cloudRef);

            returnByteArray[0] = dataBlob;
            returnByteArray[1] = metaDataBlob;

            return returnByteArray;
        }

        private List<SCloudObject> parseTocScloudObjectData(byte[] tocScloudObjectData) {
            List<SCloudObject> tocScloudObjects = new ArrayList<>();

            try {
                JSONArray tocJsonArray = new JSONArray(IOUtils.toString(tocScloudObjectData));

                for (int i = 0; i < tocJsonArray.length(); i++) {
                    JSONArray ToCSegmentJSONArray = tocJsonArray.getJSONArray(i);

                    String segmentLocator = (String) ToCSegmentJSONArray.get(1);
                    String segmentKey = (String) ToCSegmentJSONArray.get(2);

                    tocScloudObjects.add(new SCloudObject(segmentKey, segmentLocator));
                }
            } catch (JSONException exception) {
                Log.e(TAG, "SCloud ToC JSON error (rare)", exception);

                return null;
            }

            return tocScloudObjects;
        }

        private void decryptAndCombineScloudObjectData(byte[] decryptedTocScloudObjectData, byte[] decryptedTocScloudObjectMetaData) {
            if(decryptedTocScloudObjectData == null) {
                return;
            }

            DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

            if(scloudObjectRepository == null) {
                return;
            }

            List<SCloudObject> scloudObjects = parseTocScloudObjectData(decryptedTocScloudObjectData);

            boolean showDecryptingProgress = false;
            if(scloudObjects.size() > 10) {
                showDecryptingProgress = true;
            }

            AttachmentUtils.removeAttachment(mPartner, mMessageId, true, mContext);

            BufferedOutputStream bos = null;

            File attachment = AttachmentUtils.getFile(mMessageId, mContext);

            try {
                bos = new BufferedOutputStream(new FileOutputStream(attachment));

                for(int i = 0; i < scloudObjects.size(); i++) {
                    if(showDecryptingProgress) {
                        onProgressUpdate(R.string.decrypting, i + 1, scloudObjects.size());
                    }

                    SCloudObject scloudObject = scloudObjects.get(i);

                    byte[][] decryptedScloudObject = decryptScloudObjectData(scloudObjectRepository.read(scloudObject), scloudObject.getKey().toString());
                    byte[] decryptedScloudObjectData = decryptedScloudObject[0];

                    bos.write(decryptedScloudObjectData);
                    bos.flush();

                    scloudObjectRepository.remove(scloudObject);
                }

                bos.close();
            } catch(IOException exception) {
                Log.e(TAG, "SCloud decrypt and combine object exception", exception);

                if(bos != null) {
                    try {
                        bos.close();
                    } catch (IOException ignore) {
                        Log.e(TAG, "SCloud decrypt and combine object exception (ignoring)", ignore);
                    }
                }

                return;
            }

            onAttachmentAvailable(attachment, decryptedTocScloudObjectMetaData);
        }

        private DbObjectRepository getScloudObjectRepository() {
            return Util.getDbObjectRepository(mPartner, mMessageId, mContext);
        }

        private ConversationRepository getConversationRepository() {
            return Util.getConversationRepository(mContext);
        }

        private Conversation getConversation() {
            return Util.getConversation(mPartner, mContext);
        }

        private void removeMessage() {
            Util.removeMessage(mPartner, mMessageId, mContext);
        }

        private JSONObject getAttachment() {
            return Util.getAttachment(mPartner, mMessageId, mContext);
        }

        private JSONObject getMetaData() {
            return Util.getMetaData(mPartner, mMessageId, mContext);
        }

        private void removeChunks() {
            DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

            if(scloudObjectRepository == null) {
                return;
            }

            scloudObjectRepository.clear();
        }

        private void removeFileChunks() {
            Log.i(TAG, "Deleting file chunks - partner: " + mPartner + " id: " + mMessageId);

            Event event = MessageUtils.getEventById(mContext, mPartner, mMessageId);

            if(event != null) {
                if(event instanceof Message) {
                    DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                    if(scloudObjectRepository == null) {
                        return;
                    }

                    List<SCloudObject> scloudObjects = scloudObjectRepository.list();

                    String tocScloudLocator = Util.getString("cloud_url", event.getAttachment());

                    if(!TextUtils.isEmpty(tocScloudLocator)) {
                        for(SCloudObject scloudObject : scloudObjects) {
                            if(!scloudObject.getLocator().equals(tocScloudLocator)) {
                                scloudObjectRepository.remove(scloudObject);
                            }
                        }
                    }
                }
            }
        }

        private void onProgressStart(int labelResourceId) {
            onProgressUpdate(labelResourceId, 0, 100);
        }

        private void onProgressUpdate(int labelResourceId, int part, int total) {
            Intent intent = Action.PROGRESS.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            Extra.PROGRESS.to(intent, (int) Math.ceil(100 * ((double) part / total)));
            Extra.TEXT.to(intent, labelResourceId);

            mContext.sendBroadcast(intent, Manifest.permission.READ);
        }

        private void onProgressFinish(int labelResourceId) {
            onProgressCancel(labelResourceId);
        }

        private void onProgressCancel(int labelResourceId) {
            onProgressUpdate(labelResourceId, 100, 100);
        }

        private void sendTocMessage() {
            ConversationRepository conversations = AxoMessaging.getInstance(mContext).getConversations();

            Conversation conversation = conversations.findByPartner(mPartner);
            final EventRepository events = conversations.historyOf(conversation);
            final OutgoingMessage message = (OutgoingMessage) events.findById(mMessageId);

            if(message == null) {
                return;
            }

            JSONObject attributeJson = new JSONObject();

            try {
                attributeJson.put("r", true);                          // "request_receipt" - currently always true!!!

                if (conversation.hasBurnNotice()) {
                    attributeJson.put("s", conversation.getBurnDelay());  // "shred_after"
                }
            } catch (JSONException exception) {
                Log.e(TAG, "Send ToC Message JSON exception (ignoring)", exception);
            }

            if(attributeJson.length() > 0) {
                message.setAttributes(attributeJson.toString());
            }

            if(conversation.isLocationEnabled()) {
                if(Looper.myLooper() == null) {
                    Looper.prepare();
                }

                LocationObserver.observe(mContext, new OnLocationReceivedListener() {
                    @Override
                    public void onLocationReceived(Location location) {
                        MessageUtils.setMessageLocation(message, location);

                        sendMessage();
                    }

                    @Override
                    public void onLocationUnavailable() {
                        MessageUtils.setMessageLocation(message, null);

                        sendMessage();
                    }

                    private void sendMessage() {
                        events.save(message);

                        SendMessageTask task = new SendMessageTask(getApplicationContext());
                        AsyncUtils.execute(task, message);
                    }
                });
            } else {
                events.save(message);

                SendMessageTask task = new SendMessageTask(getApplicationContext());
                AsyncUtils.execute(task, message);
            }
        }

        private void onTocAvailable(String tocScloudLocator, String tocScloudKey) {
            ConversationRepository conversations = AxoMessaging.getInstance(mContext).getConversations();

            Conversation conversation = conversations.findByPartner(mPartner);
            final EventRepository events = conversations.historyOf(conversation);
            final OutgoingMessage message = (OutgoingMessage) events.findById(mMessageId);

            if(message == null) {
                return;
            }

            JSONObject attachmentJson = new JSONObject();

            try {
                attachmentJson.put("cloud_url", tocScloudLocator);
                attachmentJson.put("cloud_key", tocScloudKey);

            } catch (JSONException exception) {
                Log.e(TAG, "SCloud ToC message exception (rare)", exception);

                return;
            }

            if(attachmentJson.length() > 0) {
                message.setAttachment(attachmentJson.toString());
            }

            message.setState(MessageStates.COMPOSED);

            events.save(message);
        }

        private void onMetaDataAvailable(JSONObject metaDataJsonObject) {
            if(metaDataJsonObject != null) {
                onMetaDataAvailable(metaDataJsonObject.toString());
            }
        }

        private void onMetaDataAvailable(byte[] metaDataBytes) {
            if(metaDataBytes != null) {
                try {
                    onMetaDataAvailable(new JSONObject(IOUtils.toString(metaDataBytes)));
                } catch (JSONException exception) {
                    Log.e(TAG, "SCloud ToC JSON error (rare)", exception);
                }
            }
        }

        private void onMetaDataAvailable(String metaData) {
            ConversationRepository conversations = AxoMessaging.getInstance(mContext).getConversations();

            Conversation conversation = conversations.findByPartner(mPartner);
            EventRepository events = conversations.historyOf(conversation);
            Message message = (Message) events.findById(mMessageId);

            if(message == null) {
                return;
            }

            if(metaData != null) {
                message.setMetaData(metaData);
                message.setText(mContext.getString(R.string.attachment));
            }

            events.save(message);

            MessageUtils.notifyConversationUpdated(mContext, mPartner,
                    true, AxoMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
        }

        private void onAttachmentAvailable(File attachment, byte[] metaData) {
            Intent intent = Action.RECEIVE_ATTACHMENT.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);

            try {
                JSONObject metaDataJsonObject = new JSONObject(IOUtils.toString(metaData));

                if (metaDataJsonObject.has("preview")) {
                    metaDataJsonObject.remove("preview");
                }

                if (!metaDataJsonObject.has("SHA256")) {
                    metaDataJsonObject.put("SHA256", Utilities.hash(new FileInputStream(attachment), "SHA256"));
                }

                Extra.TEXT.to(intent, metaDataJsonObject.toString());
            } catch (Throwable ignore) {
                Log.e(TAG, "Attachment available exception (ignoring)", ignore);
            }

            mContext.sendBroadcast(intent, Manifest.permission.WRITE);
        }

        private void onAttachmentAvailable() {
            Intent intent = Action.RECEIVE_ATTACHMENT.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            Extra.EXPORTED.to(intent, true);

            mContext.sendBroadcast(intent, Manifest.permission.WRITE);
        }

        private void onError(int errorResourceId) {
            Intent intent = Action.ERROR.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            Extra.TEXT.to(intent, errorResourceId);

            mContext.sendBroadcast(intent, Manifest.permission.WRITE);
        }

        // DB Stuff
        private void setAttachmentState(AttachmentState attachmentState) {
            DB.setAttachmentState(mMessageId, mPartner, attachmentState.state);
        }

        private AttachmentState getAttachmentState() {
            return DB.getAttachmentState(mMessageId, mPartner);
        }

        private void removeAttachmentState() {
            DB.deleteAttachmentState(mMessageId, mPartner);
        }

        private void runRecoveryHandler() {
            if(Looper.myLooper() == null) {
                Looper.prepare();
            }

            AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
            boolean axoRegistered = axoMessaging.isRegistered();
            if (!axoRegistered) {
                axoMessaging.addStateChangeListener(this);
            } else {
                SCloudRecoveryRunnable scloudRecoveryRunnable = new SCloudRecoveryRunnable();
                mRecoveryHandler.post(scloudRecoveryRunnable);
            }
        }

        @Override
        public void axoRegistrationStateChange(boolean registered) {
            if (registered) {
                AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
                axoMessaging.removeStateChangeListener(this);

                runRecoveryHandler();
            }
        }
    }

    public class SCloudOutputStream extends OutputStream {
        public void onChunk(byte[] chunk) {}

        @Override
        public void write(byte [] buffer) throws IOException {
            onChunk(buffer);
        }

        @Override
        public void write(byte [] buffer, int offset, int count) throws IOException {
            byte[] segment = count < buffer.length ? new byte [count] : buffer;

            if(segment != buffer) {
                for(int i = 0; i < segment.length; i++) {
                    segment[i] = buffer[offset + i];
                }
            }

            write(segment);
        }

        @Override
        public void write(int oneByte) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    public static class HttpResponse<T> {
        public int responseCode;
        public T response;
        public String error;

        public HttpResponse(int responseCode, T response, String error) {
            this.responseCode = responseCode;
            this.response = response;
            this.error = error;
        }

        public boolean hasError() {
            return this.error != null;
        }
    }

    public static class DB {
        public static final String TAG = "SCloud DB";

        public static AttachmentState getAttachmentState(String messageId, String partner) {
            return AttachmentState.fromState(AxolotlNative.loadAttachmentStatus(IOUtils.encode(messageId), IOUtils.encode(partner), new int[1]));
        }

        public static void setAttachmentState(String messageId, String partner, AttachmentState attachmentState) {
            setAttachmentState(messageId, partner, attachmentState.state);
        }

        public static void setAttachmentState(String messageId, String partner, int attachmentState) {
            Log.i(TAG, "Attachment state set - messageId:" + messageId + " partner:" + partner + " state:" + AttachmentState.fromState(attachmentState).name());

            AxolotlNative.storeAttachmentStatus(IOUtils.encode(messageId), IOUtils.encode(partner), attachmentState);
        }

        public static List<String[]> getAttachmentsByState(AttachmentState attachmentState) {
            return getAttachmentsByState(attachmentState.state);
        }

        public static List<String[]> getAttachmentsByState(int attachmentState) {
            List<String[]> attachmentList = new ArrayList<String[]>();

            String[] attachments = AxolotlNative.loadMsgsIdsWithAttachmentStatus(attachmentState, new int[1]);

            for(int i = 0; i < attachments.length; i++) {
                String[] attachmentSplit = attachments[i].split(":");

                attachmentList.add(new String[] {attachmentSplit[0], attachmentSplit[1]});
            }

            return attachmentList;
        }

        public static void deleteAttachmentState(String messageId, String partner) {
            Log.i(TAG, "Attachment state deleted - messageId:" + messageId + " partner:" + partner);

            AxolotlNative.deleteAttachmentStatus(IOUtils.encode(messageId), IOUtils.encode(partner));
        }

        public static void deleteAttachmentsByState(AttachmentState attachmentState) {
            deleteAttachmentsByState(attachmentState.state);
        }

        public static void deleteAttachmentsByState(int attachmentState) {
            Log.i(TAG, "Attachment states deleted - state:" + AttachmentState.fromState(attachmentState).name());

            AxolotlNative.deleteWithAttachmentStatus(attachmentState);
        }
    }

    // TODO: Convert lazy for-loops into enhanced for-loops
    private class SCloudRecoveryRunnable implements Runnable {

        private final String TAG = SCloudRecoveryRunnable.class.getSimpleName();

        private boolean mFromBoot = false;
        private boolean mFromNetworkChange = false;

        @Override
        public void run() {
            Log.i(TAG, "Running attachment recovery");

            AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
            boolean axoRegistered = axoMessaging.isRegistered();
            if (!axoRegistered) {
                Log.i(TAG, "Axolotl not yet registered, cannot run recovery.");
                return;
            }

            atomicHasARecoveryRunning.set(true);

            boolean isConnected = Utilities.isNetworkConnected(getApplicationContext());

            List<String[]> uploading = DB.getAttachmentsByState(AttachmentState.UPLOADING);
            List<String[]> downloading = DB.getAttachmentsByState(AttachmentState.DOWNLOADING);

            if(!isConnected || mFromBoot) {
                if(mFromBoot) {
                    List<String[]> uploadProcessing = DB.getAttachmentsByState(AttachmentState.UPLOAD_PROCESSING);
                    List<String[]> uploadProcessingError = DB.getAttachmentsByState(AttachmentState.UPLOAD_PROCESSING_ERROR);

                    if(!uploadProcessing.isEmpty()) {
                        for(int i = 0; i < uploadProcessing.size(); i++) {
                            Util.removeMessage(uploadProcessing.get(i)[1], uploadProcessing.get(i)[0], getApplicationContext());

                            DB.deleteAttachmentState(uploadProcessing.get(i)[0], uploadProcessing.get(i)[1]);
                        }
                    }

                    if(!uploadProcessingError.isEmpty()) {
                        for(int i = 0; i < uploadProcessingError.size(); i++) {
                            Util.removeMessage(uploadProcessingError.get(i)[1], uploadProcessingError.get(i)[0], getApplicationContext());

                            DB.deleteAttachmentState(uploadProcessing.get(i)[0], uploadProcessing.get(i)[1]);
                        }
                    }
                }

                List<String[]> uploadProcessed = DB.getAttachmentsByState(AttachmentState.UPLOAD_PROCESSED);

                if(!uploading.isEmpty()) {
                    for(int i = 0; i < uploading.size(); i++) {
                        DB.setAttachmentState(uploading.get(i)[0], uploading.get(i)[1], AttachmentState.UPLOADING_ERROR);
                    }
                }

                if(!uploadProcessed.isEmpty()) {
                    for(int i = 0; i < uploadProcessed.size(); i++) {
                        DB.setAttachmentState(uploadProcessed.get(i)[0], uploadProcessed.get(i)[1], AttachmentState.UPLOADING_ERROR);
                    }
                }

                if(!downloading.isEmpty()) {
                    for(int i = 0; i < downloading.size(); i++) {
                        DB.setAttachmentState(downloading.get(i)[0], downloading.get(i)[1], AttachmentState.DOWNLOADING_ERROR);
                    }
                }
            }

            if(isConnected) {
                List<String[]> uploadErrors = DB.getAttachmentsByState(AttachmentState.UPLOADING_ERROR);

                if(!uploadErrors.isEmpty()) {
                    if(uploading.isEmpty()) {
                        String[] error = uploadErrors.get(uploadErrors.size() - 1);

                        Intent serviceIntent = Action.UPLOAD.intent(getApplicationContext(), SCloudService.class);

                        Extra.PARTNER.to(serviceIntent, error[1]);
                        Extra.ID.to(serviceIntent, error[0]);
                        serviceIntent.putExtra("IS_UNIQUE", false);
                        serviceIntent.setData(null);
                        startService(serviceIntent);
                    }
                } else {
                    List<String[]> downloadErrors = DB.getAttachmentsByState(AttachmentState.DOWNLOADING_ERROR);

                    if(!downloadErrors.isEmpty()) {
                        if(downloading.isEmpty()) {
                            String[] error = downloadErrors.get(downloadErrors.size() - 1);

                            Intent serviceIntent = Action.DOWNLOAD.intent(getApplicationContext(), SCloudService.class);

                            Extra.PARTNER.to(serviceIntent, error[1]);
                            Extra.ID.to(serviceIntent, error[0]);
                            startService(serviceIntent);
                        }
                    }
                }
            }

            atomicHasARecoveryRunning.set(false);
        }

        private void setFromBoot(boolean fromBoot) {
            this.mFromBoot = fromBoot;
        }

        private void setFromNetworkChange(boolean fromNetworkChange) { this.mFromNetworkChange = fromNetworkChange; }
    }


    private static class Broker {
        private static final String TAG = "SCloudBroker";

        private static final String BROKER_URI = "broker/";

        private static final SSLSocketFactory sslSocketFactory = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration).getSocketFactory();

        private static final List<String> IGNORED_ERRORS = new ArrayList<String>() {{
            add("You are not authorized to use that locator\n");
        }};

        private static HttpResponse<String> requestPresignedUrls(List<SCloudObject> objects, Context context) {
            byte[] apiKeyData = KeyManagerSupport.getSharedKeyData(context.getContentResolver(), ConfigurationUtilities.getShardAuthTag());

            if (apiKeyData == null) {
                Log.w(TAG, "No API key data available");

                return new HttpResponse<>(-1, null, "No API key data available");
            }

            JSONObject requestJSON = new JSONObject();

            try {
                requestJSON.put("operation", "upload");
                requestJSON.put("api_key", new String(apiKeyData, "UTF-8").trim());

                int objectsCount = objects.size();

                JSONObject filesJSON = new JSONObject();

                for (int i = 0; i < objectsCount; i++) {

                    SCloudObject object = objects.get(i);

                    JSONObject fileDataJSON = new JSONObject();

                    fileDataJSON.put("shred_date", DateUtils.getISO8601Date(System.currentTimeMillis() + 31 * DateUtils.DAY));
                    fileDataJSON.put("size", object.getSize());

                    filesJSON.put(object.getLocator().toString(), fileDataJSON);
                }

                requestJSON.put("files", filesJSON);
            } catch (JSONException exception){
                Log.e(TAG, "SCloudBroker json exception (rare)", exception);

                return new HttpResponse<>(-1, null, exception.getClass().getSimpleName());
            } catch (UnsupportedEncodingException exception) {
                Log.e(TAG, "SCloudBroker exception", exception);

                return new HttpResponse<>(-1, null, exception.getClass().getSimpleName());
            }

            HttpsURLConnection urlConnection = null;
            StringBuilder response = new StringBuilder();

            OutputStream out = null;
            try {
                String body = requestJSON.toString();

                urlConnection = (HttpsURLConnection) (new URL(ConfigurationUtilities.getProvisioningBaseUrl(context) + BROKER_URI).openConnection());

                if(sslSocketFactory != null) {
                    urlConnection.setSSLSocketFactory(sslSocketFactory);
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context, use normal SSL socket factory");

                    return new HttpResponse<>(-1, null, "SSL pinning error");
                }

                urlConnection.setRequestMethod("POST");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(body.getBytes());
                out.flush();

                int ret = urlConnection.getResponseCode();

                if (ret != HttpsURLConnection.HTTP_OK) {
                    if(urlConnection.getErrorStream() != null) {
                        IOUtils.readStream(urlConnection.getErrorStream(), response);

                        if(!TextUtils.isEmpty(response)) {
                            return new HttpResponse<>(ret, null, response.toString());
                        }
                    }

                    return new HttpResponse<>(ret, null, "");
                }

                if(urlConnection.getInputStream() != null) {
                    IOUtils.readStream(urlConnection.getInputStream(), response);

                    if(!TextUtils.isEmpty(response)) {
                        return new HttpResponse<>(ret, response.toString(), null);
                    }
                }

                return new HttpResponse<>(ret, null, "");
            } catch (IOException exception) {
                Log.e(TAG, "Broker presigned url exception", exception);

                return new HttpResponse<>(-1, null, exception.getClass().getSimpleName());
            } finally {
                try {
                    if (out != null)
                        out.close();
                } catch (IOException ignore) { }
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }

        public static boolean isIgnoredError(String error) {
            return IGNORED_ERRORS.contains(error);

        }
    }

    private static class AmazonS3 {
        private static final String TAG = "SCloudAmazonS3";

        private static final String SCLOUD_URL = "https://s3.amazonaws.com/com.silentcircle.silenttext.scloud/";

        private static final String CONTENT_TYPE_SCLOUD = "application/x-scloud";

        private static final String HEADER_AMAZON_ACL = "x-amz-acl";
        private static final String HEADER_AMAZON_ACL_VALUE_PUBLIC_READ = "public-read";

        public static final SSLSocketFactory sslSocketFactory = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration).getSocketFactory();

        public static HttpResponse<String> presignedUrlUpload(String url, InputStream is) {
            HttpsURLConnection urlConnection = null;
            StringBuilder response = new StringBuilder();
            BufferedOutputStream bos = null;

            try {
                urlConnection = (HttpsURLConnection) (new URL(url).openConnection());

                if(sslSocketFactory != null) {
                    urlConnection.setSSLSocketFactory(sslSocketFactory);
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context, use normal SSL socket factory");
                }

                urlConnection.setRequestMethod("PUT");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty(HEADER_AMAZON_ACL, HEADER_AMAZON_ACL_VALUE_PUBLIC_READ);
                urlConnection.setRequestProperty("Content-Type", CONTENT_TYPE_SCLOUD);
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                bos = new BufferedOutputStream(urlConnection.getOutputStream());
                IOUtils.pipe(is, bos);
                bos.flush();

                int ret = urlConnection.getResponseCode();

                if (ret != HttpsURLConnection.HTTP_OK) {
                    if(urlConnection.getErrorStream() != null) {
                        IOUtils.readStream(urlConnection.getErrorStream(), response);

                        if(!TextUtils.isEmpty(response)) {
                            return new HttpResponse<String>(ret, null, response.toString());
                        }
                    }

                    return new HttpResponse<>(ret, null, "");
                } else {
                    if(urlConnection.getInputStream() != null) {
                        IOUtils.readStream(urlConnection.getInputStream(), response);

                        if(!TextUtils.isEmpty(response)) {
                            return new HttpResponse<>(ret, response.toString(), null);
                        }
                    }

                    return new HttpResponse<>(ret, "", null);
                }
            } catch (IOException exception) {
                Log.e(TAG, "AmazonS3 upload exception", exception);

                return new HttpResponse<>(-1, null, exception.getClass().getSimpleName());
            } finally {
                IOUtils.close(is);
                IOUtils.close(bos);

                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }

        private static HttpResponse<Boolean> scloudDownload(String locator, File outputFile) {
            HttpsURLConnection urlConnection = null;
            StringBuilder response = new StringBuilder();

            try {
                urlConnection = (HttpsURLConnection) (new URL(getSCloudURL(locator)).openConnection());

                if(sslSocketFactory != null) {
                    urlConnection.setSSLSocketFactory(sslSocketFactory);
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context, use normal SSL socket factory");
                }

                urlConnection.setRequestMethod("GET");
                urlConnection.setRequestProperty("Content-Type", CONTENT_TYPE_SCLOUD);
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                int ret = urlConnection.getResponseCode();

                if (ret != HttpsURLConnection.HTTP_OK) {
                    if(urlConnection.getErrorStream() != null) {
                        IOUtils.readStream(urlConnection.getErrorStream(), response);

                        if(!TextUtils.isEmpty(response)) {
                            return new HttpResponse<>(ret, false, response.toString());
                        }
                    }

                    return new HttpResponse<>(ret, false, "");
                } else {
                    if(urlConnection.getInputStream() != null) {
                        InputStream in = urlConnection.getInputStream();
                        FileOutputStream out = new FileOutputStream(outputFile);

                        byte[] buffer = new byte[1024];
                        int count;

                        while((count = in.read(buffer)) > 0) {
                            out.write(buffer, 0, count);
                            out.flush();
                        }
                        in.close();
                        out.close();
                    }

                    return new HttpResponse<>(ret, true, null);
                }
            } catch (IOException exception) {
                Log.e(TAG, "AmazonS3 download exception", exception);

                return new HttpResponse<>(-1, false, exception.getClass().getSimpleName());
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }

        private static HttpResponse<String> exists(String locator) {
            HttpsURLConnection urlConnection = null;
            StringBuilder response = new StringBuilder();

            try {
                urlConnection = (HttpsURLConnection) (new URL(getSCloudURL(locator)).openConnection());

                if(sslSocketFactory != null) {
                    urlConnection.setSSLSocketFactory(sslSocketFactory);
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context, use normal SSL socket factory");
                }

                urlConnection.setRequestMethod("HEAD");
                urlConnection.setRequestProperty("Content-Type", CONTENT_TYPE_SCLOUD);
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                int ret = urlConnection.getResponseCode();

                if (ret != HttpsURLConnection.HTTP_OK) {
                    if(urlConnection.getErrorStream() != null) {
                        IOUtils.readStream(urlConnection.getErrorStream(), response);

                        if(!TextUtils.isEmpty(response)) {
                            return new HttpResponse<>(ret, null, response.toString());
                        }
                    }

                    return new HttpResponse<>(ret, null, "");
                } else {
                    if(urlConnection.getInputStream() != null) {
                        IOUtils.readStream(urlConnection.getInputStream(), response);

                        if(!TextUtils.isEmpty(response)) {
                            return new HttpResponse<>(ret, response.toString(), null);
                        }
                    }

                    return new HttpResponse<>(ret, "", null);
                }
            } catch (IOException exception) {
                Log.e(TAG, "AmazonS3 exists exception", exception);

                return new HttpResponse<>(-1, null, exception.getClass().getSimpleName());
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }

        private static String getSCloudURL(String locator ) {
            return AmazonS3.SCLOUD_URL +  locator;
        }

        private static String getSCloudURL( SCloudObject scloudObject ) {
            return AmazonS3.SCLOUD_URL +  scloudObject.getLocator().toString();
        }
    }

    private static class Util {
        private static DbObjectRepository getDbObjectRepository(String partner, String messageId, Context context) {
            if(partner == null || messageId == null) {
                return null;
            }

            AxoMessaging axoMessaging = AxoMessaging.getInstance(context);

            if(axoMessaging == null) {
                return null;
            }

            ConversationRepository conversations = axoMessaging.getConversations();

            if(conversations == null) {
                return null;
            }

            Conversation conversation = conversations.findByPartner(partner);

            if(conversation == null) {
                return null;
            }

            EventRepository events = conversations.historyOf(conversation);

            if(events == null) {
                return null;
            }

            Message message = (Message) events.findById(messageId);

            if(message == null) {
                return null;
            }

            return (DbObjectRepository) events.objectsOf(message);
        }

        public static ConversationRepository getConversationRepository(Context context) {
            AxoMessaging axoMessaging = AxoMessaging.getInstance(context);

            if(axoMessaging == null) {
                return null;
            }

            ConversationRepository conversations = axoMessaging.getConversations();

            if(conversations == null) {
                return null;
            }

            return conversations;
        }

        public static Conversation getConversation(String partner, Context context) {
            if(partner == null) {
                return null;
            }

            AxoMessaging axoMessaging = AxoMessaging.getInstance(context);

            if(axoMessaging == null) {
                return null;
            }

            ConversationRepository conversations = axoMessaging.getConversations();

            if(conversations == null) {
                return null;
            }

            Conversation conversation = conversations.findByPartner(partner);

            if(conversation == null) {
                return null;
            }

            return conversation;
        }

        private static void removeMessage(String partner, String messageId, Context context) {
            Log.i(TAG, "Removing message - partner: " + partner + " messageId: " + messageId);

            if(partner == null || messageId == null) {
                return;
            }

            ConversationRepository conversationRepository = Util.getConversationRepository(context);

            if(conversationRepository == null) {
                return;
            }

            Conversation conversation = getConversation(partner, context);

            if(conversation == null) {
                return;
            }

            EventRepository conversationEventRepository = conversationRepository.historyOf(conversation);

            if(conversationEventRepository == null) {
                return;
            }

            Event message = conversationEventRepository.findById(messageId);

            if(message == null) {
                return;
            }

            conversationEventRepository.remove(message);
        }

        private static JSONObject getAttachment(String partner, String messageId, Context context) {
            if(partner == null || messageId == null) {
                return null;
            }

            Event event = MessageUtils.getEventById(context, partner, messageId);

            if(event instanceof Message) {
                try {
                    return new JSONObject(event.getAttachment());
                } catch (JSONException exception) {
                    Log.e(TAG, "Message attachment JSON error (rare)", exception);
                }
            }

            return null;
        }

        private static JSONObject getMetaData(String partner, String messageId, Context context) {
            if(partner == null || messageId == null) {
                return null;
            }

            Event event = MessageUtils.getEventById(context, partner, messageId);

            if(event instanceof Message) {
                try {
                    return new JSONObject(((Message) event).getMetaData());
                } catch (JSONException exception) {
                    Log.e(TAG, "Message attachment JSON error (rare)", exception);
                }
            }

            return null;
        }

        private static JSONObject getMetaData( Uri uri, Context context ) {
            if(uri == null) {
                return null;
            }

            JSONObject metaData = new JSONObject();

            String mimeType = AttachmentUtils.getMIMEType(context, uri);
            String fileName = AttachmentUtils.getFileName(context, uri);

            try {
                metaData.put("MediaType", UTI.fromMIMEType(mimeType) );
                metaData.put("MimeType", AttachmentUtils.getMIMEType(context, uri));
                metaData.put("FileName", !TextUtils.isEmpty(fileName) ? fileName : "");
                metaData.put("FileSize", AttachmentUtils.getFileSize(context, uri));
            } catch( JSONException exception ) {
                Log.e(TAG, "SCloud meta data building JSON error (rare)", exception);
            }

            return metaData;
        }

        private static String getString(String string, String jsonData) {
            if(string == null || jsonData == null) {
                return null;
            }

            if(TextUtils.isEmpty(jsonData)) {
                return null;
            }

            try {
                JSONObject jsonObject = new JSONObject(jsonData);

                if(jsonObject.has(string)) {
                    return jsonObject.getString(string);
                }
            } catch (JSONException exception) {
                return null;
            }

            return null;
        }

        private static String encodeThumbnail(Bitmap bitmap, String mimeType) {
            if(bitmap == null ) {
                return "";
            }

            ByteArrayOutputStream thumbnailBytes = new ByteArrayOutputStream();
            Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;
            if (MIME.isContact(mimeType) || MIME.isOctetStream(mimeType)
                    || MIME.isDoc(mimeType) || MIME.isPdf(mimeType) || MIME.isPpt(mimeType)
                    || MIME.isText(mimeType) || MIME.isXls(mimeType)) {
                // for some types prefer PNG, will be small enough, if one color is dominant
                compressFormat = Bitmap.CompressFormat.PNG;
            }
            bitmap.compress(compressFormat, 60, thumbnailBytes);
            return Base64.encodeToString(thumbnailBytes.toByteArray(), Base64.NO_WRAP);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
