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
package org.acra.sender;

// Based on raven-java(Ken Cochrane and others)
// Adapted from https://github.com/dz0ny/acra

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.silentphone2.BuildConfig;

import org.acra.ACRA;
import org.acra.ErrorReporter;
import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.config.ACRAConfiguration;
import org.acra.sender.HttpSender.Method;
import org.acra.util.HttpRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.AVAILABLE_MEM_SIZE;
import static org.acra.ReportField.BRAND;
import static org.acra.ReportField.CUSTOM_DATA;
import static org.acra.ReportField.FILE_PATH;
import static org.acra.ReportField.INITIAL_CONFIGURATION;
import static org.acra.ReportField.INSTALLATION_ID;
import static org.acra.ReportField.IS_SILENT;
import static org.acra.ReportField.LOGCAT;
import static org.acra.ReportField.PACKAGE_NAME;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.PRODUCT;
import static org.acra.ReportField.SHARED_PREFERENCES;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.TOTAL_MEM_SIZE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_CRASH_DATE;


public class SentrySender implements ReportSender {

    private static final String TAG = SentrySender.class.getSimpleName();

    public static final String MESSAGE_FAILED_CHAT_MESSAGE = "Failed Chat Message";

    /*
     * Attributes recognized by Sentry
     *
     * https://docs.sentry.io/clientdev/attributes/
     */
    public static final String TAG_SENTRY_EVENT_ID = "event_id";
    public static final String TAG_SENTRY_TIMESTAMP = "timestamp";
    public static final String TAG_SENTRY_LOGGER = "logger";
    public static final String TAG_SENTRY_PLATFORM = "platform";
    public static final String TAG_SENTRY_CULPRIT = "culprit";
    public static final String TAG_SENTRY_LEVEL = "level";
    public static final String TAG_SENTRY_EXTRA = "extra";
    public static final String TAG_SENTRY_TAGS = "tags";
    // built-in interfaces have shortcuts, sentry.interfaces.Stacktrace -> stacktrace
    public static final String TAG_SENTRY_STACKTRACE = "stacktrace";
    public static final String TAG_SENTRY_STACKTRACE_FRAMES = "frames";
    public static final String TAG_SENTRY_EXCEPTION = "exception";
    public static final String TAG_SENTRY_EXCEPTION_VALUES = "values";
    public static final String TAG_SENTRY_EXCEPTION_VALUE = "value";
    public static final String TAG_SENTRY_EXCEPTION_TYPE = "type";
    public static final String TAG_SENTRY_EXCEPTION_MODULE = "module";
    public static final String TAG_SENTRY_MESSAGE = "message";

    /*
     * Project specific attributes, used as tags
     */
    public static final String TAG_SPA_UUID = "uuid";
    public static final String TAG_SPA_SENTRY_USER = "sentry:user";
    public static final String TAG_SPA_RELEASE = "sentry:release";
    public static final String TAG_SPA_DISPLAYNAME = "displayname";
    public static final String TAG_SPA_USERNAME = "username";
    public static final String TAG_SPA_BUILD_TYPE = "spa.build.type";
    public static final String TAG_SPA_BUILD_FLAVOR = "spa.build.flavor";
    public static final String TAG_SPA_BUILD_COMMIT = "spa.build.commit";
    public static final String TAG_SPA_BUILD_DATE = "spa.build.date";
    public static final String TAG_SPA_BUILD_NUMBER = "spa.build.number";
    public static final String TAG_SPA_OS_VERSION = "os.version";

    /*
     * Attributes for message failure report
     */
    public static final String MESSAGE_TRACE_INFO = "message_trace";
    public static final String MESSAGE_JSON_INFO = "message_json";
    public static final String MESSAGE_STATUS = "message_status";
    public static final String MESSAGE_IDENTIFIER = "message_identifier";
    public static final String MESSAGE_ERROR_CODE = "message_error_code";
    public static final String MESSAGE_STATUS_CODE = "message_status_code";

    public static final String VALUE_NOT_AVAILABLE_PLACEHOLDER = "n/a";

