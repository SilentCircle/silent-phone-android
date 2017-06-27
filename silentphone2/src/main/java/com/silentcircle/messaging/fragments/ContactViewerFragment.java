/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.views.ContactView;
import com.silentcircle.silentphone2.R;
import com.silentcircle.vcard.VCardEntry;
import com.silentcircle.vcard.VCardEntryConstructor;
import com.silentcircle.vcard.VCardEntryHandler;
import com.silentcircle.vcard.VCardInterpreter;
import com.silentcircle.vcard.VCardParser;
import com.silentcircle.vcard.VCardParser_V21;
import com.silentcircle.vcard.VCardParser_V30;
import com.silentcircle.vcard.VCardSourceDetector;
import com.silentcircle.vcard.exception.VCardException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ContactViewerFragment extends FileViewerFragment {

    final static int VCARD_VERSION_V21 = 1;
    final static int VCARD_VERSION_V30 = 2;

    public static final String EXTRA_VCARD_POSITION =
            "com.silentcircle.messaging.fragments.ContactViewerFragment.vCardPosition";

    private static final String TAG = ContactViewerFragment.class.getSimpleName();

    private ViewFlipper mViewFlipper;
    private ContactView mCard;
    private Button mButtonImportVcard;
    private Button mButtonImportAllVCards;
    private ListView mVCardListView;

    private boolean mViewFlipped;

    private class VCardHandler implements VCardEntryHandler {

        // see {@code ContactsContract.CommonDataKinds.Phone.TYPE_HOME}
        private int[] phoneTypeLabels = {
                R.string.messaging_vcard_phone_type_home,
                R.string.messaging_vcard_phone_type_mobile,
                R.string.messaging_vcard_phone_type_work,
                R.string.messaging_vcard_phone_type_silent,
                R.string.messaging_vcard_phone_type_other
        };

        private int[] imTypeLabels = {
                R.string.messaging_vcard_im_type_aim,
                R.string.messaging_vcard_im_type_msn,
                R.string.messaging_vcard_im_type_yahoo,
                R.string.messaging_vcard_im_type_skype,
                R.string.messaging_vcard_im_type_qq,
                R.string.messaging_vcard_im_type_hangouts,
                R.string.messaging_vcard_im_type_icq,
                R.string.messaging_vcard_im_type_jabber,
                R.string.messaging_vcard_im_type_other};

        private final ContactView mCard;
        private final int mPosition;
        private final List<VCardEntry> mEntries = new ArrayList<>();

        private int mCurrentPosition;
        private VCardEntry mEntry;

        VCardHandler(ContactView card) {
            mCard = card;
            mPosition = 0;
            mCurrentPosition = -1;
        }

        VCardHandler(ContactView card, int position) {
            mCard = card;
            mPosition = position;
            mCurrentPosition = -1;
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onEntryCreated(VCardEntry entry) {
            mCurrentPosition += 1;
            try {
                addEntry(entry);
                if (mCurrentPosition == mPosition) {
                    mEntry = entry;
                    populateCard(entry);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to populate vCard: " + e.getMessage());
                dispatchError();
            }
        }

        @Override
        public void onEnd() {
        }

        public List<VCardEntry> getEntries() {
            return mEntries;
        }

        public VCardEntry getEntry() {
            return mEntry;
        }

        private void addEntry(VCardEntry entry) {
            mEntries.add(entry);
        }

        private void populateCard(VCardEntry entry) {
            mCard.clear();
            mCard.setDisplayName(entry.getDisplayName());

            List<VCardEntry.PhotoData> photos = entry.getPhotoList();
            if (photos != null && photos.size() > 0) {
                VCardEntry.PhotoData photoEntry = photos.get(0);
                Bitmap photo = AttachmentUtils.getPreviewImage(
                        photoEntry.getBytes(), DisplayMetrics.DENSITY_DEFAULT);
                mCard.setImage(photo);
            }

            VCardEntry.NameData nameData = entry.getNameData();
            if (nameData != null && !nameData.isEmpty()) {
                mCard.setPhoneticName(TextUtils.join(" ",
                        new String[]{nameData.getGiven(), nameData.getMiddle(),
                                nameData.getFamily()}));
            }
            // TODO: entry.getNickNameList();

            List<VCardEntry.PhoneData> phones = entry.getPhoneList();
            if (phones != null) {
                for (VCardEntry.PhoneData phone : phones) {
                    String label = phone.getLabel();
                    if (TextUtils.isEmpty(label)) {
                        label = getString(phoneTypeLabels[
                                (phone.getType() < 0 || phone.getType() >= phoneTypeLabels.length)
                                        ? phoneTypeLabels.length - 1 : phone.getType()]);
                    }
                    mCard.add(getString(R.string.messaging_vcard_section_phone), label,
                            phone.getNumber());
                }
            }

            String birthDay = entry.getBirthday();
            if (!TextUtils.isEmpty(birthDay)) {
                mCard.add(getString(R.string.messaging_vcard_section_birthday), "", birthDay);
            }

            List<VCardEntry.EmailData> emails = entry.getEmailList();
            if (emails != null) {
                for (VCardEntry.EmailData email : emails) {
                    mCard.add(getString(R.string.messaging_vcard_section_email), email.getLabel(),
                            email.getAddress());
                }

            }

            List<VCardEntry.ImData> ims = entry.getImList();
            if (ims != null) {
                for (VCardEntry.ImData im : ims) {
                    String label = im.getCustomProtocol();
                    if (TextUtils.isEmpty(label)) {
                        label = getString(imTypeLabels[
                                (im.getProtocol() < 0 || im.getProtocol() >= imTypeLabels.length)
                                        ? imTypeLabels.length - 1 : im.getProtocol()]);
                    }
                    mCard.add(getString(R.string.messaging_vcard_section_im), label,
                            im.getAddress());
                }
            }

            List<VCardEntry.PostalData> addresses = entry.getPostalList();
            if (addresses != null) {
                for (VCardEntry.PostalData address : addresses) {
                    int addressType = address.getType();
                    String label = address.getLabel();
                    if (TextUtils.isEmpty(label)) {
                        int addressTypeLabel =
                            addressType == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
                                ? R.string.messaging_vcard_address_type_home
                                : (addressType == ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
                                    ? R.string.messaging_vcard_address_type_work
                                    : R.string.messaging_vcard_address_type_other);
                        label = getString(addressTypeLabel);
                    }
                    mCard.add(getString(R.string.messaging_vcard_section_address),
                            label, address.getFormattedAddress(0));
                }
            }

            List<VCardEntry.SipData> sipList = entry.getSipList();
            if (sipList != null) {
                for (VCardEntry.SipData sipEntry : sipList) {
                    mCard.add(getString(R.string.messaging_vcard_section_sip),
                            sipEntry.getLabel(), sipEntry.getAddress());
                }
            }

            List<VCardEntry.WebsiteData> webSites = entry.getWebsiteList();
            if (webSites != null) {
                for (VCardEntry.WebsiteData webSite : webSites) {
                    mCard.add(getString(R.string.messaging_vcard_section_web),
                            null, webSite.getWebsite());
                }
            }

            List<VCardEntry.OrganizationData> organizations = entry.getOrganizationList();
            if (organizations != null) {
                for (VCardEntry.OrganizationData organization : organizations) {
                    mCard.add(getString(R.string.messaging_vcard_section_organization),
                            organization.getTitle(), organization.getFormattedString());
                }
            }

            List<VCardEntry.NoteData> notes = entry.getNotes();
            if (notes != null) {
                for (VCardEntry.NoteData note : notes) {
                    mCard.add(getString(R.string.messaging_vcard_section_notes), "", note.getNote());
                }
            }
        }
    }

    private class VCardAdapter extends BaseAdapter {

        private final Context mContext;
        private final List<VCardEntry> mEntries;

        VCardAdapter(Context context, List<VCardEntry> entries) {
            super();
            mContext = context;
            mEntries = entries;
        }

        @Override
        public int getCount() {
            return mEntries != null ? mEntries.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            if (mEntries == null) {
                return null;
            }
            return (position >= 0 && position < mEntries.size()) ? mEntries.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.messaging_vcard_list_entry, parent, false);
            }
            ImageView photoView = (ImageView) convertView.findViewById(R.id.vcard_photo);
            TextView nameView = (TextView) convertView.findViewById(R.id.vcard_name);
            VCardEntry entry = (VCardEntry) getItem(position);

            nameView.setText(null);
            photoView.setImageResource(R.drawable.ic_profile);
            if (entry != null) {
                nameView.setText(entry.getDisplayName());
                Bitmap avatar = getAvatar(entry);
                if (avatar != null) {
                    photoView.setImageBitmap(avatar);
                }
            }
            return convertView;
        }

        @Nullable
        private Bitmap getAvatar(@NonNull VCardEntry entry) {
            Bitmap result = null;
            try {
                List<VCardEntry.PhotoData> photos = entry.getPhotoList();
                if (photos != null && photos.size() > 0) {
                    VCardEntry.PhotoData photoEntry = photos.get(0);
                    Bitmap photo = AttachmentUtils.getPreviewImage(
                            photoEntry.getBytes(), DisplayMetrics.DENSITY_DEFAULT);
                    result = ViewUtil.getCircularBitmap(photo);
                }
            }
            catch (Throwable t) {
                // Default avatar will be shown
            }
            return result;
        }
    }

    private View.OnClickListener mButtonImportVCardClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            Callback callback = getCallback();
            if (callback == null) {
                return;
            }

            try {
                callback.viewWithExternalApplication();
            }
            catch (Throwable t) {
                File file = getFile();
                Toast.makeText(activity,
                        getString(R.string.toast_failed_to_export_file, file == null ? "" : file.getName()),
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    private class ImportVCardOnClick implements View.OnClickListener {

        private final VCardEntry mEntry;

        ImportVCardOnClick(VCardEntry entry) {
            mEntry = entry;
        }

        @Override
        public void onClick(View v) {
            if (mEntry != null) {
                // import entry ...
                v.setEnabled(false);
                Toast.makeText(v.getContext(), getString(R.string.messaging_vcard_importing_contact, mEntry.getDisplayName()),
                        Toast.LENGTH_SHORT).show();
                ContentResolver resolver = v.getContext().getContentResolver();
                ArrayList<ContentProviderOperation> operationList =
                        mEntry.constructInsertOperations(resolver, null);
                pushIntoContentResolver(resolver, operationList);
                v.setEnabled(true);
            }
        }
    }

    public static ContactViewerFragment create(Uri uri, String mimeType) {
        return create(uri, mimeType, -1);
    }

    public static ContactViewerFragment create(Uri uri, String mimeType, int position) {
        ContactViewerFragment fragment = instantiate(new ContactViewerFragment(), uri, mimeType);
        Bundle arguments = fragment.getArguments();
        arguments.putInt(EXTRA_VCARD_POSITION, position);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.messaging_vcard_viewer_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        mViewFlipper = (ViewFlipper) view.findViewById(R.id.vcard_view_flipper);

        mCard = (ContactView) view.findViewById(R.id.vcard_details);

        mButtonImportVcard = (Button) view.findViewById(R.id.button_import_vcard);
        mButtonImportVcard.setOnClickListener(mButtonImportVCardClickListener);

        mButtonImportAllVCards = (Button) view.findViewById(R.id.button_import_all_vcards);
        mButtonImportAllVCards.setOnClickListener(mButtonImportVCardClickListener);

        mVCardListView = (ListView) view.findViewById(R.id.vcard_list);
        mVCardListView.setVisibility(View.GONE);
        mVCardListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mViewFlipped = true;
                prepareForShow(position);
                mViewFlipper.showNext();
            }
        });

        TextView raw = (TextView) view.findViewById(R.id.raw_vcard);
        String text = IOUtils.readAsString(getFile());
        raw.setText(text);

        prepareForShow();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mCard = null;
        mButtonImportVcard = null;
        mButtonImportAllVCards = null;
        mVCardListView = null;
        mViewFlipper = null;
    }

    public boolean onExitView() {
        boolean result = false;
        if (mViewFlipped) {
            result = true;
            if (mViewFlipper != null) {
                mViewFlipper.setInAnimation(getActivity(), R.anim.dialpad_slide_in_left);
                mViewFlipper.setOutAnimation(getActivity(), R.anim.dialpad_slide_out_right);
                mViewFlipper.showPrevious();
                mViewFlipper.setInAnimation(getActivity(), R.anim.dialpad_slide_in_right);
                mViewFlipper.setOutAnimation(getActivity(), R.anim.dialpad_slide_out_left);

            }
            mViewFlipped = false;
        }
        return result;
    }

    private void prepareForShow() {
        Context context = getActivity();
        if (context == null) {
            return;
        }

        final int[] possibleVCardVersions = new int[] { VCARD_VERSION_V30, VCARD_VERSION_V21 };
        final VCardEntryConstructor constructor =
                new VCardEntryConstructor(VCardSourceDetector.PARSE_TYPE_UNKNOWN);

        VCardHandler vCardHandler = new VCardHandler(mCard);
        constructor.addEntryHandler(vCardHandler);

        boolean successful = readVCard(VCardSourceDetector.PARSE_TYPE_UNKNOWN, constructor,
                possibleVCardVersions);

        if (!successful) {
            dispatchError();
        }
        else {
            List<VCardEntry> entries = vCardHandler.getEntries();
            if (entries != null && entries.size() > 1) {
                mButtonImportAllVCards.setText(getString(R.string.import_contacts, entries.size()));
                mButtonImportAllVCards.setVisibility(View.VISIBLE);
                mVCardListView.setAdapter(new VCardAdapter(context, entries));
                mVCardListView.setVisibility(View.VISIBLE);
                mViewFlipper.setInAnimation(context, R.anim.dialpad_slide_in_right);
                mViewFlipper.setOutAnimation(context, R.anim.dialpad_slide_out_left);
            }
            else {
                // Viewing single contact, hide back button
                mViewFlipper.showNext();
            }
        }
    }

    private void prepareForShow(int position) {
        final int[] possibleVCardVersions = new int[] { VCARD_VERSION_V30, VCARD_VERSION_V21 };
        final VCardEntryConstructor constructor =
                new VCardEntryConstructor(VCardSourceDetector.PARSE_TYPE_UNKNOWN);

        VCardHandler vCardHandler = new VCardHandler(mCard, position);
        constructor.addEntryHandler(vCardHandler);

        boolean successful = readVCard(VCardSourceDetector.PARSE_TYPE_UNKNOWN, constructor,
                possibleVCardVersions);

        if (!successful) {
            Toast.makeText(getActivity(), R.string.messaging_vcard_cannot_show_selected_contact,
                    Toast.LENGTH_SHORT).show();
        }
        else {
            // allow to import just one vcard
            mButtonImportVcard.setOnClickListener(new ImportVCardOnClick(vCardHandler.getEntry()));
        }
    }

    private boolean readVCard(int vcardType, final VCardInterpreter interpreter,
            final int[] possibleVCardVersions) {

        boolean successful = false;
        final int length = possibleVCardVersions.length;

        for (int i = 0; i < length; i++) {
            final int vCardVersion = possibleVCardVersions[i];
            InputStream is = null;
            try {
                is = getStream(getActivity());
                if (i > 0 && (interpreter instanceof VCardEntryConstructor)) {
                    // Let the object clean up internal temporary objects,
                    ((VCardEntryConstructor) interpreter).clear();
                }
                VCardParser parser;
                parser = (vCardVersion == VCARD_VERSION_V21
                        ? new VCardParser_V30(vcardType) : new VCardParser_V21(vcardType));
                parser.addInterpreter(interpreter);
                parser.parse(is);
                successful = true;
                break;
            }
            catch (IOException | VCardException e) {
                Log.e(TAG, e.getMessage());
            }
            finally {
                IOUtils.close(is);
            }
        }

        return successful;
    }

    private Uri pushIntoContentResolver(ContentResolver contentResolver,
            ArrayList<ContentProviderOperation> operationList) {
        try {
            final ContentProviderResult[] results =
                    contentResolver.applyBatch(ContactsContract.AUTHORITY, operationList);

            // the first result is always the raw_contact. return it's uri so
            // that it can be found later. do null checking for badly behaving
            // ContentResolvers
            return ((results == null || results.length == 0 || results[0] == null) ? null : results[0].uri);
        }
        catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return null;
        }
        catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return null;
        }
    }

}
