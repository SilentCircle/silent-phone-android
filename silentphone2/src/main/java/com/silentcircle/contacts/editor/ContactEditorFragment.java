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
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
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
 * limitations under the License
 */

package com.silentcircle.contacts.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import com.silentcircle.contacts.GroupMetaDataLoader;
import com.silentcircle.contacts.ScContactSaveService;
import com.silentcircle.contacts.activities.ScContactEditorActivity;
import com.silentcircle.contacts.detail.PhotoSelectionHandler;
import com.silentcircle.contacts.detail.PhotoSelectionHandler19;
import com.silentcircle.contacts.model.AccountTypeManager;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.model.ContactLoader;
import com.silentcircle.contacts.model.RawContact;
import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.RawContactDeltaList;
import com.silentcircle.contacts.model.RawContactModifier;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.utils.ContactPhotoUtils;
import com.silentcircle.contacts.utils.ContactPhotoUtils19;
import com.silentcircle.contacts.utils.HelpUtils;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentcontacts2.ScContactsContract.Intents.Insert;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Organization;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Photo;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.SipAddress;
import com.silentcircle.silentcontacts2.ScContactsContract.Groups;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Comparator;

import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;

// import android.app.DialogFragment;
//import com.android.contacts.GroupMetaDataLoader;
//import com.android.contacts.activities.ContactEditorAccountsChangedActivity;
//import com.android.contacts.activities.JoinContactActivity;
//import com.android.contacts.editor.AggregationSuggestionEngine.Suggestion;
// import com.android.contacts.model.account.AccountWithDataSet;
// import com.android.contacts.model.account.GoogleAccountType;
//import com.silentcircle.silentcontacts2.utils.AccountsListAdapter;
//import com.android.contacts.util.AccountsListAdapter.AccountListFilter;

