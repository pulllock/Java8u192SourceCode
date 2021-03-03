/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.util.function.Consumer;

/**
 * Doubly-linked list implementation of the {@code List} and {@code Deque}
 * interfaces.  Implements all optional list operations, and permits all
 * elements (including {@code null}).
 *
 * <p>All of the operations perform as could be expected for a doubly-linked
 * list.  Operations that index into the list will traverse the list from
 * the beginning or the end, whichever is closer to the specified index.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a linked list concurrently, and at least
 * one of the threads modifies the list structurally, it <i>must</i> be
 * synchronized externally.  (A structural modification is any operation
 * that adds or deletes one or more elements; merely setting the value of
 * an element is not a structural modification.)  This is typically
 * accomplished by synchronizing on some object that naturally
 * encapsulates the list.
 *
 * If no such object exists, the list should be "wrapped" using the
 * {@link Collections#synchronizedList Collections.synchronizedList}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the list:<pre>
 *   List list = Collections.synchronizedList(new LinkedList(...));</pre>
 *
 * <p>The iterators returned by this class's {@code iterator} and
 * {@code listIterator} methods are <i>fail-fast</i>: if the list is
 * structurally modified at any time after the iterator is created, in
 * any way except through the Iterator's own {@code remove} or
 * {@code add} methods, the iterator will throw a {@link
 * ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than
 * risking arbitrary, non-deterministic behavior at an undetermined
 * time in the future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author  Josh Bloch
 * @see     List
 * @see     ArrayList
 * @since 1.2
 * @param <E> the type of elements held in this collection
 *           线性表的链式存储结构，使用地址分散的存储单元存储数据元素，
 *           逻辑上相邻的数据元素，在物理位置上不一定相邻。
 *
 *           需要采用附加信息来存储元素之间的顺序关系，存储数据元素的存储单元称为结点，
 *           结点包括：数据域和地址域。
 *
 *           每个结点只有一个地址域的线性链表，称为单链表。
 *
 *           单链表中结点的空间是在插入和删除过程中动态申请和释放的，无需预先分配存储空间，
 *           可以避免顺序表的扩容和复制元素的操作，提高效率和存储空间利用率。
 *
 *           带头结点的单链表：
 *           1. 对单链表的插入、删除操作不需要区分操作位置
 *           2. 单链表头指针head非空，实现共享单链表
 *
 *           单链表不是随机存取结构。
 *
 *           排序单链表
 *
 *           循环单链表
 *           循环单链表指最后一个结点的next域指向head，形成环形结构。
 *
 *           双链表
 *           双链表每个结点有两个地址域，分别指向前驱结点和后继结点。
 *
 *           循环双链表
 *           循环双链表最后一个结点的next指向头结点，头结点的prev指向最后一个结点。
 *
 *           排序循环双链表
 */

