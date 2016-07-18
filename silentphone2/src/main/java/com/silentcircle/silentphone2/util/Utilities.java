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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;
import com.silentcircle.silentphone2.services.TiviPhoneService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class has static functions only, mainly used for convenience
 * 
 * @author werner
 * 
 */
public class Utilities {
    private static final String TAG = "Utilities";

    private static final String THEME_PREF = "sp_theme";
    private static int mSelectedTheme;

    /** Speaker state, persisting between various speaker events */
    private static boolean sIsSpeakerEnabled = false;

    private static Drawable mDefaultAvatar;
    public static int mDrawerShadowId;

    public static long get_time_ms() {
        return System.currentTimeMillis();
    }

    static public void Sleep(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException ignored) {}
    }

    /**
     * Set the caller image if available
     * 
     * @param call
     *            the call information
     */
    static public void setCallerImage(CallState call, ImageView iw) {
        if (call == null)
            return;

        if (call.image != null) {
            iw.setImageBitmap(call.image);
        }
        else { // No caller image, set dummy image
            if (mDefaultAvatar != null)
                iw.setImageDrawable(mDefaultAvatar);
            else
                iw.setImageResource(R.drawable.ic_contact_picture_holo_dark);
        }
    }

    static public void turnOnSpeaker(Context context, boolean flag, boolean store) {
        speakerStatic(context, flag);
        // record the speaker-enable value
        if (store) {
            sIsSpeakerEnabled = flag;
        }
    }
    
    /**
     * Restore the speaker mode, called after a wired headset disconnect
     * event.
     */
    static public void restoreSpeakerMode(Context context) {
        // change the mode if needed.
        if (isSpeakerOn(context) != sIsSpeakerEnabled) {
            turnOnSpeaker(context, sIsSpeakerEnabled, false);
        }
    }

    static public boolean isSpeakerOn(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isSpeakerphoneOn();
    }

    /**
     * Static function to handle speaker switching and audio manager.
     * 
     * This function takes care of some specific device handling. 
     */
    private static void speakerStatic(Context ctx, boolean bOn) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        DeviceHandling.setAecMode(ctx, bOn);
        am.setSpeakerphoneOn(bOn);
    }

    /*
     * Switch audio between normal and in-call mode.
     *
     * This is a static wrapper function to simplify thread/context handling.
     */
    public static void audioMode(Context ctx, boolean mode) {
        if (!DeviceHandling.switchAudioMode())
            return;

        AudioManager am = (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setMode(mode ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
        if (mode) {
            am.setMicrophoneMute(false);
        }
    }

    /**
     * Checks if a number/name contains SIP related parts and removes them.
     *
     * @param number to check and reformat
     * @return pure number string
     */
    public static String removeSipParts(String number) {

        if (TextUtils.isEmpty(number))
            return number;

        int idx = -1;
        String n = number;
        if (n.startsWith("sip:") || n.startsWith("sips:" )) {
            idx = n.indexOf(':');
            n = n.substring(idx + 1);
        }
        return getUsernameFromUriNumber(n);
    }

    /**
     * Check if a string starts with a schema, removes it and selectively removes domain names.
     *
     * The functions always removes a scheme identifier. After removing this it calls
     * {@link #getUsernameFromUriNumber(String)} to selectively remove the domain part.
     *
     * @param number to check and reformat
     * @return pure number string or number with domain name.
     */
    public static String removeUriPartsSelective(final String number) {

        if (TextUtils.isEmpty(number))
            return number;

        String n = number;
        int idx = n.indexOf(':');
        if (idx >= 0) {
            n = n.substring(idx + 1);
        }
        return getUsernameFromUriNumberSelective(n);
    }

    /**
     * Removes the domain part of the number/name.
     *
     * @param number SIP address of the form "username@domainname"
     *               (or the URI-escaped equivalent "username%40domainname")
     * @return the "username" part of the specified SIP address,
     *         i.e. the part before the "@" character (or "%40").
     *
     * @see isUriNumber
     */
    public static String getUsernameFromUriNumber(String number) {
        // The delimiter between username and domain name can be
        // either "@" or "%40" (the URI-escaped equivalent.)
        int delimiterIndex = number.indexOf('@');
        if (delimiterIndex < 0) {
            delimiterIndex = number.indexOf("%40");
        }
        if (delimiterIndex < 0) {
            return number;
        }
        return number.substring(0, delimiterIndex);
    }

    /**
     * Removes the domain part of the number/name if the domain matches a specified domain
     *
     * @param number SIP address of the form "username@domainname"
     *               (or the URI-escaped equivalent "username%40domainname")
     *
     * @return the "username" part of the specified address,
     *         i.e. the part before the "@" character (or "%40") if the domain
     *         part matches a domain part which should be removed
     * @see isUriNumber
     */
    public static String getUsernameFromUriNumberSelective(String number) {
        if (DialerActivity.mDomainsToRemove == null)
            return number;

        // The delimiter between username and domain name can be
        // either "@" or "%40" (the URI-escaped equivalent.)
        int delimiterIndex = number.indexOf('@');
        if (delimiterIndex < 0) {
            delimiterIndex = number.indexOf("%40");
        }
        if (delimiterIndex < 0) {
            return number;
        }
        final String domain = number.substring(delimiterIndex);
        if (!isAnyOf(domain, DialerActivity.mDomainsToRemove))
            return number;

        return number.substring(0, delimiterIndex);
    }
    /**
     * Determines if the specified number is actually a URI  (i.e. a SIP address).
     *
     * Based on whether or not the number contains an "@" character or the escaped "%40" string.
     *
     * @param number the number to check
     * @return true if number contains @
     */
    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    /**
     * Read theme from preferences and set it.
     *
     * ******* Currently mainly commented out - maybe we use selectable themes once again *******
     *
     * If no theme was set then read the theme name from preferences, check for known
     * names and set the theme. Activities shall call this functions during
     * {@link android.app.Activity#onCreate(android.os.Bundle)} before they set or initialize
     * UI or views.
     *
     * @param activity the Activity which calls
     */
    public static void setTheme(Activity activity) {
        if (mSelectedTheme == 0) {
            // Change here if standard (startup) theme changes
//            String theme = getSelectedTheme(activity);

//            if (theme.equals(activity.getString(R.string.theme_orange)))
//                mSelectedTheme = R.style.SilentPhoneThemeOrange;
//            else if (theme.equals(activity.getString(R.string.theme_white)))
//                mSelectedTheme = R.style.SilentPhoneThemeWhite;
//            else if (theme.equals(activity.getString(R.string.theme_dusk)))
//                mSelectedTheme = R.style.SilentPhoneThemeDusk;
//            else if (theme.equals(activity.getString(R.string.theme_black)))
//                mSelectedTheme = R.style.SilentPhoneThemeBlack;
//            else {
                mSelectedTheme = R.style.SilentPhoneThemeBlack;
//                Log.w(TAG, "Cannot change theme, unknown: " + theme);
//            }
        }
        activity.setTheme(mSelectedTheme);
        setStyledAttributes(activity);
    }

    /**
     * Return the currently selected theme name or the default theme.
     *
     * @param activity the Activity which calls
     * @return the theme name
     */
    public static String getSelectedTheme(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        // Change here if standard (startup) theme changes
        return prefs.getString(THEME_PREF, activity.getString(R.string.theme_black));
    }

    public static boolean storeThemeSelection(Activity activity, String selectedTheme, int themeId) {
        if (selectedTheme == null || themeId == mSelectedTheme)
            return false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        prefs.edit().putString(THEME_PREF, selectedTheme).apply();

        mSelectedTheme = 0;                         // force an update now
        setTheme(activity);
        activity.recreate();
        setStyledAttributes(activity);
        return true;
    }

    public static void setStyledAttributes(Context context) {
        mDefaultAvatar = ContextCompat.getDrawable(context, R.drawable.ic_contact_picture_holo_dark);
        mDrawerShadowId = R.drawable.drawer_shadow_dark;

        final Resources.Theme theme = context.getTheme();
        final TypedArray a = theme != null ? theme.obtainStyledAttributes(R.styleable.SpaStyle) : null;
        if (a != null) {
            mDefaultAvatar = a.getDrawable(R.styleable.SpaStyle_sp_ic_contact_picture);
            mDrawerShadowId = a.getResourceId(R.styleable.SpaStyle_sp_drawer_shadow, R.drawable.drawer_shadow_dark);
            a.recycle();
        }
    }

    public static String formatSas(String sas) {
        sas = sas.trim();
        if (sas.length() > 4)
            return sas;
        return String.valueOf(sas.charAt(0)) + ' ' + sas.charAt(1) + ' ' + sas.charAt(2) + ' ' + sas.charAt(3);
    }

    /**
     * Translate ZRTP state strings.
     *
     * @param ctx Context to get resources
     * @param call the call state of this call, contains the string to translate
     * @return translated string if available, original string otherwise
     */
    public static String translateZrtpStateMsg(final Context ctx, final CallState call) {
        if ("Looking for peer".equals(call.bufSecureMsg.toString())) {
            return ctx.getString(R.string.secstate_looking);
        }
        if ("Going secure".equals(call.bufSecureMsg.toString())) {
            return ctx.getString(R.string.secstate_going_sec);
        }
        if ("Not SECURE no crypto enabled".equals(call.bufSecureMsg.toString())) {
            return ctx.getString(R.string.secstate_sec_disabled);
        }
        return (call.bufSecureMsg.toString());
    }

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent px equivalent to dp depending on device density
     */
    @SuppressWarnings("unused")
    public static float convertDpToPixel(float dp, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return dp * (metrics.densityDpi / 160f);
    }

    /**
     * This method converts device specific pixels to density independent pixels.
     *
     * @param px A value in px (pixels) unit. Which we need to convert into db
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent dp equivalent to px value
     */
    @SuppressWarnings("unused")
    public static float convertPixelsToDp(float px, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return px / (metrics.densityDpi / 160f);
    }

    @SuppressLint("DefaultLocale")
    public static String getDurationString(CallState call) {
        if (call == null || call.uiStartTime == 0) {
            return "00:00";
        }
        long dur = (System.currentTimeMillis() - call.uiStartTime + 500) / 1000;
        long min = dur / 60;
        long sec = dur - min * 60;

        return String.format("%02d:%02d", min, sec);
    }

    public static void resizeText(TextView textView, int originalTextSize, int minTextSize) {
        final Paint paint = textView.getPaint();
        final int width = textView.getWidth();
        if (width == 0) return;
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize);
        float ratio = width / paint.measureText(textView.getText().toString());
        if (ratio <= 1.0f) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    Math.max(minTextSize, originalTextSize * ratio));
        }
    }

    /**
     * Convert dialpad characters to key codes.
     *
     * Takes a digit or one of the special characters of a dialpad and converts
     * to a key code.
     *
     * @param dialChar input character, digit or +, *, #
     * @return the keycode or KEYCODE_UNKNOWN in case of wrong input.
     */
    public static char dialCharToKey(char dialChar) {
        switch (dialChar) {
            case '1':
                return KeyEvent.KEYCODE_1;
            case '2':
                return KeyEvent.KEYCODE_2;
            case '3':
                return KeyEvent.KEYCODE_3;
            case '4':
                return KeyEvent.KEYCODE_4;
            case '5':
                return KeyEvent.KEYCODE_5;
            case '6':
                return KeyEvent.KEYCODE_6;
            case '7':
                return KeyEvent.KEYCODE_7;
            case '8':
                return KeyEvent.KEYCODE_8;
            case '9':
                return KeyEvent.KEYCODE_9;
            case '0':
                return KeyEvent.KEYCODE_0;
            case '+':
                return KeyEvent.KEYCODE_PLUS;
            case '#':
                return KeyEvent.KEYCODE_STAR;
            case '*':
                return KeyEvent.KEYCODE_POUND;
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    /**
     * Sends a string of dial codes to a EditText field using the field's onKeyDown function.
     *
     * @param in the dial codes,  digits or +, *, #
     * @param field the EditText field
     */
    public static void sendString(String in, EditText field) {
        char carr[] = in.toCharArray();
        for (char c : carr) {
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, Utilities.dialCharToKey(c));
            field.onKeyDown(event.getKeyCode(), event);
        }
    }

    /**
     * Check if a device could be a tablet.
     *
     * If the screen size is greater than Configuration.SCREENLAYOUT_SIZE_LARGE assume a tablet
     *
     * @param context Context the get the resources
     * @return true if this could be a tablet
     */
    @SuppressWarnings("unused")
    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    final protected static char[] hexArrayUpper = "0123456789ABCDEF".toCharArray();
    final protected static char[] hexArrayLower = "0123456789abcdef".toCharArray();

    /**
     * Convert a byte array to it printable hex presentation.
     *
     * This function does not truncate zero bytes.
     *
     * @param bytes the input array
     * @return a character array with hex characters
     */
    public static char[] bytesToHexChars(byte[] bytes) {
        return bytesToHexChars(bytes, false);
    }

    public static char[] bytesToHexChars(byte[] bytes, boolean lowerCase) {
        final char[] hexChars = new char[bytes.length * 2];
        final char[] hexArray = lowerCase ? hexArrayLower : hexArrayUpper;
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return hexChars;
    }

    /**
     * Convert a characters array with hex characters to the binary byte array.
     *
     * This functions does not check for any sign bytes and accepts characters
     * '0'-'9', 'a'-'f' and 'A'-'F'
     *
     * @param s the input character array
     * @return the byte array
     */
    @SuppressWarnings("unused")
    public static byte[] hexCharsToBytes(char[] s) {
        byte[] data = new byte[s.length / 2];
        for (int i = 0; i < s.length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s[i], 16) << 4) + Character.digit(s[i+1], 16));
        }
        return data;
    }

    /**
     * Hash a string with MD5 to generate some id data
     *
     * @param input
     * @return
     */
    public static String hashMd5(String input) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        final byte[] hash;
        try {
            hash = md.digest(input.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        return new String(bytesToHexChars(hash, true));
    }

    /**
     * Hash a string with SHA256 to generate some id data
     *
     * @param input
     * @return
     */
    public static String hashSha256(String input) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        final byte[] hash;
        try {
            hash = md.digest(input.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        return new String(bytesToHexChars(hash, true));
    }

    /**
     * Hash an {@link InputStream} to generate some id data
     *
     * @param is  the {@link InputStream}
     * @param algorithm one supported from {@link Security#getProviders()}
     * @return
     */
    public static String hash(InputStream is, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);

            byte[] data = new byte[1024];
            int read;
            while ((read = is.read(data)) != -1) {
                md.update(data, 0, read);
            }

            byte[] hashBytes = md.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            IOUtils.close(is);
        }
    }

    public static boolean isNetworkConnected(Context context){
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * @return True if the application is currently in RTL mode.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean isRtl() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && 
                TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
    }

    public static void setSubtitleColor(final Context ctx, @Nullable final View toolbar) {
        if (toolbar == null) {
            return;
        }
        int registerStatus = TiviPhoneService.getPhoneState();
        TextView tv = (TextView)toolbar.findViewById(R.id.sub_title);
        switch (registerStatus) {
            case 1:             // connecting
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.sc_ng_background_3));
                break;
            case 2:             // online
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.black_green_dark_1));
                break;
            default:            // offline
                tv.setTextColor(ContextCompat.getColor(ctx, R.color.sc_ng_text_red));
        }
    }

    public static boolean isAnyOf(String input, String... possibleValues) {
        if( possibleValues == null ) {
            return false;
        }

        boolean is = false;
        for( int i = 0; !is && i < possibleValues.length; i++ ) {
            String possibleValue = possibleValues[i];
            if( input == null ) {
                if( possibleValue == null ) {
                    is = true;
                    break;
                }
                continue;
            }
            is = possibleValue != null && input.equals( possibleValue );
        }
        return is;
    }

    private static final String PATTERN_USERNAME_STR = "^[a-z][a-z0-9]{1,}$";
    private static final Pattern PATTERN_USERNAME = Pattern.compile(PATTERN_USERNAME_STR);

    /**
     * Check whether given string is syntactically valid user - starts with a letter
     * and is followed by letters and digits, length of the string at least 2 symbols.
     *
     * @param userName String to check for validity.
     * @return true, if passed parameter is a valid user name, false if it is empty or does not
     * match required format.
     */
    public static boolean isValidSipUsername(final String userName) {
        if (TextUtils.isEmpty(userName)) {
            return false;
        }
        Matcher matcher = PATTERN_USERNAME.matcher(userName);
        return matcher.matches();
    }

    public static boolean isValidSipNumber(final String query) {
        return isValidSipUsername(isUriNumber(query) ? removeSipParts(query) : query);
    }

    public static boolean isValidEmail(CharSequence target) {
        return !TextUtils.isEmpty(target) && android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    public static boolean isValidPhoneNumber(String query) {
        if (TextUtils.isEmpty(query)) {
            return false;
        }
        // assume a phone number if it has at least 1 digit
        return PhoneNumberUtil.normalizeDigitsOnly(query).length() > 0;
    }

    /**
     * Return a valid E.164 phone number formatted according E.123.
     *
     * This function uses the country that the user selected for DialAssist or 'ZZ' if the
     * user did not select a country to parse a phone number. If phone number parsing fails
     * then return {@code null}.
     *
     * @param number The phone number to check
     * @return Formatted phone number or {@code null}
     */
    public static String getValidPhoneNumber(final String number) {
        if (TextUtils.isEmpty(number))
            return null;

        FindDialHelper.CountryData country = FindDialHelper.getActiveCountry();
        if (country == null) {
            return null;
        }
        String region = country.shortName;

        // Region names ending in "_" are virtual regions, thus indicates the user has
        // not selected a specific country but a simple dial assist for example.
        region = region.endsWith("_") ? "ZZ" : region.toUpperCase();
        try {
            PhoneNumberUtil pu = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phone = pu.parse(number, region);
            if (pu.isValidNumber(phone))
                return pu.format(phone, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
            else
                return null;
        } catch (NumberParseException e) {
            return null;
        }
    }

    public static String[] splitFields(String field, String separator) {
        if (TextUtils.isEmpty(field))
            return null;
        String parts[] = field.split(separator);
        if (parts.length < 1) {
            return null;
        }
        return parts;
    }
}
