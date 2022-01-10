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

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Base interface for streams, which are sequences of elements supporting
 * sequential and parallel aggregate operations.  The following example
 * illustrates an aggregate operation using the stream types {@link Stream}
 * and {@link IntStream}, computing the sum of the weights of the red widgets:
 *
 * <pre>{@code
 *     int sum = widgets.stream()
 *                      .filter(w -> w.getColor() == RED)
 *                      .mapToInt(w -> w.getWeight())
 *                      .sum();
 * }</pre>
 *
 * See the class documentation for {@link Stream} and the package documentation
 * for <a href="package-summary.html">java.util.stream</a> for additional
 * specification of streams, stream operations, stream pipelines, and
 * parallelism, which governs the behavior of all stream types.
 *
 * @param <T> the type of the stream elements
 * @param <S> the type of of the stream implementing {@code BaseStream}
 * @since 1.8
 * @see Stream
 * @see IntStream
 * @see LongStream
 * @see DoubleStream
 * @see <a href="package-summary.html">java.util.stream</a>
 *
 * 定义了流的基本方法
 */
public interface BaseStream<T, S extends BaseStream<T, S>>
        extends AutoCloseable {
    /**
     * Returns an iterator for the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return the element iterator for this stream
     *
     * 迭代器，是一个终止操作
     */
    Iterator<T> iterator();

    /**
     * Returns a spliterator for the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return the element spliterator for this stream
     *
     * 分割迭代器，是一个终止操作
     */
    Spliterator<T> spliterator();

    /**
     * Returns whether this stream, if a terminal operation were to be executed,
     * would execute in parallel.  Calling this method after invoking an
     * terminal stream operation method may yield unpredictable results.
     *
     * @return {@code true} if this stream would execute in parallel if executed
     *
     * 是否是并行
     */
    boolean isParallel();

    /**
     * Returns an equivalent stream that is sequential.  May return
     * itself, either because the stream was already sequential, or because
     * the underlying stream state was modified to be sequential.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a sequential stream
     *
     * 返回一个等价的串行流，是一个中间操作
     */
    S sequential();

    /**
     * Returns an equivalent stream that is parallel.  May return
     * itself, either because the stream was already parallel, or because
     * the underlying stream state was modified to be parallel.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a parallel stream
     *
     * 返回一个并行流，是一个中间操作
     */
    S parallel();

    /**
     * Returns an equivalent stream that is
     * <a href="package-summary.html#Ordering">unordered</a>.  May return
     * itself, either because the stream was already unordered, or because
     * the underlying stream state was modified to be unordered.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return an unordered stream
     *
     * 返回一个无序流，是一个中间操作
     */
    S unordered();

    /**
     * Returns an equivalent stream with an additional close handler.  Close
     * handlers are run when the {@link #close()} method
     * is called on the stream, and are executed in the order they were
     * added.  All close handlers are run, even if earlier close handlers throw
     * exceptions.  If any close handler throws an exception, the first
     * exception thrown will be relayed to the caller of {@code close()}, with
     * any remaining exceptions added to that exception as suppressed exceptions
     * (unless one of the remaining exceptions is the same exception as the
     * first exception, since an exception cannot suppress itself.)  May
     * return itself.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param closeHandler A task to execute when the stream is closed
     * @return a stream with a handler that is run if the stream is closed
     *
     * 当流close的时候调用，是一个中间操作
     */
    S onClose(Runnable closeHandler);

    /**
     * Closes this stream, causing all close handlers for this stream pipeline
     * to be called.
     *
     * @see AutoCloseable#close()
     *
     * 关闭流
     */
    @Override
    void close();
}
