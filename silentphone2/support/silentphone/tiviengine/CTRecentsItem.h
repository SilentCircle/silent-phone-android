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
#ifndef _CT_RECENTS_ITEM_H
#define _CT_RECENTS_ITEM_H

#include "../baseclasses/CTEditBase.h"
#include <string.h>
//class UIRecentCell

class CTRecentsItem: public CListItem{
public:
   enum{eUnknown, eDialed, eReceived, eMissed};
   CTRecentsItem():CListItem(0){
      cell=NULL;
      uiStartTime=0;
      uiDuration= 0;
      iDir=eUnknown;
      iABChecked=0;
      iAtFoundInPeer=0;
      
      iTmpMarker=0;
      iIsTmpRepeat=0;
      
      iIsTooOld=0;
      
      iAnsweredSomewhereElse=0;
      szSIPCallID[0]=0;
      szPeerAssertedUsername[0]=0;
   }
   
   inline int isTooOld(int now, int iKeepHistoryFor){
      if(iIsTooOld)return iIsTooOld;
     
      unsigned int dur = uiDuration;
      if(dur>0xffff)dur=0;
      
      iIsTooOld = now > (uiStartTime + dur + iKeepHistoryFor);
      
      return iIsTooOld;
   }
   
   int isSameRecord(CTRecentsItem *i){
      if(!i)return 0;
      
      if(!name.getLen() && !i->name.getLen()){
      }
      else{
         if(!isSameUser(i))return 0;
      }
      return isSamePeer(i);
      
   }
   int isSameUser(CTRecentsItem *i){
      if(!i)return 0;
      if(!name.getLen())return 0;
      if(!i->name.getLen())return 0;
      int iLen=name.getLen();
      short *p=name.getText();
      short *p2=i->name.getText();
      return (iLen==i->name.getLen() && p[iLen>>1]==p2[iLen>>1] && p[iLen-1]==p2[iLen-1] && memcmp(p,p2,(iLen-1)*2)==0);
   }
   int findAT_char(){
      if(iAtFoundInPeer)return iAtFoundInPeer;
      
      int iLen=peerAddr.getLen();
      short *p=peerAddr.getText();
      for(int i=0;i<iLen;i++){
         if(p[i]=='@'){
            iAtFoundInPeer=i;
            break;
         }
      }
      if(!iAtFoundInPeer)iAtFoundInPeer=iLen;
      return iAtFoundInPeer;
   }
   
   inline int haveCallLog(){return szSIPCallID[0];}
   
   int isSamePeer(CTRecentsItem *i){
      if(!i)return 0;
      
      if(peerAddr.getLen()>0){
         //int iLen=peerAddr.getLen();
         
         
         if(lbServ!=&i->lbServ)return 0;
         
         if(!iAtFoundInPeer)findAT_char();
         if(!i->iAtFoundInPeer)i->findAT_char();
         
         short *p=peerAddr.getText();
         short *i_p=i->peerAddr.getText();
         
//         if(iLen==i->peerAddr.getLen() && memcmp(p,i_p,iLen*2)==0)
  //          return 1;
         
         if(iAtFoundInPeer && i->iAtFoundInPeer){
            int is =  (iAtFoundInPeer==i->iAtFoundInPeer
                     && p[iAtFoundInPeer>>1]==i_p[iAtFoundInPeer>>1] 
                     && p[iAtFoundInPeer -1]==i_p[iAtFoundInPeer -1] 
                     && memcmp(p,i_p,(iAtFoundInPeer-1)*2)==0);
            if(is)return is;
         }
         
         if(szPeerAssertedUsername[0] && i->szPeerAssertedUsername[0]){
            return strcmp(szPeerAssertedUsername,i->szPeerAssertedUsername)==0;
         }
         
         char sz[64];
         if(szPeerAssertedUsername[0]){
            int ll = sizeof(sz)-1;
            i->peerAddr.getTextUtf8(sz, &ll);
            int ofs = (szPeerAssertedUsername[3]==':') ? 4 : 0;
          //  printf("[%s %s]\n",&szPeerAssertedUsername[0],sz);
            return strcmp(&szPeerAssertedUsername[ofs],sz)==0;
         }
         
         if(i->szPeerAssertedUsername[0]){
            int ll = sizeof(sz)-1;
            peerAddr.getTextUtf8(sz, &ll);
            int ofs = (i->szPeerAssertedUsername[3]==':') ? 4 : 0;
            //printf("[%s %s]\n",&szPeerAssertedUsername[0],sz);
            return strcmp(&i->szPeerAssertedUsername[ofs],sz)==0;
         }
         
         
      }
      return 0;
   }
   int iABChecked;
   void *cell;
   CTEditBuf<64> name;//from phonebook
   CTEditBuf<64> peerAddr;
   CTEditBuf<64> myAddr;
   CTEditBuf<64> lbServ;
   unsigned int uiStartTime;
   unsigned int uiDuration;
   int iDir;
   
