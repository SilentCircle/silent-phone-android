/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2016, Silent Circle, LLC.  All rights reserved.

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

template <class T> 
class CTAritmCoding{
   typedef struct{ 
      int pos;
      int val;
   }T_HUFF_TAB;
   //int bufHH[0x800*4];
   
   inline void swap(T_HUFF_TAB &v1, T_HUFF_TAB &v2)
   {
      int pos=v1.pos;
      T val=v1.val;
      v1.pos=v2.pos;
      v1.val=v2.val;
      v2.pos=pos;
      v2.val=val;
   }
   enum{eHuffTabSize=0x800,eHufmanHalf=eHuffTabSize>>1,eMaxHufmanHalf=eHufmanHalf-5};

   T_HUFF_TAB huffTab[eHuffTabSize];
   //static T_HUFF_TAB huffTabStat[eHuffTabSize];
   int huffEncTab[eHuffTabSize];
   T tempBufX[32000];
   static int compare_h( const void* a, const void* b )
   {
      int ha=((T_HUFF_TAB *)a)->val;
      int hb=((T_HUFF_TAB *)b)->val;

      if(ha<hb)return -1;
      if(ha==hb)return 0;
      return 1;
   }



   int iMaxHuffValue;
   int sortq()
   {
      qsort( &huffTab[0], eHuffTabSize-1, sizeof(T_HUFF_TAB), compare_h ); 
      return 0;
   }
   int sortStart()
   {
      int i;
      if(huffTab[0].val==0)
      {
         int iPosZero=-1;
         int iCnt;
         int iFrom=0;
         do{
            iCnt=0;
            for(i=0;i<eHuffTabSize-1;i++)
            {
               if(iPosZero==-1 && huffTab[i].val==0){iPosZero=i;if(iFrom)i=iFrom;}
               else if(iPosZero!=-1 && huffTab[i].val)
               {
                 iCnt =iPosZero!=-1;
                 if(!iCnt)break;
                 iFrom=i;
                 swap(huffTab[iPosZero],huffTab[i]);
                 break;
               }
            }
            iPosZero=-1;
         }while(iCnt);

      }
      return 0;
   }
   int sort()
   {
      int i,found=0;
    /*
      if(huffTab[0].val==0)
      {
         int iPosZero=-1;
         int iCnt;
         int iFrom=0;
         do{
            iCnt=0;
            for(i=0;i<eHuffTabSize-1;i++)
            {
               if(iPosZero==-1 && huffTab[i].val==0){iPosZero=i;if(iFrom)i=iFrom;}
               else if(iPosZero!=-1 && huffTab[i].val)
               {
                 iCnt =iPosZero!=-1;
                 if(!iCnt)break;
                 iFrom=i;
                 swap(huffTab[iPosZero],huffTab[i]);
                 break;
               }
            }
            iPosZero=-1;
         }while(iCnt);

      }
      */

      for(i=0;i<eHuffTabSize-1;i++)
      {
         if(huffTab[i].val<huffTab[i+1].val){swap(huffTab[i],huffTab[i+1]);found=1;}
      }
      //static int a;
      //printf("a %d ",a++);
      return found;
   }
   int toHufman(unsigned char *pOut, T *in, int iCnt,int iSigned=0)
   {
      T *tmp=in;
      int i;
      iMaxHuffValue=0;
      memset(huffTab,0,sizeof(huffTab));
//      T_HUFF_TAB *ht=(T_HUFF_TAB *)&bufHH[0];
      for(i=0;i<iCnt;i++){
         //if(iSigned)huffTab[abs(*tmp)].val++;else huffTab[*tmp].val++;
         //huffTab[abs(*tmp)].val++;
         //*tmp=abs(*tmp);

         if(*tmp<eMaxHufmanHalf && *tmp>-eMaxHufmanHalf){
            huffTab[*tmp+eHufmanHalf].val++;
            //if(sizeof(T)==2)      ht[*tmp+eHufmanHalf].val++;
            
         }
         else
         {
            return 0;
         }
         tmp++;
      }
      //if(iBigVal1)iMaxHuffValue++;
      //if(iBigVal2)iMaxHuffValue++;
      //static int iCC;   iCC++;
      for(i=0;i<eHuffTabSize;i++){
         huffTab[i].pos=i;
         //if(iCC==2000)ht[i].pos=i;
         if(huffTab[i].val)
            iMaxHuffValue++;
      }
      int sortHuff(void *h, int iSize);
    //  int sortHuffC(void *h, int iSize);
    #if 0
      if(sizeof(T)==2 && iCC==2000)
      {
         //iCC=0;
         sortHuff(&ht[0],eHuffTabSize);
         FILE *f=fopen("dct_tab.txt","a+");
         fprintf(f,"{\n   ");
         for(i=0;i<256 && ht[i].val;i++){
            fprintf(f,"%2d /*%6d*/,",ht[i].pos-1024,ht[i].val);//fprintf(f,
            if((i&15)==15)fprintf(f,"\n   ");
         }
         fputs("\n};///5000 \n--------\n",f);
         fclose(f);
      }
      #endif
      if(iMaxHuffValue>(iCnt/iHSkipDiv)+12)
      {
         iMaxHuffValue=0;
         return 0;
      }

      //for(i=0;i<20;i++)printf("<%2d %2d>,",huffTab[i].pos,huffTab[i].val);
      //sortStart();
      sortHuff(&huffTab[0],eHuffTabSize);
      
     // while(sort());
      //printf("\n");
      //for(i=0;i<iMaxHuffValue;i++)huffEncTab[i]=huffTab[i].pos;
      //printf("\n%d",iMaxHuffValue);
      for(i=0;i<eHuffTabSize;i++)
      {
         huffEncTab[huffTab[i].pos]=i;
      }
     // pOut[0]=iMaxHuffValue;
      int ret=saveHuffTable(pOut);


      //printf("\n");
     // for(i=0;i<20;i++)printf("<%2d %2d>,",huffTab[i].pos,huffTab[i].val);

      //for()
      return ret;//iMaxHuffValue+1;
   }
   int toDif(T *in, int iLen)//, int *out)
   {
      int i;
      T iPrev=in[0];
      int iCur;
      //out[l]=iPrev;
      for(i=1;i<iLen;i++)
      {
         iCur=in[i];
         in[i]=iCur-iPrev;
         iPrev=iCur;
      }
      return iLen;
   }
int toDRle(T *in, int iLen, T *out)
{
   int j,i,l=0;
   T iPrev;

   iPrev=in[0];
   for(i=0;i<iLen;)
   {
      out[l]=iPrev;
      l++;
      i++;
      for(j=0;i<iLen;i++,j++)
      {
         int iCur=in[i];
         if(iPrev!=iCur || j>2000)
         {
            iPrev=iCur;
            break;
         }
      }
      out[l]=j;l++;
      //printf("<%d,i=%d>",iPrev,i);

//      printf("(%d,%d),",out[l-2],out[l-1]);
     // l++;

   }
   return l;
}
int fromDRle(T *in, int iLen, T *out)
{
   int i,j,iCnt;
   T *outo=out;
   int v;

   for(i=0;i<iLen;i+=2)
   {
      v=*in;in++;;
      *out=v;
      out++;
      iCnt=*in;in++;
      for(j=0;j<iCnt;j++,out++)*out=v;
   }
   return out-outo;
}

int toDRleN(T *in, int iLen, T *out)
{
   T prev=in[0];
   int l;
   int neg;
   int iOLen=0;;
   int iNegPos=0;
   int cur;

   for(int i=1;i<iLen;i++)
   {
      neg=0;
      cur=prev;
      //prev=in[i];
      for(l=0;i<iLen;l++,i++)
      {
         if(neg)
         {
            if(prev==in[i] || l>1000)
            {
               prev=in[i-1];
               //i-=2;
               i--;
               l--;
               iOLen--;

               break;
            }
            else{
               out[iOLen]=in[i];
               iOLen++;
               prev=in[i];   
               continue;
            }
         }
         if(cur==in[i] &&  l<1000)
         {
            continue;
         }
         else if(l==0){
            iNegPos=iOLen;
            iOLen++;
            i--;
            //l--;
            prev=in[i];
            out[iOLen]=in[i];iOLen++;
            neg=1;
         }
         else {
            prev=in[i];
            break;
         }
      }
      if(neg)
      {
         l--;
         out[iNegPos]=-l;
         continue;
      }
      out[iOLen]=l;
      iOLen++;
      out[iOLen]=cur;
      iOLen++;
   }
   return iOLen;
}
int fromDRleN(T *in, int iLen, T *out)
{
   int v,j,l,i,iOLen=0;
   for(i=0;i<iLen;)
   {
      l=in[i];
      i++;
      if(l<0)
      {
         l=-l;
         l++;
         for(j=0;j<l;j++,i++)
         {
            out[iOLen++]=in[i];
         }
         continue;
      }
      l++;
      v=in[i];
      for(j=0;j<l;j++)
      {
         out[iOLen++]=v;
      }
      i++;
   }
   return iOLen;
}


