/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.silentcircle.silentphone2.views;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.silentphone2.R;

public class SearchEditTextLayout extends FrameLayout {

    @SuppressWarnings("unused")
    private static final String TAG = "SearchEditTextLayout";

    private static final float EXPAND_MARGIN_FRACTION_START = 0.8f;
    private static final int ANIMATION_DURATION = 400;

    private OnKeyListener mPreImeKeyListener;
    private int mTopMargin;
    private int mBottomMargin;
    private int mLeftMargin;
    private int mRightMargin;

    private float mCollapsedElevation;

    /* Subclass-visible for testing */
    protected boolean mIsExpanded = false;
    protected boolean mIsFadedOut = false;

    protected boolean mIsDialpadEnabled = true;

//    private View mCollapsed;
    private View mExpanded;
    private EditText mSearchView;
//    private View mSearchIcon;
//    private View mCollapsedSearchBox;
//    private View mVoiceSearchButtonView;
//    private View mOverflowButtonView;
    private ImageButton mBackButtonView;
    private ImageView mKeypadToggleView;

    private ValueAnimator mAnimator;

    private OnInputSwitchedListener mOnInputSwitchedListener;
    private OnBackButtonClickedListener mOnBackButtonClickedListener;

    private Runnable mShowImeRunnable = new Runnable() {
        public void run() {
            DialerUtils.showInputMethod(mSearchView);
        }
    };

    /**
     * Listener for the input switch, see {@link InputType}
     */
    public interface OnInputSwitchedListener {
        void onInputSwitched(int inputType);
    }

    /**
     * Listener for the back button next to the search view being pressed
     */
    public interface OnBackButtonClickedListener {
        void onBackButtonClicked();
    }

    public SearchEditTextLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchEditTextLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setPreImeKeyListener(OnKeyListener listener) {
        mPreImeKeyListener = listener;
    }

    public void setOnBackButtonClickedListener(OnBackButtonClickedListener listener) {
        mOnBackButtonClickedListener = listener;
    }

