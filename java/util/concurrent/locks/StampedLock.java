/*
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

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.LockSupport;

/**
 * A capability-based lock with three modes for controlling read/write
 * access.  The state of a StampedLock consists of a version and mode.
 * Lock acquisition methods return a stamp that represents and
 * controls access with respect to a lock state; "try" versions of
 * these methods may instead return the special value zero to
 * represent failure to acquire access. Lock release and conversion
 * methods require stamps as arguments, and fail if they do not match
 * the state of the lock. The three modes are:
 *
 * <ul>
 *
 *  <li><b>Writing.</b> Method {@link #writeLock} possibly blocks
 *   waiting for exclusive access, returning a stamp that can be used
 *   in method {@link #unlockWrite} to release the lock. Untimed and
 *   timed versions of {@code tryWriteLock} are also provided. When
 *   the lock is held in write mode, no read locks may be obtained,
 *   and all optimistic read validations will fail.  </li>
 *
 *  <li><b>Reading.</b> Method {@link #readLock} possibly blocks
 *   waiting for non-exclusive access, returning a stamp that can be
 *   used in method {@link #unlockRead} to release the lock. Untimed
 *   and timed versions of {@code tryReadLock} are also provided. </li>
 *
 *  <li><b>Optimistic Reading.</b> Method {@link #tryOptimisticRead}
 *   returns a non-zero stamp only if the lock is not currently held
 *   in write mode. Method {@link #validate} returns true if the lock
 *   has not been acquired in write mode since obtaining a given
 *   stamp.  This mode can be thought of as an extremely weak version
 *   of a read-lock, that can be broken by a writer at any time.  The
 *   use of optimistic mode for short read-only code segments often
 *   reduces contention and improves throughput.  However, its use is
 *   inherently fragile.  Optimistic read sections should only read
 *   fields and hold them in local variables for later use after
 *   validation. Fields read while in optimistic mode may be wildly
 *   inconsistent, so usage applies only when you are familiar enough
 *   with data representations to check consistency and/or repeatedly
 *   invoke method {@code validate()}.  For example, such steps are
 *   typically required when first reading an object or array
 *   reference, and then accessing one of its fields, elements or
 *   methods. </li>
 *
 * </ul>
 *
 * <p>This class also supports methods that conditionally provide
 * conversions across the three modes. For example, method {@link
 * #tryConvertToWriteLock} attempts to "upgrade" a mode, returning
 * a valid write stamp if (1) already in writing mode (2) in reading
 * mode and there are no other readers or (3) in optimistic mode and
 * the lock is available. The forms of these methods are designed to
 * help reduce some of the code bloat that otherwise occurs in
 * retry-based designs.
 *
 * <p>StampedLocks are designed for use as internal utilities in the
 * development of thread-safe components. Their use relies on
 * knowledge of the internal properties of the data, objects, and
 * methods they are protecting.  They are not reentrant, so locked
 * bodies should not call other unknown methods that may try to
 * re-acquire locks (although you may pass a stamp to other methods
 * that can use or convert it).  The use of read lock modes relies on
 * the associated code sections being side-effect-free.  Unvalidated
 * optimistic read sections cannot call methods that are not known to
 * tolerate potential inconsistencies.  Stamps use finite
 * representations, and are not cryptographically secure (i.e., a
 * valid stamp may be guessable). Stamp values may recycle after (no
 * sooner than) one year of continuous operation. A stamp held without
 * use or validation for longer than this period may fail to validate
 * correctly.  StampedLocks are serializable, but always deserialize
 * into initial unlocked state, so they are not useful for remote
 * locking.
 *
 * <p>The scheduling policy of StampedLock does not consistently
 * prefer readers over writers or vice versa.  All "try" methods are
 * best-effort and do not necessarily conform to any scheduling or
 * fairness policy. A zero return from any "try" method for acquiring
 * or converting locks does not carry any information about the state
 * of the lock; a subsequent invocation may succeed.
 *
 * <p>Because it supports coordinated usage across multiple lock
 * modes, this class does not directly implement the {@link Lock} or
 * {@link ReadWriteLock} interfaces. However, a StampedLock may be
 * viewed {@link #asReadLock()}, {@link #asWriteLock()}, or {@link
 * #asReadWriteLock()} in applications requiring only the associated
 * set of functionality.
 *
 * <p><b>Sample Usage.</b> The following illustrates some usage idioms
 * in a class that maintains simple two-dimensional points. The sample
 * code illustrates some try/catch conventions even though they are
 * not strictly needed here because no exceptions can occur in their
 * bodies.<br>
 *
 *  <pre>{@code
 * class Point {
 *   private double x, y;
 *   private final StampedLock sl = new StampedLock();
 *
 *   void move(double deltaX, double deltaY) { // an exclusively locked method
 *     // 写锁，返回sequence+1
 *     long stamp = sl.writeLock();
 *     try {
 *       x += deltaX;
 *       y += deltaY;
 *     } finally {
 *       // 释放写锁
 *       sl.unlockWrite(stamp);
 *     }
 *   }
 *
 *   double distanceFromOrigin() { // A read-only method
 *     // 乐观读
 *     long stamp = sl.tryOptimisticRead();
 *     double currentX = x, currentY = y;
 *     // 校验乐观读返回的stamp，看是否被写锁更改
 *     if (!sl.validate(stamp)) {
 *        // 被写锁更改，需要尝试获取悲观读锁
 *        stamp = sl.readLock();
 *        try {
 *          currentX = x;
 *          currentY = y;
 *        } finally {
 *           // 释放悲观读
 *           sl.unlockRead(stamp);
 *        }
 *     }
 *     return Math.sqrt(currentX * currentX + currentY * currentY);
 *   }
 *
 *   void moveIfAtOrigin(double newX, double newY) { // upgrade
 *     // Could instead start with optimistic, not read mode
 *     long stamp = sl.readLock();
 *     try {
 *       while (x == 0.0 && y == 0.0) {
 *         long ws = sl.tryConvertToWriteLock(stamp);
 *         if (ws != 0L) {
 *           stamp = ws;
 *           x = newX;
 *           y = newY;
 *           break;
 *         }
 *         else {
 *           sl.unlockRead(stamp);
 *           stamp = sl.writeLock();
 *         }
 *       }
 *     } finally {
 *       sl.unlock(stamp);
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.8
 * @author Doug Lea
 *
 * ReentrantReadWriteLock适用于读多写少的场景，但是可能会产生写线程饥饿问题，
 * 读写锁中读读共享，读写和写写互斥，如果有读锁则写锁需要等待读锁释放，在非公平模式下，如果有
 * 大量的读线程进来，可能会导致写线程一直获取不到锁，导致写线程饥饿问题。
 *
 * StampedLock解决了ReentrantReadWriteLock的写线程饥饿问题，了解StampedLock之前
 * 先了解下SeqLock（顺序锁，sequence lock）
 *
 * 参考：
 * - https://blog.csdn.net/zhoutaopower/article/details/86611798
 * - https://blog.csdn.net/longwang155069/article/details/52231519
 * - http://www.yebangyu.org/blog/2016/06/26/sequence-lock/
 * - https://cloud.tencent.com/developer/article/1021135
 *
 * SeqLock可以保证写线程的优先，让写线程更快的获取到锁，而不是在有读线程加锁的情况下，写
 * 线程需要等待。
 *
 * 顺序锁的设计思想：
 * 读线程在读取共享数据的时候不需要加锁，只有写线程在需要修改共享数据的时候加锁，读线程和写线程
 * 之间引入一个整形变量sequence，写线程在进入临界区的时候会将sequence加1，写线程在退出临界区
 * 的时候sequence还会再加1。当sequence为奇数的时候，表示有写操作正在进行，读操作需要等待，直到
 * sequence变为偶数；当读操作进入临界区时，需要先记录下sequence的值，等退出临界区的时候，会
 * 和当前的sequence进行比较，如果不相等则表示读操作在临界区时，有写操作发生，这时候读操作需要
 * 重试重新读取。
 *
 * 顺序锁中读写是不互斥的，只有写写是互斥的。
 *
 * 顺序锁的写操作获取锁的过程：
 * 1. 写操作获取锁，确保只有一个写线程进入临界区
 * 2. sequence加1
 *
 * 释放锁的过程
 * 1. 释放锁
 * 2. sequence加1
 *
 * 读操作：
 * 1. 获取sequence，如果是偶数则可以进入临界区；如果是奇数则等待变为偶数
 * 2. 进入临界区
 * 3. 获取sequence和老的sequence比较，如果相等则完成，否则重试
 *
 * 顺序锁适用于读多写少的场景，写操作较少但是写操作性能要求较高，也就是写操作随时可以更新。
 *
 * StampedLock
 *
 * 参考：
 * - https://muhouer.github.io/posts/ab957bc5/
 *
 * StampedLock中有三种方式：
 * - 独占写，和读写锁的写类似，写写互斥，写和悲观读互斥
 * - 悲观读，和读写锁的读类似，读读共享，读写互斥
 * - 乐观读，就是顺序锁中的读，使用sequence来判断数据是否被修改过
 *
 * 使用StampedLock的读操作的建议：
 * 乐观读时，如果写操作修改了共享资源，则升级乐观读为悲观读锁，
 * 这样可用避免乐观读反复的循环等待写锁的释放，造成CPU资源的浪费。
 *
 * StampedLock不支持重入、不支持条件变量
 */
