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

#import "SquareCamViewController.h"
#import <CoreImage/CoreImage.h>
#import <ImageIO/ImageIO.h>
#import <AssertMacros.h>
#import <AssetsLibrary/AssetsLibrary.h>
#include <CoreGraphics/CGContext.h>
#import <UIKit/UIGraphics.h>

#include "CTVideoInIOS.h"
int getIOSVersion();

#pragma mark-

// used for KVO observation of the @"capturingStillImage" property to perform flash bulb animation
static const NSString *AVCaptureStillImageIsCapturingStillImageContext = @"AVCaptureStillImageIsCapturingStillImageContext";

static CGFloat DegreesToRadians(CGFloat degrees) {return degrees * M_PI / 180;};

static void ReleaseCVPixelBuffer(void *pixel, const void *data, size_t size);
static void ReleaseCVPixelBuffer(void *pixel, const void *data, size_t size)
{
	CVPixelBufferRef pixelBuffer = (CVPixelBufferRef)pixel;
	CVPixelBufferUnlockBaseAddress( pixelBuffer, 0 );
	CVPixelBufferRelease( pixelBuffer );
}

// create a CGImage with provided pixel buffer, pixel buffer must be uncompressed kCVPixelFormatType_32ARGB or kCVPixelFormatType_32BGRA
static OSStatus CreateCGImageFromCVPixelBuffer(CVPixelBufferRef pixelBuffer, CGImageRef *imageOut);
static OSStatus CreateCGImageFromCVPixelBuffer(CVPixelBufferRef pixelBuffer, CGImageRef *imageOut)
{
	OSStatus err = noErr;
	OSType sourcePixelFormat;
	size_t width, height, sourceRowBytes;
	void *sourceBaseAddr = NULL;
	CGBitmapInfo bitmapInfo;
	CGColorSpaceRef colorspace = NULL;
	CGDataProviderRef provider = NULL;
	CGImageRef image = NULL;
	
	sourcePixelFormat = CVPixelBufferGetPixelFormatType( pixelBuffer );
	if ( kCVPixelFormatType_32ARGB == sourcePixelFormat )
		bitmapInfo = kCGBitmapByteOrder32Big | kCGImageAlphaNoneSkipFirst;
	else if ( kCVPixelFormatType_32BGRA == sourcePixelFormat )
		bitmapInfo = kCGBitmapByteOrder32Little | kCGImageAlphaNoneSkipFirst;
	else
		return -95014; // only uncompressed pixel formats
	
	sourceRowBytes = CVPixelBufferGetBytesPerRow( pixelBuffer );
	width = CVPixelBufferGetWidth( pixelBuffer );
	height = CVPixelBufferGetHeight( pixelBuffer );
	
	CVPixelBufferLockBaseAddress( pixelBuffer, 0 );
	sourceBaseAddr = CVPixelBufferGetBaseAddress( pixelBuffer );
	
	colorspace = CGColorSpaceCreateDeviceRGB();
   
	CVPixelBufferRetain( pixelBuffer );
	provider = CGDataProviderCreateWithData( (void *)pixelBuffer, sourceBaseAddr, sourceRowBytes * height, ReleaseCVPixelBuffer);
	image = CGImageCreate(width, height, 8, 32, sourceRowBytes, colorspace, bitmapInfo, provider, NULL, true, kCGRenderingIntentDefault);
	
bail:
	if ( err && image ) {
		CGImageRelease( image );
		image = NULL;
	}
	if ( provider ) CGDataProviderRelease( provider );
	if ( colorspace ) CGColorSpaceRelease( colorspace );
	*imageOut = image;
	return err;
}

