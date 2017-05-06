package monet;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

import rx.Observable;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BitmapDecodeTransformerTest {

    @Test
    public void callsOnErrorForNullRequest() {
        TestSubscriber<Bitmap> subscriber = TestSubscriber.create();
        Observable.<Request>just(null)
                .compose(new BitmapDecodeTransformer(Schedulers.immediate()))
                .subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertError(NullPointerException.class);
    }

    @Test
    public void callsOnErrorForIOException() {
        TestSubscriber<Bitmap> subscriber = TestSubscriber.create();
        Observable.just(Request.builder().build())
                .compose(new BitmapDecodeTransformer(Schedulers.immediate()) {
                    @Override
                    Bitmap decode(Request request) throws IOException {
                        throw new IOException();
                    }
                })
                .subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertError(RuntimeException.class);
    }

    @Test
    public void callsOnErrorWhenOutOfMemory() {
        TestSubscriber<Bitmap> subscriber = TestSubscriber.create();
        Observable.just(Request.builder().build())
                .compose(new BitmapDecodeTransformer(Schedulers.immediate()) {
                    @Override
                    Bitmap decode(Request request) throws IOException {
                        throw new OutOfMemoryError();
                    }
                })
                .subscribe(subscriber);
        subscriber.awaitTerminalEvent();
        subscriber.assertError(RuntimeException.class);
    }

    @Test(expected = StackOverflowError.class)
    public void failsOnFatalError() {
        TestSubscriber<Bitmap> subscriber = TestSubscriber.create();
        Observable.just(Request.builder().build())
                .compose(new BitmapDecodeTransformer(Schedulers.immediate()) {
                    @Override
                    Bitmap decode(Request request) throws IOException {
                        throw new StackOverflowError();
                    }
                })
                .subscribe(subscriber);
    }
}
