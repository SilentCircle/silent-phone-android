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

package com.silentcircle.silentphone2.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;

import java.io.InputStream;


public class CallState {

    private static final String LOG_TAG = "CallState";

    public final static int LOW = 0;
    public final static int NORMAL = 1;
    public final static int URGENT = 2;
    public final static int EMERGENCY = 3;

    public StringBuildHelper zrtpWarning = new StringBuildHelper(256);
    public StringBuildHelper zrtpPEER = new StringBuildHelper();
    private StringBuildHelper nameFromAB = new StringBuildHelper();//  from phoneBook  or sip

    public StringBuildHelper mAssertedName = new StringBuildHelper(); // getInfo(call.iEngID, call.iCallId, "AssertedId");

    public StringBuildHelper bufDialed = new StringBuildHelper();
    public StringBuildHelper bufPeer = new StringBuildHelper();
    public StringBuildHelper bufServerName = new StringBuildHelper();
    public StringBuildHelper bufMsg = new StringBuildHelper(512);

    public StringBuildHelper bufSAS = new StringBuildHelper();
    public StringBuildHelper bufSecureMsg = new StringBuildHelper();
    public StringBuildHelper bufSecureMsgV = new StringBuildHelper();

    public boolean iShowEnroll, iShowVerifySas;
    public int iShowWarningForNSec;

    /**
     * If true this Call info object is in use, managed by CTCalls class.
     */
    public boolean iInUse;

    /**
     * Set by phone engine to current system time if the call started (eStartCall).
     */
    public long uiStartTime;

    /**
     * Holds the call duration in ms - currently not used (duration computed dynamically).
     */
    public long iDuration;

    /**
     * Set to true by phone engine if incoming call detected (eIncomingCall).
     */
    public boolean iIsIncoming;

    /**
     * Set to true by phone engine if the call started (eStartCall).
     */
    public boolean iActive;

    /**
     * Set to 2 by phone engine if the call ended (eEndCall), otherwise it is 0.
     */
    public boolean callEnded;

    /**
     * If true the call is on hold (not yet used, hold function missing)
     */
    public boolean iIsOnHold;

    /**
     * Set to true if microphone is muted, managed by call window.
     */
    public boolean iMuted;

    public boolean iShowVideoSrcWhenAudioIsSecure;

    public boolean isInConference;

    public boolean iSipHasErrorMessage;

    public boolean iRecentsUpdated;

    /**
     * Security via SDES not via ZRTP
     */
    public boolean sdesActive;

    /** If true then the loader for the contact data is still running */
    public boolean mContactsLoaderActive;

    public int iUserDataLoaded;

    public boolean videoMediaActive;
    public boolean videoAccepted;

    /**
     * Set by phone engine to current system time if the call ended (eEndCall)
     */
    public long callReleasedAt;

    /**
     * Set by phone service on <code>eIncomingCall</code> and on <code>eCalling</code> callback messages.
     */
    public int iCallId;

    /**
     * Set by phone service on <code>eIncomingCall</code> and on <code>eCalling</code> callback messages.
     */
    public int iEngID;

    public int iUpdated;

    /**
     * Height of caller image picture if available
     */
    public int iImgHeight;

    /**
     * Keeps the image to reduce re-computations
     */
    public Bitmap image;

    public Uri customRingtoneUri;

    /**
     * Id of raw contact
     *
     * If not null then we have a contact in the contact DB.
     */
    public long contactId;

    /** If no null then a background service saw an exception when accessing SCA data */
    public String secExceptionMsg;

    private boolean contactsDataChecked;
    
    /** True if the user selected a number in native contacts and SPA calls this number via OCA */
    public boolean mPstnViaOca;

    public boolean mAnsweredElsewhere;

    public int mPriority = NORMAL;

    CallState() {
        reset();
    }

    void reset() {

        contactsDataChecked = false;
  
        nameFromAB.reset();
        zrtpWarning.reset();
        zrtpPEER.reset();

        mAssertedName.reset();

        bufDialed.reset();
        bufServerName.reset();
        bufPeer.reset();
        bufMsg.reset();

        bufSAS.reset();
        bufSecureMsg.reset();
        bufSecureMsgV.reset();

        iShowVideoSrcWhenAudioIsSecure = false;

        iInUse = false;
        iIsIncoming = false;
        iActive = false;
        callEnded = false;
        iIsOnHold = false;
        isInConference = false;
        iMuted = false;
        iSipHasErrorMessage = false;
        iRecentsUpdated = false;
        videoMediaActive = false;
        videoAccepted = false;
        sdesActive = false;
        mContactsLoaderActive = false;

        iUserDataLoaded = 0;

        uiStartTime = 0;
        iDuration = 0;
        callReleasedAt = 0;
        iCallId = 0;
        // pEng=NULL;
        iEngID = 0;

        iShowEnroll = false;
        iShowVerifySas = true;

        iShowWarningForNSec = 0;

        iUpdated = 0;
        iImgHeight = 0;

        image = null;
        customRingtoneUri = null;
        contactId = 0;
        secExceptionMsg = null;
        mPstnViaOca = false;
        mAnsweredElsewhere = false;

        mPriority = NORMAL;
    }

