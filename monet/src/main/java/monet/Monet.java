package monet;

import android.graphics.Bitmap;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;

public final class Monet {

  public static Single<Bitmap> decodeSingle(Request request) {
    return deferred(request)
        .observeOn(MonetSchedulers.decodeThread())
        .map(BitmapDecoder::decode);
  }

  private static Single<Request> deferred(Request request) {
    if (request.hasTargetSize() || request.fit() == null) {
      return Single.just(request);
    } else {
      return new ViewDimensionsSingle(request.fit())
          .subscribeOn(AndroidSchedulers.mainThread())
          .map(size -> request.newBuilder()
              .targetWidth(size.width())
              .targetHeight(size.height())
              .build());
    }
  }

  private Monet() {
    throw new AssertionError("No instances.");
  }
}
