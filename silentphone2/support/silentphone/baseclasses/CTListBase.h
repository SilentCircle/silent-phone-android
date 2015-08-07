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

#ifndef _C__BASE_LIST_H
#define _C__BASE_LIST_H

#ifdef remove
#undef remove //kautkaada fignja defineeta jau
#endif

#ifndef NULL
#ifdef  __cplusplus
#define NULL    0
#else
#define NULL    ((void *)0)
#endif
#endif

#if defined(ANDROID_NDK) || defined(__APPLE__) || defined(__linux__)
#define _T_WO_GUI
#endif

class CTBitmap;


class CTDeviceCtx;
class CListItem
{
public:
   enum {EType =-2};
   int iType;
   enum{eBuddy=1,eLast};
   virtual int getValueByID(int id, void **pResp){return 0;}

   CListItem(int id=0):iItemId(id),iVisible(1) {
      prev=next=NULL;
      iType=EType;
      uiCrc=0;
#ifndef _T_WO_GUI
      vo=NULL;
      iColSelected=-3;
#endif
   }
   virtual void* getEditByName(char *p){return NULL;}

   virtual ~CListItem() {

   }
   virtual int isItem(void *p, int iSize){return 0;}

   
   CListItem *prev;
   CListItem *next;

   int iItemId;
   unsigned int uiCrc;
   int iVisible;
   
#ifndef _T_WO_GUI
   CTBitmap *vo;
   int iColSelected;



  // virtual int canDrawSelected(){return 0;}
   virtual inline int drawSelecet(){return 1;}

 //  virtual int drawBackGround(CTDeviceCtx & iBase, int x, int y, int x1, int y1, int iXOff, int iYOff, int iSelected);//{return 0;}   
   virtual int drawObject(CTDeviceCtx & iBase, int x, int y, int x1, int y1, int iXOff, int iYOff){return 0;};
   inline virtual int drawControl(CTDeviceCtx & iBase, int x, int y, int x1, int y1, int iXOff, int iYOff)
   {
      return drawObject(iBase, x, y, x1, y1, iXOff, iYOff);
   }
   inline virtual int drawSelelcted(CTDeviceCtx & iBase, int x, int y, int x1, int y1, int iXOff, int iYOff){
      return drawControl(iBase, x, y, x1, y1, iXOff, iYOff);
   }
#endif

};

template<class T, int iSizeT>
class CListItemT: public CListItem{
   T t_value[iSizeT];
public:
   T &getValue(int id=0){return t_value[id];}
   inline int getValueCnt(){return iSizeT;}
   CListItemT(int idx=-1):CListItem(idx){}

};
//#include <string.h>

template<class T, int iSizeT>
class CListItemTName: public CListItem{
   T t_value[iSizeT];
public:
   char bufItemN[64];
   int iBufNLen;
   T &getValue(int id=0){return t_value[id];}
   inline int getValueCnt(){return iSizeT;}
   CListItemTName(char *name):CListItem(0){
    //  strcpy(bufItemN,name);
      iBufNLen=0;
      for(iBufNLen=0;*name;iBufNLen++)
      {

         bufItemN[iBufNLen]=*name;
         name++;
      }
      //while(*name){iBufNLen++;name++;}
      bufItemN[iBufNLen]=0;
   }
   virtual int isItem(void *p, int iSize){
      int isEqual(char *src,char *dst,int iLen);
      return iBufNLen==iSize && isEqual(( char*)p,&bufItemN[0],iSize);
   }


};
//#include <stdio.h>

class CTList{
public:
   int iCanChange;
   void *pUserStorage;
   CTList():iCanChange(1),lroot(NULL),tail(NULL){pUserStorage=NULL;}
   virtual ~CTList()
   {

      removeAll();
   }
   //CListItem *getRoot(){return lroot;}

   typedef int (FNC_LIST_CALLBACK)(CListItem *item, void *pUserData);
   void callBack(FNC_LIST_CALLBACK *p, void *pUserData)
   {
      CListItem *tmp=lroot;
      while(tmp)
      {
         p(tmp,pUserData);
         tmp=tmp->next;
      }
   }
   inline CListItem *getLRoot(){return lroot;}
   CListItem *getLTail(){return tail;}
   void removeAll()
   {
      if(!iCanChange)return;
  //--    printf("[rem all %p %p]",lroot,tail);
      
      CListItem *tmp=lroot;
      CListItem *del;
      while(tmp)
      {
         del=tmp;
         tmp=tmp->next;
         del->next=NULL;
         del->prev=NULL;
  //--       printf("[rem all del %p]",del);
         onRemove(del);

         delete del;
      }
      lroot = tail = NULL;
   
   }
   void removeLast()
   {
      remove(tail);
   }
   void removeById(int id)
   {
      CListItem *item = getById(id);
      if(item)
         remove(item);
   }
   /*
   void moveToRoot(CListItem *item)
   {
      if(item==lroot)return;
      if(item->prev)
      {
         item->prev->next=item->next;
      }
      if(item->next)
      {
         item->next->prev=item->prev;
      }
      if(lroot)lroot->prev=item;
      item->prev=NULL;
      item->next=lroot;
      lroot=item;


   }
*/   
   int countVisItems()
   {
      CListItem *tmp=lroot;
      int i=0;
      while(tmp)
      {
         if(tmp->iVisible)i++;
         tmp=tmp->next;
      }
      return i;
   }

