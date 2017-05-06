package monet;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import rx.Observable;
import rx.Scheduler;
import rx.exceptions.Exceptions;
import rx.functions.Func1;

class BitmapDecodeTransformer implements Observable.Transformer<Request, Bitmap> {

    private final Scheduler scheduler;

    BitmapDecodeTransformer(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Observable<Bitmap> call(final Observable<Request> requestObservable) {
        return requestObservable
                .observeOn(scheduler)
                .map(new Func1<Request, Bitmap>() {
                    @Override
                    public Bitmap call(Request request) {
                        try {
                            return decode(request);
                        } catch (OutOfMemoryError e) {
                            throw new RuntimeException("Out of memory.");
                        } catch (Throwable t) {
                            throw Exceptions.propagate(t);
                        }
                    }
                });
    }

    Bitmap decode(Request request) throws IOException {
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
}
