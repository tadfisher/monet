package monet.internal;

import java.io.Closeable;

/**
 * Created by tad on 5/28/17.
 */

public final class Util {

  private Util() {
    throw new AssertionError("No instances.");
  }

  /**
   * Closes {@code closeable}, ignoring any checked exceptions. Does nothing if {@code closeable} is
   * null.
   */
  public static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (RuntimeException rethrown) {
        throw rethrown;
      } catch (Exception ignored) {
      }
    }
  }
}