public class ContactEditorFragment extends Fragment /* implements SplitContactConfirmationDialogFragment.Listener,
         AggregationSuggestionEngine.Listener, AggregationSuggestionView.Listener, */
         {

    private static final String TAG = ContactEditorFragment.class.getSimpleName();

    private static final int LOADER_DATA = 1;
    private static final int LOADER_GROUPS = 2;

    private static final String KEY_URI = "uri";
    private static final String KEY_ACTION = "action";
    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_RAW_CONTACT_ID_REQUESTING_PHOTO = "photorequester";
    private static final String KEY_VIEW_ID_GENERATOR = "viewidgenerator";
    private static final String KEY_CURRENT_PHOTO_FILE = "currentphotofile";
    private static final String KEY_CURRENT_PHOTO_URI = "currentphotouri";
    private static final String KEY_CONTACT_ID_FOR_JOIN = "contactidforjoin";
    private static final String KEY_CONTACT_WRITABLE_FOR_JOIN = "contactwritableforjoin";
    private static final String KEY_SHOW_JOIN_SUGGESTIONS = "showJoinSuggestions";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NEW_LOCAL_PROFILE = "newLocalProfile";
    private static final String KEY_IS_USER_PROFILE = "isUserProfile";
    private static final String KEY_UPDATED_PHOTOS = "updatedPhotos";

    public static final String SAVE_MODE_EXTRA_KEY = "saveMode";


    /**
     * An intent extra that forces the editor to add the edited contact
     * to the default group (e.g. "My Contacts").
     */
    public static final String INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY = "addToDefaultDirectory";

    public static final String INTENT_EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";

    /**
     * Modes that specify what the AsyncTask has to perform after saving
     */
    public interface SaveMode {
        /**
         * Close the editor after saving
         */
        public static final int CLOSE = 0;

        /**
         * Reload the data so that the user can continue editing
         */
        public static final int RELOAD = 1;

        /**
         * Split the contact after saving
         */
        public static final int SPLIT = 2;

        /**
         * Join another contact after saving
         */
        public static final int JOIN = 3;

        /**
         * Navigate to Contacts Home activity after saving.
         */
        public static final int HOME = 4;
    }

    private interface Status {
        /**
         * The loader is fetching data
         */
        public static final int LOADING = 0;

        /**
         * Not currently busy. We are waiting for the user to enter data
         */
        public static final int EDITING = 1;

        /**
         * The data is currently being saved. This is used to prevent more
         * auto-saves (they shouldn't overlap)
         */
        public static final int SAVING = 2;

        /**
         * Prevents any more saves. This is used if in the following cases:
         * - After Save/Close
         * - After Revert
         * - After the user has accepted an edit suggestion
         */
        public static final int CLOSING = 3;

        /**
         * Prevents saving while running a child activity.
         */
        public static final int SUB_ACTIVITY = 4;
    }

    private static final int REQUEST_CODE_JOIN = 0;
    private static final int REQUEST_CODE_ACCOUNTS_CHANGED = 1;

    /**
     * The raw contact for which we started "take photo" or "choose photo from gallery" most
     * recently.  Used to restore {@link #mCurrentPhotoHandler} after orientation change.
     */
    private long mRawContactIdRequestingPhoto;
    /**
     * The {@link PhotoHandler} for the photo editor for the {@link #mRawContactIdRequestingPhoto}
     * raw contact.
     *
     * A {@link PhotoHandler} is created for each photo editor in {@link #bindPhotoHandler}, but
     * the only "active" one should get the activity result.  This member represents the active
     * one.
     */
    private PhotoHandler mCurrentPhotoHandler;
    private PhotoHandler19 mCurrentPhotoHandler19;

    private final EntityDeltaComparator mComparator = new EntityDeltaComparator();

    private Cursor mGroupMetaData;

    private String mCurrentPhotoFile;
    private Uri mCurrentPhotoUri;
    private Bundle mUpdatedPhotos = new Bundle();

    private Context mContext;
    private String mAction;
    private Uri mLookupUri;
    private Bundle mIntentExtras;
    private Listener mListener;

    private long mContactIdForJoin;
    private boolean mContactWritableForJoin;

    private ContactEditorUtils mEditorUtils;

    private LinearLayout mContent;
    private RawContactDeltaList mState;

    private ViewIdGenerator mViewIdGenerator;

    private long mLoaderStartTime;

    private int mStatus;

//    private AggregationSuggestionEngine mAggregationSuggestionEngine;
    private long mAggregationSuggestionsRawContactId;
    private View mAggregationSuggestionView;

    private ListPopupWindow mAggregationSuggestionPopup;

//    private static final class AggregationSuggestionAdapter extends BaseAdapter {
//        private final Activity mActivity;
//        private final boolean mSetNewContact;
//        private final AggregationSuggestionView.Listener mListener;
//        private final List<Suggestion> mSuggestions;
//
//        public AggregationSuggestionAdapter(Activity activity, boolean setNewContact,
//                AggregationSuggestionView.Listener listener, List<Suggestion> suggestions) {
//            mActivity = activity;
//            mSetNewContact = setNewContact;
//            mListener = listener;
//            mSuggestions = suggestions;
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//            Suggestion suggestion = (Suggestion) getItem(position);
//            LayoutInflater inflater = mActivity.getLayoutInflater();
//            AggregationSuggestionView suggestionView =
//                    (AggregationSuggestionView) inflater.inflate(
//                            R.layout.aggregation_suggestions_item, null);
//            suggestionView.setNewContact(mSetNewContact);
//            suggestionView.setListener(mListener);
//            suggestionView.bindSuggestion(suggestion);
//            return suggestionView;
//        }
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//        @Override
//        public Object getItem(int position) {
//            return mSuggestions.get(position);
//        }
//
//        @Override
//        public int getCount() {
//            return mSuggestions.size();
//        }
//    }
//
//    private OnItemClickListener mAggregationSuggestionItemClickListener =
//            new OnItemClickListener() {
//        @Override
//        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//            final AggregationSuggestionView suggestionView = (AggregationSuggestionView) view;
//            suggestionView.handleItemClickEvent();
//            mAggregationSuggestionPopup.dismiss();
//            mAggregationSuggestionPopup = null;
//        }
//    };
//
    private boolean mAutoAddToDefaultGroup;

    private boolean mEnabled = true;
    private boolean mRequestFocus;
    private boolean mNewLocalProfile = false;
    private boolean mIsUserProfile = false;

    public ContactEditorFragment() {
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (mContent != null) {
                int count = mContent.getChildCount();
                for (int i = 0; i < count; i++) {
                    mContent.getChildAt(i).setEnabled(enabled);
                }
            }
//            setAggregationSuggestionViewEnabled(enabled);
            final Activity activity = getActivity();
            if (activity != null)
                activity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mEditorUtils = ContactEditorUtils.getInstance(mContext);
    }

    @Override
    public void onStop() {
        super.onStop();
//        if (mAggregationSuggestionEngine != null) {
//            mAggregationSuggestionEngine.quit();
//        }

        // If anything was left unsaved, save it now but keep the editor open. TODO
        if (/*!getActivity().isChangingConfigurations() && */ mStatus == Status.EDITING) {
            save(SaveMode.RELOAD);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View view = inflater.inflate(R.layout.contact_editor_fragment, container, false);

        mContent = (LinearLayout) view.findViewById(R.id.editors);

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        validateAction(mAction);

        // Handle initial actions only when existing state missing
        final boolean hasIncomingState = savedInstanceState != null;

        if (mState == null) {
            // The delta list may not have finished loading before orientation change happens.
            // In this case, there will be a saved state but deltas will be missing.  Reload from
            // database.
            if (Intent.ACTION_EDIT.equals(mAction)) {
                // Either...
                // 1) orientation change but load never finished.
                // or
                // 2) not an orientation change.  data needs to be loaded for first time.
                getLoaderManager().initLoader(LOADER_DATA, null, mDataLoaderListener);
            }
        } 
        else {
            // Orientation change, we already have mState, it was loaded by onCreate
            bindEditors();
        }

        if (!hasIncomingState) {
            if (Intent.ACTION_INSERT.equals(mAction)) {
                selectAccountAndCreateContact();
            }
        }
    }

    /**
     * Checks if the requested action is valid.
     *
     * @param action The action to test.
     * @throws IllegalArgumentException when the action is invalid.
     */
    private void validateAction(String action) {
        if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_INSERT.equals(action) ||
                ScContactEditorActivity.ACTION_SAVE_COMPLETED.equals(action)) {
            return;
        }
        throw new IllegalArgumentException("Unknown Action String " + mAction +
                ". Only support " + Intent.ACTION_EDIT + " or " + Intent.ACTION_INSERT + " or " +
                ScContactEditorActivity.ACTION_SAVE_COMPLETED);
    }

    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupLoaderListener);
        super.onStart();
    }

    public void load(String action, Uri lookupUri, Bundle intentExtras) {
        mAction = action;
        mLookupUri = lookupUri;
        mIntentExtras = intentExtras;
        mAutoAddToDefaultGroup = mIntentExtras != null && mIntentExtras.containsKey(INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY);
        mNewLocalProfile = mIntentExtras != null && mIntentExtras.getBoolean(INTENT_EXTRA_NEW_LOCAL_PROFILE);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState != null) {
            // Restore mUri before calling super.onCreate so that onInitializeLoaders
            // would already have a uri and an action to work with
            mLookupUri = savedState.getParcelable(KEY_URI);
            mAction = savedState.getString(KEY_ACTION);
        }

        super.onCreate(savedState);

        if (savedState == null) {
            // If savedState is non-null, onRestoreInstanceState() will restore the generator.
            mViewIdGenerator = new ViewIdGenerator();
        }
        else {
            // Read state from savedState. No loading involved here
            mState = savedState.<RawContactDeltaList> getParcelable(KEY_EDIT_STATE);
            mRawContactIdRequestingPhoto = savedState.getLong(KEY_RAW_CONTACT_ID_REQUESTING_PHOTO);
            mViewIdGenerator = savedState.getParcelable(KEY_VIEW_ID_GENERATOR);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                mCurrentPhotoFile = savedState.getString(KEY_CURRENT_PHOTO_FILE);
            else
                mCurrentPhotoUri = savedState.getParcelable(KEY_CURRENT_PHOTO_URI);
            mContactIdForJoin = savedState.getLong(KEY_CONTACT_ID_FOR_JOIN);
            mContactWritableForJoin = savedState.getBoolean(KEY_CONTACT_WRITABLE_FOR_JOIN);
            mAggregationSuggestionsRawContactId = savedState.getLong(KEY_SHOW_JOIN_SUGGESTIONS);
            mEnabled = savedState.getBoolean(KEY_ENABLED);
            mStatus = savedState.getInt(KEY_STATUS);
            mNewLocalProfile = savedState.getBoolean(KEY_NEW_LOCAL_PROFILE);
            mIsUserProfile = savedState.getBoolean(KEY_IS_USER_PROFILE);
            mUpdatedPhotos = savedState.getParcelable(KEY_UPDATED_PHOTOS);
            mSavingState = false;
        }
    }

    public void setData(Contact data) {
        // If we have already loaded data, we do not want to change it here to not confuse the user
        if (mState != null) {
            Log.v(TAG, "Ignoring background change. This will have to be rebased later");
            return;
        }
        bindEditorsForExistingContact(data);
    }

    private void bindEditorsForExistingContact(Contact contact) {
        setEnabled(true);

        mState = contact.createRawContactDeltaList();
        setIntentExtras(mIntentExtras);
        mIntentExtras = null;

        mIsUserProfile = contact.isUserProfile();
        mRequestFocus = true;

        bindEditors();
    }

    /**
     * Merges extras from the intent.
     */
    public void setIntentExtras(Bundle extras) {
        if (extras == null || extras.size() == 0) {
            return;
        }

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        for (RawContactDelta state : mState) {
            final AccountType type = state.getAccountType(accountTypes);
            if (type.areContactsWritable()) {
                // Apply extras to the first writable raw contact only
                RawContactModifier.parseExtras(mContext, type, state, extras);
                break;
            }
        }
    }

    private void selectAccountAndCreateContact() {
        createContact();
     }

    /**
     * Create a contact by automatically selecting the first account. If there's no available
     * account, a device-local contact should be created.
     */
    private void createContact() {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        final AccountType accountType = accountTypes.getAccountType();

        bindEditorsForNewContact(accountType);
    }

    /**
     * Removes a current editor ({@link #mState}) and rebinds new editor for a new account.
     * Some of old data are reused with new restriction enforced by the new account.
     *
     * @ param oldState Old data being edited.
     * @ param oldAccount Old account associated with oldState.
     * @ param newAccount New account to be used.
     */
