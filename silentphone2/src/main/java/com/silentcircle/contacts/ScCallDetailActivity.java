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

package com.silentcircle.contacts;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.support.v7.app.ActionBar;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.contacts.calllognew.CallDetailHistoryAdapter;
import com.silentcircle.contacts.calllognew.CallTypeHelper;
import com.silentcircle.contacts.calllognew.ContactInfo;
import com.silentcircle.contacts.calllognew.ContactInfoHelper;
import com.silentcircle.contacts.calllognew.PhoneNumberHelper;
import com.silentcircle.contacts.model.account.LabelHelper.PhoneLabel;
import com.silentcircle.contacts.utils.AsyncTaskExecutor;
import com.silentcircle.contacts.utils.AsyncTaskExecutors;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.contacts.utils.FormatUtils;
import com.silentcircle.silentcontacts2.ScCallLog;
import com.silentcircle.silentcontacts2.ScCallLog.ScCalls;
import com.silentcircle.silentcontacts2.ScContactsContract.Intents.Insert;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.R;

/**
 * Displays the details of a specific call log entry.
 * <p>
 * This activity can be either started with the URI of a single call log entry, or with the
 * {@link #EXTRA_CALL_LOG_IDS} extra to specify a group of call log entries.
 */
public class ScCallDetailActivity extends ActionBarActivity /*implements ProximitySensorAware */ {
    private static final String TAG = "ScCallDetail";

    /** The time to wait before enabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_BLANK_DELAY_MILLIS = 100;
    /** The time to wait before disabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_UNBLANK_DELAY_MILLIS = 500;

    /** The enumeration of {@link android.os.AsyncTask} objects used in this class. */
    public enum Tasks {
        MARK_VOICE_MAIL_READ,
        DELETE_VOICE_MAIL_AND_FINISH,
        REMOVE_FROM_CALL_LOG_AND_FINISH,
        UPDATE_PHONE_CALL_DETAILS,
    }

    /** A long array extra containing ids of call log entries to display. */
    public static final String EXTRA_CALL_LOG_IDS = "EXTRA_CALL_LOG_IDS";
    /** If we are started with a voice mail, we'll find the uri to play with this extra. */
//    public static final String EXTRA_VOICE_MAIL_URI = "EXTRA_VOICE_MAIL_URI";
    /** If we should immediately start playback of the voice mail, this extra will be set to true. */
//    public static final String EXTRA_VOICE_MAIL_START_PLAYBACK = "EXTRA_VOICE_MAIL_START_PLAYBACK";
    /** If the activity was triggered from a notification. */
    public static final String EXTRA_FROM_NOTIFICATION = "EXTRA_FROM_NOTIFICATION";

    private CallTypeHelper mCallTypeHelper;
    private PhoneNumberHelper mPhoneNumberHelper;
    private PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    private TextView mHeaderTextView;
    private View mHeaderOverlayView;
    private ImageView mMainActionView;
    private ImageButton mMainActionPushLayerView;
    private ImageView mContactBackgroundView;
    private AsyncTaskExecutor mAsyncTaskExecutor;
    private ContactInfoHelper mContactInfoHelper;

    private String mNumber = null;
    private String mDefaultCountryIso;

    LayoutInflater mInflater;
    Resources mResources;
    /** Helper to load contact photos. */
    private ContactPhotoManagerNew mContactPhotoManager;

    /** Whether we should show "edit number before call" in the options menu. */
    private boolean mHasEditNumberBeforeCallOption;

    /** Whether we should show "remove from call log" in the options menu. */
    private boolean mHasRemoveFromCallLogOption;

//    private ProximitySensorManager mProximitySensorManager;
//    private final ProximitySensorListener mProximitySensorListener = new ProximitySensorListener();

    /**
     * The action mode used when the phone number is selected.  This will be non-null only when the
     * phone number is selected.
     */
    private ActionMode mPhoneNumberActionMode;

