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
 * Written by Doug Lea and Martin Buchholz with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * An unbounded thread-safe {@linkplain Queue queue} based on linked nodes.
 * 一个基于链接节点的无界的线程安全的队列。
 * This queue orders elements FIFO (first-in-first-out).
 * 此队列按照FIFO（先进先出）的顺序进行排序。
 * The <em>head</em> of the queue is that element that has been on the
 * queue the longest time.
 * 队列的head元素是在队列中时间最长的元素。
 * The <em>tail</em> of the queue is that element that has been on the
 * queue the shortest time. New elements
 * are inserted at the tail of the queue, and the queue retrieval
 * operations obtain elements at the head of the queue.
 * 队列的tail元素是在队列中时间最短的元素。新元素插入到队列的尾部，获取元素的操作在队列的头部进行。
 * A {@code ConcurrentLinkedQueue} is an appropriate choice when
 * many threads will share access to a common collection.
 * 当多个线程访问一个共享的集合时使用ConcurrentLinkedQueue是一个适当的选择。
 * Like most other concurrent collection implementations, this class
 * does not permit the use of {@code null} elements.
 * 和其他的并发集合实现一样，此队列不允许存储null元素。
 *
 * <p>This implementation employs an efficient <em>non-blocking</em>
 * algorithm based on one described in <a
 * href="http://www.cs.rochester.edu/u/michael/PODC96.html"> Simple,
 * Fast, and Practical Non-Blocking and Blocking Concurrent Queue
 * Algorithms</a> by Maged M. Michael and Michael L. Scott.
 * 此实现使用了一个高效的非阻塞算法，该算法基于Maged M. Michael和Michael L. Scott合著的
 * 《Simple, Fast, and Practical Non-Blocking and Blocking Concurrent Queue Algorithms》这篇论文
 * 中锁描述的算法。
 *
 * <p>Iterators are <i>weakly consistent</i>, returning elements
 * reflecting the state of the queue at some point at or since the
 * creation of the iterator.  They do <em>not</em> throw {@link
 * java.util.ConcurrentModificationException}, and may proceed concurrently
 * with other operations.  Elements contained in the queue since the creation
 * of the iterator will be returned exactly once.
 * 该队列的迭代器是弱一致的，迭代器返回的元素是在某个时刻队列的状态或者说是从迭代器创建之后的的状态。
 * 它们不会抛出ConcurrentModificationException异常，并且可能与其他的操作并发的进行。迭代器
 * 创建后队列中的元素只会被返回一次。
 *
 * <p>Beware that, unlike in most collections, the {@code size} method
 * is <em>NOT</em> a constant-time operation. Because of the
 * asynchronous nature of these queues, determining the current number
 * of elements requires a traversal of the elements, and so may report
 * inaccurate results if this collection is modified during traversal.
 * Additionally, the bulk operations {@code addAll},
 * {@code removeAll}, {@code retainAll}, {@code containsAll},
 * {@code equals}, and {@code toArray} are <em>not</em> guaranteed
 * to be performed atomically. For example, an iterator operating
 * concurrently with an {@code addAll} operation might view only some
 * of the added elements.
 * 需要注意的是，该队列和其他的大多数集合不一样，该队列的size方法不是一个常量时间的操作。因为
 * 该队列的异步特性决定了获取当前队列元素数量的时候需要进行一个遍历操作，所以如果队列在进行遍历
 * 操作的时候有其他的修改操作也在进行，size方法返回的数据可能是不准确的。
 * 另外，批量操作：addAll、removeAll、retainAll、containsAll、equals、toArray都不能保证原子的进行操作。
 * 列入，如果一个遍历操作和一个addAll操作同步时进行，迭代操作可能只能看到addAll添加的一部分元素。
 *
 * <p>This class and its iterator implement all of the <em>optional</em>
 * methods of the {@link Queue} and {@link Iterator} interfaces.
 * 该类和它的迭代器实现了Queue接口和Iterator接口的所有的可选方法。
 *
 * <p>Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * {@code ConcurrentLinkedQueue}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions subsequent to the access or removal of that element from
 * the {@code ConcurrentLinkedQueue} in another thread.
 * 内存一致性效果：和其他的并发集合一样，一个线程将对象写到ConcurrentLinkedQueue中的操作happen-before于
 * 后续其他的线程访问或移除该元素的操作。
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 * 此类是Java集合框架的一员。
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 *           基于链表的无界非阻塞队列，线程安全，基于CAS。
 *
 *           ConcurrentLinkedQueue基于Michael & Scott 非阻塞队列算法进行实现，做了一些修改。
 *
 *           ConcurrentLinkedQueue基于CAS，没有使用锁。
 *
 *           ConcurrentLinkedQueue有head结点和tail结点，每次元素入队列的时候，tail并不总是
 *           指向最后一个结点；出队列的时候head也并不总是指向第一个结点。
 *
 *           假设tail现在指向最后一个结点，此时tail.next==null，有新元素入队列的时候，
 *           会设置tail.next=新结点，tail并不会指向新的最后一个结点，而是等下一个元素入队列
 *           的时候修改tail的指向。
 *
 *           这样就不用每次都需要cas修改tail结点，而是每两次才需要cas修改tail结点，
 *           减少cas次数，提高效率。
 *
 *           假设head指向第一个结点，此时head.item不为null，此时直接将第一个结点中的元素返回，
 *           并设置第一个结点的item=null，下一次再有需要出队列一个元素的时候，此时head.item==null，
 *           需要将head后面的这个结点元素出队列，并将head指向刚才head的后面的后面的结点。
 *
 *           head也是通过减少cas的次数来提升效率。
 */