   int iAnsweredSomewhereElse;
   
   int iAtFoundInPeer;

   void* iTmpMarker;
   int iIsTmpRepeat;
   
   int iIsTooOld;//#SP-117
   
   char szSIPCallID[64];//if we want to store this into db we have to store also logs into disk.
   
   char szPeerAssertedUsername[64];
};
int loadRecents(CTList *l);
void saveRecents(CTList *l);
int loadFavorites(CTList *l);
void saveFavorites(CTList *l);

int keepHistoryFor(void);
int get_time(void);


class CTRecentsList: protected CTList{
   int iCurrentListIsMissed,iIsFavorites;
   CTRecentsItem *lastAdded;
   
   int isListedRecord(CTRecentsItem *i, int t, int iKeepHistoryFor){
      CTRecentsItem *tmp=(CTRecentsItem*)getNext(NULL);
      while(tmp){
         if(tmp==i)break;
         
         if(!tmp->iIsTmpRepeat && !tmp->isTooOld(t, iKeepHistoryFor) &&  i->isSameRecord(tmp))return 1;
         
         tmp=(CTRecentsItem*)getNext(tmp);
      }
      return 0;
   }
   
   int iItemCnt;
   int iIsSaved;
   int iCanSave;
   int iLoaded;
   
   //CTRecentsList(int iIsFavorites=0):CTList(),iIsFavorites(iIsFavorites){iIsSaved=0;iCurrentListIsMissed=0;lastAdded=NULL;iItemCnt=0;iLoaded=0;iCanSave=1;}
    CTRecentsList(int iIsFavorites=0):CTList(),iIsFavorites(iIsFavorites){iIsSaved=0;iCurrentListIsMissed=0;lastAdded=NULL;iItemCnt=0;iLoaded=0;iCanSave=1;}
    
    public:
    static CTRecentsList *sharedFavorites(){
        static CTRecentsList * l = new CTRecentsList(1);
        return l;
    }
    static CTRecentsList *sharedRecents(){
        static CTRecentsList * l = new CTRecentsList(0);
        return l;
    }

   
   void enableAutoSave(int f){iCanSave=f;}
   void save(){
      
      if(!iIsSaved && iLoaded && iCanSave){
         iIsSaved=1;
         if(iIsFavorites)saveFavorites(getList());
         else saveRecents(getList());
      }
   }
   void load(){
      if(iLoaded)return;
      iLoaded=1;
      if(iIsFavorites)loadFavorites(getList());
      else loadRecents(getList());
   }
   CTList *getList(){return (CTList*)this;}
   /*
   static  NSString *toNS(CTEditBase *b, int N=0){
      if(N)return toNSFromTBN(b,N);
      return toNSFromTB(b);
   }
   */
   int countItemsGrouped(){
      int c=0;
      CTRecentsItem *tmp=(CTRecentsItem*)getNext(NULL);
      
      int iKeepHistoryFor = keepHistoryFor();
      int t = get_time();
      
      while(tmp){
         tmp->iIsTmpRepeat=0;
         
         if(!iIsFavorites && (tmp->isTooOld(t, iKeepHistoryFor) || isListedRecord(tmp, t, iKeepHistoryFor))){
            tmp->iIsTmpRepeat++;
            tmp->iVisible=0;
         }
         else {c++;tmp->iVisible=1;}
         
     
         tmp=(CTRecentsItem*)getNext(tmp);
      }
      iItemCnt=c;
      return c;
   }
   CTRecentsItem* getByIndexGrouped(int idx){
      CTRecentsItem *tmp=(CTRecentsItem*)getNext();
      
      while(tmp){
         if(!tmp->iIsTmpRepeat){
            if(idx<=0)break;
            idx--;
         }
         
         tmp=(CTRecentsItem*)getNext(tmp);
      }
      return (CTRecentsItem*)tmp;
      
   }
   
   int hasRecord(CTRecentsItem *i){
      if(!i)return 0;
      CTRecentsItem *tmp=(CTRecentsItem*)getNext(NULL,1);
      
      while(tmp){
         if(tmp->isSameRecord(i))return 1;
         tmp=(CTRecentsItem*)getNext(tmp,1);
      }
      return 0;
   }
   
   
   
   CTRecentsItem *getByIdxAndMarker(int idx, void* iMarker){
      
      CTRecentsItem *tmp=(CTRecentsItem*)getNext(NULL,1);
      while(tmp){
         if(iMarker==tmp->iTmpMarker){
            if(idx<=0)return tmp;
            idx--;
         }
         tmp=(CTRecentsItem*)getNext(tmp,1);
      }
      if(tmp && iMarker==tmp->iTmpMarker)return tmp;
      return NULL;
      
   }
   
