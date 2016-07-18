#ifndef _CFG_PARSER_H
#define _CFG_PARSER_H

class CTCfgParser{
public:

   static char * getText(char *p, const char *name, int &iLen, int iMaxLen=0)
   {
      iLen=0;
      char *ret=NULL;
      if(!iMaxLen)iMaxLen=strlen(p);
      int iNLen=strlen(name);
      char *pEnd=p+iMaxLen;

      while(*p && p+iNLen+1<pEnd)
      {
         if(isalnum(p[0]))
         {//
            if(name[0]==p[0] && name[iNLen-1]==p[iNLen-1] && name[iNLen>>1]==p[iNLen>>1] && strncmp(name,p,iNLen)==0 && p[iNLen]==':')
            {
               p+=iNLen+1; //skip name(key) and ':'
               
               // puts(p);
               while(p<pEnd && !isalnum(p[0]))p++;
               if(p>=pEnd){
                  puts("[err1]");
                  return ret;
               }
               ret=p;
               while(*p>=' ' && p<pEnd)p++;
               
               iLen=p-ret;

               return ret;
            }
            while(*p>=' ' && p<pEnd)p++;
            if(p>=pEnd)break;
         }
         else
            p++;
      }
      return ret;
   }
   
   static int getText2Buf(char *p, const char *name, char *retBuf, int iMaxRetSize,int iCfgLen)
   {
      int iLen;
      char *param=getText(p,name,iLen,iCfgLen);
      if(param==NULL)return -1;
      iMaxRetSize--;
      if(iLen>iMaxRetSize)iLen=iMaxRetSize;
      strncpy(retBuf,param,iLen);
      retBuf[iLen]=0;
      return 0;

   }
   static  int getInt(int iCfgLen, char *p, const char *name,  int &iRet, int id=1)
   {
      int iLen;
      char *param=getText(p,name,iLen,iCfgLen);
      if(param==NULL)return -1;
      int i;
      char *pEnd=iLen+param;
      for(i=0;i<id-1;i++)
      {
         strtoul(param,&param,0);
         //param++;
         if(pEnd<=param)return -1;
      }
      iRet=strtoul(param,NULL,0);
      return 0;
   }
   static unsigned int getUInt(int iCfgLen,char *p, char *name, unsigned int &iRet, int id=1)
   {
      int iLen;
      char *param=getText(p,name,iLen,iCfgLen);
      if(param==NULL)return -1;
      int i;
      char *pEnd=iLen+param;
      for(i=0;i<id-1;i++)
      {
         strtoul(param,&param,0);
         //param++;
         if(pEnd<=param)return -1;
      }
      iRet=strtoul(param,NULL,0);
      return 0;
   }

#define M_FNC_2I(_F2_,_F1_)       {                   \
      int a=CTCfgParser::getInt(p,#_F1_,i1,1);  \
      if(a==0)a+=CTCfgParser::getInt(p,#_F1_,i2,2);     \
      if(a==0){ _F2_##_F1_ (i1,i2);}}

#define M_FNC_1I(_F2_,_F1_)       {                   \
      int a=CTCfgParser::getInt(p,#_F1_,i1,1);  \
      if(a==0){ _F2_##_F1_ (i1);}}

#define M_FNC_0(_F2_,_F1_)       {                   \
      char *a=CTCfgParser::getText(p,#_F1_,i1);  \
      if(a){ _F2_##_F1_ ();}}

#define M_FNC_INT(_F2_,_F1_)       {                   \
      int a=CTCfgParser::getInt(p,#_F1_,i1,1);  \
      if(a==0){ _F2_##_F1_ =(i1);}}
};
#endif