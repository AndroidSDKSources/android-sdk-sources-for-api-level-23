/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package javax.crypto;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import libcore.io.Streams;

/**
 * This class wraps an {@code InputStream} and a cipher so that {@code read()}
 * methods return data that are read from the underlying {@code InputStream} and
 * processed by the cipher.
 * <p>
 * The cipher must be initialized for the requested operation before being used
 * by a {@code CipherInputStream}. For example, if a cipher initialized for
 * decryption is used with a {@code CipherInputStream}, the {@code
 * CipherInputStream} tries to read the data an decrypt them before returning.
 */
public class CipherInputStream extends FilterInputStream {
    private final Cipher cipher;
    private final byte[] inputBuffer;
    private byte[] outputBuffer;
    private int outputIndex; // index of the first byte to return from outputBuffer
    private int outputLength; // count of the bytes to return from outputBuffer
    private boolean finished;

    /**
     * Creates a new {@code CipherInputStream} instance for an {@code
     * InputStream} and a cipher.
     *
     * <p><strong>Warning:</strong> passing a null source creates an invalid
     * {@code CipherInputStream}. All read operations on such a stream will
     * fail.
     *
     * @param is
     *            the input stream to read data from.
     * @param c
     *            the cipher to process the data with.
     */
    public CipherInputStream(InputStream is, Cipher c) {
        super(is);
        this.cipher = c;
        int blockSize = Math.max(c.getBlockSize(), 1);
        int bufferSize = Math.max(blockSize,
                BufferedInputStream.DEFAULT_BUFFER_SIZE / blockSize * blockSize);
        inputBuffer = new byte[bufferSize];
        outputBuffer = new byte[bufferSize + ((blockSize > 1) ? 2 * blockSize : 0)];
    }

    /**
     * Creates a new {@code CipherInputStream} instance for an {@code
     * InputStream} without a cipher.
     * <p>
     * A {@code NullCipher} is created and used to process the data.
     *
     * @param is
     *            the input stream to read data from.
     */
    protected CipherInputStream(InputStream is) {
        this(is, new NullCipher());
    }

    /**
     * Attempts to fill the input buffer and process some data through the
     * cipher. Returns {@code true} if output from the cipher is available to
     * use.
     */
    private boolean fillBuffer() throws IOException {
        if (finished) {
            return false;
        }
        outputIndex = 0;
        outputLength = 0;
        while (outputLength == 0) {
            // check output size on each iteration since pending state
            // in the cipher can cause this to vary from call to call
            int outputSize = cipher.getOutputSize(inputBuffer.length);
            if ((outputBuffer == null) || (outputBuffer.length < outputSize)) {
                this.outputBuffer = new byte[outputSize];
            }
            int byteCount = in.read(inputBuffer);
            if (byteCount == -1) {
                try {
                    outputLength = cipher.doFinal(outputBuffer, 0);
                } catch (Exception e) {
                    throw new IOException("Error while finalizing cipher", e);
                }
                finished = true;
                return outputLength != 0;
            }
            try {
                outputLength = cipher.update(inputBuffer, 0, byteCount, outputBuffer, 0);
            } catch (ShortBufferException e) {
                throw new AssertionError(e);  // should not happen since we sized with getOutputSize
            }
        }
        return true;
    }

    /**
     * Reads the next byte from this cipher input stream.
     *
     * @return the next byte, or {@code -1} if the end of the stream is reached.
     * @throws IOException
     *             if an error occurs.
     */
    @Override
    public int read() throws IOException {
        if (in == null) {
            throw new NullPointerException("in == null");
        }
        if (outputIndex == outputLength && !fillBuffer()) {
            return -1;
        }
        return outputBuffer[outputIndex++] & 0xFF;
    }

    /**
     * Reads the next {@code len} bytes from this input stream into buffer
     * {@code buf} starting at offset {@code off}.
     * <p>
     * if {@code buf} is {@code null}, the next {@code len} bytes are read and
     * discarded.
     *
     * @return the number of bytes filled into buffer {@code buf}, or {@code -1}
     *         of the of the stream is reached.
     * @throws IOException
     *             if an error occurs.
     * @throws NullPointerException
     *             if the underlying input stream is {@code null}.
     */
    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (in == null) {
            throw new NullPointerException("in == null");
        }
        if (outputIndex == outputLength && !fillBuffer()) {
            return -1;
        }
        int available = outputLength - outputIndex;
        if (available < len) {
            len = available;
        }
        if (buf != null) {
            System.arraycopy(outputBuffer, outputIndex, buf, off, len);
        }
        outputIndex += len;
        return len;
    }

    @Override
    public long skip(long byteCount) throws IOException {
        return Streams.skipByReading(this, byteCount);
    }

    @Override
    public int available() throws IOException {
        return outputLength - outputIndex;
    }

    /**
     * Closes this {@code CipherInputStream}, also closes the underlying input
     * stream and call {@code doFinal} on the cipher object.
     *
     * @throws IOException
     *             if an error occurs.
     */
    @Override
    public void close() throws IOException {
        in.close();
        try {
            cipher.doFinal();
        } catch (GeneralSecurityException ignore) {
            //do like RI does
        }

    }

    /**
     * Returns whether this input stream supports {@code mark} and
     * {@code reset}, which it does not.
     *
     * @return false, since this input stream does not support {@code mark} and
     *         {@code reset}.
     */
    @Override
    public boolean markSupported() {
        return false;
    }
}