    private CharSequence mPhoneNumberLabelToCopy;
    private CharSequence mPhoneNumberToCopy;

//    /** Listener to changes in the proximity sensor state. */
//    private class ProximitySensorListener implements ProximitySensorManager.Listener {
//        /** Used to show a blank view and hide the action bar. */
//        private final Runnable mBlankRunnable = new Runnable() {
//            @Override
//            public void run() {
//                View blankView = findViewById(R.id.blank);
//                blankView.setVisibility(View.VISIBLE);
//                getActionBar().hide();
//            }
//        };
//        /** Used to remove the blank view and show the action bar. */
//        private final Runnable mUnblankRunnable = new Runnable() {
//            @Override
//            public void run() {
//                View blankView = findViewById(R.id.blank);
//                blankView.setVisibility(View.GONE);
//                getActionBar().show();
//            }
//        };
//
//        @Override
//        public synchronized void onNear() {
//            clearPendingRequests();
//            postDelayed(mBlankRunnable, PROXIMITY_BLANK_DELAY_MILLIS);
//        }
//
//        @Override
//        public synchronized void onFar() {
//            clearPendingRequests();
//            postDelayed(mUnblankRunnable, PROXIMITY_UNBLANK_DELAY_MILLIS);
//        }
//
//        /** Removed any delayed requests that may be pending. */
//        public synchronized void clearPendingRequests() {
//            View blankView = findViewById(R.id.blank);
//            blankView.removeCallbacks(mBlankRunnable);
//            blankView.removeCallbacks(mUnblankRunnable);
//        }
//
//        /** Post a {@link Runnable} with a delay on the main thread. */
//        private synchronized void postDelayed(Runnable runnable, long delayMillis) {
//            // Post these instead of executing immediately so that:
//            // - They are guaranteed to be executed on the main thread.
//            // - If the sensor values changes rapidly for some time, the UI will not be
//            //   updated immediately.
//            View blankView = findViewById(R.id.blank);
//            blankView.postDelayed(runnable, delayMillis);
//        }
//    }
//
    static final String[] CALL_LOG_PROJECTION = new String[] {
        ScCallLog.ScCalls.DATE,
        ScCallLog.ScCalls.DURATION,
        ScCallLog.ScCalls.NUMBER,
        ScCallLog.ScCalls.TYPE,
        ScCallLog.ScCalls.COUNTRY_ISO,
        ScCallLog.ScCalls.GEOCODED_LOCATION,
    };

    static final int DATE_COLUMN_INDEX = 0;
    static final int DURATION_COLUMN_INDEX = 1;
    static final int NUMBER_COLUMN_INDEX = 2;
    static final int CALL_TYPE_COLUMN_INDEX = 3;
    static final int COUNTRY_ISO_COLUMN_INDEX = 4;
    static final int GEOCODED_LOCATION_COLUMN_INDEX = 5;

    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumberSelectedActionModeIfShown()) {
                return;
            }
            startActivity(((ViewEntry) view.getTag()).primaryIntent);
        }
    };

    private final View.OnClickListener mSecondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (finishPhoneNumberSelectedActionModeIfShown()) {
                return;
            }
            startActivity(((ViewEntry) view.getTag()).secondaryIntent);
        }
    };

    private final View.OnLongClickListener mPrimaryLongClickListener =
            new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (finishPhoneNumberSelectedActionModeIfShown()) {
                return true;
            }
//            startPhoneNumberSelectedActionMode(v);
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_detail);

        final Toolbar toolbar = (Toolbar)findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mCallTypeHelper = new CallTypeHelper(getResources());
        mPhoneNumberHelper = new PhoneNumberHelper(mResources);
        mPhoneCallDetailsHelper = new PhoneCallDetailsHelper(mResources, mPhoneNumberHelper);
        mHeaderTextView = (TextView) findViewById(R.id.header_text);
        mHeaderOverlayView = findViewById(R.id.photo_text_bar);
        mMainActionView = (ImageView) findViewById(R.id.main_action);
        mMainActionPushLayerView = (ImageButton) findViewById(R.id.main_action_push_layer);
        mContactBackgroundView = (ImageView) findViewById(R.id.contact_background);

        mDefaultCountryIso = ContactsUtils.getCurrentCountryIso(this);
        mContactPhotoManager = ContactPhotoManagerNew.getInstance(this);