public class StampedLock implements java.io.Serializable {
    /*
     * Algorithmic notes:
     *
     * The design employs elements of Sequence locks
     * (as used in linux kernels; see Lameter's
     * http://www.lameter.com/gelato2005.pdf
     * and elsewhere; see
     * Boehm's http://www.hpl.hp.com/techreports/2012/HPL-2012-68.html)
     * and Ordered RW locks (see Shirako et al
     * http://dl.acm.org/citation.cfm?id=2312015)
     *
     * Conceptually, the primary state of the lock includes a sequence
     * number that is odd when write-locked and even otherwise.
     * However, this is offset by a reader count that is non-zero when
     * read-locked.  The read count is ignored when validating
     * "optimistic" seqlock-reader-style stamps.  Because we must use
     * a small finite number of bits (currently 7) for readers, a
     * supplementary reader overflow word is used when the number of
     * readers exceeds the count field. We do this by treating the max
     * reader count value (RBITS) as a spinlock protecting overflow
     * updates.
     *
     * Waiters use a modified form of CLH lock used in
     * AbstractQueuedSynchronizer (see its internal documentation for
     * a fuller account), where each node is tagged (field mode) as
     * either a reader or writer. Sets of waiting readers are grouped
     * (linked) under a common node (field cowait) so act as a single
     * node with respect to most CLH mechanics.  By virtue of the
     * queue structure, wait nodes need not actually carry sequence
     * numbers; we know each is greater than its predecessor.  This
     * simplifies the scheduling policy to a mainly-FIFO scheme that
     * incorporates elements of Phase-Fair locks (see Brandenburg &
     * Anderson, especially http://www.cs.unc.edu/~bbb/diss/).  In
     * particular, we use the phase-fair anti-barging rule: If an
     * incoming reader arrives while read lock is held but there is a
     * queued writer, this incoming reader is queued.  (This rule is
     * responsible for some of the complexity of method acquireRead,
     * but without it, the lock becomes highly unfair.) Method release
     * does not (and sometimes cannot) itself wake up cowaiters. This
     * is done by the primary thread, but helped by any other threads
     * with nothing better to do in methods acquireRead and
     * acquireWrite.
     *
     * These rules apply to threads actually queued. All tryLock forms
     * opportunistically try to acquire locks regardless of preference
     * rules, and so may "barge" their way in.  Randomized spinning is
     * used in the acquire methods to reduce (increasingly expensive)
     * context switching while also avoiding sustained memory
     * thrashing among many threads.  We limit spins to the head of
     * queue. A thread spin-waits up to SPINS times (where each
     * iteration decreases spin count with 50% probability) before
     * blocking. If, upon wakening it fails to obtain lock, and is
     * still (or becomes) the first waiting thread (which indicates
     * that some other thread barged and obtained lock), it escalates
     * spins (up to MAX_HEAD_SPINS) to reduce the likelihood of
     * continually losing to barging threads.
     *
     * Nearly all of these mechanics are carried out in methods
     * acquireWrite and acquireRead, that, as typical of such code,
     * sprawl out because actions and retries rely on consistent sets
     * of locally cached reads.
     *
     * As noted in Boehm's paper (above), sequence validation (mainly
     * method validate()) requires stricter ordering rules than apply
     * to normal volatile reads (of "state").  To force orderings of
     * reads before a validation and the validation itself in those
     * cases where this is not already forced, we use
     * Unsafe.loadFence.
     *
     * The memory layout keeps lock state and queue pointers together
     * (normally on the same cache line). This usually works well for
     * read-mostly loads. In most other cases, the natural tendency of
     * adaptive-spin CLH locks to reduce memory contention lessens
     * motivation to further spread out contended locations, but might
     * be subject to future improvements.
     */

