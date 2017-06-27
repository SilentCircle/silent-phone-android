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
package com.silentcircle.messaging.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.silentcircle.common.util.CallUtils;
import com.silentcircle.common.util.DRUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.messaging.model.CallData;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.RetentionInfo;
import com.silentcircle.messaging.model.event.CallMessage;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.EventDeviceInfo;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.BaseMessageEventView;
import com.silentcircle.messaging.views.EventInfoField;
import com.silentcircle.messaging.views.adapters.ModelViewAdapter;
import com.silentcircle.messaging.views.adapters.ViewType;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import zina.ZinaNative;

import static com.silentcircle.messaging.model.MessageStates.messageStateToStringId;
import static com.silentcircle.messaging.model.event.Message.DEFAULT_EXPIRATION_TIME;

/**
 * Fragment to show detailed/debug information about a message.
 */
public class EventInfoFragment extends Fragment {

    public static final String TAG_EVENT_INFO_FRAGMENT = "com.silentcircle.messaging.fragments.EventInfo";

    protected static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS", Locale.getDefault());

    private Activity mParent;

    private CharSequence mConversationId;
    private CharSequence mMessageId;

    private Event mEvent;
    private View mEventView;

    private android.support.v7.widget.CardView mMessageContainer;
    private ViewPager mViewPager;

