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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.messaging.activities.AxoRegisterActivity;
import com.silentcircle.messaging.views.OnSwipeTouchListener;
import com.silentcircle.silentphone2.R;

import java.io.IOException;

/**
 * Fragment to display PDF.
 *
 * Code taken from https://github.com/googlesamples/android-PdfRendererBasic/blob/master/Application/src/main/java/com/example/android/pdfrendererbasic/PdfRendererBasicFragment.java
 * License: Copyright 2014 The Android Open Source Project, Inc.
 */
public class PdfViewerFragment extends FileViewerFragment implements View.OnClickListener{

    private static final String STATE_CURRENT_PAGE_INDEX =
            "com.silentcircle.messaging.fragments.PDF_PAGE_INDEX";

    private ImageView mImageView;

    private ImageButton mButtonPrevious;
    private ImageButton mButtonNext;
    private TextView mPagePosition;

    private PdfRenderer mPdfRenderer;
    private PdfRenderer.Page mCurrentPage;

    private float mRenderMultiplier;

    public static PdfViewerFragment create(Uri uri, String mimeType) {
        return instantiate(new PdfViewerFragment(), uri, mimeType);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.messaging_pdf_viewer_fragment, container, false);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mImageView = (ImageView) view.findViewById(R.id.pdf_image);
        mPagePosition = (TextView) view.findViewById(R.id.page_position);
        mButtonPrevious = (ImageButton) view.findViewById(R.id.button_previous);
        mButtonNext = (ImageButton) view.findViewById(R.id.button_next);
        mButtonPrevious.setOnClickListener(this);
        mButtonNext.setOnClickListener(this);

        mImageView.setOnTouchListener(new OnSwipeTouchListener(getActivity()) {
            @Override
            public void onSwipeLeft() {
                showPage(mCurrentPage.getIndex() + 1);
            }

            @Override
            public void onSwipeRight() {
                showPage(mCurrentPage.getIndex() - 1);
            }
        });

        mRenderMultiplier = Math.max(1.0f, getResources().getDisplayMetrics().scaledDensity);

        int index = 0;
        if (null != savedInstanceState) {
            index = savedInstanceState.getInt(STATE_CURRENT_PAGE_INDEX, 0);
        }

        if (mPdfRenderer != null) {
            showPage(index);
        }
    }

    @Override
    public void onDestroyView() {
        mImageView = null;
        mButtonPrevious = null;
        mButtonNext = null;

        super.onDestroyView();
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        commonOnAttach(context);
    }

    /*
     * Deprecated on API 23
     * Use onAttachToContext instead
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        commonOnAttach(activity);
    }

    private void commonOnAttach(Context context) {
        if (mPdfRenderer != null) {
            return;
        }

        try {
            openRenderer(context);
        } catch (IOException e) {
            Toast.makeText(context, "Failed to show PDF document [" + e.getMessage() + "]",
                    Toast.LENGTH_SHORT).show();
            dispatchError();
        }
    }

    @Override
    public void onDetach() {
        try {
            closeRenderer();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDetach();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (null != mCurrentPage) {
            outState.putInt(STATE_CURRENT_PAGE_INDEX, mCurrentPage.getIndex());
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_previous:
                showPage(mCurrentPage.getIndex() - 1);
                break;
            case R.id.button_next:
                showPage(mCurrentPage.getIndex() + 1);
                break;
            default:
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public int getPageCount() {
        return mPdfRenderer.getPageCount();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void openRenderer(Context context) throws IOException {
        mPdfRenderer = new PdfRenderer(getFileDescriptor(context));

        if (mPdfRenderer == null) {
            throw new IOException();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void closeRenderer() throws IOException {
        if (null != mCurrentPage) {
            mCurrentPage.close();
        }

        if (mPdfRenderer != null) {
            mPdfRenderer.close();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void showPage(int index) {
        if (mPdfRenderer.getPageCount() <= index || index < 0) {
            return;
        }

        if (null != mCurrentPage) {
            mCurrentPage.close();
        }

        mCurrentPage = mPdfRenderer.openPage(index);

        int renderWidth = Math.round(mRenderMultiplier * mCurrentPage.getWidth());
        int renderHeight = Math.round(mRenderMultiplier * mCurrentPage.getHeight());

        Bitmap bitmap = Bitmap.createBitmap(renderWidth, renderHeight,
                Bitmap.Config.ARGB_8888);
        mCurrentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        mImageView.setImageBitmap(bitmap);
        updateUi();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updateUi() {
        int index = mCurrentPage.getIndex();
        int pageCount = mPdfRenderer.getPageCount();
        mButtonPrevious.setEnabled(0 != index);
        mButtonNext.setEnabled(index + 1 < pageCount);
        mPagePosition.setText(String.format("%d/%d", index + 1, pageCount));
        mPagePosition.setContentDescription(
                getString(R.string.messaging_pdf_viewer_showing_page_voiceover, index + 1, pageCount));
        mImageView.setContentDescription(
                getString(R.string.messaging_pdf_viewer_showing_page_voiceover, index + 1, pageCount));
    }

}
