/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.silentcircle.silentcontacts.ScContactsContract;
import com.silentcircle.silentcontacts.ScContactsContract.PhoneLookup;
import com.silentcircle.silentcontacts.ScContactsContract.RawContacts;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.activities.TMActivity;

import java.io.InputStream;

public class CTCall {

    private static final String LOG_TAG = "CTCall";

    private boolean contactsDataChecked;

    CTCall() {
        iUserDataLoaded = 0;
        reset();
    }

    void reset() {

        contactsDataChecked = false;
  
        nameFromAB.reset();
        zrtpWarning.reset();
        zrtpPEER.reset();

        bufDialed.reset();
        bufServName.reset();
        bufPeer.reset();
        bufMsg.reset();

        bufSAS.reset();
        bufSecureMsg.reset();
        bufSecureMsgV.reset();
       
        iShowVideoSrcWhenAudioIsSecure = false;

        iInUse = false;
        iIsIncoming = false;
        iActive = false;
        iEnded = 0;
        iIsOnHold = false;
        iIsInConferece = false;
        iMuted = false;
        iSipHasErrorMessage = false;
        iRecentsUpdated = false;
        bIsVideoActive =false;
        videoAccepted =false;
        sdesActive = false;

        iUserDataLoaded = 0;

        uiStartTime = 0;
        iDuration = 0;
        uiRelAt = 0;
        iCallId = 0;
        // pEng=NULL;
        iEngID = 0;

        iShowEnroll = iShowVerifySas = false;

        iShowWarningForNSec = 0;

        iUpdated = 0;
        iImgHeight = 0;

        image = null;
        customRingtoneUri = null;
        lookupUri = null;
        contactId = 0;
        secExceptionMsg = null;
    }

