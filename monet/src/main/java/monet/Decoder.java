package monet;

import io.reactivex.annotations.CheckReturnValue;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

/** Decode a {@link Request} into some arbitrary type representing an image. */
public abstract class Decoder implements Function<Request, Publisher<? extends Image>> {

  @CheckReturnValue
  protected abstract boolean supports(Request request);

  protected abstract Publisher<? extends Image> publisher(Request request);

  @Override public final Publisher<? extends Image> apply(Request request) throws Exception {
    return publisher(request);
  }
}
