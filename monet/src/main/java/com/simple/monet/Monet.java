package com.simple.monet;

import android.graphics.Bitmap;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.widget.ImageView;

import com.simple.monet.internal.Preconditions;

import java.io.InputStream;

import rx.Observable.Transformer;

public final class Monet {

    @CheckResult @NonNull
    public static Transformer<InputStream, Request.Builder> fromInputStream() {
        return new InputStreamRequestTransformer();
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

    @NonNull
    public static Transformer<Request.Builder, Bitmap> decode() {
        return new BitmapDecodeTransformer();
    }

    private Monet() {
        throw new AssertionError("No instances.");
    }
}
