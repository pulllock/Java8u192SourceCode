/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 Google Inc.  All Rights Reserved.
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

/**
 * A stable, adaptive, iterative mergesort that requires far fewer than
 * n lg(n) comparisons when running on partially sorted arrays, while
 * offering performance comparable to a traditional mergesort when run
 * on random arrays.  Like all proper mergesorts, this sort is stable and
 * runs O(n log n) time (worst case).  In the worst case, this sort requires
 * temporary storage space for n/2 object references; in the best case,
 * it requires only a small constant amount of space.
 * TimSort是一种稳定的，自适应的，迭代的合并排序，
 * 如果数据部分有序，运行时所需的比较少于nlg(n)，
 * 而在随机数据的数组上运行时，其性能可与传统的合并排序相比。
 * TimSort是稳定的，在最坏的情况下时间复杂度是O(n log n)。
 * 最坏情况下需要的空间是n / 2，最好情况下只需要一个很小的临时空间
 *
 * This implementation was adapted from Tim Peters's list sort for
 * Python, which is described in detail here:
 *
 *   http://svn.python.org/projects/python/trunk/Objects/listsort.txt
 *
 * Tim's C code may be found here:
 *
 *   http://svn.python.org/projects/python/trunk/Objects/listobject.c
 *
 * The underlying techniques are described in this paper (and may have
 * even earlier origins):
 *
 *  "Optimistic Sorting and Information Theoretic Complexity"
 *  Peter McIlroy
 *  SODA (Fourth Annual ACM-SIAM Symposium on Discrete Algorithms),
 *  pp 467-474, Austin, Texas, 25-27 January 1993.
 *
 * While the API to this class consists solely of static methods, it is
 * (privately) instantiable; a TimSort instance holds the state of an ongoing
 * sort, assuming the input array is large enough to warrant the full-blown
 * TimSort. Small arrays are sorted in place, using a binary insertion sort.
 *
 * @author Josh Bloch
 * TimSort结合了归并排序和插入排序，利用了现实中大多数数据通常有部分是已经排序好的这一情况，
 * 对归并排序进行了优化。
 *
 * 已经排序好的序列称为run，也可认为是一个"分区"。排序的时候，将元素放到不同的run中，同时
 * 还会将不同的run进行合并，直到排序结束。
 *
 * TimSort大概思想是：使用插入排序将小的run扩充为大的run，再使用归并排序将run进行合并。
 */
class TimSort<T> {
    /**
     * This is the minimum sized sequence that will be merged.  Shorter
     * sequences will be lengthened by calling binarySort.  If the entire
     * array is less than this length, no merges will be performed.
     * 长度小于32的直接使用插入排序（二分插入排序），长度大于32的则使用归并排序。
     *
     * This constant should be a power of two.  It was 64 in Tim Peter's C
     * implementation, but 32 was empirically determined to work better in
     * this implementation.  In the unlikely event that you set this constant
     * to be a number that's not a power of two, you'll need to change the
     * {@link #minRunLength} computation.
     *
     * If you decrease this constant, you must change the stackLen
     * computation in the TimSort constructor, or you risk an
     * ArrayOutOfBounds exception.  See listsort.txt for a discussion
     * of the minimum stack length required as a function of the length
     * of the array being sorted and the minimum merge sequence length.
     */
    private static final int MIN_MERGE = 32;

    /**
     * The array being sorted.
     * 存储待排序数据的数组
     */
    private final T[] a;

    /**
     * The comparator for this sort.
     */
    private final Comparator<? super T> c;

    /**
     * When we get into galloping mode, we stay there until both runs win less
     * often than MIN_GALLOP consecutive times.
     */
    private static final int  MIN_GALLOP = 7;

    /**
     * This controls when we get *into* galloping mode.  It is initialized
     * to MIN_GALLOP.  The mergeLo and mergeHi methods nudge it higher for
     * random data, and lower for highly structured data.
     */
    private int minGallop = MIN_GALLOP;

    /**
     * Maximum initial size of tmp array, which is used for merging.  The array
     * can grow to accommodate demand.
     *
     * Unlike Tim's original C version, we do not allocate this much storage
     * when sorting smaller arrays.  This change was required for performance.
     */
    private static final int INITIAL_TMP_STORAGE_LENGTH = 256;

    /**
     * Temp storage for merges. A workspace array may optionally be
     * provided in constructor, and if so will be used as long as it
     * is big enough.
     */
    private T[] tmp;
    private int tmpBase; // base of tmp array slice
    private int tmpLen;  // length of tmp array slice

    /**
     * A stack of pending runs yet to be merged.  Run i starts at
     * address base[i] and extends for len[i] elements.  It's always
     * true (so long as the indices are in bounds) that:
     *
     *     runBase[i] + runLen[i] == runBase[i + 1]
     *
     * so we could cut the storage for this, but it's a minor amount,
     * and keeping all the info explicit simplifies the code.
     */
    private int stackSize = 0;  // Number of pending runs on stack
    private final int[] runBase;
    private final int[] runLen;

