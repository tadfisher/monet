/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package monet;

import io.reactivex.Scheduler;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Function;
import java.util.concurrent.Callable;

public final class MonetPlugins {

  private static volatile Function<Callable<Scheduler>, Scheduler> onInitDecodeThreadHandler;
  private static volatile Function<Scheduler, Scheduler> onDecodeThreadHandler;

  public static void setInitDecodeThreadSchedulerHandler(
      Function<Callable<Scheduler>, Scheduler> handler) {
    onInitDecodeThreadHandler = handler;
  }

  public static Scheduler initDecodeThreadScheduler(Callable<Scheduler> scheduler) {
    if (scheduler == null) {
      throw new NullPointerException("scheduler == null");
    } else {
      Function<Callable<Scheduler>, Scheduler> f = onInitDecodeThreadHandler;
      return f == null ? callRequireNonNull(scheduler) : applyRequireNonNull(f, scheduler);
    }
  }

  public static void setDecodeThreadSchedulerHandler(Function<Scheduler, Scheduler> handler) {
    onDecodeThreadHandler = handler;
  }

  public static Scheduler onDecodeThreadScheduler(Scheduler scheduler) {
    if (scheduler == null) {
      throw new NullPointerException("scheduler == null");
    } else {
      Function<Scheduler, Scheduler> f = onDecodeThreadHandler;
      return f == null ? scheduler : apply(f, scheduler);
    }
  }

  /**
   * Returns the current hook function.
   *
   * @return the hook function, may be null
   */
  public static Function<Callable<Scheduler>, Scheduler> getInitDecodeThreadSchedulerHandler() {
    return onInitDecodeThreadHandler;
  }

  /**
   * Returns the current hook function.
   *
   * @return the hook function, may be null
   */
  public static Function<Scheduler, Scheduler> getOnDecodeThreadSchedulerHandler() {
    return onDecodeThreadHandler;
  }

  static Scheduler callRequireNonNull(Callable<Scheduler> s) {
    try {
      Scheduler scheduler = s.call();
      if (scheduler == null) {
        throw new NullPointerException("Scheduler Callable returned null");
      } else {
        return scheduler;
      }
    } catch (Throwable var2) {
      throw Exceptions.propagate(var2);
    }
  }

  static Scheduler applyRequireNonNull(Function<Callable<Scheduler>, Scheduler> f,
      Callable<Scheduler> s) {
    Scheduler scheduler = apply(f, s);
    if (scheduler == null) {
      throw new NullPointerException("Scheduler Callable returned null");
    } else {
      return scheduler;
    }
  }

  static <T, R> R apply(Function<T, R> f, T t) {
    try {
      return f.apply(t);
    } catch (Throwable var3) {
      throw Exceptions.propagate(var3);
    }
  }

  private MonetPlugins() {
    throw new AssertionError("No instances.");
  }
}
