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

package com.silentcircle.silentphone2.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import com.silentcircle.logs.Log;

import com.silentcircle.silentphone2.fragments.InCallDrawerFragment;
import com.silentcircle.silentphone2.services.TiviPhoneService;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class contains functions to classify Android devices and perform specific handling.
 *
 * The class uses product name and similar available parameters to classify a device and
 * to perform some specific handling. For example some devices have a built-in hardware
 * echo canceler and we can switch off the SW echo canceler. This leads to much better
 * audio quality. Also we can enable some specific SW echo canceler after we know which
 * one works best for a specific device.
 *
 * Created by werner on 06.06.13.
 */

/*
  Data detected so far for various devices:

  Manufacturer  | Brand              | Model            | Device        | Market name        | AEC
  --------------+--------------------+------------------+---------------+--------------------+------------
  samsung       | samsung            | GT-N7000         | GT-N7000      | Galaxy Note I      | HW built-in
  samsung       | samsung            | GT-I9082         | baffin        | Duos               | HW built-in
  samsung       | google             | Galaxy Nexus     | maguro        | Google Nexus 3?    | SW, webRTC
  samsung       | Verizon            | SCH-I535         | d2vzw         | Galaxy S3, US      | HW built-in
  samsung       | samsung            | GT-I9300         | m0            | Galaxy S3, EU      | HW built-in
  samsung       | google             | Nexus 10         | manta         | Google Nexus 10    | ??

  lge           | google             | Nexus 4          | mako          | Google Nexus 4     | SW, webRTC
  lge           | google             | Nexus 5          | hammerhead    | Google Nexus 5     | SW, webRTC

  motorola      | sprint             | MB855            | sunfire       |                    | SW, webRTC

  HTC           | htc_asia_hk        | HTC One X        | endeavoru     | HTC One X          | HW built-in

  Vertu         | Vertu              | Vertu Ti         | ??            | Vertu Ti           | HW built-in
  Vertu         | Vertu              | Constellation V  | gambit        | Constellation V    | HW built-in
  Vertu         | Vertu              | Signature Touch  | odin          | Signature Touch    | HW built-in, gain reduction
  Vertu         | Vertu              | Aster            | alexa         | Aster              | HW built-in, gain reduction
 */
public class DeviceHandling {

    private static final String LOG_TAG = "DeviceHandling";

    private static final String HW_AEC = "*##*30*";     // Switches off echo processing in C++
    private static final String WR_AEC = "*##*33*";
    
    public static boolean mEchoCancelerActive;

    private static class DeviceData {
        final String manufacturer;
        final String brand;
        final String model;
        final String deviceName;
        final String aecSelect;
        final boolean aecSwitchModes;
        final String aecControlSpeakerOff;     // use this command also to switch off in case of HW AEC
        final String aecControlSpeakerOn;
        final boolean useProximityWakeLock;
        final boolean switchAudioMode;
        final int gainReduction;

        /**
         * Data record to describe an Android device and define it's AEC commands
         *
         * Command details, AEC select command string:
         *
         *    *##*30* - don't use any specific SW AEC handling, use built-in HW AEC.
         *    *##*33* - WebRTC AEC
         *
         * WebRTC AEC supports several modes to adjust the AEC to different speaker modes. To select
         * a mode use the following command: *##*33#n*, where n is a value between 0 and 4:
         *
         *    0 - Quiet earpiece or headset
         *    1 - Earpiece
         *    2 - Loud earpiece
         *    3 - Speakerphone
         *    4 - Loud speakerphone
         *
         * If no AEC initialization string is defined the client uses the normal webRTC as default (*##*33*).
         *
         * Depending on the Android version the checkAndSetAec(..) function may override the AEC command if
         * the command is not *##*30*. If Android AEC is not available use the command as specified, usually
         * *##*33*.
         *
         * @param m     Device's manufacturer name as per Build.MANUFACTURER. Specify manufacturer in all lower
         *              case characters.
         * @param b     Device's brand name as per Build.BRAND
         * @param model Device's model name as per Build.MODEL
         * @param d     Device's board name as per Build.BOARD
         * @param s     Use this command during start to select the AEC
         * @param sm    If true then use dynamic AEC mode switching between earpiece/headset and speaker
         * @param aModeOff Command to switch to AEC mode if speaker is not active
         * @param aModeOn  Command to switch to AEC mode is speaker is active
         * @param useProxy If true the device supports and can use the in-call proximity sensor wake-up
         * @param switchAudio If true switch audio modes during call. Some devices don't like this.
         * @param gainReduce if greater 0 the set this to the audio module, defines a shift value. A value
         *                   of one reduces shifts the audio output sample by one to the right (divide by 2)
         */
        DeviceData(String m, String b, String model, String d, String s, boolean sm, String aModeOff, String aModeOn,
            boolean useProxy, boolean switchAudio, int gainReduce) {
            manufacturer = m;
            brand = b;
            this.model = model;
            deviceName = d;
            aecSelect = s;
            aecSwitchModes = sm;
            aecControlSpeakerOff = aModeOff;
            aecControlSpeakerOn = aModeOn;
            useProximityWakeLock = useProxy;
            switchAudioMode = switchAudio;
            gainReduction = gainReduce;
        }
        DeviceData(String m, String b, String model, String d, String s, boolean sm, String aModeOff, String aModeOn) {
            this(m, b, model, d, s,  sm, aModeOff, aModeOn, true, true, 0);
        }
    }

