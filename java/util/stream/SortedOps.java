/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.IntFunction;


/**
 * Factory methods for transforming streams into sorted streams.
 *
 * @since 1.8
 */
final class SortedOps {

    private SortedOps() { }

    /**
     * Appends a "sorted" operation to the provided stream.
     *
     * @param <T> the type of both input and output elements
     * @param upstream a reference stream with element type T
     *
     * 使用SortedOps.ofRef有状态的阶段对象来包装排序逻辑。
     */
    static <T> Stream<T> makeRef(AbstractPipeline<?, T, ?> upstream) {
        return new OfRef<>(upstream);
    }

    /**
     * Appends a "sorted" operation to the provided stream.
     *
     * @param <T> the type of both input and output elements
     * @param upstream a reference stream with element type T
     * @param comparator the comparator to order elements by
     */
    static <T> Stream<T> makeRef(AbstractPipeline<?, T, ?> upstream,
                                Comparator<? super T> comparator) {
        return new OfRef<>(upstream, comparator);
    }

    /**
     * Appends a "sorted" operation to the provided stream.
     *
     * @param <T> the type of both input and output elements
     * @param upstream a reference stream with element type T
     */
    static <T> IntStream makeInt(AbstractPipeline<?, Integer, ?> upstream) {
        return new OfInt(upstream);
    }

    /**
     * Appends a "sorted" operation to the provided stream.
     *
     * @param <T> the type of both input and output elements
     * @param upstream a reference stream with element type T
     */
    static <T> LongStream makeLong(AbstractPipeline<?, Long, ?> upstream) {
        return new OfLong(upstream);
    }

    /**
     * Appends a "sorted" operation to the provided stream.
     *
     * @param <T> the type of both input and output elements
     * @param upstream a reference stream with element type T
     */
    static <T> DoubleStream makeDouble(AbstractPipeline<?, Double, ?> upstream) {
        return new OfDouble(upstream);
    }

    /**
     * Specialized subtype for sorting reference streams
     */
    private static final class OfRef<T> extends ReferencePipeline.StatefulOp<T, T> {
        /**
         * Comparator used for sorting
         */
        private final boolean isNaturalSort;
        private final Comparator<? super T> comparator;

        /**
         * Sort using natural order of {@literal <T>} which must be
         * {@code Comparable}.
         */
        OfRef(AbstractPipeline<?, T, ?> upstream) {
            super(upstream, StreamShape.REFERENCE,
                  StreamOpFlag.IS_ORDERED | StreamOpFlag.IS_SORTED);
            this.isNaturalSort = true;
            // Will throw CCE when we try to sort if T is not Comparable
            @SuppressWarnings("unchecked")
            Comparator<? super T> comp = (Comparator<? super T>) Comparator.naturalOrder();
            this.comparator = comp;
        }

        /**
         * Sort using the provided comparator.
         *
         * @param comparator The comparator to be used to evaluate ordering.
         */
        OfRef(AbstractPipeline<?, T, ?> upstream, Comparator<? super T> comparator) {
            super(upstream, StreamShape.REFERENCE,
                  StreamOpFlag.IS_ORDERED | StreamOpFlag.NOT_SORTED);
            this.isNaturalSort = false;
            this.comparator = Objects.requireNonNull(comparator);
        }

        /**
         *
         * @param flags The combined stream and operation flags up to, but not
         *        including, this operation
         * @param sink sink to which elements should be sent after processing
         * @return
         *
         * sink是下一个要执行的Sink，该方法是将下一个Sink放到当前Sink中，当前Sink处理完数据后，就可以调用下一个Sink继续处理
         */
        @Override
        public Sink<T> opWrapSink(int flags, Sink<T> sink) {
            Objects.requireNonNull(sink);

            // If the input is already naturally sorted and this operation
            // also naturally sorted then this is a no-op
            if (StreamOpFlag.SORTED.isKnown(flags) && isNaturalSort)
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedRefSortingSink<>(sink, comparator);
            else
                return new RefSortingSink<>(sink, comparator);
        }

