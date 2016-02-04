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


import com.silentcircle.messaging.model.SCloudObject;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONSCloudObjectAdapter { // implements ModelAdapter<SCloudObject> {

	public static SCloudObject adapt( JSONObject json ) {

		SCloudObject object = new SCloudObject();

		try {

			if( json.has( "url" ) ) {
				object.setURL( json.getString( "url" ) );
			}

			if( json.has( "locator" ) ) {
				object.setLocator( json.getString( "locator" ) );
			}

			if( json.has( "size" ) ) {
				object.setSize( json.getInt( "size" ) );
			}

			if( json.has( "key" ) ) {
				object.setKey( json.getString( "key" ) );
			}

			if( json.has( "uploaded" ) ) {
				object.setUploaded( json.getBoolean( "uploaded" ) );
			}

			if( json.has( "downloaded" ) ) {
				object.setDownloaded( json.getBoolean( "downloaded" ) );
			}

		} catch( JSONException exception ) {
			// Not possible.
		}

		return object;

	}

	public static JSONObject adapt( SCloudObject object ) {

		if( object == null ) {
			return null;
		}

		JSONObject json = new JSONObject();

		try {
			json.put( "key", object.getKey() );
			json.put( "url", object.getURL() );
			json.put( "locator", object.getLocator() );
			json.put( "size", object.getSize() );
			json.put( "uploaded", object.isUploaded() );
			json.put( "downloaded", object.isDownloaded() );
		} catch( JSONException impossible ) {
			// Never going to happen.
		}

		return json;

	}

//	@Override
	public SCloudObject deserialize( String serial ) {
		try {
			return serial == null ? null : adapt( new JSONObject( serial ) );
		} catch( JSONException exception ) {
			return null;
		}
	}

//	@Override
	public String identify( SCloudObject object ) {
		return object == null ? null : String.valueOf(object.getLocator());
	}

//	@Override
	public String serialize( SCloudObject object ) {
		JSONObject json = object == null ? null : adapt( object );
		return json == null ? null : json.toString();
	}

}
