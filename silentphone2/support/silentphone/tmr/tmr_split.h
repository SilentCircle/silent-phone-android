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

static int recvTMRPack(TCP_MEDIA_SOCKET *s, char *tmpBuf, int tmpSize, char *retBuf, int iRetBufSize){
   SOCKET sock = s->sock;
   
   while(s->canRecSend(s->pUser)){
    
    //  printf("wait ");
      
      int packSize = (s->iBytesInTmpBuf>=5)?*(int*)tmpBuf:0;
      
      if(s->iBytesInTmpBuf<5 || packSize>s->iBytesInTmpBuf){
         int recMax = tmpSize-s->iBytesInTmpBuf;
         
         if(recMax<1){recMax=tmpSize;s->iBytesInTmpBuf=0;}//?? how to find  start of pack? size32bit,alpha8bit... . size32bit,alpha8bit... .
         
         int bytes = (int)recv(sock, &tmpBuf[s->iBytesInTmpBuf], recMax, 0);
         if(bytes<=0){
            s->iBytesInTmpBuf=0;
            return bytes;
         }
         
         if(bytes==0){
            break;
         }
         
         s->iBytesInTmpBuf += bytes;
         
         if(s->iBytesInTmpBuf<5)continue;
      }
      
      
      packSize = *(int*)tmpBuf;
      
      if(packSize<5){
         if(packSize<0){
            /*TODO warn*/
         }
         s->iWarnings++;
         s->iBytesInTmpBuf=0;
         continue;
      }
      
      if(packSize>iRetBufSize || packSize>tmpSize){
         s->iBytesInTmpBuf=0;
         //T -roundtrip
         //L -lost
         //J -jit
         //TODO find statr
         //try to use short chunks with known length
         return 0;
      }
      
      if(packSize>s->iBytesInTmpBuf)continue;
      
      memcpy(retBuf, tmpBuf, packSize);
      
      int iLeft=s->iBytesInTmpBuf-packSize;
      
      if(iLeft>0){
         memmove(tmpBuf, tmpBuf+packSize, iLeft);
      }
      
      s->iBytesInTmpBuf = iLeft;
      
      return packSize;
   }
   
   return s->iBytesInTmpBuf;
}