    public void setOnInputSwitchedListener(OnInputSwitchedListener listener) {
        mOnInputSwitchedListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        mTopMargin = params.topMargin;
        mBottomMargin = params.bottomMargin;
        mLeftMargin = params.leftMargin;
        mRightMargin = params.rightMargin;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mCollapsedElevation = getElevation();

//        mCollapsed = findViewById(R.id.search_box_collapsed);
        mExpanded = findViewById(R.id.search_box_expanded);
        mSearchView = (EditText) mExpanded.findViewById(R.id.search_view);
        mSearchView.setFocusable(true);
        mSearchView.setFocusableInTouchMode(true);

//        mSearchIcon = findViewById(R.id.search_magnifying_glass);
//        mCollapsedSearchBox = findViewById(R.id.search_box_start_search);
//        mVoiceSearchButtonView = findViewById(R.id.voice_search_button);
//        mOverflowButtonView = findViewById(R.id.dialtacts_options_menu_button);
        mBackButtonView = (ImageButton) findViewById(R.id.search_back_button);
        mKeypadToggleView = (ImageView) findViewById(R.id.keypad_toggle_button);
        mKeypadToggleView.setFocusable(true);

        mSearchView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                setImeVisibility(hasFocus);
            }
        });

        mSearchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!TextUtils.isEmpty(s)) {
                    mKeypadToggleView.setVisibility(View.VISIBLE);
                    mKeypadToggleView.setImageResource(R.drawable.ic_clear_white_24dp);
                    mKeypadToggleView.setContentDescription(getContext().getString(R.string.description_clear_search));
                } else {
                    if (usingKeyboardInput() || !mIsDialpadEnabled) {
                        keyboardLayout(false);
                    } else {
                        dialpadLayout(false);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mKeypadToggleView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // The user wants to clear input (x)
                if (!TextUtils.isEmpty(mSearchView.getText())) {
                    mSearchView.setText("");

                    return;
                }

                // The user is switching between text or numeric keyboards
                if (!mSearchView.hasFocus()) {
                    mSearchView.requestFocus();
                } else {
                    DialerUtils.showInputMethod(mSearchView);
                }

                if (usingKeyboardInput() && mIsDialpadEnabled) {
                    dialpadLayout(false);
                } else {
                    keyboardLayout(false);
                }
            }
        });

        mBackButtonView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnBackButtonClickedListener != null) {
                    mOnBackButtonClickedListener.onBackButtonClicked();
                }
            }
        });

        super.onFinishInflate();
    }
    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (mPreImeKeyListener != null) {
            if (mPreImeKeyListener.onKey(this, event.getKeyCode(), event)) {
                return true;
            }
        }
        return super.dispatchKeyEventPreIme(event);
    }

    public void keyboardLayout(boolean clear) {
        if (mIsDialpadEnabled) {
            mKeypadToggleView.setVisibility(View.VISIBLE);
            mKeypadToggleView.setImageResource(R.drawable.ic_action_dial_pad_light);
            mKeypadToggleView.setContentDescription(getContext().getString(R.string.description_dial_pad_toggle));
        } else {
            mKeypadToggleView.setVisibility(View.GONE);
        }

        if (clear) {
            mSearchView.setInputType(InputType.TYPE_NULL);
        }
        mSearchView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        if (mOnInputSwitchedListener != null) {
            mOnInputSwitchedListener.onInputSwitched(InputType.TYPE_CLASS_TEXT);
        }
    }

    public void dialpadLayout(boolean clear) {
        mKeypadToggleView.setImageResource(R.drawable.ic_action_keyboard_dark);
        mKeypadToggleView.setContentDescription(getContext().getString(R.string.description_keyboard_toggle));

        if (clear) {
            mSearchView.setInputType(InputType.TYPE_NULL);
        }
        mSearchView.setInputType(InputType.TYPE_CLASS_PHONE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        if (mOnInputSwitchedListener != null) {
            mOnInputSwitchedListener.onInputSwitched(InputType.TYPE_CLASS_PHONE);
        }
    }

    private boolean usingKeyboardInput() {
        return mSearchView.getInputType() == (InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    }

    // Use the dialText handler to trigger the soft keyboard display. Without delay this does not
    // work reliably.
    private void setImeVisibility(final boolean makeVisible) {
        if (makeVisible) {
            mSearchView.postDelayed(mShowImeRunnable, 200);
        }
//        else {
//            mSearchView.removeCallbacks(mShowImeRunnable);
//            DialerUtils.hideInputMethod(mSearchView);        }
    }

    public void fadeOut() {
        fadeOut(null);
    }

    public void fadeOut(AnimUtils.AnimationCallback callback) {
        AnimUtils.fadeOut(this, ANIMATION_DURATION, callback);
        mIsFadedOut = true;
    }

    public void fadeIn() {
        AnimUtils.fadeIn(this, ANIMATION_DURATION);
        mIsFadedOut = false;
    }

    public void setVisible(boolean visible) {
        if (visible) {
            setAlpha(1);
            setVisibility(View.VISIBLE);
            mIsFadedOut = false;
        } else {
            setAlpha(0);
            setVisibility(View.GONE);
            mIsFadedOut = true;
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void expand(boolean animate, boolean requestFocus) {
        updateVisibility(true /* isExpand */);

        if (animate) {
            AnimUtils.fadeIn(mExpanded, ANIMATION_DURATION);
//            AnimUtils.crossFadeViews(mExpanded, mCollapsed, ANIMATION_DURATION);
            mAnimator = ValueAnimator.ofFloat(EXPAND_MARGIN_FRACTION_START, 0f);
            setMargins(EXPAND_MARGIN_FRACTION_START);
            prepareAnimator(true);
        } else {
            mExpanded.setVisibility(View.VISIBLE);
            mExpanded.setAlpha(1);
            setMargins(0f);
//            mCollapsed.setVisibility(View.GONE);
        }

        // Set 9-patch background. This owns the padding, so we need to restore the original values.
        int paddingTop = this.getPaddingTop();
        int paddingStart = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP? getPaddingStart() : getPaddingLeft();
        int paddingBottom = this.getPaddingBottom();
        int paddingEnd = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP? getPaddingEnd() : getPaddingRight();
//        setBackgroundResource(R.drawable.search_shadow);  causes a hole below action bar
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            setElevation(0);
            setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom);
            setElevation(0);
        }
        else
            setPadding(paddingStart, paddingTop, paddingEnd, paddingBottom);
        
        if (requestFocus) {
            if (mSearchView.hasFocus())
                mKeypadToggleView.requestFocus();
            // Runnable because the focus could be taken away when loading
            mSearchView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSearchView.requestFocus();
                }
            }, 200);
        }
        final Editable text = mSearchView.getText();
        if (text != null)
            mSearchView.setSelection(text.length());

        mIsExpanded = true;
    }

    public void collapse(boolean animate) {
        updateVisibility(false /* isExpand */);

        if (animate) {
//            AnimUtils.crossFadeViews(mCollapsed, mExpanded, ANIMATION_DURATION);
            AnimUtils.fadeOut(mExpanded, ANIMATION_DURATION);
            mAnimator = ValueAnimator.ofFloat(0f, 1f);
            prepareAnimator(false);
        } else {
//            mCollapsed.setVisibility(View.VISIBLE);
//            mCollapsed.setAlpha(1);
            setMargins(1f);
            mExpanded.setVisibility(View.GONE);
        }

        mIsExpanded = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setElevation(mCollapsedElevation);
//        setBackgroundResource(R.drawable.rounded_corner);     // wrong background color
    }

    public void showBackButton(boolean yes) {
        if (mBackButtonView != null) {
            mBackButtonView.setVisibility(yes ? View.VISIBLE : View.GONE);
        }
    }

    public void clearSearchQuery() {
        setSearchQuery("");
    }

    public void setSearchQuery(@Nullable final String query) {
        if (mSearchView != null) {
            mSearchView.setText(query);
            mSearchView.setSelection(mSearchView.getText().length());
        }
    }

    @Nullable
    public String getSearchQuery() {
        CharSequence query = (mSearchView != null) ? mSearchView.getText() : null;
        return query != null ? query.toString() : null;
    }

    public void showInputMethod() {
        DialerUtils.showInputMethod(mSearchView);
    }

    /**
     * Updates the visibility of views depending on whether we will show the expanded or collapsed
     * search view. This helps prevent some jank with the crossfading if we are animating.
     *
     * @param isExpand Whether we are about to show the expanded search box.
     */
    private void updateVisibility(boolean isExpand) {
        int collapsedViewVisibility = isExpand ? View.GONE : View.VISIBLE;
        int expandedViewVisibility = isExpand ? View.VISIBLE : View.GONE;

//        mSearchIcon.setVisibility(collapsedViewVisibility);
//        mCollapsedSearchBox.setVisibility(collapsedViewVisibility);
//        mVoiceSearchButtonView.setVisibility(collapsedViewVisibility);
//        mOverflowButtonView.setVisibility(collapsedViewVisibility);
        mBackButtonView.setVisibility(expandedViewVisibility);
        // TODO: Prevents keyboard from jumping up in landscape mode after exiting the
        // SearchFragment when the query string is empty. More elegant fix?
        //mExpanded.setVisibility(expandedViewVisibility);
        mKeypadToggleView.setVisibility(expandedViewVisibility);
    }

    private void prepareAnimator(final boolean expand) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        mAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final Float fraction = (Float) animation.getAnimatedValue();
                setMargins(fraction);
            }
        });

        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.start();
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    public boolean isFadedOut() {
        return mIsFadedOut;
    }

    public void setIsDialpadEnabled(boolean visible) {
        mIsDialpadEnabled = visible;
        keyboardLayout(false);
        mSearchView.setHint(mIsDialpadEnabled
                ? R.string.dialer_hint_find_contact
                : R.string.dialer_hint_find_messaging_contact);
    }

    public void setBackButtonDrawable(final Drawable drawable) {
        if (mBackButtonView != null) {
            mBackButtonView.setImageDrawable(drawable);
        }
    }

    /**
     * Assigns margins to the search box as a fraction of its maximum margin size
     *
     * @param fraction How large the margins should be as a fraction of their full size
     */
    private void setMargins(float fraction) {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        params.topMargin = (int) (mTopMargin * fraction);
        params.bottomMargin = (int) (mBottomMargin * fraction);
        params.leftMargin = (int) (mLeftMargin * fraction);
        params.rightMargin = (int) (mRightMargin * fraction);
        requestLayout();
    }
}