    private static final long serialVersionUID = -6001602636862214147L;

    /**
     * Number of processors, for spin control
     *
     * CPU核数，控制自旋次数
     */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Maximum number of retries before enqueuing on acquisition
     *
     * 未获得锁的时候自旋的次数，超过次数后加入等待队列，在节点入同步队列之前，如果whead和wtail相等，
     * 就会先自旋SPINS指定的次数，自旋完后还没获取到锁，再加入同步队列。
     */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 0;

    /**
     * Maximum number of retries before blocking at head on acquisition
     * 等待队列头节点自旋获取锁的次数，超过该值后继续阻塞。在阻塞线程前，如果whead和wtail相等，
     * 就会先自旋HEAD_SPINS指定的次数，自旋完后还没获取到锁，再进行阻塞。
     */
    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 0;

    /**
     * Maximum number of retries before re-blocking
     *
     * 再次进入阻塞前的最大重试次数
     */
    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 0;

    /** The period for yielding when waiting for overflow spinlock */
    private static final int OVERFLOW_YIELD_RATE = 7; // must be power 2 - 1

    /**
     * The number of bits to use for reader count before overflowing
     *
     * 读锁最大的位
     */
    private static final int LG_READERS = 7;

    // Values for lock state and stamp operations
    /**
     * 获取悲观读成功时，state增加的值
     */
    private static final long RUNIT = 1L;

    /**
     * 写锁标志位128（10000000），写锁获取成功时，state增加的值
     */
    private static final long WBIT  = 1L << LG_READERS;

    /**
     * 读状态标志位，127
     */
    private static final long RBITS = WBIT - 1L;

    /**
     * 读锁最大数量，126
     */
    private static final long RFULL = RBITS - 1L;

    /**
     * 用于获取读写状态
     */
    private static final long ABITS = RBITS | WBIT;
    private static final long SBITS = ~RBITS; // note overlap with ABITS

    // Initial value for lock state; avoid failure value zero
    /**
     * 初始化state值
     */
    private static final long ORIGIN = WBIT << 1;

    // Special value from cancelled acquire methods so caller can throw IE
    /**
     * 线程被中断，获取读写锁时返回的值
     */
    private static final long INTERRUPTED = 1L;

    // Values for node status; order matters
    /**
     * 节点的等待状态
     */
    private static final int WAITING   = -1;

    /**
     * 节点的取消状态
     */
    private static final int CANCELLED =  1;

    // Modes for nodes (int not boolean to allow arithmetic)
    /**
     * 读模式
     */
    private static final int RMODE = 0;

    /**
     * 写模式
     */
    private static final int WMODE = 1;

    /**
     * Wait nodes
     *
     * 等待队列中的节点
     */
    static final class WNode {
        /**
         * 前驱节点
         */
        volatile WNode prev;

        /**
         * 后继节点
         */
        volatile WNode next;

        /**
         * 读模式使用该节点形成栈
         */
        volatile WNode cowait;    // list of linked readers

        /**
         * 节点对应的线程
         */
        volatile Thread thread;   // non-null while possibly parked

        /**
         * 等待队列中结点的状态
         * 初始状态：0
         * 等待中：-1
         * 取消：1
         */
        volatile int status;      // 0, WAITING, or CANCELLED

