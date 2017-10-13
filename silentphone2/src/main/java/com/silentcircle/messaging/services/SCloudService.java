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
package com.silentcircle.messaging.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;

import com.silentcircle.common.waveform.SoundFile;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.location.LocationObserver;
import com.silentcircle.messaging.location.OnLocationReceivedListener;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.SCloudObject;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.MessageStateEvent;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.AudioProvider;
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
import com.silentcircle.messaging.util.PresentationType;
import com.silentcircle.messaging.util.UTI;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.BuildConfig;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import zina.ZinaNative;

import static com.silentcircle.messaging.services.SCloudService.AttachmentState.fromState;
import static zina.JsonStrings.ATTRIBUTE_READ_RECEIPT;
import static zina.JsonStrings.ATTRIBUTE_SHRED_AFTER;

/**
 Everything TODO regarding attachments

 axolotl error handling (pretty rare but still should)
 cancelling
 */

public class SCloudService extends Service {
    private static final String TAG = SCloudService.class.getSimpleName();

    private static final boolean LOG_TRANSFER_TIME = BuildConfig.DEBUG;

    private static final int TRANSFER_THREADS_NUM = 4;

    public static final String ATTACHMENT_INFO =
            "com.silentcircle.messaging.services.attachmentInfo";
    public static final String FLAG_GROUP_AVATAR =
            "com.silentcircle.messaging.services.flagGroupAvatar";

    public static final String SCLOUD_ATTACHMENT_CLOUD_URL = "cloud_url";
    public static final String SCLOUD_ATTACHMENT_CLOUD_KEY = "cloud_key";
    public static final String SCLOUD_ATTACHMENT_FILENAME = "filename";
    public static final String SCLOUD_ATTACHMENT_EXPORTED_FILENAME = "exported_filename";
    public static final String SCLOUD_ATTACHMENT_DISPLAYNAME = "display_name";
    public static final String SCLOUD_ATTACHMENT_SHA256 = "SHA256";
    public static final String SCLOUD_ATTACHMENT_FILESIZE = "file_size";
    public static final String SCLOUD_ATTACHMENT_MIMETYPE = "content_type";
    public static final String SCLOUD_ATTACHMENT_PRESENTATION_TYPE = "presentation_type";

    public static final String SCLOUD_METADATA_THUMBNAIL = "preview";
    public static final String SCLOUD_METADATA_WAVEFORM = "Waveform";
    public static final String SCLOUD_METADATA_DURATION = "Duration";
    public static final String SCLOUD_METADATA_MIMETYPE = "MimeType";
    public static final String SCLOUD_METADATA_MEDIATYPE = "MediaType";
    public static final String SCLOUD_METADATA_FILENAME = "FileName";
    public static final String SCLOUD_METADATA_FILESIZE = "FileSize";
    public static final String SCLOUD_METATA_DISPLAYNAME = "DisplayName";
    public static final String SCLOUD_METATA_EXPORTED_FILENAME = "ExportedFileName";

    private static final ExecutorService sParallelTransferExecutor =
            Executors.newFixedThreadPool(TRANSFER_THREADS_NUM);

    private Handler mRecoveryHandler = new Handler();

    AtomicBoolean atomicHasARecoveryRunning = new AtomicBoolean(false);