        @Override
        public <P_IN> Node<T> opEvaluateParallel(PipelineHelper<T> helper,
                                                 Spliterator<P_IN> spliterator,
                                                 IntFunction<T[]> generator) {
            // If the input is already naturally sorted and this operation
            // naturally sorts then collect the output
            if (StreamOpFlag.SORTED.isKnown(helper.getStreamAndOpFlags()) && isNaturalSort) {
                return helper.evaluate(spliterator, false, generator);
            }
            else {
                // @@@ Weak two-pass parallel implementation; parallel collect, parallel sort
                T[] flattenedData = helper.evaluate(spliterator, true, generator).asArray(generator);
                Arrays.parallelSort(flattenedData, comparator);
                return Nodes.node(flattenedData);
            }
        }
    }

    /**
     * Specialized subtype for sorting int streams.
     */
    private static final class OfInt extends IntPipeline.StatefulOp<Integer> {
        OfInt(AbstractPipeline<?, Integer, ?> upstream) {
            super(upstream, StreamShape.INT_VALUE,
                  StreamOpFlag.IS_ORDERED | StreamOpFlag.IS_SORTED);
        }

        @Override
        public Sink<Integer> opWrapSink(int flags, Sink<Integer> sink) {
            Objects.requireNonNull(sink);

            if (StreamOpFlag.SORTED.isKnown(flags))
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedIntSortingSink(sink);
            else
                return new IntSortingSink(sink);
        }

        @Override
        public <P_IN> Node<Integer> opEvaluateParallel(PipelineHelper<Integer> helper,
                                                       Spliterator<P_IN> spliterator,
                                                       IntFunction<Integer[]> generator) {
            if (StreamOpFlag.SORTED.isKnown(helper.getStreamAndOpFlags())) {
                return helper.evaluate(spliterator, false, generator);
            }
            else {
                Node.OfInt n = (Node.OfInt) helper.evaluate(spliterator, true, generator);

                int[] content = n.asPrimitiveArray();
                Arrays.parallelSort(content);

                return Nodes.node(content);
            }
        }
    }

    /**
     * Specialized subtype for sorting long streams.
     */
    private static final class OfLong extends LongPipeline.StatefulOp<Long> {
        OfLong(AbstractPipeline<?, Long, ?> upstream) {
            super(upstream, StreamShape.LONG_VALUE,
                  StreamOpFlag.IS_ORDERED | StreamOpFlag.IS_SORTED);
        }

        @Override
        public Sink<Long> opWrapSink(int flags, Sink<Long> sink) {
            Objects.requireNonNull(sink);

            if (StreamOpFlag.SORTED.isKnown(flags))
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedLongSortingSink(sink);
            else
                return new LongSortingSink(sink);
        }

        @Override
        public <P_IN> Node<Long> opEvaluateParallel(PipelineHelper<Long> helper,
                                                    Spliterator<P_IN> spliterator,
                                                    IntFunction<Long[]> generator) {
            if (StreamOpFlag.SORTED.isKnown(helper.getStreamAndOpFlags())) {
                return helper.evaluate(spliterator, false, generator);
            }
            else {
                Node.OfLong n = (Node.OfLong) helper.evaluate(spliterator, true, generator);

                long[] content = n.asPrimitiveArray();
                Arrays.parallelSort(content);

                return Nodes.node(content);
            }
        }
    }

    /**
     * Specialized subtype for sorting double streams.
     */
    private static final class OfDouble extends DoublePipeline.StatefulOp<Double> {
        OfDouble(AbstractPipeline<?, Double, ?> upstream) {
            super(upstream, StreamShape.DOUBLE_VALUE,
                  StreamOpFlag.IS_ORDERED | StreamOpFlag.IS_SORTED);
        }

        @Override
        public Sink<Double> opWrapSink(int flags, Sink<Double> sink) {
            Objects.requireNonNull(sink);

            if (StreamOpFlag.SORTED.isKnown(flags))
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedDoubleSortingSink(sink);
            else
                return new DoubleSortingSink(sink);
        }

