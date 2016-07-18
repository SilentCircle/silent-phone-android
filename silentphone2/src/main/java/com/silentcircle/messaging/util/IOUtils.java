/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.silentcircle.messaging.model.listener.OnProgressUpdateListener;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;
import java.util.Stack;

/**
 * Provides some basic I/O utilities used internally by this library. Copied from silenttext storage
 * lib and from the silenttext module. Both have a module named IOUtils.
 */
public class IOUtils {

    private final static String TAG = "IOUtils";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final CharsetEncoder utf8Encoder = UTF8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);


    public static void close(Closeable... closeables) {
        for (int i = 0; i < closeables.length; i++) {
            Closeable closeable = closeables[i];
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException exception) {
                    // Ignore.
                }
            }
        }
    }

    public static byte[] encode(String s) {
        try {
            return s.getBytes("UTF-8");
        }
        catch (Exception ex) {
            throw new Error(ex.getMessage() + ":" + s, ex);
        }
    }

    public static String flattenToPrintableAscii(String string) {
        StringBuilder out = new StringBuilder(string.length());
        if (!Normalizer.isNormalized(string, Normalizer.Form.NFD)) {
            string = Normalizer.normalize(string, Normalizer.Form.NFD);
        }
        for (int i = 0, n = string.length(); i < n; ++i) {
            char c = string.charAt(i);
            if (c >= '\u0020' && c <= '\u007F')
                out.append(c);
        }
        return out.toString();
    }

    public static byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c= new byte[aLen+bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    public static void readStream(InputStream in, StringBuilder content) {
        BufferedReader reader;
        content.delete(0, content.length()); // remove old content
        reader = new BufferedReader(new InputStreamReader(in));

        try {
            for (String str = reader.readLine(); str != null; str = reader.readLine()) {
                content.append(str).append('\n');
            }
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "readStream: " + content);
        } catch (IOException e) {
            Log.w(TAG, "I/O Exception: " + e);
            if (ConfigurationUtilities.mTrace) e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                Log.w(TAG, "I/O Exception close stream: " + e);
                if (ConfigurationUtilities.mTrace) e.printStackTrace();
            }
        }
    }

    public static byte[] copyOf(byte[] array) {
        if (array == null) {
            return null;
        }
        byte[] copy = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            copy[i] = array[i];
        }
        return copy;
    }

    public static boolean delete(File file) {

        if (file == null) {
            return true;
        }

        Stack<File> files = new Stack<File>();

        files.add(file);

        while (!files.isEmpty()) {

            File parent = files.peek();

            if (parent.delete()) {
                files.remove(parent);
                continue;
            }

            if (!parent.isDirectory()) {
                return false;
            }

            File[] children = parent.listFiles();

            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                files.add(child);
            }

        }

        return true;

    }

    public static long determineLength(InputStream in) throws IOException {
        long length = 0;
        if (in == null) {
            return length;
        }
        for (long skipped = in.skip(Long.MAX_VALUE); skipped > 0; skipped = in.skip(Long.MAX_VALUE)) {
            length += skipped;
        }
        return length;
    }

    /**
     * Fills the given buffer with bytes from the given input stream.
     *
     * @param in     the stream from which the bytes will be read
     * @param buffer the target buffer
     * @throws IOException if a problem occurs
     */
    public static void fill(InputStream in, byte[] buffer) throws IOException {
        if (in == null || buffer == null) {
            return;
        }
        fill(in, buffer, 0, buffer.length);
    }

    /**
     * Fills the given buffer with bytes from the given input stream, limited to the given bounds.
     *
     * @param in     the stream from which the bytes will be read
     * @param buffer the target buffer
     * @param offset the offset at which the beginning of the read bytes will be stored
     * @param length the number of bytes to read from the input stream
     * @throws IOException if a problem occurs
     */
    public static void fill(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        if (in == null || buffer == null) {
            return;
        }
        int index = offset;
        int remaining = length;
        for (int size = in.read(buffer, index, remaining); size > 0 && remaining > 0; size = in.read(buffer, index, remaining)) {
            index += size;
            remaining -= size;
        }
    }

    public static void flush(Flushable... flushables) {
        for (int i = 0; i < flushables.length; i++) {
            Flushable flushable = flushables[i];
            if (flushable != null) {
                try {
                    flushable.flush();
                } catch (IOException exception) {
                    // Ignore.
                }
            }
        }
    }

    public static int indexOf(byte[] array, byte value) {
        return indexOf(array, 0, array == null ? 0 : array.length, value);
    }

    public static int indexOf(byte[] array, int offset, int length, byte value) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < length; i++) {
            int index = offset + i;
            if (array[index] == value) {
                return index;
            }
        }
        return -1;
    }

    public static int indexOf(char[] array, char value) {
        return indexOf(array, 0, array == null ? 0 : array.length, value);
    }

    public static int indexOf(char[] array, int offset, int length, char value) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < length; i++) {
            int index = offset + i;
            if (array[index] == value) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Reads the specified number of bytes from the input stream and returns the buffer.
     *
     * @param in    the stream from which the bytes will be read
     * @param count the number of bytes to read from the input stream
     * @return a byte array containing {@code count} bytes. If the {@code in} is {@code null},
     * returns {@code null}.
     * @throws IOException if a problem occurs
     */
    public static byte[] read(InputStream in, int count) throws IOException {
        if (in == null) {
            return null;
        }
        byte[] buffer = new byte[count];
        fill(in, buffer);
        return buffer;
    }

    public static byte[] readFully(InputStream in) throws IOException {
        return readFully(in, new byte[16 * 1024]);
    }

    public static byte[] readFully(InputStream in, byte[] buffer) throws IOException {
        return readFully(in, buffer, 0, buffer.length);
    }

    public static byte[] readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int size = in.read(buffer, offset, length); size > -1; size = in.read(buffer, offset, length)) {
            out.write(buffer, offset, size);
        }
        return out.toByteArray();
    }

    private static byte[] toByteArray(ByteBuffer buffer, boolean compatibilityMode) {
        if (compatibilityMode) {
            return buffer.array();
        }
        byte[] array = new byte[buffer.remaining()];
        for (int i = 0; i < array.length; i++) {
            array[i] = buffer.get(i);
        }
        return array;
    }

    public static byte[] toByteArray(char[] buffer) {
        return toByteArray(buffer, false);
    }

    public static byte[] toByteArray(char[] buffer, boolean compatibilityMode) {
        return buffer != null ? toByteArray(CharBuffer.wrap(buffer), compatibilityMode) : null;
    }

    private static byte[] toByteArray(CharBuffer sensitiveData, boolean compatibilityMode) {
        return sensitiveData != null ? toByteArray(UTF8.encode(sensitiveData), compatibilityMode) : null;
    }

    public static byte[] toByteArray(CharSequence sensitiveData) {
        return toByteArray(sensitiveData, false);
    }

    private static byte[] toByteArray(CharSequence sensitiveData, boolean compatibilityMode) {
        return sensitiveData != null ? toByteArray(CharBuffer.wrap(sensitiveData), compatibilityMode) : null;
    }

    public static char[] toCharArray(byte[] buffer) {
        return buffer != null ? toCharArray(ByteBuffer.wrap(buffer)) : null;
    }

    public static char[] toCharArray(ByteBuffer buffer) {
        return buffer != null ? UTF8.decode(buffer).array() : null;
    }

    public static char[] toCharArray(CharSequence sequence) {
        if (sequence == null) {
            return null;
        }
        char[] buffer = new char[sequence.length()];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = sequence.charAt(i);
        }
        return buffer;
    }

    public static CharBuffer toCharBuffer(ByteBuffer buffer) {
        return buffer != null ? UTF8.decode(buffer) : null;
    }

    public static String toString(byte[] buffer) {
        return buffer != null ? toString(ByteBuffer.wrap(buffer)) : null;
    }

    public static String toString(byte[] buffer, int offset, int length) {
        return buffer != null ? toString(ByteBuffer.wrap(buffer, offset, length)) : null;
    }

    public static String toString(ByteBuffer buffer) {
        return buffer != null ? toString(toCharBuffer(buffer)) : null;
    }

    public static String toString(CharBuffer buffer) {
        return buffer != null ? buffer.toString() : null;
    }

    public static byte[] zero(byte[] buffer) {
        return zero(buffer, 0, buffer == null ? 0 : buffer.length);
    }

    public static byte[] zero(byte[] buffer, int offset, int length) {
        if (buffer == null) {
            return buffer;
        }
        for (int i = offset; i < length; i++) {
            buffer[i] = 0;
        }
        return buffer;
    }

    public static char[] zero(char[] buffer) {
        return zero(buffer, 0, buffer == null ? 0 : buffer.length);
    }

    public static char[] zero(char[] buffer, int offset, int length) {
        if (buffer == null) {
            return buffer;
        }
        for (int i = offset; i < length; i++) {
            buffer[i] = 0;
        }
        return buffer;
    }

    /* ***********************************************************
     * From silenttext IOUtils
     *********************************************************** */
    private static final int KB = 1024;

    private static final byte [] SHARED_BUFFER = new byte [32 * KB];

    public static synchronized long pipe( InputStream in, OutputStream out ) throws IOException {
        return pipe( in, out, SHARED_BUFFER );
    }

    public static long pipe( InputStream in, OutputStream out, byte [] buffer ) throws IOException {
        return pipe( in, out, buffer, 0, buffer.length );
    }

    public static long pipe( InputStream in, OutputStream out, byte [] buffer, int offset, int length ) throws IOException {
        return pipe( in, out, buffer, offset, length, null );
    }

    public static long pipe( InputStream in, OutputStream out, byte [] buffer, int offset, int length, OnProgressUpdateListener onProgressUpdate ) throws IOException {
        long total = 0;
        for( int size = in.read( buffer, offset, length ); size > 0; size = in.read( buffer, offset, length ) ) {
            out.write( buffer, offset, size );
            total += size;
            if( onProgressUpdate != null ) {
                onProgressUpdate.onProgressUpdate( total );
            }
        }
        return total;
    }

    public static long pipe( InputStream in, OutputStream out, byte [] buffer, OnProgressUpdateListener onProgressUpdate ) throws IOException {
        return pipe( in, out, buffer, 0, buffer.length, onProgressUpdate );
    }

    public static long pipe( InputStream in, OutputStream out, int bufferSize ) throws IOException {
        return pipe(in, out, new byte[bufferSize]);
    }

    public static synchronized long pipe( InputStream in, OutputStream out, OnProgressUpdateListener onProgressUpdate ) throws IOException {
        return pipe( in, out, SHARED_BUFFER, onProgressUpdate );
    }

    public static String readAsString(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            return readAsString(in);
        } catch (IOException exception) {
            return null;
        } finally {
            close(in);
        }
    }

    public static String readAsString(InputStream in) throws IOException {
        return new String(readFully(in));
    }

    public static boolean isZeros(byte[] array) {
        for(byte b : array) {
            if (b != 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Writes content from passed input stream to passed file.
     */
    public static File writeToFile(final File file, final InputStream input) {
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            int read;
            byte[] buffer = new byte[4 * 1024];
            while ((read = input.read(buffer, 0, buffer.length)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (IOException exception) {
            Log.w(TAG, "#writeToFile", exception);
        } finally {
            IOUtils.close(output);
        }
        return file;
    }

    /**
     * Writes content from passed uri to a temporary file. It is up to caller to delete the file
     * afterwards.
     */
    public static File writeUriContentToTempFile(Context context, Uri uri) {
        File outputFile = null;
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            File outputDir = context.getCacheDir();
            outputFile =
                    File.createTempFile(UUIDGen.makeType1UUID().toString(), "tmp", outputDir);
            writeToFile(outputFile, input);
        } catch (IOException | SecurityException exception) {
            Log.w(TAG, "#writeUriContentToTempFile", exception);
            if (outputFile != null) {
                outputFile.delete();
            }
            outputFile = null;
        } finally {
            IOUtils.close(input);
        }
        return outputFile;
    }

    public static File writeStringContentToTempFile(Context context, String data) {
        File outputFile = null;
        try {
            File outputDir = context.getCacheDir();
            outputFile =
                    File.createTempFile(UUIDGen.makeType1UUID().toString(), "tmp", outputDir);
            PrintWriter out = new PrintWriter(outputFile);
            out.print(data);
        } catch (IOException | SecurityException exception) {
            Log.w(TAG, "#writeUriContentToTempFile", exception);
            if (outputFile != null) {
                outputFile.delete();
            }
            outputFile = null;
        }
        return outputFile;
    }
}
