//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#ifndef _TINA_EXP_H
#define _TINA_EXP_H
#define _T_EXP_V extern 
class CTBmpBase;
_T_EXP_V void relTinaEnc(void *p);
_T_EXP_V void relTinaDec(void *p);
_T_EXP_V void *initTinaEnc();
_T_EXP_V void *initTinaDec();
_T_EXP_V int tina_decode(void *ctx, unsigned char *pI, int iLen, CTBmpBase *cVO);
_T_EXP_V int tina_encode(void *ctx, unsigned char *pI, unsigned char *pOut, int cx, int cy);
_T_EXP_V int tinaSpliter(unsigned char *pI, int iLen);
_T_EXP_V int tinaCmdD(void *ctx, char *cmd);
_T_EXP_V int tinaCmdE(void *ctx, char *cmd);
_T_EXP_V int tinaCanDrawThis(unsigned char *pI, int iLen);

_T_EXP_V int tinaCanDrawThis(unsigned char *pI, int iLen);
_T_EXP_V int tinaCanSkipThis(unsigned char *pI, int iLen);
_T_EXP_V int tina_canDecode(unsigned char *pI, int iLen);
#endif
