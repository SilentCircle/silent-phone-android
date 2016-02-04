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
package com.silentcircle.messaging.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.List;

public class AvatarView extends ImageView implements View.OnClickListener {

    private String username;
    private OnClickListener secondaryOnClickListener;

    protected final List<AsyncTask<?, ?, ?>> tasks = new ArrayList<AsyncTask<?, ?, ?>>();

    private boolean interactive = true;

    public AvatarView(Context context) {
        super(context);
        setOnClickListener(this);
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    public AvatarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOnClickListener(this);
    }

    private void cancelTasks() {
        while (!tasks.isEmpty()) {
            AsyncTask<?, ?, ?> task = tasks.get(0);
            if (task != null) {
                task.cancel(true);
                task = null;
            }
            tasks.remove(0);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void disableHardwareAcceleration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    public boolean isInteractive() {
        return interactive;
    }

    public void loadAvatar(ContactEntry contact) {
        setPhoto(contact.thumbnailUri, contact.photoId, null, contact.name, null,
                ContactPhotoManagerNew.TYPE_DEFAULT);
    }

    @Override
    public void onClick(View view) {
        if (!isInteractive()) {
            if (secondaryOnClickListener != null) {
                secondaryOnClickListener.onClick(view);
            }
            return;
        }
        if (username == null) {
            return;
        }

        // TEMPORARY:
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(username);
        if (contactEntry == null) {
            return;
        }
        ContactsContract.QuickContact.showQuickContact(getContext(), view,
                contactEntry.lookupUri, ContactsContract.QuickContact.MODE_LARGE, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelTasks();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int[] states = getDrawableState();
        for (int i = 0; i < states.length; i++) {
            switch (states[i]) {
                case android.R.attr.state_pressed:
                case android.R.attr.state_hovered:
                    canvas.drawColor(0x50808080, PorterDuff.Mode.DARKEN);
                    break;
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setAvatar(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
/*            LoadAvatarTask.flagForRefresh(this);
            LoadAvatarTask.forget(username);*/
        }
    }

    public void setAvatar(Bitmap avatar) {
        if (avatar != null) {
            setClippedBitmap(avatar);
        } else {
            setClippedResource(R.drawable.ic_avatar_placeholder);
        }
    }

    public void setClippedBitmap(Bitmap bitmap) {
        setClippedDrawable(new BitmapDrawable(getResources(), bitmap));
    }

    public void setClippedDrawable(Drawable drawable) {
        disableHardwareAcceleration();
        setImageDrawable(new CircleClipDrawable(drawable, getResources(), R.color.silent_dark_grey,
                R.color.silent_translucent_dark_grey, R.dimen.stroke_normal));
    }

    public void setClippedResource(int drawableResourceID) {
        setClippedDrawable(getResources().getDrawable(drawableResourceID));
    }

    public void setContact(Contact contact) {
        setContact(contact != null ? contact.getUsername() : null);
    }

    public void setContact(ContactEntry contact) {
        if (contact != null) {
            this.username = contact.imName;
            setPhoto(contact.thumbnailUri, contact.photoId, null, contact.name, null,
                    ContactPhotoManagerNew.TYPE_DEFAULT);
        } else {
            setPhoto(null, 0, null, null, null,
                    ContactPhotoManagerNew.TYPE_DEFAULT);
        }
    }

    public void setContact(Contact contact, int avatarSizeResourceID) {
        setContact(contact != null ? contact.getUsername() : null, avatarSizeResourceID);
    }

    public void setContact(String username) {
        this.username = username;
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(username);
        setContact(contactEntry);
    }

    private void setPhoto(Uri photoUri, long photoId, Uri contactUri,
                          String displayName, String identifier, int contactType) {
        ContactPhotoManagerNew contactPhotoManager = ContactPhotoManagerNew.getInstance(getContext());
        ContactPhotoManagerNew.DefaultImageRequest request =
                new ContactPhotoManagerNew.DefaultImageRequest(displayName, identifier,
                        contactType, true /* isCircular */);
        contactPhotoManager.loadThumbnail(this, photoId, false /* darkTheme */,
                true /* isCircular */, request);

//        contactPhotoManager.loadDirectoryPhoto(this, photoUri,
//                false /* darkTheme */, true /* isCircular */, request);
    }


    // TODO: anru
/*    public void setContact(String username, ContactRepository repository) {
        setContact(username, repository, R.dimen.avatar_normal);
    }

    public void setContact(String username, ContactRepository repository, int avatarSizeResourceID) {
        if (username != null && this.username != username) {
            setTag(R.id.username, username);
            this.username = username;
            setContentDescription(username);
            loadAvatar(username, repository, avatarSizeResourceID);
        }
    }*/

    public void setContact(String username, int avatarSizeResourceID) {
       // setContact(username, getDefaultContactRepository(), avatarSizeResourceID);
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void setSecondaryOnClickListener(OnClickListener secondaryOnClickListener) {
        this.secondaryOnClickListener = secondaryOnClickListener;
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag != null && tag instanceof Contact) {
            setContact((Contact) tag);
        }
    }

}
