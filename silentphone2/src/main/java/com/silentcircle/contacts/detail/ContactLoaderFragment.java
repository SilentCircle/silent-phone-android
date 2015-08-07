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
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts.detail;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Objects;
import com.silentcircle.silentphone2.R;
import com.silentcircle.contacts.ScContactSaveService;
import com.silentcircle.contacts.activities.ScContactDetailActivity;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.model.ContactLoader;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
//import com.android.contacts.list.ShortcutIntentBuilder;
//import com.android.contacts.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;

// import com.android.contacts.util.PhoneCapabilityTester;

/**
 * This is an invisible worker {link Fragment} that loads the contact details for the contact card.
 * The data is then passed to the listener, who can then pass the data to other {@link android.view.View}s.
 */
public class ContactLoaderFragment extends Fragment implements ScContactDetailActivity.FragmentKeyListener {

    private static final String TAG = ContactLoaderFragment.class.getSimpleName();

    /** The launch code when picking a ringtone */
    private static final int REQUEST_CODE_PICK_RINGTONE = 1;

    /** This is the Intent action to install a shortcut in the launcher. */
    private static final String ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";

    private boolean mOptionsMenuOptions;
    private boolean mOptionsMenuEditable;
    private boolean mOptionsMenuShareable;
    private boolean mOptionsMenuCanCreateShortcut;
    private String mCustomRingtone;

    private ShareActionProvider mShareActionProvider;

    /**
     * This is a listener to the {@link ContactLoaderFragment} and will be notified when the
     * contact details have finished loading or if the user selects any menu options.
     */
    public static interface ContactLoaderFragmentListener {
        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete
         */
        public void onContactNotFound();

        /**
         * Contact details have finished loading.
         */
        public void onDetailsLoaded(Contact result);

        /**
         * User decided to go to Edit-Mode
         */
        public void onEditRequested(Uri lookupUri);

        /**
         * User decided to delete the contact
         */
        public void onDeleteRequested(Uri lookupUri);

    }

    private static final int LOADER_DETAILS = 1;

    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String LOADER_ARG_CONTACT_URI = "contactUri";

    private Context mContext;
    private Uri mLookupUri;
    private ContactLoaderFragmentListener mListener;

    private Contact mContactData;

