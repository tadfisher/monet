package com.example.monet;

import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface ImgurService {

  @GET Single<ResponseBody> fetch(@Url String url);
}
