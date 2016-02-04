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

package com.silentcircle.contacts.vcard;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.widget.TextView;

import com.silentcircle.common.testing.NeededForTesting;
import com.silentcircle.contacts.providers.ScContactsProvider;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.io.File;

/**
 * This activity checks, starts and controls data migration from existing SCA to new embedded SCA
 *
 */
public class MigrateByVCardActivity extends ActionBarActivity implements DialogInterface.OnClickListener,
    Handler.Callback {
    private static final String LOG_TAG = "MigrateByVCardActivity";

    private static final String DO_MIGRATION = "do_nga_migration";
    private static final String ACTION_START_MIGRATION = "migration_initialize";

    private static final int EXPORT_STARTED = 1;
    private static final int WAIT_FOR_OPEN = 3;

    private VCardService mService;

    // Used temporarily when asking users to confirm the file name
    private File mTargetFile;

    // String for storing error reason temporarily.
    private String mErrorReason;

    private TextView mMessage;

    private final Handler mHandler = new Handler(this);
    private int mState = WAIT_FOR_OPEN;
    private boolean mSaveInstance;

    private ServiceConnection exportConnection = new ServiceConnection() {
        @Override
        public synchronized void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((VCardService.MyBinder) binder).getService();
            final Uri fileUri = Uri.parse("file://" + mTargetFile);
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Send export request, uri: " + fileUri);

            final ExportRequest request = new ExportRequest(fileUri, null, true); // true -> for migration

            mService.setReportHandler(mHandler);
            mState = EXPORT_STARTED;
            mService.handleExportRequest(request, new MigrationExportListener(MigrateByVCardActivity.this));
            unbindService(exportConnection);
            mService = null;
        }

        // Use synchronized since we don't want to call unbindAndFinish() just after this call.
        @Override
        public synchronized void onServiceDisconnected(ComponentName name) {
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "onServiceDisconnected() - export");
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final File file = Environment.getExternalStorageDirectory();
        mTargetFile = new File(file, "silent_contacts.vcf");
        setContentView(R.layout.activity_migrate);
        mMessage = (TextView) findViewById(R.id.migrate_message);
        mMessage.setText(getString(R.string.migrate_start));

        if (savedInstanceState != null) {
            mState = savedInstanceState.getInt("state");
            mSaveInstance = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mSaveInstance = false;
        mHandler.sendEmptyMessageDelayed(0, 100);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onSaveInstanceState (@NonNull Bundle outState) {
        outState.putInt("state", mState);
        mSaveInstance = true;
        super.onSaveInstanceState(outState);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mState = savedInstanceState.getInt("state");
        mSaveInstance = false;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (mState) {
            case EXPORT_STARTED:
                exportServiceDone();
                break;
            case WAIT_FOR_OPEN:
                waitForOpen();
                break;
        }
        return true;
    }

    private int mWaitCount;

    private void waitForOpen() {
        if (mWaitCount < 50) {
            if (ScContactsProvider.isWriteOpen()) {
                String action = getIntent().getAction();
                if (ACTION_START_MIGRATION.equals(action)) {
                    mMessage.setText(getString(R.string.migrate_export));
                    startExportForMigration();
                }
                else {
                    Log.e(LOG_TAG, "Action not known: " + getIntent().getAction());
                    finish();
                }
                return;
            }
            mWaitCount++;
            mHandler.sendEmptyMessageDelayed(0, 100);
        }
        else {
            mMessage.setText(getString(R.string.migrate_not_ready));
        }
    }

    // action_init: clear files, clear static data (if some), start service
    private void startExportForMigration() {
        if (mTargetFile.exists()) {
            mTargetFile.delete();
        }

        Intent serviceIntent = new Intent(this, VCardService.class);

        if (startService(serviceIntent) == null) {
            Log.e(LOG_TAG, "Failed to start vCard service for export");
            mErrorReason = getString(R.string.fail_reason_unknown);
            showNewDialog(R.id.dialog_fail_to_export_with_reason);
            return;
        }

        if (!bindService(serviceIntent, exportConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(LOG_TAG, "Failed to connect to vCard service for import.");
            mErrorReason = getString(R.string.fail_reason_unknown);
            showNewDialog(R.id.dialog_fail_to_export_with_reason);
        }
        // Continued in onServiceConnected() on exportConnection
    }

    private void exportServiceDone() {
        if (mTargetFile.length() < 10) {
            Log.w(LOG_TAG, "Empty contacts - no data copied.");
            importServiceDone();
            return;
        }
        if (!mSaveInstance)
            showNewDialog(R.id.dialog_import_to_contacts);
    }

    private void importVcardToContacts() {
        /* view vCards with ImportVCardActivity */
        Intent fileViewerIntent = new Intent(Intent.ACTION_VIEW);
        Uri data = Uri.fromFile(mTargetFile);
        fileViewerIntent.setDataAndType(data, MIME.TYPE_VCARD[1]);
        startActivity(fileViewerIntent);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(DO_MIGRATION, false).apply();

        finish();
    }

    private void importServiceDone() {
        mMessage.setText(getString(R.string.migrate_done));
        if (mTargetFile.exists())
            mTargetFile.delete();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(DO_MIGRATION, false).apply();

        finish();
    }

    private void showNewDialog(int resId) {
        NewDialog dialog = NewDialog.newInstance(resId);
        FragmentManager fragmentManager = getFragmentManager();
        dialog.show(fragmentManager, "ManageVCardActivity");
    }

    public static class NewDialog extends DialogFragment {

        public static NewDialog newInstance(int resId) {
            NewDialog frag = new NewDialog();
            frag.setCancelable(true);
            Bundle args = new Bundle();
            args.putInt("resId", resId);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("resId");
            final MigrateByVCardActivity parent = (MigrateByVCardActivity) getActivity();


            switch (id) {
                case R.id.dialog_fail_to_export_with_reason: {
                    return new AlertDialog.Builder(parent)
                            .setTitle(R.string.exporting_contact_failed_title)
                            .setMessage(getString(R.string.exporting_contact_failed_message,
                                    parent.mErrorReason != null ? parent.mErrorReason :
                                            getString(R.string.fail_reason_unknown)))
                            .setPositiveButton(android.R.string.ok, parent)
                            .create();
                }
                case R.id.dialog_import_to_contacts: {
                    return new AlertDialog.Builder(parent)
                            .setTitle(R.string.dialog_auto_import)
                            .setMessage(getString(R.string.dialog_auto_import_text, parent.mTargetFile.getName()))
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.importVcardToContacts();
                                }
                            })
                            .setNegativeButton(android.R.string.no, parent)
                            .create();
                }
            }
            return null;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            MigrateByVCardActivity parent = (MigrateByVCardActivity) getActivity();
            parent.finish();
        }

    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "MigrateByVCardActivity#onClick() is called");
        finish();
    }

    public static Intent getStartMigrationIntent(Context ctx) {
        Intent intent = new Intent(ctx, MigrateByVCardActivity.class);
        intent.setAction(ACTION_START_MIGRATION);
        return intent;
    }

    private final static String[] projection = {
            ScContactsContract.RawContacts._ID
    };

    private static boolean isContactsAvailable(Context ctx) {
        final Cursor cursor = ctx.getContentResolver().query(ScContactsContract.RawContacts.CONTENT_URI,
                projection, null, null, null);
        boolean retVal = false;
        if (cursor != null) {
            Log.d(LOG_TAG, "++++ Migration cursor counter: " + cursor.getCount());
            retVal = cursor.getCount() > 0;
            cursor.close();
        }
        return retVal;
    }

    @NeededForTesting
    public static void forceTrue(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().putBoolean(DO_MIGRATION, true).apply();
    }

    public static boolean doMigration(Context ctx) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (!prefs.getBoolean(DO_MIGRATION, true))
            return false;

        if (!isContactsAvailable(ctx)) {
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "No contacts data available");
            prefs.edit().putBoolean(DO_MIGRATION, false).apply();
            return false;
        }
        return true;
    }
}
