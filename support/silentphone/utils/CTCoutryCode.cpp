/*
Created by Janis Narbuts
Copyright © 2004-2012 Tivi LTD,www.tiviphone.com. All rights reserved.
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
   
   CListItemStringCountry(char *country,char *code):CListItem(atoi(code)){strcpy(ccode,code);countryName.setText(country);iCountryCodeLen=strlen(code);}
   
   CListItemStringCountry(char *country,int iCLen,char *code, int iCodeLen=0):CListItem(atoi(code)){
      sz2code[0]=0;
      countryName.setText(country,iCLen);
      if(iCodeLen<=0)iCodeLen=strlen(code);
      strncpy(ccode,code,iCodeLen);ccode[iCodeLen]=0;
      iCountryCodeLen=strlen(code);
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

class CTCountryCode{
   // CTList ccList;
   enum{eAnd=1023,eLists=1024};
   CTList ccListI[eLists];
   
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
      //3:3712:lv:Latvia:Mobile:\r\n"
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
      
      
      while(*p && *p<' ' && iLeft>0){p++;iLeft--;}
      
      
      
      return p-start;
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
      
      //  printf("[%c %c]",out[0],in[0],);
      i++;
      if(iLen<=0)break;
   }
   if(i<iLenMax && !iPatternEnded && (pattern[2]!=0 && pattern[1]!=0 && *pattern!=0 && (*pattern==')' || pattern[1]==')' || pattern[2]==')'))){i++;*out=')';out++;}
   *out=0;
   return i;
}

static int fixNR_CC(int iCCLen, const char *in, char *out, int iLenMax){
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
         fixNR_CC(l,in,out,iLenMax);
      }
   }
   else {
      if(in[0]!='+'){
        l=fixNR_CC(0,in,out,iLenMax);
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
   int iLen=strlen(nr);
   
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

#if 0
void fnTest(void *p, const char *nr){
   char buf[64];
   fixNR(p,nr,&buf[0],63);
   printf("%s->[%s]\n",nr,buf);
}


int main(){
   CTCountryCode c("Country.txt");
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
 g++ test_cc.cpp sources/utils/utils.cpp -o t
 */
#endif





