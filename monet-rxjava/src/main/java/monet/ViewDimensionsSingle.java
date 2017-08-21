package monet;

import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.MainThreadDisposable;

class ViewDimensionsSingle extends Single<ViewDimensions> {

  private final View view;

  ViewDimensionsSingle(View view) {
    this.view = view;
  }

  @Override protected void subscribeActual(SingleObserver<? super ViewDimensions> observer) {
    if (!RxMonet.checkMainThread(observer)) return;

    int width = view.getWidth();
    int height = view.getHeight();
    if (width > 0 && height > 0) {
      observer.onSuccess(ViewDimensions.create(width, height));
    } else {
      Listener listener = new Listener(view, observer);
      observer.onSubscribe(listener);
      view.getViewTreeObserver().addOnPreDrawListener(listener);
    }
  }

  static final class Listener extends MainThreadDisposable implements OnPreDrawListener {

    private final View view;
    private final SingleObserver<? super ViewDimensions> observer;

    Listener(View view, SingleObserver<? super ViewDimensions> observer) {
      this.view = view;
      this.observer = observer;
    }

    @Override public boolean onPreDraw() {
      if (!isDisposed()) {
        ViewTreeObserver vto = view.getViewTreeObserver();
        if (vto.isAlive()) {
          int width = view.getWidth();
          int height = view.getHeight();
          if (width > 0 && height > 0) {
            observer.onSuccess(ViewDimensions.create(width, height));
            dispose();
          }
        }
      }
      return true;
    }

    @Override protected void onDispose() {
      view.getViewTreeObserver().removeOnPreDrawListener(this);
    }
  }
}
