/*
 * Copyright (c) 2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.ttzero.excel.entity;

import cn.ttzero.excel.util.FileUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Create by guanquan.wang at 2019-05-10 20:04
 */
public class SharedStringTable implements AutoCloseable, Iterable<String> {
    /**
     * The temp path
     */
    private Path temp;

    /**
     * The total unique word in workbook.
     */
    private int count;

    private SeekableByteChannel channel;

    /**
     * Byte array buffer
     */
    private ByteBuffer buffer;

    /**
     * The channel mark not buffer
     */
    private long mark = -1;

    protected SharedStringTable() throws IOException {
        temp = Files.createTempFile("+", ".sst");
        channel = Files.newByteChannel(temp, StandardOpenOption.WRITE, StandardOpenOption.READ);
        // Total keyword storage the header 4 bytes
        channel.position(4);
        buffer = ByteBuffer.allocate(1 << 11);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    protected SharedStringTable(Path path) throws IOException {
        this.temp = path;
        channel = Files.newByteChannel(temp, StandardOpenOption.WRITE, StandardOpenOption.READ);

        buffer = ByteBuffer.allocate(1 << 11);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        channel.read(buffer);
        buffer.flip();

        if (buffer.remaining() > 4) {
            this.count = buffer.getInt();
        }
        buffer.clear();
        // Mark EOF
        channel.position(channel.size());
    }

    protected Path getTemp() {
        return temp;
    }

    /**
     * Write character value into table
     *
     * @param c the character value
     * @return the value index of table
     * @throws IOException if io error occur
     */
    public int push(char c) throws IOException {
        if (buffer.remaining() < 8) {
            flush();
        }
        buffer.putInt(2);
        buffer.putShort((short) 0x8000);
        buffer.putChar(c);
        return count++;
    }

    /**
     * Write string value into table
     *
     * @param key the string value
     * @return the value index of table
     * @throws IOException if io error occur
     */
    public int push(String key) throws IOException {
        byte[] bytes = key.getBytes(UTF_8);
        if (buffer.remaining() < bytes.length + 6) {
            flush();
        }
        buffer.putInt(bytes.length);
        buffer.putShort((short) key.length());
        buffer.put(bytes);
        return count++;
    }

    /**
     * Find character value from begging
     *
     * @param c the character to find
     * @return the index of character in shared string table
     * @throws IOException if io error occur
     */
    public int find(char c) throws IOException {
        return find(c, 0L);
    }

    /**
     * Find from the specified location
     *
     * @param c the character to find
     * @param pos the buffer's position
     * @return the index of character in shared string table
     * @throws IOException if io error occur
     */
    public int find(char c, long pos) throws IOException {
        // Flush before read
        flush();
        int index = 0;
        // Mark current position
        mark().skip(pos);

        A: for (; ;) {
            int dist = channel.read(buffer);
            // EOF
            if (dist <= 0) break;
            buffer.flip();
            for (; buffer.remaining() >= 8 && hasFullValue(buffer);) {
                int a = buffer.getInt();
                short n = buffer.getShort();
                // A char value
                if (n == (short) 0x8000) {
                    // Get it
                    if (buffer.getChar() == c) {
                        break A;
                    }
                } else buffer.position(buffer.position() + a);
                index++;
            }
            buffer.compact();
        }
        reset();
        buffer.rewind();
        // Returns -1 if not found
        return index < count ? index : -1;
    }

    /**
     * Find value from begging
     *
     * @param key the key to find
     * @return the index of character in shared string table
     * @throws IOException if io error occur
     */
    public int find(String key) throws IOException {
        return find(key, 0L);
    }

    /**
     * Find from the specified location
     *
     * @param key the key to find
     * @param pos the buffer's position
     * @return the index of character in shared string table
     * @throws IOException if io error occur
     */
    public int find(String key, long pos) throws IOException {
        // Flush before read
        flush();
        int index = 0;
        // Mark current position
        mark().skip(pos);

        byte[] bytes = key.getBytes(UTF_8);
        A: for (; ;) {
            int dist = channel.read(buffer);
            // EOF
            if (dist <= 0) break;
            buffer.flip();
            for (; buffer.remaining() >= 8 && hasFullValue(buffer);) {
                int a = buffer.getInt();
                short n = buffer.getShort();
                // A string value
                if (n != (short) 0x8000 && n == key.length()) {
                    int i = 0;
                    for (; i < a; ) {
                        if (buffer.get() != bytes[i++]) break;
                    }
                    if (i < a) {
                        buffer.position(buffer.position() + a - i);
                    } else break A;
                } else buffer.position(buffer.position() + a);
                index++;
            }
            buffer.compact();
        }
        reset();
        buffer.rewind();
        // Returns -1 if not found
        return index < count ? index : -1;
    }

    /**
     * Returns the cache size
     *
     * @return total keyword
     */
    public int size() {
        return count;
    }

    /**
     * Write buffered data to channel
     *
     * @throws IOException if io error occur
     */
    private void flush() throws IOException {
        buffer.flip();
        if (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }

    /**
     * Check the remaining data is complete
     *
     * @param buffer the ByteBuffer
     * @return true or false
     */
    protected static boolean hasFullValue(ByteBuffer buffer) {
        if (buffer.remaining() < 6) return false;
        int position = buffer.position();
        int n = buffer.get(position)   & 0xFF;
        n |= (buffer.get(position + 1) & 0xFF) <<  8;
        n |= (buffer.get(position + 2) & 0xFF) << 16;
        n |= (buffer.get(position + 3) & 0xFF) << 24;
        return n + 6 <= buffer.remaining();
    }

    /**
     * Commit current index file writer
     *
     * @throws IOException if io error occur
     */
    protected void commit() throws IOException {
        flush();
        buffer.putInt(count);
        buffer.flip();
        channel.position(0);
        channel.write(buffer);
    }

    /**
     * Close channel and delete temp files
     *
     * @throws IOException if io error occur
     */
    @Override
    public void close() throws IOException {
        // Commit writer
        commit();
        // Release
        buffer = null;
        if (channel != null) {
            channel.close();
        }
        FileUtil.rm(temp);
    }

    /**
     * Returns this buffer's position.
     *
     * @return  The position of this buffer
     */
    protected long position() throws IOException {
        return channel.position() + buffer.position();
    }

    /**
     * Returns a ByteBuffer data from channel position
     *
     * @param buffer the byte buffer
     * @return the read data length
     */
    protected int read(ByteBuffer buffer) throws IOException {
        return channel.read(buffer);
    }

    /**
     * Sets this buffer's mark at its position.
     *
     * @return  This SharedStringTable
     */
    protected SharedStringTable mark() throws IOException {
        flush();
        mark = channel.position();
        return this;
    }

    /**
     * Resets this buffer's position to the previously-marked position.
     *
     * Invoking this method neither changes nor discards the mark's
     * value.
     *
     * @return  This SharedStringTable
     *
     * @throws  InvalidMarkException
     *          If the mark has not been set
     */
    protected SharedStringTable reset() throws IOException {
        if (mark == -1)
            throw new InvalidMarkException();
        channel.position(mark);
        mark = -1;
        return this;
    }

    /**
     * Jump to the specified position, the actual moving position
     * will be increased by 4, the header contains an integer value.
     *
     * @param position the position to jump
     * @throws IOException if io error occur
     */
    protected SharedStringTable skip(long position) throws IOException {
        channel.position(position + 4);
        return this;
    }

    /**
     * Returns an iterator over elements of type String
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<String> iterator() {
        try {
            flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SSTIterator(temp);
    }

    private static class SSTIterator implements Iterator<String> {
        private SeekableByteChannel channel;
        private ByteBuffer buffer;
        private byte[] bytes;
        @SuppressWarnings("unused")
        private int count; // ignore
        private char[] chars;
        private SSTIterator(Path temp) {
            try {
                channel = Files.newByteChannel(temp, StandardOpenOption.READ);
                buffer = ByteBuffer.allocate(1 << 11);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                // Read ahead
                channel.read(buffer);
                buffer.flip();
                if (buffer.remaining() > 4) {
                    count = buffer.getInt();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            bytes = new byte[128];
            chars = new char[1];
        }
        @Override
        public boolean hasNext() {
            try {
                if (buffer.remaining() < 6 || !hasFullValue(buffer)) {
                    buffer.compact();
                    channel.read(buffer);
                    buffer.flip();
                }
                return buffer.hasRemaining();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String next() {
            int a = buffer.getInt();
            if (a > bytes.length) {
                bytes = new byte[a];
            }
            if (buffer.getShort() == (short) 0x8000) {
                chars[0] = buffer.getChar();
                return new String(chars);
            } else {
                buffer.get(bytes, 0, a);
                return new String(bytes, 0, a, UTF_8);
            }
        }
    }

}
