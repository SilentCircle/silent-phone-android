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
package com.silentcircle.userinfo.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.silentcircle.common.util.ExplainPermissionDialog;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.SquareImageView;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.passcode.AppLifecycleNotifierBaseActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.khronos.opengles.GL10;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchPanable;

/**
 * Activity to select avatar size.
 */
public class AvatarCropActivity extends AppLifecycleNotifierBaseActivity implements View.OnClickListener,
        View.OnLayoutChangeListener, ExplainPermissionDialog.AfterReading {

    private static final String TAG = AvatarCropActivity.class.getSimpleName();

    public static final String FLAG_RESIZE_TO_DEFAULT_SIZE =
            "com.silentcircle.avatar.extra.RESIZE_TO_DEFAULT_SIZE";

    // Identifiers and flags for permission handling
    public static final int PERMISSIONS_REQUEST_STORAGE = 1;
    private boolean mStoragePermissionAsked;

    private Uri mSourceUri;
    private Uri mSaveUri;

    private ImageViewTouchPanable mImageTouchView;
    private SquareImageView mImageBorder;
    private ImageButton mButtonCancel;
    private ImageButton mButtonAccept;
    private Button mButtonSquareCircle;
    private ImageView mAvatarPreview;

    private int mImageWidth;
    private int mImageHeight;

    private Handler mHandler;

    private final Rect mRect = new Rect();
    private final Matrix mInvertedMatrix = new Matrix();
    private final Point mScreenSize = new Point();

    private File mImageFile;

    private boolean mResizeToDefaultSize = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_crop);
        getWindow().getDecorView().setBackgroundColor(ContextCompat.getColor(this,
                R.color.sc_ng_background));

        mButtonCancel = (ImageButton) findViewById(R.id.button_cancel);
        mButtonAccept = (ImageButton) findViewById(R.id.button_accept);
        mImageTouchView = (ImageViewTouchPanable) findViewById(R.id.image_viewer);
        mImageTouchView.setDisplayType(ImageViewTouch.DisplayType.FIT_TO_SCREEN);
        mImageBorder = (SquareImageView) findViewById(R.id.avatar_border);
        mButtonSquareCircle = (Button) findViewById(R.id.button_circle);
        mAvatarPreview = (ImageView) findViewById(R.id.image_preview);
        /*
         * Left for debug
         * mAvatarPreview.setVisibility(View.VISIBLE);
         */

        Intent intent = getIntent();
        if (intent != null) {
            mResizeToDefaultSize = intent.getBooleanExtra(FLAG_RESIZE_TO_DEFAULT_SIZE, true);
            checkStoragePermissions();
        }
    }

    private void onCreateAfterPermissions() {
        mButtonCancel.setOnClickListener(this);
        mButtonAccept.setOnClickListener(this);
        mButtonSquareCircle.setOnClickListener(this);
        mAvatarPreview.setOnClickListener(this);

        mHandler = new Handler();

        Intent intent = getIntent();
        mSourceUri = intent.getData();

        Bundle extras = intent.getExtras();
        if (extras != null) {
            mSaveUri = extras.getParcelable(MediaStore.EXTRA_OUTPUT);
        }

        setPreRotatedBitmap(mSourceUri);
    }

    private void setPreRotatedBitmap(Uri sourceUri) {
        Bitmap bitmap = getBitmap(sourceUri);
        if (bitmap != null) {
            mImageWidth = bitmap.getWidth();
            mImageHeight = bitmap.getHeight();
            mImageTouchView.setImageBitmap(bitmap);

            /* save rotated image to a different file, update source uri */
            File imagePath = new File(getFilesDir(), "captured/image");
            if (!imagePath.exists()) imagePath.mkdirs();
            mImageFile = new File(imagePath, UUIDGen.makeType1UUID().toString());
            Uri uri = FileProvider.getUriForFile(this, BuildConfig.AUTHORITY_BASE + ".files",
                    mImageFile);
            uri = saveBitmapToUri(bitmap, uri);
            if (uri != null) {
                mSourceUri = uri;
            }
        } else {
            Toast.makeText(this, R.string.avatar_could_not_show_image, Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Nullable
    protected Bitmap getBitmap(Uri sourceUri) {
        Bitmap result = null;
        try {
            ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(sourceUri, "r");
            if (fileDescriptor != null) {
                /* TODO: scale image down, if too large and try to show it anyway */
                Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor.getFileDescriptor());
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode bitmap from " + sourceUri);
                    return null;
                }
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                if (width > GL10.GL_MAX_TEXTURE_SIZE || height > GL10.GL_MAX_TEXTURE_SIZE) {
                    /* for very large bitmaps scale them to be able to show them in image view */
                    Bitmap recyclable = bitmap;
                    float aspectRatio = ((float) height) / ((float) width);
                    if (aspectRatio > 1.0f) {
                        width = Math.min(width, GL10.GL_MAX_TEXTURE_SIZE) / 2;
                        height = (int) (width * aspectRatio);
                    } else {
                        height = Math.min(height, GL10.GL_MAX_TEXTURE_SIZE) / 2;
                        width = (int) (height * aspectRatio);
                    }
                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                    recyclable.recycle();
                }
                Matrix matrix = ViewUtil.getRotationMatrixFromExif(this, sourceUri);
                if (matrix != null) {
                    Bitmap recyclable = bitmap;
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                            bitmap.getHeight(), matrix, false);
                    recyclable.recycle();
                }
                result = bitmap;
            }
        } catch (OutOfMemoryError | FileNotFoundException e) {
            // failed to read image, return null. This will exit activity.
        }
        return result;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_cancel:
                setResult(RESULT_CANCELED);
                finish();
                break;
            case R.id.image_preview:
                Bitmap croppedImage = getCroppedImage();
                mAvatarPreview.setImageBitmap(croppedImage);
                break;
            case R.id.button_circle:
                mImageBorder.toggleIsCircle();
                mButtonSquareCircle.setText(mImageBorder.isCircle()
                        ? R.string.avatar_show_as_square : R.string.avatar_show_as_circle);
                break;
            case R.id.button_accept:
                croppedImage = getCroppedImage();
                saveOutput(croppedImage);
                finish();
                break;
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        if (mImageTouchView != null) {
            mImageTouchView.setCropRect(getCropRectF());
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        if (mImageBorder != null) {
            mImageBorder.addOnLayoutChangeListener(this);
        }
        updateImageBorderSquare();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (mImageBorder != null) {
            mImageBorder.removeOnLayoutChangeListener(this);
        }
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (mImageFile != null) {
            mImageFile.delete();
        }
    }

    @Override
    public void explanationRead(int token, Bundle callerBundle) {
        switch (token) {
            case PERMISSIONS_REQUEST_STORAGE:
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, token);
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

    private void checkStoragePermissions() {
        /*
         * Request READ_ETERNAL_STORAGE permission
         * 'Any app that declares the WRITE_EXTERNAL_STORAGE permission is implicitly granted this permission.'
         */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                ExplainPermissionDialog.showExplanation(this, PERMISSIONS_REQUEST_STORAGE,
                        getString(R.string.permission_storage_title),
                        getString(R.string.permission_storage_explanation), null);
            }
            else {
                if (!mStoragePermissionAsked) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSIONS_REQUEST_STORAGE);
                    mStoragePermissionAsked = true;     // Avoid a possible third, ..., permission request if user set "don't ask anymore"
                } else {
                    finish();
                }
            }
        }
        else {
            onCreateAfterPermissions();
        }
    }

    private Bitmap getCroppedImage() {
        Rect visibleRect = getVisibleRect();
        Bitmap result = decodeRegionCrop(visibleRect, visibleRect.width(), visibleRect.width());
        /*
         * At this point we have square image. Resize it to maximum size the server allows.
         */
        if (mResizeToDefaultSize) {
            try {
                if (result != null && result.getWidth() > AvatarProvider.MAX_AVATAR_SIZE) {
                    Bitmap recyclable = result;
                    result = Bitmap.createScaledBitmap(result, AvatarProvider.MAX_AVATAR_SIZE,
                            AvatarProvider.MAX_AVATAR_SIZE, true);
                    recyclable.recycle();
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to size image down.");
            }
        }
        return result;
    }

    public Rect getVisibleRect() {
        getVisibleRectF().round(mRect);
        return mRect;
    }

    public RectF getVisibleRectF() {
        Matrix imageViewMatrix = mImageTouchView.getImageViewMatrix();

        mInvertedMatrix.reset();
        imageViewMatrix.invert(mInvertedMatrix);
        mInvertedMatrix.postTranslate(mImageTouchView.getScrollX(), mImageTouchView.getScrollY());

        RectF centerRect = getCropRectF();
        mInvertedMatrix.mapRect(centerRect);

        return centerRect;
    }

    public RectF getCropRectF() {
        final Display display = getWindowManager().getDefaultDisplay();
        display.getSize(mScreenSize);

        RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams) mImageBorder.getLayoutParams();

        int w = mScreenSize.x;
        int h = mScreenSize.y - getStatusBarHeight();
        int w1 = params.width;
        int dw = (w > w1 ? (w/2 - w1/2) : 0);

        // set rectangle to map screen center
        RectF centerRect = new RectF();
        centerRect.left = 0.0f + dw;
        centerRect.top = h/2 - Math.min(w, w1)/2;
        centerRect.right = w - dw;
        centerRect.bottom = h/2 + Math.min(w, w1)/2;

        return centerRect;
    }

    private Bitmap decodeRegionCrop(Rect rect, int outWidth, int outHeight) {
        InputStream is = null;
        Bitmap croppedImage = null;
        try {
            is = getContentResolver().openInputStream(mSourceUri);
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);

            croppedImage = decoder.decodeRegion(rect, new BitmapFactory.Options());
            if (croppedImage != null && (rect.width() > outWidth || rect.height() > outHeight)) {
                Matrix matrix = new Matrix();
                matrix.postScale((float) outWidth / rect.width(), (float) outHeight / rect.height());
                croppedImage = Bitmap.createBitmap(croppedImage, 0, 0, croppedImage.getWidth(),
                        croppedImage.getHeight(), matrix, true);
            }
        } catch (IOException | OutOfMemoryError | IllegalArgumentException e) {
            // return null and so don't change avatar
        } finally {
            IOUtils.close(is);
        }
        return croppedImage;
    }

    private void saveOutput(Bitmap croppedImage) {
        if (mSaveUri != null && croppedImage != null) {
            Uri destination = saveBitmapToUri(croppedImage, mSaveUri);

            if (destination != null) {
                Intent intent = new Intent().putExtra(MediaStore.EXTRA_OUTPUT, mSaveUri);
                intent.setData(destination);
                setResult(RESULT_OK, intent);
            } else {
                setResult(RESULT_CANCELED);
            }

            final Bitmap bitmap = croppedImage;
            mHandler.post(new Runnable() {
                public void run() {
                    mImageTouchView.clear();
                    bitmap.recycle();
                }
            });
        } else {
            setResult(RESULT_CANCELED);
        }
    }

    private Uri saveBitmapToUri(@NonNull Bitmap bitmap, @NonNull Uri destination) {
        Uri result = null;
        OutputStream outputStream = null;
        try {
            outputStream = getContentResolver().openOutputStream(destination);
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            }
            result = destination;
        } catch (IOException e) {
            // failed to write to destination file, return null
        } finally {
            IOUtils.close(outputStream);
        }
        return result;
    }

    private void updateImageBorderSquare() {
        final Display display = getWindowManager().getDefaultDisplay();
        display.getSize(mScreenSize);

        int width = mScreenSize.x;
        int height = mScreenSize.y;

        RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams) mImageBorder.getLayoutParams();
        params.width = Math.min(width, height);
        params.height = Math.min(width, height);

        mImageBorder.setLayoutParams(params);
    }

    public int getStatusBarHeight() {
        int result = 0;
        Resources resources = getResources();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId);
        }
        return result;
    }

}
