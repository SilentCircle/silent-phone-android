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

package com.silentcircle.silentphone2.fragments;


import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialHelperSelectorActivity;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link DialHelperSelectorFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class DialHelperSelectorFragment extends Fragment implements View.OnClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = DialHelperSelectorFragment.class.getSimpleName();
    private DialHelperSelectorActivity mParent;
    private String mCountry;
    private ListView mCountryView;

    public static DialHelperSelectorFragment newInstance() {
        return new DialHelperSelectorFragment();
    }

    public DialHelperSelectorFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (DialHelperSelectorActivity) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.dial_helper_selector, container, false);
        if (rootView == null)
            return null;

        List<FindDialHelper.CountryData> countryList = new ArrayList<>(FindDialHelper.mCountryList.values());
        FindDialHelper.setupIndex(countryList);
        CountryArrayAdapter countryAdapter = new CountryArrayAdapter(mParent, countryList);

        // Set up the country list view
        mCountryView = (ListView)rootView.findViewById(R.id.countries);
        mCountryView.setFastScrollAlwaysVisible(true);
        mCountryView.setAdapter(countryAdapter);
        mCountryView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                FindDialHelper.CountryData country = (FindDialHelper.CountryData)parent.getItemAtPosition(position);
                if (country != null) {
                    mCountry = country.shortName;
                    int explanationId = FindDialHelper.getHelperExplanation(country);
                    String explanationText = mParent.getString(explanationId, country.national, country.countryCode, country.idd);
                    ((TextView)rootView.findViewById(R.id.DialSelectorExplanation_1)).setText(explanationText);
                }
            }
        });
        rootView.findViewById(R.id.button).setOnClickListener(this);

        FindDialHelper.CountryData country = FindDialHelper.getActiveCountry();
        if (country != null) {
            int i = 0;
            for (FindDialHelper.CountryData cd : countryList) {
                if (cd.shortName.equals(country.shortName)) {
                    mCountryView.setSelection(i);
                    break;
                }
                i++;
            }
        }
        return rootView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button:
                mParent.setCountryHelper(mCountry);
                break;
            default:
                break;
        }
    }

    private class CountryArrayAdapter extends ArrayAdapter<FindDialHelper.CountryData> implements SectionIndexer {
        private final Context context;

        public CountryArrayAdapter(Context context, List<FindDialHelper.CountryData> values) {
            super(context, R.layout.dial_helper_selector_line, values);
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView;
            rowView = inflater.inflate(R.layout.dial_helper_selector_line, parent, false);
            if (rowView == null)
                return null;

            FindDialHelper.CountryData country = getItem(position);
            TextView tv = (TextView)rowView.findViewById(R.id.list_line);
            FindDialHelper.CountryData activeCountry = FindDialHelper.getActiveCountry();
            String shortName = activeCountry == null ? "" : activeCountry.shortName;
            if (mCountry == null && country.shortName.equals(shortName)) {
                mCountryView.setItemChecked(position, true);
            }
            tv.setText(country.fullName);
            tv.setSelected(true);
            return rowView;
        }

        @Override
        public Object[] getSections() {
            return FindDialHelper.mIndexStrings;
        }

        @Override
        public int getPositionForSection(int sectionIndex) {
            return FindDialHelper.getPositionForSection(sectionIndex);
        }

        @Override
        public int getSectionForPosition(int position) {
            return FindDialHelper.getSectionForPosition(position);
        }
    }
}
