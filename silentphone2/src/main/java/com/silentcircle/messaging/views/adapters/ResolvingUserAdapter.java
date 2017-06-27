/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.views.adapters;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;

import com.google.common.base.Objects;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Adapter to resolve and show group members/users
 */
public abstract class ResolvingUserAdapter<T> extends RecyclerView.Adapter implements View.OnClickListener,
        View.OnLongClickListener {

    public static final String TAG = ResolvingUserAdapter.class.getSimpleName();

    public interface OnItemClickListener {

        void onItemClick(@NonNull View view, int position, @NonNull Object item);

        void onItemLongClick(@NonNull View view, int position, @NonNull Object item);
    }

    /* Message to notify adapter that all requests are processed */
    public static final int REQUESTS_PROCESSED = 0;
    /* Message to notify adapter that views should be re-drawn */
    @SuppressWarnings("WeakerAccess")
    public static final int REDRAW = 1;
    /* Message to notify adapter that a view should be re-drawn */
    @SuppressWarnings("WeakerAccess")
    public static final int REDRAW_ITEM = 2;

    public abstract static class LookupRequest {
        /** The number to look-up. */
        public final String name;
        public final int position;

        public LookupRequest(String number, int position) {
            this.name = number;
            this.position = position;
        }

        public abstract boolean onRequestResult();
    }

    @SuppressWarnings("WeakerAccess")
    public static class ContactEntryRequest extends LookupRequest {
        /** The cached contact information stored in the call log. */
        public final ContactEntry contactEntry;

        public ContactEntryRequest(String number, ContactEntry contactEntry, int position) {
            super(number, position);
            this.contactEntry = contactEntry;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof ContactEntryRequest)) return false;

            ContactEntryRequest other = (ContactEntryRequest) obj;
            return Objects.equal(name, other.name);
        }

        @Override
        public int hashCode() {
            return name == null ? 0 : name.hashCode();
        }

        public boolean onRequestResult() {
            ContactsCache.getContactEntry(name);
            return true;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class RedrawHandler extends Handler {
        private final WeakReference<ResolvingUserAdapter> mAdapter;

        public RedrawHandler(ResolvingUserAdapter adapter) {
            mAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            ResolvingUserAdapter adapter = mAdapter.get();
            if (adapter == null) {
                return;
            }

            switch (msg.what) {
                case REQUESTS_PROCESSED:
                    adapter.onRequestsProcessed();
                    break;
                case REDRAW:
                    adapter.notifyDataSetChanged();
                    break;
                case REDRAW_ITEM:
                    adapter.notifyItemChanged(msg.arg1);
                    break;
            }
        }
    }

    /*
     * Handles requests for contact (display) name
     */
    @SuppressWarnings("WeakerAccess")
    public class QueryThread extends Thread {
        private volatile boolean mDone = false;

        public QueryThread() {
            super("CallLogAdapter.QueryThread");
        }

        public void stopProcessing() {
            mDone = true;
        }

        @Override
        @WorkerThread
        public void run() {
            while (true) {
                // If thread is finished, return immediately.
                if (mDone) {
                    return;
                }

                try {
                    /* boolean needRedraw = false; */
                    for (LookupRequest req = mRequests.take(); req != null && !mDone; req = mRequests.poll()) {
                        if (ConfigurationUtilities.mTrace) {
                            Log.d(TAG, "QueryThread#run handling request, mDone: " + mDone
                                    + ", req: " + req.name
                                    + ", Thread id: " + android.os.Process.myTid()
                                    + ", requests: " + mRequests.size());
                        }
                        boolean needRedraw = req.onRequestResult();

                        if (needRedraw) {
                            android.os.Message message = android.os.Message.obtain();
                            message.arg1 = req.position;
                            message.what = REDRAW_ITEM;
                            mRedrawHandler.sendMessage(message);
                        }

                        if (mRequests.size() == 0) {
                            mRedrawHandler.sendEmptyMessage(REQUESTS_PROCESSED);
                        }
                    }
                    /*
                    if (needRedraw) {
                        mRedrawHandler.sendEmptyMessage(REDRAW);
                    }
                     */

                } catch (InterruptedException ie) {
                    // Ignore, and attempt to continue processing requests.
                }
            }
        }
    }

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final ContactPhotoManagerNew mContactPhotoManager;
    private final LinkedBlockingQueue<LookupRequest> mRequests;
    private List<T> mItems = new ArrayList<>();
    private Handler mRedrawHandler;
    private QueryThread mQueryThread;

    private OnItemClickListener mListener;

    public ResolvingUserAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mContactPhotoManager =
                ContactPhotoManagerNew.getInstance(mContext.getApplicationContext());
        mRequests = new LinkedBlockingQueue<>();
        mRedrawHandler = new RedrawHandler(this);
    }

    public void setItems(List<T> items) {
        mItems = items;
    }

    public Object getItem(int position) {
        return (mItems != null && position >= 0 && position < mItems.size())
                ? mItems.get(position) : null;
    }

    @Override
    public int getItemCount() {
        return mItems != null ? mItems.size() : 0;
    }

    @Override
    public void onClick(View view) {
        if (mListener != null && view != null) {
            mListener.onItemClick(view, (Integer) view.getTag(R.id.position), view.getTag());
        }
    }

    @Override
    public boolean onLongClick(View view) {
        boolean result = false;
        if (mListener != null && view != null) {
            mListener.onItemLongClick(view, (Integer) view.getTag(), view.getTag());
            result = true;
        }
        return result;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    public synchronized void startRequestProcessing() {
        if (mQueryThread != null) {
            return;
        }

        mQueryThread = createQueryThread();
        mQueryThread.setPriority(Thread.MIN_PRIORITY);
        mQueryThread.start();
    }

    public synchronized void stopRequestProcessing() {
        mRedrawHandler.removeMessages(REDRAW);
        if (mQueryThread != null) {
            mQueryThread.stopProcessing();
            mQueryThread.interrupt();
            mQueryThread = null;
        }
    }

    public void onRequestsProcessed() {
    }

    protected QueryThread createQueryThread() {
        return new QueryThread();
    }

    protected LayoutInflater getLayoutInflater() {
        return mInflater;
    }

    protected ContactPhotoManagerNew getPhotoManager() {
        return mContactPhotoManager;
    }

    protected void doContactRequest(String item, int position, ContactEntry contactEntry) {
        ContactEntryRequest request =
                new ContactEntryRequest(item, contactEntry, position);
        addRequest(request);
    }

    protected void addRequest(LookupRequest request) {
        synchronized (mRequests) {
            if (!mRequests.contains(request)) {
                mRequests.offer(request);
                mRequests.notifyAll();
            }
        }
        startRequestProcessing();
    }

}
