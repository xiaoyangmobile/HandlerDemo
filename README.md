首先我们看下`Handler.sendMessage`开始，可以看到在`Handler`中都调用了`sendMessageAtTime`:
```
public boolean sendMessageAtTime(@NonNull Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue; // A
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                    this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }
```
在A处，获取的是`mQueue`,那么这个`mQueue`是什么呢？我们查找下。
```
public Handler(@Nullable Callback callback, boolean async) {
    ...
    mLooper = Looper.myLooper();
    if (mLooper == null) {
        throw new RuntimeException(
           "Can't create handler inside thread " + Thread.currentThread()
            + " that has not called Looper.prepare()");
    }
    mQueue = mLooper.mQueue;
    ...
}
```
我们可以看到`mQueue`是在`Looper`中获取到的。那么Looper是怎么来的，这个Looper中的mQueue到底是什么东西呢？我们接着看下Looper中的源码：
```
    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
    ...
    public static @Nullable Looper myLooper() {
        return sThreadLocal.get();
    }
```
因为使用`static`修饰，所以sThreadLocal在内存中只会有一份。接着看下`sThreadLocal.get()`中的逻辑：
```
    public T get() {
        Thread t = Thread.currentThread(); // A
        ThreadLocalMap map = getMap(t); // B
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this); // C
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
        return setInitialValue();
    }
    ...
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }
```
A处获取当前线程;B处看，每个线程都会维护一个ThreadLocalMap类型的threadLocals成员变量。
** 注意 **: 总汇一下，一个线程只有一个ThreadLocalMap类型的变量threadLocals。Looper.myLoop()是调用静态变量ThreadLocal的get方法。get方法中是获取当前线程的threadLocals中以ThreadLocal为索引的value即当前线程的Looper。set方法是在Looper的prepare中设置的。
回到
```
public Handler(@Nullable Callback callback, boolean async) {
    ...
    mLooper = Looper.myLooper();
    if (mLooper == null) {
        throw new RuntimeException(
           "Can't create handler inside thread " + Thread.currentThread()
            + " that has not called Looper.prepare()");
    }
    mQueue = mLooper.mQueue; // B
    ...
}
```
代码B注释处。
```
    private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mThread = Thread.currentThread();
    }
```
一个Looper会有一个MessageQueue。这时候我们就清楚mQueue到底是什么了。接着看代码。
```
public boolean sendMessageAtTime(@NonNull Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue; // A
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                    this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis); // B
    }
```
B处是将消息加入队列。
```
    private boolean enqueueMessage(@NonNull MessageQueue queue, @NonNull Message msg,
            long uptimeMillis) {
        msg.target = this;
        msg.workSourceUid = ThreadLocalWorkSource.getUid();

        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }
```
接着看下MessageQueue中代码
```
   boolean enqueueMessage(Message msg, long when) {
        if (msg.target == null) {
            throw new IllegalArgumentException("Message must have a target.");
        }

        synchronized (this) {
            if (msg.isInUse()) {
                throw new IllegalStateException(msg + " This message is already in use.");
            }

            if (mQuitting) {
                IllegalStateException e = new IllegalStateException(
                        msg.target + " sending message to a Handler on a dead thread");
                Log.w(TAG, e.getMessage(), e);
                msg.recycle();
                return false;
            }

            msg.markInUse();
            msg.when = when;
            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                // New head, wake up the event queue if blocked.
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                // Inserted within the middle of the queue.  Usually we don't have to wake
                // up the event queue unless there is a barrier at the head of the queue
                // and the message is the earliest asynchronous message in the queue.
                needWake = mBlocked && p.target == null && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) {
                        break;
                    }
                    if (needWake && p.isAsynchronous()) {
                        needWake = false;
                    }
                }
                msg.next = p; // invariant: p == prev.next
                prev.next = msg;
            }

            // We can assume mPtr != 0 because mQuitting is false.
            if (needWake) {
                nativeWake(mPtr);
            }
        }
        return true;
    }
```
可以看出来MessageQueue中维护的是Message的链表。Message就是一个node结构。
到这里sendMessage逻辑就完了。
但还有一点要补充的，消息进队列后怎么执行的呢？
线程创建的时候会调用Looper的prepare和loop方法。prepare给当前线程threadLocals保存了Looper。loop()方法则是不停循环处理MessageQueue中的消息。

Looper中有：
* ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
* final MessageQueue mQueue;
Thread中有：
* ThreadLocalMap类型的成员变量threadLocals.
MessageQueue:
* 维护了Message的链表