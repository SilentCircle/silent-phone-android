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
package com.silentcircle.messaging.activities;

import android.Manifest;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.silentcircle.common.util.ExplainPermissionDialog;
import com.silentcircle.common.util.FileUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.fragments.ContactViewerFragment;
import com.silentcircle.messaging.fragments.FileViewerFragment;
import com.silentcircle.messaging.fragments.ImageViewerFragment;
import com.silentcircle.messaging.fragments.MediaPlayerFragmentICS;
import com.silentcircle.messaging.fragments.PdfViewerFragment;
import com.silentcircle.messaging.fragments.TextViewerFragment;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.listener.OnConfirmNoRepeatListener;
import com.silentcircle.messaging.model.listener.OnProgressUpdateListener;
import com.silentcircle.messaging.services.SCloudCleanupService;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.messaging.views.UploadView;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivityInternal;
import com.silentcircle.silentphone2.passcode.AppLifecycleNotifierBaseActivity;
import com.silentcircle.silentphone2.util.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Activity which handles attachment viewing.
 */
public class FileViewerActivity extends AppLifecycleNotifierBaseActivity implements OnProgressUpdateListener,
        FileViewerFragment.Callback, ExplainPermissionDialog.AfterReading {

    private static final String TAG = FileViewerActivity.class.getSimpleName();

    @IntDef({ACTION_NONE, ACTION_VIEW, ACTION_SHARE, ACTION_SAVE, ACTION_BURN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewerAction {}

    public static final int ACTION_NONE = -1;
    public static final int ACTION_VIEW = 0;
    public static final int ACTION_SHARE = 1;
    public static final int ACTION_SAVE = 2;
    public static final int ACTION_BURN = 3;

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 2;

    // Identifiers and flags for permission handling
    public static final int PERMISSIONS_REQUEST_STORAGE = 1;
    private boolean mStoragePermissionAsked;
    private boolean mRationaleShown;

    private MessagingBroadcastReceiver mViewUpdater;

    class ExportFileTask extends AsyncTask<MenuItem, Void, MenuItem> {

        private final Context mContext;

        public ExportFileTask(final Context context) {
            mContext = context;
        }

        @Override
        protected MenuItem doInBackground(MenuItem... items) {
            copyToExternalStorage(mContext, mFile);
            return items[0];
        }

        @Override
        protected void onPostExecute(MenuItem item) {
            super.onPostExecute(item);
            if (item != null) {
                item.setEnabled(true);
            }
        }
    }

    protected class ProcessOptionsOnConfirmListener implements OnConfirmNoRepeatListener,
            DialogInterface.OnClickListener {

        private final MenuItem mItem;
        @ViewerAction private final int mAction;

        ProcessOptionsOnConfirmListener(MenuItem item) {
            mItem = item;
            mAction = ACTION_NONE;
        }

        ProcessOptionsOnConfirmListener(@ViewerAction int action) {
            mItem = null;
            mAction = action;
        }

        @Override
        public void onClick(DialogInterface arg0, int arg1) {
            // action cancelled, do nothing
        }

        @Override
        public void onConfirm(DialogInterface dialog, int which, boolean shouldNotShowAgain) {

            // save user's choice from shouldNotShowAgain in preferences
            MessagingPreferences.getInstance(getApplicationContext())
                    .setWarnWhenDecryptAttachment(!shouldNotShowAgain);

            if (mItem != null) {
                // process the item as intended
                processMenuItem(mItem);
            }
            else {
                processAction(mAction);
            }
        }

    }

    class ShareFileTask extends AsyncTask<MenuItem, Void, MenuItem> {

        @Override
        protected MenuItem doInBackground(MenuItem... args) {
            share(mFile, mMimeType);
            return args != null && args.length > 0 ? args[0] : null;
        }
    }

    public class ViewExportedFileTask extends AsyncTask<MenuItem, Void, MenuItem> {

        @Override
        protected MenuItem doInBackground(MenuItem... args) {
            File exportedFile = export(mFile);
            viewExportedFile(exportedFile, mMimeType);
            return args != null && args.length > 0 ? args[0] : null;
        }
    }

    private static final int EXTERNAL_ACTIVITY_REQUEST = R.id.export & 0xFFFF;

    private Toolbar mToolbar;

    protected File mFile;
    protected String mHash;
    protected String mMimeType;
    protected boolean mExporting;
    protected long mFileSize;
    protected String mFileName;
    protected String mDisplayName;

    private Uri mUri;

    private String mPartner;
    private String mMessageId;

    protected final List<AsyncTask<?, ?, ?>> tasks = new ArrayList<AsyncTask<?, ?, ?>>();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EXTERNAL_ACTIVITY_REQUEST) {
            if (resultCode == RESULT_OK) {
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Callback from ExplainPermissionDialog after user read the explanation.
     *
     * After we explained the facts and the user read the explanation ask for permission again.
     * This will eventually call onRequestPermissionsResult which will check the result.
     *
     * @param token The token from ExplainPermissionDialog#showExplanation() call
     * @param callerBundle the optional bundle from ExplainPermissionDialog#showExplanation() call
     */
    @Override
    public void explanationRead(final int token, final Bundle callerBundle) {
        switch (token) {
            case PERMISSIONS_REQUEST_STORAGE:
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, token);
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    checkStoragePermissions();  // If user denied access at first, re-check which displays the rationale
                }
                else {
                    onCreateAfterPermissions();
                }
            }
            break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utilities.setTheme(this);
        super.onCreate(savedInstanceState);

        ViewUtil.setBlockScreenshots(this);

        setContentView(R.layout.activity_file_viewer);
        restoreActionBar();

        Intent intent = getIntent();

        if (intent != null) {
            checkStoragePermissions();
        }
    }

    private void onCreateAfterPermissions() {
        Intent intent = getIntent();
        mUri = intent.getData();
        mFile = new File(mUri.getPath());
        mHash = Extra.ALIAS.from(intent);
        mMimeType = intent.getType();
        mPartner = Extra.PARTNER.from(intent);
        mMessageId = Extra.ID.from(intent);

        mFileName = Extra.TEXT.from(intent);
        if (TextUtils.isEmpty(mFileName)) {
            mFileName = mFile.getName();
        }
        mFileName = FileUtils.sanitizeFilename(mFileName);

        mDisplayName = Extra.DISPLAY_NAME.from(intent);
        if (!TextUtils.isEmpty(mDisplayName)) {
            setTitle(mDisplayName);
        } else {
            setTitle(mFileName);
        }

        // check text/x-vcard before generic text/...
        if (MIME.isContact(mMimeType)) {
            setContentFragment(ContactViewerFragment.create(mUri, mMimeType));
        } else if (MIME.isPdf(mMimeType)) {
            /*
             * Pdf viewing is supported only starting with Lollipop.
             *
             * Verify PDF mime type before generic text type.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setContentFragment(PdfViewerFragment.create(mUri, mMimeType));
            }
            else {
                // avoid handling PDF as plain text
                Log.i(TAG, "PDF view is supported on only on Lollipop and later.");
                setContentFragment(FileViewerFragment.create(mUri, mMimeType));
            }
        } else if (MIME.isText(mMimeType)) {
            setContentFragment(TextViewerFragment.create(mUri, mMimeType));
        } else if (MIME.isImage(mMimeType)) {
            setContentFragment(ImageViewerFragment.create(mUri, mMimeType));
        } else if (MIME.isVideo(mMimeType)) {
            setContentFragment(createMediaPlayerFragment(mUri, mMimeType));
        } else if (MIME.isAudio(mMimeType)) {
            setContentFragment(createMediaPlayerFragment(mUri, mMimeType));
        } else {
            setContentFragment(FileViewerFragment.create(mUri, mMimeType));
        }
    }

    private void checkStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (mStoragePermissionAsked && mRationaleShown) {
                finish();
            }
            else if (!mStoragePermissionAsked) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_STORAGE);
                mStoragePermissionAsked = true;     // Avoid a possible third, ..., permission request if user set "don't ask anymore"
            }
            else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) && !mRationaleShown) {
                ExplainPermissionDialog.showExplanation(this, PERMISSIONS_REQUEST_STORAGE,
                        getString(R.string.permission_storage_title), getString(R.string.permission_storage_explanation), null);
                mRationaleShown = true;
            }
            else {
                finish();
            }
        }
        else {
            onCreateAfterPermissions();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        registerViewUpdater();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterMessagingReceiver(mViewUpdater);
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearTasks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.file_view_player, menu);

        boolean exported = AttachmentUtils.isExported(mFileName, mHash);

        menu.findItem(R.id.view).setVisible(AttachmentUtils.resolves(getPackageManager(), Intent.ACTION_VIEW, mUri, mMimeType));
        menu.findItem(R.id.share).setVisible(AttachmentUtils.resolves(getPackageManager(), Intent.ACTION_SEND, mUri, mMimeType));
        menu.findItem(R.id.save).setVisible(!exported);
        menu.findItem(R.id.burn).setVisible(exported);

        ViewUtil.tintMenuIcons(this, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onError(Uri fileURI, String fileMimeType) {
        askUserWhatToDoWithThisFile(fileURI, fileMimeType);
    }

    @Override
    public void viewWithExternalApplication() {
        if (verifyUserOKToExport(FileViewerActivity.ACTION_VIEW)) {
            processAction(FileViewerActivity.ACTION_VIEW);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save:
            case R.id.view:
            case R.id.share:
                if (!verifyUserOKToExport(item)) {
                    return true;
                }
                break;
        }
        return processMenuItem(item);
    }

    @Override
    public void onProgressUpdate(long progress) {

        final int percent = (int) Math.ceil(100.0 * progress / mFileSize);

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                UploadView view = (UploadView) findViewById(R.id.export);

                if (view == null) {
                    return;
                }

                if (mExporting && mFileSize > AttachmentUtils.FILE_SIZE_MEDIUM) {
                    view.setProgress(R.string.exporting, percent, null);
                    view.setVisibility(View.VISIBLE);
                } else {
                    view.setVisibility(View.GONE);
                }

            }

        });
    }

    @Override
    public void onBackPressed() {
        if (!onExitFragment()) {
            super.onBackPressed();
        }
    }

    protected void askUserWhatToDoWithThisFile(Uri fileURI, String fileMimeType) {
        setContentFragment(FileViewerFragment.create(fileURI, fileMimeType));
    }

    protected File copyToExternalStorage(Context context, File file) {
        return copyToExternalStorage(context, file, this);
    }

    @Nullable
    protected File copyToExternalStorage(Context context, File file,
            OnProgressUpdateListener onProgressUpdate) {
        File externalFile = AttachmentUtils.getExternalStorageFile(mFileName);

        if (AttachmentUtils.isExported(mFileName, mHash)) {
            return externalFile;
        }

        if (externalFile == null) {
            showErrorToast(mFileName);
            return null;
        }

        DecimalFormat nf3 = new DecimalFormat("#000");
        while (externalFile.exists()) {
            int i = mFileName.contains(".") ? mFileName.lastIndexOf('.') : mFileName.length();
            int suffix = new Random().nextInt(1000 - 1) + 1;
            mFileName = mFileName.substring(0, i) + " - " + nf3.format(suffix) + mFileName.substring(i);

            externalFile = AttachmentUtils.getExternalStorageFile(mFileName);
        }

        AttachmentUtils.setExportedFilename(this, mPartner, mMessageId, mFileName);

        mFileSize = AttachmentUtils.getFileSize(this, Uri.fromFile(file));
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(file);
            out = new FileOutputStream(externalFile, false);
            mExporting = true;
            IOUtils.pipe(in, out, onProgressUpdate);
            mExporting = false;

            AttachmentUtils.makePublic(context, externalFile);
            // setVisibleIf(false, R.id.export);

            showExportToast(externalFile.getAbsolutePath());

            invalidateOptionsMenu();
            return externalFile;
        } catch (IOException exception) {
            IOUtils.close(in, out);
            externalFile.delete();
        } finally {
            IOUtils.close(in, out);
        }
        return null;
    }

    protected void showExportToast(final String exportPath) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (TextUtils.isEmpty(mDisplayName)) {
                    setTitle(mFileName);
                }

                Toast.makeText(FileViewerActivity.this,
                        getString(R.string.toast_saved_to, exportPath), Toast.LENGTH_LONG).show();
            }
        });

    }

    protected void showErrorToast(final String fileName) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(FileViewerActivity.this,
                        getString(R.string.toast_failed_to_export_file,
                                fileName == null ? "" : fileName), Toast.LENGTH_LONG).show();
            }
        });
    }

    protected File export(File originalFile) {
        return copyToExternalStorage(getApplicationContext(), originalFile);
    }

    protected void viewExportedFile(final File externalFile, String fileMimeType) {

        if (externalFile != null && externalFile.exists()) {

            Uri data = Uri.fromFile(externalFile);

            Intent viewer = new Intent(Intent.ACTION_VIEW, data);
            if (launch(R.string.messaging_file_viewer_view_with, externalFile.getName(), viewer, fileMimeType)) {
                finish();
            }
            else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(FileViewerActivity.this,
                                getString(R.string.unable_to_display_file, externalFile.getAbsolutePath()),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
        else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(FileViewerActivity.this,
                            getString(R.string.toast_failed_to_export_file,
                                    TextUtils.isEmpty(mFileName) ? "" : mFileName),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    protected boolean processMenuItem(MenuItem item) {
        boolean consumed = false;
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                consumed = true;
                break;
            case R.id.save:
                item.setEnabled(false);
                tasks.add(AsyncUtils.execute(new ExportFileTask(getApplicationContext()), item));
                consumed = true;
                break;
            case R.id.burn:
                burn(mFileName);
                consumed = true;
                break;
            case R.id.view:
                tasks.add(AsyncUtils.execute(new ViewExportedFileTask(), item));
                consumed = true;
                break;
            case R.id.share:
                // Constants.mIsSharePhoto = true;
                tasks.add(AsyncUtils.execute(new ShareFileTask(), item));
                consumed = true;
                break;
            default:
                Log.e(TAG, "Unknown menu item pressed, id: " + Integer.toHexString(item.getItemId()));
                break;
        }
        return consumed ? consumed : super.onOptionsItemSelected(item);
    }

    protected void processAction(@ViewerAction int action) {
        switch (action) {
            case ACTION_SAVE:
                tasks.add(AsyncUtils.execute(new ExportFileTask(getApplicationContext())));
                break;
            case ACTION_BURN:
                burn(mFileName);
                break;
            case ACTION_VIEW:
                tasks.add(AsyncUtils.execute(new ViewExportedFileTask()));
                break;
            case ACTION_SHARE:
                // Constants.mIsSharePhoto = true;
                tasks.add(AsyncUtils.execute(new ShareFileTask()));
                break;
            default:
                Log.e(TAG, "Unknown action requested, action: " + action);
                break;
        }
    }

    protected void setContentFragment(Fragment fragment) {
        // initializeErrorView();
        FragmentManager manager = getFragmentManager();
        Fragment existing = manager.findFragmentById(R.id.content);
        if (existing == null) {
            manager.beginTransaction().add(R.id.content, fragment).commit();
        } else {
            if (!existing.getClass().equals(fragment.getClass())) {
                manager.beginTransaction().replace(R.id.content, fragment).commit();
            }
        }
    }

    @Nullable
    protected Fragment getContentFragment() {
        Fragment result = null;
        FragmentManager manager = getFragmentManager();
        if (manager != null) {
            result = manager.findFragmentById(R.id.content);
        }
        return result;
    }

    protected void share(File shareFile, String shareMimeType) {
        File externalFile = copyToExternalStorage(getApplicationContext(), shareFile);
        if (externalFile == null || !externalFile.exists()) {
            return;
        }
        Uri data = Uri.fromFile(externalFile);
        Intent sender = new Intent(Intent.ACTION_SEND);
        sender.putExtra(Intent.EXTRA_STREAM, data);
        launch(R.string.share, externalFile.getName(), sender, shareMimeType);
    }

    /*
     * Ask user whether it is ok to export from encrypted storage.
     *
     * Present option to save the choice for future exports.
     */
    protected boolean verifyUserOKToExport(final MenuItem item) {
        boolean result = true;
        boolean warnWhenDecryptAttachment = MessagingPreferences.getInstance(getApplicationContext())
                .getWarnWhenDecryptAttachment();

        if (warnWhenDecryptAttachment) {
            ProcessOptionsOnConfirmListener listener = new ProcessOptionsOnConfirmListener(item);

            new LaunchConfirmDialogOnClick(R.string.are_you_sure,
                    R.string.warning_media_to_be_exported, listener)
                    .show(FileViewerActivity.this, true);

            result = false;
        }
        return result;
    }

    protected boolean verifyUserOKToExport(@ViewerAction final int action) {
        boolean result = true;
        boolean warnWhenDecryptAttachment = MessagingPreferences.getInstance(getApplicationContext())
                .getWarnWhenDecryptAttachment();

        if (warnWhenDecryptAttachment) {
            ProcessOptionsOnConfirmListener listener = new ProcessOptionsOnConfirmListener(action);

            new LaunchConfirmDialogOnClick(R.string.are_you_sure,
                    R.string.warning_media_to_be_exported, listener)
                    .show(FileViewerActivity.this, true);

            result = false;
        }
        return result;
    }

    protected void restoreActionBar() {
        mToolbar = (Toolbar) findViewById(R.id.file_viewer_toolbar);
        setSupportActionBar(mToolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
    }

    protected void clearTasks() {
        while (!tasks.isEmpty()) {
            tasks.get(0).cancel(true);
            tasks.remove(0);
        }
    }

    private boolean startExternalActivity(Intent intent, int chooserTitleID, Object... chooserTitleArgs) {
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(Intent.createChooser(intent, getString(chooserTitleID, chooserTitleArgs)), EXTERNAL_ACTIVITY_REQUEST);
            return true;
        }
        return false;
    }

    private static Fragment createMediaPlayerFragment(Uri uri, String mimeType) {
        return MediaPlayerFragmentICS.create(uri, mimeType);
    }

    private boolean launch(int labelResourceID, String filename, Intent intent, String type) {
        intent.setDataAndType(intent.getData(), type);
        return startExternalActivity(intent, labelResourceID, filename);
    }

    private void burn(String fileName) {
        final File externalFile = AttachmentUtils.getExternalStorageFile(fileName);
        if (externalFile != null && externalFile.exists()) {
            this.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(FileViewerActivity.this, getString(R.string.toast_deleted_from, externalFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
                }
            });

            externalFile.delete();

            /* allow local version of file to be accessed but this throws off message metadata */
            /*
            mUri = Uri.fromFile(AttachmentUtils.getFile(mMessageId, FileViewerActivity.this));
            mFile = new File(mUri.getPath());
             */

            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onDestroy() {
        if(!TextUtils.isEmpty(mPartner) && !TextUtils.isEmpty(mMessageId)) {
            Intent cleanupIntent = Action.PURGE_ATTACHMENTS.intent(this, SCloudCleanupService.class);
            cleanupIntent.putExtra("KEEP_STATUS", true);
            Extra.PARTNER.to(cleanupIntent, mPartner);
            Extra.ID.to(cleanupIntent, mMessageId);
            startService(cleanupIntent);
        }

        super.onDestroy();
    }

    private boolean onExitFragment() {
        boolean consumed = false;
        Fragment fragment = getContentFragment();
        if (fragment instanceof FileViewerFragment) {
            consumed = ((FileViewerFragment) fragment).onExitView();
        }
        return consumed;
    }

    private void registerViewUpdater() {

        mViewUpdater = new MessagingBroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String partner = intent.getStringExtra(Extra.PARTNER.getName());
                boolean forCurrentConversation = TextUtils.equals(partner, mPartner);
                if (forCurrentConversation && Action.CLOSE_CONVERSATION.equals(Action.from(intent))) {
                    Toast.makeText(FileViewerActivity.this,
                            getString(R.string.group_messaging_leaving_group_unknown),
                            Toast.LENGTH_SHORT).show();

                    Log.w(TAG, "Group conversation not valid, exiting.");
                    Intent exitIntent = new Intent(FileViewerActivity.this, DialerActivityInternal.class);
                    exitIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    FileViewerActivity.this.startActivity(exitIntent);
                }
            }
        };

        // messaging events
        IntentFilter filter = Action.filter(Action.CLOSE_CONVERSATION);
        registerMessagingReceiver(this, mViewUpdater, filter, MESSAGE_PRIORITY);
    }

    // TODO move to one class
    private void registerMessagingReceiver(@NonNull Context context,
            MessagingBroadcastReceiver receiver, IntentFilter filter, int priority) {
        filter.setPriority(priority);
        MessagingBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    private void unregisterMessagingReceiver(MessagingBroadcastReceiver receiver) {
        try {
            if (receiver != null) {
                MessagingBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister view update broadcast receiver.");
            e.printStackTrace();
        }
    }
}
