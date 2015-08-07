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

#ifndef _C_T_LABEL_BASE_h
#define _C_T_LABEL_BASE_h
#include "CTEditBase.h"
//#include "../os/CTOsGui.h"

class CTDeviceCtx;
class CTFontN;

class CTLabelBase : public CTEditBase{
public:
   CTAlign al;
   int iCol;
   CTFontN *fontLb;
   

   CTLabelBase(int iMaxLen);
   CTLabelBase(char *p, int iLen=0,int iIsUnicode=0, int col=-2);
   virtual ~CTLabelBase(){}
   void draw(CTDeviceCtx & iBase, const CTRect &rc, int iXOff=0, int iYOff=0, int iIsPwd=0);

};
class CTLabelBasePtr: public CTStr {
public:
   CTAlign al;
   int iCol;
   CTFontN *fontLb;
   //CTEditBase &eB;
   
   CTLabelBasePtr(const CTStrBase &e);
   void draw(CTDeviceCtx & iBase, const CTRect &rc, int iXOff=0, int iYOff=0, int iIsPwd=0);

};
#endif //_C_T_LABEL_BASE_h
