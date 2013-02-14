
// ---LICENSE_BEGIN---
/*
 * Copyright © 2012, Silent Circle
 * All rights reserved.
 */
// ---LICENSE_END---

package com.silentcircle.silentphone.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

//import com.tivi.tiviphone.utils.TCall;
import com.silentcircle.silentphone.utils.Utilities;
import com.silentcircle.silentphone.activities.TMActivity;

/**
 * ProcessOutgoingCallTest tests {@link OutgoingCallBroadcaster} by performing
 * a couple of simple modifications to outgoing calls, and by printing log
 * messages for each call.
 */
public class OCT extends BroadcastReceiver {
   private static final String TAG = "Tivi.OCT";
   
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
      //TODO prefs.edit().putString(call_to, "").commit();
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

      i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
      
      context.startActivity(i);	     
   }
   
   public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
         String number = getResultData();

         if (TMActivity.SP_DEBUG) Log.v(TAG, "Received " + intent + " [number:" + number + "]");
         
         if (number == null) {
             return;
         }

         if(number.startsWith(prefix_check) && number.length() >= prefix_check.length() + 4){
            startTivi(context,number.substring(prefix_check.length()),intent);
            setResultData(null);
            return;
         }
         
         if(number.startsWith(prefix_add) && number.length() >= prefix_check.length() + 4){
            startTivi(context,number.substring(prefix_add.length()),intent);
            setResultData(null);
            return;
         }
      }
   }
}