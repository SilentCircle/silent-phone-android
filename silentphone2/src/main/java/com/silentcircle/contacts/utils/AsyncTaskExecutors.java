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
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts.utils;

import android.os.AsyncTask;
import android.os.Looper;

import com.google.common.base.Preconditions;

import java.util.concurrent.Executor;

/**
 * Factory methods for creating AsyncTaskExecutors.
 * <p>
 * All of the factory methods on this class check first to see if you have set a static
 * {@link AsyncTaskExecutorFactory} set through the
 * {@link #setFactoryForTest(AsyncTaskExecutorFactory)} method, and if so delegate to that instead,
 * which is one way of injecting dependencies for testing classes whose construction cannot be
 * controlled such as {@link android.app.Activity}.
 */
public final class AsyncTaskExecutors {
    /**
     * A single instance of the {@link AsyncTaskExecutorFactory}, to which we delegate if it is
     * non-null, for injecting when testing.
     */
    private static AsyncTaskExecutorFactory mInjectedAsyncTaskExecutorFactory = null;

    /**
     * Creates an AsyncTaskExecutor that submits tasks to run with
     * {@link android.os.AsyncTask#SERIAL_EXECUTOR}.
     */
    public static AsyncTaskExecutor createAsyncTaskExecutor() {
        synchronized (AsyncTaskExecutors.class) {
            if (mInjectedAsyncTaskExecutorFactory != null) {
                return mInjectedAsyncTaskExecutorFactory.createAsyncTaskExecutor();
            }
            return new SimpleAsyncTaskExecutor(AsyncTask.SERIAL_EXECUTOR);
        }
    }

    /**
     * Creates an AsyncTaskExecutor that submits tasks to run with
     * {@link android.os.AsyncTask#THREAD_POOL_EXECUTOR}.
     */
    public static AsyncTaskExecutor createThreadPoolExecutor() {
        synchronized (AsyncTaskExecutors.class) {
            if (mInjectedAsyncTaskExecutorFactory != null) {
                return mInjectedAsyncTaskExecutorFactory.createAsyncTaskExecutor();
            }
            return new SimpleAsyncTaskExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /** Interface for creating AsyncTaskExecutor objects. */
    public interface AsyncTaskExecutorFactory {
        AsyncTaskExecutor createAsyncTaskExecutor();
    }

//    @NeededForTesting
    public static void setFactoryForTest(AsyncTaskExecutorFactory factory) {
        synchronized (AsyncTaskExecutors.class) {
            mInjectedAsyncTaskExecutorFactory = factory;
        }
    }

    public static void checkCalledFromUiThread() {
        Preconditions.checkState(Thread.currentThread() == Looper.getMainLooper().getThread(),
                "submit method must be called from ui thread, was: " + Thread.currentThread());
    }

    private static class SimpleAsyncTaskExecutor implements AsyncTaskExecutor {
        private final Executor mExecutor;

        public SimpleAsyncTaskExecutor(Executor executor) {
            mExecutor = executor;
        }

        @Override
        public <T> AsyncTask<T, ?, ?> submit(Object identifier, AsyncTask<T, ?, ?> task, T... params) {
            checkCalledFromUiThread();
            return task.executeOnExecutor(mExecutor, params);
        }
    }
}
