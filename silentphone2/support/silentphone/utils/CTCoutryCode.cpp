//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

#define _T_WO_GUI
#include "../baseclasses/CTEditBase.h"
#include "../baseclasses/CTListBase.h"

const char *pTest=
"1:7721:kz:Kazakhstan:Karaganda:\r\n"
"1:7711:kz:Kazakhstan:Chapaev:\r\n"
"1:1403:ca:Canada:Alberta:\r\n"
"1:1905:ca:Canada:Ontario:\r\n"
"1:1:us:USA::\r\n"
"3:371:lv:Latvia::\r\n"
"3:37167:lv:Latvia:Riga:\r\n"
"3:3712:lv:Latvia:Mobile:\r\n"
"2:93:ag:Avg:Mobile:\r\n";

class CListItemStringCountry: public CListItem{

public:
   CTEditBuf<64> countryName;
   CTEditBuf<32> city;
   int iCountryCodeLen;
   char ccode[8];
   char sz2code[4];
   
   char idd[8];
   char ndd[8];
   
   CListItemStringCountry(char *country,char *code):CListItem(atoi(code)){strcpy(ccode,code);countryName.setText(country);iCountryCodeLen=(int)strlen(code);}
   
   CListItemStringCountry(char *country,int iCLen,char *code, int iCodeLen=0):CListItem(atoi(code)){
      sz2code[0]=0;
      countryName.setText(country,iCLen);
      if(iCodeLen<=0)iCodeLen=(int)strlen(code);
      strncpy(ccode,code,iCodeLen);ccode[iCodeLen]=0;
      iCountryCodeLen=(int)strlen(code);
      idd[0]=0;
      ndd[0]=0;
   }
   
   CListItemStringCountry *copy(){
      CListItemStringCountry *r = new CListItemStringCountry(this);
      
      return r;
   }
private:
   CListItemStringCountry(CListItemStringCountry *p):CListItem(p->iItemId){
      countryName=p->countryName;
      city=p->city;
      iCountryCodeLen=p->iCountryCodeLen;
      strcpy(ccode ,p->ccode);
      strcpy(sz2code ,p->sz2code);
      strcpy(idd ,p->idd);
      strcpy(ndd ,p->ndd);
      
      
   }
};

static int toNR(char *out, int iOutLen, const char *in){
   int i=0;
   int l=0;
   
   for(;l<iOutLen;){
      if(isdigit(in[i])){*out=in[i];out++;l++;}
      i++;
      if(!in[i] || in[i]=='@')break;
   }
   *out=0;
   return l;
}

static int remSP(char *out, int iOutLen, const char *in){
   int i=0;
   int l=0;
   
   for(;l<iOutLen;){
      if(isalnum(in[i]) || in[i]=='+'){*out=in[i];out++;l++;}
      i++;
      if(!in[i] || in[i]=='@')break;
   }
   *out=0;
   return l;
}

static int isNR(const char *nr){
   if(!nr)return 0;
   while(nr[0]){
      if(isalpha(nr[0]))return 0;
      nr++;
   }
   return 1;
}

class CTCountryCode{
   // CTList ccList;
   enum{eAnd=1023,eLists=1024};
   CTList ccListI[eLists];
   
   CTList countryList;
   
   CListItemStringCountry *selected;
   
   char path[512];
   char szCurCCode[16];
   
   inline CTList *list(char *code){
      return list((unsigned int )atoi(code));
   }
   inline CTList *list(unsigned int c){
      //  unsigned int crc32_calc(const void *ptr, size_t cnt, unsigned int crc);
      //  c=(unsigned int)crc32_calc(&c,sizeof(c),0xEDB88320);
      return &ccListI[c&eAnd];
   }
   
