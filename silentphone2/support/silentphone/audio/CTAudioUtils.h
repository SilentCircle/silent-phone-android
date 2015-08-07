/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2015, Silent Circle, LLC.  All rights reserved.

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

#ifndef _C_T_AUIDOUTILS_H
#define _C_T_AUIDOUTILS_H

class CTAudioUtils{
public:
   template<class T, class TR>
   static void mix(int iCnt, T *samples[], int iSamples, T ma, T mi,  T *res, int iCanUseFixedP){
      
      //TR miN=mi*iCnt;
      //TR maN=ma*iCnt;
      //TODO if  shorts use shift(>>1)
      //if iCnt==3 {m=((1<<14)/3+1); v*=m;v>>=14;}
      /*
       short sample1 = ...;
       short sample2 = ...;
       
       int mixed = samplef1 + sample2f;
       // reduce the volume a bit:
       mixed *= 0.8;
       short outputSample = (short)clip(mixed,-32700,32700) 
       //TODO use smart mixer with ctx, 
       //if voice inc volume, if no voice dec stream vol
       */
      if(iCnt==1){
         memcpy(res,samples[0],sizeof(T)*iSamples);
         return ;
      }
      if(iCanUseFixedP){
         if(iCnt==2){
            const int m=(((1<<14)/10+1)*8);
            for(int i=0; i<iSamples; i++){
               TR val=(TR)samples[0][i]+(TR)samples[1][i];
               
               val*=m;val>>=14; 
               if(val>ma)res[i]=ma;
               else if(val<mi)res[i]=mi;
               else res[i]=val;
            }
            
            
            return; 
         }
         if(iCnt==3){
            const int m=(((1<<14)*64/100+1));
            for(int i=0; i<iSamples; i++){
               TR val=(TR)samples[0][i]+(TR)samples[1][i]+(TR)samples[2][i];
               
               val*=m;val>>=14; 
               if(val>ma)res[i]=ma;
               else if(val<mi)res[i]=mi;
               else res[i]=val;
            }
            
            return; 
         }
         if(iCnt==4){
            const int m=(((1<<14)*64*8/1000+1));
            for(int i=0; i<iSamples; i++){
               TR val=(TR)samples[0][i]+(TR)samples[1][i]+(TR)samples[2][i]+(TR)samples[3][i];
               
               val*=m;val>>=14; 
               if(val>ma)res[i]=ma;
               else if(val<mi)res[i]=mi;
               else res[i]=val;
            }
            
            return; 
         }
      }
      //TODO analyze how many are on mute or do not speak
      TR tMult=4;//if one is speaking he can have 80% of max volume
      TR tDiv=5;
      for(int i=2;i<iCnt;i++){
         tMult*=4;
         tDiv*=5;
      }
      const long long _m=(((1<<14)*(long long)tMult/(long long)tDiv+1));
      const int m=_m;
      for(int i=0; i<iSamples; i++){
         TR val=0;
         for(int c=0;c<iCnt;c++){
            val+=(TR)samples[c][i];
         }
         val*=m;val>>=14;
         if(val>ma)res[i]=ma;
         else if(val<mi)res[i]=mi;
         else res[i]=val;
      }
      
      
   }
};

#endif
