package monet;

import android.graphics.Bitmap;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class BufferImage implements Image {

  private final int width;
  private final int height;
  private final int frameDelay;
  private final ByteBuffer buffer;
  private int[] pixels;
  private Bitmap bitmap;

  public BufferImage(int width, int height, ByteBuffer buffer) {
    this(width, height, 0, buffer);
  }

  public BufferImage(int width, int height, int frameDelay, ByteBuffer buffer) {
    this.width = width;
    this.height = height;
    this.frameDelay = frameDelay;
    this.buffer = buffer;
  }

  @Override public int width() {
    return width;
  }

  @Override public int height() {
    return height;
  }

  @Override public int frameDelay() {
    return frameDelay;
  }

  @Override public Buffer asBuffer() {
    return buffer;
  }

  @Override public int[] asPixels() {
    if (pixels == null) {
      pixels = new int[width * height];
      buffer.asIntBuffer().get(pixels);
    }
    return pixels;
  }

  @Override public Bitmap asBitmap() {
    if (bitmap == null) {
      bitmap = Bitmap.createBitmap(asPixels(), width, height, Bitmap.Config.ARGB_8888);
    }
    return bitmap;
  }
}
