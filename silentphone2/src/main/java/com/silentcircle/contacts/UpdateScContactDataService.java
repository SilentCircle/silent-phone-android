/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.contacts;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.silentcircle.common.util.StringUtils;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.Log;

import com.silentcircle.accounts.AccountConstants;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Class that handles insert, update of Silent Circle Raw Contact rows.
 *
 * To understand the functionality of this class and its functions it's necessary to
 * have a full understanding of Android's contact data structure. Good starting
 * points to this topic are:
 * <a href="http://developer.android.com/guide/topics/providers/contacts-provider.html/">
 * Contacts Provider</a> and
 * <a href="http://developer.android.com/training/contacts-provider/index.html">
 * Accessing Contacts Data</a>.
 *
 * To enable Android's standard contact app (People) to display SC specific data we
 * use the documented extension features. See the Contacts Provider documentation and
 * the account and sync adapter sections in particular.
 *
 * The SPA contact integration creates and uses a Silent Circle account specific raw
 * contact and adds data rows to it. Some data rows use normal MIME types, for example
 * for the full name (structured name) while other data rows use SC specific MIME types.
 * Android's People app uses the instructions and mapping in {@code res/xml/contacts.xml}
 * to display the SC specific data rows.
 *
 * If the user clicks on one of the SC specific entries the People app creates a
 * specific Intent and sends it. The SPA app has appropriate Intent filters to get the
 * Intent. SPA then displays the correct view, for example the chat view or the call
 * view.
 *
 * Depending on the way the user created contacts we can have two main contact structures
 * <pre>

 +-------------+                 +-------------+
 |             |                 |             |
 |   contact   |                 |   contact   |
 |             |                 |             |
 +------+------+                 +-.'--------`.+
        |                        .'            `.
        |                      .'                `.
 +------+------+      +------.'-----+        +-----`.------+
 | raw contact |      | raw contact |        | raw contact |
 | SC account  |      | xy account  | ...... | SC account  |
 |             |      |             |        |             |
 +-------------+      +-------------+        +-------------+

 * </pre>
 *
 * The first case happens if the user adds the raw contact using SPA's add contact
 * function. SPA offer this function if it cannot find an existing contact entry for an
 * incoming or outgoing call or during a search in SC user directory. In this case SPA
 * adds a SC raw contact and add the SC contact data records.
 *
 * In the second case the user already added a raw contact of a friend, colleague, or
 * family member. The contact updater uses information in this raw contact to create a
 * SC raw contact and copies the information, for example phone numbers, into the SC
 * specific data rows of the SC raw contact. Because the contact updater copied the
 * structured name to the SC raw contact Android's automatic contact aggregation function
 * should aggregate these two raw contacts and link them to the same contact aggregation.
 *
 * The contact update uses the {@code AggregationException} feature to support this
 * automatic aggregation. However, this aggregation may fail, due to whatever reason, and
 * the user should join the entries manually.
 *
 * Created by werner on 16.01.16.
 */
public class UpdateScContactDataService extends IntentService {
    private static final String TAG = UpdateScContactDataService.class.getSimpleName();

    // The projection to use when querying SC data records
    private final static String[] mAllData = {
            ContactsContract.Data._ID, //.....................0
            ContactsContract.Data.CONTACT_ID, //..............1
            ContactsContract.Data.MIMETYPE, //................2
            ContactsContract.Data.DISPLAY_NAME_PRIMARY, //....3
            ContactsContract.Data.DATA1, //...................4
            ContactsContract.Data.RAW_CONTACT_ID, //..........5
            ContactsContract.Data.SYNC4, //...................6
            ContactsContract.Data.SYNC3, //...................7
            ContactsContract.Data.SYNC2, //...................8
            ContactsContract.Data.LOOKUP_KEY, //..............9
            ContactsContract.Data.SYNC1  //..................10
    };

    private final static int _ID  =          0;
    private final static int CONTACT_ID  =   1;
    private final static int MIME_TYPE =     2;
    private final static int DISPLAY_NAME =  3;
    private final static int DATA =          4;
    private final static int RAW_CONTACT =   5;