   char *getNext(char *p, int &iLen, int &iLeft){
      int i=0;
      iLen=0;
      for(i=0;iLeft>0;i++,iLeft++){
         if(*p==':'){p++;break;}
         iLen++;
         p++;
      }
      
      return p;
   }
   int loadRow2(char *p, int iLeft){
      //3:3712:lv:Latvia:Mobile:00::\r\n"
      //2:49:de:Germany::00:0:
      char *start=p;
      while(*p && !isdigit(*p) && iLeft>0){p++;iLeft--;}
      int iLen;
      int iCCLen=atoi(p);
      p=getNext(p,iLen,iLeft);
      int iCodeLen;
      char *pCode=p;
      p=getNext(p,iCodeLen,iLeft);
      char *p2Code=p;
      p=getNext(p,iLen,iLeft);
      int iCLen;
      char *pC=p;
      p=getNext(p,iCLen,iLeft);
      int iCityLen;
      char *pCity=p;
      p=getNext(p,iCityLen,iLeft);
      
      /*
       CTEditBuf<64> countryName;
       CTEditBuf<32> city;
       int iCountryCodeLen;
       char ccode[8];
       char sz2code[4];
       */
      
      CTList *l=list(pCode);
      CListItemStringCountry *i=new CListItemStringCountry(pC,iCLen,pCode,iCodeLen);
      
      i->iCountryCodeLen=iCCLen;
      strncpy(i->sz2code,p2Code,2);i->sz2code[2]=0;
      i->city.setText(iCityLen?pCity:"",iCityLen);
      l->addToTail(i);
      
      int ln;
      
      char *pTmp = p;
      p=getNext(p,ln,iLeft);
      snprintf(i->idd,sizeof(i->idd),"%.*s",ln,pTmp);
      pTmp=p;
      p=getNext(p,ln,iLeft);
      snprintf(i->ndd,sizeof(i->ndd),"%.*s",ln,pTmp);
      
      addCountrySorted(i->copy());
      
      
      
      while(*p && *p<' ' && iLeft>0){p++;iLeft--;}
      
      
      
      return p-start;
   }
   
   void addCountrySorted( CListItemStringCountry *item)
   {
      CListItemStringCountry *ret=(CListItemStringCountry *)countryList.getNext();
      CListItemStringCountry *prev=NULL;
      
      while(ret)
      {
         int cmpU(CTStrBase *b1, CTStrBase *b2);
         if(cmpU(&ret->countryName,&item->countryName)>0)
         {
            break;
         }
         prev=ret;
         ret=(CListItemStringCountry *)countryList.getNext(ret);
      }
      countryList.addAfter(prev,item);
      
      // return  (CListItem*)prev;
   }
   
   int loadRow(char *p, int iLeft){
      int iCLen=0;
      char *start=p;
      while(!(p[0]==',' && p[1]=='+') && iLeft>1){
         iLeft--;
         iCLen++;
         p++;
      }
      p+=2;
      iLeft-=2;
      char *pcode=p;
      while(isdigit(p[0]) && iLeft>0){p++;iLeft--;}
      
      p[0]=0;
      
      if(start[iCLen]=='\"')iCLen--;
      if(start[0]=='\"')iCLen--;
      
      CTList *l=list(pcode);
      l->addToTail(new CListItemStringCountry(start[0]=='\"'?(start+1):start,iCLen,pcode));
      
      while(!isalnum(p[0]) && iLeft>0){p++;iLeft--;}
      
      return p-start;
   }
   void loadCC(){
      //szCountryListFN
      if(!path[0]){
         return;
      }
      int iLen;
      char *p;
      p=loadFile(&path[0],iLen);
      loadCC_p(p,iLen);
      delete p;
   }
   void loadCC_p(char *p, int iLen){
      if(!p)return;
      
      while(iLen>10){
         int l=loadRow2(p,iLen);
         iLen-=l;
         p+=l;
      }
      /*
       for(int i=0;i<eLists;i++){
       printf("l=%d\n",ccListI[i].countVisItems());
       }
       */
      
   }
public:
   int findCC(const char *szX){
      char szB[64];
      char *sz=&szB[0];
      char *cc=NULL;
      int i,l;
      
      for(int i=0;;i++){
         if(!szX[i])break;
         if(!isdigit(szX[i]) && szX[i]!='+' && szX[i]!='-' && szX[i]!='(' && szX[i]!=')' && szX[i]!=' ')return -1;
      }
      l=remSP(sz,63,szX);
      
      
      
      int iFail=0;
      if(l==11 && sz[0]=='1'){//PRZ asked US cc
         cc=&sz[0];
      }
      else if(sz[0]=='0' && sz[1]=='0'){
         cc=&sz[2];
      }
      else if(sz[0]=='+'){
         cc=&sz[1];
      }
      else {iFail=1;}
      
      do{
         if(l<3){iFail=1;}
         if(iFail)break;
         char *p=cc;
         while(*p){
            if(isdigit(!p[0])){iFail=1;break;}
            p++;
         }
         if(iFail)break;
         
         for(i=(min(l,8));i>0;i--){
            cc[i]=0;
            if(setCC(cc, i))return 0;
         }
         iFail=1;
         
      }while(0);
      
      
      
      if(iFail){return -1;}
      return 0;
   }
   private:
   int setCC(char *cc, int iLen){
      CListItemStringCountry *li=NULL;
      
      
      int c=atoi(cc);
      li=(CListItemStringCountry*)list((unsigned int)c)->getById(c);
      
      if(!li){
         return 0;
      }
      //  countryName->setText(li->countryName);
      strcpy(szCurCCode,cc);
      //lbCCode->setText("00",2);
      //  lbCCode->addText(&this->szCurCCode[0]);
      selected=li;
      //ccList.select(li);
      //btCountry->lb->setText(*li);
      return 1;
   }
   
