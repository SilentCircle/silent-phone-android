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

/**
 * This interface defines a read-only interface for ordered collections used by list adapters.
 */
public interface ModelProvider {

    /**
     * Returns the number of items in this collection.
     *
     * @return the number of items in this collection.
     */
    public int getCount();

    /**
     * Returns the item occurring at the given position within this collection.
     *
     * @param position
     *            the 0-indexed position of the item within this collection.
     * @return the item occurring at the given position within this collection.
     */
    public Object getItem( int position );

    /**
     * Returns a unique identifier for the item occurring at the given position within this
     * collection. This identifier is not guaranteed to be globally unique -- it is only guaranteed
     * to be unique amongst other items within this collection.
     *
     * @param position
     *            the 0-indexed position of hte item within this collection whose identifier should
     *            be retrieved.
     * @return a unique identifier for the item occurring at the given position within this
     *         collection.
     */
    public long getItemId( int position );

}