// utility used by newSquareOverlayedImageForFeatures for
static CGContextRef CreateCGBitmapContextForSize(CGSize size);
static CGContextRef CreateCGBitmapContextForSize(CGSize size)
{
   CGContextRef    context = NULL;
   CGColorSpaceRef colorSpace;
   int             bitmapBytesPerRow;
	
   bitmapBytesPerRow = (size.width * 4);
	
   colorSpace = CGColorSpaceCreateDeviceRGB();
   context = CGBitmapContextCreate (NULL,
                                    size.width,
                                    size.height,
                                    8,      // bits per component
                                    bitmapBytesPerRow,
                                    colorSpace,
                                    kCGImageAlphaPremultipliedLast);
	CGContextSetAllowsAntialiasing(context, NO);
   CGColorSpaceRelease( colorSpace );
   return context;
}

#pragma mark-
/*
 @interface UIImage (RotationMethods)
 - (UIImage *)imageRotatedByDegrees:(CGFloat)degrees;
 @end
 
 @implementation UIImage (RotationMethods)
 
 - (UIImage *)imageRotatedByDegrees:(CGFloat)degrees
 {
 
 // calculate the size of the rotated view's containing box for our drawing space
 UIView *rotatedViewBox = [[UIView alloc] initWithFrame:CGRectMake(0,0,self.size.width, self.size.height)];
 CGAffineTransform t = CGAffineTransformMakeRotation(DegreesToRadians(degrees));
 rotatedViewBox.transform = t;
 CGSize rotatedSize = rotatedViewBox.frame.size;
 [rotatedViewBox release];
 
 // Create the bitmap context
 UIGraphicsBeginImageContext(rotatedSize);
 CGContextRef bitmap = UIGraphicsGetCurrentContext();
 
 // Move the origin to the middle of the image so we will rotate and scale around the center.
 CGContextTranslateCTM(bitmap, rotatedSize.width/2, rotatedSize.height/2);
 
 //   // Rotate the image context
 CGContextRotateCTM(bitmap, DegreesToRadians(degrees));
 
 // Now, draw the rotated/scaled image into the context
 CGContextScaleCTM(bitmap, 1.0, -1.0);
 CGContextDrawImage(bitmap, CGRectMake(-self.size.width / 2, -self.size.height / 2, self.size.width, self.size.height), [self CGImage]);
 
 UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
 UIGraphicsEndImageContext();
 return newImage;
 
 }
 
 @end
 */

#pragma mark-

@interface SquareCamViewController (InternalMethods)
- (void)setupAVCapture;
- (void)teardownAVCapture;

@end

@implementation SquareCamViewController

-(int)capturing{
   return iRunning && session && [session isRunning];
}