   void reset(){szCurCCode[0]=0;selected=NULL;}
   int iInitOk;
public:
   CTCountryCode(){path[0]=0;iInitOk=0;}
   void init(char *pBuf, int iLen){if(iInitOk)return;loadCC_p(pBuf,iLen);iInitOk=1;}
   CTCountryCode(const char *p){strcpy(path,p);loadCC();iInitOk=1;}
   CTCountryCode(char *pBuf, int iLen){path[0]=0;loadCC_p(pBuf,iLen);iInitOk=1;}
   
   int findCountry(const char *nr, CTEditBase *country){
      
      reset();
      if(findCC(nr)<0)return -1;
      if(selected){
         country->setText(selected->countryName);
         return selected->iCountryCodeLen;
      }
      return 0;
   }
   CListItemStringCountry *getSelected(){
      return selected;
   }
   
   char tmp_getCountryByID[64];
   const char *getCountryByID(const char *p){

      CListItemStringCountry *c = findCountryRecord(p);
      if(!c)return "";
      int l = sizeof(tmp_getCountryByID)-1;
      return c->countryName.getTextUtf8(tmp_getCountryByID, &l);
   }
   
   CListItemStringCountry *findCountryRecord(const char *courtyID){
      
      if(!courtyID || courtyID[0]==0 || ('e'==courtyID[0] && 'n'==courtyID[1] && courtyID[2]==0))courtyID = "us";
      
      if(courtyID[2])return findCountryByName(courtyID);
      
      
      for(int i=0;i<eLists;i++){
         CTList *l = &ccListI[i];
         
         CListItemStringCountry *it = (CListItemStringCountry *)l->getNext(NULL,1);
         while(it){
            if(it->sz2code[0]==courtyID[0] && it->sz2code[1]==courtyID[1]){
               return it;
            }
            it =(CListItemStringCountry *)l->getNext(it,1);
         }
      }
      return NULL;
   }
   
   CListItemStringCountry *findCountryByName(const char *name){
      
      int nl = (int)strlen(name);
      
      for(int i=0;i<eLists;i++){
         CTList *l = &ccListI[i];
         
         CListItemStringCountry *it = (CListItemStringCountry *)l->getNext(NULL,1);
         while(it){
            if(nl == it->countryName.getLen() &&  it->countryName == name){
               return it;
            }
            it =(CListItemStringCountry *)l->getNext(it,1);
         }
      }
      return NULL;
   }
   
