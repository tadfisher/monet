package com.simple.monet;

import android.widget.ImageView;

import rx.Observable;
import rx.Observable.Transformer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

class ImageViewRequestTransformer implements Transformer<Request.Builder, Request.Builder> {

    final ImageView view;
    final boolean fitX;
    final boolean fitY;

    ImageViewRequestTransformer(ImageView view, boolean fitX, boolean fitY) {
        this.view = view;
        this.fitX = fitX;
        this.fitY = fitY;
    }

    @Override
    public Observable<Request.Builder> call(Observable<Request.Builder> builderObservable) {
        return builderObservable.flatMap(new Func1<Request.Builder, Observable<Request.Builder>>() {
            @Override
            public Observable<Request.Builder> call(Request.Builder builder) {
                return Observable.create(new ImageViewRequestOnSubscribe(builder, view, fitX, fitY))
                        .subscribeOn(AndroidSchedulers.mainThread());
            }
        });
    }
}
