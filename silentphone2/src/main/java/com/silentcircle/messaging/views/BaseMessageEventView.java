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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.thread.Updater;
import com.silentcircle.messaging.util.DateUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.Updatable;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;

import java.util.concurrent.TimeUnit;

/**
 * Base for message event views, provides implementation for burn notice updates.
 */
public abstract class BaseMessageEventView extends CheckableConstraintLayout implements Updatable, HasChoiceMode {

    protected final Updater mUpdater;
    private boolean mInChoiceMode;

    public BaseMessageEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mUpdater = new Updater(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelUpdates();
        super.onDetachedFromWindow();
    }

    @Override
    public void update() {
        updateBurnNotice();
        scheduleNextUpdate();
    }

    @Override
    public boolean isInChoiceMode() {
        return mInChoiceMode;
    }

    @Override
    public void setInChoiceMode(boolean inChoiceMode) {
        if (inChoiceMode != mInChoiceMode) {
            mInChoiceMode = inChoiceMode;
        }
    }

    @Override
    public void setChecked(boolean checked) {
        // allow to change checked state only in action mode
        if (checked && !isInChoiceMode()) {
            return;
        }
        super.setChecked(checked);
    }

    public void cancelUpdates() {
        Handler handler = getHandler();
        if (handler != null) {
            handler.removeCallbacks(mUpdater);
        }
    }

    @Nullable
    public Message getMessage() {
        Object tag = getTag();
        return tag instanceof Message ? (Message) tag : null;
    }

    protected void scheduleNextUpdate() {
        Handler handler = getHandler();
        if (handler != null) {
            handler.removeCallbacks(mUpdater);
            handler.postDelayed(mUpdater, getNextUpdateTime());
        }
    }

    protected long getNextUpdateTime() {
        long nextUpdateTime = TimeUnit.DAYS.toMillis(1);
        Message message = getMessage();
        if (message != null) {
            nextUpdateTime = message.getExpirationTime() - System.currentTimeMillis();
            if (nextUpdateTime > TimeUnit.DAYS.toMillis(1)) {
                nextUpdateTime = Math.min(nextUpdateTime % TimeUnit.HOURS.toMillis(1), TimeUnit.HOURS.toMillis(1));
            }
            else if (nextUpdateTime > TimeUnit.HOURS.toMillis(1)) {
                nextUpdateTime = Math.min(nextUpdateTime % TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(1));
            }
            else {
                nextUpdateTime = TimeUnit.SECONDS.toMillis(1);
            }
        }
        return nextUpdateTime;
    }

    protected void updateBurnNotice() {

        final Message message = getMessage();
        final Context context = getContext();

        if (message == null || context == null) {
            return;
        }

        CharSequence text = null;
        boolean messageExpires = message.expires();

        if (messageExpires) {

            if (message.getState() == MessageStates.READ) {
                long millisecondsToExpiry = message.getExpirationTime() - System.currentTimeMillis();
                text = DateUtils.getShortTimeString(context, millisecondsToExpiry);
                /*
                 * Show burn animation for message if it is expired
                 */
                if (isExpired(message)) {
                    /* asynchronously remove message from repository and request view update */
                    MessageUtils.removeMessage(message);
                    /* cancel updates as well to avoid burning message multiple times */
                    cancelUpdates();
                    /* forget about the message */
                    setTag(null);
                    // TODO other tags
                }
            } else {
                text = DateUtils.getShortTimeString(context,
                        TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
            }
        }

        setBurnNotice(text, messageExpires ? VISIBLE : GONE);
    }

    protected boolean isExpired(@NonNull final Message message) {
        long millisecondsToExpiry = message.getExpirationTime() - System.currentTimeMillis();
        return (millisecondsToExpiry <= TimeUnit.SECONDS.toMillis(1)
                || message.getState() == MessageStates.BURNED);
    }

    public abstract void setBurnNotice(@Nullable CharSequence text, int visibility);

}
