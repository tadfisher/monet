package monet;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import monet.bitmap.BitmapDecoder;

public final class Monet {
  static final List<Decoder.Factory> BUILT_IN_FACTORIES = new ArrayList<>();

  static {
    BUILT_IN_FACTORIES.add(BitmapDecoder.FACTORY);
  }

  private final List<Decoder.Factory> factories;
  private final Map<Type, Decoder<?>> decoderCache = new LinkedHashMap<>();

  Monet(Builder builder) {
    List<Decoder.Factory> factories = new ArrayList<>(
        builder.factories.size() + BUILT_IN_FACTORIES.size());
    factories.addAll(builder.factories);
    factories.addAll(BUILT_IN_FACTORIES);
    this.factories = Collections.unmodifiableList(factories);
  }

  public <T> Decoder<T> decoder(Class<T> type) {
    return decoder((Type) type);
  }

  @SuppressWarnings("unchecked")
  public <T> Decoder<T> decoder(Type type) {
    type = Types.canonicalize(type);

    // If there's an equivalent decoder in the cache, we're done!
    synchronized (decoderCache) {
      Decoder<?> result = decoderCache.get(type);
      if (result != null) return (Decoder<T>) result;
    }

    for (int i = 0, size = factories.size(); i < size; i++) {
      Decoder<T> result = (Decoder<T>) factories.get(i).create(type, this);
      if (result != null) {
        synchronized (decoderCache) {
          decoderCache.put(type, result);
        }
        return result;
      }
    }

    throw new IllegalArgumentException("No Decoder for " + type);
  }

  @SuppressWarnings("unchecked")
  public <T> Decoder<T> nextDecoder(Decoder.Factory skipPast, Type type) {
    type = Types.canonicalize(type);

    int skipPastIndex = factories.indexOf(skipPast);
    if (skipPastIndex == -1) {
      throw new IllegalArgumentException("Unable to skip past unknown factory " + skipPast);
    }
    for (int i = skipPastIndex + 1, size = factories.size(); i < size; i++) {
      Decoder<T> result = (Decoder<T>) factories.get(i).create(type, this);
      if (result != null) return result;
    }
    throw new IllegalArgumentException("No next Decoder for " + type);
  }

  public Builder newBuilder() {
    int fullSize = factories.size();
    int tailSize = BUILT_IN_FACTORIES.size();
    List<Decoder.Factory> customFactories = factories.subList(0, fullSize - tailSize);
    return new Builder().addAll(customFactories);
  }

  public static final class Builder {
    final List<Decoder.Factory> factories = new ArrayList<>();

    public <T> Builder add(final Type targetType, final Decoder<T> decoder) {
      Preconditions.checkNotNull(targetType, "type == null");
      Preconditions.checkNotNull(decoder, "decoder == null");
      add((type, monet) -> Types.equals(type, targetType) ? decoder : null);
      return this;
    }

    public Builder add(Decoder.Factory factory) {
      Preconditions.checkNotNull(factory, "factory == null");
      factories.add(factory);
      return this;
    }

    Builder addAll(List<Decoder.Factory> factories) {
      this.factories.addAll(factories);
      return this;
    }

    public Monet build() {
      return new Monet(this);
    }
  }
}