    /** In SC data row SYNC4 contains the data id of the original data row */
    private final static int SYNC4 =         6;

    /** In SC data row SYNC3 contains 0 or 1 if contact was discovered or not */
    @SuppressWarnings("unused")
    private final static int SYNC3 =         7;

    /** In SC data row SYNC2 contains a copy of the original data */
    private final static int SYNC2 =         8;

    private final static int LOOKUP_KEY =    9;

    /** In SC data row sync1 contains a indication if this was a manually added contact */
    private final static int SYNC1 =        10;

    private static final Uri mDataUri = ContactsContract.Data.CONTENT_URI;
    public static final String SC_PHONE_CONTENT_TYPE = "vnd.android.cursor.item/com.silentcircle.phone";
    public static final String SC_MSG_CONTENT_TYPE = "vnd.android.cursor.item/com.silentcircle.message";

    public static final String ACTION_UPDATE= "update";
    public static final String ACTION_UPDATE_FORCE= "update_force";

    private static String SC_CALL;
    public static String SC_TEXT;
    private static String SC_WORLD;

    private Account mSelectedAccount;
    private ArrayList<ContentProviderOperation> mOperations;
    private ArrayList<ArrayList<ContentProviderOperation>> mAllOperations;
    private ContentResolver mResolver;

    private boolean mForceReDiscover;
    private static boolean mSeenForce;              // Run update only after we saw a first "force update"

    // Query to find all data records that belong to SC account entries
    private final static String subQuery =
            ContactsContract.Data.MIMETYPE + "='" + SC_PHONE_CONTENT_TYPE + "' OR " +
            ContactsContract.Data.MIMETYPE + "='" + SC_MSG_CONTENT_TYPE + "'";

    private static class ScContactData {
        String mimeType;
        String data;                 // the original 'normal' data or UUID if it's a SC data record
        String copyOfData;           // populated from SYNC2, a copy of the original 'normal' data
        String displayName;
        long mirroredId;             // the data _ID of the mirrored data of the 'normal' record, for non-discovered contacts
        long scDataId;               // if not 0 then the _ID of the SC specific data record
        int  addedManually;          // added by the embedded ContactAdder, don't delete this
    }

    // Class holds the data of a specific contact (contact id).
    private static class CachedContactData {
        public long contactId;
        public long rawContactId;       // the data belongs to this raw contact entry, set if SC raw contact available
        String lookupUri;               // the URI of the main contact entry
        public ArrayList<ScContactData> scData;
    }

    ArrayList<CachedContactData> mCachedScContacts;

    private static class ContactHashData {
        public String mimeType;
        long contact_id;
        long scRawContactId;
        long rawContactId;
        long dataId;
        boolean discovered;
        String contactData;         // Normalized phone number, SIP address, e-mail address, etc
        String validFormatted;      // If contact data is a phone number then it contains formatted, valid number
        String displayName;
        String lookupUri;           // the URI of the main contact entry
        String copyOfData;          // stored in SYNC2, copy of the original 'normal' data without normalization etc
        String hash;
    }

    private ArrayList<ContactHashData> mHashDataArray;

    // Remember known hashes to avoid to many duplicate discovery runs. Run discovery
    // only if we have a yet unknown hash. Contacts sends several change notifications
    // to our observer and many of them can be ignored.
    private static ArrayList<String> mKnownHashes = new ArrayList<>();
    private boolean mRunDiscovery;

    StringBuilder mContent = new StringBuilder();