- (void)setupAVCapture
{
   if(session)return;
   
   
   previewLayer=nil;
   
   
   iRunning=0;
   
#if 1
	NSError *error = nil;
   
	
	session = [AVCaptureSession new];
   /*
    if ([[UIDevice currentDevice] userInterfaceIdiom] == UIUserInterfaceIdiomPhone)
    [session setSessionPreset:
    AVCaptureSessionPreset352x288];//
    //  AVCaptureSessionPreset640x480];//AVCaptureSessionPreset1280x720
    else
    [session setSessionPreset:AVCaptureSessionPresetPhoto];
    */
   [session setSessionPreset:AVCaptureSessionPreset352x288];//
   //176x144  [session setSessionPreset:AVCaptureSessionPresetLow];
   // Select a video device, make an input
	AVCaptureDevice *device = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
	AVCaptureDeviceInput *deviceInput = [AVCaptureDeviceInput deviceInputWithDevice:device error:&error];
	//require( error == nil, bail );
   if(!error){
      
      isUsingFrontFacingCamera = NO;
      if ( [session canAddInput:deviceInput] )
         [session addInput:deviceInput];
      
      
      
      // Make a video data output
      videoDataOutput = [[AVCaptureVideoDataOutput alloc] init];
      //	videoDataOutput = [AVCaptureVideoDataOutput new];
      
      /*
       kCVPixelFormatType_422YpCbCr8
       */
      // we want BGRA, both CoreGraphics and OpenGL work well with 'BGRA'
      NSDictionary *rgbOutputSettings = [NSDictionary dictionaryWithObject:
                                         [NSNumber numberWithInt:
                                          //   kCVPixelFormatType_422YpCbCr8
                                          kCMPixelFormat_32BGRA
                                          ] forKey:(id)kCVPixelBufferPixelFormatTypeKey];
      
      [videoDataOutput setVideoSettings:rgbOutputSettings];
      [videoDataOutput setAlwaysDiscardsLateVideoFrames:YES]; //with NO is worse // discard if the data output queue is blocked (as we process the still image)
      
      // create a serial dispatch queue used for the sample buffer delegate as well as when a still image is captured
      // a serial dispatch queue must be used to guarantee that video frames will be delivered in order
      // see the header doc for setSampleBufferDelegate:queue: for more information
      dispatch_queue_t queue = dispatch_queue_create("myQueue", NULL);
      [videoDataOutput setSampleBufferDelegate:self queue:queue];
      dispatch_release(queue);
      
      if ( [session canAddOutput:videoDataOutput] )
         [session addOutput:videoDataOutput];
      
      
      previewLayer = [[AVCaptureVideoPreviewLayer alloc] initWithSession:session];
      [previewLayer setBackgroundColor:[[UIColor clearColor] CGColor]];
      /*
       enum {
       AVCaptureVideoOrientationPortrait           = 1,
       AVCaptureVideoOrientationPortraitUpsideDown = 2,
       AVCaptureVideoOrientationLandscapeRight     = 3,
       AVCaptureVideoOrientationLandscapeLeft      = 4,
       };
       typedef
       */
      
      [previewLayer setVideoGravity:AVLayerVideoGravityResizeAspect];//AVLayerVideoGravityResizeAspect];
      iOrientation=[[UIApplication sharedApplication] statusBarOrientation];

      previewView=self;
      CALayer *rootLayer = [previewView layer];
      [rootLayer setMasksToBounds:YES];
      
      CGRect rr=[rootLayer bounds];
      [previewLayer setFrame:rr];
      
      
      if(getIOSVersion()>=6){
         //cvc.transform = CGAffineTransformMakeRotation(rotation);
        //-- AVCaptureConnection *previewLayerConnection=previewLayer.connection;
        //-- previewLayerConnection.videoOrientation=iOrientation;
      }
      else{
        // previewLayer.orientation=iOrientation;
      }
      
      if(1){
         previewLayer.borderWidth = 1;
         previewLayer.borderColor = [UIColor whiteColor].CGColor;
         previewLayer.cornerRadius = 5.0;
         previewLayer.masksToBounds = YES;
      }
      [rootLayer addSublayer:previewLayer];
      
      [self switchCameras:nil];//is it corect ??
      
      [session startRunning];
      iRunning=1;
   }
   
bail:
	
	if (error) {
      iRunning=0;
      [session release];
		UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:[NSString stringWithFormat:@"Failed with error %d", (int)[error code]]
                                                          message:[error localizedDescription]
                                                         delegate:nil
                                                cancelButtonTitle:@"Dismiss"
                                                otherButtonTitles:nil];
		[alertView show];
		[alertView release];
		[self teardownAVCapture];
	}
#endif
}

-(int) getDegFromOrientation:(int)v{
   
   switch(v){
      case AVCaptureVideoOrientationPortrait:return 270;
      case AVCaptureVideoOrientationPortraitUpsideDown:return 90;
      case AVCaptureVideoOrientationLandscapeLeft:return  0;
      case AVCaptureVideoOrientationLandscapeRight:return  180;
   }
   return 270;
}

+(float)getRotation:(int) toInterfaceOrientation{
   float rotation = 0;
   
   if (toInterfaceOrientation==UIInterfaceOrientationPortrait) {
      rotation = 0;
   } else
      if (toInterfaceOrientation==UIInterfaceOrientationLandscapeLeft) {
         rotation = M_PI/2;
      } else
         if (toInterfaceOrientation==UIInterfaceOrientationLandscapeRight) {
            rotation = -M_PI/2;
         }
   return  rotation;
}

