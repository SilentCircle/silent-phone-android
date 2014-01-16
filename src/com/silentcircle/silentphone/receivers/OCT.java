/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.silentcircle.silentphone.activities.TMActivity;
import com.silentcircle.silentphone.utils.Utilities;

/**
 * ProcessOutgoingCallTest tests {link OutgoingCallBroadcaster} by performing
 * a couple of simple modifications to outgoing calls, and by printing log
 * messages for each call.
 */
public class OCT extends BroadcastReceiver {
   private static final String TAG = "OCT";
   
   // silentphone 74536874663
   public static final String prefix_add = "SilentPhone";
   public static final String prefix_check = "74536874663";//silentphone
      
   public static final String call_to = "call_to";
   public static final String call_to_timestamp = "call_to_timestamp";
   
   public static final int STORED_NR_LIFETIME = 3000;// 3 sec

   public static String getCallToNumber(Context context){
      
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      
      long storedTS = prefs.getLong(call_to_timestamp,0);
      long ts = Utilities.get_time_ms();
      long d = ts - storedTS;
      
      if(d > STORED_NR_LIFETIME || d < -STORED_NR_LIFETIME)
          return null;//expired
      
      String nr = prefs.getString(call_to, "");
      
      if(nr == null  || nr.length()==0)
         return null;
      //forget number
      SharedPreferences.Editor e = prefs.edit();
      e.putString(call_to, "");
      e.commit();
      
      return nr;
      
   }
   
   private void setCallToNumber(Context context, String s){
      
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      SharedPreferences.Editor e = prefs.edit();
      e.putString(call_to, s);
      e.putLong(call_to_timestamp, Utilities.get_time_ms());
      e.commit();
      
   }
   
   
   void startTivi(Context context, String number, Intent intent){
      
      setCallToNumber(context, number);
      
      Intent i = new Intent();

      i.setClass(context, TMActivity.class);
      i.setData(intent.getData());
      i.putExtra("dst_number", number);

      i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|
              Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      
      context.startActivity(i);	     
   }

    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
            String number = getResultData();

            if (TMActivity.SP_DEBUG) Log.v(TAG, "Received " + intent + " [number:" + number + "]");

            if (number == null) {
                return;
            }

            if (number.startsWith(prefix_check) && number.length() >= prefix_check.length() + 4) {
                startTivi(context, number.substring(prefix_check.length()), intent);
                setResultData(null);
                return;
            }

            if (number.startsWith(prefix_add) && number.length() >= prefix_add.length() + 4) {
                startTivi(context, number.substring(prefix_add.length()), intent);
                setResultData(null);
            }
        }
    }
}