    public UpdateScContactDataService() {
        super(UpdateScContactDataService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(intent == null || TextUtils.isEmpty(intent.getAction())) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        SC_CALL = getResources().getString(R.string.call_other);
        SC_TEXT = getResources().getString(R.string.chat);
        SC_WORLD = getResources().getString(R.string.silent_world_contact_info);

        mResolver = getContentResolver();
        if (!checkScAccount())
            return;

        if (ACTION_UPDATE_FORCE.equals(intent.getAction())) {
            mForceReDiscover = true;
            mKnownHashes.clear();
        }
        if (!mSeenForce) {
            if (!mForceReDiscover)
                return;
            mSeenForce = true;
        }
        mForceReDiscover = true;
        if (TiviPhoneService.phoneService == null)
            return;
        TiviPhoneService.phoneService.contactObserverUnregister();

        mAllOperations = new ArrayList<>();
        mOperations = new ArrayList<>();

        mHashDataArray = new ArrayList<>();
        mCachedScContacts = new ArrayList<>();

        processContacts();

        // This is a hack: if we install multiple APK for testing purposes the re-register
        // the contact updater only for the main APK, for other APKs run it only once
        if (BuildConfig.MAIN_PACKAGE && TiviPhoneService.phoneService != null)
            TiviPhoneService.phoneService.contactObserverRegister();
    }


    private void processContacts() {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Process contacts, force: " + mForceReDiscover);
        mOperations.clear();
        mAllOperations.clear();

        Cursor cursor = mResolver.query(mDataUri, mAllData, subQuery, null, ContactsContract.Data.CONTACT_ID);
        if (cursor != null) {
            readAllScData(cursor);
            cursor.close();
        }
        if (mForceReDiscover) {
            rediscoverContacts();
        }
        if (mRunDiscovery)
            applyChanges();
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Process contacts done, run discovery " + mRunDiscovery);
    }

    private boolean checkScAccount() {
        // Get account data from system
        Account[] accounts = AccountManager.get(this).getAccountsByType(AccountConstants.ACCOUNT_TYPE);
        if (accounts.length == 0) {
            Log.w(TAG, "No SilentCircle account available - no update of contacts");
            return false;
        }
        mSelectedAccount = accounts[0];
        return true;
    }

    private void applyChanges() {
        // Don't run the OPS until the selection above works correctly
        Log.d(TAG, "Apply changes: " + mOperations.size());
        if (mOperations.size() > 0) {
            mAllOperations.add(mOperations);
            mOperations = new ArrayList<>();
        }
        for (ArrayList<ContentProviderOperation> ops : mAllOperations) {
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (Exception e) {
                // Log exception
                Log.e(TAG, "Exception encountered while processing SC contact data: " + e);
            }
        }
        mAllOperations.clear();
    }

    // Read and cache the SC record data, amend data if necessary. Link the SC data records
    // to their "Contact", not RawContact
    private void readAllScData(Cursor cursor) {
        CachedContactData contactData = null;

        if (cursor.moveToFirst()) {
            /**
             * @see <a href="https://code.google.com/p/android/issues/detail?id=32472">this bug</a>
             * for reasoning for using a do...while
             * **/
            do {
                try {
                    long contactId = cursor.getLong(CONTACT_ID);

                    if (contactData == null || contactData.contactId != contactId) {
                        contactData = new CachedContactData();
                        contactData.contactId = contactId;
                        contactData.lookupUri = ContactsContract.Contacts.getLookupUri(contactId, cursor.getString(LOOKUP_KEY)).toString();
                        contactData.rawContactId = cursor.getLong(RAW_CONTACT);  // get the SC raw contact id for the data
                        contactData.scData = new ArrayList<>();
                        mCachedScContacts.add(contactData);
                    }
                    ScContactData scd = new ScContactData();
                    contactData.scData.add(scd);

                    scd.mimeType = cursor.getString(MIME_TYPE);
                    scd.scDataId = cursor.getLong(_ID);
                    scd.data = cursor.getString(DATA);          // The SC specific URI
                    scd.addedManually = cursor.getInt(SYNC1);
                    scd.copyOfData = cursor.getString(SYNC2);   // Get copy of original data, used to detect modifications
                    scd.mirroredId = cursor.getLong(SYNC4);     // Get data _ID of the mirrored original data
                    scd.displayName = cursor.getString(DISPLAY_NAME) != null ? cursor.getString(DISPLAY_NAME) : " ";
                    //            Log.d(TAG, String.format("++++ Cache SC contact record, data: '%s', id: %d", scd.data, scd.scDataId));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read SC contact entry.");
                    e.printStackTrace();

                    continue;
                }
            } while (cursor.moveToNext());
        }
    }

    private void rediscoverContacts() {
        String query = ContactsContract.Data.MIMETYPE + " IN (" +
                "'" + Phone.CONTENT_ITEM_TYPE + "'," +
                "'" + Email.CONTENT_ITEM_TYPE + "'," +
                "'" + Website.CONTENT_ITEM_TYPE + "')";

        // Get all data records, sorted by contact id
        Cursor cursor = mResolver.query(mDataUri, mAllData, query, null, ContactsContract.Data.CONTACT_ID);
        if (cursor == null) {
            return;
        }

        ArrayList<String> computedHashes = new ArrayList<>();
//        Log.d(TAG, String.format("++++ Found %d normal contact records with query '%s'", cursor.getCount(), query.toString()));
        mHashDataArray.clear();
        if (cursor.moveToFirst()) {
            /**
             * @see <a href="https://code.google.com/p/android/issues/detail?id=32472">this bug</a>
             * for reasoning for using a do...while
             * **/
            do {
                try {
                    ContactHashData chd = createHashStructure(cursor);
                    if (chd != null) {
                        mHashDataArray.add(chd);
                        computedHashes.add(chd.hash);
                        if (!mRunDiscovery)
                            mRunDiscovery = !mKnownHashes.contains(chd.hash);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read SC contact entry.");
                    e.printStackTrace();

                    continue;
                }
            } while (cursor.moveToNext());
        }
        Log.d(TAG, String.format("++++ Created %d hash data structures, run discovery: %b", mHashDataArray.size(), mRunDiscovery));
        cursor.close();
        if (mRunDiscovery && mHashDataArray.size() > 0) {
            JSONObject requestData = createRequestJson(mHashDataArray);
            discoverContacts(requestData);
        }
        mKnownHashes = computedHashes;      // set the probably updated known hashes
    }

    // Create a ContactHashData for inserting a new data entry, get data from contact data cursor
    // The cursor has "normal" contacts data in this case, not the SC specific contact data.
    @Nullable
    private ContactHashData createHashStructure(final Cursor cursor) {
        final String mimeType = cursor.getString(MIME_TYPE);

        final ContactHashData chd = new ContactHashData();
        chd.mimeType = mimeType;
        chd.copyOfData = cursor.getString(DATA);

        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
            if (!fillHashPhoneNumber(chd.copyOfData, chd))
                return null;
        }
        else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            if (!fillHashEmail(chd.copyOfData, chd))
                return null;
        } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType)) {
            if (!fillHashWebsite(chd.copyOfData, chd)) {
                return null;
            }
        }

        chd.dataId = cursor.getLong(_ID);               // SC raw contact data mirrors this data
        chd.contact_id = cursor.getLong(CONTACT_ID);
        chd.displayName = cursor.getString(DISPLAY_NAME) != null ? cursor.getString(DISPLAY_NAME) : " ";
        chd.lookupUri = ContactsContract.Contacts.getLookupUri(chd.contact_id, cursor.getString(LOOKUP_KEY)).toString();
        chd.scRawContactId = getScRawContactForContact(chd.contact_id);
        chd.rawContactId = cursor.getLong(RAW_CONTACT);

        // get and store account data of the data's raw contact. We need it when
        // when adding the data.
        return chd;
    }

    private long getScRawContactForContact(long contactId) {
        // Try to get a SC raw contact that belongs to the contact. If available use it and
        // link new SC data records with a found SC Raw contact.
        final String query = RawContacts.CONTACT_ID + "=" + contactId + " AND " + RawContacts.ACCOUNT_TYPE +
                "='" + mSelectedAccount.type + "' AND " + RawContacts.ACCOUNT_NAME + "='" + mSelectedAccount.name + "'";
        Cursor cursor = mResolver.query(RawContacts.CONTENT_URI, new String[]{RawContacts._ID},
                query, null, RawContacts._ID + " ASC");
        long id = 0L;
        if (cursor != null) {
//            Log.d(TAG, String.format("++++ Found %d SC Raw Contacts for contact id %d", cursor.getCount(), contactId));
            if (cursor.moveToFirst()) {
                id = cursor.getLong(0);
            }
            cursor.close();
        }
        return id;
    }

    private boolean fillHashPhoneNumber(String number, ContactHashData chd) {
        if (TextUtils.isEmpty(number))
            return false;

        if (Utilities.isUriNumber(number)) {
            return false;
        }

        String normalized = PhoneNumberHelper.normalizeNumber(number);
        String formattedValid = Utilities.getValidPhoneNumber(normalized);
        if (formattedValid == null)
            return false;
        normalized = PhoneNumberHelper.normalizeNumber(formattedValid);
        chd.hash = Utilities.hashSha256(normalized);
        chd.contactData = normalized;
        chd.validFormatted = formattedValid;
//        Log.d(TAG, String.format("++++ number to hash: '%s', hash: %s", normalized, chd.hash));
        return true;
    }

    private boolean fillHashEmail(final String email, ContactHashData chd) {
        if (TextUtils.isEmpty(email)) {
            return false;
        }
        chd.hash = Utilities.hashSha256(email.toLowerCase().trim());
        chd.contactData = email;
//        Log.d(TAG, String.format("++++ email to hash: '%s', hash: %s", email, chd.hash));
        return true;
    }

    /*
     * Hashes website data in the form of "silentphone:<alias0>"
     */
    private boolean fillHashWebsite(final String website, ContactHashData chd) {
        if (TextUtils.isEmpty(website)) {
            return false;
        }

        if (!website.startsWith("silentphone:")) {
            return false;
        }

        String alias = StringUtils.ltrim(website, "silentphone:");
        try {
            alias = URLDecoder.decode(alias, "UTF-8");
        } catch (UnsupportedEncodingException ignore) {}

        chd.hash = Utilities.hashSha256(alias);
        chd.contactData = alias;
//        Log.d(TAG, String.format("++++ website to hash: '%s', hash: %s", email, chd.hash));
        return true;
    }

    int foundHashMatches;

    private void parseAndProcessHashResult() {
        if (mContent.length() < 10)
            return;

        ArrayList<String> uuidProcessed = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(mContent.toString());
            if (!root.has("contacts"))
                return;
            JSONObject foundHashes = root.getJSONObject("contacts");

            foundHashMatches = 0;
            for (Iterator<ContactHashData> iterator = mHashDataArray.iterator(); iterator.hasNext();) {
                ContactHashData chd = iterator.next();

                String hashComputed = chd.hash.substring(0, 20);
                if (foundHashes.has(hashComputed)) {                    // We have a contacts discovery match
                    JSONObject userData = foundHashes.getJSONObject(hashComputed);

                    String uuid = userData.getString("uuid");
                    userData.put("lookup_uri", chd.lookupUri);          // Add to name-lookup cache
                    ZinaMessaging.addAliasToUuid(chd.contactData, uuid, IOUtils.encode(userData.toString()));

                    // Add only one pair of SC data records per UUID
                    if (uuidProcessed.contains(uuid))
                        continue;
                    uuidProcessed.add(uuid);

                    // Check if we already have a matching SC data record for this UUID. If yes, just do
                    // nothing else add a SC data record for this UUID. Mind you: for a valid SC UUID contact
                    // we have _two_ SC data record: one for phone, one for messaging. Thus if we found one and
                    // removed it then also remove the second entry from the cache :-) .
                    ScContactData scData = removeScDataInCache(uuid);  // Lookup and remove data entry in cached SC data

//                    Log.d(TAG, String.format("++++ UUID: %s, SC data: %s", uuid, scData == null ? "No" : "Yes"));

                    // Here we have a matching (discovered) contact but had no SC data record yet, create one
                    if (scData == null) {
                        chd.discovered = true;
//                        Log.d(TAG, String.format("++++ add discovered: %s", chd.contactData));
                        addScContactData(chd);
                    }
                    else {
                        removeScDataInCache(uuid);  // Lookup and remove data entry in cached SC data, 2nd entry
                    }
                    ++foundHashMatches;
                }
                // Not a discovered contact: add it as a non-discovered SC record (world call, no messaging)
                // This creates new SC contact entries for each non-discovered contact. We remove the old
                // records below. This requires that we unregister the Contact change listener during the
                // processing.
                // For "silentcircle:<alias0>" website entries, ignore them as they are no longer valid
                else {
//                    Log.d(TAG, String.format("++++ undiscovered: %s, mime: %s", chd.contactData, chd.mimeType));
                    if (!Email.CONTENT_ITEM_TYPE.equals(chd.mimeType)
                            && !Website.CONTENT_ITEM_TYPE.equals(chd.mimeType)) {
                        addScContactData(chd);
                    }
                }
                iterator.remove();                              // Done with this hash data, remove it
            }
            // Remove every non-processed SC record from database. This covers deleted that were discovered
            // as well as re-created non-discovered contacts.
            removeNonProcessedScData();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove a SC data entry from the cache and return the removed entry.
     *
     * Lookup and remove data entry with it's UUID.
     *
     * @param uuid The UUID of the data entry, stored in ScContactData.data
     * @return the removed entry or {@code null} if no entry was removed (not found)
     */
    private ScContactData removeScDataInCache(final String uuid) {
        for (CachedContactData contact : mCachedScContacts) {
            for (Iterator<ScContactData> iterator = contact.scData.iterator(); iterator.hasNext();) {
                ScContactData scData = iterator.next();
                if (uuid != null && scData != null && scData.data != null && scData.data.contains(uuid)) {
                    iterator.remove();
                    return scData;
                }
            }
        }
        return null;
    }

    private void removeNonProcessedScData() {
        for (CachedContactData contact : mCachedScContacts) {
            for (ScContactData scData : contact.scData) {
                if (scData.addedManually > 0)
                    continue;
//                Log.d(TAG, String.format("++++ delete remaining SC data record: %s, SC data id: %d", scData.data, scData.scDataId));
                deleteScContactData(scData);
            }
        }
    }


    public static String formatWithTags(String number, boolean discovered, boolean isSip) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (isSip) {
                number = number + " (" + SC_CALL + ")";
            }
            else {
                number = number + (discovered? "" : " " + SC_WORLD);
            }
        }
        else {
            if (!isSip) {
                number = number + (discovered? "" : " " + SC_WORLD);
            }
        }
        return number;
    }

    // Add new data entries, create a SC raw-contact first if necessary.
    // Called after contact discovery for contact entries that never had a SC data record before.
    // Currently handles phone numbers and SIP names. E-mail data missing yet.
    private void addScContactData(ContactHashData chd) {

        // Prepare contact creation request
        //
        // Note: We use RawContacts because this data must be associated with a particular account.
        //       The system will aggregate this with any other data for this contact and create a
        //       corresponding entry in the ContactsContract.Contacts provider for us.
        int rawContactInsertIndex = 0;

        if (TextUtils.isEmpty(chd.contactData)) {
            Log.w(TAG, "Add contact: contact data is null, ignoring.");
            return;
        }

        // If this is a discovered contact then re-label it as a SIP address and replace
        // the original contact data with the SC specific UUID sip address. This guarantees
        // that we will be able to query this entry and also that it is added for calls and message
        if (chd.discovered) {
            byte[] userData = ZinaMessaging.getUserInfoFromCache(chd.contactData);
            AsyncTasks.UserInfo ui = AsyncTasks.parseUserInfo(userData);
            if (ui != null) {
                chd.contactData = ui.mUuid + getString(R.string.sc_sip_domain_0);
                chd.mimeType = SipAddress.CONTENT_ITEM_TYPE;
            }
        }

        // If a contact already has a SC raw contact then don't add another one, just add
        // the SC specific data to the SC raw contact. See below when we use the chd.scRawContactId
        // instead of back reference
        //
        // isScRawAccount is false if the contact does not yet has a SC raw contact. In this case we
        // create a SC raw contact, aggregate it with another raw contact (the raw contact id of the
        // phone number entry) of the contact and add the SC data to the new SC raw contact.
        final boolean isScRawAccount = chd.scRawContactId > 0;
        if (!isScRawAccount) {
            rawContactInsertIndex = mOperations.size();
            mOperations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, mSelectedAccount.type)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, mSelectedAccount.name)
                    .withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                    .build());

            mOperations.add(ContentProviderOperation.newInsert(mDataUri)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, chd.displayName)
                    .build());

            // Found a non-SC raw contact. Aggregate the SC raw contact and the non-SC raw contact.
            mOperations.add(ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, chd.rawContactId)
                    .withValueBackReference(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, rawContactInsertIndex)
                    .build());
        }

        String numberWithScheme;
        String phoneName;

        String scheme = "tel:";
        boolean isSip = false;

        // Treat the e-mail address similar to a SIP name. Either we use a SIP name or a
        // SSO (e-mail) and add an SC phone / message entry for it
        if (SipAddress.CONTENT_ITEM_TYPE.equals(chd.mimeType)
                || Email.CONTENT_ITEM_TYPE.equals(chd.mimeType)
                || Website.CONTENT_ITEM_TYPE.equals(chd.mimeType)) {
            scheme = "sip:";
            numberWithScheme = chd.contactData;
            isSip = true;
            phoneName = formatWithTags(chd.displayName, chd.discovered, true);
        }
        else {
            numberWithScheme = chd.validFormatted;
            String name = chd.discovered ? chd.displayName : chd.validFormatted;
            phoneName = formatWithTags(name, chd.discovered, false);
        }

        // always add a scheme to the number. The DialerActivity needs it when it gets
        // the URI to setup a call/message
        if (!numberWithScheme.startsWith(scheme))
            numberWithScheme = scheme + numberWithScheme;

        ContentProviderOperation.Builder opBuilder = ContentProviderOperation.newInsert(mDataUri);
        if (!isScRawAccount)
            opBuilder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex);
        else
            opBuilder.withValue(ContactsContract.Data.RAW_CONTACT_ID, chd.scRawContactId);

        opBuilder.withValue(ContactsContract.Data.MIMETYPE, SC_PHONE_CONTENT_TYPE)
                .withValue(ContactsContract.Data.DATA1, numberWithScheme)
                .withValue(ContactsContract.Data.DATA2, "Silent Circle")
                .withValue(ContactsContract.Data.DATA3, phoneName)
                .withValue(ContactsContract.Data.SYNC2, chd.copyOfData)
                .withValue(ContactsContract.Data.SYNC3, chd.discovered ? 1 : 0)
                .withValue(ContactsContract.Data.SYNC4, chd.dataId);      // The SC record mirrors content of this original Data record