        /**
         * 节点模式：写模式还是读模式
         */
        final int mode;           // RMODE or WMODE
        WNode(int m, WNode p) { mode = m; prev = p; }
    }

    /**
     * Head of CLH queue
     *
     * CLH等待队列的头结点
     */
    private transient volatile WNode whead;

    /**
     * Tail (last) of CLH queue
     *
     * CLH等待队列的尾结点
     */
    private transient volatile WNode wtail;

    // views
    /**
     * 读锁视图，不可重入，不支持条件变量
     */
    transient ReadLockView readLockView;

    /**
     * 写锁视图，不可重入，不支持条件变量
     */
    transient WriteLockView writeLockView;

    /**
     * 读写锁视图
     */
    transient ReadWriteLockView readWriteLockView;

    /**
     * Lock sequence/state
     * 锁的状态，顺序锁的sequence
     *
     * 0-6位作为读锁计数，超过RFULL（126）时，使用readerOverflow作为读锁计数，
     * 当获取到读锁时，state加1，释放读锁时state减1
     *
     * 第7位是写锁标志，1表示已获取写锁，0表示已获取读锁，线程获取写锁或者释放写锁
     * 时，都会将state加上WBIT
     *
     * 8-64位是写锁版本，获取写锁和释放写锁，其值都会改变，初始状态下第8位为1，其余位为0
     */
    private transient volatile long state;

    /**
     * extra reader count when state read count saturated
     *
     * 读锁只是用了7位，当读锁数量超过了128，则使用readerOverflow来记录读锁数量
     */
    private transient int readerOverflow;

    /**
     * Creates a new lock, initially in unlocked state.
     *
     * 创建一个StampedLock并初始化sequence为256
     */
    public StampedLock() {
        state = ORIGIN;
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available.
     *
     * @return a stamp that can be used to unlock or convert mode
     *
     * 悲观写，和读写锁的写类似，写写互斥，写读互斥
     * 获取写锁，如果获取写锁失败，就进入阻塞队列
     */
    public long writeLock() {
        /*
            s是当前的state值，也就是sequence；
            next是获取写锁之后的state值，也就是sequence+1
         */
        long s, next;  // bypass acquireWrite in fully unlocked case only
        /*
            ((s = state) & ABITS)获取低8位，用来判断是否有读锁或写锁被使用，
            如果有读锁，则0-6位不为0；如果有写锁，则第7位不为0，
            ((s = state) & ABITS) == 0L 表示读锁和写锁都没被使用。

            U.compareAndSwapLong(this, STATE, s, next = s + WBIT))使用cas更新state，
            cas更新成功，表示获取到了写锁，返回next（sequence+1），此时state是奇数；
            cas更新失败，表示锁有竞争，获取写锁线程需要入同步队列。
         */
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : acquireWrite(false, 0L));
    }

    /**
     * Exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryWriteLock() {
        long s, next;
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : 0L);
    }

    /**
     * Exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryWriteLock(long time, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long next, deadline;
            if ((next = tryWriteLock()) != 0L)
                return next;
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireWrite(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available.
     *
     * @return a stamp that can be used to unlock or convert mode
     *
     * 悲观读，和读写锁的读操作类似，读读共享，读写互斥
     */
    public long readLock() {
        /*
            s是当前state值，就是sequence；
            next是获取读锁之后的state的值，就是sequence+1
         */
        long s = state, next;  // bypass acquireRead on common uncontended case
        /*
            whead == wtail表示同步队列为空；
            (s & ABITS) < RFULL表示没有写锁且读锁数量小于126。

            同步队列为空并且写锁也没有被占用的情况下，直接cas获取读锁，成功了就返回next值；
            cas失败了，获取读锁的线程需要进入同步队列排队。
         */
        return ((whead == wtail && (s & ABITS) < RFULL &&
                 U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) ?
                next : acquireRead(false, 0L));
    }

