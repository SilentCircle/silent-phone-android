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
package com.silentcircle.messaging.activities;

import android.Manifest;
import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.util.Pair;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fenchtose.tooltip.Tooltip;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DRUtils;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ExplainPermissionDialog;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.KeyboardNotifierLinearLayout;
import com.silentcircle.common.widget.SoundRecorderController;
import com.silentcircle.common.widget.ViewHeightNotifier;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.calllognew.IntentProvider;
import com.silentcircle.contacts.utils.ClipboardUtils;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.fragments.CameraFragment;
import com.silentcircle.messaging.fragments.ChatFragment;
import com.silentcircle.messaging.fragments.EventInfoFragment;
import com.silentcircle.messaging.fragments.SearchAgainFragment;
import com.silentcircle.messaging.listener.ClickSendOnEditorSendAction;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.listener.OnConfirmListener;
import com.silentcircle.messaging.listener.SendMessageOnClick;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.AudioProvider;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.providers.PictureProvider;
import com.silentcircle.messaging.providers.TextProvider;
import com.silentcircle.messaging.providers.VCardProvider;
import com.silentcircle.messaging.providers.VideoProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.SCloudCleanupService;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.DeviceInfo;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.messaging.views.ConversationOptionsDrawer;
import com.silentcircle.messaging.views.RelativeLayout;
import com.silentcircle.messaging.views.UploadView;
import com.silentcircle.messaging.views.VerticalSeekBar;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.list.OnListFragmentScrolledListener;
import com.silentcircle.silentphone2.list.SearchFragment;
import com.silentcircle.silentphone2.passcode.AppLifecycleNotifier;
import com.silentcircle.silentphone2.passcode.AppLifecycleNotifierBaseActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.LongTapAndDragGestureDetector;
import com.silentcircle.silentphone2.views.SearchEditTextLayout;
import com.silentcircle.silentphone2.views.gridview.GridViewAdapter;
import com.silentcircle.silentphone2.views.gridview.GridViewAdapter.GridViewItem;
import com.silentcircle.userinfo.DownloadImageTask;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import zina.JsonStrings;
import zina.ZinaNative;