   int toDif(T *in, int iLen, T *out)
   {
      int i;//,l=0;
      T iPrev=in[0];
      T iCur;
      out[0]=iPrev;
      for(i=1;i<iLen;i++)
      {
         iCur=in[i];
         out[i]=iCur-iPrev;
         iPrev=iCur;
      }
      return iLen;
   }
   int fromDif(T *in, int iLen)
   {
      int i;
      T iPrev=in[0];
     // T iCur;
      //out[l]=iPrev;
      for(i=1;i<iLen;i++)
      {
         in[i]+=iPrev;
         iPrev=in[i];
      }
      return iLen;
   }   
   int  getHuffTable(unsigned char *p)
   {
      //iMaxHuffValue=p[0];
      //p++;
      int i,iPos=0,iBits;
      memset(huffEncTab,0,sizeof(huffEncTab));
      iMaxHuffValue=getBitValArit(p,iPos,iBits);iPos+=iBits;
    //iTwoAndBigers=getBitValArit(p,iPos,iBits)-1;iPos+=iBits;
      for(i=0;i<iMaxHuffValue;i++)
      {
         huffEncTab[i]=getBitValAritSig(p,iPos,iBits);iPos+=iBits;
      }
      return ((iPos+7)>>3);

   }
   int saveHuffTable(unsigned char *p)
   {
      int ret=0;
      int i;
      //int iMXV=iMaxHuffValue;
      //if(iBigVal1)iMXV--;
      //if(iBigVal2)iMXV--;
      /*
      if(0)//iMaxHuffValue>7)
      {
        for(iTwoAndBigers=0;;)
        {
           if(iTwoAndBigers>=iMaxHuffValue)break;
           if(huffTab[iTwoAndBigers].val<2)break;
           iTwoAndBigers++;
        }
        //iTwoAndBigers=i+1;
      }
      else
         iTwoAndBigers=iMaxHuffValue;

         */
      this->toAritUnsignVal(p,iMaxHuffValue,ret);
      //this->toAritUnsignVal(p,iTwoAndBigers+1,ret);

      //p+=((ret+7)>>3);

      for(i=0;i<iMaxHuffValue;i++)
      {
         toAritSignedVal(p,huffTab[i].pos-eHufmanHalf,ret);
         //p[i]=huffTab[i].pos;
      }
      //if(iBigVal1)toAritSignedVal(p,iBigVal1,ret);
      //if(iBigVal2)toAritSignedVal(p,iBigVal2,ret);
      return (ret+7)>>3;
   }
   inline int getBitValArit4(unsigned char *pBuf, int iPos, int &iBits)
   {
      int v=0;
      iBits=1;
      int b=getBit(pBuf,iPos);
      if(b)
      {
         iBits=2;
         b=getBit(pBuf,iPos+1);
         if(b)
         {
            iBits=3;
            v=getBit(pBuf,iPos+2)?3:2;
         }
         else 
            v=1;
         
      }
      return v;
   }
   inline int getBitValArit3(unsigned char *pBuf, int iPos, int &iBits)
   {
      int v=0;
      iBits=1;
      int b=getBit(pBuf,iPos);
      if(b)
      {
         iBits=2;
         v=getBit(pBuf,iPos+1)?2:1;
      }
      return v;
   }
   int getBitValAritSigPart(unsigned char *pBuf, int iPos, int &iBits)
   {
      int i=0;
      int ret=0;

      iBits=0;
      int iNegative=0;
      while(getBit(pBuf,iPos+iBits)==0 && iBits<6)iBits++;
      if(iBits)
      {
         iPos+=iBits;
         iBits++;
         for(i=0;i<iBits;i++)
         {
            ret|=getBit(pBuf,iPos+i);
            if(i+1!=iBits)
               ret<<=1;
            else
               iNegative=getBit(pBuf,iPos+i+1);
         }
         iBits*=2;
         
         ret--;
         //iBits--;
         if(iNegative)ret=-ret;
      }
      else {iBits=1;}

      return ret;
   }
   int getBitValAritSig(unsigned char *pBuf, int iPos, int &iBits)
   {
      int i=0;
      int ret=0;

      iBits=0;
      int iNegative=0;
      while(getBit(pBuf,iPos+iBits)==0)iBits++;
      if(iBits)
      {
         iPos+=iBits;
         iBits++;
         for(i=0;i<iBits;i++)
         {
            ret|=getBit(pBuf,iPos+i);
            if(i+1!=iBits)
               ret<<=1;
            else
               iNegative=getBit(pBuf,iPos+i+1);
         }
         iBits*=2;
         
         ret--;
         //iBits--;
         if(iNegative)ret=-ret;
      }
      else {iBits=1;}

      return ret;
   }
   int getBitValArit(unsigned char *pBuf, int iPos, int &iBits)
   {
      int i=0;
      int ret=0;

      iBits=0;
      //int iNegative=0;
      while(getBit(pBuf,iPos+iBits)==0)iBits++;
      if(iBits)
      {
         iPos+=iBits;
         iBits++;
         for(i=0;i<iBits;i++)
         {
            ret|=getBit(pBuf,iPos+i);
            if(i+1!=iBits)
               ret<<=1;
        //    else
          //     iNegative=getBit(pBuf,iPos+i+1);
         }
         iBits*=2;
         
         ret--;
         iBits--;
         //if(iNegative)ret=-ret;
      }
      else {iBits=1;}

      return ret;
   }