//    private void rebindEditorsForNewContact(RawContactDelta oldState, AccountWithDataSet oldAccount, AccountWithDataSet newAccount) {
//        AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
//        AccountType oldAccountType = accountTypes.getAccountType(oldAccount.type, oldAccount.dataSet);
//        AccountType newAccountType = accountTypes.getAccountType(newAccount.type, newAccount.dataSet);
//
//        if (newAccountType.getCreateContactActivityClassName() != null) {
//            Log.w(TAG, "external activity called in rebind situation");
//            if (mListener != null) {
//                mListener.onCustomCreateContactActivityRequested(newAccount, mIntentExtras);
//            }
//        } else {
//            mState = null;
//            bindEditorsForNewContact(newAccount, newAccountType, oldState, oldAccountType);
//        }
//    }

    private void bindEditorsForNewContact(final AccountType accountType) {
        bindEditorsForNewContact(accountType, null, null);
    }

    // AccountType shall be the FallbackAccountType
    private void bindEditorsForNewContact(final AccountType newAccountType, RawContactDelta oldState, AccountType oldAccountType) {

        mStatus = Status.EDITING;

        final RawContact rawContact = new RawContact(mContext);
        rawContact.setAccountToLocal();

        // Pre-populate Silent Circle specific fields
        if (mIntentExtras != null) {
            String sipName = null;
            String imName = null;
            if (mIntentExtras.containsKey(Insert.SIP_ADDRESS))
                sipName = Utilities.removeSipParts(mIntentExtras.getString(Insert.SIP_ADDRESS));
            if (mIntentExtras.containsKey(Insert.IM_HANDLE))
                imName = Utilities.getUsernameFromUriNumber(mIntentExtras.getString(Insert.IM_HANDLE));

            // Use the SIP name to pre-populate the IM entry for a new contact
            if (sipName != null && imName == null) {
                mIntentExtras.putString(Insert.IM_HANDLE, sipName + getString(R.string.sc_text_domain_0));
                mIntentExtras.putInt(Insert.IM_PROTOCOL, Im.PROTOCOL_SILENT);
            }

            // Use the IM (Silent Text) name to pre-populate the SIP entry for a new contact
            if (sipName == null && imName != null) {
                mIntentExtras.putString(Insert.SIP_ADDRESS, imName + getString(R.string.sc_sip_domain_0));
            }
        }
        RawContactDelta insert = new RawContactDelta(RawContactDelta.ValuesDelta.fromAfter(rawContact.getValues()));
        if (oldState == null) {
            // Parse any values from incoming intent
            RawContactModifier.parseExtras(mContext, newAccountType, insert, mIntentExtras);
        }
        else {
            RawContactModifier.migrateStateForNewContact(mContext, oldState, insert, oldAccountType, newAccountType);
        }

        // Ensure we have some default fields (if the account type does not support a field,
        // ensureKind will not add it, so it is safe to add e.g. Event)
        RawContactModifier.ensureKindExists(insert, newAccountType, Phone.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType, Email.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType, Organization.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType, SipAddress.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(insert, newAccountType, Im.CONTENT_ITEM_TYPE);

//        RawContactModifier.ensureKindExists(insert, newAccountType, StructuredPostal.CONTENT_ITEM_TYPE);

        // Set the correct Contact tyep for saving the contact as a profile
        if (mNewLocalProfile) {
            insert.setProfileEdit();
        }

        if (mState == null) {
            // Create state if none exists yet
            mState = RawContactDeltaList.fromSingle(insert);
        }
        else {
            // Add contact onto end of existing state
            mState.add(insert);
        }
        mRequestFocus = true;

        bindEditors();
    }

    private TextFieldsEditorView mNameEditor;
    private void bindEditors() {
        // bindEditors() can only bind views if there is data in mState, so immediately return
        // if mState is null
        if (mState == null) {
            return;
        }

        // Sort the editors
        Collections.sort(mState, mComparator);

        // Remove any existing editors and rebuild any visible
        mContent.removeAllViews();

        final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        int numRawContacts = mState.size();

        for (int i = 0; i < numRawContacts; i++) {
            // TODO ensure proper ordering of entities in the list
            final RawContactDelta rawContactDelta = mState.get(i);
            if (!rawContactDelta.isVisible()) 
                continue;

            final AccountType type = rawContactDelta.getAccountType(accountTypes);
            final long rawContactId = rawContactDelta.getRawContactId();

            final BaseRawContactEditorView editor;
            editor = (RawContactEditorView) inflater.inflate(R.layout.raw_contact_editor_view, mContent, false);
            ((RawContactEditorView)editor).setParent(this);

            editor.setEnabled(mEnabled);

            mContent.addView(editor);

            editor.setState(rawContactDelta, type, mViewIdGenerator, isEditingUserProfile());

            // Set up the photo handler.
            bindPhotoHandler(editor, type, mState);

            // If a new photo was chosen but not yet saved, we need to
            // update the thumbnail to reflect this.
            Bitmap bitmap = updatedBitmapForRawContact(rawContactId);
            if (bitmap != null) 
                editor.setPhotoBitmap(bitmap);

            if (editor instanceof RawContactEditorView) {
                final Activity activity = getActivity();
                final RawContactEditorView rawContactEditor = (RawContactEditorView) editor;
                Editor.EditorListener listener = new Editor.EditorListener() {

                    @Override
                    public void onRequest(int request) {
                        if (activity.isFinishing()) { // Make sure activity is still running.
                            return;
                        }
                        if (request == Editor.EditorListener.FIELD_CHANGED && !isEditingUserProfile()) {
                            acquireAggregationSuggestions(activity, rawContactEditor);
                        }
                    }

                    @Override
                    public void onDeleteRequested(Editor removedEditor) {
                    }
                };

                final TextFieldsEditorView nameEditor = rawContactEditor.getNameEditor();
                mNameEditor = nameEditor;
                if (mRequestFocus) {
                    nameEditor.requestFocus();
                    mRequestFocus = false;
                }
                nameEditor.setEditorListener(listener);

                final TextFieldsEditorView phoneticNameEditor = rawContactEditor.getPhoneticNameEditor();
                phoneticNameEditor.setEditorListener(listener);
                rawContactEditor.setAutoAddToDefaultGroup(mAutoAddToDefaultGroup);

                if (rawContactId == mAggregationSuggestionsRawContactId) {
                    acquireAggregationSuggestions(activity, rawContactEditor);
                }
            }
        }

        mRequestFocus = false;

        bindGroupMetaData();

        // Show editor now that we've loaded state
        mContent.setVisibility(View.VISIBLE);

        // Refresh Action Bar as the visibility of the join command
        // Activity can be null if we have been detached from the Activity
        final Activity activity = getActivity();
        if (activity != null) 
            activity.invalidateOptionsMenu();
    }

    /**
     * If we've stashed a temporary file containing a contact's new photo,
     * decode it and return the bitmap.
     * @param rawContactId identifies the raw-contact whose Bitmap we'll try to return.
     * @return Bitmap of photo for specified raw-contact, or null
    */
    private Bitmap updatedBitmapForRawContact(long rawContactId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            String path = mUpdatedPhotos.getString(String.valueOf(rawContactId));
            if (path == null)
                return null;
            return BitmapFactory.decodeFile(path);
        }
        else {
//        Uri uri = mUpdatedPhotos.getParcelable(String.valueOf(rawContactId));
//        if (uri == null)
//            return null;
//        try {
//            return ContactPhotoUtils19.getBitmapFromUri(mContext, uri);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//            return null;
//        }
            return null;
        }
    }

    private void bindPhotoHandler(BaseRawContactEditorView editor, AccountType type, RawContactDeltaList state) {
        final int mode;
        if (type.areContactsWritable()) {
            if (editor.hasSetPhoto()) {
                if (hasMoreThanOnePhoto()) {
                    mode = PhotoActionPopup.Modes.PHOTO_ALLOW_PRIMARY;
                } else {
                    mode = PhotoActionPopup.Modes.PHOTO_DISALLOW_PRIMARY;
                }
            } else {
                mode = PhotoActionPopup.Modes.NO_PHOTO;
            }
        } else {
            if (editor.hasSetPhoto() && hasMoreThanOnePhoto()) {
                mode = PhotoActionPopup.Modes.READ_ONLY_ALLOW_PRIMARY;
            } else {
                // Read-only and either no photo or the only photo ==> no options
                editor.getPhotoEditor().setEditorListener(null);
                return;
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            final PhotoHandler photoHandler = new PhotoHandler(mContext, editor, mode, state, this);
            editor.getPhotoEditor().setEditorListener((PhotoHandler.PhotoEditorListener) photoHandler.getListener());

            // Note a newly created raw contact gets some random negative ID, so any value is valid
            // here. (i.e. don't check against -1 or anything.)
            if (mRawContactIdRequestingPhoto == editor.getRawContactId()) {
                mCurrentPhotoHandler = photoHandler;
            }
        }
        else {
            final PhotoHandler19 photoHandler = new PhotoHandler19(mContext, editor, mode, state, this);
            editor.getPhotoEditor().setEditorListener((PhotoHandler19.PhotoEditorListener) photoHandler.getListener());

            // Note a newly created raw contact gets some random negative ID, so any value is valid
            // here. (i.e. don't check against -1 or anything.)
            if (mRawContactIdRequestingPhoto == editor.getRawContactId()) {
                mCurrentPhotoHandler19 = photoHandler;
            }
        }
    }

    private void bindGroupMetaData() {
        if (mGroupMetaData == null) {
            return;
        }

        int editorCount = mContent.getChildCount();
        for (int i = 0; i < editorCount; i++) {
            BaseRawContactEditorView editor = (BaseRawContactEditorView) mContent.getChildAt(i);
            editor.setGroupMetaData(mGroupMetaData);
        }
    }

    private void saveDefaultAccountIfNecessary() {
        // Verify that this is a newly created contact, that the contact is composed of only
        // 1 raw contact, and that the contact is not a user profile.
        if (!Intent.ACTION_INSERT.equals(mAction) && mState.size() == 1 &&
                !isEditingUserProfile()) {
            return;
        }

        // Find the associated account for this contact (retrieve it here because there are
        // multiple paths to creating a contact and this ensures we always have the correct
        // account).
        final RawContactDelta rawContactDelta = mState.get(0);
        mEditorUtils.saveDefaultAndAllAccounts();
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.edit_contact, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // This supports the keyboard shortcut to save changes to a contact but shouldn't be visible
        // because the custom action bar contains the "save" button now (not the overflow menu).
        // TODO: Find a better way to handle shortcuts, i.e. onKeyDown()?
        final MenuItem doneMenu = menu.findItem(R.id.menu_done);
//        final MenuItem splitMenu = menu.findItem(R.id.menu_split);
//        final MenuItem joinMenu = menu.findItem(R.id.menu_join);
        final MenuItem helpMenu = menu.findItem(R.id.menu_help);

        // Set visibility of menus
        doneMenu.setVisible(false);

//        // Split only if more than one raw profile and not a user profile
//        splitMenu.setVisible(mState != null && mState.size() > 1 && !isEditingUserProfile());
//
//        // Cannot join a user profile
//        joinMenu.setVisible(!isEditingUserProfile());

        // help menu depending on whether this is inserting or editing
        if (Intent.ACTION_INSERT.equals(mAction)) {
            // inserting
            HelpUtils.prepareHelpMenuItem(mContext, helpMenu, R.string.help_url_people_add);
        }
        else if (Intent.ACTION_EDIT.equals(mAction)) {
            // editing
            HelpUtils.prepareHelpMenuItem(mContext, helpMenu, R.string.help_url_people_edit);
        }
        else {
            // something else, so don't show the help menu
            helpMenu.setVisible(false);
        }

        int size = menu.size();
        for (int i = 0; i < size; i++) {
            menu.getItem(i).setEnabled(mEnabled);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
                return save(SaveMode.CLOSE);
            case R.id.menu_discard:
                return revert();
//            case R.id.menu_split:
//                return doSplitContactAction();
//            case R.id.menu_join:
//                return doJoinContactAction();
        }
        return false;
    }

//    private boolean doSplitContactAction() {
//        if (!hasValidState()) return false;
//
//        final SplitContactConfirmationDialogFragment dialog = new SplitContactConfirmationDialogFragment();
//        dialog.setTargetFragment(this, 0);
//        dialog.show(getFragmentManager(), SplitContactConfirmationDialogFragment.TAG);
//        return true;
//    }

//    private boolean doJoinContactAction() {
//        if (!hasValidState()) {
//            return false;
//        }
//
//        // If we just started creating a new contact and haven't added any data, it's too
//        // early to do a join
//        if (mState.size() == 1 && mState.get(0).isContactInsert() && !hasPendingChanges()) {
//            Toast.makeText(mContext, R.string.toast_join_with_empty_contact, Toast.LENGTH_LONG).show();
//            return true;
//        }
//
//        return save(SaveMode.JOIN);
//    }
//
    /**
     * Check if our internal {@link #mState} is valid, usually checked before
     * performing user actions.
     */
    private boolean hasValidState() {
        return mState != null && mState.size() > 0;
    }

    /**
     * Return true if there are any edits to the current contact which need to
     * be saved.
     */
    private boolean hasPendingChanges() {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        return RawContactModifier.hasChanges(mState, accountTypes);
    }

    public void showInputInfo(String title, String msg, int positiveBtnLabel, int nagetiveBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(title, msg, positiveBtnLabel, nagetiveBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, "SilentPhoneContactEditor");
    }
    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    public boolean save(int saveMode) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            return false;

        }

        // SCA-50 - in order to save the contact, name field is required if other field has data.
        if (mNameEditor.isEmpty()) {
            if (!mSavingState && hasPendingChanges()) {
                showInputInfo(getString(R.string.information_dialog), getString(R.string.provide_your_name), android.R.string.ok, android.R.string.cancel);
                return false;
            }
        }


        // If we are about to close the editor - there is no need to refresh the data
        if (saveMode == SaveMode.CLOSE || saveMode == SaveMode.SPLIT) {
            getLoaderManager().destroyLoader(LOADER_DATA);
        }

        mStatus = Status.SAVING;

        if (!hasPendingChanges()) {
            if (mLookupUri == null && saveMode == SaveMode.RELOAD) {
                // We don't have anything to save and there isn't even an existing contact yet.
                // Nothing to do, simply go back to editing mode
                mStatus = Status.EDITING;
                return true;
            }
            onSaveCompleted(false, saveMode, mLookupUri != null, mLookupUri);
            return true;
        }

        setEnabled(false);

        // Store account as default account, only if this is a new contact
        saveDefaultAccountIfNecessary();
        checkSilentCircleEntries();

        // Save contact
        Intent intent = ScContactSaveService.createSaveContactIntent(mContext, mState,
                SAVE_MODE_EXTRA_KEY, saveMode, isEditingUserProfile(),
                ((Activity) mContext).getClass(), ScContactEditorActivity.ACTION_SAVE_COMPLETED,
                mUpdatedPhotos);
        mContext.startService(intent);

        // Don't try to save the same photos twice.
        mUpdatedPhotos = new Bundle();

        return true;
    }

    /**
     * Check for SilentCircle entries in the current state and append SC domains if it does not contains a domain.
     */
    private void checkSilentCircleEntries() {
        // Check if we are displaying anything here
        for (RawContactDelta state : mState) {
            boolean hasEntries = state.hasMimeEntries(SipAddress.CONTENT_ITEM_TYPE);
            if (!hasEntries)
                continue;
            for (RawContactDelta.ValuesDelta entry : state.getMimeEntries(SipAddress.CONTENT_ITEM_TYPE)) {
                final String address = entry.getAsString(SipAddress.SIP_ADDRESS);
                if (!TextUtils.isEmpty(address) && !PhoneNumberHelper.isUriNumber(address)) {
                    entry.put(SipAddress.SIP_ADDRESS, address + getString(R.string.sc_sip_domain_0));
                }
            }
        }
        for (RawContactDelta state : mState) {
            boolean hasEntries = state.hasMimeEntries(Im.CONTENT_ITEM_TYPE);
            if (!hasEntries)
                continue;
            for (RawContactDelta.ValuesDelta entry : state.getMimeEntries(Im.CONTENT_ITEM_TYPE)) {
                int type = entry.getAsInteger(Im.PROTOCOL, -1);
                if (type != ScContactsContract.CommonDataKinds.Im.TYPE_SILENT)
                    continue;

                final String address = entry.getAsString(Im.DATA);
                if (!TextUtils.isEmpty(address) && !PhoneNumberHelper.isUriNumber(address)) {
                    entry.put(Im.DATA, address + getString(R.string.sc_text_domain_0));
                }
            }
        }
    }

    public static class CancelEditDialogFragment extends DialogFragment {

        public static void show(ContactEditorFragment fragment) {
            CancelEditDialogFragment dialog = new CancelEditDialogFragment();
            dialog.setTargetFragment(fragment, 0);
            dialog.show(fragment.getFragmentManager(), "cancelEditor");
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setIconAttribute(android.R.attr.alertDialogIcon);

            AlertDialog dialog = builder.setMessage(R.string.cancel_confirmation_dialog_message)
                    .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int whichButton) {
                                ((ContactEditorFragment)getTargetFragment()).doRevertAction();
                            }
                        }
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            return dialog;
        }
    }

    private boolean revert() {
        if (mState == null || !hasPendingChanges()) {
            doRevertAction();
        } else {
            CancelEditDialogFragment.show(this);
        }
        return true;
    }

    private void doRevertAction() {
        // When this Fragment is closed we don't want it to auto-save
        mStatus = Status.CLOSING;
        if (mListener != null) mListener.onReverted();
    }

    public void doSaveAction() {
        save(SaveMode.CLOSE);
    }

    public void onJoinCompleted(Uri uri) {
        onSaveCompleted(false, SaveMode.RELOAD, uri != null, uri);
    }

    public void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded, Uri contactLookupUri) {
        if (hadChanges) {
            if (saveSucceeded) {
                if (saveMode != SaveMode.JOIN) {
                    Toast.makeText(mContext, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(mContext, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }
        }
        switch (saveMode) {
            case SaveMode.CLOSE:
            case SaveMode.HOME:
                final Intent resultIntent;
                if (saveSucceeded && contactLookupUri != null) {
                    final String requestAuthority = mLookupUri == null ? null : mLookupUri.getAuthority();

                    resultIntent = new Intent();
                    resultIntent.setAction(Intent.ACTION_VIEW);

                    // Otherwise pass back a lookup-style Uri
                    resultIntent.setData(contactLookupUri);
                } 
                else {
                    resultIntent = null;
                }
                // It is already saved, so prevent that it is saved again
                mStatus = Status.CLOSING;
                if (mListener != null) 
                    mListener.onSaveFinished(resultIntent);
                break;

            case SaveMode.RELOAD:
            case SaveMode.JOIN:
                if (saveSucceeded && contactLookupUri != null) {
//                    // If it was a JOIN, we are now ready to bring up the join activity.
//                    if (saveMode == SaveMode.JOIN && hasValidState()) {
//                        showJoinAggregateActivity(contactLookupUri);
//                    }

                    // If this was in INSERT, we are changing into an EDIT now.
                    // If it already was an EDIT, we are changing to the new Uri now
                    mState = null;
                    load(Intent.ACTION_EDIT, contactLookupUri, null);
                    mStatus = Status.LOADING;
                    getLoaderManager().restartLoader(LOADER_DATA, null, mDataLoaderListener);
                }
                break;

            case SaveMode.SPLIT:
                mStatus = Status.CLOSING;
                if (mListener != null) {
                    mListener.onContactSplit(contactLookupUri);
                } else {
                    Log.d(TAG, "No listener registered, can not call onSplitFinished");
                }
                break;
        }
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     *
     * @param contactLookupUri the fresh URI for the currently edited contact (after saving it)
     */
//    private void showJoinAggregateActivity(Uri contactLookupUri) {
//        if (contactLookupUri == null || !isAdded()) {
//            return;
//        }
//
//        mContactIdForJoin = ContentUris.parseId(contactLookupUri);
//        mContactWritableForJoin = isContactWritable();
//        final Intent intent = new Intent(JoinContactActivity.JOIN_CONTACT);
//        intent.putExtra(JoinContactActivity.EXTRA_TARGET_CONTACT_ID, mContactIdForJoin);
//        startActivityForResult(intent, REQUEST_CODE_JOIN);
//    }

    /**
     * Performs aggregation with the contact selected by the user from suggestions or A-Z list.
     */
//    private void joinAggregate(final long contactId) {
//        Intent intent = ContactSaveService.createJoinContactsIntent(mContext, mContactIdForJoin,
//                contactId, mContactWritableForJoin,
//                ContactEditorActivity.class, ContactEditorActivity.ACTION_JOIN_COMPLETED);
//        mContext.startService(intent);
//    }
//
    /**
     * Returns true if there is at least one writable raw contact in the current contact.
     */
//    private boolean isContactWritable() {
//        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
//        int size = mState.size();
//        for (int i = 0; i < size; i++) {
//            RawContactDelta entity = mState.get(i);
//            final AccountType type = entity.getAccountType(accountTypes);
//            if (type.areContactsWritable()) {
//                return true;
//            }
//        }
//        return false;
//    }

    private boolean isEditingUserProfile() {
        return mNewLocalProfile || mIsUserProfile;
    }

    public static interface Listener {
        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete (unless it was a new contact)
         */
        void onContactNotFound();

        /**
         * Contact was split, so we can close now.
         * @param newLookupUri The lookup uri of the new contact that should be shown to the user.
         * The editor tries best to chose the most natural contact here.
         */
        void onContactSplit(Uri newLookupUri);

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(Intent resultIntent);

//        /**
//         * User switched to editing a different contact (a suggestion from the
//         * aggregation engine).
//         */
//        void onEditOtherContactRequested(Uri contactLookupUri, ArrayList<ContentValues> contentValues);
//
//        /**
//         * Contact is being created for an external account that provides its own
//         * new contact activity.
//         */
//        void onCustomCreateContactActivityRequested(AccountWithDataSet account,  Bundle intentExtras);
//
//        /**
//         * The edited raw contact belongs to an external account that provides
//         * its own edit activity.
//         *
//         * @param redirect indicates that the current editor should be closed
//         *            before the custom editor is shown.
//         */
//        void onCustomEditContactActivityRequested(AccountWithDataSet account, Uri rawContactUri, Bundle intentExtras, boolean redirect);
    }

    private class EntityDeltaComparator implements Comparator<RawContactDelta> {
        /**
         * Compare EntityDeltas for sorting the stack of editors.
         */
        @Override
        public int compare(RawContactDelta one, RawContactDelta two) {
            // Check direct equality
            if (one.equals(two)) {
                return 0;
            }

            // Both are in the same account, fall back to contact ID
            Long oneId = one.getRawContactId();
            Long twoId = two.getRawContactId();
            if (oneId == null) {
                return -1;
            } else if (twoId == null) {
                return 1;
            }

            return (int)(oneId - twoId);
        }
    }

    /**
     * Returns the contact ID for the currently edited contact or 0 if the contact is new.
     */
    protected long getContactId() {
        if (mState != null) {
            for (RawContactDelta rawContact : mState) {
                Long contactId = rawContact.getValues().getAsLong(RawContacts._ID);
                if (contactId != null) {
                    return contactId;
                }
            }
        }
        return 0;
    }

    /**
     * Triggers an asynchronous search for aggregation suggestions.
     */
    private void acquireAggregationSuggestions(Context context, RawContactEditorView rawContactEditor) {
    }


    /**
     * Joins the suggested contact (specified by the id's of constituent raw
     * contacts), save all changes, and stay in the editor.
     */
    protected void doJoinSuggestedContact(long[] rawContactIds) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            return;
        }

        mState.setJoinWithRawContacts(rawContactIds);
        save(SaveMode.RELOAD);
    }


    private boolean mSavingState;

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_URI, mLookupUri);
        outState.putString(KEY_ACTION, mAction);

        if (hasValidState()) {
            // Store entities with modifications
            outState.putParcelable(KEY_EDIT_STATE, mState);
        }
        outState.putLong(KEY_RAW_CONTACT_ID_REQUESTING_PHOTO, mRawContactIdRequestingPhoto);
        outState.putParcelable(KEY_VIEW_ID_GENERATOR, mViewIdGenerator);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            outState.putString(KEY_CURRENT_PHOTO_FILE, mCurrentPhotoFile);
        else
            outState.putParcelable(KEY_CURRENT_PHOTO_URI, mCurrentPhotoUri);
        outState.putLong(KEY_CONTACT_ID_FOR_JOIN, mContactIdForJoin);
        outState.putBoolean(KEY_CONTACT_WRITABLE_FOR_JOIN, mContactWritableForJoin);
        outState.putLong(KEY_SHOW_JOIN_SUGGESTIONS, mAggregationSuggestionsRawContactId);
        outState.putBoolean(KEY_ENABLED, mEnabled);
        outState.putBoolean(KEY_NEW_LOCAL_PROFILE, mNewLocalProfile);
        outState.putBoolean(KEY_IS_USER_PROFILE, mIsUserProfile);
        outState.putInt(KEY_STATUS, mStatus);
        outState.putParcelable(KEY_UPDATED_PHOTOS, mUpdatedPhotos);

        mSavingState = true;
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mStatus == Status.SUB_ACTIVITY) {
            mStatus = Status.EDITING;
        }

        // See if the photo selection handler handles this result.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (mCurrentPhotoHandler != null && mCurrentPhotoHandler.handlePhotoActivityResult(requestCode, resultCode, data)) {
                return;
            }
        }
        else {
            if (mCurrentPhotoHandler19 != null && mCurrentPhotoHandler19.handlePhotoActivityResult(requestCode, resultCode, data)) {
                return;
            }
        }
    }

    /**
     * Sets the photo stored in mPhoto and writes it to the RawContact with the given id
     */
    private void setPhoto(long rawContact, Bitmap photo, String photoFile, Uri photoUri) {
        BaseRawContactEditorView requestingEditor = getRawContactEditorView(rawContact);

        if (photo == null || photo.getHeight() < 0 || photo.getWidth() < 0) {
            // This is unexpected.
            Log.w(TAG, "Invalid bitmap passed to setPhoto()");
        }

        if (requestingEditor != null) {
            requestingEditor.setPhotoBitmap(photo);
        } else {
            Log.w(TAG, "The contact that requested the photo is no longer present.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            final String croppedPhotoPath = ContactPhotoUtils.pathForCroppedPhoto(mContext, photoFile);
            mUpdatedPhotos.putString(String.valueOf(rawContact), croppedPhotoPath);
        }
        else
            mUpdatedPhotos.putParcelable(String.valueOf(rawContact), photoUri);
    }

    /**
     * Finds raw contact editor view for the given rawContactId.
     */
    public BaseRawContactEditorView getRawContactEditorView(long rawContactId) {
        for (int i = 0; i < mContent.getChildCount(); i++) {
            final View childView = mContent.getChildAt(i);
            if (childView instanceof BaseRawContactEditorView) {
                final BaseRawContactEditorView editor = (BaseRawContactEditorView) childView;
                if (editor.getRawContactId() == rawContactId) {
                    return editor;
                }
            }
        }
        return null;
    }

    /**
     * Returns true if there is currently more than one photo on screen.
     */
    private boolean hasMoreThanOnePhoto() {
        int countWithPicture = 0;
        final int numEntities = mState.size();
        for (int i = 0; i < numEntities; i++) {
            final RawContactDelta entity = mState.get(i);
            if (entity.isVisible()) {
                final RawContactDelta.ValuesDelta primary = entity.getPrimaryEntry(Photo.CONTENT_ITEM_TYPE);
                if (primary != null && primary.getPhoto() != null) {
                    countWithPicture++;
                } 
                else {
                    final long rawContactId = entity.getRawContactId();
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        final String path = mUpdatedPhotos.getString(String.valueOf(rawContactId));
                        if (path != null) {
                            final File file = new File(path);
                            if (file.exists()) {
                                countWithPicture++;
                            }
                        }
                    }
                    else {
                        final Uri uri = mUpdatedPhotos.getParcelable(String.valueOf(rawContactId));
                        if (uri != null) {
                            try {
                                mContext.getContentResolver().openInputStream(uri);
                                countWithPicture++;
                            } catch (FileNotFoundException e) {
                            }
                        }
                    }
                }

                if (countWithPicture > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The listener for the data loader
     */
    private final LoaderManager.LoaderCallbacks<Contact> mDataLoaderListener =  new LoaderManager.LoaderCallbacks<Contact>() {
        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            mLoaderStartTime = SystemClock.elapsedRealtime();
            return new ContactLoader(mContext, mLookupUri, true);
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            final long loaderCurrentTime = SystemClock.elapsedRealtime();
            Log.v(TAG, "Time needed for loading: " + (loaderCurrentTime-mLoaderStartTime));
            if (!data.isLoaded()) {
                // Item has been deleted
                Log.i(TAG, "No contact found. Closing activity");
                if (mListener != null) mListener.onContactNotFound();
                return;
            }

            mStatus = Status.EDITING;
            mLookupUri = data.getLookupUri();
            final long setDataStartTime = SystemClock.elapsedRealtime();
            setData(data);
            final long setDataEndTime = SystemClock.elapsedRealtime();

            Log.v(TAG, "Time needed for setting UI: " + (setDataEndTime-setDataStartTime));
        }

        @Override
        public void onLoaderReset(Loader<Contact> loader) {
        }
    };

    /**
     * The listener for the group meta data loader for all groups.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mGroupLoaderListener = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            return new GroupMetaDataLoader(mContext, Groups.CONTENT_URI);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mGroupMetaData = data;
            bindGroupMetaData();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

//    @Override
//    public void onSplitContactConfirmed() {
//        if (mState == null) {
//            // This may happen when this Fragment is recreated by the system during users
//            // confirming the split action (and thus this method is called just before onCreate()),
//            // for example.
//            Log.e(TAG, "mState became null during the user's confirming split action. " +
//                    "Cannot perform the save action.");
//            return;
//        }
//
//        mState.markRawContactsForSplitting();
//        save(SaveMode.SPLIT);
//    }
//
    /**
     * Custom photo handler for the editor.  The inner listener that this creates also has a
     * reference to the editor and acts as an {@link com.silentcircle.contacts.editor.Editor.EditorListener},
     * and uses that editor to hold state information in several of the listener methods.
     */
    private final class PhotoHandler extends PhotoSelectionHandler {

        final long mRawContactId;
        private final BaseRawContactEditorView mEditor;
        private final PhotoActionListener mPhotoEditorListener;

        public PhotoHandler(Context context, BaseRawContactEditorView editor, int photoMode, RawContactDeltaList state, 
                Fragment fragment) {

            super(context, editor.getPhotoEditor(), photoMode, false, state, fragment);
            mEditor = editor;
            mRawContactId = editor.getRawContactId();
            mPhotoEditorListener = new PhotoEditorListener();
        }

        @Override
        public PhotoActionListener getListener() {
            return mPhotoEditorListener;
        }

        @Override
        public void startPhotoActivity(Intent intent, int requestCode, String photoFile) {
            mRawContactIdRequestingPhoto = mEditor.getRawContactId();
            mCurrentPhotoHandler = this;
            mStatus = Status.SUB_ACTIVITY;
            mCurrentPhotoFile = photoFile;
            ContactEditorFragment.this.startActivityForResult(intent, requestCode);
        }

        private final class PhotoEditorListener extends PhotoSelectionHandler.PhotoActionListener
                implements Editor.EditorListener {

            @Override
            public void onRequest(int request) {
                if (!hasValidState()) return;

                if (request == Editor.EditorListener.REQUEST_PICK_PHOTO) {
                    onClick(mEditor.getPhotoEditor());
                }
            }

            @Override
            public void onDeleteRequested(Editor removedEditor) {
                // The picture cannot be deleted, it can only be removed, which is handled by
                // onRemovePictureChosen()
            }

            /**
             * User has chosen to set the selected photo as the (super) primary photo
             */
            @Override
            public void onUseAsPrimaryChosen() {
                // Set the IsSuperPrimary for each editor
                int count = mContent.getChildCount();
                for (int i = 0; i < count; i++) {
                    final View childView = mContent.getChildAt(i);
                    if (childView instanceof BaseRawContactEditorView) {
                        final BaseRawContactEditorView editor =
                                (BaseRawContactEditorView) childView;
                        final PhotoEditorView photoEditor = editor.getPhotoEditor();
                        photoEditor.setSuperPrimary(editor == mEditor);
                    }
                }
                bindEditors();
            }

            /**
             * User has chosen to remove a picture
             */
            @Override
            public void onRemovePictureChosen() {
                mEditor.setPhotoBitmap(null);

                // Prevent bitmap from being restored if rotate the device.
                // (only if we first chose a new photo before removing it)
                mUpdatedPhotos.remove(String.valueOf(mRawContactId));
                bindEditors();
            }

            @Override
            public void onPhotoSelected(Bitmap bitmap) {
                setPhoto(mRawContactId, bitmap, mCurrentPhotoFile, null);
                mCurrentPhotoHandler = null;
                bindEditors();
            }

            @Override
            public String getCurrentPhotoFile() {
                return mCurrentPhotoFile;
            }

            @Override
            public void onPhotoSelectionDismissed() {
                // Nothing to do.
            }
        }
    }

    /**
    * Custom photo handler for the editor.  The inner listener that this creates also has a
    * reference to the editor and acts as an {@link com.silentcircle.contacts.editor.Editor.EditorListener},
    * and uses that editor to hold state information in several of the listener methods.
    */
    private final class PhotoHandler19 extends PhotoSelectionHandler19 {

        final long mRawContactId;
        private final BaseRawContactEditorView mEditor;
        private final PhotoActionListener mPhotoEditorListener;

        public PhotoHandler19(Context context, BaseRawContactEditorView editor, int photoMode, RawContactDeltaList state,
                            Fragment fragment) {

            super(context, editor.getPhotoEditor(), photoMode, false, state, fragment);
            mEditor = editor;
            mRawContactId = editor.getRawContactId();
            mPhotoEditorListener = new PhotoEditorListener();
        }

        @Override
        public PhotoActionListener getListener() {
            return mPhotoEditorListener;
        }

        @Override
        public void startPhotoActivity(Intent intent, int requestCode, Uri photoUri) {
            mRawContactIdRequestingPhoto = mEditor.getRawContactId();
            mCurrentPhotoHandler19 = this;
            mStatus = Status.SUB_ACTIVITY;
            mCurrentPhotoUri = photoUri;
            ContactEditorFragment.this.startActivityForResult(intent, requestCode);
        }

        private final class PhotoEditorListener extends PhotoSelectionHandler19.PhotoActionListener
                implements Editor.EditorListener {

            @Override
            public void onRequest(int request) {
                if (!hasValidState()) return;

                if (request == Editor.EditorListener.REQUEST_PICK_PHOTO) {
                    onClick(mEditor.getPhotoEditor());
                }
            }

            @Override
            public void onDeleteRequested(Editor removedEditor) {
                // The picture cannot be deleted, it can only be removed, which is handled by
                // onRemovePictureChosen()
            }

            /**
             * User has chosen to set the selected photo as the (super) primary photo
             */
            @Override
            public void onUseAsPrimaryChosen() {
                // Set the IsSuperPrimary for each editor
                int count = mContent.getChildCount();
                for (int i = 0; i < count; i++) {
                    final View childView = mContent.getChildAt(i);
                    if (childView instanceof BaseRawContactEditorView) {
                        final BaseRawContactEditorView editor =
                                (BaseRawContactEditorView) childView;
                        final PhotoEditorView photoEditor = editor.getPhotoEditor();
                        photoEditor.setSuperPrimary(editor == mEditor);
                    }
                }
                bindEditors();
            }

            /**
             * User has chosen to remove a picture
             */
            @Override
            public void onRemovePictureChosen() {
                mEditor.setPhotoBitmap(null);

                // Prevent bitmap from being restored if rotate the device.
                // (only if we first chose a new photo before removing it)
                mUpdatedPhotos.remove(String.valueOf(mRawContactId));
                bindEditors();
            }

            @Override
            public void onPhotoSelected(Uri uri) throws FileNotFoundException {
                final Bitmap bitmap = ContactPhotoUtils19.getBitmapFromUri(mContext, uri);
                setPhoto(mRawContactId, bitmap, null, uri);
                mCurrentPhotoHandler19 = null;
                bindEditors();
            }

            @Override
            public Uri getCurrentPhotoUri() {
                return mCurrentPhotoUri;
            }

            @Override
            public void onPhotoSelectionDismissed() {
                // Nothing to do.
            }
        }
    }
}