    /**
     * Non-exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryReadLock() {
        for (;;) {
            long s, m, next;
            if ((m = (s = state) & ABITS) == WBIT)
                return 0L;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                    return next;
            }
            else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
    }

    /**
     * Non-exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryReadLock(long time, TimeUnit unit)
        throws InterruptedException {
        long s, m, next, deadline;
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            if ((m = (s = state) & ABITS) != WBIT) {
                if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireRead(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long readLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireRead(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * Returns a stamp that can later be validated, or zero
     * if exclusively locked.
     *
     * @return a stamp, or zero if exclusively locked
     * 乐观读
     * 返回sequence
     * 使用该方法返回后，需要继续调用validate方法进行验证，看当前是否有
     * 其他线程持有写锁
     */
    public long tryOptimisticRead() {
        long s;
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * Returns true if the lock has not been exclusively acquired
     * since issuance of the given stamp. Always returns false if the
     * stamp is zero. Always returns true if the stamp represents a
     * currently held lock. Invoking this method with a value not
     * obtained from {@link #tryOptimisticRead} or a locking method
     * for this lock has no defined effect or result.
     *
     * @param stamp a stamp
     * @return {@code true} if the lock has not been exclusively acquired
     * since issuance of the given stamp; else false
     * 该方法对乐观读返回的数据进行操作，如果当前时间有其他线程持有了写锁就返回false
     */
    public boolean validate(long stamp) {
        /*
            loadFence内存屏障，禁止load操作重排序，使得此点之前的所有读写操作
            都执行后才可开始执行此点之后的操作
         */
        U.loadFence();
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     * If the lock state matches the given stamp, releases the
     * exclusive lock.
     *
     * @param stamp a stamp returned by a write-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     *
     * 释放写锁，参数是加锁时返回的值，sequence+1，为奇数
     *
     */
    public void unlockWrite(long stamp) {
        WNode h;
        // state和stamp不一样或者写锁未被占用，抛异常
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        // sequence继续加1，为偶数
        state = (stamp += WBIT) == 0L ? ORIGIN : stamp;
        if ((h = whead) != null && h.status != 0)
            // 释放写锁，唤醒后继结点
            release(h);
    }

    /**
     * If the lock state matches the given stamp, releases the
     * non-exclusive lock.
     *
     * @param stamp a stamp returned by a read-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     *
     * 释放读锁，参数是加锁时返回的值，sequence+1
     */
    public void unlockRead(long stamp) {
        long s, m; WNode h;
        for (;;) {
            if (((s = state) & SBITS) != (stamp & SBITS) ||
                (stamp & ABITS) == 0L || (m = s & ABITS) == 0L || m == WBIT)
                throw new IllegalMonitorStateException();
            // 读锁未超限
            if (m < RFULL) {
                // 读锁数量减1
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    // 如果读锁数量为1，唤醒同步队列中的队首节点
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            // 读锁超限，readerOverflow减1
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    /**
     * If the lock state matches the given stamp, releases the
     * corresponding mode of the lock.
     *
     * @param stamp a stamp returned by a lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    public void unlock(long stamp) {
        long a = stamp & ABITS, m, s; WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L)
                break;
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, returns it.  Or, if a read lock, if the write lock is
     * available, releases the read lock and returns a write stamp.
     * Or, if an optimistic read, returns a write stamp only if
     * immediately available. This method returns zero in all other
     * cases.
     *
     * @param stamp a stamp
     * @return a valid write stamp, or zero on failure
     */
    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                return stamp;
            }
            else if (m == RUNIT && a != 0L) {
                if (U.compareAndSwapLong(this, STATE, s,
                                         next = s - RUNIT + WBIT))
                    return next;
            }
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, releases it and obtains a read lock.  Or, if a read lock,
     * returns it. Or, if an optimistic read, acquires a read lock and
     * returns a read stamp only if immediately available. This method
     * returns zero in all other cases.
     *
     * @param stamp a stamp
     * @return a valid read stamp, or zero on failure
     */
    public long tryConvertToReadLock(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = next = s + (WBIT + RUNIT);
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            }
            else if (a != 0L && a < WBIT)
                return stamp;
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp then, if the stamp
     * represents holding a lock, releases it and returns an
     * observation stamp.  Or, if an optimistic read, returns it if
     * validated. This method returns zero in all other cases, and so
     * may be useful as a form of "tryUnlock".
     *
     * @param stamp a stamp
     * @return a valid optimistic read stamp, or zero on failure
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        U.loadFence();
        for (;;) {
            if (((s = state) & SBITS) != (stamp & SBITS))
                break;
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                return s;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = next = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return next & SBITS;
                }
            }
            else if ((next = tryDecReaderOverflow(s)) != 0L)
                return next & SBITS;
        }
        return 0L;
    }

    /**
     * Releases the write lock if it is held, without requiring a
     * stamp value. This method may be useful for recovery after
     * errors.
     *
     * @return {@code true} if the lock was held, else false
     */
    public boolean tryUnlockWrite() {
        long s; WNode h;
        if (((s = state) & WBIT) != 0L) {
            state = (s += WBIT) == 0L ? ORIGIN : s;
            if ((h = whead) != null && h.status != 0)
                release(h);
            return true;
        }
        return false;
    }

    /**
     * Releases one hold of the read lock if it is held, without
     * requiring a stamp value. This method may be useful for recovery
     * after errors.
     *
     * @return {@code true} if the read lock was held, else false
     */
    public boolean tryUnlockRead() {
        long s, m; WNode h;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return true;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    /**
     * Returns combined state-held and overflow read count for given
     * state s.
     */
    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL)
            readers = RFULL + readerOverflow;
        return (int) readers;
    }

    /**
     * Returns {@code true} if the lock is currently held exclusively.
     *
     * @return {@code true} if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * Returns {@code true} if the lock is currently held non-exclusively.
     *
     * @return {@code true} if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    /**
     * Returns a string identifying this lock, as well as its lock
     * state.  The state, in brackets, includes the String {@code
     * "Unlocked"} or the String {@code "Write-locked"} or the String
     * {@code "Read-locks:"} followed by the current number of
     * read-locks held.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        long s = state;
        return super.toString() +
            ((s & ABITS) == 0L ? "[Unlocked]" :
             (s & WBIT) != 0L ? "[Write-locked]" :
             "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #readLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asReadLock() {
        ReadLockView v;
        return ((v = readLockView) != null ? v :
                (readLockView = new ReadLockView()));
    }

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #writeLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asWriteLock() {
        WriteLockView v;
        return ((v = writeLockView) != null ? v :
                (writeLockView = new WriteLockView()));
    }

    /**
     * Returns a {@link ReadWriteLock} view of this StampedLock in
     * which the {@link ReadWriteLock#readLock()} method is mapped to
     * {@link #asReadLock()}, and {@link ReadWriteLock#writeLock()} to
     * {@link #asWriteLock()}.
     *
     * @return the lock
     */
    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        return ((v = readWriteLockView) != null ? v :
                (readWriteLockView = new ReadWriteLockView()));
    }