    public static final ReportField[] SENTRY_TAGS_FIELDS = {APP_VERSION_CODE, APP_VERSION_NAME,
            PACKAGE_NAME, FILE_PATH, PHONE_MODEL, BRAND, PRODUCT, ANDROID_VERSION, TOTAL_MEM_SIZE,
            AVAILABLE_MEM_SIZE, IS_SILENT, USER_APP_START_DATE, USER_CRASH_DATE, INSTALLATION_ID};
    public static final ReportField[] EXTRA_FIELDS = {LOGCAT, STACK_TRACE, CUSTOM_DATA,
            // these may be too detailed, user specific
            SHARED_PREFERENCES, INITIAL_CONFIGURATION};

    private SentryConfig mSentryConfig;
    private final ACRAConfiguration mAcraConfig;
    private final Date mReportDate;

    private static final Semaphore mSenderSemaphore = new Semaphore(1, true);

    /**
     * Send a report to Sentry with additional detailed information for an event.
     *
     * @param event Event for which to gather information.
     * @param statusCode Code returned by sip library (for cases where event state is updated due
     *                   to sending issues.
     * @param errorCode Code returned by Zina (for cases when event could not be send by Zina or
     *                  could not be processed by Zina, e.g., decryption issue).
     */
    public static void sendMessageStateReport(final @NonNull Event event, final int statusCode,
            @MessageErrorCodes.MessageErrorCode final int errorCode) {

        if (!ACRA.isInitialised()) {
            Log.w(TAG, "Could not send message state report, ACRA not initialised.");
            return;
        }

        final Exception exception = new Exception(MESSAGE_FAILED_CHAT_MESSAGE
                + " (" + statusCode + ", " + errorCode + ")");

        // try to run off of messaging thread
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    report();
                }
                catch (InterruptedException | IllegalStateException e) {
                    Log.w(TAG, "Could not send message state report.");
                }
            }

            private void report() throws InterruptedException {
                mSenderSemaphore.acquire();
                try {
                    ErrorReporter reporter = ACRA.getErrorReporter();
                    String messageId = (event instanceof ErrorEvent)
                            ? ((ErrorEvent) event).getMessageId()
                            : event.getId();
                    int status = (event instanceof Message)
                            ? ((Message) event).getState()
                            : MessageStates.UNKNOWN;

                    reporter.putCustomData(SentrySender.MESSAGE_TRACE_INFO,
                            MessageUtils.getMessageTraceInfo(event));
                    // this would always be 12 - "Failed"
                    reporter.putCustomData(SentrySender.MESSAGE_STATUS, String.valueOf(status));
                    reporter.putCustomData(SentrySender.MESSAGE_ERROR_CODE, String.valueOf(errorCode));
                    reporter.putCustomData(SentrySender.MESSAGE_STATUS_CODE, String.valueOf(statusCode));
                    reporter.putCustomData(SentrySender.MESSAGE_IDENTIFIER, messageId);
                    JSONObject json = new JSONEventAdapter().adapt(event);
                    if (json != null) {
                        // remove all sensitive fields from event json
                        json.remove(JSONEventAdapter.TAG_EVENT_TEXT);
                        json.remove(JSONEventAdapter.TAG_EVENT_CONVERSATION_ID);
                        json.remove(JSONEventAdapter.TAG_DEVICE_INFO_ARRAY);
                        json.remove(JSONEventAdapter.TAG_MESSAGE_ATTACHMENT);
                        json.remove(JSONEventAdapter.TAG_MESSAGE_METADATA);
                        json.remove(JSONEventAdapter.TAG_MESSAGE_LOCATION);
                        json.remove(JSONEventAdapter.TAG_MESSAGE_SENDER);
                        reporter.putCustomData(SentrySender.MESSAGE_JSON_INFO, json.toString());
                    }
                    reporter.handleException(exception, false);
                    // remove custom data we have possibly added
                    try {
                        reporter.removeCustomData(SentrySender.MESSAGE_TRACE_INFO);
                        reporter.removeCustomData(SentrySender.MESSAGE_JSON_INFO);
                        reporter.removeCustomData(SentrySender.MESSAGE_STATUS);
                        reporter.removeCustomData(SentrySender.MESSAGE_ERROR_CODE);
                        reporter.removeCustomData(SentrySender.MESSAGE_IDENTIFIER);
                        reporter.removeCustomData(SentrySender.MESSAGE_STATUS_CODE);
                    } catch (Throwable t) {
                        // ignore failure to remove custom field
                        // crash here can be caused by acra internal issue
                    }
                } finally {
                    mSenderSemaphore.release();
                }
            }
        });
    }

    /**
     * Initializes sentry sender.
     *
     * @param acraConfig Current ACRA configuration
     * @param sentryDSN Sentry DSN in form '{PROTOCOL}://{PUBLIC_KEY}:{SECRET_KEY}@{HOST}/{PATH}/{PROJECT_ID}'
     */
    public SentrySender(@NonNull ACRAConfiguration acraConfig, @NonNull String sentryDSN) {
        mAcraConfig = acraConfig;
        mSentryConfig = new SentryConfig(sentryDSN);
        mReportDate = new Date();
    }

    @Override
    public void send(@NonNull Context context, @NonNull CrashReportData errorContent) throws ReportSenderException {
        if (mSentryConfig == null) {
            return;
        }

        final Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Sentry-Auth", buildAuthHeader());

        final HttpRequest request = new HttpRequest(mAcraConfig);
        request.setConnectionTimeOut(mAcraConfig.connectionTimeout());
        request.setSocketTimeOut(mAcraConfig.socketTimeout());
        request.setHeaders(headers);

        try {
            request.send(context, mSentryConfig.getSentryURL(), Method.POST, buildJSON(errorContent),
                    org.acra.sender.HttpSender.Type.JSON);
        } catch (IOException | JSONException e) {
            throw new ReportSenderException("Error while sending report to Sentry.", e);
        }
    }

    /**
     * Build up the sentry auth header in the following format.
     * <p/>
     * The header is composed of the timestamp from when the message was generated, and an
     * arbitrary client version string. The client version should be something distinct to your client,
     * and is simply for reporting purposes.
     * <p/>
     * X-Sentry-Auth: Sentry sentry_version=3,
     * sentry_timestamp=<signature timestamp>[,
     * sentry_key=<public api key>,[
     * sentry_client=<client version, arbitrary>]]
     *
     * @return String version of the sentry auth header
     */
    protected String buildAuthHeader() {
        /*
          X-Sentry-Auth: Sentry sentry_version=3,
            sentry_client=<client version, arbitrary>,
            sentry_timestamp=<current timestamp>,
            sentry_key=<public api key>,
            sentry_secret=<secret api key>
        */
        StringBuilder header = new StringBuilder();
        header.append("Sentry sentry_version=7");
        header.append(",sentry_client=ACRA");
        header.append(",sentry_timestamp=");
        header.append(getTimestampString());
        header.append(",sentry_key=");
        header.append(mSentryConfig.getPublicKey());
        header.append(",sentry_secret=");
        header.append(mSentryConfig.getSecretKey());

        return header.toString();
    }

    // Sentry expects ISO8601 time without timezone: https://docs.sentry.io/clientdev/attributes/
    private static final SimpleDateFormat ISO8601;
    static {
        ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Given the time right now return a ISO8601 formatted date string
     *
     * @return ISO8601 formatted date string
     */
    public String getTimestampString() {
        return ISO8601.format(mReportDate);
    }

    private String buildJSON(CrashReportData report) throws JSONException {
        JSONObject obj = new JSONObject();

        String message = report.getProperty(ReportField.STACK_TRACE).split("\n")[0];
        String placeOfException = report.getProperty(ReportField.STACK_TRACE).split("\n")[1];

        /*
         * Populate base attributes of the report
         */
        // Hexadecimal string representing a uuid4 value.
        obj.put(TAG_SENTRY_EVENT_ID, report.getProperty(ReportField.REPORT_ID).replace("-", ""));
        obj.put(TAG_SENTRY_CULPRIT, message);
        obj.put(TAG_SENTRY_LEVEL, "error");
        obj.put(TAG_SENTRY_TIMESTAMP, getTimestampString());
        obj.put(TAG_SENTRY_LOGGER, "org.acra");
        obj.put(TAG_SENTRY_PLATFORM, "android");
        /*
         * Do not send exception as it is not properly constructed. This seems to throw off Sentry
         * and report is not searchable
         *
         * obj.put(TAG_SENTRY_EXCEPTION, buildException(report.getProperty(ReportField.STACK_TRACE)));
         */
        try {
            obj.put(TAG_SENTRY_STACKTRACE, buildStacktrace(report.getProperty(ReportField.STACK_TRACE)));
        } catch (Throwable t) {
            /* stacktrace is available in EXTRA_FIELDS as well, so leave it */
            if (ACRA.DEV_LOGGING) {
                ACRA.log.d(ACRA.LOG_TAG, "Could not parse exception.");
            }
        }
        obj.put(TAG_SENTRY_MESSAGE, (message.contains(MESSAGE_FAILED_CHAT_MESSAGE)
                ? message
                : message + " " + placeOfException.trim()));

        /*
         * Populate tags which are searchable
         */
        JSONObject tags = remap(report, SENTRY_TAGS_FIELDS);
        String param = SilentPhoneApplication.sUuid;
        tags.put(TAG_SPA_UUID, TextUtils.isEmpty(param) ? VALUE_NOT_AVAILABLE_PLACEHOLDER : param);
        tags.put(TAG_SPA_SENTRY_USER, TextUtils.isEmpty(param) ? VALUE_NOT_AVAILABLE_PLACEHOLDER : param);
        tags.put(TAG_SPA_RELEASE, BuildConfig.VERSION_NAME);
        param = SilentPhoneApplication.sDisplayName;
        tags.put(TAG_SPA_DISPLAYNAME, TextUtils.isEmpty(param) ? VALUE_NOT_AVAILABLE_PLACEHOLDER : param);
        param = SilentPhoneApplication.sDisplayAlias;
        tags.put(TAG_SPA_USERNAME, TextUtils.isEmpty(param) ? VALUE_NOT_AVAILABLE_PLACEHOLDER : param);
        tags.put(TAG_SPA_BUILD_TYPE, BuildConfig.BUILD_TYPE);
        tags.put(TAG_SPA_BUILD_FLAVOR, BuildConfig.FLAVOR);
        tags.put(TAG_SPA_BUILD_COMMIT, BuildConfig.SPA_BUILD_COMMIT);
        tags.put(TAG_SPA_BUILD_DATE, BuildConfig.SPA_BUILD_DATE);
        tags.put(TAG_SPA_BUILD_NUMBER, BuildConfig.SPA_BUILD_NUMBER);
        tags.put(TAG_SPA_OS_VERSION, System.getProperty("os.version"));

        obj.put(TAG_SENTRY_TAGS, tags);

        /*
         * Populate extra fields
         */
        JSONObject extra = new JSONObject();
        remap(extra, report, EXTRA_FIELDS);

        /*
         * Pick select fields from custom data and add to extra section itself.
         * This may be unnecessary but matches SPi report more closely.
         */
        try {
            JSONObject json = new JSONObject(report.getProperty(CUSTOM_DATA));
            if (json.has(MESSAGE_IDENTIFIER)) {
                extra.put(MESSAGE_IDENTIFIER, json.get(MESSAGE_IDENTIFIER));
            }
            if (json.has(MESSAGE_STATUS)) {
                extra.put(MESSAGE_STATUS, json.get(MESSAGE_STATUS));
            }
            if (json.has(MESSAGE_ERROR_CODE)) {
                extra.put(MESSAGE_ERROR_CODE, json.get(MESSAGE_ERROR_CODE));
            }

        } catch (Throwable t) {
            // ignore, the fields will be in custom data section
        }

        obj.put(TAG_SENTRY_EXTRA, extra);

        if (ACRA.DEV_LOGGING) {
            ACRA.log.d(ACRA.LOG_TAG, obj.toString());
        }

        return obj.toString();
    }

    /**
     * Determines the class and method name where the root cause exception occurred.
     *
     * @param exception exception
     * @return the culprit
     */
    private String determineCulprit(Throwable exception) {
        Throwable cause = exception;
        String culprit = null;
        while (cause != null) {
            StackTraceElement[] elements = cause.getStackTrace();
            if (elements.length > 0) {
                StackTraceElement trace = elements[0];
                culprit = trace.getClassName() + "." + trace.getMethodName();
            }
            cause = cause.getCause();
        }
        return culprit;
    }

    private JSONObject buildException(Throwable exception) throws JSONException {
        JSONObject json = new JSONObject();
        json.put(TAG_SENTRY_EXCEPTION_TYPE, exception.getClass().getSimpleName());
        json.put(TAG_SENTRY_EXCEPTION_VALUE, exception.getMessage());
        json.put(TAG_SENTRY_EXCEPTION_MODULE, exception.getClass().getPackage().getName());
        return json;
    }

    private JSONObject buildException(String exception) throws JSONException {
        String[] lines = exception.split("\n");
        JSONObject json = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        try {
            for (String line : lines) {
                JSONObject valuesJson = new JSONObject();
                String[] tv = line.split(":");
                if (tv.length > 0) {
                    valuesJson.put(TAG_SENTRY_EXCEPTION_TYPE, tv[0] /* exception.getClass().getSimpleName() */);
                }
                if (tv.length > 1) {
                    valuesJson.put(TAG_SENTRY_EXCEPTION_VALUE, tv[1] /* exception.getMessage() */);
                }
                /* valuesJson.put("module", exception.getClass().getPackage().getName()); */
                jsonArray.put(valuesJson);
                break;
            }
            json.put(TAG_SENTRY_EXCEPTION_VALUES, jsonArray);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return json;
    }

    private JSONObject buildStacktrace(Throwable exception) throws JSONException {
        JSONArray array = new JSONArray();
        Throwable cause = exception;
        while (cause != null) {
            StackTraceElement[] elements = cause.getStackTrace();
            for (int index = 0; index < elements.length; ++index) {
                if (index == 0) {
                    JSONObject causedByFrame = new JSONObject();
                    String msg = "Caused by: " + cause.getClass().getName();
                    if (cause.getMessage() != null) {
                        msg += " (\"" + cause.getMessage() + "\")";
                    }
                    causedByFrame.put("filename", msg);
                    causedByFrame.put("lineno", -1);
                    array.put(causedByFrame);
                }
                StackTraceElement element = elements[index];
                JSONObject frame = new JSONObject();
                frame.put("filename", element.getClassName());
                frame.put("function", element.getMethodName());
                frame.put("lineno", element.getLineNumber());
                array.put(frame);
            }
            cause = cause.getCause();
        }
        JSONObject stacktrace = new JSONObject();
        stacktrace.put(TAG_SENTRY_STACKTRACE_FRAMES, array);
        return stacktrace;
    }

    /*
     * HACK
     *
     * Crash report instance does not provide access to exception in its class form only
     * string representation. Therefore it is parsed to obtain stack trace json representation
     * for upload to Sentry.
     */
    private static final Pattern mHeadLinePattern = Pattern.compile("([\\w\\.]+):(.*)?");
    private static final Pattern mFrameHeadLinePattern = Pattern.compile("Caused by: ([\\w\\.]+):(.*)?");
    private static final Pattern mTracePattern = Pattern.compile("\\s*at\\s+([\\w\\.$_]+)\\.([\\w$_]+)\\((.*)\\)");
    private static final Pattern mFileNameAndLinePatern = Pattern.compile("(.*java)?:(\\d+)");

    private JSONObject buildStacktrace(@NonNull String exception) throws JSONException {
        String[] lines = exception.split("\n");
        JSONArray array = new JSONArray();
        for (String line : lines) {
            Matcher traceLineMatcher = mTracePattern.matcher(line);
            if (traceLineMatcher.find()) {
                JSONObject frame = new JSONObject();
                frame.put("function", traceLineMatcher.group(2));
                Matcher fileNameMatcher = mFileNameAndLinePatern.matcher(traceLineMatcher.group(3));
                if (fileNameMatcher.find()) {
                    frame.put("filename", fileNameMatcher.group(1));
                    frame.put("lineno", fileNameMatcher.group(2));
                }
                else {
                    frame.put("filename", traceLineMatcher.group(1));
                    frame.put("lineno", -1);
                }
                array.put(frame);
            }
            else {
                Matcher frameHeadLineMatcher = mFrameHeadLinePattern.matcher(line);
                if (frameHeadLineMatcher.find()) {
                    JSONObject causedByFrame = new JSONObject();
                    String msg = "Caused by: " + frameHeadLineMatcher.group(1);
                    String cause = frameHeadLineMatcher.group(2);
                    if (!TextUtils.isEmpty(cause)) {
                        msg += " (\"" + cause + "\")";
                    }
                    causedByFrame.put("filename", msg);
                    causedByFrame.put("lineno", -1);
                    array.put(causedByFrame);
                }
                else {
                    Matcher headLineMatcher = mHeadLinePattern.matcher(line);
                    if (headLineMatcher.find()) {
                        JSONObject causedByFrame = new JSONObject();
                        String msg = "Caused by: " + headLineMatcher.group(1);
                        String cause = headLineMatcher.group(2);
                        if (!TextUtils.isEmpty(cause)) {
                            msg += " (\"" + cause + "\")";
                        }
                        causedByFrame.put("filename", msg);
                        causedByFrame.put("lineno", -1);
                        array.put(causedByFrame);
                    }
                }
            }
        }

        JSONArray reversedArray = new JSONArray();
        for(int i = 0; i < array.length(); i++){
            reversedArray.put(array.get(array.length() - i - 1));
        }

        JSONObject stacktrace = new JSONObject();
        stacktrace.put(TAG_SENTRY_STACKTRACE_FRAMES, reversedArray);
        return stacktrace;
    }

    private JSONObject remap(CrashReportData report, ReportField[] fields) throws JSONException {

        final JSONObject result = new JSONObject();
        return remap(result, report, fields);
    }

    private JSONObject remap(@NonNull JSONObject result, @NonNull CrashReportData report,
            @NonNull ReportField[] fields) throws JSONException {
        for (ReportField originalKey : fields) {
            try {
                result.put(originalKey.toString(), report.getProperty(originalKey));
            } catch (Throwable t) {
                // value is empty, e.g., USER_COMMENT
            }
        }
        return result;
    }

    private class SentryConfig {

        private String host, protocol, publicKey, secretKey, path, projectId;
        private int port;

        /**
         * Takes in a sentryDSN and builds up the configuration
         *
         * @param sentryDSN '{PROTOCOL}://{PUBLIC_KEY}:{SECRET_KEY}@{HOST}/{PATH}/{PROJECT_ID}'
         */
        public SentryConfig(String sentryDSN) {

            try {
                URL url = new URL(sentryDSN);
                this.host = url.getHost();
                this.protocol = url.getProtocol();
                String urlPath = url.getPath();

                int lastSlash = urlPath.lastIndexOf("/");
                this.path = urlPath.substring(0, lastSlash);
                // ProjectId is the integer after the last slash in the path
                this.projectId = urlPath.substring(lastSlash + 1);

                String userInfo = url.getUserInfo();
                String[] userParts = userInfo.split(":");

                this.secretKey = userParts[1];
                this.publicKey = userParts[0];

                this.port = url.getPort();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

        }

        /**
         * The Sentry server URL that we post the message to.
         *
         * @return sentry server url
         * @throws MalformedURLException
         */
        public URL getSentryURL() throws MalformedURLException {
            StringBuilder serverUrl = new StringBuilder();
            serverUrl.append(getProtocol());
            serverUrl.append("://");
            serverUrl.append(getHost());
            if ((getPort() != 0) && (getPort() != 80) && getPort() != -1) {
                serverUrl.append(":").append(getPort());
            }
            serverUrl.append(getPath());
            serverUrl.append("/api/store/");
            // absence of project id is not important?
            // serverUrl.append("/api/").append(projectId).append("/store/");
            return new URL(serverUrl.toString());
        }

        /**
         * The sentry server host
         *
         * @return server host
         */
        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        /**
         * Sentry server protocol http https?
         *
         * @return http or https
         */
        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * The Sentry public key
         *
         * @return Sentry public key
         */
        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        /**
         * The Sentry secret key
         *
         * @return Sentry secret key
         */
        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        /**
         * sentry url path
         *
         * @return url path
         */
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Sentry project Id
         *
         * @return project Id
         */
        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        /**
         * sentry server port
         *
         * @return server port
         */
        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

    }
}
