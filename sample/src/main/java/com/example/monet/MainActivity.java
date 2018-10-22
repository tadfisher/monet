package com.example.monet;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

public class MainActivity extends AppCompatActivity {

  ImgurService service;
  RecyclerView recyclerView;
  MonetAdapter adapter;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    final HttpUrl url = HttpUrl.parse("https://i.imgur.com/");
    assert url != null;

    service = new Retrofit.Builder()
        .baseUrl(url)
        .client(client)
        .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
        .build()
        .create(ImgurService.class);

    recyclerView = findViewById(R.id.grid);
    recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
    adapter = new MonetAdapter(service);
    recyclerView.setAdapter(adapter);
  }

  @Override protected void onPause() {
    super.onPause();
    adapter.onPause();
  }
}
