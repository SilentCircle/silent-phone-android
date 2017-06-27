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


import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.common.util.StringUtils;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.BaseMessageEventView;
import com.silentcircle.silentphone2.R;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * This adapter can represent heterogenous lists of model objects of varying types, mapping each
 * model with the appropriate view and recycling previously constructed views wherever possible.
 */
public class ModelViewAdapter extends RecyclerView.Adapter<EventViewHolder> {

    public static final Boolean NEW_GROUP = true;

    public class ViewHolder extends EventViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }

        public void bind(Object item, int position) {
            View view = getView();
            view.setTag(R.id.group_conversation_flag, isGroupConversation());
            view.setTag(R.id.new_group_flag, null);
            view.setTag(R.id.sender_display_name, null);
            if (isNewGroup(position, item)) {
                view.setTag(R.id.new_group_flag, NEW_GROUP);
                if (isGroupConversation()) {
                    view.setTag(R.id.sender_display_name, getSenderName(item));
                }
            }
            view.setTag(R.id.view_position, position);
            view.setTag(item);
            if (item instanceof Event) {
                view.setTag(R.id.view_event_id, ((Event) item).getId());
            }
        }
    }

    private ModelProvider modelProvider;
    private final ViewType[] viewTypes;
    private boolean mIsGroupConversation;

    private final HashMap<String, CharSequence> mSenderNameCache = new HashMap<>();

    /**
     * Convenience constructor for using a list-backed model provider. This is equivalent to calling
     * {@code new ModelViewAdapter( new ListModelProvider( models ), viewTypes)}
     *
     * @param models    the backing list for this adapter.
     * @param viewTypes the model/view mappings for this adapter.
     */
    public ModelViewAdapter(List<?> models, ViewType[] viewTypes) {
        this(new ListModelProvider(models), viewTypes);
    }

    /**
     * @param modelProvider the data source for models used by this adapter.
     * @param viewTypes     the model/view mappings for this adapter.
     */
    public ModelViewAdapter(ModelProvider modelProvider, ViewType[] viewTypes) {
        /*
         * Assume that we will have stable unique ids.
         * Unused for now. Position based ids could be used.
         *
         * setHasStableIds(true);
         */
        this.modelProvider = modelProvider;
        this.viewTypes = viewTypes;
    }

    /**
     * Convenience constructor for using an array-backed model provider. This is equivalent to
     * calling {@code new ModelViewAdapter( new ListModelProvider( models ), viewTypes)}
     *
     * @param models    the backing array for this adapter.
     * @param viewTypes the model/view mappings for this adapter.
     */
    public ModelViewAdapter(Object[] models, ViewType[] viewTypes) {
        this(Arrays.asList(models), viewTypes);
    }

    public ViewType[] getViewTypes() {
        return viewTypes;
    }

    public ModelProvider getModelProvider() {
        return modelProvider;
    }

    // -----------------------------------

    @Override
    public EventViewHolder onCreateViewHolder(ViewGroup parent, int viewTypeIndex) {
        ViewType viewType = viewTypes[viewTypeIndex % viewTypes.length];
        View view = viewType.get(null, parent);
        view.setLayoutParams(
                new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(EventViewHolder holder, int position) {
        Object item = getItem(position);
        if (item != null) {
            final ViewHolder viewHolder = (ViewHolder) holder;
            viewHolder.bind(item, position);
        }
    }

    @Override
    public void onBindViewHolder(EventViewHolder holder, int position, List<Object> payloads) {
        Object item = (payloads != null && payloads.size() > 0) ? payloads.get(0) : getItem(position);
        if (item != null) {
            final ViewHolder viewHolder = (ViewHolder) holder;
            viewHolder.bind(item, position);
        }
    }

    public void onViewRecycled(EventViewHolder holder) {
        View view = holder.getView();
        if (view instanceof BaseMessageEventView) {
            ((BaseMessageEventView) view).cancelUpdates();
        }
        view.setTag(R.id.new_group_flag, null);
        view.setTag(R.id.sender_display_name, null);
        view.setTag(R.id.view_position, null);
        view.setTag(R.id.group_conversation_flag, null);
        view.setTag(null);

        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return getCount();
    }

    // -----------------------------------

    public int getCount() {
        return modelProvider != null ? modelProvider.getCount() : 0;
    }

    public Object getItem(int position) {
        return modelProvider.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return modelProvider.getItemId(position);
    }

    public int getViewTypeCount() {
        return viewTypes.length;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = getItem(position);
        int viewType = getItemViewTypeIndex(item);
        return viewType;
    }

    public void setIsGroupConversation(boolean isGroupConversation) {
        mIsGroupConversation = isGroupConversation;
    }

    public boolean isGroupConversation() {
        return mIsGroupConversation;
    }

    protected ViewType getItemViewType(Object item) {
        return viewTypes[getItemViewTypeIndex(item)];
    }

    protected int getItemViewTypeIndex(Object item) {
        for (int i = 0; i < viewTypes.length; i++) {
            ViewType viewType = viewTypes[i];
            if (viewType.isModel(item)) {
                return i;
            }
        }
        return getViewTypes().length - 1;
    }

    /**
     * Locates the model object occurring at the given position, maps it to an appropriate view
     * (recycling the given convertView if possible), and tags the view with the model object via
     * {@link View#setTag(Object)}.
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = getItem(position);
        ViewType viewType = getItemViewType(item);
        View view = viewType.get(convertView, parent);

        boolean isNewGroup = isNewGroup(position, item);
        view.setTag(R.id.new_group_flag, isNewGroup ? NEW_GROUP : null);
        view.setTag(R.id.group_conversation_flag, isGroupConversation());
        view.setTag(R.id.sender_display_name, isNewGroup ? getSenderName(item) : null);
        view.setTag(item);

        return view;
    }

    public void setModelProvider(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    public void setModels(List<?> models) {
        setModelProvider(new ListModelProvider(models));
        notifyDataSetChanged();
    }

    public List<?> getModels() {
        return getModelProvider().getItems();
    }

    public void setModels(Object... models) {
        setModelProvider(new ListModelProvider(models));
    }

    /**
     * Check whether position is a position for item from models.
     */
    public boolean isDataPosition(int position) {
        return true;
    }

    /**
     * Translate given list position to a corresponding position for model item.
     */
    public int getDataPosition(int position) {
        return position;
    }

    /**
     * Translate given model position to a corresponding position for list item.
     */
    public int getScreenPosition(int position) {
        return position;
    }

    public boolean isNewGroup(int position) {
        return isNewGroup(position, getItem(position));
    }

    public void resetNameCache() {
        synchronized (mSenderNameCache) {
            mSenderNameCache.clear();
        }
    }

    private boolean isNewGroup(int position, @Nullable final Object item) {
        boolean result = false;
        if (item != null && item instanceof Message) {
            int i = position - 1;
            if (i <= 0) {
                result = true;
            }
            while (i > 0) {
                Object previousItem = getItem(i);
                if (previousItem instanceof Message) {
                    boolean sameGroup = item.getClass().equals(previousItem.getClass());
                    if (sameGroup) {
                        sameGroup = ((Message) item).getSender().equals(((Message) previousItem).getSender());
                    }
                    if (!sameGroup) {
                        result = true;
                    }
                    break;
                }
                else if (previousItem instanceof InfoEvent) {
                    result = !item.getClass().equals(previousItem.getClass());
                }
                i--;
            }
        }
        return result;
    }

    @Nullable
    private CharSequence getSenderName(Object object) {
        CharSequence result = null;
        if (object instanceof IncomingMessage) {
            synchronized (mSenderNameCache) {
                String sender = ((IncomingMessage) object).getSender();
                result = mSenderNameCache.get(sender);
                if (TextUtils.isEmpty(result)) {
                    CharSequence name = StringUtils.formatDisplayName(MessageUtils.getDisplayName(sender));
                    if (!TextUtils.isEmpty(name)) {
                        result = name;
                        mSenderNameCache.put(sender, result);
                    } else {
                        result = sender;
                        // don't cache sender (for now) to show name when v1/user is updated
                    }
                }
            }
        }
        return result;
    }

}

