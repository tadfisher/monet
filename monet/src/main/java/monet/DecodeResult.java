package monet;

public final class DecodeResult<T> {

  public static <T> DecodeResult<T> unsupported() {
    return new DecodeResult<>(false, null, null);
  }

  public static <T> DecodeResult<T> success(T result) {
    return new DecodeResult<>(true, result, null);
  }

  public static <T> DecodeResult<T> error(Throwable e) {
    return new DecodeResult<>(true, null, e);
  }

  final boolean supported;
  final T result;
  final Throwable error;

  /**
   * Result of a decoding attempt.
   * @param supported  true if this decoder supports the request.
   * @param result     decoded result, if successful
   * @param error      decode error, if not successful
   */
  DecodeResult(boolean supported, T result, Throwable error) {
    this.supported = supported;
    this.result = result;
    this.error = error;
  }
}
