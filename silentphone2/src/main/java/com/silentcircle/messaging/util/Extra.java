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

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

public enum Extra {

    ID("ID"),
    DATA("DATA"),
    PARTNER("PARTNER"),
    FORCE("FORCE"),
    TEXT("TEXT"),
    PROGRESS("PROGRESS"),
    EXPORTED("EXPORTED"),
    STATE("STATE"),
    REASON("REASON"),
    ALIAS("ALIAS"),
    DISPLAY_NAME("DISPLAY_NAME");

    public final String INTENT_EXTRA_NAME_FORMAT = "com.silentcircle.messaging.extra.%s";

    private final String name;

    Extra(String name) {
        this.name = String.format(INTENT_EXTRA_NAME_FORMAT, name);
    }

    public Bundle flag(Bundle bundle) {
        if (bundle != null) {
            bundle.putBoolean(getName(), true);
        }
        return bundle;
    }

    public Intent flag(Intent intent) {
        if (intent != null) {
            intent.putExtra(getName(), true);
        }
        return intent;
    }

    public String from(Bundle bundle) {
        return bundle == null ? null : bundle.getString(getName());
    }

    public String from(Intent intent) {
        return intent == null ? null : intent.getStringExtra(getName());
    }

    public byte[] getByteArray(Bundle bundle) {
        return bundle == null ? null : bundle.getByteArray(getName());
    }

    public byte[] getByteArray(Intent intent) {
        return intent == null ? null : intent.getByteArrayExtra(getName());
    }

    public char[] getCharArray(Bundle bundle) {
        return bundle == null ? null : bundle.getCharArray(getName());
    }

    public char[] getCharArray(Intent intent) {
        return intent == null ? null : intent.getCharArrayExtra(getName());
    }

    public CharSequence getCharSequence(Bundle bundle) {
        return bundle == null ? null : bundle.getCharSequence(getName());
    }

    public CharSequence getCharSequence(Intent intent) {
        return intent == null ? null : intent.getCharSequenceExtra(getName());
    }

    public int getInt(Intent intent) {
        return intent == null ? 0 : intent.getIntExtra(getName(), 0);
    }

    public boolean getBoolean(Intent intent) {
        return intent != null && intent.getBooleanExtra(getName(), false);
    }

    public String getName() {
        return name;
    }

    public <T extends Parcelable> T getParcelable(Intent intent) {
        return intent == null ? null : (T) intent.getParcelableExtra(getName());
    }

    public boolean in(Bundle bundle) {
        return bundle != null && bundle.containsKey(getName());
    }

    public boolean in(Intent intent) {
        return intent != null && intent.hasExtra(getName());
    }

    public boolean test(Bundle bundle) {
        return bundle != null && bundle.getBoolean(getName(), false);
    }

    public boolean test(Intent intent) {
        return intent != null && intent.getBooleanExtra(getName(), false);
    }

    public Bundle to(Bundle bundle, byte[] value) {
        if (bundle != null) {
            bundle.putByteArray(getName(), value);
        }
        return bundle;
    }

    public Bundle to(Bundle bundle, char[] value) {
        if (bundle != null) {
            bundle.putCharArray(getName(), value);
        }
        return bundle;
    }

    public Bundle to(Bundle bundle, String value) {
        if (bundle != null) {
            bundle.putString(getName(), value);
        }
        return bundle;
    }

    public Intent to(Intent intent, byte[] value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, char[] value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, CharSequence value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, int value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, boolean value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, long value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, Parcelable value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, PendingIntent value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

    public Intent to(Intent intent, String value) {
        if (intent != null) {
            intent.putExtra(getName(), value);
        }
        return intent;
    }

}

