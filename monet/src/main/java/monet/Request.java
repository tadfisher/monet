package monet;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import okio.BufferedSource;
import okio.Okio;

@AutoValue public abstract class Request {

  /** Create a {@link Request.Builder} for decoding image data from a byte array. */
  public static Builder builder(@NonNull byte[] bytes) {
    return builder().source(Okio.buffer(Okio.source(new ByteArrayInputStream(bytes))));
  }

  /** Create a {@link Request.Builder} for decoding image data from a {@link File}. */
  public static Builder builder(@NonNull File file) throws FileNotFoundException {
    return builder().source(Okio.buffer(Okio.source(file)));
  }

  /** Create a {@link Request.Builder} for decoding image data from an {@link InputStream}. */
  public static Builder builder(@NonNull InputStream inputStream) {
    return builder().source(Okio.buffer(Okio.source(inputStream)));
  }

  /** Create a {@link Request.Builder} for decoding image data from a {@link BufferedSource}. */
  public static Builder builder(@NonNull BufferedSource source) {
    return builder().source(source);
  }

  private static Builder builder() {
    return new AutoValue_Request.Builder()
        .config(Bitmap.Config.ARGB_8888)
        .targetHeight(0)
        .targetWidth(0);
  }

  @Memoized public boolean hasTargetSize() {
    return targetWidth() != 0 || targetHeight() != 0;
  }

  // TODO Some of these properties are decoder-specific. Figure out a nice API and move them.

  public abstract Bitmap.Config config();

  @Nullable public abstract View fitView();

  @Nullable public abstract ImageView.ScaleType scale();

  public abstract BufferedSource source();

  public abstract int targetWidth();

  public abstract int targetHeight();

  public abstract Builder newBuilder();

  @AutoValue.Builder public static abstract class Builder {

    /** Apply a {@linkplain Bitmap.Config bitmap configuration} to this request. */
    public abstract Builder config(Bitmap.Config config);

    /**
     * Scale the decoded image to fit the target bounds using the provided
     * {@linkplain ImageView.ScaleType scaling algorithm}.
     * <p>
     * If set and {@link #fit(View) fit} is called with an {@link ImageView}, ignore the view's
     * {@link ImageView#getScaleType() scaleType} property.
     */
    public abstract Builder scale(@Nullable ImageView.ScaleType scaleType);

    /**
     * Scale the decoded image to fit this view's bounds.
     * <p>
     * If provided an ImageView, and {@link #scale(ImageView.ScaleType) scale} has not been set,
     * honor the {@link ImageView#getScaleType() scaleType} property at decode time.
     * <p>
     * <em>Note:</em> If set, bitmap decoding will be deferred until the view has been laid out and
     * assigned a size. The {@link #targetWidth() targetWidth} and
     * {@link #targetHeight() targetHeight} parameters will be overwritten with the view's width.
     */
    public Builder fit(@Nullable View view) {
      fitView(view);
      targetWidth(0);
      targetHeight(0);
      return this;
    }

    /**
     * Scale the decoded image to fit the requested width and height. Set either width or height
     * to {@code 0} to scale in a single dimension while maintaining aspect ratio.
     * <p>
     * <em>Note:</em> If set, {@link #fit(View) fit} will have no effect.
     */
    public Builder size(int width, int height) {
      fitView(null);
      targetWidth(Math.max(width, 0));
      targetHeight(Math.max(height, 0));
      return this;
    }

    abstract Builder fitView(View view);
    abstract Builder source(BufferedSource source);
    abstract Builder targetWidth(int targetWidth);
    abstract Builder targetHeight(int targetHeight);

    public abstract Request build();
  }
}
