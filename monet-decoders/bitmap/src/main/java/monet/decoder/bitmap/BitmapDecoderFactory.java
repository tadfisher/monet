package monet.decoder.bitmap;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import io.reactivex.Scheduler;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import monet.Decoder;
import monet.Monet;
import monet.MonetSchedulers;

public final class BitmapDecoderFactory implements Decoder.Factory {

  public static BitmapDecoderFactory create() {
    return new BitmapDecoderFactory(null, null);
  }

  public static BitmapDecoderFactory create(@NonNull Scheduler scheduler) {
    return new BitmapDecoderFactory(scheduler, null);
  }

  public static BitmapDecoderFactory create(@NonNull BitmapDecoder.ImageType... imageTypes) {
    return new BitmapDecoderFactory(null, imageTypes);
  }

  public static BitmapDecoderFactory create(@NonNull Scheduler scheduler,
      @NonNull BitmapDecoder.ImageType... imageTypes) {
    return new BitmapDecoderFactory(scheduler, imageTypes);
  }

  private final Scheduler scheduler;
  private final Set<BitmapDecoder.ImageType> imageTypes;

  private BitmapDecoderFactory(Scheduler scheduler,
      BitmapDecoder.ImageType[] imageTypes) {
    this.scheduler = scheduler == null ? MonetSchedulers.decodeThread() : null;
    this.imageTypes = new LinkedHashSet<>(Arrays.asList(
        imageTypes == null ? BitmapDecoder.ImageType.values() : imageTypes));
  }

  @Nullable @Override public Decoder<?> get(Type type, Monet monet) {
    if (type == Bitmap.class) {
      return new BitmapDecoder(scheduler, imageTypes);
    }
    return null;
  }
}