   void removeCurrent(){
      CListItem *tmp=getNext(NULL,1);
      CListItem *n=NULL;
      while(tmp){
         n=getNext(tmp,1);
         if(iCurrentListIsMissed && ((CTRecentsItem*)tmp)->iDir==CTRecentsItem::eMissed){
            remove(tmp);
           //-- iItemCnt--;
            iIsSaved=0;
         }
         else if(!iCurrentListIsMissed){// && ((CTRecentsItem*)tmp)->iDir!=CTRecentsItem::eMissed){
            remove(tmp);
           //-- iItemCnt--;
            iIsSaved=0;
         }
         tmp=n;
      }
      save();
      iItemCnt=0;
   }
   int countVisItems(){
      return iItemCnt;
   }
   int removeRecord(CTRecentsItem *i){
      if(!i)return 0;
      CTRecentsItem *tmp=(CTRecentsItem*)getNext(NULL,1);
      CTRecentsItem *n=NULL;
      while(tmp){
         n=(CTRecentsItem*)getNext(tmp,1);
         if(i!=tmp && i->isSameRecord(tmp)){
            remove(tmp);
            iIsSaved=0;
         }
         tmp=n;
      }
      remove(i);
      countItemsGrouped();
      save();
      return 0;
   }
   
   int removeByIndex(int idx){
      if(iItemCnt<=0){
        // NSLog(@"BUG, shoud not be here,iItemCnt=%d ",iItemCnt);
         return -1;//?????
      }
      CTRecentsItem *i=getByIndex(idx);
      if(!i)return -1;
   //--   if(i->iVisible)iItemCnt--;
      remove(i);
      iIsSaved=0;
      save();
      countItemsGrouped();
      return 0;
   }
   int removeByIndexGr(int idx){
      if(iItemCnt<=0){
        // NSLog(@"BUG, shoud not be here,iItemCnt=%d ",iItemCnt);
         return -1;//?????
      }
      CTRecentsItem *i=getByIndexGrouped(idx);
      if(!i)return -1;
   //--   if(i->iVisible)iItemCnt--;
      remove(i);
      iIsSaved=0;
      save();
      return 0;
   }
   CTRecentsItem* getByIndex(int idx){
      
      //TODO mutex
      CListItem *tmp=getNext();
      while(tmp){
         if(idx<=0)break;
         idx--;
         tmp=getNext(tmp);
      }
      return (CTRecentsItem*)tmp;
      
   }
   
   int activateMissed(){
      iCurrentListIsMissed=1;
      int i=0;
      CListItem *tmp=getLRoot();
      while(tmp)
      {
         tmp->iVisible=(((CTRecentsItem*)tmp)->iDir==CTRecentsItem::eMissed);
         if(tmp->iVisible)i++;
         tmp=tmp->next;
      }
      iItemCnt=i;
      return i;
   }
   int activateAll(){
      iCurrentListIsMissed=0;
      int i=0;
      CListItem *tmp=getLRoot();
      while(tmp)
      {
         tmp->iVisible=1;//(((CTRecentsItem*)tmp)->iDir==CTRecentsItem::eMissed);
         i++;
         tmp=tmp->next;
      }
      iItemCnt=i;
      return i;
   }
   virtual void onRemove(CListItem *item){
      CTRecentsItem *i=(CTRecentsItem*)item;
      if(i){
         if(i->iVisible)iItemCnt--;
         iIsSaved=0;
      }
   }
   virtual void onInsert(CListItem *item){
      CTRecentsItem *i=(CTRecentsItem*)item;
      if(i){
         i->iVisible=!iCurrentListIsMissed || i->iDir==CTRecentsItem::eMissed;
         if(i->iVisible)iItemCnt++;
         lastAdded=i;
         iIsSaved=0;
      }
   }
   
   CTRecentsItem* add(int iDir, CTStrBase *nameFromABorSIP, const char *peer, const char *myAddr, const char *serv, unsigned int uiDuration, int iAnsweredSomewhereElse){
      if(iDir==CTRecentsItem::eMissed)uiDuration=0;
      CTRecentsItem *i=new CTRecentsItem();
      if(!i){
         //TODO LOG
         return NULL;
      }
      i->iDir=iDir;
      i->name.setText(*nameFromABorSIP);
      i->peerAddr.setText(peer);
      i->myAddr.setText(myAddr);
      i->lbServ.setText(serv);
      extern int get_time();
      i->uiStartTime=get_time()-uiDuration;
      i->uiDuration=uiDuration;
      i->iVisible=!iCurrentListIsMissed || iDir==CTRecentsItem::eMissed;
      i->iAnsweredSomewhereElse = iAnsweredSomewhereElse;
      
      this->addToRoot(i);
      
      lastAdded=i;
      // if(i->iVisible)iItemCnt++;
      
      
      //TODO lookup
      /*
       UIRecentCell *cell;
       CTEditBuf<64> name;//from phonebook
       CTEditBuf<64> peerAddr;
       CTEditBuf<64> myAddr;
       unsigned int uiStartTime;
       unsigned int uiDuration;
       int iDir;
       */
      iIsSaved=0;
      
      return i;
   }
};



#endif
