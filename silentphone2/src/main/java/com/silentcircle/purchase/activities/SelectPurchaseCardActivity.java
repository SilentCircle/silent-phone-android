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

package com.silentcircle.purchase.activities;

import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.silentcircle.contacts.utils.AsyncTaskExecutor;
import com.silentcircle.contacts.utils.AsyncTaskExecutors;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.purchase.data.StripeToken;
import com.silentcircle.purchase.dialogs.PurchaseDialogFragment;
import com.silentcircle.purchase.fragments.PaymentFormFragment;
import com.silentcircle.purchase.interfaces.PaymentForm;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class SelectPurchaseCardActivity extends AppCompatActivity implements View.OnClickListener,
        AdapterView.OnItemSelectedListener {

    public static final String TAG = "SelectPurchaseCardAct";
    public static final int PURCHASE_REQUEST_CODE = 7777;
    public static final String PURCHASE_SUCCESSFUL = "purchase_successfule";

    public static final String GET_TOKEN_LIST_TASK = "get_token_list_task";
    public static final String GET_PUBLISHABLE_KEY_TASK = "get_publishable_key_task";
    public static final String SEND_STRIPE_TOKEN_TASK = "send_stripe_token_task";
    public static final String PROCESS_CHARGE_TASK = "process_charge_task";

    public static final String CARD_LIST = "card_list";
    public static final String STRIPE_TOKEN_LIST = "stripe_token_list";
    public static final String PURCHASE_AMOUNT = "purchase_amount";

    private Spinner mCardSpinner;
    private Button mPurchaseBtn;
    private ArrayAdapter<String> mCardSpinnerAdapter;
    private ArrayList<String> mCards = new ArrayList<>();
    private List<StripeToken> mTokenList = new ArrayList<>();
    private String mAmount, mDigits, mSelectedCardId;

    private PurchaseDialogFragment mProgressFragment;
    private PurchaseDialogFragment mErrorDialogFragment;

    private PaymentFormFragment mPaymentFormFragment;
    private AsyncTaskExecutor mAsyncTaskExecutor;

    private Card mCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_card);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setTitle(getString(R.string.in_app_purchase));

        // Handle rotation
        if(savedInstanceState != null){
            mAmount = savedInstanceState.getString(PURCHASE_AMOUNT);
            mCards = savedInstanceState.getStringArrayList(CARD_LIST);
            mTokenList = savedInstanceState.getParcelableArrayList(STRIPE_TOKEN_LIST);
        }
        else{
            mAmount = getIntent().getStringExtra(PurchaseDialogFragment.PURCHASE_AMOUNT);
            if(TextUtils.isEmpty(mAmount)){
                if(ConfigurationUtilities.mTrace){
                    Log.e(TAG, "Should not be here. We must have purchase amount to get here.");
                }
            }
        }
        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();

        initView();

    }

    private void initView(){
        mCardSpinner = (Spinner) findViewById(R.id.card_spinner_id);
        mCardSpinner.setOnItemSelectedListener(this);

        if(mCards.size() == 0) {
            mAsyncTaskExecutor.submit(GET_TOKEN_LIST_TASK, new HandlePaymentTask(), GET_TOKEN_LIST_TASK);
            mCards.add(getString(R.string.new_card));
        }

        mCardSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mCards);
        mCardSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCardSpinner.setAdapter(mCardSpinnerAdapter);

        mPurchaseBtn = (Button) findViewById(R.id.purchase_btn_id);
        mPurchaseBtn.setOnClickListener(this);

    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString(PURCHASE_AMOUNT, mAmount);
        savedInstanceState.putStringArrayList(CARD_LIST, mCards);
        savedInstanceState.putParcelableArrayList(STRIPE_TOKEN_LIST, (ArrayList<? extends Parcelable>) mTokenList);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        //TODO: onRestoreInstanceState() called after onCreate()
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.purchase_btn_id:
                mDigits = mCardSpinner.getSelectedItem().toString().trim();
                if(mDigits.contains(":"))
                {
                    String message = getString(R.string.confirm_payment_message, mAmount, mDigits.split(":")[1]);

                    PurchaseDialogFragment fragment = PurchaseDialogFragment.newInstance(PurchaseDialogFragment.PURCHASE_CONFIRM_DIALOG, message);
                    fragment.show(getSupportFragmentManager(), PurchaseDialogFragment.PURCHASE_INPUT_DIALOG);
                }
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(parent.getId() == R.id.card_spinner_id){
            mDigits = parent.getSelectedItem().toString().trim();
            if(mTokenList.size() > 0) {
                mSelectedCardId = mTokenList.get(position).getToken();
                mDigits = mTokenList.get(position).getDigits();
            }
            if(mDigits.equals(getString(R.string.new_card))) {
                showPaymentFormFragment();
            }
            else{
                removePaymentFormFragment();
            }
        }
    }
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }
    private void showPaymentFormFragment(){
        mPurchaseBtn.setVisibility(View.GONE);
        mPaymentFormFragment= (PaymentFormFragment)getFragmentManager().findFragmentByTag(PaymentFormFragment.TAG);
        if (mPaymentFormFragment == null) {
            mPaymentFormFragment = new PaymentFormFragment();
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.payment_form_container_id, mPaymentFormFragment, PaymentFormFragment.TAG);
        transaction.addToBackStack(PaymentFormFragment.TAG);
        transaction.commit();

        showKeyboard();
    }
    private void removePaymentFormFragment(){
        mPurchaseBtn.setVisibility(View.VISIBLE);
        mPaymentFormFragment= (PaymentFormFragment)getFragmentManager().findFragmentByTag(PaymentFormFragment.TAG);
        if(mPaymentFormFragment != null){
            mPaymentFormFragment.clearForm();;
        }
        for(int i = 0; i < getFragmentManager().getBackStackEntryCount(); i++) {
            getFragmentManager().popBackStack();
        }
        removeKeyboard();
    }
    /**
     *
     * @param resId is the message shown in progress
     */
    private void startProgress(int resId) {
        lockOrientation();
        if(mProgressFragment == null) {
            mProgressFragment = PurchaseDialogFragment.newInstance(PurchaseDialogFragment.PURCHASE_PROGRESS_DIALOG, null);
        }
        mProgressFragment.setMessage(getString(resId));
        mProgressFragment.show(getSupportFragmentManager(), PurchaseDialogFragment.PURCHASE_PROGRESS_DIALOG);
    }

    private void finishProgress() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if(mProgressFragment != null) {
            mProgressFragment.dismiss();
        }
    }

    // Disable rotation when AsyncTask doingBackground
    private void lockOrientation() {
        Display display = getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        int height;
        int width;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
            height = display.getHeight();
            width = display.getWidth();
        } else {
            Point size = new Point();
            display.getSize(size);
            height = size.y;
            width = size.x;
        }
        switch (rotation) {
            case Surface.ROTATION_90:
                if (width > height) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                break;
            case Surface.ROTATION_180:
                if (height > width) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                break;
            case Surface.ROTATION_270:
                if (width > height) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                break;
            default :
                if (height > width) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
        }
    }

    /**
     *
     * @param token is from Stripe server once card registration successfully. we need to send token to the server
     */
    private void handleStripeToken(Token token){
        mPaymentFormFragment = (PaymentFormFragment)getFragmentManager().findFragmentByTag(PaymentFormFragment.TAG);
        if(mPaymentFormFragment == null){
            Log.e(TAG, "PaymentFormFragment must not be null. Something is wrong");
            return;
        }
        mPaymentFormFragment.clearForm();
        mAsyncTaskExecutor.submit(SEND_STRIPE_TOKEN_TASK, new HandlePaymentTask(), SEND_STRIPE_TOKEN_TASK, token.getId());
    }

    /**
     * @param json is result back from the server once the server stores the card successfully
     *
     * json : {"result": "success", "card": {"last4": "4242", "id": "card_7RpmfNVz51SsL7"}}
     */
    private void updateCards(String json) {
        try{
            JSONObject object = new JSONObject(json);
            if(json.contains("card")) {
                JSONObject obj = object.getJSONObject("card");
                if(obj != null && obj.toString().contains("last4") && obj.toString().contains("id")){
                    StripeToken t = new StripeToken();
                    t.setToken(obj.getString("id"));
                    t.setDigits(obj.getString("last4"));
                    mTokenList.add(0, t);
                    mCards.add(0, getString(R.string.card_ending_with) + t.getDigits());
                    mCardSpinnerAdapter.notifyDataSetChanged();
                    mCardSpinner.setSelection(0);
                }
                else{
                    if(ConfigurationUtilities.mTrace) {
                        Log.e(TAG, "Something goes wrong to get here: Response has to have last4 and id");
                    }
                }
            }
        }catch(JSONException e){
            Log.e(TAG, "JSONException : "+e.toString());
        }
    }
    private void showKeyboard(){
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    private void removeKeyboard(){
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     *
     * @param error : it could be any error message to show in dialog to let the use see
     */
    public void handleError(String error) {
        mPaymentFormFragment = (PaymentFormFragment)getFragmentManager().findFragmentByTag(PaymentFormFragment.TAG);
        if(mPaymentFormFragment != null){
            mPaymentFormFragment.clearError();
        }

        if(mErrorDialogFragment == null) {
            mErrorDialogFragment = PurchaseDialogFragment.newInstance(PurchaseDialogFragment.PURCHASE_ERROR_DIALOG, error);
        }
        mErrorDialogFragment.setMessage(error);
        mErrorDialogFragment.show(getSupportFragmentManager(), PurchaseDialogFragment.PURCHASE_ERROR_DIALOG);

        finishProgress();

    }

    private void inlineError(String error, int resId){
        mPaymentFormFragment = (PaymentFormFragment)getFragmentManager().findFragmentByTag(PaymentFormFragment.TAG);
        if(mPaymentFormFragment != null){
            mPaymentFormFragment.handleError(error, resId);
        }

    }

    /**
     * @param json : response of client request list of card from the server
     */
    private void setCardSpinner(String json){
        int defaultPaymentPosition = 0;
        try {
            JSONObject object = new JSONObject(json);
            JSONArray ary = object.getJSONArray("cards");
            mCards.clear();
            mTokenList.clear();
            StripeToken t;
            for(int i=0; i<ary.length(); i++){
                t = new StripeToken();
                JSONObject obj = ary.getJSONObject(i);
                if(obj.toString().contains("default") && obj.getBoolean("default")){
                    defaultPaymentPosition = i;
                    mSelectedCardId = obj.getString("id");
                }
                t.setToken(obj.getString("id"));
                t.setDigits(obj.getString("last4"));
                mTokenList.add(t);
                mCards.add(i, getString(R.string.card_ending_with)+obj.getString("last4"));
            }
            t = new StripeToken();
            t.setDigits(getString(R.string.new_card));
            mTokenList.add(t);
            mCards.add(getString(R.string.new_card));

            mCardSpinnerAdapter.notifyDataSetChanged();
            mCardSpinner.setSelection(defaultPaymentPosition);
            if(!mCards.get(defaultPaymentPosition).equals(getString(R.string.new_card))){
                mPaymentFormFragment = (PaymentFormFragment)getFragmentManager().findFragmentByTag(PaymentFormFragment.TAG);
                if(mPaymentFormFragment != null){
                    removePaymentFormFragment();
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSONException: "+e.getMessage());

        }
    }

    /**
     * @param form : payment form has required info to make registration on Stripe Server
     */
    public void saveCreditCard(PaymentForm form) {
        mCard = new Card(
                form.getCardNumber(),
                form.getExpMonth(),
                form.getExpYear(),
                form.getCvc());
        boolean validation = mCard.validateCard();
        if(validation && !TextUtils.isEmpty(form.getCvc())) {
            startProgress(R.string.creating_token);
            mAsyncTaskExecutor.submit(GET_PUBLISHABLE_KEY_TASK, new HandlePaymentTask(), GET_PUBLISHABLE_KEY_TASK);
        }
        else if (!mCard.validateNumber()) {
            inlineError(getString(R.string.invalid_card_number), R.id.card_number_til_id);
        } else if (!mCard.validateExpiryDate()) {
            handleError(getString(R.string.invalid_expiration_date));
        } else if (!mCard.validateCVC()) {
            inlineError(getString(R.string.invalid_card_number), R.id.cvc_til_id);
        } else {
            handleError(getString(R.string.invalid_card_info));
        }
    }

    /**
     *
     * @param publishablekey : retrieved from the server, it's initially provided by Stripe
     *
     * Register credit card info to Stripe to get registerd and get
     * back a Stripe token which will be sotred on file for later
     * processing real charge.
     */
    public void CreateStripeToken(String publishablekey){
        new Stripe().createToken(
                mCard,
                publishablekey,
                new TokenCallback() {
                    public void onSuccess(Token token) {
                        if (ConfigurationUtilities.mTrace) {
                            Log.i(TAG, "stripe token string : " + com.stripe.model.Token.GSON.toJson(token));
                        }
                        handleStripeToken(token);
                    }

                    public void onError(Exception error) {
                        handleError(error.getLocalizedMessage());
                    }
                });
    }

    public void processCharge(){
        mAsyncTaskExecutor.submit(PROCESS_CHARGE_TASK, new HandlePaymentTask(), PROCESS_CHARGE_TASK, mAmount);
    }

    private void chargeSuccessful(){
        Toast toast = Toast.makeText(SelectPurchaseCardActivity.this, R.string.payment_successful, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        // When we're ready to show payment history, we'll go to that
        // screen next; for now we go back to the dialer.
        Intent intent = new Intent();
        intent.putExtra(PURCHASE_SUCCESSFUL, true);
        setResult(RESULT_OK, intent);
        finish();
    }

// AsyncTask handles communications to the server
class HandlePaymentTask extends AsyncTask<String, Void, String> {
        private String mTask = null;
        private String mData= null;
        @Override
        protected String doInBackground(String... params) {
            URL requestUrl = null;
            mTask = params[0].trim();

            try{
                // Get the correct URL for the request
                if(mTask.equals(GET_PUBLISHABLE_KEY_TASK)) {
                    requestUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(SelectPurchaseCardActivity.this) + ConfigurationUtilities.getPublishableKeyUrl(SelectPurchaseCardActivity.this));
                }
                else {
                    // The URL for GET_TOKEN_LIST_TASK and
                    // SEND_STRIPE_TOKEN_TASK are the same; the requests differ
                    // by the HTTP method type.  Both require the API key to be
                    // appended.  The PROCESS_CHARGE_TASK URL is distinct and
                    // we'll append the API key here as well.
                    byte[] data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
                    if (data == null) {
                        Log.w(TAG, "No API key data available");
                        return null;
                    }
                    String devAuthorization = new String(data, "UTF-8");

                    // Show different message on progress
                    if(mTask.equals(GET_TOKEN_LIST_TASK)){
                        startProgress(R.string.getting_cc_info);
                    }
                    else if(mTask.equals(PROCESS_CHARGE_TASK)){
                        startProgress(R.string.processing_payment);
                    }

                    // Get correct url for request
                    if(mTask.equals(PROCESS_CHARGE_TASK)){
                        String url = String.format(ConfigurationUtilities.getProcessChargeUrl(SelectPurchaseCardActivity.this), mSelectedCardId, devAuthorization);
                        requestUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(SelectPurchaseCardActivity.this) + url);
                    }else{
                        requestUrl = new URL(ConfigurationUtilities.getProvisioningBaseUrl(SelectPurchaseCardActivity.this) + ConfigurationUtilities.getStripeTokensUrl(SelectPurchaseCardActivity.this) + "?api_key=" + devAuthorization);

                    }
                }

                if(ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "requestUrl : " + requestUrl);
                }
                // Make HttpsURLConnection
                HttpsURLConnection urlConnection = (HttpsURLConnection)requestUrl.openConnection();
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                urlConnection.setConnectTimeout(2000);

                // SEND_STRIPE_TOKEN_TASK and PROCESS_CHARGE_TASK are "POST" request.
                if(mTask.equals(SEND_STRIPE_TOKEN_TASK) || mTask.equals(PROCESS_CHARGE_TASK)) {
                    if (mTask.equals(SEND_STRIPE_TOKEN_TASK)) {
                        mData = params[1];
                        mData = "{\"stripe_token\":\"" + mData + "\"}";
                    }
                    else if(mTask.equals(PROCESS_CHARGE_TASK)){
                        String amount = params[1].replace("$", "");
                        mData = "{\"amount\": \"" + amount +"\", \"currency\": \"USD\"}";
                    }
                    if(ConfigurationUtilities.mTrace) {
                        Log.d(TAG, "post data : " + mData);
                    }
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setDoInput(true);
                    urlConnection.setDoOutput(true);
                    urlConnection.setRequestProperty("Content-Type", "application/json");
                    OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                    out.write(mData.getBytes());
                    out.flush();
                    out.close();
                }

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code: " + ret);

                if (ret == HttpsURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader( new InputStreamReader(new BufferedInputStream(urlConnection.getInputStream())));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null){
                        builder.append(line);
                    }
                    reader.close();
                    return builder.toString();
                }
                else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(urlConnection.getErrorStream())));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();
                    if (ConfigurationUtilities.mTrace) {
                        Log.e(TAG, "Server Respons is not HTTP_OK (200): " + builder.toString());
                    }
                    try {
                        JSONObject jsonObj = new JSONObject(builder.toString());
                        if(jsonObj.has("error_msg")) {
                            handleError(jsonObj.getString("error_msg"));
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSONException: " + e.getMessage());
                    }
                }
            }catch (MalformedURLException e){
                Log.e(TAG, "MalformedURLException: "+e.getMessage());
            }catch (IOException e){
                Log.e(TAG, "IOException: "+e.getMessage());
            }

            return null;
        }
        protected void onPostExecute(String result) {
            if(ConfigurationUtilities.mTrace){
                Log.d(TAG, "Server response: "+result);
            }
            if(TextUtils.isEmpty(result)){
                finishProgress();
                Log.e(TAG, "Server response code is not "+HttpsURLConnection.HTTP_OK);
                return;
            }
            if(mTask.equals(GET_PUBLISHABLE_KEY_TASK)) {
                try {
                    JSONObject jsonObj = new JSONObject(result.toString());
                    String publishableKey = jsonObj.getString("STRIPE_PUBLISHABLE_KEY");
                    if(ConfigurationUtilities.mTrace) {
                        Log.i(TAG, "Publishable Key  : " + publishableKey);
                    }
                    // publishableKey is required to register credit card on
                    // Stripe server
                    CreateStripeToken(publishableKey);
                }catch (JSONException e) {
                    Log.e(TAG, "JSONException: " + e.getMessage());
                }
            }
            else if(mTask.equals(SEND_STRIPE_TOKEN_TASK)){
                //once credit card registered on Stripe server, remove credit card form
                removePaymentFormFragment();
                updateCards(result);
                finishProgress();
            } else if(mTask.equals(GET_TOKEN_LIST_TASK)){
                setCardSpinner(result);
                finishProgress();
            }
            else if(mTask.equals(PROCESS_CHARGE_TASK)){
                finishProgress();
                chargeSuccessful();
            }
        }
    }
}
