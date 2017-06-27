/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.thread;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.support.v4.content.ContextCompat;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.providers.AudioProvider;
import com.silentcircle.messaging.providers.VCardProviderUtils;
import com.silentcircle.messaging.providers.VideoProvider;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static android.graphics.BitmapFactory.decodeResource;

public class CreateThumbnail  {

    private enum ContentType {

        UNKNOWN,
        IMAGE,
        VIDEO,
        AUDIO,
        VCARD;

        public static ContentType forName( String name ) {
            if( name == null ) {
                return UNKNOWN;
            }
            if( name.startsWith( "image/" ) ) {
                return IMAGE;
            }
            if( name.startsWith( "video/" ) ) {
                return VIDEO;
            }
            if( name.startsWith( "audio/" ) || MIME.isAudio(name) ) {
                return AUDIO;
            }
            if( MIME.isContact( name )) {
                return VCARD;
            }
            return UNKNOWN;
        }

    }

    private static final Uri ALBUM_ART_URI = Uri.parse( "content://media/external/audio/albumart" );

    private static Bitmap createEmptyThumbnail( int targetSize ) {
        return createEmptyThumbnail( targetSize, targetSize );
    }

    private static Bitmap createEmptyThumbnail( int targetWidth, int targetHeight ) {
        Bitmap bitmap = Bitmap.createBitmap( targetWidth, targetHeight, Bitmap.Config.ARGB_8888 );
        new Canvas( bitmap ).drawColor( 0xFF000000 );
        return bitmap;
    }

