package monet.decoder.gif;

import android.support.annotation.VisibleForTesting;
import java.io.IOException;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Source;
import okio.Timeout;

public final class GifSource implements Source {
  private static final byte SECTION_SIGNATURE = 0;
  private static final byte SECTION_HEADER = 1;
  private static final byte SECTION_BODY = 3;
  private static final byte SECTION_DONE = 4;

  private static final ByteString SIGNATURE = ByteString.encodeUtf8("GIF");

  private final BufferedSource source;
  @VisibleForTesting final GifBlockSource blockSource;

  private int section = SECTION_SIGNATURE;

  GifSource(BufferedSource source) {
    this.source = source;
    this.blockSource = new GifBlockSource(source);
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0 :" + byteCount);
    if (byteCount == 0) return 0;

    if (section == SECTION_SIGNATURE) readSignature();
    if (section == SECTION_HEADER) readHeader();
    if (section == SECTION_BODY) {
      long result = blockSource.read(sink, byteCount);
      if (result != -1) return result;
    }
    if (!source.exhausted()) {
      throw new IOException("gif finished without exhausting source");
    }
    section = SECTION_DONE;
    return -1;
  }

  @VisibleForTesting
  void readSignature() throws IOException {
    source.require(6);
    if (!source.rangeEquals(0, SIGNATURE)) {
      throw new IOException("GIF signature not found");
    }
    source.skip(6);
    section = SECTION_HEADER;
  }

  @VisibleForTesting
  Format readHeader() throws IOException {
    final int width = readShort();
    final int height = readShort();

    final int packed = readByte();
    final int globalColorTableSize = 2 << (packed & 0x07);
    final int backgroundIndex = readByte();
    source.skip(1); // Ignore pixel aspect ratio.

    final Buffer globalColorTable = new Buffer();
    if ((packed & 0x80) != 0 && globalColorTableSize > 0) {
      globalColorTable.write(source, globalColorTableSize * 3);
    }

    final Format format =
        new Format(width, height, globalColorTable, globalColorTableSize, backgroundIndex);

    blockSource.setFormat(format);
    section = SECTION_BODY;

    return format;
  }

  @Override public Timeout timeout() {
    return source.timeout();
  }

  @Override public void close() throws IOException {
    blockSource.close();
  }

  private int readByte() throws IOException {
    return source.readByte() & 0xff;
  }

  private int readShort() throws IOException {
    return source.readShortLe() & 0xffff;
  }

  static class Format {
    final int width;
    final int height;

    final Buffer globalColorTable;
    final int globalColorTableSize;
    final int backgroundIndex;

    Format(int width, int height, Buffer globalColorTable, int globalColorTableSize,
        int backgroundIndex) {
      this.width = width;
      this.height = height;
      this.globalColorTable = globalColorTable;
      this.globalColorTableSize = globalColorTableSize;
      this.backgroundIndex = backgroundIndex;
    }
  }
}
