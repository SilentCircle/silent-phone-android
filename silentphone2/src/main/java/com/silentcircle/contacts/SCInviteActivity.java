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
package com.silentcircle.contacts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.widget.Toast;
import java.util.ArrayList;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;

public class SCInviteActivity extends Activity {
    final int REQUEST_PHONE_CODE = 0;
    final int REQUEST_EMAIL_CODE = 1;
    public static final String INVITE_PHONE_NUMBER = "invite_phone_number";
    String contactId = null;
    String contactName = null;
    String userName = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            if (TextUtils.isEmpty(userName))
                userName = TiviPhoneService.getInfo(0, -1, "cfg.un");
        } catch (Exception ex) {
            /* ignore */
        }

        // did we get passed a phone number ?
        String phoneNumber = getIntent().getStringExtra(INVITE_PHONE_NUMBER);
        if (!TextUtils.isEmpty(phoneNumber)) {
            openSendInviteSMS(phoneNumber);
            return;
        }

        // did we get passed a contact uri ?
        Uri data = getIntent().getData();
        if (data != null) {
            Cursor cursor = getContentResolver().query(data, null, null, null, null);
            if (cursor.moveToFirst()) {
                contactId = cursor.getString(cursor.getColumnIndex("contact_id"));
                contactName = cursor.getString(cursor.getColumnIndex("display_name"));
            }
            cursor.close();
        }

        // if we have no contact let the user pick one
        if (TextUtils.isEmpty(contactId)) {
            DialogInterface.OnClickListener selectTypeListener = new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    pickContact(which);
                    dialog.dismiss();
                }
            };
            final ArrayList<String> array = new ArrayList<>();
            array.add("Send invite via SMS");
            array.add("Send invite via Email");

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Please Select");
            builder.setItems(array.toArray(new String[array.size()]), selectTypeListener);
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    // if user backs out of dialog without selection just finish
                    finish();
                }
            });
            builder.show();
        } else {
            // pick a phone number or email from this contactId
            pickContact(contactId);
        }
    }

    // pick either phone number contacts or email contacts
    // unfortunately the default mechanism does not let you show both
    private void pickContact(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI); // ContactsContract.Contacts.CONTENT_URI);

        switch(requestCode) {
            case REQUEST_PHONE_CODE:
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                break;
            case REQUEST_EMAIL_CODE:
                intent.setType(ContactsContract.CommonDataKinds.Email.CONTENT_TYPE);
                break;
            default:
                break;
        }
        startActivityForResult(intent, requestCode);
    }

    // user will have picked a phone number or email address from his contact list
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_PHONE_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the URI and query the content provider for the phone number
                    Uri contactUri = data.getData();
                    String[] projection = new String[]{
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.Contacts.DISPLAY_NAME
                    };
                    Cursor cursor = getContentResolver().query(contactUri, projection,
                            null, null, null);
                    // If the cursor returned is valid, get the phone number
                    if (cursor.moveToFirst()) {
                        String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                        // send invite via sms
                        openSendInviteSMS(phoneNumber);
                    }
                    cursor.close();
                } else { finish(); }
                break;
            case REQUEST_EMAIL_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the URI and query the content provider for the phone number
                    Uri contactUri = data.getData();
                    String[] projection = new String[]{
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.Contacts.DISPLAY_NAME
                    };
                    Cursor cursor = getContentResolver().query(contactUri, projection,
                            null, null, null);
                    // If the cursor returned is valid, get the phone number
                    if (cursor.moveToFirst()) {
                        String email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                        // send invite via email
                        sendInviteEmail(email);
                    }
                    cursor.close();
                } else { finish(); }
                break;
            default:
                finish();
                break;
        }
    }

    private void pickContact(String contact_id) {
        final ArrayList<String> arrayPhoneEmail = new ArrayList<>();
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String selection = ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " = ?";
            String[] selectionArgs = new String[]{contact_id};
            String[] projection = new String[]{
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
            };

            Cursor curPhone = getContentResolver().query(uri, projection, selection, selectionArgs, null);

            int indexNumber = curPhone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int indexName = curPhone.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            String number;

            if (curPhone.moveToFirst()) {
                contactName = curPhone.getString(indexName);
                for (int b = 0; b < curPhone.getCount(); b++) {
                    number = curPhone.getString(indexNumber);
                    arrayPhoneEmail.add(number);
                }
            }
            curPhone.close();
        } catch (Exception ex) {
            // ignore
        }

        try {
            Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
            String selection = ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " = ?";
            String[] selectionArgs = new String[]{contact_id};
            String[] projection = new String[]{
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Email.ADDRESS};

            Cursor curEmail = getContentResolver().query(uri, projection, selection, selectionArgs, null);
            int indexAddress = curEmail.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);
            int indexName = curEmail.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);

            String email;
            if (curEmail.moveToFirst()) {
                contactName = curEmail.getString(indexName);
                for (int b = 0; b < curEmail.getCount(); b++) {
                    email = curEmail.getString(indexAddress);
                    arrayPhoneEmail.add(email);
                }
            }
            curEmail.close();
        } catch (Exception ex) {
            // ignore
        }

        DialogInterface.OnClickListener selectItemListener = new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                if (isValidEmail(arrayPhoneEmail.get(which))) {
                    // send invite via email
                    sendInviteEmail(arrayPhoneEmail.get(which));
                } else {
                    // send invite via sms
                    openSendInviteSMS(arrayPhoneEmail.get(which));
                }
                dialog.dismiss();
            }
        };
        if (arrayPhoneEmail.size() > 1) {
            // all emails and phones retrieved, present chooser
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Please Select");
            builder.setItems(arrayPhoneEmail.toArray(new String[arrayPhoneEmail.size()]), selectItemListener);
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                // if user backs out of dialog without selection just finish
                finish();
                }
            });
            builder.show();
        } else if (arrayPhoneEmail.size() == 1) {
            // only one choice of phone/email, skip chooser
            if (isValidEmail(arrayPhoneEmail.get(0))) {
                // send invite via email
                sendInviteEmail(arrayPhoneEmail.get(0));
            } else {
                // send invite via sms
                openSendInviteSMS(arrayPhoneEmail.get(0));
            }
        } else {
            // size is zero, so this contact has no phone numbers or email
            // so there's nothing we can do.
            Toast.makeText(this, R.string.invite_no_phone_or_email, Toast.LENGTH_LONG).show();
            finish();
        }

    }

    private boolean isValidEmail(CharSequence target) {
        if (target == null)
            return false;

        return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    // this method actually sends an SMS without further action by the user
    private void sendInviteSMS(String phoneNumber) {

        try {
            String smsBody = "Hi" + (TextUtils.isEmpty(contactName) ? "," : " " + contactName + ",") +
                    " let's talk securely with Silent Phone. Install it for iOS or Android here: https://silentcircle.com/invite" +
                    (!TextUtils.isEmpty(userName) ? " ... my username is " + userName + "." : "");
            String SMS_SENT = "SMS_SENT";

            PendingIntent sentPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), 0);

            // SMS sent (or error)
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Toast.makeText(context, "SMS sent successfully", Toast.LENGTH_LONG).show();
                            break;
                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                        case SmsManager.RESULT_ERROR_NULL_PDU:
                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                        default:
                            Toast.makeText(context, R.string.invite_sms_not_setup, Toast.LENGTH_LONG).show();
                            break;
                    }
                    unregisterReceiver(this);
                    finish();
                }
            }, new IntentFilter(SMS_SENT));

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, smsBody, sentPendingIntent, null); //deliveredPendingIntent);
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), R.string.invite_sms_not_setup, Toast.LENGTH_LONG).show();
        }
        finally {
            finish();
        }
    }

    private void sendInviteEmail(String emailAddress) {
        try {
            String subject = "Let's talk securely on Silent Phone";
            String message = "Hi" + (TextUtils.isEmpty(contactName) ? "," : " " + contactName + ",") +
                    " let's talk securely with Silent Phone. Install it for iOS or Android here: https://silentcircle.com/invite" +
                    (!TextUtils.isEmpty(userName) ? " ... my username is " + userName + "." : "");

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, message);

            // to prompts email client only
            emailIntent.setType("message/rfc822");
            startActivity(emailIntent);
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), R.string.invite_email_not_setup, Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }

    private void openSendInviteSMS(String phoneNumber) {
        try {
            String smsBody = "Hi" + (TextUtils.isEmpty(contactName) ? "," : " " + contactName + ",") +
                    " let's talk securely with Silent Phone. Install it for iOS or Android here: https://silentcircle.com/invite" +
                    (!TextUtils.isEmpty(userName) ? " ... my username is " + userName + "." : "");

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", phoneNumber, null));
            intent.putExtra("sms_body", smsBody);
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), getString(R.string.invite_sms_not_setup), Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }

}

