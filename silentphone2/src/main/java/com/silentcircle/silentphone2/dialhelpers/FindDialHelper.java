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

package com.silentcircle.silentphone2.dialhelpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/**
 * Find a Dial helper for a given country or area.
 *
 * Created by werner on 22.06.14.
 */
public class FindDialHelper {

    private static final String TAG = FindDialHelper.class.getSimpleName();

    private static final String PREFERRED_COUNTRY = "preferred_country";
    private static final String SC = "SC";

    private static DialHelper mDialHelper;
    private static CountryData mActiveCountry;
    private static final DialHelper mNullHelper;

    private static String mPartnerId;

    private final static HashMap<String, DialHelper> mHelperList = new HashMap<>(10);
    private final static HashMap<String, PartnerData> mPartnerList = new HashMap<>(10);

    private static boolean mInitialized;

    // The country stuff is public - simplifies access in selector activity
    public final static TreeMap<String, CountryData> mCountryList = new TreeMap<>();
    public static class CountryData {
        public final String fullName;
        public final String shortName;
        public final String countryCode;
        public final String helperName;
        public final String idd;
        public final String national;

        CountryData(String fn, String sn, String cc, String hn, String idd, String national) {
            fullName = fn;
            shortName = sn;
            countryCode = cc;
            helperName = hn;
            this.idd = idd;
            this.national = national;
        }
    }

    private static class PartnerData {
        @SuppressWarnings("unused")
        private final String fullName;
        @SuppressWarnings("unused")
        private final String shortName;
        private final String countryName;
        private final boolean offerHelper;

        PartnerData(String fn, String sn, String cn, boolean offer) {
            fullName = fn;
            shortName = sn;
            countryName = cn;
            offerHelper = offer;
        }
    }