    // view classes

    final class ReadLockView implements Lock {
        public void lock() { readLock(); }
        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }
        public boolean tryLock() { return tryReadLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockRead(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class WriteLockView implements Lock {
        public void lock() { writeLock(); }
        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }
        public boolean tryLock() { return tryWriteLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockWrite(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class ReadWriteLockView implements ReadWriteLock {
        public Lock readLock() { return asReadLock(); }
        public Lock writeLock() { return asWriteLock(); }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.

    final void unstampedUnlockWrite() {
        WNode h; long s;
        if (((s = state) & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        state = (s += WBIT) == 0L ? ORIGIN : s;
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    final void unstampedUnlockRead() {
        for (;;) {
            long s, m; WNode h;
            if ((m = (s = state) & ABITS) == 0L || m >= WBIT)
                throw new IllegalMonitorStateException();
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }

    // internals

    /**
     * Tries to increment readerOverflow by first setting state
     * access bits value to RBITS, indicating hold of spinlock,
     * then updating, then releasing.
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     *
     * 使用readerOverflow来记录读锁数量
     */
    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                ++readerOverflow;
                state = s;
                return s;
            }
        }
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * Tries to decrement readerOverflow.
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                int r; long next;
                if ((r = readerOverflow) > 0) {
                    readerOverflow = r - 1;
                    next = s;
                }
                else
                    next = s - RUNIT;
                 state = next;
                 return next;
            }
        }
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * Wakes up the successor of h (normally whead). This is normally
     * just h.next, but may require traversal from wtail if next
     * pointers are lagging. This may fail to wake up an acquiring
     * thread when one or more have been cancelled, but the cancel
     * methods themselves provide extra safeguards to ensure liveness.
     *
     * 释放锁，唤醒后继节点
     */
    private void release(WNode h) {
        if (h != null) {
            WNode q; Thread w;
            // 头节点状态由-1设置为0
            U.compareAndSwapInt(h, WSTATUS, WAITING, 0);
            if ((q = h.next) == null || q.status == CANCELLED) {
                // 从队尾开始查找距离头节点最近的状态为-1的节点
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            // 唤醒队列头节点
            if (q != null && (w = q.thread) != null)
                U.unpark(w);
        }
    }

    /**
     * See above for explanation.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero)
     * @return next state, or INTERRUPTED
     *
     * 获取写锁，尝试自旋获取解锁，获取不到则入同步队列并阻塞挂起
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        // node是当前结点，p是当前结点的前驱结点
        WNode node = null, p;
        // 这个自旋主要用来尝试获取锁，获取不到就如同步队列
        for (int spins = -1;;) { // spin while enqueuing
            long m, s, ns;
            // (s = state) & ABITS 为0表示没有读锁也没有写锁，尝试cas获取锁
            if ((m = (s = state) & ABITS) == 0L) {
                if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT))
                    return ns;
            }
            // 设置自旋次数
            else if (spins < 0)
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;
            // 每次自旋后自旋次数减少
            else if (spins > 0) {
                if (LockSupport.nextSecondarySeed() >= 0)
                    --spins;
            }
            // 尾节点为空，说明队列为空，初始化同步队列
            else if ((p = wtail) == null) { // initialize queue
                WNode hd = new WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }
            // 创建要入队的节点，写模式的节点
            else if (node == null)
                node = new WNode(WMODE, p);
            else if (node.prev != p)
                node.prev = p;
            // 节点入队，入队后跳出自旋
            else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                p.next = node;
                break;
            }
        }

        // 这里的自旋主要用来阻塞挂起线程
        for (int spins = -1;;) {
            WNode h, np, pp; int ps;
            // 头节点和尾节点相等
            if ((h = whead) == p) {
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;
                // 再次进行自旋获取锁
                for (int k = spins;;) { // spin at head
                    long s, ns;
                    if (((s = state) & ABITS) == 0L) {
                        // 能够获取到锁后直接返回
                        if (U.compareAndSwapLong(this, STATE, s,
                                                 ns = s + WBIT)) {
                            whead = node;
                            node.prev = null;
                            return ns;
                        }
                    }
                    // 自旋次数减少
                    else if (LockSupport.nextSecondarySeed() >= 0 &&
                             --k <= 0)
                        break;
                }
            }
            // 唤醒头节点的cowait栈中的读线程
            else if (h != null) { // help release stale waiters
                WNode c; Thread w;
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null)
                        U.unpark(w);
                }
            }
            if (whead == h) {
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                else if ((ps = p.status) == 0)
                    // 当前节点的前驱节点的状态设置为-1
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time; // 0 argument to park means no timeout
                    if (deadline == 0L)
                        time = 0L;
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) &&
                        whead == h && node.prev == p)
                        // 阻塞当前线程
                        U.park(false, time);  // emulate LockSupport.park
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted())
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * See above for explanation.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero)
     * @return next state, or INTERRUPTED
     *
     * 先尝试自旋的读锁获取，获取不到则加入等待队列并挂起阻塞线程
     */
    private long acquireRead(boolean interruptible, long deadline) {
        // node是当前要入队的节点，p是入队之前的尾结点
        WNode node = null, p;
        /*
            第一次自旋，有两种情况：
            第一种情况，锁被占用，同步队列为空，没有其他的线程在排队等待锁，这时候
            可以自旋尝试获取锁，因为此时应该可以很快就获取到锁，不应该立刻就进行阻
            塞挂起的操作。
            第二种情况，写锁被占用，排队队列不为空，并且队尾是读模式的节点，这时候
            会进行自旋获取锁，获取不到就阻塞线程，等待被唤醒后继续在此自旋获取锁。
         */
        for (int spins = -1;;) {
            // h是队列的头结点
            WNode h;
            /*
                头结点和尾结点相等，表示队列为空或者只有头结点，直接先自旋一段时间尝试获取读锁；
                因为前面没有获取到锁，走到这里发现队列为空，应该可以马上就获取到锁，所以这里进
                行自旋尝试获取锁。
             */
            if ((h = whead) == (p = wtail)) {
                // s是state，就是sequence；ns是获取读锁之后的值，就是sequence+1；m是写锁的状态
                for (long m, s, ns;;) {
                    /*
                        (m = (s = state) & ABITS) < RFULL用来判断写锁是否被占用，
                        如果写锁没有被占用并且读锁数量未超限，直接cas更新同步状态；
                        如果写锁没有被占用但是读锁数量超限，则超出的数量放到readerOverflow中记录，
                        获取写锁成功就直接返回。
                     */
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L))
                        return ns;
                    // m >= WBIT说明写锁被占用
                    else if (m >= WBIT) {
                        // 需要自旋尝试获取锁
                        if (spins > 0) {
                            if (LockSupport.nextSecondarySeed() >= 0)
                                --spins;
                        }
                        else {
                            // 用完最大自旋次数后
                            if (spins == 0) {
                                // nh是当前队列的头节点，np是当前队列的尾结点
                                WNode nh = whead, np = wtail;
                                if ((nh == h && np == p) || (h = nh) != (p = np))
                                    break;
                            }
                            spins = SPINS;
                        }
                    }
                }
            }
            // p == null说明队列为空，且当前线程未获取到读锁，需要初始化同步队列
            if (p == null) { // initialize queue
                // 初始化一个新节点，写模式的节点
                WNode hd = new WNode(WMODE, null);
                // cas更新队列的头结点为新节点
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    // 新节点赋值给尾结点
                    wtail = hd;
            }
            // 初始化当前节点，读模式的节点，p是当前队列的尾结点
            else if (node == null)
                node = new WNode(RMODE, p);
            // h == p说明队列只有一个头节点，p.mode != RMODE说明尾结点不是读节点
            else if (h == p || p.mode != RMODE) {
                if (node.prev != p)
                    node.prev = p;
                // cas直接将新节点添加到队列尾，完成后退出自旋
                else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                    p.next = node;
                    break;
                }
            }
            // 队列不为空，且队尾是读节点，将当前节点添加到队尾节点的cowait链中，形成一个栈
            else if (!U.compareAndSwapObject(p, WCOWAIT,
                                             node.cowait = p.cowait, node))
                node.cowait = null;
            /*
                队列不为空，当前节点不为空，头节点和尾节点不相等，当前锁状态是读锁状态，当前节点cas加入尾节点的cowait栈失败，
                也就是有线程获取到了写锁还没释放，队列中有读锁在排队，这里会再次自旋尝试获取锁
             */
            else {
                for (;;) {
                    WNode pp, c; Thread w;
                    // 头节点不为空，并且头节点的cowait栈不为空，并且线程也不为空，则将cowait栈中第一个元素唤醒
                    if ((h = whead) != null && (c = h.cowait) != null &&
                        U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null) // help release
                        U.unpark(w);
                    // 头节点等于尾节点的前驱节点或者头节点等于尾节点或者尾节点的前驱节点为空，说明可能马上轮到自己获取锁
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        long m, s, ns;
                        do {
                            // 尝试获取锁
                            if ((m = (s = state) & ABITS) < RFULL ?
                                U.compareAndSwapLong(this, STATE, s,
                                                     ns = s + RUNIT) :
                                (m < WBIT &&
                                 (ns = tryIncReaderOverflow(s)) != 0L))
                                return ns;
                        } while (m < WBIT); // m小于WBIT说明写锁没被占用
                    }
                    // 头节点没发生变化，尾节点也没发生变化
                    if (whead == h && p.prev == pp) {
                        long time;
                        // 尾节点的前驱节点为空或者头节点等于尾节点或者老的尾节点已经取消
                        if (pp == null || h == p || p.status > 0) {
                            // 当前节点置为null后退出当前循环，回到外面的大循环后会重新创建节点
                            node = null; // throw away
                            break;
                        }
                        if (deadline == 0L)
                            time = 0L;
                        else if ((time = deadline - System.nanoTime()) <= 0L)
                            // 超时后取消节点
                            return cancelWaiter(node, p, false);
                        Thread wt = Thread.currentThread();
                        U.putObject(wt, PARKBLOCKER, this);
                        node.thread = wt;
                        if ((h != pp || (state & ABITS) == WBIT) &&
                            whead == h && p.prev == pp)
                            // 阻塞当前线程
                            U.park(false, time);
                        // 线程唤醒后继续自旋
                        node.thread = null;
                        U.putObject(wt, PARKBLOCKER, null);
                        if (interruptible && Thread.interrupted())
                            return cancelWaiter(node, p, true);
                    }
                }
            }
        }

        /*
            第二次自旋，这一次自旋是对上面第一次自旋的补充，上面的情况是可能马上就获取到锁，
            但是持有锁的线程一直没有释放锁，这里会继续自旋，并且自旋次数会增加；如果这里自
            旋还是没有获取到锁，就阻塞当前线程，等到被唤醒后继续获取锁。
         */
        for (int spins = -1;;) {
            WNode h, np, pp; int ps;
            // 头节点和尾节点相等，可能马上就能获取到锁
            if ((h = whead) == p) {
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;
                // 自旋获取锁
                for (int k = spins;;) { // spin at head
                    long m, s, ns;
                    // 尝试获取锁
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        // 获取到了锁
                        WNode c; Thread w;
                        whead = node;
                        node.prev = null;
                        // 唤醒当前节点中的cowait栈中的节点线程
                        while ((c = node.cowait) != null) {
                            if (U.compareAndSwapObject(node, WCOWAIT,
                                                       c, c.cowait) &&
                                (w = c.thread) != null)
                                U.unpark(w);
                        }
                        return ns;
                    }
                    // 没获取到锁，减少自旋次数
                    else if (m >= WBIT &&
                             LockSupport.nextSecondarySeed() >= 0 && --k <= 0)
                        break;
                }
            }
            // 队列不为空，头节点不为空
            else if (h != null) {
                WNode c; Thread w;
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null)
                        U.unpark(w);
                }
            }
            // 头节点没变化
            if (whead == h) {
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                else if ((ps = p.status) == 0)
                    // 前驱节点状态设置为-1，需要进行阻塞挂起
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                // 自旋后还是没有获取到锁，进行阻塞操作
                else {
                    long time;
                    if (deadline == 0L)
                        time = 0L;
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        // 超时取消节点
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 &&
                        (p != h || (state & ABITS) == WBIT) &&
                        whead == h && node.prev == p)
                        // 阻塞线程
                        U.park(false, time);
                    // 线程被唤醒后继续自旋获取锁
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted())
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * If node non-null, forces cancel status and unsplices it from
     * queue if possible and wakes up any cowaiters (of the node, or
     * group, as applicable), and in any case helps release current
     * first waiter if lock is free. (Calling with null arguments
     * serves as a conditional form of release, which is not currently
     * needed but may be needed under possible future cancellation
     * policies). This is a variant of cancellation methods in
     * AbstractQueuedSynchronizer (see its detailed explanation in AQS
     * internal documentation).
     *
     * @param node if nonnull, the waiter
     * @param group either node or the group node is cowaiting with
     * @param interrupted if already interrupted
     * @return INTERRUPTED if interrupted or Thread.interrupted, else zero
     */
    private long cancelWaiter(WNode node, WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            node.status = CANCELLED;
            // unsplice cancelled nodes from group
            for (WNode p = group, q; (q = p.cowait) != null;) {
                if (q.status == CANCELLED) {
                    U.compareAndSwapObject(p, WCOWAIT, q, q.cowait);
                    p = group; // restart
                }
                else
                    p = q;
            }
            if (group == node) {
                for (WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null)
                        U.unpark(w);       // wake up uncancelled co-waiters
                }
                for (WNode pred = node.prev; pred != null; ) { // unsplice
                    WNode succ, pp;        // find valid successor
                    while ((succ = node.next) == null ||
                           succ.status == CANCELLED) {
                        WNode q = null;    // find successor the slow way
                        for (WNode t = wtail; t != null && t != node; t = t.prev)
                            if (t.status != CANCELLED)
                                q = t;     // don't link if succ cancelled
                        if (succ == q ||   // ensure accurate successor
                            U.compareAndSwapObject(node, WNEXT,
                                                   succ, succ = q)) {
                            if (succ == null && node == wtail)
                                U.compareAndSwapObject(this, WTAIL, node, pred);
                            break;
                        }
                    }
                    if (pred.next == node) // unsplice pred link
                        U.compareAndSwapObject(pred, WNEXT, node, succ);
                    if (succ != null && (w = succ.thread) != null) {
                        succ.thread = null;
                        U.unpark(w);       // wake up succ to observe new pred
                    }
                    if (pred.status != CANCELLED || (pp = pred.prev) == null)
                        break;
                    node.prev = pp;        // repeat if new pred wrong/cancelled
                    U.compareAndSwapObject(pp, WNEXT, pred, succ);
                    pred = pp;
                }
            }
        }
        WNode h; // Possibly release first waiter
        while ((h = whead) != null) {
            long s; WNode q; // similar to release() but check eligibility
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (h == whead) {
                if (q != null && h.status == 0 &&
                    ((s = state) & ABITS) != WBIT && // waiter is eligible
                    (s == 0L || q.mode == RMODE))
                    release(h);
                break;
            }
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long STATE;
    private static final long WHEAD;
    private static final long WTAIL;
    private static final long WNEXT;
    private static final long WSTATUS;
    private static final long WCOWAIT;
    private static final long PARKBLOCKER;

    static {
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = StampedLock.class;
            Class<?> wk = WNode.class;
            STATE = U.objectFieldOffset
                (k.getDeclaredField("state"));
            WHEAD = U.objectFieldOffset
                (k.getDeclaredField("whead"));
            WTAIL = U.objectFieldOffset
                (k.getDeclaredField("wtail"));
            WSTATUS = U.objectFieldOffset
                (wk.getDeclaredField("status"));
            WNEXT = U.objectFieldOffset
                (wk.getDeclaredField("next"));
            WCOWAIT = U.objectFieldOffset
                (wk.getDeclaredField("cowait"));
            Class<?> tk = Thread.class;
            PARKBLOCKER = U.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));

        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