   void toAritSignedValPart(unsigned char *p, int v, int &iBitsInBuf)
   {
      int iNeg;
      if(v<0)
      {
         iNeg=1;
         v=-v;
      }
      else iNeg=0;
      if(v>0)addBit(p,0,iBitsInBuf);
      if(v>2)addBit(p,0,iBitsInBuf);;
      if(v>6)addBit(p,0,iBitsInBuf);;
      if(v>14)addBit(p,0,iBitsInBuf);
      if(v>30)addBit(p,0,iBitsInBuf);
      v++;
      if(v){
         if(v>1){
            if(v>3){
               if(v>7){
                  if(v>15){
                     if(v>31){
                        addBit(p,v&512?1:0,iBitsInBuf);
                        addBit(p,v&256?1:0,iBitsInBuf);
                        addBit(p,v&128?1:0,iBitsInBuf);
                        addBit(p,v&64?1:0,iBitsInBuf);
                        addBit(p,v&32?1:0,iBitsInBuf);
                     }
                     addBit(p,v&16?1:0,iBitsInBuf);
                  }
                  addBit(p,v&8?1:0,iBitsInBuf);
               }
               addBit(p,v&4?1:0,iBitsInBuf);
            }
            addBit(p,v&2?1:0,iBitsInBuf);
         }
         addBit(p,v&1?1:0,iBitsInBuf);
         if(v!=1){addBit(p,iNeg?1:0,iBitsInBuf);}
      }
   }

