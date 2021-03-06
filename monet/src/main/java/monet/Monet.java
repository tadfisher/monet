package monet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.reactivestreams.Publisher;

public final class Monet {
  private final List<Decoder> decoders;

  Monet(Builder builder) {
    List<Decoder> decoders = new ArrayList<>(builder.decoders.size());
    decoders.addAll(builder.decoders);
    this.decoders = Collections.unmodifiableList(decoders);
  }

  public Publisher<? extends Image> decode(Request request) {
    return decoder(request).publisher(request);
  }

  private Decoder decoder(Request request) {
    for (int i = 0; i < decoders.size(); i++) {
      final Decoder decoder = decoders.get(i);
      if (decoder.supports(request)) {
        return decoder;
      }
    }
    throw new DecodeException("Unsupported image type");
  }

  public Builder newBuilder() {
    return new Builder().addAll(decoders);
  }

  public static final class Builder {
    final List<Decoder> decoders = new ArrayList<>();

    public Builder add(Decoder decoder) {
      checkNotNull(decoder, "decoder == null");
      decoders.add(decoder);
      return this;
    }

    Builder addAll(List<Decoder> decoders) {
      this.decoders.addAll(decoders);
      return this;
    }

    public Monet build() {
      return new Monet(this);
    }
  }

  private static void checkNotNull(Object value, String message) {
    if (value == null) {
      throw new NullPointerException(message);
    }
  }
}
