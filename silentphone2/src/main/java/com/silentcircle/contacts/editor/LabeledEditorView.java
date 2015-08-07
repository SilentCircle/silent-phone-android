/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.silentcircle.contacts.editor;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.RawContactModifier;
import com.silentcircle.contacts.model.account.AccountType;
import com.silentcircle.contacts.model.dataitem.DataKind;
import com.silentcircle.contacts.utils.DialogManager;
import com.silentcircle.contacts.utils.DialogManager.DialogShowingView;
import com.silentcircle.silentphone2.R;

import java.util.List;

/**
 * Base class for editors that handles labels and values. Uses
 * {@link com.silentcircle.contacts.model.RawContactDelta.ValuesDelta} to read any existing { @link RawContact} values, and to
 * correctly write any changes values.
 */
public abstract class LabeledEditorView extends LinearLayout implements Editor, DialogShowingView {
    protected static final String DIALOG_ID_KEY = "dialog_id";
    private static final int DIALOG_ID_CUSTOM = 1;

    private static final int INPUT_TYPE_CUSTOM = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;

    private Spinner mLabel;
    private EditTypeAdapter mEditTypeAdapter;
    private View mDeleteContainer;
    private ImageView mDelete;

    private DataKind mKind;
    private RawContactDelta.ValuesDelta mEntry;
    private RawContactDelta mState;
    private boolean mReadOnly;
    private boolean mWasEmpty = true;
    private boolean mIsDeletable = true;
    private boolean mIsAttachedToWindow;

    private AccountType.EditType mType;

    private ViewIdGenerator mViewIdGenerator;
    private DialogManager mDialogManager = null;
    private EditorListener mListener;
    protected int mMinLineItemHeight;

    private Context mContext;
    
    /**
     * A marker in the spinner adapter of the currently selected custom type.
     */
    public static final AccountType.EditType CUSTOM_SELECTION = new AccountType.EditType(0, 0);

