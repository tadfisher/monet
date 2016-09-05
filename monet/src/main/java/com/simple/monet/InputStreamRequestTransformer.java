package com.simple.monet;

import com.simple.monet.Request.Builder;

import java.io.InputStream;

import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

class InputStreamRequestTransformer implements Transformer<InputStream, Builder> {

    @Override
    public Observable<Builder> call(Observable<InputStream> streamObservable) {
        return streamObservable.map(new Func1<InputStream, Builder>() {
            @Override
            public Request.Builder call(InputStream stream) {
                return Request.builder().stream(stream);
            }
        });
    }
}