   int fillCountrys(char *out, int iMaxLen){
      
      int len = 0;
      char bufC[64];
      
      int t_snprintf(char *buf, int iMaxSize, const char *format, ...);
      
      for(int i=0;i<1;i++){
         CTList *l = &countryList;//&ccListI[i];
         
         CListItemStringCountry *it = (CListItemStringCountry *)l->getNext(NULL,1);
         while(it){
            
            if((it->idd[0] || it->ndd[0]) && !hasInCountryInList(it)){
               
               getText(bufC,63, &it->countryName);
               len += t_snprintf(out+len, iMaxLen-len, "%s,",bufC);
              // puts(bufC);
            }
            
            it =(CListItemStringCountry *)l->getNext(it,1);
         }
      }
      if(len){len--;out[len]=0;}//rem last ,
      return len;
   }
   

private:
   int hasInCountryInList(CListItemStringCountry *cur){
      for(int i=0;i<1;i++){
         CTList *l = &countryList;//&ccListI[i];
         
         CListItemStringCountry *it = (CListItemStringCountry *)l->getNext(NULL,1);
         while(it){
            if(it==cur)return 0;
            
            if((it->idd[0] || it->ndd[0])){
               int r = it->sz2code[0]==cur->sz2code[0] && it->sz2code[1]==cur->sz2code[1];
               
               if(r)return 1;
            }
            
            it =(CListItemStringCountry *)l->getNext(it,1);
            
         }
      }
      return 0;
   }
   
};

static int fixNR_pattern(const char *pattern, int iLen, const char *in, char *out, int iLenMax){
   int i=0;
   int iPatternEnded=0;
   for(;i<iLenMax;){
      
      if(!iPatternEnded && *pattern==0)iPatternEnded=1;
      if(iPatternEnded || *pattern=='#'){
         *out=*in;
         in++;
         iLen--;
         out++;
      }
      else{
         *out=*pattern;
         out++;
      }
      pattern++;
      i++;
      if(iLen<=0)break;
   }
   if(i<iLenMax && !iPatternEnded && (pattern[2]!=0 && pattern[1]!=0 && *pattern!=0 && (*pattern==')' || pattern[1]==')' || pattern[2]==')'))){i++;*out=')';out++;}
   *out=0;
   return i;
}

