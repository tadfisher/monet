package monet;

import android.graphics.Bitmap;
import java.nio.Buffer;
import java.nio.IntBuffer;

public class BitmapImage implements Image {

  private final int frameDelay;
  private final Bitmap bitmap;
  private Buffer buffer;
  private int[] pixels;

  public BitmapImage(Bitmap bitmap) {
    this(bitmap, 0);
  }

  public BitmapImage(Bitmap bitmap, int frameDelay) {
    this.bitmap = bitmap;
    this.frameDelay = frameDelay;
  }

  @Override public int width() {
    return bitmap.getWidth();
  }

  @Override public int height() {
    return bitmap.getHeight();
  }

  @Override public int frameDelay() {
    return frameDelay;
  }

  @Override public Buffer asBuffer() {
    if (buffer == null) {
      buffer = IntBuffer.allocate(width() * height());
      bitmap.copyPixelsToBuffer(buffer);
    }
    return buffer;
  }

  @Override public int[] asPixels() {
    if (pixels == null) {
      final int w = width();
      final int h = height();
      pixels = new int[w * h];
      bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
    }
    return pixels;
  }

  @Override public Bitmap asBitmap() {
    return bitmap;
  }
}
