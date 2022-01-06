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

package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 *
 * FutureTask是RunnableFuture的实现，既可以作为任务被执行，也可以作为一个异步计算的结果
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     */

    /*
        Future
        Future代表一个异步计算的结果。

        RunnableFuture
        RunnableFuture继承了Runnable和Future，可以类比生产者消费者模型，Runnable
        是生产者，通过run方法计算出结果；Future是消费者，通过get方法获取结果，如果获取
        不到结果就会阻塞等待。

        FutureTask
        FutureTask表示一个异步计算。

        当任务还没有完成，如果有线程通过get方法获取结果，这个线程会被封装成WaitNode并加入到
        Treiber栈中，线程挂起；任务执行完后，将栈中的线程唤醒，让其拿到结果返回。
     */

    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     * 任务执行的状态
     */
    private volatile int state;

    /**
     * 任务初始化状态
     */
    private static final int NEW          = 0;

    /**
     * 正在进行结果设置，是个中间状态
     */
    private static final int COMPLETING   = 1;

    /**
     * 任务正常完成后的状态
     */
    private static final int NORMAL       = 2;

    /**
     * 任务异常后的状态
     */
    private static final int EXCEPTIONAL  = 3;

    /**
     * 取消任务后的状态
     */
    private static final int CANCELLED    = 4;

    /**
     * 线程被中断的中间状态
     */
    private static final int INTERRUPTING = 5;

    /**
     * 线程被中断后的状态
     */
    private static final int INTERRUPTED  = 6;

    /**
     * The underlying callable; nulled out after running
     *
     * 当前要执行的任务
     */
    private Callable<V> callable;

    /**
     * The result to return or exception to throw from get()
     *
     * 当前任务执行的结果，如果发生异常则是对应的异常信息；如果任务被取消，则为null。
     *
     * 非volatile，由状态state来保证安全
     */
    private Object outcome; // non-volatile, protected by state reads/writes

    /**
     * The thread running the callable; CASed during run()
     *
     * 当前执行任务的线程
     */
    private volatile Thread runner;

    /** Treiber stack of waiting threads */
    /*
        Treiber stack
        是一个无锁并发栈，使用cas实现无锁算法。
        Treiber stack是一个使用单向链表来表示的栈，链表头是栈顶，出栈和入栈
        使用cas来保证下线程安全。

        这个栈用来保存使用Future.get方法阻塞的线程。每当有一个线程调用了Future.get
        方法获取任务执行结果，而此时任务还在运行中没有结束，调用get方法的线程就会加入到
        Treiber栈中，并将新的结点赋值给waiters。

     */
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     *
     * 返回任务执行的结果或者抛异常
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        // 任务执行结果
        Object x = outcome;
        // 如果是正常结束，直接返回结果
        if (s == NORMAL)
            return (V)x;
        // 任务被取消，抛出被取消异常
        if (s >= CANCELLED)
            throw new CancellationException();
        // 执行的异常，抛出执行中的异常
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        // 将Runnable类型任务转换成Callable类型任务
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    public boolean isDone() {
        return state != NEW;
    }

    /**
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this
     * task should be interrupted; otherwise, in-progress tasks are allowed
     * to complete
     * @return
     *
     * 尝试取消任务的执行
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 只有状态为NEW的才有可能会被取消成功
        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {    // in case call to interrupt throws exception
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                        t.interrupt();
                } finally { // final state
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            finishCompletion();
        }
        return true;
    }

    /**
     * @throws CancellationException {@inheritDoc}
     *
     * 获取任务执行结果，如果任务还没结束，获取结果的线程会阻塞等待
     *
     * 当一个线程调用get方法，获取任务的结果，如果任务还没有完成（正常、异常、中断），
     * 就会生成一个新的WaitNode，并加入等待队列，当前获取任务结果的线程挂起。
     */
    public V get() throws InterruptedException, ExecutionException {
        // 当前FutureTask的状态
        int s = state;
        // 状态如果是新建或者是正在完成，则继续等待，否则调用report方法返回结果
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        // 任务执行完成，返回结果
        return report(s);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     *
     * 获取任务执行结果，可设置超时时间，如果任务还没结束，获取结果的线程会阻塞等待，
     * 如果等待结果超时则抛异常
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        // 当前FutureTask的状态
        int s = state;
        // 状态必须是NEW或COMPLETING
        if (s <= COMPLETING &&
                // 等待任务完成
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();
        // 返回任务执行结果
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     *
     * 设置Future的结果信息
     */
    protected void set(V v) {
        // CAS将状态从NEW设置为中间状态：COMPLETING
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            // 设置结果
            outcome = v;
            // 将状态设置为任务正常完成状态：NORMAL
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            // 任务结束后，需要唤醒栈中等待获取结果的线程
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     *
     * 设置任务异常执行的结果
     */
    protected void setException(Throwable t) {
        // CAS将状态从NEW设置为中间状态：COMPLETING
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            // 设置结果
            outcome = t;
            // 将状态设置为任务异常完成状态：NORMAL
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            // 任务结束后，需要唤醒栈中等待获取结果的线程
            finishCompletion();
        }
    }

    /**
     * 执行任务逻辑的方法
     */
    public void run() {
        /*
            state状态有如下几种：
            - NEW，新建创建，未执行
            - COMPLETING，正在完成，是个中间状态
            - NORMAL，正常结束状态
            — EXCEPTIONAL，异常结束状态
            - CANCELLED，取消状态
            - INTERRUPTING，正在被中断
            - INTERRUPTED，

            如果state不是NEW状态，说明已经执行了，不需要再次执行，直接返回；
            如果state是NEW状态，但是cas设置当前线程为执行线程失败了，说明任务已经被其他线程
            执行了，不需要再次执行，直接返回。
         */
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            // 要执行的任务
            Callable<V> c = callable;
            // 再次判断下当前状态是不是NEW
            if (c != null && state == NEW) {
                // 任务执行的结果
                V result;
                // ran为ture表示任务正常运行结束
                boolean ran;
                try {
                    // 执行任务逻辑，拿到结果
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    /*
                        设置异常信息，包括如下几步：
                        1. cas设置state为COMPLETING中间状态
                        2. 设置异常信息给outcome
                        3. cas设置state为EXCEPTIONAL
                        4. 将waiters中所有等待获取结果的线程唤醒并移除
                     */
                    setException(ex);
                }
                // 任务正常执行结束
                if (ran)
                    /*
                        设置正常结束信息，包括如下几步：
                        1. cas设置state为COMPLETING中间状态
                        2. 设置结果给outcome
                        3. cas设置state为NORMAL
                        4. 将waiters中所有等待获取结果的线程唤醒并移除
                     */
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     *
     * Treiber栈中记录等待结果的线程的节点
     */
    static final class WaitNode {

        /**
         * 等待结果的线程
         */
        volatile Thread thread;

        /**
         * 下一个节点
         */
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     *
     * 任务完成后，唤醒栈中等待获取结果的线程
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    // 等待获取结果的线程
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        // 唤醒线程
                        LockSupport.unpark(t);
                    }
                    // 继续栈中的下一个线程
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        // 子类实现
        done();

        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     *
     * 等待任务执行完成
     */
    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        // 任务执行的截止时间
        final long deadline = timed ? System.nanoTime() + nanos : 0L;

        // 栈中等待获取结果的线程节点
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            // 当前FutureTask的状态
            int s = state;
            // 已经完成（正常或者异常或者中断），直接返回
            if (s > COMPLETING) {
                if (q != null)
                    // 等待节点的线程置为null
                    q.thread = null;
                // 返回结果
                return s;
            }
            // 正在完成状态，让出当前线程的时间片
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
            /*
                到这里说明当前任务状态还是NEW，需要将当前调用get的线程
                封装成一个WaitNode，并放到等待栈中去
             */
            else if (q == null)
                q = new WaitNode();
            /*
                这里将等待的线程放入到栈中
             */
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            else if (timed) {
                nanos = deadline - System.nanoTime();
                // 等待超时，返回
                if (nanos <= 0L) {
                    // 将节点从栈中移除
                    removeWaiter(q);
                    return state;
                }
                // 阻塞获取结果的线程
                LockSupport.parkNanos(this, nanos);
            }
            // 挂起当前调用get的线程
            else
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (;;) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                          q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