static int fixNR_CC(int iCCLen, CListItemStringCountry *li, const char *in, char *out, int iLenMax){
   char bufTmp[64];
   
   int iOutLen=0;
   
   if(iCCLen>0){
      if(in[0]=='+')
      {out[0]=in[0];in++;iOutLen++;}
      else if((in[0]=='0' && in[1]=='0') )
      {out[0]=in[0];iOutLen++;in++;out[1]=in[0];iOutLen++;in++;}
   }
   //  printf("iOutLen=%d,iCCLen=%d\n",iOutLen,iCCLen);
   int iLen=toNR(&bufTmp[0], 63, in);
   
   out[iOutLen]=0;
   
   if(iCCLen==0){
      if(iLen==10 &&  in[0]!='+')
         iOutLen+=fixNR_pattern("(###) ###-#### ####",iLen,&bufTmp[0],&out[iOutLen],iLenMax-iOutLen);
      else{
         return 0;
      }
   }
   else if(iCCLen==1){
      iOutLen+=fixNR_pattern("# (###) ###-#### ####",iLen,&bufTmp[0],&out[iOutLen],iLenMax-iOutLen);
   }
   else if(iCCLen==3 && iLen==11){
      iOutLen+=fixNR_pattern("### ####-####",iLen,&bufTmp[0],&out[iOutLen],iLenMax-iOutLen);
   }
   else if(iCCLen==2  && iLen==11){
      iOutLen+=fixNR_pattern("## #### ####",iLen,&bufTmp[0],&out[iOutLen],iLenMax-iOutLen);
   }
   else {
      int iUsed=0;
      iOutLen+=fixNR_pattern("########",iCCLen,&bufTmp[0],&out[iOutLen],iLenMax-iOutLen);
      iUsed+=iCCLen;
      int iLeft=iLen-iCCLen;
      if(iLeft<5){
         iOutLen+=fixNR_pattern(" ####",iLeft,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);
         iUsed+=iLeft;
      }
      else if(iLeft<13){
         const char *pat[]={
            " ## ###"," ### ###"," ### ####"," #### ####"," ### ### ###",
            " ### ### ####"," ### #### ####"," #### #### ####"};
         iOutLen+=fixNR_pattern(pat[iLeft-5],iLeft,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);
         iUsed+=iLeft;
      }
      else{
         int ix=iLeft&3;
         
         if(ix==1){
            iOutLen+=fixNR_pattern(" ##",2,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);iUsed+=2;
            iOutLen+=fixNR_pattern(" ###",3,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);iUsed+=3;
            iLeft-=5;
         }
         else if(ix==2){
            iOutLen+=fixNR_pattern(" ###",3,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);
            iUsed+=3;
            iOutLen+=fixNR_pattern(" ###",3,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);
            iLeft-=6;
            iUsed+=3;
         }
         else if(ix==3){
            iOutLen+=fixNR_pattern(" ###",3,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);
            iUsed+=3;
            iLeft-=3;
         }
         
         for(;iLeft>0;iLeft-=4){
            iOutLen+=fixNR_pattern(" ####",4,&bufTmp[iUsed],&out[iOutLen],iLenMax-iOutLen);
            iUsed+=4;
         }
      }
      
   }
   if(iOutLen && (out[iOutLen-1]==' ' || out[iOutLen-1]=='-'))iOutLen--;
   // if(iOutLen>1 && (out[iOutLen-1]==')' || out[iOutLen-1]=='-'))iOutLen--;
   out[iOutLen]=0;
   return iOutLen;
   
}

int fixNR(void *p, const char *in, char *out, int iLenMax){
   CTCountryCode *c=(CTCountryCode*)p;
   CTEditBuf<64> b;
   //PRZ in[0]=='1'
   int iCheckCC=0;
   if(in[0]=='1' || in[0]=='+'){iCheckCC=1;}
   else if(in[0]=='0' && in[1]=='0'){ iCheckCC=1;}
   // else {strncpy(out,in,iLenMax);out[iLenMax]=0;return 0;}
   
   if(in[0]=='.'){strncpy(out,in,iLenMax);out[iLenMax]=0;return 1;}
   
   
   int l=0;
   if(iCheckCC){
      l=c->findCountry(in,&b);
      
      if(l>0){
         fixNR_CC(l,c->getSelected(),in,out,iLenMax);
      }
   }
   else {
      if(0&&in[0]!='+'){
         l=fixNR_CC(0,NULL,in,out,iLenMax);
      }
   }
   if(l<=0)
      strncpy(out,in,iLenMax);
   
   out[iLenMax]=0;
   
   return l>0;
}



CTCountryCode cc;
void initCC(char *p, int iLen){
   cc.init(p, iLen);
}

#define US_NR_LEN 10

int canAddUS_CCode(const char *nr){
   if(*nr=='0' || *nr=='1' || *nr=='+')return 0;
   int iLen=(int)strlen(nr);
   
   char nrClean[US_NR_LEN+2];
   int iCleanLen=1;nrClean[0]='1';
   
   if(iLen<US_NR_LEN || iLen>63)return 0;
   
   for(int i=0;i<iLen;i++){
      if(isalpha(nr[i]))return 0;
      if(nr[i]=='@'){break;}
      if(isdigit(nr[i])){nrClean[iCleanLen]=nr[i];iCleanLen++; if(iCleanLen>US_NR_LEN+1)return 0;}
   }
   if(iCleanLen!=US_NR_LEN+1)return 0;
   nrClean[iCleanLen]=0;
   
   
   
   CTEditBuf<64> b;
   cc.findCountry(nrClean,&b);
   
   CListItemStringCountry *s=cc.getSelected();
   
   if(!s)return 0;
   
   return s->city.getLen()>0?1:0;
}