    private static final ViewType[] VIEW_TYPES = ChatFragment.VIEW_TYPES;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            setEvent(Extra.PARTNER.getCharSequence(args), Extra.ID.getCharSequence(args));
        }
        if (savedInstanceState != null) {
            setEvent(savedInstanceState.getCharSequence(Extra.PARTNER.getName()),
                    savedInstanceState.getCharSequence(Extra.ID.getName()));
        }
    }

    public void setEvent(final CharSequence conversationId, final CharSequence messageId) {
        setEvent(conversationId, messageId,
                MessageUtils.getEventById(conversationId.toString(), messageId.toString()));
    }

    public void setEvent(final CharSequence conversationId, final CharSequence messageId,
            final Event event) {
        mConversationId = conversationId;
        mMessageId = messageId;
        mEvent = event;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        commonOnAttach(getActivity());
    }

    /*
     * Deprecated on API 23
     * Use onAttachToContext instead
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            commonOnAttach(activity);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_event_info, container, false);
        if (rootView == null) {
            return null;
        }

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        List<Event> events = new ArrayList<>();
        events.add(mEvent);

        mMessageContainer = (android.support.v7.widget.CardView) view.findViewById(R.id.event_container);
        ModelViewAdapter adapter = new ModelViewAdapter(events, VIEW_TYPES);
        mEventView = adapter.getView(0, null, mMessageContainer);

        adjustMaxHeightIfNecessary();

        mViewPager = (ViewPager) view.findViewById(R.id.pager);
        mViewPager.setAdapter(new EventInfoPagerAdapter(mParent, mEvent));
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mEventView instanceof BaseMessageEventView) {
            ((BaseMessageEventView) mEventView).update();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(Extra.PARTNER.getName(), mConversationId);
        outState.putCharSequence(Extra.ID.getName(), mMessageId);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mEventView instanceof BaseMessageEventView) {
            ((BaseMessageEventView) mEventView).cancelUpdates();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
    }

    private void commonOnAttach(Activity activity) {
        mParent = activity;
    }

    private void adjustMaxHeightIfNecessary() {
        View viewToAdd = mEventView;

        int maximumHeightPercentage = 30;
        Point point = new Point();
        mEventView.measure(0, 0);
        int height = mEventView.getMeasuredHeight();
        ViewUtil.getScreenDimensions(mParent, point);
        int maximumHeight = (maximumHeightPercentage * point.y * 2 - point.y) / 200;
        if (height > maximumHeight) {
            ViewGroup.LayoutParams params = mMessageContainer.getLayoutParams();
            params.height = maximumHeight;
            mMessageContainer.requestLayout();
            ScrollView scrollView = new ScrollView(mParent);
            scrollView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            scrollView.addView(mEventView);
            viewToAdd = scrollView;
        }
        mMessageContainer.addView(viewToAdd);
    }

    private void updateInfoFields(@NonNull Context context, @NonNull ViewGroup container,
            @NonNull Event event) {
        container.removeAllViews();

        Resources resources = context.getResources();

        long now = System.currentTimeMillis();
        long composeTime = event.getComposeTime();
        container.addView(getInfoView(context, R.string.message_info_id, event.getId()));
        container.addView(getInfoView(context, R.string.message_info_message_composed_at,
                (composeTime == 0 ? "N/A" : DATE_FORMAT.format(new Date(composeTime)))));
        /*
        container.addView(getInfoView(context, "Time",
                DATE_FORMAT.format(new Date(event.getTime()))));
         */

        RetentionInfo retentionInfo = null;
        if (event instanceof IncomingMessage || event instanceof OutgoingMessage) {
            Message message = (Message) event;
            retentionInfo = message.getRetentionInfo();

            container.addView(getInfoView(context, R.string.message_info_text,
                    event.getText()));
            container.addView(getInfoView(context, R.string.message_info_sender,
                    message.getSender()));
            if (message.getReadTime() > 0) {
                container.addView(getInfoView(context,
                        R.string.message_info_message_read_at,
                        DATE_FORMAT.format(new Date(message.getReadTime()))));
            }
            if (message.getDeliveryTime() > 0) {
                container.addView(getInfoView(context,
                        R.string.message_info_message_delivered_at,
                        DATE_FORMAT.format(new Date(message.getDeliveryTime()))));
            }

            container.addView(getInfoView(context, R.string.message_info_burn_delay,
                    com.silentcircle.messaging.util.DateUtils.getTimeString(context,
                            TimeUnit.SECONDS.toMillis(message.getBurnNotice()))));

            if (DEFAULT_EXPIRATION_TIME != message.getExpirationTime()) {
                long millisecondsToExpiry = message.getExpirationTime() - now;
                container.addView(getInfoView(context,
                        R.string.message_info_message_expires_in,
                        com.silentcircle.messaging.util.DateUtils.getTimeString(context, millisecondsToExpiry)
                    + " (" + DATE_FORMAT.format(new Date(message.getExpirationTime())) + ")"));
            }

            container.addView(getInfoView(context,
                    R.string.message_info_has_attachments,
                    context.getString(message.hasAttachment()
                            ? R.string.dialog_button_yes : R.string.dialog_button_no)));

            /* retention not supported
            container.addView(getInfoView(context,
                    R.string.message_info_is_retained, context.getString(message.isRetained()
                            ? R.string.dialog_button_yes : R.string.dialog_button_no)));
             */

            // sent to devices
            EventDeviceInfo[] eventDeviceInfo = event.getEventDeviceInfo();
            StringBuilder sb = new StringBuilder();
            if (eventDeviceInfo != null) {
                int numDev = eventDeviceInfo.length;
                for (EventDeviceInfo devInfo : eventDeviceInfo) {
                    String readableState = context.getString(messageStateToStringId(devInfo.state));
                    sb.append(devInfo.deviceName).append(": ").append(readableState).append('\n');
                }
                container.addView(getInfoView(context,
                        resources.getString(R.string.message_info_message_sent_to) + " "
                                + resources.getQuantityString(R.plurals.n_devices, numDev, numDev), sb));
            }
        }

        if (event instanceof CallMessage) {
            CallMessage callMessage = (CallMessage) event;
            retentionInfo = callMessage.getRetentionInfo();
            int callTypeId = CallUtils.getTypeStringResId(callMessage.getCallType());
            String errorMessage = callMessage.getErrorMessage();
            container.addView(getInfoView(context,
                    R.string.message_info_call_type, context.getString(callTypeId)));
            container.addView(getInfoView(context,
                    R.string.message_info_call_duration,
                    android.text.format.DateUtils.formatElapsedTime(callMessage.getCallDuration())));
            container.addView(getInfoView(context,
                    R.string.message_info_call_time,
                    DATE_FORMAT.format(new Date(callMessage.getCallTime()))));
            if (!TextUtils.isEmpty(errorMessage)) {
                container.addView(getInfoView(context,
                        R.string.message_info_call_error,
                        CallData.translateSipErrorMsg(context, errorMessage)));
            }
        }

        if (retentionInfo != null) {
            if (retentionInfo.localRetentionData != null) {
                container.addView(getInfoView(context,
                        R.string.message_info_local_retention_info,
                        getLocalizedDataRetention(context, retentionInfo.localRetentionData)));
            }
            if (retentionInfo.remoteRetentionData != null) {
                for (RetentionInfo.DataRetention info : retentionInfo.remoteRetentionData) {
                    container.addView(getInfoView(context,
                            R.string.message_info_remote_retention_info,
                            getLocalizedDataRetention(context, info)));
                }
            }
        }

        if (event instanceof ErrorEvent) {
            ErrorEvent errorEvent = (ErrorEvent) event;
            if (errorEvent.getDeviceId() != null) {
                container.addView(getInfoView(context,
                        R.string.message_info_error_device_id, errorEvent.getDeviceId()));
            }
            if (errorEvent.getError() != MessageErrorCodes.SUCCESS) {
                int errorTextId = MessageErrorCodes.messageErrorToStringId(errorEvent.getError());
                container.addView(getInfoView(context,
                        R.string.message_info_error_text, context.getString(errorTextId)));
                container.addView(getInfoView(context,
                        R.string.message_info_error_number, errorEvent.getError()));
            } else {
                container.addView(getInfoView(context,
                        R.string.message_info_error_text, errorEvent.getText()));
            }
            if (errorEvent.getMessageComposeTime() != 0) {
                container.addView(getInfoView(context,
                        R.string.message_info_message_composed_at, (composeTime == 0
                                ? "N/A"
                                : DATE_FORMAT.format(new Date(errorEvent.getMessageComposeTime())))));
            }
            if (errorEvent.getMessageId() != null) {
                container.addView(getInfoView(context,
                        R.string.message_info_error_message_id, errorEvent.getMessageId()));
            }
            container.addView(getInfoView(context,
                    R.string.message_info_sender, errorEvent.getSender()));
            container.addView(getInfoView(context,
                    R.string.message_info_error_recipient_device_id, TextUtils.isEmpty(errorEvent.getSentToDevId())
                            ? "N/A"
                            : errorEvent.getSentToDevId()));
            container.addView(getInfoView(context,
                    R.string.message_info_error_is_duplicate, errorEvent.isDuplicate()));
        }

        if (event instanceof InfoEvent) {
            container.addView(getInfoView(context, R.string.message_info_text,
                    event.getText()));
        }

        // previously dialog showed un-localized formatted string
        // container.addView(getInfoView(context, "Info", event.toFormattedString()));

    }

    private void updateJsonFields(@NonNull Context context, @NonNull ViewGroup container,
            @NonNull Event event) {
        String json = getEventJSON(event);
        if (!TextUtils.isEmpty(json)) {
            View view = getInfoView(context, R.string.message_info_tab_json, json);
            container.addView(view);
        }
    }

    private void updateTraceFields(@NonNull Context context, @NonNull ViewGroup container,
            @NonNull Event event) {
        if (BuildConfig.DEBUG) {
            int[] code = new int[1];
            String id = (event instanceof ErrorEvent) ? ((ErrorEvent) event).getMessageId() : event.getId();
            byte[][] trace = ZinaNative.loadCapturedMsgs(null, IOUtils.encode(id), null, code);

            if (trace.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (byte[] data : trace) {
                    sb.append(new String(data)).append("\n");
                }
                container.addView(getInfoView(context, R.string.message_info_trace, sb));
            } else {
                container.addView(getInfoView(context, R.string.message_info_trace,
                        context.getString(R.string.message_info_tab_no_trace_available)));
            }
        }
    }

    private String getLocalizedDataRetention(@NonNull Context context,
            @NonNull RetentionInfo.DataRetention info) {
        StringBuilder sb = new StringBuilder();
        DRUtils.DRMessageHelper helper = new DRUtils.DRMessageHelper(context);
        helper.getRetainingOrganizationDescription(sb, info.getForOrgName());

        RetentionInfo.DataRetention.RetentionFlags flags = info.getRetentionFlags();
        helper.getRetentionDescriptionAsList(sb,
                flags.isMessageMetadata(), flags.isMessagePlaintext(), flags.isCallMetadata(),
                flags.isCallPlaintext(), flags.isAttachmentPlaintext());
        return sb.toString();
    }

    private View getInfoView(@NonNull Context context, int label,
            @Nullable Object information) {
        return getInfoView(context, context.getString(label), information);
    }

    private View getInfoView(@NonNull Context context, @NonNull String label,
            @Nullable Object information) {
        EventInfoField view = new EventInfoField(context);
        view.setInfo(label, String.valueOf(information));
        return view;
    }

    private String getEventJSON(@NonNull Event event) {
        String result = null;
        try {
            JSONObject json = new JSONEventAdapter().adapt(event);
            if (!BuildConfig.DEBUG) {
                json.remove("metaData");
            }
            result = json.toString(2);
        } catch (JSONException e) {
            // ignore, failed to format json properly
        }
        return result;
    }

    public enum EventInfoPageEnum {

        INFO(R.string.message_info_tab_info, R.layout.widget_event_info_container),
        JSON(R.string.message_info_tab_json, R.layout.widget_event_info_container);
        /*
         * TODO format trace information
         *
        TRACE(R.string.message_info_tab_trace, R.layout.widget_event_info_container);
         */

        private int mTitleResId;
        private int mLayoutResId;

        EventInfoPageEnum(int titleResId, int layoutResId) {
            mTitleResId = titleResId;
            mLayoutResId = layoutResId;
        }

        public int getTitleResId() {
            return mTitleResId;
        }

        public int getLayoutResId() {
            return mLayoutResId;
        }

    }

    public class EventInfoPagerAdapter extends PagerAdapter {

        private final Context mContext;
        private final Event mEvent;

        public EventInfoPagerAdapter(Context context, Event event) {
            mContext = context;
            mEvent = event;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            EventInfoPageEnum eventInfoPageEnum = EventInfoPageEnum.values()[position];
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(eventInfoPageEnum.getLayoutResId(),
                    collection, false);
            collection.addView(layout);
            ViewGroup container = (ViewGroup) layout.findViewById(R.id.event_details_container);
            if (mEvent != null) {
                if (position == 0) {
                    updateInfoFields(mContext, container, mEvent);
                } else if (position == 1) {
                    updateJsonFields(mContext, container, mEvent);
                } else if (position == 2) {
                    updateTraceFields(mContext, container, mEvent);
                }
            }
            return layout;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }

        @Override
        public int getCount() {
            return EventInfoPageEnum.values().length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            EventInfoPageEnum eventInfoPageEnum = EventInfoPageEnum.values()[position];
            return mContext.getString(eventInfoPageEnum.getTitleResId());
        }
    }

}