    /*
     * Create bitmap from first frame of the video
     * Size matching MediaStore.Video.Thumbnails.MINI_KIND is assumed.
     *
     * This function is copied from Android's ThumbnailUtils.
     */
    public static Bitmap createVideoThumbnail(Context context, Uri file) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, file);
            bitmap = retriever.getFrameAtTime(-1);
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }

        if (bitmap == null) return null;

        // Scale down the bitmap if it's too large.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        if (max > 512) {
            float scale = 512f / max;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }

        return bitmap;
    }

    private static Bitmap decoratePlayableThumbnail( Bitmap bitmap ) {

        int centerX = bitmap.getWidth() / 2;
        int centerY = bitmap.getHeight() / 2;
        int innerBound = Math.min( bitmap.getWidth(), bitmap.getHeight() );
        int outerRadius = innerBound / 4;
        int innerRadius = innerBound / 8;

        Bitmap mutableBitmap = null;

        if(!bitmap.isMutable()) {
            try {
                Bitmap immutableBitmap = Bitmap.createBitmap(bitmap);

                if (immutableBitmap == null) {
                    return bitmap;
                }

                mutableBitmap = immutableBitmap.copy(Bitmap.Config.ARGB_8888, true);
                immutableBitmap.recycle();
            } catch (OutOfMemoryError exception) {
                // Just continue with original bitmap
            }
        }

        Canvas canvas = new Canvas( mutableBitmap != null ? mutableBitmap : bitmap );
        Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
        paint.setStrokeWidth( 2 );

        Path triangle = new Path();
        triangle.moveTo( centerX - innerRadius, centerY - innerRadius );
        triangle.lineTo( centerX + innerRadius, centerY );
        triangle.lineTo( centerX - innerRadius, centerY + innerRadius );
        triangle.close();

        paint.setColor( 0x88000000 );
        paint.setStyle( Style.FILL );
        canvas.drawCircle( centerX, centerY, outerRadius, paint );

        paint.setColor( 0xCCFFFFFF );
        paint.setStyle( Style.STROKE );
        canvas.drawCircle( centerX, centerY, outerRadius, paint );

        paint.setStyle( Style.FILL );
        canvas.drawPath( triangle, paint );

        return bitmap;

    }

    private static Bitmap resize( Bitmap bitmap, int targetWidth, int targetHeight ) {
        if(bitmap == null) {
            return null;
        }

        Bitmap mutableBitmap = null;

        if(!bitmap.isMutable()) {
            try {
                Bitmap immutableBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);

                if (immutableBitmap == null) {
                    return bitmap;
                }

                mutableBitmap = immutableBitmap.copy(Bitmap.Config.ARGB_8888, true);
                immutableBitmap.recycle();
            } catch (OutOfMemoryError exception) {
                return bitmap;
            }
        }

        return mutableBitmap != null ? mutableBitmap : bitmap;
    }

    protected final PackageManager packageManager;
    protected final ContentResolver resolver;
    protected final Uri uri;
    protected final String contentType;
    protected final Context mContext;

    private final int maximumWidth;

    private final int maximumHeight;

    private static final String TAG = CreateThumbnail.class.getSimpleName();

    public CreateThumbnail( Context context, Intent intent, int maximumWidth, int maximumHeight ) {
        this( context, intent.getData(), intent.getType(), maximumWidth, maximumHeight );
    }

    public CreateThumbnail( Context context, Uri uri, String contentType, int maximumWidth, int maximumHeight ) {
        this( context, context.getPackageManager(), context.getContentResolver(), uri, contentType, maximumWidth, maximumHeight );
    }

    public CreateThumbnail( Context context, PackageManager packageManager, ContentResolver resolver, Uri uri, String contentType, int maximumWidth, int maximumHeight ) {
        mContext = context;
        this.packageManager = packageManager;
        this.resolver = resolver;
        this.uri = uri;
        this.contentType = contentType;
        this.maximumWidth = maximumWidth;
        this.maximumHeight = maximumHeight;
    }

    private Bitmap decorateAudioThumbnail( Bitmap bitmap ) {
        return ( bitmap == null ? createEmptyThumbnail( Math.min( maximumWidth, maximumHeight ) ) : bitmap );
    }

    protected Bitmap decorateUnknownThumbnail( Bitmap bitmap ) {
        return null;
//         These are very low quality
//        Intent intent = new Intent( Intent.ACTION_VIEW );
//        intent.setDataAndType( uri, contentType );
//        ResolveInfo activity = packageManager.resolveActivity( intent, 0 );
//        if( activity != null ) {
//            if(activity.activityInfo.packageName.equals("android")) {
//                return null;
//            }
//
//            Drawable icon = activity.loadIcon( packageManager );
//            icon.setBounds( 0, 0, bitmap.getWidth(), bitmap.getHeight() );
//            icon.draw( new Canvas( bitmap ) );
//        } else {
//            return null;
//        }
//        return bitmap;
    }

    private Bitmap decorateVideoThumbnail( Bitmap bitmap ) {
        return (bitmap == null ? createEmptyThumbnail(maximumWidth, maximumWidth * 9 / 16) : bitmap);
    }

    private Bitmap getAudioThumbnail() {

        Bitmap bitmap = null;
        try {
            // start with default bitmap for audio files
            bitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_sound);
        } catch (Throwable t) {
            // default black rectangle will be used
            bitmap = createEmptyThumbnail(Math.min(maximumWidth, maximumHeight));
        }

        if( uri.equals(AudioProvider.CONTENT_URI) ) {
            return decorateAudioThumbnail(resize(bitmap));
        } else {
            if (!hasMediaPermission()) {
                return decorateAudioThumbnail(resize(bitmap));
            }

            Cursor cursor = resolver.query(uri, new String[]{
                    AudioColumns.ALBUM_ID
            }, null, null, null);

            if (cursor == null) {
                return decorateAudioThumbnail(resize(bitmap));
            }

            if (cursor.moveToNext()) {
                try {
                    long albumID = cursor.getLong(cursor.getColumnIndexOrThrow(AudioColumns.ALBUM_ID));
                    Uri albumArtURI = ContentUris.withAppendedId(ALBUM_ART_URI, albumID);
                    bitmap = MediaStore.Images.Media.getBitmap(resolver, albumArtURI);
                } catch (IOException exception) {
                    bitmap = getUnknownThumbnail();
                } catch (IllegalArgumentException exception) {
                    bitmap = getUnknownThumbnail();
                }
            }
            cursor.close();

            return decorateAudioThumbnail(resize(bitmap));
        }
    }
    private Bitmap getVCardThumbnail() {
        Bitmap result = VCardProviderUtils.getVCardPreviewForContact(mContext, uri);
        if (result == null) {
            result = getThumbnailFromResource(R.drawable.ic_vcard);
        }
        return result;
    }

    private Bitmap getThumbnailFromResource(int resourceId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.outWidth = 96;
        options.outHeight = 128;
        options.inMutable = true;
        return decodeResource(mContext.getResources(), resourceId, options);
    }

    private ContentType getContentType() {
        return ContentType.forName(contentType);
    }

    private Bitmap getImageThumbnail() {

        InputStream input = null;
        Matrix matrix = null;
        BitmapFactory.Options options = new BitmapFactory.Options();

        try {
            input = resolver.openInputStream( uri );
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
        } catch( IOException exception ) {
            Log.w(TAG, "#getImageThumbnail", exception);
        } catch( SecurityException exception ) {
            Log.w(TAG, "#getImageThumbnail", exception);
        } finally {
            IOUtils.close(input);
        }

        matrix = getOrientation(mContext, uri);

        try {
            input = resolver.openInputStream( uri );
            setSampleSize( options );
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeStream( input, null, options );
            if (matrix != null) {
                Bitmap recyclable = bitmap;
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, false);
                recyclable.recycle();
            }
            return bitmap;
        } catch( IOException exception ) {
            Log.w(TAG, "#getImageThumbnail", exception);
        } catch( SecurityException exception ) {
            Log.w(TAG, "#getImageThumbnail", exception);
        } finally {
            IOUtils.close( input );
        }

        return decorateUnknownThumbnail(createEmptyThumbnail(Math.min(maximumWidth, maximumHeight)));
    }

    private Matrix getOrientation(Context context, Uri photoUri) {
        Matrix matrix = null;
        InputStream input = null;
        try {
            input = resolver.openInputStream(photoUri);
            File outputDir = context.getCacheDir();
            File outputFile =
                    File.createTempFile(UUIDGen.makeType1UUID().toString(), "tmp", outputDir);
            IOUtils.writeToFile(outputFile, input);
            matrix = ViewUtil.getRotationMatrixFromExif(outputFile.getAbsolutePath());
            outputFile.delete();
        } catch (IOException exception) {
            Log.w(TAG, "#getOrientation", exception);
        } catch (SecurityException exception) {
            Log.w(TAG, "#getOrientation", exception);
        } finally {
            IOUtils.close(input);
        }
        return matrix;
    }

    public Bitmap getThumbnail() {
        Bitmap result = getThumbnail( getContentType() );

        if (result == null) {
            int previewIcon = AttachmentUtils.getPreviewIcon(contentType);
            if (previewIcon != 0) {
                result = getThumbnailFromResource(previewIcon);
            }
            else {
                result = getUnknownThumbnail();
            }
        }

        return result;

    }

    private Bitmap getThumbnail( ContentType type ) {
        switch( type ) {
            case IMAGE:
                return getImageThumbnail();
            case VIDEO:
                return getVideoThumbnail();
            case AUDIO:
                return getAudioThumbnail();
            case VCARD:
                return getVCardThumbnail();
            default:
                return null;
        }
    }

    private Bitmap getUnknownThumbnail() {
        return decorateUnknownThumbnail( createEmptyThumbnail( Math.min( maximumWidth, maximumHeight ) ) );
    }

    private Bitmap getVideoThumbnail() {
        Bitmap bitmap = null;
        if( uri.equals(VideoProvider.CONTENT_URI) ) {
            bitmap = createVideoThumbnail( mContext, uri );
        } else if( uri.equals(AudioProvider.CONTENT_URI) ) {
            return decorateAudioThumbnail(resize(bitmap));
        } else {
            if (!hasMediaPermission()) {
                bitmap = createVideoThumbnail( mContext, uri );
            } else {
                Cursor cursor = MediaStore.Video.query(resolver, uri, new String[]{
                        BaseColumns._ID
                });

                if (cursor != null) {
                    if (cursor.moveToNext()) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inMutable = true;
                        bitmap = MediaStore.Video.Thumbnails.getThumbnail(resolver, cursor.getInt(0), MediaStore.Video.Thumbnails.MINI_KIND, options);
                    } else {
                        bitmap = getVideoThumbnailFroyo();
                    }

                    cursor.close();
                }

                if (bitmap == null) {
                    bitmap = createVideoThumbnail(mContext, uri);
                }
            }
        }
        return decorateVideoThumbnail( resize( bitmap ) );
    }

    @TargetApi( Build.VERSION_CODES.FROYO )
    private Bitmap getVideoThumbnailFroyo() {
        Bitmap bitmap = null;
        if( android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO ) {
            bitmap = ThumbnailUtils.createVideoThumbnail( uri.getPath(), MediaStore.Video.Thumbnails.MINI_KIND );
        }
        return bitmap;
    }

    /**
     * @param bitmap
     */
    protected void onThumbnailCreated( Bitmap bitmap ) {
        // By default, do nothing.
    }

    private Bitmap resize( Bitmap bitmap ) {
        if( bitmap == null ) {
            return bitmap;
        }
        int targetWidth = bitmap.getWidth();
        int targetHeight = bitmap.getHeight();
        double scale = 1;
        scale = Math.min( scale, (double) maximumWidth / targetWidth );
        scale = Math.min( scale, (double) maximumHeight / targetHeight );
        targetWidth = (int) Math.floor(targetWidth * scale);
        targetHeight = (int) Math.floor(targetHeight * scale);
        return resize(bitmap, targetWidth, targetHeight);
    }

    private void setSampleSize( BitmapFactory.Options options ) {
        int sampleWidth = options.outWidth / maximumWidth;
        int sampleHeight = options.outHeight / maximumHeight;
        options.inSampleSize = Math.max( sampleWidth, sampleHeight );
        if( options.inSampleSize < 1 ) {
            options.inSampleSize = 1;
        }
    }

    // Used for generating thumbnails
    private boolean hasMediaPermission() {
        return ContextCompat.checkSelfPermission(mContext,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

}
