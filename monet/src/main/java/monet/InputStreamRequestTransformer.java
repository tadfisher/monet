package monet;

import java.io.InputStream;
import monet.Request.Builder;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

class InputStreamRequestTransformer implements Transformer<InputStream, Builder> {

    @Override
    public Observable<Builder> call(Observable<InputStream> streamObservable) {
        return streamObservable
                .observeOn(Schedulers.computation())
                .map(new Func1<InputStream, Builder>() {
                    @Override
                    public Request.Builder call(InputStream stream) {
                        return Request.builder().stream(stream);
                    }
                });
    }
}
