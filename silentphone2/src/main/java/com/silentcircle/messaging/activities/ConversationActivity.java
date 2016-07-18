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
package com.silentcircle.messaging.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.calllognew.IntentProvider;
import com.silentcircle.contacts.utils.ClipboardUtils;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.fragments.ChatFragment;
import com.silentcircle.messaging.fragments.SearchAgainFragment;
import com.silentcircle.messaging.listener.ClickSendOnEditorSendAction;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
import com.silentcircle.messaging.listener.OnConfirmListener;
import com.silentcircle.messaging.listener.SendMessageOnClick;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.AudioProvider;
import com.silentcircle.messaging.providers.PictureProvider;
import com.silentcircle.messaging.providers.TextProvider;
import com.silentcircle.messaging.providers.VCardProvider;
import com.silentcircle.messaging.providers.VideoProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.services.SCloudCleanupService;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.task.SendMessageTask;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.SoundNotifications;
import com.silentcircle.messaging.views.ConversationOptionsDrawer;
import com.silentcircle.messaging.views.ScreenLockView;
import com.silentcircle.messaging.views.UploadView;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.list.OnListFragmentScrolledListener;
import com.silentcircle.silentphone2.list.SearchFragment;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SearchEditTextLayout;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ConversationActivity extends AppCompatActivity implements
        ChatFragment.Callback, OnListFragmentScrolledListener, SearchFragment.HostInterface,
        AxoMessaging.AxoMessagingStateCallback, ScreenLockView.OnUnlockListener,
        TiviPhoneService.ServiceStateChangeListener {

    @SuppressWarnings("unused")
    private static final String TAG = ConversationActivity.class.getSimpleName();

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 2;

    /* Limit single message, non-attachment-ified text to 1000 symbols */
    private static final int MESSAGE_TEXT_LENGTH_LIMIT = 1000;

    /* Limit single message input text to 5000 symbols */
    private static final int MESSAGE_TEXT_INPUT_LENGTH_LIMIT = 5000;

    /** Limit after which unread message count is displayed with greater than sign */
    public static final int UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT = 10;

    protected Conversation mConversation;
    protected String mConversationPartnerId;

    protected BroadcastReceiver mViewUpdater;

    /*
    protected Handler mHandler;
    protected Runnable mLockViewRunnable = new Runnable() {
        @Override
        public void run() {
            lockViewIfNecessary();
        }
    };
     */

    /* variables for attachment handling */
    private Uri mPendingImageCaptureUri;
    private Uri mPendingVideoCaptureUri;
    private Uri mPendingAudioCaptureUri;
    private static final int R_id_share = 0xFFFF & R.id.share;

    private Toolbar mToolbar;
    private QuickContactBadge mAvatarView;
    private TextView mTitle;
    private View mHome;
    private View mUnreadMessageNotification;
    private TextView mUnreadMessageCount;
    private DrawerLayout mDrawerLayout;
    private MenuItem mOptionsMenuItem;
    private SearchAgainFragment mSearchFragment;

    private boolean mInSearchView;
    private String mTextToForward;
    private Message mMessageToForward;

    /* Progress bar for generic operations */
    private UploadView mProgressBar;
    private ViewGroup mComposeLayout;
    private EditText mComposeText;
    private ScreenLockView mPasswordOverlay;
    private ActionMode mActionMode;
    private boolean mLockTimePersisted = true;

    private List<Event> mEvents;

    /* State variables for refreshing timed messages, etc */
    private Timer mTimer;
    private boolean mRefreshing;
    private boolean mRerunRefresh;
    private boolean mRerunForcedRefresh;
    private boolean mIsVisible;
    private boolean mDestroyed;
    private boolean mIsViewLocked;

    private boolean mIsAxolotlRegistered;

    protected Bundle mSavedInstanceState;

    private ContactPhotoManagerNew mContactPhotoManager;

    public static final String [] SUPPORTED_IMTO_HOSTS = {
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

    private Runnable mOpenActionBarRunnable = new Runnable() {
        public void run() {
            openActionBarQueryField();
        }
    };

    private Runnable mOpenMessageInputRunnable = new Runnable() {
        public void run() {
            if(mComposeText != null) {
                DialerUtils.showInputMethod(mComposeText);
            }
        }
    };

    private TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable editable) {
            if(editable == mComposeText.getEditableText()) {
                ImageView buttonSend = (ImageView) findViewById(R.id.compose_send);
                Integer imageResource = (Integer) buttonSend.getTag();
                int newImageResource = editable.length() > 0
                        ? R.drawable.ic_action_send
                        : R.drawable.ic_action_attachment_dark;
                if (imageResource == null || imageResource != newImageResource) {
                    buttonSend.setTag(newImageResource);

                    if (imageResource == null && newImageResource == R.drawable.ic_action_attachment_dark) {
                        // Special case
                        // Don't animate to the attachment icon on activity creation - it is
                        // already that icon by default (tag (imageResource) is null at this point)
                    } else {
                        ViewUtil.animateImageChange(ConversationActivity.this, buttonSend, newImageResource);
                    }
                }

                if(editable.toString().length() > MESSAGE_TEXT_INPUT_LENGTH_LIMIT) {
                    Toast.makeText(ConversationActivity.this, getString(R.string.messaging_compose_length_error), Toast.LENGTH_SHORT).show();

                    mComposeText.setText(editable.subSequence(0, MESSAGE_TEXT_INPUT_LENGTH_LIMIT));
                    mComposeText.setSelection(mComposeText.getText().length());
                }
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
                if (!mInSearchView) {
                    enterSearchUI();
                }
            }

            if (mSearchFragment != null && mSearchFragment.isVisible()) {
                mSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    TextView.OnEditorActionListener mPhoneSearchQueryEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                if (!mInSearchView)
                    return false;
                String query = mSearchFragment.getQueryString();
                if (!TextUtils.isEmpty(query) && Utilities.isValidSipUsername(query))
                    mSearchFragment.validateUser(query);
                return true;
            }
            return false;
        }
    };

    private View.OnFocusChangeListener mTextFocusListener = new View.OnFocusChangeListener() {

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            /* when view receives focus, scroll to bottom of conversation */
            if (hasFocus && !mInSearchView) {
                scrollToBottom();
            }
        }
    };

    /* Listener for attach button to start file selection. */
    private View.OnClickListener mAttachButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mComposeText.length() <= 0) {
                if(!LoadUserInfo.canSendAttachments(getApplicationContext())) {
                    showDialog(R.string.information_dialog, R.string.basic_feature_info,
                            android.R.string.ok, -1);
                    return;
                }

                selectFile();
            }
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
            hideSoftKeyboard(R.id.compose_text);
            mOptionsMenuItem.setIcon(R.drawable.ic_drawer_open);
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            mOptionsMenuItem.setIcon(R.drawable.ic_drawer_closed);
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            // Nothing to do
        }
    };

    /**
     * If the search term is empty and the user closes the soft keyboard, close the search UI.
     */
    private final View.OnKeyListener mSearchEditTextLayoutListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN &&
                    TextUtils.isEmpty(mSearchView.getText())) {
                maybeExitSearchUi();
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
            mPhoneService = ((TiviPhoneService.LocalBinder)service).getService();
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
        super.onCreate(savedInstanceState);

        mSavedInstanceState = savedInstanceState;

        mActionBarHeight = getResources().getDimensionPixelSize(R.dimen.action_bar_height_large);

        mContactPhotoManager = ContactPhotoManagerNew.getInstance(getApplicationContext());

        setContentView(R.layout.activity_conversation_with_drawer);

        initConversationView(false);

        doBindService();

        // TODO: Is this worth going here?
        //runAttachmentManager();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (mInSearchView) {
            exitSearchUI(true);
        }

        initConversationView(true);

        // check if we need to forward a message and do it here
        doMessageForward();
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsVisible = true;

        mIsViewLocked = lockViewIfNecessary();
        registerViewUpdater();
        updateViews();

        Notifications.cancelMessageNotification(this, getPartner());

        if (mConversationPartnerId == null) {
            mConversationPartnerId = getPartner();
        }

        if (getConversation() != null) {
            refresh(false);

            if (mComposeText.length() == 0) {
                mComposeText.setText(getConversation().getUnsentText());
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsVisible = false;
        mProgressBar.setVisibility(View.GONE);
        unregisterReceiver(mViewUpdater);
        saveUnsentText();
        mEvents = null;
        updateLastLockTimeIfNecessary();
        /*
        mHandler.removeCallbacks(mLockViewRunnable);
         */

    }

    private void updateLastLockTimeIfNecessary() {
        if (!mLockTimePersisted) {
            MessagingPreferences.getInstance(getApplicationContext())
                    .setLastMessagingUnlockTime(System.currentTimeMillis());
        }
        mLockTimePersisted = true;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        Conversation conversation = getConversation();

        byte[] chatKeyData =
                KeyManagerSupport.getPrivateKeyData(getContentResolver(),
                        ConfigurationUtilities.getChatProtectionKey());
        MessagingPreferences preferences = MessagingPreferences.getInstance(this);
        boolean visible = (!(preferences.isMessagingUnlockTimeExpired()
                && chatKeyData != null)
                && !mInSearchView && hasChatFragment());

        MenuItem shareLocation = menu.findItem(R.id.action_share_location);
        boolean locationSharingEnabled = LocationUtils.isLocationSharingAvailable(this)
                && (conversation != null && conversation.isLocationEnabled())
                && visible;
        shareLocation.setIcon(locationSharingEnabled
                ? R.drawable.ic_location_selected : R.drawable.ic_location_unselected);
        shareLocation.setVisible(locationSharingEnabled);

        MenuItem burnNotice = menu.findItem(R.id.action_burn_notice);
        boolean burnNoticeEnabled = (conversation != null && conversation.hasBurnNotice())
                && visible;
        burnNotice.setIcon(burnNoticeEnabled
                ? R.drawable.ic_burn_enabled : R.drawable.ic_burn_disabled);
        burnNotice.setVisible(burnNoticeEnabled);

        // TODO: determine whether call is with particular person
        boolean hasCall = TiviPhoneService.calls.getCallCount() > 0;
        MenuItem silentPhoneCall = menu.findItem(R.id.action_silent_phone_call);
        silentPhoneCall.setVisible(visible && !hasCall);

        // keep reference to this item
        mOptionsMenuItem = menu.findItem(R.id.options);
        mOptionsMenuItem.setVisible(visible);
        if (mDrawerLayout != null) {
            mOptionsMenuItem.setIcon(mDrawerLayout.isDrawerOpen(GravityCompat.END)
                    ? R.drawable.ic_drawer_open : R.drawable.ic_drawer_closed);
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
            case R.id.action_burn_notice:
                /*
                 * do not allow user to switch burn off
                 * setBurnNotice(false);
                 * for now just pop out the options
                 */
                toggleOptionsDrawer();
                consumed = true;
                break;
            case R.id.action_share_location:
                setLocationSharing(false);
                consumed = true;
                break;
            case R.id.action_silent_phone_call:
                launchSilentPhoneCall();
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
        }
        else if (mInSearchView && !TextUtils.isEmpty(getPartner())) {
            // close search view
            exitSearchUI(true);
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        cancelAutoRefresh();
        doUnbindService();
        super.onDestroy();
    }

    protected boolean isAxolotlInitialised() {
        // verify that Axolotl has been initialised
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
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
        }
        return axoRegistered;
    }

    protected void initConversationView(boolean fromIntent) {
        mIsAxolotlRegistered = isAxolotlInitialised();

        String partner = getPartner();

        mConversation = getConversation(getPartner());
        SendMessageOnClick sendMessageOnClickListener =
                createSendMessageOnClickListener(mConversation);
        findViewById(R.id.compose_send).setOnClickListener(sendMessageOnClickListener);
        findViewById(R.id.compose_attach).setOnClickListener(mAttachButtonListener);

        mComposeLayout = (ViewGroup) findViewById(R.id.compose);
        hideSoftKeyboard(R.id.compose_text);
        mComposeText = (EditText) findViewById(R.id.compose_text);
        mComposeText.addTextChangedListener(mTextWatcher);
        mComposeText.setOnFocusChangeListener(mTextFocusListener);
        mComposeText.setOnEditorActionListener(new ClickSendOnEditorSendAction());

        mProgressBar = (UploadView) findViewById(R.id.upload);
        findViewById(R.id.upload).setVisibility(View.GONE);

        createOptionsDrawer();
        restoreActionBar();

        if (mIsAxolotlRegistered) {
            if (TextUtils.isEmpty(partner)) {
                enterSearchUI();
            } else {
                getChatFragment(true);

                String pendingAttachmentFile = getPendingAttachmentFile();
                String pendingAttachmentText = getPendingAttachmentText();

                boolean hasPendingAttachment = !TextUtils.isEmpty(pendingAttachmentFile)
                        || !TextUtils.isEmpty(pendingAttachmentText);

                boolean canSendAttachments =
                        LoadUserInfo.canSendAttachments(getApplicationContext());

                if (hasPendingAttachment && !canSendAttachments) {
                    showDialog(R.string.information_dialog, R.string.basic_feature_info,
                            android.R.string.ok, -1);
                }

                if(!TextUtils.isEmpty(pendingAttachmentFile) && canSendAttachments) {
                    handleAttachment(new Intent().setData(Uri.parse(pendingAttachmentFile)), false);
                } else if(!TextUtils.isEmpty(pendingAttachmentText) && canSendAttachments) {
                    mComposeText.setText(pendingAttachmentText);
                } else if(fromIntent) {
                    mComposeText.post(mOpenMessageInputRunnable);
                }
            }
        }

        mPasswordOverlay = (ScreenLockView) findViewById(R.id.password_overlay);
        mPasswordOverlay.setOnUnlockListener(this);

        ViewGroup overlay = (ViewGroup) findViewById(R.id.overlay);
        overlay.setVisibility(mIsAxolotlRegistered ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
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

    @Override
    public void onUnlock() {
        mLockTimePersisted = false;
        updateLastLockTimeIfNecessary();
        mIsViewLocked = lockViewIfNecessary();
        /* start refresh task as some messages can be in unread state, switch to read is necessary */
        refresh(true);
    }


    protected void setLocationSharing(boolean enabled) {
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            conversation.setLocationEnabled(enabled);
            repository.save(conversation);
        }

        invalidateOptionsMenu();
        updateConversation();
    }

    protected void setSendReadReceipts(boolean enabled) {
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            conversation.setSendReadReceipts(enabled);
            repository.save(conversation);
        }

        updateConversation();
    }

    protected void setBurnDelay(long burnDelay) {
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            conversation.setBurnDelay(burnDelay);
            repository.save(conversation);
        }
    }

    protected void setBurnNotice(boolean enabled) {
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
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
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {

            for (Event event : repository.historyOf(conversation).list()) {
                deleteEvent(event);
            }

            repository.historyOf(conversation).clear();
        }
        // clear events cache
        mEvents = null;

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
        String unsentText = mComposeText.getText().toString();
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
        Conversation conversation = getConversation();
        if (repository != null && conversation != null) {
            conversation.setUnsentText(unsentText);
            repository.save(conversation);
        }
    }

    protected void showMessagingFailure(int errorCode, String errorInfo, Message message) {
        if (!isFinishing()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            String partner = message.getConversationID();

            if (SearchUtil.isUuid(partner)) {
                byte[] partnerName = AxoMessaging.getDisplayName(partner);

                if (partnerName != null) {
                    partner = new String(partnerName);
                }
            }

            alert.setTitle(R.string.dialog_title_failed_to_send_message);
            alert.setMessage(getString(R.string.dialog_message_failed_to_send_to,
                    /* FIXME: Uncomment when we don't care about 4.1.3 clients - this is so we try
                       not to show the UUID on error
                    */
//                    message.getConversationID(),
                    partner,
                    getString(MessageErrorCodes.messageErrorToStringId(errorCode))));

            alert.setCancelable(false);
            alert.setPositiveButton(R.string.dialog_button_ok, null);

            alert.show();
        }
    }

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, TAG);

        // Make possible links clickable
        fragmentManager.executePendingTransactions();
        ((TextView) infoMsg.getDialog().findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    protected void restoreActionBar() {
        mToolbar = (Toolbar) findViewById(R.id.conversation_toolbar);
        mToolbar.setContentInsetsAbsolute(0, 0);
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(getPartner());

        String displayName = ConversationUtils.resolveDisplayName(contactEntry, getConversation());
        mTitle = (TextView) mToolbar.findViewById(R.id.title);
        mTitle.setText(displayName);
//                contactEntry != null ? contactEntry.name : getPartner());
        mHome = mToolbar.findViewById(R.id.home);
        mHome.setOnClickListener(mHomeButtonListener);
        mUnreadMessageNotification = mToolbar.findViewById(R.id.unread_message_notification);
        mUnreadMessageCount = (TextView) mToolbar.findViewById(R.id.unread_message_count);
        mUnreadMessageNotification.setVisibility(View.VISIBLE);

        mAvatarView = (QuickContactBadge) mToolbar.findViewById(R.id.message_avatar);
        mAvatarView.setVisibility(View.VISIBLE);
        AvatarUtils.setPhoto(mContactPhotoManager, mAvatarView,
                contactEntry);

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

        setUnreadMessageCount();
    }

    private void setUnreadMessageCount() {
        int unreadMessageCount = ConversationUtils.getUnreadMessageCount(getApplicationContext());
        Conversation conversation = getConversation();
        if (conversation != null) {
            unreadMessageCount -= conversation.getUnreadMessageCount();
        }
        if (mUnreadMessageNotification != null) {
            mUnreadMessageNotification.setVisibility(unreadMessageCount > 0 ? View.VISIBLE : View.GONE);
        }
        if (mUnreadMessageCount != null) {
            mUnreadMessageCount.setText(unreadMessageCount > UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                    ? ">" + UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                    : String.valueOf(unreadMessageCount));
        }
    }

    protected void invalidateActionBar(boolean hidden) {
        mAvatarView.setVisibility(hidden ? View.GONE : View.VISIBLE);
        mUnreadMessageNotification.setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    protected ConversationOptionsDrawer getOptionsDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        mDrawerLayout.setDrawerListener(mDrawerListener);
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

        if (mInSearchView) {
            exitSearchUI(true);
        }

        FragmentManager manager = getFragmentManager();
        final FragmentTransaction transaction = manager.beginTransaction();

        ChatFragment fragment =
                (ChatFragment) manager.findFragmentByTag(ChatFragment.TAG_CONVERSATION_CHAT_FRAGMENT);

        if (fragment == null) {
            if(!createIfNull) {
                return null;
            }

            fragment = new ChatFragment();
            transaction.replace(R.id.chat, fragment, ChatFragment.TAG_CONVERSATION_CHAT_FRAGMENT);
        }
        else {
            transaction.show(fragment);
        }

        try {
            transaction.commitAllowingStateLoss();
        } catch(RuntimeException exception) {
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
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
        if (!axoMessaging.isRegistered()) {
            return null;
        }

        ConversationRepository conversations = axoMessaging.getConversations();
        Conversation conversation = AxoMessaging.getInstance(getApplicationContext())
                .getOrCreateConversation(conversationPartner);

        if (conversation != null) {
            /*
             * For now all conversations will have burn notice for all messages
             * If a conversation does not have burn notice set it to 3 days by default.
             */
            if (!conversation.hasBurnNotice()) {
                conversation.setBurnNotice(true);
                conversation.setBurnDelay(BurnDelay.getDefaultDelay());
                conversations.save(conversation);
            }
        }

        return conversation;
    }

    /**
     * Get the partner's UUID.
     *
     * This function parses the Intent if it's an SENDTO and stores the partners UUID
     * and th partners alias. Users of the the SENDTO should set the alias string.
     *
     * @return The partners UUID
     */
    public String getPartner() {
        Intent intent = getIntent();
        if (intent != null) {
            if( Intent.ACTION_SENDTO.equals( intent.getAction() ) ) {
                Uri uri = intent.getData();
                if (uri != null) {
                    if (Constants.SCHEME_IMTO.equals(uri.getScheme()) && Utilities.isAnyOf(uri.getHost(), SUPPORTED_IMTO_HOSTS)) {
                        mConversationPartnerId = uri.getLastPathSegment();
                    }
                }
            }
            else {
                mConversationPartnerId = Extra.PARTNER.from(intent);
            }
            /*
             * TODO: handle case when conversation activity does not have conversation partner in its intent
             */
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

    protected SendMessageOnClick createSendMessageOnClickListener(Conversation conversation) {

        TextView composeText = (TextView) findViewById(R.id.compose_text);

        return new SendMessageOnClick(composeText, getUsername(), conversation, ConversationUtils.getConversations(getApplicationContext()),
                shouldRequestDeliveryNotification()) {

            @Override
            public void onClick(View button) {
                // TODO: notify about account expiration before sending message
                // TODO: validate session
                // TODO: add message as attachment if it is too long
                CharSequence text = mSource.getText();

                // refresh conversation, it can be changed
                mConversation = getConversation();

                if (text.length() <= 0) {
                    if(!LoadUserInfo.canSendAttachments(getApplicationContext())) {
                        showDialog(R.string.information_dialog, R.string.basic_feature_info,
                                android.R.string.ok, -1);

                        super.onClick(button);

                        return;
                    }

                    hideSoftKeyboard(R.id.compose_text);
                    selectFile();
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
                        if (!getResultStatus() && getResultCode() < 0) {
                            showMessagingFailure(getResultCode(), getResultInfo(), message);
                        }
                    }
                };
                AsyncUtils.execute(task, message);
            }

        };
    }

    protected void createOptionsDrawer() {
        Conversation conversation = getConversation();

        boolean isConversationEmpty =
                conversation == null || getConversationHistory().isEmpty();
        boolean isLocationSharingEnabled =
                conversation == null || conversation.isLocationEnabled();
        boolean isSendReceiptsEnabled =
                conversation == null || conversation.shouldSendReadReceipts();
        boolean hasBurnNotice = conversation == null || conversation.hasBurnNotice();
        long burnDelay = conversation != null ? conversation.getBurnDelay() : BurnDelay.getDefaultDelay();

        ConversationOptionsDrawer drawer = getOptionsDrawer();
        drawer.setConversationOptions(this, getPartner(), isConversationEmpty, isLocationSharingEnabled,
                isSendReceiptsEnabled, hasBurnNotice, burnDelay);
        drawer.setConversationOptionsChangeListener(mConversationOptionsChangeListener);
    }

    protected void toggleOptionsDrawer() {
        if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
        } else {
            mDrawerLayout.openDrawer(Gravity.RIGHT);
        }
    }

    protected void updateViews(List<Event> events) {
        mEvents = events;
        if (!isFinishing() && !mInSearchView) {
            ChatFragment fragment = getChatFragment(true);
            if (fragment != null) {
                fragment.setEvents(events);
            }
            createOptionsDrawer();
        }
    }

    protected void updateViews() {
        updateViews(getConversationHistory());
    }

    protected void updateViewsWithMessage(Message message) {
        if (mEvents != null) {
            boolean messagePresent = false;
            ListIterator<Event> iterator = mEvents.listIterator();
            while (iterator.hasNext()) {
                Event event = iterator.next();
                if (event.getId().equals(message.getId())) {
                    iterator.set(message);
                    messagePresent = true;
                    break;
                }
            }
            if (!messagePresent) {
                mEvents.add(message);
            }
            updateViews(mEvents);
        }
        else {
            updateViews();
        }
    }

    protected String getUsername() {
        return AxoMessaging.getInstance(this).getUserName();
    }

    protected boolean shouldRequestDeliveryNotification() {
        // In Silent Text application this always returns true
        // TODO: determine whether delivery notification should be returned
        // otherwise remove this function and parameter to SendMessageOnClick
        return true;
    }

    protected boolean shouldSendDeliveryNotification(Message message) {
        return message.isRequestReceipt();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
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

    protected void selectFile() {
        selectFileOfType("*/*");
    }

    private void selectFileOfType(String type) {
        ChooserBuilder chooser = new ChooserBuilder(this);
        chooser.label(R.string.share_from);
        chooser.intent(createSelectFileIntent(type));
        chooser.intent(createCaptureAudioIntent(), R.string.capture_audio_label);
        chooser.intent(createCaptureImageIntent());
        chooser.intent(createCaptureVideoIntent(), R.string.capture_video_label);
        chooser.intent(createSelectContactIntent());
        chooser.ignore(new String[]{"com.android.soundrecorder", "com.cyanogenmod.filemanager", "com.borqs.blackphone.music"});
        startActivityForResult(chooser.build(), R_id_share);
    }

    private Intent createCaptureImageIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //FIXME: Hotfix
        File imagePath = new File(getFilesDir(), "captured/image");
        if(!imagePath.exists()) imagePath.mkdirs();
        Uri uri = FileProvider.getUriForFile(this, "com.silentcircle.files", new File(imagePath, PictureProvider.JPG_FILE_NAME));
        mPendingImageCaptureUri = uri;

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(null, uri));
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 10 * 1024 * 1024L);

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

            Event event = MessageUtils.getEventById(ConversationActivity.this, partner, messageId);

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
                    String fileName = attachmentMetaDataJson.getString("FileName");
                    String displayName = attachmentMetaDataJson.optString("DisplayName");

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

        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
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
        if (mEvents != null) {
            return mEvents;
        }

        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
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
        final IntentProvider intentProvider = IntentProvider.getReturnCallIntentProvider(getPartner());
        final Intent intent = intentProvider.getIntent(getApplicationContext());
        startActivity(intent);
    }

    private void handleTextAttachment(String plaintext) {
        try {
            getContentResolver().openOutputStream(TextProvider.CONTENT_URI).write(IOUtils.toByteArray(plaintext));
        } catch (IOException impossible) {
            Log.e(TAG, "Text attachment error", impossible);

            return;
        }

        handleAttachment(new Intent().setData(TextProvider.CONTENT_URI), true);
    }

    private void handleAttachment(final Intent data, final boolean isUnique) {
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
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
        if (repository == null) {
            return;
        }

        if (attachment == null) {
            return;
        }

        Uri uri = attachment.getData();
        if (uri == null) {
            uri = attachment.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (uri == null) {
            return;
        }

        final long size = AttachmentUtils.getFileSize(this, uri);

        if (size <= 0) {
            return;
        }

        if (size >= AttachmentUtils.FILE_SIZE_LIMIT) {
            AttachmentUtils.showFileSizeErrorDialog(this);
            return;
        }

        Message message = MessageUtils.composeMessage(getUsername(), "",
                shouldRequestDeliveryNotification(), getConversation(), null);
        EventRepository events = repository.historyOf(getConversation());

        message.setState(MessageStates.COMPOSING);
        message.setAttachment("");
        events.save(message);

        // message has been saved, refresh view to show it
        // alternative would be to MessageUtils.notifyConversationUpdated
        updateViewsWithMessage(message);

        Intent serviceIntent = Action.UPLOAD.intent(this, SCloudService.class);
        Extra.PARTNER.to(serviceIntent, message.getConversationID());
        Extra.ID.to(serviceIntent, message.getId());
        serviceIntent.putExtra("IS_UNIQUE", isUnique);
        serviceIntent.setData(uri);
        startService(serviceIntent);
    }

    private void registerViewUpdater() {

        mViewUpdater = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String partner = intent.getStringExtra(Extra.PARTNER.getName());
                boolean forCurrentConversation =
                        (!TextUtils.isEmpty(partner) && (partner.equals(getPartner())));
                switch (Action.from(intent)) {
                    case RECEIVE_MESSAGE:
                        /*
                         * Sound notification for an arrived message for another conversation
                         * Play sound and stop broadcast insetad of showing message alarm notification.
                         */
                        boolean soundsEnabled =
                                MessagingPreferences.getInstance(context).getMessageSoundsEnabled();
                        if (!forCurrentConversation && soundsEnabled) {
                            SoundNotifications.playReceiveMessageSound();
                            setUnreadMessageCount();
                        }
                    case UPDATE_CONVERSATION:
                        /* update view with new message information */
                        if (forCurrentConversation) {
                            handleUpdateNotification(intent);
                            if (isOrderedBroadcast()) {
                                abortBroadcast();
                            }
                        }
                        break;
                    case PROGRESS:
                        if(Extra.PARTNER.from(intent).equals(getPartner())) {
                            mProgressBar.setProgress(Extra.TEXT.getInt(intent), Extra.PROGRESS.getInt(intent), null);
                        }
                        break;
                    case CANCEL:
                        mProgressBar.setVisibility(View.GONE);
                        break;
                    case ERROR:
                        int errorResourceId = Extra.TEXT.getInt(intent);

                        Toast.makeText(ConversationActivity.this, getString(errorResourceId), Toast.LENGTH_LONG).show();

                        break;
                    case DOWNLOAD:
                        String messageId = Extra.ID.from(intent);

                        Intent serviceIntent = Action.DOWNLOAD.intent(context, SCloudService.class);
                        Extra.PARTNER.to(serviceIntent, partner);
                        Extra.ID.to(serviceIntent, messageId);
                        startService(serviceIntent);
                        break;
                    case RECEIVE_ATTACHMENT:
                        onReceiveAttachment(intent);
                        break;
                    default:
                        break;
                    }
                }
            };

        registerReceiver(mViewUpdater, Action.RECEIVE_MESSAGE.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.UPDATE_CONVERSATION.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.PROGRESS.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.CANCEL.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.ERROR.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.DOWNLOAD.filter(), MESSAGE_PRIORITY);
        registerReceiver(mViewUpdater, Action.RECEIVE_ATTACHMENT.filter(), MESSAGE_PRIORITY);
    }

    private void handleUpdateNotification(Intent intent) {
        // clear events cache
        mEvents = null;

        boolean forceRefresh = Extra.FORCE.getBoolean(intent);
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

    private Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int priority) {
        filter.setPriority(priority);
        return registerReceiver(receiver, filter);
    }

    @Override
    public void onActionModeCreated(ActionMode mode) {
        mActionMode = mode;
        findViewById(R.id.compose).setVisibility(View.GONE);
        hideSoftKeyboard(R.id.compose_text);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void onActionModeDestroyed() {
        mActionMode = null;
        findViewById(R.id.compose).setVisibility(mInSearchView ? View.GONE : View.VISIBLE);
        if (!mInSearchView) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
    }

    @Override
    public void onActionPerformed() {
        updateViews();
    }

    @Override
    public void performAction(int actionID, List<Event> targets) {
        for (Event target : targets) {
            performAction(actionID, target);
        }
    }

    @Override
    public void axoRegistrationStateChange(boolean registered) {
        mIsAxolotlRegistered = registered;
        if (registered && !isFinishing()) {
            AxoMessaging axoMessaging = AxoMessaging.getInstance(getApplicationContext());
            axoMessaging.removeStateChangeListener(this);

            // restart activity with its own intent
            Intent messagingIntent = ContactsUtils.getMessagingIntent(getPartner(), this);
            startActivity(messagingIntent);
        }
    }

    @Override
    public void zrtpStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
    }

    @Override
    public void callStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
        invalidateOptionsMenu();
    }

    protected void performAction(int actionID, final Event event) {
        switch (actionID) {
            case R.id.burn:
                boolean soundsEnabled =
                        MessagingPreferences.getInstance(
                                SilentPhoneApplication.getAppContext()).getMessageSoundsEnabled();
                if (soundsEnabled) {
                    SoundNotifications.playBurnMessageSound();
                }
                MessageUtils.burnMessage(this, event);
                break;
            case R.id.delete:
                deleteEvent(event);
                break;
            case R.id.copy:
                ClipboardUtils.copyText(this, /* label */ null, event.getText(), true);
                break;
            case R.id.action_resend:
                requestResend(event);
                break;
            case R.id.info:
                MessageUtils.showEventInfoDialog(this, event);
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
        ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
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
        if (event instanceof OutgoingMessage) {
            OutgoingMessage message = (OutgoingMessage) event;
            if (/* !isTalkingToSelf() */ true) {
                ConversationRepository repository = ConversationUtils.getConversations(getApplicationContext());
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
        }
        else {
            Log.e(TAG, "Resend requested for other type of event than OutgoingMessage");
        }
    }

    protected void forwardMessage(final Event event) {
        if (event instanceof Message) {
            Message message = (Message) event;
            if (message.hasAttachment()) {
                mMessageToForward = message;
            }
            else {
                mTextToForward = message.getText();
            }

            enterSearchUI();
        }
        else {
            Log.w(TAG, "Unable to forward event which is not a message");
        }
    }

    /*
     * Do the actual forward action:
     *
     * If message a message with an attachment is being forwarded, send it,
     * If a text message is being forwarded, populate compose field.
     */
    protected void doMessageForward() {
        if (mMessageToForward != null) {
            MessageUtils.forwardMessage(this, getUsername(), getPartner(), mMessageToForward);
            mMessageToForward = null;
        } else if (!TextUtils.isEmpty(mTextToForward)) {
            mComposeText.setText(mTextToForward);
            mTextToForward = null;
        }
    }

    private void openActionBarQueryField() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        ((TextView)mToolbar.findViewById(R.id.title)).setText(null);
        ((TextView)mToolbar.findViewById(R.id.sub_title)).setText(null);
        ((SearchEditTextLayout)actionBar.getCustomView()).expand(false /* animate */, true /* requestFocus */);
        ((SearchEditTextLayout)actionBar.getCustomView()).showBackButton(false);
    }

    private void maybeExitSearchUi() {
        if (mInSearchView && TextUtils.isEmpty(mSearchQuery)) {
            exitSearchUI(false);
            DialerUtils.hideInputMethod(mSearchView);
        }
    }

    protected void enterSearchUI() {
        FragmentManager manager = getFragmentManager();
        final FragmentTransaction transaction = manager.beginTransaction();
        mSearchFragment =
                (SearchAgainFragment) manager.findFragmentByTag(
                        SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);

        if (mSearchFragment == null) {
            mSearchFragment = new SearchAgainFragment();

            // We store these pending attachment strings temporarily in the search fragment
            // They are passed back to the activity when a recipient is selected
            String pendingDataFile = getPendingAttachmentFile();

            if(!TextUtils.isEmpty(pendingDataFile)) {
                mSearchFragment.setArguments(Extra.DATA.to(new Bundle(), pendingDataFile));
            } else {
                String pendingDataText = getPendingAttachmentText();

                if (!TextUtils.isEmpty(pendingDataText)) {
                    mSearchFragment.setArguments(Extra.TEXT.to(new Bundle(), pendingDataText));
                }
            }

            transaction.add(R.id.chat, mSearchFragment,
                    SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);
        }
        else {
            transaction.show(mSearchFragment);
        }
        transaction.commit();
        mSearchView.post(mOpenActionBarRunnable);

        mInSearchView = true;
        mComposeLayout.setVisibility(View.GONE);
        hideSoftKeyboard(R.id.compose_text);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        invalidateActionBar(mInSearchView);
    }

    protected void exitSearchUI(boolean transitioning) {
        FragmentManager manager = getFragmentManager();

        SearchAgainFragment fragment =
                (SearchAgainFragment) manager.findFragmentByTag(
                        SearchAgainFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);

        if (fragment != null) {
            final FragmentTransaction transaction = manager.beginTransaction();
            transaction.setCustomAnimations(android.R.animator.fade_out, 0);
            transaction.remove(fragment);
            transaction.commit();
        }

        mInSearchView = false;
        if (transitioning) {
            mComposeLayout.setVisibility(View.VISIBLE);
        }
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(false);

            SearchEditTextLayout seTextLayout = (SearchEditTextLayout) actionBar.getCustomView();
            if (seTextLayout.isExpanded()) {
                seTextLayout.collapse(true /* animate */);
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

    // Get a possible pending attachment file to send
    private String getPendingAttachmentFile() {
        Intent intent = getIntent();

        if(Intent.ACTION_SEND.equals(intent.getAction())) {
            // We have some type of send intent for a file
            Uri fileUri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);

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
            if (intent.getType().equals("text/plain")) {
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

    protected void hideSoftKeyboard(int... viewResourceIDs) {
        for (int id : viewResourceIDs) {
            View view = findViewById(id);
            if (view != null) {
                DialerUtils.hideInputMethod(view);
                view.clearFocus();
            }
        }
    }

    private void scrollToBottom() {
        ChatFragment fragment = getChatFragment(false);
        if (fragment != null) {
            fragment.scrollToBottom();
        }
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
                AsyncUtils.execute(new RefreshTask(ConversationUtils.getConversations(getApplicationContext()), getConversation(), forceRefresh), getPartner());
            }
        });

        return true;
    }

    protected boolean lockViewIfNecessary() {
        MessagingPreferences preferences = MessagingPreferences.getInstance(this);
        byte[] chatKeyData =
                KeyManagerSupport.getPrivateKeyData(getContentResolver(),
                        ConfigurationUtilities.getChatProtectionKey());

        boolean lockingEnabled = chatKeyData != null && !mInSearchView;
        boolean isViewLocked = lockingEnabled && preferences.isMessagingUnlockTimeExpired();

        mPasswordOverlay.setVisibility(isViewLocked ? View.VISIBLE : View.GONE);
        if (!mInSearchView) {
            invalidateActionBar(isViewLocked);
        }
        invalidateOptionsMenu();

        if (isViewLocked) {
            mTitle.setText(R.string.messaging_screen_locked);
            mPasswordOverlay.focus();
            if (mActionMode != null) {
                mActionMode.finish();
            }
        }
        else {
            if (!mInSearchView) {
                restoreActionBar();
            }
        }

        /*
         * If periodic locks are necessary (while on the chat view, without leaving)
         *
        if (!isViewLocked) {
            mHandler.postDelayed(mLockViewRunnable, preferences.getMessagingLockPeriod());
        }
         */

        return isViewLocked;
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
        bindService(new Intent(ConversationActivity.this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE);
    }

    private void doUnbindService() {
        if (mPhoneIsBound) {
            mPhoneService.removeStateChangeListener(this);
            // Detach our existing connection.
            unbindService(phoneConnection);
            mPhoneIsBound = false;
        }
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

    class RefreshTask extends AsyncTask<String, Void, List<Event>> {

        final Conversation mConversation;
        final ConversationRepository mRepository;
        final boolean mForceRefresh;

        private int mUnreadMessageCount = 0;

        public RefreshTask(ConversationRepository repository, Conversation conversation, boolean forceRefresh) {
            mRepository = repository;
            mConversation = conversation;
            mForceRefresh = forceRefresh;
        }

        @Override
        protected List<Event> doInBackground(String... args) {

            String partner = args[0];

            if (partner == null) {
                partner = getPartner();
            }

            if (mConversation == null || mRepository == null) {
                // cannot proceed with refresh here
                return new ArrayList<>();
            }
            ConversationActivity.this.mConversationPartnerId = partner;

            EventRepository history = mRepository.historyOf(mConversation);
            List<Event> events = history.list();
            events = removeExpiredMessages(history, events);

            mUnreadMessageCount = 0;
            long autoRefreshTime = Long.MAX_VALUE;
            for (Event event : events) {
                if (event instanceof Message) {
                    Message message = (Message) event;
                    boolean save = false;
                    if (event instanceof IncomingMessage) {
                        save = handleMessageByState(message);

                        if (message.hasAttachment() && !message.hasMetaData()) {
                            startService(SCloudService.getDownloadThumbnailIntent(event, partner,
                                    ConversationActivity.this));
                        }
                    }
                    long currentMillis = System.currentTimeMillis();
                    /*
                     * calculate next timeslot for refresh,
                     * do not refresh sooner than after one second
                     */
                    autoRefreshTime =
                            Math.max(
                                    Math.min(autoRefreshTime, message.getExpirationTime()),
                                    currentMillis + TimeUnit.SECONDS.toMillis(1));

                    if (save)
                        history.save(message);
                }
            }
            if (autoRefreshTime < Long.MAX_VALUE) {
                scheduleAutoRefresh(autoRefreshTime);
            }

            // attempt to correct unread message count for conversation
            checkUnreadMessageCount(partner);

            return MessageUtils.filter(events, DialerActivity.mShowErrors);
        }

        @Override
        protected void onPostExecute(List<Event> events) {

            try {
                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "RefreshTask.onPostExecute: force refresh " + mForceRefresh
                            + ", rerun required: " + mRerunRefresh
                            + ", rerun forced: " + mRerunForcedRefresh);
                }

                if (mForceRefresh && !(mRerunRefresh | mRerunForcedRefresh)) {
                    if (ConfigurationUtilities.mTrace)
                        Log.d(TAG, "RefreshTask forces view update");
                    updateViews(events);
                }

                findViewById(R.id.compose_send).setOnClickListener(createSendMessageOnClickListener(mConversation));
                mRefreshing = false;

                if (mRerunRefresh || mRerunForcedRefresh) {
                    refresh(mForceRefresh | mRerunForcedRefresh);
                }
            } catch (Exception e) {
                Log.e("ConversationActivity", "RefreshTask.onPostExecute try to catch null pointer : " + e.getMessage());
            }
        }

        private boolean handleMessageByState(Message message) {
            boolean save = false;
            int state = message.getState();
            if (MessageStates.RECEIVED == state) {
                mUnreadMessageCount += 1;
            } else if (MessageStates.READ == state
                    && message.hasFailureFlagSet(Message.FAILURE_READ_NOTIFICATION)) {
                Log.d(TAG, "Message has a failed read notification flag, resending");
                save = true;
                AxoMessaging.getInstance(getBaseContext()).sendReadNotification(message);
            } else if (MessageStates.BURNED == state
                    && message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
                Log.d(TAG, "Message has a failed burn notification flag, resending");
                save = true;
                String conversationId = MessageUtils.getConversationId(message);
                AxoMessaging.getInstance(getBaseContext()).sendBurnNoticeRequest(
                        message, conversationId);
            }
            return save;
        }

        private List<Event> removeExpiredMessages(EventRepository history, List<Event> events) {
            List<Event> newList = new ArrayList<>();
            for (Event event : events) {
                if (event instanceof Message) {
                    Message message = (Message) event;

                    if (MessageStates.BURNED == message.getState()
                            && message.hasFailureFlagSet(Message.FAILURE_BURN_NOTIFICATION)) {
                        String conversationId = MessageUtils.getConversationId(message);
                        AxoMessaging.getInstance(getBaseContext()).sendBurnNoticeRequest(
                                message, conversationId);
                        history.save(message);
                        continue;
                    }

                    if (message.isExpired()) {
                        if (ConfigurationUtilities.mTrace)
                            Log.d(TAG, "#list removing expired id: " + message.getId());

                        history.remove(message);
                        MessageUtils.startAttachmentCleanUp(getApplicationContext(), getPartner(), message);
                        continue;
                    }
                }
                newList.add(event);
            }
            return newList;
        }

        private void checkUnreadMessageCount(@NonNull final String partner) {
            // re-read conversation to avoid using possibly stale version
            Conversation conversation = mRepository.findById(partner);
            if (conversation != null) {
                int unreadMessageCount = conversation.getUnreadMessageCount();
                if (unreadMessageCount != mUnreadMessageCount) {
                    Log.w(TAG, "Discrepancy between actual and reported unread message count for conversation: "
                            + mUnreadMessageCount + "/" + unreadMessageCount);
                    conversation.setUnreadMessageCount(mUnreadMessageCount);
                    mRepository.save(conversation);
                }
            }
        }
    }

}
