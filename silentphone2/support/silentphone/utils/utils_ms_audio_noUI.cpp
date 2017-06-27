/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2017, Silent Circle, LLC.  All rights reserved.

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

#if 1
#ifndef UNICODE
#define UNICODE
#endif

//#include <C:\Program Files (x86)\Microsoft DirectX SDK (November 2008)\Include\dsound.h>

#include <dsound.h>

#include "../baseclasses/CTListBase.h"
#include "../gui/CTEdit.h"


#include "utils_ms_audio_noUI.h"

void tivi_log_au(const char* format, ...);

CTAudioCard::CTAudioCard(int id, GUID *g, char *name)
:CListItem(id)
{
   memcpy(&guid,g,sizeof(GUID));
   strcpy(szDriverName,name);
}



#define IS_OK_MMR(c) if((c)!=0){tivi_log_au("rmp-1,%d\n",c);return -1;}

int remMicFromPlayback(UINT iDestC,UINT uiMixId, unsigned int uiCaptDevCnt=-1)
{
//      PMIXERCONTROLDETAILS_BOOLEAN pBol;

   tivi_log_au("rmp");
      PMIXERCONTROL mixc;//MIXER_GETCONTROLDETAILSF_VALUE
      MIXERCONTROLDETAILS   mcd;
      MIXERLINECONTROLS mc;
      MMRESULT mmr;
      HMIXER hmx;
      MIXERLINE ml;
      int ok=0;
      MIXERCONTROLDETAILS_UNSIGNED   pmxcd_u;
      UINT x,y,z;
      if(uiCaptDevCnt==-1) uiCaptDevCnt=waveInGetNumDevs();
      if(uiMixId>=uiCaptDevCnt || uiCaptDevCnt==-1){
         tivi_log_au("rmp-2");
         return -2;
      }


      mmr = mixerOpen(&hmx, uiMixId, 0, 0L, 0);
      IS_OK_MMR(mmr);
      for(x=0;x<iDestC ;x++)
      {
         tivi_log_au("rmpx=%d",x);
         ml.cbStruct=sizeof(ml);
         ml.dwDestination=x;
         mmr=mixerGetLineInfo((HMIXEROBJ)hmx,&ml,MIXER_GETLINEINFOF_DESTINATION);
         IS_OK_MMR(mmr);
         if(MIXERLINE_COMPONENTTYPE_DST_SPEAKERS==ml.dwComponentType)
         {
            tivi_log_au("rmpx=ok");
            ok=1;
            break;
         }
      }
      if(!ok)
         mixerClose(hmx);
      ok=0;
      if(ml.cConnections>1)
      {
         UINT dstCnt=ml.cConnections;
         for(y=0;y<dstCnt;y++)
         {
            tivi_log_au("cony=%d",y);
            ml.cbStruct=sizeof(ml);
            ml.dwDestination=x;
            ml.dwSource =y;
            mmr=mixerGetLineInfo((HMIXEROBJ)hmx,&ml,MIXER_GETLINEINFOF_SOURCE);
            IS_OK_MMR(mmr);

           if(MIXERLINE_COMPONENTTYPE_SRC_MICROPHONE==ml.dwComponentType)
           {
              tivi_log_au("cony ok");
              ok=1;
              break;
           }
         }
      }
      if(!ok)
         mixerClose(hmx);

      
      mixc =(MIXERCONTROL *)malloc(sizeof(MIXERCONTROL) * ml.cControls);
     
      mc.cbStruct=sizeof(mc);
      mc.cControls=ml.cControls;

      mc.dwLineID=ml.dwLineID;
      mc.cbmxctrl       = sizeof(*mixc);
      mc.pamxctrl       = mixc;

      mmr = mixerGetLineControls((HMIXEROBJ)hmx, &mc, MIXER_GETLINECONTROLSF_ALL);
      IS_OK_MMR(mmr);
  //  MIXERR_INVALCONTROL
      for(z=0;z<mc.cControls;z++)
      {
         tivi_log_au("cony z=%d",z);
         if(MIXERCONTROL_CONTROLTYPE_MUTE==mixc[z].dwControlType)
         {
            tivi_log_au("cony z=%d ok",z);
            mcd.cbStruct=sizeof(mcd);
            mcd.dwControlID=mixc[z].dwControlID;
            mcd.hwndOwner=0;
            mcd.cChannels=1;
            mcd.cbDetails=sizeof(pmxcd_u);

            pmxcd_u.dwValue=mixc[z].Bounds.dwMaximum;
            mcd.paDetails=&pmxcd_u;

            mmr=mixerSetControlDetails((HMIXEROBJ)hmx,&mcd,0) ;
            IS_OK_MMR(mmr);
            break;
         }
      }
      free(mixc);

      mixerClose(hmx);
      tivi_log_au("rmp ok");
      return 0;
}

