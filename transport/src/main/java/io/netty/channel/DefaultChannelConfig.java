/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.PlatformDependent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.AUTO_CLOSE;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.channel.ChannelOption.MAX_MESSAGES_PER_READ;
import static io.netty.channel.ChannelOption.MESSAGE_SIZE_ESTIMATOR;
import static io.netty.channel.ChannelOption.RCVBUF_ALLOCATOR;
import static io.netty.channel.ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK;
import static io.netty.channel.ChannelOption.WRITE_BUFFER_LOW_WATER_MARK;
import static io.netty.channel.ChannelOption.WRITE_SPIN_COUNT;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link SocketChannelConfig} implementation.
 */
public class DefaultChannelConfig implements ChannelConfig {
    private static final MessageSizeEstimator DEFAULT_MSG_SIZE_ESTIMATOR = DefaultMessageSizeEstimator.DEFAULT;

    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    private static final AtomicIntegerFieldUpdater<DefaultChannelConfig> AUTOREAD_UPDATER;

    static {
        AtomicIntegerFieldUpdater<DefaultChannelConfig> autoReadUpdater =
            PlatformDependent.newAtomicIntegerFieldUpdater(DefaultChannelConfig.class, "autoRead");
        if (autoReadUpdater == null) {
            autoReadUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultChannelConfig.class, "autoRead");
        }
        AUTOREAD_UPDATER = autoReadUpdater;
    }

    protected final Channel channel;

    private volatile ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
    private volatile RecvByteBufAllocator rcvBufAllocator;
    private volatile MessageSizeEstimator msgSizeEstimator = DEFAULT_MSG_SIZE_ESTIMATOR;

    private volatile int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT;
    private volatile int writeSpinCount = 16;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile int autoRead = 1;
    private volatile boolean autoClose = true;
    private volatile int writeBufferHighWaterMark = 64 * 1024;
    private volatile int writeBufferLowWaterMark = 32 * 1024;

    public DefaultChannelConfig(Channel channel) {
        this(channel, new AdaptiveRecvByteBufAllocator());
    }

    protected DefaultChannelConfig(Channel channel, RecvByteBufAllocator allocator) {
        setRecvByteBufAllocator(allocator, channel.metadata());
        this.channel = channel;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                null,
                CONNECT_TIMEOUT_MILLIS, MAX_MESSAGES_PER_READ, WRITE_SPIN_COUNT,
                ALLOCATOR, AUTO_READ, AUTO_CLOSE, RCVBUF_ALLOCATOR, WRITE_BUFFER_HIGH_WATER_MARK,
                WRITE_BUFFER_LOW_WATER_MARK, MESSAGE_SIZE_ESTIMATOR);
    }

    protected Map<ChannelOption<?>, Object> getOptions(
            Map<ChannelOption<?>, Object> result, ChannelOption<?>... options) {
        if (result == null) {
            result = new IdentityHashMap<ChannelOption<?>, Object>();
        }
        for (ChannelOption<?> o: options) {
            result.put(o, getOption(o));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean setOptions(Map<ChannelOption<?>, ?> options) {
        if (options == null) {
            throw new NullPointerException("options");
        }

        boolean setAllOptions = true;
        for (Entry<ChannelOption<?>, ?> e: options.entrySet()) {
            if (!setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                setAllOptions = false;
            }
        }

        return setAllOptions;
    }

    @Override
    @SuppressWarnings({ "unchecked", "deprecation" })
    public <T> T getOption(ChannelOption<T> option) {
        if (option == null) {
            throw new NullPointerException("option");
        }

        if (option == CONNECT_TIMEOUT_MILLIS) {
            return (T) Integer.valueOf(getConnectTimeoutMillis());
        }
        if (option == MAX_MESSAGES_PER_READ) {
            return (T) Integer.valueOf(getMaxMessagesPerRead());
        }
        if (option == WRITE_SPIN_COUNT) {
            return (T) Integer.valueOf(getWriteSpinCount());
        }
        if (option == ALLOCATOR) {
            return (T) getAllocator();
        }
        if (option == RCVBUF_ALLOCATOR) {
            return (T) getRecvByteBufAllocator();
        }
        if (option == AUTO_READ) {
            return (T) Boolean.valueOf(isAutoRead());
        }
        if (option == AUTO_CLOSE) {
            return (T) Boolean.valueOf(isAutoClose());
        }
        if (option == WRITE_BUFFER_HIGH_WATER_MARK) {
            return (T) Integer.valueOf(getWriteBufferHighWaterMark());
        }
        if (option == WRITE_BUFFER_LOW_WATER_MARK) {
            return (T) Integer.valueOf(getWriteBufferLowWaterMark());
        }
        if (option == MESSAGE_SIZE_ESTIMATOR) {
            return (T) getMessageSizeEstimator();
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == CONNECT_TIMEOUT_MILLIS) {
            setConnectTimeoutMillis((Integer) value);
        } else if (option == MAX_MESSAGES_PER_READ) {
            setMaxMessagesPerRead((Integer) value);
        } else if (option == WRITE_SPIN_COUNT) {
            setWriteSpinCount((Integer) value);
        } else if (option == ALLOCATOR) {
            setAllocator((ByteBufAllocator) value);
        } else if (option == RCVBUF_ALLOCATOR) {
            setRecvByteBufAllocator((RecvByteBufAllocator) value);
        } else if (option == AUTO_READ) {
            setAutoRead((Boolean) value);
        } else if (option == AUTO_CLOSE) {
            setAutoClose((Boolean) value);
        } else if (option == WRITE_BUFFER_HIGH_WATER_MARK) {
            setWriteBufferHighWaterMark((Integer) value);
        } else if (option == WRITE_BUFFER_LOW_WATER_MARK) {
            setWriteBufferLowWaterMark((Integer) value);
        } else if (option == MESSAGE_SIZE_ESTIMATOR) {
            setMessageSizeEstimator((MessageSizeEstimator) value);
        } else {
            return false;
        }

        return true;
    }

    protected <T> void validate(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }
        option.validate(value);
    }

    @Override
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    @Override
    public ChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException(String.format(
                    "connectTimeoutMillis: %d (expected: >= 0)", connectTimeoutMillis));
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * @throws IllegalStateException if {@link #getRecvByteBufAllocator()} does not return an object of type
     * {@link MaxMessagesRecvByteBufAllocator}.
     */
    @Override
    @Deprecated
    public int getMaxMessagesPerRead() {
        try {
            MaxMessagesRecvByteBufAllocator allocator = getRecvByteBufAllocator();
            return allocator.maxMessagesPerRead();
        } catch (ClassCastException e) {
            throw new IllegalStateException("getRecvByteBufAllocator() must return an object of type " +
                    "MaxMessagesRecvByteBufAllocator", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * @throws IllegalStateException if {@link #getRecvByteBufAllocator()} does not return an object of type
     * {@link MaxMessagesRecvByteBufAllocator}.
     */
    @Override
    @Deprecated
    public ChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        try {
            MaxMessagesRecvByteBufAllocator allocator = getRecvByteBufAllocator();
            allocator.maxMessagesPerRead(maxMessagesPerRead);
            return this;
        } catch (ClassCastException e) {
            throw new IllegalStateException("getRecvByteBufAllocator() must return an object of type " +
                    "MaxMessagesRecvByteBufAllocator", e);
        }
    }

    @Override
    public int getWriteSpinCount() {
        return writeSpinCount;
    }

    @Override
    public ChannelConfig setWriteSpinCount(int writeSpinCount) {
        if (writeSpinCount <= 0) {
            throw new IllegalArgumentException(
                    "writeSpinCount must be a positive integer.");
        }
        this.writeSpinCount = writeSpinCount;
        return this;
    }

    @Override
    public ByteBufAllocator getAllocator() {
        return allocator;
    }

    @Override
    public ChannelConfig setAllocator(ByteBufAllocator allocator) {
        if (allocator == null) {
            throw new NullPointerException("allocator");
        }
        this.allocator = allocator;
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RecvByteBufAllocator> T getRecvByteBufAllocator() {
        return (T) rcvBufAllocator;
    }

    @Override
    public ChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        rcvBufAllocator = checkNotNull(allocator, "allocator");
        return this;
    }

    /**
     * Set the {@link RecvByteBufAllocator} which is used for the channel to allocate receive buffers.
     * @param allocator the allocator to set.
     * @param metadata Used to set the {@link ChannelMetadata#defaultMaxMessagesPerRead()} if {@code allocator}
     * is of type {@link MaxMessagesRecvByteBufAllocator}.
     * @return this
     */
    private void setRecvByteBufAllocator(RecvByteBufAllocator allocator, ChannelMetadata metadata) {
        if (allocator instanceof MaxMessagesRecvByteBufAllocator) {
            ((MaxMessagesRecvByteBufAllocator) allocator).maxMessagesPerRead(metadata.defaultMaxMessagesPerRead());
        } else if (allocator == null) {
            throw new NullPointerException("allocator");
        }
        rcvBufAllocator = allocator;
    }

    @Override
    public boolean isAutoRead() {
        return autoRead == 1;
    }

    @Override
    public ChannelConfig setAutoRead(boolean autoRead) {
        boolean oldAutoRead = AUTOREAD_UPDATER.getAndSet(this, autoRead ? 1 : 0) == 1;
        if (autoRead && !oldAutoRead) {
            channel.read();
        } else if (!autoRead && oldAutoRead) {
            autoReadCleared();
        }
        return this;
    }

    /**
     * Is called once {@link #setAutoRead(boolean)} is called with {@code false} and {@link #isAutoRead()} was
     * {@code true} before.
     */
    protected void autoReadCleared() { }

    @Override
    public boolean isAutoClose() {
        return autoClose;
    }

    @Override
    public ChannelConfig setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
        return this;
    }

    @Override
    public int getWriteBufferHighWaterMark() {
        return writeBufferHighWaterMark;
    }

    @Override
    public ChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        if (writeBufferHighWaterMark < getWriteBufferLowWaterMark()) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark cannot be less than " +
                            "writeBufferLowWaterMark (" + getWriteBufferLowWaterMark() + "): " +
                            writeBufferHighWaterMark);
        }
        if (writeBufferHighWaterMark < 0) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark must be >= 0");
        }
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
        return this;
    }

    @Override
    public int getWriteBufferLowWaterMark() {
        return writeBufferLowWaterMark;
    }

    @Override
    public ChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        if (writeBufferLowWaterMark > getWriteBufferHighWaterMark()) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark cannot be greater than " +
                            "writeBufferHighWaterMark (" + getWriteBufferHighWaterMark() + "): " +
                            writeBufferLowWaterMark);
        }
        if (writeBufferLowWaterMark < 0) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark must be >= 0");
        }
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
        return this;
    }

    @Override
    public MessageSizeEstimator getMessageSizeEstimator() {
        return msgSizeEstimator;
    }

    @Override
    public ChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        if (estimator == null) {
            throw new NullPointerException("estimator");
        }
        msgSizeEstimator = estimator;
        return this;
    }
}