    public ContactLoaderFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLookupUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CONTACT_URI, mLookupUri);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);
        // This is an invisible view.  This fragment is declared in a layout, so it can't be
        // "viewless".  (i.e. can't return null here.)
        // See also the comment in the layout file.
        return inflater.inflate(R.layout.contact_detail_loader_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mLookupUri != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().initLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    public void loadUri(Uri lookupUri) {
        if (Objects.equal(lookupUri, mLookupUri)) {
            // Same URI, no need to load the data again
            return;
        }

        mLookupUri = lookupUri;
        if (mLookupUri == null) {
            getLoaderManager().destroyLoader(LOADER_DETAILS);
            mContactData = null;
            if (mListener != null) {
                mListener.onDetailsLoaded(mContactData);
            }
        } else if (getActivity() != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().restartLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    public void setListener(ContactLoaderFragmentListener value) {
        mListener = value;
    }

    /**
     * The listener for the detail loader
     */
    private final LoaderManager.LoaderCallbacks<Contact> mDetailLoaderListener =  new LoaderManager.LoaderCallbacks<Contact>() {
        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            Uri lookupUri = args.getParcelable(LOADER_ARG_CONTACT_URI);
            return new ContactLoader(mContext, lookupUri, true /* loadGroupMetaData */,
                    true /* loadStreamItems */, false /* load invitable account types */,
                    true /* postViewNotification */, true /* computeFormattedPhoneNumber */);
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            if (!mLookupUri.equals(data.getRequestedUri())) {
                Log.e(TAG, "Different URI: requested=" + mLookupUri + "  actual=" + data);
                return;
            }

            if (data.isError()) {
                // This shouldn't ever happen, so throw an exception. The {@link ContactLoader}
                // should log the actual exception.
                throw new IllegalStateException("Failed to load contact", data.getException());
            } else if (data.isNotFound()) {
                Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                mContactData = null;
            } else {
                mContactData = data;
            }

            if (mListener != null) {
                if (mContactData == null) {
                    mListener.onContactNotFound();
                } else {
                    mListener.onDetailsLoaded(mContactData);
                }
            }
            // Make sure the options menu is setup correctly with the loaded data.
            if (getActivity() != null) {
                setShareIntent();
                getActivity().invalidateOptionsMenu();
            }
        }

        @Override
        public void onLoaderReset(Loader<Contact> loader) {}
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view_contact, menu);
    }

    public boolean isOptionsMenuChanged() {
        return mOptionsMenuOptions != isContactOptionsChangeEnabled()
                || mOptionsMenuEditable != isContactEditable()
                || mOptionsMenuShareable != isContactShareable()
                || mOptionsMenuCanCreateShortcut != isContactCanCreateShortcut();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuOptions = isContactOptionsChangeEnabled();
        mOptionsMenuEditable = isContactEditable();
        mOptionsMenuShareable = isContactShareable();
        mOptionsMenuCanCreateShortcut = isContactCanCreateShortcut();
        if (mContactData != null) {
            mCustomRingtone = mContactData.getCustomRingtone();
        }

        final MenuItem optionsRingtone = menu.findItem(R.id.menu_set_ringtone);
        if (optionsRingtone != null) {
            optionsRingtone.setVisible(mOptionsMenuOptions);
        }
        
        final MenuItem editMenu = menu.findItem(R.id.menu_edit);
        editMenu.setVisible(mOptionsMenuEditable);

        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete);
        deleteMenu.setVisible(mOptionsMenuEditable);

        final MenuItem shareMenu = menu.findItem(R.id.menu_share);
        shareMenu.setVisible(mOptionsMenuShareable);

        mShareActionProvider = (ShareActionProvider)MenuItemCompat.getActionProvider(shareMenu); // (ShareActionProvider)shareMenu.getActionProvider();

//        final MenuItem createContactShortcutMenu = menu.findItem(R.id.menu_create_contact_shortcut);
//        createContactShortcutMenu.setVisible(mOptionsMenuCanCreateShortcut);
    }

    public boolean isContactOptionsChangeEnabled() {
        return mContactData != null && !mContactData.isDirectoryEntry();
//                 && PhoneCapabilityTester.isPhone(mContext);
    }

    public boolean isContactEditable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    public boolean isContactShareable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    public boolean isContactCanCreateShortcut() {
        return mContactData != null && !mContactData.isUserProfile()
                && !mContactData.isDirectoryEntry();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home: {
                Activity activity = getActivity();
                Intent upIntent = NavUtils.getParentActivityIntent(activity);
                if (NavUtils.shouldUpRecreateTask(activity, upIntent)) {
                    // This activity is NOT part of this app's task, so create a new task
                    // when navigating up, with a synthesized back stack.
                    TaskStackBuilder.create(activity)
                            // Add all of this activity's parents to the back stack
                            .addNextIntentWithParentStack(upIntent)
                                    // Navigate up to the closest parent
                            .startActivities();
                    activity.finish();
                }
                else {
                    // This activity is part of this app's task, so simply
                    // navigate up to the logical parent activity.
                    NavUtils.navigateUpTo(activity, upIntent);
                }
                return true;
            }
            case R.id.menu_edit: {
                if (mListener != null) mListener.onEditRequested(mLookupUri);
                break;
            }
            case R.id.menu_delete: {
                if (mListener != null) 
                    mListener.onDeleteRequested(mLookupUri);
                return true;
            }
            case R.id.menu_set_ringtone: {
                if (mContactData == null) 
                    return false;
                doPickRingtone();
                return true;
            }
/*
 * To have same behaviour as standard Android contact:
 * Enable the code below, remove the embedded shareActionProvider from menu/view_contact.xml modify the
 * handling of mShareActionProvider and calling the function setShareIntent() below.
 */
//            case R.id.menu_share: {
//                if (mContactData == null) 
//                    return false;
//
//                long rawContactId = ContentUris.parseId(mLookupUri);
//                Uri shareUri = ContentUris.withAppendedId(RawContacts.CONTENT_VCARD_URI, rawContactId);
//                if (mContactData.isUserProfile()) {
//                    // User is sharing the profile.  We don't want to force the receiver to have
//                    // the highly-privileged READ_PROFILE permission, so we need to request a
//                    // pre-authorized URI from the provider.
//                    shareUri = getPreAuthorizedUri(shareUri);
//                }
//
//                final Intent intent = new Intent(Intent.ACTION_SEND);
//                intent.setType("text/x-vcard");
//                intent.putExtra(Intent.EXTRA_STREAM, shareUri);
//
//                // Launch chooser to share contact via
//                final CharSequence chooseTitle = mContext.getText(R.string.share_via);
//                final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);
//
//                try {
//                    mContext.startActivity(chooseIntent);
//                } catch (ActivityNotFoundException ex) {
//                    Toast.makeText(mContext, R.string.share_error, Toast.LENGTH_SHORT).show();
//                }
//                return true;
//            }


            /*
             * Not implemented yet. Is it required to have it? Place the contact on the home screen?
             */
//            case R.id.menu_create_contact_shortcut: {
//                // Create a launcher shortcut with this contact
//                createLauncherShortcutWithContact();
//                return true;
//            }
        }
        return false;
    }

    // Update the share intent
    private void setShareIntent() {
        if (mShareActionProvider != null) {
            long rawContactId = ContentUris.parseId(mLookupUri);
            Uri shareUri = ContentUris.withAppendedId(RawContacts.CONTENT_VCARD_URI, rawContactId); // (RawContacts.CONTENT_VCARD_URI, lookupKey);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/x-vcard");
            // RawContacts.CONTENT_VCARD_TYPE is not supported by standard Bluetooth sender app
            // intent.setType(RawContacts.CONTENT_VCARD_TYPE);
            intent.putExtra(Intent.EXTRA_STREAM, shareUri);
            mShareActionProvider.setShareIntent(intent);
        }
    }
    /**
     * Creates a launcher shortcut with the current contact.
     */
