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
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.WorkerThread;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.GeoUtil;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.calllognew.CallDetailHistoryAdapter;
import com.silentcircle.contacts.calllognew.CallTypeHelper;
import com.silentcircle.contacts.calllognew.ContactInfo;
import com.silentcircle.contacts.calllognew.ContactInfoHelper;
import com.silentcircle.contacts.calllognew.PhoneNumberDisplayHelper;
import com.silentcircle.contacts.calllognew.PhoneNumberUtilsWrapper;
import com.silentcircle.contacts.utils.AsyncTaskExecutor;
import com.silentcircle.contacts.utils.AsyncTaskExecutors;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.contacts.ScCallLog.ScCalls;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Displays the details of a specific call log entry.
 * <p>
 * This activity can be either started with the URI of a single call log entry, or with the
 * {@link #EXTRA_CALL_LOG_IDS} extra to specify a group of call log entries.
 */
public class ScCallDetailActivity extends AppCompatActivity /*implements ProximitySensorAware */ {
    private static final String TAG = "ScCallDetail";

    private static final char LEFT_TO_RIGHT_EMBEDDING = '\u202A';
    private static final char POP_DIRECTIONAL_FORMATTING = '\u202C';

    /** The time to wait before enabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_BLANK_DELAY_MILLIS = 100;
    /** The time to wait before disabling the blank the screen due to the proximity sensor. */
    private static final long PROXIMITY_UNBLANK_DELAY_MILLIS = 500;

    /** The enumeration of {@link android.os.AsyncTask} objects used in this class. */
    public enum Tasks {
        MARK_VOICEMAIL_READ,
        DELETE_VOICEMAIL_AND_FINISH,
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

//    public static final String VOICEMAIL_FRAGMENT_TAG = "voicemail_fragment";

    private CallTypeHelper mCallTypeHelper;
    private PhoneNumberDisplayHelper mPhoneNumberHelper;
    private QuickContactBadge mQuickContactBadge;
    private TextView mCallerName;
    private TextView mCallerNumber;
    private TextView mAccountLabel;
    private AsyncTaskExecutor mAsyncTaskExecutor;
    private ContactInfoHelper mContactInfoHelper;

    private String mNumber = null;
    private String mDefaultCountryIso;
    private String mUuid;

    LayoutInflater mInflater;
    Resources mResources;
    /** Helper to load contact photos. */
    private ContactPhotoManagerNew mContactPhotoManager;
    /** Helper to make async queries to content resolver. */
//    private CallDetailActivityQueryHandler mAsyncQueryHandler;  // For voicemail only
    /** Helper to get voicemail status messages. */
//   private VoicemailStatusHelper mVoicemailStatusHelper;
    // Views related to voicemail status message.
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;
    private TextView mVoicemailTranscription;
    private LinearLayout mVoicemailHeader;

    private Uri mVoicemailUri;
    private BidiFormatter mBidiFormatter;

    /** Whether we should show "edit number before call" in the options menu. */
    private boolean mHasEditNumberBeforeCallOption;
    /** Whether we should show "trash" in the options menu. */
    private boolean mHasTrashOption;
    /** Whether we should show "remove from call log" in the options menu. */
    private boolean mHasRemoveFromCallLogOption;

//    private ProximitySensorManager mProximitySensorManager;
//    private final ProximitySensorListener mProximitySensorListener = new ProximitySensorListener();

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

    static final String[] CALL_LOG_PROJECTION = new String[] {
        ScCallLog.ScCalls.DATE,
        ScCallLog.ScCalls.DURATION,
        ScCallLog.ScCalls.NUMBER,
        ScCallLog.ScCalls.TYPE,
        ScCallLog.ScCalls.COUNTRY_ISO,
        ScCallLog.ScCalls.GEOCODED_LOCATION,
        ScCalls.SC_OPTION_TEXT2
//        CallLog.Calls.NUMBER_PRESENTATION,
//        CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
//        CallLog.Calls.PHONE_ACCOUNT_ID,
//        CallLog.Calls.FEATURES,
//        CallLog.Calls.DATA_USAGE,
//        CallLog.Calls.TRANSCRIPTION
    };

    static final int DATE_COLUMN_INDEX = 0;
    static final int DURATION_COLUMN_INDEX = 1;
    static final int NUMBER_COLUMN_INDEX = 2;
    static final int CALL_TYPE_COLUMN_INDEX = 3;
    static final int COUNTRY_ISO_COLUMN_INDEX = 4;
    static final int GEOCODED_LOCATION_COLUMN_INDEX = 5;
    public static final int SC_OPTION_TEXT2 = 6;
    static final int NUMBER_PRESENTATION_COLUMN_INDEX = 6;
    static final int ACCOUNT_COMPONENT_NAME = 7;
    static final int ACCOUNT_ID = 8;
    static final int FEATURES = 9;
    static final int DATA_USAGE = 10;
    static final int TRANSCRIPTION_COLUMN_INDEX = 11;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ViewUtil.setBlockScreenshots(this);

        setContentView(R.layout.call_detail);

        final Toolbar toolbar = (Toolbar)findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            mBidiFormatter = BidiFormatter.getInstance();

        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
        mInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mResources = getResources();

        mCallTypeHelper = new CallTypeHelper(this);
        mPhoneNumberHelper = new PhoneNumberDisplayHelper(/*this,*/ mResources);
//        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
//        mAsyncQueryHandler = new CallDetailActivityQueryHandler(this);

//        mVoicemailUri = getIntent().getParcelableExtra(EXTRA_VOICEMAIL_URI);

        mQuickContactBadge = (QuickContactBadge) findViewById(R.id.quick_contact_photo);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mQuickContactBadge.setOverlay(null);
        mCallerName = (TextView) findViewById(R.id.caller_name);
        mCallerNumber = (TextView) findViewById(R.id.caller_number);
        mAccountLabel = (TextView) findViewById(R.id.phone_account_label);
        mDefaultCountryIso = GeoUtil.getCurrentCountryIso(this);
        mContactPhotoManager = ContactPhotoManagerNew.getInstance(this);
//        mProximitySensorManager = new ProximitySensorManager(this, mProximitySensorListener);
        mContactInfoHelper = new ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

//        optionallyHandleVoicemail();
        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            closeSystemDialogs();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateData(getCallLogEntryUris());
    }

//    /**
//     * Handle voicemail playback or hide voicemail ui.
//     * <p>
//     * If the Intent used to start this Activity contains the suitable extras, then start voicemail
//     * playback.  If it doesn't, then don't inflate the voicemail ui.
//     */
//    private void optionallyHandleVoicemail() {
//
//        if (hasVoicemail()) {
//            LayoutInflater inflater =
//                    (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//            mVoicemailHeader =
//                    (LinearLayout) inflater.inflate(R.layout.call_details_voicemail_header, null);
//            View voicemailContainer = mVoicemailHeader.findViewById(R.id.voicemail_container);
//            mStatusMessageView = mVoicemailHeader.findViewById(R.id.voicemail_status);
//            mStatusMessageText =
//                    (TextView) mVoicemailHeader.findViewById(R.id.voicemail_status_message);
//            mStatusMessageAction =
//                    (TextView) mVoicemailHeader.findViewById(R.id.voicemail_status_action);
//            mVoicemailTranscription = (
//                    TextView) mVoicemailHeader.findViewById(R.id.voicemail_transcription);
//            ListView historyList = (ListView) findViewById(R.id.history);
//            historyList.addHeaderView(mVoicemailHeader);
//            // Has voicemail: add the voicemail fragment.  Add suitable arguments to set the uri
//            // to play and optionally start the playback.
//            // Do a query to fetch the voicemail status messages.
//            VoicemailPlaybackFragment playbackFragment;
//
//            playbackFragment = (VoicemailPlaybackFragment) getFragmentManager().findFragmentByTag(
//                    VOICEMAIL_FRAGMENT_TAG);
//
//            if (playbackFragment == null) {
//                playbackFragment = new VoicemailPlaybackFragment();
//                Bundle fragmentArguments = new Bundle();
//                fragmentArguments.putParcelable(EXTRA_VOICEMAIL_URI, mVoicemailUri);
//                if (getIntent().getBooleanExtra(EXTRA_VOICEMAIL_START_PLAYBACK, false)) {
//                    fragmentArguments.putBoolean(EXTRA_VOICEMAIL_START_PLAYBACK, true);
//                }
//                playbackFragment.setArguments(fragmentArguments);
//                getFragmentManager().beginTransaction()
//                        .add(R.id.voicemail_container, playbackFragment, VOICEMAIL_FRAGMENT_TAG)
//                                .commitAllowingStateLoss();
//            }
//
//            voicemailContainer.setVisibility(View.VISIBLE);
//            mAsyncQueryHandler.startVoicemailStatusQuery(mVoicemailUri);
//            markVoicemailAsRead(mVoicemailUri);
//        }
//    }
//
    private boolean hasVoicemail() {
        return mVoicemailUri != null;
    }

//    private void markVoicemailAsRead(final Uri voicemailUri) {
//        mAsyncTaskExecutor.submit(Tasks.MARK_VOICEMAIL_READ, new AsyncTask<Void, Void, Void>() {
//            @Override
//            public Void doInBackground(Void... params) {
//                ContentValues values = new ContentValues();
//                values.put(VoicemailContract.Voicemails.IS_READ, true);
//                getContentResolver().update(voicemailUri, values,
//                        VoicemailContract.Voicemails.IS_READ + " = 0", null);
//                return null;
//            }
//        });
//    }

    /**
     * Returns the list of URIs to show.
     * <p>
     * There are two ways the URIs can be provided to the activity: as the data on the intent, or as
     * a list of ids in the call log added as an extra on the URI.
     * <p>
     * If both are available, the data on the intent takes precedence.
     */
    private Uri[] getCallLogEntryUris() {
        final Uri uri = getIntent().getData();
        if (uri != null) {
            // If there is a data on the intent, it takes precedence over the extra.
            return new Uri[]{ uri };
        }
        final long[] ids = getIntent().getLongArrayExtra(EXTRA_CALL_LOG_IDS);
        final int numIds = ids == null ? 0 : ids.length;
        final Uri[] uris = new Uri[numIds];
        for (int index = 0; index < numIds; ++index) {
            uris[index] = ContentUris.withAppendedId(ScCalls.CONTENT_URI, ids[index]);
        }
        return uris;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                // Make sure phone isn't already busy before starting direct call
                TelephonyManager tm = (TelephonyManager)
                        getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    DialerUtils.startActivityWithErrorToast(this,
                            ContactsUtils.getCallIntent(Uri.fromParts(Constants.SCHEME_TEL, mNumber,
                                    null)), R.string.call_not_available);
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
                Context context = ScCallDetailActivity.this;

                if (details == null) {
                    // Somewhere went wrong: we're going to bail out and show error to users.
                    Toast.makeText(context, R.string.toast_call_detail_error,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // We know that all calls are from the same number and the same contact, so pick the
                // first.
                PhoneCallDetails firstDetails = details[0];
                mNumber = firstDetails.number.toString();
                mUuid = firstDetails.uuid;
//                final int numberPresentation = firstDetails.numberPresentation;
                final Uri contactUri = firstDetails.contactUri;
                final Uri photoUri = firstDetails.photoUri;
//                final PhoneAccountHandle accountHandle = firstDetails.accountHandle;

                // Cache the details about the phone number.
                final boolean canPlaceCallsTo =
                    PhoneNumberUtilsWrapper.canPlaceCallsTo(mNumber, /*numberPresentation*/ 0);
//                final PhoneNumberUtilsWrapper phoneUtils = PhoneNumberUtilsWrapper.INSTANCE; // new PhoneNumberUtilsWrapper(context);
//                final boolean isVoicemailNumber = phoneUtils.isVoicemailNumber(accountHandle, mNumber);
                final boolean isSipNumber = com.silentcircle.contacts.utils.PhoneNumberHelper.isUriNumber(mNumber);
                final boolean isUuid = !TextUtils.isEmpty(mUuid);

                final CharSequence callLocationOrType = getNumberTypeOrLocation(firstDetails);

                // Don't show the UUID string in the header
                final CharSequence displayNumber = isUuid ?  "" :
                        mPhoneNumberHelper.getDisplayNumber(
                                firstDetails.number,
                                1, // presentation allowed
                                firstDetails.formattedNumber);
                final String displayNumberStr;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    displayNumberStr = mBidiFormatter.unicodeWrap(
                            displayNumber.toString(), TextDirectionHeuristics.LTR);
                }
                else
                    displayNumberStr = displayNumber.toString();

                if (!TextUtils.isEmpty(firstDetails.name)) {
                    mCallerName.setText(firstDetails.name);
                    mCallerNumber.setText(callLocationOrType + " " + displayNumberStr);
                } else {
                    mCallerName.setText(displayNumberStr);
                    if (!TextUtils.isEmpty(callLocationOrType)) {
                        mCallerNumber.setText(callLocationOrType);
                        mCallerNumber.setVisibility(View.VISIBLE);
                    } else {
                        mCallerNumber.setVisibility(View.GONE);
                    }
                }

//                String accountLabel = null; // PhoneAccountUtils.getAccountLabel(context, accountHandle);
//                if (!TextUtils.isEmpty(accountLabel)) {
//                    mAccountLabel.setText(accountLabel);
//                    mAccountLabel.setVisibility(View.VISIBLE);
//                } else {
                    mAccountLabel.setVisibility(View.GONE);
//                }
//                else if (isSipNumber) {
//                    // To fix this for SIP addresses, we need to:
//                    // - define ContactsContract.Intents.Insert.SIP_ADDRESS, and use it here if
//                    //   the current number is a SIP address
//                    // - update the contacts UI code to handle Insert.SIP_ADDRESS by
//                    //   updating the SipAddress field
//                    // and then we can remove the "!isSipNumber" check above.
//                    mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
//                    mainActionIntent.setType(RawContacts.CONTENT_ITEM_TYPE);
//                    mainActionIntent.putExtra(Insert.SIP_ADDRESS, mNumber);
////                    mainActionIntent.putExtra(Insert.PHONE_TYPE, SipAddress.TYPE_SILENT);
//                    mainActionIcon = R.drawable.ic_add_contact_holo_dark;
//                    mainActionDescription = getString(R.string.description_add_contact);
//                }
//                else if (canPlaceCallsTo) {
//                    mainActionIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
//                    mainActionIntent.setType(RawContacts.CONTENT_ITEM_TYPE);
//                    mainActionIntent.putExtra(Insert.PHONE, mNumber);
//                    mainActionIcon = R.drawable.ic_add_contact_holo_dark;
//                    mainActionDescription = getString(R.string.description_add_contact);
//                }
//                else {
//                    // If we cannot call the number, when we probably cannot add it as a contact either.
//                    // This is usually the case of private, unknown, or payphone numbers.
//                    mainActionIntent = null;
//                    mainActionIcon = 0;
//                    mainActionDescription = null;
//                }

                mHasEditNumberBeforeCallOption = canPlaceCallsTo && !isSipNumber && !isUuid; // && !isVoicemailNumber;
                mHasTrashOption = hasVoicemail();
                mHasRemoveFromCallLogOption = !hasVoicemail();
                invalidateOptionsMenu();

                ListView historyList = (ListView) findViewById(R.id.history);
                historyList.setAdapter(
                        new CallDetailHistoryAdapter(context, mInflater, mCallTypeHelper, details));

                String lookupKey = contactUri == null ? null
                        : ContactInfoHelper.getLookupKeyFromUri(contactUri);

                final boolean isBusiness = false; // mContactInfoHelper.isBusiness(firstDetails.sourceType);

                final int contactType =
//                        isVoicemailNumber? ContactPhotoManagerNew.TYPE_VOICEMAIL :
                        isBusiness ? ContactPhotoManagerNew.TYPE_BUSINESS :
                        ContactPhotoManagerNew.TYPE_DEFAULT;

                String nameForDefaultImage;
                if (TextUtils.isEmpty(firstDetails.name)) {
                    nameForDefaultImage = mPhoneNumberHelper.getDisplayNumber(
                            firstDetails.number,
                            1,
                            firstDetails.formattedNumber).toString();
                } else {
                    nameForDefaultImage = firstDetails.name.toString();
                }

//                if (hasVoicemail() && !TextUtils.isEmpty(firstDetails.transcription)) {
//                    mVoicemailTranscription.setText(firstDetails.transcription);
//                    mVoicemailTranscription.setVisibility(View.VISIBLE);
//                }

                loadContactPhotos(
                        contactUri, photoUri, nameForDefaultImage, lookupKey, contactType);
                findViewById(R.id.call_detail).setVisibility(View.VISIBLE);
            }

            /**
             * Determines the location geocode text for a call, or the phone number type
             * (if available).
             *
             * @param details The call details.
             * @return The phone number type or location.
             */
            private CharSequence getNumberTypeOrLocation(PhoneCallDetails details) {
                if (!TextUtils.isEmpty(details.name)) {
                    return Phone.getTypeLabel(mResources, details.numberType,
                            details.numberLabel);
                } else {
                    return details.geocode;
                }
            }
        }
        mAsyncTaskExecutor.submit(Tasks.UPDATE_PHONE_CALL_DETAILS, new UpdateContactDetailsTask());
    }

    /** Return the phone call details for a given call log URI. */
    @WorkerThread
    private PhoneCallDetails getPhoneCallDetailsForUri(Uri callUri) {
        ContentResolver resolver = getContentResolver();
        Cursor callCursor = resolver.query(callUri, CALL_LOG_PROJECTION, null, null, null);
        try {
            if (callCursor == null || !callCursor.moveToFirst()) {
                throw new IllegalArgumentException("Cannot find content: " + callUri);
            }

//            DatabaseUtils.dumpCursor(callCursor);
            // Read call log specifics.
            String number = callCursor.getString(NUMBER_COLUMN_INDEX);
//            final int numberPresentation = callCursor.getInt(
//                    NUMBER_PRESENTATION_COLUMN_INDEX);
            final long date = callCursor.getLong(DATE_COLUMN_INDEX);
            final long duration = callCursor.getLong(DURATION_COLUMN_INDEX);
            final int callType = callCursor.getInt(CALL_TYPE_COLUMN_INDEX);
            String countryIso = callCursor.getString(COUNTRY_ISO_COLUMN_INDEX);
            final String geocode = callCursor.getString(GEOCODED_LOCATION_COLUMN_INDEX);
            final String displayNameSip = callCursor.getString(SC_OPTION_TEXT2);

//            final String transcription = callCursor.getString(TRANSCRIPTION_COLUMN_INDEX);

//            final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
//                    callCursor.getString(ACCOUNT_COMPONENT_NAME),
//                    callCursor.getString(ACCOUNT_ID));
//
            if (TextUtils.isEmpty(countryIso)) {
                countryIso = mDefaultCountryIso;
            }

            // Formatted phone number.
            CharSequence formattedNumber;
            // Read contact specifics.
            final CharSequence nameText;
            final int numberType;
            final CharSequence numberLabel;
            final Uri photoUri;
            final Uri lookupUri;
            // If this is not a regular number, there is no point in looking it up in the contacts.
            ContactInfo info =
                    PhoneNumberUtilsWrapper.canPlaceCallsTo(number, 0)
//                    && !new PhoneNumberUtilsWrapper(this).isVoicemailNumber(accountHandle, number)
                            ? mContactInfoHelper.lookupNumber(number, countryIso)
                            : null;
            if (info == null) {
                formattedNumber = number;
                nameText = "";
                numberType = 0;
                numberLabel = "";
                photoUri = null;
                lookupUri = null;
            }
            else {
                formattedNumber = info.formattedNumber;
                if (SearchUtil.isUuid(formattedNumber.toString())) {
                    info.uuid = info.number;
                    if (info.name != null)
                        number = info.name;                 // Don't show the UUID URI as number
                }
                nameText = info.name;
                numberType = info.type;
                numberLabel = info.label;
                photoUri = info.photoUri;
                lookupUri = info.lookupUri;
            }
            formattedNumber = TextUtils.isEmpty(displayNameSip) ? formattedNumber : displayNameSip;
//            final int features = callCursor.getInt(FEATURES);
//            Long dataUsage = null;
//            if (!callCursor.isNull(DATA_USAGE)) {
//                dataUsage = callCursor.getLong(DATA_USAGE);
//            }
            return new PhoneCallDetails(number,
                    formattedNumber, countryIso, geocode,
                    new int[]{ callType }, date, duration,
                    nameText, numberType, numberLabel, lookupUri, photoUri, info.uuid );
        } finally {
            if (callCursor != null) {
                callCursor.close();
            }
        }
    }

    /** Load the contact photos and places them in the corresponding views. */
    private void loadContactPhotos(Uri contactUri, Uri photoUri, String displayName,
            String lookupKey, int contactType) {

        final ContactPhotoManagerNew.DefaultImageRequest request = new ContactPhotoManagerNew.DefaultImageRequest(displayName, lookupKey,
                contactType, true /* isCircular */);

// issue NGA-386        mQuickContactBadge.assignContactUri(contactUri);
        mQuickContactBadge.setContentDescription(
                mResources.getString(R.string.description_contact_details, displayName));

        mContactPhotoManager.loadDirectoryPhoto(mQuickContactBadge, photoUri,
                false /* darkTheme */, true /* isCircular */, request);
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

//    protected void updateVoicemailStatusMessage(Cursor statusCursor) {
//        if (statusCursor == null) {
//            mStatusMessageView.setVisibility(View.GONE);
//            return;
//        }
//        final StatusMessage message = getStatusMessage(statusCursor);
//        if (message == null || !message.showInCallDetails()) {
//            mStatusMessageView.setVisibility(View.GONE);
//            return;
//        }
//
//        mStatusMessageView.setVisibility(View.VISIBLE);
//        mStatusMessageText.setText(message.callDetailsMessageId);
//        if (message.actionMessageId != -1) {
//            mStatusMessageAction.setText(message.actionMessageId);
//        }
//        if (message.actionUri != null) {
//            mStatusMessageAction.setClickable(true);
//            mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    DialerUtils.startActivityWithErrorToast(CallDetailActivity.this,
//                            new Intent(Intent.ACTION_VIEW, message.actionUri));
//                }
//            });
//        } else {
//            mStatusMessageAction.setClickable(false);
//        }
//    }

//    private StatusMessage getStatusMessage(Cursor statusCursor) {
//        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
//        if (messages.size() == 0) {
//            return null;
//        }
//        // There can only be a single status message per source package, so num of messages can
//        // at most be 1.
//        if (messages.size() > 1) {
//            Log.w(TAG, String.format("Expected 1, found (%d) num of status messages." +
//                    " Will use the first one.", messages.size()));
//        }
//        return messages.get(0);
//    }

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
                        String[] selectArg = TextUtils.isEmpty(mUuid) ? new String[]{mNumber} : new String[]{mUuid};
                        getContentResolver().delete(ScCalls.CONTENT_URI,
                                ScCallLog.ScCalls.NUMBER + " =?", selectArg);
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
    public void enableProximitySensor() {
//        mProximitySensorManager.enable();
    }

//    @Override
    public void disableProximitySensor(boolean waitForFarState) {
//        mProximitySensorManager.disable(waitForFarState);
    }

//    private void startPhoneNumberSelectedActionMode(View targetView) {
//        mPhoneNumberActionMode = startActionMode(new PhoneNumberActionModeCallback(targetView));
//    }
//
    private void closeSystemDialogs() {
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    /** Returns the given text, forced to be left-to-right. */
    private static CharSequence forceLeftToRight(CharSequence text) {
        StringBuilder sb = new StringBuilder();
        sb.append(LEFT_TO_RIGHT_EMBEDDING);
        sb.append(text);
        sb.append(POP_DIRECTIONAL_FORMATTING);
        return sb.toString();
    }
}
