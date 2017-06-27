/*
Copyright (C) 2013-2017, Silent Circle, LLC.  All rights reserved.

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
 * This  implementation is an edited version of original Android sources.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.silentcircle.vcard;

/**
 * <P>
 * The interface which should be implemented by the classes which have to analyze each
 * vCard entry minutely.
 * </P>
 * <P>
 * Here, there are several terms specific to vCard (and this library).
 * </P>
 * <P>
 * The term "entry" is one vCard representation in the input, which should start with "BEGIN:VCARD"
 * and end with "END:VCARD".
 * </P>
 * <P>
 * The term "property" is one line in vCard entry, which consists of "group", "property name",
 * "parameter(param) names and values", and "property values".
 * </P>
 * <P>
 * e.g. group1.propName;paramName1=paramValue1;paramName2=paramValue2;propertyValue1;propertyValue2...
 * </P>
 */
public interface VCardInterpreter {
    /**
     * Called when vCard interpretation started.
     */
    void onVCardStarted();

    /**
     * Called when vCard interpretation finished.
     */
    void onVCardEnded();

    /**
     * Called when parsing one vCard entry started.
     * More specifically, this method is called when "BEGIN:VCARD" is read.
     *
     * This may be called before {@link #onEntryEnded()} is called, as vCard 2.1 accepts nested
     * vCard.
     *
     * <code>
     * BEGIN:VCARD
     * BEGIN:VCARD
     * VERSION:2.1
     * N:test;;;;
     * END:VCARD
     * END:VCARD
     * </code>
     */
    void onEntryStarted();

    /**
     * Called when parsing one vCard entry ended.
     * More specifically, this method is called when "END:VCARD" is read.
     */
    void onEntryEnded();

    /**
     * Called when a property is created.
     */
    void onPropertyCreated(VCardProperty property);
}
