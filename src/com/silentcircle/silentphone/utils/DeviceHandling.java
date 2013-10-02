/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.utils;

import android.os.Build;
import android.util.Log;

import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.activities.TMActivity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class contains functions to classify Android devices and perform specific handling.
 *
 * The class uses product name and similar available parameter to classify a device and
 * to perform some specific handling. For example some devices have a built-in hardware
 * echo canceler and we can switch off the SW echo canceler. This leads to much better
 * audio quality. Also we can enable some specific SW echo canceler after some we know
 * which one works best for a specific device.
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
  lge           | google             | Nexus 4          | mako          | Google Nexus 4     | SW, webRTC
  HTC           | htc_asia_hk        | HTC One X        | endeavoru     | HTC One X          | HW built-in
 */
public class DeviceHandling {

    private static final String LOG_TAG = "DeviceHandling";

    private static class DeviceData {
        final String manufacturer;
        final String brand;
        final String model;
        final String device;
        final String aecSelect;
        final boolean aecSwitchModes;
        final String aecControlSpkrOff;     // use this command also to switch off in case of HW AEC
        final String aecControlSpkrOn;

        /**
         * Data record to describe an Android device and define it's AEC commands
         *
         * Command details, AEC select command string:
         *
         *    *##*30* - switch off SW AEC, use built-in HW AEC
         *    *##*31* - speex AEC
         *    *##*32* - Google AEC
         *    *##*33* - WebRTC AEC
         *
         * WebRTC AEC supports several modes to adjust the AEC to different speaker modes. To select
         * a mode use the following command: *##*33#n*, where n is a vaule between 0 and 4:
         *
         *    0 - Quiet earpiece or headset
         *    1 - Earpiece
         *    2 - Loud earpiece
         *    3 - Speakerphone
         *    4 - Loud speakerphone
         *
         * @param m     Device's manufacturer name as per Build.MANUFACTURER. Specify manufacturer in all lower
         *              case characters.
         * @param b     Device's brand name as per Build.BRAND
         * @param modl  Device's model name as per Build.MODEL
         * @param d     Device's board name as per Build.BOARD
         * @param s     Use this command during start to select the AEC
         * @param sm    If true then use dynamic AEC mode switching between earpiece/headset and speaker
         * @param aModeOff Command to switch to AEC mode if speaker is not active
         * @param aModeOn  Command to switch to AEC mode is speaker is active
         */
        DeviceData(String m, String b, String modl, String d, String s, boolean sm, String aModeOff, String aModeOn) {
            manufacturer = m;
            brand = b;
            model = modl;
            device = d;
            aecSelect = s;
            aecSwitchModes = sm;
            aecControlSpkrOff = aModeOff;
            aecControlSpkrOn = aModeOn;
        }
    }

    // True if we tried to classify device at least once
    private static boolean initialized;

    // Not null if we could classify the device
    private static DeviceData device;

    // This list holds device data for devices that have "samsung" as manufacturer string (case insensitive)
    // This may include other brands like google.
    private static ArrayList<DeviceData> samsung = new ArrayList<DeviceData>(10);

    // This list holds device data for devices that have "lge" as manufacturer string (case insensitive)
    // This may include other brands like google.
    private static ArrayList<DeviceData> lge = new ArrayList<DeviceData>(10);

    private static ArrayList<DeviceData> htc = new ArrayList<DeviceData>(10);
   
    // This Map holds the manufacturer lists, key is manufacturer name
    private static HashMap<String, ArrayList<DeviceData>> manufacturerList = new HashMap<String, ArrayList<DeviceData>>(10);


    static {
        samsung.add(new DeviceData("samsung", "samsung", "GT-N7000", "GT-N7000", "*##*30*", false, null , null));
        samsung.add(new DeviceData("samsung", "samsung", "GT-I9082", "baffin", "*##*30*", false, null , null));   // Duos
        samsung.add(new DeviceData("samsung", "Verizon", "SCH-I535", "d2vzw", "*##*30*", false, null , null));    // US galaxy S3
        samsung.add(new DeviceData("samsung", "samsung", "GT-I9300", "m0", "*##*30*", false, null , null));       // EU galaxy S3
        samsung.add(new DeviceData("samsung", "google", "Galaxy Nexus", "maguro", "*##*33*", true, "*##*33#1*" , "*##*33#3*"));

        // Use the WebRTC AEC for Nexus 4, maybe we need to add a mode as well, maybe need to switch modes during
        // speaker handling
        lge.add(new DeviceData("lge", "google", "Nexus 4", "mako", "*##*33*", false, null, null));
       
        htc.add(new DeviceData("HTC", "htc_asia_hk", "HTC One X", "endeavoru", "*##*30*", false, null, null));

        manufacturerList.put("samsung", samsung);
        manufacturerList.put("lge", lge);
        manufacturerList.put("htc", htc);
    }

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
//            boolean dev = dd.device != null && dd.device.equals(Build.DEVICE);

            if (model) {        // If the model of this manufacturer matches assume a hit
                device = dd;
                break;
            }
        }
        initialized = true;
    }

    /**
     * Classify device and set the Accustic Echo Canceler during application start.
     */
    public static void checkAndSetAec() {
        deviceClassification();
        if (device == null)         // cannot classify, return, do nothing
            return;

        if (device.aecSelect != null) {
            if (TMActivity.SP_DEBUG) Log.i(LOG_TAG, "AEC command during startup: " + device.aecSelect);
            TiviPhoneService.doCmd(device.aecSelect);
        }
    }

    /**
     * Set AEC mode according to the speaker state.
     *
     * @param speakerOn if true use the command defined in <code>aecControlSpkrOn</code>, otherwise
     *                  use <code>aecControlSpkrOff</code>
     */
//    public static void setAecMode(boolean speakerOn) {
//        deviceClassification();
//        if (device == null || !device.aecSwitchModes) {
//            return;
//        }
//        if (speakerOn) {
//            if (device.aecControlSpkrOn != null) {
//                TiviPhoneService.doCmd(device.aecControlSpkrOn);
//            }
//        }
//        else {
//            if (device.aecControlSpkrOff != null) {
//                TiviPhoneService.doCmd(device.aecControlSpkrOff);
//            }
//        }
//    }
}
