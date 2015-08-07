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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.silentcircle.contacts.providers.ScContactsProvider;
import com.silentcircle.keymngrsupport.KeyManagerSupport;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.vcard.VCardConfig;

import java.io.File;
import java.util.ArrayList;

/**
 * This activity checks, starts and controls data migration from existing SCA to new embedded SCA
 *
 */
public class MigrateByVCardActivity extends ActionBarActivity implements DialogInterface.OnClickListener, View.OnClickListener,
    Handler.Callback {
    private static final String LOG_TAG = "MigrateByVCardActivity";

    private static final String DO_MIGRATION = "do_sca_migration";
    private static final String ACTION_START_MIGRATION = "migration_initialize";
    private static final int KEY_MANAGER_READY = 7;

    private static final int EXPORT_STARTED = 1;
    private static final int IMPORT_STARTED = 2;
    private static final int WAIT_FOR_OPEN = 3;

    private static boolean mHasBpForwarder;

    private VCardService mService;

    // Used temporarily when asking users to confirm the file name
    private File mTargetFile;

    // String for storing error reason temporarily.
    private String mErrorReason;

    private TextView mMessage;

    private final Handler mHandler = new Handler(this);
    private int mState = WAIT_FOR_OPEN;

    /**
     * Name of the old SilentCircle Key Manager package, included in silent contacts
     */
    private static final String OLD_SKA_PKG = "com.silentcircle.contacts";

    private int mGoBack;

    private ServiceConnection exportConnection = new ServiceConnection() {
        @Override
        public synchronized void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((VCardService.MyBinder) binder).getService();
            final Uri fileUri = Uri.parse("file://" + mTargetFile);
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Send export request, uri: " + fileUri);

            final ExportRequest request = new ExportRequest(fileUri, "v30_generic", true);   // true -> this is a migration request

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

    private ServiceConnection importConnection = new ServiceConnection() {
        @Override
        public synchronized void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((VCardService.MyBinder) binder).getService();
            final Uri fileUri = Uri.parse("file://" + mTargetFile);
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Send import request, uri: " + fileUri);

            ArrayList<ImportRequest> requests = new ArrayList<>();

            final ImportRequest request = new ImportRequest(null, null, fileUri, "Migration import",
                    VCardConfig.VCARD_TYPE_V30_GENERIC,
                    null, ImportVCardActivity.VCARD_VERSION_V30, 0);
            requests.add(request);

            // The connection object will call finish().
            mMessage.setText(getString(R.string.migrate_import));
            mService.setReportHandler(mHandler);
            mState = IMPORT_STARTED;
            mService.handleImportRequest(requests, new MigrationExportListener(MigrateByVCardActivity.this));
            unbindService(importConnection);
            mService = null;
        }

        // Use synchronized since we don't want to call unbindAndFinish() just after this call.
        @Override
        public synchronized void onServiceDisconnected(ComponentName name) {
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "onServiceDisconnected() - import");
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        mTargetFile = new File(getFilesDir(), "migrate_contacts.vcf");
        setContentView(R.layout.activity_migrate);
        mMessage = (TextView) findViewById(R.id.migrate_message);
        mMessage.setText(getString(R.string.migrate_start));
        findViewById(R.id.migrate_go).setOnClickListener(this);
        findViewById(R.id.migrate_cancel).setOnClickListener(this);
        if (mHasBpForwarder)
            findViewById(R.id.migrate_explanation_2).setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        if (mGoBack > 1) {
            mGoBack = 0;
            super.onBackPressed();
        }
        mGoBack++;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case KEY_MANAGER_READY:
                if (resultCode != RESULT_OK) {
                    Log.w(LOG_TAG, getString(R.string.migrate_failed_old_km) + " (" + resultCode+ ")");
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit().putBoolean(DO_MIGRATION, false).apply();
                    finish();
                }
                else {
                    keyManagerChecked();
                }
                break;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (mState) {
            case EXPORT_STARTED:
                exportServiceDone();
                break;
            case IMPORT_STARTED:
                importServiceDone();
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
                try {
                    startActivityForResult(KeyManagerSupport.getKeyManagerReadyIntent(), KEY_MANAGER_READY);
                } catch (Exception ex) {
                    finish();
                }
                return;
            }
            mWaitCount++;
            mHandler.sendEmptyMessageDelayed(0, 100);
        }
        else {
            mMessage.setText(getString(R.string.migrate_not_ready));
            mGoBack = 2;
        }
    }


    // 2nd step in onCreate, after key manager is ready
    private void keyManagerChecked() {
        // on result key manager ready - check intent action

        String action = getIntent().getAction();
        if (ACTION_START_MIGRATION.equals(action)) {
            mMessage.setText(getString(R.string.migrate_export));
            startExportForMigration();
        }
        else
            finish();
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
        Intent serviceIntent = new Intent(this, VCardService.class);

        if (startService(serviceIntent) == null) {
            Log.e(LOG_TAG, "Failed to start vCard service for import");
            mErrorReason = getString(R.string.fail_reason_unknown);
            showNewDialog(R.id.dialog_fail_to_export_with_reason);
            return;
        }

        if (!bindService(serviceIntent, importConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(LOG_TAG, "Failed to connect to vCard service for export.");
            mErrorReason = getString(R.string.fail_reason_unknown);
            showNewDialog(R.id.dialog_fail_to_export_with_reason);
        }
        // Continued in onServiceConnected() on importConnection
    }

    private void importServiceDone() {
        mMessage.setText(getString(R.string.migrate_done));
        if (mTargetFile.exists())
            mTargetFile.delete();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(DO_MIGRATION, false).apply();

        if (!mHasBpForwarder) {
            // Trigger to uninstall old SCA
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + OLD_SKA_PKG));
            startActivity(intent);
        }
        else {
            ComponentName cn = new ComponentName("com.silentcircle.contacts",
                    "com.silentcircle.contacts.activities.ScContactsMainActivity");
            Intent intent = new Intent("com.silentcircle.blackphone.contact.FORWARDER");
            intent.setComponent(cn);
            startActivity(intent);
        }
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
            MigrateByVCardActivity parent = (MigrateByVCardActivity) getActivity();


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
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.migrate_cancel:
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putBoolean(DO_MIGRATION, false).apply();
                finish();
                break;

            case R.id.migrate_go:
                mHandler.sendEmptyMessageDelayed(0, 100);
                findViewById(R.id.migrate_buttons).setVisibility(View.GONE);
                findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                // Continued in keyManagerChecked() via delayed handling
                break;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "MigrateByVCardActivity#onClick() is called");
        finish();
    }

    public static void setBpForwarder(boolean hasForwarder) {
        mHasBpForwarder = hasForwarder;
    }

    public static Intent getStartMigrationIntent(Context ctx) {
        Intent intent = new Intent(ctx, MigrateByVCardActivity.class);
        intent.setAction(ACTION_START_MIGRATION);
        return intent;
    }

    public static boolean doMigration(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (!prefs.getBoolean(DO_MIGRATION, true))
            return false;

        if (!KeyManagerSupport.hasKeyManager(ctx.getPackageManager())) {
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "No Key Manager");
            prefs.edit().putBoolean(DO_MIGRATION, false).apply();
            return false;
        }
        if (!KeyManagerSupport.signaturesMatch(ctx.getPackageManager(), ctx.getPackageName())) {
            if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "Not the same signature");
            prefs.edit().putBoolean(DO_MIGRATION, false).apply();
            return false;
        }
        return true;
    }
}
