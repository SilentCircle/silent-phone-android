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

package com.silentcircle.messaging.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.messaging.activities.ShowRemoteDevicesActivity;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays the Axolotl (messaging) device management fragment.
 *
 * Created by werner on 21.06.15.
 */
public class RemoteDevicesFragment extends Fragment implements View.OnClickListener {
    @SuppressWarnings("unused")
    private static final String TAG = RemoteDevicesFragment.class.getSimpleName();

    ShowRemoteDevicesActivity mParent;
    private ListView mDevicesList;
    private ProgressBar mProgressBar;
    private String mPartner;
    private View mRootView;
    private boolean mNameMissing;

    private static class DeviceData {
        final String name;
        final String devId;
        final String identityKey;
        final String zrtpVerificationState;

        DeviceData(String name, String id, String key, String verifyState) {
            this.name = name;
            this.devId = id;
            identityKey = key;
            zrtpVerificationState = verifyState;
        }
    }

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
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (ShowRemoteDevicesActivity) activity;
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
        mRootView.findViewById(R.id.ok).setOnClickListener(this);
        mProgressBar = (ProgressBar)mRootView.findViewById(R.id.progressBar);
        mDevicesList = (ListView)mRootView.findViewById(R.id.AxoDeviceList);
        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        getDevicesInfo();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                mParent.finish();
                break;

            case R.id.call:
                DeviceData devData = (DeviceData)view.getTag();
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
    }

    // InCallActivity handles the Menu selection
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.axo_renew:
                mDevicesList.setVisibility(View.INVISIBLE);
                mRootView.findViewById(R.id.AxoDeviceListHeader).setVisibility(View.INVISIBLE);
                mRootView.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.INVISIBLE);
                mRootView.findViewById(R.id.AxoDeviceHeader).setVisibility(View.INVISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                AxoCommandInBackground aib = new AxoCommandInBackground();
                aib.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "removeAxoConversation", mPartner);
                return true;

            case R.id.axo_rescan:
                doRescan();
                return true;
        }
        return false;
    }

    private void doRescan() {
        mDevicesList.setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.AxoDeviceListHeader).setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.AxoDeviceHeader).setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        AxoCommandInBackground aib = new AxoCommandInBackground();
        aib.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "rescanUserDevices", mPartner);
    }

    /** Request the identity key and associated device info from Axolotl conversation */
    private void getDevicesInfo() {
        byte[] ownDevice = AxoMessaging.getOwnIdentityKey();
        parseSetOwnDevice(new String(ownDevice));

        byte[][] devices = AxoMessaging.getIdentityKeys(IOUtils.encode(mPartner));
        if (devices.length == 0) {
            emptyDeviceInfo();
            return;
        }
        ArrayList<DeviceData> devData = new ArrayList<>(5);
        boolean rescan = false;
        for (byte[] device : devices) {
            DeviceData devInfo = parseDeviceInfo(new String(device));
            if (devInfo != null && TextUtils.isEmpty(devInfo.name) && !mNameMissing) {
                rescan = true;
                mNameMissing = true;
                break;
            }
            devData.add(parseDeviceInfo(new String(device)));
        }
        if (rescan) {
            doRescan();
        }
        else {
            setupDeviceList(devData);
        }
    }

    private void emptyDeviceInfo() {
        ((TextView)mRootView.findViewById(R.id.AxoDeviceHeader)).setText(getText(R.string.no_axo_device));
        mRootView.findViewById(R.id.AxoDeviceHeader).setVisibility(View.VISIBLE);
    }

    private void setupDeviceList(ArrayList<DeviceData> devData) {
        TextView tv = (TextView) mRootView.findViewById(R.id.AxoDeviceHeader);
        tv.setVisibility(View.VISIBLE);

        Resources res = getResources();
        int count = devData.size();
        String axoDevices = res.getQuantityString(R.plurals.axo_remote_devices, count, count);
        tv.setText(axoDevices);
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
        final String idKeyFingerprint = fingerprint(idKey);
        TextView tv = (TextView) view.findViewById(R.id.AxoLocalDeviceKey);
        tv.setText(idKeyFingerprint);
    }

    // identityKey:device name:device id:verify state
    private DeviceData parseDeviceInfo(String devData) {
        String elements[] = devData.split(":");
        if (elements.length != 4) {
            return null;
        }
        // Convert id key to hex string with leading zeros
        byte[] idKey = Base64.decode(elements[0], Base64.DEFAULT);
        final String idKeyFingerprint = fingerprint(idKey);
        return new DeviceData(elements[1], elements[2], idKeyFingerprint, elements[3]);
    }

    private String fingerprint(byte[] data) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        final byte[] hash = md.digest(data);

        final String hexString = new String(Utilities.bytesToHexChars(hash, true));
        final int len = hexString.length();
        final StringBuilder sb = new StringBuilder(80);
        for (int i = 1; i <= len; i++) {
            sb.append(hexString.charAt(i-1));
            if ((i % 2) == 0)
                sb.append(':');
            if ((i % 16) == 0)
                sb.append('\n');
        }
        if (sb.charAt(sb.length()-2) == ':') {
            sb.deleteCharAt(sb.length() - 2);
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    private class DevicesArrayAdapter extends ArrayAdapter<DeviceData> {
        private final Context context;

        public DevicesArrayAdapter(Context context, List<DeviceData> values) {
            super(context, R.layout.axo_remote_devices_line, values);
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null)
                convertView = inflater.inflate(R.layout.axo_remote_devices_line, parent, false);
            View rowView = convertView;
            DeviceData devData = getItem(position);

            TextView tv = (TextView)rowView.findViewById(R.id.dev_name);
            tv.setText(devData.name);
            tv = (TextView)rowView.findViewById(R.id.dev_id);
            tv.setText(devData.devId);

            tv = (TextView)rowView.findViewById(R.id.id_key);
            tv.setText(devData.identityKey);

            ImageView iv = (ImageView)rowView.findViewById(R.id.verify_check);
            switch (devData.zrtpVerificationState) {
                case "0":
                    break;
                case "1":
                    iv.setImageResource(R.drawable.ic_check_white_24dp);
                    iv.setVisibility(View.VISIBLE);
                    break;
                case "2":
                    iv.setImageResource(R.drawable.ic_check_green_24dp);
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
            AxoMessaging.axoCommand(mCommand, data);
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for async command '" + mCommand + "': " + time);
            mProgressBar.setVisibility(View.INVISIBLE);
            getDevicesInfo();
        }
    }
}
