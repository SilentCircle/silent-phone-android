/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.util;

public class UTI {

    public static String fromMIMEType( String mimeType ) {
        if( mimeType == null ) {
            return "public.data";
        }
        if( mimeType.startsWith( "image/" ) ) {
            return "public.image";
        }
        if( mimeType.startsWith( "video/" ) ) {
            return "public.movie";
        }
        if( mimeType.startsWith( "audio/" ) ) {
            return "public.audio";
        }
        if( mimeType.equals( "text/plain" ) ) {
            return "public.plain-text";
        }
        if( mimeType.equals( "text/vcard" ) || mimeType.equals( "text/x-vcard" ) ) {
            return "public.vcard";
        }
        if( mimeType.equals( "application/pdf" ) ) {
            return "com.adobe.pdf";
        }
        return "public.data";
    }

    public static boolean isAudio( String type ) {
        boolean audio = false;
        audio = audio || "com.apple.m4a-audio".equals( type );
        audio = audio || "public.audio".equals( type );
        audio = audio || "public.midi-audio".equals( type );
        audio = audio || "public.mpeg-4-audio".equals( type );
        audio = audio || "public.mp3".equals( type );
        audio = audio || "com.microsoft.waveform-audio".equals( type );
        return audio;
    }

    public static boolean isContact( String type ) {
        boolean contact = false;
        contact = contact || "public.vcard".equals( type );
        return contact;
    }

    public static boolean isImage( String type ) {
        boolean image = false;
        image = image || "public.image".equals( type );
        image = image || "com.microsoft.bmp".equals( type );
        image = image || "com.compuserve.gif".equals( type );
        image = image || "public.png".equals( type );
        image = image || "public.jpeg".equals( type );
        return image;
    }

    public static boolean isVideo( String type ) {
        boolean video = false;
        video = video || "public.movie".equals( type );
        video = video || "public.3gpp".equals( type );
        video = video || "public.mpeg-4".equals( type );
        return video;
    }

    public static boolean isVisual( String type ) {
        return isImage( type ) || isVideo( type );
    }

}
