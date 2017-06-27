/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.silentcircle.common.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.fenchtose.tooltip.Tooltip;
import com.fenchtose.tooltip.TooltipAnimation;
import com.getkeepsafe.taptargetview.TapTarget;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.ChooserBuilder;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.SettingsFragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Provides static functions to work with views
 */
public class ViewUtil {

    private static final String TAG = ViewUtil.class.getSimpleName();

    public static final int ROTATE_90 = 90;
    public static final int ROTATE_180 = 180;
    public static final int ROTATE_270 = 270;

    /**
     * Wrapper for LinkMovementMethod to avoid crashes when a link is pressed
     * on a device without any web browser.
     *
     * On some phones this would be handled by URLSpan which won't throw exception
     * and just add a log entry about viewer activity absence.
     */
    public static class MovementCheck extends LinkMovementMethod {

        private final String mMessage;
        private final View mAnchorView;

        public MovementCheck(@NonNull View view, @NonNull String message) {
            super();
            mAnchorView = view;
            mMessage = message;
        }

        public MovementCheck(@NonNull Context context, @NonNull View view, int messageId) {
            super();
            mAnchorView = view;
            mMessage = context.getResources().getString(messageId);
        }

        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                return super.onTouchEvent(widget, buffer, event);
            } catch (Exception ex) {
                Snackbar.make(mAnchorView, mMessage, Snackbar.LENGTH_LONG).show();
                return true;
            }
        }
    }

    private static int statusBarHeight = 0;

    private ViewUtil() {
    }

    /**
     * Returns the width as specified in the LayoutParams
     *
     * @throws IllegalStateException Thrown if the view's width is unknown before a layout pass
     *                               s
     */
    public static int getConstantPreLayoutWidth(View view) {
        // We haven't been layed out yet, so get the size from the LayoutParams
        final ViewGroup.LayoutParams p = view.getLayoutParams();
        if (p.width < 0) {
            throw new IllegalStateException("Expecting view's width to be a constant rather " +
                    "than a result of the layout pass");
        }
        return p.width;
    }

    /**
     * Returns a boolean indicating whether or not the view's layout direction is RTL
     *
     * @param view - A valid view
     * @return True if the view's layout direction is RTL
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean isViewLayoutRtl(View view) {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) &&
                (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
    }

    @SuppressLint("NewApi")
    private static final ViewOutlineProvider OVAL_OUTLINE_PROVIDER;
    @SuppressLint("NewApi")
    private static final ViewOutlineProvider RECT_OUTLINE_PROVIDER;
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            OVAL_OUTLINE_PROVIDER = new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            };
            RECT_OUTLINE_PROVIDER = new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRect(0, 0, view.getWidth(), view.getHeight());
                }
            };
        }
        else {
            OVAL_OUTLINE_PROVIDER = RECT_OUTLINE_PROVIDER = null;
        }
    }

    /**
     * Adds a rectangular outline to a view. This can be useful when you want to add a shadow
     * to a transparent view. See b/16856049.
     * @param view view that the outline is added to
     * @param res The resources file.
     */
    public static void addRectangularOutlineProvider(View view, Resources res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            view.setOutlineProvider(RECT_OUTLINE_PROVIDER);
    }

    /**
     * Configures the floating action button, clipping it to a circle and setting its translation z.
     * @param view The float action button's view.
     * @param res The resources file.
     */
    public static void setupFloatingActionButton(View view, Resources res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(OVAL_OUTLINE_PROVIDER);
            view.setTranslationZ(res.getDimensionPixelSize(R.dimen.floating_action_button_translation_z));
        }
    }

    /**
     * Adds padding to the bottom of the given {@link android.widget.ListView} so that the floating action button
     * does not obscure any content.
     *
     * @param viewGroup to add the padding to
     * @param res valid resources object
     */
    public static void addBottomPaddingToListViewForFab(ViewGroup viewGroup, Resources res) {
        final int fabPadding = res.getDimensionPixelSize(
                R.dimen.floating_action_button_list_bottom_padding);
        addBottomPaddingToListView(viewGroup, fabPadding);
    }

    public static void addBottomPaddingToListView(ViewGroup viewGroup, int padding) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            viewGroup.setPaddingRelative(viewGroup.getPaddingStart(), viewGroup.getPaddingTop(),
                    viewGroup.getPaddingEnd(), viewGroup.getPaddingBottom() + padding);
        else
            viewGroup.setPadding(viewGroup.getPaddingLeft(), viewGroup.getPaddingTop(),
                    viewGroup.getPaddingRight(), viewGroup.getPaddingBottom() + padding);

        viewGroup.setClipToPadding(false);
    }

    /**
     * Sets view's opacity.
     *
     * @param view View to set opacity to.
     * @param alpha The opacity of the view.
     *
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setAlpha(View view, float alpha) {
        if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            view.setAlpha(alpha);
        }
    }

    /**
     * Set drawable on the left of text view.
     *
     * @param view Text view to set drawable to.
     * @param drawableResourceID Drawable id to set to text view.
     */
    public static void setDrawableStart(TextView view, int drawableResourceID) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            setDrawableLeft(view, drawableResourceID);
        } else {
            setDrawableStartForReal(view, drawableResourceID);
        }
    }

    private static void setDrawableLeft(TextView view, int drawableResourceID) {
        view.setCompoundDrawablesWithIntrinsicBounds(drawableResourceID, 0, 0, 0);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void setDrawableStartForReal(TextView view, int drawableResourceID) {
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableResourceID, 0, 0, 0);
    }

    public static Intent createIntentForLinks(TextView view) {

        CharSequence text = view.getText();

        if (text instanceof Spannable) {

            Spannable stext = (Spannable) text;
            URLSpan[] spans = stext.getSpans(0, stext.length(), URLSpan.class);

            if (spans != null && spans.length > 0) {
                ChooserBuilder chooser = new ChooserBuilder(view.getContext());
                chooser.label(R.string.view);
                for (URLSpan span : spans) {
                    String url = span.getURL();
                    CharSequence label = stext.subSequence(stext.getSpanStart(span), stext.getSpanEnd(span));
                    chooser.intent(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), label);
                }
                return chooser.build();

            }
        }
        return null;
    }

    public static boolean startActivityForTextLinks(final Context context, final TextView view) {
        boolean linksHandled = false;
        Intent links = createIntentForLinks(view);
        if (links != null) {
            linksHandled = true;
            try {
                context.startActivity(links);
            }
            catch (Exception e) {
                Log.d(TAG, "Could not start activity for links in message text: " + e.getMessage());
                Toast.makeText(context, R.string.messaging_could_not_view_link, Toast.LENGTH_SHORT).show();
            }
        }
        return linksHandled;
    }

    /**
     * Returns screen dimensions in passed Point structure
     */
    public static void getScreenDimensions(final Context context, final Point size) {
        if (context == null || size == null) {
            return;
        }

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        display.getSize(size);
    }

    public static Point getScreenDimensions(final Context context) {
        Point point = new Point();
        getScreenDimensions(context, point);
        return point;
    }

    public static int getStatusBarHeight(Context context) {
        if (statusBarHeight == 0) {
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
            }
        }
        return statusBarHeight;
    }

    public static Rect getViewInset(View view) {
        Point screenSize = getScreenDimensions(view.getContext());
        if (view == null || Build.VERSION.SDK_INT < 21 || view.getHeight() == screenSize.y || view.getHeight() == screenSize.y - statusBarHeight) {
            return null;
        }
        try {
            Field mAttachInfoField = View.class.getDeclaredField("mAttachInfo");
            mAttachInfoField.setAccessible(true);
            Object mAttachInfo = mAttachInfoField.get(view);
            if (mAttachInfo != null) {
                Field mStableInsetsField = mAttachInfo.getClass().getDeclaredField("mStableInsets");
                mStableInsetsField.setAccessible(true);
                Rect insets = (Rect)mStableInsetsField.get(mAttachInfo);
                return insets;
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Convert dp values to pixels
     * @param value size in dp
     * @return size in pixels
     */
    public static int dp(float value, Context context) {
        if (value == 0) {
            return 0;
        }
        return (int) Math.ceil(density(context) * value);
    }

    public static float density(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    /**
     * Sets state (enabled/disabled) for viewGroup and its children. Recursively set state
     * for childs which are viewgroups themselves.
     *
     * @param viewGroup ViewGroup for which to set state.
     * @param enabled State to set.
     */
    public static void setEnabled(@Nullable final ViewGroup viewGroup, boolean enabled) {
        if (viewGroup == null) {
            return;
        }
        viewGroup.setEnabled(enabled);
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View view = viewGroup.getChildAt(i);
            view.setEnabled(enabled);
            if (view instanceof ViewGroup) {
                setEnabled((ViewGroup) view, enabled);
            }
        }
    }

    /**
     * Finds JPEG image rotation flags from exif and returns matrix to be used to rotate image.
     *
     * @param fileName JPEG image file name.
     *
     * @return Matrix to use in image rotate transformation or null if parsing failed.
     */
    public static Matrix getRotationMatrixFromExif(final String fileName) {
        Matrix matrix = null;
        try {
            ExifInterface exif = new ExifInterface(fileName);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int rotate = 0;

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = ROTATE_90;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = ROTATE_180;
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = ROTATE_270;
                    break;
            }

            if (rotate != 0) {
                matrix = new Matrix();
                matrix.preRotate(rotate);
            }
        }
        catch (IOException e) {
            Log.i(TAG, "Failed to determine image flags from file " + fileName);
        }
        return matrix;
    }

    public static Matrix getRotationMatrixFromExif(final Context context, final Uri uri) {
        Matrix matrix = null;
        File tmpFile = IOUtils.writeUriContentToTempFile(context, uri);
        if (tmpFile != null) {
            matrix = ViewUtil.getRotationMatrixFromExif(tmpFile.getAbsolutePath());
            tmpFile.delete();
        }
        return matrix;
    }

    public static void animateImageChange(@NonNull final Context context,
                                          @NonNull final ImageView imageView, final int newImage, final ImageView.ScaleType newScaleType, final int duration) {
        final Animation animOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
        final Animation animIn  = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
        if (duration >= 0) {
            animOut.setDuration(duration);
            animIn.setDuration(duration);
        }

        animOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                imageView.setImageResource(newImage);
                imageView.setScaleType(newScaleType);
                animIn.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                    }
                });
                imageView.startAnimation(animIn);
            }
        });
        imageView.startAnimation(animOut);
    }

    public static void scaleToInvisible(View view) {
        ScaleAnimation animate = new ScaleAnimation(1.0f, 0.0f, 1.0f, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animate.setDuration(100);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        view.setVisibility(View.GONE);
    }

    public static void scaleToVisible(View view) {
        ScaleAnimation animate = new ScaleAnimation(0.0f, 1.0f, 0.5f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animate.setDuration(100);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        view.setVisibility(View.VISIBLE);
    }

    public static void setViewWidthHeight(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    public static void setViewHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }

    /**
     * Cut a circular image from provided bitmap.
     *
     * @param bitmap Bitmap from which to cut the circle.
     *
     */
    @Nullable
    public static Bitmap getCircularBitmap(@Nullable final Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int dimens = width;
        if (width > height) {
            dimens = height;
        }
        Bitmap output = Bitmap.createBitmap(dimens, dimens, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final float radius = width / 2.0f;
        final int color = 0xffff00ff;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, width, height);

        canvas.drawARGB(0, 0, 0, 0);
        paint.setAntiAlias(true);
        paint.setColor(color);
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    public static void setBlockScreenshots(final Activity activity) {
        if (activity == null) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean areScreenshotsDisabled = prefs.getBoolean(SettingsFragment.BLOCK_SCREENSHOTS, false);

        if (areScreenshotsDisabled) {
            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
        else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    public static void tintMenuIcons(@NonNull Context context, @NonNull Menu menu) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.sp_actionbar_title_text_color, typedValue, true);
        if (typedValue.resourceId > 0) {
            int color = ContextCompat.getColor(context, typedValue.resourceId);
            tintMenuIcons(menu, color);
        }
    }

    public static void tintMenuIcons(@NonNull Menu menu, final int color) {
        for (int i = 0; i < menu.size(); ++i) {
            final MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                icon.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                item.setIcon(icon);
            }
        }
    }

    public static int getColorIdFromAttributeId(final Context context, final int attributeId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, typedValue, true);
        return typedValue.resourceId;
    }

    public static int getColorFromAttributeId(final Context context, final int attributeId) {
        return ContextCompat.getColor(context, getColorIdFromAttributeId(context, attributeId));
    }

    /**
     * Returns the wrapped Adapter in case a list view has a header or a footer.
     */
    public static <T extends Adapter> T getAdapter(AdapterView<T> listView) {
        T adapter = listView.getAdapter();
        if (adapter instanceof HeaderViewListAdapter) {
            adapter = (T) ((HeaderViewListAdapter)adapter).getWrappedAdapter();
        }
        return adapter;
    }

    // from http://stackoverflow.com/questions/2067955/fast-bitmap-blur-for-android-sdk
    public static Bitmap fastBlur(Bitmap sentBitmap, float scale, int radius) {

        int width = Math.round(sentBitmap.getWidth() * scale);
        int height = Math.round(sentBitmap.getHeight() * scale);
        sentBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false);

        Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);

        if (radius < 1) {
            return (null);
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        Log.e("pix", w + " " + h + " " + pix.length);
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return (bitmap);
    }

    public static boolean hasViewDrawableState(@NonNull  View view, int drawableState) {
        boolean result = false;
        int[] states = view.getDrawableState();
        for (int state : states) {
            if (state == drawableState) {
                result = true;
            }
        }
        return result;
    }

    public static Bitmap getBitmapForDrawable(Context context, int drawableId) {
        Drawable drawable = AppCompatDrawableManager.get().getDrawable(context, drawableId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable).mutate();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static TapTarget applyDefTapTargetParams(TapTarget tapTargetView) {
        return tapTargetView
                .outerCircleColor(R.color.onboarding_decoration_red)
//                .targetCircleColor(R.color.sc_ng_background)
                .targetCircleColorInt(3553083)
                .titleTextSize(22)
//                .titleTextColor(R.color.feature_discovery_title_color)
                .titleTextColorInt(16777215)
                .descriptionTextSize(18)
//                .descriptionTextColor(R.color.feature_discovery_description_color)
                .descriptionTextColorInt(16777215)
                .cancelable(true)
                .tintTarget(false)
                .transparentTarget(false)
                .targetRadius(40);
    }

    @Nullable
    public static Drawable getTintedCompatDrawable(@Nullable Context context, final int drawableId,
            final int colorAttributeId) {
        if (context == null) {
            return null;
        }

        final Drawable originalDrawable = ContextCompat.getDrawable(context, drawableId);
        final Drawable wrappedDrawable = DrawableCompat.wrap(originalDrawable);
        DrawableCompat.setTint(wrappedDrawable, ViewUtil.getColorFromAttributeId(context,
                colorAttributeId));

        return wrappedDrawable;
    }

    public static void setCompatBackgroundDrawable(@Nullable View view, final int color) {
        if (view == null) {
            return;
        }

        int paddingLeft = view.getPaddingLeft();
        int paddingTop = view.getPaddingTop();
        int paddingRight = view.getPaddingRight();
        int paddingBottom = view.getPaddingBottom();
        final Drawable originalDrawable = view.getBackground();
        final Drawable wrappedDrawable = DrawableCompat.wrap(originalDrawable);
        DrawableCompat.setTint(wrappedDrawable, color);
        view.setBackground(wrappedDrawable);
        view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
    }

    public static Tooltip showTooltip(Activity activity, String text, View anchor, ViewGroup rootLayout, @Tooltip.Position int position) {
        View tipView = activity.getLayoutInflater().inflate(R.layout.tooltip_textview, null);
        tipView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((TextView) tipView.findViewById(R.id.tooltip_textview)).setText(text);

        float density = ViewUtil.density(activity);
        Tooltip.Tip tip = new Tooltip.Tip((int) (12 * density),
                (int) (8 * density),
                ContextCompat.getColor(activity, R.color.sc_ng_red_dark),
                (int) (density * 2));
        Tooltip tooltip = new Tooltip.Builder(activity)
                .anchor(anchor, position)
                .content(tipView)
                .into(rootLayout)
                .autoAdjust(true)
                .animate(new TooltipAnimation(TooltipAnimation.FADE, 400))
                .cancelable(true)
                .autoCancel(2000)
                .withTip(tip)
                .show();

        return tooltip;
    }

}
