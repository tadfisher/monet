package com.example.monet;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import okhttp3.HttpUrl;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

public class MainActivity extends AppCompatActivity {

  ImgurService service;
  RecyclerView recyclerView;
  MonetAdapter adapter;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    service = new Retrofit.Builder().baseUrl(HttpUrl.parse("http://i.imgur.com/"))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
        .build()
        .create(ImgurService.class);

    recyclerView = (RecyclerView) findViewById(R.id.grid);
    recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
    adapter = new MonetAdapter(service);
    recyclerView.setAdapter(adapter);
  }

  @Override protected void onPause() {
    super.onPause();
    adapter.onPause();
  }
}
