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
package com.silentcircle.messaging.location;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class LocationUtils {

    public static final int DEFAULT_ZOOM_LEVEL = 11;
    public static final String URI_FORMAT_LOCATION_WEB_GOOGLE = "https://www.google.com/maps/@%1$.6f,%2$.6f,%3$fz";
    public static final String URI_FORMAT_LOCATION_GEO = "geo:0,0?q=%1$.6f,%2$.6f(%4$s)";

    public static final String FIELD_LATITUDE = "la";
    public static final String FIELD_LONGITUDE = "lo";
    public static final String FIELD_TIME = "t";
    public static final String FIELD_ALTITUDE = "a";
    public static final String FIELD_ACCURACY_HORIZONTAL = "v";
    public static final String FIELD_ACCURACY_VERTICAL = "h";
    public static final String LOCATION_PROVIDER = "GPS";

    public static boolean isLocationSharingAvailable(Context context) {

        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        if (manager == null) {
            return false;
        }
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);

        String provider = manager.getBestProvider(criteria, true);

        return !(provider == null || LocationManager.PASSIVE_PROVIDER.equals(provider));

    }

    public static void startLocationSettingsActivity(Context context) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        if (context.getPackageManager().resolveActivity(intent, 0) != null) {
            context.startActivity(intent);
        }
    }

    public static void viewLocation(Context context, double latitude, double longitude) {
        viewLocation(context, latitude, longitude, DEFAULT_ZOOM_LEVEL);
    }

    public static void viewLocation(Context context, double latitude, double longitude, float zoom) {
        try {
            viewLocation(context, latitude, longitude, zoom, URI_FORMAT_LOCATION_GEO);
        } catch (ActivityNotFoundException exception) {
            viewLocation(context, latitude, longitude, zoom, URI_FORMAT_LOCATION_WEB_GOOGLE);
        }
    }

    public static void viewLocation(Context context, double latitude, double longitude, float zoom, String format) {
        context.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Locale.US, format, latitude, longitude, zoom,
                        context.getString(R.string.undisclosed_location)))));
    }

    public static void viewLocation(Context context, double latitude, double longitude, String format) {
        viewLocation(context, latitude, longitude, DEFAULT_ZOOM_LEVEL, format);
    }

    public static void viewLocation(Context context, Location location) {
        viewLocation(context, location, 11);
    }

    public static void viewLocation(Context context, Location location, float zoom) {
        if (location != null) {
            viewLocation(context, location.getLatitude(), location.getLongitude(), zoom);
        }
    }

    /**
     * Parse location from JSON string.
     *
     * @param messageAttributes String in form
     *        <tt>{"la":26.9817699,"lo":34.1850782,"t":1434179465770,"a":0,"h":52.5,"v":52.5}</tt>
     * @return Location from parsed string or null if parsing failed.
     */
    public static Location parseMessageLocation(String messageAttributes) {
        Location location;
        try {
            JSONObject json = new JSONObject(messageAttributes);
            location = new Location(LOCATION_PROVIDER);
            location.setLongitude(json.getDouble(FIELD_LONGITUDE));
            location.setLatitude(json.getDouble(FIELD_LATITUDE));
            location.setTime(json.getLong(FIELD_TIME));
            location.setAltitude(json.getLong(FIELD_ALTITUDE));
            location.setAccuracy(json.getLong(FIELD_ACCURACY_HORIZONTAL));
        } catch (JSONException e) {
            location = null;
        }
        return location;
    }

    /**
     * Extract {@link com.silentcircle.messaging.model.Location} from passed JSON string.
     *
     * @param locationString JSON with location store as attributes.
     *
     * @return {@link com.silentcircle.messaging.model.Location} instance. If there is a parsing
     *         failure, null is returned.
     */
    public static com.silentcircle.messaging.model.Location stringToMessageLocation(
            final String locationString) {
        com.silentcircle.messaging.model.Location location = null;
        if (TextUtils.isEmpty(locationString))
            return null;
        try {
            location = jsonToMessageLocation(new JSONObject(locationString));
        } catch (JSONException exception) {
            // return null as location
        }
        return location;
    }

    /**
     * Extract {@link com.silentcircle.messaging.model.Location} from passed JSON.
     *
     * @param jsonLocation JSON with location store as attributes.
     *
     * @return {@link com.silentcircle.messaging.model.Location} instance. If there is a parsing
     *         failure, null is returned.
     */
    public static com.silentcircle.messaging.model.Location jsonToMessageLocation(
            final JSONObject jsonLocation) {
        com.silentcircle.messaging.model.Location location;
        try {
            location = new com.silentcircle.messaging.model.Location();
            location.setLatitude(jsonLocation.getDouble(FIELD_LATITUDE));
            location.setLongitude(jsonLocation.getDouble(FIELD_LONGITUDE));
            location.setTimestamp(jsonLocation.optLong(FIELD_TIME));
            location.setAltitude(jsonLocation.optDouble(FIELD_ALTITUDE));
            location.setHorizontalAccuracy(jsonLocation.optDouble(FIELD_ACCURACY_HORIZONTAL));
            location.setVerticalAccuracy(jsonLocation.optDouble(FIELD_ACCURACY_VERTICAL));
        } catch (JSONException exception) {
            location = null;
        }
        return location;
    }

    /**
     * Store {@link android.location.Location}.fields in a JSON object as attributes.
     *
     * @param location Instance of {@link android.location.Location}.
     *
     * @return .{@link org.json.JSONObject} with location fields stored as attributes in JSON.
     *          JSON object will be empty if passed location instance is null.
     */
    public static JSONObject locationToJSON(final Location location) {
        JSONObject jsonLocation = new JSONObject();
        if (location != null) {
            locationToJSON(jsonLocation, location);
        }
        return jsonLocation;
    }

    /**
     * Store {@link android.location.Location}.fields in passed JSON object as
     * attributes.
     *
     * @param jsonLocation A JSON object to store location in.
     * @param location Instance of {@link android.location.Location}.
     *
     * @return .{@link org.json.JSONObject} with location fields stored as attributes in JSON.
     */
    public static JSONObject locationToJSON(final JSONObject jsonLocation, final Location location) {
        if (location != null && jsonLocation != null) {
            try {
                jsonLocation.put(FIELD_LATITUDE, location.getLatitude());   // "latitude"
                jsonLocation.put(FIELD_LONGITUDE, location.getLongitude()); // "longitude"
                jsonLocation.put(FIELD_TIME, location.getTime());           // "timestamp"
                jsonLocation.put(FIELD_ALTITUDE, location.getAltitude());   // "altitude"
                jsonLocation.put(FIELD_ACCURACY_HORIZONTAL, location.getAccuracy()); // "horizontalAccuracy"
                jsonLocation.put(FIELD_ACCURACY_VERTICAL, location.getAccuracy());   // "verticalAccuracy"
            } catch (JSONException exception) {
                // return empty json
            }
        }
        return jsonLocation;
    }

    public static String locationToJSONString(final Location location) {
        return locationToJSON(location).toString();
    }

    /**
     * Store {@link com.silentcircle.messaging.model.Location}.fields in JSON object as
     * attributes.
     *
     * @param location Instance of {@link com.silentcircle.messaging.model.Location}.
     *
     * @return .{@link org.json.JSONObject} with location fields stored as attributes in JSON.
     *          JSON will be empty if passed location instance is null.
     */
    public static JSONObject messageLocationToJSON(final com.silentcircle.messaging.model.Location location) {
        JSONObject jsonLocation = new JSONObject();

        if (location != null) {
            messageLocationToJSON(jsonLocation, location);
        }

        return jsonLocation;
    }

    /**
     * Store {@link com.silentcircle.messaging.model.Location}.fields in JSON object as
     * attributes.
     *
     * @param jsonLocation A JSON object to store location in.
     * @param location Instance of {@link com.silentcircle.messaging.model.Location}.
     *
     * @return .{@link org.json.JSONObject} with location fields stored as attributes in JSON.
     *          JSON will be empty if passed location instance is null.
     */
    public static JSONObject messageLocationToJSON(final JSONObject jsonLocation,
            final com.silentcircle.messaging.model.Location location) {
        if (location != null && jsonLocation != null) {
            try {
                jsonLocation.put(FIELD_LATITUDE, location.getLatitude());
                jsonLocation.put(FIELD_LONGITUDE, location.getLongitude());
                jsonLocation.put(FIELD_TIME, location.getTimestamp());
                jsonLocation.put(FIELD_ALTITUDE, location.getAltitude());
                jsonLocation.put(FIELD_ACCURACY_HORIZONTAL, location.getHorizontalAccuracy());
                jsonLocation.put(FIELD_ACCURACY_VERTICAL, location.getVerticalAccuracy());
            } catch (JSONException exception) {
            }
        }
        return jsonLocation;
    }

    /**
     * Return {@link android.location.Location Location} instance which matches passed
     * {@link com.silentcircle.messaging.model.Location}.
     *
     * @param location Instance of {@link android.location.Location}.
     *
     * @return {@link android.location.Location Location} instance or null if null was passed.
     */
    public static Location messageLocationToLocation(
            final com.silentcircle.messaging.model.Location location) {
        Location result = null;
        if (location != null) {
            result = new Location(LOCATION_PROVIDER);
            result.setLatitude(location.getLatitude());
            result.setLongitude(location.getLongitude());
            result.setTime(location.getTimestamp());
            result.setAltitude(location.getAltitude());
            result.setAccuracy((float) location.getHorizontalAccuracy());
            // TODO: ignore vertical accuracy?
        }
        return result;
    }

    /**
     * Retrieve system's current set location mode as pair of integer constant and string
     * description.
     *
     * There are four location modes defined in Android:
     *     Settings.Secure.LOCATION_MODE_OFF
     *     Settings.Secure.LOCATION_MODE_SENSORS_ONLY
     *     Settings.Secure.LOCATION_MODE_BATTERY_SAVING
     *     Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
     *
     * For result pair them with with strings with ids
     *     R.string.location_mode_of
     *     R.string.location_mode_device
     *     R.string.location_mode_battery_saving
     *     R.string.location_mode_high_accuracy
     */
    public static Pair<Integer, String> getCurrentLocationMode(final Context context) {
        int[] modeStringIds = {R.string.location_mode_off, R.string.location_mode_device,
                R.string.location_mode_battery_saving, R.string.location_mode_high_accuracy};
        int locationMode = Settings.Secure.LOCATION_MODE_OFF;
        String modeString = null;
        try {
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            locationMode = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.LOCATION_MODE);
            modeString = (modeStringIds.length > locationMode)
                    ? context.getResources().getString(modeStringIds[locationMode])
                    : null;
        } catch (Settings.SettingNotFoundException e) {
            // unable to determine location mode
        }
        return new Pair<>(locationMode, modeString);
    }

}