int fixNR(const char *in, char *out, int iLenMax){
   return fixNR(&cc,in,out,iLenMax);
}

int findCSC_C_S(const char *nr, char *szCountry, char *szCity, char *szID, int iMaxLen){
   char tmp[128];
   if(fixNR(nr,tmp,127)<=0)return 0;
   CListItemStringCountry *i=(CListItemStringCountry*)cc.getSelected();
   if(!i)return 0;
   
   int l=toNR(tmp,127, nr);
   
   if(tmp[0]=='1' && l<4)
      return 0;//tmp fix
   
   if(l && tmp[0]=='.')return 0;
   
   szCity[0]=szID[0]=szCountry[0]=0;
   l=iMaxLen;i->countryName.getTextUtf8(szCountry,&l);
   l=iMaxLen;i->city.getTextUtf8(szCity,&l);
   
   strcpy(szID,i->sz2code);
   
   
   return !!(szCountry[0] && szID[0]);
}

int findCSC_C_S_java(const char *nr, char *ret, int iMaxLen){
   char tmp[128];
   if(fixNR(nr,tmp,127)<=0)return 0;
   CListItemStringCountry *i=(CListItemStringCountry*)cc.getSelected();
   if(!i)return 0;
   
   int l=toNR(tmp,127, nr);
   
   if(tmp[0]=='1' && l<4)
      return 0;//tmp fix
   
   if(!i->sz2code[0])return 0;
   int pos=2;
   strcpy(ret,i->sz2code);
   ret[pos]=':';pos++;
   
   iMaxLen-=l;l=iMaxLen;
   i->countryName.getTextUtf8(&ret[pos],&l);
   if(!l)return 0;
   pos+=l;
   ret[pos]=':';pos++;
   
   iMaxLen-=l;l=iMaxLen;
   i->city.getTextUtf8(&ret[pos],&l);
   pos+=l;
   
   return 1;
   
}

#include "CTNumberHelper.h"

class CTNumberHelper: public CTNumberHelperBase{
   int iHasUpdated;
   char bufUpdated[64];
   char bufDialed[50];
   char countryID[64];
   CListItemStringCountry *cr;//
   char *countryList;
public:
   CTNumberHelper(const char *_countryID){
      cr=NULL;
      clear();
      countryList=NULL;
      setID(_countryID);
   }
   ~CTNumberHelper(){
      if(countryList)delete countryList;
      countryList=NULL;
   }
/*
   CTList *newSupportedCountryList(){
      CTList *l = new CTList();
      return l;
   }
  */
   void setID(const char * _countryID){
      
      if(!_countryID || !_countryID[0])_countryID = "us";
      
      if(strcmp(countryID,_countryID)==0 && cr)return;
      
      strncpy(countryID,_countryID,sizeof(countryID));
      countryID[sizeof(countryID)-1]=0;
      iHasUpdated=0;
      bufUpdated[0]=0;
      
      cr = cc.findCountryRecord(countryID);
   }
   int canModifyNR(){return cr && (cr->idd[0] || cr->ndd[0]);}
   
   void clear(){bufUpdated[0]=0;iHasUpdated=0;bufDialed[0]=0;}
   
   const char *tryUpdate(const char *nr){
      if(setDialed(nr))return bufUpdated;
      return nr;
   }
   
   char nrWONdd[128];
   
   const char *tryRemoveNDD(const char *nr){
      if(iHasUpdated==2)return nr;
      int l = (int)strlen(nr);
      
      if(l>=sizeof(nrWONdd))return nr;
      if(l<7)return nr;
      
      if(nr[0]=='+'){//fix +44(0)123123, will remove (0)
         char bufD[128];
         remSP(bufD, sizeof(bufD)-1, nr);
         int r = cc.findCC(bufD);
         if(r<0)return nr;
         
         CListItemStringCountry *c = cc.getSelected();
         if(!c)return nr;
         
         if(!c->ndd[0]){iHasUpdated=2; return nr;}
         
         char bufCC_NDD[16];
         int ll = snprintf(bufCC_NDD, sizeof(bufCC_NDD), "+%.*s%s",c->iCountryCodeLen,c->ccode,c->ndd);

         if(strncmp(bufD,bufCC_NDD,ll)!=0)return nr;
         
         snprintf(nrWONdd, sizeof(nrWONdd),"+%.*s%s",c->iCountryCodeLen,c->ccode, &bufD[ll]);
         iHasUpdated=2;
         return &nrWONdd[0];
      }
      return nr;
   }
   
