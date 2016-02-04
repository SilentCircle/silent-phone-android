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

/*
 * This  implementation is an edited version of original Android sources.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.contacts.vcard;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import com.silentcircle.silentphone2.R;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * The class letting users to import vCard. This includes the UI part for letting them select an Account and posssibly a file if
 * there's no Uri is given from its caller Activity.
 * 
 * Note that this Activity assumes that the instance is a "one-shot Activity", which will be finished (with the method
 * {@link android.app.Activity#finish()}) after the import and never reuse any Dialog in the instance. So this code is careless about the
 * management around managed dialogs stuffs (like how onCreateDialog() is used).
 */
public class ManageVCardActivity extends ActionBarActivity {
    private static final String LOG_TAG = "ManageVCard";

    private static final String SECURE_DIRECTORY_NAME = ".android_secure";


    private ProgressDialog mProgressDialogForScanVCard;

    private List<VCardFile> mAllVCardFileList;
    private VCardScanThread mVCardScanThread;

    private static class VCardFile {
        private final String mName;
        private final String mCanonicalPath;
        private final long mLastModified;

        public VCardFile(String name, String canonicalPath, long lastModified) {
            mName = name;
            mCanonicalPath = canonicalPath;
            mLastModified = lastModified;
        }

        public String getName() {
            return mName;
        }

        public String getCanonicalPath() {
            return mCanonicalPath;
        }

        public long getLastModified() {
            return mLastModified;
        }
    }

    // Runs on the UI thread.
    private class DialogDisplayer implements Runnable {
        private final int mResId;

        public DialogDisplayer(int resId) {
            mResId = resId;
        }

        @Override
        public void run() {
            if (!isFinishing()) {
                showNewDialog(mResId);
            }
        }
    }

    private class CancelListener implements OnClickListener, OnCancelListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private CancelListener mCancelListener = new CancelListener();

    private class VCardSelectedListener implements OnClickListener, DialogInterface.OnMultiChoiceClickListener {
        private Set<Integer> mSelectedIndexSet;

