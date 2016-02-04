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
package com.silentcircle.messaging.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Mime types for file viewing.
 */
public class MIME {

    private static final String TYPE_AUDIO = "audio/";
    private static final String TYPE_IMAGE = "image/";
    private static final String TYPE_VIDEO = "video/";
    private static final String TYPE_TEXT = "text/";
    public static final String[] TYPE_VCARD = {
            "text/x-vcard",
            "text/vcard"
    };
    private static final String[] TYPE_PDF = {
            "application/acrobat",
            "application/pdf",
            "application/x-pdf",
            "application/vnd.pdf",
            "text/pdf",
            "text/x-pdf"
    };

    private static final String[] TYPE_DOC = {
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
    };

    private static final String[] TYPE_XLS = {
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.ms-excel.addin.macroEnabled.12",
            "application/vnd.ms-excel.sheet.binary.macroEnabled.12"
    };

    private static final String[] TYPE_PPT = {
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.slide"
    };

    private static final String[] TYPE_OCTETSTREAM = {
            "application/octet-stream"
    };

    private static final Map<String, String> UTI_TO_MIME = new HashMap<String, String>();

    static {
        UTI_TO_MIME.put("public.jpeg", "image/jpeg");
        UTI_TO_MIME.put("public.tiff", "image/tiff");
        UTI_TO_MIME.put("public.png", "image/png");
        UTI_TO_MIME.put("public.mpeg", "video/mpeg");
        UTI_TO_MIME.put("com.apple.quicktime-movie", "video/quicktime");
        UTI_TO_MIME.put("public.avi", "video/avi");
        UTI_TO_MIME.put("public.mpeg-4", "video/mp4");
        UTI_TO_MIME.put("public.mp3", "audio/mpeg");
        UTI_TO_MIME.put("com.compuserve.gif", "image/gif");
        UTI_TO_MIME.put("public.data", "application/octet-stream");
    }



    public static String fromUTI(String uti) {
        return UTI_TO_MIME.get(uti);
    }

    public static boolean isAudio(String type) {
        return type != null && type.startsWith(TYPE_AUDIO);
    }

    public static boolean isContact(String type) {
        if (type == null) {
            return false;
        }
        for (String t : TYPE_VCARD) {
            if (type.equals(t)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isImage(String type) {
        return type != null && type.startsWith(TYPE_IMAGE);
    }

    public static boolean isText(String type) {
        return type != null && type.startsWith(TYPE_TEXT);
    }

    public static boolean isVideo(String type) {
        return type != null && type.startsWith(TYPE_VIDEO);
    }

    public static boolean isPdf(final String type) {
        return isType(type, TYPE_PDF);
    }

    public static boolean isDoc(final String type) {
        return isType(type, TYPE_DOC);
    }

    public static boolean isXls(final String type) {
        return isType(type, TYPE_XLS);
    }

    public static boolean isOctetStream(final String type) {
        return isType(type, TYPE_OCTETSTREAM);
    }

    public static boolean isPpt(final String type) {
        return isType(type, TYPE_PPT);
    }

    public static boolean isType(final String type, String[] types) {
        if (type == null) {
            return false;
        }
        for (String t : types) {
            if (type.equals(t)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVisual(String type) {
        return isImage(type) || isVideo(type);
    }

}