-(void)setOrientation:(int)v{
   /*
    enum {
    AVCaptureVideoOrientationPortrait           = 1,
    AVCaptureVideoOrientationPortraitUpsideDown = 2,
    AVCaptureVideoOrientationLandscapeRight     = 3,
    AVCaptureVideoOrientationLandscapeLeft      = 4,
    };
    */
   
   
   iOrientation=v;
   
   if(!previewLayer)return;
   
   CGFloat w = self.frame.size.width;
   CGFloat h = self.frame.size.height;
   int iHor=w>h;
   int iRev=0;
   if(iHor &&
      (v==AVCaptureVideoOrientationPortrait || v==AVCaptureVideoOrientationPortraitUpsideDown))
      iRev=1;
   else if(!iHor &&
           !(v==AVCaptureVideoOrientationPortrait || v == AVCaptureVideoOrientationPortraitUpsideDown))
      iRev=1;
   
   if(iRev)
      previewView.frame = CGRectMake(self.frame.origin.x,self.frame.origin.y, h,w);
   
   
   if(getIOSVersion()>=6){
      //cvc.transform = CGAffineTransformMakeRotation(rotation);
//      AVCaptureConnection *previewLayerConnection=previewLayer.connection;
  //    previewLayerConnection.videoOrientation=iOrientation;
   }
   else{
     // previewLayer.orientation=iOrientation;
   }
   CALayer *rootLayer = [previewView layer];
   [rootLayer setMasksToBounds:YES];
   
   CGRect rr=[rootLayer bounds];
   [previewLayer setFrame:rr];
   
   
   
}

-(void)stopR{
   if(session)[session stopRunning];
}

-(void)startR{
   if(session)[session startRunning];
}


// clean up capture setup
- (void)teardownAVCapture
{
   iRunning=0;
   if(session)[session stopRunning];
   
	if(videoDataOutput)[videoDataOutput release];
   if (videoDataOutputQueue && videoDataOutput)
		dispatch_release(videoDataOutputQueue);
   
   if(previewLayer){
	   [previewLayer removeFromSuperlayer];
	   [previewLayer release];
   }
   previewLayer=NULL;
   
   if(session)[session release];
   session=NULL;
   videoDataOutput=NULL;
}

-(void)dic:(CGContextRef)context im:(UIImage*)im{
   CGContextSetInterpolationQuality(context,kCGInterpolationNone);
   
   CGContextDrawImage(context, self.bounds, [im CGImage]);
   
}

