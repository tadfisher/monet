package com.simple.monet;

import android.util.Log;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.ImageView;

import rx.Observable;
import rx.Subscriber;
import rx.android.MainThreadSubscription;

import static rx.android.MainThreadSubscription.verifyMainThread;

class ImageViewRequestOnSubscribe implements Observable.OnSubscribe<Request.Builder> {

    final Request.Builder builder;
    final ImageView view;
    final boolean fitX;
    final boolean fitY;

    ImageViewRequestOnSubscribe(Request.Builder builder, ImageView view, boolean fitX,
            boolean fitY) {
        this.builder = builder;
        this.view = view;
        this.fitX = fitX;
        this.fitY = fitY;
    }

    @Override
    public void call(final Subscriber<? super Request.Builder> subscriber) {
        verifyMainThread();

        int width = view.getWidth();
        int height = view.getHeight();
        if (width > 0 && height > 0) {
            Log.d("Monet", String.format("view has size: %d %d", width, height));
            if (!subscriber.isUnsubscribed()) {
                subscriber.onNext(setTargetDimensions(width, height));
            }
        } else {
            Log.d("Monet", "adding preDraw listener");
            listenForPreDraw(subscriber);
        }
    }

    private void listenForPreDraw(final Subscriber<? super Request.Builder> subscriber) {
        final OnPreDrawListener listener = new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!subscriber.isUnsubscribed()) {
                    ViewTreeObserver vto = view.getViewTreeObserver();
                    if (vto.isAlive()) {
                        int width = view.getWidth();
                        int height = view.getHeight();
                        if (width > 0 && height > 0) {
                            vto.removeOnPreDrawListener(this);
                            Log.d("Monet",
                                    String.format("onPreDraw got size: %d %d", width, height));
                            subscriber.onNext(setTargetDimensions(width, height));
                        }
                    }
                }
                return true;
            }
        };

        view.getViewTreeObserver().addOnPreDrawListener(listener);

        subscriber.add(new MainThreadSubscription() {
            @Override
            protected void onUnsubscribe() {
                view.getViewTreeObserver().removeOnPreDrawListener(listener);
            }
        });
    }

    private Request.Builder setTargetDimensions(int width, int height) {
        return builder
                .targetWidth(fitX ? width : 0)
                .targetHeight(fitY ? height : 0);
    }
}
