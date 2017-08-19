package monet;

import android.graphics.Bitmap;
import java.nio.Buffer;

public interface Image {
  int width();
  int height();
  Buffer asBuffer();
  int[] asPixels();
  Bitmap asBitmap();
}
