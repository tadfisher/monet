package monet.decoder.gif;

import android.graphics.Bitmap;
import io.reactivex.Observable;
import monet.DecodeResult;
import monet.Decoder;
import monet.Request;

public class GifDecoder extends Decoder<Observable<Bitmap>> {

  @Override public DecodeResult<Observable<Bitmap>> decode(Request request) {
    return null;
  }
}
