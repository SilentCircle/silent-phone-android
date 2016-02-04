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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * This class simplifies model/view mapping for adapters representing heterogenous lists by
 * encapsulating the logic for model/view mapping, recycling, and view inflation.
 */
public class ViewType {

    private final Class<?> viewType;
    private final int layoutResourceID;

    /**
     * @param viewType         The view class at the root of the specified layout.
     * @param layoutResourceID The layout resource ID to be inflated for this view type.
     */
    public ViewType(Class<?> viewType, int layoutResourceID) {
        this.viewType = viewType;
        this.layoutResourceID = layoutResourceID;
    }

    /**
     * Returns a view of this type, recycling the given convertView if it is of a compatible type.
     *
     * @param convertView the view to be recycled. May be null.
     * @param parent      the intended parent view, used during inflation for setting
     *                    the appropriate layout parameters.
     * @return A view of the type represented by this object.
     */
    public View get(View convertView, ViewGroup parent) {
        return isView(convertView) ? convertView : inflate(parent);
    }

    /**
     * Inflates a view of the type represented by this object. Consider using
     * {@link #get(View, ViewGroup)} instead, which supports view recycling.
     *
     * @param parent the intended parent view, used during inflation for setting the appropriate layout
     *               parameters.
     * @return A view of the type represented by this object.
     * @see {@link #get(View, ViewGroup)}
     */
    public View inflate(ViewGroup parent) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(layoutResourceID, parent, false);
    }

    /**
     * Checks the given model object to determine whether it can be represented with this view type.
     *
     * @param model the model object to check.
     * @return By default, this method always returns true. Override it to perform additional
     * validation.
     */
    public boolean isModel(Object model) {
        return true;
    }

    /**
     * Checks the given view to determine whether its class matches what we would expect a view of
     * this type to have.
     *
     * @param view the view whose type is to be checked.
     * @return true if the view is of the expected type. Otherwise, returns false.
     */
    private boolean isView(View view) {
        return view != null && viewType.isInstance(view);
    }

}