    /**
     * Creates a TimSort instance to maintain the state of an ongoing sort.
     *
     * @param a the array to be sorted
     * @param c the comparator to determine the order of the sort
     * @param work a workspace array (slice)
     * @param workBase origin of usable space in work array
     * @param workLen usable size of work array
     */
    private TimSort(T[] a, Comparator<? super T> c, T[] work, int workBase, int workLen) {
        this.a = a;
        this.c = c;

        // Allocate temp storage (which may be increased later if necessary)
        int len = a.length;
        int tlen = (len < 2 * INITIAL_TMP_STORAGE_LENGTH) ?
            len >>> 1 : INITIAL_TMP_STORAGE_LENGTH;
        if (work == null || workLen < tlen || workBase + tlen > work.length) {
            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            T[] newArray = (T[])java.lang.reflect.Array.newInstance
                (a.getClass().getComponentType(), tlen);
            tmp = newArray;
            tmpBase = 0;
            tmpLen = tlen;
        }
        else {
            tmp = work;
            tmpBase = workBase;
            tmpLen = workLen;
        }

        /*
         * Allocate runs-to-be-merged stack (which cannot be expanded).  The
         * stack length requirements are described in listsort.txt.  The C
         * version always uses the same stack length (85), but this was
         * measured to be too expensive when sorting "mid-sized" arrays (e.g.,
         * 100 elements) in Java.  Therefore, we use smaller (but sufficiently
         * large) stack lengths for smaller arrays.  The "magic numbers" in the
         * computation below must be changed if MIN_MERGE is decreased.  See
         * the MIN_MERGE declaration above for more information.
         * The maximum value of 49 allows for an array up to length
         * Integer.MAX_VALUE-4, if array is filled by the worst case stack size
         * increasing scenario. More explanations are given in section 4 of:
         * http://envisage-project.eu/wp-content/uploads/2015/02/sorting.pdf
         */
        int stackLen = (len <    120  ?  5 :
                        len <   1542  ? 10 :
                        len < 119151  ? 24 : 49);
        runBase = new int[stackLen];
        runLen = new int[stackLen];
    }

    /*
     * The next method (package private and static) constitutes the
     * entire API of this class.
     */

    /**
     * Sorts the given range, using the given workspace array slice
     * for temp storage when possible. This method is designed to be
     * invoked from public methods (in class Arrays) after performing
     * any necessary array bounds checks and expanding parameters into
     * the required forms.
     *
     * @param a the array to be sorted 待排序的数组
     * @param lo the index of the first element, inclusive, to be sorted 待排序的序列的开始元素位置
     * @param hi the index of the last element, exclusive, to be sorted 待排序的序列的结束元素位置
     * @param c the comparator to use 比较器
     * @param work a workspace array (slice) 临时数组
     * @param workBase origin of usable space in work array
     * @param workLen usable size of work array
     * @since 1.8
     * 将指定的序列进行排序
     */
    static <T> void sort(T[] a, int lo, int hi, Comparator<? super T> c,
                         T[] work, int workBase, int workLen) {
        assert c != null && a != null && lo >= 0 && lo <= hi && hi <= a.length;

        // 序列中元素个数
        int nRemaining  = hi - lo;
        // 序列中包含0个或者1个元素，直接返回，因为这都是已经排好序的
        if (nRemaining < 2)
            return;  // Arrays of size 0 and 1 are always sorted

        // If array is small, do a "mini-TimSort" with no merges
        // 元素个数比较小的时候使用二分插入排序，默认是小于32，python中默认是64
        if (nRemaining < MIN_MERGE) {
            // 统计lo和hi之间的最长的升序序列，initRunLen就是最长升序序列的长度
            // 统计完后，下面二分插入排序就可以直接跳过前面升序的部分，从后面未排序
            // 的位置开始进行排序，减少排序数据量
            int initRunLen = countRunAndMakeAscending(a, lo, hi, c);
            // 二分插入排序
            binarySort(a, lo, hi, lo + initRunLen, c);
            return;
        }

        /**
         * March over the array once, left to right, finding natural runs,
         * extending short natural runs to minRun elements, and merging runs
         * to maintain stack invariant.
         */
        TimSort<T> ts = new TimSort<>(a, c, work, workBase, workLen);
        // 选取run的最小长度
        int minRun = minRunLength(nRemaining);
        do {
            // Identify next run
            // 统计lo和hi之间的最长的升序序列，initRunLen就是最长升序序列的长度
            // 这里操作完之后，lo和hi之间前一部分的序列是个升序序列
            int runLen = countRunAndMakeAscending(a, lo, hi, c);

            // If run is short, extend to min(minRun, nRemaining)
            // 如果lo和hi之间的最长的升序序列比minRun还小，
            // 需要将lo和hi之间的升序序列进行扩充，扩充后的run的长度是minRun或者是序列长度
            if (runLen < minRun) {
                int force = nRemaining <= minRun ? nRemaining : minRun;
                // 由于lo和hi之间升序序列比minRun小，所以需要把后半部分未排序的序列中的元素扩充到run中
                // 二分插入排序
                binarySort(a, lo, lo + force, lo + runLen, c);
                runLen = force;
            }

            // Push run onto pending-run stack, and maybe merge
            // 将一个run压入栈中
            ts.pushRun(lo, runLen);
            // 检查是不是需要合并run
            ts.mergeCollapse();

            // Advance to find next run
            // 继续往左边找下一个run
            // lo的位置变为上一个run长度后面的索引
            lo += runLen;
            // 剩余元素是总的减去上一个run长度
            nRemaining -= runLen;
        } while (nRemaining != 0);

        // Merge all remaining runs to complete sort
        assert lo == hi;
        // 最后合并run
        ts.mergeForceCollapse();
        // 最终合并完所有run后，只剩下一个run
        assert ts.stackSize == 1;
    }