   void toAritSignedVal(unsigned char *p, int v, int &iBitsInBuf)
   {
      int iNeg;
      if(v<0)
      {
         iNeg=1;
         v=-v;
      }
      else iNeg=0;
      if(v>0)addBit(p,0,iBitsInBuf);
      if(v>2)addBit(p,0,iBitsInBuf);;
      if(v>6)addBit(p,0,iBitsInBuf);;
      if(v>14)addBit(p,0,iBitsInBuf);
      if(v>30)addBit(p,0,iBitsInBuf);
      if(v>62)addBit(p,0,iBitsInBuf);
      if(v>126)addBit(p,0,iBitsInBuf);
      if(v>254)addBit(p,0,iBitsInBuf);
      if(v>510)addBit(p,0,iBitsInBuf);
      if(v>1022)addBit(p,0,iBitsInBuf);
      if(v>2046)addBit(p,0,iBitsInBuf);
      v++;
      if(v){
         if(v>1){
            if(v>3){
               if(v>7){
                  if(v>15){
                     if(v>31){
                        if(v>63){
                           if(v>127){
                              if(v>255){
                                 if(v>511){
                                    if(v>1023){
                                       if(v>2047){
                                          addBit(p,v&2048?1:0,iBitsInBuf);
                                       }
                                       addBit(p,v&1024?1:0,iBitsInBuf);
                                    }
                                    addBit(p,v&512?1:0,iBitsInBuf);
                                 }
                                 addBit(p,v&256?1:0,iBitsInBuf);
                              }
                              addBit(p,v&128?1:0,iBitsInBuf);
                           }
                           addBit(p,v&64?1:0,iBitsInBuf);
                        }
                        addBit(p,v&32?1:0,iBitsInBuf);
                     }
                     addBit(p,v&16?1:0,iBitsInBuf);
                  }
                  addBit(p,v&8?1:0,iBitsInBuf);
               }
               addBit(p,v&4?1:0,iBitsInBuf);
            }
            addBit(p,v&2?1:0,iBitsInBuf);
         }
         addBit(p,v&1?1:0,iBitsInBuf);
         if(v!=1){addBit(p,iNeg?1:0,iBitsInBuf);}
      }
   }
   void toAritUnsignVal(unsigned char *p, int v, int &iBitsInBuf)
   {
      //v=data[i];
      if(v>0)addBit(p,0,iBitsInBuf);
      if(v>2)addBit(p,0,iBitsInBuf);;
      if(v>6)addBit(p,0,iBitsInBuf);;
      if(v>14)addBit(p,0,iBitsInBuf);
      if(v>30)addBit(p,0,iBitsInBuf);
      if(v>62)addBit(p,0,iBitsInBuf);
      if(v>126)addBit(p,0,iBitsInBuf);
      if(v>254)addBit(p,0,iBitsInBuf);
      if(v>510)addBit(p,0,iBitsInBuf);
      if(v>1022)addBit(p,0,iBitsInBuf);
      v++;
      if(v){
         if(v>1){
            if(v>3){
               if(v>7){
                  if(v>15){
                     if(v>31){
                        if(v>63){
                           if(v>127){
                              if(v>255){
                                 if(v>511){
                                    if(v>1023){
                                       addBit(p,v&1024?1:0,iBitsInBuf);
                                    }
                                    addBit(p,v&512?1:0,iBitsInBuf);
                                 }
                                 addBit(p,v&256?1:0,iBitsInBuf);
                              }
                              addBit(p,v&128?1:0,iBitsInBuf);
                           }
                           addBit(p,v&64?1:0,iBitsInBuf);
                        }
                        addBit(p,v&32?1:0,iBitsInBuf);
                     }
                     addBit(p,v&16?1:0,iBitsInBuf);
                  }
                  addBit(p,v&8?1:0,iBitsInBuf);
               }
               addBit(p,v&4?1:0,iBitsInBuf);
            }
            addBit(p,v&2?1:0,iBitsInBuf);
         }
         addBit(p,v&1?1:0,iBitsInBuf);
      }
   }

