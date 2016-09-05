package com.simple.monet;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;

import rx.Observable;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.exceptions.Exceptions;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

class BitmapDecodeTransformer implements Observable.Transformer<Request.Builder, Bitmap> {

    BitmapDecodeTransformer() {
        // Pass
    }

    @Override
    public Observable<Bitmap> call(final Observable<Request.Builder> requestObservable) {
        return requestObservable
                .map(new Func1<Request.Builder, Bitmap>() {
                    @Override
                    public Bitmap call(Request.Builder requestBuilder) {
                        try {
                            return decode(requestBuilder.build());
                        } catch (IOException e) {
                            throw Exceptions.propagate(e);
                        }
                    }
                })
                .subscribeOn(DecodeSchedulerHolder.getInstance())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Bitmap decode(Request request) throws IOException {
        Log.d("Monet", "Decoding request: " + request);

        InputStream stream = request.stream();
        MarkableInputStream markStream = new MarkableInputStream(stream);
        stream = markStream;
        markStream.allowMarksToExpire(false);
        long mark = markStream.savePosition(1024);

        final BitmapFactory.Options options = createBitmapOptions(request);
        final boolean calculateSize = options != null && options.inJustDecodeBounds;

        if (calculateSize) {
            BitmapFactory.decodeStream(stream, null, options);
            calculateInSampleSize(request.targetWidth(), request.targetHeight(), options, request);
            markStream.reset(mark);
        }

        markStream.allowMarksToExpire(true);
        Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
        if (bitmap == null) {
            throw new IOException("Failed to decode stream.");
        }
        return bitmap;
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

    private static void calculateInSampleSize(int reqWidth, int reqHeight,
            BitmapFactory.Options options, Request request) {
        calculateInSampleSize(reqWidth, reqHeight, options.outWidth, options.outHeight, options,
                request);
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
                sampleSize = request.centerInside()
                        ? Math.max(heightRatio, widthRatio)
                        : Math.min(heightRatio, widthRatio);
            }
        }
        options.inSampleSize = sampleSize;
        options.inJustDecodeBounds = false;
    }

    private static class DecodeSchedulerHolder {
        private static final Scheduler INSTANCE =
                Schedulers.from(Executors.newSingleThreadExecutor());

        private DecodeSchedulerHolder() {
            throw new AssertionError("No instances.");
        }

        static Scheduler getInstance() {
            return INSTANCE;
        }
    }
}
