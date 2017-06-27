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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialog;
import android.view.View;
import android.view.WindowManager;

import com.silentcircle.silentphone2.R;

/**
 * Dialog for selection action on avatar.
 */
public class AvatarActionsDialog extends AppCompatDialog implements View.OnClickListener {

    public interface OnAvatarActionSelectedListener {

        void onGallerySelected();

        void onCaptureImageSelected();

        void onDeleteAvatarSelected();
    }

    private View mButtonGallery;
    private View mButtonCamera;
    private View mButtonDeleteAvatar;

    private boolean mCameraButtonVisible = true;
    private boolean mDeleteButtonVisible = true;

    private OnAvatarActionSelectedListener mListener;

    public AvatarActionsDialog(final Context context) {
        super(context, R.style.Dialog_Spa4);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_choose_avatar_action);
        setTitle(R.string.dialog_title_choose_avatar_action);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        getWindow().setAttributes(params);

        mButtonGallery = findViewById(R.id.button_gallery);
        mButtonCamera = findViewById(R.id.button_camera);
        mButtonDeleteAvatar = findViewById(R.id.button_delete_avatar);

        mButtonGallery.setOnClickListener(this);
        mButtonCamera.setOnClickListener(this);
        mButtonDeleteAvatar.setOnClickListener(this);

        setCameraButtonEnabled(mCameraButtonVisible);
        setDeleteButtonEnabled(mDeleteButtonVisible);
    }

    public void setOnCallOrConversationSelectedListener(
            final OnAvatarActionSelectedListener listener) {
        mListener = listener;
    }

    public void setCameraButtonEnabled(boolean enabled) {
        if (mButtonCamera != null) {
            mButtonCamera.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        mCameraButtonVisible = enabled;
    }

    public void setDeleteButtonEnabled(boolean enabled) {
        if (mButtonDeleteAvatar != null) {
            mButtonDeleteAvatar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        mDeleteButtonVisible = enabled;
    }

    @Override
    public void onClick(View v) {
        dismiss();
        switch (v.getId()) {
            case R.id.button_gallery:
                if (mListener != null) {
                    mListener.onGallerySelected();
                }
                break;
            case R.id.button_camera:
                if (mListener != null) {
                    mListener.onCaptureImageSelected();
                }
                break;
            case R.id.button_delete_avatar:
                if (mListener != null) {
                    mListener.onDeleteAvatarSelected();
                }
                break;
            default:;
        }
    }
}
