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

package com.silentcircle.silentphone2.activities;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OnAccountsUpdateListener;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.silentcircle.accounts.AccountConstants;
import com.silentcircle.contacts.UpdateScContactDataService;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.ContactAdderFragment;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;

import java.io.IOException;
import java.util.ArrayList;

public final class ContactAdder extends Activity implements OnAccountsUpdateListener {
    public static final String TAG = "ContactsAdder";

    private EditText mContactNameEditText;
    private Button mContactSaveButton;
    private Account mSelectedAccount;

    private String mAssertedName;
    private String mText;

    private ContactAdderFragment mAdderFragment;
    private String mSearchQuery;
    private boolean mSearchUiActive;

    private long mOtherRawId;

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                // If the query hasn't changed (perhaps due to activity being destroyed
                // and restored.
                return;
            }
            mSearchQuery = newText;

            // Show search fragment only when the query string is changed to non-empty text.
            if (!TextUtils.isEmpty(newText)) {
                // Call enterSearchUi only if we are showing a search fragment for the first time.
                if (!mSearchUiActive)
                    enterSearchUi(mSearchQuery);
                mAdderFragment.setQueryString(mSearchQuery, false /* delaySelection */);

            }
            else {
                if (mSearchUiActive) {
                    exitSearchUi();
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };


    /**
     * Called when the activity is first created. Responsible for initializing the UI.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_adder);

        if (!parseIntent(getIntent())) {
            finish();
            return;
        }
        // Obtain handles and setup UI objects
        mContactSaveButton = (Button) findViewById(R.id.contactSaveButton);

        mContactNameEditText = (EditText) findViewById(R.id.contactNameEditText);
        if (mText != null) {
            mContactNameEditText.setText(mText);
            mContactSaveButton.setVisibility(View.VISIBLE);
        }
        mContactNameEditText.addTextChangedListener(mTextListener);

        // Prepare the system account manager. On registering the listener below, we also ask for
        // an initial callback to pre-populate the account list.
        AccountManager.get(this).addOnAccountsUpdatedListener(this, null, true);

        mContactSaveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSaveButtonClicked();
            }
        });
        mAdderFragment = new ContactAdderFragment();
        getFragmentManager().beginTransaction()
                .add(R.id.list_fragment, mAdderFragment).hide(mAdderFragment)
                .commit();
        mOtherRawId = 0;
    }

    public void setContactName(String name) {
        mContactNameEditText.setText(name);
        exitSearchUi();
        if (!TextUtils.isEmpty(name))
            mContactSaveButton.setVisibility(View.VISIBLE);
    }

    /**
     * Actions for when the Save button is clicked. Creates a contact entry and terminates the
     * activity.
     */
    private void onSaveButtonClicked() {
        createContactEntry();
        finish();
    }

    private boolean parseIntent(Intent intent) {
        mAssertedName = intent.getStringExtra("AssertedName");
        mText = intent.getStringExtra("Text");
        Log.i(TAG, "Intent data: " + mAssertedName + " (" + mText + ")");
        mText = Utilities.removeUriPartsSelective(mText);
        return !(TextUtils.isEmpty(mAssertedName) || TextUtils.isEmpty(mText));
    }

    /**
     * Creates a contact entry from the current UI values in the account named by mSelectedAccount.
     */
    protected void createContactEntry() {
        // Get values from UI
        String name = mContactNameEditText.getText().toString();
        if (TextUtils.isEmpty(name))
            return;

        // Prepare contact creation request
        //
        // Note: We create a new RawContact because this data must be associated with the SC account.
        //       The system will aggregate this with any other data for this contact and create a
        //       corresponding entry in the ContactsContract.Contacts provider for us.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, mSelectedAccount.type)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, mSelectedAccount.name)
                .build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build());

        if (mOtherRawId != 0) {
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, mOtherRawId)
                    .withValueBackReference(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, 0)
                    .build());
        }

        String phoneName = UpdateScContactDataService.formatWithTags(name, false /*discovered*/, true /*isSip*/);

        ContentProviderOperation.Builder opBuilder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        opBuilder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, UpdateScContactDataService.SC_PHONE_CONTENT_TYPE)
                .withValue(ContactsContract.Data.DATA1, mAssertedName)
                .withValue(ContactsContract.Data.DATA2, "Silent Circle")
                .withValue(ContactsContract.Data.DATA3, phoneName)
                .withValue(ContactsContract.Data.SYNC1, 1);
        ops.add(opBuilder.build());

        // Enable this for numbers also once we provide messaging via numbers. A discovered contact may
        // be a number. 'isSip' also covers discovered e-mail addresses.
        String msgName = name;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            msgName = msgName + " (" + UpdateScContactDataService.SC_TEXT +")";
        }
        opBuilder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        opBuilder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, UpdateScContactDataService.SC_MSG_CONTENT_TYPE)
                .withValue(ContactsContract.Data.DATA1, mAssertedName)
                .withValue(ContactsContract.Data.DATA2, "Silent Circle")
                .withValue(ContactsContract.Data.DATA3, msgName)
                .withValue(ContactsContract.Data.SYNC1, 1);