int setMicOn(UINT iDestC,UINT uiMixId, unsigned int uiCaptDevCnt=-1)
{
   tivi_log_au("set mic on");
      //PMIXERCONTROLDETAILS_BOOLEAN pBol;
      PMIXERCONTROL mixc;//MIXER_GETCONTROLDETAILSF_VALUE
      MIXERCONTROLDETAILS   mcd;
      MIXERLINECONTROLS mc;
      MMRESULT mmr;
      HMIXER hmx;
      MIXERLINE ml;
      MIXERCONTROLDETAILS_UNSIGNED   pmxcd_u;
      UINT x,y,z;
//      PMIXERCONTROLDETAILS_LISTTEXT list;
      int ok;
      //int iDC=waveInGetNumDevs();
      if(uiCaptDevCnt==-1) uiCaptDevCnt=waveInGetNumDevs();
      if(uiMixId>=uiCaptDevCnt || uiCaptDevCnt==-1){tivi_log_au("set mic on -2");return -2;}
      mmr = mixerOpen(&hmx, uiMixId, 0, 0L, 0);
      IS_OK_MMR(mmr);

      for(x=0;x<iDestC ;x++)
      {
         tivi_log_au("dest %d",x);
         ml.cbStruct=sizeof(ml);
         ml.dwDestination=x;
         mmr=mixerGetLineInfo((HMIXEROBJ)hmx,&ml,MIXER_GETLINEINFOF_DESTINATION);
         IS_OK_MMR(mmr);
         if(MIXERLINE_COMPONENTTYPE_DST_WAVEIN==ml.dwComponentType)
         {
            tivi_log_au("dest ok",x);
            break;
         }
      }
     
      if(ml.cConnections>1)
      {
         UINT dstCnt=ml.cConnections;
         for(y=0;y<dstCnt;y++)
         {
            tivi_log_au("dest y=%d",y);
            ml.cbStruct=sizeof(ml);
            ml.dwDestination=x;
            ml.dwSource =y;
            mmr=mixerGetLineInfo((HMIXEROBJ)hmx,&ml,MIXER_GETLINEINFOF_SOURCE);
            IS_OK_MMR(mmr);

            if(MIXERLINE_COMPONENTTYPE_SRC_MICROPHONE==ml.dwComponentType){
               tivi_log_au("dest ok=%d",y);
               break;
            }
         }
      }
      mc.cbStruct=sizeof(mc);
      mixc =(MIXERCONTROL *)malloc(sizeof(MIXERCONTROL) * ml.cControls);
      
      mc.cControls=ml.cControls;
      mc.dwLineID=ml.dwLineID;
      mc.cbmxctrl       = sizeof(*mixc);
      mc.pamxctrl       = mixc;
      mmr=mixerGetLineControls((HMIXEROBJ)hmx,&mc,MIXER_GETLINECONTROLSF_ALL);
      IS_OK_MMR(mmr);

      
      //if(mixc->cMultipleItems==0)
      //{
      ok=0;
         for(z=0;z<mc.cControls;z++)
         {
            tivi_log_au("smo z=%d",z);
            if(MIXERCONTROL_CONTROLTYPE_MUTE==mixc[z].dwControlType)
            {
               mcd.cbStruct=sizeof(mcd);
               mcd.dwControlID=mixc[z].dwControlID;
               mcd.hwndOwner=0;
               mcd.cChannels=1;
               mcd.cbDetails=sizeof(pmxcd_u);

               pmxcd_u.dwValue=mixc[z].Bounds.dwMinimum;
               mcd.paDetails=&pmxcd_u;

               tivi_log_au("smo z=%d ok",z);
               mmr=mixerSetControlDetails((HMIXEROBJ)hmx,&mcd,0) ;
               IS_OK_MMR(mmr);
               ok=1;
               break;
            }
         }


      free(mixc);
      mixerClose(hmx);
      tivi_log_au("smo ok");
      return 0;
}