        @Override
        public <P_IN> Node<Double> opEvaluateParallel(PipelineHelper<Double> helper,
                                                      Spliterator<P_IN> spliterator,
                                                      IntFunction<Double[]> generator) {
            if (StreamOpFlag.SORTED.isKnown(helper.getStreamAndOpFlags())) {
                return helper.evaluate(spliterator, false, generator);
            }
            else {
                Node.OfDouble n = (Node.OfDouble) helper.evaluate(spliterator, true, generator);

                double[] content = n.asPrimitiveArray();
                Arrays.parallelSort(content);

                return Nodes.node(content);
            }
        }
    }

    /**
     * Abstract {@link Sink} for implementing sort on reference streams.
     *
     * <p>
     * Note: documentation below applies to reference and all primitive sinks.
     * <p>
     * Sorting sinks first accept all elements, buffering then into an array
     * or a re-sizable data structure, if the size of the pipeline is known or
     * unknown respectively.  At the end of the sink protocol those elements are
     * sorted and then pushed downstream.
     * This class records if {@link #cancellationRequested} is called.  If so it
     * can be inferred that the source pushing source elements into the pipeline
     * knows that the pipeline is short-circuiting.  In such cases sub-classes
     * pushing elements downstream will preserve the short-circuiting protocol
     * by calling {@code downstream.cancellationRequested()} and checking the
     * result is {@code false} before an element is pushed.
     * <p>
     * Note that the above behaviour is an optimization for sorting with
     * sequential streams.  It is not an error that more elements, than strictly
     * required to produce a result, may flow through the pipeline.  This can
     * occur, in general (not restricted to just sorting), for short-circuiting
     * parallel pipelines.
     */
    private static abstract class AbstractRefSortingSink<T> extends Sink.ChainedReference<T, T> {
        protected final Comparator<? super T> comparator;
        // @@@ could be a lazy final value, if/when support is added
        protected boolean cancellationWasRequested;

        AbstractRefSortingSink(Sink<? super T> downstream, Comparator<? super T> comparator) {
            super(downstream);
            this.comparator = comparator;
        }

        /**
         * Records is cancellation is requested so short-circuiting behaviour
         * can be preserved when the sorted elements are pushed downstream.
         *
         * @return false, as this sink never short-circuits.
         */
        @Override
        public final boolean cancellationRequested() {
            cancellationWasRequested = true;
            return false;
        }
    }

    /**
     * {@link Sink} for implementing sort on SIZED reference streams.
     */
    private static final class SizedRefSortingSink<T> extends AbstractRefSortingSink<T> {
        private T[] array;
        private int offset;

        SizedRefSortingSink(Sink<? super T> sink, Comparator<? super T> comparator) {
            super(sink, comparator);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            array = (T[]) new Object[(int) size];
        }

        @Override
        public void end() {
            Arrays.sort(array, 0, offset, comparator);
            downstream.begin(offset);
            if (!cancellationWasRequested) {
                for (int i = 0; i < offset; i++)
                    downstream.accept(array[i]);
            }
            else {
                for (int i = 0; i < offset && !downstream.cancellationRequested(); i++)
                    downstream.accept(array[i]);
            }
            downstream.end();
            array = null;
        }

        @Override
        public void accept(T t) {
            array[offset++] = t;
        }
    }

    /**
     * {@link Sink} for implementing sort on reference streams.
     *
     * 排序用的Sink
     */
    private static final class RefSortingSink<T> extends AbstractRefSortingSink<T> {

        /**
         * 存放排序的元素
         */
        private ArrayList<T> list;

        RefSortingSink(Sink<? super T> sink, Comparator<? super T> comparator) {
            super(sink, comparator);
        }

        /**
         *
         * @param size
         *
         * 开始操作，需要在数据进入到Sink前调用，这里初始化一个ArrayList用来存放排序的元素
         */
        @Override
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            // 初始化一个ArrayList用来存放排序的元素
            list = (size >= 0) ? new ArrayList<T>((int) size) : new ArrayList<T>();
        }

