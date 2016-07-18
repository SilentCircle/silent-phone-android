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
package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 *
 */
public class FileViewerFragment extends BaseFragment {

    public interface Callback {
        void onError(Uri uri, String mimeType);
    }

    public static final String EXTRA_TYPE = "type";

    private ParcelFileDescriptor mFileDescriptor; // populated if necessary

    public static FileViewerFragment create(Uri uri, String mimeType) {
        return instantiate(new FileViewerFragment(), uri, mimeType);
    }

    protected static <T extends FileViewerFragment> T instantiate(T fragment, Uri uri, String mimeType) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(Intent.EXTRA_STREAM, uri);
        arguments.putString(EXTRA_TYPE, mimeType);
        fragment.setArguments(arguments);
        return fragment;
    }

    protected void dispatchError() {

        Bundle arguments = getArguments();
        Callback callback = getCallback();

        if (arguments == null || callback == null) {
            return;
        }

        Uri uri = arguments.getParcelable(Intent.EXTRA_STREAM);
        String mimeType = arguments.getString(EXTRA_TYPE);

        callback.onError(uri, mimeType);

    }

    protected Callback getCallback() {
        Activity activity = getActivity();
        return activity == null ? null : (Callback) activity;
    }

    protected File getFile() {
        Uri uri = getURI();
        return uri == null ? null : new File(uri.getPath());
    }

    protected ParcelFileDescriptor getFileDescriptor(Context context) throws FileNotFoundException {
        if (mFileDescriptor != null) {
            boolean isClosed = true;
            try {
                int fd = mFileDescriptor.getFd();
                // file descriptor with value -1 indicates that this ParcelFileDescriptor
                // has been closed
                isClosed = (fd == -1);
            } catch (IllegalStateException ex) {
                // file descriptor closed
            }

            if (!isClosed) {
                return mFileDescriptor;
            }
        }

        Uri uri = getURI();
        mFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");

        return mFileDescriptor;
    }

    protected InputStream getStream(Context context) throws FileNotFoundException {
        return new ParcelFileDescriptor.AutoCloseInputStream(getFileDescriptor(context));
    }

    protected String getType() {
        Bundle arguments = getArguments();
        return arguments == null ? null : arguments.getString(EXTRA_TYPE);
    }

    protected Uri getURI() {
        Bundle arguments = getArguments();
        return arguments == null ? null : (Uri) arguments.getParcelable(Intent.EXTRA_STREAM);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.messaging_file_viewer_fragment, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        IOUtils.close(mFileDescriptor);
        mFileDescriptor = null;
    }

    protected InputStream openFileForReading() {
        File file = getFile();
        try {
            return file == null ? null : new FileInputStream(file);
        } catch (FileNotFoundException exception) {
            return null;
        }
    }

}

