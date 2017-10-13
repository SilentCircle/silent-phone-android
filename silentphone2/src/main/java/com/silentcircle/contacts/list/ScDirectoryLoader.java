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

package com.silentcircle.contacts.list;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.JsonReader;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.contacts.utils.NameSplitter;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.logs.Log;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialogHelperActivity;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Custom loader to load SC directory entries and setup a cursor.
 *
 * Created by werner on 29.09.14.
 */
public class ScDirectoryLoader extends AsyncTaskLoader<Cursor> {

    private static final String TAG = "ScDirectoryLoader";

    /** Add every field to the returned cursor */
    public static final int ALL = 0;

    /** Add only the name field to the returned cursor */
    public static final int NAMES_ONLY = 1;

    /** Add only the number field to the returned cursor */
    public static final int NUMBERS_ONLY = 2;

    public static final int MIN_SEARCH_LENGTH = 2;
    public static final int MAX_RECORDS = 20;

    private final static String MAX_RECORDS_SERVER = "&start=0&max=" + (MAX_RECORDS+1);

    public static final String SC_PRIVATE_FIELD = "sc_private_field";
    public static final String SC_UUID_FIELD = "sc_uuid_field";
    /* *** IMPORTANT: Keep in-sync with PhoneNumberListAdapter projections, see also addRow() below *** */
    private static final String[] PROJECTION = new String[]{
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.PHOTO_ID,                     // 6
            Phone.DISPLAY_NAME_PRIMARY,         // 7
            Phone.PHOTO_THUMBNAIL_URI,          // 8
            SC_PRIVATE_FIELD,                   // 9
            SC_UUID_FIELD,                      // 10 // must be at the end, otherwise the adapters/fragments get confused
    };

    private StringBuilder mContent = new StringBuilder();
    private final MatrixCursor mEmptyCursor = new MatrixCursor(PROJECTION, 0);

    private boolean mQueryChanged;

    private int mFilterType = ALL;

    static class UserData {
        final String fullName;
        String displayName;
        final String userName;
        final String uuid;
        final String organization;
        final boolean inSameOrganization;
        String sortKey;

        UserData(String full, String display, String un, String uid, String sort) {
            this(full, display, un, uid, sort, null, false);
        }

        UserData(String full, String display, String un, String uid, String sort, String org,
                 boolean inSameOrg) {
            fullName = full;
            displayName = display;
            userName = un;
            uuid = uid;
            sortKey = sort;
            organization = org;
            inSameOrganization = inSameOrg;
        }
    }

    // Singleton class used for data that must survive the re-creation of the loader class until
    // explicitly reset. This enables some limited caching and thus reducing server queries.
    public static class DirectoryHelper extends Handler{
        public static final int MSG_UPDATE_CACHE = 0;
        public static class QueryResults {
            // Class used to send the query results as a message from the background to the main thread
            public QueryResults(String query,TreeMap<String, UserData>  data, boolean error) {
                this.query = query;
                this.data = data;
                this.error = error;
            }
            public String query;
            public TreeMap<String, UserData> data;
            public boolean error;
        }

        public String mPreviousQuery = "";
        public TreeMap<String, UserData> mUserList;

        public NameSplitter mNameSplitter;
        public boolean mUseScDirLoaderOrg;
        public boolean mErrorOnPreviousSearch;
        public Handler mHandler;

        public DirectoryHelper(Looper looper)  {
            super(looper);
        }

