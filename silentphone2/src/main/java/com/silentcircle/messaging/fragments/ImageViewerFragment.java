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

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.messaging.activities.FileViewerActivity;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.InputStream;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;

/**
 * Fragment to show image from message attachment.
 */
public class ImageViewerFragment extends FileViewerFragment {

    private static final String TAG = ImageViewerFragment.class.getSimpleName();

    class LoadBitmapTask extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Void... args) {
            return getBitmap();
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            onBitmapCreated(bitmap);
        }
    }

    public static ImageViewerFragment create(Uri uri, String mimeType) {
        return instantiate(new ImageViewerFragment(), uri, mimeType);
    }

    protected static Bitmap getBitmap(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (true) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                Matrix matrix = ViewUtil.getRotationMatrixFromExif(file.getAbsolutePath());
                if (matrix != null) {
                    Bitmap recyclable = bitmap;
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                            bitmap.getHeight(), matrix, false);
                    recyclable.recycle();
                }
                return bitmap;
            } catch (OutOfMemoryError error) {
                options.inSampleSize *= 2;
            }
        }
    }

    protected static Bitmap getBitmap(InputStream stream) {
        Bitmap result = null;
        try {
            result = BitmapFactory.decodeStream(stream);
        } finally {
            IOUtils.close(stream);
        }
        return result;
    }

    protected static Bitmap getBitmap(byte[] content) {
        Bitmap result = null;
        try {
            result = BitmapFactory.decodeByteArray(content, 0, content.length);
        } catch (OutOfMemoryError error) {
            ;
        }
        return result;
    }

    protected static Bitmap getBitmap(FileDescriptor fileDescriptor) {
        return BitmapFactory.decodeFileDescriptor(fileDescriptor);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void setLayerType(ImageView view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    protected void createBitmap() {
        onBitmapCreated(getBitmap());
    }

    protected Bitmap getBitmap() {
        Bitmap bitmap = getBitmap(getFile());

        if (bitmap == null) {
            try {
                bitmap = getBitmap(getStream(getActivity()));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to find image file by uri [" + getURI() + "]");
            }
        }
        return bitmap;
    }

    protected void loadBitmap() {
        AsyncUtils.execute(new LoadBitmapTask());
    }

    protected void onBitmapCreated(Bitmap bitmap) {

        Activity activity = getActivity();

        if (bitmap == null) {
            Toast.makeText(activity, getString(R.string.messaging_image_viewer_unable_to_display_image),
                    Toast.LENGTH_SHORT).show();
            dispatchError();
            return;
        }

        ImageViewTouch view = (ImageViewTouch) findViewById(R.id.image_viewer);

        if (bitmap.getWidth() * bitmap.getHeight() > 2048 * 2048) {
            setLayerType(view);
        }

        view.setImageBitmap(bitmap);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.messaging_image_viewer_fragment, container, false);
    }

    @Override
    public void onViewCreated(View v, Bundle savedInstanceState) {

        super.onViewCreated(v, savedInstanceState);

        ImageViewTouch view = (ImageViewTouch) v.findViewById(R.id.image_viewer);
        view.setDisplayType(ImageViewTouch.DisplayType.FIT_TO_SCREEN);
        view.setSingleTapListener(new ImageViewTouch.OnImageViewTouchSingleTapListener() {

            @Override
            public void onSingleTapConfirmed() {
                android.support.v7.app.ActionBar bar =
                        ((FileViewerActivity) getActivity()).getSupportActionBar();
                if (bar.isShowing()) {
                    bar.hide();
                } else {
                    bar.show();
                }
            }
        });

        createBitmap();
    }

}