    // True if we tried to classify device at least once
    private static boolean initialized;

    // Not null if we could classify the device
    private static DeviceData device;

    private static ArrayList<DeviceData> huawei = new ArrayList<>(10);
    private static ArrayList<DeviceData> htc = new ArrayList<>(10);

    // This list holds device data for devices that have "lge" as manufacturer string (case insensitive)
    // This may include other brands like google.
    private static ArrayList<DeviceData> lge = new ArrayList<>(10);
    private static ArrayList<DeviceData> motorola = new ArrayList<>(10);
    // This list holds device data for devices that have "samsung" as manufacturer string (case insensitive)
    // This may include other brands like google.
    private static ArrayList<DeviceData> samsung = new ArrayList<>(10);
    private static ArrayList<DeviceData> vertu = new ArrayList<>(10);
    private static ArrayList<DeviceData> sgp = new ArrayList<>(10);

    // This Map holds the manufacturer lists, key is manufacturer name
    private static HashMap<String, ArrayList<DeviceData>> manufacturerList = new HashMap<>(10);

    static {
        samsung.add(new DeviceData("samsung", "samsung", "GT-N7000", "GT-N7000", HW_AEC, false, null, null));
        samsung.add(new DeviceData("samsung", "samsung", "GT-I9082", "baffin", HW_AEC, false, null, null));   // Duos
        samsung.add(new DeviceData("samsung", "Verizon", "SCH-I535", "d2vzw", HW_AEC, false, null, null));    // US galaxy S3
        samsung.add(new DeviceData("samsung", "samsung", "GT-I9300", "m0", HW_AEC, false, null, null));       // EU galaxy S3
        samsung.add(new DeviceData("samsung", "google", "Galaxy Nexus", "maguro", WR_AEC, true, "*##*33#1*", "*##*33#3*"));
        samsung.add(new DeviceData("samsung", "google", "Nexus 10", "manta", WR_AEC, false, null, null));

        sgp.add(new DeviceData("SGP", "SGP", "Blackphone 2", "BP2", HW_AEC, false, null, null));

        htc.add(new DeviceData("HTC", "htc_asia_hk", "HTC One X", "endeavoru", HW_AEC, false, null, null));

        // Use the WebRTC AEC for Nexus 4, maybe we need to add a mode as well, maybe need to switch modes during
        // speaker handling.
        lge.add(new DeviceData("lge", "google", "Nexus 4", "mako", WR_AEC, false, null, null));
        lge.add(new DeviceData("lge", "google", "Nexus 5", "hammerhead", WR_AEC, false, null, null));

        motorola.add(new DeviceData("motorola", "sprint", "MB855", "sunfire", WR_AEC, true, "*##*33#1*", "*##*33#3*",
                false, false, 0));     // don't switch audio mode, don't use proximity wake-up on this (old) device
        motorola.add(new DeviceData("motorola", "google", "Nexus 6", "shamu", WR_AEC, true, null, null));

        vertu.add(new DeviceData("Vertu", "Vertu", "Vertu Ti", "UNKNOWN", HW_AEC, false, null, null));
        vertu.add(new DeviceData("Vertu", "Vertu", "Constellation V", "gambit", HW_AEC, false, null, null));
        vertu.add(new DeviceData("Vertu", "Vertu", "Signature Touch", "odin", HW_AEC, false, null, null, true, true, 1));
        vertu.add(new DeviceData("Vertu", "Vertu", "Aster", "alexa", HW_AEC, false, null, null, true, true, 1));

        // Assume a HW echo canceler
        huawei.add(new DeviceData("huawei", "google", "Nexus 6P", "angler", HW_AEC, false, null, null));

        manufacturerList.put("samsung", samsung);
        manufacturerList.put("sgp", sgp);
        manufacturerList.put("htc", htc);
        manufacturerList.put("lge", lge);
        manufacturerList.put("vertu", vertu);
        manufacturerList.put("motorola", motorola);
        manufacturerList.put("huawei", huawei);
    }

