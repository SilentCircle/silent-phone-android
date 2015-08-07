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
package com.silentcircle.silentphone2.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DidSelectionActivity;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Constants;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Fragment that handles the DID selection UI.
 */
public class DidSelectionFragment extends Fragment implements AdapterView.OnItemClickListener,
        View.OnClickListener {

    private static final String TAG = "DidSelectionFragment";

    private DidSelectionActivity mParent;
    private String mDevAuthorization;
    private ActionBar mActionBar;
    private View mRootView;

    private String[] mRegions;
    private String[] mAreas;

    private ListView mListView;

    private TextView mRegionText;
    private TextView mAreaText;
    private TextView mNumberText;
    private TextView mListTitle;

    private Button mOkButton;

    private ArrayAdapter<String> mRegionAdapter;
    private ArrayAdapter<String> mAreaAdapter;

    private enum State {RegionSelect, AreaSelect, NumberSelect}

    private State mState;
    private Fragment mFragment;


    public static DidSelectionFragment newInstance(String regions, String apiKey) {
        DidSelectionFragment f = new DidSelectionFragment();

        Bundle args = new Bundle();
        args.putString(DidSelectionActivity.REGIONS, regions);
        args.putString(DidSelectionActivity.API_KEY, apiKey);
        f.setArguments(args);
        return f;
    }

    public DidSelectionFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFragment = this;

        Bundle args = getArguments();
        if (args == null) {
            mParent.finish();
            return;
        }
        mDevAuthorization = args.getString(DidSelectionActivity.API_KEY);
        setHasOptionsMenu(true);
        try {
            JSONObject jsonObj = new JSONObject(args.getString(DidSelectionActivity.REGIONS));
            JSONArray jsonArray = jsonObj.getJSONArray("regions");
            if (jsonArray.length() < 1) {
                mParent.finish();
                return;
            }
            mRegions = new String[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                mRegions[i] = jsonArray.getString(i);
            }
        } catch (JSONException e) {
            Log.w(TAG, "JSON exception reading regions: " + e);
            mParent.finish();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.did_selection_fragment, container, false);
        mListView = (ListView) mRootView.findViewById(R.id.listView);
        mListView.setOnItemClickListener(this);

        mRegionText = (TextView) mRootView.findViewById(R.id.did_selected_region);
        mAreaText = (TextView) mRootView.findViewById(R.id.did_selected_area);
        mNumberText = (TextView) mRootView.findViewById(R.id.did_selected_number);
        mListTitle = (TextView) mRootView.findViewById(R.id.did_select_list_title);

        mOkButton = (Button) mRootView.findViewById(R.id.did_ok_button);
        mOkButton.setOnClickListener(this);

        mState = State.RegionSelect;
        if (mRegions.length > 1) {
            TextView explanation = (TextView) mRootView.findViewById(R.id.did_explanation);
            explanation.setText(R.string.did_region_area_explanation);
            explanation.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.VISIBLE);
            mListTitle.setText(getString(R.string.did_region_list));
            mListTitle.setVisibility(View.VISIBLE);
            mRegionAdapter = new ArrayAdapter<>(mParent, android.R.layout.simple_list_item_1, mRegions);
            mListView.setAdapter(mRegionAdapter);
            mActionBar.setTitle(R.string.did_region_list);
        }
        else
            processRegion(mRegions[0]);
        return mRootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (DidSelectionActivity) activity;
        mActionBar = mParent.getSupportActionBar();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
        mActionBar = null;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleHomeClick();
                return true;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        TextView tv = (TextView) v;
//            Log.d(TAG, "+++ selected : " + tv.getText() + ", at state: " + mState);

        switch (mState) {
            case RegionSelect:
                processRegion(tv.getText().toString());
                break;
            case AreaSelect:
                processArea(tv.getText().toString());
                break;
            case NumberSelect:
                processNumber(tv.getText().toString());
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.did_ok_button:
                setNumber();
                break;
            default:
                break;
        }
    }

    private void handleHomeClick() {
        switch (mState) {
            case RegionSelect:
                break;

            case AreaSelect:
                if (mRegions.length > 1) {
                    stepBackToRegionSelect();
                }
                break;

            case NumberSelect:
                if (mAreas.length > 1) {
                    stepBackToAreaSelect();
                }
                else if (mRegions.length > 1) {
                    stepBackToRegionSelect();
                }
                break;
        }
    }

    private void processRegion(String region) {
        mRegionText.setText(region);
        mRegionText.setVisibility(View.VISIBLE);
        mRootView.findViewById(R.id.did_select).setVisibility(View.VISIBLE);
        mRootView.findViewById(R.id.did_select_region).setVisibility(View.VISIBLE);
        URL areaUrl;
        try {
            areaUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mParent.getBaseContext()) +
                    ConfigurationUtilities.getDidSelectionBase(mParent.getBaseContext()) +
                    region + "/?api_key=" +
                    Uri.encode(mDevAuthorization));