public class ConcurrentLinkedQueue<E> extends AbstractQueue<E>
        implements Queue<E>, java.io.Serializable {
    private static final long serialVersionUID = 196745693267521676L;

    /*
     * This is a modification of the Michael & Scott algorithm,
     * adapted for a garbage-collected environment, with support for
     * interior node deletion (to support remove(Object)).  For
     * explanation, read the paper.
     * 该实现是对Michael & Scott算法的改进，以适应具有垃圾回收机制的语言环境，
     * 并且支持内部节点的删除操作（为了支持remove(Object)）。
     * 更多的解释可以参考Michael & Scott的论文。
     *
     * Note that like most non-blocking algorithms in this package,
     * this implementation relies on the fact that in garbage
     * collected systems, there is no possibility of ABA problems due
     * to recycled nodes, so there is no need to use "counted
     * pointers" or related techniques seen in versions used in
     * non-GC'ed settings.
     * 和concurrent包中其他的非阻塞算法一样，该实现基于一个这样的事实：在有垃圾回收机制的情况下，
     * 即使出现了被回收的节点被重新使用的情况，也不会出现ABA问题，所以在实现上不需要使用计数指针或
     * 者其他的相关技术来防止ABA问题。
     *
     * The fundamental invariants are:
     * 基本的不变式如下：
     * - There is exactly one (last) Node with a null next reference,
     *   which is CASed when enqueueing.  This last Node can be
     *   reached in O(1) time from tail, but tail is merely an
     *   optimization - it can always be reached in O(N) time from
     *   head as well.
     * - 队列中始终只有一个next引用为null的节点，该节点是队列尾部节点（最后一个节点），
     *   当新节点入队时，队尾节点的next会使用CAS操作指向新节点。从tail访问队尾节点只需
     *   要O(1)复杂度的时间，不过tail仅仅只是一个优化，因为总是可以使用O(N)复杂度的时间
     *   从head开始来找到队尾。
     * - The elements contained in the queue are the non-null items in
     *   Nodes that are reachable from head.  CASing the item
     *   reference of a Node to null atomically removes it from the
     *   queue.  Reachability of all elements from head must remain
     *   true even in the case of concurrent modifications that cause
     *   head to advance.  A dequeued Node may remain in use
     *   indefinitely due to creation of an Iterator or simply a
     *   poll() that has lost its time slice.
     * - 队列中的元素都是一些节点，这些节点中保存的值不能为null，这些元素的节点一定可以从head
     *   开始被访问到。将节点中的值通过cas操作设置为null，就相当于将这个节点从队列中删除。
     *   即便是由于并发的修改导致了head节点变化，也能保证从head开始能遍历到所有的元素。一个
     *   已经出队列的节点可能会被迭代器继续无限期的持有，或者是被一个失去CPU时间片的poll()操作无限期持有。
     *
     * The above might appear to imply that all Nodes are GC-reachable
     * from a predecessor dequeued Node.  That would cause two problems:
     * - allow a rogue Iterator to cause unbounded memory retention
     * - cause cross-generational linking of old Nodes to new Nodes if
     *   a Node was tenured while live, which generational GCs have a
     *   hard time dealing with, causing repeated major collections.
     * However, only non-deleted Nodes need to be reachable from
     * dequeued Nodes, and reachability does not necessarily have to
     * be of the kind understood by the GC.  We use the trick of
     * linking a Node that has just been dequeued to itself.  Such a
     * self-link implicitly means to advance to head.
     * 上面的设计：已经出队列的节点仍然可以访问到（GC可达的）队列中所有的节点，这种情况会导致两个问题：
     * - 一个恶意的迭代器可能会导致一个节点即使已经出队列了，也无法被GC回收掉
     * - 会导致跨代连接问题，老年代的节点对新生代的节点的引用，会导致很难回收已经出队列的还在新生代的节点
     * 但是，我们只需要保证从已经出队列的节点能访问到没有被删除的节点（没有被删除的节点的值是不为null的，
     * 被删除的节点的值是为null的），并且这种可达性不需要同垃圾回收的可达性一样，我们将出队列的节点的next
     * 指针指向自己（self-link），当遍历过程中遇到这样的节点后，也就意味着遍历要重新从head节点开始。
     *
     * Both head and tail are permitted to lag.  In fact, failing to
     * update them every time one could is a significant optimization
     * (fewer CASes). As with LinkedTransferQueue (see the internal
     * documentation for that class), we use a slack threshold of two;
     * that is, we update head/tail when the current pointer appears
     * to be two or more steps away from the first/last node.
     * head和tail被设计为可滞后的。实际上，对它们的更新失败正是优化的关键（有更少的cas操作）。
     * 和LinkedTransferQueue一样，将滞后系数设置为2；也就是当head距离队列头的距离大于2或者tail距离队列尾
     * 的距离大于2时，才会尝试将head和tail更新到队列头或者队列尾。
     *
     * Since head and tail are updated concurrently and independently,
     * it is possible for tail to lag behind head (why not)?
     * 因为head和tail是相互独立的更新的，因此可能出现head跑到tail后面的情况
     *
     * CASing a Node's item reference to null atomically removes the
     * element from the queue.  Iterators skip over Nodes with null
     * items.  Prior implementations of this class had a race between
     * poll() and remove(Object) where the same element would appear
     * to be successfully removed by two concurrent operations.  The
     * method remove(Object) also lazily unlinks deleted Nodes, but
     * this is merely an optimization.
     * 使用cas将节点的值设置为null也表示当前节点从队列中移除掉了。迭代器会跳过这些
     * 节点的值为null的节点。在之前的实现中，poll()方法和remove(Object)方法会有竞争，
     * 会出现两个并发操作都会成功的删除同一个节点。remove(Object)方法同样也不是直接删除
     * 掉节点，而是lazily unlink被删除的节点，这也仅仅是一个优化。
     *
     * When constructing a Node (before enqueuing it) we avoid paying
     * for a volatile write to item by using Unsafe.putObject instead
     * of a normal write.  This allows the cost of enqueue to be
     * "one-and-a-half" CASes.
     * 当在入队之前新建一个节点时，我们通过使用Unsafe.putObject方法来进行普通的变量写操作，避免直接进行volatile写操作。
     * 这样入队操作的代价就变成了一个半的cas操作。
     *
     * Both head and tail may or may not point to a Node with a
     * non-null item.  If the queue is empty, all items must of course
     * be null.  Upon creation, both head and tail refer to a dummy
     * Node with null item.  Both head and tail are only updated using
     * CAS, so they never regress, although again this is merely an
     * optimization.
     * head和tail可能会指向值为null的元素。如果队列是空的，所有的元素都必须是null。
     * 在初始化队列时，head和tail都指向一个值为null的哨兵节点。head和tail只能使用cas进行更新，
     * 所以他们不会发生回退，尽管head和tail不过是一个优化而已
     */

    /**
     * 队列中的结点
     * @param <E>
     */
    private static class Node<E> {
        /**
         * 存放元素
         * volatile类型
         * ConcurrentLinkedQueue使用减少CAS次数来提升出队列的效率，
         * head结点不总是指向第一个结点，在更新head结点指向时也需要根据item
         * 是否为null来作为判断条件，所以这里使用volatile来确保item的可见性
         */
        volatile E item;

        /**
         * 结点的后继结点
         * volatile类型
         * ConcurrentLinkedQueue使用减少CAS次数来提升入队列的效率，
         * tail结点不总是指向最后一个结点，在更新tail结点指向时也需要根据next
         * 是否为null来作为判断条件，所以在这里使用volatile来确保next的可见性
         */
        volatile Node<E> next;

        /**
         * Constructs a new node.  Uses relaxed write because item can
         * only be seen after publication via casNext.
         * item是一个volatile类型的变量，在一个元素要入队列之前，需要先新建一个结点，
         * 此时结点新建和设置item并没有竞争，写item时只需要作为一个普通的变量写入，
         * 无需使用volatile类型，无需使用内存屏障。此时直接使用了UNSAFE.putObject
         * 方法来将一个volatile类型的变量以普通变量方式进行更新，不涉及到内存屏障，
         * 提升性能。
         */
        Node(E item) {
            UNSAFE.putObject(this, itemOffset, item);
        }

        /**
         * cas更新item，在出队列时，将元素设置为null的时候使用
         * @param cmp
         * @param val
         * @return
         */
        boolean casItem(E cmp, E val) {
            return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
        }

        /**
         * 使用这个方法更新next，不保证可见性。
         * 使用UNSAFE.putOrderedObject方法来更新next，此方法设置完对应值后，不保证可见性，
         * 通常用在volatile类型的字段上。
         *
         * 在对volatile字段更新时，为了保证可见性，会使用到StoreLoad内存屏障，也就是最重的那个
         * 内存屏障，而使用UNSAFE.putOrderedObject则只使用到StoreStore内存屏障，比StoreLoad
         * 内存屏障性能更好。但是需要牺牲可见性，更新后可能不能马上被别的线程可见
         * @param val
         */
        void lazySetNext(Node<E> val) {
            UNSAFE.putOrderedObject(this, nextOffset, val);
        }

        /**
         * cas方式更新next
         * @param cmp
         * @param val
         * @return
         */
        boolean casNext(Node<E> cmp, Node<E> val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        // Unsafe mechanics

        private static final sun.misc.Unsafe UNSAFE;
        private static final long itemOffset;
        private static final long nextOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = Node.class;
                itemOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("item"));
                nextOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * A node from which the first live (non-deleted) node (if any)
     * can be reached in O(1) time.
     * 如果head节点未删除，可以使用O(1)复杂度的时间访问到
     * Invariants:
     * head节点的不变式：
     * - all live nodes are reachable from head via succ()
     * - 可以通过head的succ()方法访问所有的存活节点
     * - head != null
     * - head节点不会为null
     * - (tmp = head).next != tmp || tmp != head
     * - head的next不能指向自身
     * Non-invariants:
     * head的可变式：
     * - head.item may or may not be null.
     * - head节点的元素可以为null也可以不为null
     * - it is permitted for tail to lag behind head, that is, for tail
     *   to not be reachable from head!
     * - 允许head在tail的后面，也就是从head开始不能找到tail
     *
     *   头结点
     *   头结点并不总是队列中的第一个结点，不是每次出队列都更新head结点，减少cas操作
     */
    private transient volatile Node<E> head;

    /**
     * A node from which the last node on list (that is, the unique
     * node with node.next == null) can be reached in O(1) time.
     * 尾节点（也是队列中唯一一个next指向null的节点），可以使用O(1)复杂度时间访问到
     * Invariants:
     * tail的不变式：
     * - the last node is always reachable from tail via succ()
     * - 最后一个节点总是可以通过tail的succ()方法访问到
     * - tail != null
     * - tail不为null
     * Non-invariants:
     * tail的可变式：
     * - tail.item may or may not be null.
     * - tail节点的值可为null也可不为null
     * - it is permitted for tail to lag behind head, that is, for tail
     *   to not be reachable from head!
     * - tail可以在head的前面，也就是从head开始不能找到tail
     * - tail.next may or may not be self-pointing to tail.
     * - tail的next可能会指向自己
     *
     * 尾节点
     * 尾节点并不一定是链表的最后一个节点
     *
     * tail并不总是指向队列的尾节点
     *
     * 在cas添加一个新的节点后可以立即将尾节点也cas更新一下，让tail始终都是指向尾节点，但是为什么没有这么做呢？
     *
     * 如果有大量的入队操作，每次都需要cas方式来更新tail指向的节点，当数据量很大的时候对性能影响很大。
     * 所以offer中的实现，减少cas操作来提高大数量的入队操作的性能：每间隔一次进行cas操作更新tail指向尾节点。
     * （但是距离越来越长带来的负面效果就是每次入队时定位尾节点的时间就越长，因为循环体需要多循环一次来定位
     * 出尾节点）
     */
    private transient volatile Node<E> tail;

    /**
     * Creates a {@code ConcurrentLinkedQueue} that is initially empty.
     * 初始化的时候新建一个结点，head和tail都指向新建的结点
     */
    public ConcurrentLinkedQueue() {
        head = tail = new Node<E>(null);
    }

    /**
     * Creates a {@code ConcurrentLinkedQueue}
     * initially containing the elements of the given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public ConcurrentLinkedQueue(Collection<? extends E> c) {
        Node<E> h = null, t = null;
        for (E e : c) {
            checkNotNull(e);
            Node<E> newNode = new Node<E>(e);
            if (h == null)
                h = t = newNode;
            else {
                t.lazySetNext(newNode);
                t = newNode;
            }
        }
        if (h == null)
            h = t = new Node<E>(null);
        head = h;
        tail = t;
    }

    // Have to override just to update the javadoc

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never throw
     * {@link IllegalStateException} or return {@code false}.
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     * 添加一个元素到队列中，由于队列是无界的队列，所以该方法不会抛异常或返回false
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Tries to CAS head to p. If successful, repoint old head to itself
     * as sentinel for succ(), below.
     */
    final void updateHead(Node<E> h, Node<E> p) {
        if (h != p && casHead(h, p))
            h.lazySetNext(h);
    }

    /**
     * Returns the successor of p, or the head node if p.next has been
     * linked to self, which will only be true if traversing with a
     * stale pointer that is now off the list.
     * 返回p的后继结点
     */
    final Node<E> succ(Node<E> p) {
        // p的后继结点
        Node<E> next = p.next;
        // 如果p的后继结点指向p自己，则返回head
        return (p == next) ? head : next;
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never return {@code false}.
     * 将指定的元素插入到队列的尾部。
     * 由于队列是无界的，该方法永远不会返回false.
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException if the specified element is null
     * 元素入队
     */
    public boolean offer(E e) {
        checkNotNull(e);
        // 新建一个入队列结点，此时创建节点以及设置节点的元素并不会产生竞争，
        // 所以使用UNSAFE.putObject设置结点元素，没有使用volatile方式设置元素
        final Node<E> newNode = new Node<E>(e);

        // 循环进行cas入队，直到成功
        // t指向tail节点的引用，tail并不总是指向最后一个结点
        // p用来表示队列的尾结点，尾节点可能是tail指向的节点也可能不是tail指向的节点
        for (Node<E> t = tail, p = t;;) {
            // 获取p的下一个节点
            Node<E> q = p.next;
            // q为null，说明p是最后一个结点
            if (q == null) {
                // p is last node
                // p是最后一个结点，可以尝试使用cas将新节点入队列，设置p.next=newNode，如果不成功说明有其他的线程更新过最后一个节点
                if (p.casNext(null, newNode)) {
                    // Successful CAS is the linearization point
                    // for e to become an element of this queue,
                    // and for newNode to become "live".

                    // 新节点入队列成功
                    // 接下来需要看下是不是需要将tail指向队列中最后一个节点：
                    // 如果p==t，说明newNode入队列前tail指向的是最后一个节点，新节点入队成功后此时队列如下：head,.....,tail,newNode，
                    // 此时是不需要将tail指向到newNode上的，保留原来的tail指向的节点不动。
                    // 如果p!=t，说明入队时tail不是指向的最后一个节点，新节点入队成功后此时队列如下：head,......,tail,q,newNode，
                    // 也就是tail指向的节点后面至少有两个节点，此时需要将tail指向newNode，
                    if (p != t) // hop two nodes at a time
                        // 设置tail节点为刚才新入队的节点，失败了也没事，失败了说明有其他的线程已经更改了tail
                        casTail(t, newNode);  // Failure is OK.
                    return true;
                }
                // Lost CAS race to another thread; re-read next
                // 如果cas失败，说明有其他线程入队了新结点，继续执行下次循环入队列
            }

            // 如果q != null，也就是p.next不是null，此时有两种情况（分别对应下面的else if分支和else分支）：
            // 1. 如果p == q，说明p.next指向自己，这种情况会在poll的时候发生，poll出队列的时候会将head的next设置为自己，
            //    此时说明p节点都已经被出队列了，也就是tail指向的节点也一定已经出队列了，此时head指向的节点可能是已经出队列了，
            //    也可能是指向了队列中的有效节点，所以此时需要将tail指向队列中的有效节点，并且再继续进行循环将新节点入队列
            // 2. 如果p != q，说明p指向的也不是最后一个节点了，此时需要继续往后找最后一个节点，并且再继续进行循环将新节点入队列
            else if (p == q)
                // We have fallen off list.  If tail is unchanged, it
                // will also be off-list, in which case we need to
                // jump to head, from which all live nodes are always
                // reachable.  Else the new tail is a better bet.
                // tail指向的节点已经不是有效节点了，需要将tail指向队列中的有效节点
                // t == tail说明此没有其他线程更新tail，直接将head指向的节点赋值给p，并继续进行循环将新节点入队列
                // t != tail说明此时有其他线程更新了tail，则直接使用新的tail指向的节点赋值给p,并继续循环将新节点入队列
                p = (t != (t = tail)) ? t : head;
            else
                // Check for tail updates after two hops.
                // 如果 p == t，说明tail结点没有被其他线程修改，p节点后面有了新的其他的节点，此时将q（也就是p的下一个节点）节点赋值给p，
                // 并继续循环将新节点入队列。
                // 如果p != t，说明有其他线程变更了tail指向的节点，此时需要将p直接指向tail指向的新的节点，并继续循环将新节点入队列
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    public E poll() {
        restartFromHead:
        for (;;) {
            // h指向head节点的引用，head指向的节点并不一定会在队列中
            // p指向队列中的第一个节点
            // q是p.next
            for (Node<E> h = head, p = h, q;;) {
                // p指向队列中的第一个节点，item是队列中第一个节点中存储的值
                E item = p.item;

                // 队列中有效节点的值一定不为null，item不为null说明p指向的是队列中第一个有效节点，
                // 此时直接尝试使用cas将队列中第一个有效节点的值设置为null，表示出队列。
                // 如果cas成功了，表示出队列成功，出队列成功之后会尝试将head节点往后移动到队列中第一个有效节点上
                // 如果cas失败了，说明有其他的线程将第一个节点出队列了，当前线程会进入到下一次循环继续进行出队列操作
                if (item != null && p.casItem(item, null)) {
                    // Successful CAS is the linearization point
                    // for item to be removed from this queue.
                    // 在当前线程看来此时p是队列中第一个有效节点，因此需要看看head指向的节点是不是p指向的节点
                    // 如果p == h则说明head和p指向的都是同一个节点，此时不需要将head进行移动，需要等到下一个节点出队列的时候再将head移动，
                    // 这样可以减少一次cas操作。
                    // 如果p != h则说明head指向的节点比p指向的节点还要老，需要将head指向到p指向的节点的后面的节点，此时p已经出队列了，所以
                    // p后面的节点才可能是队列中有效的节点。同时还会将原来的head指向的节点的next指向自己，这个指向自己会在offer方法中用到。
                    if (p != h) // hop two nodes at a time
                        updateHead(h, ((q = p.next) != null) ? q : p);
                    return item;
                }
                // 如果item为null，说明p指向的节点已经被出队列了，此时会有三种情况（分别对应下面的三个else分支）：
                // 1. 如果p.next为null，说明队列已经是空的了，将head指向p指向的节点；
                // 2. 如果p.next不为null，并且p == q（也就是p = p.next），说明p已经被移出队列了，需要跳出循环从头来；
                // 3. 如果p.next不为null，并且p != q（也就是p != p.next），说明p的下一个节点是可用节点，直接将p的next节点赋值给p,继续
                //    下一次循环出队列。
                else if ((q = p.next) == null) {
                    updateHead(h, p);
                    return null;
                }
                else if (p == q)
                    continue restartFromHead;
                else
                    p = q;
            }
        }
    }

    public E peek() {
        restartFromHead:
        for (;;) {
            for (Node<E> h = head, p = h, q;;) {
                E item = p.item;
                if (item != null || (q = p.next) == null) {
                    updateHead(h, p);
                    return item;
                }
                else if (p == q)
                    continue restartFromHead;
                else
                    p = q;
            }
        }
    }

    /**
     * Returns the first live (non-deleted) node on list, or null if none.
     * This is yet another variant of poll/peek; here returning the
     * first node, not element.  We could make peek() a wrapper around
     * first(), but that would cost an extra volatile read of item,
     * and the need to add a retry loop to deal with the possibility
     * of losing a race to a concurrent poll().
     */
    Node<E> first() {
        restartFromHead:
        for (;;) {
            for (Node<E> h = head, p = h, q;;) {
                boolean hasItem = (p.item != null);
                if (hasItem || (q = p.next) == null) {
                    updateHead(h, p);
                    return hasItem ? p : null;
                }
                else if (p == q)
                    continue restartFromHead;
                else
                    p = q;
            }
        }
    }

    /**
     * Returns {@code true} if this queue contains no elements.
     *
     * @return {@code true} if this queue contains no elements
     */
    public boolean isEmpty() {
        return first() == null;
    }

    /**
     * Returns the number of elements in this queue.  If this queue
     * contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     * Additionally, if elements are added or removed during execution
     * of this method, the returned result may be inaccurate.  Thus,
     * this method is typically not very useful in concurrent
     * applications.
     *
     * @return the number of elements in this queue
     * size不准确
     */
    public int size() {
        int count = 0;
        for (Node<E> p = first(); p != null; p = succ(p))
            if (p.item != null)
                // Collection.size() spec says to max out
                if (++count == Integer.MAX_VALUE)
                    break;
        return count;
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null && o.equals(item))
                return true;
        }
        return false;
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        if (o != null) {
            Node<E> next, pred = null;
            for (Node<E> p = first(); p != null; pred = p, p = next) {
                boolean removed = false;
                E item = p.item;
                if (item != null) {
                    if (!o.equals(item)) {
                        next = succ(p);
                        continue;
                    }
                    removed = p.casItem(item, null);
                }

                next = succ(p);
                if (pred != null && next != null) // unlink
                    pred.casNext(p, next);
                if (removed)
                    return true;
            }
        }
        return false;
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this queue, in the order that they are returned by the specified
     * collection's iterator.  Attempts to {@code addAll} of a queue to
     * itself result in {@code IllegalArgumentException}.
     *
     * @param c the elements to be inserted into this queue
     * @return {@code true} if this queue changed as a result of the call
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     * @throws IllegalArgumentException if the collection is this queue
     */
    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            // As historically specified in AbstractQueue#addAll
            throw new IllegalArgumentException();

        // Copy c into a private chain of Nodes
        Node<E> beginningOfTheEnd = null, last = null;
        for (E e : c) {
            checkNotNull(e);
            Node<E> newNode = new Node<E>(e);
            if (beginningOfTheEnd == null)
                beginningOfTheEnd = last = newNode;
            else {
                last.lazySetNext(newNode);
                last = newNode;
            }
        }
        if (beginningOfTheEnd == null)
            return false;

        // Atomically append the chain at the tail of this collection
        for (Node<E> t = tail, p = t;;) {
            Node<E> q = p.next;
            if (q == null) {
                // p is last node
                if (p.casNext(null, beginningOfTheEnd)) {
                    // Successful CAS is the linearization point
                    // for all elements to be added to this queue.
                    if (!casTail(t, last)) {
                        // Try a little harder to update tail,
                        // since we may be adding many elements.
                        t = tail;
                        if (last.next == null)
                            casTail(t, last);
                    }
                    return true;
                }
                // Lost CAS race to another thread; re-read next
            }
            else if (p == q)
                // We have fallen off list.  If tail is unchanged, it
                // will also be off-list, in which case we need to
                // jump to head, from which all live nodes are always
                // reachable.  Else the new tail is a better bet.
                p = (t != (t = tail)) ? t : head;
            else
                // Check for tail updates after two hops.
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        // Use ArrayList to deal with resizing.
        ArrayList<E> al = new ArrayList<E>();
        for (Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null)
                al.add(item);
        }
        return al.toArray();
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     *  <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        // try to use sent-in array
        int k = 0;
        Node<E> p;
        for (p = first(); p != null && k < a.length; p = succ(p)) {
            E item = p.item;
            if (item != null)
                a[k++] = (T)item;
        }
        if (p == null) {
            if (k < a.length)
                a[k] = null;
            return a;
        }

        // If won't fit, use ArrayList version
        ArrayList<E> al = new ArrayList<E>();
        for (Node<E> q = first(); q != null; q = succ(q)) {
            E item = q.item;
            if (item != null)
                al.add(item);
        }
        return al.toArray(a);
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /**
         * Next node to return item for.
         */
        private Node<E> nextNode;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         */
        private E nextItem;

        /**
         * Node of the last returned item, to support remove.
         */
        private Node<E> lastRet;

        Itr() {
            advance();
        }

        /**
         * Moves to next valid node and returns item to return for
         * next(), or null if no such.
         */
        private E advance() {
            lastRet = nextNode;
            E x = nextItem;

            Node<E> pred, p;
            if (nextNode == null) {
                p = first();
                pred = null;
            } else {
                pred = nextNode;
                p = succ(nextNode);
            }

            for (;;) {
                if (p == null) {
                    nextNode = null;
                    nextItem = null;
                    return x;
                }
                E item = p.item;
                if (item != null) {
                    nextNode = p;
                    nextItem = item;
                    return x;
                } else {
                    // skip over nulls
                    Node<E> next = succ(p);
                    if (pred != null && next != null)
                        pred.casNext(p, next);
                    p = next;
                }
            }
        }

        public boolean hasNext() {
            return nextNode != null;
        }

        public E next() {
            if (nextNode == null) throw new NoSuchElementException();
            return advance();
        }

        public void remove() {
            Node<E> l = lastRet;
            if (l == null) throw new IllegalStateException();
            // rely on a future traversal to relink.
            l.item = null;
            lastRet = null;
        }
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData All of the elements (each an {@code E}) in
     * the proper order, followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out all elements in the proper order.
        for (Node<E> p = first(); p != null; p = succ(p)) {
            Object item = p.item;
            if (item != null)
                s.writeObject(item);
        }

        // Use trailing null as sentinel
        s.writeObject(null);
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in elements until trailing null sentinel found
        Node<E> h = null, t = null;
        Object item;
        while ((item = s.readObject()) != null) {
            @SuppressWarnings("unchecked")
            Node<E> newNode = new Node<E>((E) item);
            if (h == null)
                h = t = newNode;
            else {
                t.lazySetNext(newNode);
                t = newNode;
            }
        }
        if (h == null)
            h = t = new Node<E>(null);
        head = h;
        tail = t;
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class CLQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final ConcurrentLinkedQueue<E> queue;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        CLQSpliterator(ConcurrentLinkedQueue<E> queue) {
            this.queue = queue;
        }

        public Spliterator<E> trySplit() {
            Node<E> p;
            final ConcurrentLinkedQueue<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                ((p = current) != null || (p = q.first()) != null) &&
                p.next != null) {
                Object[] a = new Object[n];
                int i = 0;
                do {
                    if ((a[i] = p.item) != null)
                        ++i;
                    if (p == (p = p.next))
                        p = q.first();
                } while (p != null && i < n);
                if ((current = p) == null)
                    exhausted = true;
                if (i > 0) {
                    batch = i;
                    return Spliterators.spliterator
                        (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                         Spliterator.CONCURRENT);
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedQueue<E> q = this.queue;
            if (!exhausted &&
                ((p = current) != null || (p = q.first()) != null)) {
                exhausted = true;
                do {
                    E e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedQueue<E> q = this.queue;
            if (!exhausted &&
                ((p = current) != null || (p = q.first()) != null)) {
                E e;
                do {
                    e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                } while (e == null && p != null);
                if ((current = p) == null)
                    exhausted = true;
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public long estimateSize() { return Long.MAX_VALUE; }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new CLQSpliterator<E>(this);
    }

    /**
     * Throws NullPointerException if argument is null.
     *
     * @param v the element
     */
    private static void checkNotNull(Object v) {
        if (v == null)
            throw new NullPointerException();
    }

    private boolean casTail(Node<E> cmp, Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, tailOffset, cmp, val);
    }

    private boolean casHead(Node<E> cmp, Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;
    private static final long tailOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = ConcurrentLinkedQueue.class;
            headOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("head"));
            tailOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("tail"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