    public static final List<AsyncTask<Void, Void, Void>> DOWNLOAD_LIST
            = Collections.synchronizedList(new ArrayList<AsyncTask<Void, Void, Void>>());
    public static final List<AsyncTask<Void, Void, Void>> UPLOAD_LIST
            = Collections.synchronizedList(new ArrayList<AsyncTask<Void, Void, Void>>());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null) {
            String action = intent.getAction();

            Bundle extras = new Bundle();
            // populate extras which will be added to broadcast events
            extras.putBoolean(FLAG_GROUP_AVATAR, intent.getBooleanExtra(FLAG_GROUP_AVATAR, false));
            extras.putString(ATTACHMENT_INFO, intent.getStringExtra(ATTACHMENT_INFO));

            switch(Action.from(action)) {
                case UPLOAD: {
                    Uri uri = intent.getData();

                    String partner = Extra.PARTNER.from(intent);
                    String messageId = Extra.ID.from(intent);

                    boolean isUnique = intent.getBooleanExtra("IS_UNIQUE", false);

                    SCloudUploadTask uploadTask = new SCloudUploadTask(partner, messageId, uri, isUnique, extras, this);

                    if ((isUploading() || isDownloading())
                            && !intent.getBooleanExtra(FLAG_GROUP_AVATAR, false)) {
                        uploadTask.mAttachmentManager.onCancel(R.string.attachment_wait_until_complete);
                    } else {
                        uploadTask.execute();
                    }

                    break;
                }

                case DOWNLOAD: {
                    String partner = Extra.PARTNER.from(intent);
                    String messageId = Extra.ID.from(intent);
                    String attachmentInfo = intent.getStringExtra(ATTACHMENT_INFO);

                    SCloudDownloadTask downloadTask = new SCloudDownloadTask(partner, messageId, attachmentInfo, extras, this);

                    if ((isUploading() || isDownloading())
                            && !intent.getBooleanExtra(FLAG_GROUP_AVATAR, false)) {
                        downloadTask.mAttachmentManager.onCancel(R.string.attachment_wait_until_complete);
                    } else {
                        downloadTask.execute();
                    }

                    break;
                }

                case DOWNLOAD_THUMBNAIL: {
                    String partner = Extra.PARTNER.from(intent);
                    String messageId = Extra.ID.from(intent);

                    SCloudDownloadThumbnailTask thumbnailDownloadTask = new SCloudDownloadThumbnailTask(partner, messageId, extras, this);

                    try {
                        thumbnailDownloadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } catch (RejectedExecutionException e) {
                        // Do nothing for now. RefreshTask executed by either ConversationActivity or GroupMessaging
                        // will pick up the rejected message later
                        Log.e(TAG, "Failed to submit a task for execution", e);
                    }

                    break;
                }

                case RUN_ATTACHMENT_HANDLER: {
                    if(Looper.myLooper() == null) {
                        Looper.prepare();
                    }

                    boolean fromBoot = intent.getBooleanExtra("FROM_BOOT", false);
//                    boolean fromNetworkChange = intent.getBooleanExtra("FROM_NETWORK", false);    state not used

//                    SCloudRecoveryRunnable scloudRecoveryRunnable = new SCloudRecoveryRunnable();
//                    scloudRecoveryRunnable.setFromBoot(fromBoot);
//                    scloudRecoveryRunnable.setFromNetworkChange(fromNetworkChange);
//                    mRecoveryHandler.post(scloudRecoveryRunnable);
                    break;
                }

                default:
                case _INVALID_:
                    break;
            }
        }

        return START_STICKY;
    }

    private class SCloudUploadTask extends AsyncTask<Void, Void, Void> {

        private AttachmentManager mAttachmentManager;

        private String mPartner;
        private String mMessageId;

        Uri mUri;

        boolean mIsUnique;

        private Context mContext;

        SCloudUploadTask(String partner, String messageId, Uri uri, boolean isUnique, Bundle extras, Context context) {
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
            mAttachmentManager.setExtras(extras);
        }

        @Override
        protected Void doInBackground(Void... params) {
            synchronized (UPLOAD_LIST) {
                UPLOAD_LIST.add(this);
            }

            mAttachmentManager.setEncryptionContext();      // Don't comment this, we need the context
            mAttachmentManager.upload(mUri, mIsUnique);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (UPLOAD_LIST) {
                UPLOAD_LIST.remove(this);
            }
        }

        @Override
        protected void onCancelled() {
            synchronized (UPLOAD_LIST) {
                UPLOAD_LIST.remove(this);
            }
        }
    }

    private class SCloudDownloadTask extends AsyncTask<Void, Void, Void> {

        private AttachmentManager mAttachmentManager;

        private String mPartner;
        private String mMessageId;

        private Context mContext;

        SCloudDownloadTask(String partner, String messageId, String attachmentInfo, Bundle extras, Context context) {
            this.mPartner = partner;
            this.mMessageId = messageId;

            this.mContext = context;

            if(mMessageId != null) {
                this.mAttachmentManager = new AttachmentManager(mPartner, mMessageId, mContext);
            } else {
                this.mAttachmentManager = new AttachmentManager(mPartner, mContext);
            }
            mAttachmentManager.setAttachmentInfo(attachmentInfo);
            mAttachmentManager.setExtras(extras);
        }

        @Override
        protected Void doInBackground(Void... params) {
            synchronized (DOWNLOAD_LIST) {
                DOWNLOAD_LIST.add(this);
            }

            mAttachmentManager.download();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (DOWNLOAD_LIST) {
                DOWNLOAD_LIST.remove(this);
            }
        }

        @Override
        protected void onCancelled() {
            synchronized (DOWNLOAD_LIST) {
                DOWNLOAD_LIST.remove(this);
            }
        }
    }

    private class SCloudDownloadThumbnailTask extends AsyncTask<Void, Void, Void> {

        private AttachmentManager mAttachmentManager;

        private String mPartner;
        private String mMessageId;

        private Context mContext;

        SCloudDownloadThumbnailTask(String partner, String messageId,  Bundle extras, Context context) {
            this.mPartner = partner;
            this.mMessageId = messageId;

            this.mContext = context;

            if(mMessageId != null) {
                this.mAttachmentManager = new AttachmentManager(mPartner, mMessageId, mContext);
            } else {
                this.mAttachmentManager = new AttachmentManager(mPartner, mContext);
            }
            mAttachmentManager.setExtras(extras);
        }

        @Override
        protected Void doInBackground(Void... params) {
            mAttachmentManager.downloadThumbnail();

            return null;
        }
    }

    public static boolean isUploading() {
        synchronized (UPLOAD_LIST) {
            if (UPLOAD_LIST.size() == 0) {
                return false;
            }

            for (AsyncTask<Void, Void, Void> task : UPLOAD_LIST) {
                if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isDownloading() {
        synchronized (DOWNLOAD_LIST) {
            if (DOWNLOAD_LIST.size() == 0) {
                return false;
            }

            for (AsyncTask<Void, Void, Void> task : DOWNLOAD_LIST) {
                if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
                    return true;
                }
            }
        }

        return false;
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

        @Nullable
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

    public class AttachmentManager implements ZinaMessaging.AxoMessagingStateCallback {

        private Context mContext;
        private String mPartner;
        private String mMessageId;
        private String mAttachmentInfo;

        private Bundle mExtras;

        private static final int DEFAULT_CHUNK_SIZE = 256 * 1024;

//        private int mChunkSize; - is never assigned/initialized - remove it
        private byte[] mEncryptionContext;

        private boolean mClearObjectRepoOnFinish;

        AttachmentManager(String partner, String messageId, Context context) {
            this.mContext = context;

            this.mPartner = partner;
            this.mMessageId = messageId;
        }

        AttachmentManager(String partner, Context context) {
            this.mContext = context;

            this.mPartner = partner;
            this.mMessageId = UUIDGen.makeType1UUID().toString();

            Util.createDbObjectRepository(partner, mMessageId);
            mClearObjectRepoOnFinish = true;
        }

        public void setExtras(Bundle extras) {
            mExtras = extras;
        }

        void setAttachmentInfo(String attachmentInfo) {
            mAttachmentInfo = attachmentInfo;
        }

        public void upload(Uri file, boolean isUnique) {
            Uploader uploader = new Uploader();

            Uploader.UploadProcessor uploadProcessor = uploader.new UploadProcessor();
            uploadProcessor.process(file);

            uploader.upload(isUnique);

            if (mClearObjectRepoOnFinish) {
                removeMessage();
            }
        }

        public void download() {
            Downloader downloader = new Downloader();

            Downloader.DownloadProcessor downloadProcessor = downloader.new DownloadProcessor();
            byte[][] decryptedToCSCloudObject = downloadProcessor.process();

            downloader.download(decryptedToCSCloudObject);

            if (mClearObjectRepoOnFinish) {
                removeMessage();
            }
        }

        void downloadThumbnail() {
            Downloader processor = new Downloader();

            Downloader.ThumbnailDownloadProcessor thumbnailDownloadProcessor = processor.new ThumbnailDownloadProcessor();
            thumbnailDownloadProcessor.process();

            if (mClearObjectRepoOnFinish) {
                removeMessage();
            }
        }

        // Populates presigned urls, uploads, and saves SCloudObjects
        private class Uploader {

            public void upload(boolean isUnique) {
                long startTime = 0;
                if (LOG_TRANSFER_TIME) {
                    startTime = SystemClock.elapsedRealtime();
                }
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

                ArrayList<Future<Boolean>> alreadyUploadedFutures = new ArrayList<>(scloudObjects.size());
                ArrayList<SCloudObject> futureObjects = new ArrayList<>(scloudObjects.size());
                Iterator<SCloudObject> it = scloudObjects.iterator();
                while (it.hasNext()) {
                    final SCloudObject scloudObject = it.next();

                    if (scloudObject.isUploaded()) {
                        // Local chunk already flagged as uploaded
                        it.remove();

                        onProgressUpdate(R.string.optimizing, count + 1, scloudObjects.size());
                    } else if(!isUnique) {
                        // Check asynchronously if chunk is already uploaded using futures
                        Callable<Boolean> callable = new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                HttpResponse<String> amazonExistsResponse = AmazonS3.exists(scloudObject.getLocator().toString());
                                return !amazonExistsResponse.hasError();
                            }
                        };
                        Future<Boolean> alreadyUploadedFuture = sParallelTransferExecutor.submit(callable);
                        alreadyUploadedFutures.add(alreadyUploadedFuture);
                        futureObjects.add(scloudObject);
                    }

                    count++;
                }
                // Block until submitted futures have finished
                for (int i = 0; i < alreadyUploadedFutures.size(); i++) {
                    try {
                        if (alreadyUploadedFutures.get(i).get()) {
                            scloudObjects.remove(futureObjects.get(i));
                            onProgressUpdate(R.string.optimizing, count + 1, scloudObjects.size());
                            count++;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
                Map<String, String> preSignedUrls;

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

                // Upload SCloudObjects
                if (uploadScloudObjects(scloudObjects, preSignedUrls)) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Chunks uploaded:" + scloudObjects.size());

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
                }

                if (LOG_TRANSFER_TIME) {
                    long duration = SystemClock.elapsedRealtime() - startTime;
                    String optimizing = (!isUnique) ? "optimizing and " : "";
                    Log.d(TAG, String.format(Locale.US, "Finished " + optimizing + "uploading %d chunks in %d ms",
                            scloudObjects.size(), duration));
                }
            }

            boolean uploadScloudObjects(final List<SCloudObject> scloudObjects, final Map<String, String> presignedUrls) {
                boolean fullyUploaded = true;

                final DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                if(scloudObjectRepository == null) {
                    return false;
                }

                ArrayList<Future<Boolean>> futures = new ArrayList<>(scloudObjects.size());

                // Parallelized loop
                for(int i = 0; i < scloudObjects.size(); i++) {
                    final SCloudObject scloudObject = scloudObjects.get(i);
                    Callable<Boolean> callable = new Callable<Boolean>() {

                        @Override
                        public Boolean call() throws Exception {
                            if(scloudObject == null) {
                                return false;
                            }

                            HttpResponse<String> presignedUrlUploadResponse = null;
                            FileInputStream fis = null;
                            try {
                                fis = new FileInputStream(scloudObjectRepository.getDataFile(scloudObject));
                                presignedUrlUploadResponse = AmazonS3.presignedUrlUpload(presignedUrls.get(scloudObject.getLocator().toString()), fis);
                            } catch (FileNotFoundException exception) {
                                Log.e(TAG, "Error in reading SCloudObject data", exception);

                                return false;
                            } finally {
                                IOUtils.close(fis);
                            }

                            if (presignedUrlUploadResponse == null) {
                                return false;
                            }

                            if(!presignedUrlUploadResponse.hasError()) {
                                scloudObject.setUploaded(true);
                                scloudObjectRepository.save(scloudObject);
                            } else {
                                if(!TextUtils.isEmpty(presignedUrlUploadResponse.error)) {
                                    Log.i(TAG, "Presigned upload error: " + presignedUrlUploadResponse.error +  " code: " + presignedUrlUploadResponse.responseCode);
                                } else {
                                    Log.i(TAG, "Presigned upload error code: " + presignedUrlUploadResponse.responseCode);
                                }

                                return false;
                            }

                            return true;
                        }
                    };

                    Future<Boolean> future = sParallelTransferExecutor.submit(callable);
                    futures.add(future);
                }

                // Block until all futures have finished
                for (int i = 0; i < futures.size(); i++) {
                    try {
                        fullyUploaded &= futures.get(i).get();
                        onProgressUpdate(R.string.uploading, i + 1, scloudObjects.size());
                    } catch (Exception e) {
                        e.printStackTrace();
                        fullyUploaded = false;
                    }
                }

                return fullyUploaded;
            }

            @Nullable
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

                boolean shouldProcess() {
                    AttachmentState attachmentState = getAttachmentState();

                    return attachmentState == null || !attachmentState.equals(AttachmentState.UPLOAD_PROCESSING_ERROR);
                }

                public void process(Uri file) {
                    if(!shouldProcess()) {
                        return;
                    }

                    if(file == null) {
                        return;
                    }

                    setAttachmentState(AttachmentState.UPLOAD_PROCESSING);

//                    if(file.equals(PictureProvider.CONTENT_URI)) {
//                        InputStream in = null;
//                        OutputStream out = null;
//                        try {
//                            in = mContext.getContentResolver().openInputStream(file);
//                            BitmapFactory.Options bitmapOpts = new BitmapFactory.Options();
//                            bitmapOpts.inSampleSize = 8;
//                            Bitmap bitmap = BitmapFactory.decodeStream(in, null, bitmapOpts);
//                            in.close();
//
//                            /*
//                             * Pre-rotate bitmap as with this operation we are loosing Exif information.
//                             * Exif has to be explicitly added to end file:
//                             *
//                             *
//                             * ExifInterface exif = new ExifInterface(<file>);
//                             * exif.setAttribute(ExifInterface.TAG_ORIENTATION, <original orientation>);
//                             * exif.saveAttributes();
//                             */
//                            Matrix matrix = ViewUtil.getRotationMatrixFromExif(mContext, file);
//                            if (matrix != null) {
//                                Bitmap recyclable = bitmap;
//                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
//                                        bitmap.getHeight(), matrix, false);
//                                recyclable.recycle();
//                            }
//
//                            out = mContext.getContentResolver().openOutputStream(file);
//                            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out);
//                            out.flush();
//                            out.close();
//                        } catch(IOException exception) {
//                            Log.e(TAG, "Image resizing exception (ignoring)", exception);
//                        }
//                    }

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
                        if (fileInputStream == null) {
                            Log.e(TAG, "Cannot open input steam: " + uri.toString());
                            return false;
                        }
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

                            byte[] buffer = new byte[fileSize > 0 && fileSize < getChunkSize() ? (int) fileSize : getChunkSize()];

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

                        } catch (Exception exception) {
                            Log.e(TAG, "Error in chunking", exception);

                            return false;
                        } finally {
                            IOUtils.close(fileInputStream);
                            if (AttachmentUtils.matchAttachmentUri(uri) == AttachmentUtils.MATCH_VCARD_URI) {
                                mContext.getContentResolver().delete(uri, null, null);
                            }
                        }
                    } catch (Exception exception) {
                        Log.e(TAG, "Error in chunking", exception);

                        return false;
                    }

                    return true;
                }

                @Nullable
                private SCloudObject createEncryptAndSaveSCloudObject(byte[] chunkDatum, JSONObject metaData) {
                    DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                    if(scloudObjectRepository == null) {
                        return null;
                    }

                    SCloudObject encryptedSCloudObject;

                    try {
                        int[] code = new int[1];

                        long cloudRef = ZinaNative.cloudEncryptNew(getEncryptionContext(), chunkDatum, metaData.toString().getBytes("UTF-8"), code);
                        ZinaNative.cloudCalculateKey(cloudRef);

                        byte[] dataBlob = ZinaNative.cloudEncryptNext(cloudRef, code);
                        byte[] keyBlob = ZinaNative.cloudEncryptGetKeyBLOB(cloudRef, code);
                        byte[] locatorBlob = ZinaNative.cloudEncryptGetLocatorREST(cloudRef, code);

                        encryptedSCloudObject = new SCloudObject(keyBlob, locatorBlob, dataBlob);

                        ZinaNative.cloudFree(cloudRef);
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

                @Nullable
                private SCloudObject createEncryptAndSaveToc(Uri file, JSONObject metaData) {
                    DbObjectRepository scloudObjectRepository = getScloudObjectRepository();

                    if(scloudObjectRepository == null) {
                        return null;
                    }

                    List<SCloudObject> scloudObjects = scloudObjectRepository.list();

                    try {
                        CreateThumbnail createThumbnail = new CreateThumbnail(mContext, new Intent().setDataAndType(file, metaData.getString(SCLOUD_METADATA_MIMETYPE)), 240, 320);
                        String encodedThumbnail = Util.encodeThumbnail(createThumbnail.getThumbnail(), metaData.getString(SCLOUD_METADATA_MIMETYPE));

                        if (file.equals(AudioProvider.CONTENT_URI)) {
                            SoundFile soundFile = SoundFile.create(mContext, file, 80);
                            if (soundFile != null) {
                                String durationString = AttachmentUtils
                                        .getDurationAsString(soundFile.getDurationMS());
                                metaData.put(SCLOUD_METADATA_DURATION, durationString);
                                float[] levels = soundFile.getLevels();
                                if (levels != null) {
                                    String base64Levels = AttachmentUtils.getLevelsAsBase64String(levels);
                                    metaData.put(SCLOUD_METADATA_WAVEFORM, base64Levels);
                                }
                            }
                        }

                        metaData.put("Scloud_Segments", scloudObjects.size());
                        metaData.put(SCLOUD_METADATA_THUMBNAIL, encodedThumbnail);
                        metaData.put(SCLOUD_ATTACHMENT_SHA256, Utilities.hash(getContentResolver().openInputStream(file), "SHA256"));

                        onMetaDataAvailable(metaData);

                        return createEncryptAndSaveSCloudObject(getTableOfContents(scloudObjects).toString().getBytes("UTF-8"), metaData);
                    } catch (Exception exception) {
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
            boolean downloadSCloudObjectData(String locator) {
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

            @Nullable
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

            private boolean createDownloadAndSaveScloudObjects(final List<SCloudObject> tocScloudObjects) {
                long startTime = 0;
                if (LOG_TRANSFER_TIME) {
                    startTime = SystemClock.elapsedRealtime();
                }

                boolean fullyDownloaded = true;
                ArrayList<Future<Boolean>> futures = new ArrayList<>(tocScloudObjects.size());

                // Parallelized loop
                for(int i = 0; i < tocScloudObjects.size(); i++) {
                    final int index = i;
                    Callable<Boolean> callable = new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return createDownloadAndSaveScloudObject(tocScloudObjects.get(index).getLocator().toString()) != null;
                        }
                    };

                    Future<Boolean> future = sParallelTransferExecutor.submit(callable);
                    futures.add(future);
                }

                // Block until all futures have finished
                for (int i = 0; i < futures.size(); i++) {
                    try {
                        fullyDownloaded &= futures.get(i).get();
                        onProgressUpdate(R.string.downloading, i + 1, tocScloudObjects.size() );
                    } catch (Exception e) {
                        e.printStackTrace();
                        fullyDownloaded = false;
                    }
                }

                if (LOG_TRANSFER_TIME) {
                    long duration = SystemClock.elapsedRealtime() - startTime;
                    Log.d(TAG, String.format(Locale.US, "Finished downloading %d chunks in %d ms",
                            tocScloudObjects.size(), duration));
                }

                return fullyDownloaded;
            }

            private class DownloadProcessor {
                boolean mayAlreadyHaveFiles() {
                    AttachmentState attachmentState = getAttachmentState();

                    return attachmentState != null &&
                            (attachmentState.equals(AttachmentState.UPLOADED) || attachmentState.equals(AttachmentState.DOWNLOADED));
                }

                boolean shouldProcess() {
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

                    String tocScloudLocator;
                    String tocScloudKey;

                    if(!attachment.has(SCLOUD_ATTACHMENT_CLOUD_URL) || !attachment.has(SCLOUD_ATTACHMENT_CLOUD_KEY)) {
                        return null;
                    }

                    try {
                        tocScloudLocator = attachment.getString(SCLOUD_ATTACHMENT_CLOUD_URL);
                        tocScloudKey = attachment.getString(SCLOUD_ATTACHMENT_CLOUD_KEY);
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
                    if (mayAlreadyHaveFiles()) {
                        JSONObject metaData = getMetaData();

                        if(metaData != null) {
                            String exportedFileName = metaData.optString(SCLOUD_METATA_EXPORTED_FILENAME);
                            String fileName = metaData.optString(SCLOUD_METADATA_FILENAME);
                            String hash = metaData.optString(SCLOUD_ATTACHMENT_SHA256);

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

                    String tocScloudLocator;
                    String tocScloudKey;

                    if(!attachment.has(SCLOUD_ATTACHMENT_CLOUD_URL) || !attachment.has(SCLOUD_ATTACHMENT_CLOUD_KEY)) {
                        return null;
                    }

                    try {
                        tocScloudLocator = attachment.getString(SCLOUD_ATTACHMENT_CLOUD_URL);
                        tocScloudKey = attachment.getString(SCLOUD_ATTACHMENT_CLOUD_KEY);
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
            return DEFAULT_CHUNK_SIZE;
        }

        private byte[] getEncryptionContext() {
            return mEncryptionContext != null ? mEncryptionContext : null;
        }

        private void setEncryptionContext() {
            this.mEncryptionContext = CryptoUtil.randomBytes(16);
        }

        @Nullable
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

            long cloudRef = ZinaNative.cloudDecryptNew(IOUtils.toByteArray(scloudKey), code);

            ZinaNative.cloudDecryptNext(cloudRef, scloudObjectData);

            byte[] dataBlob = ZinaNative.cloudGetDecryptedData(cloudRef);
            byte[] metaDataBlob = ZinaNative.cloudGetDecryptedMetaData(cloudRef);

            ZinaNative.cloudFree(cloudRef);

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
            } catch (JSONException | NullPointerException exception) {
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
            if (scloudObjects == null) {
                return;
            }

            boolean showDecryptingProgress = false;
            if(scloudObjects.size() > 10) {
                showDecryptingProgress = true;
            }

            AttachmentUtils.removeAttachment(mPartner, mMessageId, true, mContext);

            BufferedOutputStream bos = null;

            File attachment = AttachmentUtils.getFile(mMessageId, mContext);
            if (attachment == null) {
                return;
            }

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
            } catch(IOException | NullPointerException exception) {
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
            return Util.getDbObjectRepository(mPartner, mMessageId);
        }

        private void removeMessage() {
            Util.removeMessage(mPartner, mMessageId);
        }

        private JSONObject getAttachment() {
            JSONObject result = null;
            if (!TextUtils.isEmpty(mAttachmentInfo)) {
                result = Util.getAttachment(mAttachmentInfo);
            }
            if (result == null) {
                result = Util.getAttachment(mPartner, mMessageId, mContext);
            }

            return result;
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

        private void onProgressStart(int labelResourceId) {
            onProgressUpdate(labelResourceId, 0, 100);
        }

        private void onProgressUpdate(int labelResourceId, int part, int total) {
            Intent intent = Action.PROGRESS.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            Extra.PROGRESS.to(intent, (int) Math.ceil(100 * ((double) part / total)));
            Extra.TEXT.to(intent, labelResourceId);

            MessagingBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        private void onProgressFinish(int labelResourceId) {
            onProgressCancel(labelResourceId);
        }

        private void onProgressCancel(int labelResourceId) {
            onProgressUpdate(labelResourceId, 100, 100);
        }

        private void sendTocMessage() {
            ConversationRepository conversations = ZinaMessaging.getInstance().getConversations();

            Conversation conversation = conversations.findByPartner(mPartner);
            if (conversation == null) {
                return;
            }
            final EventRepository events = conversations.historyOf(conversation);
            final Event event = events.findById(mMessageId);
            if (event instanceof OutgoingMessage) {
                final OutgoingMessage message = (OutgoingMessage) event;
                final boolean isGroup = conversation.getPartner().isGroup();

                JSONObject attributeJson = new JSONObject();

                try {
                    if (!isGroup) {
                        // "request_receipt" - currently always true for non-group messages
                        attributeJson.put(ATTRIBUTE_READ_RECEIPT, true);
                    }

                    if (conversation.hasBurnNotice()) {
                        // use message's burn time as conversation can be updated already
                        attributeJson.put(ATTRIBUTE_SHRED_AFTER, message.getBurnNotice());
                    }
                } catch (JSONException exception) {
                    Log.e(TAG, "Send ToC Message JSON exception (ignoring)", exception);
                }

                if (attributeJson.length() > 0) {
                    message.setAttributes(attributeJson.toString());
                }

                if (conversation.isLocationEnabled()) {
                    if (Looper.myLooper() == null) {
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
        }

        private void onTocAvailable(String tocScloudLocator, String tocScloudKey) {
            ConversationRepository conversations = ZinaMessaging.getInstance().getConversations();

            Conversation conversation = conversations.findByPartner(mPartner);
            if (conversation == null) {
                return;
            }
            final EventRepository events = conversations.historyOf(conversation);

            final Event event = events.findById(mMessageId);
            if (event != null) {

                JSONObject attachmentJson = new JSONObject();
                try {
                    attachmentJson.put(SCLOUD_ATTACHMENT_CLOUD_URL, tocScloudLocator);
                    attachmentJson.put(SCLOUD_ATTACHMENT_CLOUD_KEY, tocScloudKey);
                    JSONObject attachmentMetaDataJson = new JSONObject();

                    if (event instanceof OutgoingMessage && ((OutgoingMessage) event).hasMetaData()) {
                        attachmentMetaDataJson = new JSONObject(((OutgoingMessage) event).getMetaData());
                    }
                    else if (event instanceof InfoEvent) {
                        attachmentMetaDataJson =
                                new JSONObject(event.getAttachment() == null ? "" : event.getAttachment());
                    }

                    try {
                        String mimeType = attachmentMetaDataJson.getString(SCLOUD_METADATA_MIMETYPE);
                        String presentationType = attachmentMetaDataJson.optString(SCLOUD_ATTACHMENT_PRESENTATION_TYPE, null);
                        String exportedFilename = attachmentMetaDataJson.optString(SCLOUD_METATA_EXPORTED_FILENAME);
                        String fileName = attachmentMetaDataJson.getString(SCLOUD_METADATA_FILENAME);
                        String displayName = attachmentMetaDataJson.optString(SCLOUD_METATA_DISPLAYNAME);
                        String hash = attachmentMetaDataJson.optString(SCLOUD_ATTACHMENT_SHA256);
                        long size = attachmentMetaDataJson.optLong(SCLOUD_METADATA_FILESIZE, -1);

                        attachmentJson.put(SCLOUD_ATTACHMENT_MIMETYPE, mimeType);
                        if (presentationType != null) {
                            attachmentJson.put(SCLOUD_ATTACHMENT_PRESENTATION_TYPE, presentationType);
                        }
                        attachmentJson.put(SCLOUD_ATTACHMENT_FILENAME, fileName);
                        if (!TextUtils.isEmpty(exportedFilename)) {
                            attachmentJson.put(SCLOUD_ATTACHMENT_EXPORTED_FILENAME, exportedFilename);
                        }
                        if (!TextUtils.isEmpty(displayName)) {
                            attachmentJson.put(SCLOUD_ATTACHMENT_DISPLAYNAME, displayName);
                        }
                        if (!TextUtils.isEmpty(hash)) {
                            attachmentJson.put(SCLOUD_ATTACHMENT_SHA256, hash);
                        }
                        if (size >= 0) {
                            attachmentJson.put(SCLOUD_ATTACHMENT_FILESIZE, size);
                        }
                    } catch (JSONException exception) {
                        Log.e(TAG, "SCloud TOC attachment JSON exception (rare)", exception);
                    }
                } catch (JSONException exception) {
                    Log.e(TAG, "SCloud ToC message exception (rare)", exception);
                    return;
                }

                if (attachmentJson.length() > 0) {
                    event.setAttachment(attachmentJson.toString());
                }

                if (event instanceof OutgoingMessage) {
                    ((OutgoingMessage) event).setState(MessageStates.COMPOSED);
                }

                events.save(event);

                // notify attachment event receiver about TOC availability
                Intent intent = Action.UPLOAD.intent();
                if (mExtras != null) {
                    intent.putExtras(mExtras);
                }
                Extra.PARTNER.to(intent, mPartner);
                Extra.ID.to(intent, mMessageId);
                intent.putExtra(ATTACHMENT_INFO, attachmentJson.toString());
                MessagingBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            }
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
            ConversationRepository conversations = ZinaMessaging.getInstance().getConversations();

            Conversation conversation = conversations.findByPartner(mPartner);
            if (conversation == null) {
                return;
            }
            EventRepository events = conversations.historyOf(conversation);
            Event event = events.findById(mMessageId);
            if (event instanceof Message) {
                Message message = (Message) event;

                if (metaData != null) {
                    message.setMetaData(metaData);
                    message.setText(mContext.getString(R.string.attachment));
                }

                events.save(message);

                MessageUtils.notifyConversationUpdated(mContext, mPartner,
                        true, ZinaMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE, message.getId());
            }
            else if (event instanceof InfoEvent) {
                if (metaData != null) {
                    // save attachment metadata in info event
                    event.setAttachment(metaData);
                    events.save(event);
                }
            }
        }

        private void onAttachmentAvailable(File attachment, byte[] metaData) {
            Intent intent = Action.RECEIVE_ATTACHMENT.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            if (mExtras != null) {
                intent.putExtras(mExtras);
            }

            try {
                JSONObject metaDataJsonObject = new JSONObject(IOUtils.toString(metaData));

                if (metaDataJsonObject.has(SCLOUD_METADATA_THUMBNAIL)) {
                    metaDataJsonObject.remove(SCLOUD_METADATA_THUMBNAIL);
                }

                if (!metaDataJsonObject.has(SCLOUD_ATTACHMENT_SHA256)) {
                    metaDataJsonObject.put(SCLOUD_ATTACHMENT_SHA256, Utilities.hash(new FileInputStream(attachment), "SHA256"));
                }

                Extra.TEXT.to(intent, metaDataJsonObject.toString());
            } catch (Throwable ignore) {
                Log.e(TAG, "Attachment available exception (ignoring)", ignore);
            }

            MessagingBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        private void onAttachmentAvailable() {
            Intent intent = Action.RECEIVE_ATTACHMENT.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            Extra.EXPORTED.to(intent, true);
            if (mExtras != null) {
                intent.putExtras(mExtras);
            }

            MessagingBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        private void onError(int errorResourceId) {
            Intent intent = Action.ERROR.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            Extra.TEXT.to(intent, errorResourceId);
            if (mExtras != null) {
                intent.putExtras(mExtras);
            }

            MessagingBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        private void onCancel(int cancelResourceId) {
            Intent intent = Action.CANCEL.intent();
            Extra.PARTNER.to(intent, mPartner);
            Extra.ID.to(intent, mMessageId);
            Extra.TEXT.to(intent, cancelResourceId);
            if (mExtras != null) {
                intent.putExtras(mExtras);
            }

            MessagingBroadcastManager.getInstance(mContext).sendBroadcast(intent);
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

            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
            boolean axoRegistered = axoMessaging.isRegistered();
            if (!axoRegistered) {
                axoMessaging.addStateChangeListener(this);
            } else {
//                SCloudRecoveryRunnable scloudRecoveryRunnable = new SCloudRecoveryRunnable();
//                mRecoveryHandler.post(scloudRecoveryRunnable);
            }
        }

        @Override
        public void axoRegistrationStateChange(boolean registered) {
            if (registered) {
                ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
                axoMessaging.removeStateChangeListener(this);

                runRecoveryHandler();
            }
        }
    }

    public class SCloudOutputStream extends OutputStream {
        public void onChunk(byte[] chunk) {}

        @Override
        public void write(@NonNull byte [] buffer) throws IOException {
            onChunk(buffer);
        }

        @Override
        public void write(@NonNull byte [] buffer, int offset, int count) throws IOException {
            byte[] segment = count < buffer.length ? new byte [count] : buffer;

            if(segment != buffer) {
                System.arraycopy(buffer, offset, segment, 0, segment.length);
            }

            write(segment);
        }

        @Override
        public void write(int oneByte) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    public static class HttpResponse<T> {
        int responseCode;
        public T response;
        public String error;

        HttpResponse(int responseCode, T response, String error) {
            this.responseCode = responseCode;
            this.response = response;
            this.error = error;
        }

        boolean hasError() {
            return this.error != null;
        }
    }

    public static class DB {
        public static final String TAG = "SCloud DB";

        public static AttachmentState getAttachmentState(String messageId, String partner) {
            return fromState(ZinaNative.loadAttachmentStatus(IOUtils.encode(messageId), IOUtils.encode(partner), new int[1]));
        }

        static void setAttachmentState(String messageId, String partner, AttachmentState attachmentState) {
            setAttachmentState(messageId, partner, attachmentState.state);
        }

        static void setAttachmentState(String messageId, String partner, int attachmentState) {
            if (BuildConfig.DEBUG) {
                AttachmentState state = AttachmentState.fromState(attachmentState);
                Log.i(TAG, "Attachment state set - messageId:" + messageId + " partner:" + partner + " state:" +
                        (state == null ? "UNKNOWN" : state.name()));
            }

            ZinaNative.storeAttachmentStatus(IOUtils.encode(messageId), IOUtils.encode(partner), attachmentState);
        }

        static List<String[]> getAttachmentsByState(AttachmentState attachmentState) {
            return getAttachmentsByState(attachmentState.state);
        }

        static List<String[]> getAttachmentsByState(int attachmentState) {
            List<String[]> attachmentList = new ArrayList<>();

            int[] code = new int[1];
            String[] attachments = ZinaNative.loadMsgsIdsWithAttachmentStatus(attachmentState, code);

            if (attachments != null) {
                for (String attachment : attachments) {
                    if (TextUtils.isEmpty(attachment)) {
                        continue;
                    }
                    String[] attachmentSplit = attachment.split(":");
                    attachmentList.add(new String[]{attachmentSplit[0], attachmentSplit[1]});
                }
            } else {
                Log.e(TAG, "Could not load messages with specified attachment status: "
                        + attachmentState + ", result: " + code[0]);
            }

            return attachmentList;
        }

        public static void deleteAttachmentState(String messageId, String partner) {
            Log.i(TAG, "Attachment state deleted - messageId:" + messageId + " partner:" + partner);

            ZinaNative.deleteAttachmentStatus(IOUtils.encode(messageId), IOUtils.encode(partner));
        }
    }

    // TODO: Convert lazy for-loops into enhanced for-loops
    private class SCloudRecoveryRunnable implements Runnable {

        private final String TAG = SCloudRecoveryRunnable.class.getSimpleName();

        private boolean mFromBoot = false;
//        private boolean mFromNetworkChange = false;  This state is no used

        @Override
        public void run() {
            Log.i(TAG, "Running attachment recovery");

            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
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
                            Util.removeMessage(uploadProcessing.get(i)[1], uploadProcessing.get(i)[0]);

                            DB.deleteAttachmentState(uploadProcessing.get(i)[0], uploadProcessing.get(i)[1]);
                        }
                    }

                    if(!uploadProcessingError.isEmpty()) {
                        for(int i = 0; i < uploadProcessingError.size(); i++) {
                            Util.removeMessage(uploadProcessingError.get(i)[1], uploadProcessingError.get(i)[0]);

                            DB.deleteAttachmentState(uploadProcessingError.get(i)[0], uploadProcessingError.get(i)[1]);
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

//        private void setFromNetworkChange(boolean fromNetworkChange) { this.mFromNetworkChange = fromNetworkChange; }
    }


    private static class Broker {
        private static final String TAG = "SCloudBroker";

        private static final String BROKER_URI = "broker/";

        private static final SSLContext sslContext = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
        private static final SSLSocketFactory sslSocketFactory = sslContext != null ? sslContext.getSocketFactory() : null;

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

        static boolean isIgnoredError(String error) {
            return IGNORED_ERRORS.contains(error);

        }
    }

    private static class AmazonS3 {
        private static final String TAG = "SCloudAmazonS3";

        private static final String SCLOUD_URL = "https://s3.amazonaws.com/com.silentcircle.silenttext.scloud/";

        private static final String CONTENT_TYPE_SCLOUD = "application/x-scloud";

        private static final String HEADER_AMAZON_ACL = "x-amz-acl";
        private static final String HEADER_AMAZON_ACL_VALUE_PUBLIC_READ = "public-read";

        private static final SSLContext sslContext = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
        private static final SSLSocketFactory sslSocketFactory = sslContext != null ? sslContext.getSocketFactory() : null;

        static HttpResponse<String> presignedUrlUpload(String url, InputStream is) {
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

            InputStream in = null;
            FileOutputStream out = null;

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
                        in = urlConnection.getInputStream();
                        out = new FileOutputStream(outputFile);

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

                IOUtils.close(in, out);
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

        @NonNull
        private static String getSCloudURL(String locator ) {
            return AmazonS3.SCLOUD_URL +  locator;
        }
    }

    public static class Util {
        private static DbObjectRepository createDbObjectRepository(String partner, String messageId) {
            if(partner == null || messageId == null) {
                return null;
            }

            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
            ConversationRepository conversations = axoMessaging.getConversations();

            Conversation conversation = conversations.findByPartner(partner);

            if(conversation == null) {
                return null;
            }

            EventRepository events = conversations.historyOf(conversation);

            if(events == null) {
                return null;
            }

            Event event = new MessageStateEvent();
            event.setId(messageId);

            events.save(event);

            return (DbObjectRepository) events.objectsOf(event);
        }

        private static DbObjectRepository getDbObjectRepository(String partner, String messageId) {
            if(partner == null || messageId == null) {
                return null;
            }

            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();

            ConversationRepository conversations = axoMessaging.getConversations();

            Conversation conversation = conversations.findByPartner(partner);

            if(conversation == null) {
                return null;
            }

            EventRepository events = conversations.historyOf(conversation);

            if(events == null) {
                return null;
            }

            Event event = events.findById(messageId);

            if(event == null) {
                return null;
            }

            return (DbObjectRepository) events.objectsOf(event);
        }

        static ConversationRepository getConversationRepository() {
            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();

            return axoMessaging.getConversations();
        }

        static Conversation getConversation(String partner) {
            if(partner == null) {
                return null;
            }

            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();

            ConversationRepository conversations = axoMessaging.getConversations();

            Conversation conversation = conversations.findByPartner(partner);

            if(conversation == null) {
                return null;
            }

            return conversation;
        }

        private static void removeMessage(String partner, String messageId) {
            Log.i(TAG, "Removing message - partner: " + partner + " messageId: " + messageId);

            if(partner == null || messageId == null) {
                return;
            }

            ConversationRepository conversationRepository = Util.getConversationRepository();

            Conversation conversation = getConversation(partner);

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

        @Nullable
        private static JSONObject getAttachment(String partner, String messageId, Context context) {
            if(partner == null || messageId == null) {
                return null;
            }

            Event event = MessageUtils.getEventById(partner, messageId);

            try {
                return new JSONObject(event.getAttachment());
            } catch (JSONException | NullPointerException exception) {
                Log.e(TAG, "Message attachment JSON error (rare)", exception);
            }

            return null;
        }

        @Nullable
        private static JSONObject getAttachment(String attachmentInfo) {
            try {
                return new JSONObject(attachmentInfo);
            } catch (JSONException | NullPointerException exception) {
                Log.e(TAG, "Invalid attachment info: ", exception);
            }

            return null;
        }

        @Nullable
        private static JSONObject getMetaData(String partner, String messageId, Context context) {
            if(partner == null || messageId == null) {
                return null;
            }

            Event event = MessageUtils.getEventById(partner, messageId);

            if(event instanceof Message) {
                try {
                    return new JSONObject(((Message) event).getMetaData());
                } catch (JSONException exception) {
                    Log.e(TAG, "Message attachment JSON error (rare)", exception);
                }
            }

            return null;
        }

        @Nullable
        public static JSONObject getMetaData(Uri uri, Context context ) {
            if(uri == null) {
                return null;
            }

            JSONObject metaData = new JSONObject();

            String mimeType = AttachmentUtils.getMIMEType(context, uri);
            String presentationType = PresentationType.getType(uri);
            String fileName = AttachmentUtils.getFileName(context, uri);

            try {
                metaData.put(SCLOUD_METADATA_MEDIATYPE, UTI.fromMIMEType(mimeType) );
                metaData.put(SCLOUD_METADATA_MIMETYPE, AttachmentUtils.getMIMEType(context, uri));
                metaData.put(SCLOUD_ATTACHMENT_PRESENTATION_TYPE, presentationType);
                metaData.put(SCLOUD_METADATA_FILENAME, !TextUtils.isEmpty(fileName) ? fileName : "");
                metaData.put(SCLOUD_METADATA_FILESIZE, AttachmentUtils.getFileSize(context, uri));
            } catch( JSONException exception ) {
                Log.e(TAG, "SCloud meta data building JSON error (rare)", exception);
            }

            return metaData;
        }

        @NonNull
        private static String encodeThumbnail(Bitmap bitmap, String mimeType) {
            if(bitmap == null ) {
                return "";
            }

            ByteArrayOutputStream thumbnailBytes = new ByteArrayOutputStream();
            Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;
            if (MIME.isContact(mimeType) || MIME.isOctetStream(mimeType)
                    || MIME.isDoc(mimeType) || MIME.isPdf(mimeType) || MIME.isPpt(mimeType)
                    || MIME.isText(mimeType) || MIME.isXls(mimeType) || MIME.isAudio(mimeType)) {
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
