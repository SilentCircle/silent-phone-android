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

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.calllognew.IntentProvider;
import com.silentcircle.contacts.utils.ClipboardUtils;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.messaging.fragments.ChatFragment;
import com.silentcircle.messaging.fragments.SearchFragment;
import com.silentcircle.messaging.listener.ClickSendOnEditorSendAction;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
import com.silentcircle.messaging.listener.OnConfirmListener;
import com.silentcircle.messaging.listener.SendMessageOnClick;
import com.silentcircle.messaging.location.LocationUtils;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
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
import com.silentcircle.messaging.util.BurnDelay;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.messaging.views.ConversationOptionsDrawer;
import com.silentcircle.messaging.views.UploadView;
import com.silentcircle.silentphone2.Manifest;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ConversationActivity extends ActionBarActivity implements ChatFragment.Callback {

    @SuppressWarnings("unused")
    private static final String TAG = ConversationActivity.class.getSimpleName();

    /* Priority for this view to handle message broadcasts. */
    private static final int MESSAGE_PRIORITY = 2;

    /* Limit single message, non-attachment-ified text to 1000 symbols */
    private static final int MESSAGE_TEXT_LENGTH_LIMIT = 1000;

    /* Limit single message input text to 5000 symbols */
    private static final int MESSAGE_TEXT_INPUT_LENGTH_LIMIT = 5000;

    protected Conversation mConversation;
    protected String mConversationPartner;

    protected BroadcastReceiver mViewUpdater;
    /* new message sound notification player */
    protected MediaPlayer mPlayer;

    /* variables for attachment handling */
    private Uri mPendingImageCaptureUri;
    private Uri mPendingVideoCaptureUri;
    private Uri mPendingAudioCaptureUri;
    private static final int R_id_share = 0xFFFF & R.id.share;

    /** Several modules use this format to define a common UTC date */
    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private Toolbar mToolbar;
    private QuickContactBadge mAvatarView;
    private TextView mTitle;
    private DrawerLayout mDrawerLayout;
    private MenuItem mOptionsMenuItem;
    private SearchFragment mSearchFragment;

    private boolean mInSearchView;
    private String mTextToForward;
    private Message mMessageToForward;

    /* Progress bar for generic operations */
    private UploadView mProgressBar;
    private ViewGroup mComposeLayout;
    private EditText mComposeText;

    /* State variables for refreshing timed messages, etc */
    private Timer mTimer;
    private boolean mRefreshing;
    private boolean mIsVisible;

    protected Bundle mSavedInstanceState;

    public static final String [] SUPPORTED_IMTO_HOSTS = {
//            "jabber",
            "silentcircle",
//            "silent text",
//            "silenttext",
//            "silentcircle.com",
//            "com.silentcircle",
//            "silent circle"
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
                    ViewUtil.animateImageChange(ConversationActivity.this, buttonSend, newImageResource);
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
                selectFile();
            }
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSavedInstanceState = savedInstanceState;

        setContentView(R.layout.activity_conversation_with_drawer);

        ContactsCache.buildContactsCache(this);

        initConversationView();

        // TODO: Is this worth going here?
        //runAttachmentManager();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (mInSearchView) {
            exitSearchUI();
        }

        initConversationView();

        // check if we need to forward a message and do it here
        if (mMessageToForward != null) {
            MessageUtils.forwardMessage(this, getUsername(), getPartner(), mMessageToForward);
            mMessageToForward = null;
        } else if (!TextUtils.isEmpty(mTextToForward)) {
            mComposeText.setText(mTextToForward);
            mTextToForward = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsVisible = true;
        registerViewUpdater();
        updateViews();
        Notifications.cancelMessageNotification(this, getPartner());

        if (mConversationPartner == null) {
            mConversationPartner = getPartner();
        }
        if (MessagingPreferences.getInstance(this).getMessageSoundsEnabled()) {
            mPlayer = MediaPlayer.create(getApplicationContext(), R.raw.received);
        }
        if (getConversation() != null) {
            refresh();

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
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        saveUnsentText();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        Conversation conversation = getConversation();

        MenuItem shareLocation = menu.findItem(R.id.action_share_location);
        boolean locationSharingEnabled = LocationUtils.isLocationSharingAvailable(this)
                && (conversation != null && conversation.isLocationEnabled())
                && !mInSearchView;
        shareLocation.setIcon(locationSharingEnabled
                ? R.drawable.ic_location_selected : R.drawable.ic_location_unselected);
        shareLocation.setVisible(locationSharingEnabled);

        MenuItem burnNotice = menu.findItem(R.id.action_burn_notice);
        boolean burnNoticeEnabled = (conversation != null && conversation.hasBurnNotice())
                && !mInSearchView;
        burnNotice.setIcon(burnNoticeEnabled
                ? R.drawable.ic_burn_enabled : R.drawable.ic_burn_disabled);
        burnNotice.setVisible(burnNoticeEnabled);

        MenuItem silentPhoneCall = menu.findItem(R.id.action_silent_phone_call);
        silentPhoneCall.setVisible(!mInSearchView);
        // TODO: check validity of partner's name before showing this item

        // keep reference to this item
        mOptionsMenuItem = menu.findItem(R.id.options);
        mOptionsMenuItem.setVisible(!mInSearchView);
        if (mDrawerLayout != null) {
            mOptionsMenuItem.setIcon(mDrawerLayout.isDrawerOpen(Gravity.END)
                    ? R.drawable.ic_drawer_open : R.drawable.ic_drawer_closed);
        }

        return super.onCreateOptionsMenu(menu);
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
            exitSearchUI();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    protected void initConversationView() {
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

        if (TextUtils.isEmpty(partner)) {
            enterSearchUI();
        } else {
            getChatFragment();
        }
    }

    protected void setLocationSharing(boolean enabled) {
        Conversation conversation = getConversation();
        conversation.setLocationEnabled(enabled);
        getConversations().save(conversation);

        invalidateOptionsMenu();
        updateConversation();
    }

    protected void setSendReadReceipts(boolean enabled) {
        Conversation conversation = getConversation();
        conversation.setSendReadReceipts(enabled);
        getConversations().save(conversation);

        updateConversation();
    }

    protected void setBurnDelay(long burnDelay) {
        Conversation conversation = getConversation();
        conversation.setBurnDelay(burnDelay);
        getConversations().save(conversation);
    }

    protected void setBurnNotice(boolean enabled) {
        Conversation conversation = getConversation();
        conversation.setBurnNotice(enabled);
        if (enabled && conversation.getBurnDelay() <= 0) {
            conversation.setBurnDelay(BurnDelay.getDefaultDelay());
        }
        getConversations().save(conversation);

        invalidateOptionsMenu();
        updateConversation();
    }

    protected void confirmClearConversation() {
        OnConfirmListener onConfirm = new OnConfirmListener() {
            @Override
            public void onConfirm(Context context) {
                clearConversation();
            }
        };
        new LaunchConfirmDialogOnClick(R.string.are_you_sure,
                R.string.cannot_be_undone,
                onConfirm).show(ConversationActivity.this);
    }

    protected void clearConversation() {
        Conversation conversation = getConversation();

        for(Event event : getConversations().historyOf(conversation).list()) {
            deleteEvent(event);
        }

        getConversations().historyOf(conversation).clear();

        updateConversation();
    }

    protected void confirmSaveConversation() {
        OnConfirmListener onConfirm = new OnConfirmListener() {
            @Override
            public void onConfirm(Context context) {
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
        Conversation conversation = getConversation();
        if (conversation != null) {
            conversation.setUnsentText(unsentText);
            getConversations().save(conversation);
        }
    }

    protected void showMessagingFailure(int errorCode, String errorInfo, Message message) {
        if (!isFinishing()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle(R.string.dialog_title_failed_to_send_message);
            alert.setMessage(getString(R.string.dialog_message_failed_to_send_to,
                    message.getConversationID(),
                    getString(MessageErrorCodes.messageErrorToStringId(errorCode))));

            alert.setCancelable(false);
            alert.setPositiveButton(R.string.dialog_button_ok, null);

            alert.show();
        }
    }

    protected void restoreActionBar() {

        mToolbar = (Toolbar) findViewById(R.id.conversation_toolbar);
        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(getPartner());

        mTitle = (TextView) mToolbar.findViewById(R.id.title);
        mTitle.setText(
                contactEntry != null ? contactEntry.name : getPartner());

        mAvatarView = (QuickContactBadge) mToolbar.findViewById(R.id.message_avatar);
        mAvatarView.setVisibility(View.VISIBLE);
        setPhoto(mAvatarView,
                contactEntry != null ? contactEntry.photoId : 0,
                contactEntry != null ? contactEntry.lookupUri : null,
                contactEntry != null ? contactEntry.name : null,
                null,
                ContactPhotoManagerNew.TYPE_DEFAULT);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    protected void invalidateActionBar() {
        mAvatarView.setVisibility(mInSearchView ? View.GONE : View.VISIBLE);
    }

    protected ConversationOptionsDrawer getOptionsDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        mDrawerLayout.setDrawerListener(mDrawerListener);
        return (ConversationOptionsDrawer) findViewById(R.id.drawer_content);
    }

    /**
     * Sends broadcast event with action UPDATE_CONVERSATION, forcing conversation update in views.
     */
    private void updateConversation() {
        Intent intent = Action.UPDATE_CONVERSATION.intent();
        Extra.PARTNER.to(intent, getPartner());
        sendOrderedBroadcast(intent, Manifest.permission.READ);

        // TODO: this will get called also when broadcast above is received.
        updateViews();
    }

    private ChatFragment getChatFragment() {

        if (isFinishing()) {
            return null;
        }

        if (mInSearchView) {
            exitSearchUI();
        }

        if(mSavedInstanceState != null) {
            return null;
        }

        FragmentManager manager = getFragmentManager();
        final FragmentTransaction transaction = manager.beginTransaction();

        ChatFragment fragment =
                (ChatFragment) manager.findFragmentByTag(ChatFragment.TAG_CONVERSATION_CHAT_FRAGMENT);

        if (fragment == null) {
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

    private Conversation getConversation() {
        mConversation = getConversation(getPartner());
        return mConversation;
    }

    private Conversation getConversation(String conversationPartner) {
        ConversationRepository conversations =
                AxoMessaging.getInstance(getApplicationContext()).getConversations();
        Conversation conversation = AxoMessaging.getInstance(getApplicationContext())
                .getOrCreateConversation(conversationPartner);

        if (conversation != null) {
            if (conversation.containsUnreadMessages()) {
                conversation.setUnreadMessageCount(0);
                conversations.save(conversation);
            }

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

    protected String getPartner() {
        Intent intent = getIntent();
        if (intent != null) {
            if( Intent.ACTION_SENDTO.equals( intent.getAction() ) ) {
                Uri uri = intent.getData();
                if (uri != null) {
                    if (Constants.SCHEME_IMTO.equals(uri.getScheme()) && Utilities.isAnyOf(uri.getHost(), SUPPORTED_IMTO_HOSTS)) {
                        mConversationPartner = uri.getLastPathSegment();
                    }
                }
            }
            else {
                mConversationPartner = Extra.PARTNER.from(intent);
            }
            /*
             * TODO: handle case when conversation activity does not have conversation
             * partner in its intent
             */
        }
        if (mConversationPartner == null && mConversation != null) {
            mConversationPartner = mConversation.getPartner().getUsername();
        }

        if (mConversationPartner != null) {
            mConversationPartner = mConversationPartner.trim();
            mConversationPartner = Utilities.removeSipParts(mConversationPartner);
        }
        Log.d(TAG, "Conversation partner: " + mConversationPartner);

        return mConversationPartner;
    }

    protected SendMessageOnClick createSendMessageOnClickListener(Conversation conversation) {

        TextView composeText = (TextView) findViewById(R.id.compose_text);

        return new SendMessageOnClick(composeText, getUsername(), conversation, getConversations(),
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
                    hideSoftKeyboard(R.id.compose_text);
                    selectFile();
                } else if(text.length() > MESSAGE_TEXT_LENGTH_LIMIT) {
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
                updateViews();
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

    protected void updateViews() {
        if (!isFinishing() && !mInSearchView) {
            ChatFragment fragment = getChatFragment();

            if(fragment != null) {
                fragment.setEvents(getConversationHistory());
            }
            createOptionsDrawer();
        }
    }

    protected String getUsername() {
        return AxoMessaging.getInstance(this).getUserName();
    }

    public ConversationRepository getConversations() {
        return AxoMessaging.getInstance(getApplicationContext()).getConversations();
    }

    protected boolean shouldRequestDeliveryNotification() {
        // In Silent Text application this always returns true
        // TODO: determine whether delivery notification should be returned
        // otherwise remove this function and parameter to SendMessageOnClick
        return true;
    }

    protected boolean shouldSendDeliveryNotification(Message message) {
        //if (!OptionsDrawer.isSendReceiptsEnabled(this)) {
        //    return false;
        // }
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
        chooser.ignore(new String[] { "com.android.soundrecorder", "com.cyanogenmod.filemanager", "com.borqs.blackphone.music" });
        startActivityForResult(chooser.build(), R_id_share);
    }

    private Intent createCaptureImageIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri uri = PictureProvider.CONTENT_URI;
        mPendingImageCaptureUri = uri;
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
                    }
                    startActivity(fileViewerIntent);
                } catch (JSONException exception) {
                    Log.e(TAG, "Attachment JSON exception (rare)", exception);
                }
            }
        }
    }

    public JSONObject getAttachment(String messageId) {
        ConversationRepository conversations = AxoMessaging.getInstance(this).getConversations();

        Conversation conversation = conversations.findByPartner(getPartner());
        EventRepository events = conversations.historyOf(conversation);
        Message message = (Message) events.findById(messageId);

        try {
            return new JSONObject(message.getAttachment());
        } catch (JSONException exception) {
            Log.e(TAG, "Message attachment JSON error (rare)", exception);
        }

        return null;
    }

    private List<Event> getConversationHistory() {
        Conversation conversation = getConversation();
        List<Event> items;
        if (conversation != null) {
            items = filter(getConversations().historyOf(conversation).list());
            Collections.reverse(items);
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
        EventRepository events = getConversations().historyOf(getConversation());

        message.setState(MessageStates.COMPOSING);
        message.setAttachment("");
        events.save(message);

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
                        if (!forCurrentConversation && mPlayer != null && !mPlayer.isPlaying()) {
                            mPlayer.start();
                        }
                    case UPDATE_CONVERSATION:
                        /* update view with new message information */
                        if (forCurrentConversation) {
                            boolean forceRefresh = intent.getBooleanExtra("FORCE_REFRESH", false);
                            if (mIsVisible && forceRefresh)  // if not visible the next onResume will refresh()
                                refresh();
                            else
                                updateViews();
                        }
                        if (isOrderedBroadcast()) {
                            abortBroadcast();
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

    private Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int priority) {
        filter.setPriority(priority);
        return registerReceiver(receiver, filter);
    }

    @Override
    public void onActionModeCreated() {
        findViewById(R.id.compose).setVisibility(View.GONE);
        hideSoftKeyboard(R.id.compose_text);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void onActionModeDestroyed() {
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
    public void performAction(int actionID, List targets) {
        for (Object target : targets) {
            if (target instanceof Event) {
                performAction(actionID, (Event) target);
            }
        }
    }

    protected void performAction(int actionID, final Event event) {
        switch (actionID) {
            case R.id.burn:
                if (event instanceof Message && MessageUtils.isBurnable((Message) event)) {
                    MessageUtils.burnMessage(this, (Message) event);
                }
                else {
                    deleteEvent(event);
                }
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
                Log.d(TAG, "Message forwarding not implemented");
                forwardMessage(event);
                break;
            default:
                // Unknown or unhandled action.
                break;
        }
    }

    protected void deleteEvent(final Event event) {
        getConversations().historyOf(getConversation()).remove(event);

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
                EventRepository events = getConversations().historyOf(getConversation());
                events.remove(message);
                message.setId(UUIDGen.makeType1UUID().toString());
                message.setState(MessageStates.COMPOSED);
                events.save(event);
                SendMessageTask task = new SendMessageTask(getApplicationContext());
                AsyncUtils.execute(task, message);
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

    protected void enterSearchUI() {
        FragmentManager manager = getFragmentManager();
        final FragmentTransaction transaction = manager.beginTransaction();
        mSearchFragment =
                (SearchFragment) manager.findFragmentByTag(
                        SearchFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);
        transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        if (mSearchFragment == null) {
            mSearchFragment = new SearchFragment();
            transaction.add(R.id.chat, mSearchFragment,
                    SearchFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);
        }
        else {
            transaction.show(mSearchFragment);
        }
        transaction.commit();

        mInSearchView = true;
        mComposeLayout.setVisibility(View.GONE);
        hideSoftKeyboard(R.id.compose_text);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mTitle.setText(R.string.messaging_conversation_select_conversation_partner);
        invalidateOptionsMenu();
        invalidateActionBar();
    }

    protected void exitSearchUI() {
        FragmentManager manager = getFragmentManager();

        SearchFragment fragment =
                (SearchFragment) manager.findFragmentByTag(
                        SearchFragment.TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT);

        if (fragment != null) {
            final FragmentTransaction transaction = manager.beginTransaction();
            transaction.setCustomAnimations(android.R.animator.fade_out, 0);
            transaction.remove(fragment);
            transaction.commit();
        }

        mInSearchView = false;
        mComposeLayout.setVisibility(View.VISIBLE);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        invalidateOptionsMenu();
        restoreActionBar();
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
        ChatFragment fragment = getChatFragment();
        if (fragment != null) {
            fragment.scrollToBottom();
        }
    }

    private void setPhoto(QuickContactBadge view, long photoId, Uri contactUri,
                          String displayName, String identifier, int contactType) {
        ContactPhotoManagerNew contactPhotoManager = ContactPhotoManagerNew.getInstance(this);
        view.assignContactUri(contactUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            view.setOverlay(null);
        ContactPhotoManagerNew.DefaultImageRequest request =
                new ContactPhotoManagerNew.DefaultImageRequest(displayName, identifier,
                        contactType, true /* isCircular */);
        contactPhotoManager.loadThumbnail(view, photoId, false /* darkTheme */,
                true /* isCircular */, request);
    }


    private static List<Event> filter(List<Event> events) {
        Iterator<Event> iterator = events.iterator();
        Set<String> messageIds = new HashSet<>();
        List<ErrorEvent> errorEvents = new ArrayList<>();
        while (iterator.hasNext()) {
            Event event = iterator.next();
            if (event instanceof Message) {
                Message message = (Message) event;
                if (message instanceof IncomingMessage) {
                    messageIds.add(message.getId());
                }
                switch (message.getState()) {
                    case MessageStates.BURNED:
                        iterator.remove();
                        break;
                    case MessageStates.COMPOSED:
                        // TODO: remove if 'isOutgoingResendRequest'
                        break;
                    default:
                        break;
                }
                // TODO: remove incoming messages which are not chat messages
                // or not messages with attachment
            }
            else if (event instanceof ErrorEvent) {
                // to avoid showing errors for existing incoming/outgoing messages
                // keep track of error events.
                ErrorEvent errorEvent = (ErrorEvent) event;
                String messageId = errorEvent.getMessageId();
                if (!TextUtils.isEmpty(messageId)) {
                    errorEvents.add(errorEvent);
                }
            }
        }

        // clear those error events for which a message has been received
        for (ErrorEvent errorEvent : errorEvents) {
            String messageId = errorEvent.getMessageId();
            if (messageId != null && messageIds.contains(messageId)) {
                events.remove(errorEvent);
            }
        }

        Collections.sort(events);
        Collections.reverse(events);

        /*
         * at this point messages are sorted by the time set when arrived on local device
         * sort incoming messages in order they have been created on sender's device
         */
        List<Event> result = new ArrayList<>();
        List<Event> incomingEventsGroup = new ArrayList<>();
        for (Event event : events) {
            if (event instanceof IncomingMessage) {
                incomingEventsGroup.add(event);
            }
            else {
                Collections.sort(incomingEventsGroup, Event.EVENT_ID_COMPARATOR);
                result.addAll(incomingEventsGroup);
                incomingEventsGroup.clear();

                result.add(event);
            }
        }
        Collections.sort(incomingEventsGroup, Event.EVENT_ID_COMPARATOR);
        result.addAll(incomingEventsGroup);

        return result;
    }

    protected void refresh() {

        if( mRefreshing ) {
            return;
        }
        mRefreshing = true;
        runOnUiThread( new Runnable() {

            @Override
            public void run() {
                AsyncUtils.execute(new RefreshTask(), getPartner());
            }
        } );

    }

    protected void cancelAutoRefresh() {
        if( mTimer != null ) {
            mTimer.cancel();
            mTimer = null;
            mRefreshing = false;
        }
    }

    protected void scheduleAutoRefresh(long autoRefreshTime) {
        cancelAutoRefresh();
        mTimer = new Timer( "conversation:auto-refresh" );
        mTimer.schedule(new AutoRefresh(), new Date(autoRefreshTime));
    }

    private class AutoRefresh extends TimerTask {

        public AutoRefresh() { }

        @Override
        public void run() {
            refresh();
        }
    }

    class RefreshTask extends AsyncTask<String, Void, List<Event>> {

        @Override
        protected List<Event> doInBackground( String... args ) {

            String partner = args[0];

            if( partner == null ) {
                partner = getPartner();
            }

            mConversation = getConversation(partner);
            if (mConversation == null) {
                // cannot proceed with refresh here
                return new ArrayList<>();
            }
            ConversationActivity.this.mConversationPartner = partner;

            EventRepository history = getConversations().historyOf(mConversation);
            List<Event> events = history.list();
            events = removeExpiredMessages(history, events);

            long autoRefreshTime = Long.MAX_VALUE;
            for (Event event : events ) {
                if( event instanceof Message ) {
                    Message message = (Message) event;
                    boolean save = false;
                    if( event instanceof IncomingMessage) {
                        if( MessageStates.RECEIVED == message.getState() ) {
                            message.setState(MessageStates.READ);
                            save = true;
                            if (shouldSendDeliveryNotification(message)) {
                                AxoMessaging.getInstance(getBaseContext()).sendDeliveryNotification(message);
                            }
                        }

                        if(message.hasAttachment() && !message.hasMetaData()) {
                            startService(SCloudService.getDownloadThumbnailIntent(event, partner, ConversationActivity.this));
                        }
                    }

                    long currentMillis = System.currentTimeMillis();
                    if (MessageStates.READ == message.getState()
                            && message.expires()
                            && message.getExpirationTime() == Message.DEFAULT_EXPIRATION_TIME) {
                        save = true;
                        message.setExpirationTime(currentMillis + message.getBurnNotice() * 1000L);
                    }
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
            if( autoRefreshTime < Long.MAX_VALUE ) {
                scheduleAutoRefresh(autoRefreshTime);
            }
            if( mConversation.getUnreadMessageCount() > 0 ) {
                mConversation.setUnreadMessageCount( 0 );
                getConversations().save(mConversation);
            }
//            empty = events.isEmpty();
            filter( events );
            return events;
        }

        @Override
        protected void onPostExecute( List<Event> events ) {

            try {
//                updateDisplayName();
//                setText( R.id.device_name, conversation.getPartner().getDevice() );

                updateViews(/* events */);

                findViewById(R.id.compose_send).setOnClickListener(createSendMessageOnClickListener(mConversation) );

//                updateActionBar();
//                updateOptionsDrawer();
//                invalidateSupportOptionsMenu();
                mRefreshing = false;
            } catch( Exception e ) {
                Log.e( "ConversationActivity", "RefreshTask.onPostExecute try to catch null pointer : " + e.getMessage() );
            }
        }
        private List<Event> removeExpiredMessages(EventRepository history, List<Event> events) {
            List<Event> newList = new ArrayList<>();
            for (Event event : events) {
                if (event instanceof Message) {
                    Message message = (Message) event;
                    if (message.isExpired()) {
                        if (ConfigurationUtilities.mTrace) Log.d(TAG, "#list removing expired id: " + message.getId());
                        history.remove(message);

                        if(((Message) event).hasAttachment()) {
                            Intent cleanupIntent = Action.PURGE_ATTACHMENTS.intent(ConversationActivity.this, SCloudCleanupService.class);
                            cleanupIntent.putExtra("KEEP_STATUS", false);
                            Extra.PARTNER.to(cleanupIntent, getPartner());
                            Extra.ID.to(cleanupIntent, event.getId());
                            startService(cleanupIntent);
                        }
                        continue;
                    }
                }
                newList.add(event);
            }
            return newList;
        }
    }

}
