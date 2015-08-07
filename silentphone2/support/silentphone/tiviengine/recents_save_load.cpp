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

#if  (defined(_WIN32) || defined(__SYMBIAN32__) || defined(_WIN32_WCE) || defined(ANDROID_NDK) || defined(__APPLE__))
//&&  !defined(__SYMBIAN32__)

#include <stdio.h>
#include <stdlib.h>

#include "../xml/parse_xml.h"
#include "../utils/utils.h"

#define _T_WO_GUI

#include "../baseclasses/CTListBase.h"
#include "CTRecentsItem.h"


static int iLoaded=0;
static int iLoadedFav=0;


int getPath(char *p, int iMaxLen);
int getPathW(short *p, int iMaxLen);
int fillCfg(char *dest,STR_XML *src);

int hex2BinL(unsigned char *Bin, char * Hex, int iLen);

static int loadBE(char *v, int iLen , CTEditBase *b){
   
//TODO getMaxLen
   short tmp[256];
   if(!iLen){
      b->reset();
      return 0;
   }
   if(iLen>510)iLen=510;
   
   hex2BinL((unsigned char *)tmp,v,iLen);
   tmp[iLen/4]=0;
   b->setText((char*)tmp,iLen/4,1);
   return 0;
}

static int addItemBE(FILE *f, const char *key, CTEditBase *b){
   char r[1024];
   
   void bin2Hex(unsigned char *Bin, char * Hex ,int iBinLen);
   
   int l=b->getLen();
   if(l>255)l=255;//???
   
   bin2Hex((unsigned char *)b->getText(),&r[0],l*2);
   //r[l*4]=0;
   
   fprintf(f," %s=\"%s\"",key,r);
   //printf(" %s=\"%s\"",key,r);
   return 0;
}

static int addItemI(FILE *f, const char *key, int v){

   fprintf(f," %s=\"%d\"",key,v);
   //printf(" %s=\"%d\"",key,v);
   return 0;
}

static int addItemUI(FILE *f, const char *key, unsigned int v){
   
   fprintf(f," %s=\"%u\"",key,v);
 //  printf(" %s=\"%u\"",key,v);
   return 0;
}

int keepHistoryFor(){
   void *findGlobalCfgKey(const char *key);
   
   static char *_p = (char*)findGlobalCfgKey("szRecentsMaxHistory");
   char *p  = _p;
   
#define T_DEFAULT_HIST_DUR 30*24*3600
   if(!p || !p[0])return T_DEFAULT_HIST_DUR;
   
   if(strcmp(p, "never")==0)return 0;
   
   int l = atoi(p);
   if(l){
     while(*p && p[0]!=' ')p++;
     if(p[0]!=' ')return T_DEFAULT_HIST_DUR;
   }
   else l = 1;
   
   p++;
   switch(p[0]){
      case 'm':{
         if(p[1]=='o')return l * 31 * 24 * 3600;
         return l * 60;
      }
      case 'h':return l * 3600;
      case 'd':return l * 24 * 3600;
      case 'w':return l * 7 * 24 * 3600;
      case 'y':return l * 365 * 24 * 3600;
   }
   
   return T_DEFAULT_HIST_DUR;
}

void saveRecetnsFN(CTList *list, const char *tag, int iIsRecents, const char *fn){
   
   unsigned int getTickCount();
   unsigned int ui=getTickCount();
   if(!iLoaded)return;
   FILE *f=fopen(fn,"wb+");
   if(!f)return;
   
   CTRecentsItem *rec=(CTRecentsItem*)list->getNext(NULL,1);
   /*
    int iABChecked;
    void *cell;
    CTEditBuf<64> name;//from phonebook
    CTEditBuf<64> peerAddr;
    CTEditBuf<64> myAddr;
    CTEditBuf<64> lbServ;
    unsigned int uiStartTime;
    unsigned int uiDuration;
    int iDir;
    */
   
   
   int iKeepHistoryFor = keepHistoryFor();
   int t = get_time();
   
   
   fprintf(f,"<%s>\n",tag);
   while(rec)
   {
      if(!iIsRecents || !rec->isTooOld(t, iKeepHistoryFor)){
         fprintf(f,"<item");
         addItemBE(f,"name",&rec->name);
         addItemBE(f,"peerAddr",&rec->peerAddr);
         addItemBE(f,"myAddr",&rec->myAddr);
         addItemBE(f,"lbServ",&rec->lbServ);
         
         if(iIsRecents){
            addItemUI(f,"uiStartTime",rec->uiStartTime);
            addItemUI(f,"uiDuration",rec->uiDuration);
            addItemI(f,"iDir",rec->iDir);
            
            addItemI(f,"iABChecked",rec->iABChecked);//has name
            
            addItemI(f, "iAnswSE", rec->iAnsweredSomewhereElse);
         }
         
         fputs(" />\n",f);
      }
      rec=(CTRecentsItem*)list->getNext(rec,1);
   }
   fprintf(f,"</%s>",tag);
   
   fclose(f);
   
   if(iIsRecents){
      void setFileBackgroundReadable(const char *fn);
      setFileBackgroundReadable(fn);
   }
   
   printf("[save-ab %dms]",getTickCount()-ui);
   //CTRecentsItem
      
}

