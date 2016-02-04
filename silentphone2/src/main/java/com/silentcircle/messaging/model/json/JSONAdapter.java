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
package com.silentcircle.messaging.model.json;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class JSONAdapter {

	protected static boolean getBoolean( JSONObject json, String key ) {
		return getBoolean( json, key, false );
	}

	protected static boolean getBoolean( JSONObject json, String key, boolean defaultValue ) {
		try {
			return json.getBoolean( key );
		} catch( JSONException exception ) {
			return defaultValue;
		}
	}

	protected static double getDouble( JSONObject json, String key ) {
		return getDouble( json, key, Double.MIN_VALUE );
	}

	protected static double getDouble( JSONObject json, String key, double defaultValue ) {
		try {
			return json.getDouble( key );
		} catch( JSONException exception ) {
			return defaultValue;
		}
	}

	protected static int getInt( JSONObject json, String key ) {
		return getInt( json, key, Integer.MIN_VALUE );
	}

	protected static int getInt( JSONObject json, String key, int defaultValue ) {
		try {
			return json.getInt( key );
		} catch( JSONException exception ) {
			return defaultValue;
		}
	}

	protected static JSONArray getJSONArray( JSONObject json, String key ) {
		return getJSONArray( json, key, null );
	}

	protected static JSONArray getJSONArray( JSONObject json, String key, JSONArray defaultValue ) {
		try {
			return json.getJSONArray( key );
		} catch( JSONException exception ) {
			return defaultValue;
		}
	}

	protected static JSONObject getJSONObject( JSONObject json, String key ) {
		return getJSONObject( json, key, null );
	}

	protected static JSONObject getJSONObject( JSONObject json, String key, JSONObject defaultValue ) {
		try {
			return json.getJSONObject( key );
		} catch( JSONException exception ) {
			return defaultValue;
		}
	}

	protected static long getLong( JSONObject json, String key ) {
		return getLong( json, key, Long.MIN_VALUE );
	}

	protected static long getLong( JSONObject json, String key, long defaultValue ) {
		try {
			return json.getLong( key );
		} catch( JSONException exception ) {
			return defaultValue;
		}
	}

	protected static String getString( JSONObject json, String key ) {
		return getString( json, key, null );
	}

	protected static String getString( JSONObject json, String key, String defaultValue ) {
		try {
			return json.getString( key );
		} catch( JSONException exception ) {
			return defaultValue;
		}
	}

}
