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

package com.silentcircle.silentphone2.util.tests;

import android.test.InstrumentationTestCase;
import android.util.Log;

import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Perform some simple tests for utility functions
 *
 * Created by werner on 25.11.15.
 */
public class SimpleTests extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DialerActivity.mDomainsToRemove = new String[] {"@domain.net", "%40domain.net"};
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testDomainRemoval() {
        String name = Utilities.getUsernameFromUriNumberSelective("tester@domain.net");
        assertEquals("tester", name);

        name = Utilities.getUsernameFromUriNumberSelective("tester_1%40domain.net");
        assertEquals("tester_1", name);

        name = Utilities.getUsernameFromUriNumberSelective("tester@xxx.net");
        assertEquals("tester@xxx.net", name);

        name = Utilities.getUsernameFromUriNumberSelective("tester%40xx.net");
        assertEquals("tester%40xx.net", name);
    }
}