    public void fillDataFromContacts(TiviPhoneService service) {
        if (contactsDataChecked || service == null)
            return;

        Context ctx = service.getBaseContext();
        Uri contentUri;
        String phoneLookUpId;
        String ringtoneIdx;
        String displayNameIdx;
        boolean hasSilentContacts = false;

        if (service.hasSilentContacts()) {
            if (!PhoneNumberUtils.isGlobalPhoneNumber(bufPeer.toString())) {
                // "peer's number" is a SIP address, so use the PhoneLookup table with the SIP parameter.
                Uri.Builder uriBuilder = PhoneLookup.CONTENT_FILTER_URI.buildUpon();
                uriBuilder.appendPath(Uri.encode(bufPeer.toString() + "@sip.silentcircle.net"));
                uriBuilder.appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, "1");
                lookupUri = uriBuilder.build();
                if (TMActivity.SP_DEBUG) Log.d("CTCall", "SIP lookup uri: " + lookupUri);
                phoneLookUpId = ScContactsContract.Data.RAW_CONTACT_ID;     // TODO - check and test this
            }
            else {
                lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, bufPeer.toString());
                phoneLookUpId = PhoneLookup._ID;
            }
            lookupUri = lookupUri.buildUpon().appendQueryParameter(ScContactsContract.NON_BLOCKING, "true").build();
            displayNameIdx = RawContacts.DISPLAY_NAME;
            contentUri = RawContacts.CONTENT_URI;
            ringtoneIdx = PhoneLookup.CUSTOM_RINGTONE;
            hasSilentContacts = true;
        }
        else {
            lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, bufPeer.toString());
            displayNameIdx = ContactsContract.Contacts.DISPLAY_NAME;
            phoneLookUpId = ContactsContract.PhoneLookup._ID;
            contentUri = ContactsContract.Contacts.CONTENT_URI;
            ringtoneIdx = ContactsContract.PhoneLookup.CUSTOM_RINGTONE;
        }
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(lookupUri, null, null, null, null);
        } catch (Exception e) {
            secExceptionMsg = "Cannot read contact data.";
            Log.w(LOG_TAG, "Contacts query Exception, not using contacts data.");
        }
        if (c != null) {
            if (c.moveToFirst()) {
//                DatabaseUtils.dumpCursor(c);
                int idx = c.getColumnIndex(displayNameIdx);
                if (idx != -1) {
                    String name = c.getString(idx);
                    if (name != null && name.length() > 0)
                        nameFromAB.setText(name);
                }

                // Check for a small photo
                idx = c.getColumnIndex(phoneLookUpId);
                if (idx != -1) {
                    contactId = c.getLong(idx);
                    if (contactId != 0) {
                        // Get photo of contactId as input stream:
                        Uri uri = ContentUris.withAppendedId(contentUri, contactId);                        
                        InputStream input = hasSilentContacts ?
                                RawContacts.openContactPhotoInputStream(ctx.getContentResolver(), uri):
                                ContactsContract.Contacts.openContactPhotoInputStream(ctx.getContentResolver(), uri);
                        if (input != null)
                            image = BitmapFactory.decodeStream(input);
                    }
                }

                idx =  c.getColumnIndex(ringtoneIdx);
                if (idx != -1) {
                    String customRing = c.getString(idx);
                    if (customRing != null)
                        customRingtoneUri = Uri.parse(customRing);
                }
            }
            c.close();
        }
    }        

    public String getNameFromAB() {
       
       if(nameFromAB.getLen() == 0){
          String s = TiviPhoneService.getInfo(iEngID, iCallId, "peername");
          nameFromAB.setText(s);
       }
       return nameFromAB.toString();
    }

    public boolean mustShowAnswerBT() {
        return iInUse && iIsIncoming && iEnded == 0 && !iActive;
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

    public CTStringBuffer zrtpWarning = new CTStringBuffer(256);
    public CTStringBuffer zrtpPEER = new CTStringBuffer();
    private CTStringBuffer nameFromAB = new CTStringBuffer();//  from phoneBook  or sip

    public CTStringBuffer bufDialed = new CTStringBuffer();
    public CTStringBuffer bufPeer = new CTStringBuffer();
    public CTStringBuffer bufServName = new CTStringBuffer();
    public CTStringBuffer bufMsg = new CTStringBuffer(512);

    public CTStringBuffer bufSAS = new CTStringBuffer();
    public CTStringBuffer bufSecureMsg = new CTStringBuffer();
    public CTStringBuffer bufSecureMsgV = new CTStringBuffer();
   
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
     * Set to true by phone engine if incoming call detected (eIncomCall).
     */
    public boolean iIsIncoming;

    /**
     * Set to true by phone engine if the call started (eStartCall).
     */
    public boolean iActive;

    /**
     * Set to 2 by phone engine if the call ended (eEndCall), otherwise it is 0.
     */
    public int iEnded;

    /**
     * If true the call is on hold (not yet used, hold function missing)
     */
    public boolean iIsOnHold;

    /**
     * Set to true if microphone is muted, managed by call window.
     */
    public boolean iMuted;

    public boolean iShowVideoSrcWhenAudioIsSecure;

    public boolean iIsInConferece;

    public boolean iSipHasErrorMessage;

    public boolean iRecentsUpdated;
    
    /**
     * Security via SDES not via ZRTP
     */
    public boolean sdesActive;

    public int iUserDataLoaded;
   
    public boolean bIsVideoActive;
    public boolean videoAccepted;

    /**
     * Set by phone engine to current system time if the call ended (eEndCall)
     */
    public long uiRelAt;

    /**
     * Set by phone service on <code>eIncomCall</code> and on <code>eCalling</code> callback messages.
     */
    public int iCallId;

    /**
     * Set by phone service on <code>eIncomCall</code> and on <code>eCalling</code> callback messages.
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
     * The lookup URI to locate the contact in the DB.
     */
    public Uri lookupUri;

    /**
     * Id of raw contact
     *
     * If not null then we have a contact in the contact DB.
     */
    public long contactId;

    /** If no null then a background service saw an exception when accessing SCA data */
    public String secExceptionMsg;
};