- (UIImage *) imageFromSampleBuffer:(CMSampleBufferRef) sampleBuffer
{
   // Get a CMSampleBuffer's Core Video image buffer for the media data
   CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
   // Lock the base address of the pixel buffer
   CVPixelBufferLockBaseAddress(imageBuffer, 0);
   
   // Get the number of bytes per row for the pixel buffer
   void *baseAddress = CVPixelBufferGetBaseAddress(imageBuffer);
   
   // Get the number of bytes per row for the pixel buffer
   size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
   // Get the pixel buffer width and height
   size_t width = CVPixelBufferGetWidth(imageBuffer);
   size_t height = CVPixelBufferGetHeight(imageBuffer);
   
   // Create a device-dependent RGB color space
   CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
   
   // Create a bitmap graphics context with the sample buffer data
   CGContextRef context = CGBitmapContextCreate(baseAddress, width, height, 8,
                                                bytesPerRow, colorSpace, kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);
   // Create a Quartz image from the pixel data in the bitmap graphics context
   CGImageRef quartzImage = CGBitmapContextCreateImage(context);
   // Unlock the pixel buffer
   CVPixelBufferUnlockBaseAddress(imageBuffer,0);
   
   // Free up the context and color space
   CGContextRelease(context);
   CGColorSpaceRelease(colorSpace);
   
   // Create an image object from the Quartz image
   UIImage *image = [UIImage imageWithCGImage:quartzImage];
   
   // Release the Quartz image
   CGImageRelease(quartzImage);
   
   return (image);
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection
{
   if(!cVI)return;
   
   connection.videoOrientation=iOrientation;//previewLayer.orientation;

   
   CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
   
   CVPixelBufferLockBaseAddress(imageBuffer,0);
   
   
   uint8_t *baseAddress = (uint8_t *)CVPixelBufferGetBaseAddress(imageBuffer);
   //  size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
   size_t width = CVPixelBufferGetWidth(imageBuffer);
   size_t height = CVPixelBufferGetHeight(imageBuffer);
   int deg = [self getDegFromOrientation: iOrientation];
  //-- printf("[deg=%d]",deg);
   
   deg=0;
   
  // printf("[captureOutput %p (%d,%d)]\n",baseAddress,width,height);
   
   unsigned int uiPos=cVI->onNewVideoData((int*)baseAddress, NULL,width, height, deg);
   
   
   CVPixelBufferUnlockBaseAddress(imageBuffer,0);
   if(uiPos>0){
      cVI->sendBuf(uiPos);
   }
   //#define _TEST_BUILT_IN_ENCODER
#ifdef _TEST_BUILT_IN_ENCODER
   /*
    UIImage *im = [self imageFromSampleBuffer:sampleBuffer];
    [im retain];
    dispatch_async(dispatch_get_main_queue(), ^(void) {
    // [self setNeedsDisplay];
    //      [image drawInRect:self.bounds];
    self.image=nil;
    [self setImage:im];
    [im release];
    });
    //[self dic:UIGraphicsGetCurrentContext() im:[image retain]];
    [im release];
    */
   //  self set
   
   // 30% faster
   unsigned int getTickCount();
   unsigned int ui =getTickCount();
   
   UIImage *img = [self imageFromSampleBuffer:sampleBuffer];
   NSData * ns=UIImageJPEGRepresentation (img,.3f );
   //[ns release];
   // [img release];
   
   printf("[hsp%d ns%d]",getTickCount()-ui,ns.length );
   
#endif /*_TEST_BUILT_IN_ENCODER*/
   return;

}


/*
 // utility routing used during image capture to set up capture orientation
 - (AVCaptureVideoOrientation)avOrientationForDeviceOrientation:(UIDeviceOrientation)deviceOrientation
 {
 printf("[deviceOrientation=%d]",deviceOrientation);
 AVCaptureVideoOrientation result = deviceOrientation;
 if ( deviceOrientation == UIDeviceOrientationLandscapeLeft )
 result = AVCaptureVideoOrientationLandscapeRight;
 else if ( deviceOrientation == UIDeviceOrientationLandscapeRight )
 result = AVCaptureVideoOrientationLandscapeLeft;
 return deviceOrientation;//result;
 }
 */


// utility routine to display error aleart if takePicture fails
- (void)displayErrorOnMainQueue:(NSError *)error withMessage:(NSString *)message
{
	dispatch_async(dispatch_get_main_queue(), ^(void) {
		UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:[NSString stringWithFormat:@"%@ (%d)", message, (int)[error code]]
                                                          message:[error localizedDescription]
                                                         delegate:nil
                                                cancelButtonTitle:@"Dismiss"
                                                otherButtonTitles:nil];
		[alertView show];
		[alertView release];
	});
}