        /**
         * 结束操作，所有元素都处理完后调用，这里对元素进行排序
         */
        @Override
        public void end() {
            // 元素全部处理完后，开始排序
            list.sort(comparator);
            // 调用下游Sink的begin方法
            downstream.begin(list.size());
            // 下游Sink不包含短路操作，就将元素传递给下游的Sink的accept方法进行处理
            if (!cancellationWasRequested) {
                list.forEach(downstream::accept);
            }
            // 下游Sink包含短路操作
            else {
                // 下游Sink每处理一个元素就判断下是否可以结束操作
                for (T t : list) {
                    if (downstream.cancellationRequested()) break;
                    // 下游Sink处理元素
                    downstream.accept(t);
                }
            }
            // 调用下游Sink的end方法
            downstream.end();
            list = null;
        }

        /**
         *
         * @param t the input argument
         *
         * 接收并处理元素，这里将元素放到列表中
         */
        @Override
        public void accept(T t) {
            // 将元素放到列表中
            list.add(t);
        }
    }

    /**
     * Abstract {@link Sink} for implementing sort on int streams.
     */
    private static abstract class AbstractIntSortingSink extends Sink.ChainedInt<Integer> {
        protected boolean cancellationWasRequested;

        AbstractIntSortingSink(Sink<? super Integer> downstream) {
            super(downstream);
        }

        @Override
        public final boolean cancellationRequested() {
            cancellationWasRequested = true;
            return false;
        }
    }

    /**
     * {@link Sink} for implementing sort on SIZED int streams.
     */
    private static final class SizedIntSortingSink extends AbstractIntSortingSink {
        private int[] array;
        private int offset;

        SizedIntSortingSink(Sink<? super Integer> downstream) {
            super(downstream);
        }

        @Override
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            array = new int[(int) size];
        }

        @Override
        public void end() {
            Arrays.sort(array, 0, offset);
            downstream.begin(offset);
            if (!cancellationWasRequested) {
                for (int i = 0; i < offset; i++)
                    downstream.accept(array[i]);
            }
            else {
                for (int i = 0; i < offset && !downstream.cancellationRequested(); i++)
                    downstream.accept(array[i]);
            }
            downstream.end();
            array = null;
        }

        @Override
        public void accept(int t) {
            array[offset++] = t;
        }
    }

    /**
     * {@link Sink} for implementing sort on int streams.
     */
    private static final class IntSortingSink extends AbstractIntSortingSink {
        private SpinedBuffer.OfInt b;

        IntSortingSink(Sink<? super Integer> sink) {
            super(sink);
        }

        @Override
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            b = (size > 0) ? new SpinedBuffer.OfInt((int) size) : new SpinedBuffer.OfInt();
        }

        @Override
        public void end() {
            int[] ints = b.asPrimitiveArray();
            Arrays.sort(ints);
            downstream.begin(ints.length);
            if (!cancellationWasRequested) {
                for (int anInt : ints)
                    downstream.accept(anInt);
            }
            else {
                for (int anInt : ints) {
                    if (downstream.cancellationRequested()) break;
                    downstream.accept(anInt);
                }
            }
            downstream.end();
        }

        @Override
        public void accept(int t) {
            b.accept(t);
        }
    }

    /**
     * Abstract {@link Sink} for implementing sort on long streams.
     */
    private static abstract class AbstractLongSortingSink extends Sink.ChainedLong<Long> {
        protected boolean cancellationWasRequested;

        AbstractLongSortingSink(Sink<? super Long> downstream) {
            super(downstream);
        }

        @Override
        public final boolean cancellationRequested() {
            cancellationWasRequested = true;
            return false;
        }
    }

    /**
     * {@link Sink} for implementing sort on SIZED long streams.
     */
    private static final class SizedLongSortingSink extends AbstractLongSortingSink {
        private long[] array;
        private int offset;

        SizedLongSortingSink(Sink<? super Long> downstream) {
            super(downstream);
        }

        @Override
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            array = new long[(int) size];
        }

        @Override
        public void end() {
            Arrays.sort(array, 0, offset);
            downstream.begin(offset);
            if (!cancellationWasRequested) {
                for (int i = 0; i < offset; i++)
                    downstream.accept(array[i]);
            }
            else {
                for (int i = 0; i < offset && !downstream.cancellationRequested(); i++)
                    downstream.accept(array[i]);
            }
            downstream.end();
            array = null;
        }

        @Override
        public void accept(long t) {
            array[offset++] = t;
        }
    }

    /**
     * {@link Sink} for implementing sort on long streams.
     */
    private static final class LongSortingSink extends AbstractLongSortingSink {
        private SpinedBuffer.OfLong b;

        LongSortingSink(Sink<? super Long> sink) {
            super(sink);
        }

        @Override
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            b = (size > 0) ? new SpinedBuffer.OfLong((int) size) : new SpinedBuffer.OfLong();
        }

        @Override
        public void end() {
            long[] longs = b.asPrimitiveArray();
            Arrays.sort(longs);
            downstream.begin(longs.length);
            if (!cancellationWasRequested) {
                for (long aLong : longs)
                    downstream.accept(aLong);
            }
            else {
                for (long aLong : longs) {
                    if (downstream.cancellationRequested()) break;
                    downstream.accept(aLong);
                }
            }
            downstream.end();
        }

        @Override
        public void accept(long t) {
            b.accept(t);
        }
    }

    /**
     * Abstract {@link Sink} for implementing sort on long streams.
     */
    private static abstract class AbstractDoubleSortingSink extends Sink.ChainedDouble<Double> {
        protected boolean cancellationWasRequested;

        AbstractDoubleSortingSink(Sink<? super Double> downstream) {
            super(downstream);
        }

        @Override
        public final boolean cancellationRequested() {
            cancellationWasRequested = true;
            return false;
        }
    }

    /**
     * {@link Sink} for implementing sort on SIZED double streams.
     */
    private static final class SizedDoubleSortingSink extends AbstractDoubleSortingSink {
        private double[] array;
        private int offset;

        SizedDoubleSortingSink(Sink<? super Double> downstream) {
            super(downstream);
        }

        @Override
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            array = new double[(int) size];
        }

        @Override
        public void end() {
            Arrays.sort(array, 0, offset);
            downstream.begin(offset);
            if (!cancellationWasRequested) {
                for (int i = 0; i < offset; i++)
                    downstream.accept(array[i]);
            }
            else {
                for (int i = 0; i < offset && !downstream.cancellationRequested(); i++)
                    downstream.accept(array[i]);
            }
            downstream.end();
            array = null;
        }

        @Override
        public void accept(double t) {
            array[offset++] = t;
        }
    }

    /**
     * {@link Sink} for implementing sort on double streams.
     */
    private static final class DoubleSortingSink extends AbstractDoubleSortingSink {
        private SpinedBuffer.OfDouble b;

        DoubleSortingSink(Sink<? super Double> sink) {
            super(sink);
        }

        @Override
        public void begin(long size) {
            if (size >= Nodes.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(Nodes.BAD_SIZE);
            b = (size > 0) ? new SpinedBuffer.OfDouble((int) size) : new SpinedBuffer.OfDouble();
        }

        @Override
        public void end() {
            double[] doubles = b.asPrimitiveArray();
            Arrays.sort(doubles);
            downstream.begin(doubles.length);
            if (!cancellationWasRequested) {
                for (double aDouble : doubles)
                    downstream.accept(aDouble);
            }
            else {
                for (double aDouble : doubles) {
                    if (downstream.cancellationRequested()) break;
                    downstream.accept(aDouble);
                }
            }
            downstream.end();
        }

        @Override
        public void accept(double t) {
            b.accept(t);
        }
    }
}
