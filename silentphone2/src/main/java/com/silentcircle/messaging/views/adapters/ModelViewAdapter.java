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
package com.silentcircle.messaging.views.adapters;


import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.Arrays;
import java.util.List;

/**
 * This adapter can represent heterogenous lists of model objects of varying types, mapping each
 * model with the appropriate view and recycling previously constructed views wherever possible.
 */
public class ModelViewAdapter extends BaseAdapter {

    private ModelProvider modelProvider;
    private final ViewType[] viewTypes;

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

    @Override
    public int getCount() {
        return modelProvider.getCount();
    }

    @Override
    public Object getItem(int position) {
        return modelProvider.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return modelProvider.getItemId(position);
    }

    @Override
    public int getItemViewType(int position) {
        return getItemViewTypeIndex(getItem(position));
    }

    private ViewType getItemViewType(Object item) {
        return viewTypes[getItemViewTypeIndex(item)];
    }

    private int getItemViewTypeIndex(Object item) {
        for (int i = 0; i < viewTypes.length; i++) {
            ViewType viewType = viewTypes[i];
            if (viewType.isModel(item)) {
                return i;
            }
        }
        return getViewTypeCount() - 1;
    }

    /**
     * Locates the model object occurring at the given position, maps it to an appropriate view
     * (recycling the given convertView if possible), and tags the view with the model object via
     * {@link View#setTag(Object)}.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        Object item = getItem(position);
        ViewType viewType = getItemViewType(item);
        View view = viewType.get(convertView, parent);

        view.setTag(item);

        return view;

    }

    @Override
    public int getViewTypeCount() {
        return viewTypes.length;
    }

    public void setModelProvider(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    public void setModels(List<?> models) {
        setModelProvider(new ListModelProvider(models));
    }

    public void setModels(Object... models) {
        setModelProvider(new ListModelProvider(models));
    }

}

