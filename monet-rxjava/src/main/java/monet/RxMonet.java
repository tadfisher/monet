package monet;

import android.os.Looper;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import java.util.concurrent.TimeUnit;

public final class RxMonet {
  private final Monet monet;

  public static RxMonet from(Monet monet) {
    return new RxMonet(monet);
  }

  RxMonet(Monet monet) {
    this.monet = monet;
  }

  public Observable<? extends Image> decode(Request request) {
    return decode(request, MonetSchedulers.decodeThread());
  }

  public Observable<? extends Image> decode(Request request, Scheduler scheduler) {
    final Observable<? extends Image> images;

    if (request.fitView() != null) {
      images = new ViewDimensionsSingle(request.fitView())
          .subscribeOn(AndroidSchedulers.mainThread())
          .observeOn(scheduler)
          .flatMapObservable(ignored -> Observable.fromPublisher(monet.decode(request)));
    } else {
      images = Observable.fromPublisher(monet.decode(request));
    }

    return images.concatMap(i -> Observable.just(i).delay(i.frameDelay(), TimeUnit.MILLISECONDS));
  }

  static boolean checkMainThread(SingleObserver<?> observer) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      observer.onError(new IllegalStateException(
          "Expected to be called on the main thread but was " + Thread.currentThread().getName()));
      return false;
    }
    return true;
  }
}