        // Runs in main thread and updates the cache related variables according to the
        // previous query results
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_CACHE:
                    QueryResults results = (QueryResults) msg.obj;
                    mErrorOnPreviousSearch = results.error;
                    if (results.error) {
                        clearCachedData();
                    }
                    else {
                        if (results.data.size() == 0)
                            return;
                        mPreviousQuery = results.query;
                        mUserList = results.data;
                    }
                    break;
                default:
                    break;
            }
        }

    }

    private static DirectoryHelper directoryHelper;
    /** Getter method to access the singleton class of the helper */
    public static DirectoryHelper getHelper() {
        if (directoryHelper == null) {
            directoryHelper = new DirectoryHelper(Looper.getMainLooper());
        }
        return directoryHelper;
    }

    private boolean mSortAlternative;
    private boolean mDisplayAlternative;
    private String mDevAuthorization;

    private String mSearchText = "";


    public ScDirectoryLoader(Context context) {
        super(context);
        byte[] data = KeyManagerSupport.getSharedKeyData(context.getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (data != null) {
            try {
                mDevAuthorization = new String(data, "UTF-8").trim();
                if (ConfigurationUtilities.mTrace)
                    Log.d(TAG, "Authentication data SC dir (API key) : " + mDevAuthorization);
            } catch (UnsupportedEncodingException ignored) {
            }
        }
    }

    public void setQueryString(String query) {
        if (ConfigurationUtilities.mTrace)
            Log.d(TAG, "Query: " + query + ", previous: " + getHelper().mPreviousQuery +
                    ", list: " + (getHelper().mUserList == null ? "null" : "not null"));
        mSearchText = query;

        /*
         * If search query is a phone number, normalize it by removing spaces and other non-numeric
         * characters (excluding + sign).
         *
         * v2/people will only return result for a normalized phone number.
         */
        if (mSearchText != null && PhoneNumberUtils.isGlobalPhoneNumber(mSearchText.replaceAll("\\s", ""))) {
            mSearchText = PhoneNumberHelper.normalizeNumber(mSearchText);
        }

        mQueryChanged = true;
    }

    public static void clearCachedData() {
        getHelper().mUserList = null;
        getHelper().mNameSplitter = null;
        getHelper().mPreviousQuery = "";
    }

    public void setFilterType(final int filterType) {
        mFilterType = filterType;
    }

    @Override
    protected void onStartLoading() {
        // Perform SC directory search only if the application is online, i.e. has an Internet connection
        if (TextUtils.isEmpty(mSearchText)
                || !Utilities.isNetworkConnected(getContext())) {
            getHelper().mPreviousQuery = "";
            getHelper().mUserList = null;
            deliverResult(mEmptyCursor);
            return;
        }

        if (getHelper().mUserList != null && mSearchText.equals(getHelper().mPreviousQuery)) {
            deliverResult(createCursor(getHelper().mUserList));
            return;
        }

        // The new query string is shorter than the old - no subset, refresh content
        if (mSearchText.length() < getHelper().mPreviousQuery.length()) {
            if (!mSearchText.regionMatches(0, getHelper().mPreviousQuery, 0, mSearchText.length())) {
                getHelper().mUserList = null;
            }
            else {
                Cursor cursor = createCursorMatching(getHelper().mUserList, mSearchText);
                if (cursor.getCount() >= MAX_RECORDS) {
                    deliverResult(cursor);
                    return;
                }
                else {
                    getHelper().mUserList = null;
                }
            }
        }

        if (getHelper().mUserList == null || getHelper().mUserList.size() == 0) {
            forceLoad();
            return;
        }

//        if (getHelper().mUserList.size() > MAX_RECORDS) {
//            if (mQueryChanged) {
//                forceLoad();
//                mQueryChanged = false;
//            }
//            else
//                deliverResult(createCursor());
//            return;
//        }

        if (mSearchText.startsWith(getHelper().mPreviousQuery) && getHelper().mUserList.size() < MAX_RECORDS) {
            Cursor cursor = createCursorMatching(getHelper().mUserList, mSearchText);
            if (cursor.getCount() > 0) {
                deliverResult(cursor);
                return;
            }
        }
        forceLoad();
    }

    @Override
    protected void onStopLoading() {
    }

    @Override
    public void onCanceled(Cursor data) {
        if (data != null) {
            data.close();
        }
    }

    @Override
    public Cursor loadInBackground() {
        Log.d(TAG, "Query: " + mSearchText);
        if (mSearchText == null || mSearchText.length() < MIN_SEARCH_LENGTH) {
            return mEmptyCursor;
        }
        URL dirUrl;
        try {
            dirUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(getContext()) +
                    ConfigurationUtilities.getDirectorySearch(getContext()) +
                    Uri.encode(mSearchText) + "&api_key=" +
                    Uri.encode(mDevAuthorization) + MAX_RECORDS_SERVER +
                    (getHelper().mUseScDirLoaderOrg ? "&org_only=1" : ""));
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "SC directory URL: "+dirUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return mEmptyCursor;
        }
        mQueryChanged = false;                  // we are processing the query right now
        HttpsURLConnection urlConnection = null;
        TreeMap<String, UserData> userList = null;
        boolean error = false;
        try {
            urlConnection = (HttpsURLConnection) dirUrl.openConnection();
            SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
            if (context != null) {
                urlConnection.setSSLSocketFactory(context.getSocketFactory());
            }
            else {
                Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                throw new AssertionError("Failed to get pinned SSL context");
            }
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
            urlConnection.setConnectTimeout(5000);

            int ret = urlConnection.getResponseCode();
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code SC directory loader: " + ret);
            if (ret == HttpsURLConnection.HTTP_OK) {
                if (!ConfigurationUtilities.mTrace)
                    userList = parseUserDataStream(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
                else {
                    //  Use the following code for testing to see the data (readStream dumps it)
                    AsyncTasks.readStream(new BufferedInputStream(urlConnection.getInputStream()), mContent);
                    userList = parseUserDataStream(new StringReader(mContent.toString()));
                }
            }
            else {
                AsyncTasks.readStream(new BufferedInputStream(urlConnection.getErrorStream()), mContent);
                JSONObject jsonObj = new JSONObject(mContent.toString());
                parseErrorMessage(jsonObj);
                error = true;
                return mEmptyCursor;
            }
        } catch (IOException e) {
            showInputInfo(getContext().getString(R.string.network_not_available_title),
                    getContext().getString(R.string.network_not_available_description));
            Log.e(TAG, "Network not available: " + e.getMessage());
            error = true;
            return mEmptyCursor;
        } catch (Exception e) {
            showInputInfo(getContext().getString(R.string.network_not_available_title),
                    getContext().getString(R.string.network_not_available_description));
            Log.e(TAG, "Network connection problem: " + e.getMessage());
            error = true;
            return mEmptyCursor;
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
            DirectoryHelper.QueryResults results = new DirectoryHelper.QueryResults(mSearchText, userList, error);
            getHelper().sendMessage(getHelper().obtainMessage(DirectoryHelper.MSG_UPDATE_CACHE, results));
        }
        return createCursor(userList);
    }

    @Override
    protected void onReset() {
        stopLoading();
    }

    public void setDisplayAlternative(boolean alternative) {
        mDisplayAlternative = alternative;
    }

    public void setSortAlternative(boolean alternative) {
        mSortAlternative = alternative;
    }

    protected void useScDirectoryOrganization(boolean use) {
        if (getHelper().mUseScDirLoaderOrg != use)
            getHelper().mUserList = null;               // force a new search if restriction changed
        getHelper().mUseScDirLoaderOrg = use;
    }

    protected static void reSort(final boolean alternative, final Context ctx) {
        if (getHelper().mUserList == null || getHelper().mUserList.size() == 0)
            return;

        final TreeMap<String, UserData> newList = new TreeMap<>();

        createNameSplitter(ctx);
        final List<UserData> userList = new ArrayList<>(getHelper().mUserList.values());
        final NameSplitter.Name name = new NameSplitter.Name();
        for (UserData ud : userList) {
            String fullName = ud.fullName;

            name.clear();
            getHelper().mNameSplitter.split(name, fullName);
//            ud.sortKey = UCharacter.foldCase(mNameSplitter.join(name, !alternative, true), true);
            ud.sortKey = getHelper().mNameSplitter.join(name, !alternative, true).toLowerCase(Locale.getDefault());
            newList.put(ud.sortKey, ud);
        }
        getHelper().mUserList = newList;
    }

    protected static void reDisplay(boolean alternative, Context ctx) {
        if (getHelper().mUserList == null || getHelper().mUserList.size() == 0)
            return;

        TreeMap<String, UserData> newList = new TreeMap<>();

        createNameSplitter(ctx);
        List<UserData> userList = new ArrayList<>(getHelper().mUserList.values());
        NameSplitter.Name name = new NameSplitter.Name();
        for (UserData ud : userList) {
            String fullName = ud.fullName;

            name.clear();
            getHelper().mNameSplitter.split(name, fullName);
            ud.displayName = getHelper().mNameSplitter.join(name, !alternative, true);
            newList.put(ud.sortKey, ud);
        }
        getHelper().mUserList = newList;
    }

    private Cursor createCursor(TreeMap<String, UserData> userList) {
        int size;
        if (userList == null || (size = userList.size()) == 0)
            return mEmptyCursor;

        MatrixCursor cursor = new MatrixCursor(PROJECTION, size * 2);
        List<UserData> userListValues = new ArrayList<>(userList.values());

        int _id = 3;
        for (UserData ud : userListValues) {
            addRow(cursor, ud, _id);
            _id++;
        }
        return cursor;
    }

    private Cursor createCursorMatching(TreeMap<String, UserData> userList, String searchText) {
        int size;
        if (userList == null || (size = userList.size()) == 0)
            return mEmptyCursor;

        MatrixCursor cursor = new MatrixCursor(PROJECTION, size * 2);
        List<UserData> userListValues = new ArrayList<>(userList.values());

        String searchFold = searchText.toLowerCase(Locale.getDefault());
        long _id = 3;
        for (UserData ud : userListValues) {
            String dpNameFold = ud.displayName.toLowerCase(Locale.getDefault());
            String userNameFold = ud.userName.toLowerCase(Locale.getDefault());
            if (dpNameFold.contains(searchFold)) {
                addRow(cursor, ud, _id);
                _id++;
            }
        }
        return cursor;
    }

    private void addRow(MatrixCursor cursor, UserData ud, long _id) {

/*              Phone._ID,                          // 0
                Phone.TYPE,                         // 1
                Phone.LABEL,                        // 2
                Phone.NUMBER,                       // 3
                Phone.CONTACT_ID,                   // 4
                Phone.LOOKUP_KEY,                   // 5
                Phone.PHOTO_ID,                     // 6
                Phone.DISPLAY_NAME_PRIMARY,         // 7
                Phone.PHOTO_THUMBNAIL_URI,          // 8
*/

        MatrixCursor.RowBuilder row;

        if (mFilterType == ALL || mFilterType == NAMES_ONLY) {
            row = cursor.newRow();
            row.add(_id);                               // _ID
            row.add(Phone.TYPE_CUSTOM);                 // TYPE, must be an in-circle number
            row.add(getContext().getString(R.string.phoneTypeSilent));
            row.add(ud.userName);
            row.add(_id);                               // CONTACT_ID
            row.add(null);                              // LOOKUP-KEY
            row.add(null);                              // PHOTO_ID
            row.add(ud.displayName);
            row.add(null);                              // Phone.PHOTO_THUMBNAIL_URI
            row.add(null);                              // SC_PRIVATE_FIELD
            row.add(ud.uuid);                           // SC_UUID_FIELD
        }
    }

    private void parseErrorMessage(JSONObject jsonObj) {
        String message;
        int errorCode;
        try {
            message = getContext().getString(R.string.call_error) + ": " + jsonObj.getString("error_msg");
            errorCode = jsonObj.getInt("error_code");
            Log.w(TAG, "SC Directory search error: " +errorCode+": " +jsonObj.getString("error_msg"));

        } catch (JSONException e) {
            message = getContext().getString(R.string.provisioning_wrong_format) + e.getMessage();
            Log.w(TAG, "JSON exception: " + e);
        }
        showInputInfo(message);
    }

    private static void createNameSplitter(Context ctx) {
        if (getHelper().mNameSplitter == null) {
            getHelper().mNameSplitter = new NameSplitter(ctx.getString(R.string.common_name_prefixes),
                    ctx.getString(R.string.common_last_name_prefixes), ctx.getString(R.string.common_name_suffixes),
                    ctx.getString(R.string.common_name_conjunctions), Locale.getDefault());
        }
    }

    private void showInputInfo(String title, String msg) {
        Intent intent = new Intent(getContext(), DialogHelperActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(InfoMsgDialogFragment.TITLE, title != null ? title : getContext().getString(R.string.directory_use_sc));
        intent.putExtra(InfoMsgDialogFragment.MESSAGE, msg);
        getContext().startActivity(intent);
    }

    private void showInputInfo(String msg) {
        showInputInfo(null, msg);
    }

    private TreeMap<String, UserData> parseUserDataStream(Reader inReader) {
        TreeMap<String, UserData> userList = new TreeMap<>();
        createNameSplitter(getContext());

        JsonReader reader = new JsonReader(inReader);
        try {
            readPeopleObject(reader, userList);
        } catch (IOException ex) {
            Log.w(TAG, "JSON exception reading error message: " + ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Log.w(TAG, "JSON exception reading error message: " + ex);
            }
        }
        return userList;
    }

    private void readPeopleObject(JsonReader reader, TreeMap<String, UserData> userList) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("people")) {
                readUserArray(reader, userList);
            }
            else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    // Iterate over the user object array and fill in UserData
    private void readUserArray(JsonReader reader, TreeMap<String, UserData> userList) throws IOException {
        NameSplitter.Name name = new NameSplitter.Name();
        new TreeMap<>();

        reader.beginArray();
        while (reader.hasNext()) {
            String userName = null;
            String fullName = null;
            String uuid = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String propertyName = reader.nextName();

                switch (propertyName) {
                    case "display_alias":
                    case "default_alias":
                        userName = reader.nextString();
                        break;
                    case "display_name":
                        fullName = reader.nextString();
                        if (TextUtils.isEmpty(fullName))
                            fullName = userName;
                        break;
                    case "uuid":
                        uuid = reader.nextString();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            final String sortKeyAppend = (userName != null && !userName.equals(fullName)) ? userName : null;

            name.clear();
            getHelper().mNameSplitter.split(name, fullName);
            final String displayName = getHelper().mNameSplitter.join(name, !mDisplayAlternative, true);
//            String sortKey = UCharacter.foldCase(mNameSplitter.join(name, !mSortAlternative, true), true);
            final String sortKey =
                    getHelper().mNameSplitter.join(name, !mSortAlternative, true).toLowerCase(Locale.getDefault()) + sortKeyAppend;

            UserData user = new UserData(fullName, displayName, userName, uuid, sortKey);
            userList.put(sortKey, user);
        }
        reader.endArray();
    }
}
