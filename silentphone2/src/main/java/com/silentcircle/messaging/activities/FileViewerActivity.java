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
package com.silentcircle.messaging.activities;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.silentcircle.messaging.fragments.ContactViewerFragment;
import com.silentcircle.messaging.fragments.FileViewerFragment;
import com.silentcircle.messaging.fragments.ImageViewerFragment;
import com.silentcircle.messaging.fragments.MediaPlayerFragmentICS;
import com.silentcircle.messaging.fragments.PdfViewerFragment;
import com.silentcircle.messaging.fragments.TextViewerFragment;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity which handles attachment viewing.
 */
public class FileViewerActivity extends ActionBarActivity implements OnProgressUpdateListener,
        FileViewerFragment.Callback {

    private static final String TAG = FileViewerActivity.class.getSimpleName();

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
            item.setEnabled(true);

            export(mFile);
        }
    }

    protected class ProcessOptionsOnConfirmListener implements OnConfirmNoRepeatListener,
            DialogInterface.OnClickListener {

        private final MenuItem item;

        ProcessOptionsOnConfirmListener(MenuItem item) {
            this.item = item;
        }

        @Override
        public void onClick(DialogInterface arg0, int arg1) {
            // action cancelled, do nothing
        }

        @Override
        public void onConfirm(Context context, boolean shouldNotShowAgain) {

            // save user's choice from shouldNotShowAgain in preferences
            MessagingPreferences.getInstance(getApplicationContext())
                    .setWarnWhenDecryptAttachment(!shouldNotShowAgain);

            // process the item as intended
            processMenuItem(item);
        }

    }

    class ShareFileTask extends AsyncTask<MenuItem, Void, MenuItem> {

        @Override
        protected MenuItem doInBackground(MenuItem... args) {
            share(mFile, mMimeType);
            return args[0];
        }
    }

    public class ViewExportedFileTask extends AsyncTask<MenuItem, Void, MenuItem> {

        @Override
        protected MenuItem doInBackground(MenuItem... args) {
            File exportedFile = export(mFile);
            viewExportedFile(exportedFile, mMimeType);
            return args[0];
        }
    }

    private static final int EXTERNAL_ACTIVITY_REQUEST = R.id.export & 0xFFFF;

    private Toolbar mToolbar;

    protected File mFile;
    protected String mMimeType;
    protected boolean mExporting;
    protected long mFileSize;
    protected String mFileName;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_viewer);
        restoreActionBar();

        Intent intent = getIntent();

        if (intent != null) {

            mUri = intent.getData();
            mFile = new File(mUri.getPath());
            mMimeType = intent.getType();
            mPartner = Extra.PARTNER.from(intent);
            mMessageId = Extra.ID.from(intent);

            mFileName = Extra.TEXT.from(intent);
            if (TextUtils.isEmpty(mFileName)) {
                mFileName = mFile.getName();
            }
            setTitle(mFileName);

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
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearTasks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.file_view_player, menu);

        boolean exported = AttachmentUtils.isExported(mFileName);

        menu.findItem(R.id.view).setVisible(AttachmentUtils.resolves(getPackageManager(), Intent.ACTION_VIEW, mUri, mMimeType));
        menu.findItem(R.id.share).setVisible(AttachmentUtils.resolves(getPackageManager(), Intent.ACTION_SEND, mUri, mMimeType));
        menu.findItem(R.id.save).setVisible(!exported);
        menu.findItem(R.id.burn).setVisible(exported);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onError(Uri fileURI, String fileMimeType) {
        askUserWhatToDoWithThisFile(fileURI, fileMimeType);
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

                if (mExporting && mFileSize > 0) {
                    view.setProgress(R.string.exporting, percent, null);
                    view.setVisibility(View.VISIBLE);
                } else {
                    view.setVisibility(View.GONE);
                }

            }

        });
    }

    protected void askUserWhatToDoWithThisFile(Uri fileURI, String fileMimeType) {
        setContentFragment(FileViewerFragment.create(fileURI, fileMimeType));
    }

    protected File copyToExternalStorage(Context context, File file) {
        return copyToExternalStorage(context, file, this);
    }

    protected File copyToExternalStorage(Context context, File file,
            OnProgressUpdateListener onProgressUpdate) {
        final File externalFile = AttachmentUtils.getExternalStorageFile(mFileName);
        if (externalFile == null || externalFile.exists()) {
            return externalFile;
        }
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

            this.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(FileViewerActivity.this, getString(R.string.toast_saved_to, externalFile.getAbsolutePath()), Toast.LENGTH_SHORT).show();
                }
            });

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

    protected File export(File originalFile) {
        File externalFile = copyToExternalStorage(getApplicationContext(), originalFile);
        return externalFile;
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
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
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
                Log.e(TAG, "Unknown menu item pressed, id: " +  Integer.toHexString(item.getItemId()));
                break;

        }
        return consumed ? consumed : super.onOptionsItemSelected(item);
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

    protected void restoreActionBar() {
        mToolbar = (Toolbar) findViewById(R.id.file_viewer_toolbar);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
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
        if (startExternalActivity(intent, labelResourceID, filename)) {
            return true;
        }
        return false;
    }

    private void burn(String fileName) {
        final File externalFile = AttachmentUtils.getExternalStorageFile(fileName);
        if (externalFile != null && externalFile.exists()) {
            this.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(FileViewerActivity.this, getString(R.string.toast_deleted_from, externalFile.getAbsolutePath()), Toast.LENGTH_SHORT).show();
                }
            });

            externalFile.delete();

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
}
