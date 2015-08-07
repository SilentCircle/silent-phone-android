/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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

package com.silentcircle.contacts.widget;

import android.annotation.TargetApi;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.Intents;
import com.silentcircle.silentcontacts2.ScContactsContract.PhoneLookup;
import com.silentcircle.silentcontacts2.ScContactsContract.QuickContact;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;

import com.silentcircle.silentphone2.R;

/**
 * Created by werner on 11.02.15.
 */
public class ScQuickContactBadgeNew extends ImageView implements View.OnClickListener  {
    private Uri mContactUri;
    private String mContactEmail;
    private String mContactPhone;
    private Drawable mOverlay;
    private QueryHandler mQueryHandler;
    private Drawable mDefaultAvatar;
    private Bundle mExtras = null;

    protected String[] mExcludeMimes = null;

    static final private int TOKEN_EMAIL_LOOKUP = 0;
    static final private int TOKEN_PHONE_LOOKUP = 1;
    static final private int TOKEN_EMAIL_LOOKUP_AND_TRIGGER = 2;
    static final private int TOKEN_PHONE_LOOKUP_AND_TRIGGER = 3;

    static final private String EXTRA_URI_CONTENT = "uri_content";

    static final String[] EMAIL_LOOKUP_PROJECTION = new String[] {
            RawContacts._ID,
    };
    static final int EMAIL_ID_COLUMN_INDEX = 0;
    static final int EMAIL_LOOKUP_STRING_COLUMN_INDEX = 1;

    static final String[] PHONE_LOOKUP_PROJECTION = new String[] {
            PhoneLookup._ID,
    };
    static final int PHONE_ID_COLUMN_INDEX = 0;
    static final int PHONE_LOOKUP_STRING_COLUMN_INDEX = 1;

    private Context mContext;
    private int mPaddingTop;
    private int mPaddingLeft;

    public ScQuickContactBadgeNew(Context context) {
        this(context, null);
    }