    static {
        mNullHelper = new NullHelper();
        mHelperList.put("nanp", new NanpHelper());
        mHelperList.put("00_0", new Dial_00_0Helper());
        mHelperList.put("00_x", new Dial_00_xHelper());
        mHelperList.put("00_n", new Dial_00_nHelper());
        mHelperList.put("_none_", mNullHelper);
        mHelperList.put("_simple_", new SimpleHelper());

        // Our partners and and if the customers of the partners should see the dial support option
        mPartnerList.put(SC, new PartnerData("Silent Circle", "SC", "!simple_", true));
        mPartnerList.put("KPN", new PartnerData("KPN", "KPN", "!none_", false));

        // The countries and dial plan areas, including some 'virtual' countries (_ appended)
        // The country list (TreeMap) sorts entries by the key and this is the order in the selection
        // list on the UI. That's why the top entries start with a '!' in initialize() to keep these
        // entries on top of the selection list.

        // The country's short name is it's two letter code according to ISO 3166-1. The
        // country list (TreeMap) key _must_ be the same as the country's short name

        // The entries in the initialization are (roughly) sorted in the same order as in the ITU document
        // that describes the dialing procedures T-SP-E.164C-2011-PDF-E

        // Note on the country long names: we tried to use the official country name, using the official
        // language, followed by the English name in parenthesis if the names differ. For arabic countries
        // we used the transcription according to ISO 233 to avoid problems with right-to-left writing.
        // For languages that use/allow left-to-right writing we use the correct character set if available,
        // for example cyrillic, chinese signs, etc.

        // This a a dial plan region, not only a single country. Handle as a virtual country
        mCountryList.put("no_", new CountryData("North America",       "no_",   "+1", "nanp", "011", ""));

        mCountryList.put("af", new CountryData("Afghānestān (Afghanistan)","af",  "+93", "00_0", "00", "0"));
        mCountryList.put("al", new CountryData("Shqipëri (Albania)",       "al", "+355", "00_0", "00", "0"));
//        mCountryList.put("dz", new CountryData("al-Dschazā’ir (Algeria)",  "dz", "+213", "00_0", "00", "0"));
        mCountryList.put("dz", new CountryData("(Algeria) الجزائر",        "dz", "+213", "00_0", "00", "0"));
        mCountryList.put("ad", new CountryData("Andorra",                  "ad", "+376", "00_x", "00", ""));
        mCountryList.put("ao", new CountryData("Angola",                   "ao", "+244", "00_x", "00", ""));

// mCountryList.put("aq", new CountryData("Antarctica", "aq", "+6721", "CHECK_ME", "", ""));

        mCountryList.put("ar", new CountryData("Argentina",                "ar",  "+54", "00_0", "00", "0"));
        mCountryList.put("am", new CountryData("Hayastan (Armenia)",       "am", "+374", "00_0", "00", "0"));
        mCountryList.put("aw", new CountryData("Aruba",                    "aw", "+297", "00_x", "00", ""));
        mCountryList.put("au", new CountryData("Australia",                "au",  "+61", "00_0", "0011", "0"));
        mCountryList.put("at", new CountryData("Österreich (Austria)",     "at",  "+43", "00_0", "00", "0"));
        mCountryList.put("az", new CountryData("Azərbaycan (Azerbaijan)",  "az", "+994", "00_0", "00", "0"));
//        mCountryList.put("bh", new CountryData("al-Baḥrayn (Bahrain)",     "bh", "+973", "00_x", "00", ""));
        mCountryList.put("bh", new CountryData("(Bahrain) البحرين‎",        "bh", "+973", "00_x", "00", ""));
        mCountryList.put("bd", new CountryData("Bāṃlādeś (Bangladesh)",    "bd", "+880", "00_0", "00", "0"));
        mCountryList.put("by", new CountryData("Беларусь (Belarus)",       "by", "+375", "00_0", "810", "8"));
        mCountryList.put("be", new CountryData("België, Belgique (Belgium)","be", "+32", "00_0", "00", "0"));
        mCountryList.put("bz", new CountryData("Belize",                   "bz", "+501", "00_x", "00", ""));
        mCountryList.put("bj", new CountryData("Bénin (Benin)",            "bj", "+229", "00_x", "00", ""));
        mCountryList.put("bt", new CountryData("Druk Yul (Bhutan)",        "bt", "+975", "00_x", "00", ""));
        mCountryList.put("bo", new CountryData("Bolivia",                  "bo", "+591", "00_0", "00", "0"));
        mCountryList.put("an", new CountryData("Bonaire",                  "an", "+599", "00_0", "00", "0"));
        mCountryList.put("ba", new CountryData("BiH/БиХ (Bosnia & Herzegovina)",
                                                                           "ba", "+387", "00_0", "00", "0"));
        mCountryList.put("bw", new CountryData("Botswana",                 "bw", "+267", "00_x", "00", ""));
        mCountryList.put("br", new CountryData("Brasil (Brazil)",          "br",  "+55", "00_0", "00", "0"));
        mCountryList.put("bn", new CountryData("Brunei Darussalam",        "bn", "+673", "00_x", "00", ""));
        mCountryList.put("bg", new CountryData("България (Bulgaria)",      "bg", "+359", "00_0", "00", "0"));
        mCountryList.put("bf", new CountryData("Burkina Faso",             "bf", "+226", "00_x", "00", ""));
        mCountryList.put("bi", new CountryData("Burundi",                  "bi", "+257", "00_x", "00", ""));

// Cambodia has two IDD codes (001 and 007)
// mCountryList.put("kh", new CountryData("Cambodia", "kh", "+855", "CHECK_ME", "", ""));

        mCountryList.put("cm", new CountryData("Cameroon",                 "cm", "+237", "00_x", "00", ""));
        mCountryList.put("cv", new CountryData("Cabo Verde (Cape Verde)",  "cv", "+238", "00_x", "00", ""));
        mCountryList.put("cf", new CountryData("Centrafricaine (Central African Republic)",
                                                                           "cf", "+236", "00_x", "00", ""));
        mCountryList.put("td", new CountryData("Tchad (Chad)",             "td", "+235", "00_x", "00", ""));

// Chile has several IDD codes according to ITU,  IDD: 1YZ0, NDD: 1YZ, where YZ is the long distance operator code
// mCountryList.put("cl", new CountryData("Chile", "cl", "+56", "CHECK_ME", "", ""));

        mCountryList.put("cn", new CountryData("中华人民共和国 (China)",    "cn", "+86", "00_0", "00", "0"));

// mCountryList.put("cx", new CountryData("Christmas Island", "cx", "+61891006", "00_0", "00", "0"));
// mCountryList.put("cc", new CountryData("Cocos Islands", "cc", "+61891010", "00_0", "00", "0"));
// Columbia has several IDD and NDD codes
// mCountryList.put("co", new CountryData("Colombia", "co", "+57", "CHECK_ME", "", ""));

        mCountryList.put("km", new CountryData("Comores (Comoros)",        "km", "+269", "00_x", "00", ""));
        mCountryList.put("cd", new CountryData("Rép. Dém. du Congo (Dem. Rep. Congo)",
                                                                           "cd", "+243", "00_0", "00", "0"));
        mCountryList.put("cg", new CountryData("République du Congo (Congo)",
                                                                           "cg", "+242", "00_x", "00", ""));
        mCountryList.put("ck", new CountryData("Cook Islands",             "ck", "+682", "00_x", "00", ""));
        mCountryList.put("cr", new CountryData("Costa Rica",               "cr", "+506", "00_x", "00", ""));
        mCountryList.put("ci", new CountryData("Cote d'Ivoire",            "ci", "+225", "00_x", "00", ""));
        mCountryList.put("hr", new CountryData("Hrvatska (Croatia)",       "hr", "+385", "00_0", "00", "0"));
        mCountryList.put("ca", new CountryData("Canada",                   "ca",   "+1", "nanp", "011", ""));
        mCountryList.put("cu", new CountryData("Cuba",                     "cu",  "+53", "00_n", "119", "0"));
        mCountryList.put("cy", new CountryData("Κύπρος (Cyprus)",          "cy", "+357", "00_x", "00", ""));
        mCountryList.put("cz", new CountryData("Česko (Czech Republic)",   "cz", "+420", "00_x", "00", ""));
        mCountryList.put("dk", new CountryData("Danmark (Denmark)",        "dk",  "+45", "00_x", "00", ""));
        mCountryList.put("x1", new CountryData("Diego Garcia",             "x1", "+246", "00_x", "00", ""));
        mCountryList.put("dj", new CountryData("Djibouti",                 "dj", "+253", "00_x", "00", ""));
        mCountryList.put("ec", new CountryData("Ecuador",                  "ec", "+593", "00_0", "00", "0"));
//        mCountryList.put("eg", new CountryData("Miṣr (Egypt)‎",             "eg",  "+20", "00_0", "00", "0"));
        mCountryList.put("eg", new CountryData("(Egypt) مصر‎",              "eg",  "+20", "00_0", "00", "0"));
        mCountryList.put("sv", new CountryData("El Salvador",              "sv", "+503", "00_x", "00", ""));
        mCountryList.put("gq", new CountryData("Guinea Ecuatorial (Equatorial Guinea)",
                                                                           "gq", "+240", "00_x", "00", ""));
        mCountryList.put("er", new CountryData("ኤርትራ (Eritrea)",          "er", "+291", "00_0", "00", "0"));
        mCountryList.put("ee", new CountryData("Eesti (Estonia)",          "ee", "+372", "00_x", "00", ""));
        mCountryList.put("et", new CountryData("ኢትዮጵያ (Ethiopia)",        "et", "+251", "00_0", "00", "0"));
        mCountryList.put("fk", new CountryData("Falkland Islands",         "fk", "+500", "00_x", "00", ""));
        mCountryList.put("fo", new CountryData("Føroyar (Faroe Islands)",  "fo", "+298", "00_x", "00", ""));
        mCountryList.put("fj", new CountryData("Viti (Fiji)",              "fj", "+679", "00_x", "00", ""));
        mCountryList.put("fi", new CountryData("Suomi (Finland)",          "fi", "+358", "00_0", "00", "0"));
        mCountryList.put("fr", new CountryData("France",                   "fr",  "+33", "00_0", "00", "0"));
        mCountryList.put("gf", new CountryData("Guyane (French Guiana)",   "gf", "+594", "00_0", "00", "0"));
        mCountryList.put("pf", new CountryData("Polynésie française (French Polynesia)",
                                                                           "pf", "+689", "00_x", "00", ""));
        mCountryList.put("ga", new CountryData("République Gabonaise (Gabon)",
                                                                           "ga", "+241", "00_0", "00", "0"));
        mCountryList.put("gm", new CountryData("Gambia",                   "gm", "+220", "00_x", "00", ""));
        mCountryList.put("ge", new CountryData("საქართველო (Georgia)",     "ge", "+995", "00_0", "00", "0"));
        mCountryList.put("de", new CountryData("Deutschland (Germany)",    "de",  "+49", "00_0", "00", "0"));
        mCountryList.put("gh", new CountryData("Ghana",                    "gh", "+233", "00_0", "00", "0"));
        mCountryList.put("gi", new CountryData("Gibraltar",                "gi", "+350", "00_x", "00", ""));
        mCountryList.put("gr", new CountryData("Ελλάς (Greece)",           "gr",  "+30", "00_0", "00", "0"));
        mCountryList.put("gl", new CountryData("Kalaallit Nunaat (Greenland)", "gl", "+299", "00_x", "00", ""));
        mCountryList.put("gp", new CountryData("Guadeloupe",               "gp", "+590", "00_x", "00", ""));
        mCountryList.put("gt", new CountryData("Guatemala",                "gt", "+502", "00_x", "00", ""));
        mCountryList.put("gn", new CountryData("Guinée (Guinea)",          "gn", "+224", "00_x", "00", ""));
        mCountryList.put("gw", new CountryData("Guiné-Bissau (Guinea-Bissau)", "gw", "+245", "00_x", "00", ""));
        mCountryList.put("gy", new CountryData("Guyana",                   "gy", "+592", "00_x", "001", ""));
        mCountryList.put("ht", new CountryData("Ayiti (Haiti)",            "ht", "+509", "00_x", "00", ""));
        mCountryList.put("hn", new CountryData("Honduras",                 "hn", "+504", "00_x", "00", ""));
        mCountryList.put("hk", new CountryData("香港 (Hong Kong)",         "hk", "+852", "00_x", "001", ""));
        mCountryList.put("hu", new CountryData("Magyarország (Hungary)",   "hu",  "+36", "00_0", "00", "06"));
        mCountryList.put("is", new CountryData("Ísland (Iceland)",         "is", "+354", "00_x", "00", ""));
        mCountryList.put("in", new CountryData("India",                    "in",  "+91", "00_0", "00", "0"));
// Indonesia has 2 IDD codes (001, 008)
// mCountryList.put("id", new CountryData("Indonesia", "id", "+62", "CHECK_ME", "", ""));
//        mCountryList.put("ir", new CountryData("Īrān (Iran)",              "ir",  "+98", "00_0", "00", "0"));
        mCountryList.put("ir", new CountryData("(Iran) ‏ايران",              "ir",  "+98", "00_0", "00", "0"));
//        mCountryList.put("iq", new CountryData("al-ʿIrāq, Îraqê (Iraq)",             "iq", "+964", "00_0", "00", "0"));
        mCountryList.put("iq", new CountryData("(Iraq) كۆماری عێراق‎ / جمهورية العراق",
                                                                           "iq", "+964", "00_0", "00", "0"));
        mCountryList.put("ie", new CountryData("Ireland",                  "ie", "+353", "00_0", "00", "0"));
        mCountryList.put("il", new CountryData("Jisra'el (Israel)",        "il", "+972", "00_0", "00", "0"));
        mCountryList.put("it", new CountryData("Italia (Italy)",           "it",  "+39", "00_x", "00", ""));
        mCountryList.put("jp", new CountryData("日本 (Japan)",             "jp",  "+81", "00_0", "010", "0"));
//        mCountryList.put("jo", new CountryData("al-Urdunn (Jordan)",       "jo", "+962", "00_0", "00", "0"));
        mCountryList.put("jo", new CountryData("(Jordan) الأُرْدُنّ‎",      "jo", "+962", "00_0", "00", "0"));
        mCountryList.put("kz", new CountryData("Қазақстан (Kazakhstan)",   "kz",   "+7", "00_0", "810", "8"));
        mCountryList.put("ke", new CountryData("Kenya",                    "ke", "+254", "00_0", "000", "0"));
        mCountryList.put("ki", new CountryData("Kiribati",                 "ki", "+686", "00_x", "00", ""));
// Korea: mulitple IDD and NDD codes
// mCountryList.put("kr", new CountryData("Korea", "kr", "+82", "CHECK_ME", "", "0"));
//        mCountryList.put("kw", new CountryData("al-Kuwait (Kuwait)",       "kw", "+965", "00_x", "00", ""));
        mCountryList.put("kw", new CountryData("(Kuwait) الكويت‎",          "kw", "+965", "00_x", "00", ""));
        mCountryList.put("kg", new CountryData("Кыргызстан (Kyrgyzstan)",  "kg", "+996", "00_0", "00", "0"));
        mCountryList.put("la", new CountryData("ປະເທດລາວ (Laos)",         "la", "+856", "00_0", "00", "0"));
        mCountryList.put("lv", new CountryData("Latvija (Latvia)",         "lv", "+371", "00_x", "00", ""));
//        mCountryList.put("lb", new CountryData("al-lubnāniyya (Lebanon)",  "lb", "+961", "00_0", "00", "0"));
        mCountryList.put("lb", new CountryData("(Lebanon) اللبنانية‎",      "lb", "+961", "00_0", "00", "0"));
        mCountryList.put("ls", new CountryData("Lesotho",                  "ls", "+266", "00_x", "00", ""));
        mCountryList.put("lr", new CountryData("Liberia",                  "lr", "+231", "00_x", "00", ""));
//        mCountryList.put("ly", new CountryData("Lībiyā (Libya)",           "ly", "+218", "00_0", "00", "0"));
        mCountryList.put("ly", new CountryData("(Libya) ليبيا‎",            "ly", "+218", "00_0", "00", "0"));
        mCountryList.put("li", new CountryData("Liechtenstein",            "li", "+423", "00_x", "00", ""));
        mCountryList.put("lt", new CountryData("Lietuva (Lithuania)",      "lt", "+370", "00_0", "00", "0"));
        mCountryList.put("lu", new CountryData("Luxembourg",               "lu", "+352", "00_x", "00", ""));
        mCountryList.put("mo", new CountryData("Macau",                    "mo", "+853", "00_x", "00", ""));
        mCountryList.put("mk", new CountryData("Македонија (Macedonia)",   "mk", "+389", "00_0", "00", "0"));
        mCountryList.put("mg", new CountryData("Madagasikara (Madagascar)","mg", "+261", "00_x", "00", ""));
        mCountryList.put("mw", new CountryData("Malawi",                   "mw", "+265", "00_x", "00", ""));
        mCountryList.put("my", new CountryData("Malaysia",                 "my",  "+60", "00_0", "00", "0"));
        mCountryList.put("mv", new CountryData("Maldives",                 "mv", "+960", "00_x", "00", ""));
        mCountryList.put("ml", new CountryData("Mali",                     "ml", "+223", "00_x", "00", ""));
        mCountryList.put("mt", new CountryData("Malta",                    "mt", "+356", "00_x", "00", ""));
        mCountryList.put("mh", new CountryData("Marshall Islands",         "mh", "+692", "00_n", "011", "1"));
        mCountryList.put("mq", new CountryData("Martinique",               "mq", "+596", "00_0", "00", "0"));
//        mCountryList.put("mr", new CountryData("al-Mūrītāniyyah (Mauritania)", "mr", "+222", "00_x", "00", ""));
        mCountryList.put("mr", new CountryData("(Mauritania) الموريتانية", "mr", "+222", "00_x", "00", ""));
        mCountryList.put("mu", new CountryData("Mauritius",                "mu", "+230", "00_x", "00", ""));
        mCountryList.put("mx", new CountryData("México (Mexico)",          "mx",  "+52", "00_0", "00", "01"));
        mCountryList.put("fm", new CountryData("Micronesia",               "fm", "+691", "00_n", "011", "1"));
        mCountryList.put("md", new CountryData("Moldova",                  "md", "+373", "00_0", "00", "0"));
        mCountryList.put("mc", new CountryData("Monaco",                   "mc", "+377", "00_x", "00", ""));
        mCountryList.put("mn", new CountryData("Монгол Улс (Mongolia)",    "mn", "+976", "00_0", "00", "0"));
        mCountryList.put("me", new CountryData("Crna Gora (Montenegro)",   "me", "+382", "00_0", "00", "0"));
//        mCountryList.put("ma", new CountryData("al-Maghribīya (Morocco)",  "ma", "+212", "00_0", "00", "0"));
        mCountryList.put("ma", new CountryData("(Morocco) المغرب",         "ma", "+212", "00_0", "00", "0"));
        mCountryList.put("mz", new CountryData("Moçambique (Mozambique)",  "mz", "+258", "00_x", "00", ""));
        mCountryList.put("mm", new CountryData("Myăma (Myanmar)",          "mm",  "+95", "00_0", "00", "0"));
        mCountryList.put("na", new CountryData("Namibia",                  "na", "+264", "00_0", "00", "0"));
        mCountryList.put("nr", new CountryData("Nauru",                    "nr", "+674", "00_x", "00", ""));
        mCountryList.put("np", new CountryData("नेपाल (Nepal)",             "np", "+977", "00_0", "00", "0"));
        mCountryList.put("nl", new CountryData("Nederland (Netherlands)",  "nl",  "+31", "00_0", "00", "0"));
        mCountryList.put("nc", new CountryData("Nouvelle-Calédonie (New Caledonia)",
                                                                           "nc", "+687", "00_x", "00", ""));
        mCountryList.put("nz", new CountryData("New Zealand",              "nz", "+64", "00_0", "00", "0"));
        mCountryList.put("ni", new CountryData("Nicaragua",                "ni", "+505", "00_x", "00", ""));
        mCountryList.put("ne", new CountryData("Niger",                    "ne", "+227", "00_x", "00", ""));
        mCountryList.put("ng", new CountryData("Nigeria",                  "ng", "+234", "00_0", "009", "0"));
        mCountryList.put("nu", new CountryData("Niue",                     "nu", "+683", "00_x", "00", ""));
// CC 6723 not listed in ITU document
// mCountryList.put("nf", new CountryData("Norfolk island", "nf", "+6723", "00_x", "00", ""));
        mCountryList.put("kp", new CountryData("North Korea",              "kp", "+850", "00_0", "00", "0"));
        mCountryList.put("no", new CountryData("Norge (Norway)",           "no",  "+47", "00_x", "00", ""));
//        mCountryList.put("om", new CountryData("Uman (Oman)",              "om", "+968", "00_x", "00", ""));
        mCountryList.put("om", new CountryData("(Oman) سلطنة عمان",        "om", "+968", "00_x", "00", ""));
        mCountryList.put("pk", new CountryData("Pākistān (Pakistan)",      "pk", "+92", "00_0", "00", "0"));
        mCountryList.put("pw", new CountryData("Palau",                    "pw", "+680", "00_x", "011", ""));
        mCountryList.put("pa", new CountryData("Panama",                   "pa", "+507", "00_x", "00", ""));
        mCountryList.put("pg", new CountryData("Papua New Guinea",         "pg", "+675", "00_x", "00", ""));
        mCountryList.put("py", new CountryData("Paraguay",                 "py", "+595", "00_0", "00", "0"));
        mCountryList.put("pe", new CountryData("Peru",                     "pe",  "+51", "00_0", "00", "0"));
        mCountryList.put("ph", new CountryData("Philippines",              "ph",  "+63", "00_0", "00", "0"));
        mCountryList.put("pl", new CountryData("Polska (Poland)",          "pl",  "+48", "00_0", "00", "0"));
        mCountryList.put("pt", new CountryData("Portuguesa (Portugal)",    "pt", "+351", "00_x", "00", ""));
//        mCountryList.put("qa", new CountryData("Qatar",                    "qa", "+974", "00_x", "00", ""));
        mCountryList.put("qa", new CountryData("(Qatar) قطر‎",              "qa", "+974", "00_x", "00", ""));
// French Departments and Territories in the Indian Ocean
        mCountryList.put("re", new CountryData("Réunion (Reunion)",        "re", "+262", "00_x", "00", ""));
        mCountryList.put("ro", new CountryData("România (Romania)",        "ro",  "+40", "00_0", "00", "0"));
        mCountryList.put("ru", new CountryData("Российская Федерация (Russia)", "ru",   "+7", "00_0", "810", "8"));
        mCountryList.put("rw", new CountryData("Rwanda",                   "rw", "+250", "00_x", "00", ""));
        mCountryList.put("sh", new CountryData("St. Helena",               "sh", "+290", "00_x", "00", ""));
        mCountryList.put("pm", new CountryData("Saint-Pierre-et-Miquelon (St. Pierre & Miquelon)",
                                                                           "pm", "+508", "00_x", "00", ""));
        mCountryList.put("ws", new CountryData("Samoa",                    "ws", "+685", "00_x", "00", ""));
        mCountryList.put("sm", new CountryData("San Marino",               "sm", "+378", "00_x", "00", ""));
        mCountryList.put("st", new CountryData("São Tomé e Príncipe (Sao Tome & Principe)",
                                                                           "st", "+239", "00_x", "00", ""));
//        mCountryList.put("sa", new CountryData("al-ʿarabiyya as-saʿūdiyya (Saudi Arabia)",
//                                                                           "sa", "+966", "00_0", "00", "0"));
        mCountryList.put("sa", new CountryData("(Saudi Arabia) ‏المملكة العربية السعودية‎",
                                                                           "sa", "+966", "00_0", "00", "0"));
        mCountryList.put("sn", new CountryData("Sénégal (Senegal)",        "sn", "+221", "00_x", "00", ""));
        mCountryList.put("rs", new CountryData("Србија (Serbia)",          "rs", "+381", "00_0", "00", "0"));
        mCountryList.put("sc", new CountryData("Seychelles",               "sc", "+248", "00_x", "00", ""));
        mCountryList.put("sl", new CountryData("Sierra Leone",             "sl", "+232", "00_0", "00", "0"));
// Singapore has 2 IDD codes (001, 008), no NDD
// mCountryList.put("sg", new CountryData("Singapore", "sg", "+65", "CHECK_ME", "000", ""));
        mCountryList.put("sk", new CountryData("Slovensko (Slovakia)",     "sk", "+421", "00_0", "00", "0"));
        mCountryList.put("si", new CountryData("Slovenija (Slovenia)",     "si", "+386", "00_0", "00", "0"));
        mCountryList.put("sb", new CountryData("Solomon Islands",          "sb", "+677", "00_x", "00", ""));
        mCountryList.put("so", new CountryData("Soomaaliya (Somalia)",     "so", "+252", "00_x", "00", ""));
        mCountryList.put("za", new CountryData("South Africa",             "za",  "+27", "00_0", "00", "0"));
        mCountryList.put("ss", new CountryData("South Sudan",              "ss", "+211", "00_0", "00", "0"));
        mCountryList.put("es", new CountryData("España (Spain)",           "es",  "+34", "00_x", "00", ""));
        mCountryList.put("lk", new CountryData("Sri Lanka",                "lk",  "+94", "00_0", "00", "0"));
        mCountryList.put("sd", new CountryData("Sudan",                    "sd", "+249", "00_0", "00", "0"));
        mCountryList.put("sr", new CountryData("Suriname",                 "sr", "+597", "00_0", "00", "0"));
        mCountryList.put("sz", new CountryData("Swaziland",                "sz", "+268", "00_x", "00", ""));
        mCountryList.put("se", new CountryData("Sverige (Sweden)",         "se",  "+46", "00_0", "00", "0"));
        mCountryList.put("ch", new CountryData("Schweiz, Suisse, Svizzera, Svizra (Switzerland)",
                                                                           "ch",  "+41", "00_0", "00", "0"));
//        mCountryList.put("sy", new CountryData("as-sūriyya (Syria)",       "sy", "+963", "00_0", "00", "0"));
        mCountryList.put("sy", new CountryData("(Syria) الجمهورية العربية السورية",
                                                                           "sy", "+963", "00_0", "00", "0"));
        mCountryList.put("tw", new CountryData("中華民國 (Taiwan)",        "tw", "+886", "00_0", "002", "0"));
        mCountryList.put("tj", new CountryData("Тоҷикистон (Tajikistan)",  "tj", "+992", "00_0", "810", "8"));
        mCountryList.put("tz", new CountryData("Tanzania",                 "tz", "+255", "00_0", "000", "0"));
        mCountryList.put("th", new CountryData("Thailand",                 "th",  "+66", "00_0", "001", "0"));
        mCountryList.put("tl", new CountryData("Timor-Leste (East-Timor)", "tl", "+670", "00_x", "00", ""));
        mCountryList.put("tg", new CountryData("Togo",                     "tg", "+228", "00_x", "00", ""));
        mCountryList.put("tk", new CountryData("Tokelau",                  "tk", "+690", "00_x", "00", ""));
        mCountryList.put("to", new CountryData("Tonga",                    "to", "+676", "00_x", "00", ""));
//        mCountryList.put("tn", new CountryData("at-Tūnisiyya (Tunisia)",   "tn", "+216", "00_x", "00", ""));
        mCountryList.put("tn", new CountryData("(Tunisia) تونس‎",           "tn", "+216", "00_x", "00", ""));
        mCountryList.put("tr", new CountryData("Türkiye (Turkey)",         "tr",  "+90", "00_0", "00", "0"));
        mCountryList.put("tm", new CountryData("Türkmenistan (Turkmenistan)", "tm", "+993", "00_0", "810", "8"));
        mCountryList.put("tv", new CountryData("Tuvalu",                   "tv", "+688", "00_x", "00", ""));
        mCountryList.put("ug", new CountryData("Uganda",                   "ug", "+256", "00_0", "000", "0"));
        mCountryList.put("us", new CountryData("United States",            "us",   "+1", "nanp", "011", ""));
        mCountryList.put("ua", new CountryData("Україна (Ukraine)",        "ua", "+380", "00_0", "00", "0"));
//        mCountryList.put("ae", new CountryData("al-Imārāt al-ʿarabiyya al-muttaḥida (United Arab Emirates)",
//                                                                           "ae", "+971", "00_0", "00", "0"));
        mCountryList.put("ae", new CountryData("(U.A. Emirates) الإمارات العربيّة المتّحدة‎",
                                                                           "ae", "+971", "00_0", "00", "0"));
        mCountryList.put("gb", new CountryData("United Kingdom",           "gb",  "+44", "00_0", "00", "0"));
        mCountryList.put("uk", new CountryData("United Kingdom",           "uk",  "+44", "00_0", "00", "0"));
        mCountryList.put("uy", new CountryData("Uruguay",                  "uy", "+598", "00_x", "00", ""));
        mCountryList.put("uz", new CountryData("Oʻzbekiston (Uzbekistan)", "uz", "+998", "00_0", "810", "8"));
        mCountryList.put("vu", new CountryData("Vanuatu",                  "vu", "+678", "00_x", "00", ""));
// Vatican has no IDD and no NDD according to ITU
// mCountryList.put("va", new CountryData("Civitatis Vaticanæ (Vatican City)", "va", "+379", "00_x", "00", ""));
        mCountryList.put("ve", new CountryData("Venezuela",                "ve", "+58", "00_0", "00", "0"));
        mCountryList.put("vn", new CountryData("Việt Nam (Vietnam)",                  "vn",  "+84", "00_0", "00", "0"));
        mCountryList.put("wf", new CountryData("Wallis et Futuna (Wallis & Futuna)",
                                                                           "wf", "+681", "00_x", "00", ""));
//        mCountryList.put("ye", new CountryData("al-Yamaniyya (Yemen)",     "ye", "+967", "00_0", "00", "0"));
        mCountryList.put("ye", new CountryData("(Yemen) اليمن‎",            "ye", "+967", "00_0", "00", "0"));
        mCountryList.put("zm", new CountryData("Zambia",                   "zm", "+260", "00_0", "00", "0"));
        mCountryList.put("zw", new CountryData("Zimbabwe",                 "zw", "+263", "00_0", "00", "0"));
    }

