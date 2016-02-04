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
package com.silentcircle.messaging.model;

import com.silentcircle.messaging.util.IOUtils;

import java.util.Arrays;


public class Contact extends Burnable {

	private static final byte [] UNKNOWN_ALIAS = IOUtils.toByteArray("(unknown)");

	private static String toString( byte [] value ) {
		return value == null ? null : new String( value );
	}

	protected byte [] alias;
	protected byte [] username;
	protected byte [] device;

	public Contact() {
	}

	public Contact( byte [] username ) {
		this.username = username;
	}

	public Contact( CharSequence username ) {
		this( IOUtils.toByteArray( username ) );
	}

	@Override
	public void clear() {
		removeAlias();
		removeDevice();
		removeUsername();
	}

	@Override
	public boolean equals( Object o ) {
		return o != null && hashCode() == o.hashCode();
	}

	public String getAlias() {
		return toString( getAliasAsByteArray() );
	}

	public byte [] getAliasAsByteArray() {
		if( alias == null ) {
			if( username == null ) {
				return UNKNOWN_ALIAS;
			}
			int index = IOUtils.indexOf( username, AT );
			if( index >= 0 ) {
				alias = new byte [index];
				System.arraycopy( username, 0, alias, 0, alias.length );
			}
		}
		return alias;
	}

	public String getDevice() {
		return toString( getDeviceAsByteArray() );
	}

	public byte [] getDeviceAsByteArray() {
		return device;
	}

	public String getUsername() {
		return toString( getUsernameAsByteArray() );
	}

	public byte [] getUsernameAsByteArray() {
		return username;
	}

	@Override
	public int hashCode() {
		return username == null ? 0 : Arrays.hashCode( username );
	}

	public void removeAlias() {
		burn( alias );
		alias = null;
	}

	public void removeDevice() {
		burn( device );
		device = null;
	}

	public void removeUsername() {
		burn( username );
		username = null;
	}

	public void setAlias( byte [] alias ) {
		this.alias = alias;
	}

	public void setAlias( CharSequence alias ) {
		setAlias( IOUtils.toByteArray( alias ) );
	}

	public void setDevice( byte [] device ) {
		this.device = device;
	}

	public void setDevice( CharSequence device ) {
		setDevice( IOUtils.toByteArray( device ) );
	}

	public void setUsername( byte [] username ) {
		this.username = username;
	}

	public void setUsername( CharSequence username ) {
		setUsername( IOUtils.toByteArray( username ) );
	}
}
