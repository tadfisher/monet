package monet.decoder.bitmap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import monet.BitmapImage;
import monet.DecodeException;
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

public class BitmapDecoder extends Decoder {

  public enum ImageType {
    BMP(ByteString.encodeUtf8("BM")),
    GIF(ByteString.encodeUtf8("GIF87a"), ByteString.encodeUtf8("GIF89a")),
    ICO(ByteString.decodeHex("00000100"), ByteString.decodeHex("00000200")),
    JPEG(ByteString.decodeHex("ffd8ff")),
    WEBP(ByteString.encodeUtf8("RIFF"));

    final ByteString[] signatures;

    ImageType(ByteString... signatures) {
      this.signatures = signatures;
    }
  }

  private static final int MAX_SIGNATURE_LENGTH = 16;

  private static final ByteString WEBP_SUFFIX = ByteString.encodeUtf8("WEBP");

  private final Set<ImageType> imageTypes;

  public static Decoder create() {
    return create(new LinkedHashSet<>(Arrays.asList(ImageType.values())));
  }

  public static Decoder create(Set<ImageType> imageTypes) {
    return new BitmapDecoder(imageTypes);
  }

  BitmapDecoder(Set<ImageType> imageTypes) {
    this.imageTypes = Collections.unmodifiableSet(imageTypes);
  }

  @Override protected boolean supports(Request request) {
    final BufferedSource source = request.source();
    try {
      if (!source.request(MAX_SIGNATURE_LENGTH)) {
        return false;
      }
    } catch (IOException e) {
      return false;
    }
    final Buffer buf = source.buffer();
    final ByteString bytes = buf.snapshot(MAX_SIGNATURE_LENGTH);
    for (ImageType type : imageTypes) {
      for (ByteString signature : type.signatures) {
        if (bytes.startsWith(signature)) {
          return !(type == ImageType.WEBP && !buf.rangeEquals(8, WEBP_SUFFIX));
        }
      }
    }
    return false;
  }

  @Override protected Publisher<? extends Image> publisher(Request request) {
    return (Publisher<Image>) subscriber ->
      subscriber.onSubscribe(new BitmapSubscription(subscriber, request));
  }

  static class BitmapSubscription implements Subscription {

    private final Subscriber<? super Image> subscriber;
    private final Request request;

    private volatile boolean isCancelled;
    private volatile BitmapFactory.Options options;

    BitmapSubscription(Subscriber<? super Image> subscriber, Request request) {
      this.subscriber = subscriber;
      this.request = request;
    }

    @Override public void request(long n) {
      if (isCancelled) return;

      final BufferedSource source = request.source();
      try {
        subscriber.onNext(decode(source));
        subscriber.onComplete();
      } catch (DecodeException|IOException e) {
        subscriber.onError(e);
      }

      isCancelled = true;
    }

    @Override public void cancel() {
      if (isCancelled) return;
      isCancelled = true;
    }

    private BitmapImage decode(BufferedSource source) throws DecodeException, IOException {
      InputStream stream = source.inputStream();
      options = createBitmapOptions(request);
      final boolean calculateSize = options != null && options.inJustDecodeBounds;

      if (calculateSize) {
        final MarkableInputStream markStream = new MarkableInputStream(stream);
        stream = markStream;
        markStream.allowMarksToExpire(false);
        long mark = markStream.savePosition(1024);
        BitmapFactory.decodeStream(stream, null, options);
        calculateInSampleSize(request.targetWidth(), request.targetHeight(), options.outWidth,
            options.outHeight, options, request);
        try {
          markStream.reset(mark);
        } catch (IOException e) {
          subscriber.onError(e);
          Util.closeQuietly(source);
        }
        markStream.allowMarksToExpire(true);
      }
      final Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
      if (bitmap == null) throw new DecodeException("Failed to decode bitmap.");
      return new BitmapImage(bitmap);
    }

    private static BitmapFactory.Options createBitmapOptions(Request request) {
      final boolean justBounds = request.hasTargetSize();
      final Bitmap.Config config = request.config();
      final boolean hasConfig = config != null;
      BitmapFactory.Options options = null;
      if (justBounds || hasConfig) {
        options = new BitmapFactory.Options();
        options.inJustDecodeBounds = justBounds;
        if (hasConfig) {
          options.inPreferredConfig = config;
        }
      }
      return options;
    }

    private static void calculateInSampleSize(int reqWidth, int reqHeight, int width, int height,
        BitmapFactory.Options options, Request request) {
      int sampleSize = 1;
      if (height > reqHeight || width > reqWidth) {
        final int heightRatio;
        final int widthRatio;
        if (reqHeight == 0) {
          sampleSize = (int) Math.floor((float) width / (float) reqWidth);
        } else if (reqWidth == 0) {
          sampleSize = (int) Math.floor((float) height / (float) reqHeight);
        } else {
          heightRatio = (int) Math.floor((float) height / (float) reqHeight);
          widthRatio = (int) Math.floor((float) width / (float) reqWidth);
          final View fitView = request.fitView();
          if (fitView != null
              && fitView instanceof ImageView
              && ((ImageView) fitView).getScaleType() == ImageView.ScaleType.CENTER_INSIDE) {
            sampleSize = Math.max(heightRatio, widthRatio);
          } else {
            sampleSize = Math.min(heightRatio, widthRatio);
          }
        }
      }
      options.inSampleSize = sampleSize;
      options.inJustDecodeBounds = false;
    }
  }
}
