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

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.contacts.quickcontact;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.silentcircle.contacts.Collapser;

/**
 * Abstract definition of an action that could be performed, along with
 * string description and icon.
 */
public interface Action extends Collapser.Collapsible<Action> {
    public CharSequence getBody();
    public CharSequence getSubtitle();

    public String getMimeType();

    /** Returns an icon that can be clicked for the alternate action. */
    public Drawable getAlternateIcon();

    /** Returns the content description of the icon for the alternate action. */
    public String getAlternateIconDescription();

    /** Build an {@link android.content.Intent} that will perform this action. */
    public Intent getIntent();

    /** Build an {@link android.content.Intent} that will perform the alternate action. */
    public Intent getAlternateIntent();

    /** Checks if the contact data for this action is primary. */
    public Boolean isPrimary();

    /**
     * Returns a lookup (@link Uri) for the contact data item or null if there is no data item
     * corresponding to this row
     */
    public Uri getDataUri();

    /**
     * Returns the id of the contact data item or -1 of there is no data item corresponding to this
     * row
     */
    public long getDataId();

    /** Returns the presence of this item or -1 if it was never set */
    public int getPresence();
}