static int iLastRecCardID=0xffff;


int activeMicVol(UINT uiVol, UINT uiMixId, bool bGet, unsigned int uiCaptDevCnt=-1)
{
   if(uiMixId<0 || uiMixId>=0xffff)uiMixId=iLastRecCardID;
   if(uiMixId<0 || uiMixId>=0xffff)uiMixId=0; // bool bFound=false;
   tivi_log_au("amv ");
   printf("[activeMicVol %d %d]",uiVol,uiMixId);
   int ret=0;
//      PMIXERCONTROLDETAILS_BOOLEAN pBol;
      PMIXERCONTROL mixc;//MIXER_GETCONTROLDETAILSF_VALUE
      MIXERCONTROLDETAILS   mcd;
      MIXERLINECONTROLS mc;
      MMRESULT mmr;
      HMIXER hmx;
      MIXERLINE ml;
      MIXERCONTROLDETAILS_UNSIGNED   pmxcd_u;
      UINT x,y,z;
      MIXERCAPS mxcaps;
      if(uiCaptDevCnt==-1) uiCaptDevCnt=waveInGetNumDevs();
      if(uiMixId>=uiCaptDevCnt || uiCaptDevCnt==-1){tivi_log_au("amv -2");return -2;}

      mmr = mixerGetDevCaps(uiMixId, &mxcaps, sizeof(mxcaps));
      IS_OK_MMR(mmr);

      mmr = mixerOpen(&hmx, uiMixId, 0, 0L, 0);
      IS_OK_MMR(mmr);
      for(x=0;x<mxcaps.cDestinations ;x++)
      {
         tivi_log_au("amv x=%d",x);
         ml.cbStruct=sizeof(ml);
         ml.dwDestination=x;
         mmr=mixerGetLineInfo((HMIXEROBJ)hmx,&ml,MIXER_GETLINEINFOF_DESTINATION);
         if(MIXERLINE_COMPONENTTYPE_DST_WAVEIN==ml.dwComponentType)
         {
            tivi_log_au("amv x=%d ok",x);
            break;
         }
      }
      if(ml.cConnections>1)
      {
         UINT dstCnt=ml.cConnections;
         for(y=0;y<dstCnt;y++)
         {
            tivi_log_au("amv y=%d",y);
            ml.cbStruct=sizeof(ml);
            ml.dwDestination=x;
            ml.dwSource =y;
            mmr=mixerGetLineInfo((HMIXEROBJ)hmx,&ml,MIXER_GETLINEINFOF_SOURCE);
            IS_OK_MMR(mmr);
            if(MIXERLINE_COMPONENTTYPE_SRC_MICROPHONE==ml.dwComponentType){
               tivi_log_au("amv y=%d",y);
               break;
            }
         }
      }
      
      mixc =(MIXERCONTROL *)malloc(sizeof(MIXERCONTROL) * ml.cControls);
     
      mc.cbStruct=sizeof(mc);
      mc.cControls=ml.cControls;

      mc.dwLineID=ml.dwLineID;
      mc.cbmxctrl       = sizeof(*mixc);
      mc.pamxctrl       = mixc;

      mmr = mixerGetLineControls((HMIXEROBJ)hmx, &mc, MIXER_GETLINECONTROLSF_ALL);
      IS_OK_MMR(mmr);
 //  MIXERR_INVALCONTROL
      for(z=0;z<mc.cControls;z++)
      {
         tivi_log_au("amv z=%d",z);
         if(MIXERCONTROL_CONTROLTYPE_VOLUME==mixc[z].dwControlType)
         {
            tivi_log_au("amv z=%d ok",z);
            mcd.cbStruct=sizeof(mcd);
            mcd.dwControlID=mixc[z].dwControlID;
            mcd.hwndOwner=0;
            mcd.cChannels=1;
            mcd.cbDetails=sizeof(pmxcd_u);

            pmxcd_u.dwValue=mixc[z].Bounds.dwMaximum*uiVol/100;
            mcd.paDetails=&pmxcd_u;
            if(bGet)
            {
               tivi_log_au("get z=%d",z);
               mmr=mixerGetControlDetails((HMIXEROBJ)hmx,&mcd,0) ;
               if(mixc[z].Bounds.dwMaximum)
                  ret=pmxcd_u.dwValue*100/mixc[z].Bounds.dwMaximum;
            }
            else{
               tivi_log_au("!get z=%d",z);
               mmr=mixerSetControlDetails((HMIXEROBJ)hmx,&mcd,0) ;
            }

            IS_OK_MMR(mmr);
            break;
         }
      }
      free(mixc);

      mixerClose(hmx);
      tivi_log_au("amv ok");
//      MIXERLINE_LINEF_ACTIVE 
   return  ret;

}