    private OnItemSelectedListener mSpinnerListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            onTypeSelectionChange(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    public LabeledEditorView(Context context) {
        super(context);
        init(context);
    }

    public LabeledEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LabeledEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mMinLineItemHeight = context.getResources().getDimensionPixelSize(R.dimen.editor_min_line_item_height);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {

        mLabel = (Spinner)findViewById(R.id.spinner);
        // Turn off the Spinner's own state management. We do this ourselves on rotation
        mLabel.setId(View.NO_ID);
        mLabel.setOnItemSelectedListener(mSpinnerListener);

        mDelete = (ImageView) findViewById(R.id.delete_button);
        mDeleteContainer = findViewById(R.id.delete_button_container);
        mDeleteContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // defer removal of this button so that the pressed state is visible shortly
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        // Don't do anything if the view is no longer attached to the window
                        // (This check is needed because when this {@link Runnable} is executed,
                        // we can't guarantee the view is still valid.
                        if (!mIsAttachedToWindow) {
                            return;
                        }
                        // Send the delete request to the listener (which will in turn call
                        // deleteEditor() on this view if the deletion is valid - i.e. this is not
                        // the last {@link Editor} in the section).
                        if (mListener != null) {
                            mListener.onDeleteRequested(LabeledEditorView.this);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Keep track of when the view is attached or detached from the window, so we know it's
        // safe to remove views (in case the user requests to delete this editor).
        mIsAttachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsAttachedToWindow = false;
    }

    @Override
    public void deleteEditor() {
        // Keep around in model, but mark as deleted
        mEntry.markDeleted();

        final ViewGroup victimParent = (ViewGroup)this.getParent();
        if (victimParent != null) {
            victimParent.removeView(this);
        }
        // Remove the view
        EditorAnimator.getInstance().removeEditorView(this);
    }

    public boolean isReadOnly() {
        return mReadOnly;
    }

    public int getBaseline(int row) {
        if (row == 0 && mLabel != null) {
            return mLabel.getBaseline();
        }
        return -1;
    }

    /**
     * Configures the visibility of the type label button and enables or disables it properly.
     */
    private void setupLabelButton(boolean shouldExist) {
        if (shouldExist) {
            mLabel.setEnabled(!mReadOnly && isEnabled());
            mLabel.setVisibility(View.VISIBLE);
        } else {
            mLabel.setVisibility(View.GONE);
        }
    }

    /**
     * Configures the visibility of the "delete" button and enables or disables it properly.
     */
    private void setupDeleteButton() {
        if (mIsDeletable) {
            mDeleteContainer.setVisibility(View.VISIBLE);
            mDelete.setEnabled(!mReadOnly && isEnabled());
        } else {
            mDeleteContainer.setVisibility(View.GONE);
        }
    }

    public void setDeleteButtonVisible(boolean visible) {
        if (mIsDeletable) {
            mDeleteContainer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    protected void onOptionalFieldVisibilityChange() {
        if (mListener != null) {
            mListener.onRequest(EditorListener.EDITOR_FORM_CHANGED);
        }
    }

    @Override
    public void setEditorListener(EditorListener listener) {
        mListener = listener;
    }

    @Override
    public void setDeletable(boolean deletable) {
        mIsDeletable = deletable;
        setupDeleteButton();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mLabel.setEnabled(!mReadOnly && enabled);
        mDelete.setEnabled(!mReadOnly && enabled);
    }

    public Spinner getLabel() {
        return mLabel;
    }

    public ImageView getDelete() {
        return mDelete;
    }

    protected DataKind getKind() {
        return mKind;
    }

    protected RawContactDelta.ValuesDelta getEntry() {
        return mEntry;
    }

    protected AccountType.EditType getType() {
        return mType;
    }

    /**
     * Build the current label state based on selected {@link com.silentcircle.contacts.model.account.AccountType.EditType} and
     * possible custom label string.
     */
    private void rebuildLabel() {
        if (mEditTypeAdapter == null) {
            mEditTypeAdapter = new EditTypeAdapter(mContext);
            mLabel.setAdapter(mEditTypeAdapter);
        }
        if (mEditTypeAdapter.hasCustomSelection()) {
            mLabel.setSelection(mEditTypeAdapter.getPosition(CUSTOM_SELECTION));
        } 
        else {
            mLabel.setSelection(mEditTypeAdapter.getPosition(mType));
        }
    }

    @Override
    public void onFieldChanged(String column, String value) {
        if (!isFieldChanged(column, value)) {
            return;
        }

        // Field changes are saved directly
        saveValue(column, value);

        // Notify listener if applicable
        notifyEditorListener();
    }

    protected void saveValue(String column, String value) {
        mEntry.put(column, value);
    }

    protected void notifyEditorListener() {
        if (mListener != null) {
            mListener.onRequest(EditorListener.FIELD_CHANGED);
        }

        boolean isEmpty = isEmpty();
        if (mWasEmpty != isEmpty) {
            if (isEmpty) {
                if (mListener != null) {
                    mListener.onRequest(EditorListener.FIELD_TURNED_EMPTY);
                }
                if (mIsDeletable) mDeleteContainer.setVisibility(View.INVISIBLE);
            } else {
                if (mListener != null) {
                    mListener.onRequest(EditorListener.FIELD_TURNED_NON_EMPTY);
                }
                if (mIsDeletable) mDeleteContainer.setVisibility(View.VISIBLE);
            }
            mWasEmpty = isEmpty;
        }
    }

    protected boolean isFieldChanged(String column, String value) {
        final String dbValue = mEntry.getAsString(column);
        // nullable fields (e.g. Middle Name) are usually represented as empty columns,
        // so lets treat null and empty space equivalently here
        final String dbValueNoNull = dbValue == null ? "" : dbValue;
        final String valueNoNull = value == null ? "" : value;
        return !TextUtils.equals(dbValueNoNull, valueNoNull);
    }

    protected void rebuildValues() {
        setValues(mKind, mEntry, mState, mReadOnly, mViewIdGenerator);
    }

    /**
     * Prepare this editor using the given {@link DataKind} for defining
     * structure and {@link com.silentcircle.contacts.model.RawContactDelta.ValuesDelta} describing the content to edit.
     */
    @Override
    public void setValues(DataKind kind, RawContactDelta.ValuesDelta entry, RawContactDelta state, boolean readOnly, ViewIdGenerator vig) {

        mKind = kind;
        mEntry = entry;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;
        setId(vig.getId(state, kind, entry, ViewIdGenerator.NO_VIEW_INDEX));

        if (!entry.isVisible()) {
            // Hide ourselves entirely if deleted
            setVisibility(View.GONE);
            return;
        }
        setVisibility(View.VISIBLE);

        // Display label selector if multiple types available
        final boolean hasTypes = RawContactModifier.hasEditTypes(kind);
        setupLabelButton(hasTypes);
        mLabel.setEnabled(!readOnly && isEnabled());
        if (hasTypes) {
            mType = RawContactModifier.getCurrentType(entry, kind);
            rebuildLabel();
        }
    }

    public RawContactDelta.ValuesDelta getValues() {
        return mEntry;
    }

    /**
     * Prepare dialog for entering a custom label. The input value is trimmed: white spaces before
     * and after the input text is removed.
     * <p>
     * If the final value is empty, this change request is ignored;
     * no empty text is allowed in any custom label.
     */
    private Dialog createCustomDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final LayoutInflater layoutInflater = LayoutInflater.from(mContext /*builder.getContext() check this */ );
        builder.setTitle(R.string.customLabelPickerTitle);

        final View view = layoutInflater.inflate(R.layout.contact_editor_label_name_dialog, null);
        final EditText editText = (EditText) view.findViewById(R.id.custom_dialog_content);
        editText.setInputType(INPUT_TYPE_CUSTOM);
        editText.setSaveEnabled(true);

        builder.setView(view);
        editText.requestFocus();

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String customText = editText.getText().toString().trim();
                if (ContactsUtils.isGraphic(customText)) {
                    final List<AccountType.EditType> allTypes = RawContactModifier.getValidTypes(mState, mKind, null);
                    mType = null;
                    for (AccountType.EditType editType : allTypes) {
                        if (editType.customColumn != null) {
                            mType = editType;
                            break;
                        }
                    }
                    if (mType == null)
                        return;

                    mEntry.put(mKind.typeColumn, mType.rawValue);
                    mEntry.put(mType.customColumn, customText);
                    rebuildLabel();
// see comment below                    requestFocusForFirstEditField();
                    onLabelRebuilt();
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                updateCustomDialogOkButtonState(dialog, editText);
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCustomDialogOkButtonState(dialog, editText);
            }
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        return dialog;
    }

    void updateCustomDialogOkButtonState(AlertDialog dialog, EditText editText) {
        final Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        okButton.setEnabled(!TextUtils.isEmpty(editText.getText().toString().trim()));
    }

    /**
     * Called after the label has changed (either chosen from the list or entered in the Dialog)
     */
    protected void onLabelRebuilt() {
    }

    protected void onTypeSelectionChange(int position) {
        AccountType.EditType selected = mEditTypeAdapter.getItem(position);
        // See if the selection has in fact changed
        if (mEditTypeAdapter.hasCustomSelection() && selected == CUSTOM_SELECTION) {
            return;
        }
        if (mType == selected && mType.customColumn == null) {
            return;
        }
        if (selected.customColumn != null) {
            showDialog(DIALOG_ID_CUSTOM);
        } 
        else {
            // User picked type, and we're sure it's ok to actually write the entry.
            mType = selected;
            mEntry.put(mKind.typeColumn, mType.rawValue);
            rebuildLabel();
// don't do - text not shown anymore. Don't know why: requestFocusForFirstEditField();
            onLabelRebuilt();
        }
    }

    void showDialog(int bundleDialogId) {
        Bundle bundle = new Bundle();
        bundle.putInt(DIALOG_ID_KEY, bundleDialogId);
        getDialogManager().showDialogInView(this, bundle);
    }

    private DialogManager getDialogManager() {
        if (mDialogManager == null) {
            Context context = getContext();
            if (!(context instanceof DialogManager.DialogShowingViewActivity)) {
                throw new IllegalStateException(
                        "View must be hosted in an Activity that implements DialogManager.DialogShowingViewActivity");
            }
            mDialogManager = ((DialogManager.DialogShowingViewActivity)context).getDialogManager();
        }
        return mDialogManager;
    }

    @Override
    public Dialog createDialog(Bundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        int dialogId = bundle.getInt(DIALOG_ID_KEY);
        switch (dialogId) {
            case DIALOG_ID_CUSTOM:
                return createCustomDialog();
            default:
                throw new IllegalArgumentException("Invalid dialogId: " + dialogId);
        }
    }

    protected abstract void requestFocusForFirstEditField();

    private class EditTypeAdapter extends ArrayAdapter<AccountType.EditType> {
        private final LayoutInflater mInflater;
        private boolean mHasCustomSelection;
        private int mTextColor;

        public EditTypeAdapter(Context context) {
            super(context, 0);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mTextColor = context.getResources().getColor(R.color.secondary_text_color);

            if (mType != null && mType.customColumn != null) {

                // Use custom label string when present
                final String customText = mEntry.getAsString(mType.customColumn);
                if (customText != null) {
                    add(CUSTOM_SELECTION);
                    mHasCustomSelection = true;
                }
            }
            for (AccountType.EditType type : RawContactModifier.getValidTypes(mState, mKind, mType)) {
                add(type);
            }
        }

        public boolean hasCustomSelection() {
            return mHasCustomSelection;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createViewFromResource(position, convertView, parent, android.R.layout.simple_spinner_item);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return createViewFromResource(position, convertView, parent, android.R.layout.simple_spinner_dropdown_item);
        }

        private View createViewFromResource(int position, View convertView, ViewGroup parent, int resource) {
            TextView textView;

            if (convertView == null) {
                textView = (TextView) mInflater.inflate(resource, parent, false);
                textView.setAllCaps(true);

                textView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                textView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
                textView.setTextColor(mTextColor);
                textView.setBackgroundColor(mContext.getResources().getColor(R.color.black_dark_gray));
                textView.setEllipsize(TruncateAt.MIDDLE);
            }
            else {
                textView = (TextView) convertView;
            }

            AccountType.EditType type = getItem(position);
            String text;
            if (type == CUSTOM_SELECTION) {
                text = mEntry.getAsString(mType.customColumn);
            }
            else {
                text = getContext().getString(type.labelRes);
            }
            textView.setText(text);
            return textView;
        }
    }
}