    /**
     * Sorts the specified portion of the specified array using a binary
     * insertion sort.  This is the best method for sorting small numbers
     * of elements.  It requires O(n log n) compares, but O(n^2) data
     * movement (worst case).
     *
     * If the initial part of the specified range is already sorted,
     * this method can take advantage of it: the method assumes that the
     * elements from index {@code lo}, inclusive, to {@code start},
     * exclusive are already sorted.
     *
     * @param a the array in which a range is to be sorted
     * @param lo the index of the first element in the range to be sorted
     * @param hi the index after the last element in the range to be sorted
     * @param start the index of the first element in the range that is
     *        not already known to be sorted ({@code lo <= start <= hi})
     * @param c comparator to used for the sort
     * 二分插入排序
     * lo到start之间的元素是排好序的，这里排序直接从start位置开始
     */
    @SuppressWarnings("fallthrough")
    private static <T> void binarySort(T[] a, int lo, int hi, int start,
                                       Comparator<? super T> c) {
        assert lo <= start && start <= hi;
        if (start == lo)
            start++;
        // 需要排序的位置是start到hi
        for ( ; start < hi; start++) {
            // 选取第一个未排序的元素
            T pivot = a[start];

            // Set left (and right) to the index where a[start] (pivot) belongs
            // 已排序序列的最左边元素索引
            int left = lo;
            // 未排序序列的最左边元素索引
            int right = start;
            assert left <= right;
            /*
             * Invariants:
             *   pivot >= all in [lo, left).
             *   pivot <  all in [right, start).
             */
            while (left < right) {
                int mid = (left + right) >>> 1;
                // 未排序元素pivot比a[mid]小，说明在左边的序列中
                if (c.compare(pivot, a[mid]) < 0)
                    right = mid;
                else
                    // 说明未排序元素pivot在右边的序列中
                    left = mid + 1;
            }
            // 循环完后，left == right，找到了pivot的位置位left
            assert left == right;

            /*
             * The invariants still hold: pivot >= all in [lo, left) and
             * pivot < all in [left, start), so pivot belongs at left.  Note
             * that if there are elements equal to pivot, left points to the
             * first slot after them -- that's why this sort is stable.
             * Slide elements over to make room for pivot.
             * pivot >= [lo, left) && pivot < [left, start)
             * 所以pivot的位置就是left
             * 需要将[left, start)的元素整体向右移动一位
             * n就是要移动的元素个数
             */
            int n = start - left;  // The number of elements to move
            // Switch is just an optimization for arraycopy in default case
            // 1个元素或者是2个元素需要移动，直接移动元素，无需调用System.arraycopy
            // 这里的switch用的很6666666
            switch (n) {
                case 2:  a[left + 2] = a[left + 1];
                case 1:  a[left + 1] = a[left];
                         break;
                default: System.arraycopy(a, left, a, left + 1, n);
            }
            // 元素移动完后，将pivot放到left位置
            a[left] = pivot;
        }
    }