//    private void createLauncherShortcutWithContact() {
//        // Hold the parent activity of this fragment in case this fragment is destroyed
//        // before the callback to onShortcutIntentCreated(...)
//        final Activity parentActivity = getActivity();
//
//        ShortcutIntentBuilder builder = new ShortcutIntentBuilder(parentActivity,
//                new OnShortcutIntentCreatedListener() {
//
//            @Override
//            public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
//                // Broadcast the shortcutIntent to the launcher to create a
//                // shortcut to this contact
//                shortcutIntent.setAction(ACTION_INSTALL_SHORTCUT);
//                parentActivity.sendBroadcast(shortcutIntent);
//
//                // Send a toast to give feedback to the user that a shortcut to this
//                // contact was added to the launcher.
//                Toast.makeText(parentActivity,
//                        R.string.createContactShortcutSuccessful,
//                        Toast.LENGTH_SHORT).show();
//            }
//
//        });
//        builder.createContactShortcutIntent(mLookupUri);
//    }

    /**
     * Calls into the contacts provider to get a pre-authorized version of the given URI.
     */
    private Uri getPreAuthorizedUri(Uri uri) {
//        Bundle uriBundle = new Bundle();
//        uriBundle.putParcelable(ScContactsContract.Authorization.KEY_URI_TO_AUTHORIZE, uri);
//        Bundle authResponse = mContext.getContentResolver().call(
//                ScContactsContract.AUTHORITY_URI,
//                ScContactsContract.Authorization.AUTHORIZATION_METHOD,
//                null,
//                uriBundle);
//        if (authResponse != null) {
//            return (Uri) authResponse.getParcelable(
//                    ContactsContract.Authorization.KEY_AUTHORIZED_URI);
//        } else {
            return uri;
//        }
    }

    @Override
    public boolean handleKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL: {
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            }
        }
        return false;
    }

    private void doPickRingtone() {

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        // Allow user to pick 'Default'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        // Show only ringtones
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        // Don't show 'Silent'
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

        Uri ringtoneUri;
        if (mCustomRingtone != null) {
            ringtoneUri = Uri.parse(mCustomRingtone);
        }
        else {
            // Otherwise pick default ringtone Uri so that something is selected.
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        // Put checkmark next to the current ringtone for this contact
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri);

        // Launch!
        startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_PICK_RINGTONE: {
                Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                handleRingtonePicked(pickedUri);
                break;
            }
        }
    }

    private void handleRingtonePicked(Uri pickedUri) {
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
            mCustomRingtone = null;
        } 
        else {
            mCustomRingtone = pickedUri.toString();
        }
        Intent intent = ScContactSaveService.createSetRingtone(mContext, mLookupUri, mCustomRingtone);
        mContext.startService(intent);
    }

    /** Toggles whether to load stream items. Just for debugging */
    public void toggleLoadStreamItems() {
        Loader<Contact> loaderObj = getLoaderManager().getLoader(LOADER_DETAILS);
        ContactLoader loader = (ContactLoader) loaderObj;
        loader.setLoadStreamItems(!loader.getLoadStreamItems());
    }

    /** Returns whether to load stream items. Just for debugging */
    public boolean getLoadStreamItems() {
        Loader<Contact> loaderObj = getLoaderManager().getLoader(LOADER_DETAILS);
        ContactLoader loader = (ContactLoader) loaderObj;
        return loader != null && loader.getLoadStreamItems();
    }
}