   int copyAritmSigned(unsigned char *p, T *data, int iCnt, int iIsRle)
   {
      int i;
      int iBitsInBuf=0;
      int v;
//      int iNeg;
      for(i=0;i<iCnt;i++)
      {
         v=data[i];
         toAritSignedVal(p,v,iBitsInBuf);
         if(!iIsRle)continue;
         i++;
         v=data[i];
         //toAritSignedVal(p,v,iBitsInBuf);
         toAritUnsignVal(p,v,iBitsInBuf);
      }
      

      return (iBitsInBuf+7)>>3;
   }
   int copyAritmUnsigned(unsigned char *p, T *data, int iCnt, int iHasHufm)
   {
      int i;
      int iBitsInBuf=0;
      int v;
      int dv;
      for(i=0;i<iCnt;i++)
      {
#define TA_GET_VALUE_H\
            dv=data[i];\
            if(iHasHufm)v=huffEncTab[dv+eHufmanHalf];else v=dv;\

//            if(iHasHufm && v>iTwoAndBigers)v=iTwoAndBigers+1;
         TA_GET_VALUE_H

         toAritUnsignVal(p,v,iBitsInBuf);
      }
      return (iBitsInBuf+7)>>3;
   }
   int copyBinary4(unsigned char *p, T *data, int iCnt, int iHasHufm)
   {
      int i;
      int iBitsInBuf=0;
      int v;//,dv;
      for(i=0;i<iCnt;i++)
      {
         if(iHasHufm)v=huffEncTab[data[i]+eHufmanHalf];else v=data[i];
         //TA_GET_VALUE_H
         switch(v)
         {
         case 0:addBit(p,0,iBitsInBuf);
            break;
         case 1:addBit(p,1,iBitsInBuf);addBit(p,0,iBitsInBuf);
            break;
         case 2:addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);addBit(p,0,iBitsInBuf);
            break;
         case 3:addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);
            break;
         }
         //addBit(p,v&1?1:0,iBitsInBuf);
      }
      return (iBitsInBuf+7)>>3;
   }
   int copyBinary3(unsigned char *p, T *data, int iCnt, int iHasHufm)
   {
      int i;
      int iBitsInBuf=0;
      int v;//,dv;
      for(i=0;i<iCnt;i++)
      {
         if(iHasHufm)v=huffEncTab[data[i]+eHufmanHalf];else v=data[i];
         //TA_GET_VALUE_H
         switch(v)
         {
         case 0:addBit(p,0,iBitsInBuf);
            break;
         case 1:addBit(p,1,iBitsInBuf);addBit(p,0,iBitsInBuf);
            break;
         case 2:addBit(p,1,iBitsInBuf);addBit(p,1,iBitsInBuf);
            break;
         }
         //addBit(p,v&1?1:0,iBitsInBuf);
      }
      return (iBitsInBuf+7)>>3;
   }

   int copyBinary(unsigned char *p, T *data, int iCnt, int iHasHufm)
   {
      int i;
      int iBitsInBuf=0;
      int v;
      //int iNeg;

      //printf("\n%d\n",huffEncTab[257]);
      //printf("%d\n",huffEncTab[265]);
      for(i=0;i<iCnt;i++)
      {
         //printf("data[i]=%d, h->%d",data[i],huffEncTab[data[i]+(eHuffTabSize>>1)]);
         if(iHasHufm)v=huffEncTab[data[i]+eHufmanHalf];else v=data[i];
            //TA_GET_VALUE_H
         //printf("%d %d,",v,data[i]);
         //v=data[i];
         //toAritSignedVal(p,v,iBitsInBuf);
         addBit(p,v&1?1:0,iBitsInBuf);
      }
      return (iBitsInBuf+7)>>3;
   }

