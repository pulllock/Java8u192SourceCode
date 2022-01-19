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

package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.*;


/**
 * Base implementation class for selectable channels.
 *
 * <p> This class defines methods that handle the mechanics of channel
 * registration, deregistration, and closing.  It maintains the current
 * blocking mode of this channel as well as its current set of selection keys.
 * It performs all of the synchronization required to implement the {@link
 * java.nio.channels.SelectableChannel} specification.  Implementations of the
 * abstract protected methods defined in this class need not synchronize
 * against other threads that might be engaged in the same operations.  </p>
 *
 *
 * @author Mark Reinhold
 * @author Mike McCloskey
 * @author JSR-51 Expert Group
 * @since 1.4
 *
 * SelectableChannel的抽象实现
 */

public abstract class AbstractSelectableChannel
    extends SelectableChannel
{

    // The provider that created this channel
    /**
     * 创建此通道的SelectorProvider
     */
    private final SelectorProvider provider;

    // Keys that have been created by registering this channel with selectors.
    // They are saved because if this channel is closed the keys must be
    // deregistered.  Protected by keyLock.
    //
    /**
     * 当前通道的SelectionKey集合
     */
    private SelectionKey[] keys = null;

    /**
     * SelectionKey集合中已有SelectionKey的数量
     */
    private int keyCount = 0;

    // Lock for key set and count
    /**
     * 已注册SelectionKey集合更新或者统计时使用的锁
     */
    private final Object keyLock = new Object();

    // Lock for registration and configureBlocking operations
    /**
     * 设置通道的阻塞模式时使用的锁
     */
    private final Object regLock = new Object();

    // Blocking mode, protected by regLock
    /**
     * 是否是阻塞模式
     */
    boolean blocking = true;

    /**
     * Initializes a new instance of this class.
     *
     * @param  provider
     *         The provider that created this channel
     */
    protected AbstractSelectableChannel(SelectorProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel.
     *
     * @return  The provider that created this channel
     *
     * 返回创建此通道的SelectorProvider
     */
    public final SelectorProvider provider() {
        return provider;
    }


    // -- Utility methods for the key set --

    private void addKey(SelectionKey k) {
        assert Thread.holdsLock(keyLock);
        int i = 0;
        if ((keys != null) && (keyCount < keys.length)) {
            // Find empty element of key array
            for (i = 0; i < keys.length; i++)
                if (keys[i] == null)
                    break;
        } else if (keys == null) {
            keys =  new SelectionKey[3];
        } else {
            // Grow key array
            int n = keys.length * 2;
            SelectionKey[] ks =  new SelectionKey[n];
            for (i = 0; i < keys.length; i++)
                ks[i] = keys[i];
            keys = ks;
            i = keyCount;
        }
        keys[i] = k;
        keyCount++;
    }

    /**
     *
     * @param sel
     * @return
     *
     * 判断当前通道是否注册到了某个Selector上
     */
    private SelectionKey findKey(Selector sel) {
        synchronized (keyLock) {
            if (keys == null)
                return null;
            for (int i = 0; i < keys.length; i++)
                if ((keys[i] != null) && (keys[i].selector() == sel))
                    return keys[i];
            return null;
        }
    }

    void removeKey(SelectionKey k) {                    // package-private
        synchronized (keyLock) {
            for (int i = 0; i < keys.length; i++)
                if (keys[i] == k) {
                    keys[i] = null;
                    keyCount--;
                }
            ((AbstractSelectionKey)k).invalidate();
        }
    }

    private boolean haveValidKeys() {
        synchronized (keyLock) {
            if (keyCount == 0)
                return false;
            for (int i = 0; i < keys.length; i++) {
                if ((keys[i] != null) && keys[i].isValid())
                    return true;
            }
            return false;
        }
    }


    // -- Registration --

    /**
     *
     * @return
     *
     * 判断当前通道是否注册到了某个Selector上
     */
    public final boolean isRegistered() {
        synchronized (keyLock) {
            return keyCount != 0;
        }
    }

    /**
     *
     * @param   sel
     *          The selector
     *
     * @return
     *
     * 在指定的Selector里找当前通道对应的SelectionKey
     */
    public final SelectionKey keyFor(Selector sel) {
        return findKey(sel);
    }

    /**
     * Registers this channel with the given selector, returning a selection key.
     *
     * <p>  This method first verifies that this channel is open and that the
     * given initial interest set is valid.
     *
     * <p> If this channel is already registered with the given selector then
     * the selection key representing that registration is returned after
     * setting its interest set to the given value.
     *
     * <p> Otherwise this channel has not yet been registered with the given
     * selector, so the {@link AbstractSelector#register register} method of
     * the selector is invoked while holding the appropriate locks.  The
     * resulting key is added to this channel's key set before being returned.
     * </p>
     *
     * @throws  ClosedSelectorException {@inheritDoc}
     *
     * @throws  IllegalBlockingModeException {@inheritDoc}
     *
     * @throws  IllegalSelectorException {@inheritDoc}
     *
     * @throws  CancelledKeyException {@inheritDoc}
     *
     * @throws  IllegalArgumentException {@inheritDoc}
     *
     * 将Channel注册到Selector上，返回SelectionKey
     */
    public final SelectionKey register(Selector sel, int ops,
                                       Object att)
        throws ClosedChannelException
    {
        synchronized (regLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if ((ops & ~validOps()) != 0)
                throw new IllegalArgumentException();
            if (blocking)
                throw new IllegalBlockingModeException();
            // 从当前通道中的SelectionKey集合中查找，看通道是否在Selector上注册过
            SelectionKey k = findKey(sel);
            // 通道已经在Selector上注册过
            if (k != null) {
                // 设置此SelectionKey监听的事件
                k.interestOps(ops);
                // 设置SelectionKey的附属对象
                k.attach(att);
            }
            // 通道还没有在此Selector上注册过
            if (k == null) {
                // New registration
                synchronized (keyLock) {
                    if (!isOpen())
                        throw new ClosedChannelException();
                    // 将指定的通道注册到当前Selector上，返回生成的SelectionKey
                    k = ((AbstractSelector)sel).register(this, ops, att);
                    // 将新的SelectionKey加入到当前通道的SelectionKey集合中
                    addKey(k);
                }
            }
            return k;
        }
    }


    // -- Closing --

    /**
     * Closes this channel.
     *
     * <p> This method, which is specified in the {@link
     * AbstractInterruptibleChannel} class and is invoked by the {@link
     * java.nio.channels.Channel#close close} method, in turn invokes the
     * {@link #implCloseSelectableChannel implCloseSelectableChannel} method in
     * order to perform the actual work of closing this channel.  It then
     * cancels all of this channel's keys.  </p>
     */
    protected final void implCloseChannel() throws IOException {
        implCloseSelectableChannel();
        synchronized (keyLock) {
            int count = (keys == null) ? 0 : keys.length;
            for (int i = 0; i < count; i++) {
                SelectionKey k = keys[i];
                if (k != null)
                    k.cancel();
            }
        }
    }

    /**
     * Closes this selectable channel.
     *
     * <p> This method is invoked by the {@link java.nio.channels.Channel#close
     * close} method in order to perform the actual work of closing the
     * channel.  This method is only invoked if the channel has not yet been
     * closed, and it is never invoked more than once.
     *
     * <p> An implementation of this method must arrange for any other thread
     * that is blocked in an I/O operation upon this channel to return
     * immediately, either by throwing an exception or by returning normally.
     * </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    protected abstract void implCloseSelectableChannel() throws IOException;


    // -- Blocking --

    /**
     *
     * @return
     *
     * 判断是否是阻塞模式
     */
    public final boolean isBlocking() {
        synchronized (regLock) {
            return blocking;
        }
    }

    public final Object blockingLock() {
        return regLock;
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> If the given blocking mode is different from the current blocking
     * mode then this method invokes the {@link #implConfigureBlocking
     * implConfigureBlocking} method, while holding the appropriate locks, in
     * order to change the mode.  </p>
     *
     * 设置Channel的阻塞模式，true是阻塞，false是非阻塞
     */
    public final SelectableChannel configureBlocking(boolean block)
        throws IOException
    {
        synchronized (regLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (blocking == block)
                return this;
            if (block && haveValidKeys())
                throw new IllegalBlockingModeException();
            implConfigureBlocking(block);
            blocking = block;
        }
        return this;
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> This method is invoked by the {@link #configureBlocking
     * configureBlocking} method in order to perform the actual work of
     * changing the blocking mode.  This method is only invoked if the new mode
     * is different from the current mode.  </p>
     *
     * @param  block  If <tt>true</tt> then this channel will be placed in
     *                blocking mode; if <tt>false</tt> then it will be placed
     *                non-blocking mode
     *
     * @throws IOException
     *         If an I/O error occurs
     */
    protected abstract void implConfigureBlocking(boolean block)
        throws IOException;

}