public class ConversationActivity extends AppLifecycleNotifierBaseActivity implements
        ChatFragment.Callback, OnListFragmentScrolledListener, SearchFragment.HostInterface,
        ZinaMessaging.AxoMessagingStateCallback, TiviPhoneService.ServiceStateChangeListener,
        ExplainPermissionDialog.AfterReading, KeyboardNotifierLinearLayout.KeyboardListener,
        CameraFragment.CameraFragmentListener,
        ConversationUtils.UnreadEventsRunnable.OnUnreadEventsListener {

    @SuppressWarnings("unused")
    private static final String TAG = ConversationActivity.class.getSimpleName();

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 2;

    /* Limit single message, non-attachment-ified text to 1000 symbols */
    private static final int MESSAGE_TEXT_LENGTH_LIMIT = 1000;

    /* Limit single message input text to 5000 symbols */
    private static final int MESSAGE_TEXT_INPUT_LENGTH_LIMIT = 5000;

    /**
     * Limit after which unread message count is displayed with greater than sign
     */
    public static final int UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT = 10;

    /**
     * Value for situation when unread message count for other conversation is unknown
     */
    public static final int MESSAGE_COUNT_UNKNOWN = -1;

    public static final int PERMISSIONS_REQUEST_LOCATION = 1;
    public static final int PERMISSIONS_REQUEST_CAMERA = 2;
    public static final int PERMISSIONS_REQUEST_CAMERA_VIDEO = 3; // Unique key to know to launch video

    public static final String UNREAD_MESSAGE_COUNT =
            "com.silentcircle.messaging.activities.ConversationActivity.unreadMessageCount";

    public static final String TASK_SHOW_EVENT_INFO =
            "com.silentcircle.messaging.activities.ConversationActivity.showEventInfo";

    public static final String IS_IN_SEARCH_VIEW =
            "com.silentcircle.messaging.activities.ConversationActivity.isInSearchView";
    public static final String IS_IN_INFO_VIEW =
            "com.silentcircle.messaging.activities.ConversationActivity.isInInfoView";

    private static final int INTENT_CAPTURE_PHOTO = 0;
    private static final int INTENT_CAPTURE_VIDEO = 1;
    private static final int INTENT_RECORD_AUDIO = 2;
    private static final int INTENT_PICK_MEDIA = 3;
    private static final int INTENT_PICK_FILE = 4;
    private static final int INTENT_PICK_CONTACT = 5;

    public static final String SHARED_PREFS_NAME = "com.silentcircle.conversation_preferences";

    private static final String KEYBOARD_HEIGHT_KEY = "keyboard_height";

    public static final String KEY_FEATURE_DISCOVERY = "feature_discovery";

    /*
     * Minimum length of query string to initiate directory search,
     * same as {@link com.silentcircle.contacts.list.ScDirectoryLoader#MIN_SEARCH_LENGTH}
     */
    public static final int MIN_SEARCH_LENGTH = 2;


    protected Conversation mConversation;
    private View.OnClickListener mComposeOnClickListener;
    protected String mConversationPartnerId;

    protected MessagingBroadcastReceiver mViewUpdater;
    protected MessagingBroadcastReceiver mAttachmentEventReceiver;

    /* variables for attachment handling */
    private Uri mPendingImageCaptureUri;
    private Uri mPendingVideoCaptureUri;
    private Uri mPendingAudioCaptureUri;
    private static final int R_id_share = 0xFFFF & R.id.share;
    private static final int RESULT_ADD_CONTACT = 1;
    private FrameLayout mAttachmentGridFrame;
    private GridView mAttachmentGrid;
    private boolean mCameraFragmentVisible;

    private SoundRecorderController mSoundRecorder;

    private Toolbar mToolbar;
    private QuickContactBadge mAvatarView;
    private TextView mTitle;
    private TextView mSubTitle;
    private View mHome;
    private View mUnreadMessageNotification;
    private TextView mTextUnreadMessageCount;
    private DrawerLayout mDrawerLayout;
    private MenuItem mOptionsMenuItem;
    private SearchAgainFragment mSearchFragment;

    private String mAlias;
    private String mPartnerDisplayName;

    private boolean mInSearchView;

    private boolean mInInfoView;

    /* Progress bar for generic operations */
    private UploadView mProgressBar;
    private ViewGroup mComposeLayout;
    private ViewGroup mBurnDelayContainer;
    private EditText mComposeText;
    private View mComposeSend;
    private View mComposeAttach;

    /* State variables for refreshing timed messages, etc */
    private Timer mTimer;
    private Handler mHandler;
    private boolean mRefreshing;
    private boolean mRerunRefresh;
    private boolean mRerunForcedRefresh;
    private boolean mIsVisible;
    private boolean mDestroyed;
    private int mUnreadMessageCount = MESSAGE_COUNT_UNKNOWN;

    private ViewHeightNotifier mComposeElementsNotifier;

    private boolean mIsAxolotlRegistered;

    private boolean mIsPartnerMessagingDrEnabled;
    private boolean mIsPartnerCallDrEnabled;
    private byte[] mPartnerUserInfo;

    protected Bundle mSavedInstanceState;

    private ContactPhotoManagerNew mContactPhotoManager;

    /* Progress dialog when uploading avatar */
    private ProgressDialog mAvatarFetchProgressDialog;

    public static boolean mFeatureDiscoveryRan;

    public static final String[] SUPPORTED_IMTO_HOSTS = {
//            "jabber",
            "silentcircle",
//            "silent text",
//            "silenttext",
//            "silentcircle.com",
//            "com.silentcircle",
//            "silent circle"
    };

    private int mActionBarHeight;
    private EditText mSearchView;
    private String mSearchQuery;
    protected ContactEntry mContactEntry;

    private boolean mIsKeyboardVisible;
    private boolean mShouldShowAttachmentGrid;
    private int mKeyboardHeight;

    public static boolean sCloseDrawer; // Flag to close drawer (after entering group management)


    /**
     * The available modes that affect the UI state of the compose send button.
     */
    private enum RecordingMode {
        /**
         * This should be enabled when the user has typed something and should allow sending of
         * text messages.
         */
        TEXT_SEND,
        /**
         * The recording UI should show up, so that we can start recording or switch back to TEXT_SEND
         * if the user types something.
         */
        RECORDING_IDLE,
        /**
         * We are actually recording. We can switch to RECORDING_IDLE when recording finishes.
         */
        RECORDING_ACTIVE
    }

    private RecordingMode mRecordingMode = RecordingMode.RECORDING_IDLE;

    /**
     * Sets the recording mode and executes the corresponding transition according to the current
     * state and the provided one.
     * @param mode the RecordingMode state to transition to
     */
    private void setRecordingMode(RecordingMode mode) {
        if (mode == mRecordingMode)
            return;

        ImageView buttonAttach = (ImageView) findViewById(R.id.compose_attach);
        ImageView buttonSend = (ImageView) findViewById(R.id.compose_send);
        Integer tag = (Integer) buttonSend.getTag();

        // transition based on current and future recording mode
        switch (mRecordingMode) {
            case TEXT_SEND:
            {
                if (mode == RecordingMode.RECORDING_IDLE) {
                    ViewUtil.scaleToVisible(buttonAttach);
                    if (tag == null || tag == R.drawable.ic_action_send) {

                        buttonSend.setTag(R.drawable.action_button_voice_recording);
                        // Use image and scale type from "ComposeRecord" style
                        ViewUtil.animateImageChange(ConversationActivity.this, buttonSend,
                                R.drawable.action_button_voice_recording, ImageView.ScaleType.FIT_CENTER, 80);
                    }
                }
            }
            break;
            case RECORDING_IDLE:
            {
                if (mode == RecordingMode.TEXT_SEND) {
                    if (buttonAttach.getVisibility() == View.VISIBLE) {
                        ViewUtil.scaleToInvisible(buttonAttach);
                    }
                    if (tag == null || tag == R.drawable.action_button_voice_recording) {

                        buttonSend.setTag(R.drawable.ic_action_send);
                        // Use image and scale type from "ComposeSend" style
                        ViewUtil.animateImageChange(ConversationActivity.this, buttonSend,
                                R.drawable.ic_action_send, ImageView.ScaleType.CENTER, 80);
                    }
                } else if (mode == RecordingMode.RECORDING_ACTIVE) {
                    // SoundRecorderController handles this internally
                }
            }
            break;
            case RECORDING_ACTIVE:
            {
                if (mode == RecordingMode.RECORDING_IDLE) {
                    // SoundRecorderController handles this internally
                }
            }
            break;
        }

        mRecordingMode = mode;
    }

    private Runnable mOpenActionBarRunnable = new Runnable() {
        public void run() {
            openActionBarQueryField();
        }
    };

    private Runnable mOpenMessageInputRunnable = new Runnable() {
        public void run() {
            if (mComposeText != null) {
                DialerUtils.showInputMethod(mComposeText);
                setBurnContainerVisibility(View.GONE);
            }
        }
    };

    private TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable editable) {
            if (mRecordingMode == RecordingMode.RECORDING_ACTIVE) {
                if (editable.length() != 0) {
                    editable.clear();
                }
                return;
            }
            setBurnContainerVisibility(View.GONE);

            if (editable.length() > 0) {
                setRecordingMode(RecordingMode.TEXT_SEND);
            } else {
                setRecordingMode(RecordingMode.RECORDING_IDLE);

            }

            if (editable == mComposeText.getEditableText() && editable.length() > MESSAGE_TEXT_INPUT_LENGTH_LIMIT) {
                Toast.makeText(ConversationActivity.this,
                        getString(R.string.messaging_compose_length_error), Toast.LENGTH_SHORT).show();
                mComposeText.setText(editable.subSequence(0, MESSAGE_TEXT_INPUT_LENGTH_LIMIT));
                mComposeText.setSelection(mComposeText.getText().length());
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Ignore.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Ignore.
        }
    };

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            setBurnContainerVisibility(View.GONE);

            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                // If the query hasn't changed (perhaps due to activity being destroyed
                // and restored, or user launching the same DIAL intent twice), then there is
                // no need to do anything here.
                return;
            }
            mSearchQuery = newText;

            // Show search fragment only when the query string is changed to non-empty text.
            if (!TextUtils.isEmpty(newText)) {
                if (!isInSearchUi()) {
                    enterSearchUI(new Bundle());
                }
            }

            if (mSearchFragment != null && mSearchFragment.isVisible()) {
                mSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (s.length() < MIN_SEARCH_LENGTH) {
                return;
            }

            Utilities.formatNumberAssistedInput(mSearchView);
        }
    };

    TextView.OnEditorActionListener mPhoneSearchQueryEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                if (!isInSearchUi()) {
                    return false;
                }
                String query = mSearchFragment.getQueryString();
                Bundle arguments = mSearchFragment.getArguments();
                String group = Extra.GROUP.from(arguments);
                if (!TextUtils.isEmpty(query) && Utilities.isValidSipUsername(query)) {
                    if (!TextUtils.isEmpty(group)) {
                        mSearchFragment.validateUserAndStartGroupConversation(group, query);
                    } else {
                        mSearchFragment.validateUser(query);
                    }
                }
                return true;
            }
            return false;
        }
    };

    private View.OnFocusChangeListener mTextFocusListener = new View.OnFocusChangeListener() {

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            /* when view receives focus, scroll to bottom of conversation */
            if (hasFocus && !isInSearchUi()) {
                setBurnContainerVisibility(View.GONE);
                scrollToBottom();
            }
        }
    };

    /* Listener for attach button to start file selection. */
    private View.OnClickListener mAttachButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (DRUtils.isMessagingDrBlocked(ConversationActivity.this, getPartner())) {
                return;
            }
            if (mComposeText.length() <= 0) {
                if (!LoadUserInfo.canSendAttachments(getApplicationContext())) {
                    showDialog(R.string.information_dialog, LoadUserInfo.getDenialStringResId(),
                            android.R.string.ok, -1);
                    return;
                }
                toggleAttachmentGrid();
            }
        }
    };

    private View.OnClickListener mBurnButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // toggle burn slider visibility
            View burnDelayContainer = findViewById(R.id.burn_delay_value);
            int visibility = burnDelayContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
            setBurnContainerVisibility(visibility);
        }
    };

    private View.OnTouchListener mSeekerTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            int action = event.getAction();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }

            v.onTouchEvent(event);
            return true;
        }
    };

    VerticalSeekBar.OnVerticalSeekBarChangeListener mSeekerChangeListener = new VerticalSeekBar.OnVerticalSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // Ignore, handled by seek bar itself
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Ignore
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int progress = seekBar.getProgress();
            setBurnDelay(BurnDelay.Defaults.getDelay(progress));
        }

        @Override
        public void onPositionChanged(SeekBar seekBar, int progress, int verticalPosition) {
            android.widget.TextView textView = (android.widget.TextView) findViewById(R.id.burn_delay_description);
            if (textView != null) {
                textView.setText(BurnDelay.Defaults.getAlternateLabel(ConversationActivity.this, progress));
                int specWidth = View.MeasureSpec.makeMeasureSpec(0 /* any */, View.MeasureSpec.UNSPECIFIED);
                textView.measure(specWidth, specWidth);
                int questionHeight = textView.getMeasuredHeight();
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) textView.getLayoutParams();
                int topMargin = verticalPosition - (questionHeight / 2);
                params.setMargins(params.leftMargin, topMargin, params.rightMargin, params.bottomMargin);
            }
        }

        @Override
        public void onVisibilityChanged(int visibility) {
            View view = findViewById(R.id.burn_delay_description);
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    };

    /* Listener for burn delay container layout changes */
    LayoutTransition.TransitionListener mSeekerContainerTransitionListener =
            new LayoutTransition.TransitionListener() {

                @Override
                public void endTransition(LayoutTransition transition, ViewGroup container, View view,
                                          int transitionType) {
                    TextView burnDelayDescription = (TextView) findViewById(R.id.burn_delay_description);
                    burnDelayDescription.requestLayout();
                }

                @Override
                public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                }
            };

    /* Listener for attach button to start file selection. */
    private View.OnClickListener mHomeButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onBackPressed();
        }
    };

    ConversationOptionsDrawer.ConversationOptionsChangeListener mConversationOptionsChangeListener =
            new ConversationOptionsDrawer.ConversationOptionsChangeListener() {

                @Override
                public void onBurnNoticeChanged(boolean enabled) {
                    setBurnNotice(enabled);
                }

                @Override
                public void onLocationSharingChanged(boolean enabled) {
                    setLocationSharing(enabled);
                }

                @Override
                public void onBurnDelayChanged(long burnDelay) {
                    setBurnDelay(burnDelay);
                }

                @Override
                public void onSendReceiptChanged(boolean enabled) {
                    setSendReadReceipts(enabled);
                }

                @Override
                public void onClearConversation() {
                    confirmClearConversation();
                }

                @Override
                public void onSaveConversation() {
                    confirmSaveConversation();
                }

                @Override
                public void onMessageVerification() {
                    // not implemented
                }

                @Override
                public void onResetKeys() {
                    // not implemented
                }
            };

    DrawerLayout.DrawerListener mDrawerListener = new DrawerLayout.DrawerListener() {

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            // Nothing to do
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            setBurnContainerVisibility(View.GONE);
            hideSoftKeyboard(R.id.compose_text);
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            // Nothing to do
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            // Nothing to do
        }
    };

    private View.OnClickListener mTitleLayoutListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isGroupChat()) {
                startGroupManagement(false);
            } else if (BuildConfig.DEBUG) {
                MessageUtils.showPartnerInfoDialog(ConversationActivity.this, mPartnerDisplayName, getPartner());
            }
        }
    };

    private Runnable mUpdateGroupBurnTime = new Runnable() {
        @Override
        public void run() {
            Conversation conversation = getConversation();
            if (conversation != null) {
                final String groupId = getPartner();
                ConversationUtils.applyGroupChangeSet(ConversationActivity.this, groupId);

                // update events in repository and refresh chat views
                AsyncUtils.execute(new RefreshTask(
                        ConversationUtils.getConversations(), conversation, false) {

                    protected void onPostExecute(List<Event> events) {
                        resetViews();
                        updateViews();
                    }
                }, groupId);
            }
            else {
                Log.d(TAG, "Could not update burn time, conversation null");
            }
        }
    };

    /**
     * Callback from ExplainPermissionDialog after user read the explanation.
     * <p>
     * After we explained the facts and the user read the explanation ask for permission again.
     *
     * @param token        The token from ExplainPermissionDialog#showExplanation() call
     * @param callerBundle the optional bundle from ExplainPermissionDialog#showExplanation() call
     */
    @Override
    public void explanationRead(final int token, final Bundle callerBundle) {
        switch (token) {
            case PERMISSIONS_REQUEST_LOCATION:
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, token);
                break;
            case PERMISSIONS_REQUEST_CAMERA:
            case PERMISSIONS_REQUEST_CAMERA_VIDEO:
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, token);
                break;
            default:
                break;
        }
    }

    // We ask for permissions only if the user enables location, thus use 'true' when calling
    // the location enable functions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getOptionsDrawer().checkLocationAfterPermission(true);
                } else {
                    getOptionsDrawer().checkLocationAfterPermission(false);
                }
            }
            break;
            case PERMISSIONS_REQUEST_CAMERA:
            case PERMISSIONS_REQUEST_CAMERA_VIDEO: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showCameraFragment(requestCode == PERMISSIONS_REQUEST_CAMERA
                            ? CameraFragment.TYPE_PHOTO
                            : CameraFragment.TYPE_VIDEO);
                }
            }
            break;
        }
    }

    /**
     * If the search term is empty and the user closes the soft keyboard, close the search UI.
     */
    private final View.OnKeyListener mSearchEditTextLayoutListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN &&
                    TextUtils.isEmpty(mSearchView.getText())) {
                onBackPressed();

                return true;
            }
            return false;
        }
    };

    /* *********************************************************************
     * Functions and variables to bind this activity to the TiviPhoneService
     * This uses standard Android mechanism.
     * ******************************************************************* */
    private boolean mPhoneIsBound;
    private TiviPhoneService mPhoneService;

    private ServiceConnection phoneConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mPhoneService = ((TiviPhoneService.LocalBinder) service).getService();
            mPhoneIsBound = true;
            if (mDestroyed) {
                doUnbindService();
                return;
            }
            mPhoneService.addStateChangeListener(ConversationActivity.this);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mPhoneService = null;
            mPhoneIsBound = false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utilities.setTheme(this);

        super.onCreate(savedInstanceState);

        setDefaultVisibilityFlags();

        ViewUtil.setBlockScreenshots(this);

        mHandler = new Handler();

        mSavedInstanceState = savedInstanceState;

        /*
         * on resume reset known unread message count to allow it to be refreshed
         * otherwise rely on what caller has passed us
         */
        mUnreadMessageCount = mSavedInstanceState != null ? MESSAGE_COUNT_UNKNOWN
                : getIntent().getIntExtra(UNREAD_MESSAGE_COUNT, MESSAGE_COUNT_UNKNOWN);

        mActionBarHeight = getResources().getDimensionPixelSize(R.dimen.action_bar_height_large);

        mContactPhotoManager = ContactPhotoManagerNew.getInstance(getApplicationContext());

        setContentView(R.layout.activity_conversation_with_drawer);

        initConversationView(false);

        if (mSavedInstanceState != null) {
            CameraFragment cameraFragment = (CameraFragment) getFragmentManager().findFragmentByTag(CameraFragment.TAG_CAMERA_FRAGMENT);
            if (cameraFragment != null) {
                adjustLayoutForCameraFragment();
            }

            // For now leave info view and show chat content when recreating activity
            // TODO show info view if that was the last view
            if (mSavedInstanceState.getBoolean(IS_IN_INFO_VIEW, false)) {
                exitInfoUI();
            }
            if (mSavedInstanceState.getBoolean(IS_IN_SEARCH_VIEW, false)) {
                exitSearchUI(true);
            }

        }

        doBindService();

        // TODO: Is this worth going here?
        //runAttachmentManager();

        // check if we need to forward a message and do it here
        // but only on new intent
        if (mSavedInstanceState == null) {
            doStartupTask(getIntent());
        }
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // ensure that unread message count will be re-calculated
        mUnreadMessageCount = MESSAGE_COUNT_UNKNOWN;

        // exit subviews
        if (isInSearchUi()) {
            exitSearchUI(true);
        }
        if (isInInfoUi()) {
            exitInfoUI();
        }

        // clear loaded views and reset paging so that new conversation loads correctly
        clearViews();

        initConversationView(true);

        // check if we need to forward a message and do it here
        doStartupTask(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Check if we need to close the drawer (coming from group management)
        if (sCloseDrawer) {
            sCloseDrawer = false;

            if (mDrawerLayout != null) {
                mDrawerLayout.closeDrawer(Gravity.RIGHT, false);
            }
        }

        mIsVisible = true;

        String partner = getPartner();

        registerViewUpdater();
        updateViews();
        setUnreadMessageCount();

        // TODO move this to restoreActionBar
        if (!isInSearchUi() && !isInInfoUi()) {
            restoreActionBar();
        }

        Notifications.cancelMessageNotification(this, partner);

        mConversationPartnerId = partner;
        if (mCameraFragmentVisible) {
            CameraFragment cameraFragment = (CameraFragment) getFragmentManager().findFragmentByTag(CameraFragment.TAG_CAMERA_FRAGMENT);
            if (!cameraFragment.isMiniMode()) {
                setFullScreenVisibilityFlags();
            }
        }

        if (getConversation() != null) {
            refresh(false);

            if (mComposeText.length() == 0) {
                mComposeText.setText(getConversation().getUnsentText());
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_IN_INFO_VIEW, isInInfoUi());
        outState.putBoolean(IS_IN_SEARCH_VIEW, isInSearchUi());
    }

    @Override
    public void onPause() {
        super.onPause();
        mUnreadMessageCount = MESSAGE_COUNT_UNKNOWN;
        mIsVisible = false;
        mProgressBar.setVisibility(View.GONE);
        unregisterMessagingReceiver(mViewUpdater);
        unregisterMessagingReceiver(mAttachmentEventReceiver);
        mSoundRecorder.onPause();

        saveUnsentText();
        saveNewConversationIfUnseen();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        Conversation conversation = getConversation();


        boolean visible = (!isInSearchUi() && !isInInfoUi() && hasChatFragment());

        MenuItem shareLocation = menu.findItem(R.id.action_share_location);
        boolean locationSharingEnabled = LocationUtils.isLocationSharingAvailable(this)
                && (conversation != null && conversation.isLocationEnabled())
                && visible;
        shareLocation.setIcon(locationSharingEnabled
                ? R.drawable.ic_location_selected : R.drawable.ic_location_unselected);
        shareLocation.setVisible(locationSharingEnabled);

        boolean hasCall = TiviPhoneService.calls.hasCallWith(getPartner());
        MenuItem silentPhoneCall = menu.findItem(R.id.action_silent_phone_call);
        silentPhoneCall.setVisible(visible && !hasCall && !isGroupChat() && !Contact.UNKNOWN_USER_ID.equals(getPartner()));

        // Allow adding partner to contact if they are not already in contacts
        if (visible
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            final MenuItem addToContacts = menu.findItem(R.id.action_add_contact);
            if ((!TextUtils.isEmpty(mAlias) || !canMessagePartner())
                    && !Contact.UNKNOWN_USER_ID.equals(getPartner())) {
                ContactEntry contact = ContactsCache.getContactEntryFromContacts(getPartner());
                addToContacts.setVisible((contact == null || contact.lookupUri == null) && !isGroupChat());
            }
        }

        MenuItem inviteUser = menu.findItem(R.id.action_invite_user);
        inviteUser.setVisible(false);
        if (visible && isGroupChat()) {
            ConversationUtils.GroupData groupData = ConversationUtils.getGroup(getPartner());
            if (groupData != null) {
                inviteUser.setVisible(groupData.getMemberCount() < groupData.getGroupMaxMembers());
            }
        }

        // keep reference to this item
        mOptionsMenuItem = menu.findItem(R.id.options);
        mOptionsMenuItem.setVisible(canMessagePartner() && visible);
        mOptionsMenuItem.setIcon(R.drawable.ic_drawer_closed);

        ViewUtil.tintMenuIcons(this, menu);
        if (canMessagePartner() && !isGroupChat()) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    featureDiscovery1();
                }
            });
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        /* do not create options menu when axolotl has not been initialised */
        if (!mIsAxolotlRegistered) {
            return false;
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean consumed = false;
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                consumed = true;
                break;
            case R.id.options:
                toggleOptionsDrawer();
                consumed = true;
                break;
            case R.id.action_share_location:
                setLocationSharing(false);
                consumed = true;
                break;
            case R.id.action_add_contact:
                if (canMessagePartner()) {
                    addToContact(!TextUtils.isEmpty(mAlias) ? mAlias : getPartner(),
                            mPartnerDisplayName,
                            getPartnerPhotoUrl());
                } else {
                    AppLifecycleNotifier.getSharedInstance().onWillStartExternalActivity(true);
                    Intent intent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                    intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
                    intent.putExtra(ContactsContract.Intents.Insert.PHONE, getPartner());
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    startActivityForResult(intent, RESULT_ADD_CONTACT);
                }
                consumed = true;
                break;
            case R.id.action_silent_phone_call:
                launchSilentPhoneCall();
                break;
            case R.id.action_invite_user:
                inviteUserToGroupChat();
                break;
            default:
                break;
        }
        return consumed || super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // close conversation options drawer first, if it's open
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
        } else if (isInInfoUi()) {
            exitInfoUI();
        } else if (isInSearchUi() && !TextUtils.isEmpty(getPartner())) {
            // close search view
            if (mSearchView != null) {
                DialerUtils.hideInputMethod(mSearchView);
            }
            exitSearchUI(true);
        } else if (mAttachmentGrid.getVisibility() == View.VISIBLE) {
            hideAttachmentGrid();
        } else if (mCameraFragmentVisible) {
            CameraFragment cameraFragment = (CameraFragment) getFragmentManager().findFragmentByTag(CameraFragment.TAG_CAMERA_FRAGMENT);
            cameraFragment.onBackPressed();
        } else {
            try {
                super.onBackPressed();
            } catch (IllegalStateException exception) {
                finish(); // Call finish instead
            }
        }
    }

    @Override
    protected void onDestroy() {
        KeyboardNotifierLinearLayout notifier = (KeyboardNotifierLinearLayout) findViewById(R.id.conversation_root);
        notifier.setListener(null);
        mComposeElementsNotifier.setHeightListener(null);
        mSoundRecorder.onDestroy();
        mDestroyed = true;
        cancelAutoRefresh();
        doUnbindService();
        super.onDestroy();
    }

    @Override
    public void onUnreadEventsCounted(int unreadMessageCount, int unreadCallCount) {
        mUnreadMessageCount = unreadMessageCount + unreadCallCount;
        MessageUtils.requestRefresh();
    }

    protected boolean isAxolotlInitialised() {
        // verify that Axolotl has been initialised
        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        boolean axoRegistered = axoMessaging.isRegistered();
        if (!axoRegistered) {
            axoMessaging.addStateChangeListener(this);
            /*
             * TODO: waiting for Axolotl to initialise works only once if deliberately crashing
             * in SoundRecorderActivity.
             *
             * And calling axoMessaging.initialize(); will not initialise Axolotl
             * as keystorehelper is not ready.
             */

            /* if keystore is not ready, restart conversation activity through dialer activity */
        }
        return axoRegistered;
    }

    protected void initConversationView(boolean fromIntent) {
        mIsAxolotlRegistered = isAxolotlInitialised();
        mConversation = getConversation();
        String partner = getPartner();

        if (mConversation != null) {
            final LongTapAndDragGestureDetector detector = new LongTapAndDragGestureDetector(
                    getBaseContext(), composeSendAndRecordGestureListener);
            findViewById(R.id.compose_send).setOnTouchListener(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {

                    return detector.onTouchEvent(event);
                }
            });
            mComposeOnClickListener = createSendMessageOnClickListener(mConversation);
            findViewById(R.id.compose_send).setOnClickListener(mComposeOnClickListener);
            findViewById(R.id.compose_send).setTag(R.drawable.action_button_voice_recording);
            findViewById(R.id.db_circle).bringToFront();
            findViewById(R.id.compose_send).bringToFront();
            findViewById(R.id.compose_attach).setOnClickListener(mAttachButtonListener);
            findViewById(R.id.burn).setOnClickListener(mBurnButtonListener);

            @SuppressLint("WrongViewCast")
            VerticalSeekBar seeker = (VerticalSeekBar) findViewById(R.id.burn_delay_value);
            seeker.setMax(BurnDelay.Defaults.numLevels() - 1);
            seeker.setProgress(mConversation.hasBurnNotice() ? BurnDelay.Defaults.getLevel(mConversation.getBurnDelay()) : 0);
            seeker.setOnTouchListener(mSeekerTouchListener);
            seeker.setOnVerticalSeekBarChangeListener(mSeekerChangeListener);
        } else {
            /**
             * Axolotl is probably not registered, wait for it to become registered
             * See {@link ConversationActivity#axoRegistrationStateChange(boolean)}
             */
            // TODO: Handle Axolotl not being registered more gracefully
            //1. Possibly trigger a registration if one is not currently ongoing (if keystore is ready)
        }

        KeyboardNotifierLinearLayout keyboardNotifier = (KeyboardNotifierLinearLayout) findViewById(R.id.conversation_root);
        keyboardNotifier.setListener(this);

        mComposeElementsNotifier = (ViewHeightNotifier) findViewById(R.id.compose_elements_notifier);
        mComposeElementsNotifier.setHeightListener(new ViewHeightNotifier.HeightListener() {
            @Override
            public void onHeightChanged(int viewHeight) {
                if (!isInSearchUi() && !isInInfoUi()) {
                    ChatFragment chat = getChatFragment(false);
                    if (chat == null)
                        return;
                    chat.setFooter(viewHeight);
                }
            }
        });

        mBurnDelayContainer = (ViewGroup) findViewById(R.id.burn_delay_container);
        LayoutTransition mainLayoutTransition = mBurnDelayContainer.getLayoutTransition();
        mainLayoutTransition.addTransitionListener(mSeekerContainerTransitionListener);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            ViewUtil.setCompatBackgroundDrawable(findViewById(R.id.compose_container),
                    ContextCompat.getColor(this, R.color.chat_outgoing_message_color_light));
            ViewUtil.setCompatBackgroundDrawable(findViewById(R.id.burn_delay_inner_container),
                    ContextCompat.getColor(this, R.color.chat_outgoing_message_color_light));
            ViewUtil.setCompatBackgroundDrawable(findViewById(R.id.burn_delay_description),
                    ContextCompat.getColor(this, R.color.chat_outgoing_message_color_light));
        }
        mComposeLayout = (ViewGroup) findViewById(R.id.compose);
        mComposeText = (EditText) findViewById(R.id.compose_text);
        mComposeText.addTextChangedListener(mTextWatcher);
        // mComposeText.setOnFocusChangeListener(mTextFocusListener);
        mComposeText.setOnEditorActionListener(new ClickSendOnEditorSendAction());
        hideSoftKeyboard(R.id.compose_text);

        mComposeSend = findViewById(R.id.compose_send);
        mComposeAttach = findViewById(R.id.compose_attach);

        mSoundRecorder = new SoundRecorderController(getBaseContext(), this,
                new SoundRecorderController.SoundRecorderListener() {
                    @Override
                    public void onSoundRecorded(Uri uri) {
                        composeMessageWithAttachment(uri, true);
                    }

                    @Override
                    public void onShortRecording() {
                        showRecordignButtonTooltip();
                    }

                    @Override
                    public void onRecordingCanceled() {

                    }

                    @Override
                    public void onError() {
                        Toast.makeText(getBaseContext(), R.string.sound_recording_error, Toast.LENGTH_SHORT).show();
                    }
                });

        setComposeItemsVisibility(canMessagePartner() ? View.VISIBLE : View.GONE);

        mProgressBar = (UploadView) findViewById(R.id.upload);
        mProgressBar.setVisibility(View.GONE);

        createOptionsDrawer();
        restoreActionBar();

        if (mIsAxolotlRegistered) {
            if (TextUtils.isEmpty(partner)) {
                enterSearchUI(new Bundle());
            } else {
                getChatFragment(true);

                String pendingAttachmentFile = getPendingAttachmentFile();
                String pendingAttachmentText = getPendingAttachmentText();

                boolean hasPendingAttachment = !TextUtils.isEmpty(pendingAttachmentFile)
                        || !TextUtils.isEmpty(pendingAttachmentText);

                boolean canSendAttachments =
                        LoadUserInfo.canSendAttachments(getApplicationContext());

                if (hasPendingAttachment && !canSendAttachments) {
                    showDialog(R.string.information_dialog, LoadUserInfo.getDenialStringResId(),
                            android.R.string.ok, -1);
                }

                if (!TextUtils.isEmpty(pendingAttachmentFile) && canSendAttachments) {
                    handleAttachment(new Intent().setData(Uri.parse(pendingAttachmentFile)), false);
                } else if (!TextUtils.isEmpty(pendingAttachmentText) && canSendAttachments) {
                    mComposeText.setText(pendingAttachmentText);
                } else if (fromIntent) {
                    mComposeText.post(mOpenMessageInputRunnable);
                }
            }
        }

        View overlay = findViewById(R.id.conversation_progress);
        overlay.setVisibility(mIsAxolotlRegistered ? View.GONE : View.VISIBLE);

        mAttachmentGridFrame = (FrameLayout) findViewById(R.id.attachment_grid_placehodler);
        mAttachmentGrid = (GridView) findViewById(R.id.attachment_grid);
        mAttachmentGrid.setVisibility(View.GONE);

        // initialize the attachment grid
        List<GridViewItem> mItems = new ArrayList<>();
        Resources resources = getResources();

        mItems.add(new GridViewItem(ContextCompat.getDrawable(this, R.drawable.ic_photo_camera_white),
                resources.getString(R.string.take_photo), INTENT_CAPTURE_PHOTO));
        mItems.add(new GridViewItem(ContextCompat.getDrawable(this, R.drawable.ic_photo_library_white),
                resources.getString(R.string.gallery), INTENT_PICK_MEDIA));
        mItems.add(new GridViewItem(ContextCompat.getDrawable(this, R.drawable.ic_videocam_white),
                resources.getString(R.string.take_video), INTENT_CAPTURE_VIDEO));
        mItems.add(new GridViewItem(ContextCompat.getDrawable(this, R.drawable.ic_mic_white),
                resources.getString(R.string.record_audio), INTENT_RECORD_AUDIO));
        mItems.add(new GridViewItem(ContextCompat.getDrawable(this, R.drawable.ic_insert_drive_file_white),
                resources.getString(R.string.send_file), INTENT_PICK_FILE));
        mItems.add(new GridViewItem(ContextCompat.getDrawable(this, R.drawable.ic_contact_phone_white),
                resources.getString(R.string.share_contact), INTENT_PICK_CONTACT));
        mAttachmentGrid.setAdapter(new GridViewAdapter(this, mItems));
        mAttachmentGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                hideSoftKeyboard(R.id.compose_text);
                GridViewAdapter.ViewHolder holder = (GridViewAdapter.ViewHolder) view.getTag();
                int intentCode = (int) holder.userData;
                hideAttachmentGrid();
                Intent intent;
                switch (intentCode) {
                    case INTENT_CAPTURE_PHOTO:
                        handleCameraAttachmentPermission(CameraFragment.TYPE_PHOTO);
                        return;
//                        intent = createCaptureImageIntent();
//                        break;
                    case INTENT_CAPTURE_VIDEO:
                        handleCameraAttachmentPermission(CameraFragment.TYPE_VIDEO);
                        return;
//                        intent = createCaptureVideoIntent();
//                        break;
                    case INTENT_PICK_MEDIA:
                        intent = createSelectMediaIntent();
                        break;
                    case INTENT_PICK_FILE:
                        intent = createSelectFileIntent("*/*");
                        break;
                    case INTENT_PICK_CONTACT:
                        intent = createSelectContactIntent();
                        break;
                    case INTENT_RECORD_AUDIO:
                        intent = createCaptureAudioIntent();
                        break;
                    default:
                        return;

                }

                startSelectFileActivity(intent);
            }
        });
    }

    private void showRecordignButtonTooltip() {
        ViewUtil.showTooltip(this,
                getString(R.string.tooltip_record_button),
                findViewById(R.id.compose_send_dummy),
                (ViewGroup) findViewById(R.id.activity),
                Tooltip.Position.TOP);
    }

    private void featureDiscovery1() {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        boolean startDiscovery = !prefs.getBoolean(KEY_FEATURE_DISCOVERY, BuildConfig.DEBUG);

        SharedPreferences defPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean forceStartDiscovery = defPrefs.getBoolean(SettingsFragment.FORCE_FEATURE_DISCOVERY, false);

        if (!mFeatureDiscoveryRan && (startDiscovery || forceStartDiscovery)) {
            mFeatureDiscoveryRan = true;

            TapTargetView.showFor(this, ViewUtil.applyDefTapTargetParams(
                    TapTarget.forView(findViewById(R.id.action_silent_phone_call),
                            getString(R.string.discovery_conversation_1_title),
                            getString(R.string.discovery_conversation_1_desc))),
                    new TapTargetView.Listener() {
                        @Override
                        public void onTargetCancel(TapTargetView view) {
                            onTargetClick(view);
                        }

                        @Override
                        public void onOuterCircleClick(TapTargetView view) {
                            onTargetClick(view);
                        }

                        @Override
                        public void onTargetClick(TapTargetView view) {
                            super.onTargetClick(view);
                        }

                        @Override
                        public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                            super.onTargetDismissed(view, userInitiated);

                            featureDiscovery2();
                        }
                    });
        }
    }

    private void featureDiscovery2() {
        TapTargetView.showFor(this, ViewUtil.applyDefTapTargetParams(
                TapTarget.forView(findViewById(R.id.compose_send),
                        getString(R.string.discovery_conversation_2_title),
                        getString(R.string.discovery_conversation_2_desc)))
//                .targetCircleColor(android.R.color.white)
                .targetCircleColorInt(16777215)
                .transparentTarget(true),
                new TapTargetView.Listener() {
                    @Override
                    public void onTargetCancel(TapTargetView view) {
                        onTargetClick(view);
                    }

                    @Override
                    public void onOuterCircleClick(TapTargetView view) {
                        onTargetClick(view);
                    }

                    @Override
                    public void onTargetClick(TapTargetView view) {
                        super.onTargetClick(view);
                    }

                    @Override
                    public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                        super.onTargetDismissed(view, userInitiated);

                        featureDiscovery3();
                    }
                });
    }

    private void featureDiscovery3() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showAttachmentGrid();
                    }
                }, 200);
            }
        });

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        TapTargetView.showFor(ConversationActivity.this, ViewUtil.applyDefTapTargetParams(
                            TapTarget.forView(findViewById(R.id.compose_attach),
                                    getString(R.string.discovery_conversation_3_title),
                                    getString(R.string.discovery_conversation_3_desc)))
                                    .targetCircleColor(R.color.chat_outgoing_message_color_light),
                            new TapTargetView.Listener() {
                                @Override
                                public void onTargetCancel(TapTargetView view) {
                                    onTargetClick(view);
                                }

                                @Override
                                public void onOuterCircleClick(TapTargetView view) {
                                    onTargetClick(view);
                                }

                                @Override
                                public void onTargetClick(TapTargetView view) {
                                    super.onTargetClick(view);

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            new Handler().postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    hideAttachmentGrid();
                                                }
                                            }, 400);
                                        }
                                    });

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            new Handler().postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    featureDiscovery4();
                                                }
                                            }, 800);
                                        }
                                    });
                                }
                            });
                    }
                }, 600);
            }
        });
    }

    private void featureDiscovery4() {
        setBurnContainerVisibility(View.VISIBLE);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        TapTargetView.showFor(ConversationActivity.this, ViewUtil.applyDefTapTargetParams(
                                TapTarget.forView(findViewById(R.id.burn),
                                        getString(R.string.discovery_conversation_4_title),
                                        getString(R.string.discovery_conversation_4_desc)))
//                                        .targetCircleColor(android.R.color.white)
                                        .targetCircleColorInt(16777215)
                                        .transparentTarget(true),
                                new TapTargetView.Listener() {
                                    @Override
                                    public void onTargetCancel(TapTargetView view) {
                                        onTargetClick(view);
                                    }

                                    @Override
                                    public void onOuterCircleClick(TapTargetView view) {
                                        onTargetClick(view);
                                    }

                                    @Override
                                    public void onTargetClick(TapTargetView view) {
                                        super.onTargetClick(view);
                                        setBurnContainerVisibility(View.GONE);

                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                new Handler().postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        featureDiscovery5();
                                                    }
                                                }, 400);
                                            }
                                        });
                                    }
                                });
                    }
                }, 400);
            }
        });
    }

    private void featureDiscovery5() {
        // Check if they already added user to contact - don't show feature discovery in this case
        View addContact = findViewById(R.id.action_add_contact);
        if (addContact == null) {
            featureDiscovery6();

            return;
        }

        TapTargetView.showFor(ConversationActivity.this, ViewUtil.applyDefTapTargetParams(
                TapTarget.forView(addContact,
                        getString(R.string.discovery_conversation_5_title),
                        getString(R.string.discovery_conversation_5_desc))),
                new TapTargetView.Listener() {
                    @Override
                    public void onTargetCancel(TapTargetView view) {
                        onTargetClick(view);
                    }

                    @Override
                    public void onOuterCircleClick(TapTargetView view) {
                        onTargetClick(view);
                    }

                    @Override
                    public void onTargetClick(TapTargetView view) {
                        super.onTargetClick(view);
                    }

                    @Override
                    public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                        super.onTargetDismissed(view, userInitiated);

                        featureDiscovery6();
                    }
                });
    }

    private void featureDiscovery6() {
        TapTargetView.showFor(ConversationActivity.this, ViewUtil.applyDefTapTargetParams(
                TapTarget.forView(findViewById(R.id.options),
                        getString(R.string.discovery_conversation_6_title),
                        getString(R.string.discovery_conversation_6_desc))),
                new TapTargetView.Listener() {
                    @Override
                    public void onTargetCancel(TapTargetView view) {
                        onTargetClick(view);
                    }

                    @Override
                    public void onOuterCircleClick(TapTargetView view) {
                        onTargetClick(view);
                    }

                    @Override
                    public void onTargetClick(TapTargetView view) {
                        super.onTargetClick(view);
                    }

                    @Override
                    public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
                        super.onTargetDismissed(view, userInitiated);

                        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putBoolean(KEY_FEATURE_DISCOVERY, true).apply();
                    }
                });
    }

    private void handleCameraAttachmentPermission(int attachmentType) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                ExplainPermissionDialog.showExplanation(this, attachmentType == CameraFragment.TYPE_PHOTO
                                ? ConversationActivity.PERMISSIONS_REQUEST_CAMERA
                                : PERMISSIONS_REQUEST_CAMERA_VIDEO,
                        getString(R.string.permission_camera_title), getString(R.string.permission_camera_explanation), null);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                        attachmentType == CameraFragment.TYPE_PHOTO
                                ? ConversationActivity.PERMISSIONS_REQUEST_CAMERA
                                : PERMISSIONS_REQUEST_CAMERA_VIDEO);
            }
        } else {
            showCameraFragment(attachmentType);
        }
    }

    private void adjustLayoutForCameraFragment() {
        ViewGroup container = (ViewGroup) findViewById(R.id.camera_fragment_container);
        container.setVisibility(View.VISIBLE);
        setFullScreenVisibilityFlags();
        mCameraFragmentVisible = true;
    }

    private void showCameraFragment(int type) {
        if (TiviPhoneService.calls.getCallCount() > 0) {
            if (type == CameraFragment.TYPE_VIDEO) {
                Log.e(TAG, "Video recording is not supported during a call");
            } else {
                Log.e(TAG, "Photo capturing is not supported during a call");
            }
            Toast.makeText(this, R.string.record_currently_on_call, Toast.LENGTH_LONG).show();
            return;
        }

        adjustLayoutForCameraFragment();

        CameraFragment cameraFragment = (CameraFragment) getFragmentManager().findFragmentByTag(CameraFragment.TAG_CAMERA_FRAGMENT);
        if (cameraFragment == null) {
            cameraFragment = new CameraFragment();
            Bundle args = new Bundle();
            if (type == CameraFragment.TYPE_VIDEO) {
                Uri uri = VideoProvider.CONTENT_URI;
                args.putParcelable(CameraFragment.URI_KEY, uri);
                args.putInt(CameraFragment.TYPE_KEY, CameraFragment.TYPE_VIDEO);
            } else {
                Uri uri = PictureProvider.CONTENT_URI;
//                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), "test.JPG");
//                uri = Uri.fromFile(file);
                args.putParcelable(CameraFragment.URI_KEY, uri);
                args.putInt(CameraFragment.TYPE_KEY, CameraFragment.TYPE_PHOTO);
            }
            cameraFragment.setArguments(args);
            cameraFragment.appearWithAnimation(true);
            getFragmentManager().beginTransaction().add(R.id.camera_fragment_container,
                    cameraFragment, CameraFragment.TAG_CAMERA_FRAGMENT).commitAllowingStateLoss();
        }
    }

    private void hideCameraFragment(boolean animated) {
        setDefaultVisibilityFlags();
        setComposeItemsVisibility(View.VISIBLE);
        ViewUtil.setViewHeight(findViewById(R.id.mini_mode_empty_space), 0);
        /*
        ChatFragment chatFragment = getChatFragment(true);
        chatFragment.setFooter(0);
         */

        CameraFragment cameraFragment = (CameraFragment) getFragmentManager().findFragmentByTag(CameraFragment.TAG_CAMERA_FRAGMENT);
        cameraFragment.hide(animated);
    }

    private void removeCameraFragment() {
        CameraFragment cameraFragment = (CameraFragment) getFragmentManager().findFragmentByTag(CameraFragment.TAG_CAMERA_FRAGMENT);
        if (cameraFragment != null) {
            getFragmentManager().beginTransaction().remove(cameraFragment).commitAllowingStateLoss();
        }
        ViewGroup container = (ViewGroup) findViewById(R.id.camera_fragment_container);
        container.setVisibility(View.GONE);

        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        mCameraFragmentVisible = false;
    }

    @Override
    public void shouldDismissFragment(boolean error) {
        // animate if dismissing withour error
        hideCameraFragment(!error);
    }

    @Override
    public void onVideoRecorded(Uri uri) {
        hideCameraFragment(true);
        composeMessageWithAttachment(uri, true);
    }

    @Override
    public void onPhotoCaptured(Uri uri) {
        hideCameraFragment(true);
        composeMessageWithAttachment(uri, true);
    }

    @Override
    public void onMiniModeHeightChanged(int height) {
        setComposeItemsVisibility(View.GONE);
        ViewUtil.setViewHeight(findViewById(R.id.mini_mode_empty_space), height);
        /*
        ChatFragment chatFragment = getChatFragment(true);
        chatFragment.setFooter(height);
         */
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void onDisplayModeChanged(boolean isMiniMode) {
        if (isMiniMode) {
            setDefaultVisibilityFlags();
        } else {
            setFullScreenVisibilityFlags();
        }
    }

    @Override
    public void onFragmentHidden() {
        removeCameraFragment();
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            DialerUtils.hideInputMethod(mSearchView);
        }
    }

    @Override
    public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount,
                                     int totalItemCount) {
    }

    @Override
    public int getActionBarHeight() {
        return mActionBarHeight;
    }

    @Override
    public boolean isDialpadShown() {
        return false;
    }

    @Override
    public int getActionBarHideOffset() {
        return getSupportActionBar().getHideOffset();
    }

    @Override
    public boolean isActionBarShowing() {
        return true;
    }

    protected void setLocationSharing(boolean enabled) {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            conversation.setLocationEnabled(enabled);
            repository.save(conversation);
        }

        invalidateOptionsMenu();
        updateConversation();
    }

    protected void setSendReadReceipts(boolean enabled) {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            conversation.setSendReadReceipts(enabled);
            repository.save(conversation);
        }

        updateConversation();
    }

    protected void setBurnDelay(long burnDelay) {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            if (conversation.getBurnDelay() != burnDelay) {

                /*
                 * Propagate burn timer changes to other participants of group.
                 * Done after 2 seconds to avoid too many events.
                 */
                if (isGroupChat()) {
                    int result = ZinaMessaging.setGroupBurnTime(
                            conversation.getPartner().getUserId(),
                            burnDelay,
                            ZinaMessaging.FROM_SEND_RETROACTIVE);
                    if (result == MessageErrorCodes.SUCCESS) {
                        conversation.setBurnDelay(burnDelay);
                        conversation.setLastModified(System.currentTimeMillis());

                        createBurnDelayChangeEvent(repository, conversation);

                        mHandler.removeCallbacks(mUpdateGroupBurnTime);
                        mHandler.postDelayed(mUpdateGroupBurnTime, TimeUnit.SECONDS.toMillis(2));
                    }
                    else {
                        Log.w(TAG, "Could not update group's burn time"
                                + ": " + ZinaNative.getErrorInfo()
                                + " (" + ZinaNative.getErrorCode() + ")");
                        Toast.makeText(this,
                                getString(R.string.group_messaging_edit_group_burn_failed)
                                        + ": " + ZinaNative.getErrorInfo()
                                        + " (" + ZinaNative.getErrorCode() + ")",
                                Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    conversation.setBurnDelay(burnDelay);
                    conversation.setLastModified(System.currentTimeMillis());
                }
                repository.save(conversation);
            }
        }
    }

    protected void setBurnNotice(boolean enabled) {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            conversation.setBurnNotice(enabled);
            if (enabled && conversation.getBurnDelay() <= 0) {
                conversation.setBurnDelay(BurnDelay.getDefaultDelay());
            }
            repository.save(conversation);
        }

        invalidateOptionsMenu();
        updateConversation();
    }

    protected void confirmClearConversation() {
        OnConfirmListener onConfirm = new OnConfirmListener() {
            @Override
            public void onConfirm(Context context, int which) {
                clearConversation();
            }
        };
        new LaunchConfirmDialogOnClick(R.string.are_you_sure,
                R.string.cannot_be_undone,
                onConfirm).show(ConversationActivity.this);
    }

    protected void clearConversation() {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            for (Event event : repository.historyOf(conversation).list()) {
                deleteEvent(event);
            }
            repository.historyOf(conversation).clear();

            /*
            AsyncUtils.execute(new AttachmentUtils.PurgeAttachmentsRunnable(getApplicationContext(),
                    getPartner(), attachments));
             */
        }

        clearViews();
        updateConversation();
    }

    protected void confirmSaveConversation() {
        OnConfirmListener onConfirm = new OnConfirmListener() {
            @Override
            public void onConfirm(Context context, int which) {
                List<Event> messages = getConversationHistory();
                String messagesAsString = MessageUtils.getPrintableHistory(messages);
                if (!TextUtils.isEmpty(messagesAsString)
                        && messagesAsString.length() > (1024 * 1024)) {
                    MessageUtils.saveMessagesToFile(ConversationActivity.this, messagesAsString);
                    // TODO: check size, show dialog as in Silent Text
                } else {
                    MessageUtils.sendMessages(ConversationActivity.this, getPartner(),
                            messagesAsString);
                }
            }
        };
        new LaunchConfirmDialogOnClick(R.string.are_you_sure,
                R.string.save_will_expose_data,
                R.string.dialog_button_cancel,
                R.string.dialog_button_save,
                onConfirm).show(ConversationActivity.this);
    }

    /*
     * Save text user has entered but not sent as message.
     * Text is preserved with conversation object and can be restored when returning to this view.
     */
    protected void saveUnsentText() {
        // save unsent text
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            String savedUnsentText = conversation.getUnsentText();
            CharSequence unsentText = mComposeText.getText();
            if (unsentText == null) {
                unsentText = "";
            }
            if (TextUtils.isEmpty(unsentText) != TextUtils.isEmpty(savedUnsentText)
                    || !unsentText.toString().equals(savedUnsentText)) {
                conversation.setUnsentText(unsentText.toString());
                repository.save(conversation);
            }
        }
    }

    protected void saveNewConversationIfUnseen() {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            if (conversation.getLastModified() == 0) {
                conversation.setLastModified(System.currentTimeMillis());
                repository.save(conversation);
            }
        }
    }

    protected void showMessagingFailure(int errorCode, String errorInfo, Message message) {
        if (!isFinishing()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            String partner = message.getConversationID();

            if (SearchUtil.isUuid(partner)) {
                byte[] partnerName = ZinaMessaging.getDisplayName(partner);

                if (partnerName != null) {
                    partner = new String(partnerName);
                }
            }

            alert.setTitle(R.string.dialog_title_failed_to_send_message);
//            alert.setMessage(getString(R.string.dialog_message_failed_to_send_to,
//                    /* FIXME: Uncomment when we don't care about 4.1.3 clients - this is so we try
//                       not to show the UUID on error
//                    */
////                    message.getConversationID(),
//                    partner,
//                    getString(MessageErrorCodes.messageErrorToStringId(errorCode))));

            alert.setMessage(getString(MessageErrorCodes.messageErrorToStringId(errorCode)));
            alert.setCancelable(false);
            alert.setPositiveButton(R.string.dialog_button_ok, null);

            alert.show();
        }
    }

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                    .add(infoMsg, TAG)
                    .commitAllowingStateLoss();
        }
    }

    protected void restoreActionBar() {
        mToolbar = (Toolbar) findViewById(R.id.conversation_toolbar);
        mToolbar.setContentInsetsAbsolute(0, 0);

        final String partnerId = getPartner();
        refreshContactEntry(partnerId);
        if (!isGroupChat()) {
            refreshUserInfo(partnerId);
        }

        Conversation conversation = getConversation();
        mPartnerDisplayName = isGroupChat()
                ? getGroupTitle()
                : ConversationUtils.resolveDisplayName(mContactEntry, conversation);
        mTitle = (TextView) mToolbar.findViewById(R.id.title);
        if (!mPartnerDisplayName.equals(ConversationUtils.UNKNOWN_DISPLAY_NAME)) {
            // While the contact is being created, the display name may be null when adding a contact
            mTitle.setText(mPartnerDisplayName);
        }

        mToolbar.findViewById(R.id.title_container).setVisibility(View.VISIBLE);
        mToolbar.findViewById(R.id.title_layout).setOnClickListener(mTitleLayoutListener);

        mSubTitle = (TextView) mToolbar.findViewById(R.id.sub_title);
        mSubTitle.setVisibility(View.GONE);
        if (mIsAxolotlRegistered && !isGroupChat()) {
            // For the subtitle, show either verification string or the primary alias
            // if it differs from the display name
            String verifyInfo = DeviceInfo.getDeviceVerificationString(getApplicationContext(), partnerId);
            if (verifyInfo != null) {
                mSubTitle.setVisibility(View.VISIBLE);
                mSubTitle.setText(verifyInfo);
            } else {
                String alias = null;

                if (canMessagePartner()) {
                    byte[] userInfoBytes = ZinaNative.getUserInfoFromCache(partnerId);

                    if (userInfoBytes != null) {
                        AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(userInfoBytes);
                        if (userInfo != null) {
                            if (!TextUtils.isEmpty(userInfo.mAlias)) {
                                alias = userInfo.mAlias;
                            }
                        }
                    }

                    if (TextUtils.isEmpty(alias) && Utilities.isValidSipUsername(partnerId)) {
                        alias = partnerId;
                    }
                } else {
                    alias = Utilities.formatNumber(partnerId);
                }

                if (!TextUtils.isEmpty(alias)) {
                    mAlias = alias;

                    if (!alias.equals(mPartnerDisplayName)
                            && !Contact.UNKNOWN_USER_ID.equals(alias)) {
                        mSubTitle.setVisibility(View.VISIBLE);
                        mSubTitle.setText(alias);
                    }
                }
            }
        }
        if (isGroupChat()) {
            String description = getGroupDescription();
            mSubTitle.setText(description);
            mSubTitle.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
        }

        mToolbar.findViewById(R.id.title_layout).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardUtils.copyText(ConversationActivity.this,
                        null,
                        mSubTitle.getVisibility() == View.VISIBLE ? mSubTitle.getText() : mTitle.getText(),
                        null,
                        true);
                return true;
            }
        });

        mHome = mToolbar.findViewById(R.id.home);
        mHome.setOnClickListener(mHomeButtonListener);
        mUnreadMessageNotification = mToolbar.findViewById(R.id.unread_message_notification);
        mTextUnreadMessageCount = (TextView) mToolbar.findViewById(R.id.unread_message_count);

        mAvatarView = (QuickContactBadge) mToolbar.findViewById(R.id.message_avatar);
        mAvatarView.setVisibility(View.VISIBLE);
        AvatarUtils.setPhoto(mContactPhotoManager, mAvatarView, mContactEntry,
                /* isCircular */ conversation == null
                        || !AvatarProvider.AVATAR_TYPE_GENERATED.equals(conversation.getAvatarUrl()));

        setSupportActionBar(mToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setCustomView(R.layout.search_edittext);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);
            SearchEditTextLayout searchEditTextLayout =
                    (SearchEditTextLayout) actionBar.getCustomView();
            searchEditTextLayout.setPreImeKeyListener(mSearchEditTextLayoutListener);

            mSearchView = (EditText) searchEditTextLayout.findViewById(R.id.search_view);
            mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
            mSearchView.setOnEditorActionListener(mPhoneSearchQueryEditorActionListener);
            mSearchView.setHint(R.string.messaging_conversation_select_conversation_partner);
            mSearchView.setImeOptions(EditorInfo.IME_ACTION_GO);
        }

        if (mIsAxolotlRegistered) {
            setUnreadMessageCount();
        }
    }

    private void refreshContactEntry(final String partnerId) {
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(partnerId);
        if (contactEntry != null) {
            mContactEntry = contactEntry;
        }
        if (ContactsCache.hasExpired(contactEntry)) {
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    mContactEntry = ContactsCache.getContactEntry(partnerId);
                    if (mContactEntry != null) {
                        MessageUtils.requestRefresh();
                    }
                }
            });
        }
    }

    private void refreshUserInfo(final String partnerId) {
        boolean isDrEnabled =
                // messaging DR enabled
                LoadUserInfo.isLrmm() | LoadUserInfo.isLrmp() | LoadUserInfo.isLrap()
                        // call DR enabled
                        | LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp();

        if (canMessagePartner() && mIsAxolotlRegistered) {
            mPartnerUserInfo = ZinaNative.getUserInfoFromCache(partnerId);

            if (mPartnerUserInfo != null) {
                AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(mPartnerUserInfo);
                mIsPartnerMessagingDrEnabled = userInfo != null
                        && (userInfo.rrmm | userInfo.rrmp | userInfo.rrap);
                mIsPartnerCallDrEnabled = userInfo != null
                        && (userInfo.rrcm | userInfo.rrcp);


                isDrEnabled |= mIsPartnerMessagingDrEnabled | mIsPartnerCallDrEnabled;
            } else {
                AsyncUtils.execute(new Runnable() {
                    @Override
                    public void run() {
                        int[] errorCode = new int[1];
                        mPartnerUserInfo = ZinaNative.getUserInfo(partnerId, null, errorCode);
                        if (mPartnerUserInfo != null) {
                            MessageUtils.requestRefresh();
                        }
                    }
                });
            }
        }
    }

    private boolean isMessagingDrEnabled() {
        return false;
//        return  // messaging DR enabled
//                LoadUserInfo.isLrmm() | LoadUserInfo.isLrmp() | LoadUserInfo.isLrap()
//                | mIsPartnerMessagingDrEnabled;
    }

    private boolean isCallDrEnabled() {
        return false;
//        return  // local call DR enabled
//                LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp()
//                // remote user will retain call information
//                | mIsPartnerCallDrEnabled;
    }

    private String getGroupTitle() {
        String result = null;
        Conversation conversation = getConversation();
        if (conversation != null) {
            result = conversation.getPartner().getDisplayName();
        }
        if (TextUtils.isEmpty(result)) {
            String groupId = getPartner();
            ConversationUtils.GroupData groupData =
                    ConversationUtils.getGroup(groupId);
            result = groupData != null ? groupData.getGroupName() : groupId;
        }
        return result;
    }

    private String getGroupDescription() {
        String groupId = getPartner();
        ConversationUtils.GroupData groupData =
                ConversationUtils.getGroup(groupId);
        return groupData != null ? groupData.getGroupDescription() : null;
    }

    private void setUnreadMessageCount() {
        if (mUnreadMessageCount == MESSAGE_COUNT_UNKNOWN) {
            AsyncUtils.execute(new ConversationUtils.UnreadEventsRunnable(getPartner(), this));
        }
        if (mUnreadMessageNotification != null) {
            mUnreadMessageNotification.setVisibility(mUnreadMessageCount > 0 && !isInSearchUi()
                    ? View.VISIBLE : View.GONE);
        }
        if (mTextUnreadMessageCount != null) {
            mTextUnreadMessageCount.setText(mUnreadMessageCount > UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                    ? ">" + UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                    : String.valueOf(mUnreadMessageCount));
        }
    }

    protected void invalidateActionBar(boolean hidden) {
        mAvatarView.setVisibility(hidden ? View.GONE : View.VISIBLE);
        mUnreadMessageNotification.setVisibility(hidden
                ? View.GONE : ((mUnreadMessageCount > 0) ? View.VISIBLE : View.GONE));
    }

    protected ConversationOptionsDrawer getOptionsDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        mDrawerLayout.setDrawerListener(mDrawerListener);
        if (!canMessagePartner()) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
        return (ConversationOptionsDrawer) findViewById(R.id.drawer_content);
    }

    /**
     * Sends broadcast event with action UPDATE_CONVERSATION, forcing conversation update in views.
     */
    public void updateConversation() {
        MessageUtils.notifyConversationUpdated(this, getPartner(), true);

        // TODO: this will get called also when broadcast above is received.
        updateViews();
    }

    private ChatFragment getChatFragment(boolean createIfNull) {

        if (isFinishing()) {
            return null;
        }

        if (isInSearchUi()) {
            exitSearchUI(true);
        }
        if (isInInfoUi()) {
            exitInfoUI();
        }

        FragmentManager manager = getFragmentManager();
        final FragmentTransaction transaction = manager.beginTransaction();

        ChatFragment fragment =
                (ChatFragment) manager.findFragmentByTag(ChatFragment.TAG_CONVERSATION_CHAT_FRAGMENT);

        if (fragment == null) {
            if (!createIfNull) {
                return null;
            }

            fragment = new ChatFragment();
            transaction.replace(R.id.chat, fragment, ChatFragment.TAG_CONVERSATION_CHAT_FRAGMENT);
        } else {
            transaction.show(fragment);
        }

        try {
            transaction.commitAllowingStateLoss();
        } catch (RuntimeException exception) {
            // Safety check, mostly as a ghetto isDestroyed() check
            return null;
        }

        fragment.setCallback(this);

        return fragment;
    }

    private boolean hasChatFragment() {
        FragmentManager manager = getFragmentManager();

        ChatFragment fragment =
                (ChatFragment) manager.findFragmentByTag(ChatFragment.TAG_CONVERSATION_CHAT_FRAGMENT);

        return fragment != null;
    }


    public Conversation getConversation() {
        mConversation = getConversation(getPartner());
        return mConversation;
    }

    private Conversation getConversation(String conversationPartner) {
        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        ConversationRepository conversations = axoMessaging.getConversations();
        Conversation conversation = ZinaMessaging.getInstance()
                .getOrCreateConversation(conversationPartner);

        Contact partner = conversation.getPartner();
        Intent intent = getIntent();
        if (Extra.VALID.getBoolean(intent) && !partner.isValidated()) {
            partner.setValidated(true);
            conversations.save(conversation);
            Extra.VALID.remove(intent);
        }

        /*
         * For now all conversations will have burn notice for all messages
         * If a conversation does not have burn notice set it to 3 days by default.
         * Except for group conversations which have no burn time.
         */
        if (!conversation.hasBurnNotice() && (Utilities.canMessage(getPartner()) || partner.isGroup())) {
            conversation.setBurnNotice(true);
            conversation.setBurnDelay(BurnDelay.getDefaultDelay());
            conversations.save(conversation);
        }

        return conversation;
    }

    /**
     * Get the partner's UUID.
     * <p>
     * This function parses the Intent if it's an SENDTO and stores the partners UUID
     * and th partners alias. Users of the the SENDTO should set the alias string.
     *
     * @return The partners UUID
     */
    public String getPartner() {
        Intent intent = getIntent();
        if (intent != null) {
            if (Intent.ACTION_SENDTO.equals(intent.getAction())) {
                Uri uri = intent.getData();
                if (uri != null) {
                    if (Constants.SCHEME_IMTO.equals(uri.getScheme()) && Utilities.isAnyOf(uri.getHost(), SUPPORTED_IMTO_HOSTS)) {
                        mConversationPartnerId = uri.getLastPathSegment();
                    }
                }
            } else {
                mConversationPartnerId = Extra.PARTNER.from(intent);
            }
        }
        if (mConversationPartnerId == null && mConversation != null) {
            mConversationPartnerId = mConversation.getPartner().getUserId();

        }

        if (mConversationPartnerId != null) {
            mConversationPartnerId = mConversationPartnerId.trim();
            mConversationPartnerId = Utilities.removeUriPartsSelective(mConversationPartnerId);
        }
//        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Conversation partner: " + mConversationPartnerId +
//                ", (" + mConversationPartnerAlias + ")");

        return mConversationPartnerId;
    }

    public boolean isTalkingToSelf() {
        String partner = getPartner();
        return (!TextUtils.isEmpty(partner) && partner.equals(getUsername()));
    }

    private boolean canMessagePartner() {
        return Utilities.canMessage(getPartner()) || isGroupChat();
    }

    private boolean isGroupChat() {
        Conversation conversation = getConversation();
        return (conversation != null && conversation.getPartner().isGroup());
    }

    private boolean isSoundRecordingMode() {
        return (int) mComposeSend.getTag() == R.drawable.action_button_voice_recording;
    }

    private boolean canSendMessage() {
        if (!isTalkingToSelf() && DRUtils.isMessagingDrBlocked(ConversationActivity.this, getPartner())) {
            return false;
        }
        if (!LoadUserInfo.canMessageOutbound(getApplicationContext())) {
            showDialog(R.string.information_dialog, LoadUserInfo.getDenialStringResId(),
                    android.R.string.ok, -1);
            return false;
        }
        return true;
    }

    protected SendMessageOnClick createSendMessageOnClickListener(Conversation conversation) {

        TextView composeText = (TextView) findViewById(R.id.compose_text);

        return new SendMessageOnClick(composeText, getUsername(), conversation, ConversationUtils.getConversations(),
                shouldRequestDeliveryNotification(), isMessagingDrEnabled()) {

            @Override
            public void onClick(View button) {

                if (!canSendMessage()) {
                    return;
                }

                CharSequence text = mSource.getText();

                // refresh conversation, it can be changed
                mConversation = getConversation();
                mIsRetained = isMessagingDrEnabled();

                if (text.length() <= 0) {
                    // start voice recording
                    startSelectFileActivity(createCaptureAudioIntent());
                } else if (text.length() > MESSAGE_TEXT_LENGTH_LIMIT) {
                    // Message is too long to send itself over SIP - send it as an attachment
                    handleTextAttachment(text.toString());

                    mSource.setText(null);

                    super.onClick(button);
                } else {
                    super.onClick(button);
                }
            }

            @Override
            protected void withMessage(Message message) {
                updateViewsWithMessage(message);
                scrollToBottom();

                SendMessageTask task = new SendMessageTask(getApplicationContext()) {

                    @Override
                    protected void onPostExecute(Message message) {
                        if (!getResultStatus() && (getResultCode() < 0
                                || message.getState() == MessageStates.FAILED)) {
                            Log.w(TAG, "Sending failed: error: " + getResultCode() + ", " + getResultInfo());
                            /*
                             * No modal error for group conversation message failures.
                             *
                             * An alternative would be a toast message, snackbar is hidden by keyboard.
                             *
                             * Toast.makeText(ConversationActivity.this,
                                        getString(MessageErrorCodes.messageErrorToStringId(getResultCode())),
                                        Toast.LENGTH_SHORT).show();
                             */
                            // FIXME: Why is the result 1 but status false on a network error?
                            if (!isGroupChat()) {
                                showMessagingFailure(getResultCode() > 0 ? -999 : getResultCode(),
                                        getResultInfo(), message);
                            }
                        }
                    }
                };
                AsyncUtils.execute(task, message);
            }

        };
    }

    /**
     * A gesture listener that sends the detected gestures to {@link SoundRecorderController} for
     * sound recording purposes or performs a click on {@link SendMessageOnClick} for sending text messages
     * depending on the current value of {@link #mRecordingMode}.
     */
    private LongTapAndDragGestureDetector.OnGestureListener composeSendAndRecordGestureListener
            = new LongTapAndDragGestureDetector.OnGestureListener() {

        @Override
        public void onFirstTapDown() {
           setPreventTouchesWhileRecording(true);
        }

        @Override
        public void onSingleTapUp() {
            setPreventTouchesWhileRecording(false);

            if (mRecordingMode == RecordingMode.TEXT_SEND) {
                mComposeSend.performClick();
                return;
            }

            showRecordignButtonTooltip();
        }

        @Override
        public void onLongPressDown() {
            if (mRecordingMode == RecordingMode.TEXT_SEND) {
                return;
            }
            setRecordingMode(RecordingMode.RECORDING_ACTIVE);

            if (!canSendMessage()) { //TODO: is this needed?
                return;
            }
            if (TiviPhoneService.calls.getCallCount() > 0) {
                Log.e(TAG, "Sound recording is not supported during a call");

                Toast.makeText(getBaseContext(), R.string.record_currently_on_call,
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (SCloudService.isDownloading() || SCloudService.isUploading()) {
                Log.e(TAG, "Sound recording is not supported during uploading/downloading");

                Toast.makeText(getBaseContext(), R.string.attachment_wait_until_complete,
                        Toast.LENGTH_LONG).show();

                return;
            }

            // We assume that we already have Manifest.permission.RECORD_AUDIO granted
            mSoundRecorder.onLongPressDown();
        }

        @Override
        public void onLongPressUp() {
           setPreventTouchesWhileRecording(false);

            if (mRecordingMode == RecordingMode.TEXT_SEND) {
                return;
            }
            setRecordingMode(RecordingMode.RECORDING_IDLE);

            mSoundRecorder.onLongPressUp();
        }

        @Override
        public void onLongPressCancelled() {
            setPreventTouchesWhileRecording(false);

            if (mRecordingMode == RecordingMode.TEXT_SEND) {
                return;
            }

            setRecordingMode(RecordingMode.RECORDING_IDLE);

            mSoundRecorder.onLongPressCancelled();
        }

        @Override
        public void onDrag(float distanceX, float distanceY) {
            if (mRecordingMode == RecordingMode.TEXT_SEND) {
                return;
            }

            mSoundRecorder.onDrag(distanceX, distanceY);
        }
    };

    private void setPreventTouchesWhileRecording(boolean enabled) {
        if (enabled) {
            ((ViewGroup) findViewById(R.id.conversation_coordinator_layout))
                    .setMotionEventSplittingEnabled(false);
            // http://stackoverflow.com/questions/40687745/excessive-logs-from-viewdraghelper-from-android-drawer
            mDrawerLayout.requestDisallowInterceptTouchEvent(true);
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
        else {
            ((ViewGroup) findViewById(R.id.conversation_coordinator_layout))
                    .setMotionEventSplittingEnabled(true);
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        }
    }

    protected void createOptionsDrawer() {
        Conversation conversation = getConversation();
        boolean isConversationEmpty = false;
                /*
                 * FIXME
                 * Introduce and use conversations.historyOf(conversation).isEmpty()
                 * without de-serializing events.
                 *
                 * conversation == null || getConversationHistory().isEmpty();
                 */
        boolean isLocationSharingEnabled =
                conversation == null || conversation.isLocationEnabled();
        boolean isSendReceiptsEnabled =
                conversation == null || conversation.shouldSendReadReceipts();
        boolean hasBurnNotice = conversation == null || conversation.hasBurnNotice();
        long burnDelay = conversation != null ? conversation.getBurnDelay() : BurnDelay.getDefaultDelay();

        ConversationOptionsDrawer drawer = getOptionsDrawer();
        drawer.setConversationOptions(this, getPartner(), isConversationEmpty, isLocationSharingEnabled,
                isSendReceiptsEnabled, hasBurnNotice, burnDelay, isGroupChat());
        drawer.setConversationOptionsChangeListener(mConversationOptionsChangeListener);
    }

    protected void toggleOptionsDrawer() {
        if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
        } else {
            mDrawerLayout.openDrawer(Gravity.RIGHT);
        }
    }

    protected void updateViews() {
        if (!isFinishing() && !isInSearchUi() && !isInInfoUi()) {
            ChatFragment fragment = getChatFragment(true);
            if (fragment != null) {
                fragment.setEvents(getPartner());
            }
            createOptionsDrawer();
        }
    }

    protected void clearViews() {
        if (!isFinishing() && !isInSearchUi() && !isInInfoUi()) {
            ChatFragment fragment = getChatFragment(true);
            if (fragment != null) {
                fragment.clearPagingContext();
            }
        }
    }

    protected void resetViews() {
        if (!isFinishing() && !isInSearchUi() && !isInInfoUi()) {
            ChatFragment fragment = getChatFragment(true);
            if (fragment != null) {
                fragment.resetPagingContext();
            }
        }
    }

    protected void updateViewsWithMessage(Message message) {
        MessageUtils.notifyConversationUpdated(this, getPartner(), false,
                ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, message.getId());
    }

    protected String getUsername() {
        return ZinaMessaging.getInstance().getUserName();
    }

    protected boolean shouldRequestDeliveryNotification() {
        return !isGroupChat();
    }

    protected boolean shouldSendDeliveryNotification(Message message) {
        return message.isRequestReceipt();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case RESULT_ADD_CONTACT:{
                switch(resultCode) {
                    case RESULT_OK:
                        ContactsCache.removeCachedEntry(getPartner());
                        refreshContactEntry(getPartner());
                        break;
                    }
                }
                break;
            case R_id_share:
                switch(resultCode) {
                    case RESULT_OK:
                        boolean isUnique = false;

                        Intent result = data;
                        if(result == null) {
                            result = new Intent();
                        }

                        if(result.getData() == null) {
                            if(AttachmentUtils.getFileSize(this, mPendingImageCaptureUri) > 0) {
                                result.setData(mPendingImageCaptureUri);
                                isUnique = true;
                            } else if(AttachmentUtils.getFileSize(this, mPendingVideoCaptureUri) > 0) {
                                result.setData(mPendingVideoCaptureUri);
                                isUnique = true;
                            }
                        } else {
                            if(AttachmentUtils.fromOurMediaProvider(result.getData())) {
                                isUnique = true;
                            }
                        }

                        handleAttachment(result, isUnique);
                        return;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
    }

//    protected void selectFile() {
//        selectFileOfType("*/*");
//    }
//
//    private void selectFileOfType(String type) {
//        ChooserBuilder chooser = new ChooserBuilder(this);
//        chooser.label(R.string.share_from);
//        chooser.intent(createSelectFileIntent(type));
//        chooser.intent(createCaptureAudioIntent(), R.string.capture_audio_label);
//        chooser.intent(createCaptureImageIntent());
//        chooser.intent(createCaptureVideoIntent(), R.string.capture_video_label);
//        chooser.intent(createSelectContactIntent());
//        chooser.ignore(new String[]{"com.android.soundrecorder", "com.cyanogenmod.filemanager", "com.borqs.blackphone.music"});
//
//        startSelectFileActivity(chooser.build());
//    }

    private void startSelectFileActivity(@NonNull Intent intent) {
        AppLifecycleNotifier notifier = AppLifecycleNotifier.getSharedInstance();
        try {
            notifier.onWillStartExternalActivity(true);
            startActivityForResult(intent, R_id_share);
        } catch (ActivityNotFoundException e) {
            notifier.onWillStartExternalActivity(false);
            Log.w(TAG, "Unable to launch activity for selected action: " + e.getMessage());
            Toast.makeText(ConversationActivity.this,
                    R.string.warning_selected_action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private Intent createCaptureImageIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //FIXME: Hotfix
        File imagePath = new File(getFilesDir(), "captured/image");
        if(!imagePath.exists()) imagePath.mkdirs();
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.AUTHORITY_BASE + ".files", new File(imagePath, PictureProvider.JPG_FILE_NAME));
        mPendingImageCaptureUri = uri;

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(null, uri));
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 10 * 1024 * 1024L);
        intent.putExtra("return-data", true);

        return intent;
    }

    private Intent createCaptureVideoIntent() {
        Intent intent = Action.VIDEO_CAPTURE.intent();
        Uri uri = VideoProvider.CONTENT_URI;
        mPendingVideoCaptureUri = uri;
        intent.setDataAndType(uri, "video/mp4");
        return intent;
    }

    private Intent createCaptureAudioIntent() {
        Intent intent = Action.AUDIO_CAPTURE.intent();
        Uri uri = AudioProvider.CONTENT_URI;
        mPendingAudioCaptureUri = uri;
        intent.setDataAndType(uri, "audio/mp4");
        return intent;
    }

    private Intent createSelectMediaIntent() {
        // opens photo/album app to pick a picture
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // Using the INTERNAL_CONTENT_URI with Google's photos app exposes both internal and external
        // images and videos (bug?)
        // Use it by default if it exists, with INTERNAL_CONTENT_URI
        PackageManager pm = getPackageManager();
        if (pm != null) {
            try {
                PackageInfo pi = pm.getPackageInfo("com.google.android.apps.photos", PackageManager.GET_ACTIVITIES);
                if (pi != null) {
                    intent.setData(MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                    intent.setPackage("com.google.android.apps.photos");
                }
            } catch (PackageManager.NameNotFoundException ignore) {}
        }

        return intent;
    }

    private Intent createSelectContactIntent() {
        return new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
    }

    private static Intent createSelectFileIntent(String type) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(type);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return intent;
    }

    private void onReceiveAttachment(Intent intent) {
        String partner = Extra.PARTNER.from(intent);

        if(partner.equals(getPartner())) {
            String messageId = Extra.ID.from(intent);
            String attachmentMetaData = Extra.TEXT.from(intent);
            boolean exported = Extra.EXPORTED.getBoolean(intent);

            Event event = MessageUtils.getEventById(partner, messageId);

            if(attachmentMetaData == null) {
                if(event instanceof Message) {
                    attachmentMetaData = ((Message) event).getMetaData();
                }
            }

            if(attachmentMetaData != null) {
                try {
                    JSONObject attachmentMetaDataJson = new JSONObject(attachmentMetaData);

                    File attachmentFile;

                    String mimeType = attachmentMetaDataJson.getString("MimeType");
                    String exportedFilename = attachmentMetaDataJson.optString("ExportedFileName");
                    String fileName = attachmentMetaDataJson.getString("FileName");
                    String displayName = attachmentMetaDataJson.optString("DisplayName");
                    String hash = attachmentMetaDataJson.optString("SHA256");

                    if (!TextUtils.isEmpty(exportedFilename)) {
                        fileName = exportedFilename;
                    }

                    if(!exported) {
                        attachmentFile = AttachmentUtils.getFile(messageId, this);
                    } else {
                        attachmentFile = AttachmentUtils.getExternalStorageFile(fileName);
                    }

                    // TODO: move to a separate function
                    /* view regular files with FileViewerActivity */
                    Intent fileViewerIntent = new Intent(this, FileViewerActivity.class);
                    fileViewerIntent.setDataAndType(Uri.fromFile(attachmentFile), mimeType);
                    Extra.PARTNER.to(fileViewerIntent, partner);
                    Extra.ID.to(fileViewerIntent, messageId);
                    if (event != null && event instanceof Message) {
                        Extra.TEXT.to(fileViewerIntent, fileName);
                        Extra.ALIAS.to(fileViewerIntent, hash);
                        Extra.DISPLAY_NAME.to(fileViewerIntent, displayName);
                    }
                    startActivity(fileViewerIntent);
                } catch (JSONException exception) {
                    Log.e(TAG, "Attachment JSON exception (rare)", exception);
                }
            }
        }
    }

    public JSONObject getAttachment(String messageId) {
        JSONObject attachment = null;

        ConversationRepository repository = ConversationUtils.getConversations();
        if (repository != null) {
            Conversation conversation = repository.findByPartner(getPartner());
            EventRepository events = repository.historyOf(conversation);
            Message message = (Message) events.findById(messageId);

            try {
                attachment = new JSONObject(message.getAttachment());
            } catch (JSONException exception) {
                Log.e(TAG, "Message attachment JSON error (rare)", exception);
            }
        }

        return attachment;
    }

    private List<Event> getConversationHistory() {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        List<Event> items;
        if (repository != null && conversation != null) {
            items = MessageUtils.filter(repository.historyOf(conversation).list(),
                    DialerActivity.mShowErrors);
        }
        else {
            // return empty list if conversation is not available
            items = new ArrayList<>();
        }
        return items;
    }

    private void launchSilentPhoneCall() {
        if (TiviPhoneService.calls.getCallCount() >= 1 && !LoadUserInfo.canStartConference(this)) {
            showDialog(R.string.information_dialog, LoadUserInfo.getDenialStringResId(),
                    android.R.string.ok, -1);
            return;
        }

        /*
        if (LoadUserInfo.isBrmr() || LoadUserInfo.isBrdr()) {
            // always ensure that user info is up to date before the call
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    ZinaMessaging.refreshUserData(getPartner(), null);
                    ConversationActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            launchSilentPhoneCall(getPartner());
                        }
                    });
                }
            });
        } else {
            launchSilentPhoneCall(getPartner());
        }
         */
        launchSilentPhoneCall(getPartner());
    }

    private void launchSilentPhoneCall(String partner) {
        /*
        if (DRUtils.isCallingDrBlocked(this, partner)) {
            return;
        }
         */

        final IntentProvider intentProvider = IntentProvider.getReturnCallIntentProvider(partner);
        final Intent intent = intentProvider.getIntent(getApplicationContext());
        startActivity(intent);
    }

    private void inviteUserToGroupChat() {
        if (!isGroupChat()) {
            Log.e(TAG, "Cannot invite user to a non-group chat");
            return;
        }

        if (!Utilities.isNetworkConnected(this)) {
            InfoMsgDialogFragment.showDialog(this, R.string.no_internet,
                    R.string.connected_to_network, android.R.string.ok, -1);
            return;
        }

        Conversation conversation = getConversation();
        ConversationUtils.GroupData group = null;
        if (conversation != null) {
            group = ConversationUtils.getGroup(conversation.getPartner().getUserId());
        }
        if (group != null) {
            int maxMemberCount = group.getGroupMaxMembers();
            int memberCount = group.getMemberCount();
            if (memberCount >= maxMemberCount) {
                Log.d(TAG, "Group already has maximum number of members.");
                Snackbar.make(findViewById(R.id.conversation_coordinator_layout),
                        "Group already has maximum number of members.",
                        Snackbar.LENGTH_INDEFINITE)
                        .show();
            } else {
                Bundle arguments = new Bundle();
                Extra.GROUP.to(arguments, group.getGroupId());
                Extra.IS_GROUP.to(arguments, true);
                arguments.putInt(SearchFragment.SELECTION_MAX_SIZE, maxMemberCount - memberCount);
                enterSearchUI(arguments);
            }
        }
    }

    public void startGroupManagement(boolean animate) {
        Context context = ConversationActivity.this;
        Intent intent = new Intent(context, GroupManagementActivity.class);
        intent.putExtra(GroupManagementActivity.GROUP_ID, getPartner());
        intent.putExtra(GroupManagementActivity.TASK, GroupManagementActivity.TASK_DETAILS_FROM_CONVERSATION);
        startActivity(intent);
        if (!animate) {
            overridePendingTransition(0, 0);
        }

        sCloseDrawer = true;
    }

    private void handleTextAttachment(String plaintext) {
        if (SCloudService.isDownloading() || SCloudService.isUploading()) {
            Toast.makeText(getBaseContext(), R.string.attachment_wait_until_complete,
                    Toast.LENGTH_LONG).show();

            return;
        }

        try {
            getContentResolver().openOutputStream(TextProvider.CONTENT_URI).write(IOUtils.toByteArray(plaintext));
        } catch (IOException impossible) {
            Log.e(TAG, "Text attachment error", impossible);

            return;
        }

        handleAttachment(new Intent().setData(TextProvider.CONTENT_URI), true);
    }

    private void handleAttachment(final Intent data, final boolean isUnique) {
        if (SCloudService.isDownloading() || SCloudService.isUploading()) {
            Toast.makeText(getBaseContext(), R.string.attachment_wait_until_complete,
                    Toast.LENGTH_LONG).show();

            return;
        }

        Uri uri = data.getData();
        if (AttachmentUtils.matchAttachmentUri(uri) == AttachmentUtils.MATCH_CONTACTS_URI) {
            // if a contact has been selected let it pass through our VCardProvider
            // to generate a VCard string as attachment
            Log.d(TAG, "Creating and sending vCard for: " + uri);
            data.setData(VCardProvider.getVCardUriForContact(this, uri));
        }
        composeMessageWithAttachment(data, isUnique);
    }

    @SuppressWarnings("unused")
    private void runAttachmentManager() {
        Intent serviceIntent = Action.RUN_ATTACHMENT_HANDLER.intent(this, SCloudService.class);
        startService(serviceIntent);
    }

    private void composeMessageWithAttachment(final Intent attachment, final boolean isUnique) {
        if (attachment == null) {
            return;
        }

        Uri uri = attachment.getData();
        if (uri == null) {
            uri = attachment.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (uri == null) {
            Log.d(TAG, "composeMessageWithAttachment: no uri for attachment");
            return;
        }

        composeMessageWithAttachment(uri, isUnique);
    }

    private void composeMessageWithAttachment(final Uri uri, final boolean isUnique) {
        ConversationRepository repository = ConversationUtils.getConversations();
        if (repository == null) {
            return;
        }

        final long size = AttachmentUtils.getFileSize(this, uri);

        if (size <= 0) {
            Log.d(TAG, "composeMessageWithAttachment: content length is 0 or invalid");
            return;
        }

        if (size >= AttachmentUtils.FILE_SIZE_LIMIT) {
            AttachmentUtils.showFileSizeErrorDialog(this);
            return;
        }

        Message message = MessageUtils.composeMessage(getUsername(), "",
                shouldRequestDeliveryNotification(), getConversation(), null, isMessagingDrEnabled());
        EventRepository events = repository.historyOf(getConversation());

        message.setState(MessageStates.COMPOSING);
        message.setAttachment("");
        events.save(message);

        // message has been saved, refresh view to show it
        updateViewsWithMessage(message);

        Intent serviceIntent = Action.UPLOAD.intent(this, SCloudService.class);
        Extra.PARTNER.to(serviceIntent, message.getConversationID());
        Extra.ID.to(serviceIntent, message.getId());
        serviceIntent.putExtra("IS_UNIQUE", isUnique);
        serviceIntent.setData(uri);
        startService(serviceIntent);
    }

    private void registerViewUpdater() {

        mViewUpdater = new MessagingBroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String partner = intent.getStringExtra(Extra.PARTNER.getName());
                boolean forCurrentConversation = TextUtils.equals(partner, getPartner());
                switch (Action.from(intent)) {
                    case RECEIVE_MESSAGE:
                        /*
                         * Sound notification for an arrived message for another conversation
                         * Play sound and stop broadcast instead of showing message alarm notification.
                         */
                        if (!forCurrentConversation) {
                            SoundNotifications.playReceiveMessageSound();
                            incrementUnredMessageCount(context);
                        }
                    case UPDATE_CONVERSATION:
                        /* update view with new message information */
                        if (forCurrentConversation) {
                            boolean forceRefresh = Extra.FORCE.getBoolean(intent);
                            handleUpdateNotification(forceRefresh);
                            if (isOrdered()) {
                                setConsumed(true);
                            }
                        }
                        break;
                    case REFRESH_SELF:
                        invalidateOptionsMenu();
                        if (!isInSearchUi() && !isInInfoUi()) {
                            restoreActionBar();
                        }
                        setUnreadMessageCount();
                        /*
                        clearViews();
                        updateViews();
                         */
                        break;
                    case CLOSE_CONVERSATION:
                        if (forCurrentConversation) {
                            Toast.makeText(ConversationActivity.this,
                                    getString(R.string.group_messaging_leaving_group_unknown),
                                    Toast.LENGTH_SHORT).show();

                            Log.w(TAG, "Group conversation not valid, exiting.");
                            Intent exitIntent = new Intent(ConversationActivity.this, DialerActivity.class);
                            exitIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            ConversationActivity.this.startActivity(exitIntent);
                            break;
                        }
                    default:
                        break;
                }
            }
        };

        mAttachmentEventReceiver = new MessagingBroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String partner = intent.getStringExtra(Extra.PARTNER.getName());
                boolean forCurrentConversation = TextUtils.equals(partner, getPartner());
                boolean isGroupAvatarEvent = intent.getBooleanExtra(SCloudService.FLAG_GROUP_AVATAR, false);
                switch (Action.from(intent)) {
                    case PROGRESS:
                        if (forCurrentConversation && !isGroupAvatarEvent) {
                            mProgressBar.setProgress(Extra.TEXT.getInt(intent), Extra.PROGRESS.getInt(intent), null);
                        }
                        break;
                    case CANCEL: {
                        mProgressBar.setVisibility(View.GONE);

                        // show toast message for attachment issue if it is not group avatar
                        if (!isGroupAvatarEvent) {
                            int cancelResourceId = Extra.TEXT.getInt(intent);
                            if (cancelResourceId > 0) {
                                Toast.makeText(ConversationActivity.this, getString(cancelResourceId), Toast.LENGTH_LONG).show();
                            }
                        }
                        break;
                    }
                    case ERROR: {
                        mProgressBar.setVisibility(View.GONE);

                        // show toast message for attachment issue if it is not group avatar
                        if (!isGroupAvatarEvent) {
                            int errorResourceId = Extra.TEXT.getInt(intent);
                            if (errorResourceId > 0) {
                                Toast.makeText(ConversationActivity.this, getString(errorResourceId), Toast.LENGTH_LONG).show();
                            }
                        }
                        break;
                    }
                    case DOWNLOAD:
                        String messageId = Extra.ID.from(intent);
                        Intent serviceIntent = Action.DOWNLOAD.intent(context, SCloudService.class);
                        Extra.PARTNER.to(serviceIntent, partner);
                        Extra.ID.to(serviceIntent, messageId);
                        startService(serviceIntent);
                        break;
                    case RECEIVE_ATTACHMENT:
                        if (!intent.getBooleanExtra(SCloudService.FLAG_GROUP_AVATAR, false)) {
                            onReceiveAttachment(intent);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        // messaging events
        IntentFilter filter = Action.filter(
                Action.RECEIVE_MESSAGE, Action.UPDATE_CONVERSATION, Action.REFRESH_SELF,
                Action.CLOSE_CONVERSATION);
        registerMessagingReceiver(this, mViewUpdater, filter, MESSAGE_PRIORITY);

        // attachment events
        filter = Action.filter(
                Action.PROGRESS, Action.CANCEL, Action.ERROR, Action.DOWNLOAD, Action.RECEIVE_ATTACHMENT);
        registerMessagingReceiver(this, mAttachmentEventReceiver, filter, MESSAGE_PRIORITY);
    }

    private void handleUpdateNotification(boolean forceRefresh) {
        // if not visible the next onResume will refresh()
        if (mIsVisible) {
            if (!refresh(forceRefresh)) {
                mRerunRefresh = !forceRefresh;
                mRerunForcedRefresh = forceRefresh;
                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "Refresh task already running, rise flag to re-run it "
                        + "(rerun: " + mRerunRefresh + ", rerun forced: " + mRerunForcedRefresh + ").");
                }
            }
        }
        else {
            updateViews();
        }
    }

    private void registerMessagingReceiver(@NonNull Context context, MessagingBroadcastReceiver receiver,
            IntentFilter filter, int priority) {
        filter.setPriority(priority);
        MessagingBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    @Override
    public void onActionModeCreated(ActionMode mode) {
        ViewUtil.tintMenuIcons(this, mode.getMenu());
        setComposeItemsEnabled(false);

        hideSoftKeyboard(R.id.compose_text);
        mDrawerLayout.requestDisallowInterceptTouchEvent(true);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        setBurnContainerVisibility(View.GONE);

        hideAttachmentGrid();
    }

    @Override
    public void onActionModeDestroyed() {
        boolean enabled = canMessagePartner(); //!(isInSearchUi() || !canMessagePartner());
        setComposeItemsEnabled(enabled);

        if (!isInSearchUi() && !isInInfoUi() && canMessagePartner()) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            if (mComposeText != null) {
                mComposeText.requestFocus();
            }
        }
    }

    @Override
    public void onActionPerformed() {
        /*
         * Avoid unnecessarily refreshing chat fragment
         * All views should be updated already through intents.
         * updateViews() can be called in a special case if necessary.
         */
    }

    @Override
    public void performAction(int actionID, List<Event> targets) {
        if (actionID == R.id.burn) {
            /*
             * Handle burn separately to avoid starting too many async tasks if a single task is
             * sufficient.
             * This helps with selecting and burning large number (>20) messages simultaneously.
             */
            SoundNotifications.playBurnMessageSound();
            MessageUtils.burnMessage(this, targets.toArray(new Event[targets.size()]));
        } else if (actionID == R.id.delete) {
            SoundNotifications.playBurnMessageSound();
            MessageUtils.removeMessage(getPartner(), targets.toArray(new Event[targets.size()]));
        } else {
            for (Event target : targets) {
                performAction(actionID, target);
            }
        }
    }

    @Override
    public void axoRegistrationStateChange(boolean registered) {
        mIsAxolotlRegistered = registered;
        if (registered && !isFinishing()) {
            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
            axoMessaging.removeStateChangeListener(this);

            // restart activity with its own intent
            Intent messagingIntent = ContactsUtils.getMessagingIntent(getPartner(), this);
            startActivity(messagingIntent);
        }
    }

    @Override
    public void zrtpStateChange(@NonNull CallState call, TiviPhoneService.CT_cb_msg msg) {
    }

    @Override
    public void callStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
        invalidateOptionsMenu();
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            if (receiver != null) {
                super.unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister broadcast receiver.");
            e.printStackTrace();
        }
    }

    public void unregisterMessagingReceiver(MessagingBroadcastReceiver receiver) {
        try {
            if (receiver != null) {
                MessagingBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister view update broadcast receiver.");
            e.printStackTrace();
        }
    }

    protected void performAction(int actionID, final Event event) {
        switch (actionID) {
            case R.id.burn:
                SoundNotifications.playBurnMessageSound();
                MessageUtils.burnMessage(this, event);
                break;
            case R.id.delete:
                deleteEvent(event);
                break;
            case R.id.copy:
                ClipboardUtils.copyText(this, /* label */ null, event.getText(),
                        getString(R.string.toast_text_copied), true);
                break;
            case R.id.action_resend:
                requestResend(event);
                break;
            case R.id.info:
                enterInfoUI(event);
                break;
            case R.id.forward:
                forwardMessage(event);
                break;
            default:
                // Unknown or unhandled action.
                break;
        }
    }

    protected void deleteEvent(final Event event) {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            repository.historyOf(conversation).remove(event);
        }

        if(event instanceof Message) {
            if(((Message) event).hasAttachment()) {
                Intent cleanupIntent = Action.PURGE_ATTACHMENTS.intent(this, SCloudCleanupService.class);
                cleanupIntent.putExtra("KEEP_STATUS", false);
                Extra.PARTNER.to(cleanupIntent, getPartner());
                Extra.ID.to(cleanupIntent, event.getId());
                startService(cleanupIntent);
            }
        }
    }

    protected void requestResend(final Event event) {
        if (DRUtils.isMessagingDrBlocked(ConversationActivity.this, getPartner())) {
            return;
        }
        if (event instanceof OutgoingMessage) {
            OutgoingMessage message = (OutgoingMessage) event;
            if (/* !isTalkingToSelf() */ true) {
                ConversationRepository repository = ConversationUtils.getConversations();
                if (repository != null) {
                    EventRepository events = repository.historyOf(getConversation());
                    message.setState(MessageStates.COMPOSED);
                    message.setAttributes(
                            MessageUtils.getMessageAttributesJSON(message.getBurnNotice(),
                                    message.isRequestReceipt(), message.getLocation()));
                    events.save(message);
                    SendMessageTask task = new SendMessageTask(getApplicationContext());
                    AsyncUtils.execute(task, message);
                }
            }
        } else {
            Log.e(TAG, "Resend requested for other type of event than OutgoingMessage");
        }
    }

    protected void forwardMessage(final Event event) {
        if (DRUtils.isMessagingDrBlocked(ConversationActivity.this, getPartner())) {
            return;
        }
        if (event instanceof Message) {
            Message message = (Message) event;
            Bundle arguments = new Bundle();
            if (message.hasAttachment()) {
                Extra.ID.to(arguments, message.getId());
                Extra.PARTNER.to(arguments, getPartner());
            }
            else {
                Extra.TEXT.to(arguments, message.getText());
            }

            enterSearchUI(arguments);
        }
        else {
            Log.w(TAG, "Unable to forward event which is not a message");
        }
    }

    /*
     * Do the action required by passed intent:
     *
     * If message a message with an attachment is being forwarded, send it,
     * If a text message is being forwarded, populate compose field.
     */
    protected void doStartupTask(@Nullable final Intent intent) {
        if (intent == null) {
            return;
        }
        /*
         * to avoid repeating the same task (forward or invite) intent flags could be checked
         * for Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY and action skipped if this flag is
         * present
         */

        Bundle extras = intent.getExtras();
        if (extras != null) {

            String task = Extra.TASK.from(extras);
            // message forward task
            String text = Extra.TEXT.from(extras);
            String messageId = Extra.ID.from(extras);
            String partner = Extra.PARTNER.from(extras);
            Message message = null;
            if (!TextUtils.isEmpty(messageId) && !TextUtils.isEmpty(partner)) {
                try {
                    message = (Message) MessageUtils.getEventById(partner, messageId);
                } catch (ClassCastException e) {
                    Log.e(TAG, "Trying to forward an event which is not a message.");
                }
            }
            if (message != null) {
                MessageUtils.forwardMessage(this, getUsername(), getPartner(), message,
                        LoadUserInfo.isLrmm() | LoadUserInfo.isLrmp() | LoadUserInfo.isLrap()
                                | message.isRetained());
            } else if (!TextUtils.isEmpty(text) && mComposeText != null) {
                mComposeText.setText(text);
            }

            // invite task
            final String group = Extra.GROUP.from(extras);
            final CharSequence[] participants = Extra.PARTICIPANTS.getCharSequences(extras);

            if (!TextUtils.isEmpty(group) && participants != null) {
                inviteParticipants(group, participants);
            }

            // open event details view task
            if (TASK_SHOW_EVENT_INFO.equals(task)) {
                Event event = MessageUtils.getEventById(getPartner(), messageId);
                enterInfoUI(event);
            }
        }
    }

    private void openActionBarQueryField() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        mToolbar.findViewById(R.id.title_container).setVisibility(View.GONE);
        ((SearchEditTextLayout)actionBar.getCustomView()).expand(false /* animate */, true /* requestFocus */);
        ((SearchEditTextLayout)actionBar.getCustomView()).showBackButton(false);
        ((SearchEditTextLayout)actionBar.getCustomView()).setIsDialpadEnabled(false);
        ((SearchEditTextLayout)actionBar.getCustomView()).keyboardLayout(false);
    }

    private void maybeExitSearchUi() {
        if (isInSearchUi() && TextUtils.isEmpty(mSearchQuery)) {
            exitSearchUI(true);
            DialerUtils.hideInputMethod(mSearchView);
        }
    }

    protected void enterSearchUI(@NonNull final Bundle arguments) {
        FragmentManager manager = getFragmentManager();
        final FragmentTransaction transaction = manager.beginTransaction();
        transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        mSearchFragment =
                (SearchAgainFragment) manager.findFragmentByTag(
                        SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);

        if (mSearchFragment == null) {
            mSearchFragment = new SearchAgainFragment();

            // We store these pending attachment strings temporarily in the search fragment
            // They are passed back to the activity when a recipient is selected
            String pendingDataFile = getPendingAttachmentFile();

            if (!TextUtils.isEmpty(pendingDataFile)) {
                Extra.DATA.to(arguments, pendingDataFile);
            } else {
                String pendingDataText = getPendingAttachmentText();

                if (!TextUtils.isEmpty(pendingDataText)) {
                    Extra.TEXT.to(arguments, pendingDataText);
                }
            }

            mSearchFragment.setArguments(arguments);
            transaction.add(R.id.chat, mSearchFragment,
                    SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);
        }
        else {
            transaction.show(mSearchFragment);
        }
        transaction.commitAllowingStateLoss();

        ChatFragment chatFragment = getChatFragment(false);
        if (chatFragment != null) {
            final View view = chatFragment.getView();
            if (view != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                    view.animate().alpha(0).withLayer();
                else
                    view.animate().alpha(0);
            }
        }

        runOnUiThread(mOpenActionBarRunnable);

        mInSearchView = true;
        setComposeItemsVisibility(View.GONE);
        hideSoftKeyboard(R.id.compose_text);

        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        invalidateActionBar(isInSearchUi());
    }

    protected void exitSearchUI(final boolean transitioning) {
        /* Clear search view flag before calling getChatFragment to avoid stack overflow */
        mInSearchView = false;

                FragmentManager manager = getFragmentManager();

                SearchAgainFragment fragment =
                        (SearchAgainFragment) manager.findFragmentByTag(
                                SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);

                if (fragment != null) {
                    final FragmentTransaction transaction = manager.beginTransaction();
                    transaction.remove(fragment);
                    transaction.commit();
                }

                ChatFragment chatFragment = getChatFragment(false);
                if (chatFragment != null) {
                    final View view = chatFragment.getView();
                    if (view != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                            view.animate().alpha(1).withLayer();
                        else
                            view.animate().alpha(1);
                    }
                }

                if (transitioning) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setComposeItemsVisibility(View.VISIBLE);
                        }
                    }, 150);
                }
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setDisplayShowCustomEnabled(false);

                    SearchEditTextLayout seTextLayout = (SearchEditTextLayout) actionBar.getCustomView();
                    if (seTextLayout.isExpanded()) {
                        seTextLayout.collapse(false /* animate */);
                    }
                    if (seTextLayout.isFadedOut()) {
                        seTextLayout.fadeIn();
                    }
                }
                invalidateOptionsMenu();

                if (transitioning) {
                    restoreActionBar();
                }
    }

    private boolean isInSearchUi() {
        return mInSearchView;
    }

    private void enterInfoUI(@Nullable Event event) {
        if (event == null) {
            return;
        }

        FragmentManager manager = getFragmentManager();
        final FragmentTransaction transaction = manager.beginTransaction();

        EventInfoFragment eventInfoFragment = new EventInfoFragment();
        transaction.add(R.id.chat, eventInfoFragment, EventInfoFragment.TAG_EVENT_INFO_FRAGMENT);

        eventInfoFragment.setEvent(getPartner(), event.getId(), event);
        transaction.commitAllowingStateLoss();

        mInInfoView = true;

        if (mTitle != null) {
            mTitle.setText(R.string.message_info_title);
        }
        if (mSubTitle != null) {
            mSubTitle.setVisibility(View.GONE);
        }

        setComposeItemsVisibility(View.GONE);
        hideSoftKeyboard(R.id.compose_text);

        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        invalidateOptionsMenu();
        invalidateActionBar(isInSearchUi() | isInInfoUi());
    }

    private void exitInfoUI() {
        mInInfoView = false;

        FragmentManager manager = getFragmentManager();

        EventInfoFragment fragment =
                (EventInfoFragment) manager.findFragmentByTag(
                        EventInfoFragment.TAG_EVENT_INFO_FRAGMENT);

        if (fragment != null) {
            final FragmentTransaction transaction = manager.beginTransaction();
            transaction.setCustomAnimations(android.R.animator.fade_out, 0);
            transaction.remove(fragment);
            transaction.commit();
        }

        invalidateOptionsMenu();
        restoreActionBar();

        if (canMessagePartner()) {
            setComposeItemsVisibility(View.VISIBLE);
        }
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    private boolean isInInfoUi() {
        return mInInfoView;
    }

    // Get a possible pending attachment file to send
    private String getPendingAttachmentFile() {
        Intent intent = getIntent();

        if(Intent.ACTION_SEND.equals(intent.getAction())) {
            // We have some type of send intent for a file∂
            Uri fileUri = getPendingAttachmentUri(intent);
            // Check if we have a file
            if (fileUri != null) {
                return fileUri.toString();
            }

            return null;
        } else if(Intent.ACTION_SENDTO.equals(intent.getAction())) {
            // We store and retrieve this data internally from the search fragment
            return Extra.DATA.from(intent);
        }

        return null;
    }

    // Get a possible pending attachment plaintext to send
    private String getPendingAttachmentText() {
        Intent intent = getIntent();

        if(Intent.ACTION_SEND.equals(intent.getAction())) {
            // Check if we have plaintext
            if ("text/plain".equals(intent.getType())) {
                ClipData attachmentClipData = intent.getClipData();

                if (attachmentClipData != null) {
                    if (attachmentClipData.getItemCount() == 0) {
                        return null;
                    }

                    StringBuilder sb = new StringBuilder();

                    for (int i = 0; i < attachmentClipData.getItemCount(); i++) {
                        CharSequence text = attachmentClipData.getItemAt(i).getText();

                        if (!TextUtils.isEmpty(text)) {
                            sb.append(text);
                            sb.append("\n");
                        }
                    }

                    if (sb.length() != 0) {
                        return sb.toString().trim();
                    } else {
                        return null;
                    }
                }
            }

            return null;
        } else if(Intent.ACTION_SENDTO.equals(intent.getAction())) {
            // We store and retrieve this data internally from the search fragment
            return Extra.TEXT.from(intent);
        }

        return null;
    }

    @Nullable
    private Uri getPendingAttachmentUri(@NonNull final Intent intent) {
        Uri fileUri = null;
        try {
            Object stream = intent.getExtras().get(Intent.EXTRA_STREAM);
            if (stream != null) {
                if (stream instanceof Uri) {
                    fileUri = (Uri) stream;
                }
                else if (stream instanceof List<?>) {
                    /* no support for multi-file attachments although first item could be used(?) */
                    List uriList = (List) stream;
                    fileUri = uriList.size() > 0 ? (Uri) uriList.get(0) : null;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get attachment uri from intent: " + e.getMessage());
        }
        return fileUri;
    }

    protected void hideSoftKeyboard(int... viewResourceIDs) {
        for (int id : viewResourceIDs) {
            View view = findViewById(id);
            if (view != null) {
                DialerUtils.hideInputMethod(view);
                view.clearFocus();
            }
        }
    }

    @Override
    public void onKeyboardHeightChanged(boolean isVisible, int keyboardHeight) {
        mIsKeyboardVisible = isVisible;
        // Store keyboard's height to adjust adjustment grid's height
        if (isVisible && keyboardHeight > ViewUtil.dp(200.f, this)) {
            mKeyboardHeight = keyboardHeight;
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().
                    putInt(KEYBOARD_HEIGHT_KEY, mKeyboardHeight).commit();
        }

        adjustLayoutTransitionForKeyboard();
        if (isVisible) {    // keyboard appears
            hideAttachmentGrid(false);
        }
        else {              // keyboard disappears
            if (mShouldShowAttachmentGrid) {
                showAttachmentGrid(false);
            }
        }
    }

    private void showAttachmentGrid() {
        showAttachmentGrid(true);
    }

    private void showAttachmentGrid(boolean usingTransition) {
        mShouldShowAttachmentGrid = false;
        if (mAttachmentGrid.getVisibility() == View.VISIBLE) {
            return;
        }
        // Layout Transitions
        if (usingTransition) {
            adjustLayoutTransitionForGrid();
        }
//        // Adjust height so that it matches the keyboard's height
//        ViewGroup.LayoutParams params = mAttachmentGrid.getLayoutParams();
//        if (mKeyboardHeight == 0) {
//            mKeyboardHeight = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
//                    .getInt(KEYBOARD_HEIGHT_KEY, params.height);
//        }
//        params.height = mKeyboardHeight;
//        mAttachmentGrid.setLayoutParams(params);
        mAttachmentGrid.setVisibility(View.VISIBLE);

        AnimUtils.setViewRotation(findViewById(R.id.compose_attach), 100, 0);
    }



    private void hideAttachmentGrid() {
        hideAttachmentGrid(true);
    }
    private void hideAttachmentGrid(boolean usingTransition) {
        mShouldShowAttachmentGrid = false;
        if (mAttachmentGrid.getVisibility() == View.GONE) {
            return;
        }
        // Layout Transitions
        if (usingTransition && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) {
            adjustLayoutTransitionForGrid();
            LayoutTransition transition = new LayoutTransition();
            transition.addTransitionListener(new LayoutTransition.TransitionListener() {
                @Override
                public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                }

                @Override
                public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                    mAttachmentGridFrame.setLayoutTransition(null);
                }
            });
            mAttachmentGridFrame.setLayoutTransition(transition);
        }

        mAttachmentGrid.setVisibility(View.GONE);
        mAttachmentGrid.setSelection(0);

        AnimUtils.setViewRotation(findViewById(R.id.compose_attach), 100, 45);
    }

    private void toggleAttachmentGrid() {
        /*
         * In case the keyboard is visible, we can't show the attachment grid right away, or else
         * a graphic glitch will occur. So, we hide the soft keyboard and when
         * "onKeyboardHeightChanged()" is fired, we show the attachment grid.
         */
        boolean isGridVisible = mAttachmentGrid.getVisibility() == View.VISIBLE;
        if (isGridVisible) {    // should hide grid
            hideAttachmentGrid();
        }
        else {                  // should show grid
            if (mIsKeyboardVisible) {
                hideSoftKeyboard(R.id.compose_text);
                mShouldShowAttachmentGrid = true;
            }
            else {
                // Hide the keyboard if undocked and clear focus
                hideSoftKeyboard(R.id.compose_text);
                showAttachmentGrid();
            }
        }
    }

    private void setComposeItemsVisibility(int visibility) {
        if (mComposeLayout != null) {
            mComposeLayout.setVisibility(visibility);
        }

        if (mComposeSend != null) {
            mComposeSend.setVisibility(visibility);
        }

        if (mBurnDelayContainer != null) {
            mBurnDelayContainer.setVisibility(visibility);
        }
    }

    private void setComposeItemsEnabled(boolean enabled) {
        if (mComposeText != null) {
            mComposeText.setEnabled(enabled);
        }
        if (mComposeAttach != null) {
            mComposeAttach.setEnabled(enabled);
        }
        if (mComposeSend != null) {
            mComposeSend.setEnabled(enabled);
        }
        ViewUtil.setEnabled(mBurnDelayContainer, enabled);
    }

    private void scrollToBottom() {
        ChatFragment fragment = getChatFragment(false);
        if (fragment != null) {
            fragment.scrollToBottom();
        }
    }

    private void enableLayoutTransition() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        ViewGroup rootView = (ViewGroup)findViewById(R.id.activity);
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        transition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {}

            @Override
            public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                disableLayoutTransition();
            }
        });
        rootView.setLayoutTransition(transition);
    }

    private void disableLayoutTransition() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        ViewGroup rootView = (ViewGroup)findViewById(R.id.activity);
        rootView.setLayoutTransition(null);
    }

    private void adjustLayoutTransitionForKeyboard() {
        // Disable transition when hiding keyboard. If the grid is going to appear in its place,
        // it will enable the transition itself.
        if (mIsKeyboardVisible) {   // keyboard appears
            disableLayoutTransition();
        }
        else {                      // keyboard disappears
            disableLayoutTransition();
        }
    }

    private void adjustLayoutTransitionForGrid() {
        enableLayoutTransition();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setDefaultVisibilityFlags() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setFullScreenVisibilityFlags() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    protected boolean refresh(final boolean forceRefresh) {

        if (mRefreshing) {
            return false;
        }

        mRerunRefresh = false;
        mRerunForcedRefresh = false;
        mRefreshing = true;
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                AsyncUtils.execute(new RefreshTask(ConversationUtils.getConversations(), getConversation(), forceRefresh), getPartner());
            }
        });

        return true;
    }

    protected synchronized void cancelAutoRefresh() {
        if( mTimer != null ) {
            mTimer.cancel();
            mTimer = null;
            mRefreshing = false;
        }
    }

    protected synchronized void scheduleAutoRefresh(long autoRefreshTime) {
        cancelAutoRefresh();
        mTimer = new Timer( "conversation:auto-refresh" );
        mTimer.schedule(new AutoRefresh(this), new Date(autoRefreshTime));
    }

    private void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(ConversationActivity.this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
    }

    private void doUnbindService() {
        if (mPhoneIsBound) {
            mPhoneService.removeStateChangeListener(this);
            // Detach our existing connection.
            unbindService(phoneConnection);
            mPhoneIsBound = false;
        }
    }

    private void setBurnContainerVisibility(final int visibility) {
        @SuppressLint("WrongViewCast")
        final VerticalSeekBar seeker = (VerticalSeekBar) findViewById(R.id.burn_delay_value);
        if (seeker.getVisibility() == visibility) {
            return;
        }
        seeker.setVisibility(visibility);

        Conversation conversation = getConversation();
        if (conversation != null) {
            seeker.setProgress(conversation.hasBurnNotice()
                    ? BurnDelay.Defaults.getLevel(conversation.getBurnDelay()) : 0);
        }
    }

    private String getPartnerPhotoUrl() {
        String photoUri= null;
        final String partnerId = getPartner();
        byte[] userInfoBytes = ZinaMessaging.getUserInfoFromCache(partnerId);
        if (userInfoBytes != null) {
            AsyncTasks.UserInfo userInfo = AsyncTasks.parseUserInfo(userInfoBytes);
            if (userInfo != null) {
                if (!TextUtils.isEmpty(userInfo.mAvatarUrl)
                        && !LoadUserInfo.URL_AVATAR_NOT_SET.equals(userInfo.mAvatarUrl)) {
                    photoUri = userInfo.mAvatarUrl;
                }
            }
        }
        return photoUri;
    }


    private void addToContact(String partnerId, String name) {
        addToContact(partnerId, name, (byte[]) null);
    }

    private void addToContact(final String partnerId, final String name, String photoUrl) {
        if (TextUtils.isEmpty(photoUrl)) {
            addToContact(partnerId, name);
            return;
        }

        AsyncUtils.execute(new DownloadImageTask(this) {
            @Override
            protected void onPostExecute(byte[] imageData) {
                if (mAvatarFetchProgressDialog != null) {
                    mAvatarFetchProgressDialog.dismiss();
                    mAvatarFetchProgressDialog = null;
                }

                addToContact(partnerId, name, imageData);
            }
        }, photoUrl);
        if (mAvatarFetchProgressDialog == null) {
            mAvatarFetchProgressDialog = new ProgressDialog(this);
            mAvatarFetchProgressDialog.setMessage(getText(R.string.avatar_fetch_loading_dialog));
            mAvatarFetchProgressDialog.show();
        }
    }

    private void addToContact(String partnerId, String name, byte[] photoData) {
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT, ContactsContract.Contacts.CONTENT_URI);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

        ArrayList<ContentValues> data = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
        row1.put(ContactsContract.CommonDataKinds.Website.LABEL, partnerId);
        try {
            partnerId = URLEncoder.encode(partnerId, "UTF-8");
        } catch (UnsupportedEncodingException ignore) {}
        row1.put(ContactsContract.CommonDataKinds.Website.URL, "silentphone:" + partnerId);
        row1.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_OTHER);
        data.add(row1);
        if (photoData != null) {
            ContentValues row2 = new ContentValues();
            row2.put(ContactsContract.Contacts.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
            row2.put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData);
            data.add(row2);
        }
        intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data);

        AppLifecycleNotifier.getSharedInstance().onWillStartExternalActivity(true);
        startActivityForResult(intent, RESULT_ADD_CONTACT);
    }

    private void createBurnDelayChangeEvent(@NonNull ConversationRepository repository,
            @NonNull Conversation conversation) {
        Context context = SilentPhoneApplication.getAppContext();
        String text = context.getString(R.string.group_messaging_new_burn_this,
                BurnDelay.Defaults.getAlternateLabel(context,
                        BurnDelay.Defaults.getLevel(conversation.getBurnDelay())));
        int tag = InfoEvent.INFO_NEW_BURN;
        String displayName = LoadUserInfo.getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = LoadUserInfo.getDisplayName();
        }
        String details = StringUtils.jsonFromPairs(
                new Pair<String, Object>(JsonStrings.GROUP_BURN_SEC, conversation.getBurnDelay()),
                new Pair<String, Object>(JsonStrings.MEMBER_ID, LoadUserInfo.getUuid()),
                new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
        InfoEvent event = MessageUtils.createInfoEvent(getPartner(), tag, text, details);
        repository.historyOf(conversation).save(event);
        MessageUtils.notifyConversationUpdated(context, getPartner(), true,
                ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, event.getId());
    }

    private Rect mHitRectangle = new Rect();
    private int[] mScreenLocation = new int[2];

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        View burnButton = findViewById(R.id.burn);
        int visibility = burnButton.getVisibility();
        if (visibility == View.VISIBLE && event.getAction() == MotionEvent.ACTION_DOWN) {
            mBurnDelayContainer.getHitRect(mHitRectangle);
            mBurnDelayContainer.getLocationOnScreen(mScreenLocation);
            mHitRectangle.offset(mScreenLocation[0] - mBurnDelayContainer.getLeft(),
                    mScreenLocation[1] - mBurnDelayContainer.getTop());
            boolean inside = mHitRectangle.contains((int) event.getRawX(), (int) event.getRawY());
            if (!inside) {
                View seeker = findViewById(R.id.burn_delay_value);
                seeker.getHitRect(mHitRectangle);
                seeker.getLocationOnScreen(mScreenLocation);
                mHitRectangle.offset(mScreenLocation[0] - seeker.getLeft(),
                        mScreenLocation[1] - seeker.getTop());
                inside = mHitRectangle.contains((int) event.getRawX(), (int) event.getRawY());
                if (!inside) {
                    setBurnContainerVisibility(View.GONE);
                }
            }
        }
        /** @see <a href="https://sentry.silentcircle.org/sentry/spa/issues/4397/">this crash</a> **/
        try {
            return super.dispatchTouchEvent(event);
        } catch (IllegalStateException ignore) {}

        return false;
    }

    private void incrementUnredMessageCount(Context context) {
        if (mUnreadMessageCount == MESSAGE_COUNT_UNKNOWN) {
            AsyncUtils.execute(new ConversationUtils.UnreadEventsRunnable(getPartner(),
                    ConversationActivity.this));
        } else {
            mUnreadMessageCount += 1;
            setUnreadMessageCount();
        }
    }

    private void inviteParticipants(@NonNull final String group,
            @NonNull final CharSequence[] participants) {
        AsyncUtils.execute(new InviteParticipantsRunnable(group, participants));
    }

    private static class AutoRefresh extends TimerTask {

        private WeakReference<ConversationActivity> mConversationActivity;

        public AutoRefresh(ConversationActivity activity) {
            mConversationActivity = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            ConversationActivity activity = mConversationActivity.get();
            if (activity != null) {
                activity.refresh(false);
            }
        }
    }

    private static class InviteParticipantsRunnable implements Runnable {

        private final String mGroupId;
        private final List<CharSequence> mParticipants;

        InviteParticipantsRunnable(final String groupId, final CharSequence[] participants) {
            super();
            mGroupId = groupId;
            mParticipants = Arrays.asList(participants);
        }

        @Override
        public void run() {
            Context context = SilentPhoneApplication.getAppContext();
            ConversationUtils.addParticipants(context, mGroupId, mParticipants);

            /*
             * Generate group avatar and display name if necessary.
             */
            ConversationUtils.GroupData groupData = ConversationUtils.getGroup(mGroupId);
            if (groupData != null) {
                // generate group avatar
                if (TextUtils.isEmpty(groupData.getAvatarInfo())
                        || AvatarProvider.AVATAR_TYPE_GENERATED.equals(groupData.getAvatarInfo())) {
                    AvatarUtils.setGeneratedGroupAvatar(context, mGroupId);
                }

                // generate group name if no name set for group
                if (TextUtils.isEmpty(groupData.getGroupName())) {
                    ConversationUtils.setGeneratedGroupName(mGroupId);
                }

                // clear photo cache to see the result
                ContactPhotoManagerNew.getInstance(context).refreshCache();
                // HACK: notify about conversation changes to refresh conversation list quicker
                MessageUtils.notifyConversationUpdated(context, mGroupId, true);
                // ask views to refresh to see the changes
                MessageUtils.requestRefresh();
            }
        }
    }

    private class RefreshTask extends com.silentcircle.messaging.task.RefreshTask {

        private final boolean mForceRefresh;

        RefreshTask(ConversationRepository repository, Conversation conversation,
                boolean forceRefresh) {
            super(repository, conversation);
            mForceRefresh = forceRefresh;
        }

        protected void onScheduleNext(long next) {
            if (next < Long.MAX_VALUE) {
                scheduleAutoRefresh(next);
            }
        }

        @Override
        protected void onPostExecute(List<Event> events) {

            if (ConfigurationUtilities.mTrace) {
                Log.d(TAG, "RefreshTask.onPostExecute: force refresh " + mForceRefresh
                        + ", rerun required: " + mRerunRefresh
                        + ", rerun forced: " + mRerunForcedRefresh);
            }

            mRefreshing = false;

            if (mRerunRefresh || mRerunForcedRefresh) {
                refresh(mForceRefresh | mRerunForcedRefresh);
            }
        }
    }

}