public:
   CTAritmCoding(){iHSkipDiv=24;}
   int iHSkipDiv;
   int iTwoAndBigers;
   int decode(unsigned char *p, int iLen, T *out)
   {
      int iCnt=(p[0]<<8)+p[1];
      int i;
      int iHasHuffTable=p[2]&1;
      int iHasRle=p[2]&4;
      int iHasDif=p[2]&2;
      iTwoAndBigers=0;
      int iLenWasZero=iLen==0;
      p+=3;
      iMaxHuffValue=0;
      int iEncBytes=3;
      if(iHasHuffTable>7)debugss("err-a-dec huff",0,0);
      if(iHasHuffTable)
      {
         int l=getHuffTable(p);
         iEncBytes+=l;
         p+=l;
         iLen-=l;
      }
      int iPos=0,iBits,v;
      int val0=huffEncTab[0];
      int val1=huffEncTab[1];
      //int iCurX=0;
      for(i=0;i<iCnt;i++)//check iLEN
      {
         if(!iHasHuffTable)
         {
            out[i]=getBitValAritSig(p,iPos,iBits);iPos+=iBits;
            if(iHasRle)
            {  
               i++;
               //out[i]=getBitValAritSig(p,iPos,iBits);iPos+=iBits;
               out[i]=getBitValArit(p,iPos,iBits);iPos+=iBits;
            }
            continue;
         }
         else
         if(iMaxHuffValue==1)
         {
            out[i]=val0;
            iPos=0;
            continue;
         }

         else if(iMaxHuffValue==2)
         {
            v=getBit(p,iPos);iPos++;
            out[i]=v?val1:val0;
            continue;
         }
         else if(iMaxHuffValue==3)
         {
            v=getBitValArit3(p,iPos,iBits);iPos+=iBits;
            out[i]=huffEncTab[v];
            continue;
         }
         else if(iMaxHuffValue==4)
         {
            v=getBitValArit4(p,iPos,iBits);iPos+=iBits;
            out[i]=huffEncTab[v];
            continue;
         }
         else
         
         {
            v=getBitValArit(p,iPos,iBits);iPos+=iBits;
            /*
            if(v==iTwoAndBigers+1) 
            {
               out[i]=huffEncTab[iTwoAndBigers+iCurX];iCurX++;
            }
            else 
            */
               out[i]=huffEncTab[v];
            continue;
         }
            
      }
//      int toDRle(int *in, int iLen, int *out);
 //     int fromDRle(int *in, int iLen, int *out);
      int iDecAritmLen=iLenWasZero?(((iPos+7)>>3)):(iCnt-iEncBytes);
      if(iHasRle)
      {
         iCnt=fromDRle(out, iCnt, &this->tempBufX[0]);
         memcpy(out,&this->tempBufX[0],iCnt*sizeof(T));;
      }
      if(iHasDif)
      {
         fromDif(out,iCnt);
      }
      /*
      iCnt=fromDRleN(out,  iCnt, &this->tmpBufX2[0]);
      memcpy(out,&this->tmpBufX2[0],iCnt*sizeof(T));;
      fromDif(out,iCnt);
      */
      //return iCnt;
     // in=&this->tmpBufX2[0];

      return iLenWasZero?(iEncBytes+((iPos+7)>>3)):iCnt;
      //int iDecAritmLen=iLenWasZero?(((iPos+7)>>3)):(iCnt-iEncBytes);
      //return iDecAritmLen+iEncBytes;
   }

  int tmpBufX2[16000];
  int tmpBufX3[16000];
  int toVlc(unsigned char *pOut, T *in, int iCnt)
  {
     //unsigned char *pOutO;
      pOut[0]=iCnt>>8;
      pOut[1]=iCnt-(pOut[0]<<8);
     //int i=0;
     //int iLen=(pOut-pOutO);
     return copyAritmUnsigned(pOut+2,in,iCnt,0);
  }
   int encode(unsigned char *pOut, T *in, int iCnt, int iToHuffman=0, int iSigned=0)
   {
      //iToHuffman=0;
      /*
      toDif(in,iCnt,&this->tmpBufX3[0]);
      in=&this->tmpBufX3[0];
      iCnt=toDRleN(in,  iCnt, &this->tmpBufX2[0]);
      in=&this->tmpBufX2[0];
*/
      //if(x<iCnt)iCnt=x;
      iMaxHuffValue=0;
      iTwoAndBigers=0;
      int l=3;
      pOut[0]=iCnt>>8;
      pOut[1]=iCnt-(pOut[0]<<8);
      //unsigned char *pEncBin=pOut+2;
      pOut[2]=iToHuffman;
      unsigned char *pFlag=pOut+2;

      if(iToHuffman)
      {
         int l2=toHufman(pOut+l,in,iCnt,iSigned);
         if(l2==0){iToHuffman=0;pOut[2]=0;iMaxHuffValue=0;}
         l+=l2;
         //pOut+=l;
      }
      //pEncBin[0]|=iEncBin;
      //puts("uu");
      
      if(iToHuffman==0 && iSigned!=2)// || (iMaxHuffValue>1 && iMaxHuffValue<(iCnt>>5)))
      {
         if(iToHuffman==0 && iCnt<15000)
         {
            toDif(in,iCnt,&this->tempBufX[0]);
            pFlag[0]|=2;
            in=&this->tempBufX[0];
         }
         if(iCnt<15000)
         {
         int x=toDRle(in,  iCnt, &this->tempBufX[16000]);
         //if(x*3<iCnt*2)
         if(x*2<iCnt)
         {
            in=&this->tempBufX[16000];
            iCnt=x;
            pOut[0]=iCnt>>8;
            pOut[1]=iCnt-(pOut[0]<<8);
            pFlag[0]|=4;
            if(0)//0)//iToHuffman)
            {
               l=3;
               iMaxHuffValue=0;
               int l2=toHufman(pOut+l,in,iCnt,iSigned);
               if(l2==0){iToHuffman=0;pOut[2]&=~1;iMaxHuffValue=0;}
               l+=l2;
            }
         }
         }
      }
      
      
      if(iToHuffman==0)
      {
//toDif(in,iCnt,&this->tempBufX[0]);
//in=&this->tempBufX[0];
         l+=copyAritmSigned(pOut+l,in,iCnt,pOut[2]&4);
      }
      else if(iMaxHuffValue==1)
      {
         
      }
      else  if(iMaxHuffValue==2)
      {
         l+=copyBinary(pOut+l,in,iCnt,iToHuffman);
         //te vareetu uzbraukt ar rle
      }
      else if(iMaxHuffValue==3)
         l+=copyBinary3(pOut+l,in,iCnt,iToHuffman);
      else if(iMaxHuffValue==4)
         l+=copyBinary4(pOut+l,in,iCnt,iToHuffman);
      else
      
         l+=copyAritmUnsigned(pOut+l,in,iCnt,iToHuffman);

      if(0)
      {
         CTAritmCoding<T> de;
         T testb[32000];
         int lD=de.decode(pOut,0,&testb[0]);
         if(lD!=l)
            debugss("err arr-iCnt",0,0);
         if(memcmp(testb,in,sizeof(T)*iCnt))
            debugss("err arr     ",0,0);
      }
      return l;
   }


};
static int encAritm(unsigned char *out , int *in ,int iCnt)
{
   CTAritmCoding<int> a;
   return a.encode(out,in,iCnt,1,1);
}
static int encAritm2(unsigned char *out , int *in ,int iCnt)
{
   CTAritmCoding<int> a;
   return a.encode(out,in,iCnt,0,1);
}

