package monet;

import org.reactivestreams.Publisher;

/** Decode a {@link Request} into some arbitrary type representing an image. */
public abstract class Decoder {

  public abstract boolean supports(Request request);

  public abstract Publisher<? extends Image> publisher(Request request);
}
