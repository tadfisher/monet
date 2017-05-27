package monet;

import android.support.annotation.Nullable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.SingleTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import java.lang.reflect.Type;

/** Decode a {@link Request} into some arbitrary type representing an image. */
public abstract class Decoder<T> implements SingleTransformer<Request, T> {

  private final Scheduler scheduler;

  protected Decoder(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Decode the request to the result type. Returns a {@link DecodeResult} with information
   * about the decoding attempt.
   */
  // TODO support decoder-specific options, perhaps by extending Request
  public abstract DecodeResult<T> decode(Request request);

  @Override public SingleSource<T> apply(Single<Request> upstream) {
    return upstream.flatMap(Decoder::deferredSingle)
        .flatMap(request -> Single.fromCallable(() -> decode(request)).subscribeOn(scheduler))
        .flatMap(result -> {
          if (!result.supported) {
            if (result.error != null) {
              return Single.error(new DecodeException("Unsupported source", result.error));
            }
            return Single.error(new DecodeException("Unsupported source"));
          }
          if (result.error != null) {
            return Single.error(new DecodeException("Unable to decode", result.error));
          }
          return Single.just(result.result);
        });
  }

  // TODO Move this somewhere else.
  private static Single<Request> deferredSingle(Request request) {
    if (request.hasTargetSize() || request.fitView() == null) {
      return Single.just(request);
    } else {
      return new ViewDimensionsSingle(request.fitView())
          .subscribeOn(AndroidSchedulers.mainThread())
          .map(size -> request.newBuilder()
              .targetWidth(size.width())
              .targetHeight(size.height())
              .build());
    }
  }

  public interface Factory {

    /**
     * Attempts to get a decoder for {@code type}. This returns the decoder if one was
     * created, or null if this factory isn't capable of creating such an adapter.
     */
    @Nullable Decoder<?> get(Type type, Monet monet);
  }
}
