package it.sephiroth.android.library.imagezoom;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

/**
 * ImageTouchView which can be dragged to a position around a crop rectangle.
 */
public class ImageViewTouchPanable extends ImageViewTouch {

    protected RectF mCropRect = new RectF(0.0f, 0.0f, 0.0f, 0.0f);

    public ImageViewTouchPanable(Context context) {
        this(context, null);
    }

    public ImageViewTouchPanable(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!mScrollEnabled) return false;

        if (e1 == null || e2 == null) return false;
        if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1) return false;
        if (mScaleDetector.isInProgress()) return false;

        mUserScaled = true;
        scrollBy(-distanceX, -distanceY);
        invalidate();
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    public void setCropRect(RectF cropRect) {
        if (cropRect != null) {
            mCropRect = cropRect;
        }
    }

    @Override
    protected void panBy(double dx, double dy) {
        RectF rect = getBitmapRect();
        mScrollRect.set((float) dx, (float) dy, 0, 0);
        updateRect(rect, mScrollRect);
        postTranslate(mScrollRect.left, mScrollRect.top);
    }

    @Override
    protected void updateRect(RectF bitmapRect, RectF scrollRect) {
        if (bitmapRect == null) {
            return;
        }

        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "updateRect w, h " + getCurrentWidth() + ", " + getCurrentHeight());
            Log.d(LOG_TAG, "updateRect   bitmapRect " + bitmapRect.left + ", " + bitmapRect.top
                    + ", " + bitmapRect.right + ", " + bitmapRect.bottom);
            Log.d(LOG_TAG, "updateRect     cropRect " + mCropRect.left + ", " + mCropRect.top
                    + ", " + mCropRect.right + ", " + mCropRect.bottom);
            Log.d(LOG_TAG, "updateRect b scrollRect " + scrollRect.left + ", " + scrollRect.top
                    + ", " + scrollRect.right + ", " + scrollRect.bottom);
        }

        float bitmapWidth = bitmapRect.right - bitmapRect.left;
        float bitmapHeight = bitmapRect.bottom - bitmapRect.top;
        float cropRectWidth = mCropRect.right - mCropRect.left;
        float cropRectHeight = mCropRect.bottom - mCropRect.top;

        // FIXME normalize
        if (bitmapHeight <= cropRectHeight) {
            if (bitmapRect.top + scrollRect.top < mCropRect.top) {
                scrollRect.top = (int) (mCropRect.top - bitmapRect.top);
            }
            else if (bitmapRect.bottom + scrollRect.top > mCropRect.bottom) {
                scrollRect.top = (int) (mCropRect.bottom - bitmapRect.bottom);
            }
        }
        else {
            if (bitmapRect.top + scrollRect.top < mCropRect.top) {
                if (scrollRect.top <= 0) {
                    if (bitmapRect.bottom > mCropRect.bottom) {
                        // move up by as much as bottom is below lower crop border
                        scrollRect.top = Math.max(scrollRect.top, mCropRect.bottom - bitmapRect.bottom);
                    }
                    else {
                        // move down by as much as bottom is above lower crop border
                        scrollRect.top = mCropRect.bottom - bitmapRect.bottom;
                    }
                }
            }
            else if (bitmapRect.bottom + scrollRect.top > mCropRect.bottom) {
                if (scrollRect.top >= 0) {
                    if (bitmapRect.top < mCropRect.top) {
                        // move down by as much as top is over upper crop border
                        scrollRect.top = Math.min(scrollRect.top, mCropRect.top - bitmapRect.top);
                    }
                    else {
                        // move up by as much as top is below of upper crop border
                        scrollRect.top = mCropRect.top - bitmapRect.top;
                    }
                }
            }
        }

        if (bitmapWidth <= cropRectWidth) {
            if (bitmapRect.left + scrollRect.left < mCropRect.left) {
                scrollRect.left = (int) (mCropRect.left - bitmapRect.left);
            }
            else if (bitmapRect.right + scrollRect.left > mCropRect.right) {
                scrollRect.left = (int) (mCropRect.right - bitmapRect.right);
            }
        }
        else {
            if (bitmapRect.left + scrollRect.left < mCropRect.left) {
                if (scrollRect.left <= 0) {
                    if (bitmapRect.right > mCropRect.right) {
                        // allow move left as much as right is over right side of screen
                        scrollRect.left = Math.max(scrollRect.left, mCropRect.right - bitmapRect.right);
                    }
                    else {
                        // move right by as much as right is to left of right crop border
                        scrollRect.left = mCropRect.right - bitmapRect.right;
                    }
                }
            }
            else if (bitmapRect.right + scrollRect.left > mCropRect.right) {
                if (scrollRect.left >= 0) {
                    if (bitmapRect.left < mCropRect.left) {
                        // allow move right by as much as left is over left side of screen
                        scrollRect.left = Math.min(scrollRect.left, mCropRect.left - bitmapRect.left);
                    }
                    else {
                        // move left by as much as left is to right of left crop border
                        scrollRect.left = mCropRect.left - bitmapRect.left;
                    }
                }
            }
        }

        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "updateRect a scrollRect " + scrollRect.left + ", " + scrollRect.top
                    + ", " + scrollRect.right + ", " + scrollRect.bottom);
        }
    }

    @Override
    protected void zoomTo(float scale, float centerX, float centerY) {
        if (LOG_ENABLED) {
            Log.d(LOG_TAG, "zoomTo old scale: " + getScale()
                    + ", max scale: " + getMaxScale()
                    + ", min scale: " + getMinScale()
                    + ", centerX: " + centerX
                    + ", centerY: " + centerY);
        }

        if (scale > getMaxScale()) scale = getMaxScale();
        if (scale < getMinScale()) scale = getMinScale();

        float oldScale = getScale();
        float deltaScale = scale / oldScale;
        postScale(deltaScale, centerX, centerY);
        onZoom(getScale());
        panBy(0, 0);
    }

    protected float computeMinZoom() {
        float scale = 1F;

        if (LOG_ENABLED) {
            Log.i(LOG_TAG, "computeMinZoom: " + scale);
        }

        return scale;
    }
}
