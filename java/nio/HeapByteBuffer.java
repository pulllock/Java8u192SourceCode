/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;


/**
 * A read/write HeapByteBuffer.
 *
 * 堆内存字节缓冲区
 */

class HeapByteBuffer
    extends ByteBuffer
{

    // For speed these fields are actually declared in X-Buffer;
    // these declarations are here as documentation
    /*

    protected final byte[] hb;
    protected final int offset;

    */

    HeapByteBuffer(int cap, int lim) {            // package-private

        super(-1, 0, lim, cap, new byte[cap], 0);
        /*
        hb = new byte[cap];
        offset = 0;
        */
    }

    HeapByteBuffer(byte[] buf, int off, int len) { // package-private

        super(-1, off, off + len, buf.length, buf, 0);
        /*
        hb = buf;
        offset = 0;
        */
    }

    protected HeapByteBuffer(byte[] buf,
                                   int mark, int pos, int lim, int cap,
                                   int off)
    {

        super(mark, pos, lim, cap, buf, off);
        /*
        hb = buf;
        offset = off;
        */
    }

    /**
     *
     * @return
     *
     * 切片，创建一个新的堆内存字节缓冲区，新旧缓冲区的数据共享，修改数据会相互影响，但是新旧缓冲区的position、limit、mark相互独立
     */
    public ByteBuffer slice() {
        return new HeapByteBuffer(hb,
                                        -1,
                                        0,
                                        this.remaining(),
                                        this.remaining(),
                                        this.position() + offset);
    }

    /**
     *
     * @return
     *
     * 复制，创建一个新的堆内存字节缓冲区，新旧缓冲区的数据共享，修改数据会相互影响，但是新旧缓冲区的position、limit、mark相互独立
     */
    public ByteBuffer duplicate() {
        return new HeapByteBuffer(hb,
                                        this.markValue(),
                                        this.position(),
                                        this.limit(),
                                        this.capacity(),
                                        offset);
    }

    /**
     *
     * @return
     *
     * 创建一个只读的新堆内存缓冲区，新旧缓冲区的数据共享，修改旧的缓冲区数据会影响到新的缓冲区，新的缓冲区不能修改，新旧缓冲区的position、limit、mark相互独立
     */
    public ByteBuffer asReadOnlyBuffer() {

        return new HeapByteBufferR(hb,
                                     this.markValue(),
                                     this.position(),
                                     this.limit(),
                                     this.capacity(),
                                     offset);
    }

    protected int ix(int i) {
        return i + offset;
    }

    public byte get() {
        return hb[ix(nextGetIndex())];
    }

    public byte get(int i) {
        return hb[ix(checkIndex(i))];
    }

    /**
     *
     * @param  dst
     *         The array into which bytes are to be written
     *
     * @param  offset
     *         The offset within the array of the first byte to be
     *         written; must be non-negative and no larger than
     *         <tt>dst.length</tt>
     *
     * @param  length
     *         The maximum number of bytes to be written to the given
     *         array; must be non-negative and no larger than
     *         <tt>dst.length - offset</tt>
     *
     * @return
     *
     * 从字节缓冲区中读取数据到指定的字节数组中
     */
    public ByteBuffer get(byte[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if (length > remaining())
            throw new BufferUnderflowException();
        // 从堆内存缓冲区的数组中拷贝数据到目标字节数组
        System.arraycopy(hb, ix(position()), dst, offset, length);
        position(position() + length);
        return this;
    }

    /**
     *
     * @return
     *
     * 返回缓冲区是直接内存缓冲区还是堆内存缓冲区
     */
    public boolean isDirect() {
        return false;
    }

    /**
     *
     * @return
     *
     * 缓冲是否只读
     */
    public boolean isReadOnly() {
        return false;
    }

    /**
     *
     * @param x
     * @return
     *
     * 在position处写一个字节的数据
     */
    public ByteBuffer put(byte x) {
        hb[ix(nextPutIndex())] = x;
        return this;
    }

    /**
     *
     * @param i
     * @param x
     * @return
     *
     * 在指定的位置处写一个字节数据
     */
    public ByteBuffer put(int i, byte x) {
        hb[ix(checkIndex(i))] = x;
        return this;
    }

    /**
     *
     * @param  src
     *         The array from which bytes are to be read
     *
     * @param  offset
     *         The offset within the array of the first byte to be read;
     *         must be non-negative and no larger than <tt>array.length</tt>
     *
     * @param  length
     *         The number of bytes to be read from the given array;
     *         must be non-negative and no larger than
     *         <tt>array.length - offset</tt>
     *
     * @return
     *
     * 将指定的字节缓冲区中的数据写到当前的堆内存字节缓冲区中
     */
    public ByteBuffer put(byte[] src, int offset, int length) {

        checkBounds(offset, length, src.length);
        if (length > remaining())
            throw new BufferOverflowException();
        // 从指定的字节数组中拷贝数据到堆内存字节缓冲区的字节数组中
        System.arraycopy(src, offset, hb, ix(position()), length);
        position(position() + length);
        return this;
    }

    /**
     *
     * @param  src
     *         The source buffer from which bytes are to be read;
     *         must not be this buffer
     *
     * @return
     *
     * 将指定的字节缓冲区中的数据写到当前的字节缓冲区中
     */
    public ByteBuffer put(ByteBuffer src) {
        // 源字节缓冲区是堆内存字节缓冲区
        if (src instanceof HeapByteBuffer) {
            if (src == this)
                throw new IllegalArgumentException();
            HeapByteBuffer sb = (HeapByteBuffer)src;
            int n = sb.remaining();
            if (n > remaining())
                throw new BufferOverflowException();
            // 将源堆内存字节缓冲区的字节数组的数据拷贝到目的堆内存字节缓冲区的字节数组中
            System.arraycopy(sb.hb, sb.ix(sb.position()),
                             hb, ix(position()), n);
            // 修改源堆内存字节缓冲区的position
            sb.position(sb.position() + n);
            // 修改当前堆内存字节缓冲区的position
            position(position() + n);
        }
        // 源字节缓冲区是直接内存缓冲区
        else if (src.isDirect()) {
            int n = src.remaining();
            if (n > remaining())
                throw new BufferOverflowException();
            // 从源直接内存缓冲区读取数据到当前字节缓冲区的字节数组中
            src.get(hb, ix(position()), n);
            // 修改当前堆内存字节缓冲区的position
            position(position() + n);
        } else {
            super.put(src);
        }
        return this;
    }

    /**
     *
     * @return
     *
     * 压缩，比如当前Buffer中还有部分未读的数据，但是此时想要继续写数据进当前Buffer，此时可以使用compact方法，将未读的数据移到Buffer的最前面，这样就可以继续写当前Buffer。
     */
    public ByteBuffer compact() {
        System.arraycopy(hb, ix(position()), hb, ix(0), remaining());
        position(remaining());
        limit(capacity());
        discardMark();
        return this;
    }

    byte _get(int i) {                          // package-private
        return hb[i];
    }

    void _put(int i, byte b) {                  // package-private
        hb[i] = b;
    }

    // char
    public char getChar() {
        return Bits.getChar(this, ix(nextGetIndex(2)), bigEndian);
    }

    public char getChar(int i) {
        return Bits.getChar(this, ix(checkIndex(i, 2)), bigEndian);
    }

    public ByteBuffer putChar(char x) {
        Bits.putChar(this, ix(nextPutIndex(2)), x, bigEndian);
        return this;
    }

    public ByteBuffer putChar(int i, char x) {
        Bits.putChar(this, ix(checkIndex(i, 2)), x, bigEndian);
        return this;
    }

    public CharBuffer asCharBuffer() {
        int size = this.remaining() >> 1;
        int off = offset + position();
        return (bigEndian
                ? (CharBuffer)(new ByteBufferAsCharBufferB(this,
                                                               -1,
                                                               0,
                                                               size,
                                                               size,
                                                               off))
                : (CharBuffer)(new ByteBufferAsCharBufferL(this,
                                                               -1,
                                                               0,
                                                               size,
                                                               size,
                                                               off)));
    }

    // short
    public short getShort() {
        return Bits.getShort(this, ix(nextGetIndex(2)), bigEndian);
    }

    public short getShort(int i) {
        return Bits.getShort(this, ix(checkIndex(i, 2)), bigEndian);
    }

    public ByteBuffer putShort(short x) {
        Bits.putShort(this, ix(nextPutIndex(2)), x, bigEndian);
        return this;
    }

    public ByteBuffer putShort(int i, short x) {
        Bits.putShort(this, ix(checkIndex(i, 2)), x, bigEndian);
        return this;
    }

    public ShortBuffer asShortBuffer() {
        int size = this.remaining() >> 1;
        int off = offset + position();
        return (bigEndian
                ? (ShortBuffer)(new ByteBufferAsShortBufferB(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                : (ShortBuffer)(new ByteBufferAsShortBufferL(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
    }

    // int
    public int getInt() {
        return Bits.getInt(this, ix(nextGetIndex(4)), bigEndian);
    }

    public int getInt(int i) {
        return Bits.getInt(this, ix(checkIndex(i, 4)), bigEndian);
    }

    public ByteBuffer putInt(int x) {
        Bits.putInt(this, ix(nextPutIndex(4)), x, bigEndian);
        return this;
    }

    public ByteBuffer putInt(int i, int x) {
        Bits.putInt(this, ix(checkIndex(i, 4)), x, bigEndian);
        return this;
    }

    public IntBuffer asIntBuffer() {
        int size = this.remaining() >> 2;
        int off = offset + position();
        return (bigEndian
                ? (IntBuffer)(new ByteBufferAsIntBufferB(this,
                                                             -1,
                                                             0,
                                                             size,
                                                             size,
                                                             off))
                : (IntBuffer)(new ByteBufferAsIntBufferL(this,
                                                             -1,
                                                             0,
                                                             size,
                                                             size,
                                                             off)));
    }

    // long
    public long getLong() {
        return Bits.getLong(this, ix(nextGetIndex(8)), bigEndian);
    }

    public long getLong(int i) {
        return Bits.getLong(this, ix(checkIndex(i, 8)), bigEndian);
    }

    public ByteBuffer putLong(long x) {
        Bits.putLong(this, ix(nextPutIndex(8)), x, bigEndian);
        return this;
    }

    public ByteBuffer putLong(int i, long x) {
        Bits.putLong(this, ix(checkIndex(i, 8)), x, bigEndian);
        return this;
    }

    public LongBuffer asLongBuffer() {
        int size = this.remaining() >> 3;
        int off = offset + position();
        return (bigEndian
                ? (LongBuffer)(new ByteBufferAsLongBufferB(this,
                                                               -1,
                                                               0,
                                                               size,
                                                               size,
                                                               off))
                : (LongBuffer)(new ByteBufferAsLongBufferL(this,
                                                               -1,
                                                               0,
                                                               size,
                                                               size,
                                                               off)));
    }

    // float
    public float getFloat() {
        return Bits.getFloat(this, ix(nextGetIndex(4)), bigEndian);
    }

    public float getFloat(int i) {
        return Bits.getFloat(this, ix(checkIndex(i, 4)), bigEndian);
    }

    public ByteBuffer putFloat(float x) {
        Bits.putFloat(this, ix(nextPutIndex(4)), x, bigEndian);
        return this;
    }

    public ByteBuffer putFloat(int i, float x) {
        Bits.putFloat(this, ix(checkIndex(i, 4)), x, bigEndian);
        return this;
    }

    public FloatBuffer asFloatBuffer() {
        int size = this.remaining() >> 2;
        int off = offset + position();
        return (bigEndian
                ? (FloatBuffer)(new ByteBufferAsFloatBufferB(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                : (FloatBuffer)(new ByteBufferAsFloatBufferL(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
    }

    // double
    public double getDouble() {
        return Bits.getDouble(this, ix(nextGetIndex(8)), bigEndian);
    }

    public double getDouble(int i) {
        return Bits.getDouble(this, ix(checkIndex(i, 8)), bigEndian);
    }

    public ByteBuffer putDouble(double x) {
        Bits.putDouble(this, ix(nextPutIndex(8)), x, bigEndian);
        return this;
    }

    public ByteBuffer putDouble(int i, double x) {
        Bits.putDouble(this, ix(checkIndex(i, 8)), x, bigEndian);
        return this;
    }

    public DoubleBuffer asDoubleBuffer() {
        int size = this.remaining() >> 3;
        int off = offset + position();
        return (bigEndian
                ? (DoubleBuffer)(new ByteBufferAsDoubleBufferB(this,
                                                                   -1,
                                                                   0,
                                                                   size,
                                                                   size,
                                                                   off))
                : (DoubleBuffer)(new ByteBufferAsDoubleBufferL(this,
                                                                   -1,
                                                                   0,
                                                                   size,
                                                                   size,
                                                                   off)));
    }
}