/*
// find where the video box is positioned within the preview layer based on the video size and gravity
+ (CGRect)videoPreviewBoxForGravity:(NSString *)gravity frameSize:(CGSize)frameSize apertureSize:(CGSize)apertureSize
{
   CGFloat apertureRatio = apertureSize.height / apertureSize.width;
   CGFloat viewRatio = frameSize.width / frameSize.height;
   
   CGSize size = CGSizeZero;
   if ([gravity isEqualToString:AVLayerVideoGravityResizeAspectFill]) {
      if (viewRatio > apertureRatio) {
         size.width = frameSize.width;
         size.height = apertureSize.width * (frameSize.width / apertureSize.height);
      } else {
         size.width = apertureSize.height * (frameSize.height / apertureSize.width);
         size.height = frameSize.height;
      }
   } else if ([gravity isEqualToString:AVLayerVideoGravityResizeAspect]) {
      if (viewRatio > apertureRatio) {
         size.width = apertureSize.height * (frameSize.height / apertureSize.width);
         size.height = frameSize.height;
      } else {
         size.width = frameSize.width;
         size.height = apertureSize.width * (frameSize.width / apertureSize.height);
      }
   } else if ([gravity isEqualToString:AVLayerVideoGravityResize]) {
      size.width = frameSize.width;
      size.height = frameSize.height;
   }
	
	CGRect videoBox;
	videoBox.size = size;
	if (size.width < frameSize.width)
		videoBox.origin.x = (frameSize.width - size.width) / 2;
	else
		videoBox.origin.x = (size.width - frameSize.width) / 2;
	
	if ( size.height < frameSize.height )
		videoBox.origin.y = (frameSize.height - size.height) / 2;
	else
		videoBox.origin.y = (size.height - frameSize.height) / 2;
   
	return videoBox;
}
*/



- (void)dealloc
{
	[self teardownAVCapture];
	[super dealloc];
}

// use front/back camera
- (IBAction)switchCameras:(id)sender
{
	AVCaptureDevicePosition desiredPosition;
	if (isUsingFrontFacingCamera)
		desiredPosition = AVCaptureDevicePositionBack;
	else
		desiredPosition = AVCaptureDevicePositionFront;
	
	for (AVCaptureDevice *d in [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo]) {
		if ([d position] == desiredPosition) {
			if(previewLayer)[[previewLayer session] beginConfiguration];
			AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:d error:nil];
         if(previewLayer){
            for (AVCaptureInput *oldInput in [[previewLayer session] inputs]) {
               [[previewLayer session] removeInput:oldInput];
            }
            [[previewLayer session] addInput:input];
            [[previewLayer session] commitConfiguration];
         }
			break;
		}
	}
	isUsingFrontFacingCamera = !isUsingFrontFacingCamera;
}

- (void)switchCamerasStep:(int)step
{
	AVCaptureDevicePosition desiredPosition;
	if (isUsingFrontFacingCamera)
		desiredPosition = AVCaptureDevicePositionBack;
	else
		desiredPosition = AVCaptureDevicePositionFront;
	
	for (AVCaptureDevice *d in [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo]) {
		if ([d position] == desiredPosition) {
			if(previewLayer && step==1)[[previewLayer session] beginConfiguration];
			AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:d error:nil];
         if(previewLayer){
            if(step==1){
               for (AVCaptureInput *oldInput in [[previewLayer session] inputs]) {
                  [[previewLayer session] removeInput:oldInput];
               }
            }
            else{
               [[previewLayer session] addInput:input];
               [[previewLayer session] commitConfiguration];
            }
         }
			break;
		}
	}
	if(step==2)isUsingFrontFacingCamera = !isUsingFrontFacingCamera;
}



#pragma mark - View lifecycle

- (id)initWithCoder:(NSCoder *)aDecoder{
   if(self = [super initWithCoder:aDecoder]) {
      [self initT];
      
      // [self addTarget:self  action:@selector(switchCameras:) forControlEvents:UIControlEventTouchDown];
   }
   return self;
}

-(void)initT{
   // iCanStart=0;
   void g_setQWview_vi(void *p);
   g_setQWview_vi(self);
   previewLayer=NULL;
   
   iCanAutoStart=0;
   
}


@end