   int setDialed(const char *nr){
      if(iHasUpdated)return 0;
      int l = (int)strlen(nr);
      
      if(l>=sizeof(bufDialed))return 0;
      
      strncpy(bufDialed, nr, sizeof(bufDialed));
      bufDialed[sizeof(bufDialed)-1]=0;

      if(l<7)return 0;
      
      if(nr[0]=='+'){
         return 0;
      }
 
      if(!cr)return 0;
      
      if(!isdigit(nr[0]) || !isNR(nr))return 0;
      
      remSP(bufDialed, sizeof(bufDialed)-1, nr);

      int iIsNA = cr->ndd[0]==0 && strcmp(cr->idd, "011")==0;
      
      nr = &bufDialed[0];
      
      int nddl = (int)strlen(cr->ndd);
      int iddl = (int)strlen(cr->idd);
      
      printf(" ndd[%s] idd[%s]\n",cr->ndd, cr->idd);
      
      
      /*
       //#SP-492
       
       OBS: If user has set dialing helper to US it is expected that international calls will be dialed as 011 or +. And if there is an incoming call from UK caller id will be presented as 0044xxx, and in this case user will not be able to call back as number will be translated to +10044
       EXP: SP needs to treat 00 similarly as 011 or +, should not modify the number and send as +e164 form.
       */
      if(iIsNA && strncmp(bufDialed,"00",2)==0){
         snprintf(bufUpdated, sizeof(bufUpdated), "+%s", &bufDialed[2]);
         iHasUpdated=1;
         return 1;
      }
      
      //if ndd.len=0 && idd=="011" //north america
      //we have to support old dialing 1 555 333 4444
      if(bufDialed[0]=='1' && iIsNA && strncmp(cr->idd,bufDialed,iddl)!=0){
         snprintf(bufUpdated, sizeof(bufUpdated), "+%s", &bufDialed[nddl]);
         iHasUpdated=1;
         return 1;
      }
      
      if(nddl>iddl){
         if(nddl==0 ||  strncmp(cr->ndd,bufDialed,nddl)==0){
            iHasUpdated=1;
            snprintf(bufUpdated, sizeof(bufUpdated), "+%s%s", cr->ccode, &bufDialed[nddl]);

         }
         else if(iddl==0 || strncmp(cr->idd,bufDialed,iddl)==0){
            iHasUpdated=1;
            snprintf(bufUpdated, sizeof(bufUpdated), "+%s", &bufDialed[iddl]);
         }
      }
      else{
         if(iddl==0 || strncmp(cr->idd,bufDialed,iddl)==0){
            iHasUpdated=1;
            snprintf(bufUpdated, sizeof(bufUpdated), "+%s", &bufDialed[iddl]);
         }
         else if(nddl==0 || strncmp(cr->ndd,bufDialed,nddl)==0){
            iHasUpdated=1;
            snprintf(bufUpdated, sizeof(bufUpdated), "+%s%s", cr->ccode, &bufDialed[nddl]);
         }
      }
      
      return iHasUpdated;
      
   }
   
   const char *getUpdated(){return iHasUpdated ? &bufUpdated[0] : &bufDialed[0];}
   
   const char *getDialingPrefCountryList(){
      if(0)return "set_country or call initCC";
      //return "set_country or call initCC";
      
      const int iMaxSize = 400 * 80;//max 400 countrys and 80 chars per a country name
      if(!countryList){
         countryList = new char [iMaxSize];
         if(!countryList) return "out of mem,getDialingPrefCountryList";
         countryList[0]=0;
      }
      else if(countryList[0])return countryList;
      
      cc.fillCountrys(countryList, iMaxSize);
      
      return countryList;
   }
   
};