public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable
{
    /**
     * 链表长度
     * 记录长度可以让一些操作的时间复杂度由O(n^2)降为O(n)
     */
    transient int size = 0;

    /**
     * Pointer to first node.
     * Invariant: (first == null && last == null) ||
     *            (first.prev == null && first.item != null)
     *            链表中第一个结点
     */
    transient Node<E> first;

    /**
     * Pointer to last node.
     * Invariant: (first == null && last == null) ||
     *            (last.next == null && last.item != null)
     *            链表中最后一个结点
     */
    transient Node<E> last;

    /**
     * Constructs an empty list.
     */
    public LinkedList() {
    }

    /**
     * Constructs a list containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param  c the collection whose elements are to be placed into this list
     * @throws NullPointerException if the specified collection is null
     * 使用给定的集合来构造一个链表
     */
    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * Links e as first element.
     * 将e作为链表的第一个元素
     */
    private void linkFirst(E e) {
        // first指向的结点暂时赋值给f
        final Node<E> f = first;
        // 使用e构造一个新节点，前驱结点为null，后继结点为f指向的节点
        final Node<E> newNode = new Node<>(null, e, f);
        // first指向新的节点
        first = newNode;
        // f为null的话，说明之前的链表是个空链表
        if (f == null)
            // e加入后，当前链表只有一个结点，所以last指向新节点
            last = newNode;
        else
            // 之前的链表不是空链表，需要将原来f的前驱指向新节点
            f.prev = newNode;
        // 大小加1
        size++;
        // 结构性修改次数加1
        modCount++;
    }

    /**
     * Links e as last element.
     * 将e作为链表的最后一个元素
     */
    void linkLast(E e) {
        // 原来的last暂时赋值给l
        final Node<E> l = last;
        // 给e创建一个新节点，前驱是之前链表中的最后一个结点，后继是null
        final Node<E> newNode = new Node<>(l, e, null);
        // 将新的最后一个结点赋值给last
        last = newNode;
        // l为null，说明之前链表是空链表
        if (l == null)
            // first也指向新的结点
            first = newNode;
        else
            // 之前链表不为空，之前最后一个结点的后继指向新的最后结点
            l.next = newNode;
        // 大小加1
        size++;
        // 结构性修改次数加1
        modCount++;
    }

    /**
     * Inserts element e before non-null Node succ.
     * 在succ结点之前插入元素e
     */
    void linkBefore(E e, Node<E> succ) {
        // assert succ != null;
        // succ的前驱结点
        final Node<E> pred = succ.prev;
        // 给e创建一个新结点，前驱是succ的前驱，后继是succ
        final Node<E> newNode = new Node<>(pred, e, succ);
        // 新结点成为了succ的前驱结点
        succ.prev = newNode;
        // 如果succ的前驱结点是null，说明之前的是空链表
        if (pred == null)
            // first指向新结点
            first = newNode;
        else
            // 之前不是空链表，succ原来的前驱结点的后继结点指向新结点
            pred.next = newNode;
        // 链表大小加1
        size++;
        // 结构性修改次数加1
        modCount++;
    }

    /**
     * Unlinks non-null first node f.
     * 移除链表首个结点，返回移除的节点中的元素
     */
    private E unlinkFirst(Node<E> f) {
        // assert f == first && f != null;
        // 要移除的结点中包含的元素
        final E element = f.item;
        // 要移除结点的后继结点
        final Node<E> next = f.next;
        // 移除结点中元素设置为null
        f.item = null;
        // 移除结点后继结点设置为null
        f.next = null; // help GC
        // first指向移除结点的后继结点
        first = next;
        // next为null，说明原来链表中只有一个元素
        if (next == null)
            // last指向null
            last = null;
        else
            // next的前驱设置为null，next变成了第一个结点了
            next.prev = null;
        // 链表大小减1
        size--;
        // 结构性修改次数加1
        modCount++;
        // 返回移除结点中的元素
        return element;
    }

    /**
     * Unlinks non-null last node l.
     * 移除链表中最后一个结点，返回移除结点中的元素
     */
    private E unlinkLast(Node<E> l) {
        // assert l == last && l != null;
        // 要移除结点的元素
        final E element = l.item;
        // 要移除结点的前驱
        final Node<E> prev = l.prev;
        // 要移除结点元素设置为null
        l.item = null;
        // 要移除结点前驱设置为null
        l.prev = null; // help GC
        // last指向移除结点的前驱
        last = prev;
        // 移除结点的前驱是null，说明之前链表只有一个结点
        if (prev == null)
            // first也指向null
            first = null;
        else
            // 移除结点的前驱结点的后继变为null，prev已经变成了最后一个结点了
            prev.next = null;
        // 链表大小减1
        size--;
        // 结构性修改加1
        modCount++;
        // 返回移除的元素
        return element;
    }

    /**
     * Unlinks non-null node x.
     *  移除结点x，返回移除结点中的元素
     */
    E unlink(Node<E> x) {
        // assert x != null;
        // 要移除结点中的元素
        final E element = x.item;
        // 移除结点的后继结点
        final Node<E> next = x.next;
        // 移除结点的前驱结点
        final Node<E> prev = x.prev;

        // 移除结点的前驱结点为null，说明移除的结点是第一个结点
        if (prev == null) {
            // first指向移除结点的后继结点
            first = next;
        }
        // 移除的不是第一个结点
        else {
            // 移除结点的前驱结点指向移除结点的后继结点
            prev.next = next;
            // 移除结点的前驱设置为null
            x.prev = null;
        }

        // 移除的结点是最后一个结点
        if (next == null) {
            // 将last指向移除结点的前驱结点
            last = prev;
        }
        // 移除的不是最后一个结点
        else {
            // 移除结点的后继结点的前驱指向移除结点的前驱
            next.prev = prev;
            // 移除结点的后继结点变为null
            x.next = null;
        }

        // 移除结点的元素设置为null
        x.item = null;
        // 链表大小减1
        size--;
        // 结构性修改次数加1
        modCount++;
        // 返回被移除结点的元素
        return element;
    }

    /**
     * Returns the first element in this list.
     *
     * @return the first element in this list
     * @throws NoSuchElementException if this list is empty
     * 返回链表中的第一个结点包含的元素
     */
    public E getFirst() {
        // first指向的结点就是第一个结点
        final Node<E> f = first;
        // 结点不存在，抛异常
        if (f == null)
            throw new NoSuchElementException();
        // 返回第一个结点的元素
        return f.item;
    }

    /**
     * Returns the last element in this list.
     *
     * @return the last element in this list
     * @throws NoSuchElementException if this list is empty
     * 获取链表中最后一个结点的元素
     */
    public E getLast() {
        // last指向最后一个结点
        final Node<E> l = last;
        // 没有最后一个结点，抛异常
        if (l == null)
            throw new NoSuchElementException();
        // 返回最后一个结点的元素
        return l.item;
    }

    /**
     * Removes and returns the first element from this list.
     *
     * @return the first element from this list
     * @throws NoSuchElementException if this list is empty
     * 移除第一个结点
     */
    public E removeFirst() {
        // first指向第一个结点
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        // 使用unlinkFirst移除第一个结点
        return unlinkFirst(f);
    }

    /**
     * Removes and returns the last element from this list.
     *
     * @return the last element from this list
     * @throws NoSuchElementException if this list is empty
     * 移除最后一个结点
     */
    public E removeLast() {
        // last指向最后一个结点
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        // 使用unlinkLast移除最后一个结点
        return unlinkLast(l);
    }

    /**
     * Inserts the specified element at the beginning of this list.
     *
     * @param e the element to add
     *          添加元素到链表开始位置
     */
    public void addFirst(E e) {
        linkFirst(e);
    }

    /**
     * Appends the specified element to the end of this list.
     *
     * <p>This method is equivalent to {@link #add}.
     *
     * @param e the element to add
     *          添加元素到链表尾部
     */
    public void addLast(E e) {
        linkLast(e);
    }

    /**
     * Returns {@code true} if this list contains the specified element.
     * More formally, returns {@code true} if and only if this list contains
     * at least one element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return {@code true} if this list contains the specified element
     * 链表中是否包含元素
     */
    public boolean contains(Object o) {
        // 使用indexOf来判断元素是否存在链表中，indexOf会遍历链表查找元素
        return indexOf(o) != -1;
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     * 返回链表中元素个数
     */
    public int size() {
        return size;
    }

    /**
     * Appends the specified element to the end of this list.
     *
     * <p>This method is equivalent to {@link #addLast}.
     *
     * @param e element to be appended to this list
     * @return {@code true} (as specified by {@link Collection#add})
     * 添加元素到链表中，添加到链表的尾部
     */
    public boolean add(E e) {
        linkLast(e);
        return true;
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If this list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * {@code i} such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns {@code true} if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if this list contained the specified element
     * 移除在链表中第一个出现的元素o
     * 返回false表示没有移除的元素，true表示找到并移除了一个元素
     */
    public boolean remove(Object o) {
        // 移除null元素
        if (o == null) {
            // 遍历链表，找第一个包含null的结点移除
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            // 遍历链表，找第一个和元素匹配的结点，移除
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this list, in the order that they are returned by the specified
     * collection's iterator.  The behavior of this operation is undefined if
     * the specified collection is modified while the operation is in
     * progress.  (Note that this will occur if the specified collection is
     * this list, and it's nonempty.)
     *
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     * 将指定的集合中的元素添加到链表尾部
     */
    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list, starting at the specified position.  Shifts the element
     * currently at that position (if any) and any subsequent elements to
     * the right (increases their indices).  The new elements will appear
     * in the list in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param index index at which to insert the first element
     *              from the specified collection
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     * 将指定的集合中的元素添加到链表中指定位置
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        // 检查要添加的位置是否合法
        checkPositionIndex(index);
        // 转成数组
        Object[] a = c.toArray();
        // 新添加元素的个数
        int numNew = a.length;
        if (numNew == 0)
            return false;

        Node<E> pred, succ;
        // 要添加的位置在链表尾部
        if (index == size) {
            // 添加位置的后继是null
            succ = null;
            // 添加位置的前驱是链表中最后一个结点
            pred = last;
        }
        // 要添加的位置在链表非尾部
        else {
            // 添加位置的后继是当前位置的结点
            succ = node(index);
            // 添加位置的前驱是当前位置的结点的前驱
            pred = succ.prev;
        }

        // 遍历要添加的元素，将元素添加到链表中
        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E) o;
            // 创建新节点，前驱是插入位置结点的前驱，后继暂时为null
            Node<E> newNode = new Node<>(pred, e, null);
            // pred为null，说明在链表头插入
            if (pred == null)
                // first指向新节点
                first = newNode;
            else
                // 插入位置前驱的后继设置为新节点
                pred.next = newNode;
            // 新节点变为了pred
            pred = newNode;
        }

        // 所有元素插入完成，succ为null说明是在尾部插入的
        if (succ == null) {
            // last指向最后一个元素
            last = pred;
        } else {
            // 这里说明不是在尾部插入的，将新插入最后一个元素的next指向原来链表的插入位置后的元素
            pred.next = succ;
            succ.prev = pred;
        }

        // 链表大小增加
        size += numNew;
        // 结构性修改次数增加
        modCount++;
        return true;
    }

    /**
     * Removes all of the elements from this list.
     * The list will be empty after this call returns.
     * 移除链表中所有元素
     */
    public void clear() {
        // Clearing all of the links between nodes is "unnecessary", but:
        // - helps a generational GC if the discarded nodes inhabit
        //   more than one generation
        // - is sure to free memory even if there is a reachable Iterator
        // 遍历整个链表，挨个将结点的前驱、后继、元素都设置为null
        for (Node<E> x = first; x != null; ) {
            Node<E> next = x.next;
            x.item = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        // first和last都指向null
        first = last = null;
        // 链表大小设置为0
        size = 0;
        // 结构性修改次数增加
        modCount++;
    }


    // Positional Access Operations

    /**
     * Returns the element at the specified position in this list.
     *
     * @param index index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * 获取指定位置处的元素
     */
    public E get(int index) {
        // 检查索引合法性
        checkElementIndex(index);
        // 获取指定位置处的结点后，获取其元素
        return node(index).item;
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * 替换指定位置处的元素
     */
    public E set(int index, E element) {
        // 检查位置是否合法
        checkElementIndex(index);
        // 找到指定位置的结点
        Node<E> x = node(index);
        // 结点中的原来的元素
        E oldVal = x.item;
        // 将结点中的元素设置为新的元素
        x.item = element;
        // 返回原来的元素
        return oldVal;
    }

    /**
     * Inserts the specified element at the specified position in this list.
     * Shifts the element currently at that position (if any) and any
     * subsequent elements to the right (adds one to their indices).
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * 在指定位置处添加新元素
     */
    public void add(int index, E element) {
        // 检查位置合法性
        checkPositionIndex(index);

        // 插入位置在最后，直接添加到最后
        if (index == size)
            linkLast(element);
        else
            // 插入位置不在最后，插入位置指定的索引处结点前
            linkBefore(element, node(index));
    }

    /**
     * Removes the element at the specified position in this list.  Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     * Returns the element that was removed from the list.
     *
     * @param index the index of the element to be removed
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * 移除指定位置的索引
     */
    public E remove(int index) {
        // 检查索引合法性
        checkElementIndex(index);
        // 先根据索引找到对应的结点，再移除结点
        return unlink(node(index));
    }

    /**
     * Tells if the argument is the index of an existing element.
     * 检查索引是否是链表中的索引
     */
    private boolean isElementIndex(int index) {
        return index >= 0 && index < size;
    }

    /**
     * Tells if the argument is the index of a valid position for an
     * iterator or an add operation.
     * 检查索引是否适合插入
     */
    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= size;
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    private void checkElementIndex(int index) {
        if (!isElementIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private void checkPositionIndex(int index) {
        if (!isPositionIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * Returns the (non-null) Node at the specified element index.
     * 返回指定索引处的结点，需要遍历链表才能找到
     */
    Node<E> node(int index) {
        // assert isElementIndex(index);

        // 索引在链表前半部分
        if (index < (size >> 1)) {
            // 从头开始遍历
            Node<E> x = first;
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        }
        // 索引在链表后半部分
        else {
            // 从链表尾部开始遍历
            Node<E> x = last;
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }

    // Search Operations

    /**
     * Returns the index of the first occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the lowest index {@code i} such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @return the index of the first occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element
     *         查找元素在链表中第一个出现的位置
     */
    public int indexOf(Object o) {
        int index = 0;
        // null元素，遍历链表
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null)
                    return index;
                index++;
            }
        } else {
            // 非null元素，遍历链表
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item))
                    return index;
                index++;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the last occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the highest index {@code i} such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @return the index of the last occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element
     *         返回元素最后一次在元素中出现的位置
     *         从最后开始遍历，找到从后往前的第一个出现位置
     */
    public int lastIndexOf(Object o) {
        int index = size;
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (x.item == null)
                    return index;
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (o.equals(x.item))
                    return index;
            }
        }
        return -1;
    }

    // Queue operations.

    /**
     * Retrieves, but does not remove, the head (first element) of this list.
     *
     * @return the head of this list, or {@code null} if this list is empty
     * @since 1.5
     * 获取链表中第一个结点的元素，但不移除结点，不存在的话返回null
     *
     */
    public E peek() {
        // first指向的是第一个结点
        final Node<E> f = first;
        // 返回第一个结点的元素或者null
        return (f == null) ? null : f.item;
    }

    /**
     * Retrieves, but does not remove, the head (first element) of this list.
     *
     * @return the head of this list
     * @throws NoSuchElementException if this list is empty
     * @since 1.5
     * 获取链表中第一个结点的元素，但不移除结点，不存在则抛异常
     */
    public E element() {
        // 使用getFirst获取第一个结点元素，不存在就会抛异常
        return getFirst();
    }

    /**
     * Retrieves and removes the head (first element) of this list.
     *
     * @return the head of this list, or {@code null} if this list is empty
     * @since 1.5
     * 获取链表中第一个结点元素，并移除结点，不存在就返回null
     */
    public E poll() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * Retrieves and removes the head (first element) of this list.
     *
     * @return the head of this list
     * @throws NoSuchElementException if this list is empty
     * @since 1.5
     * 移除链表中第一个结点元素，并返回元素，不存在就抛异常
     */
    public E remove() {
        return removeFirst();
    }

    /**
     * Adds the specified element as the tail (last element) of this list.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Queue#offer})
     * @since 1.5
     * 添加元素到链表尾部
     */
    public boolean offer(E e) {
        return add(e);
    }

    // Deque operations
    /**
     * Inserts the specified element at the front of this list.
     *
     * @param e the element to insert
     * @return {@code true} (as specified by {@link Deque#offerFirst})
     * @since 1.6
     * 添加元素到链表头部
     */
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    /**
     * Inserts the specified element at the end of this list.
     *
     * @param e the element to insert
     * @return {@code true} (as specified by {@link Deque#offerLast})
     * @since 1.6
     * 添加元素到链表尾部
     */
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /**
     * Retrieves, but does not remove, the first element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the first element of this list, or {@code null}
     *         if this list is empty
     * @since 1.6
     * 获取链表第一个元素，但不移除结点，不存在就返回null
     */
    public E peekFirst() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
     }

    /**
     * Retrieves, but does not remove, the last element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the last element of this list, or {@code null}
     *         if this list is empty
     * @since 1.6
     * 获取链表最后一个元素，但不移除结点，不存在就返回null
     */
    public E peekLast() {
        final Node<E> l = last;
        return (l == null) ? null : l.item;
    }

    /**
     * Retrieves and removes the first element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the first element of this list, or {@code null} if
     *     this list is empty
     * @since 1.6
     * 获取第一个结点元素，并移除，不存在就返回null
     */
    public E pollFirst() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * Retrieves and removes the last element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the last element of this list, or {@code null} if
     *     this list is empty
     * @since 1.6
     * 获取最后一个元素，并移除结点，不存在就返回null
     */
    public E pollLast() {
        final Node<E> l = last;
        return (l == null) ? null : unlinkLast(l);
    }

    /**
     * Pushes an element onto the stack represented by this list.  In other
     * words, inserts the element at the front of this list.
     *
     * <p>This method is equivalent to {@link #addFirst}.
     *
     * @param e the element to push
     * @since 1.6
     * 添加元素到链表头部
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * Pops an element from the stack represented by this list.  In other
     * words, removes and returns the first element of this list.
     *
     * <p>This method is equivalent to {@link #removeFirst()}.
     *
     * @return the element at the front of this list (which is the top
     *         of the stack represented by this list)
     * @throws NoSuchElementException if this list is empty
     * @since 1.6
     * 从链表头部移除结点，不存在就抛异常
     */
    public E pop() {
        return removeFirst();
    }

    /**
     * Removes the first occurrence of the specified element in this
     * list (when traversing the list from head to tail).  If the list
     * does not contain the element, it is unchanged.
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if the list contained the specified element
     * @since 1.6
     * 移除链表中第一个出现的元素
     */
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    /**
     * Removes the last occurrence of the specified element in this
     * list (when traversing the list from head to tail).  If the list
     * does not contain the element, it is unchanged.
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if the list contained the specified element
     * @since 1.6
     * 移除最后一个出现的元素
     */
    public boolean removeLastOccurrence(Object o) {
        // 从链表最后开始往前遍历
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a list-iterator of the elements in this list (in proper
     * sequence), starting at the specified position in the list.
     * Obeys the general contract of {@code List.listIterator(int)}.<p>
     *
     * The list-iterator is <i>fail-fast</i>: if the list is structurally
     * modified at any time after the Iterator is created, in any way except
     * through the list-iterator's own {@code remove} or {@code add}
     * methods, the list-iterator will throw a
     * {@code ConcurrentModificationException}.  Thus, in the face of
     * concurrent modification, the iterator fails quickly and cleanly, rather
     * than risking arbitrary, non-deterministic behavior at an undetermined
     * time in the future.
     *
     * @param index index of the first element to be returned from the
     *              list-iterator (by a call to {@code next})
     * @return a ListIterator of the elements in this list (in proper
     *         sequence), starting at the specified position in the list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @see List#listIterator(int)
     */
    public ListIterator<E> listIterator(int index) {
        checkPositionIndex(index);
        return new ListItr(index);
    }

    private class ListItr implements ListIterator<E> {
        private Node<E> lastReturned;
        private Node<E> next;
        private int nextIndex;
        private int expectedModCount = modCount;

        ListItr(int index) {
            // assert isPositionIndex(index);
            next = (index == size) ? null : node(index);
            nextIndex = index;
        }

        public boolean hasNext() {
            return nextIndex < size;
        }

        public E next() {
            checkForComodification();
            if (!hasNext())
                throw new NoSuchElementException();

            lastReturned = next;
            next = next.next;
            nextIndex++;
            return lastReturned.item;
        }

        public boolean hasPrevious() {
            return nextIndex > 0;
        }

        public E previous() {
            checkForComodification();
            if (!hasPrevious())
                throw new NoSuchElementException();

            lastReturned = next = (next == null) ? last : next.prev;
            nextIndex--;
            return lastReturned.item;
        }

        public int nextIndex() {
            return nextIndex;
        }

        public int previousIndex() {
            return nextIndex - 1;
        }

        public void remove() {
            checkForComodification();
            if (lastReturned == null)
                throw new IllegalStateException();

            Node<E> lastNext = lastReturned.next;
            unlink(lastReturned);
            if (next == lastReturned)
                next = lastNext;
            else
                nextIndex--;
            lastReturned = null;
            expectedModCount++;
        }

        public void set(E e) {
            if (lastReturned == null)
                throw new IllegalStateException();
            checkForComodification();
            lastReturned.item = e;
        }

        public void add(E e) {
            checkForComodification();
            lastReturned = null;
            if (next == null)
                linkLast(e);
            else
                linkBefore(e, next);
            nextIndex++;
            expectedModCount++;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            while (modCount == expectedModCount && nextIndex < size) {
                action.accept(next.item);
                lastReturned = next;
                next = next.next;
                nextIndex++;
            }
            checkForComodification();
        }

        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    private static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }

    /**
     * @since 1.6
     */
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    /**
     * Adapter to provide descending iterators via ListItr.previous
     */
    private class DescendingIterator implements Iterator<E> {
        private final ListItr itr = new ListItr(size());
        public boolean hasNext() {
            return itr.hasPrevious();
        }
        public E next() {
            return itr.previous();
        }
        public void remove() {
            itr.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<E> superClone() {
        try {
            return (LinkedList<E>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Returns a shallow copy of this {@code LinkedList}. (The elements
     * themselves are not cloned.)
     *
     * @return a shallow copy of this {@code LinkedList} instance
     */
    public Object clone() {
        LinkedList<E> clone = superClone();

        // Put clone into "virgin" state
        clone.first = clone.last = null;
        clone.size = 0;
        clone.modCount = 0;

        // Initialize clone with our elements
        for (Node<E> x = first; x != null; x = x.next)
            clone.add(x.item);

        return clone;
    }

    /**
     * Returns an array containing all of the elements in this list
     * in proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this list.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this list
     *         in proper sequence
     *         转换成数组，新建数组，遍历添加到数组中
     */
    public Object[] toArray() {
        Object[] result = new Object[size];
        int i = 0;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;
        return result;
    }

    /**
     * Returns an array containing all of the elements in this list in
     * proper sequence (from first to last element); the runtime type of
     * the returned array is that of the specified array.  If the list fits
     * in the specified array, it is returned therein.  Otherwise, a new
     * array is allocated with the runtime type of the specified array and
     * the size of this list.
     *
     * <p>If the list fits in the specified array with room to spare (i.e.,
     * the array has more elements than the list), the element in the array
     * immediately following the end of the list is set to {@code null}.
     * (This is useful in determining the length of the list <i>only</i> if
     * the caller knows that the list does not contain any null elements.)
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a list known to contain only strings.
     * The following code can be used to dump the list into a newly
     * allocated array of {@code String}:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the list
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this list
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size)
            a = (T[])java.lang.reflect.Array.newInstance(
                                a.getClass().getComponentType(), size);
        int i = 0;
        Object[] result = a;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;

        if (a.length > size)
            a[size] = null;

        return a;
    }

    private static final long serialVersionUID = 876323262645176354L;

    /**
     * Saves the state of this {@code LinkedList} instance to a stream
     * (that is, serializes it).
     *
     * @serialData The size of the list (the number of elements it
     *             contains) is emitted (int), followed by all of its
     *             elements (each an Object) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (Node<E> x = first; x != null; x = x.next)
            s.writeObject(x.item);
    }

    /**
     * Reconstitutes this {@code LinkedList} instance from a stream
     * (that is, deserializes it).
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            linkLast((E)s.readObject());
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED} and
     * {@link Spliterator#ORDERED}.  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @implNote
     * The {@code Spliterator} additionally reports {@link Spliterator#SUBSIZED}
     * and implements {@code trySplit} to permit limited parallelism..
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new LLSpliterator<E>(this, -1, 0);
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class LLSpliterator<E> implements Spliterator<E> {
        static final int BATCH_UNIT = 1 << 10;  // batch array size increment
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedList<E> list; // null OK unless traversed
        Node<E> current;      // current node; null until initialized
        int est;              // size estimate; -1 until first needed
        int expectedModCount; // initialized when est set
        int batch;            // batch size for splits

        LLSpliterator(LinkedList<E> list, int est, int expectedModCount) {
            this.list = list;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEst() {
            int s; // force initialization
            final LinkedList<E> lst;
            if ((s = est) < 0) {
                if ((lst = list) == null)
                    s = est = 0;
                else {
                    expectedModCount = lst.modCount;
                    current = lst.first;
                    s = est = lst.size;
                }
            }
            return s;
        }

        public long estimateSize() { return (long) getEst(); }

        public Spliterator<E> trySplit() {
            Node<E> p;
            int s = getEst();
            if (s > 1 && (p = current) != null) {
                int n = batch + BATCH_UNIT;
                if (n > s)
                    n = s;
                if (n > MAX_BATCH)
                    n = MAX_BATCH;
                Object[] a = new Object[n];
                int j = 0;
                do { a[j++] = p.item; } while ((p = p.next) != null && j < n);
                current = p;
                batch = j;
                est = s - j;
                return Spliterators.spliterator(a, 0, j, Spliterator.ORDERED);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p; int n;
            if (action == null) throw new NullPointerException();
            if ((n = getEst()) > 0 && (p = current) != null) {
                current = null;
                est = 0;
                do {
                    E e = p.item;
                    p = p.next;
                    action.accept(e);
                } while (p != null && --n > 0);
            }
            if (list.modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            if (getEst() > 0 && (p = current) != null) {
                --est;
                E e = p.item;
                current = p.next;
                action.accept(e);
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

}