    private static void initialize(Context ctx) {
        mCountryList.put("!none_", new CountryData(ctx.getString(R.string.sp_dial_helper_none), "!none_", "", "_none_", "", ""));
        mCountryList.put("!simple_", new CountryData(ctx.getString(R.string.sp_dial_helper_simple), "!simple_", "", "_simple_", "", ""));
    }

    public static CountryData getActiveCountry() {
        return mActiveCountry;
    }

    public static DialHelper getDialHelper() {
        return mDialHelper == null ? mNullHelper : mDialHelper;
    }

    /**
     * Return the specific helper explanation text of a selected country.
     *
     * @param cd Short name of the country
     * @return resource id of explanation string
     */
    public static int getHelperExplanation(CountryData cd) {
        if (cd == null)
            return R.string.sp_dial_helper_select_explanation_1;  // a generic string
        DialHelper tmpHelper = mHelperList.get(cd.helperName);
        if (tmpHelper == null)
            return R.string.sp_dial_helper_select_explanation_1;  // a generic string
        return tmpHelper.getExplanation();
    }

    /**
     * Set a selected or default dial helper.
     *
     * The call this function after the phone server was started and initialized. Otherwise
     * the functions cannot read the partner id.
     *
     * @param ctx Context required for shared preferences.
     */
    public static void setDialHelper(Context ctx) {
        if (ctx == null)
            return;

        if (!mInitialized) {
            initialize(ctx);
            mInitialized = true;
        }
        if (mPartnerId == null) {
            String id = TiviPhoneService.getInfo(0, -1, "cfg.partnerId");
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Partner identifier (set): '" + id + "'");
            mPartnerId = TextUtils.isEmpty(id) ? SC : id;
        }
        PartnerData partner = mPartnerList.get(mPartnerId);
        if (partner == null)
            partner = mPartnerList.get("SC");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String country = prefs.getString(PREFERRED_COUNTRY, null);

        // If no preference set then initialize with the default country that's defined for
        // the partner
        CountryData cd = TextUtils.isEmpty(country) ? mCountryList.get(partner.countryName) : mCountryList.get(country);
        if (cd == null)
            return;

        if (mActiveCountry == null || !cd.shortName.equals(mActiveCountry.shortName)) {
            DialHelper tmpHelper = mHelperList.get(cd.helperName);
            if (tmpHelper == null)              // none available for this country/dial plan area
                return;

            mActiveCountry = cd;
            mDialHelper = tmpHelper;
        }
    }

