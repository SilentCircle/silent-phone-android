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

package com.silentcircle.contacts;

import java.util.Iterator;
import java.util.List;

/**
 * Class used for collapsing data items into groups of similar items. The data items that should be
 * collapsible should implement the Collapsible interface. The class also contains a utility
 * function that takes an ArrayList of items and returns a list of the same items collapsed into
 * groups.
 */
public final class Collapser {

    /*
     * This utility class cannot be instantiated.
     */
    private Collapser() {}

    /*
     * Interface implemented by data types that can be collapsed into groups of similar data. This
     * can be used for example to collapse similar contact data items into a single item.
     */
    public interface Collapsible<T> {
        public boolean collapseWith(T t);
        public boolean shouldCollapseWith(T t);
    }

    /**
     * Collapses a list of Collapsible items into a list of collapsed items. Items are collapsed
     * if {@link Collapsible#shouldCollapseWith(Object)} returns true, and are collapsed
     * through the {@Link Collapsible#collapseWith(Object)} function implemented by the data item.
     *
     * @param list List of Objects of type <T extends Collapsible<T>> to be collapsed.
     */
    public static <T extends Collapsible<T>> void collapseList(List<T> list) {

        int listSize = list.size();

        for (int i = 0; i < listSize; i++) {
            T iItem = list.get(i);
            if (iItem != null) {
                for (int j = i + 1; j < listSize; j++) {
                    T jItem = list.get(j);
                    if (jItem != null) {
                        if (iItem.shouldCollapseWith(jItem)) {
                            iItem.collapseWith(jItem);
                            list.set(j, null);
                        }
                    }
                }
            }
        }

        // Remove the null items
        Iterator<T> itr = list.iterator();
        while (itr.hasNext()) {
            if (itr.next() == null) {
                itr.remove();
            }
        }

    }
}
