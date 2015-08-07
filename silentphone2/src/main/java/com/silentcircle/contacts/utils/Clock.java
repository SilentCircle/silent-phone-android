/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License
 */

package com.silentcircle.contacts.utils;

import com.silentcircle.common.testing.NeededForTesting;

public class Clock {
    private static final Clock INSTANCE = new Clock();

    private static Clock sInstance = INSTANCE;

    public static final Clock getInstance() {
        return sInstance;
    }

    @NeededForTesting
    public static void injectInstance(Clock clock) {
        sInstance = clock;
    }

    @NeededForTesting
    public static void resetInstance() {
        sInstance = INSTANCE;
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
