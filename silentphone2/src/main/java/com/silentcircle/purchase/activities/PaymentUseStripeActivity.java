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
package com.silentcircle.purchase.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;

import com.silentcircle.purchase.dialogs.PurchaseDialogFragment;
import com.silentcircle.silentphone2.R;

public class PaymentUseStripeActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "PaymentUseStripActivity";
    public static final String CHECKED_RADIO_ID = "checked_radio_id";

    private RadioGroup mRadioGroup;
    private Button mSelectBtn;
    private String mAmount;

    private int[] mRadioResIds = {R.id.radio_1_id, R.id.radio_2_id, R.id.radio_3_id, R.id.radio_4_id};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_use_stripe);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        mSelectBtn = (Button) findViewById(R.id.select_btn_id);
        mSelectBtn.setOnClickListener(this);

        mRadioGroup = (RadioGroup) findViewById(R.id.radio_group_id);

        int checkedRadioIndex = 0;
        for (int i = 0; i < mRadioResIds.length; i++){
            AppCompatRadioButton radioButton = ((AppCompatRadioButton)(findViewById(mRadioResIds[i])));
            radioButton.setOnClickListener(this);
            // Handle device rotation
            if (savedInstanceState != null){
                if (savedInstanceState.getInt(CHECKED_RADIO_ID) == mRadioResIds[i]){
                    checkedRadioIndex = i;
                }
            }
        }
        setCheckedRadioButton(checkedRadioIndex);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.radio_1_id:
            case R.id.radio_2_id:
            case R.id.radio_3_id:
                mAmount = ((AppCompatRadioButton) findViewById(v.getId())).getText().toString();
                break;
            case R.id.radio_4_id:
                // Bring up dialog to let user provide input
                PurchaseDialogFragment fragment = PurchaseDialogFragment.newInstance(PurchaseDialogFragment.PURCHASE_INPUT_DIALOG, null);
                fragment.show(getSupportFragmentManager(), PurchaseDialogFragment.PURCHASE_INPUT_DIALOG);
                break;
            case R.id.select_btn_id:
                if (TextUtils.isEmpty(mAmount)) {
                    mAmount = ((AppCompatRadioButton) findViewById(mRadioResIds[0])).getText().toString();
                }
                Intent intent = new Intent(this, SelectPurchaseCardActivity.class);
                intent.putExtra(PurchaseDialogFragment.PURCHASE_AMOUNT, mAmount);
                startActivityForResult(intent, SelectPurchaseCardActivity.PURCHASE_REQUEST_CODE);
                break;
            default:
                break;
        }
    }

    public void setCheckedRadioButton(int index) {
        AppCompatRadioButton radioButton = (AppCompatRadioButton)(findViewById(mRadioResIds[index]));
        radioButton.setChecked(true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SelectPurchaseCardActivity.PURCHASE_REQUEST_CODE && resultCode == Activity.RESULT_OK){
            // If payment is successful, purchase_successful = true,
            // pass back to finish in-app purchase activity and back
            // to dialer
            if (data != null && data.getBooleanExtra(SelectPurchaseCardActivity.PURCHASE_SUCCESSFUL, false)){
                finish();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(CHECKED_RADIO_ID, mRadioGroup.getCheckedRadioButtonId());
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // TODO: onRestoreInstanceState() called after onCreate()
    }
}
