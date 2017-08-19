package monet.decoder.gif;

import java.io.IOException;
import monet.BufferImage;
import monet.Decoder;
import monet.Image;
import monet.Request;
import monet.internal.Util;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class GifDecoder extends Decoder {

  private final ByteString SIGNATURE = ByteString.encodeUtf8("GIF");

  public static Decoder create() {
    return new GifDecoder();
  }

  GifDecoder() {
    // Prevent instantiation.
  }

  @Override protected boolean supports(Request request) {
    final BufferedSource source = request.source();
    try {
      if (!source.request(SIGNATURE.size())) {
        return false;
      }
    } catch (IOException e) {
      return false;
    }
    final Buffer buf = source.buffer();
    final ByteString bytes = buf.snapshot(SIGNATURE.size());
    return bytes.equals(SIGNATURE);
  }

  @Override public Publisher<? extends Image> publisher(final Request request) {
    return s -> {
      final GifSource gifSource = new GifSource(request.source());
      final GifSource.Header header;
      try {
        header = gifSource.readHeader();
      } catch (Exception e) {
        s.onError(e);
        Util.closeQuietly(gifSource);
        return;
      }
      s.onSubscribe(new GifSubscription(s, header, gifSource));
    };
  }

  static class GifSubscription implements Subscription {

    private final Subscriber<? super Image> subscriber;
    private final GifSource.Header header;
    private final GifSource source;

    private volatile boolean isCancelled;

    GifSubscription(Subscriber<? super Image> subscriber, GifSource.Header header,
        GifSource source) {
      this.subscriber = subscriber;
      this.header = header;
      this.source = source;
    }

    @Override public void request(long n) {
      if (isCancelled) return;

      try {
        for (int i = 0; i < n; i++) {
          if (isCancelled) return;

          final GifSource.Frame frame = source.readFrame();
          if (frame == null) {
            subscriber.onComplete();
            cancel();
            return;
          }

          subscriber.onNext(
              new BufferImage(header.width, header.height, frame.pixelData.asByteBuffer()));
        }
      } catch (Exception e) {
        cancel();
        subscriber.onError(e);
      }
    }

    @Override public void cancel() {
      if (isCancelled) return;
      isCancelled = true;
      Util.closeQuietly(source);
    }
  }
}