        public VCardSelectedListener(boolean multipleSelect) {
            if (multipleSelect) {
                mSelectedIndexSet = new HashSet<>();
            }
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (mSelectedIndexSet != null) {
                    final int size = mAllVCardFileList.size();

                    for (int i = 0; i < size; i++) {
                        if (mSelectedIndexSet.contains(i)) {
                            File file = new File(mAllVCardFileList.get(i).getCanonicalPath());
                            file.delete();
                        }
                    }
                }
                finish();
            }
            else if (which == DialogInterface.BUTTON_NEGATIVE) {
                finish();
            }
            else {
                // Some file is selected.
                if (mSelectedIndexSet != null) {
                    if (mSelectedIndexSet.contains(which)) {
                        mSelectedIndexSet.remove(which);
                    }
                    else {
                        mSelectedIndexSet.add(which);
                    }
                }
            }
        }

        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            if (mSelectedIndexSet == null || (mSelectedIndexSet.contains(which) == isChecked)) {
                Log.e(LOG_TAG,
                        String.format("Inconsistent state in index %d (%s)", which, mAllVCardFileList.get(which).getCanonicalPath()));
            }
            else {
                onClick(dialog, which);
            }
        }
    }

    /**
     * Thread scanning VCard from SDCard. After scanning, the dialog which lets a user select a vCard file is shown.
     */
    private class VCardScanThread extends Thread implements OnCancelListener, OnClickListener {
        private boolean mCanceled;
        private boolean mGotIOException;
        private File mRootDirectory;

        // To avoid recursive link.
        private Set<String> mCheckedPaths;
        private PowerManager.WakeLock mWakeLock;

        private class CanceledException extends Exception {
        }

        public VCardScanThread(File sdcardDirectory) {
            mCanceled = false;
            mGotIOException = false;
            mRootDirectory = sdcardDirectory;
            mCheckedPaths = new HashSet<>();
            PowerManager powerManager = (PowerManager) ManageVCardActivity.this.getSystemService(Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void run() {
            mAllVCardFileList = new Vector<>();
            try {
                mWakeLock.acquire();
                getVCardFileRecursively(mRootDirectory);
            }
            catch (CanceledException e) {
                mCanceled = true;
            }
            catch (IOException e) {
                mGotIOException = true;
            }
            finally {
                mWakeLock.release();
            }

            if (mCanceled) {
                mAllVCardFileList = null;
            }
            mProgressDialogForScanVCard.dismiss();
            mProgressDialogForScanVCard = null;

            if (mGotIOException) {
                runOnUiThread(new DialogDisplayer(R.id.dialog_io_exception));
            }
            else if (mCanceled) {
                finish();
            }
            else {
                int size = mAllVCardFileList.size();
                if (size == 0) {
                    runOnUiThread(new DialogDisplayer(R.id.dialog_vcard_not_found));
                }
                else {
                    runOnUiThread(new DialogDisplayer(R.id.dialog_select_multiple_vcard));
                }
            }
        }

        @SuppressLint("DefaultLocale")
        private void getVCardFileRecursively(File directory) throws CanceledException, IOException {
            if (mCanceled) {
                throw new CanceledException();
            }

            // e.g. secured directory may return null toward listFiles().
            final File[] files = directory.listFiles();
            if (files == null) {
                final String currentDirectoryPath = directory.getCanonicalPath();
                final String secureDirectoryPath = mRootDirectory.getCanonicalPath().concat(SECURE_DIRECTORY_NAME);
                if (!TextUtils.equals(currentDirectoryPath, secureDirectoryPath)) {
                    Log.w(LOG_TAG, "listFiles() returned null (directory: " + directory + ")");
                }
                return;
            }
            for (File file : directory.listFiles()) {
                if (mCanceled) {
                    throw new CanceledException();
                }
                String canonicalPath = file.getCanonicalPath();
                if (mCheckedPaths.contains(canonicalPath)) {
                    continue;
                }

                mCheckedPaths.add(canonicalPath);

                if (file.isDirectory()) {
                    getVCardFileRecursively(file);
                }
                else if (canonicalPath.toLowerCase().endsWith(".vcf") && file.canRead()) {
                    String fileName = file.getName();
                    VCardFile vcardFile = new VCardFile(fileName, canonicalPath, file.lastModified());
                    mAllVCardFileList.add(vcardFile);
                }
            }
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
            }
        }
    }

    private Dialog getVCardFileSelectDialog(boolean multipleSelect) {
        final int size = mAllVCardFileList.size();
        final VCardSelectedListener listener = new VCardSelectedListener(multipleSelect);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.select_vcard_title_remove)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, mCancelListener);

        CharSequence[] items = new CharSequence[size];
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < size; i++) {
            VCardFile vcardFile = mAllVCardFileList.get(i);
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            stringBuilder.append(vcardFile.getName());
            stringBuilder.append('\n');
            int indexToBeSpanned = stringBuilder.length();
            // Smaller date text looks better, since each file name becomes easier to read.
            // The value set to RelativeSizeSpan is arbitrary. You can change it to any other
            // value (but the value bigger than 1.0f would not make nice appearance :)
            stringBuilder.append("(").append(dateFormat.format(new Date(vcardFile.getLastModified()))).append(")");
            stringBuilder.setSpan(new RelativeSizeSpan(0.7f), indexToBeSpanned, stringBuilder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            items[i] = stringBuilder;
        }
        if (multipleSelect) {
            builder.setMultiChoiceItems(items, null, listener);
        }
        else {
            builder.setSingleChoiceItems(items, 0, listener);
        }
        return builder.create();
    }


    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        doScanExternalStorageAndImportVCard();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // This Activity should finish itself on orientation change, and give the main screen back
        // to the caller Activity.
        finish();
    }

    /**
     * Scans vCard in external storage (typically SDCard) and tries to import it. - When there's no SDCard available, an error
     * dialog is shown. - When multiple vCard files are available, asks a user to select one.
     */
    private void doScanExternalStorageAndImportVCard() {
        // TODO: should use getExternalStorageState().
        final File file = Environment.getExternalStorageDirectory();
        if (!file.exists() || !file.isDirectory() || !file.canRead()) {
            showNewDialog(R.id.dialog_sdcard_not_found);
        }
        else {
            mVCardScanThread = new VCardScanThread(file);
            showNewDialog(R.id.dialog_searching_vcard);
        }
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
            int resId = getArguments().getInt("resId");

            ManageVCardActivity parent = (ManageVCardActivity)getActivity();
            switch (resId) {
                case R.id.dialog_searching_vcard: {
                    if (parent.mProgressDialogForScanVCard == null) {
                        String message = getString(R.string.searching_vcard_message);
                        parent.mProgressDialogForScanVCard = ProgressDialog.show(parent, "", message, true, false);
                        parent.mVCardScanThread.start();
                    }
                    return parent.mProgressDialogForScanVCard;
                }
                case R.id.dialog_sdcard_not_found: {
                    AlertDialog.Builder builder = new AlertDialog.Builder(parent)
                            .setMessage(R.string.no_sdcard_message)
                            .setPositiveButton(android.R.string.ok, parent.mCancelListener);

                    builder.setIconAttribute(android.R.attr.alertDialogIcon);

                    return builder.create();
                }
                case R.id.dialog_vcard_not_found: {
                    final String message = getString(R.string.import_failure_no_vcard_file);
                    AlertDialog.Builder builder = new AlertDialog.Builder(parent)
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok, parent.mCancelListener);
                    return builder.create();
                }
                case R.id.dialog_io_exception: {
                    String message = (getString(R.string.scanning_sdcard_failed_message, getString(R.string.fail_reason_io_error)));
                    AlertDialog.Builder builder = new AlertDialog.Builder(parent)
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok, parent.mCancelListener);

                    builder.setIconAttribute(android.R.attr.alertDialogIcon);

                    return builder.create();
                }
                case R.id.dialog_select_multiple_vcard: {
                    return parent.getVCardFileSelectDialog(true);
                }
            }
            return null;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            ((ManageVCardActivity)getActivity()).mVCardScanThread.onCancel(dialog);
            getActivity().finish();
        }
    }
}