typedef struct{
   CTList *l;
   int iCnt;
}A_ENUM_STRCT;
void convert16to8(char *dst, const short *src, int iLen);

BOOL CALLBACK DSEnumProc( LPGUID lpGUID, LPSTR lpszDesc,	LPSTR lpszDrvName, LPVOID lpContext )
{
   //HWND   hCombo = *(HWND *)lpContext;
   LPGUID lpTemp = NULL;


   if( lpGUID != NULL )
   {
      A_ENUM_STRCT *e=(A_ENUM_STRCT *)lpContext;
      char buf[256];
      convert16to8((char *)buf,(short*)lpszDesc,0);
      tivi_log_au("ds-en %d %s",e->iCnt,buf);
      
      e->l->addToTail((CListItem *)new CTAudioCard(e->iCnt,lpGUID,buf));
      e->iCnt++;
   }

   return( TRUE );
}


int isALD(int c){
   return (c>='A' && c<='Z') || (c>='a' && c<='z') || (c>='0' && c<='9');
}
int splitWords(char *w, char *p[], int *l){
   int cnt=0;
   int iLen=0;
   while(w[0]){
      iLen=0;
      p[cnt]=w;
      while(w[0] && isALD(w[0])){iLen++;w++;}
      l[cnt]=iLen;
      
      //printf("==[%.*s]==\n",l[cnt],p[cnt]);
      cnt++;
      while(w[0] && !isALD(w[0]))w++;
   }
   
   return cnt;
}
int eqWords(char *p1, char* p2){
   char *w1[32];
   char *w2[32];
   int iL1[32];
   int iL2[32];
   //int wc=0;
   int wc1,wc2;
   wc1=splitWords(p1,w1,&iL1[0]);
   wc2=splitWords(p2,w2,&iL2[0]);
   
   int eq=0;
   int i,j;
   for(i=0;i<wc1;i++){
      for(j=0;j<wc2;j++){
         if(iL2[j]==iL1[i] && strncmp(w1[i],w2[j],iL1[i])==0){
            eq++;
            break;
         }
      }

   }

   return eq;
}

CTAudioCard* getAudioCard(char *name, CTList *l)
{
   tivi_log_au("getAC");
   CTAudioCard*tmp=(CTAudioCard*)l->getNext();
   while(tmp)
   {
      if(strcmp(tmp->szDriverName,name)==0)
      {
         tivi_log_au("getAC ok tmp");
         return tmp;
      }
      tmp=(CTAudioCard*)l->getNext(tmp);
   }
   tmp=(CTAudioCard*)l->getNext();
   CTAudioCard*ret=NULL;
   int iBest=0;
   while(tmp)
   {
      int res=eqWords(&tmp->szDriverName[0],name);
      if(res>iBest){iBest=res;ret=tmp;}
      
      tmp=(CTAudioCard*)l->getNext(tmp);
   }
   tivi_log_au("getAC ok");

   return ret;
}





