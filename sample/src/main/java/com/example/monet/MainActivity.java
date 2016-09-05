package com.example.monet;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;

import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import rx.Subscription;
import rx.schedulers.Schedulers;

import static com.simple.monet.Monet.decode;
import static com.simple.monet.Monet.fit;
import static com.simple.monet.Monet.fromInputStream;

public class MainActivity extends AppCompatActivity {

    ImageView view;
    ImgurService service;

    Subscription subscription;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.image);

        view = (ImageView) findViewById(R.id.image);

        service = new Retrofit.Builder()
                .baseUrl(HttpUrl.parse("http://i.imgur.com/"))
                .addCallAdapterFactory(
                        RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .build()
                .create(ImgurService.class);
    }

    @Override
    protected void onResume() {
        super.onResume();

        String path = Data.URLS[0];

        subscription = service.fetch(path)
                .map(ResponseBody::byteStream)
                .compose(fromInputStream())
                .compose(fit(view))
                .compose(decode())
                .subscribe(view::setImageBitmap, this::onError);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    void onError(Throwable t) {
        Log.e("Monet", "Exception in pipeline", t);
    }
}
