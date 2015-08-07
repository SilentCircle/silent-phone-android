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

package com.silentcircle.contacts.providers.tests.testutils;

import com.silentcircle.contacts.utils.Clock;

public class MockClock extends Clock {
    /** Current time.  Only updated with advance(). */
    private long mCurrentTimeMillis;

    public void install() {
        Clock.injectInstance(this);

        mCurrentTimeMillis = 100000;
    }

    public void uninstall() {
        Clock.resetInstance();
    }

    @Override
    public long currentTimeMillis() {
        return mCurrentTimeMillis;
    }

    public void setCurrentTimeMillis(long time) {
        mCurrentTimeMillis = time;
    }

    public void advance() {
        mCurrentTimeMillis++;
    }
}
