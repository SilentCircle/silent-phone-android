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



#import <UIKit/UIKit.h>

#include "CTVideoOut_Quartz.h"

@protocol UITouchDetector <NSObject>

@optional

- (void)onTouch:(UIView *)v updown:(int)updown x:(int)x y:(int)y;

@end

class CTAVideoMem{
   int *data;
   int iDataSize;
   int w,h;
public:
   
   CTAVideoMem(){dstLayer=NULL;data=NULL;w=h=0;iDataSize=0;}
   ~CTAVideoMem(){delete data;data=NULL;}
   
   void rel();
   
   void clear(){
      if(data)memset(data,255,iDataSize);
   }
   void setSize(int cx, int cy);
   int *getData(){return data;}
   
   CGImageRef imageEmpty;
   CGContextRef drawCtx;
   CALayer *dstLayer;
   
};

typedef struct{
   CTVideoOut *pVO;
   CTAVideoMem vm[2];
   CALayer *prevL;
   
   int iPrevVM;
   int iPrevFrame;
   int iInUse;
   
   int w,h;
   
   void init(){
      iInUse=0;
      prevL=NULL;
      w=0;h=0;
      iPrevFrame=-1;
      pVO=NULL;
   }
   
   
   void setSize(int cx, int cy){
      if(w==cx && h==cy)return;
      w=cx;
      h=cy;
      vm[0].setSize(cx,cy);
      vm[1].setSize(cx,cy);
   }

   
}VOObject;

#define eMaxVOObjCnt 8
//TODO fix this class ,data, iDataSize ,drawCtx
@interface QuartzImageView : UIView
{
@public int iCanDrawOnScreen;

 
   id <UITouchDetector>  _touchDetector;

}

-(int)getDrawInto:(CTVideoOut*)vo;
-(void *)getNextData:(CTVideoOut*)vo;
-(void )relData:(CTVideoOut*)vo;
-(void)clearImg:(CTVideoOut*)vo;
//-(void)drawInContext:(CGContextRef)context;

@property(nonatomic,assign)   id <UITouchDetector>   touchDetector;
//@property(nonatomic,readwrite) int* data;
//@property(nonatomic,readwrite) CGContextRef drawCtx;

@end
