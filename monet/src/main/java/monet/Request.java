package monet;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.io.InputStream;

@AutoValue
public abstract class Request {

    static Builder builder() {
        return new AutoValue_Request.Builder()
                .centerInside(false)
                .targetHeight(0)
                .targetWidth(0);
    }

    boolean hasTargetSize() {
        return targetWidth() != 0 || targetHeight() != 0;
    }

    abstract boolean centerInside();
    @Nullable abstract Bitmap.Config config();
    @Nullable abstract InputStream stream();
    abstract int targetWidth();
    abstract int targetHeight();

    abstract Builder newBuilder();

    @AutoValue.Builder
    public static abstract class Builder {
        public abstract Builder centerInside(boolean centerInside);
        public abstract Builder config(Bitmap.Config config);
        public abstract Builder stream(InputStream stream);
        public abstract Builder targetWidth(int targetWidth);
        public abstract Builder targetHeight(int targetHeight);
        public abstract Request build();
    }
}
