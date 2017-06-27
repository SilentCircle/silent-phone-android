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

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Intents.Insert;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;

import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ContactAdder;
import com.silentcircle.silentphone2.activities.DialerActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ContactsUtils {
    private static final String TAG = "ContactsUtils";
    private static final String WAIT_SYMBOL_AS_STRING = String.valueOf(PhoneNumberUtils.WAIT);

    public static final String PARAM_TEXT = "Text";
    public static final String PARAM_ASSERTED_NAME = "AssertedName";
    public static final String PARAM_ALIAS = "Alias";
    public static final String PARAM_PHOTO_URL = "PhotoUrl";

    private static int sThumbnailSize = -1;

    /**
     * Test if the given {@link CharSequence} contains any graphic characters,
     * first checking {@link android.text.TextUtils#isEmpty(CharSequence)} to handle null.
     */
    public static boolean isGraphic(CharSequence str) {
        return !TextUtils.isEmpty(str) && TextUtils.isGraphic(str);
    }

    /**
     * Returns true if two objects are considered equal.  Two null references are equal here.
     */
    public static boolean areObjectsEqual(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * Returns true if two data with mimetypes which represent values in contact entries are
     * considered equal for collapsing in the GUI. For caller-id, use
     * {@link android.telephony.PhoneNumberUtils#compare(android.content.Context, String, String)} instead
     */
    public static final boolean shouldCollapse(CharSequence mimetype1, CharSequence data1,
            CharSequence mimetype2, CharSequence data2) {
        // different mimetypes? don't collapse
        if (!TextUtils.equals(mimetype1, mimetype2)) return false;

        // exact same string? good, bail out early
        if (TextUtils.equals(data1, data2)) return true;

        // so if either is null, these two must be different
        if (data1 == null || data2 == null) return false;

        // if this is not about phone numbers, we know this is not a match (of course, some
        // mimetypes could have more sophisticated matching is the future, e.g. addresses)
        if (!TextUtils.equals(Phone.CONTENT_ITEM_TYPE, mimetype1)) return false;

        return shouldCollapsePhoneNumbers(data1.toString(), data2.toString());
    }

    private static boolean shouldCollapsePhoneNumbers(
            String number1WithLetters, String number2WithLetters) {
        final String number1 = PhoneNumberUtils.convertKeypadLettersToDigits(number1WithLetters);
        final String number2 = PhoneNumberUtils.convertKeypadLettersToDigits(number2WithLetters);

        int index1 = 0;
        int index2 = 0;
        for (;;) {
            // Skip formatting characters.
            while (index1 < number1.length() &&
                    !PhoneNumberUtils.isNonSeparator(number1.charAt(index1))) {
                index1++;
            }
            while (index2 < number2.length() &&
                    !PhoneNumberUtils.isNonSeparator(number2.charAt(index2))) {
                index2++;
            }
            // If both have finished, match.  If only one has finished, not match.
            final boolean number1End = (index1 == number1.length());
            final boolean number2End = (index2 == number2.length());
            if (number1End) {
                return number2End;
            }
            if (number2End)
                return false;

            // If the non-formatting characters are different, not match.
            if (number1.charAt(index1) != number2.charAt(index2)) return false;

            // Go to the next characters.
            index1++;
            index2++;
        }
    }

    /**
     * Returns true if two {@link android.content.Intent}s are both null, or have the same action.
     */
    public static final boolean areIntentActionEqual(Intent a, Intent b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return TextUtils.equals(a.getAction(), b.getAction());
    }

    /**
     * @return The ISO 3166-1 two letters country code of the country the user
     *         is in.
     */
    public static final String getCurrentCountryIso(Context context) {
        String locale = context.getResources().getConfiguration().locale.getCountry();
        return locale;
    }

// TODO    public static boolean areContactWritableAccountsAvailable(Context context) {
//        final List<AccountWithDataSet> accounts =
//                AccountTypeManager.getInstance(context).getAccounts(true /* writeable */);
//        return !accounts.isEmpty();
//    }
//
//    public static boolean areGroupWritableAccountsAvailable(Context context) {
//        final List<AccountWithDataSet> accounts =
//                AccountTypeManager.getInstance(context).getGroupWritableAccounts();
//        return !accounts.isEmpty();
//    }

    /**
     * Returns the intent to launch for the given invitable account type and contact lookup URI.
     * This will return null if the account type is not invitable (i.e. there is no
     * {@link com.silentcircle.contacts.model.account.AccountType#getInviteContactActivityClassName()} or
     * {@link com.silentcircle.contacts.model.account.AccountType#syncAdapterPackageName}).
     */

    /**
     * Return Uri with an appropriate scheme, accepting Voicemail, SIP, and usual phone call
     * numbers.
     */
    public static Uri getCallUri(String number) {
        if (PhoneNumberHelper.isUriNumber(number)) {
             return Uri.fromParts(Constants.SCHEME_SIP, number, null);
        }
        return Uri.fromParts(Constants.SCHEME_TEL, number, null);
     }

    public static Uri getMessagingUri(String address) {
        return new Uri.Builder().scheme(Constants.SCHEME_IMTO).authority("silentcircle").
                appendPath(address).build();
    }

    public static Intent getAddNumberToContactIntent(Context ctx, CharSequence text, String assertedName, String alias) {
        final Intent intent = new Intent(ctx, ContactAdder.class);
        intent.putExtra(PARAM_ASSERTED_NAME, assertedName);
        intent.putExtra(PARAM_ALIAS, alias);
        intent.putExtra(PARAM_TEXT, text);

        return intent;
    }

    public static Intent getAddScToContactIntent(Context context, String uuid, String alias, String displayName) {
        return getAddNumberToContactIntent(context, displayName, "sip:" + uuid + context.getString(R.string.sc_sip_domain_0), alias);
    }

    public static Intent getAddScToContactIntent(Context context, String uuid, String alias, String displayName,
            String photoUrl) {
        Intent intent = getAddScToContactIntent(context, uuid, alias, displayName);
        intent.putExtra(PARAM_PHOTO_URL, photoUrl);
        return intent;
    }

    public static Intent getAddNumberToContactIntent(CharSequence text) {
        return getAddToContactIntent(null /* name */, text /* phoneNumber */,
                -1 /* phoneNumberType */);
    }

    public static Intent getAddToContactIntent(CharSequence name, CharSequence phoneNumber,
                                               int phoneNumberType) {
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Insert.PHONE, phoneNumber);
        // Only include the name and phone type extras if they are specified (the method
        // getAddNumberToContactIntent does not use them).
        if (name != null) {
            intent.putExtra(Insert.NAME, name);
        }
        if (phoneNumberType != -1) {
            intent.putExtra(Insert.PHONE_TYPE, phoneNumberType);
        }
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        return intent;
    }

    /**
     * Get the EDIT_BEFORE_CALL action string.
     *
     * @return the correct action string.
     */
    public static String getEditBeforeCallAction() {
        return DialerActivity.SILENT_EDIT_BEFORE_CALL_ACTION;
    }

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallIntent(String number) {
        return getCallIntent(number, null);
    }

    public static Intent getMessagingIntent(String address, Context context) {
        return getMessagingIntent(address, null, context);
    }

    /**
     * Return an Intent for making a phone call. A given Uri will be used as is (without any
     * sanity check).
     */
    public static Intent getCallIntent(Uri uri) {
        return getCallIntent(uri, null);
    }

    /**
     * A variant of {@link #getCallIntent(String)} but also accept a call origin. For more
     * information about call origin, see comments in Phone package (PhoneApp).
     */
    private static Intent getCallIntent(String number, String callOrigin) {
        return getCallIntent(getCallUri(number), callOrigin);
    }

    /**
     * A variant of {@link #getCallIntent(android.net.Uri)} but also accept a call origin. For more
     * information about call origin, see comments in Phone package (PhoneApp).
     */
    public static Intent getCallIntent(Uri uri, String callOrigin) {
        final Intent intent = new Intent(DialerActivity.SILENT_CALL_ACTION, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static Intent getMessagingIntent(String address, String callOrigin, Context context) {
        return getMessagingIntent(getMessagingUri(address), callOrigin, context);
    }

    public static Intent getMessagingIntent(Uri uri, String callOrigin, Context context) {
        return new Intent(Intent.ACTION_SENDTO, uri, context, ConversationActivity.class);
    }

    // Supports either LOOKUP_KEY or display_name (for temporary contacts)
    public static String getFlexibleLookupKey(Uri lookupUri) {
        if(lookupUri == null) {
            return null;
        }

        String lookup = null;
        String lookupKey = null;

        if(lookupUri != null) {
            List<String> segments = lookupUri.getPathSegments();

            if(segments.size() > 2) {
                lookup = segments.get(1);
                lookupKey = segments.get(2);

                if (!TextUtils.isEmpty(lookupKey) && !TextUtils.isEmpty(lookup)
                        && lookup.equals(ContactsContract.Contacts.LOOKUP_KEY)) {
                    if (lookupKey.equals("encoded")) {
                        // Temporary contact
                        // content://com.android.contacts/contacts/lookup/encoded?directory=<DIRECTORY_ID>#{"display_name":"<DISPLAY_NAME>" ...
                        // lookupKey = DISPLAY_NAME
                        String fragmentString = lookupUri.getFragment();
                        try {
                            JSONObject fragmentJson = new JSONObject(fragmentString);

                            if(fragmentJson.has(ContactsContract.Contacts.DISPLAY_NAME)) {
                                String displayName = fragmentJson.getString(ContactsContract.Contacts.DISPLAY_NAME);

                                lookupKey = displayName;
                            }
                        } catch (JSONException exception) {
                            lookupKey = null;
                        }
                    } else {
                        // Real contact
                        // content://com.android.contacts/contacts/lookup/<LOOKUP_KEY>/<CONTACT_ID>
                        // lookupKey = LOOKUP_KEY
                    }
                } else {
                    lookupKey = null;
                }
            }
        }

        return lookupKey;
    }

    /**
     * Returns the {@link android.graphics.Rect} with left, top, right, and bottom coordinates
     * that are equivalent to the given {@link android.view.View}'s bounds. This is equivalent to how the
     * target {@link android.graphics.Rect} is calculated in {@ link QuickContact#showQuickContact}.
     */
    public static Rect getTargetRectFromView(Context context, View view) {
        final float appScale = 1.0f;    // context.getResources().getCompatibilityInfo().applicationScale;
        final int[] pos = new int[2];
        view.getLocationOnScreen(pos);

        final Rect rect = new Rect();
        rect.left = (int) (pos[0] * appScale + 0.5f);
        rect.top = (int) (pos[1] * appScale + 0.5f);
        rect.right = (int) ((pos[0] + view.getWidth()) * appScale + 0.5f);
        rect.bottom = (int) ((pos[1] + view.getHeight()) * appScale + 0.5f);
        return rect;
    }
}