//                Log.d(TAG, "++++ Get available area URL: " + areaUrl);
            LoaderTask loaderTask = new LoaderTask();
            loaderTask.execute(areaUrl);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private void processArea(String area) {
        mAreaText.setText(area);
        mAreaText.setVisibility(View.VISIBLE);
        mRootView.findViewById(R.id.did_select_area).setVisibility(View.VISIBLE);
        URL didUrl;
        try {
            didUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mParent.getBaseContext()) +
                    ConfigurationUtilities.getDidSelectionBase(mParent.getBaseContext()) +
                    mRegionText.getText() + "/" + area + "/?api_key=" +
                    Uri.encode(mDevAuthorization));
//                Log.d(TAG, "++++ Get available DID URL: " + didUrl);
            LoaderTask loaderTask = new LoaderTask();
            loaderTask.execute(didUrl);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private void processNumber(String number) {
        mNumberText.setText(number);
        mNumberText.setVisibility(View.VISIBLE);
        mRootView.findViewById(R.id.did_select_number).setVisibility(View.VISIBLE);
        mOkButton.setVisibility(View.VISIBLE);
    }

    private void stepBackToRegionSelect() {
        mState = State.RegionSelect;
        hideSelectionInfo();
        mActionBar.setTitle(R.string.did_region_list);
        mActionBar.setDisplayHomeAsUpEnabled(false);
        mListView.setAdapter(mRegionAdapter);
        mListTitle.setText(getString(R.string.did_region_list));
        mRegionAdapter.notifyDataSetChanged();
    }

    private void stepBackToAreaSelect() {
        mState = State.AreaSelect;
        hideSelectionInfo();
        mActionBar.setTitle(R.string.did_area_list);
        mListView.setAdapter(mAreaAdapter);
        mListTitle.setText(getString(R.string.did_area_list));
        mAreaAdapter.notifyDataSetChanged();
    }

    private void hideSelectionInfo() {
        mRootView.findViewById(R.id.did_select).setVisibility(View.INVISIBLE);

        mRegionText.setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.did_select_region).setVisibility(View.INVISIBLE);

        mAreaText.setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.did_select_area).setVisibility(View.INVISIBLE);

        mNumberText.setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.did_select_number).setVisibility(View.INVISIBLE);

        mOkButton.setVisibility(View.INVISIBLE);
    }

    private void setNumber() {
        URL didUrl;
        try {
            didUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(mParent.getBaseContext()) +
                    ConfigurationUtilities.getSetDidBase(mParent.getBaseContext()) +
                    mNumberText.getText() + "/?api_key=" +
                    Uri.encode(mDevAuthorization));
//                Log.d(TAG, "++++ Set DID URL: " + didUrl);
            LoaderTask loaderTask = new LoaderTask();
            loaderTask.execute(didUrl);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private void parseAndShowArea(StringBuilder content) {
        try {
            JSONObject jsonObj = new JSONObject(content.toString());
            JSONArray jsonArray = jsonObj.getJSONArray("areas");
            if (jsonArray.length() < 1) {
                showInputInfo(getString(R.string.did_no_area));
                return;
            }
            mAreas = new String[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                mAreas[i] = jsonArray.getString(i);
            }
            mState = State.AreaSelect;
            if (mAreas.length == 1) {
                processArea(mAreas[0]);
                return;
            }
            mAreaAdapter = new ArrayAdapter<>(mParent, android.R.layout.simple_list_item_1, mAreas);
            mListView.setAdapter(mAreaAdapter);
            mListView.setVisibility(View.VISIBLE);
            mAreaAdapter.notifyDataSetChanged();
            mListTitle.setText(getString(R.string.did_area_list));
            mListTitle.setVisibility(View.VISIBLE);
            mActionBar.setTitle(R.string.did_area_list);
            mActionBar.setDisplayHomeAsUpEnabled(true);
        } catch (JSONException e) {
            Log.w(TAG, "JSON exception reading areas: " + e);
        }
    }

    private void parseAndShowNumber(StringBuilder content) {
        try {
            JSONObject jsonObj = new JSONObject(content.toString());
            JSONArray jsonArray = jsonObj.getJSONArray("numbers");
            if (jsonArray.length() < 1) {
                showInputInfo(getString(R.string.did_no_number));
                return;
            }
            String[] numbers = new String[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                numbers[i] = jsonArray.getString(i);
            }
            mState = State.NumberSelect;
            if (numbers.length == 1) {
                processNumber(numbers[0]);
                return;
            }
            ArrayAdapter<String> numberAdapter = new ArrayAdapter<>(mParent, android.R.layout.simple_list_item_1, numbers);
            mListView.setAdapter(numberAdapter);
            mListView.setVisibility(View.VISIBLE);
            numberAdapter.notifyDataSetChanged();
            mListTitle.setText(getString(R.string.did_number_list));
            mListTitle.setVisibility(View.VISIBLE);
            mActionBar.setTitle(R.string.did_number_list);
            mActionBar.setDisplayHomeAsUpEnabled(true);
        } catch (JSONException e) {
            Log.w(TAG, "JSON exception reading numbers: " + e);
        }
    }

    private void showInputInfo(String msg) {
        ProvisioningActivity.InfoMsgDialogFragment infoMsg = ProvisioningActivity.InfoMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null)            // may happen if underlying Activity is finished?
            return;
        infoMsg.show(fragmentManager, "SilentPhoneDidInfo");
    }

    private String parseAndShowError(StringBuilder content) {
        String msg;
        try {
            JSONObject jsonObj = new JSONObject(content.toString());
            msg = jsonObj.getString("error_msg");
        } catch (JSONException e) {
            Log.w(TAG, "JSON exception reading error message: " + e);
            msg = getString(R.string.did_selection_wrong_format) + e.getMessage();
        }
        return msg;
    }

    private void showDialog(String title, String msg, int positiveBtnLabel, int nagetiveBtnLabel) {
        com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment infoMsg = com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment.newInstance(title, msg, positiveBtnLabel, nagetiveBtnLabel);
        FragmentManager fragmentManager = mFragment.getFragmentManager();
        infoMsg.show(fragmentManager,TAG );
    }

    private class LoaderTask extends AsyncTask<URL, Integer, Integer> {

        private StringBuilder content = new StringBuilder();

        LoaderTask() {
        }

        @Override
        protected Integer doInBackground(URL... params) {
            HttpsURLConnection urlConnection = null;
//            if (DidSelectionActivity.testing) {
//                switch (mState) {
//                    case RegionSelect:
//                        content.append(DidSelectionActivity.TEST_RUN[1]);
//                        break;
//                    case AreaSelect:
//                        content.append(DidSelectionActivity.TEST_RUN[2]);
//                        break;
//                    case NumberSelect:
////                            Log.d(TAG, "+++++ Set DID: " + params[0]);
//                        break;
//                }
//                return HttpsURLConnection.HTTP_OK;
//            }
            try {
                urlConnection = (HttpsURLConnection)params[0].openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestMethod(mState == State.NumberSelect ? "PUT" : "GET");
                urlConnection.setDoInput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code DID loader: " + ret + ", (" + mState + ")");
                if (ret == HttpsURLConnection.HTTP_OK) {
                    ProvisioningActivity.readStream(new BufferedInputStream(urlConnection.getInputStream()), content);
                }
                else {
                    ProvisioningActivity.readStream(new BufferedInputStream(urlConnection.getErrorStream()), content);
                }
                return ret;

            } catch (IOException e) {
                if(!Utilities.isNetworkConnected(mFragment.getActivity())){
                    return Constants.NO_NETWORK_CONNECTION;
                }
                showInputInfo(getString(R.string.provisioning_no_network) + e.getLocalizedMessage());
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } catch (Exception e) {
                showInputInfo(getString(R.string.provisioning_error) + e.getLocalizedMessage());
                Log.e(TAG, "Network connection problem: " + e.getMessage());
                return -1;
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                switch (mState) {
                    case RegionSelect:
                        parseAndShowArea(content);
                        break;
                    case AreaSelect:
                        parseAndShowNumber(content);
                        break;
                    case NumberSelect:
                        mParent.setResult(Activity.RESULT_OK);
                        mParent.finish();
                        break;
                }
            }
            else if(result == Constants.NO_NETWORK_CONNECTION) {
                showDialog(mFragment.getActivity().getString(R.string.information_dialog), mFragment.getActivity().getString(R.string.connected_to_network), android.R.string.ok, -1);
            }
            else {
                showInputInfo(parseAndShowError(content));
            }
        }
    }
}

