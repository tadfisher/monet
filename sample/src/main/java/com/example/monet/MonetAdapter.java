package com.example.monet;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.example.monet.MonetAdapter.ViewHolder;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import monet.Monet;
import monet.MonetSchedulers;
import monet.Request;
import monet.decoder.bitmap.BitmapDecoder;
import monet.decoder.gif.GifDecoder;

class MonetAdapter extends RecyclerView.Adapter<ViewHolder> {

  private final ImgurService service;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final Monet monet = new Monet.Builder()
      .add(GifDecoder.create())
      .add(BitmapDecoder.create())
      .build();

  MonetAdapter(ImgurService service) {
    this.service = service;
  }

  void onPause() {
    disposables.clear();
  }

  @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    ImageView view =
        (ImageView) LayoutInflater.from(parent.getContext()).inflate(R.layout.image, parent, false);
    return new ViewHolder(view);
  }

  @Override public void onBindViewHolder(ViewHolder holder, int position) {
    holder.bind(Data.URLS[position]);
  }

  @Override public int getItemCount() {
    return Data.URLS.length;
  }

  final class ViewHolder extends RecyclerView.ViewHolder {

    private final ImageView view;
    private Disposable disposable;

    ViewHolder(ImageView itemView) {
      super(itemView);
      this.view = itemView;
    }

    void bind(String url) {
      if (disposable != null) {
        disposables.remove(disposable);
      }
      view.setImageDrawable(null);
      disposable = service.fetch(url)
          .map(body -> Request.builder(body.source()).fit(view).build())
          .observeOn(MonetSchedulers.decodeThread())
          .flatMapPublisher(r -> monet.decoder(r).apply(r))
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
              image -> view.setImageBitmap(image.asBitmap()),
              error -> Log.e("Monet", "Error decoding bitmap", error));
      disposables.add(disposable);
    }
  }
}