//                Log.d(TAG, "++++ operation: " + msgName + ", name: " + mAssertedName);
        ops.add(opBuilder.build());



        // Ask the Contact provider to create a new contact
        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "Selected account: " + mSelectedAccount.name + " (" + mSelectedAccount.type + ")");
            Log.d(TAG, "Creating contact: " + name);
        }
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            // Display warning
            Context ctx = getApplicationContext();
            CharSequence txt = getString(R.string.contactCreationFailure);
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(ctx, txt, duration);
            toast.show();

            // Log exception
            Log.e(TAG, "Exception encountered while inserting contact: " + e);
        }
    }

    /**
     * Called when this activity is about to be destroyed by the system.
     */
    @Override
    public void onDestroy() {
        // Remove AccountManager callback
        super.onDestroy();
        AccountManager.get(this).removeOnAccountsUpdatedListener(this);
    }

    /**
     * Updates account list spinner when the list of Accounts on the system changes. Satisfies
     * OnAccountsUpdateListener implementation.
     */
    public void onAccountsUpdated(Account[] a) {
        // Get account data from system
        Account[] accounts = AccountManager.get(this).getAccountsByType(AccountConstants.ACCOUNT_TYPE);
        AccountManager.get(this).removeOnAccountsUpdatedListener(this);
        if (accounts.length == 0) {
            createScAccount();
            return;
        }
        mSelectedAccount = accounts[0];
        if (ConfigurationUtilities.mTrace)
            Log.d(TAG, "Selected account: " + mSelectedAccount.name + " (" + mSelectedAccount.type + ")");
    }

    public Account getAccountInfo() {
        return mSelectedAccount;
    }

    public void setOtherRawId(long id) {
        mOtherRawId = id;
    }

    private void createScAccount() {
        // Check if user correctly provisioned the device
        byte[] data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (data == null) {
            Log.e(TAG, "Authentication data is null");
            return;
        }

        final AccountManager am = AccountManager.get(this);

        final Bundle addAccountOptions = new Bundle();
        addAccountOptions.putString(AccountConstants.SC_ACCOUNT_NAME, LoadUserInfo.getDisplayAlias());

        // This convenience helper combines the functionality of
        // getAccountsByTypeAndFeatures(String, String[], AccountManagerCallback, Handler),
        // getAuthToken(Account, String, Bundle, Activity, AccountManagerCallback, Handler),
        // and addAccount(String, String, String[], Bundle, Activity, AccountManagerCallback, Handler).
        am.getAuthTokenByFeatures(AccountConstants.ACCOUNT_TYPE, AccountConstants.ACCOUNT_ACCESS, null /*features */,
                this, addAccountOptions, null,
                new AccountManagerCallback<Bundle>() {
                    public void run(AccountManagerFuture<Bundle> future) {
                        try {
                            final Bundle result = future.getResult();
                            if (ConfigurationUtilities.mTrace) Log.d(TAG, "useAccountManager result: " + result);
                            final Bundle userData = result.getBundle(AccountManager.KEY_USERDATA);
                            if (userData == null) {
                                Log.e(TAG, "AccountManager did not provide provisioning data");
                                return;
                            }
                            Account[] accounts = am.getAccountsByType(AccountConstants.ACCOUNT_TYPE);
                            if (accounts.length > 0)
                                mSelectedAccount = accounts[0];

                            Log.i(TAG, "Created account: " + mSelectedAccount.name + " (" + mSelectedAccount.type + ")");

                        } catch (OperationCanceledException e) {
                            Log.w(TAG, "getAuthTokenByFeatures canceled", e);
                        } catch (IOException e) {
                            Log.w(TAG, "getAuthTokenByFeatures I/O problem", e);
                        } catch (AuthenticatorException e) {
                            Log.w(TAG, "getAuthTokenByFeatures Authenticator exception", e);
                        }
                    }
                }, null);
    }

    private void enterSearchUi(String query) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        findViewById(R.id.list_fragment).setVisibility(View.VISIBLE);
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.show(mAdderFragment);

        mAdderFragment.setHasOptionsMenu(false);
        mAdderFragment.setQueryString(query, false /* delaySelection */);
        transaction.commit();
        mSearchUiActive = true;
        mContactSaveButton.setVisibility(View.VISIBLE);
    }

    private void exitSearchUi() {
        findViewById(R.id.list_fragment).setVisibility(View.GONE);
        mAdderFragment.setQueryString(null, false /* delaySelection */);
        getFragmentManager().beginTransaction()
                .hide(mAdderFragment).commit();
        mSearchUiActive = false;
        mContactSaveButton.setVisibility(View.INVISIBLE);
    }

    private static class UidBackgroundTask extends AsyncTask<String, Void, Integer> {
        private String mAlias;
        protected byte[] mUserInfo;
        protected int[] errorCode = new int[1];

        @Override
        protected Integer doInBackground(String... alias) {
            mAlias = alias[0];
            long startTime = System.currentTimeMillis();
            if (mAlias != null) {
                mUserInfo = AxoMessaging.getUserInfo(mAlias, null, errorCode);
            }
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for getUserInfo '" + mAlias + "': " + time);
        }
    }
}
