package monet;

import android.os.HandlerThread;
import android.os.Process;

import java.util.concurrent.atomic.AtomicReference;

import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.annotations.Experimental;

/**
 * Schedulers tailored for image processing.
 */
public final class MonetSchedulers {

    private static final AtomicReference<MonetSchedulers> INSTANCE = new AtomicReference<>();

    private final Scheduler decodeThreadScheduler;

    private static MonetSchedulers getInstance() {
        for (;;) {
            MonetSchedulers current = INSTANCE.get();
            if (current != null) {
                return current;
            }
            current = new MonetSchedulers();
            if (INSTANCE.compareAndSet(null, current)) {
                return current;
            }
        }
    }

    private MonetSchedulers() {
        DecodeThread decodeThread = new DecodeThread();
        decodeThread.start();
        decodeThreadScheduler = AndroidSchedulers.from(decodeThread.getLooper());
    }

    /**
     * A {@link Scheduler} which performs work serially on a dedicated background thread. This is
     * intended to avoid thrashing the heap during bitmap decode operations.
     */
    public static Scheduler decodeThread() {
        return getInstance().decodeThreadScheduler;
    }

    /**
     * Resets the current {@link MonetSchedulers} instance. This will re-init the cached
     * schedulers on the next usage, which can be useful for testing.
     */
    @Experimental
    public static void reset() {
        INSTANCE.set(null);
    }

    private static class DecodeThread extends HandlerThread {
        DecodeThread() {
            super("Monet-DecodeThread", Process.THREAD_PRIORITY_BACKGROUND);
        }
    }
}