void enumAuidoDev(CTList *lCapture,CTList *lPlay)
{
   tivi_log_au("en-ad %p %p",lCapture,lPlay);
   A_ENUM_STRCT e;
   char buf[256];
   int i=0,l2,l1;
   int iItems;
   if(lCapture)
   {
      lCapture->removeAll();
      
      e.iCnt=0;
      e.l=lCapture;
      DirectSoundCaptureEnumerate( (LPDSENUMCALLBACK)DSEnumProc, (void *)&e);
   }
   tivi_log_au("en1");

   if(lPlay)
   {
      lPlay->removeAll();
      e.iCnt=0;
      e.l=lPlay;
      DirectSoundEnumerate( (LPDSENUMCALLBACK)DSEnumProc, (void *)&e);
   }
   tivi_log_au("en2");

   CTAudioCard*tmp;
   if(lCapture)
   {
     // WAVEINCAPS wic;
      MIXERCAPS wic;


      tmp=(CTAudioCard*)lCapture->getNext();
      iItems=(int)waveInGetNumDevs();
      tivi_log_au("cap it=%d",iItems);
      while(tmp)
      {
         l2=strlen(tmp->szDriverName);
         int iBest=0;
         for(i=0;i<iItems;i++)
         {      
            //waveInGetDevCaps(i,&wic,sizeof(wic));
            mixerGetDevCaps(i,&wic,sizeof(wic));
            convert16to8((char *)buf, (short *)wic.szPname, 0);
            int res=eqWords(&tmp->szDriverName[0],buf);
            if(res>iBest){iBest=res;tmp->iItemId=i;}
            /*
            l1=strlen(buf);
            l1=l1<l2?l1:l2;
            if(strncmp(buf, tmp->szDriverName,l1)==0)
            {
               tivi_log_au("cap=%d",i);
               tmp->iItemId=i;
               break;
            }*/

         }
      
         i++;
         tmp=(CTAudioCard*)lCapture->getNext(tmp);
      }
   }
   if(lPlay)
   {
      WAVEOUTCAPS woc; 
      tmp=(CTAudioCard*)lPlay->getNext();

      iItems=(int)waveOutGetNumDevs();
      tivi_log_au("pl it=%d",iItems);
      while(tmp)
      {
         l2=strlen(tmp->szDriverName);
         int iBest=0;
         for(i=0;i<iItems;i++)
         {
            waveOutGetDevCaps(i,&woc,sizeof(woc));
            convert16to8((char *)buf, (short *)woc.szPname, 0);
            int res=eqWords(&tmp->szDriverName[0],buf);
            if(res>iBest){iBest=res;tmp->iItemId=i;}
            tivi_log_au("%s %s b%d res%d,id=%d",&tmp->szDriverName[0],buf,iBest,res,tmp->iItemId);
/*            l1=strlen(buf);
            l1=l1<l2?l1:l2;
            if(strncmp(buf, tmp->szDriverName,l1)==0)
            {
               tivi_log_au("play=%d",i);
               tmp->iItemId=i;
               break;
            }
*/
         }
      
         i++;
         tmp=(CTAudioCard*)lPlay->getNext(tmp);
      }
   }

   tivi_log_au("en ok");
}


static CTAudioCard * geACByName(int iRec ,const char *pname){
   
   static CTList aclist;//must be static !!
   aclist.removeAll();
   
   enumAuidoDev(iRec ? &aclist:NULL, !iRec ? &aclist:NULL);
   
   CTAudioCard*a = getAudioCard((char *)pname, &aclist);
   return a;
}


void *getGUIDByName(int iRec ,const char *pname){
   
   CTAudioCard*ac = geACByName(iRec, pname);
   if(ac){
      if(iRec)iLastRecCardID=ac->iItemId;
      return &ac->guid;
   }
   return NULL;
}

int getAC_IDByName(int iRec ,const char *pname){
   
   CTAudioCard*ac = geACByName(iRec, pname);
   if(ac){
      if(iRec)iLastRecCardID=ac->iItemId;
      return ac->iItemId;
   }
   return 0;
   
}

void resetMic(int id)
{
   tivi_log_au("rmic=%d",id);
   if(id!=-1)
   {
      MIXERCAPS mxcaps;
      MMRESULT mmr = mixerGetDevCaps(id, &mxcaps, sizeof(mxcaps));
      setMicOn(mxcaps.cDestinations,id);
      activeMicVol(100,id,false);
      remMicFromPlayback(mxcaps.cDestinations,id);
   }
   tivi_log_au("rmic=%d ok",id);
}


#endif