    public ScQuickContactBadgeNew(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScQuickContactBadgeNew(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ScQuickContactBadgeNew(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {

        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(context);
    }
    
    private void initialize(Context context) {
        mContext = context;
        TypedArray styledAttributes = mContext.obtainStyledAttributes(R.styleable.Theme);
        mOverlay = mContext.getResources().getDrawable(R.drawable.quickcontact_badge_overlay_dark);  // Should be theme-able
        styledAttributes.recycle();

        if (!isInEditMode()) {
            mQueryHandler = new QueryHandler(mContext.getContentResolver());
        }
        setOnClickListener(this);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mOverlay != null && mOverlay.isStateful()) {
            mOverlay.setState(getDrawableState());
            invalidate();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (mOverlay != null) {
            mOverlay.setHotspot(x, y);
        }
    }

    /** This call has no effect anymore, as there is only one QuickContact mode */
    @SuppressWarnings("unused")
    public void setMode(int size) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isEnabled()) {
            // not clickable? don't show triangle
            return;
        }

        if (mOverlay == null || mOverlay.getIntrinsicWidth() == 0 ||
                mOverlay.getIntrinsicHeight() == 0) {
            // nothing to draw
            return;
        }

        mOverlay.setBounds(0, 0, getWidth(), getHeight());

        mPaddingTop = getPaddingTop();
        mPaddingLeft = getPaddingLeft();

        if (mPaddingTop == 0 && mPaddingLeft == 0) {
            mOverlay.draw(canvas);
        } else {
            int saveCount = canvas.getSaveCount();
            canvas.save();
            canvas.translate(mPaddingLeft, mPaddingTop);
            mOverlay.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    /** True if a contact, an email address or a phone number has been assigned */
    private boolean isAssigned() {
        return mContactUri != null || mContactEmail != null || mContactPhone != null;
    }

    /**
     * Resets the contact photo to the default state.
     */
    public void setImageToDefault() {
        if (mDefaultAvatar == null) {
            mDefaultAvatar = mContext.getResources().getDrawable(R.drawable.ic_contact_picture_holo_dark);
        }
        setImageDrawable(mDefaultAvatar);
    }

    /**
     * Assign the contact uri that this ScQuickContactBadgeNew should be associated
     * with. Note that this is only used for displaying the QuickContact window and
     * won't bind the contact's photo for you. Call {@link #setImageDrawable(Drawable)} to set the
     * photo.
     *
     * @param contactUri Either a {@link android.provider.ContactsContract.Contacts#CONTENT_URI} or
     *            {@link android.provider.ContactsContract.Contacts#CONTENT_LOOKUP_URI} style URI.
     */
    public void assignContactUri(Uri contactUri) {
        mContactUri = contactUri;
        mContactEmail = null;
        mContactPhone = null;
        onContactUriChanged();
    }

    /**
     * Assign a contact based on an email address. This should only be used when
     * the contact's URI is not available, as an extra query will have to be
     * performed to lookup the URI based on the email.
     *
     * @param emailAddress The email address of the contact.
     * @param lazyLookup If this is true, the lookup query will not be performed
     * until this view is clicked.
     */
    public void assignContactFromEmail(String emailAddress, boolean lazyLookup) {
        assignContactFromEmail(emailAddress, lazyLookup, null);
    }

    /**
     * Assign a contact based on an email address. This should only be used when
     * the contact's URI is not available, as an extra query will have to be
     * performed to lookup the URI based on the email.

     @param emailAddress The email address of the contact.
     @param lazyLookup If this is true, the lookup query will not be performed
     until this view is clicked.
     @param extras A bundle of extras to populate the contact edit page with if the contact
     is not found and the user chooses to add the email address to an existing contact or
     create a new contact. Uses the same string constants as those found in
     {@link android.provider.ContactsContract.Intents.Insert}
     */

    public void assignContactFromEmail(String emailAddress, boolean lazyLookup, Bundle extras) {
        mContactEmail = emailAddress;
        mExtras = extras;
        if (!lazyLookup && mQueryHandler != null) {
            mQueryHandler.startQuery(TOKEN_EMAIL_LOOKUP, null,
                    Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(mContactEmail)),
                    EMAIL_LOOKUP_PROJECTION, null, null, null);
        } else {
            mContactUri = null;
            onContactUriChanged();
        }
    }


    /**
     * Assign a contact based on a phone number. This should only be used when
     * the contact's URI is not available, as an extra query will have to be
     * performed to lookup the URI based on the phone number.
     *
     * @param phoneNumber The phone number of the contact.
     * @param lazyLookup If this is true, the lookup query will not be performed
     * until this view is clicked.
     */
    public void assignContactFromPhone(String phoneNumber, boolean lazyLookup) {
        assignContactFromPhone(phoneNumber, lazyLookup, new Bundle());
    }

    /**
     * Assign a contact based on a phone number. This should only be used when
     * the contact's URI is not available, as an extra query will have to be
     * performed to lookup the URI based on the phone number.
     *
     * @param phoneNumber The phone number of the contact.
     * @param lazyLookup If this is true, the lookup query will not be performed
     * until this view is clicked.
     * @param extras A bundle of extras to populate the contact edit page with if the contact
     * is not found and the user chooses to add the phone number to an existing contact or
     * create a new contact. Uses the same string constants as those found in
     * {@link android.provider.ContactsContract.Intents.Insert}
     */
    public void assignContactFromPhone(String phoneNumber, boolean lazyLookup, Bundle extras) {
        mContactPhone = phoneNumber;
        mExtras = extras;
        if (!lazyLookup && mQueryHandler != null) {
            mQueryHandler.startQuery(TOKEN_PHONE_LOOKUP, null,
                    Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, mContactPhone),
                    PHONE_LOOKUP_PROJECTION, null, null, null);
        } else {
            mContactUri = null;
            onContactUriChanged();
        }
    }

    /**
     * Assigns the drawable that is to be drawn on top of the assigned contact photo.
     *
     * @param overlay Drawable to be drawn over the assigned contact photo. Must have a non-zero
     *         instrinsic width and height.
     */
    public void setOverlay(Drawable overlay) {
        mOverlay = overlay;
    }

    private void onContactUriChanged() {
        setEnabled(isAssigned());
    }

    @Override
    public void onClick(View v) {
        // If contact has been assigned, mExtras should no longer be null, but do a null check
        // anyway just in case assignContactFromPhone or Email was called with a null bundle or
        // wasn't assigned previously.
        final Bundle extras = (mExtras == null) ? new Bundle() : mExtras;
        Log.d("BadgeNew", "++++ show quick uri: " + mContactUri);
        if (mContactUri != null) {
            QuickContact.showQuickContact(getContext(), ScQuickContactBadgeNew.this, mContactUri,
                    QuickContact.MODE_LARGE, mExcludeMimes);
        } else if (mContactEmail != null && mQueryHandler != null) {
            extras.putString(EXTRA_URI_CONTENT, mContactEmail);
            mQueryHandler.startQuery(TOKEN_EMAIL_LOOKUP_AND_TRIGGER, extras,
                    Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(mContactEmail)),
                    EMAIL_LOOKUP_PROJECTION, null, null, null);
        } else if (mContactPhone != null && mQueryHandler != null) {
            extras.putString(EXTRA_URI_CONTENT, mContactPhone);
            mQueryHandler.startQuery(TOKEN_PHONE_LOOKUP_AND_TRIGGER, extras,
                    Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, mContactPhone),
                    PHONE_LOOKUP_PROJECTION, null, null, null);
        } else {
            // If a contact hasn't been assigned, don't react to click.
            return;
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(ScQuickContactBadgeNew.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(ScQuickContactBadgeNew.class.getName());
    }

    /**
     * Set a list of specific MIME-types to exclude and not display. For
     * example, this can be used to hide the {@link android.provider.ContactsContract.Contacts#CONTENT_ITEM_TYPE}
     * profile icon.
     */
    public void setExcludeMimes(String[] excludeMimes) {
        mExcludeMimes = excludeMimes;
    }

    private class QueryHandler extends AsyncQueryHandler {

        public QueryHandler(ContentResolver cr) {
            super(cr);

        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            Uri lookupUri = null;
            Uri createUri = null;
            boolean trigger = false;
            Bundle extras = (cookie != null) ? (Bundle) cookie : new Bundle();
            try {
                switch(token) {
                    case TOKEN_PHONE_LOOKUP_AND_TRIGGER:
                        trigger = true;
                        createUri = Uri.fromParts("tel", extras.getString(EXTRA_URI_CONTENT), null);

                        //$FALL-THROUGH$
                    case TOKEN_PHONE_LOOKUP: {
                        if (cursor != null && cursor.moveToFirst()) {
                            final long contactId = cursor.getLong(PHONE_ID_COLUMN_INDEX);
                            lookupUri = RawContacts.getLookupUri(contactId);
                        }

                        break;
                    }
                    case TOKEN_EMAIL_LOOKUP_AND_TRIGGER:
                        trigger = true;
                        createUri = Uri.fromParts("mailto",
                                extras.getString(EXTRA_URI_CONTENT), null);

                        //$FALL-THROUGH$
                    case TOKEN_EMAIL_LOOKUP: {
                        if (cursor != null && cursor.moveToFirst()) {
                            final long contactId = cursor.getLong(EMAIL_ID_COLUMN_INDEX);
                            lookupUri = RawContacts.getLookupUri(contactId);
                        }
                        break;
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            mContactUri = lookupUri;
            onContactUriChanged();
            Log.d("BadgeNew", "++++ query lookup uri" + mContactUri);

            if (trigger && lookupUri != null) {
                // Found contact, so trigger QuickContact
                QuickContact.showQuickContact(getContext(), ScQuickContactBadgeNew.this, lookupUri,
                        QuickContact.MODE_LARGE, mExcludeMimes);
            } else if (createUri != null) {
                // Prompt user to add this person to contacts
                final Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT, createUri);
                if (extras != null) {
                    extras.remove(EXTRA_URI_CONTENT);
                    intent.putExtras(extras);
                }
                getContext().startActivity(intent);
            }
        }
    }

}
