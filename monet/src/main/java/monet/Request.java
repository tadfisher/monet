package monet;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.io.InputStream;

@AutoValue public abstract class Request {

  public static Builder builder(InputStream inputStream) {
    return new AutoValue_Request.Builder()
        .stream(inputStream)
        .targetHeight(0)
        .targetWidth(0);
  }

  @Memoized boolean hasTargetSize() {
    return targetWidth() != 0 || targetHeight() != 0;
  }

  @Nullable abstract Bitmap.Config config();

  @Nullable abstract ImageView fit();

  @Nullable abstract InputStream stream();

  abstract int targetWidth();

  abstract int targetHeight();

  abstract Builder newBuilder();

  @AutoValue.Builder public static abstract class Builder {

    public abstract Builder config(@Nullable Bitmap.Config config);

    abstract Builder fit(@Nullable ImageView scaleView);

    abstract Builder stream(@Nullable InputStream stream);

    abstract Builder targetWidth(int targetWidth);

    abstract Builder targetHeight(int targetHeight);

    public Builder scale(@NonNull ImageView.ScaleType scaleType, @NonNull ImageView view) {
      fit(view);
      targetWidth(0);
      targetHeight(0);
      return this;
    }

    public Builder size(int width, int height) {
      fit(null);
      targetWidth(Math.max(width, 0));
      targetHeight(Math.max(height, 0));
      return this;
    }

    public abstract Request build();
  }
}
