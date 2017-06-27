/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.ProgressBar;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.ShowRemoteDevicesActivity;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.DeviceInfo;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the Axolotl (messaging) device management fragment.
 *
 * Created by werner on 21.06.15.
 */
public class RemoteDevicesFragment extends Fragment implements View.OnClickListener,
        AlertDialogFragment.OnAlertDialogConfirmedListener {

    @SuppressWarnings("unused")
    private static final String TAG = RemoteDevicesFragment.class.getSimpleName();

    /* Dialog request code for delete conversation confirmation */
    private static final int REKEY_CONVERSATION = 1;

    ShowRemoteDevicesActivity mParent;
    private String mDevicesHeader;
    private ListView mDevicesList;
    private ProgressBar mProgressBar;
    private String mPartner;
    private View mRootView;
    private boolean mNameMissing;

    private int mColorBase;
    private int mColorGreen;
    private CharSequence mNoDevices;
    private CharSequence mLocalDeviceIdEmpty;

    public static RemoteDevicesFragment newInstance(Bundle args) {
        RemoteDevicesFragment f = new RemoteDevicesFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            mParent.finish();
            return;
        }
        mPartner = args.getString(ShowRemoteDevicesActivity.PARTNER, null);
        if (TextUtils.isEmpty(mPartner)) {
            mParent.finish();
            return;
        }
        // Indicate that this fragment would like to influence the set of actions in the action bar.
        setHasOptionsMenu(true);
        mColorBase = ViewUtil.getColorFromAttributeId(mParent, R.attr.sp_activity_primary_text_color);
        mColorGreen = ContextCompat.getColor(mParent, R.color.sc_ng_text_green);
        mDevicesHeader = getString(R.string.axo_remote_devices);
        mNoDevices = getString(R.string.no_axo_device);
        mLocalDeviceIdEmpty = getString(R.string.axo_local_device_id_empty);
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

    private void commonOnAttach(Activity activity) {
        try {
            mParent = (ShowRemoteDevicesActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must be ShowRemoteDevicesActivity.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.axo_remote_devices, container, false);
        if (mRootView == null)
            return null;
        mProgressBar = (ProgressBar)mRootView.findViewById(R.id.progressBar);
        mDevicesList = (ListView)mRootView.findViewById(R.id.AxoDeviceList);
        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        doRescan();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.call:
                DeviceInfo.DeviceData devData = (DeviceInfo.DeviceData)view.getTag();
                String directDial = mPartner + ";xscdevid=" + devData.devId;
                Intent intent = ContactsUtils.getCallIntent(directDial);
                intent.putExtra(DialerActivity.NO_NUMBER_CHECK, true);
                startActivity(intent);
                mParent.finish();
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.axo_remote_devices, menu);
        super.onCreateOptionsMenu(menu, inflater);

        if(!BuildConfig.DEBUG) {
            String self = getUsername();

            if(self != null) {
                if(self.equals(mPartner)) {
                    menu.findItem(R.id.axo_renew).setVisible(false);
                }
            }
        }
    }

    // InCallActivity handles the Menu selection
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.axo_renew:
                confirmConversationRekey();
                return true;
            case R.id.axo_rescan:
                doRescan();
                return true;
        }
        return false;
    }

    @Override
    public void onAlertDialogConfirmed(DialogInterface dialog, int requestCode, Bundle bundle,
                                       boolean saveChoice) {
        if (requestCode == REKEY_CONVERSATION) {
            setProgressBarVisibility();
            AxoCommandInBackground aib = new AxoCommandInBackground();
            aib.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "reKeyAllDevices", mPartner);
        }
    }

    private void doRescan() {
        setProgressBarVisibility();
        AxoCommandInBackground aib = new AxoCommandInBackground();
        aib.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "rescanUserDevices", mPartner);
    }

    private String getUsername() {
        ZinaMessaging axo = ZinaMessaging.getInstance();
        return axo.getUserName();
    }

    /** Request the identity key and associated device info from ZINA conversation */
    private void getDevicesInfo() {
        byte[] ownDevice = ZinaMessaging.getOwnIdentityKey();
        if (ownDevice != null)
            parseSetOwnDevice(new String(ownDevice));

        setOwnDeviceId(Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(
                SilentPhoneApplication.getAppContext(), false)));

        byte[][] devices = ZinaMessaging.getIdentityKeys(IOUtils.encode(mPartner));
        if (devices == null || devices.length == 0) {
            emptyDeviceInfo();
            return;
        }
        ArrayList<DeviceInfo.DeviceData> devData = new ArrayList<>(5);
        for (byte[] device : devices) {
            DeviceInfo.DeviceData devInfo = DeviceInfo.parseDeviceInfo(new String(device));
            if (devInfo != null) {
                devData.add(devInfo);
            }
        }
        setupDeviceList(devData);
    }

    private void emptyDeviceInfo() {
        ((TextView)mRootView.findViewById(R.id.AxoDeviceHeader)).setText(mNoDevices);
        mRootView.findViewById(R.id.AxoDeviceHeader).setVisibility(View.VISIBLE);
    }

    private void setupDeviceList(ArrayList<DeviceInfo.DeviceData> devData) {
        TextView tv = (TextView) mRootView.findViewById(R.id.AxoDeviceHeader);
        tv.setVisibility(View.VISIBLE);

        if (isAdded()) {
            Resources res = getResources();
            int count = devData.size();
            mDevicesHeader = res.getQuantityString(R.plurals.axo_remote_devices_n, count, count);
        }
        tv.setText(mDevicesHeader);
        mRootView.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.VISIBLE);
        mRootView.findViewById(R.id.AxoDeviceListHeader).setVisibility(View.VISIBLE);

        // Set up the devices list view
        DevicesArrayAdapter devicesAdapter = new DevicesArrayAdapter(mParent, devData);
        mDevicesList.setAdapter(devicesAdapter);
        mDevicesList.setVisibility(View.VISIBLE);