    public static String createSipLookupClause(String sipAddress) {
        final StringBuilder sb = new StringBuilder();
        sb.append(SipAddress.SIP_ADDRESS + " LIKE ");
        DatabaseUtils.appendEscapedSQLString(sb, sipAddress + '%');
        sb.append(" AND " + SipAddress.MIMETYPE + "='" + SipAddress.CONTENT_ITEM_TYPE + "'");

        return sb.toString();
    }

    public void fillDataFromContacts(TiviPhoneService service) {
        if (contactsDataChecked || service == null)
            return;

        Uri lookupUri;
        String phoneLookUpId;
        Context ctx = service.getBaseContext();

        String selection = null;
        String[] projection = null;

        if (!PhoneNumberUtils.isGlobalPhoneNumber(bufPeer.toString())) {
            // number is a SIP address: use customized lookup
            lookupUri = ContactsContract.Data.CONTENT_URI;
            phoneLookUpId = ContactsContract.Data.CONTACT_ID;
            selection = createSipLookupClause(bufPeer.toString() + ctx.getString(R.string.sc_sip_domain_0));
            projection = new String[] {ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.DISPLAY_NAME};
        }
        else {
            lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, bufPeer.toString());
            phoneLookUpId = PhoneLookup._ID;
        }
        if (ConfigurationUtilities.mTrace) Log.d(LOG_TAG, "SPA lookup uri: " + lookupUri);

        mContactsLoaderActive = true;
        contactsDataChecked = true;
        LoaderTaskLoadContactData loaderTask = new LoaderTaskLoadContactData(phoneLookUpId, ctx, projection, selection);
        loaderTask.execute(lookupUri);
    }

    public String getNameFromAB() {
        if (nameFromAB.getLen() == 0){
            String s = TiviPhoneService.getInfo(iEngID, iCallId, "peername");
            nameFromAB.setText(s);
        }
        return nameFromAB.toString();
    }

    public boolean mustShowAnswerBT() {
        return iInUse && iIsIncoming && !callEnded && !iActive;
    }

    /**
     * Set caller's name or number.
     * 
     * The functions strips off the SIP protocol identifier and the domain name.
     * @param s The name
     */
    public void setPeerName(String s) {
        if (!iInUse) {
            return;
        }
        bufPeer.setText(Utilities.removeSipParts(s));
    }

    private class LoaderTaskLoadContactData extends AsyncTask<Uri, Void, Void> {

        String mPhoneLookUpId;
        Context mCtx;
        String mSelection;
        String[] mProjection;

        LoaderTaskLoadContactData(String phoneId, Context ctx, String[] projection, String selection) {
            mPhoneLookUpId = phoneId;
            mCtx = ctx;
            mProjection = projection;
            mSelection = selection;
        }

        @Override
        protected Void doInBackground(Uri... uri) {
            Cursor c = null;
            try {
                c = mCtx.getContentResolver().query(uri[0], mProjection, mSelection, null, null);
            } catch (Exception e) {
                secExceptionMsg = "Cannot read contact data.";
                Log.w(LOG_TAG, "Contacts query Exception, not using contacts data.");
            }
            if (c != null) {
//                DatabaseUtils.dumpCursor(c);
                if (c.moveToFirst()) {
                    int idx = c.getColumnIndex(Contacts.DISPLAY_NAME);
                    if (idx != -1) {
                        String name = c.getString(idx);
                        if (name != null && name.length() > 0)
                            nameFromAB.setText(name);
                    }

                    // Check for a photo, hi-res preferred
                    idx = c.getColumnIndex(mPhoneLookUpId);
                    if (idx != -1) {
                        contactId = c.getLong(idx);
                        if (contactId != 0) {
                            // Get photo of contactId as input stream:
                            Uri uriStream = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                            if (uriStream != null) {
                                InputStream input =
                                        Contacts.openContactPhotoInputStream(mCtx.getContentResolver(), uriStream, true);
                                if (input != null)
                                    image = BitmapFactory.decodeStream(input);
                            }
                        }
                    }

                    idx = c.getColumnIndex(PhoneLookup.CUSTOM_RINGTONE);
                    if (idx != -1) {
                        String customRing = c.getString(idx);
                        if (customRing != null)
                            customRingtoneUri = Uri.parse(customRing);
                    }
                }
                c.close();
            }
            return null;
        }

        protected void onProgressUpdate(Void... progress) {}

        @Override
        protected void onCancelled(Void result) {
            mContactsLoaderActive = false;
        }

        @Override
        protected void onPostExecute(Void result) {
            mContactsLoaderActive = false;
        }
    }
}
