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
 * This class uses many (a lot) ideas from the standard Android contacts provider, in some parts
 * it's even copied verbatim to reduce the learning curve for Android aficionados.
 */

package com.silentcircle.silentcontacts2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.View;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ScContactsContract {

    public static final String TAG = "ScContactsProvider";
    /** 
     * The authority for the contacts provider
     */
    public static final String AUTHORITY = "com.silentcircle.contacts2";
    
    /** 
     * A content:// style uri to the authority for the contacts provider
     */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * Query parameter that should be used by the client to access a specific
     * {@link Directory}. The parameter value should be the _ID of the corresponding
     * directory, e.g.
     * {@code content://com.android.contacts/data/emails/filter/acme?directory=3}
     */
    public static final String DIRECTORY_PARAM_KEY = "directory";

    /**
     * Query parameter used to limit the number of call logs returned.
     * <p>
     * TYPE: integer
     */
    public static final String LIMIT_PARAM_KEY = "limit";

    /**
     * An optional URI parameter for insert, update, or delete queries
     * that allows the caller
     * to specify that it is a sync adapter. The default value is false. If true
     * {@link RawContacts#DIRTY} is not automatically set and the
     * "syncToNetwork" parameter is set to false when calling
     * {@link
     * ContentResolver#notifyChange(android.net.Uri, android.database.ContentObserver, boolean)}.
     * This prevents an unnecessary extra synchronization, see the discussion of
     * the delete operation in {@link RawContacts}.
     */
    public static final String CALLER_IS_SYNCADAPTER = "caller_is_syncadapter";

    /**
     * A key to a boolean in the "extras" bundle of the cursor.
     * The boolean indicates that the provider did not create a snippet and that the client asking
     * for the snippet should do it (true means the snippeting was deferred to the client).
     *
     * @hide
     */
    public static final String DEFERRED_SNIPPETING = "deferred_snippeting";

    /**
     * Key to retrieve the original query on the client side.
     *
     * @hide
     */
    public static final String DEFERRED_SNIPPETING_QUERY = "deferred_snippeting_query";

    /**
     * A boolean parameter for {@link CommonDataKinds.Phone#CONTENT_URI},
     * {@link CommonDataKinds.Email#CONTENT_URI}, and
     * {@link CommonDataKinds.StructuredPostal#CONTENT_URI}.
     * This enables a content provider to remove duplicate entries in results.
     *
     * @hide
     */
    public static final String REMOVE_DUPLICATE_ENTRIES = "remove_duplicate_entries";

    /**
     * A boolean parameter for every activity with the ScContacts provider.
     *
     * If set to true the provider returns immediately if the database is not open, for
     * example the user did not enter a password or locked it. The provider return a
     * "no action done" value in this case, for example {@code null} for a query operation.
     */
    public static final String NON_BLOCKING = "non_blocking";

    /**
     * A boolean parameter for {@link Contacts#CONTENT_STREQUENT_URI} and
     * {@link Contacts#CONTENT_STREQUENT_FILTER_URI}, which requires the ContactsProvider to
     * return only phone-related results. For example, frequently contacted person list should
     * include persons contacted via phone (not email, sms, etc.)
     *
     * @hide
     */
    public static final String STREQUENT_PHONE_ONLY = "strequent_phone_only";

    /**
     * @hide
     */
    public static final class Preferences {

        /**
         * A key in the {@link android.provider.Settings android.provider.Settings} provider
         * that stores the preferred sorting order for contacts (by given name vs. by family name).
         *
         * @hide
         */
        public static final String SORT_ORDER = "android.contacts.SORT_ORDER";

        /**
         * The value for the SORT_ORDER key corresponding to sorting by given name first.
         *
         * @hide
         */
        public static final int SORT_ORDER_PRIMARY = 1;

        /**
         * The value for the SORT_ORDER key corresponding to sorting by family name first.
         *
         * @hide
         */
        public static final int SORT_ORDER_ALTERNATIVE = 2;

        /**
         * A key in the {@link android.provider.Settings android.provider.Settings} provider
         * that stores the preferred display order for contacts (given name first vs. family
         * name first).
         *
         * @hide
         */
        public static final String DISPLAY_ORDER = "android.contacts.DISPLAY_ORDER";

        /**
         * The value for the DISPLAY_ORDER key corresponding to showing the given name first.
         *
         * @hide
         */
        public static final int DISPLAY_ORDER_PRIMARY = 1;

        /**
         * The value for the DISPLAY_ORDER key corresponding to showing the family name first.
         *
         * @hide
         */
        public static final int DISPLAY_ORDER_ALTERNATIVE = 2;
    }

    /**
     * Generic columns for use by sync adapters. The specific functions of
     * these columns are private to the sync adapter. Other clients of the API
     * should not attempt to either read or write this column.
     *
     * @see RawContacts
     * @see Groups
     */
    protected interface BaseSyncColumns {

        /** Generic column for use by sync adapters. */
        public static final String SYNC1 = "sync1";
        /** Generic column for use by sync adapters. */
        public static final String SYNC2 = "sync2";
        /** Generic column for use by sync adapters. */
        public static final String SYNC3 = "sync3";
        /** Generic column for use by sync adapters. */
        public static final String SYNC4 = "sync4";
    }

    public static final class Directory implements ScBaseColumns {

        /**
         * Not instantiable.
         */
        private Directory() {
        }

        /**
         * The content:// style URI for this table.  Requests to this URI can be
         * performed on the UI thread because they are always unblocking.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "directories");

        /**
         * The MIME-type of {@link #CONTENT_URI} providing a directory of
         * contact directories.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.contact_directories";

        /**
         * The MIME type of a {@link #CONTENT_URI} item.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.contact_directory";

        /**
         * _ID of the default directory, which represents locally stored contacts.
         */
        public static final long DEFAULT = 0;

        /**
         * _ID of the directory that represents locally stored invisible contacts.
         */
        public static final long LOCAL_INVISIBLE = 1;

        /**
         * The name of the package that owns this directory. Contacts Provider
         * fill it in with the name of the package containing the directory provider.
         * If the package is later uninstalled, the directories it owns are
         * automatically removed from this table.
         *
         * <p>TYPE: TEXT</p>
         */
        public static final String PACKAGE_NAME = "packageName";

        /**
         * The type of directory captured as a resource ID in the context of the
         * package {@link #PACKAGE_NAME}, e.g. "Corporate Directory"
         *
         * <p>TYPE: INTEGER</p>
         */
        public static final String TYPE_RESOURCE_ID = "typeResourceId";

        /**
         * An optional name that can be used in the UI to represent this directory,
         * e.g. "Acme Corp"
         * <p>TYPE: text</p>
         */
        public static final String DISPLAY_NAME = "displayName";

        /**
         * <p>
         * The authority of the Directory Provider. Contacts Provider will
         * use this authority to forward requests to the directory provider.
         * A directory provider can leave this column empty - Contacts Provider will fill it in.
         * </p>
         * <p>
         * Clients of this API should not send requests directly to this authority.
         * All directory requests must be routed through Contacts Provider.
         * </p>
         *
         * <p>TYPE: text</p>
         */
        public static final String DIRECTORY_AUTHORITY = "authority";

//        /**
//         * The account type which this directory is associated.
//         *
//         * <p>TYPE: text</p>
//         */
//        public static final String ACCOUNT_TYPE = "accountType";
//
//        /**
//         * The account with which this directory is associated. If the account is later
//         * removed, the directories it owns are automatically removed from this table.
//         *
//         * <p>TYPE: text</p>
//         */
//        public static final String ACCOUNT_NAME = "accountName";
//
        /**
         * One of {@link #EXPORT_SUPPORT_NONE}, {@link #EXPORT_SUPPORT_ANY_ACCOUNT},
         * {@link #EXPORT_SUPPORT_SAME_ACCOUNT_ONLY}. This is the expectation the
         * directory has for data exported from it.  Clients must obey this setting.
         */
        public static final String EXPORT_SUPPORT = "exportSupport";

        /**
         * An {@link #EXPORT_SUPPORT} setting that indicates that the directory
         * does not allow any data to be copied out of it.
         */
        public static final int EXPORT_SUPPORT_NONE = 0;

        /**
         * An {@link #EXPORT_SUPPORT} setting that indicates that the directory
         * allow its data copied only to the account specified by
         * {@link #ACCOUNT_TYPE}/{@link #ACCOUNT_NAME}.
         */
        public static final int EXPORT_SUPPORT_SAME_ACCOUNT_ONLY = 1;

        /**
         * An {@link #EXPORT_SUPPORT} setting that indicates that the directory
         * allow its data copied to any contacts account.
         */
        public static final int EXPORT_SUPPORT_ANY_ACCOUNT = 2;

        /**
         * One of {@link #SHORTCUT_SUPPORT_NONE}, {@link #SHORTCUT_SUPPORT_DATA_ITEMS_ONLY},
         * {@link #SHORTCUT_SUPPORT_FULL}. This is the expectation the directory
         * has for shortcuts created for its elements. Clients must obey this setting.
         */
        public static final String SHORTCUT_SUPPORT = "shortcutSupport";

        /**
         * An {@link #SHORTCUT_SUPPORT} setting that indicates that the directory
         * does not allow any shortcuts created for its contacts.
         */
        public static final int SHORTCUT_SUPPORT_NONE = 0;

        /**
         * An {@link #SHORTCUT_SUPPORT} setting that indicates that the directory
         * allow creation of shortcuts for data items like email, phone or postal address,
         * but not the entire contact.
         */
        public static final int SHORTCUT_SUPPORT_DATA_ITEMS_ONLY = 1;

        /**
         * An {@link #SHORTCUT_SUPPORT} setting that indicates that the directory
         * allow creation of shortcuts for contact as well as their constituent elements.
         */
        public static final int SHORTCUT_SUPPORT_FULL = 2;

        /**
         * One of {@link #PHOTO_SUPPORT_NONE}, {@link #PHOTO_SUPPORT_THUMBNAIL_ONLY},
         * {@link #PHOTO_SUPPORT_FULL}. This is a feature flag indicating the extent
         * to which the directory supports contact photos.
         */
        public static final String PHOTO_SUPPORT = "photoSupport";

        /**
         * An {@link #PHOTO_SUPPORT} setting that indicates that the directory
         * does not provide any photos.
         */
        public static final int PHOTO_SUPPORT_NONE = 0;

        /**
         * An {@link #PHOTO_SUPPORT} setting that indicates that the directory
         * can only produce small size thumbnails of contact photos.
         */
        public static final int PHOTO_SUPPORT_THUMBNAIL_ONLY = 1;

        /**
         * An {@link #PHOTO_SUPPORT} setting that indicates that the directory
         * has full-size contact photos, but cannot provide scaled thumbnails.
         */
        public static final int PHOTO_SUPPORT_FULL_SIZE_ONLY = 2;

        /**
         * An {@link #PHOTO_SUPPORT} setting that indicates that the directory
         * can produce thumbnails as well as full-size contact photos.
         */
        public static final int PHOTO_SUPPORT_FULL = 3;

        /**
         * Notifies the system of a change in the list of directories handled by
         * a particular directory provider. The Contacts provider will turn around
         * and send a query to the directory provider for the full list of directories,
         * which will replace the previous list.
         */
        public static void notifyDirectoryChange(ContentResolver resolver) {
            // This is done to trigger a query by Contacts Provider back to the directory provider.
            // No data needs to be sent back, because the provider can infer the calling
            // package from binder.
            ContentValues contentValues = new ContentValues();
            resolver.update(Directory.CONTENT_URI, contentValues, null, null);
        }
    }

    /**
     * Columns that appear when each row of a table belongs to a specific
     * account, including sync information that an account may need.
     *
     * @see RawContacts
     * @see Groups
     */
    protected interface SyncColumns extends BaseSyncColumns {
//        /**
//         * The name of the account instance to which this row belongs, which when paired with
//         * {@link #ACCOUNT_TYPE} identifies a specific account.
//         * <P>Type: TEXT</P>
//         */
//        public static final String ACCOUNT_NAME = "account_name";
//
//        /**
//         * The type of account to which this row belongs, which when paired with
//         * {@link #ACCOUNT_NAME} identifies a specific account.
//         * <P>Type: TEXT</P>
//         */
//        public static final String ACCOUNT_TYPE = "account_type";

        /**
         * String that uniquely identifies this row to its source account.
         * <P>Type: TEXT</P>
         */
        public static final String SOURCE_ID = "sourceid";

        /**
         * Version number that is updated whenever this row or its related data
         * changes.
         * <P>Type: INTEGER</P>
         */
        public static final String VERSION = "version";

        /**
         * Flag indicating that {@link #VERSION} has changed, and this row needs
         * to be synchronized by its owning account.
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String DIRTY = "dirty";
    }

    /**
     * Columns of {@link ContactsContract.Contacts} that track the user's
     * preferences for, or interactions with, the contact.
     *
     * @see Contacts
     * @see RawContacts
     * @see ContactsContract.Data
     * @see PhoneLookup
     * @see ContactsContract.Contacts.AggregationSuggestions
     */
    protected interface ContactOptionsColumns {
        /**
         * The number of times a contact has been contacted
         * <P>Type: INTEGER</P>
         */
        public static final String TIMES_CONTACTED = "times_contacted";

        /**
         * The last time a contact was contacted.
         * <P>Type: INTEGER</P>
         */
        public static final String LAST_TIME_CONTACTED = "last_time_contacted";

        /**
         * Is the contact starred?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String STARRED = "starred";

        /**
         * The position at which the contact is pinned. If {@link PinnedPositions.UNPINNED},
         * the contact is not pinned. Also see {@link PinnedPositions}.
         * <P>Type: INTEGER </P>
         * @hide
         */
        public static final String PINNED = "pinned";

        /**
         * URI for a custom ringtone associated with the contact. If null or missing,
         * the default ringtone is used.
         * <P>Type: TEXT (URI to the ringtone)</P>
         */
        public static final String CUSTOM_RINGTONE = "custom_ringtone";

//        /**
//         * Whether the contact should always be sent to voicemail. If missing,
//         * defaults to false.
//         * <P>Type: INTEGER (0 for false, 1 for true)</P>
//         */
//        public static final String SEND_TO_VOICEMAIL = "send_to_voicemail";
    }

    /**
     * Constants for various styles of combining given name, family name etc into
     * a full name.  For example, the western tradition follows the pattern
     * 'given name' 'middle name' 'family name' with the alternative pattern being
     * 'family name', 'given name' 'middle name'.  The CJK tradition is
     * 'family name' 'middle name' 'given name', with Japanese favoring a space between
     * the names and Chinese omitting the space.
     */
    public interface FullNameStyle {
        public static final int UNDEFINED = 0;
        public static final int WESTERN = 1;

        /**
         * Used if the name is written in Hanzi/Kanji/Hanja and we could not determine
         * which specific language it belongs to: Chinese, Japanese or Korean.
         */
        public static final int CJK = 2;

        public static final int CHINESE = 3;
        public static final int JAPANESE = 4;
        public static final int KOREAN = 5;
    }

    /**
     * Constants for various styles of capturing the pronunciation of a person's name.
     */
    public interface PhoneticNameStyle {
        public static final int UNDEFINED = 0;

        /**
         * Pinyin is a phonetic method of entering Chinese characters. Typically not explicitly
         * shown in UIs, but used for searches and sorting.
         */
        public static final int PINYIN = 3;

        /**
         * Hiragana and Katakana are two common styles of writing out the pronunciation
         * of a Japanese names.
         */
        public static final int JAPANESE = 4;

        /**
         * Hangul is the Korean phonetic alphabet.
         */
        public static final int KOREAN = 5;
    }

    /**
     * Types of data used to produce the display name for a contact. In the order
     * of increasing priority: {@link #EMAIL}, {@link #PHONE},
     * {@link #ORGANIZATION}, {@link #NICKNAME}, {@link #STRUCTURED_NAME}.
     */
    public interface DisplayNameSources {
        public static final int UNDEFINED = 0;
        public static final int EMAIL = 10;
        public static final int PHONE = 20;
        public static final int ORGANIZATION = 30;
        public static final int NICKNAME = 35;
        public static final int STRUCTURED_NAME = 40;
    }

    /**
     * Columns of {@link ContactsContract.Contacts} that refer to intrinsic
     * properties of the contact, as opposed to the user-specified options
     * found in {@link ContactOptionsColumns}.
     *
     * @see Contacts
     * @see ContactsContract.Data
     * @see PhoneLookup
     * @see ContactsContract.Contacts.AggregationSuggestions
     */
    protected interface RawContactsColumnsAdd {
        /**
         * The display name for the contact.
         * <P>Type: TEXT</P>
         */
        public static final String DISPLAY_NAME = ContactNameColumns.DISPLAY_NAME_PRIMARY;

        /**
         * Reference to the row in the RawContacts table holding the contact name.
         * <P>Type: INTEGER REFERENCES raw_contacts(_id)</P>
         * @hide
         */
//        public static final String NAME_RAW_CONTACT_ID = "name_raw_contact_id";
//
        /**
         * Reference to the row in the data table holding the photo.  A photo can
         * be referred to either by ID (this field) or by URI (see {@link #PHOTO_THUMBNAIL_URI}
         * and {@link #PHOTO_URI}).
         * If PHOTO_ID is null, consult {@link #PHOTO_URI} or {@link #PHOTO_THUMBNAIL_URI},
         * which is a more generic mechanism for referencing the contact photo, especially for
         * contacts returned by non-local directories (see {@link Directory}).
         *
         * <P>Type: INTEGER REFERENCES data(_id)</P>
         */
        public static final String PHOTO_ID = "photo_id";

        /**
         * Photo file ID of the full-size photo.  If present, this will be used to populate
         * {@link #PHOTO_URI}.  The ID can also be used with
         * {@link ContactsContract.DisplayPhoto#CONTENT_URI} to create a URI to the photo.
         * If this is present, {@link #PHOTO_ID} is also guaranteed to be populated.
         *
         * <P>Type: INTEGER</P>
         */
        public static final String PHOTO_FILE_ID = "photo_file_id";

        /**
         * A URI that can be used to retrieve the contact's full-size photo.
         * If PHOTO_FILE_ID is not null, this will be populated with a URI based off
         * {@link ContactsContract.DisplayPhoto#CONTENT_URI}.  Otherwise, this will
         * be populated with the same value as {@link #PHOTO_THUMBNAIL_URI}.
         * A photo can be referred to either by a URI (this field) or by ID
         * (see {@link #PHOTO_ID}). If either PHOTO_FILE_ID or PHOTO_ID is not null,
         * PHOTO_URI and PHOTO_THUMBNAIL_URI shall not be null (but not necessarily
         * vice versa).  Thus using PHOTO_URI is a more robust method of retrieving
         * contact photos.
         *
         * <P>Type: TEXT</P>
         */
        public static final String PHOTO_URI = "photo_uri";

        /**
         * A URI that can be used to retrieve a thumbnail of the contact's photo.
         * A photo can be referred to either by a URI (this field or {@link #PHOTO_URI})
         * or by ID (see {@link #PHOTO_ID}). If PHOTO_ID is not null, PHOTO_URI and
         * PHOTO_THUMBNAIL_URI shall not be null (but not necessarily vice versa).
         * If the content provider does not differentiate between full-size photos
         * and thumbnail photos, PHOTO_THUMBNAIL_URI and {@link #PHOTO_URI} can contain
         * the same value, but either both shall be null or both not null.
         *
         * <P>Type: TEXT</P>
         */
        public static final String PHOTO_THUMBNAIL_URI = "photo_thumb_uri";
//
//        /**
//         * Flag that reflects the {@link Groups#GROUP_VISIBLE} state of any
//         * {@link CommonDataKinds.GroupMembership} for this contact.
//         */
//        public static final String IN_VISIBLE_GROUP = "in_visible_group";
//
//        /**
//         * Flag that reflects whether this contact represents the user's
//         * personal profile entry.
//         */
//        public static final String IS_USER_PROFILE = "is_user_profile";
//
        /**
         * An indicator of whether this contact has at least one phone number. "1" if there is
         * at least one phone number, "0" otherwise.
         * <P>Type: INTEGER</P>
         */
        public static final String HAS_PHONE_NUMBER = "has_phone_number";

        /**
         * An opaque value that contains hints on how to find the contact if
         * its row id changed as a result of a sync or aggregation.
         */
        public static final String LOOKUP_KEY = "lookup";

        /**
         * Timestamp (milliseconds since epoch) of when this contact was last updated.  This
         * includes updates to all data associated with this contact including raw contacts.  Any
         * modification (including deletes and inserts) of underlying contact data are also
         * reflected in this timestamp.
         */
        public static final String CONTACT_LAST_UPDATED_TIMESTAMP = "contact_last_updated_timestamp";

    }

    /**
     * Contact name and contact name metadata columns in the RawContacts table.
     *
     * @see Contacts
     * @see RawContacts
     */
    protected interface ContactNameColumns {

        /**
         * The kind of data that is used as the display name for the contact, such as
         * structured name or email address.  See {@link DisplayNameSources}.
         */
        public static final String DISPLAY_NAME_SOURCE = "display_name_source";

        /**
         * <p>
         * The standard text shown as the contact's display name, based on the best
         * available information for the contact (for example, it might be the email address
         * if the name is not available).
         * The information actually used to compute the name is stored in
         * {@link #DISPLAY_NAME_SOURCE}.
         * </p>
         * <p>
         * A contacts provider is free to choose whatever representation makes most
         * sense for its target market.
         * For example in the default Android Open Source Project implementation,
         * if the display name is
         * based on the structured name and the structured name follows
         * the Western full-name style, then this field contains the "given name first"
         * version of the full name.
         * <p>
         *
         * @see ContactsContract.ContactNameColumns#DISPLAY_NAME_ALTERNATIVE
         */
        public static final String DISPLAY_NAME_PRIMARY = "display_name";

        /**
         * <p>
         * An alternative representation of the display name, such as "family name first"
         * instead of "given name first" for Western names.  If an alternative is not
         * available, the values should be the same as {@link #DISPLAY_NAME_PRIMARY}.
         * </p>
         * <p>
         * A contacts provider is free to provide alternatives as necessary for
         * its target market.
         * For example the default Android Open Source Project contacts provider
         * currently provides an
         * alternative in a single case:  if the display name is
         * based on the structured name and the structured name follows
         * the Western full name style, then the field contains the "family name first"
         * version of the full name.
         * Other cases may be added later.
         * </p>
         */
        public static final String DISPLAY_NAME_ALTERNATIVE = "display_name_alt";

        /**
         * The phonetic alphabet used to represent the {@link #PHONETIC_NAME}.  See
         * {@link PhoneticNameStyle}.
         */
        public static final String PHONETIC_NAME_STYLE = "phonetic_name_style";

        /**
         * <p>
         * Pronunciation of the full name in the phonetic alphabet specified by
         * {@link #PHONETIC_NAME_STYLE}.
         * </p>
         * <p>
         * The value may be set manually by the user. This capability is of
         * interest only in countries with commonly used phonetic alphabets,
         * such as Japan and Korea. See {@link PhoneticNameStyle}.
         * </p>
         */
        public static final String PHONETIC_NAME = "phonetic_name";

        /**
         * Sort key that takes into account locale-based traditions for sorting
         * names in address books.  The default
         * sort key is {@link #DISPLAY_NAME_PRIMARY}.  For Chinese names
         * the sort key is the name's Pinyin spelling, and for Japanese names
         * it is the Hiragana version of the phonetic name.
         */
        public static final String SORT_KEY_PRIMARY = "sort_key";

        /**
         * Sort key based on the alternative representation of the full name,
         * {@link #DISPLAY_NAME_ALTERNATIVE}.  Thus for Western names,
         * it is the one using the "family name first" format.
         */
        public static final String SORT_KEY_ALTERNATIVE = "sort_key_alt";
    }

    /**
     * URI parameter and cursor extras that return counts of rows grouped by the
     * address book index, which is usually the first letter of the sort key.
     * When this parameter is supplied, the row counts are returned in the
     * cursor extras bundle.
     *
     * @hide
     */
    public final static class ContactCounts {

        /**
         * Add this query parameter to a URI to get back row counts grouped by
         * the address book index as cursor extras. For most languages it is the
         * first letter of the sort key. This parameter does not affect the main
         * content of the cursor.
         *
         * @hide
         */
        public static final String ADDRESS_BOOK_INDEX_EXTRAS = "address_book_index_extras";

        /**
         * The array of address book index titles, which are returned in the
         * same order as the data in the cursor.
         * <p>TYPE: String[]</p>
         *
         * @hide
         */
        public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = "address_book_index_titles";

        /**
         * The array of group counts for the corresponding group.  Contains the same number
         * of elements as the EXTRA_ADDRESS_BOOK_INDEX_TITLES array.
         * <p>TYPE: int[]</p>
         *
         * @hide
         */
        public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = "address_book_index_counts";
    }

    protected interface DeletedContactsColumns {

        /**
         * A reference to the {@link ContactsContract.Contacts#_ID} that was deleted.
         * <P>Type: INTEGER</P>
         */
        public static final String CONTACT_ID = "contact_id";

        /**
         * Time (milliseconds since epoch) that the contact was deleted.
         */
        public static final String CONTACT_DELETED_TIMESTAMP = "contact_deleted_timestamp";
    }

    /**
     * Constants for the deleted contact table.  This table holds a log of deleted contacts.
     * <p>
     * Log older than {@link #DAYS_KEPT_MILLISECONDS} may be deleted.
     */
    public static final class DeletedContacts implements DeletedContactsColumns {

        /**
         * This utility class cannot be instantiated
         */
        private DeletedContacts() {
        }

        /**
         * The content:// style URI for this table, which requests a directory of raw contact rows
         * matching the selection criteria.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "deleted_contacts");

        /**
         * Number of days that the delete log will be kept.  After this time, delete records may be
         * deleted.
         *
         * @hide
         */
        private static final int DAYS_KEPT = 30;

        /**
         * Milliseconds that the delete log will be kept.  After this time, delete records may be
         * deleted.
         */
        public static final long DAYS_KEPT_MILLISECONDS = 1000L * 60L * 60L * 24L * (long)DAYS_KEPT;
    }

    protected interface RawContactsColumns {

        /**
         * The type for this contact.
         * <P>Type: INTEGER</P>
         */
        public static final String CONTACT_TYPE = "contact_type";

        /**
         * The "deleted" flag: "0" by default, "1" if the row has been marked
         * for deletion. When {@link android.content.ContentResolver#delete} is
         * called on a raw contact, it is marked for deletion and removed from its
         * aggregate contact. The sync adaptor deletes the raw contact on the server and
         * then calls ContactResolver.delete once more, this time passing the
         * {@link ContactsContract#CALLER_IS_SYNCADAPTER} query parameter to finalize
         * the data removal.
         * <P>Type: INTEGER</P>
         */
        public static final String DELETED = "deleted";

        /**
         * The "name_verified" flag: "1" means that the name fields on this raw
         * contact can be trusted and therefore should be used for the entire
         * aggregated contact.
         * <p>
         * If an aggregated contact contains more than one raw contact with a
         * verified name, one of those verified names is chosen at random.
         * If an aggregated contact contains no verified names, the
         * name is chosen randomly from the constituent raw contacts.
         * </p>
         * <p>
         * Updating this flag from "0" to "1" automatically resets it to "0" on
         * all other raw contacts in the same aggregated contact.
         * </p>
         * <p>
         * Sync adapters should only specify a value for this column when
         * inserting a raw contact and leave it out when doing an update.
         * </p>
         * <p>
         * The default value is "0"
         * </p>
         * <p>Type: INTEGER</p>
         *
         * @hide
         */
        public static final String NAME_VERIFIED = "name_verified";

        /**
         * The "read-only" flag: "0" by default, "1" if the row cannot be modified or
         * deleted except by a sync adapter.  See {@link ContactsContract#CALLER_IS_SYNCADAPTER}.
         * <P>Type: INTEGER</P>
         */
        public static final String RAW_CONTACT_IS_READ_ONLY = "raw_contact_is_read_only";

        /**
         * Flag that reflects whether this raw contact belongs to the user's
         * personal profile entry.
         */
        public static final String RAW_CONTACT_IS_USER_PROFILE = "raw_contact_is_user_profile";
    }

    public static final class RawContacts implements ScBaseColumns, RawContactsColumns,  RawContactsColumnsAdd,
        ContactOptionsColumns, ContactNameColumns, SyncColumns {
        /**
         * This utility class cannot be instantiated
         */
        private RawContacts() {
        }

        /**
         * The content:// style URI for this table, which requests a directory of raw contact rows matching the selection
         * criteria.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "raw_contacts");

        /**
         * The content:// style URI used for "type-to-filter" functionality on the
         * {@link #CONTENT_URI} URI. The filter string will be used to match
         * various parts of the contact name. The filter argument should be passed
         * as an additional path segment after this URI.
         */
        public static final Uri CONTENT_FILTER_URI = Uri.withAppendedPath(CONTENT_URI, "filter");

        /**
         * Base {@link Uri} for referencing a single {@link RawContacts} entry. Provides
         * {@link OpenableColumns} columns when queried, or returns the
         * referenced contact formatted as a vCard when opened through
         * {@link ContentResolver#openAssetFileDescriptor(Uri, String)}.
         */
        public static final Uri CONTENT_VCARD_URI = Uri.withAppendedPath(CONTENT_URI, "as_vcard");

//        /**
//         * Base {@link Uri} for referencing multiple {@link Contacts} entry,
//         * created by appending {@link #LOOKUP_KEY} using
//         * {@link Uri#withAppendedPath(Uri, String)}. The lookup keys have to be
//         * encoded and joined with the colon (":") separator. The resulting string
//         * has to be encoded again. Provides
//         * {@link OpenableColumns} columns when queried, or returns the
//         * referenced contact formatted as a vCard when opened through
//         * {@link ContentResolver#openAssetFileDescriptor(Uri, String)}.
//         *
//         * This is private API because we do not have a well-defined way to
//         * specify several entities yet. The format of this Uri might change in the future
//         * or the Uri might be completely removed.
//         *
//         */
//        public static final Uri CONTENT_MULTI_VCARD_URI = Uri.withAppendedPath(CONTENT_URI, "as_multi_vcard");


        /**
         * The MIME type of the results from {@link #CONTENT_URI} when a specific ID value is not provided, and multiple raw
         * contacts may be returned.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.raw_contact";

        /**
         * The MIME type of the results when a raw contact ID is appended to {@link #CONTENT_URI}, yielding a subdirectory of a
         * single person.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.raw_contact";

        /**
         * The MIME type of a {@link #CONTENT_URI} subdirectory of a single
         * person.
         */
        public static final String CONTENT_VCARD_TYPE = "text/x-silentvcard";

        /**
         * Boolean parameter that may be used with {@link #CONTENT_VCARD_URI}
         * and {@link #CONTENT_MULTI_VCARD_URI} to indicate that the returned
         * vcard should not contain a photo.
         */
         public static final String QUERY_PARAMETER_VCARD_NO_PHOTO = "nophoto";

        /**
         * Normal contact
         */
        public static final int CONTACT_TYPE_NORMAL = 0;

        /**
         * Own contact data (own profile)
         */
        public static final int CONTACT_TYPE_OWN = 1;

        /**
         * The content:// style URI for this table joined with useful data from
         * {@link ScContactsContract.Data}, filtered to include only starred contacts
         * and the most frequently contacted contacts.
         */
        public static final Uri CONTENT_STREQUENT_URI = Uri.withAppendedPath(
                CONTENT_URI, "strequent");

        /**
         * The content:// style URI for showing frequently contacted person listing.
         * @hide
         */
        public static final Uri CONTENT_FREQUENT_URI = Uri.withAppendedPath(
                CONTENT_URI, "frequent");

        /**
         * The content:// style URI used for "type-to-filter" functionality on the
         * {@link #CONTENT_STREQUENT_URI} URI. The filter string will be used to match
         * various parts of the contact name. The filter argument should be passed
         * as an additional path segment after this URI.
         */
        public static final Uri CONTENT_STREQUENT_FILTER_URI = Uri.withAppendedPath(
                CONTENT_STREQUENT_URI, "filter");


        /**
         * Simulate a {@link #CONTENT_LOOKUP_URI} lookup {@link Uri} using the
         * given {@link ScContactsContract.RawContacts#_ID}.
         */
        public static Uri getLookupUri(long rawContactId) {
            return ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        }

        /**
         * Opens an InputStream for the contacts's photo and returns the
         * photo as a byte stream.
         * @param cr The content resolver to use for querying
         * @param contactUri the contact whose photo should be used. This can be used with
         * either a {@link #CONTENT_URI} or a {@link #CONTENT_LOOKUP_URI} URI.
         * @param preferHighres If this is true and the contact has a higher resolution photo
         * available, it is returned. If false, this function always tries to get the thumbnail
         * @return an InputStream of the photo, or null if no photo is present
         */
        public static InputStream openContactPhotoInputStream(ContentResolver cr, Uri contactUri, boolean preferHighres) {
            if (preferHighres) {
                final Uri displayPhotoUri = Uri.withAppendedPath(contactUri, RawContacts.Photo.DISPLAY_PHOTO);
                try {
                    AssetFileDescriptor fd = cr.openAssetFileDescriptor(displayPhotoUri, "r");
                    return fd.createInputStream();
                }
                catch (IOException e) {
                    // fallback to the thumbnail code
                }
            }

            Uri photoUri = Uri.withAppendedPath(contactUri, Photo.CONTENT_DIRECTORY);
            if (photoUri == null) {
                return null;
            }
            Cursor cursor = cr.query(photoUri, new String[] { ScContactsContract.CommonDataKinds.Photo.PHOTO }, null, null, null);
            try {
                if (cursor == null || !cursor.moveToNext()) {
                    return null;
                }
                byte[] data = cursor.getBlob(0);
                if (data == null) {
                    return null;
                }
                return new ByteArrayInputStream(data);
            }
            finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        /**
         * Opens an InputStream for the contacts's thumbnail photo and returns the
         * photo as a byte stream.
         * @param cr The content resolver to use for querying
         * @param contactUri the contact whose photo should be used. This can be used with
         * either a {@link #CONTENT_URI} or a {@link #CONTENT_LOOKUP_URI} URI.
         * @return an InputStream of the photo, or null if no photo is present
         * @see #openContactPhotoInputStream(ContentResolver, Uri, boolean), if instead
         * of the thumbnail the high-res picture is preferred
         */
        public static InputStream openContactPhotoInputStream(ContentResolver cr, Uri contactUri) {
            return openContactPhotoInputStream(cr, contactUri, false);
        }

        /**
         * A sub-directory of a single raw contact that contains all of its {@link ContactsContract.Data} rows. To access this
         * directory append {@link Data#CONTENT_DIRECTORY} to the raw contact URI.
         */
        public static final class Data implements ScBaseColumns, DataColumns {
            /**
             * no public constructor since this is a utility class
             */
            private Data() {
            }

            /**
             * The directory twig for this sub-table
             */
            public static final String CONTENT_DIRECTORY = "data";
        }

        /**
         * <p>
         * A sub-directory of a single raw contact that contains all of its {@link ContactsContract.Data} rows. To access this
         * directory append {@link RawContacts.Entity#CONTENT_DIRECTORY} to the raw contact URI. See {@link RawContactsEntity} for
         * a stand-alone table containing the same data.
         * </p>
         * <p>
         * Entity has two ID fields: {@link #_ID} for the raw contact and {@link #DATA_ID} for the data rows. Entity always
         * contains at least one row, even if there are no actual data rows. In this case the {@link #DATA_ID} field will be null.
         * </p>
         * <p>
         * Using Entity should be preferred to using two separate queries: RawContacts followed by Data. The reason is that Entity
         * reads all data for a raw contact in one transaction, so there is no possibility of the data changing between the two
         * queries.
         */
        public static final class Entity implements ScBaseColumns, DataColumns, DataUsageStatColumns {
            /**
             * no public constructor since this is a utility class
             */
            private Entity() {
            }

            /**
             * The directory twig for this sub-table
             */
            public static final String CONTENT_DIRECTORY = "entity";

            /**
             * The ID of the data row. The value will be null if this raw contact has no data rows.
             * <P>
             * Type: INTEGER
             * </P>
             */
            public static final String DATA_ID = "data_id";
        }

        /**
         * <p>
         * A sub-directory of a single raw contact that contains all of its {@link ContactsContract.StreamItems} rows. To access
         * this directory append {@link RawContacts.StreamItems#CONTENT_DIRECTORY} to the raw contact URI. See
         * {@link ContactsContract.StreamItems} for a stand-alone table containing the same data.
         * </p>
         * <p>
         * Access to the social stream through this sub-directory requires additional permissions beyond the read/write contact
         * permissions required by the provider. Querying for social stream data requires android.permission.READ_SOCIAL_STREAM
         * permission, and inserting or updating social stream items requires android.permission.WRITE_SOCIAL_STREAM permission.
         * </p>
         */
        public static final class StreamItems implements ScBaseColumns, StreamItemsColumns {
            /**
             * No public constructor since this is a utility class
             */
            private StreamItems() {
            }

            /**
             * The directory twig for this sub-table
             */
            public static final String CONTENT_DIRECTORY = "stream_items";
        }

        /**
         * <p>
         * A sub-directory of a single raw contact that represents its primary display photo. To access this directory append
         * {@link RawContacts.DisplayPhoto#CONTENT_DIRECTORY} to the raw contact URI. The resulting URI represents an image file,
         * and should be interacted with using ContentResolver.openAssetFileDescriptor.
         * <p>
         * <p>
         * Note that this sub-directory also supports opening the photo as an asset file in write mode. Callers can create or
         * replace the primary photo associated with this raw contact by opening the asset file and writing the full-size photo
         * contents into it. When the file is closed, the image will be parsed, sized down if necessary for the full-size display
         * photo and thumbnail dimensions, and stored.
         * </p>
         * <p>
         * Usage example:
         *
         * <pre>
         * public void writeDisplayPhoto(long rawContactId, byte[] photo) {
         *     Uri rawContactPhotoUri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
         *             RawContacts.DisplayPhoto.CONTENT_DIRECTORY);
         *     try {
         *         AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(rawContactPhotoUri, &quot;rw&quot;);
         *         OutputStream os = fd.createOutputStream();
         *         os.write(photo);
         *         os.close();
         *         fd.close();
         *     }
         *     catch (IOException e) {
         *         // Handle error cases.
         *     }
         * }
         * </pre>
         *
         * </p>
         */
        public static final class DisplayPhoto {
            /**
             * No public constructor since this is a utility class
             */
            private DisplayPhoto() {
            }

            /**
             * The directory twig for this sub-table
             */
            public static final String CONTENT_DIRECTORY = "display_photo";
        }

        /**
         * A <i>read-only</i> sub-directory of a single contact that contains
         * the contact's primary photo.  The photo may be stored in up to two ways -
         * the default "photo" is a thumbnail-sized image stored directly in the data
         * row, while the "display photo", if present, is a larger version stored as
         * a file.
         * <p>
         * Usage example:
         * <dl>
         * <dt>Retrieving the thumbnail-sized photo</dt>
         * <dd>
         * <pre>
         * public InputStream openPhoto(long contactId) {
         *     Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
         *     Uri photoUri = Uri.withAppendedPath(contactUri, Contacts.Photo.CONTENT_DIRECTORY);
         *     Cursor cursor = getContentResolver().query(photoUri,
         *          new String[] {Contacts.Photo.PHOTO}, null, null, null);
         *     if (cursor == null) {
         *         return null;
         *     }
         *     try {
         *         if (cursor.moveToFirst()) {
         *             byte[] data = cursor.getBlob(0);
         *             if (data != null) {
         *                 return new ByteArrayInputStream(data);
         *             }
         *         }
         *     } finally {
         *         cursor.close();
         *     }
         *     return null;
         * }
         * </pre>
         * </dd>
         * <dt>Retrieving the larger photo version</dt>
         * <dd>
         * <pre>
         * public InputStream openDisplayPhoto(long contactId) {
         *     Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
         *     Uri displayPhotoUri = Uri.withAppendedPath(contactUri, Contacts.Photo.DISPLAY_PHOTO);
         *     try {
         *         AssetFileDescriptor fd =
         *             getContentResolver().openAssetFileDescriptor(displayPhotoUri, "r");
         *         return fd.createInputStream();
         *     } catch (IOException e) {
         *         return null;
         *     }
         * }
         * </pre>
         * </dd>
         * </dl>
         *
         * </p>
         * <p>You may also consider using the convenience method
         * {@link ContactsContract.Contacts#openContactPhotoInputStream(ContentResolver, Uri, boolean)}
         * to retrieve the raw photo contents of either the thumbnail-sized or the full-sized photo.
         * </p>
         * <p>
         * This directory can be used either with a {@link #CONTENT_URI} or
         * {@link #CONTENT_LOOKUP_URI}.
         * </p>
         */
        public static final class Photo implements ScBaseColumns, DataColumnsWithJoins {
            /**
             * no public constructor since this is a utility class
             */
            private Photo() {}

            /**
             * The directory twig for this sub-table
             */
            public static final String CONTENT_DIRECTORY = "photo";

            /**
             * The directory twig for retrieving the full-size display photo.
             */
            public static final String DISPLAY_PHOTO = "display_photo";

            /**
             * Full-size photo file ID of the raw contact.
             * See {@link ContactsContract.DisplayPhoto}.
             * <p>
             * Type: NUMBER
             */
            public static final String PHOTO_FILE_ID = DATA14;

            /**
             * Thumbnail photo of the raw contact. This is the raw bytes of an image
             * that could be inflated using {@link android.graphics.BitmapFactory}.
             * <p>
             * Type: BLOB
             */
            public static final String PHOTO = DATA15;
        }
        /**
         * TODO: javadoc
         *
         * @param cursor
         * @return
         */
        public static EntityIterator newEntityIterator(Cursor cursor) {
            return new EntityIteratorImpl(cursor);
        }

        private static class EntityIteratorImpl extends CursorEntityIterator {
            private static final String[] DATA_KEYS = new String[] {
                Data.DATA1,
                Data.DATA2,
                Data.DATA3,
                Data.DATA4,
                Data.DATA5,
                Data.DATA6,
                Data.DATA7,
                Data.DATA8,
                Data.DATA9,
                Data.DATA10,
                Data.DATA11,
                Data.DATA12,
                Data.DATA13,
                Data.DATA14,
                Data.DATA15,
                Data.SYNC1,
                Data.SYNC2,
                Data.SYNC3,
                Data.SYNC4
                };

            public EntityIteratorImpl(Cursor cursor) {
                super(cursor);
            }

            @Override
            public android.content.Entity getEntityAndIncrementCursor(Cursor cursor) throws RemoteException {
                final int columnRawContactId = cursor.getColumnIndexOrThrow(_ID);
                final long rawContactId = cursor.getLong(columnRawContactId);

                // we expect the cursor is already at the row we need to read from
                ContentValues cv = new ContentValues();
                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, _ID);
                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, DIRTY);
                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, VERSION);
                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, SOURCE_ID);
                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, SYNC1);
                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, SYNC2);
                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, SYNC3);
                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, SYNC4);
                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, DELETED);
                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, STARRED);
                DatabaseUtils.cursorIntToContentValuesIfPresent(cursor, cv, NAME_VERIFIED);
                android.content.Entity contact = new android.content.Entity(cv);

                // read data rows until the contact id changes
                do {
                    if (rawContactId != cursor.getLong(columnRawContactId)) {
                        break;
                    }
                    // add the data to to the contact
                    cv = new ContentValues();
                    cv.put(_ID, cursor.getLong(cursor.getColumnIndexOrThrow(Entity.DATA_ID)));
                    DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, Data.MIMETYPE);
                    DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, Data.IS_PRIMARY);
                    DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, Data.IS_SUPER_PRIMARY);
                    DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, cv, Data.DATA_VERSION);
                    DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, CommonDataKinds.GroupMembership.GROUP_SOURCE_ID);
                    DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, Data.DATA_VERSION);
                    DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, cv, PHOTO_URI);

                    for (String key : DATA_KEYS) {
                        final int columnIndex = cursor.getColumnIndexOrThrow(key);
                        // continue early if isNull because SQLCipher returns a wrong value for FIELD_TYPE_NULL
                        if (columnIndex == -1 || cursor.isNull(columnIndex))
                            continue;

                        switch (cursor.getType(columnIndex)) {
                        case Cursor.FIELD_TYPE_NULL:
                            // don't put anything
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                        case Cursor.FIELD_TYPE_FLOAT:
                        case Cursor.FIELD_TYPE_STRING:
                            cv.put(key, cursor.getString(columnIndex));
                            break;
                        case Cursor.FIELD_TYPE_BLOB:
                            cv.put(key, cursor.getBlob(columnIndex));
                            break;
                        default:
                            throw new IllegalStateException("Invalid or unhandled data type");
                        }
                    }
                    contact.addSubValue(ScContactsContract.Data.CONTENT_URI, cv);
                } while (cursor.moveToNext());

                return contact;
            }
        }

        /**
         * Abstract implementation of EntityIterator that makes it easy to wrap a cursor
         * that can contain several consecutive rows for an entity.
         * @hide
         */
        private static abstract class CursorEntityIterator implements EntityIterator {
            private final Cursor mCursor;
            private boolean mIsClosed;

            /**
             * Constructor that makes initializes the cursor such that the iterator points to the
             * first Entity, if there are any.
             * @param cursor the cursor that contains the rows that make up the entities
             */
            public CursorEntityIterator(Cursor cursor) {
                mIsClosed = false;
                mCursor = cursor;
                mCursor.moveToFirst();
            }

            /**
             * Returns the entity that the cursor is currently pointing to. This must take care to advance
             * the cursor past this entity. This will never be called if the cursor is at the end.
             * @param cursor the cursor that contains the entity rows
             * @return the entity that the cursor is currently pointing to
             * @throws RemoteException if a RemoteException is caught while attempting to build the Entity
             */
            public abstract android.content.Entity getEntityAndIncrementCursor(Cursor cursor) throws RemoteException;

            /**
             * Returns whether there are more elements to iterate, i.e. whether the
             * iterator is positioned in front of an element.
             *
             * @return {@code true} if there are more elements, {@code false} otherwise.
             * @see EntityIterator#next()
             */
            public final boolean hasNext() {
                if (mIsClosed) {
                    throw new IllegalStateException("calling hasNext() when the iterator is closed");
                }

                return !mCursor.isAfterLast();
            }

            /**
             * Returns the next object in the iteration, i.e. returns the element in
             * front of the iterator and advances the iterator by one position.
             *
             * @return the next object.
             * @throws java.util.NoSuchElementException
             *             if there are no more elements.
             * @see EntityIterator#hasNext()
             */
            public android.content.Entity next() {
                if (mIsClosed) {
                    throw new IllegalStateException("calling next() when the iterator is closed");
                }
                if (!hasNext()) {
                    throw new IllegalStateException("you may only call next() if hasNext() is true");
                }

                try {
                    return getEntityAndIncrementCursor(mCursor);
                } catch (RemoteException e) {
                    throw new RuntimeException("caught a remote exception, this process will die soon", e);
                }
            }

            public void remove() {
                throw new UnsupportedOperationException("remove not supported by EntityIterators");
            }

            public final void reset() {
                if (mIsClosed) {
                    throw new IllegalStateException("calling reset() when the iterator is closed");
                }
                mCursor.moveToFirst();
            }

            /**
             * Indicates that this iterator is no longer needed and that any associated resources
             * may be released (such as a SQLite cursor).
             */
            public final void close() {
                if (mIsClosed) {
                    throw new IllegalStateException("closing when already closed");
                }
                mIsClosed = true;
                mCursor.close();
            }
        }
    }

    public final static class RawContactsEntity implements ScBaseColumns, DataColumns, RawContactsColumns {
        /**
         * This utility class cannot be instantiated
         */
        private RawContactsEntity() {}

        /**
         * The content:// style URI for this table
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "raw_contact_entities");


        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of raw contact entities.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.raw_contact_entity";

        /**
         * If {@link #FOR_EXPORT_ONLY} is explicitly set to "1", returned Cursor toward
         * Data.CONTENT_URI contains only exportable data.
         *
         * This flag is useful (currently) only for vCard exporter in Contacts app, which
         * needs to exclude "un-exportable" data from available data to export, while
         * Contacts app itself has privilege to access all data including "un-exportable"
         * ones and providers return all of them regardless of the callers' intention.
         * <P>Type: INTEGER</p>
         *
         * @hide Maybe available only in Eclair and not really ready for public use.
         * TODO: remove, or implement this feature completely. As of now (Eclair),
         * we only use this flag in queryEntities(), not query().
         */
        public static final String FOR_EXPORT_ONLY = "for_export_only";

        /**
         * The ID of the data column. The value will be null if this raw contact has no data rows.
         * <P>Type: INTEGER</P>
         */
        public static final String DATA_ID = "data_id";
    }

    public static final class StreamItems implements ScBaseColumns, StreamItemsColumns {
        /**
         * This utility class cannot be instantiated
         */
        private StreamItems() {
        }

        /**
         * The content:// style URI for this table, which handles social network stream
         * updates for the user's contacts.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "stream_items");

        /**
         * <p>
         * A content:// style URI for the photos stored in a sub-table underneath
         * stream items.  This is only used for inserts, and updates - queries and deletes
         * for photos should be performed by appending
         * {@link StreamItems.StreamItemPhotos#CONTENT_DIRECTORY} path to URIs for a
         * specific stream item.
         * </p>
         * <p>
         * When using this URI, the stream item ID for the photo(s) must be identified
         * in the {@link ContentValues} passed in.
         * </p>
         */
        public static final Uri CONTENT_PHOTO_URI = Uri.withAppendedPath(CONTENT_URI, "photo");

        /**
         * This URI allows the caller to query for the maximum number of stream items
         * that will be stored under any single raw contact.
         */
        public static final Uri CONTENT_LIMIT_URI = Uri.withAppendedPath(AUTHORITY_URI, "stream_items_limit");

        /**
         * The MIME type of a directory of stream items.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.stream_item";

        /**
         * The MIME type of a single stream item.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.stream_item";

        /**
         * Queries to {@link ContactsContract.StreamItems#CONTENT_LIMIT_URI} will
         * contain this column, with the value indicating the maximum number of
         * stream items that will be stored under any single raw contact.
         */
        public static final String MAX_ITEMS = "max_items";

        /**
         * <p>
         * A sub-directory of a single stream item entry that contains all of its
         * photo rows. To access this
         * directory append {@link StreamItems.StreamItemPhotos#CONTENT_DIRECTORY} to
         * an individual stream item URI.
         * </p>
         * <p>
         * Access to social stream photos requires additional permissions beyond the read/write
         * contact permissions required by the provider.  Querying for social stream photos
         * requires android.permission.READ_SOCIAL_STREAM permission, and inserting or updating
         * social stream photos requires android.permission.WRITE_SOCIAL_STREAM permission.
         * </p>
         */
        public static final class StreamItemPhotos implements ScBaseColumns, StreamItemPhotosColumns {
            /**
             * No public constructor since this is a utility class
             */
            private StreamItemPhotos() {
            }

            /**
             * The directory twig for this sub-table
             */
            public static final String CONTENT_DIRECTORY = "photo";

            /**
             * The MIME type of a directory of stream item photos.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.stream_item_photo";

            /**
             * The MIME type of a single stream item photo.
             */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.stream_item_photo";
        }
    }

    /**
     * Columns in the StreamItems table.
     *
     * @see ContactsContract.StreamItems
     */
    protected interface StreamItemsColumns {
        /**
         * A reference to the {@link android.provider.ContactsContract.Contacts#_ID}
         * that this stream item belongs to.
         *
         * <p>Type: INTEGER</p>
         * <p>read-only</p>
         */
        public static final String CONTACT_ID = "contact_id";

        /**
         * A reference to the {@link android.provider.ContactsContract.Contacts#LOOKUP_KEY}
         * that this stream item belongs to.
         *
         * <p>Type: TEXT</p>
         * <p>read-only</p>
         */
        public static final String CONTACT_LOOKUP_KEY = "contact_lookup";

        /**
         * A reference to the {@link RawContacts#_ID}
         * that this stream item belongs to.
         * <p>Type: INTEGER</p>
         */
        public static final String RAW_CONTACT_ID = "raw_contact_id";

//        /**
//         * The package name to use when creating {@link Resources} objects for
//         * this stream item. This value is only designed for use when building
//         * user interfaces, and should not be used to infer the owner.
//         * <P>Type: TEXT</P>
//         */
//        public static final String RES_PACKAGE = "res_package";
//
//        /**
//         * The account type to which the raw_contact of this item is associated. See
//         * {@link RawContacts#ACCOUNT_TYPE}
//         *
//         * <p>Type: TEXT</p>
//         * <p>read-only</p>
//         */
//        public static final String ACCOUNT_TYPE = "account_type";
//
//        /**
//         * The account name to which the raw_contact of this item is associated. See
//         * {@link RawContacts#ACCOUNT_NAME}
//         *
//         * <p>Type: TEXT</p>
//         * <p>read-only</p>
//         */
//        public static final String ACCOUNT_NAME = "account_name";
//
//        /**
//         * The data set within the account that the raw_contact of this row belongs to. This allows
//         * multiple sync adapters for the same account type to distinguish between
//         * each others' data.
//         * {@link RawContacts#DATA_SET}
//         *
//         * <P>Type: TEXT</P>
//         * <p>read-only</p>
//         */
//        public static final String DATA_SET = "data_set";
//
        /**
         * The source_id of the raw_contact that this row belongs to.
         * {@link RawContacts#SOURCE_ID}
         *
         * <P>Type: TEXT</P>
         * <p>read-only</p>
         */
        public static final String RAW_CONTACT_SOURCE_ID = "raw_contact_source_id";

        /**
         * The resource name of the icon for the source of the stream item.
         * This resource should be scoped by the {@link #RES_PACKAGE}. As this can only reference
         * drawables, the "@drawable/" prefix must be omitted.
         * <P>Type: TEXT</P>
         */
        public static final String RES_ICON = "icon";

        /**
         * The resource name of the label describing the source of the status update, e.g. "Google
         * Talk". This resource should be scoped by the {@link #RES_PACKAGE}. As this can only
         * reference strings, the "@string/" prefix must be omitted.
         * <p>Type: TEXT</p>
         */
        public static final String RES_LABEL = "label";

        /**
         * <P>
         * The main textual contents of the item. Typically this is content
         * that was posted by the source of this stream item, but it can also
         * be a textual representation of an action (e.g. ”Checked in at Joe's”).
         * This text is displayed to the user and allows formatting and embedded
         * resource images via HTML (as parseable via
         * {@link android.text.Html#fromHtml}).
         * </P>
         * <P>
         * Long content may be truncated and/or ellipsized - the exact behavior
         * is unspecified, but it should not break tags.
         * </P>
         * <P>Type: TEXT</P>
         */
        public static final String TEXT = "text";

        /**
         * The absolute time (milliseconds since epoch) when this stream item was
         * inserted/updated.
         * <P>Type: NUMBER</P>
         */
        public static final String TIMESTAMP = "timestamp";

        /**
         * <P>
         * Summary information about the stream item, for example to indicate how
         * many people have reshared it, how many have liked it, how many thumbs
         * up and/or thumbs down it has, what the original source was, etc.
         * </P>
         * <P>
         * This text is displayed to the user and allows simple formatting via
         * HTML, in the same manner as {@link #TEXT} allows.
         * </P>
         * <P>
         * Long content may be truncated and/or ellipsized - the exact behavior
         * is unspecified, but it should not break tags.
         * </P>
         * <P>Type: TEXT</P>
         */
        public static final String COMMENTS = "comments";

        /** Generic column for use by sync adapters. */
        public static final String SYNC1 = "stream_item_sync1";
        /** Generic column for use by sync adapters. */
        public static final String SYNC2 = "stream_item_sync2";
        /** Generic column for use by sync adapters. */
        public static final String SYNC3 = "stream_item_sync3";
        /** Generic column for use by sync adapters. */
        public static final String SYNC4 = "stream_item_sync4";
    }

    public static final class StreamItemPhotos implements ScBaseColumns, StreamItemPhotosColumns {
        /**
         * No public constructor since this is a utility class
         */
        private StreamItemPhotos() {
        }

        /**
         * <p>
         * The binary representation of the photo.  Any size photo can be inserted;
         * the provider will resize it appropriately for storage and display.
         * </p>
         * <p>
         * This is only intended for use when inserting or updating a stream item photo.
         * To retrieve the photo that was stored, open {@link StreamItemPhotos#PHOTO_URI}
         * as an asset file.
         * </p>
         * <P>Type: BLOB</P>
         */
        public static final String PHOTO = "photo";
    }

    /**
     * Columns in the StreamItemPhotos table.
     *
     * @see ContactsContract.StreamItemPhotos
     */
    protected interface StreamItemPhotosColumns {
        /**
         * A reference to the {@link StreamItems#_ID} this photo is associated with.
         * <P>Type: NUMBER</P>
         */
        public static final String STREAM_ITEM_ID = "stream_item_id";

        /**
         * An integer to use for sort order for photos in the stream item.  If not
         * specified, the {@link StreamItemPhotos#_ID} will be used for sorting.
         * <P>Type: NUMBER</P>
         */
        public static final String SORT_INDEX = "sort_index";

        /**
         * Photo file ID for the photo.
         * See {@link ContactsContract.DisplayPhoto}.
         * <P>Type: NUMBER</P>
         */
        public static final String PHOTO_FILE_ID = "photo_file_id";

        /**
         * URI for retrieving the photo content, automatically populated.  Callers
         * may retrieve the photo content by opening this URI as an asset file.
         * <P>Type: TEXT</P>
         */
        public static final String PHOTO_URI = "photo_uri";

        /** Generic column for use by sync adapters. */
        public static final String SYNC1 = "stream_item_photo_sync1";
        /** Generic column for use by sync adapters. */
        public static final String SYNC2 = "stream_item_photo_sync2";
        /** Generic column for use by sync adapters. */
        public static final String SYNC3 = "stream_item_photo_sync3";
        /** Generic column for use by sync adapters. */
        public static final String SYNC4 = "stream_item_photo_sync4";
    }

    /**
     * <p>
     * Constants for the photo files table, which tracks metadata for hi-res photos
     * stored in the file system.
     * </p>
     *
     * @hide
     */
    public static final class PhotoFiles implements ScBaseColumns, PhotoFilesColumns {
        /**
         * No public constructor since this is a utility class
         */
        private PhotoFiles() {
        }
    }

    /**
     * Columns in the PhotoFiles table.
     *
     * @see ContactsContract.PhotoFiles
     *
     * @hide
     */
    protected interface PhotoFilesColumns {

        /**
         * The height, in pixels, of the photo this entry is associated with.
         * <P>Type: NUMBER</P>
         */
        public static final String HEIGHT = "height";

        /**
         * The width, in pixels, of the photo this entry is associated with.
         * <P>Type: NUMBER</P>
         */
        public static final String WIDTH = "width";

        /**
         * The size, in bytes, of the photo stored on disk.
         * <P>Type: NUMBER</P>
         */
        public static final String FILESIZE = "filesize";
    }

    /**
     * Columns in the Data table.
     *
     * @see ContactsContract.Data
     */
    protected interface DataColumns {
        /**
         * The package name to use when creating {@link Resources} objects for
         * this data row. This value is only designed for use when building user
         * interfaces, and should not be used to infer the owner.
         *
         * @hide
         */
        public static final String RES_PACKAGE = "res_package";

        /**
         * The MIME type of the item represented by this row.
         */
        public static final String MIMETYPE = "mimetype";

        /**
         * A reference to the {@link RawContacts#_ID}
         * that this data belongs to.
         */
        public static final String RAW_CONTACT_ID = "raw_contact_id";

        /**
         * Whether this is the primary entry of its kind for the raw contact it belongs to.
         * <P>Type: INTEGER (if set, non-0 means true)</P>
         */
        public static final String IS_PRIMARY = "is_primary";

        /**
         * Whether this is the primary entry of its kind for the aggregate
         * contact it belongs to. Any data record that is "super primary" must
         * also be "primary".
         * <P>Type: INTEGER (if set, non-0 means true)</P>
         */
        public static final String IS_SUPER_PRIMARY = "is_super_primary";

        /**
         * The "read-only" flag: "0" by default, "1" if the row cannot be modified or
         * deleted except by a sync adapter.  See {@link ContactsContract#CALLER_IS_SYNCADAPTER}.
         * <P>Type: INTEGER</P>
         */
        public static final String IS_READ_ONLY = "is_read_only";

        /**
         * The version of this data record. This is a read-only value. The data column is
         * guaranteed to not change without the version going up. This value is monotonically
         * increasing.
         * <P>Type: INTEGER</P>
         */
        public static final String DATA_VERSION = "data_version";

        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA1 = "data1";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA2 = "data2";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA3 = "data3";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA4 = "data4";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA5 = "data5";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA6 = "data6";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA7 = "data7";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA8 = "data8";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA9 = "data9";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA10 = "data10";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA11 = "data11";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA12 = "data12";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA13 = "data13";
        /** Generic data column, the meaning is {@link #MIMETYPE} specific */
        public static final String DATA14 = "data14";
        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific. By convention,
         * this field is used to store BLOBs (binary data).
         */
        public static final String DATA15 = "data15";

        /** Generic column for use by sync adapters. */
        public static final String SYNC1 = "data_sync1";
        /** Generic column for use by sync adapters. */
        public static final String SYNC2 = "data_sync2";
        /** Generic column for use by sync adapters. */
        public static final String SYNC3 = "data_sync3";
        /** Generic column for use by sync adapters. */
        public static final String SYNC4 = "data_sync4";
    }

    /**
     * Columns in the Data_Usage_Stat table
     */
    protected interface DataUsageStatColumns {
        /** The last time (in milliseconds) this {@link Data} was used. */
        public static final String LAST_TIME_USED = "last_time_used";

        /** The number of times the referenced {@link Data} has been used. */
        public static final String TIMES_USED = "times_used";
    }

    /**
     * Combines all columns returned by {@link ContactsContract.Data} table queries.
     *
     * @see ContactsContract.Data
     */
    protected interface DataColumnsWithJoins extends ScBaseColumns, DataColumns, /* StatusColumns,  TODO */
            RawContactsColumns, RawContactsColumnsAdd, ContactNameColumns, ContactOptionsColumns, DataUsageStatColumns
            /*, ContactStatusColumns*/ {
    }

    public final static class Data implements DataColumnsWithJoins {
        /**
         * This utility class cannot be instantiated
         */
        private Data() {}

        /**
         * The content:// style URI for this table, which requests a directory
         * of data rows matching the selection criteria.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "data");

        /**
         * The MIME type of the results from {@link #CONTENT_URI}.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.data";

        /**
         * <p>
         * Build a {@link android.provider.ContactsContract.Contacts#CONTENT_LOOKUP_URI}
         * style {@link Uri} for the parent {@link android.provider.ContactsContract.Contacts}
         * entry of the given {@link ContactsContract.Data} entry.
         * </p>
         * <p>
         * Returns the Uri for the contact in the first entry returned by
         * {@link ContentResolver#query(Uri, String[], String, String[], String)}
         * for the provided {@code dataUri}.  If the query returns null or empty
         * results, silently returns null.
         * </p>
         */
//        public static Uri getContactLookupUri(ContentResolver resolver, Uri dataUri) {
//            final Cursor cursor = resolver.query(dataUri, new String[] {
//                    RawContacts.CONTACT_ID, Contacts.LOOKUP_KEY
//            }, null, null, null);
//
//            Uri lookupUri = null;
//            try {
//                if (cursor != null && cursor.moveToFirst()) {
//                    final long contactId = cursor.getLong(0);
//                    final String lookupKey = cursor.getString(1);
//                    return Contacts.getLookupUri(contactId, lookupKey);
//                }
//            } finally {
//                if (cursor != null) cursor.close();
//            }
//            return lookupUri;
//        }
    }

    /**
     * @see PhoneLookup
     */
    protected interface PhoneLookupColumns {
        /**
         * The phone number as the user entered it.
         * <P>Type: TEXT</P>
         */
        public static final String NUMBER = "number";

        /**
         * The type of phone number, for example Home or Work.
         * <P>Type: INTEGER</P>
         */
        public static final String TYPE = "type";

        /**
         * The user defined label for the phone number.
         * <P>Type: TEXT</P>
         */
        public static final String LABEL = "label";

        /**
         * The phone number's E164 representation.
         * <P>Type: TEXT</P>
         */
        public static final String NORMALIZED_NUMBER = "normalized_number";
    }

    public static final class PhoneLookup implements ScBaseColumns, PhoneLookupColumns,
        RawContactsColumnsAdd, ContactOptionsColumns {
        /**
         * This utility class cannot be instantiated
         */
        private PhoneLookup() {
        }

        /**
         * The content:// style URI for this table. Append the phone number you want to lookup to this URI and query it to perform
         * a lookup. For example:
         *
         * <pre>
         * Uri lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
         * </pre>
         */
        public static final Uri CONTENT_FILTER_URI = Uri.withAppendedPath(AUTHORITY_URI, "phone_lookup");

        /**
         * The MIME type of {@link #CONTENT_FILTER_URI} providing a directory of phone lookup rows.
         *
         * @hide
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.phone_lookup";

        /**
         * Boolean parameter that is used to look up a SIP address.
         *
         * @hide
         */
        public static final String QUERY_PARAMETER_SIP_ADDRESS = "sip";
    }

    /**
     * Additional column returned by the {@link Contacts#CONTENT_FILTER_URI} providing the
     * explanation of why the filter matched the contact.  Specifically, it contains the
     * data elements that matched the query.  The overall number of words in the snippet
     * can be capped.
     *
     * @hide
     */
    public static class SearchSnippetColumns {

        /**
         * The search snippet constructed according to the SQLite rules, see
         * http://www.sqlite.org/fts3.html#snippet
         * <p>
         * The snippet may contain (parts of) several data elements comprising
         * the contact.
         *
         * @hide
         */
        public static final String SNIPPET = "snippet";


        /**
         * Comma-separated parameters for the generation of the snippet:
         * <ul>
         * <li>The "start match" text. Default is &lt;b&gt;</li>
         * <li>The "end match" text. Default is &lt;/b&gt;</li>
         * <li>The "ellipsis" text. Default is &lt;b&gt;...&lt;/b&gt;</li>
         * <li>Maximum number of tokens to include in the snippet. Can be either
         * a positive or a negative number: A positive number indicates how many
         * tokens can be returned in total. A negative number indicates how many
         * tokens can be returned per occurrence of the search terms.</li>
         * </ul>
         *
         * @hide
         */
        public static final String SNIPPET_ARGS_PARAM_KEY = "snippet_args";

        /**
         * A key to ask the provider to defer the snippeting to the client if possible.
         * Value of 1 implies true, 0 implies false when 0 is the default.
         * When a cursor is returned to the client, it should check for an extra with the name
         * {@link ContactsContract#DEFERRED_SNIPPETING} in the cursor. If it exists, the client
         * should do its own snippeting using {@link ContactsContract#snippetize}. If
         * it doesn't exist, the snippet column in the cursor should already contain a snippetized
         * string.
         *
         * @hide
         */
        public static final String DEFERRED_SNIPPETING_KEY = "deferred_snippeting";
    }

    /**
     * Container for definitions of common data types stored in the {@link ContactsContract.Data}
     * table.
     */
    public static final class CommonDataKinds {
        /**
         * This utility class cannot be instantiated
         */
        private CommonDataKinds() {}

        /**
         * The {@link Data#RES_PACKAGE} value for common data that should be
         * shown using a default style.
         *
         * @hide RES_PACKAGE is hidden
         */
        public static final String PACKAGE_COMMON = "common";

        /**
         * The base types that all "Typed" data kinds support.
         */
        public interface BaseTypes {
            /**
             * A custom type. The custom label should be supplied by user.
             */
            public static int TYPE_CUSTOM = 0;
        }

        /**
         * Columns common across the specific types.
         */
        protected interface CommonColumns extends BaseTypes {
            /**
             * The data for the contact method.
             * <P>Type: TEXT</P>
             */
            public static final String DATA = DataColumns.DATA1;

            /**
             * The type of data, for example Home or Work.
             * <P>Type: INTEGER</P>
             */
            public static final String TYPE = DataColumns.DATA2;

            /**
             * The user defined label for the the contact method.
             * <P>Type: TEXT</P>
             */
            public static final String LABEL = DataColumns.DATA3;
        }

        /**
         * A data kind representing the contact's proper name. You can use all
         * columns defined for {@link ContactsContract.Data} as well as the following aliases.
         *
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th><th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #DISPLAY_NAME}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #GIVEN_NAME}</td>
         * <td>{@link #DATA2}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #FAMILY_NAME}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #PREFIX}</td>
         * <td>{@link #DATA4}</td>
         * <td>Common prefixes in English names are "Mr", "Ms", "Dr" etc.</td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #MIDDLE_NAME}</td>
         * <td>{@link #DATA5}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #SUFFIX}</td>
         * <td>{@link #DATA6}</td>
         * <td>Common suffixes in English names are "Sr", "Jr", "III" etc.</td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #PHONETIC_GIVEN_NAME}</td>
         * <td>{@link #DATA7}</td>
         * <td>Used for phonetic spelling of the name, e.g. Pinyin, Katakana, Hiragana</td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #PHONETIC_MIDDLE_NAME}</td>
         * <td>{@link #DATA8}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #PHONETIC_FAMILY_NAME}</td>
         * <td>{@link #DATA9}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class StructuredName implements DataColumnsWithJoins {
            /**
             * This utility class cannot be instantiated
             */
            private StructuredName() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.name";

            /**
             * The name that should be used to display the contact.
             * <i>Unstructured component of the name should be consistent with
             * its structured representation.</i>
             * <p>
             * Type: TEXT
             */
            public static final String DISPLAY_NAME = DATA1;

            /**
             * The given name for the contact.
             * <P>Type: TEXT</P>
             */
            public static final String GIVEN_NAME = DATA2;

            /**
             * The family name for the contact.
             * <P>Type: TEXT</P>
             */
            public static final String FAMILY_NAME = DATA3;

            /**
             * The contact's honorific prefix, e.g. "Sir"
             * <P>Type: TEXT</P>
             */
            public static final String PREFIX = DATA4;

            /**
             * The contact's middle name
             * <P>Type: TEXT</P>
             */
            public static final String MIDDLE_NAME = DATA5;

            /**
             * The contact's honorific suffix, e.g. "Jr"
             */
            public static final String SUFFIX = DATA6;

            /**
             * The phonetic version of the given name for the contact.
             * <P>Type: TEXT</P>
             */
            public static final String PHONETIC_GIVEN_NAME = DATA7;

            /**
             * The phonetic version of the additional name for the contact.
             * <P>Type: TEXT</P>
             */
            public static final String PHONETIC_MIDDLE_NAME = DATA8;

            /**
             * The phonetic version of the family name for the contact.
             * <P>Type: TEXT</P>
             */
            public static final String PHONETIC_FAMILY_NAME = DATA9;

            /**
             * The style used for combining given/middle/family name into a full name.
             * See {@link ContactsContract.FullNameStyle}.
             *
             * @hide
             */
            public static final String FULL_NAME_STYLE = DATA10;

            /**
             * The alphabet used for capturing the phonetic name.
             * See ContactsContract.PhoneticNameStyle.
             * @hide
             */
            public static final String PHONETIC_NAME_STYLE = DATA11;
        }

        /**
         * <p>A data kind representing the contact's nickname. For example, for
         * Bob Parr ("Mr. Incredible"):
         * <pre>
         * ArrayList&lt;ContentProviderOperation&gt; ops =
         *          new ArrayList&lt;ContentProviderOperation&gt;();
         *
         * ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
         *          .withValue(Data.RAW_CONTACT_ID, rawContactId)
         *          .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
         *          .withValue(StructuredName.DISPLAY_NAME, &quot;Bob Parr&quot;)
         *          .build());
         *
         * ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
         *          .withValue(Data.RAW_CONTACT_ID, rawContactId)
         *          .withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
         *          .withValue(Nickname.NAME, "Mr. Incredible")
         *          .withValue(Nickname.TYPE, Nickname.TYPE_CUSTOM)
         *          .withValue(Nickname.LABEL, "Superhero")
         *          .build());
         *
         * getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
         * </pre>
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as well as the
         * following aliases.
         * </p>
         *
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th><th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #NAME}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>
         * Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_DEFAULT}</li>
         * <li>{@link #TYPE_OTHER_NAME}</li>
         * <li>{@link #TYPE_MAIDEN_NAME}</li>
         * <li>{@link #TYPE_SHORT_NAME}</li>
         * <li>{@link #TYPE_INITIALS}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Nickname implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Nickname() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.nickname";

            public static final int TYPE_DEFAULT = 1;
            public static final int TYPE_OTHER_NAME = 2;
            public static final int TYPE_MAIDEN_NAME = 3;
            /** @deprecated Use TYPE_MAIDEN_NAME instead. */
            @Deprecated
            public static final int TYPE_MAINDEN_NAME = 3;
            public static final int TYPE_SHORT_NAME = 4;
            public static final int TYPE_INITIALS = 5;

            /**
             * The name itself
             */
            public static final String NAME = DATA;
        }

        /**
         * <p>
         * A data kind representing a telephone number.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #NUMBER}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_HOME}</li>
         * <li>{@link #TYPE_MOBILE}</li>
         * <li>{@link #TYPE_WORK}</li>
         * <li>{@link #TYPE_FAX_WORK}</li>
         * <li>{@link #TYPE_FAX_HOME}</li>
         * <li>{@link #TYPE_PAGER}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * <li>{@link #TYPE_CALLBACK}</li>
         * <li>{@link #TYPE_CAR}</li>
         * <li>{@link #TYPE_COMPANY_MAIN}</li>
         * <li>{@link #TYPE_ISDN}</li>
         * <li>{@link #TYPE_MAIN}</li>
         * <li>{@link #TYPE_OTHER_FAX}</li>
         * <li>{@link #TYPE_RADIO}</li>
         * <li>{@link #TYPE_TELEX}</li>
         * <li>{@link #TYPE_TTY_TDD}</li>
         * <li>{@link #TYPE_WORK_MOBILE}</li>
         * <li>{@link #TYPE_WORK_PAGER}</li>
         * <li>{@link #TYPE_ASSISTANT}</li>
         * <li>{@link #TYPE_MMS}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Phone implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Phone() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.phone_v2";

            /**
             * The MIME type of {@link #CONTENT_URI} providing a directory of
             * phones.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.phone_v2";

            /**
             * The content:// style URI for all data records of the
             * {@link #CONTENT_ITEM_TYPE} MIME type, combined with the
             * associated raw contact and aggregate contact data.
             */
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Data.CONTENT_URI, "phones");

            /**
             * The content:// style URL for phone lookup using a filter. The filter returns
             * records of MIME type {@link #CONTENT_ITEM_TYPE}. The filter is applied
             * to display names as well as phone numbers. The filter argument should be passed
             * as an additional path segment after this URI.
             */
            public static final Uri CONTENT_FILTER_URI = Uri.withAppendedPath(CONTENT_URI, "filter");

            /**
             * A boolean query parameter that can be used with {@link #CONTENT_FILTER_URI}.
             * If "1" or "true", display names are searched.  If "0" or "false", display names
             * are not searched.  Default is "1".
             */
            public static final String SEARCH_DISPLAY_NAME_KEY = "search_display_name";

            /**
             * A boolean query parameter that can be used with {@link #CONTENT_FILTER_URI}.
             * If "1" or "true", phone numbers are searched.  If "0" or "false", phone numbers
             * are not searched.  Default is "1".
             */
            public static final String SEARCH_PHONE_NUMBER_KEY = "search_phone_number";

            public static final int TYPE_HOME = 1;
            public static final int TYPE_MOBILE = 2;
            public static final int TYPE_WORK = 3;
            public static final int TYPE_SILENT = 30;
            public static final int TYPE_FAX_WORK = 4;
            public static final int TYPE_FAX_HOME = 5;
            public static final int TYPE_PAGER = 6;
            public static final int TYPE_OTHER = 7;
            public static final int TYPE_CALLBACK = 8;
            public static final int TYPE_CAR = 9;
            public static final int TYPE_COMPANY_MAIN = 10;
            public static final int TYPE_ISDN = 11;
            public static final int TYPE_MAIN = 12;
            public static final int TYPE_OTHER_FAX = 13;
            public static final int TYPE_RADIO = 14;
            public static final int TYPE_TELEX = 15;
            public static final int TYPE_TTY_TDD = 16;
            public static final int TYPE_WORK_MOBILE = 17;
            public static final int TYPE_WORK_PAGER = 18;
            public static final int TYPE_ASSISTANT = 19;
            public static final int TYPE_MMS = 20;

            /**
             * The phone number as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String NUMBER = DATA;

            /**
             * The phone number's E164 representation. This value can be omitted in which
             * case the provider will try to automatically infer it.  (It'll be left null if the
             * provider fails to infer.)
             * If present, {@link #NUMBER} has to be set as well (it will be ignored otherwise).
             * <P>Type: TEXT</P>
             */
            public static final String NORMALIZED_NUMBER = DATA4;

        }

        /**
         * <p>
         * A data kind representing an email address.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #ADDRESS}</td>
         * <td>{@link #DATA1}</td>
         * <td>Email address itself.</td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_HOME}</li>
         * <li>{@link #TYPE_WORK}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * <li>{@link #TYPE_MOBILE}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Email implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Email() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.email_v2";

            /**
             * The MIME type of {@link #CONTENT_URI} providing a directory of email addresses.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.email_v2";

            /**
             * The content:// style URI for all data records of the
             * {@link #CONTENT_ITEM_TYPE} MIME type, combined with the
             * associated raw contact and aggregate contact data.
             */
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Data.CONTENT_URI, "emails");

            /**
             * <p>
             * The content:// style URL for looking up data rows by email address. The
             * lookup argument, an email address, should be passed as an additional path segment
             * after this URI.
             * </p>
             * <p>Example:
             * <pre>
             * Uri uri = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(email));
             * Cursor c = getContentResolver().query(uri,
             *          new String[]{Email.CONTACT_ID, Email.DISPLAY_NAME, Email.DATA},
             *          null, null, null);
             * </pre>
             * </p>
             */
            public static final Uri CONTENT_LOOKUP_URI = Uri.withAppendedPath(CONTENT_URI, "lookup");

            /**
             * <p>
             * The content:// style URL for email lookup using a filter. The filter returns
             * records of MIME type {@link #CONTENT_ITEM_TYPE}. The filter is applied
             * to display names as well as email addresses. The filter argument should be passed
             * as an additional path segment after this URI.
             * </p>
             * <p>The query in the following example will return "Robert Parr (bob@incredibles.com)"
             * as well as "Bob Parr (incredible@android.com)".
             * <pre>
             * Uri uri = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode("bob"));
             * Cursor c = getContentResolver().query(uri,
             *          new String[]{Email.DISPLAY_NAME, Email.DATA},
             *          null, null, null);
             * </pre>
             * </p>
             */
            public static final Uri CONTENT_FILTER_URI = Uri.withAppendedPath(CONTENT_URI, "filter");

            /**
             * The email address.
             * <P>Type: TEXT</P>
             */
            public static final String ADDRESS = DATA1;

            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;
            public static final int TYPE_MOBILE = 4;

            /**
             * The display name for the email address
             * <P>Type: TEXT</P>
             */
            public static final String DISPLAY_NAME = DATA4;
        }

        /**
         * <p>
         * A data kind representing a postal addresses.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #FORMATTED_ADDRESS}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_HOME}</li>
         * <li>{@link #TYPE_WORK}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #STREET}</td>
         * <td>{@link #DATA4}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #POBOX}</td>
         * <td>{@link #DATA5}</td>
         * <td>Post Office Box number</td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #NEIGHBORHOOD}</td>
         * <td>{@link #DATA6}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #CITY}</td>
         * <td>{@link #DATA7}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #REGION}</td>
         * <td>{@link #DATA8}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #POSTCODE}</td>
         * <td>{@link #DATA9}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #COUNTRY}</td>
         * <td>{@link #DATA10}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class StructuredPostal implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private StructuredPostal() {
            }

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.postal-address_v2";

            /**
             * The MIME type of {@link #CONTENT_URI} providing a directory of
             * postal addresses.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.postal-address_v2";

            /**
             * The content:// style URI for all data records of the
             * {@link StructuredPostal#CONTENT_ITEM_TYPE} MIME type.
             */
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Data.CONTENT_URI, "postals");

            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;

            /**
             * The full, unstructured postal address. <i>This field must be
             * consistent with any structured data.</i>
             * <p>
             * Type: TEXT
             */
            public static final String FORMATTED_ADDRESS = DATA;

            /**
             * Can be street, avenue, road, etc. This element also includes the
             * house number and room/apartment/flat/floor number.
             * <p>
             * Type: TEXT
             */
            public static final String STREET = DATA4;

            /**
             * Covers actual P.O. boxes, drawers, locked bags, etc. This is
             * usually but not always mutually exclusive with street.
             * <p>
             * Type: TEXT
             */
            public static final String POBOX = DATA5;

            /**
             * This is used to disambiguate a street address when a city
             * contains more than one street with the same name, or to specify a
             * small place whose mail is routed through a larger postal town. In
             * China it could be a county or a minor city.
             * <p>
             * Type: TEXT
             */
            public static final String NEIGHBORHOOD = DATA6;

            /**
             * Can be city, village, town, borough, etc. This is the postal town
             * and not necessarily the place of residence or place of business.
             * <p>
             * Type: TEXT
             */
            public static final String CITY = DATA7;

            /**
             * A state, province, county (in Ireland), Land (in Germany),
             * departement (in France), etc.
             * <p>
             * Type: TEXT
             */
            public static final String REGION = DATA8;

            /**
             * Postal code. Usually country-wide, but sometimes specific to the
             * city (e.g. "2" in "Dublin 2, Ireland" addresses).
             * <p>
             * Type: TEXT
             */
            public static final String POSTCODE = DATA9;

            /**
             * The name or code of the country.
             * <p>
             * Type: TEXT
             */
            public static final String COUNTRY = DATA10;

        }

        /**
         * <p>
         * A data kind representing an IM address
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #DATA}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_HOME}</li>
         * <li>{@link #TYPE_WORK}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #PROTOCOL}</td>
         * <td>{@link #DATA5}</td>
         * <td>
         * <p>
         * Allowed values:
         * <ul>
         * <li>{@link #PROTOCOL_CUSTOM}. Also provide the actual protocol name
         * as {@link #CUSTOM_PROTOCOL}.</li>
         * <li>{@link #PROTOCOL_AIM}</li>
         * <li>{@link #PROTOCOL_MSN}</li>
         * <li>{@link #PROTOCOL_YAHOO}</li>
         * <li>{@link #PROTOCOL_SKYPE}</li>
         * <li>{@link #PROTOCOL_QQ}</li>
         * <li>{@link #PROTOCOL_GOOGLE_TALK}</li>
         * <li>{@link #PROTOCOL_ICQ}</li>
         * <li>{@link #PROTOCOL_JABBER}</li>
         * <li>{@link #PROTOCOL_NETMEETING}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #CUSTOM_PROTOCOL}</td>
         * <td>{@link #DATA6}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Im implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Im() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.im";

            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;

            public static final int TYPE_SILENT = 30;

            /**
             * This column should be populated with one of the defined
             * constants, e.g. {@link #PROTOCOL_YAHOO}. If the value of this
             * column is {@link #PROTOCOL_CUSTOM}, the {@link #CUSTOM_PROTOCOL}
             * should contain the name of the custom protocol.
             */
            public static final String PROTOCOL = DATA5;

            public static final String CUSTOM_PROTOCOL = DATA6;

            /*
             * The predefined IM protocol types.
             */
            public static final int PROTOCOL_CUSTOM = -1;
            public static final int PROTOCOL_AIM = 0;
            public static final int PROTOCOL_MSN = 1;
            public static final int PROTOCOL_YAHOO = 2;
            public static final int PROTOCOL_SKYPE = 3;
            public static final int PROTOCOL_QQ = 4;
            public static final int PROTOCOL_GOOGLE_TALK = 5;
            public static final int PROTOCOL_ICQ = 6;
            public static final int PROTOCOL_JABBER = 7;
            public static final int PROTOCOL_NETMEETING = 8;

            public static final int PROTOCOL_SILENT = 20;

            /**
             * The content:// style URI for all data records of the
             * {@link #CONTENT_ITEM_TYPE} MIME type, combined with the
             * associated raw contact and aggregate contact data.
             */
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Data.CONTENT_URI, "im");

            /**
             * <p>
             * The content:// style URL for looking up data rows by email address. The
             * lookup argument, an email address, should be passed as an additional path segment
             * after this URI.
             * </p>
             * <p>Example:
             * <pre>
             * Uri uri = Uri.withAppendedPath(Im.CONTENT_LOOKUP_URI, Uri.encode(imAddress));
             * Cursor c = getContentResolver().query(uri,
             *          new String[]{Im._ID, Im.DISPLAY_NAME, Im.DATA},
             *          null, null, null);
             * </pre>
             * </p>
             */
            public static final Uri CONTENT_LOOKUP_URI = Uri.withAppendedPath(CONTENT_URI, "lookup");
        }

        /**
         * <p>
         * A data kind representing an organization.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #COMPANY}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_WORK}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #TITLE}</td>
         * <td>{@link #DATA4}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #DEPARTMENT}</td>
         * <td>{@link #DATA5}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #JOB_DESCRIPTION}</td>
         * <td>{@link #DATA6}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #SYMBOL}</td>
         * <td>{@link #DATA7}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #PHONETIC_NAME}</td>
         * <td>{@link #DATA8}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #OFFICE_LOCATION}</td>
         * <td>{@link #DATA9}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>PHONETIC_NAME_STYLE</td>
         * <td>{@link #DATA10}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Organization implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Organization() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.organization";

            public static final int TYPE_WORK = 1;
            public static final int TYPE_OTHER = 2;

            /**
             * The company as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String COMPANY = DATA;

            /**
             * The position title at this company as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String TITLE = DATA4;

            /**
             * The department at this company as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String DEPARTMENT = DATA5;

            /**
             * The job description at this company as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String JOB_DESCRIPTION = DATA6;

            /**
             * The symbol of this company as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String SYMBOL = DATA7;

            /**
             * The phonetic name of this company as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String PHONETIC_NAME = DATA8;

            /**
             * The office location of this organization.
             * <P>Type: TEXT</P>
             */
            public static final String OFFICE_LOCATION = DATA9;

            /**
             * The alphabet used for capturing the phonetic name.
             * See {@link ContactsContract.PhoneticNameStyle}.
             * @hide
             */
            public static final String PHONETIC_NAME_STYLE = DATA10;

            /**
             * Return the string resource that best describes the given
             * {@link #TYPE}. Will always return a valid resource.
             */
            public static final int getTypeLabelResource(int type) {
                switch (type) {
//                    case TYPE_WORK: return com.android.internal.R.string.orgTypeWork;
//                    case TYPE_OTHER: return com.android.internal.R.string.orgTypeOther;
                    default: return 0;// TODO com.android.internal.R.string.orgTypeCustom;
                }
            }

            /**
             * Return a {@link CharSequence} that best describes the given type,
             * possibly substituting the given {@link #LABEL} value
             * for {@link #TYPE_CUSTOM}.
             */
            public static final CharSequence getTypeLabel(Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                    return label;
                } else {
                    final int labelRes = getTypeLabelResource(type);
                    return res.getText(labelRes);
                }
            }
        }

        /**
         * <p>
         * A data kind representing a relation.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #NAME}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_ASSISTANT}</li>
         * <li>{@link #TYPE_BROTHER}</li>
         * <li>{@link #TYPE_CHILD}</li>
         * <li>{@link #TYPE_DOMESTIC_PARTNER}</li>
         * <li>{@link #TYPE_FATHER}</li>
         * <li>{@link #TYPE_FRIEND}</li>
         * <li>{@link #TYPE_MANAGER}</li>
         * <li>{@link #TYPE_MOTHER}</li>
         * <li>{@link #TYPE_PARENT}</li>
         * <li>{@link #TYPE_PARTNER}</li>
         * <li>{@link #TYPE_REFERRED_BY}</li>
         * <li>{@link #TYPE_RELATIVE}</li>
         * <li>{@link #TYPE_SISTER}</li>
         * <li>{@link #TYPE_SPOUSE}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Relation implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Relation() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.relation";

            public static final int TYPE_ASSISTANT = 1;
            public static final int TYPE_BROTHER = 2;
            public static final int TYPE_CHILD = 3;
            public static final int TYPE_DOMESTIC_PARTNER = 4;
            public static final int TYPE_FATHER = 5;
            public static final int TYPE_FRIEND = 6;
            public static final int TYPE_MANAGER = 7;
            public static final int TYPE_MOTHER = 8;
            public static final int TYPE_PARENT = 9;
            public static final int TYPE_PARTNER = 10;
            public static final int TYPE_REFERRED_BY = 11;
            public static final int TYPE_RELATIVE = 12;
            public static final int TYPE_SISTER = 13;
            public static final int TYPE_SPOUSE = 14;

            /**
             * The name of the relative as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String NAME = DATA;

            /**
             * Return the string resource that best describes the given
             * {@link #TYPE}. Will always return a valid resource.
             */
            public static final int getTypeLabelResource(int type) {
                switch (type) {
//                    case TYPE_ASSISTANT: return com.android.internal.R.string.relationTypeAssistant;
//                    case TYPE_BROTHER: return com.android.internal.R.string.relationTypeBrother;
//                    case TYPE_CHILD: return com.android.internal.R.string.relationTypeChild;
//                    case TYPE_DOMESTIC_PARTNER:
//                            return com.android.internal.R.string.relationTypeDomesticPartner;
//                    case TYPE_FATHER: return com.android.internal.R.string.relationTypeFather;
//                    case TYPE_FRIEND: return com.android.internal.R.string.relationTypeFriend;
//                    case TYPE_MANAGER: return com.android.internal.R.string.relationTypeManager;
//                    case TYPE_MOTHER: return com.android.internal.R.string.relationTypeMother;
//                    case TYPE_PARENT: return com.android.internal.R.string.relationTypeParent;
//                    case TYPE_PARTNER: return com.android.internal.R.string.relationTypePartner;
//                    case TYPE_REFERRED_BY:
//                            return com.android.internal.R.string.relationTypeReferredBy;
//                    case TYPE_RELATIVE: return com.android.internal.R.string.relationTypeRelative;
//                    case TYPE_SISTER: return com.android.internal.R.string.relationTypeSister;
//                    case TYPE_SPOUSE: return com.android.internal.R.string.relationTypeSpouse;
                    default: return 0; // TODO com.android.internal.R.string.orgTypeCustom;
                }
            }

            /**
             * Return a {@link CharSequence} that best describes the given type,
             * possibly substituting the given {@link #LABEL} value
             * for {@link #TYPE_CUSTOM}.
             */
            public static final CharSequence getTypeLabel(Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                    return label;
                } else {
                    final int labelRes = getTypeLabelResource(type);
                    return res.getText(labelRes);
                }
            }
        }

        /**
         * <p>
         * A data kind representing an event.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #START_DATE}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_ANNIVERSARY}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * <li>{@link #TYPE_BIRTHDAY}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Event implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Event() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.contact_event";

            public static final int TYPE_ANNIVERSARY = 1;
            public static final int TYPE_OTHER = 2;
            public static final int TYPE_BIRTHDAY = 3;

            /**
             * The event start date as the user entered it.
             * <P>Type: TEXT</P>
             */
            public static final String START_DATE = DATA;

            /**
             * Return the string resource that best describes the given
             * {@link #TYPE}. Will always return a valid resource.
             */
            public static int getTypeResource(Integer type) {
                if (type == null) {
                    return 0; // TODO com.android.internal.R.string.eventTypeOther;
                }
                switch (type) {
//                    case TYPE_ANNIVERSARY:
//                        return com.android.internal.R.string.eventTypeAnniversary;
//                    case TYPE_BIRTHDAY: return com.android.internal.R.string.eventTypeBirthday;
//                    case TYPE_OTHER: return com.android.internal.R.string.eventTypeOther;
                    default: return 0; // com.android.internal.R.string.eventTypeCustom;
                }
            }
        }

        /**
         * <p>
         * A data kind representing a photo for the contact.
         * </p>
         * <p>
         * Some sync adapters will choose to download photos in a separate
         * pass. A common pattern is to use columns {@link ContactsContract.Data#SYNC1}
         * through {@link ContactsContract.Data#SYNC4} to store temporary
         * data, e.g. the image URL or ID, state of download, server-side version
         * of the image.  It is allowed for the {@link #PHOTO} to be null.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>NUMBER</td>
         * <td>{@link #PHOTO_FILE_ID}</td>
         * <td>{@link #DATA14}</td>
         * <td>ID of the hi-res photo file.</td>
         * </tr>
         * <tr>
         * <td>BLOB</td>
         * <td>{@link #PHOTO}</td>
         * <td>{@link #DATA15}</td>
         * <td>By convention, binary data is stored in DATA15.  The thumbnail of the
         * photo is stored in this column.</td>
         * </tr>
         * </table>
         */
        public static final class Photo implements DataColumnsWithJoins {
            /**
             * This utility class cannot be instantiated
             */
            private Photo() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.photo";

            /**
             * Photo file ID for the display photo of the raw contact.
             * See {@link ContactsContract.DisplayPhoto}.
             * <p>
             * Type: NUMBER
             */
            public static final String PHOTO_FILE_ID = DATA14;

            /**
             * Thumbnail photo of the raw contact. This is the raw bytes of an image
             * that could be inflated using {@link android.graphics.BitmapFactory}.
             * <p>
             * Type: BLOB
             */
            public static final String PHOTO = DATA15;
        }

        /**
         * <p>
         * Notes about the contact.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #NOTE}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Note implements DataColumnsWithJoins {
            /**
             * This utility class cannot be instantiated
             */
            private Note() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.note";

            /**
             * The note text.
             * <P>Type: TEXT</P>
             */
            public static final String NOTE = DATA1;
        }

        /**
         * <p>
         * Group Membership.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>long</td>
         * <td>{@link #GROUP_ROW_ID}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #GROUP_SOURCE_ID}</td>
         * <td>none</td>
         * <td>
         * <p>
         * The sourceid of the group that this group membership refers to.
         * Exactly one of this or {@link #GROUP_ROW_ID} must be set when
         * inserting a row.
         * </p>
         * <p>
         * If this field is specified, the provider will first try to
         * look up a group with this {@link Groups Groups.SOURCE_ID}.  If such a group
         * is found, it will use the corresponding row id.  If the group is not
         * found, it will create one.
         * </td>
         * </tr>
         * </table>
         */
        public static final class GroupMembership implements DataColumnsWithJoins {
            /**
             * This utility class cannot be instantiated
             */
            private GroupMembership() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.group_membership";

            /**
             * The row id of the group that this group membership refers to. Exactly one of
             * this or {@link #GROUP_SOURCE_ID} must be set when inserting a row.
             * <P>Type: INTEGER</P>
             */
            public static final String GROUP_ROW_ID = DATA1;

            /**
             * The sourceid of the group that this group membership refers to.  Exactly one of
             * this or {@link #GROUP_ROW_ID} must be set when inserting a row.
             * <P>Type: TEXT</P>
             */
            public static final String GROUP_SOURCE_ID = "group_sourceid";
        }

        /**
         * <p>
         * A data kind representing a website related to the contact.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #URL}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_HOMEPAGE}</li>
         * <li>{@link #TYPE_BLOG}</li>
         * <li>{@link #TYPE_PROFILE}</li>
         * <li>{@link #TYPE_HOME}</li>
         * <li>{@link #TYPE_WORK}</li>
         * <li>{@link #TYPE_FTP}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class Website implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private Website() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.website";

            public static final int TYPE_HOMEPAGE = 1;
            public static final int TYPE_BLOG = 2;
            public static final int TYPE_PROFILE = 3;
            public static final int TYPE_HOME = 4;
            public static final int TYPE_WORK = 5;
            public static final int TYPE_FTP = 6;
            public static final int TYPE_OTHER = 7;

            /**
             * The website URL string.
             * <P>Type: TEXT</P>
             */
            public static final String URL = DATA;
        }

        /**
         * <p>
         * A data kind representing a SIP address for the contact.
         * </p>
         * <p>
         * You can use all columns defined for {@link ContactsContract.Data} as
         * well as the following aliases.
         * </p>
         * <h2>Column aliases</h2>
         * <table class="jd-sumtable">
         * <tr>
         * <th>Type</th>
         * <th>Alias</th><th colspan='2'>Data column</th>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #SIP_ADDRESS}</td>
         * <td>{@link #DATA1}</td>
         * <td></td>
         * </tr>
         * <tr>
         * <td>int</td>
         * <td>{@link #TYPE}</td>
         * <td>{@link #DATA2}</td>
         * <td>Allowed values are:
         * <p>
         * <ul>
         * <li>{@link #TYPE_CUSTOM}. Put the actual type in {@link #LABEL}.</li>
         * <li>{@link #TYPE_HOME}</li>
         * <li>{@link #TYPE_WORK}</li>
         * <li>{@link #TYPE_OTHER}</li>
         * </ul>
         * </p>
         * </td>
         * </tr>
         * <tr>
         * <td>String</td>
         * <td>{@link #LABEL}</td>
         * <td>{@link #DATA3}</td>
         * <td></td>
         * </tr>
         * </table>
         */
        public static final class SipAddress implements DataColumnsWithJoins, CommonColumns {
            /**
             * This utility class cannot be instantiated
             */
            private SipAddress() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.sip_address";

            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;

            public static final int TYPE_SILENT = 30;

            /**
             * The SIP address.
             * <P>Type: TEXT</P>
             */
            public static final String SIP_ADDRESS = DATA1;
            // ...and TYPE and LABEL come from the CommonColumns interface.

            /**
             * Return the string resource that best describes the given
             * {@link #TYPE}. Will always return a valid resource.
             */
            public static final int getTypeLabelResource(int type) {
                switch (type) {
//                    case TYPE_HOME: return com.android.internal.R.string.sipAddressTypeHome;
//                    case TYPE_WORK: return com.android.internal.R.string.sipAddressTypeWork;
//                    case TYPE_OTHER: return com.android.internal.R.string.sipAddressTypeOther;
                    default: return 0; // TODO com.android.internal.R.string.sipAddressTypeCustom;
                }
            }

            /**
             * Return a {@link CharSequence} that best describes the given type,
             * possibly substituting the given {@link #LABEL} value
             * for {@link #TYPE_CUSTOM}.
             */
            public static final CharSequence getTypeLabel(Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                    return label;
                } else {
                    final int labelRes = getTypeLabelResource(type);
                    return res.getText(labelRes);
                }
            }
        }

        /**
         * A data kind representing an Identity related to the contact.
         * <p>
         * This can be used as a signal by the aggregator to combine raw contacts into
         * contacts, e.g. if two contacts have Identity rows with
         * the same NAMESPACE and IDENTITY values the aggregator can know that they refer
         * to the same person.
         * </p>
         */
        public static final class Identity implements DataColumnsWithJoins {
            /**
             * This utility class cannot be instantiated
             */
            private Identity() {}

            /** MIME type used when storing this in data table. */
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.identity";

            /**
             * The identity string.
             * <P>Type: TEXT</P>
             */
            public static final String IDENTITY = DataColumns.DATA1;

            /**
             * The namespace of the identity string, e.g. "com.google"
             * <P>Type: TEXT</P>
             */
            public static final String NAMESPACE = DataColumns.DATA2;
        }

        /**
         * <p>
         * Convenient functionalities for "callable" data. Note that, this is NOT a separate data
         * kind.
         * </p>
         * <p>
         * This URI allows the ContactsProvider to return a unified result for "callable" data
         * that users can use for calling purposes. {@link Phone} and {@link SipAddress} are the
         * current examples for "callable", but may be expanded to the other types.
         * </p>
         * <p>
         * Each returned row may have a different MIMETYPE and thus different interpretation for
         * each column. For example the meaning for {@link Phone}'s type is different than
         * {@link SipAddress}'s.
         * </p>
         *
         * @hide
         */
        public static final class Callable implements DataColumnsWithJoins, CommonColumns {
            /**
             * Similar to {@link Phone#CONTENT_URI}, but returns callable data instead of only
             * phone numbers.
             */
            public static final Uri CONTENT_URI = Uri.withAppendedPath(Data.CONTENT_URI,
                    "callables");
            /**
             * Similar to {@link Phone#CONTENT_FILTER_URI}, but allows users to filter callable
             * data.
             */
            public static final Uri CONTENT_FILTER_URI = Uri.withAppendedPath(CONTENT_URI,
                    "filter");
        }
    }
    /**
     * Private API for inquiring about the general status of the provider.
     *
     * @hide
     */
    public static final class ProviderStatus {

        /**
         * Not instantiable.
         */
        private ProviderStatus() {
        }

        /**
         * The content:// style URI for this table.  Requests to this URI can be
         * performed on the UI thread because they are always unblocking.
         *
         * @hide
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "provider_status");

        /**
         * The MIME-type of {@link #CONTENT_URI} providing a directory of
         * settings.
         *
         * @hide
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.provider_status";

        /**
         * An integer representing the current status of the provider.
         *
         * @hide
         */
        public static final String STATUS = "status";

        /**
         * Default status of the provider.
         *
         * @hide
         */
        public static final int STATUS_NORMAL = 0;

        /**
         * The status used when the provider is in the process of upgrading.  Contacts
         * are temporarily unaccessible.
         *
         * @hide
         */
        public static final int STATUS_UPGRADING = 1;

        /**
         * The status used if the provider was in the process of upgrading but ran
         * out of storage. The DATA1 column will contain the estimated amount of
         * storage required (in bytes). Update status to STATUS_NORMAL to force
         * the provider to retry the upgrade.
         *
         * @hide
         */
        public static final int STATUS_UPGRADE_OUT_OF_MEMORY = 2;

        /**
         * The status used during a locale change.
         *
         * @hide
         */
        public static final int STATUS_CHANGING_LOCALE = 3;

        /**
         * The status that indicates that there are no accounts and no contacts
         * on the device.
         *
         * @hide
         */
        public static final int STATUS_NO_ACCOUNTS_NO_CONTACTS = 4;

        /**
         * Additional data associated with the status.
         *
         * @hide
         */
        public static final String DATA1 = "data1";
    }

    /**
     * <p>
     * API allowing applications to send usage information for each {@link Data} row to the
     * Contacts Provider. Applications can also clear all usage information.
     * </p>
     * <p>
     * With the feedback, Contacts Provider may return more contextually appropriate results for
     * Data listing, typically supplied with
     * {@link ScContactsContract.CommonDataKinds.Email#CONTENT_FILTER_URI},
     * {@link ScContactsContract.CommonDataKinds.Phone#CONTENT_FILTER_URI}, and users can benefit
     * from better ranked (sorted) lists in applications that show auto-complete list.
     * </p>
     * <p>
     * There is no guarantee for how this feedback is used, or even whether it is used at all.
     * The ranking algorithm will make best efforts to use the feedback data, but the exact
     * implementation, the storage data structures as well as the resulting sort order is device
     * and version specific and can change over time.
     * </p>
     * <p>
     * When updating usage information, users of this API need to use
     * {@link ContentResolver#update(Uri, ContentValues, String, String[])} with a Uri constructed
     * from {@link DataUsageFeedback#FEEDBACK_URI}. The Uri must contain one or more data id(s) as
     * its last path. They also need to append a query parameter to the Uri, to specify the type of
     * the communication, which enables the Contacts Provider to differentiate between kinds of
     * interactions using the same contact data field (for example a phone number can be used to
     * make phone calls or send SMS).
     * </p>
     * <p>
     * Selection and selectionArgs are ignored and must be set to null. To get data ids,
     * you may need to call {@link ContentResolver#query(Uri, String[], String, String[], String)}
     * toward {@link Data#CONTENT_URI}.
     * </p>
     * <p>
     * {@link ContentResolver#update(Uri, ContentValues, String, String[])} returns a positive
     * integer when successful, and returns 0 if no contact with that id was found.
     * </p>
     * <p>
     * Example:
     * <pre>
     * Uri uri = DataUsageFeedback.FEEDBACK_URI.buildUpon()
     *         .appendPath(TextUtils.join(",", dataIds))
     *         .appendQueryParameter(DataUsageFeedback.USAGE_TYPE,
     *                 DataUsageFeedback.USAGE_TYPE_CALL)
     *         .build();
     * boolean successful = resolver.update(uri, new ContentValues(), null, null) > 0;
     * </pre>
     * </p>
     * <p>
     * Applications can also clear all usage information with:
     * <pre>
     * boolean successful = resolver.delete(DataUsageFeedback.DELETE_USAGE_URI, null, null) > 0;
     * </pre>
     * </p>
     */
    public static final class DataUsageFeedback {

        /**
         * The content:// style URI for sending usage feedback.
         * Must be used with {@link ContentResolver#update(Uri, ContentValues, String, String[])}.
         */
        public static final Uri FEEDBACK_URI =  Uri.withAppendedPath(Data.CONTENT_URI, "usagefeedback");

        /**
         * The content:// style URI for deleting all usage information.
         * Must be used with {@link ContentResolver#delete(Uri, String, String[])}.
         * The {@code where} and {@code selectionArgs} parameters are ignored.
         */
        public static final Uri DELETE_USAGE_URI =  Uri.withAppendedPath(RawContacts.CONTENT_URI, "delete_usage");

        /**
         * <p>
         * Name for query parameter specifying the type of data usage.
         * </p>
         */
        public static final String USAGE_TYPE = "type";

        /**
         * <p>
         * Type of usage for voice interaction, which includes phone call, voice chat, and
         * video chat.
         * </p>
         */
        public static final String USAGE_TYPE_CALL = "call";

        /**
         * <p>
         * Type of usage for text interaction involving longer messages, which includes email.
         * </p>
         */
        public static final String USAGE_TYPE_LONG_TEXT = "long_text";

        /**
         * <p>
         * Type of usage for text interaction involving shorter messages, which includes SMS,
         * text chat with email addresses.
         * </p>
         */
        public static final String USAGE_TYPE_SHORT_TEXT = "short_text";
    }

    public static final class DisplayPhoto {
        /**
         * no public constructor since this is a utility class
         */
        private DisplayPhoto() {}

        /**
         * The content:// style URI for this class, which allows access to full-size photos,
         * given a key.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "display_photo");

        /**
         * This URI allows the caller to query for the maximum dimensions of a display photo
         * or thumbnail.  Requests to this URI can be performed on the UI thread because
         * they are always unblocking.
         */
        public static final Uri CONTENT_MAX_DIMENSIONS_URI =  Uri.withAppendedPath(AUTHORITY_URI, "photo_dimensions");

        /**
         * Queries to {@link ContactsContract.DisplayPhoto#CONTENT_MAX_DIMENSIONS_URI} will
         * contain this column, populated with the maximum height and width (in pixels)
         * that will be stored for a display photo.  Larger photos will be down-sized to
         * fit within a square of this many pixels.
         */
        public static final String DISPLAY_MAX_DIM = "display_max_dim";

        /**
         * Queries to {@link ContactsContract.DisplayPhoto#CONTENT_MAX_DIMENSIONS_URI} will
         * contain this column, populated with the height and width (in pixels) for photo
         * thumbnails.
         */
        public static final String THUMBNAIL_MAX_DIM = "thumbnail_max_dim";
    }

    /**
     * @see Groups
     */
    protected interface GroupsColumns {
//        /**
//         * The data set within the account that this group belongs to.  This allows
//         * multiple sync adapters for the same account type to distinguish between
//         * each others' group data.
//         *
//         * This is empty by default, and is completely optional.  It only needs to
//         * be populated if multiple sync adapters are entering distinct group data
//         * for the same account type and account name.
//         * <P>Type: TEXT</P>
//         */
//        public static final String DATA_SET = "data_set";
//
//        /**
//         * A concatenation of the account type and data set (delimited by a forward
//         * slash) - if the data set is empty, this will be the same as the account
//         * type.  For applications that need to be aware of the data set, this can
//         * be used instead of account type to distinguish sets of data.  This is
//         * never intended to be used for specifying accounts.
//         * @hide
//         */
//        public static final String ACCOUNT_TYPE_AND_DATA_SET = "account_type_and_data_set";
//
        /**
         * The display title of this group.
         * <p>
         * Type: TEXT
         */
        public static final String TITLE = "title";

        /**
         * The package name to use when creating {@link Resources} objects for
         * this group. This value is only designed for use when building user
         * interfaces, and should not be used to infer the owner.
         *
         * @hide
         */
        public static final String RES_PACKAGE = "res_package";

        /**
         * The display title of this group to load as a resource from
         * {@link #RES_PACKAGE}, which may be localized.
         * <P>Type: TEXT</P>
         *
         * @hide
         */
        public static final String TITLE_RES = "title_res";

        /**
         * Notes about the group.
         * <p>
         * Type: TEXT
         */
        public static final String NOTES = "notes";

        /**
         * The ID of this group if it is a System Group, i.e. a group that has a special meaning
         * to the sync adapter, null otherwise.
         * <P>Type: TEXT</P>
         */
        public static final String SYSTEM_ID = "system_id";

        /**
         * The total number of {@link Contacts} that have
         * {@link CommonDataKinds.GroupMembership} in this group. Read-only value that is only
         * present when querying {@link Groups#CONTENT_SUMMARY_URI}.
         * <p>
         * Type: INTEGER
         */
        public static final String SUMMARY_COUNT = "summ_count";

        /**
         * A boolean query parameter that can be used with {@link Groups#CONTENT_SUMMARY_URI}.
         * It will additionally return {@link #SUMMARY_GROUP_COUNT_PER_ACCOUNT}.
         *
         * @hide
         */
        public static final String PARAM_RETURN_GROUP_COUNT_PER_ACCOUNT = "return_group_count_per_account";

        /**
         * The total number of groups of the account that a group belongs to.
         * This column is available only when the parameter
         * {@link #PARAM_RETURN_GROUP_COUNT_PER_ACCOUNT} is specified in
         * {@link Groups#CONTENT_SUMMARY_URI}.
         *
         * For example, when the account "A" has two groups "group1" and "group2", and the account
         * "B" has a group "group3", the rows for "group1" and "group2" return "2" and the row for
         * "group3" returns "1" for this column.
         *
         * Note: This counts only non-favorites, non-auto-add, and not deleted groups.
         *
         * Type: INTEGER
         * @hide
         */
        public static final String SUMMARY_GROUP_COUNT_PER_ACCOUNT = "group_count_per_account";

        /**
         * The total number of {@link Contacts} that have both
         * {@link CommonDataKinds.GroupMembership} in this group, and also have phone numbers.
         * Read-only value that is only present when querying
         * {@link Groups#CONTENT_SUMMARY_URI}.
         * <p>
         * Type: INTEGER
         */
        public static final String SUMMARY_WITH_PHONES = "summ_phones";

        /**
         * Flag indicating if the contacts belonging to this group should be
         * visible in any user interface.
         * <p>
         * Type: INTEGER (boolean)
         */
        public static final String GROUP_VISIBLE = "group_visible";

        /**
         * The "deleted" flag: "0" by default, "1" if the row has been marked
         * for deletion. When {@link android.content.ContentResolver#delete} is
         * called on a group, it is marked for deletion. The sync adaptor
         * deletes the group on the server and then calls ContactResolver.delete
         * once more, this time setting the the
         * {@link ContactsContract#CALLER_IS_SYNCADAPTER} query parameter to
         * finalize the data removal.
         * <P>Type: INTEGER</P>
         */
        public static final String DELETED = "deleted";

        /**
         * Whether this group should be synced if the SYNC_EVERYTHING settings
         * is false for this group's account.
         * <p>
         * Type: INTEGER (boolean)
         */
        public static final String SHOULD_SYNC = "should_sync";

        /**
         * Any newly created contacts will automatically be added to groups that have this
         * flag set to true.
         * <p>
         * Type: INTEGER (boolean)
         */
        public static final String AUTO_ADD = "auto_add";

        /**
         * When a contacts is marked as a favorites it will be automatically added
         * to the groups that have this flag set, and when it is removed from favorites
         * it will be removed from these groups.
         * <p>
         * Type: INTEGER (boolean)
         */
        public static final String FAVORITES = "favorites";

        /**
         * The "read-only" flag: "0" by default, "1" if the row cannot be modified or
         * deleted except by a sync adapter.  See {@link ContactsContract#CALLER_IS_SYNCADAPTER}.
         * <P>Type: INTEGER</P>
         */
        public static final String GROUP_IS_READ_ONLY = "group_is_read_only";
    }

    /**
     * Constants for the groups table. Only per-account groups are supported.
     * <h2>Columns</h2>
     * <table class="jd-sumtable">
     * <tr>
     * <th colspan='4'>Groups</th>
     * </tr>
     * <tr>
     * <td>long</td>
     * <td>{@link #_ID}</td>
     * <td>read-only</td>
     * <td>Row ID. Sync adapter should try to preserve row IDs during updates.
     * In other words, it would be a really bad idea to delete and reinsert a
     * group. A sync adapter should always do an update instead.</td>
     * </tr>
     # <tr>
     * <td>String</td>
     * <td>{@link #DATA_SET}</td>
     * <td>read/write-once</td>
     * <td>
     * <p>
     * The data set within the account that this group belongs to.  This allows
     * multiple sync adapters for the same account type to distinguish between
     * each others' group data.  The combination of {@link #ACCOUNT_TYPE},
     * {@link #ACCOUNT_NAME}, and {@link #DATA_SET} identifies a set of data
     * that is associated with a single sync adapter.
     * </p>
     * <p>
     * This is empty by default, and is completely optional.  It only needs to
     * be populated if multiple sync adapters are entering distinct data for
     * the same account type and account name.
     * </p>
     * <p>
     * It should be set at the time the group is inserted and never changed
     * afterwards.
     * </p>
     * </td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #TITLE}</td>
     * <td>read/write</td>
     * <td>The display title of this group.</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #NOTES}</td>
     * <td>read/write</td>
     * <td>Notes about the group.</td>
     * </tr>
     * <tr>
     * <td>String</td>
     * <td>{@link #SYSTEM_ID}</td>
     * <td>read/write</td>
     * <td>The ID of this group if it is a System Group, i.e. a group that has a
     * special meaning to the sync adapter, null otherwise.</td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #SUMMARY_COUNT}</td>
     * <td>read-only</td>
     * <td>The total number of {@link Contacts} that have
     * {@link CommonDataKinds.GroupMembership} in this group. Read-only value
     * that is only present when querying {@link Groups#CONTENT_SUMMARY_URI}.</td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #SUMMARY_WITH_PHONES}</td>
     * <td>read-only</td>
     * <td>The total number of {@link Contacts} that have both
     * {@link CommonDataKinds.GroupMembership} in this group, and also have
     * phone numbers. Read-only value that is only present when querying
     * {@link Groups#CONTENT_SUMMARY_URI}.</td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #GROUP_VISIBLE}</td>
     * <td>read-only</td>
     * <td>Flag indicating if the contacts belonging to this group should be
     * visible in any user interface. Allowed values: 0 and 1.</td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #DELETED}</td>
     * <td>read/write</td>
     * <td>The "deleted" flag: "0" by default, "1" if the row has been marked
     * for deletion. When {@link android.content.ContentResolver#delete} is
     * called on a group, it is marked for deletion. The sync adaptor deletes
     * the group on the server and then calls ContactResolver.delete once more,
     * this time setting the the {@link ContactsContract#CALLER_IS_SYNCADAPTER}
     * query parameter to finalize the data removal.</td>
     * </tr>
     * <tr>
     * <td>int</td>
     * <td>{@link #SHOULD_SYNC}</td>
     * <td>read/write</td>
     * <td>Whether this group should be synced if the SYNC_EVERYTHING settings
     * is false for this group's account.</td>
     * </tr>
     * </table>
     */
    public static final class Groups implements ScBaseColumns, GroupsColumns, SyncColumns {
        /**
         * This utility class cannot be instantiated
         */
        private Groups() {
        }

        /**
         * The content:// style URI for this table
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "groups");

        /**
         * The content:// style URI for this table joined with details data from
         * {@link ContactsContract.Data}.
         */
        public static final Uri CONTENT_SUMMARY_URI = Uri.withAppendedPath(AUTHORITY_URI, "groups_summary");

        /**
         * The MIME type of a directory of groups.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.silentcircle.group";

        /**
         * The MIME type of a single group.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.silentcircle.group";

//        public static EntityIterator newEntityIterator(Cursor cursor) {
//            return new EntityIteratorImpl(cursor);
//        }
//
//        private static class EntityIteratorImpl extends CursorEntityIterator {
//            public EntityIteratorImpl(Cursor cursor) {
//                super(cursor);
//            }
//
//            @Override
//            public Entity getEntityAndIncrementCursor(Cursor cursor) throws RemoteException {
//                // we expect the cursor is already at the row we need to read from
//                final ContentValues values = new ContentValues();
//                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, values, _ID);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, ACCOUNT_NAME);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, ACCOUNT_TYPE);
//                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, values, DIRTY);
//                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, values, VERSION);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, SOURCE_ID);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, RES_PACKAGE);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, TITLE);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, TITLE_RES);
//                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, values, GROUP_VISIBLE);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, SYNC1);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, SYNC2);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, SYNC3);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, SYNC4);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, SYSTEM_ID);
//                DatabaseUtils.cursorLongToContentValuesIfPresent(cursor, values, DELETED);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, NOTES);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, SHOULD_SYNC);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, FAVORITES);
//                DatabaseUtils.cursorStringToContentValuesIfPresent(cursor, values, AUTO_ADD);
//                cursor.moveToNext();
//                return new Entity(values);
//            }
//        }
    }

    /**
     * Contains helper classes used to create or manage {@link android.content.Intent Intents}
     * that involve contacts.
     */
    public static final class Intents {
        /**
         * This is the intent that is fired when a search suggestion is clicked on.
         */
        public static final String SEARCH_SUGGESTION_CLICKED =
                "com.silentcircle.silentcontacts.provider.Contacts.SEARCH_SUGGESTION_CLICKED";

        /**
         * This is the intent that is fired when a search suggestion for dialing a number
         * is clicked on.
         */
        public static final String SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED =
                "com.silentcircle.silentcontacts.provider.Contacts.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED";

        /**
         * This is the intent that is fired when a search suggestion for creating a contact
         * is clicked on.
         */
        public static final String SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED =
                "com.silentcircle.silentcontacts.provider.Contacts.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED";

        /**
         * Starts an Activity that lets the user pick a contact to attach an image to.
         * After picking the contact it launches the image cropper in face detection mode.
         */
        public static final String ATTACH_IMAGE =
                "com.silentcircle.silentcontacts.action.ATTACH_IMAGE";

        /**
         * This is the intent that is fired when the user clicks the "invite to the network" button
         * on a contact.  Only sent to an activity which is explicitly registered by a contact
         * provider which supports the "invite to the network" feature.
         * <p>
         * {@link Intent#getData()} contains the lookup URI for the contact.
         */
        public static final String INVITE_CONTACT =
                "com.silentcircle.silentcontacts.action.INVITE_CONTACT";

        /**
         * Takes as input a data URI with a mailto: or tel: scheme. If a single
         * contact exists with the given data it will be shown. If no contact
         * exists, a dialog will ask the user if they want to create a new
         * contact with the provided details filled in. If multiple contacts
         * share the data the user will be prompted to pick which contact they
         * want to view.
         * <p>
         * For <code>mailto:</code> URIs, the scheme specific portion must be a
         * raw email address, such as one built using
         * {@link Uri#fromParts(String, String, String)}.
         * <p>
         * For <code>tel:</code> URIs, the scheme specific portion is compared
         * to existing numbers using the standard caller ID lookup algorithm.
         * The number must be properly encoded, for example using
         * {@link Uri#fromParts(String, String, String)}.
         * <p>
         * Any extras from the {@link Insert} class will be passed along to the
         * create activity if there are no contacts to show.
         * <p>
         * Passing true for the {@link #EXTRA_FORCE_CREATE} extra will skip
         * prompting the user when the contact doesn't exist.
         */
        public static final String SHOW_OR_CREATE_CONTACT =
                "com.silentcircle.silentcontacts.action.SHOW_OR_CREATE_CONTACT";

        /**
         * Starts an Activity that lets the user select the multiple phones from a
         * list of phone numbers which come from the contacts or
         * {@link #EXTRA_PHONE_URIS}.
         * <p>
         * The phone numbers being passed in through {@link #EXTRA_PHONE_URIS}
         * could belong to the contacts or not, and will be selected by default.
         * <p>
         * The user's selection will be returned from
         * {@link android.app.Activity#onActivityResult(int, int, android.content.Intent)}
         * if the resultCode is
         * {@link android.app.Activity#RESULT_OK}, the array of picked phone
         * numbers are in the Intent's
         * {@link #EXTRA_PHONE_URIS}; otherwise, the
         * {@link android.app.Activity#RESULT_CANCELED} is returned if the user
         * left the Activity without changing the selection.
         *
         * @hide
         */
        public static final String ACTION_GET_MULTIPLE_PHONES =
                "com.silentcircle.silentcontacts.action.GET_MULTIPLE_PHONES";

        /**
         * A broadcast action which is sent when any change has been made to the profile, such
         * as the profile name or the picture.  A receiver must have
         * the android.permission.READ_PROFILE permission.
         *
         * @hide
         */
        public static final String ACTION_PROFILE_CHANGED =
                "com.silentcircle.silentcontacts.provider.Contacts.PROFILE_CHANGED";

        /**
         * Used with {@link #SHOW_OR_CREATE_CONTACT} to force creating a new
         * contact if no matching contact found. Otherwise, default behavior is
         * to prompt user with dialog before creating.
         * <p>
         * Type: BOOLEAN
         */
        public static final String EXTRA_FORCE_CREATE =
                "com.silentcircle.silentcontacts.action.FORCE_CREATE";

        /**
         * Used with {@link #SHOW_OR_CREATE_CONTACT} to specify an exact
         * description to be shown when prompting user about creating a new
         * contact.
         * <p>
         * Type: STRING
         */
        public static final String EXTRA_CREATE_DESCRIPTION =
            "com.silentcircle.silentcontacts.action.CREATE_DESCRIPTION";

        /**
         * Used with {@link #ACTION_GET_MULTIPLE_PHONES} as the input or output value.
         * <p>
         * The phone numbers want to be picked by default should be passed in as
         * input value. These phone numbers could belong to the contacts or not.
         * <p>
         * The phone numbers which were picked by the user are returned as output
         * value.
         * <p>
         * Type: array of URIs, the tel URI is used for the phone numbers which don't
         * belong to any contact, the content URI is used for phone id in contacts.
         *
         * @hide
         */
        public static final String EXTRA_PHONE_URIS =
            "com.silentcircle.silentcontacts.extra.PHONE_URIS";

        /**
         * Optional extra used with {@link #SHOW_OR_CREATE_CONTACT} to specify a
         * dialog location using screen coordinates. When not specified, the
         * dialog will be centered.
         *
         * @hide
         */
        @Deprecated
        public static final String EXTRA_TARGET_RECT = "target_rect";

        /**
         * Optional extra used with {@link #SHOW_OR_CREATE_CONTACT} to specify a
         * desired dialog style, usually a variation on size. One of
         * {@link #MODE_SMALL}, {@link #MODE_MEDIUM}, or {@link #MODE_LARGE}.
         *
         * @hide
         */
        @Deprecated
        public static final String EXTRA_MODE = "mode";

        /**
         * Value for {@link #EXTRA_MODE} to show a small-sized dialog.
         *
         * @hide
         */
        @Deprecated
        public static final int MODE_SMALL = 1;

        /**
         * Value for {@link #EXTRA_MODE} to show a medium-sized dialog.
         *
         * @hide
         */
        @Deprecated
        public static final int MODE_MEDIUM = 2;

        /**
         * Value for {@link #EXTRA_MODE} to show a large-sized dialog.
         *
         * @hide
         */
        @Deprecated
        public static final int MODE_LARGE = 3;

        /**
         * Optional extra used with {@link #SHOW_OR_CREATE_CONTACT} to indicate
         * a list of specific MIME-types to exclude and not display. Stored as a
         * {@link String} array.
         *
         * @hide
         */
        @Deprecated
        public static final String EXTRA_EXCLUDE_MIMES = "exclude_mimes";

        /**
         * Intents related to the Contacts app UI.
         *
         * @hide
         */
        public static final class UI {
            /**
             * The action for the default contacts list tab.
             */
            public static final String LIST_DEFAULT =
                    "com.silentcircle.silentcontacts.action.LIST_DEFAULT";

            /**
             * The action for the contacts list tab.
             */
            public static final String LIST_GROUP_ACTION =
                    "com.silentcircle.silentcontacts.action.LIST_GROUP";

            /**
             * When in LIST_GROUP_ACTION mode, this is the group to display.
             */
            public static final String GROUP_NAME_EXTRA_KEY = "com.silentcircle.silentcontacts.extra.GROUP";

            /**
             * The action for the all contacts list tab.
             */
            public static final String LIST_ALL_CONTACTS_ACTION =
                    "com.silentcircle.silentcontacts.action.LIST_ALL_CONTACTS";

            /**
             * The action for the contacts with phone numbers list tab.
             */
            public static final String LIST_CONTACTS_WITH_PHONES_ACTION =
                    "com.silentcircle.silentcontacts.action.LIST_CONTACTS_WITH_PHONES";

            /**
             * The action for the starred contacts list tab.
             */
            public static final String LIST_STARRED_ACTION =
                    "com.silentcircle.silentcontacts.action.LIST_STARRED";

            /**
             * The action for the frequent contacts list tab.
             */
            public static final String LIST_FREQUENT_ACTION =
                    "com.silentcircle.silentcontacts.action.LIST_FREQUENT";

            /**
             * The action for the "strequent" contacts list tab. It first lists the starred
             * contacts in alphabetical order and then the frequent contacts in descending
             * order of the number of times they have been contacted.
             */
            public static final String LIST_STREQUENT_ACTION =
                    "com.silentcircle.silentcontacts.action.LIST_STREQUENT";

            /**
             * A key for to be used as an intent extra to set the activity
             * title to a custom String value.
             */
            public static final String TITLE_EXTRA_KEY =
                    "com.silentcircle.silentcontacts.extra.TITLE_EXTRA";

            /**
             * Activity Action: Display a filtered list of contacts
             * <p>
             * Input: Extra field {@link #FILTER_TEXT_EXTRA_KEY} is the text to use for
             * filtering
             * <p>
             * Output: Nothing.
             */
            public static final String FILTER_CONTACTS_ACTION =
                    "com.silentcircle.silentcontacts.action.FILTER_CONTACTS";

            /**
             * Used as an int extra field in {@link #FILTER_CONTACTS_ACTION}
             * intents to supply the text on which to filter.
             */
            public static final String FILTER_TEXT_EXTRA_KEY =
                    "com.silentcircle.silentcontacts.extra.FILTER_TEXT";
        }

        /**
         * Convenience class that contains string constants used
         * to create contact {@link android.content.Intent Intents}.
         */
        public static final class Insert {
            /** The action code to use when adding a contact */
            public static final String ACTION = Intent.ACTION_INSERT;

            /**
             * If present, forces a bypass of quick insert mode.
             */
            public static final String FULL_MODE = "full_mode";

            /**
             * The extra field for the contact name.
             * <P>Type: String</P>
             */
            public static final String NAME = "name";

            // TODO add structured name values here.

            /**
             * The extra field for the contact phonetic name.
             * <P>Type: String</P>
             */
            public static final String PHONETIC_NAME = "phonetic_name";

            /**
             * The extra field for the contact company.
             * <P>Type: String</P>
             */
            public static final String COMPANY = "company";

            /**
             * The extra field for the contact job title.
             * <P>Type: String</P>
             */
            public static final String JOB_TITLE = "job_title";

            /**
             * The extra field for the contact notes.
             * <P>Type: String</P>
             */
            public static final String NOTES = "notes";

            /**
             * The extra field for the contact phone number.
             * <P>Type: String</P>
             */
            public static final String PHONE = "phone";

            /**
             * The extra field for the contact phone number type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.Phone},
             *  or a string specifying a custom label.</P>
             */
            public static final String PHONE_TYPE = "phone_type";

            /**
             * The extra field for the phone isprimary flag.
             * <P>Type: boolean</P>
             */
            public static final String PHONE_ISPRIMARY = "phone_isprimary";

            /**
             * The extra field for the phone's SIP address.
             * <P>Type: String</P>
             */
            public static final String SIP_ADDRESS = "sip_address";

            /**
             * The extra field for an optional second contact phone number.
             * <P>Type: String</P>
             */
            public static final String SECONDARY_PHONE = "secondary_phone";

            /**
             * The extra field for an optional second contact phone number type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.Phone},
             *  or a string specifying a custom label.</P>
             */
            public static final String SECONDARY_PHONE_TYPE = "secondary_phone_type";

            /**
             * The extra field for an optional third contact phone number.
             * <P>Type: String</P>
             */
            public static final String TERTIARY_PHONE = "tertiary_phone";

            /**
             * The extra field for an optional third contact phone number type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.Phone},
             *  or a string specifying a custom label.</P>
             */
            public static final String TERTIARY_PHONE_TYPE = "tertiary_phone_type";

            /**
             * The extra field for the contact email address.
             * <P>Type: String</P>
             */
            public static final String EMAIL = "email";

            /**
             * The extra field for the contact email type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.Email}
             *  or a string specifying a custom label.</P>
             */
            public static final String EMAIL_TYPE = "email_type";

            /**
             * The extra field for the email isprimary flag.
             * <P>Type: boolean</P>
             */
            public static final String EMAIL_ISPRIMARY = "email_isprimary";

            /**
             * The extra field for an optional second contact email address.
             * <P>Type: String</P>
             */
            public static final String SECONDARY_EMAIL = "secondary_email";

            /**
             * The extra field for an optional second contact email type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.Email}
             *  or a string specifying a custom label.</P>
             */
            public static final String SECONDARY_EMAIL_TYPE = "secondary_email_type";

            /**
             * The extra field for an optional third contact email address.
             * <P>Type: String</P>
             */
            public static final String TERTIARY_EMAIL = "tertiary_email";

            /**
             * The extra field for an optional third contact email type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.Email}
             *  or a string specifying a custom label.</P>
             */
            public static final String TERTIARY_EMAIL_TYPE = "tertiary_email_type";

            /**
             * The extra field for the contact postal address.
             * <P>Type: String</P>
             */
            public static final String POSTAL = "postal";

            /**
             * The extra field for the contact postal address type.
             * <P>Type: Either an integer value from
             * {@link CommonDataKinds.StructuredPostal}
             *  or a string specifying a custom label.</P>
             */
            public static final String POSTAL_TYPE = "postal_type";

            /**
             * The extra field for the postal isprimary flag.
             * <P>Type: boolean</P>
             */
            public static final String POSTAL_ISPRIMARY = "postal_isprimary";

            /**
             * The extra field for an IM handle.
             * <P>Type: String</P>
             */
            public static final String IM_HANDLE = "im_handle";

            /**
             * The extra field for the IM protocol
             */
            public static final String IM_PROTOCOL = "im_protocol";

            /**
             * The extra field for the IM isprimary flag.
             * <P>Type: boolean</P>
             */
            public static final String IM_ISPRIMARY = "im_isprimary";

            /**
             * The extra field that allows the client to supply multiple rows of
             * arbitrary data for a single contact created using the {@link Intent#ACTION_INSERT}
             * or edited using {@link Intent#ACTION_EDIT}. It is an ArrayList of
             * {@link ContentValues}, one per data row. Supplying this extra is
             * similar to inserting multiple rows into the {@link Data} table,
             * except the user gets a chance to see and edit them before saving.
             * Each ContentValues object must have a value for {@link Data#MIMETYPE}.
             * If supplied values are not visible in the editor UI, they will be
             * dropped.  Duplicate data will dropped.  Some fields
             * like {@link CommonDataKinds.Email#TYPE Email.TYPE} may be automatically
             * adjusted to comply with the constraints of the specific account type.
             * For example, an Exchange contact can only have one phone numbers of type Home,
             * so the contact editor may choose a different type for this phone number to
             * avoid dropping the valueable part of the row, which is the phone number.
             * <p>
             * Example:
             * <pre>
             *  ArrayList&lt;ContentValues&gt; data = new ArrayList&lt;ContentValues&gt;();
             *
             *  ContentValues row1 = new ContentValues();
             *  row1.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
             *  row1.put(Organization.COMPANY, "Android");
             *  data.add(row1);
             *
             *  ContentValues row2 = new ContentValues();
             *  row2.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
             *  row2.put(Email.TYPE, Email.TYPE_CUSTOM);
             *  row2.put(Email.LABEL, "Green Bot");
             *  row2.put(Email.ADDRESS, "android@android.com");
             *  data.add(row2);
             *
             *  Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
             *  intent.putParcelableArrayListExtra(Insert.DATA, data);
             *
             *  startActivity(intent);
             * </pre>
             */
            public static final String DATA = "data";

            /**
             * Used to specify the account in which to create the new contact.
             * <p>
             * If this value is not provided, the user is presented with a disambiguation
             * dialog to chose an account
             * <p>
             * Type: {@link Account}
             *
             * @hide
             */
            public static final String ACCOUNT = "com.silentcircle.silentcontacts.extra.ACCOUNT";

            /**
             * Used to specify the data set within the account in which to create the
             * new contact.
             * <p>
             * This value is optional - if it is not specified, the contact will be
             * created in the base account, with no data set.
             * <p>
             * Type: String
             *
             * @hide
             */
            public static final String DATA_SET = "com.silentcircle.silentcontacts.extra.DATA_SET";
        }
    }

    /**
     * Helper methods to display QuickContact dialogs that allow users to pivot on
     * a specific {@link Contacts} entry.
     */
    public static final class QuickContact {
        /**
         * Action used to trigger person pivot dialog.
         * @hide
         */
        public static final String ACTION_QUICK_CONTACT = "com.silentcircle.silentcontacts.action.QUICK_CONTACT";

        /**
         * Extra used to specify pivot dialog location in screen coordinates.
         * @deprecated Use {@link Intent#setSourceBounds(Rect)} instead.
         * @hide
         */
        @Deprecated
        public static final String EXTRA_TARGET_RECT = "target_rect";

        /**
         * Extra used to specify size of pivot dialog.
         * @hide
         */
        public static final String EXTRA_MODE = "mode";

        /**
         * Extra used to indicate a list of specific MIME-types to exclude and
         * not display. Stored as a {@link String} array.
         * @hide
         */
        public static final String EXTRA_EXCLUDE_MIMES = "exclude_mimes";

        /**
         * Small QuickContact mode, usually presented with minimal actions.
         */
        public static final int MODE_SMALL = 1;

        /**
         * Medium QuickContact mode, includes actions and light summary describing
         * the {@link Contacts} entry being shown. This may include social
         * status and presence details.
         */
        public static final int MODE_MEDIUM = 2;

        /**
         * Large QuickContact mode, includes actions and larger, card-like summary
         * of the {@link Contacts} entry being shown. This may include detailed
         * information, such as a photo.
         */
        public static final int MODE_LARGE = 3;

        /**
         * Constructs the QuickContacts intent with a view's rect.
         * @hide
         */
        public static Intent composeQuickContactsIntent(Context context, View target, Uri lookupUri,
                int mode, String[] excludeMimes) {
            // Find location and bounds of target view, adjusting based on the
            // assumed local density.
            final float appScale = 1.0f; // TODO - check this: context.getResources().getCompatibilityInfo().applicationScale;
            final int[] pos = new int[2];
            target.getLocationOnScreen(pos);

            final Rect rect = new Rect();
            rect.left = (int) (pos[0] * appScale + 0.5f);
            rect.top = (int) (pos[1] * appScale + 0.5f);
            rect.right = (int) ((pos[0] + target.getWidth()) * appScale + 0.5f);
            rect.bottom = (int) ((pos[1] + target.getHeight()) * appScale + 0.5f);

            return composeQuickContactsIntent(context, rect, lookupUri, mode, excludeMimes);
        }

        /**
         * Constructs the QuickContacts intent.
         * @hide
         */
        public static Intent composeQuickContactsIntent(Context context, Rect target,
                Uri lookupUri, int mode, String[] excludeMimes) {
            // When launching from an Activiy, we don't want to start a new task, but otherwise
            // we *must* start a new task.  (Otherwise startActivity() would crash.)
            Context actualContext = context;
            while ((actualContext instanceof ContextWrapper)
                    && !(actualContext instanceof Activity)) {
                actualContext = ((ContextWrapper) actualContext).getBaseContext();
            }
            final int intentFlags = (actualContext instanceof Activity)
                    ? 0
                    : Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP;

            // Launch pivot dialog through intent for now
            final Intent intent = new Intent(ACTION_QUICK_CONTACT).addFlags(intentFlags);

            intent.setData(lookupUri);
            intent.setSourceBounds(target);
            intent.putExtra(EXTRA_MODE, mode);
            intent.putExtra(EXTRA_EXCLUDE_MIMES, excludeMimes);
            return intent;
        }

        /**
         * Trigger a dialog that lists the various methods of interacting with
         * the requested {@link Contacts} entry. This may be based on available
         * {@link ContactsContract.Data} rows under that contact, and may also
         * include social status and presence details.
         *
         * @param context The parent {@link Context} that may be used as the
         *            parent for this dialog.
         * @param target Specific {@link View} from your layout that this dialog
         *            should be centered around. In particular, if the dialog
         *            has a "callout" arrow, it will be pointed and centered
         *            around this {@link View}.
         * @param lookupUri A {@link ContactsContract.Contacts#CONTENT_LOOKUP_URI} style
         *            {@link Uri} that describes a specific contact to feature
         *            in this dialog.
         * @param mode Any of {@link #MODE_SMALL}, {@link #MODE_MEDIUM}, or
         *            {@link #MODE_LARGE}, indicating the desired dialog size,
         *            when supported.
         * @param excludeMimes Optional list of {@link Data#MIMETYPE} MIME-types
         *            to exclude when showing this dialog. For example, when
         *            already viewing the contact details card, this can be used
         *            to omit the details entry from the dialog.
         */
        public static void showQuickContact(Context context, View target, Uri lookupUri, int mode,
                String[] excludeMimes) {
            // Trigger with obtained rectangle
            Intent intent = composeQuickContactsIntent(context, target, lookupUri, mode,
                    excludeMimes);
            context.startActivity(intent);
        }

        /**
         * Trigger a dialog that lists the various methods of interacting with
         * the requested {@link Contacts} entry. This may be based on available
         * {@link ContactsContract.Data} rows under that contact, and may also
         * include social status and presence details.
         *
         * @param context The parent {@link Context} that may be used as the
         *            parent for this dialog.
         * @param target Specific {@link Rect} that this dialog should be
         *            centered around, in screen coordinates. In particular, if
         *            the dialog has a "callout" arrow, it will be pointed and
         *            centered around this {@link Rect}. If you are running at a
         *            non-native density, you need to manually adjust using
         *            {@link DisplayMetrics#density} before calling.
         * @param lookupUri A
         *            {@link ContactsContract.Contacts#CONTENT_LOOKUP_URI} style
         *            {@link Uri} that describes a specific contact to feature
         *            in this dialog.
         * @param mode Any of {@link #MODE_SMALL}, {@link #MODE_MEDIUM}, or
         *            {@link #MODE_LARGE}, indicating the desired dialog size,
         *            when supported.
         * @param excludeMimes Optional list of {@link Data#MIMETYPE} MIME-types
         *            to exclude when showing this dialog. For example, when
         *            already viewing the contact details card, this can be used
         *            to omit the details entry from the dialog.
         */
        public static void showQuickContact(Context context, Rect target, Uri lookupUri, int mode,
                String[] excludeMimes) {
            Intent intent = composeQuickContactsIntent(context, target, lookupUri, mode,
                    excludeMimes);
            context.startActivity(intent);
        }
    }

    /**
     * <p>
     * Contact-specific information about whether or not a contact has been pinned by the user
     * at a particular position within the system contact application's user interface.
     * </p>
     *
     * <p>
     * This pinning information can be used by individual applications to customize how
     * they order particular pinned contacts. For example, a Dialer application could
     * use pinned information to order user-pinned contacts in a top row of favorites.
     * </p>
     *
     * <p>
     * It is possible for two or more contacts to occupy the same pinned position (due
     * to aggregation and sync), so this pinning information should be used on a best-effort
     * basis to order contacts in-application rather than an absolute guide on where a contact
     * should be positioned. Contacts returned by the ContactsProvider will not be ordered based
     * on this information, so it is up to the client application to reorder these contacts within
     * their own UI adhering to (or ignoring as appropriate) information stored in the pinned
     * column.
     * </p>
     *
     * <p>
     * By default, unpinned contacts will have a pinned position of
     * {@link PinnedPositions#UNPINNED}. Client-provided pinned positions can be positive
     * integers that are greater than 1.
     * </p>
     */
    public static final class PinnedPositions {
        /**
         * The method to invoke in order to undemote a formerly demoted contact. The contact id of
         * the contact must be provided as an argument. If the contact was not previously demoted,
         * nothing will be done.
         * @hide
         */
        public static final String UNDEMOTE_METHOD = "undemote";

        /**
         * Undemotes a formerly demoted contact. If the contact was not previously demoted, nothing
         * will be done.
         *
         * @param contentResolver to perform the undemote operation on.
         * @param contactId the id of the contact to undemote.
         */
        public static void undemote(ContentResolver contentResolver, long contactId) {
            contentResolver.call(ScContactsContract.AUTHORITY_URI, PinnedPositions.UNDEMOTE_METHOD,
                    String.valueOf(contactId), null);
        }

        /**
         * Pins a contact at a provided position, or unpins a contact.
         *
         * @param contentResolver to perform the pinning operation on.
         * @param pinnedPosition the position to pin the contact at. To unpin a contact, use
         *         {@link PinnedPositions#UNPINNED}.
         */
        public static void pin(ContentResolver contentResolver, long contactId, int pinnedPosition) {
            final Uri uri = Uri.withAppendedPath(RawContacts.CONTENT_URI, String.valueOf(contactId));
            final ContentValues values = new ContentValues();
            values.put(RawContacts.PINNED, pinnedPosition);
            contentResolver.update(uri, values, null, null);
        }

        /**
         * Default value for the pinned position of an unpinned contact.
         */
        public static final int UNPINNED = 0;

        /**
         * Value of pinned position for a contact that a user has indicated should be considered
         * of the lowest priority. It is up to the client application to determine how to present
         * such a contact - for example all the way at the bottom of a contact list, or simply
         * just hidden from view.
         */
        public static final int DEMOTED = -1;
    }
}