    @SuppressLint("DefaultLocale")
    public static void deviceClassification() {
        if (initialized)
            return;

        String m = Build.MANUFACTURER.toLowerCase();
        ArrayList<DeviceData> ddList = manufacturerList.get(m);
        if (ddList == null)
            return;

        for (DeviceData dd : ddList) {
//            boolean brand = dd.brand != null && dd.brand.equals(Build.BRAND);
            boolean model = dd.model != null && dd.model.equals(Build.MODEL);
//            boolean dev = dd.deviceName != null && dd.deviceName.equals(Build.DEVICE);

            if (model) {        // If the model of this manufacturer matches assume a hit
                device = dd;
                break;
            }
        }
        initialized = true;
    }

    /**
     * Classify device and set the Acoustic Echo Canceler (AEC) and gain handling during application start.
     *
     * Due to changes in Android Lollipop AEC this code always sets the AEC via the command. The commented
     * code shows how to enable the Android build-in AEC again in case it's required
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void checkAndSetAec() {
        deviceClassification();

        if (device == null || device.aecSelect == null) {
            return;
        }
        String aec = device.aecSelect;
        TiviPhoneService.doCmd(aec);

        if (device.gainReduction != 0) {
            int ret = TiviPhoneService.doCmd("set.gainReduction=" + device.gainReduction);
            if (ConfigurationUtilities.mTrace) Log.i(LOG_TAG, "Device play gain reduction: " + device.gainReduction + "(" + ret + ")");
        }
    }

    public static boolean useProximityWakeup() {
        deviceClassification();
        return device == null || device.useProximityWakeLock;
    }

    public static boolean switchAudioMode() {
        deviceClassification();
        return device == null || device.switchAudioMode;
    }

    public static String getModel() {
        deviceClassification();
        if (device == null)
            return null;
        return device.model;
    }

    /**
      * Set AEC mode according to the speaker state.
      *
      * @param speakerOn if true use the command defined in <code>aecControlSpeakerOn</code>, otherwise
      *                  use <code>aecControlSpeakerOff</code>
      */
    public static void setAecMode(Context context, boolean speakerOn) {
        deviceClassification();
//        if (device == null || !device.aecSwitchModes) {
//            return;
//        }
        // Don't switch AEC if the device has a HW echo canceler
        if (device != null && HW_AEC.equals(device.aecSelect)) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean echoSwitchCheck = prefs.getBoolean(InCallDrawerFragment.ECHO_CANCEL_SWITCHING, true);

        // If user disabled this option then always use AEC in case this device has no HW AEC
        if (!echoSwitchCheck) {
            TiviPhoneService.doCmd(WR_AEC);
            mEchoCancelerActive = true;
            return;
        }
        if (speakerOn) {
            TiviPhoneService.doCmd(WR_AEC);
            mEchoCancelerActive = true;
//            if (device.aecControlSpeakerOn != null) {
//                TiviPhoneService.doCmd(device.aecControlSpeakerOn);
//            }
        }
        else {
            TiviPhoneService.doCmd(HW_AEC);
            mEchoCancelerActive = false;
//            if (device.aecControlSpeakerOff != null) {
//                TiviPhoneService.doCmd(device.aecControlSpeakerOff);
//            }
        }
    }
}
