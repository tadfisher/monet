package monet;

import android.graphics.Bitmap;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.widget.ImageView;
import java.io.InputStream;
import monet.internal.Preconditions;
import rx.Observable.Transformer;

public final class Monet {

    private static final Transformer<InputStream, Request.Builder> fromInputStreamTransformer =
            new InputStreamRequestTransformer();

    private static final Transformer<Request, Bitmap> defaultDecodeTransformer =
            new BitmapDecodeTransformer(MonetSchedulers.decodeThread());

    @CheckResult @NonNull
    public static Transformer<InputStream, Request.Builder> fromInputStream() {
        return fromInputStreamTransformer;
    }

    @CheckResult @NonNull
    public static Transformer<Request.Builder, Request.Builder> fit(@NonNull ImageView imageView) {
        Preconditions.checkNotNull(imageView, "imageView == null");
        return new ImageViewRequestTransformer(imageView, true, true);
    }

    @CheckResult @NonNull
    public static Transformer<Request.Builder, Request.Builder> fitX(@NonNull ImageView imageView) {
        Preconditions.checkNotNull(imageView, "imageView == null");
        return new ImageViewRequestTransformer(imageView, true, false);
    }

    @CheckResult @NonNull
    public static Transformer<Request.Builder, Request.Builder> fitY(@NonNull ImageView imageView) {
        Preconditions.checkNotNull(imageView, "imageView == null");
        return new ImageViewRequestTransformer(imageView, false, true);
    }

    @CheckResult @NonNull
    public static Transformer<Request, Bitmap> decode() {
        return defaultDecodeTransformer;
    }

    private Monet() {
        throw new AssertionError("No instances.");
    }
}