   void moveToRoot(CListItem *item)
   {
      remove(item,0);
      addToRoot(item);
   }
   void remove(CListItem *item, int iDel=1)
   {
      if(!iCanChange)return;
      if(!item)return;
      //TODO check
      //item=findItem(item)
      onRemove(item);

      if(item==tail)
      {
         if(item->prev)
            tail=item->prev;
         else
            tail=NULL;
      }
      if(item==lroot)
      {
         if(item->next)
            lroot=item->next;
         else
            lroot=NULL;
      }
      if(item->prev)
         item->prev->next=item->next;
      else
         lroot=item->next;

      if(item->next)
         item->next->prev=item->prev;
      else
         tail=item->prev;

      if(iDel)
         delete item;
      else
      {
         item->next=NULL;
         item->prev=NULL;
      }
   }
   CListItem * findItem(CListItem *item)
   {
      CListItem *tmp=lroot;
      while(tmp)
      {
         if(tmp==item)
         {
            break;
         }
         tmp=tmp->next;
      }
      return tmp;
   }
   CListItem *findItem(const void *p,int iSize)
   {
      CListItem *tmp=lroot;
      while(tmp)
      {
         if(tmp->isItem((void*)p,iSize))
         {
            break;
         }
         tmp=tmp->next;
      }
      return tmp;
   }
   CListItem *findItem(unsigned int crc, const void *p,int iSize)
   {
      CListItem *tmp=lroot;
      while(tmp)
      {
         if(crc == tmp->uiCrc && tmp->isItem((void*)p,iSize))
         {
            break;
         }
         tmp=tmp->next;
      }
      return tmp;
   }
   CListItem *getById(int id)
   {
      CListItem *tmp=lroot;
      while(tmp)
      {
         if(tmp->iItemId==id)
         {
            break;
         }
         tmp=tmp->next;
      }
      return tmp;
   }
   void addAfter(CListItem *after, CListItem *item)
   {
      if(item)item->next=item->prev=NULL;
      if(lroot==NULL || after==NULL)
      {
        // addToTail(item);
         addToRoot(item);
         return;
      }
      else if(after==tail )
      {
         addToTail(item);
         return;
      }
      item->prev=after;
      item->next=after->next;

      if(after->next)
         after->next->prev=item;
      after->next=item;

      onInsert(item);


   }
   void addToRoot(CListItem *item)
   {
      if(item)item->next=item->prev=NULL;
      if(lroot)
      {
         item->next=lroot;
         lroot->prev=item;
      }

      if(tail==NULL)
         tail=item;

      lroot=item;

      onInsert(item);
   }
   void setList(CTList *l)
   {
      if(iCanChange==0)
      {
         lroot=l->getLRoot();
         tail=l->getLTail();
      }
   }
   void addToTail(CListItem *item)
   {
      if(item)item->next=item->prev=NULL;
      if(tail)
      {
         tail->next=item;
      }
      item->prev=tail;

      tail=item;
      if(lroot==NULL)
         lroot=item;

      onInsert(item);
   }
   CListItem *getNext(CListItem *cur=NULL, int iAll=0) 
   {
      CListItem *tmp=cur?cur->next:lroot;
      while(tmp)
      {
         if(iAll|| tmp->iVisible)break;//visible = filtred
         tmp=tmp->next;
      }
      return tmp;
   }
   CListItem *getPrev(CListItem *cur=NULL, int iAll=0) 
   {
      CListItem *tmp=cur?cur->prev:tail;
      while(tmp)
      {
         if(iAll || tmp->iVisible)break;//visible = filtred
         tmp=tmp->prev;
      }
      return tmp;
   }

protected:
   CListItem *lroot;
   CListItem *tail;
   int iNeedProtect;

   virtual void onInsert(CListItem *item){}
   virtual void onRemove(CListItem *item){}

};
#endif