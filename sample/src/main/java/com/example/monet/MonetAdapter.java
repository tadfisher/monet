package com.example.monet;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.example.monet.MonetAdapter.ViewHolder;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import monet.Monet;
import monet.Request;

class MonetAdapter extends RecyclerView.Adapter<ViewHolder> {

  private final ImgurService service;
  private final CompositeDisposable disposables = new CompositeDisposable();

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
          .map(body -> Request.builder(body.byteStream())
              .scale(ImageView.ScaleType.CENTER_CROP, view)
              .build())
          .flatMap(Monet::decodeSingle)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(view::setImageBitmap);
      disposables.add(disposable);
    }
  }
}
