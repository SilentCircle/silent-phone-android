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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.activities.AxoRegisterActivity;
import com.silentcircle.messaging.activities.ShowRemoteDevicesActivity;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays the Axolotl (messaging) device management fragment.
 *
 * Created by werner on 21.06.15.
 */
public class AxoDevicesFragment extends Fragment implements View.OnClickListener {
    @SuppressWarnings("unused")
    private static final String TAG = AxoDevicesFragment.class.getSimpleName();

    AxoRegisterActivity mParent;
    private ListView mDevicesList;
    private boolean mManage;
    private String mDeviceId;
    private ProgressBar mProgressBar;

    private DeviceData mSelectedDevData;

    private static class DeviceData {
        final String name;
        final String devId;
        String zrtpVerificationState;
        PopupMenu mExtendedMenu;

        DeviceData(String name, String id) {
            this.name = name;
            this.devId = id;
        }
    }

    public static AxoDevicesFragment newInstance(Bundle args) {
        AxoDevicesFragment f = new AxoDevicesFragment();
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
        String action = args.getString(AxoRegisterActivity.ACTION, AxoRegisterActivity.ACTION_MANAGE);
        mManage = AxoRegisterActivity.ACTION_MANAGE.equals(action);

        getDevicesInfo();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (AxoRegisterActivity) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.axo_devices, container, false);
        if (rootView == null)
            return null;
        rootView.findViewById(R.id.ok).setOnClickListener(this);
        rootView.findViewById(R.id.no).setOnClickListener(this);
        rootView.findViewById(R.id.cancel).setOnClickListener(this);
        mDevicesList = (ListView)rootView.findViewById(R.id.AxoDeviceList);
        mProgressBar = (ProgressBar)rootView.findViewById(R.id.progressBar);
        return rootView;
    }

    @Override
    public void onClick(View view) {
        int[] code = new int[1];
        switch (view.getId()) {
            case R.id.ok:
                mParent.registerDevice();
                break;

            case R.id.cancel:
                mParent.finish();
                break;

            case R.id.no:
                mParent.noRegistration();
                break;

            case R.id.menu:
                selectExtendedMenu((DeviceData) view.getTag());
                break;

            default:
                break;
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.delete_dev:
                if (mSelectedDevData == null)
                    return true;
                mProgressBar.setVisibility(View.VISIBLE);
                mDevicesList.setVisibility(View.INVISIBLE);
                AxoDeleteInBackground deleteBackground = new AxoDeleteInBackground();
                deleteBackground.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, IOUtils.encode(mSelectedDevData.devId));
                mSelectedDevData = null;
                return true;

            case R.id.call_dev:
                if (mSelectedDevData == null)
                    return true;
                String directDial = AxoMessaging.getInstance(mParent).getUserName() + ";xscdevid=" + mSelectedDevData.devId;
                Intent intent = ContactsUtils.getCallIntent(directDial);
                intent.putExtra(DialerActivity.NO_NUMBER_CHECK, true);
                startActivity(intent);
                mParent.finish();
                return true;

            default:
                break;
        }
        return false;
    }

    /** Request the device info from provisioning server and displays data if available */
    private void getDevicesInfo() {
        DevicesInBackground devicesBackground = new DevicesInBackground(mParent);
        devicesBackground.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void emptyDeviceInfo() {

        View view = getView();
        if (view == null)
            return;
        if (!mManage) {                 // show the buttons only during registration action
            view.findViewById(R.id.buttons).setVisibility(View.VISIBLE);
            view.findViewById(R.id.AxoDeviceAsk).setVisibility(View.VISIBLE);
        }
        mProgressBar.setVisibility(View.GONE);
        view.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.VISIBLE);
        ((TextView)view.findViewById(R.id.AxoDeviceListHeader)).setText(getText(R.string.no_axo_device));
    }

    private void setupDeviceList(ArrayList<DeviceData> devData) {
        emptyDeviceInfo();

        getSiblingDeviceInfo(devData);

        View view = getView();
        if (view == null)
            return;

        TextView tv = (TextView) view.findViewById(R.id.AxoDeviceListHeader);
        Resources res = getResources();
        int count = devData.size();
        String axoDevices = res.getQuantityString(R.plurals.axo_devices, count, count);
        tv.setText(axoDevices);

        view.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.VISIBLE);

        // Set up the devices list view
        DevicesArrayAdapter devicesAdapter = new DevicesArrayAdapter(mParent, devData);
        mDevicesList.setAdapter(devicesAdapter);
        mDevicesList.setVisibility(View.VISIBLE);
    }

    // {"devices": [{"version": 1, "id": "4d1ad....", "device_name": "Nexus 5"}]}
    private ArrayList<DeviceData> parseDeviceInfoJson(String json) {
        try {
            JSONObject jObj = new JSONObject(json);
            if (!jObj.has("devices")) {
                emptyDeviceInfo();
                return null;
            }
            JSONArray jArray = jObj.getJSONArray("devices");
            if (jArray.length() == 0) {
                emptyDeviceInfo();
                return null;
            }
            int length = jArray.length();
            ArrayList<DeviceData> devData = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                JSONObject idName = jArray.getJSONObject(i);
                String name = idName.optString("device_name", "Unknown");
                String id = idName.getString("id");
                DeviceData data = new DeviceData(name, id);
                devData.add(data);
            }
            return devData;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class DevicesArrayAdapter extends ArrayAdapter<DeviceData> {
        private final Context context;

        public DevicesArrayAdapter(Context context, List<DeviceData> values) {
            super(context, R.layout.axo_devices_line, values);
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null)
                convertView = inflater.inflate(R.layout.axo_devices_line, parent, false);
            View rowView = convertView;
            DeviceData devData = getItem(position);

            ImageView iv = (ImageView)rowView.findViewById(R.id.verificationState);
            iv.setTag(devData.devId);

            TextView tv = (TextView)rowView.findViewById(R.id.dev_name);
            tv.setText(devData.name);
            if (devData.devId.equals(mDeviceId)) {
                tv.setTextColor(mParent.getResources().getColor(R.color.black_yellow));
                iv.setVisibility(View.INVISIBLE);
            }
            else {
                if (devData.zrtpVerificationState != null) {
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
                            break;
                    }
                }
                if (devData.mExtendedMenu == null) {
                    devData.mExtendedMenu = new PopupMenu(getContext(), rowView.findViewById(R.id.menu));
                    rowView.findViewById(R.id.menu).setOnClickListener(AxoDevicesFragment.this);
                    rowView.findViewById(R.id.menu).setTag(devData);
                    rowView.findViewById(R.id.menu).setVisibility(View.VISIBLE);
                    devData.mExtendedMenu.inflate(R.menu.local_device_extended);
                }
                devData.mExtendedMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return onOptionsItemSelected(item);
                    }
                });
            }
            tv = (TextView)rowView.findViewById(R.id.dev_id);
            tv.setText(devData.devId);
            return rowView;
        }
    }

    private void selectExtendedMenu(DeviceData devData) {
        if (devData.mExtendedMenu == null)
            return;
        devData.mExtendedMenu.show();
        mSelectedDevData = devData;
    }

    // Parse the device info string and update the matching device data structure
    // identityKey:device name:device id:verify state
    private void parseDeviceInfo(String devData, ArrayList<DeviceData> devDataList) {
        String elements[] = devData.split(":");
        if (elements.length != 4) {
            return;
        }
        for (DeviceData data : devDataList) {
            if (elements[2].equals(data.devId))
                data.zrtpVerificationState = elements[3];
        }
    }

    private void getSiblingDeviceInfo(ArrayList<DeviceData> devData) {
        byte[][] devices = AxoMessaging.getIdentityKeys(IOUtils.encode(AxoMessaging.getInstance(mParent).getUserName()));
        if (devices.length == 0) {
            return;
        }
        for (byte[] device : devices) {
            parseDeviceInfo(new String(device), devData);
        }
    }

    private class DevicesInBackground extends AsyncTask<Void, Void, Integer> {
        byte[] devices;
        String json;
        Context mCtx;

        DevicesInBackground(Context ctx) {
            mCtx = ctx;
        }

        @Override
        protected Integer doInBackground(Void... commands) {
            mDeviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(mCtx, false));

            long startTime = System.currentTimeMillis();
            AxoMessaging axo = AxoMessaging.getInstance(mCtx);
            devices = AxoMessaging.getAxoDevicesUser(IOUtils.encode(axo.getUserName()));
            if (devices != null) {
                json = new String(devices);
            }
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for getAxoDevicesUser: " + time);
            if (TextUtils.isEmpty(json))
                emptyDeviceInfo();
            else {
                ArrayList<DeviceData> devData = parseDeviceInfoJson(json);
                if (devData != null)
                    setupDeviceList(devData);
                else
                    emptyDeviceInfo();
            }
        }
    }

    private class AxoDeleteInBackground extends AsyncTask<byte[], Void, Integer> {

        private int[] mCode = new int[1];

        @Override
        protected Integer doInBackground(byte[]... devIds) {
            long startTime = System.currentTimeMillis();
            AxoMessaging.removeAxolotlDevice(devIds[0], mCode);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Delete device result: " + mCode[0]);
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for delete: " + time);
            if (mCode[0] == 200)
                getDevicesInfo();
            else {
                mProgressBar.setVisibility(View.INVISIBLE);
                mDevicesList.setVisibility(View.VISIBLE);
            }
        }
    }
}
