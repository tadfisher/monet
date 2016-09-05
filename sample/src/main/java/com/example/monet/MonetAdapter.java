package com.example.monet;

import com.example.monet.MonetAdapter.ViewHolder;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Created by tad on 9/4/16.
 */

public class MonetAdapter extends RecyclerView.Adapter<ViewHolder> {

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ImageView view = (ImageView)
                LayoutInflater.from(parent.getContext()).inflate(R.layout.image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return Data.URLS.length;
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView view;

        ViewHolder(ImageView itemView) {
            super(itemView);
            this.view = itemView;
        }

        void bind(String url) {

        }
    }
}