//        mProximitySensorManager = new ProximitySensorManager(this, mProximitySensorListener);
        mContactInfoHelper = new ContactInfoHelper(this, ContactsUtils.getCurrentCountryIso(this));
        configureActionBar();
        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            closeSystemDialogs();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateData(getCallLogEntryUris());
    }

    /**
     * Returns the list of URIs to show.
     * <p>
     * There are two ways the URIs can be provided to the activity: as the data on the intent, or as
     * a list of ids in the call log added as an extra on the URI.
     * <p>
     * If both are available, the data on the intent takes precedence.
     */
    private Uri[] getCallLogEntryUris() {
        Uri uri = getIntent().getData();
        if (uri != null) {
            // If there is a data on the intent, it takes precedence over the extra.
            return new Uri[]{ uri };
        }
        long[] ids = getIntent().getLongArrayExtra(EXTRA_CALL_LOG_IDS);
        Uri[] uris = new Uri[ids.length];
        for (int index = 0; index < ids.length; ++index) {
            uris[index] = ContentUris.withAppendedId(ScCalls.CONTENT_URI, ids[index]);
        }
        return uris;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // Make sure phone isn't already busy before starting direct call
                TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    startActivity(ContactsUtils.getCallIntent(Uri.fromParts(Constants.SCHEME_TEL, mNumber, null)));
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Update user interface with details of given call.
     *
     * @param callUris URIs into {@link com.silentcircle.silentcontacts.ScCallLog.ScCalls} of the calls to be displayed
     */
    private void updateData(final Uri... callUris) {
        class UpdateContactDetailsTask extends AsyncTask<Void, Void, PhoneCallDetails[]> {
            @Override
            public PhoneCallDetails[] doInBackground(Void... params) {
                // TODO: All phone calls correspond to the same person, so we can make a single
                // lookup.
                final int numCalls = callUris.length;
                PhoneCallDetails[] details = new PhoneCallDetails[numCalls];
                try {
                    for (int index = 0; index < numCalls; ++index) {
                        details[index] = getPhoneCallDetailsForUri(callUris[index]);
                    }
                    return details;
                } catch (IllegalArgumentException e) {
                    // Something went wrong reading in our primary data.
                    Log.w(TAG, "invalid URI starting call details", e);
                    return null;
                }
            }

            @Override
            public void onPostExecute(PhoneCallDetails[] details) {
                if (details == null) {
                    // Somewhere went wrong: we're going to bail out and show error to users.
                    Toast.makeText(ScCallDetailActivity.this, R.string.toast_call_detail_error, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // We know that all calls are from the same number and the same contact, so pick the first.
                PhoneCallDetails firstDetails = details[0];
                mNumber = firstDetails.number.toString();
                final Uri contactUri = firstDetails.contactUri;
                final Uri photoUri = firstDetails.photoUri;

                // Set the details header, based on the first phone call.
                mPhoneCallDetailsHelper.setCallDetailsHeader(mHeaderTextView, firstDetails);

                // Cache the details about the phone number.
                final boolean canPlaceCallsTo = mPhoneNumberHelper.canPlaceCallsTo(mNumber);
                final boolean isSipNumber = mPhoneNumberHelper.isSipNumber(mNumber);

                // Let user view contact details if they exist, otherwise add option to create new
                // contact from this number.
                final Intent mainActionIntent;
                final int mainActionIcon;
                final String mainActionDescription;

                final CharSequence nameOrNumber;
                if (!TextUtils.isEmpty(firstDetails.name)) {
                    nameOrNumber = firstDetails.name;
                }
                else {
                    nameOrNumber = firstDetails.number;
                }

                if (contactUri != null) {
                    mainActionIntent = new Intent(Intent.ACTION_VIEW, contactUri);
                    // This will launch People's detail contact screen, so we probably want to
                    // treat it as a separate People task.
                    mainActionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mainActionIcon = R.drawable.ic_contacts_holo_dark;
                    mainActionDescription =  getString(R.string.description_view_contact, nameOrNumber);
                }
                else if (isSipNumber) {
                    // To fix this for SIP addresses, we need to:
                    // - define ContactsContract.Intents.Insert.SIP_ADDRESS, and use it here if
                    //   the current number is a SIP address
                    // - update the contacts UI code to handle Insert.SIP_ADDRESS by
                    //   updating the SipAddress field
                    // and then we can remove the "!isSipNumber" check above.
                    mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    mainActionIntent.setType(RawContacts.CONTENT_ITEM_TYPE);
                    mainActionIntent.putExtra(Insert.SIP_ADDRESS, mNumber);
//                    mainActionIntent.putExtra(Insert.PHONE_TYPE, SipAddress.TYPE_SILENT);
                    mainActionIcon = R.drawable.ic_add_contact_holo_dark;
                    mainActionDescription = getString(R.string.description_add_contact);
                }
                else if (canPlaceCallsTo) {
                    mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    mainActionIntent.setType(RawContacts.CONTENT_ITEM_TYPE);
                    mainActionIntent.putExtra(Insert.PHONE, mNumber);
                    mainActionIcon = R.drawable.ic_add_contact_holo_dark;
                    mainActionDescription = getString(R.string.description_add_contact);
                }
                else {
                    // If we cannot call the number, when we probably cannot add it as a contact either.
                    // This is usually the case of private, unknown, or payphone numbers.
                    mainActionIntent = null;
                    mainActionIcon = 0;
                    mainActionDescription = null;
                }

                if (mainActionIntent == null) {
                    mMainActionView.setVisibility(View.INVISIBLE);
                    mMainActionPushLayerView.setVisibility(View.GONE);
                    mHeaderTextView.setVisibility(View.INVISIBLE);
                    mHeaderOverlayView.setVisibility(View.INVISIBLE);
                }
                else {
                    mMainActionView.setVisibility(View.VISIBLE);
                    mMainActionView.setImageResource(mainActionIcon);
                    mMainActionPushLayerView.setVisibility(View.VISIBLE);
                    mMainActionPushLayerView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(mainActionIntent);
                        }
                    });
                    mMainActionPushLayerView.setContentDescription(mainActionDescription);
                    mHeaderTextView.setVisibility(View.VISIBLE);
                    mHeaderOverlayView.setVisibility(View.VISIBLE);
                }

                // This action allows to call the number that places the call.
                if (canPlaceCallsTo) {
                    final CharSequence displayNumber = mPhoneNumberHelper.getDisplayNumber(
                                    firstDetails.number, firstDetails.formattedNumber);

                    ViewEntry entry = new ViewEntry(
                            getString(R.string.menu_callNumber,
                                    FormatUtils.forceLeftToRight(displayNumber)),
                                    ContactsUtils.getCallIntent(mNumber),
                                    getString(R.string.description_call, nameOrNumber));

                    // Only show a label if the number is shown and it is not a SIP address.
                    if (!TextUtils.isEmpty(firstDetails.name)
                            && !TextUtils.isEmpty(firstDetails.number)
                            && !com.silentcircle.contacts.utils.PhoneNumberHelper.isUriNumber(firstDetails.number.toString())) {
                        entry.label = PhoneLabel.getTypeLabel(mResources, firstDetails.numberType, firstDetails.numberLabel);
                    }

                    // The secondary action allows to send an SMS to the number that placed the
                    // call.
                    if (mPhoneNumberHelper.canSendSmsTo(mNumber)) {
                        entry.setSecondaryAction(R.drawable.ic_text_holo_dark, new Intent(Intent.ACTION_SENDTO,
                                           Uri.fromParts("sms", mNumber, null)),
                                getString(R.string.description_send_text_message, nameOrNumber));
                    }

                    configureCallButton(entry);
                    mPhoneNumberToCopy = displayNumber;
                    mPhoneNumberLabelToCopy = entry.label;
                }
                else {
                    disableCallButton();
                    mPhoneNumberToCopy = null;
                    mPhoneNumberLabelToCopy = null;
                }

                mHasEditNumberBeforeCallOption = canPlaceCallsTo && !isSipNumber;
                mHasRemoveFromCallLogOption = true; // !hasVoicemail();
                invalidateOptionsMenu();

                ListView historyList = (ListView) findViewById(R.id.history);
                historyList.setAdapter(
                        new CallDetailHistoryAdapter(ScCallDetailActivity.this, mInflater,
                                mCallTypeHelper, details, false /*hasVoicemail()*/, canPlaceCallsTo,
                                findViewById(R.id.controls)));
                BackScrollManager.bind(
                        new BackScrollManager.ScrollableHeader() {
                            private View mControls = findViewById(R.id.controls);
                            private View mPhoto = findViewById(R.id.contact_background_sizer);
                            private View mHeader = findViewById(R.id.photo_text_bar);
                            private View mSeparator = findViewById(R.id.blue_separator);

                            @Override
                            public void setOffset(int offset) {
                                    mControls.setY(-offset);
                            }

                            @Override
                            public int getMaximumScrollableHeaderOffset() {
                                // We can scroll the photo out, but we should keep the header if
                                // present.
                                if (mHeader.getVisibility() == View.VISIBLE) {
                                    return mPhoto.getHeight() - mHeader.getHeight();
                                }
                                else {
                                    // If the header is not present, we should also scroll out the
                                    // separator line.
                                    return mPhoto.getHeight() + mSeparator.getHeight();
                                }
                            }
                        },
                        historyList
                );
                loadContactPhotos(photoUri);
                findViewById(R.id.call_detail).setVisibility(View.VISIBLE);
            }
        }
        mAsyncTaskExecutor.submit(Tasks.UPDATE_PHONE_CALL_DETAILS, new UpdateContactDetailsTask());
    }

    /** Return the phone call details for a given call log URI. */
    private PhoneCallDetails getPhoneCallDetailsForUri(Uri callUri) {
        ContentResolver resolver = getContentResolver();
        Cursor callCursor = resolver.query(callUri, CALL_LOG_PROJECTION, null, null, null);
        try {
            if (callCursor == null || !callCursor.moveToFirst()) {
                throw new IllegalArgumentException("Cannot find content: " + callUri);
            }

            // Read call log specifics.
            String number = callCursor.getString(NUMBER_COLUMN_INDEX);
            long date = callCursor.getLong(DATE_COLUMN_INDEX);
            long duration = callCursor.getLong(DURATION_COLUMN_INDEX);
            int callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
            String countryIso = callCursor.getString(COUNTRY_ISO_COLUMN_INDEX);
            final String geocode = callCursor.getString(GEOCODED_LOCATION_COLUMN_INDEX);

            if (TextUtils.isEmpty(countryIso)) {
                countryIso = mDefaultCountryIso;
            }

            // Formatted phone number.
            final CharSequence formattedNumber;
            // Read contact specifics.
            final CharSequence nameText;
            final int numberType;
            final CharSequence numberLabel;
            final Uri photoUri;
            final Uri lookupUri;
            // If this is not a regular number, there is no point in looking it up in the contacts.
            ContactInfo info = mPhoneNumberHelper.canPlaceCallsTo(number) ?
                    mContactInfoHelper.lookupNumber(number, countryIso) : null;
            if (info == null) {
                formattedNumber = mPhoneNumberHelper.getDisplayNumber(number, null);
                nameText = "";
                numberType = 0;
                numberLabel = "";
                photoUri = null;
                lookupUri = null;
            }
            else {
                formattedNumber = info.formattedNumber;
                nameText = info.name;
                numberType = info.type;
                numberLabel = info.label;
                photoUri = info.photoUri;
                lookupUri = info.lookupUri;
            }
            return new PhoneCallDetails(number, formattedNumber, countryIso, geocode, new int[] { callType }, date, duration,
                    nameText, numberType, numberLabel, lookupUri, photoUri);
        }
        finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
    }

    /** Load the contact photos and places them in the corresponding views. */
    private void loadContactPhotos(Uri photoUri) {
        mContactPhotoManager.loadPhoto(mContactBackgroundView, photoUri, mContactBackgroundView.getWidth(), true /* dark */,
                false /*not circular*/, null);
    }

    static final class ViewEntry {
        public final String text;
        public final Intent primaryIntent;
        /** The description for accessibility of the primary action. */
        public final String primaryDescription;

        public CharSequence label = null;
        /** Icon for the secondary action. */
        public int secondaryIcon = 0;
        /** Intent for the secondary action. If not null, an icon must be defined. */
        public Intent secondaryIntent = null;
        /** The description for accessibility of the secondary action. */
        public String secondaryDescription = null;

        public ViewEntry(String text, Intent intent, String description) {
            this.text = text;
            primaryIntent = intent;
            primaryDescription = description;
        }

        public void setSecondaryAction(int icon, Intent intent, String description) {
            secondaryIcon = icon;
            secondaryIntent = intent;
            secondaryDescription = description;
        }
    }

    /** Disables the call button area, e.g., for private numbers. */
    private void disableCallButton() {
        findViewById(R.id.call_and_sms).setVisibility(View.GONE);
    }

    /** Configures the call button area using the given entry. */
    private void configureCallButton(ViewEntry entry) {
        View convertView = findViewById(R.id.call_and_sms);
        convertView.setVisibility(View.VISIBLE);

        ImageView icon = (ImageView) convertView.findViewById(R.id.call_and_sms_icon);
        View divider = convertView.findViewById(R.id.call_and_sms_divider);
        TextView text = (TextView) convertView.findViewById(R.id.call_and_sms_text);

        View mainAction = convertView.findViewById(R.id.call_and_sms_main_action);
        mainAction.setOnClickListener(mPrimaryActionListener);
        mainAction.setTag(entry);
        mainAction.setContentDescription(entry.primaryDescription);
        mainAction.setOnLongClickListener(mPrimaryLongClickListener);

        if (entry.secondaryIntent != null) {
            icon.setOnClickListener(mSecondaryActionListener);
            icon.setImageResource(entry.secondaryIcon);
            icon.setVisibility(View.VISIBLE);
            icon.setTag(entry);
            icon.setContentDescription(entry.secondaryDescription);
            divider.setVisibility(View.VISIBLE);
        }
        else {
            icon.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        }
        text.setText(entry.text);

        TextView label = (TextView) convertView.findViewById(R.id.call_and_sms_label);
        if (TextUtils.isEmpty(entry.label)) {
            label.setVisibility(View.GONE);
        }
        else {
            label.setText(entry.label);
            label.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_details_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // This action deletes all elements in the group from the call log.
        // We don't have this action for voicemails, because you can just use the trash button.
        menu.findItem(R.id.menu_remove_from_call_log).setVisible(mHasRemoveFromCallLogOption);
        menu.findItem(R.id.menu_remove_same_from_call_log).setVisible(mHasRemoveFromCallLogOption);
        menu.findItem(R.id.menu_edit_number_before_call).setVisible(mHasEditNumberBeforeCallOption);
        return super.onPrepareOptionsMenu(menu);
    }

    public void onMenuRemoveFromCallLog(MenuItem menuItem) {
        final StringBuilder callIds = new StringBuilder();
        for (Uri callUri : getCallLogEntryUris()) {
            if (callIds.length() != 0) {
                callIds.append(",");
            }
            callIds.append(ContentUris.parseId(callUri));
        }
        mAsyncTaskExecutor.submit(Tasks.REMOVE_FROM_CALL_LOG_AND_FINISH,
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        getContentResolver().delete(ScCalls.CONTENT_URI, ScCalls._ID + " IN (" + callIds + ")", null);
                        return null;
                    }

                    @Override
                    public void onPostExecute(Void result) {
                        finish();
                    }
                });
    }

    public void onMenuRemoveSameFromCallLog(MenuItem menuItem) {
        final StringBuilder callIds = new StringBuilder();
        for (Uri callUri : getCallLogEntryUris()) {
            if (callIds.length() != 0) {
                callIds.append(",");
            }
            callIds.append(ContentUris.parseId(callUri));
        }
        mAsyncTaskExecutor.submit(Tasks.REMOVE_FROM_CALL_LOG_AND_FINISH,
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        getContentResolver().delete(ScCalls.CONTENT_URI,
                                ScCallLog.ScCalls.NUMBER + " =?", new String[]{mNumber});
                        return null;
                    }

                    @Override
                    public void onPostExecute(Void result) {
                        finish();
                    }
                });
    }

    public void onMenuEditNumberBeforeCall(MenuItem menuItem) {
        Intent intent = new Intent(ContactsUtils.getEditBeforeCallAction(), ContactsUtils.getCallUri(mNumber));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
    }

    private void configureActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        }
    }

    @Override
    protected void onPause() {
        // Immediately stop the proximity sensor.
//        disableProximitySensor(false);
//        mProximitySensorListener.clearPendingRequests();
        super.onPause();
    }