unsigned int CTVideoInIOS::onNewVideoData(int *d, unsigned char *yuv, int nw, int nh, int iRotDeg){
   if(!iStarted || !d)return 0;
   unsigned int getTickCount();
   unsigned int uiPos=(getTickCount()&~1);
   setXY_priv(nw,nh,iRotDeg==90 || iRotDeg==270);
   unsigned char *p=buf;
   static unsigned int uiPrevFrameAt;
   
   int diff = (int)(uiPos - uiPrevFrameAt);
   
   if(diff>0 && diff<((iVideoFrameEveryMs*3)>>2))return 0;
   
   uiPrevFrameAt=uiPos;
   if(iRotDeg==270){
      
      const int h4=h*4;
      for(int y=0;y<h;y++){
         const int *od=d+y+w*h;
         unsigned char *rb=(unsigned char*)(&od[0]);
         for(int x=0;x<w;x++){
            rb-=h4;
            p[0]=rb[0];
            p[1]=rb[1];
            p[2]=rb[2];
            //
            p+=3;
         }
      }
   }
   else if(iRotDeg==90){
      const int h4=h*4;
      for(int y=0;y<h;y++){
         const int *od=d+(h-y-1)+w*h;
         unsigned char *rb=(unsigned char*)(&od[0]);
         for(int x=0;x<w;x++){
            rb-=h4;
            p[0]=rb[0];
            p[1]=rb[1];
            p[2]=rb[2];
            //
            p+=3;
         }
      }
      
   }
   else if(iRotDeg==0){
      const int sz=w*h;
      const int *od=d;;
      unsigned char *rb=(unsigned char*)(&od[0]);
      for(int i=0;i<sz;i++){
         p[0]=rb[0];
         p[1]=rb[1];
         p[2]=rb[2];
         rb+=4;
         p+=3;
      }
      
   }
   else{
      for(int y=0;y<h;y++){
         const int *od=d+w*(h-y-1);
         unsigned char *rb=(unsigned char*)(&od[0]);
         for(int x=0;x<w;x++){
            p[0]=rb[0];
            p[1]=rb[1];
            p[2]=rb[2];
            rb+=4;
            p+=3;
         }
      }
   }
   return uiPos;
}
void CTVideoInIOS::sendBuf(unsigned int uiPos){
   if(!iStarted || iEnding || !buf)return ;
   if(cb)cb->videoIn(buf,w*h*3,uiPos);
}


CTVideoInIOS::~CTVideoInIOS(){}
int CTVideoInIOS::start(void *pData){
   
   //if(pData==NULL)user_presed
   
   if(pData)idArray.add(pData);
   
   if(!ptr)return 0;
   if(iStarted)return 0;
   init(NULL);
   
   for(int i=0;i<100 && iEnding;i++)usleep(10000);
   
   iStarted=2;
   SquareCamViewController *p=(SquareCamViewController*)ptr;
   if(p->iCanAutoStart)[p setupAVCapture];
   iStarted=1;
   return 0;
}

void CTVideoInIOS::stop(void *pData){
   
   if(pData){
      idArray.rem(pData);
      if(!idArray.isEmpty())return;
   }
   if(!ptr)return;
   
   //if(pData==NULL)user_presed
   
   if(!iStarted || iEnding)return;
   iEnding=1;
   
   for(int i=0;i<20 && iStarted==2;i++)usleep(10000);
   
   iEnding=1;
   iStarted=0;
   
   SquareCamViewController *p=(SquareCamViewController*)ptr;
   [p teardownAVCapture];
   iEnding=0;
   
}
int CTVideoInIOS::init(void *hParent){
   SquareCamViewController *p=(SquareCamViewController*)ptr;
   if(p){
      // p->cb=this;
      p->cVI=this;
   }
   return 0;
}
void CTVideoInIOS::setXY(int x, int y){}//TODO user wants screen x by y

void CTVideoInIOS::setXY_priv(int x, int y, int iRot){
   // h=192*2;w=144*2;
   int wh=w*h;
   if(iRot){
      int r=x;x=y;y=r;
   }
   if(x==w && y==h)return;
   w=x;h=y;
   if(wh==w*h)return;
   unsigned char *nb=new unsigned char [(w+16)*(h+16)*3];
   unsigned char *ob=buf;
   buf=nb;
   delete ob;
   return ;
}