//        mDevicesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//        }
    }

    private void parseSetOwnDevice(String ownDevice) {
        View view = getView();
        if (view == null)
            return;
        String elements[] = ownDevice.split(":");
        if (elements.length < 1)
            return;
        // Convert id key to hex string with leading zeros
        byte[] idKey = Base64.decode(elements[0], Base64.DEFAULT);
        final String idKeyFingerprint = DeviceInfo.fingerprint(idKey);
        TextView tv = (TextView) view.findViewById(R.id.AxoLocalDeviceKey);
        tv.setText(idKeyFingerprint);
    }

    private void setOwnDeviceId(String ownDeviceId) {
        View view = getView();
        if (view == null)
            return;
        TextView tv = (TextView) view.findViewById(R.id.AxoLocalDeviceId);
        tv.setText(TextUtils.isEmpty(ownDeviceId)
                ? mLocalDeviceIdEmpty
                : ownDeviceId);
    }

    private void confirmConversationRekey() {
        AlertDialogFragment dialogFragment = AlertDialogFragment.getInstance(
                R.string.rekey_messaging_sessions,
                R.string.warning_rekey_conversation,
                R.string.dialog_button_cancel,
                R.string.dialog_button_ok,
                null,
                false);
        dialogFragment.setTargetFragment(this, REKEY_CONVERSATION);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                    .add(dialogFragment, AlertDialogFragment.TAG_ALERT_DIALOG)
                    .commitAllowingStateLoss();
        }
    }

    private void setProgressBarVisibility() {
        mDevicesList.setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.AxoDeviceListHeader).setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.AxoDeviceHeader).setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private class DevicesArrayAdapter extends ArrayAdapter<DeviceInfo.DeviceData> {
        private final Context context;

        DevicesArrayAdapter(Context context, List<DeviceInfo.DeviceData> values) {
            super(context, R.layout.axo_remote_devices_line, values);
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null)
                convertView = inflater.inflate(R.layout.axo_remote_devices_line, parent, false);
            View rowView = convertView;
            DeviceInfo.DeviceData devData = getItem(position);

            TextView tv = (TextView)rowView.findViewById(R.id.dev_name);
            tv.setText(devData.name);
            tv = (TextView)rowView.findViewById(R.id.dev_id);
            tv.setText(devData.devId);

            tv = (TextView)rowView.findViewById(R.id.id_key);
            tv.setText(devData.identityKey);

            ImageView iv = (ImageView) rowView.findViewById(R.id.verify_check);
            switch (devData.zrtpVerificationState) {
                case "0":
                    iv.setVisibility(View.INVISIBLE);
                    tv.setVisibility(View.VISIBLE);
                    break;
                case "1":
                    iv.setImageResource(R.drawable.ic_check_white_24dp);
                    iv.setColorFilter(mColorBase);
                    iv.setVisibility(View.VISIBLE);
                    tv.setVisibility(View.VISIBLE);
                    break;
                case "2":
                    iv.setImageResource(R.drawable.ic_check_green_24dp);
                    iv.setColorFilter(mColorGreen);
                    iv.setVisibility(View.VISIBLE);
                    tv.setVisibility(View.GONE);
                    break;
            }
            iv = (ImageView)rowView.findViewById(R.id.call);
            iv.setTag(devData);
            iv.setOnClickListener(RemoteDevicesFragment.this);
            return rowView;
        }
    }

    private class AxoCommandInBackground extends AsyncTask<String, Void, Integer> {

        private String mCommand;

        @Override
        protected Integer doInBackground(String... commands) {
            long startTime = System.currentTimeMillis();
            byte[] data = null;
            if (commands.length >= 1)
                data = IOUtils.encode(commands[1]);
            mCommand = commands[0];
            int[] code = new int[1];
            ZinaMessaging.zinaCommand(mCommand, data, code);
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command '" + mCommand + "': " + time);
            mProgressBar.setVisibility(View.INVISIBLE);
            if (mParent != null) {
                getDevicesInfo();
            }
        }
    }
}
