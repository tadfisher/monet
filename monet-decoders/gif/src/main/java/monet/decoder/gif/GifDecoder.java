package monet.decoder.gif;

import android.graphics.Bitmap;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import monet.DecodeResult;
import monet.Decoder;
import monet.Request;

public class GifDecoder extends Decoder<Flowable<Bitmap>> {

  private final Scheduler scheduler;

  GifDecoder(Scheduler scheduler) {
    super(scheduler);
    this.scheduler = scheduler;
  }

  @Override public DecodeResult<Flowable<Bitmap>> decode(Request request) {
    return null;
  }
}