CTNumberHelper glob_mod("en");
CTNumberHelperBase *g_getDialerHelper(){return &glob_mod;}

const char *getDialingPrefCountryList(void){
   return glob_mod.getDialingPrefCountryList();
}

const char *getCountryByID(const char *p){
   return cc.getCountryByID(p);
}

int tryToFixNumber(const char *countryID, const char *nr, char *dst, int iMaxLen){
   dst[0]=0;
   if(nr[0]=='+'){
      return 0;
   }
   
   CTNumberHelper ch(countryID);
   
   if(!ch.canModifyNR()){
      return 0;
   }
   
   strncpy(dst,ch.tryUpdate(nr),iMaxLen);dst[iMaxLen-1]=0;
   
   return 1;
}

#ifdef TEST_NR_FIX_CC

void testNRFix(const char *countryID, const char *nr){
   char buf[64];
   int r= tryToFixNumber(countryID, nr, buf, sizeof(buf));
   printf("%s_nr=[%s],ret=%d[%s]\n",countryID,nr,r,buf);
}

void fnTest(void *p, const char *nr){
   char buf[64];
   fixNR(p,nr,&buf[0],63);
   printf("%s->[%s]\n",nr,buf);
}




int main(){
   CTCountryCode c("Country.txt");
   
   cc=c;//quick hack, will have seg-fault at end
   
   CTEditBuf<64> b;
   const char *nr="+37122112211";
   int l=c.findCountry(nr,&b);
   if(l>0){
      if(nr[0]=='+')l++;else if((nr[0]=='0' && nr[1]=='0') )l+=2;
   }
   if(l>0){
      char buf[64];
      int m=63;
      printf("cc=%.*s,name=%s\n",l,nr,b.getTextUtf8(&buf[0],&m));
   }
   fnTest(&c,nr);
   
   fnTest(&c,"+15552224444");
   fnTest(&c,"+38552224444");
   fnTest(&c,"+93123");
   fnTest(&c,"+931234");
   fnTest(&c,"+9312345");
   fnTest(&c,"+93123456");
   fnTest(&c,"+931234567");
   fnTest(&c,"+9312345678");
   fnTest(&c,"+93123456789");
   fnTest(&c,"+931234567890");
   fnTest(&c,"+9312345678901");
   fnTest(&c,"+93123456789012");
   fnTest(&c,"+931234567890123");
   fnTest(&c,"+9312345678901234");
   
   testNRFix("lv","0022112211");
   testNRFix("lv","22112211");
   
   testNRFix("de","08822112211");
   testNRFix("de","8822112211");
   
   testNRFix("us","4582224444");
   testNRFix("us","0113715552224444");
   
   testNRFix("ru","8103715552224444");
   testNRFix("ru","85552224444");
   testNRFix("ru","3715552224444");
   
   testNRFix("dz","37122112211");
   testNRFix("dz","01212121221");
   return 0;
   
   int x;
   char buf[64];
   for(int i=0;i<200;i++){
      sprintf(buf,"+%u",rand()*1122000+rand()*32121+rand()+1000000);
      // fnTest(&c,buf);
      l=c.findCountry(buf,&b);
      if(l>0){
         if(nr[0]=='+')l++;else if((nr[0]=='0' && nr[1]=='0') )l+=2;
         if(l>0){
            char bufcn[64];
            int m=63;
            printf("nr=%s cc=%.*s,name=%s\n",buf,l,buf,b.getTextUtf8(&bufcn[0],&m));
         }
      }
   }
   
   
   return 0;
}
/*
g++ CTCoutryCode.cpp ../../sources/utils/utils.cpp ../../sources/baseclasses/CTEditBase.cpp -D=TEST_NR_FIX_CC -o t
 */
#endif

