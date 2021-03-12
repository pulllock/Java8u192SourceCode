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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import sun.misc.Unsafe;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h3>Usage</h3>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 *
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 *
 * (Shared mode is similar but may involve cascading signals.)
 *
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h3>Usage Examples</h3>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes
 * one of the instrumentation methods:
 *
 *  <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Reports whether in locked state
 *     protected boolean isHeldExclusively() {
 *       return getState() == 1;
 *     }
 *
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (getState() == 0) throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Provides a Condition
 *     Condition newCondition() { return new ConditionObject(); }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()                { sync.acquire(1); }
 *   public boolean tryLock()          { return sync.tryAcquire(1); }
 *   public void unlock()              { sync.release(1); }
 *   public Condition newCondition()   { return sync.newCondition(); }
 *   public boolean isLocked()         { return sync.isHeldExclusively(); }
 *   public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 *  <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 *
 * 参考：
 * - https://topsli.github.io/2016/03/13/aqs.html
 * - http://kexianda.info/2017/08/13/%E5%B9%B6%E5%8F%91%E7%B3%BB%E5%88%97-3-%E4%BB%8EAQS%E5%88%B0futex%E4%B9%8B%E4%B8%80-AQS%E5%92%8CLockSupport/
 * - http://ifeve.com/aqs/
 * - https://www.cnblogs.com/binarylei/p/12533857.html
 *
 * 1. 操作系统中的信号量
 * 2. 互斥锁和同步
 * 3. 信号量实现互斥锁、实现生产者消费者
 * 4. 管程
 * 5. 管程和synchronize以及AQS
 * 6. LockSupport
 * 7. CLH队列锁
 *
 * 管程
 * 信号量的使用比较繁琐，并且极容易出错，管程解决了信号量的这些问题。
 *
 * 管程在信号量的基础上增加了条件变量和等待队列，封装了同步操作，管程的组成如下：
 * 一个变量、条件队列和对应的等待队列、同步队列、条件变量可执行的wait和signal操作
 *
 * 管程模型：
 * 1. Hasen模型
 * 2. Hoare模型
 * 3. MESA模型
 *
 * 假设两个线程T1和T2，T1等待T2的某些操作，使得T1等待的条件成立
 * Hasen模型：要求将notify放到代码最后，也就是T2执行完后才通知T1执行，这样就能保证同一时刻
 * 只有一个线程执行
 *
 * Hoare模型：T2执行，通知T1后，T2马上阻塞，T1执行，等待T1执行完成后唤醒T2继续执行，这样
 * T2多了一次阻塞唤醒操作
 *
 * MESA模型：T2执行，通知T1后，T2继续执行，T1不立刻执行，而是从条件变量的等待队列进入到入口的
 * 等待队列。T1需要使用自旋来检测条件变量，看是否满足条件。
 *
 * synchronized和AQS都是基于管程实现的。
 *
 * 条件变量
 *
 * 条件变量是用来实现线程间的依赖或等待机制的方法，比如线程A阻塞等待某个条件才能继续执行，线程B
 * 的执行使得条件成立，就会唤醒A继续执行。
 *
 * AQS设计思路
 * 参考：https://blog.csdn.net/lengxiao1993/article/details/108449850
 * AQS的需求：
 * - 功能需求：提供两种基本操作acquire和release
 * - 性能需求：允许定制化公平性策略、尽可能缩短释放锁和获取锁之间的时间、平衡CPU和内存的资源消耗
 *
 * 核心思路：
 * 1. 获取锁的线程需要排队，可满足公平性需求
 * 2. 选择性的阻塞正在等待获取锁的线程，这样在竞争激烈的时候，可让资源消耗控制在一个可预测范围内；
 * 同时允许一定程度自旋，在竞争少的情况下能尽可能缩短释放锁和获取锁之间的时间消耗。
 * 3. 释放锁的时候，根据情况唤醒一个或者多个线程，平衡竞争效率和公平性的需求。
 *
 * 由思路引出要解决的问题：
 * - 如何原子性管理同步器状态
 * - 如何维护一个队列
 * - 如何阻塞和唤醒线程
 *
 * AQS中同步器状态的表示
 * 使用一个volatile类型的int变量，既可以表示锁（Lock）类型同步器状态，也可以表示计数类型的同步器状态（比如Semaphore）
 *
 * AQS中使用的CLH队列锁
 * AQS核心就是阻塞线程的队列管理，使用CLH锁的变种，对CLH做了修改和调整。
 *
 * AQS的CLH队列锁添加了前驱和后继指针
 * CLH锁原来的设计中，结点之间没有使用指针相互关联，而AQS的CLH队列结点增加了前驱和后继指针。
 *
 * 通过前驱指针，AQS的CLH队列可以处理锁获取过程中的超时和取消，如果一个结点的前驱结点对应线程
 * 取消了对锁的等待，当前结点可以利用前驱指针读取更前面的结点状态，用于判断自己是否可以获取锁
 *
 * 通过后继指针，可以帮助当前结点快捷找到后继结点，进行唤醒。
 *
 * 后继指针是不可靠的，如果试图从队列头部通过后继指针遍历整个队列时，可能某个结点的后继指针为空，但是
 * 实际该结点的后继已经追加了一个甚至多个结点，所以当通过后继指针找不到后继结点时，必须需要从尾部依靠
 * 前驱指针反向遍历一下，才能判断结点的后继是否真的没有结点。
 *
 * AQS的CLH队列修改了锁获取的判定条件
 * CLH的锁获取的原始设计如下：
 * 结点有一个状态变量，每个线程自旋判断前驱结点的状态变量，用来判断自己能否获取锁，当一个结点释放锁的时候，
 * 会修改自己的结点状态，用来通知后继结点可以结束自旋。
 *
 * AQS做了如下调整：
 * - CLH原始设计中的每个节点自己的状态变量被抽取出来，变为整个队列可见的公共的变量
 * - AQS添加一个head指针，当持有锁的线程释放锁的时候，会将head指针指向这个线程对应的节点，通知后续线程
 * 可以尝试获取锁
 * - 一个线程通过判断head的位置，决定自己是否可以获得锁。队列中的头结点线程可以通过cas方式原子性修改状态变量，
 * 修改成功就是获得了锁
 *
 * AQS的CLH队列为结点增加了waitStatus变量
 * waitStatus变量有如下表示信息：
 * - 可表示线程是否取消了锁的等待
 * - 可表示线程是否需要唤醒下一个等待线程
 * - 可表示条件变量的等待
 * - 可表示共享状态
 *
 * AQS如何阻塞和唤醒线程
 * JSR166之前只有Thread.suspend和Thread.resume，但是如果一个线程调用了resume后调用suspend，
 * 则这个resume不会产生任何作用。JSR166后增加了LockSupport来解决这个问题，park阻塞当前线程，
 * unpark唤醒线程
 * 如果在park之前调用unpark，park不会阻塞线程，也就是说unpark可以先于park调用。
 *
 * unpark操作不计数，在park之前多次调用unpark，park调用时不会阻塞线程，如果再次调用park，则会阻塞线程。
 *
 * park和unpark还支持超时和中断。
 *
 * acquire操作：
 * if (尝试获取锁不成功) {
 *     node = 创建新结点，并入队列
 *     pred = 结点的前驱结点
 *     while (pred 不是头结点 || 尝试获取锁失败) {
 *         if (前驱结点的waitStatus是SIGNAL) {
 *             将当前线程挂起
 *         } else {
 *             cas设置前驱结点的waitStatus为SIGNAL
 *         }
 *
 *         head = node
 *     }
 * }
 *
 * release操作：
 * if (尝试释放锁 && 头结点的waitStatus是SIGNAL) {
 *     cas设置头结点waitStatus不是SIGNAL
 *     唤醒后继结点
 * }
 */
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     * Wait queue node class.
     *
     * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers, but
     * use the same basic tactic of holding some of the control
     * information about a thread in the predecessor of its node.  A
     * "status" field in each node keeps track of whether a thread
     * should block.  A node is signalled when its predecessor
     * releases.  Each node of the queue otherwise serves as a
     * specific-notification-style monitor holding a single waiting
     * thread. The status field does NOT control whether threads are
     * granted locks etc though.  A thread may try to acquire if it is
     * first in the queue. But being first does not guarantee success;
     * it only gives the right to contend.  So the currently released
     * contender thread may need to rewait.
     *
     * <p>To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you just set the head field.
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     *
     * <p>Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple atomic point of
     * demarcation from unqueued to queued. Similarly, dequeuing
     * involves only updating the "head". However, it takes a bit
     * more work for nodes to determine who their successors are,
     * in part to deal with possible cancellation due to timeouts
     * and interrupts.
     *
     * <p>The "prev" links (not used in original CLH locks), are mainly
     * needed to handle cancellation. If a node is cancelled, its
     * successor is (normally) relinked to a non-cancelled
     * predecessor. For explanation of similar mechanics in the case
     * of spin locks, see the papers by Scott and Scherer at
     * http://www.cs.rochester.edu/u/scott/synchronization/
     *
     * <p>We also use "next" links to implement blocking mechanics.
     * The thread id for each node is kept in its own node, so a
     * predecessor signals the next node to wake up by traversing
     * next link to determine which thread it is.  Determination of
     * successor must avoid races with newly queued nodes to set
     * the "next" fields of their predecessors.  This is solved
     * when necessary by checking backwards from the atomically
     * updated "tail" when a node's successor appears to be null.
     * (Or, said differently, the next-links are an optimization
     * so that we don't usually need a backward scan.)
     *
     * <p>Cancellation introduces some conservatism to the basic
     * algorithms.  Since we must poll for cancellation of other
     * nodes, we can miss noticing whether a cancelled node is
     * ahead or behind us. This is dealt with by always unparking
     * successors upon cancellation, allowing them to stabilize on
     * a new predecessor, unless we can identify an uncancelled
     * predecessor who will carry this responsibility.
     *
     * <p>CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     *
     * <p>Threads waiting on Conditions use the same nodes, but
     * use an additional link. Conditions only need to link nodes
     * in simple (non-concurrent) linked queues because they are
     * only accessed when exclusively held.  Upon await, a node is
     * inserted into a condition queue.  Upon signal, the node is
     * transferred to the main queue.  A special value of status
     * field is used to mark which queue a node is on.
     *
     * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     * 同步队列中的节点用来保存获取同步状态失败的线程引用、等待状态、以及
     * 前驱和后继节点。
     *
     * 了解CLH锁的顺序如下：
     * 1. 自旋锁 SpinLock
     * 2. 排号自旋锁 Ticket Lock
     * 3. MCS锁
     * 4. CLH锁
     *
     * 参考：
     * https://zh.wikipedia.org/wiki/%E8%87%AA%E6%97%8B%E9%94%81
     * https://zh.wikipedia.org/wiki/%E6%8E%92%E5%8F%B7%E8%87%AA%E6%97%8B%E9%94%81
     * https://coderbee.net/index.php/concurrent/20131115/577
     * https://destiny1020.blog.csdn.net/article/details/79677891
     * https://destiny1020.blog.csdn.net/article/details/79783104
     * https://destiny1020.blog.csdn.net/article/details/79842501
     *
     * https://blog.csdn.net/lengxiao1993/article/details/108227584
     * https://blog.csdn.net/lengxiao1993/article/details/108448199
     * https://blog.csdn.net/lengxiao1993/article/details/108449111
     * https://blog.csdn.net/lengxiao1993/article/details/108449850
     *
     * 自旋锁
     * 自旋锁是用于多线程间同步的一种锁，自旋锁使用的基本思路是：如果线程获取不到锁，不会将线程
     * 挂起等待，而是一直循环检测锁是否可用。等到锁可用，就会退出循环，表示当前线程获取到锁。
     *
     * 自旋锁优点：
     * 由于等待锁的线程一直自旋，没有挂起阻塞操作，避免了进程或者线程的调度开销
     *
     * 自旋锁缺点：
     * - 由于等待锁的线程一直自旋，会浪费CPU
     * - 自旋锁不能保证公平性，多个线程自旋等待锁的时候，获取锁的时候是没有次序的，非公平的
     *
     * 自旋锁适用场景：
     * 由于等待锁的一直自旋占有CPU，因此比较适合等待锁的时间很短的场景。短暂的自旋就能获取到锁，相比
     * 挂起等待的进程调度开销要小的情况下收益才比较明显。
     *
     * 单核单线程CPU不适合使用自旋锁，同一时间只有一个线程在运行状态，如果自旋锁获取的时间较长，导致
     * 获取锁的线程长时间占用CPU，浪费资源。
     *
     * 排号自旋锁
     * 排号自旋锁解决了自旋锁的公平性问题。排号自旋锁和去银行排队办理业务类似。
     *
     * 排号自旋锁有一个变量叫服务号ServiceNumber，表示已经获取到锁的线程，还有一个变量叫排队号Ticket，
     * 每一个获取锁的线程都会获得一个排队号，当一个线程获取到锁的时候，服务号和该线程的排队号相等，
     * 做完操作释放锁的时候，该线程会将自己的排队号加1，并赋值给服务号，其他等待锁的线程都拥有自己的排队号，
     * 并且他们在自旋等待，当锁被释放时，等待的某一个线程会发现新的服务号和他自己的排队号相等，
     * 于是这个线程就能获取到锁了。
     *
     * 利用排队号的顺序，就能让锁的获取有序，保证了公平性（FIFO）。
     *
     * 排号自旋锁优点：
     * 解决了自旋锁的公平问题。
     *
     * 排号自旋锁的缺点
     * 在多处理器上，多个进程或者线程需要读写同一个ServiceNumber服务号，处理器核数越多，同步问题越严重，
     * 会降低系统的性能。
     *
     * 所有等待锁的线程都在同一个共享变量上自旋，会导致频繁的CPU缓存同步，可以使用MCS锁和CLH锁来解决这个问题。
     *
     * MCS锁
     * MCS锁是基于单向链表的自旋锁，保证公平性，并且性能高。
     *
     * MCS锁中，等待锁的线程只需要在本地变量上自旋，不需要所有线程共享同一个变量，并且每个结点的直接前驱来通知
     * 结点自旋结束。
     *
     * 由于自旋是在本地变量，不是共享变量，相比排号自旋锁，解决了共享变量导致的CPU缓存同步问题。
     *
     * MCS是一个不可重入的独占锁。
     *
     * CLH锁
     * CLH锁是比MCS更轻量的一个锁，基于单向链表（隐式创建）的自旋锁，保证公平性，性能高。
     *
     * CLH锁中，等待锁的线程只需要在前驱结点的本地变量进行自旋，和MCS锁不一样，MCS在线程自己的节点上进行自旋。
     *
     * CLH是一个不可重入的独占锁。
     */
    static final class Node {
        /**
         * Marker to indicate a node is waiting in shared mode
         * 标记结点当前在共享模式下
         */
        static final Node SHARED = new Node();
        /**
         * Marker to indicate a node is waiting in exclusive mode
         * 标记结点当前在独占模式下
         */
        static final Node EXCLUSIVE = null;

        /**
         * 下面几个int常量是给waitStatus用的
         * waitStatus value to indicate thread has cancelled
         * 表示此线程取消了争抢这个锁
         * 同步队列中等待的线程等待超时或者被中断，需要从同步队列中取消等待，
         * 节点进入该状态后将不会在发生变化。
         */
        static final int CANCELLED =  1;
        /**
         * waitStatus value to indicate successor's thread needs unparking
         * 表示当前结点的后继结点对应的线程需要被唤醒
         * 后继节点的状态处于等待，当前结点的线程如果释放了同步状态或者被取消，
         * 将会通知后继节点，使后继节点的线程得以运行。
         */
        static final int SIGNAL    = -1;
        /**
         * waitStatus value to indicate thread is waiting on condition
         * 节点在等待队列中，节点线程等待在Condition上，当其他线程对Condition调用了
         * signal()方法后，该节点会从等待队列中转移到同步队列中，加入到对同步状态的获取中。
         */
        static final int CONDITION = -2;

        /*
            PROPAGATE的引入是为了解决有些线程无法被唤醒的问题，
            bug详情：https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6801020
            代码变更：http://gee.cs.oswego.edu/cgi-bin/viewcvs.cgi/jsr166/src/main/java/util/concurrent/locks/AbstractQueuedSynchronizer.java?r1=1.73&r2=1.74
            旧的setHeadAndPropagate方法如下：
             private void setHeadAndPropagate(Node node, int propagate) {
                setHead(node);
                if (propagate > 0 && node.waitStatus != 0) {
                    Node s = node.next;
                    if (s == null || s.isShared())
                        unparkSuccessor(node);
                }
             }

             旧的releaseShared方法如下：
             public final boolean releaseShared(int arg) {
                 if (tryReleaseShared(arg)) {
                    Node h = head;
                     if (h != null && h.waitStatus != 0)
                        unparkSuccessor(h);
                     return true;
                 }
                 return false;
             }

            bug中提供的示例代码：
            import java.util.concurrent.Semaphore;

            public class TestSemaphore {

                private static Semaphore sem = new Semaphore(0);

                private static class Thread1 extends Thread {
                    @Override
                    public void run() {
                        sem.acquireUninterruptibly();
                    }
                }

                private static class Thread2 extends Thread {
                    @Override
                    public void run() {
                        sem.release();
                    }
                }

                public static void main(String[] args) throws InterruptedException {
                    for (int i = 0; i < 10000000; i++) {
                        Thread t1 = new Thread1();
                        Thread t2 = new Thread1();
                        Thread t3 = new Thread2();
                        Thread t4 = new Thread2();
                        t1.start();
                        t2.start();
                        t3.start();
                        t4.start();
                        t1.join();
                        t2.join();
                        t3.join();
                        t4.join();
                        System.out.println(i);
                    }
                }
            }

            按照老的代码分析下示例代码：
            Semaphore初始化state值为0，4个线程运行，线程t1和t2同时获取锁，线程t3和t4同时释放锁.
            由于state初始值为0，t1和t2获取锁会失败，此时同步队列中排队情况如下：
            head --> t1 --> t2

            接下来t3先释放锁，t4后释放锁

            t3调用releaseShared方法，唤醒队列中的t1，此时head状态由-1变为0，t1被唤醒后从挂起的
            地方继续执行，会先调用tryAcquireShared，该方法返回的propagate值为0，此时还未执行
            setHeadAndPropagate方法。

            此时t4调用releaseShared方法，而旧的方法代码如下：
            public final boolean releaseShared(int arg) {
                 if (tryReleaseShared(arg)) {
                    Node h = head;
                     if (h != null && h.waitStatus != 0)
                        unparkSuccessor(h);
                     return true;
                 }
                 return false;
             }
             此时t4读到的head和上面t1被唤醒还未执行setHeadAndPropagate方法的head是一样的，而
             head的waitStatus=0，所以旧代码releaseShared方法中的if (h != null && h.waitStatus != 0)
             不满足，不能调用unparkSuccessor唤醒后继结点。

             而这个时候，t1继续执行setHeadAndPropagate方法，旧代码如下：
             private void setHeadAndPropagate(Node node, int propagate) {
                setHead(node);
                if (propagate > 0 && node.waitStatus != 0) {
                    Node s = node.next;
                    if (s == null || s.isShared())
                        unparkSuccessor(node);
                }
             }
             此时唤醒t1时的propagate=0，而不能满足if (propagate > 0 && node.waitStatus != 0)条件，也不会唤醒
             后继结点。

             本来t2应该要唤醒的，但是上面的情况导致了t2不能被唤醒。

             引入了PROPAGATE=-3之后的情况如下：
             新的setHeadAndPropagate方法的代码：
             private void setHeadAndPropagate(Node node, int propagate) {
                Node h = head; // Record old head for check below
                setHead(node);
                 if (propagate > 0 || h == null || h.waitStatus < 0) {
                     Node s = node.next;
                     if (s == null || s.isShared())
                        doReleaseShared();
                 }
             }

             新的releaseShared方法的代码：
             public final boolean releaseShared(int arg) {
                 if (tryReleaseShared(arg)) {
                    doReleaseShared();
                    return true;
                 }
                 return false;
             }

             新的doReleaseShared方法的代码：
             private void doReleaseShared() {
                for (;;) {
                    Node h = head;
                    if (h != null && h != tail) {
                        int ws = h.waitStatus;
                        if (ws == Node.SIGNAL) {
                            if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                                continue;
                            unparkSuccessor(h);
                        }
                         else if (ws == 0 &&
                             !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                             continue;
                    }
                     if (h == head)
                        break;
                }
             }

            在引入了PROPAGATE之后的情况如下：
            t3调用releaseShared方法，唤醒队列中的t1，此时head状态由-1变为0，t1被唤醒后从挂起的
            地方继续执行，会先调用tryAcquireShared，该方法返回的propagate值为0，此时还未执行
            setHeadAndPropagate方法。

            此时t4调用releaseShared方法，此时t4读到的head和上面t1被唤醒还未执行setHeadAndPropagate
            方法的head是一样的，而head的waitStatus=0，此时新代码中就有对ws==0的判断，此时会将
            head结点状态waitStatus设置为PROPAGATE（-3）

            而这个时候，t1继续执行setHeadAndPropagate方法，新的代码如下：
            private void setHeadAndPropagate(Node node, int propagate) {
                Node h = head; // Record old head for check below
                setHead(node);
                 if (propagate > 0 || h == null || h.waitStatus < 0) {
                     Node s = node.next;
                     if (s == null || s.isShared())
                        doReleaseShared();
                 }
             }

             此时head的waitStatus=-3，被唤醒t1时的propagate=0，因此setHeadAndPropagate方法中
             if (propagate > 0 || h == null || h.waitStatus < 0) 判断就能满足，会继续调用
             releaseShared方法，后继线程

             PROPAGATE是为了让setHeadPropagate方法更快速的唤醒后继结点。
             Semaphore初始化state值为0，4个线程运行，线程t1和t2同时获取锁，线程t3和t4同时释放锁.
             由于state初始值为0，t1和t2获取锁会失败，假设此时的情况是：
             - t1入队列并park住
             - t2入队列但还没有park
             - t3执行releaseShared唤醒t1
             - t4执行releaseShared准备唤醒t2
             此时队列中的情况如下：
             head(waitStatus=-1) --> t1(waitStatus=0) --> t2

             假设t2入队列并且park住了，此时队列应该是如下：
             head(waitStatus=-1) --> t1(waitStatus=-1) --> t2
             t1的waitStatus=-1，t3唤醒t1后，t1执行setHeadPropagate时会执行doReleaseShared，这里会
             唤醒t2；或者t4在releaseShared的时候，也会执行doReleaseShared，也会唤醒t2，所以t2无论如何
             都会被唤醒，没有问题。

             假设t2入队列，还未park住，此时队列如下：
             head(waitStatus=-1) --> t1(waitStatus=0) --> t2
             t1的waitStatus=0，t3唤醒t1后，t1执行到了setHeadPropagate方法，此时有三种情况：

             第一种情况：t1执行到了setHeadPropagate方法，但还没执行setHead(node)将自己设置为head，此时
             t2已经入队列，但还没park住，这时候t2正在连续执行两次shouldParkAfterFailedAcquire，t2第一次
             执行shouldParkAfterFailedAcquire会把t1的waitStatus设置为-1，t2第二次执行shouldParkAfterFailedAcquire
             后会把自己park住，此时t1继续执行setHead后，刚好执行到if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0)，此时t1的waitStatus为-1小于0，肯定能进if，所以
            此时调用doReleaseShared会把t2唤醒，这种情况也没有问题。

            第二种情况：t1执行到了setHeadPropagate方法，并且执行了setHead(node)将自己设置为head，此时
            t4已经执行了releaseShared，假设doReleaseShared方法代码如下，没有了PROPAGATE相关设置：
            private void doReleaseShared() {
                for (;;) {
                    Node h = head;
                    if (h != null && h != tail) {
                        int ws = h.waitStatus;
                        if (ws == Node.SIGNAL) {
                            if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                                continue;
                            unparkSuccessor(h);
                        }
                         // 去除了PROPAGATE，当做没有PROPAGATE
                    }
                     if (h == head)
                        break;
                }
             }
             此时t4执行了doReleaseShared后，没有任何操作，t1的waitStatus还是0，这时候t1继续执行setHead后
             的操作：if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0)由于t1的waiStatus=0，此时if条件不满足，t1无法执行
            doReleaseShared方法，t1的setHeadPropagate方法就执行完了，这时t4也已经执行完了doReleaseShared，
            此时t4的releaseShared完了，信号量的state=1了，t1和t4都没有unpark后续t2，此时t2正在执行第一次的shouldParkAfterFailedAcquire，
            这一次会把t1的waitStatus设置为-1，但是要执行第二次循环的时候，本应该第二次执行shouldParkAfterFailedAcquire
            将t2进行park，但是此时在循环中的tryAcquireShared中得到了返回值为1，t2执行完setHeadPropagate后正常返回了。

            如果doReleaseShared方法有PROPAGATE相关设置：
            private void doReleaseShared() {
                for (;;) {
                    Node h = head;
                    if (h != null && h != tail) {
                        int ws = h.waitStatus;
                        if (ws == Node.SIGNAL) {
                            if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                                continue;
                            unparkSuccessor(h);
                        }
                        else if (ws == 0 &&
                                !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                            continue;
                    }
                    if (h == head)
                        break;
                }
            }
            此时t4执行了doReleaseShared时，会执行compareAndSetWaitStatus(h, 0, Node.PROPAGATE)，将head也就是t1的
            waitStatus设置为了-3，这时候t1继续执行setHead后
             的操作：if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0)由于t1的waiStatus=-3，此时if条件满足，将要执行doReleaseShared方法，
            此时t2刚好执行两次shouldParkAfterFailedAcquire方法，把t1的waitStatus设置为了-1，t2自己也park住了，
            此时t1执行doReleaseShared方法时，刚好可以把t2唤醒。

            第三种情况：t1执行到了setHeadPropagate方法，并且执行了setHead(node)将自己设置为head，此时
            t4还没有执行releaseShared方法，t1的waitStatus还是0，t2还没有park，这时t1执行if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0)发现条件不满足，直接返回了。这个时候t2继续执行两次shouldParkAfterFailedAcquire方法
            将t1的waitStatus设置为-1，并将自己park住，这时候t4执行releaseShared方法，唤醒t2.

            假设t4执行了releaseShared方法，t1的waitStatus变成-3，t2还没有park，这时t1执行if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0)发现条件满足继续执行doReleaseShared方法，这个时候t2继续执行两次shouldParkAfterFailedAcquire方法
            将t1的waitStatus设置为-1，并将自己park住，这是t1继续执行doReleaseShared中的unparkSuccessor依然可以唤醒t2。

            PROPAGATE就是更快速更高效的唤醒后继结点：
            第一种情况：
            t4在doReleaseShared的时候发现t1的waitStatus=0，就会将t1的waitStatus改为-3，此时t1可以满足if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0)执行doReleaseShared方法，这时候t2执行两次shouldParkAfterFailedAcquire，将
            t1的waitStatus设置为-1，并park自己，此时t1在执行doReleaseShared的时候就可以把t2唤醒；也可以t1先unpark t2，t2park自己的
            时候就会没有用。

            参考：
            - https://www.zhihu.com/question/295925198/answer/1622051796
            - http://go4ward.top/2019/07/20/AQS%E5%85%B1%E4%BA%AB%E6%A8%A1%E5%BC%8F/

            // TODO 还是没搞明白PROPAGATE
         */
        /**
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate
         * 表示下一次共享式同步状态获取将会无条件的被传播下去。
         */
        static final int PROPAGATE = -3;

        /**
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be)
         *               blocked (via park), so the current node must
         *               unpark its successor when it releases or
         *               cancels. To avoid races, acquire methods must
         *               first indicate they need a signal,
         *               then retry the atomic acquire, and then,
         *               on failure, block.
         *   CANCELLED:  This node is cancelled due to timeout or interrupt.
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  This node is currently on a condition queue.
         *               It will not be used as a sync queue node
         *               until transferred, at which time the status
         *               will be set to 0. (Use of this value here has
         *               nothing to do with the other uses of the
         *               field, but simplifies mechanics.)
         *   PROPAGATE:  A releaseShared should be propagated to other
         *               nodes. This is set (for head node only) in
         *               doReleaseShared to ensure propagation
         *               continues, even if other operations have
         *               since intervened.
         *   0:          None of the above
         *
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified using CAS
         * (or when possible, unconditional volatile writes).
         * 取值为上面的1， -1， -2， -3 或者0
         * 大于0表示此线程取消了等待
         *
         * 初始状态为0
         */
        volatile int waitStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueuing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         * 阻塞队列双向链表
         * 前驱结点，当节点加入同步队列时被设置
         */
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned during enqueuing, adjusted
         * when bypassing cancelled predecessors, and nulled out (for
         * sake of GC) when dequeued.  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.  The next field of cancelled nodes is set to
         * point to the node itself instead of null, to make life
         * easier for isOnSyncQueue.
         * 阻塞队列双向链表
         * 后继结点
         */
        volatile Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         * 线程
         */
        volatile Thread thread;

        /**
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         * 条件队列单向链表
         * 下一个结点
         * 等待队列中的后继节点，如果当前结点是共享的，这个字段是一个SHARED常量，
         * 也就是节点类型（独占和共享）和等待队列中的后继节点共用同一个字段。
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     * 头结点，可以当做当前持有锁的线程
     */
    private transient volatile Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     * 尾结点，每个新来的结点都插入到最后，形成一个链
     */
    private transient volatile Node tail;

    /**
     * The synchronization state.
     * 当前锁的状态
     * 0 代表没有被占用
     * 大于0代表线程持有当前锁，锁可以重入，所以state可以大于1
     *
     * 管程中的共享变量
     *
     * Semaphore
     * 在Semaphore中表示资源的个数
     */
    private volatile int state;

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     * @param newState the new state value
     *                 没有竞争的时候，比如释放锁的时候，可以直接使用该方法，
     *                 有竞争的时候，比如获取锁的时候，可以使用compareAndSetState方法
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     *         value was not equal to the expected value.
     *         使用cas设置state，可以保证设置的原子性
     *         有竞争的时候，可以使用该方法，比如获取锁的时候
     *         无竞争的时候，可以使用setState方法，比如释放锁的时候
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * Inserts node into queue, initializing if necessary. See picture above.
     * @param node the node to insert
     * @return node's predecessor
     * 自旋加CAS将指定节点入队列，可能是节点入空队列，也可能是结点入一个已经有元素的队列
     */
    private Node enq(final Node node) {
        /**
         * 走到这里有两种情况：
         * 1. tail == null 说明队列是空的
         * 2. CAS失败，说明有线程竞争入队列
         *
         * 还有一种情况是条件变量的signal方法，将等待队列中的结点转移到同步队列中去
         * 不管什么情况，这里使用自旋加CAS让结点入队列，一定会入队列
         */
        for (;;) {
            // 队列尾结点
            Node t = tail;
            // tail为null，说明队列为空
            if (t == null) { // Must initialize
                /**
                 * 初始化node结点，原来的head和tail初始化的时候是null
                 * 使用CAS初始化，如果CAS成功了，说head结点初始化成功了，此时head结点的
                 * waitStatus == 0，head和tail都指向新初始化的节点
                 *
                 * 此时并没有直接将当前结点node入队列，而是继续自旋，等下一次循环再入队列，
                 * 因为由于并发原因，到这里的时候tail并不一定是之前的tail了，可能已经有
                 * 其他线程入队列了，所以要自旋，重新获取tail
                 */
                if (compareAndSetHead(new Node()))
                    tail = head;
            }
            // 走到这里说明队列中至少一个初始化的节点或者已经有其他结点在队列中了，可以将node入队了
            else {
                /**
                 * 这里还是使用CAS入队列尾
                 * 循环一直尝试，如果成功就返回，不成功就继续自旋
                 */
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     *
     * 把线程包装成Node，同时添加到队列中
     *
     * 主要逻辑有两部分：
     * 1. 阻塞队列的尾结点为null，调用enq()插入；
     * 2  阻塞队列的尾结点不为null，采用尾插入队（compareAndSetTail()）。
     *
     * 如果compareAndSetTail失败了怎么办？就会继续进入到enq()方法进行操作。
     * 一般在CAS操作后，会继续进行自旋进行重试。因此可以判断：
     * enq()方法有两个作用：
     * 1. 处理当前阻塞队列尾结点为null时的入队操作；
     * 2. 如果CAS尾插失败后，负责自旋进行尝试。
     *
     * 入队列之后，在阻塞队列中的节点会做什么事情来保证自己能够有机会获得
     * 独占锁？这件事情就是acquireQueued()方法的事情了，进行排队获取锁。
     *
     * Semaphore信号量
     * 线程获取不到信号量，就会将当前线程封装成一个结点入同步队列，此时结点
     * 的类型是SHARED。
     * 入队操作就是在将当前结点加入到队列尾tail后面，先看队列尾tail是不是
     * null，如果tail不是null，说明队列中有排队的其他线程的节点，将当前
     * 结点的前驱结点设置为tail，使用CAS设置当前结点为tail，如果设置成功
     * 就可以返回。
     *
     * 如果CAS设置当前结点为tail失败，说明有其他线程先入队列了，需要继续调
     * 用enq方法再次入队列。
     *
     * 另外在方法刚开始判断tail为null的时候，说明队列是空的，此时也需要使用
     * enq方法进行入队列。
     *
     * enq方法就是使用自旋方式加CAS将结点入队列。
     *
     * ReentrantLock可重入锁
     * 线程获取不到锁，将当前线程封装成一个独占模式的结点，加入到同步队列中去。
     *
     * cas入队或者自旋加cas入队。
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        /**
         * 下面是想把当前node加到链表的最后面去，
         * 也就是进入到阻塞队列的最后
         */
        Node pred = tail;
        /**
         * tail != null表示队列不为空
         * tail == head的时候表示队列是空的
         */
        if (pred != null) {
            // 设置node的前驱为当前的队尾结点
            node.prev = pred;
            // CAS把node设置为队尾，如果成功了tail就等于node了
            if (compareAndSetTail(pred, node)) {
                /**
                 * 走到这里说明设置成功了，node已经加到队尾了
                 * 可以返回了
                 */
                pred.next = node;
                return node;
            }
        }
        /**
         * 走到这里有两种情况：
         * 1. tail == null 说明队列是空的
         * 2. CAS失败，说明有线程竞争入队列
         */
        enq(node);
        return node;
    }

    /**
     * Sets head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * Wakes up node's successor, if one exists.
     *
     * @param node the node node是head结点
     * 唤醒后继结点
     */
    private void unparkSuccessor(Node node) {
        /**
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         * head结点的waitStatus小于0，就将其修改为0
         */
        int ws = node.waitStatus;
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);

        /**
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         * 下面代码唤醒后继结点，但是有可能后继结点已经取消了等待
         * 所有要从队尾往前找，找到waitStatus小于等于0的所有节点中排在
         * 最前面的
         */
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        // 唤醒线程，唤醒后，从被挂起的位置继续执行
        if (s != null)
            LockSupport.unpark(s.thread);
    }

    /**
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     *
     * Semaphore信号量
     * Semaphore的实现中，会先释放许可，然后调用此方法唤醒后继线程，
     * 释放线程的节点是head节点，需要唤醒的是head的后继结点
     *
     * h == null 队列为空，不需要唤醒后继结点
     * h == tail 说明阻塞队列中没有其他后继结点需要唤醒了
     *
     * 如果head的waitStatus是SIGNAL，说明后继结点需要被唤醒，接下来就先将
     * head的waitStatus设置为0，接着唤醒head的后继结点（unparkSuccessor方法）。
     *
     * 0是个中间状态
     *
     * 如果head的waitStatus == 0 说明head的后继已被唤醒或者即将被唤醒，中间状态
     * 也即将消失。
     *
     */
    private void doReleaseShared() {
        /*
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         */
        for (;;) {
            Node h = head;
            /**
             * 1. h == null 说明阻塞队列为空
             * 2. h == tail 说明头结点可能是刚刚初始化的头结点或者是普通线程结点，
             *    但此节点既然是头结点，就代表已经被唤醒了，阻塞队列没有其他结点了
             * 所以上面两种情况不需要进行唤醒后继结点
             */
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    // 唤醒head的后继结点
                    unparkSuccessor(h);
                }
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * Sets head of queue, and checks if successor may be waiting
     * in shared mode, if so propagating if either propagate > 0 or
     * PROPAGATE status was set.
     *
     * @param node the node
     * @param propagate the return value from a tryAcquireShared
     *
     *                  Semaphore信号量
     *                  走到这里说明当前线程获取到了锁，需要将head指向当前线程，
     *                  这里propagate是当前线程获取到信号量资源后，信号量的剩余资源个数。
     *                  Semaphore实现中如果能到这个方法，propagate肯定是大于等于0的。
     *
     *                  这里先调用setHead方法，将head指向当前结点，然后继续下面的if判断，
     *                  而Semaphore到这里propagate肯定是大于等于0的，如果是大于0，说明
     *                  当前线程获取了信号量后，还有剩余资源，可以通知后面的线程进行资源的获取。
     *                  如果是等于0，说明当前线程获取信号量后，没有剩余资源可供获取了，在某些
     *                  情况下还是会继续唤醒后续线程，造成一些不必要的唤醒。
     *
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        // 旧的头结点
        Node h = head; // Record old head for check below
        // 将当前获取到锁的结点设置新的头结点
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         * 可能会造成不必要的唤醒
         *
         * 如果propagate大于0,（说明有其他空闲资源）
         * 或者旧的头结点为空
         * 或者旧的头结点的waitStatus小于0
         * 或者新的头结点为空
         * 或者新的头结点的waitStatus小于0
         * 这几种条件需要唤醒后继结点
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                // 可参考：
                // https://blog.csdn.net/anlian523/article/details/106319294
                // https://blog.csdn.net/anlian523/article/details/106319538
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * Cancels an ongoing attempt to acquire.
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        node.thread = null;

        // Skip cancelled predecessors
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        Node predNext = pred.next;

        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     * 没有抢到锁，就会到这里
     * 当前线程没有抢到锁，是否需要挂起当前线程
     *
     * 如果返回true，说明前驱结点的waitStatus == -1，是正常情况，
     * 当前线程需要挂起，等待以后被唤醒；
     * 如果返回false，说明不需要被挂起。
     *
     * 一般第一次进来的时候，不会返回true，而是返回false
     * 返回false的时候为什么不直接挂起？是因为有可能在经过这个方法后，
     * node已经是head的直接后继结点了，就不要再挂起了。
     *
     * Semaphore信号量
     * node是当前结点，pred是前驱结点
     * 该方法在线程获取不到信号量资源的时候调用，查看当前结点是否需要挂起等待
     *
     * 首先检查前驱结点的waitStatus，如果前驱结点的waitStatus是SIGNAL，说明
     * 当前结点仍在等待前驱结点唤醒，也就是前驱结点要么也在等待锁，要么已经获取到锁
     * 但还没释放锁，所以此时当前结点的线程是需要挂起的，直接返回true。
     *
     * 如果前驱结点的waitStatus大于0，也就是CANCELLED，目前只有CANCELLED是大于0的，
     * 说明前驱结点取消了等待（超时或者中断等），此时需要从前驱节点开始，找前驱的前驱，
     * 直到找到一个前驱结点的waitStatus不是取消的，将当前结点和这个节点关联起来。
     *
     * 如果前驱结点的waitStatus既不是SIGNAL，也不是CANCELLED，那就应该是0，-2，-3中的一种，
     * 而此时的操作是线程获取信号量不成功，便加入同步队列，到这里判断当前线程是否需要挂起，
     * 此时当前结点的前驱结点的waitStatus还是初始状态0，没有设置过，这里就使用CAS将
     * 前驱结点的waitStatus设置为SIGNAL，此时会直接返回false，并不会直接告诉前面一步
     * 可以挂起，而是继续返回去进行自旋，或许返回去后就能获取到锁了，如果获取不到，就会再
     * 执行该方法，返回true表示可以挂起
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        /**
         * 前驱结点waitStatus == -1 说明前驱结点状态正常
         * 当前线程需要挂起，直接返回true
         */
        if (ws == Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
        /**
         * 前驱结点 waitStatus 大于0，说明前驱结点取消了排队
         *
         * 进入阻塞队列排队的线程会被挂起，唤醒的操作是前驱结点完成的。
         *
         * 下面的循环就是从当前的结点的前驱往前找，找到一个没有被取消
         * 的结点作为前驱
         */
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */

            /**
             * 走到这里，说明前驱结点的waitStatus不等于-1和1，只能是0，-2，-3
             * 而每个新的node入队时，waitStatus都是0
             * 这里CAS将前驱结点的waitStatus设置为-1
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     * 挂起线程，等待被唤醒
     * 直接使用LockSupport.park方法进行挂起
     */
    private final boolean parkAndCheckInterrupt() {
        /**
         * 挂起线程，等待被唤醒
         *
         * 唤醒的时候，从这里开始执行，然后会回到acquireQueued方法
         * 继续进行循环，这时候node就是head节点了，就可以继续尝试获取锁。。。
         */
        LockSupport.park(this);
        return Thread.interrupted();
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting
     * 真正的挂起线程，然后被唤醒后去获取锁
     *
     * acquireQueued自旋过程中主要有两件事情：
     * 1. 如果当前结点的前驱结点是头结点，并且能够获取到同步状态，
     *    当前线程就能获得锁，该方法执行结束并返回；
     * 2. 如果获取锁失败的话，先将头结点设置为SIGNAL状态，然后调用
     *    LockSupprot.park()方法是当前线程挂起等待。
     *
     * ReentrantLock可重入锁
     * 上一步是将当前线程加入同步队列，这里是将线程进行挂起操作
     *
     * 挂起之前会再次尝试下能不能获取到锁，获取不到再挂起。
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                // 当前节点的前驱结点（需要先熟悉CLH队列）
                final Node p = node.predecessor();
                /**
                 * p == head 说明当前节点的前驱结点持有锁，当前结点可以尝试获取下锁，
                 * 如果获取到了，设置head指向当前结点，然后就返回。
                 *
                 * 获取不到锁可能原因有：
                 * 1. 当前节点前驱结点还持有锁
                 * 2. 锁被其他线程抢先占有了，非公平模式下可能会出现这样的情况
                 */
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }

                /**
                 * 走到这里，说明还是没有获取到锁
                 *
                 * shouldParkAfterFailedAcquire 当前线程没有抢到锁，是否需要挂起当前线程
                 * 如果返回true，说明前驱结点的waitStatus == -1，是正常情况，
                 * 当前线程需要挂起，等待以后被唤醒；
                 * 如果返回false，说明不需要被挂起
                 *
                 * parkAndCheckInterrupt 挂起线程，停在这里等待被唤醒
                 */
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared uninterruptible mode.
     * @param arg the acquire argument
     * 退出条件是：当前结点的前驱结点是头结点，
     * 并且tryAcquireShared(arg)返回值大于等于0
     * 获取锁成功，才能返回
     *
     *            Semaphore信号量不可用时使用该方法将当前线程入队列并等待信号量可用。
     *            这里的addWaiter就是将当前线程加入同步队列中，后续的操作以及parkAndCheckInterrupt
     *            就是阻塞等待信号量可用，也就是等待信号量的release方法（V操作）调用。
     *
     *            首先是addWaiter方法，将当前线程加入同步队列，队列是CLH锁队列的变种。
     *            Semaphore是一种共享锁，所以这里加入同步队列的结点是SHARED类型。
     *
     *            接下来的操作是在阻塞等待之前，会争取看看下能不能直接获取到信号量，
     *            如果能获取到信号量就可以直接返回，不需要阻塞等待了；如果获取不到信号量
     *            则阻塞等待。
     *
     */
    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                /*
                    Semaphore信号量
                    竞争锁的线程封装成CLH结点，加入完队列后，在阻塞等待操作之前，
                    会先确认下这时能不能获取到信号量，如果能获取到就不用进行阻塞等待了，
                    如果获取不到信号量，才进行阻塞等待。相当于最后再做一次挣扎。

                    这里node.predecessor()获取到前驱结点，CLH原始设计中，是使用前驱结点
                    的状态来控制当前结点是否能获取到锁的，在AQS中的CLH设计中，也是使用前驱结点，
                    但是不是使用前驱结点状态，而是看前驱结点是不是head结点，如果前驱结点是head
                    结点，则说明前驱结点持有锁，或者是前驱结点刚刚持有但是现在已经释放了锁。

                    如果前驱结点是head结点，则当前结点可以尝试获取锁，如果获取不到，说明前驱结点
                    还在持有锁或者锁被其他线程抢去（非公平模式下可能会发生），获取不到就会继续往下
                    执行，看是不是要将线程阻塞；如果获取到了锁，也就是r >= 0，则说明获取到了信号量
                    资源，需要设置head指向自己，并检查后继节点的状态，这一步是在setHeadAndPropagate
                    方法中做的，后续是对中断的设置，这样当前线程就获取到了信号量。

                    如果前驱结点不是head结点，则看下当前结点是否需要阻塞等待，如果需要阻塞等待，就阻塞；
                    如果不需要就继续自旋。
                    shouldParkAfterFailedAcquire方法看是否需要阻塞，
                    parkAndCheckInterrupt方法执行阻塞
                 */

                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                /*
                    Semaphore信号量
                    如果当前结点的前驱结点的waitStatus是SIGNAL，说明前驱结点还在等待锁，或者前驱结点
                    已经获取到锁，但还没释放锁，此时就需要进行挂起等待，挂起操作在parkAndCheckInterrupt方法中。
                    直接使用LockSupport.park方法进行挂起线程。
                    到这里获取不到信号量的线程就挂起了，等待前驱结点通知唤醒当前线程。
                 */
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared interruptible mode.
     * @param arg the acquire argument
     * 获取共享锁，该方法可中断
     */
    private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
        // 入队
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    // Main exported methods

    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     *         been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     * 独占式获取同步状态，实现该方法需要查询当前状态并判断同步状态是否符合预期，
     * 然后进行CAS设置同步状态。
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     *         state, so that any waiting threads may attempt to acquire;
     *         and {@code false} otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     * 独占式释放同步状态，等待获取同步状态的线程将有机会获取同步状态。
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     *         mode succeeded but no subsequent shared-mode acquire can
     *         succeed; and a positive value if acquisition in shared
     *         mode succeeded and subsequent shared-mode acquires might
     *         also succeed, in which case a subsequent waiting thread
     *         must check availability. (Support for three different
     *         return values enables this method to be used in contexts
     *         where acquires only sometimes act exclusively.)  Upon
     *         success, this object has been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     * 共享式的获取同步状态，返回大于等于0的值，表示获取成功，反之获取失败。
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     * 共享式释放同步状态
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return {@code true} if synchronization is held exclusively;
     *         {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     * 当前同步器是否在独占模式下被线程占用，一般该方法表示是否被当前线程所独占。
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * 独占式获取同步状态，如果当前线程获取同步状态成功，则由该方法返回，
     * 否则会进入同步队列等待，该方法将会调用重写的tryAcquire方法。
     *
     * 如果tryAcquire成功了，直接就结束了
     * 否则acquireQueued方法会将线程压到队列中
     *
     *            ReentrantLock可重入锁
     *            可重入锁的获取锁的方法，相当于操作系统的互斥锁的加锁操作。
     *            先尝试获取锁，如果获取不到就将当前线程加入同步队列。
     *
     *            获取不到锁的情况：
     *            1. 非公平模式下，锁被其他线程占有，当前线程获取不到锁
     *            2. 公平模式下，锁被其他线程占有，当前线程获取不到锁
     *            3. 公平模式下，锁没有被其他线程占有，但是同步队列中有排队的线程
     *
     *            addWaiter将线程以独占模式加入同步队列
     *            acquireQueued加入队列后再尝试获取锁，如果获取不到将线程挂起阻塞等待
     */
    public final void acquire(int arg) {
        /**
         * 先试一下，如果能直接获取到锁，就直接结束
         * 没有成功，需要把当前线程挂起，放到阻塞队列中
         *
         * addWaiter将结点包装成node，入队列，经过addWaiter
         * 后，就已经进入阻塞队列了。
         *
         * 如果acquireQueued返回true的话，就会进入selfInterrupt，
         * 所以正常情况下应该返回false。
         *
         * acquireQueued方法真正的挂起线程，被唤醒后去获取锁，
         * 都在这个方法里
         */
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     * 与acquire相同，但是该方法响应中断，当前线程未获取到同步状态而进入同步队列中，
     * 如果当前线程被中断，则该方法会抛出InterruptedException并返回。
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     * 在acquireInterruptibly基础上增加了超时限制，如果当前线程在超时时间
     * 内没有获取到同步状态，则返回false，如果获取到则返回true。
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
            doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     * 释放锁
     * 先尝试释放锁，释放成功，就唤醒同步队列中的后继线程
     *
     * ReentrantLock可重入锁，由于锁是可重入的，如果不是最后一次释放锁，tryRelease
     * 就会返回false，此时就不会唤醒同步队列中的后继线程，需要等待重入的锁都释放了，
     * 才会唤醒同步队列中的后继线程。
     *
     */
    public final boolean release(int arg) {
        /**
         * 尝试释放锁，释放成功后唤醒后继结点
         */
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                // 唤醒后继线程
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * 共享式的获取同步状态，如果当前线程未获取到同步状态，将会进入同步队列等待，
     * 与独占获取的主要区别是同一时刻可以有多个线程获取到同步状态。
     *
     *            Semaphore信号量的acquire方法（P操作）使用该方法进行实现，先回顾下
     *            信号量的P操作流程：先将值减1，判断如果值小于0，则将当前线程加入同步队列，
     *            并将当前线程进行阻塞；如果值大于等于0，则p操作结束。
     *
     *            这里的实现也是如此，tryAcquireShared是将值减1，判断值是否小于0，
     *            如果小于0，doAcquireShared方法将线程加入同步队列，当然这只是大概步骤，
     *            实际的实现比这要复杂，但是原理大概就是这样。
     *
     *            tryAcquireShared方法是信号量p操作的将值减1，这个方法在Semaphore的
     *            公平和非公平同步器中都有实现，逻辑基本上一样。
     *
     *            tryAcquireShared在Semaphore的公平同步器中实现逻辑是，先判断如果队列
     *            中有已经在等待获取信号量的线程，则当前线程肯定也得等待，这时tryAcquireShared
     *            直接返回-1；如果没有队列等待，则使用CAS进行减1操作，然后将减一后的值返回，
     *            这里会做是否小于0的判断。
     *
     *            tryAcquireShared在Semaphore的公平同步器中实现逻辑是，无需判断队列中
     *            有没有等待线程，直接使用CAS进行减1操作，然后将减一后的值返回，这里会做是否小于
     *            0的判断。
     *
     *            如果tryAcquireShared大于等于0，说明信号量可以使用，P操作结束；如果小于0，
     *            说明信号量不可用，需要将当前线程加入同步队列阻塞等待信号量可用。doAcquireShared
     *            方法就是用来入队列、等待操作的。
     *
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     * 与acquireShared相同，但是该方法响应中断。
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        /**
         * CountDownLatch的tryAcquireShared 只有当state == 0的时候才会返回1，其他的返回-1
         * 当调用CountDownLatch的await方法的时候，state大于0，此时if条件为true
         */
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     * 在acquireSharedInterruptibly基础上增加了超时时间的限制。
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
            doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     * 共享式的释放同步状态
     *
     * Semaphore信号量
     * 信号量中用来释放许可（V操作）
     * tryReleaseShared释放许可，也即是v操作中的将变量加1，Semaphore中使用
     * 自旋加CAS操作，所以Semaphore的tryReleaseShared一定会返回true，
     * 许可释放后，紧接着就需要唤醒队列中等待的后继线程了，方法是doReleaseShared。
     */
    public final boolean releaseShared(int arg) {
        /**
         * 只有state为0的时候，tryReleaseShared才返回true
         * 否则就state = state - 1
         *
         * 等到state为0的时候，执行doReleaseShared方法
         */
        if (tryReleaseShared(arg)) {
            // 唤醒await的线程
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    /**
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     *  <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread() &&
     * hasQueuedThreads()}</pre>
     *
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     *  <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     *         current thread, and {@code false} if the current thread
     *         is at the head of the queue or the queue is empty
     * @since 1.7
     * 查看同步队列中是否还有排队线程
     */
    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        return h != t &&
            ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     * 获取等待在同步队列上的线程集合。
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q  = hasQueuedThreads() ? "non" : "";
        return super.toString() +
            "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     * 判断结点是否已经转移到阻塞队列中了
     *
     * 条件队列的结点在初始化的时候，waitStates = CONDITION
     * signal的时候需要将结点从条件队列转移到阻塞队列
     */
    final boolean isOnSyncQueue(Node node) {
        /**
         * waitStatus == CONDITION 说明在条件队列
         * 如果node的前驱prev为null，说明没有在阻塞队列中
         */
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        /**
         * 结点的后继next如果不为null
         * 那肯定就在阻塞队列中了
         */
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /**
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         *
         * 这个方法会从阻塞队列队尾往前遍历，如果找到相等的，说明在阻
         * 塞队列，否则就不在阻塞队列。
         *
         * 不能通过prev != null来判断node是否在阻塞队列 ：
         * AQS入队方法，首先设置node.prev指向tail，
         * 然后CAS设置自己为新的tail，但是CAS可能会失败
         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     * 将结点从条件队列转到阻塞队列中
     * true 表示转移成功
     * false 表示signal之前结点已经取消了
     */
    final boolean transferForSignal(Node node) {
        /**
         * If cannot change waitStatus, the node has been cancelled.
         * CAS失败，说明waitStatus不是CONDITION了，结点已经取消
         * 方法返回false，继续转一下一个结点
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /**
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         * 自旋进入阻塞队列的队尾
         * 返回值p是node在阻塞队列中的前驱结点
         */
        Node p = enq(node);
        int ws = p.waitStatus;
        /**
         * 阻塞队列中前驱结点的ws大于0，说明阻塞队列中前驱结点取消了等待
         * 直接唤醒node对应的线程
         *
         * 如果ws小于等于0，会调用cas设置状态，因为结点入队后，需要把前驱结点
         * 的状态修改为SIGNAL
         *
         * 如果前驱结点取消或者CAS失败，会唤醒线程
         */
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * Returns true if thread was cancelled before being signalled.
     *
     * @param node the node
     * @return true if cancelled before the node was signalled
     */
    final boolean transferAfterCancelledWait(Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     * @param node the condition node for this wait
     * @return previous sync state
     *
     * 完全释放掉锁，锁可以重入，所以要使用saveState，而不是1
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            int savedState = getState();
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     * 每个条件变量都会对应一个ConditionObject
     * 条件变量也会对应有一个等待队列，用来存放等待这个条件变量的所有线程
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * First node of condition queue.
         * 条件队列的第一个结点
         */
        private transient Node firstWaiter;
        /**
         * Last node of condition queue.
         * 条件队列的最后一个结点
         */
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() { }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         * @return its new wait node
         * 将当前线程添加到条件队列中
         * 入队，插入队尾
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            // 如果条件队列最后一个结点取消了，需要清除出去
            if (t != null && t.waitStatus != Node.CONDITION) {
                // 该方法会遍历整个条件队列，将已取消的所有节点清除出去
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         * 从条件队列的队头往后遍历，找出第一个需要转移的node
         * 有些线程会取消排队，但是还在队列中
         */
        private void doSignal(Node first) {
            do {
                /**
                 * firstWaiter指向first结点后面的第一个
                 * 如果将队列头移除后，后面没有结点等待，需要将lastWaiter设置为null
                 */
                if ( (firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         * @param first (non-null) the first node on condition queue
         *              挨个将条件等待队列中的结点转移到同步队列中去
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         * 清除等待队列中已经取消的结点
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                // waitStatus不是CONDITION的话，这个结点就是取消的
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                }
                else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         * 唤醒线程，转移到阻塞队列，通常由另外一个线程来操作
         * 唤醒等待了最久的线程，转移到阻塞队列中
         */
        public final void signal() {
            // 调用signal方法的时候，必须持有当前的独占锁
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /** Mode meaning to reinterrupt on exit from wait */
        private static final int REINTERRUPT =  1;
        /** Mode meaning to throw InterruptedException on exit from wait */
        private static final int THROW_IE    = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
            throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         * 条件变量的等待操作
         * 可被中断
         * 会阻塞，直到调用signal或signalAll方法
         */
        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            // 添加到条件变量对应的条件队列中，就是正常的入队列操作，
            // 不需要加锁，因为条件变量在使用的时候就已经是在获取锁之后了
            Node node = addConditionWaiter();
            // 释放锁，返回的是释放锁之前的state值
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            /**
             * 上面把线程加入到了条件变量对应的等待线程，并且释放掉了锁，
             * 下面会先看线程是否在同步队列，如果不在同步队列中，需要将线程挂起，
             * 等待条件变量的signal或者signalAll唤醒当前线程，并转移到同步队列。
             *
             * 不在阻塞队列的话，会自旋，挂起
             * 有两种情况会退出循环：
             * 1. 进入阻塞队列
             * 2. 线程中断了
             */
            while (!isOnSyncQueue(node)) {
                /**
                 * 线程挂起
                 *
                 * 线程被唤醒后，会进入阻塞队列，
                 * 需要从这边继续执行
                 */
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            /**
             * 被唤醒之后，线程将进入阻塞队列，等待获取锁
             * 这里会调用acquireQueued，先尝试获取锁，获取不到就挂起线程
             */
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                        expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
