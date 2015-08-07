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

package com.silentcircle.contacts.providers.tests;

import android.content.SharedPreferences;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * A programmable mock content provider.
 */
public class MockSharedPreferences implements SharedPreferences, SharedPreferences.Editor {

    private HashMap<String, Object> mValues = new HashMap<String, Object>();
    private HashMap<String, Object> mTempValues = new HashMap<String, Object>();

    public Editor edit() {
        return this;
    }

    public boolean contains(String key) {
        return mValues.containsKey(key);
    }

    public Map<String, ?> getAll() {
        return new HashMap<String, Object>(mValues);
    }

    public boolean getBoolean(String key, boolean defValue) {
        if (mValues.containsKey(key)) {
            return ((Boolean)mValues.get(key)).booleanValue();
        }
        return defValue;
    }

    public float getFloat(String key, float defValue) {
        if (mValues.containsKey(key)) {
            return ((Float)mValues.get(key)).floatValue();
        }
        return defValue;
    }

    public int getInt(String key, int defValue) {
        if (mValues.containsKey(key)) {
            return ((Integer)mValues.get(key)).intValue();
        }
        return defValue;
    }

    public long getLong(String key, long defValue) {
        if (mValues.containsKey(key)) {
            return ((Long)mValues.get(key)).longValue();
        }
        return defValue;
    }

    public String getString(String key, String defValue) {
        if (mValues.containsKey(key))
            return (String)mValues.get(key);
        return defValue;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (mValues.containsKey(key)) {
            return (Set<String>) mValues.get(key);
        }
        return defValues;
    }

    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        throw new UnsupportedOperationException();
    }

    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        throw new UnsupportedOperationException();
    }

    public Editor putBoolean(String key, boolean value) {
        mTempValues.put(key, Boolean.valueOf(value));
        return this;
    }

    public Editor putFloat(String key, float value) {
        mTempValues.put(key, value);
        return this;
    }

    public Editor putInt(String key, int value) {
        mTempValues.put(key, value);
        return this;
    }

    public Editor putLong(String key, long value) {
        mTempValues.put(key, value);
        return this;
    }

    public Editor putString(String key, String value) {
        mTempValues.put(key, value);
        return this;
    }

    public Editor putStringSet(String key, Set<String> values) {
        mTempValues.put(key, values);
        return this;
    }

    public Editor remove(String key) {
        mTempValues.remove(key);
        return this;
    }

    public Editor clear() {
        mTempValues.clear();
        return this;
    }

    @SuppressWarnings("unchecked")
    public boolean commit() {
        mValues = (HashMap<String, Object>)mTempValues.clone();
        return true;
    }

    public void apply() {
        commit();
    }
}
