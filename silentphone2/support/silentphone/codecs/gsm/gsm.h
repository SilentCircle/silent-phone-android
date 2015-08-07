/*
 * Copyright 1992 by Jutta Degener and Carsten Bormann, Technische
 * Universitaet Berlin.  See the accompanying file "COPYRIGHT" for
 * details.  THERE IS ABSOLUTELY NO WARRANTY FOR THIS SOFTWARE.
 */

/*$Header: /cvs/sources/codecs/gsm/gsm.h,v 1.1.1.1 2005/10/07 13:03:49 janis Exp $*/


#ifndef GSM_H
#define GSM_H


#ifndef WORD_X
#define WORD_X
typedef short                   word;           /* 16 bit signed int    */
typedef int                     longword;       /* 32 bit signed int    */

typedef unsigned short          uword;          /* unsigned word        */
typedef unsigned int            ulongword;      /* unsigned longword    */
#endif

#define  NeedFunctionPrototypes  1

#ifdef __cplusplus
#       define  NeedFunctionPrototypes  1
#endif

#if __STDC__
#       define  NeedFunctionPrototypes  1
#endif

#ifdef _NO_PROTO
#       undef   NeedFunctionPrototypes
#endif

#undef  GSM_P   /* gnu stdio.h actually defines this...         */

#if NeedFunctionPrototypes
#       define  GSM_P( protos ) protos
#else
#       define  GSM_P( protos ) ( /* protos */ )
#endif

#ifdef NeedFunctionPrototypes
#   include     <stdio.h>               /* for FILE *   */
#endif

/* AUTO_SPARC_HACK added by John Walker.  If defined, USE_FLOAT_MUL   
   automatically set when compiling on a Sparc.  This lets most
   people avoid editing the Makefile. */

#ifdef AUTO_SPARC_HACK
#ifdef sparc
#define USE_FLOAT_MUL
#define FAST
#endif
#endif

/*
 *      Interface
 */

typedef struct gsm_state *      gsm;
typedef short                   gsm_signal;             /* signed 16 bit */
typedef unsigned char           gsm_byte;
typedef gsm_byte                gsm_frame[33];          /* 33 * 8 bits   */

#define GSM_MAGIC       0xD                             /* 13 kbit/s RPE-LTP */

#define GSM_PATCHLEVEL  2
#define GSM_MINOR       0
#define GSM_MAJOR       1

#define GSM_OPT_VERBOSE 1
#define GSM_OPT_FAST    2

extern gsm  gsm_create  GSM_P((void));
extern void gsm_destroy GSM_P((gsm));   

extern int  gsm_print   GSM_P((FILE *, gsm, gsm_byte  *));
extern int  gsm_option  GSM_P((gsm, long, long *));

extern void gsm_encode  GSM_P((gsm, gsm_signal *, gsm_byte  *));
//extern int  gsm_decode  GSM_P((gsm, gsm_byte   *, gsm_signal *));
extern void  gsm_decode  GSM_P((gsm, gsm_byte   *, gsm_signal *));

extern int  gsm_explode GSM_P((gsm, gsm_byte   *, gsm_signal *));
extern void gsm_implode GSM_P((gsm, gsm_signal *, gsm_byte   *));

#undef  GSM_P
#include "../CCodecBase.h"
class CGsmcodec: public CCodecBase{
public:
   CGsmcodec():CCodecBase(33,320)
   {
      ctx=NULL;//gsm_create();
   }
   ~CGsmcodec()
   {
      if(ctx)gsm_destroy(ctx);
      ctx=NULL;
   }
   int hasPLC(){return 1;}
   virtual inline int encodeFrame(short *pIn, unsigned char *pOut)
   {
      if(!ctx)ctx=gsm_create();
      if(!ctx)return iCodecFrameSizeEnc;//TODO info user
      
      gsm_encode(ctx,(gsm_signal *)pIn,(gsm_byte *)pOut);
      return iCodecFrameSizeEnc;
   }
   virtual inline int decodeFrame(unsigned char *pIn, short *pOut)
   {
      if(!ctx)ctx=gsm_create();
      if(!ctx)return iCodecFrameSizeDec;//TODO info user
      gsm_decode(ctx,(gsm_byte *)pIn,(gsm_signal *)pOut);
      return iCodecFrameSizeDec;
   }
   /*
   virtual int encode(short *pIn,  char *pOut, int iBytes=0)
   {
      int i;
      if(iBytes==0)
         iBytes=iCodecFrameSizeDec;
      int t=iBytes/iCodecFrameSizeDec;
      for(i=0 ;  i<t ;  i++, pIn+=(iCodecFrameSizeDec/2), pOut+=iCodecFrameSizeEnc)
      {
         gsm_encode(ctx,(gsm_signal *)pIn,(gsm_byte *)pOut);
      }
      return t*iCodecFrameSizeEnc;
   }
   virtual int decode(char *pIn, short *pOut,int iBytes=0)
   {
      int i;
      int t=iBytes/iCodecFrameSizeEnc;
      if(iBytes==0)
         iBytes=iCodecFrameSizeEnc;
      for(i=0 ;  i<t ; i++ ,pIn+=iCodecFrameSizeEnc,pOut+=(iCodecFrameSizeDec)/2)
      {
         gsm_decode(ctx,(gsm_byte *)pIn,(gsm_signal *)pOut);
      }
      return iCodecFrameSizeDec*t; 
   }
   */
   /*
   inline void encode(gsm_signal *signal, gsm_byte *bufout)
   {
      gsm_encode(ctx,signal,bufout);
   }
   inline void decode(gsm_byte *bufin, gsm_signal *signal)
   {
      gsm_decode(ctx,bufin,signal);
   }*/

private:


 gsm ctx;

public:
 static const word gsm_A[8], gsm_B[8], gsm_MIC[8], gsm_MAC[8];
 static const word gsm_INVA[8];
 static const word gsm_DLB[4], gsm_QLB[4];
 static const word gsm_H[11];
 static const word gsm_NRFAC[8];
 static const word gsm_FAC[8];
 static const unsigned char bitoff[ 256 ];

};



#endif  /* GSM_H */