    /**
     * Returns the length of the run beginning at the specified position in
     * the specified array and reverses the run if it is descending (ensuring
     * that the run will always be ascending when the method returns).
     *
     * A run is the longest ascending sequence with:
     *
     *    a[lo] <= a[lo + 1] <= a[lo + 2] <= ...
     *
     * or the longest descending sequence with:
     *
     *    a[lo] >  a[lo + 1] >  a[lo + 2] >  ...
     *
     * For its intended use in a stable mergesort, the strictness of the
     * definition of "descending" is needed so that the call can safely
     * reverse a descending sequence without violating stability.
     *
     * @param a the array in which a run is to be counted and possibly reversed
     * @param lo index of the first element in the run run中的第一个元素位置
     * @param hi index after the last element that may be contained in the run.
              It is required that {@code lo < hi}. run中最后一个元素的后面一个元素的位置
     * @param c the comparator to used for the sort
     * @return  the length of the run beginning at the specified position in
     *          the specified array
     * 返回一个升序的run的长度，如果序列是降序，会将序列进行翻转成升序
     */
    private static <T> int countRunAndMakeAscending(T[] a, int lo, int hi,
                                                    Comparator<? super T> c) {
        assert lo < hi;
        // 一个run后面的一个元素的索引
        int runHi = lo + 1;
        // 说明序列中只有两个元素，升序序列个数为1
        if (runHi == hi)
            return 1;

        // Find end of run, and reverse range if descending
        // 开头两个元素对比，a[low] > a[runHi] 认为是降序
        if (c.compare(a[runHi++], a[lo]) < 0) { // Descending
            // 降序的序列，一直找到不是降序为止
            while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) < 0)
                runHi++;
            // 从low一直到runHi的元素都是降序，需要反转成升序
            reverseRange(a, lo, runHi);
        }
        // 开头两个元素对比后，认为是升序
        else {                              // Ascending
            // 升序的序列，一直找到不是升序为止
            while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) >= 0)
                runHi++;
            // 循环完后从low到runHi的元素序列是升序
        }

        // 这里是升序序列中的个数
        return runHi - lo;
    }

    /**
     * Reverse the specified range of the specified array.
     *
     * @param a the array in which a range is to be reversed
     * @param lo the index of the first element in the range to be reversed
     * @param hi the index after the last element in the range to be reversed
     * 将数组进行逆序排列，分别从头尾开始交换元素位置
     */
    private static void reverseRange(Object[] a, int lo, int hi) {
        hi--;
        while (lo < hi) {
            Object t = a[lo];
            a[lo++] = a[hi];
            a[hi--] = t;
        }
    }

    /**
     * Returns the minimum acceptable run length for an array of the specified
     * length. Natural runs shorter than this will be extended with
     * {@link #binarySort}.
     *
     * Roughly speaking, the computation is:
     *
     *  If n < MIN_MERGE, return n (it's too small to bother with fancy stuff).
     *  Else if n is an exact power of 2, return MIN_MERGE/2.
     *  Else return an int k, MIN_MERGE/2 <= k <= MIN_MERGE, such that n/k
     *   is close to, but strictly less than, an exact power of 2.
     *
     * For the rationale, see listsort.txt.
     *
     * @param n the length of the array to be sorted
     * @return the length of the minimum run to be merged
     * 这里是根据输入的n获取最小的run长度
     * 如果n小于32，则run长度为n
     * 如果n大于等于32，并且n是2的幂，则返回n/2
     * 如果n大于等于32，n不是2的幂，则选择一个k，k满足：MIN_MERGE/2 <= k <= MIN_MERGE
     */
    private static int minRunLength(int n) {
        assert n >= 0;
        int r = 0;      // Becomes 1 if any 1 bits are shifted off
        while (n >= MIN_MERGE) {
            r |= (n & 1);
            n >>= 1;
        }
        return n + r;
    }

    /**
     * Pushes the specified run onto the pending-run stack.
     *
     * @param runBase index of the first element in the run
     * @param runLen  the number of elements in the run
     */
    private void pushRun(int runBase, int runLen) {
        this.runBase[stackSize] = runBase;
        this.runLen[stackSize] = runLen;
        stackSize++;
    }

    /**
     * Examines the stack of runs waiting to be merged and merges adjacent runs
     * until the stack invariants are reestablished:
     *
     *     1. runLen[i - 3] > runLen[i - 2] + runLen[i - 1]
     *     2. runLen[i - 2] > runLen[i - 1]
     *
     * This method is called each time a new run is pushed onto the stack,
     * so the invariants are guaranteed to hold for i < stackSize upon
     * entry to the method.
     * 假设栈顶三个run的元素个数依次是C、B、A
     * 需要满足如下条件：
     * A > B + C
     * B > C
     * 如果不满足这样的条件，需要将相邻两个run合并，一直到满足条件
     */
    private void mergeCollapse() {
        while (stackSize > 1) {
            // n是C、B、A中B的索引的位置
            int n = stackSize - 2;
            // A <= B + C
            if (n > 0 && runLen[n-1] <= runLen[n] + runLen[n+1]) {
                // A < C
                if (runLen[n - 1] < runLen[n + 1])
                    n--;
                // 如果 A < C 则合并A、B，如果A > C则合并B、C
                mergeAt(n);
            }
            // B <= C
            else if (runLen[n] <= runLen[n + 1]) {
                // 合并B、C
                mergeAt(n);
            } else {
                break; // Invariant is established
            }
        }
    }

    /**
     * Merges all runs on the stack until only one remains.  This method is
     * called once, to complete the sort.
     */
    private void mergeForceCollapse() {
        while (stackSize > 1) {
            int n = stackSize - 2;
            if (n > 0 && runLen[n - 1] < runLen[n + 1])
                n--;
            mergeAt(n);
        }
    }

    /**
     * Merges the two runs at stack indices i and i+1.  Run i must be
     * the penultimate or antepenultimate run on the stack.  In other words,
     * i must be equal to stackSize-2 or stackSize-3.
     *
     * @param i stack index of the first of the two runs to merge
     * 合并两个run，要合并的两个run的索引是i和i+1
     */
    private void mergeAt(int i) {
        assert stackSize >= 2;
        assert i >= 0;
        // 合并的索引位置要么是栈顶第2个元素，要么是栈顶第3个元素
        assert i == stackSize - 2 || i == stackSize - 3;

        // 栈里i位置处的run对应在待排序序列中的索引位置为base1
        int base1 = runBase[i];
        // 栈里i位置处的run的长度
        int len1 = runLen[i];
        // 栈里i+1位置处的run对应在待排序序列中的索引位置为base2
        int base2 = runBase[i + 1];
        // 栈里i+1位置处的run的长度
        int len2 = runLen[i + 1];
        assert len1 > 0 && len2 > 0;
        // 两个run在待排序序列中位置是相邻的
        assert base1 + len1 == base2;

        /*
         * Record the length of the combined runs; if i is the 3rd-last
         * run now, also slide over the last run (which isn't involved
         * in this merge).  The current run (i+1) goes away in any case.
         */
        // i和i+1处的两个run合并后，还放到i位置，长度是两个run的和
        runLen[i] = len1 + len2;
        // i是栈顶第三个run，i和i+1合并后变成i，则i+2应该变成i+1
        if (i == stackSize - 3) {
            runBase[i + 1] = runBase[i + 2];
            runLen[i + 1] = runLen[i + 2];
        }
        // 两个run合并后，栈的大小减少1
        stackSize--;

        /*
         * Find where the first element of run2 goes in run1. Prior elements
         * in run1 can be ignored (because they're already in place).
         * i位置处是run1，在左边（下边），i+1位置处是run2，在右边（上边）
         * 这里是看下run2的首元素可以放到run1的哪个位置，这样有可能让run1左边的部分数据不需要移动
         * k是run1的某个索引位置
         */
        int k = gallopRight(a[base2], a, base1, len1, 0, c);
        assert k >= 0;
        // run1的前面部分元素无需进行比较，从base1+k之后开始进行比较
        base1 += k;
        // run1中待比较的长度也要变化
        len1 -= k;
        // run2第一个元素都比run1大，两个run直接合并一起就是排好序的
        if (len1 == 0)
            return;

        /*
         * Find where the last element of run1 goes in run2. Subsequent elements
         * in run2 can be ignored (because they're already in place).
         * 看下run1的最后一个元素可以插入到run2的什么位置，这样的话run2后面的元素就不需要参与归并了
         */
        len2 = gallopLeft(a[base1 + len1 - 1], a, base2, len2, len2 - 1, c);
        assert len2 >= 0;
        if (len2 == 0)
            return;

        // Merge remaining runs, using tmp array with min(len1, len2) elements
        // 归并排序run1和run2剩余部分的元素
        if (len1 <= len2)
            mergeLo(base1, len1, base2, len2);
        else
            mergeHi(base1, len1, base2, len2);
    }

    /**
     * Locates the position at which to insert the specified key into the
     * specified sorted range; if the range contains an element equal to key,
     * returns the index of the leftmost equal element.
     *
     * @param key the key whose insertion point to search for run1的最后一个元素
     * @param a the array in which to search 原始数组
     * @param base the index of the first element in the range run2第一个元素的索引
     * @param len the length of the range; must be > 0 run2的长度
     * @param hint the index at which to begin the search, 0 <= hint < n.
     *     The closer hint is to the result, the faster this method will run. run2的长度减1
     * @param c the comparator used to order the range, and to search
     * @return the int k,  0 <= k <= n such that a[b + k - 1] < key <= a[b + k],
     *    pretending that a[b - 1] is minus infinity and a[b + n] is infinity.
     *    In other words, key belongs at index b + k; or in other words,
     *    the first k elements of a should precede key, and the last n - k
     *    should follow it.
     *
     * 看run1的最后一个元素应该插入到run2的哪个位置
     */
    private static <T> int gallopLeft(T key, T[] a, int base, int len, int hint,
                                      Comparator<? super T> c) {
        assert len > 0 && hint >= 0 && hint < len;
        int lastOfs = 0;
        int ofs = 1;
        // run1的最后一个元素和run2的最后一个元素比较
        // 如果run1的最后一个元素大于run2的最后一个元素，则从run2的最后往右边找
        if (c.compare(key, a[base + hint]) > 0) {
            // Gallop right until a[base+hint+lastOfs] < key <= a[base+hint+ofs]
            // maxOfs = 1
            int maxOfs = len - hint;
            while (ofs < maxOfs && c.compare(key, a[base + hint + ofs]) > 0) {
                lastOfs = ofs;
                ofs = (ofs << 1) + 1;
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to base
            lastOfs += hint;
            ofs += hint;
        } else { // key <= a[base + hint]
            // Gallop left until a[base+hint-ofs] < key <= a[base+hint-lastOfs]
            final int maxOfs = hint + 1;
            while (ofs < maxOfs && c.compare(key, a[base + hint - ofs]) <= 0) {
                lastOfs = ofs;
                ofs = (ofs << 1) + 1;
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to base
            int tmp = lastOfs;
            lastOfs = hint - ofs;
            ofs = hint - tmp;
        }
        assert -1 <= lastOfs && lastOfs < ofs && ofs <= len;

        /*
         * Now a[base+lastOfs] < key <= a[base+ofs], so key belongs somewhere
         * to the right of lastOfs but no farther right than ofs.  Do a binary
         * search, with invariant a[base + lastOfs - 1] < key <= a[base + ofs].
         */
        lastOfs++;
        while (lastOfs < ofs) {
            int m = lastOfs + ((ofs - lastOfs) >>> 1);

            if (c.compare(key, a[base + m]) > 0)
                lastOfs = m + 1;  // a[base + m] < key
            else
                ofs = m;          // key <= a[base + m]
        }
        assert lastOfs == ofs;    // so a[base + ofs - 1] < key <= a[base + ofs]
        return ofs;
    }

    /**
     * Like gallopLeft, except that if the range contains an element equal to
     * key, gallopRight returns the index after the rightmost equal element.
     *
     * @param key the key whose insertion point to search for run2开始的元素
     * @param a the array in which to search 待排序数组
     * @param base the index of the first element in the range run1开始索引
     * @param len the length of the range; must be > 0 run1长度
     * @param hint the index at which to begin the search, 0 <= hint < n.
     *     The closer hint is to the result, the faster this method will run.
     *             搜索开始位置，从run1的hint位置开始查找，这里是0
     * @param c the comparator used to order the range, and to search
     * @return the int k,  0 <= k <= n such that a[b + k - 1] <= key < a[b + k]
     * 找run2的第一个元素应该放到run1中的哪个位置
     */
    private static <T> int gallopRight(T key, T[] a, int base, int len,
                                       int hint, Comparator<? super T> c) {
        assert len > 0 && hint >= 0 && hint < len;

        int ofs = 1;
        int lastOfs = 0;
        // run2第一个元素比run1的第一个元素小，可以直接返回0，不明白为啥还要有下面一大坨？
        if (c.compare(key, a[base + hint]) < 0) {
            // Gallop left until a[b+hint - ofs] <= key < a[b+hint - lastOfs]
            // maxOfs = 1
            int maxOfs = hint + 1;
            // ofs = 1，所以这里循环进不去
            while (ofs < maxOfs && c.compare(key, a[base + hint - ofs]) < 0) {
                lastOfs = ofs;
                ofs = (ofs << 1) + 1;
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            // ofs = maxOfs = 1
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to b
            // tmp = 0
            int tmp = lastOfs;
            // lastOfs = 0 - 1 = -1
            lastOfs = hint - ofs;
            // ofs = 0 - 0 = 0
            ofs = hint - tmp;
        }
        // run2第一个元素大于等于run1的第一个元素
        else { // a[b + hint] <= key
            // Gallop right until a[b+hint + lastOfs] <= key < a[b+hint + ofs]
            // 最大的offset是run1的长度
            int maxOfs = len - hint;
            // 从run1中一直找，一直找到key < run1中的元素
            while (ofs < maxOfs && c.compare(key, a[base + hint + ofs]) >= 0) {
                //
                lastOfs = ofs;
                // ofs = 2ofs + 1
                ofs = (ofs << 1) + 1;
                // 防止溢出
                if (ofs <= 0)   // int overflow
                    ofs = maxOfs;
            }
            // run1遍历完
            if (ofs > maxOfs)
                ofs = maxOfs;

            // Make offsets relative to b
            // hint = 0，这里lastOfs和ofs都不变
            lastOfs += hint;
            ofs += hint;
        }
        assert -1 <= lastOfs && lastOfs < ofs && ofs <= len;

        /*
         * Now a[b + lastOfs] <= key < a[b + ofs], so key belongs somewhere to
         * the right of lastOfs but no farther right than ofs.  Do a binary
         * search, with invariant a[b + lastOfs - 1] <= key < a[b + ofs].
         * base是run1的开始的索引
         * hint是搜索开始位置，从run1的hint位置开始查找，这里是0
         * 现在 run1[lastOfs] <= key < run1[ofs]
         * 在[lastOfs, ofs]之间做二分查找
         */
        lastOfs++;
        while (lastOfs < ofs) {
            int m = lastOfs + ((ofs - lastOfs) >>> 1);

            if (c.compare(key, a[base + m]) < 0)
                ofs = m;          // key < a[b + m]
            else
                lastOfs = m + 1;  // a[b + m] <= key
        }
        assert lastOfs == ofs;    // so a[b + ofs - 1] <= key < a[b + ofs]
        return ofs;
    }

    /**
     * Merges two adjacent runs in place, in a stable fashion.  The first
     * element of the first run must be greater than the first element of the
     * second run (a[base1] > a[base2]), and the last element of the first run
     * (a[base1 + len1-1]) must be greater than all elements of the second run.
     * 归并排序run1剩余部分和run2剩余部分
     * run1剩余部分第一个元素大于run2剩余部分第一个元素
     * run1剩余部分最后一个元素大于run2剩余部分所有元素
     *
     * 此时len1 <= len2
     *
     * For performance, this method should be called only when len1 <= len2;
     * its twin, mergeHi should be called if len1 >= len2.  (Either method
     * may be called if len1 == len2.)
     *
     * @param base1 index of first element in first run to be merged
     * @param len1  length of first run to be merged (must be > 0)
     * @param base2 index of first element in second run to be merged
     *        (must be aBase + aLen)
     * @param len2  length of second run to be merged (must be > 0)
     */
    private void mergeLo(int base1, int len1, int base2, int len2) {
        assert len1 > 0 && len2 > 0 && base1 + len1 == base2;

        // Copy first run into temp array
        T[] a = this.a; // For performance
        T[] tmp = ensureCapacity(len1);
        int cursor1 = tmpBase; // Indexes into tmp array
        int cursor2 = base2;   // Indexes int a
        int dest = base1;      // Indexes int a
        System.arraycopy(a, base1, tmp, cursor1, len1);

        // Move first element of second run and deal with degenerate cases
        a[dest++] = a[cursor2++];
        if (--len2 == 0) {
            System.arraycopy(tmp, cursor1, a, dest, len1);
            return;
        }
        if (len1 == 1) {
            System.arraycopy(a, cursor2, a, dest, len2);
            a[dest + len2] = tmp[cursor1]; // Last elt of run 1 to end of merge
            return;
        }

        Comparator<? super T> c = this.c;  // Use local variable for performance
        int minGallop = this.minGallop;    //  "    "       "     "      "
    outer:
        while (true) {
            int count1 = 0; // Number of times in a row that first run won
            int count2 = 0; // Number of times in a row that second run won

            /*
             * Do the straightforward thing until (if ever) one run starts
             * winning consistently.
             */
            do {
                assert len1 > 1 && len2 > 0;
                if (c.compare(a[cursor2], tmp[cursor1]) < 0) {
                    a[dest++] = a[cursor2++];
                    count2++;
                    count1 = 0;
                    if (--len2 == 0)
                        break outer;
                } else {
                    a[dest++] = tmp[cursor1++];
                    count1++;
                    count2 = 0;
                    if (--len1 == 1)
                        break outer;
                }
            } while ((count1 | count2) < minGallop);

            /*
             * One run is winning so consistently that galloping may be a
             * huge win. So try that, and continue galloping until (if ever)
             * neither run appears to be winning consistently anymore.
             */
            do {
                assert len1 > 1 && len2 > 0;
                count1 = gallopRight(a[cursor2], tmp, cursor1, len1, 0, c);
                if (count1 != 0) {
                    System.arraycopy(tmp, cursor1, a, dest, count1);
                    dest += count1;
                    cursor1 += count1;
                    len1 -= count1;
                    if (len1 <= 1) // len1 == 1 || len1 == 0
                        break outer;
                }
                a[dest++] = a[cursor2++];
                if (--len2 == 0)
                    break outer;

                count2 = gallopLeft(tmp[cursor1], a, cursor2, len2, 0, c);
                if (count2 != 0) {
                    System.arraycopy(a, cursor2, a, dest, count2);
                    dest += count2;
                    cursor2 += count2;
                    len2 -= count2;
                    if (len2 == 0)
                        break outer;
                }
                a[dest++] = tmp[cursor1++];
                if (--len1 == 1)
                    break outer;
                minGallop--;
            } while (count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
            if (minGallop < 0)
                minGallop = 0;
            minGallop += 2;  // Penalize for leaving gallop mode
        }  // End of "outer" loop
        this.minGallop = minGallop < 1 ? 1 : minGallop;  // Write back to field

        if (len1 == 1) {
            assert len2 > 0;
            System.arraycopy(a, cursor2, a, dest, len2);
            a[dest + len2] = tmp[cursor1]; //  Last elt of run 1 to end of merge
        } else if (len1 == 0) {
            throw new IllegalArgumentException(
                "Comparison method violates its general contract!");
        } else {
            assert len2 == 0;
            assert len1 > 1;
            System.arraycopy(tmp, cursor1, a, dest, len1);
        }
    }

    /**
     * Like mergeLo, except that this method should be called only if
     * len1 >= len2; mergeLo should be called if len1 <= len2.  (Either method
     * may be called if len1 == len2.)
     *
     * @param base1 index of first element in first run to be merged
     * @param len1  length of first run to be merged (must be > 0)
     * @param base2 index of first element in second run to be merged
     *        (must be aBase + aLen)
     * @param len2  length of second run to be merged (must be > 0)
     */
    private void mergeHi(int base1, int len1, int base2, int len2) {
        assert len1 > 0 && len2 > 0 && base1 + len1 == base2;

        // Copy second run into temp array
        T[] a = this.a; // For performance
        T[] tmp = ensureCapacity(len2);
        int tmpBase = this.tmpBase;
        System.arraycopy(a, base2, tmp, tmpBase, len2);

        int cursor1 = base1 + len1 - 1;  // Indexes into a
        int cursor2 = tmpBase + len2 - 1; // Indexes into tmp array
        int dest = base2 + len2 - 1;     // Indexes into a

        // Move last element of first run and deal with degenerate cases
        a[dest--] = a[cursor1--];
        if (--len1 == 0) {
            System.arraycopy(tmp, tmpBase, a, dest - (len2 - 1), len2);
            return;
        }
        if (len2 == 1) {
            dest -= len1;
            cursor1 -= len1;
            System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
            a[dest] = tmp[cursor2];
            return;
        }

        Comparator<? super T> c = this.c;  // Use local variable for performance
        int minGallop = this.minGallop;    //  "    "       "     "      "
    outer:
        while (true) {
            int count1 = 0; // Number of times in a row that first run won
            int count2 = 0; // Number of times in a row that second run won

            /*
             * Do the straightforward thing until (if ever) one run
             * appears to win consistently.
             */
            do {
                assert len1 > 0 && len2 > 1;
                if (c.compare(tmp[cursor2], a[cursor1]) < 0) {
                    a[dest--] = a[cursor1--];
                    count1++;
                    count2 = 0;
                    if (--len1 == 0)
                        break outer;
                } else {
                    a[dest--] = tmp[cursor2--];
                    count2++;
                    count1 = 0;
                    if (--len2 == 1)
                        break outer;
                }
            } while ((count1 | count2) < minGallop);

            /*
             * One run is winning so consistently that galloping may be a
             * huge win. So try that, and continue galloping until (if ever)
             * neither run appears to be winning consistently anymore.
             */
            do {
                assert len1 > 0 && len2 > 1;
                count1 = len1 - gallopRight(tmp[cursor2], a, base1, len1, len1 - 1, c);
                if (count1 != 0) {
                    dest -= count1;
                    cursor1 -= count1;
                    len1 -= count1;
                    System.arraycopy(a, cursor1 + 1, a, dest + 1, count1);
                    if (len1 == 0)
                        break outer;
                }
                a[dest--] = tmp[cursor2--];
                if (--len2 == 1)
                    break outer;

                count2 = len2 - gallopLeft(a[cursor1], tmp, tmpBase, len2, len2 - 1, c);
                if (count2 != 0) {
                    dest -= count2;
                    cursor2 -= count2;
                    len2 -= count2;
                    System.arraycopy(tmp, cursor2 + 1, a, dest + 1, count2);
                    if (len2 <= 1)  // len2 == 1 || len2 == 0
                        break outer;
                }
                a[dest--] = a[cursor1--];
                if (--len1 == 0)
                    break outer;
                minGallop--;
            } while (count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
            if (minGallop < 0)
                minGallop = 0;
            minGallop += 2;  // Penalize for leaving gallop mode
        }  // End of "outer" loop
        this.minGallop = minGallop < 1 ? 1 : minGallop;  // Write back to field

        if (len2 == 1) {
            assert len1 > 0;
            dest -= len1;
            cursor1 -= len1;
            System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
            a[dest] = tmp[cursor2];  // Move first elt of run2 to front of merge
        } else if (len2 == 0) {
            throw new IllegalArgumentException(
                "Comparison method violates its general contract!");
        } else {
            assert len1 == 0;
            assert len2 > 0;
            System.arraycopy(tmp, tmpBase, a, dest - (len2 - 1), len2);
        }
    }

    /**
     * Ensures that the external array tmp has at least the specified
     * number of elements, increasing its size if necessary.  The size
     * increases exponentially to ensure amortized linear time complexity.
     *
     * @param minCapacity the minimum required capacity of the tmp array
     * @return tmp, whether or not it grew
     */
    private T[] ensureCapacity(int minCapacity) {
        if (tmpLen < minCapacity) {
            // Compute smallest power of 2 > minCapacity
            int newSize = minCapacity;
            newSize |= newSize >> 1;
            newSize |= newSize >> 2;
            newSize |= newSize >> 4;
            newSize |= newSize >> 8;
            newSize |= newSize >> 16;
            newSize++;

            if (newSize < 0) // Not bloody likely!
                newSize = minCapacity;
            else
                newSize = Math.min(newSize, a.length >>> 1);

            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            T[] newArray = (T[])java.lang.reflect.Array.newInstance
                (a.getClass().getComponentType(), newSize);
            tmp = newArray;
            tmpLen = newSize;
            tmpBase = 0;
        }
        return tmp;
    }
}
