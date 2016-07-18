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
package com.silentcircle.silentphone2.list;


/**
 * Classes that want to receive callbacks in response to drag events should implement this
 * interface.
 */
public interface OnDragDropListener {
    /**
     * Called when a drag is started.
     * @param x X-coordinate of the drag event
     * @param y Y-coordinate of the drag event
     * @param view The contact tile which the drag was started on
     */
    void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view);

    /**
     * Called when a drag is in progress and the user moves the dragged contact to a
     * location.
     *
     * @param x X-coordinate of the drag event
     * @param y Y-coordinate of the drag event
     * @param view Contact tile in the ListView which is currently being displaced
     * by the dragged contact
     */
    void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view);

    /**
     * Called when a drag is completed (whether by dropping it somewhere or simply by dragging
     * the contact off the screen)
     * @param x X-coordinate of the drag event
     * @param y Y-coordinate of the drag event
     */
    void onDragFinished(int x, int y);

    /**
     * Called when a contact has been dropped on the remove view, indicating that the user
     * wants to remove this contact.
     */
    void onDroppedOnRemove();
}
