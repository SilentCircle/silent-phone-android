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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.test.mock.MockPackageManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Mock {@link android.content.pm.PackageManager} that knows about a specific set of packages
 * to help test security models. Because {@link android.os.Binder#getCallingUid()}
 * can't be mocked, you'll have to find your mock-UID manually using your
 * {@link android.content.Context#getPackageName()}.
 */
public class ContactsMockPackageManager extends MockPackageManager {
    private final HashMap<Integer, String> mForward = new HashMap<Integer, String>();
    private final HashMap<String, Integer> mReverse = new HashMap<String, Integer>();
    private List<PackageInfo> mPackages;

    public ContactsMockPackageManager() {
    }

    /**
     * Add a UID-to-package mapping, which is then stored internally.
     */
    public void addPackage(int packageUid, String packageName) {
        mForward.put(packageUid, packageName);
        mReverse.put(packageName, packageUid);
    }

    @Override
    public String getNameForUid(int uid) {
        return "name-for-uid";
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        if (mPackages != null) {
            return new String[] { mPackages.get(0).packageName };
        } else {
            return new String[] { ContactsActor.sCallingPackage };
        }
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags) {
        ApplicationInfo info = new ApplicationInfo();
        Integer uid = mReverse.get(packageName);
        info.uid = (uid != null) ? uid : -1;
        return info;
    }

    public void setInstalledPackages(List<PackageInfo> packages) {
        this.mPackages = packages;
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags) {
        return mPackages;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        for (PackageInfo info : mPackages) {
            if (info.packageName.equals(packageName)) {
                return info;
            }
        }
        throw new NameNotFoundException();
    }

    @Override
    public Resources getResourcesForApplication(String appPackageName) {
        return new ContactsMockResources();
    }

    @Override
    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags) {
        final List<ProviderInfo> ret = new ArrayList<ProviderInfo>();
        if (mPackages == null) return ret;
        for (PackageInfo packageInfo : mPackages) {
            if (packageInfo.providers == null) continue;
            for (ProviderInfo providerInfo : packageInfo.providers) {
                ret.add(providerInfo);
            }
        }
        return ret;
    }
}
