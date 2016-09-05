package com.example.monet;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.example.monet.MonetAdapter.ViewHolder;
import com.simple.monet.Monet;

import okhttp3.ResponseBody;
import rx.Subscription;
import rx.subscriptions.CompositeSubscription;

class MonetAdapter extends RecyclerView.Adapter<ViewHolder> {

    final ImgurService service;
    CompositeSubscription subscriptions;

    MonetAdapter(ImgurService service) {
        this.service = service;
    }

    void onResume() {
        subscriptions = new CompositeSubscription();
    }

    void onPause() {
        if (subscriptions != null) {
            subscriptions.unsubscribe();
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ImageView view = (ImageView)
                LayoutInflater.from(parent.getContext()).inflate(R.layout.image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bind(Data.URLS[position]);
    }

    @Override
    public int getItemCount() {
        return Data.URLS.length;
    }

    final class ViewHolder extends RecyclerView.ViewHolder {

        final ImageView view;
        Subscription subscription;

        ViewHolder(ImageView itemView) {
            super(itemView);
            this.view = itemView;
        }

        void bind(String url) {
            if (subscription != null) {
                subscriptions.remove(subscription);
                subscription.unsubscribe();
            }
            view.setImageDrawable(null);
            subscription = service.fetch(url)
                    .map(ResponseBody::byteStream)
                    .compose(Monet.fromInputStream())
                    .compose(Monet.fit(view))
                    .compose(Monet.decode())
                    .subscribe(view::setImageBitmap);
            subscriptions.add(subscription);
        }
    }
}
