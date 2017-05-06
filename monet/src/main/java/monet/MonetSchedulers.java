package monet;

import android.os.HandlerThread;
import android.os.Process;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * Schedulers tailored for image processing.
 */
public final class MonetSchedulers {

  private static final class SchedulerHolder {

    private static final DecodeThread THREAD = new DecodeThread();

    static {
      THREAD.start();
    }

    static final Scheduler DEFAULT = AndroidSchedulers.from(THREAD.getLooper());
  }

  private static final Scheduler DECODE_THREAD =
      MonetPlugins.initDecodeThreadScheduler(() -> SchedulerHolder.DEFAULT);

  /**
   * A {@link Scheduler} which performs work serially on a dedicated background thread. This is
   * intended to avoid thrashing the heap during bitmap decode operations.
   */
  public static Scheduler decodeThread() {
    return MonetPlugins.onDecodeThreadScheduler(DECODE_THREAD);
  }

  private static class DecodeThread extends HandlerThread {

    DecodeThread() {
      super("Monet-DecodeThread", Process.THREAD_PRIORITY_BACKGROUND);
    }
  }

  private MonetSchedulers() {
    throw new AssertionError("No instances.");
  }
}