    public static boolean showDialHelperOption() {
        if (mPartnerId == null) {
            String id = TiviPhoneService.getInfo(0, -1, "cfg.partnerId");
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Partner identifier: '" + id + "'");
            mPartnerId = TextUtils.isEmpty(id) ? SC : id;
        }
        PartnerData partner = mPartnerList.get(mPartnerId);
        return partner != null && partner.offerHelper;
    }

    public static void resetDialHelper() {
        mPartnerId = null;
        mActiveCountry = null;
        mDialHelper = null;
    }

    public static String getCountryName(Context ctx) {
        if (mActiveCountry == null || mActiveCountry.fullName == null)
            return ctx.getString(R.string.sp_dial_helper_none);
        return mActiveCountry.fullName;
    }

    public static boolean storeAndSetCountryHelper(Context ctx, String country) {
        if (ctx == null || TextUtils.isEmpty(country))
            return false;

        if (!mCountryList.containsKey(country))
            return false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().putString(PREFERRED_COUNTRY, country).apply();
        setDialHelper(ctx);       // load and update internal data
        return true;
    }

    /*
     * Data structures and helper functions for the SectionIndexer of the CountryArrayAdapter
     * (DialHelperSelectorFragment)
     */
    private static char mIndexChars[] = {'!', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private static int mSectionIndex[] = new int[mIndexChars.length];

    public final static String mIndexStrings[] = {" ", "A", "B", "C", "C", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N",
            "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};

    /**
     * Setup the section index.
     *
     * This functions assumes that the countries' short name starts with a character of the
     * mIndexChars array. Failing to do so leads of an index-out-of-bounds exception.
     *
     * @param countryList the list of countries in the list view, derived from mCountryList
     */
    public static void setupIndex(List<CountryData> countryList) {
        char previousIndexChar = mIndexChars[0];
        int index = 0;
        mSectionIndex[0] = 0;
        int numCountries = countryList.size();
        for (int i = 0; i < numCountries; i++) {
            String shortName = countryList.get(i).shortName;
            if (previousIndexChar != shortName.charAt(0)) {
                previousIndexChar = shortName.charAt(0);
                mSectionIndex[++index] = i;
            }
        }
    }

    /**
     * Return the section number for this position.
     *
     * @param position the position of the element in the list
     * @return the section index which determines the string to display
     */
    public static int getSectionForPosition(int position) {
        int section = 0;

        while (position >= mSectionIndex[section]) {
            section++;
            if (section >= mSectionIndex.length)
                break;
        }
        return section;
    }

    /**
     * Return the first position of a section.
     *
     * @param sectionIndex the section number.
     * @return the position of the first item in this section.
     */
    public static int getPositionForSection(int sectionIndex) {
        if (sectionIndex >= mSectionIndex.length) {
            return mSectionIndex[mSectionIndex.length - 1];
        }
        if (sectionIndex < 0) {
            return mSectionIndex[0];
        }
        return mSectionIndex[sectionIndex];
    }
}
