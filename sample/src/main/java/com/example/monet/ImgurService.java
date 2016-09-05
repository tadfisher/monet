package com.example.monet;

import okhttp3.ResponseBody;
import retrofit2.http.GET;
import retrofit2.http.Url;
import rx.Observable;

public interface ImgurService {

    @GET
    Observable<ResponseBody> fetch(@Url String url);
}
