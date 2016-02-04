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

/*
 * This  implementation is an edited version of original Android sources.
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
package com.silentcircle.contacts.vcard;

import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A base processor class. One instance processes vCard one import/export request (imports a given
 * vCard or exports a vCard). Expected to be used with {@link java.util.concurrent.ExecutorService}.
 *
 * This instance starts itself with {@link #run()} method, and can be cancelled with
 * {@link #cancel(boolean)}. Users can check the processor's status using {@link #isCancelled()}
 * and {@link #isDone()} asynchronously.
 *
 * {@link #get()} and {@link #get(long, java.util.concurrent.TimeUnit)}, which are form {@link java.util.concurrent.Future}, aren't
 * supported and {@link UnsupportedOperationException} will be just thrown when they are called.
 */
public abstract class ProcessorBase implements RunnableFuture<Object> {

    /**
     * @return the type of the processor. Must be {@link VCardService#TYPE_IMPORT} or
     * {@link VCardService#TYPE_EXPORT}.
     */
    public abstract int getType();

    @Override
    public abstract void run();

    /**
     * Cancels this operation.
     *
     * @param mayInterruptIfRunning ignored. When this method is called, the instance
     * stops processing and finish itself even if the thread is running.
     *
     * @see java.util.concurrent.Future#cancel(boolean)
     */
    @Override
    public abstract boolean cancel(boolean mayInterruptIfRunning);
    @Override
    public abstract boolean isCancelled();
    @Override
    public abstract boolean isDone();

    /**
     * Just throws {@link UnsupportedOperationException}.
     */
    @Override
    public final Object get() {
        throw new UnsupportedOperationException();
    }

    /**
     * Just throws {@link UnsupportedOperationException}.
     */
    @Override
    public final Object get(long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }
}