//    @Override
//    public void enableProximitySensor() {
//        mProximitySensorManager.enable();
//    }

//    @Override
//    public void disableProximitySensor(boolean waitForFarState) {
//        mProximitySensorManager.disable(waitForFarState);
//    }

    /**
     * If the phone number is selected, unselect it and return {@code true}.
     * Otherwise, just {@code false}.
     */
    private boolean finishPhoneNumberSelectedActionModeIfShown() {
        if (mPhoneNumberActionMode == null) return false;
//        mPhoneNumberActionMode.finish();
//        return true;
        return false;
    }

//    private void startPhoneNumberSelectedActionMode(View targetView) {
//        mPhoneNumberActionMode = startActionMode(new PhoneNumberActionModeCallback(targetView));
//    }
//
    private void closeSystemDialogs() {
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
//
//    private class PhoneNumberActionModeCallback implements ActionMode.Callback {
//        private final View mTargetView;
//        private final Drawable mOriginalViewBackground;
//
//        public PhoneNumberActionModeCallback(View targetView) {
//            mTargetView = targetView;
//
//            // Highlight the phone number view.  Remember the old background, and put a new one.
//            mOriginalViewBackground = mTargetView.getBackground();
//            mTargetView.setBackgroundColor(getResources().getColor(R.color.item_selected));
//        }
//
//        @Override
//        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
//            if (TextUtils.isEmpty(mPhoneNumberToCopy)) return false;
//
//            getMenuInflater().inflate(R.menu.call_details_cab, menu);
//            return true;
//        }
//
//        @Override
//        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
//            return true;
//        }
//
//        @Override
//        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
//            switch (item.getItemId()) {
//                case R.id.copy_phone_number:
//                    ClipboardUtils.copyText(ScCallDetailActivity.this, mPhoneNumberLabelToCopy,
//                            mPhoneNumberToCopy, true);
//                    mode.finish(); // Close the CAB
//                    return true;
//            }
//            return false;
//        }
//
//        @Override
//        public void onDestroyActionMode(ActionMode mode) {
//            mPhoneNumberActionMode = null;
//
//            // Restore the view background.
//            mTargetView.setBackground(mOriginalViewBackground);
//        }
//    }
}
