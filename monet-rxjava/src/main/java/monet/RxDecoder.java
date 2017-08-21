package monet;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;

class RxDecoder {

  private final Decoder decoder;
  private final Scheduler scheduler;

  RxDecoder(Decoder decoder, Scheduler scheduler) {
    this.decoder = decoder;
    this.scheduler = scheduler;
  }

  Flowable<? extends Image> decode(Request request) {
    return Flowable.fromPublisher(decoder.publisher(request))
        .subscribeOn(scheduler);
  }
}
