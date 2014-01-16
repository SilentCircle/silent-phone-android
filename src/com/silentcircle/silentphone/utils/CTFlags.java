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

package com.silentcircle.silentphone.utils;

import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;

public class CTFlags {
      
   public class CTRet {
      
      public String countryCode=null;
      public String country=null;
      public String city=null;
      
      
      public int iResID=-1;
      
      void reset(){
         countryCode=null;
         country=null;
         city=null;
         iResID=-1;
      }
   }
   
   public CTFlags() {
   }
   
   public CTRet ret =  new CTRet();
   
   public int getNumberInfo(String number) {
      //format of returned info string: "cc:country:city"
      String info = TiviPhoneService.getInfo(-1, -1, "get.flag=" + number);

      if(info == null || info.length() <= 3) 
          return -1;
      
      ret.reset();
      
      ret.countryCode = info.substring(0,2);
      
      int ofs = info.indexOf(':',3);
      ret.country = info.substring(3,ofs);
      ret.city = info.substring(ofs+1);
      
      ret.iResID = getResID(ret.countryCode); //3x faster then r.getIdentifier
      
      return 0;
   }
   
   public static String formatNumber(String number) {
      //# (###) ###-#### ####
      String newNr = TiviPhoneService.getInfo(-1, -1, "format.nr=" + number);
      if(newNr == null || newNr.length() <= 0)
          return number;
      return newNr;
   }

   static private int getResID(String cc) {
    int ret = -1;
    if ("do".compareToIgnoreCase(cc) == 0)
        cc = "dr";
    try {
       Class<?> res = R.drawable.class;
       java.lang.reflect.Field field = res.getField(cc);
       ret = field.getInt(null);
    }
    catch (Exception ignored) {}
    return ret;
   }
}