//        Log.d(TAG, String.format("++++ phone add operation: %s, qualified: %s", phoneName, numberWithScheme));
        mOperations.add(opBuilder.build());

        // Enable this for numbers also once we provide messaging via numbers. A discovered contact may
        // be a number. 'isSip' also covers discovered e-mail addresses.
        if (isSip) {
            String msgName = chd.displayName;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                msgName = msgName + " (" + SC_TEXT +")";
            }
            opBuilder = ContentProviderOperation.newInsert(mDataUri);
            if (!isScRawAccount)
                opBuilder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex);
            else
                opBuilder.withValue(ContactsContract.Data.RAW_CONTACT_ID, chd.scRawContactId);

            opBuilder.withValue(ContactsContract.Data.MIMETYPE, SC_MSG_CONTENT_TYPE)
                    .withValue(ContactsContract.Data.DATA1, numberWithScheme)
                    .withValue(ContactsContract.Data.DATA2, "Silent Circle")
                    .withValue(ContactsContract.Data.DATA3, msgName)
                    .withValue(ContactsContract.Data.SYNC2, chd.copyOfData)
                    .withValue(ContactsContract.Data.SYNC4, chd.dataId);      // The SC record mirrors content of this original Data record
//            Log.d(TAG, String.format("++++ message add operation: %s, qualified: %s", msgName, numberWithScheme));
            mOperations.add(opBuilder.build());
        }
        if (mOperations.size() > 300) {
            mAllOperations.add(mOperations);
            mOperations = new ArrayList<>();
        }
    }

    private void deleteScContactData(ScContactData scData) {
        Uri deleteUri = ContentUris.withAppendedId(mDataUri, scData.scDataId);
        mOperations.add(ContentProviderOperation.newDelete(deleteUri).build());
        if (mOperations.size() > 300) {
            mAllOperations.add(mOperations);
            mOperations = new ArrayList<>();
        }
    }



    private JSONObject createRequestJson(ArrayList<ContactHashData> hashDataArray) {
        JSONObject root = new JSONObject();
        JSONArray hashes = new JSONArray();
        for (ContactHashData cdh : hashDataArray) {
            if (!TextUtils.isEmpty(cdh.hash))
                hashes.put(cdh.hash.substring(0, 6));
        }
        try {
            root.put("contacts", hashes);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return root;
    }

    public int discoverContacts(JSONObject customerData) {
        HttpsURLConnection urlConnection = null;

        int contentLength;
        String body = customerData.toString();
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Discovery data: " + body);
        if (body != null) {
            contentLength = body.getBytes().length;
        }
        else {
            return -1;
        }

        byte[] data = KeyManagerSupport.getSharedKeyData(getContentResolver(),
                ConfigurationUtilities.getShardAuthTag());
        if (data == null) {
            Log.w(TAG, "No API key data available");
            return -1;
        }
        String devAuthorization = null;
        try {
            devAuthorization = new String(data, "UTF-8");
        }  catch (UnsupportedEncodingException ignore) {
        }

        URL mRequestUrlDiscoverContact;
        try {
            mRequestUrlDiscoverContact = new URL(ConfigurationUtilities.getProvisioningBaseUrl(getApplicationContext())
                    + "v2/contacts/validate/"
                    + "?api_key=" + devAuthorization);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return -1;
        }
        OutputStream out = null;
        try {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Discovery request URL: " + mRequestUrlDiscoverContact);
            // For an existing account we add the license code to the account, thus PUT to modify
            // the existing account.
            urlConnection = (HttpsURLConnection) mRequestUrlDiscoverContact.openConnection();
            SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
            if (context != null) {
                urlConnection.setSSLSocketFactory(context.getSocketFactory());
            }
            else {
                Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                throw new AssertionError("Failed to get pinned SSL context");
            }
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
            urlConnection.setFixedLengthStreamingMode(contentLength);
            urlConnection.setConnectTimeout(2000);

            out = new BufferedOutputStream(urlConnection.getOutputStream());
            out.write(body.getBytes());
            out.flush();

            int ret = urlConnection.getResponseCode();
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

            if (ret == HttpsURLConnection.HTTP_OK) {
                AsyncTasks.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
            }
            else {
                AsyncTasks.readStream(new BufferedInputStream(urlConnection.getErrorStream()), mContent);
            }
            out.close();
            if (ret == HttpsURLConnection.HTTP_OK) {
                parseAndProcessHashResult();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Found matching contacts: " + foundHashMatches);
            }
            return ret;
        } catch (IOException e) {
            Log.e(TAG, "Network not available: " + e.getMessage());
            return -1;
        } catch (Exception e) {
            Log.e(TAG, "Network connection problem: " + e.getMessage(), e);
            return -1;
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException ignore) { }
            if (urlConnection != null)
                urlConnection.disconnect();
        }
    }
}