static int encAritmUC(unsigned char *out , unsigned char *in ,int iCnt)
{
   CTAritmCoding<unsigned char> a;
   return a.encode(out,in,iCnt,1,0);
}
/*




int encAritmNH_S(unsigned char *out , int *in ,int iCnt)
{
   CTAritmCoding<int> a;
   return a.encode(out,in,iCnt,0,2);
}

int encAritmUS_I(unsigned char *out , int *in ,int iCnt)
{
   CTAritmCoding<int> a;
   return a.toVlc(out,in,iCnt);
}
int encAritmC(unsigned char *out , short *in ,int iCnt)
{
   CTAritmCoding<short> a;
   a.iHSkipDiv=4;
   return a.encode(out,in,iCnt,1,1);
}

int encAritmD(unsigned char *in , int *out ,int iCnt)
{
   CTAritmCoding<int> a;
   int l=a.decode(in,iCnt,out);
   
   return (int)((int)in[0]<<8)+(int)in[1]; 
}
*/
//#if  defined(__SYMBIAN32__) || defined(_WIN32_WCE)
static int encAritmGetValCnt(unsigned char *in ){
   return (int)((int)in[0]<<8)+(int)in[1]; 
}
//#endif

static int s_encAritmDUC(CTAritmCoding<unsigned char> &a,unsigned char *in , unsigned char *out ,int iMaxLen)
{
   
   return a.decode(in,0,out);
}
static int s_encAritmDec(CTAritmCoding<int> &a,unsigned char *in , int *out ,int iMaxLen)
{
   
   
   return a.decode(in,0,out);
}

static int s_encAritm(CTAritmCoding<int> &a,unsigned char *out , int *in ,int iCnt)
{
   
   return a.encode(out,in,iCnt,1,1);
}

static int s_encAritmUC(CTAritmCoding<unsigned char> &a, unsigned char *out , unsigned char *in ,int iCnt)
{
   //CTAritmCoding<unsigned char> a;
   return a.encode(out,in,iCnt,1,0);
}