int loadRecetnsFN(CTList *list, const char *tag, int iIsRecents, const char *fn){
   CParseXml xml;
   NODE *n=xml.mainXML(fn);

   //printf("[%p ]",n);
   if(!n || !n->child){
      char *loadFile(const  char *fn, int &iLen);
      void saveFile(const char *fn,void *p, int iLen);
      int l;
      char *p=loadFile(fn,l);
         char buf[1024];
         strcpy(buf,fn);
         strcat(buf,"broken.txt");
         
      if(p){
         saveFile(&buf[0],p,l);
         delete p;
      }
      else{
         saveFile(&buf[0],(void*)"empty",5);
      }
      return -1;
   }

   NODE *node=n->child;

#define TCMP(N,_V) (N.len+1==sizeof(_V) && N.s[N.len>>1]==_V[N.len>>1] && memcmp(N.s,_V,N.len)==0)
   int t = get_time();
  // unsigned int uiDontLoadRecBefore=(unsigned int)get_time() - keepHistoryFor();//60*60*24*31;//1 month
   int iKeepHistoryFor = keepHistoryFor();

   CTRecentsItem *rec=NULL;
   int iCallRecords=0;
   while(node && iCallRecords<1000){
      
      nameValue *nv=node->nV;
      rec=new CTRecentsItem();
      if(!rec)break;
      int iCanAdd=1;
     
      while(nv && iCanAdd){
         do{
#define T_TRY_LOAD_E(_A,_DST) if(TCMP(nv->name,_A)){loadBE(nv->value.s,nv->value.len, _DST);break;}
#define T_TRY_LOAD_I(_A,_DST) if(TCMP(nv->name,_A)){_DST=atoi(nv->value.s);;break;}
#define T_TRY_LOAD_UI(_A,_DST) if(TCMP(nv->name,_A)){_DST=atol(nv->value.s);break;}

            if(iIsRecents){
               
               T_TRY_LOAD_UI("uiStartTime",rec->uiStartTime)
               T_TRY_LOAD_UI("uiDuration",rec->uiDuration)
               
               if(rec->uiStartTime && (rec->isTooOld(t, iKeepHistoryFor) || iCallRecords>500)){
                  iCanAdd=0;
                  delete rec;
                  break;
               }
               
               T_TRY_LOAD_I("iABChecked",rec->iABChecked)
               T_TRY_LOAD_I("iDir",rec->iDir)
               T_TRY_LOAD_I( "iAnswSE", rec->iAnsweredSomewhereElse);
            }
            
            T_TRY_LOAD_E("name",&rec->name);
            T_TRY_LOAD_E("peerAddr",&rec->peerAddr);
            T_TRY_LOAD_E("myAddr",&rec->myAddr);
            T_TRY_LOAD_E("lbServ",&rec->lbServ);
            
         }while(0);
         nv=nv->next;
      }
//      list->addToRoot(rec);
      if(iCanAdd){list->addToTail(rec);rec=NULL;iCallRecords++;}
      node=node->next;
   }
   
   return 0;
}
char *getFileStorePath();

int createFN(char *p, const char *fn){
   //snprint(p,511,"%s/%s",getFileStorePath(),fn);
   strcpy(p, getFileStorePath());
   strcat(p,"/");
   strcat(p, fn);
   return 0;
}
int loadRecents(CTList *l){
   char buf[512];
   iLoaded=1;
   createFN(buf,"t_phonebook.xml");
   return loadRecetnsFN(l,"recents",1,&buf[0]);
}
void saveRecents(CTList *l){
   char buf[512];
   createFN(buf,"t_phonebook.xml");
   return saveRecetnsFN(l,"recents",1,&buf[0]);
}
int loadFavorites(CTList *l){
   char buf[512];
   iLoadedFav=1;
   createFN(buf,"t_favorites.xml");
   return loadRecetnsFN(l,"favorites",0,&buf[0]);
}
void saveFavorites(CTList *l){
   char buf[512];
   if(!iLoadedFav)return;
   createFN(buf,"t_favorites.xml");
   return saveRecetnsFN(l,"favorites",0,&buf[0]);
}

//int bMelFound=false;

#endif
