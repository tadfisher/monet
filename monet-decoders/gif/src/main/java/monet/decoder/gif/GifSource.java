package monet.decoder.gif;

import android.annotation.SuppressLint;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.annotation.Nullable;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Source;
import okio.Timeout;

public final class GifSource implements Source {

  // Outer states for the GIF parser.
  private static final byte SECTION_HEADER = 0;
  private static final byte SECTION_BODY = 1;
  private static final byte SECTION_DONE = 2;

  // Inner states for each GIF frame.
  private static final byte FRAME_HEADER = 0;
  private static final byte FRAME_DATA = 1;

  private static final ByteString SIGNATURE = ByteString.encodeUtf8("GIF");

  // Netscape extension
  private static final ByteString APPLICATION_NETSCAPE =
      ByteString.encodeString("NETSCAPE2.0", Charset.forName("US-ASCII"));

  // Frame disposal methods
  private static final int DISPOSAL_METHOD_UNKNOWN = 0;
  private static final int DISPOSAL_METHOD_LEAVE = 1;
  private static final int DISPOSAL_METHOD_BACKGROUND = 2;
  private static final int DISPOSAL_METHOD_RESTORE = 3;

  // Decoding parameters
  private static final int MAX_STACK_SIZE = 4096;

  // Work buffers
  private Buffer indexData;
  private short[] prefix;
  private byte[] suffix;
  private byte[] pixelStack;

  private final BufferedSource source;

  private int section = SECTION_HEADER;
  private int frameSection = FRAME_HEADER;

  private Header header;
  private Frame frame;
  private ByteString restoreCanvas;
  private int pos = 0;

  GifSource(BufferedSource source) {
    this.source = source;
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0 :" + byteCount);
    if (byteCount == 0) return 0;

    if (section == SECTION_HEADER) {
      header = readHeader();
      section = SECTION_BODY;
    }

    if (section == SECTION_BODY) {
      if (frame == null || pos >= frame.pixelData.size()) {
        frameSection = FRAME_HEADER;
        frame = readFrame();
        pos = 0;
      }
      if (frame == null) {
        section = SECTION_DONE;
        return -1;
      }
      int bytes = (int) Math.min(frame.pixelData.size() - pos, byteCount);
      sink.write(frame.pixelData.substring(pos, bytes));
      pos = bytes;
      return bytes;
    }

    if (!source.exhausted()) {
      throw new IOException("gif read finished without exhausting source");
    }
    section = SECTION_DONE;
    return -1;
  }

  public Header readHeader() throws IOException {
    // Signature
    //
    //    7 6 5 4 3 2 1 0        Field Name                    Type
    //   +---------------+
    // 0 |               |       Signature                     3 Bytes
    //   +-             -+
    // 1 |               |
    //   +-             -+
    // 2 |               |
    //   +---------------+
    // 3 |               |       Version                       3 Bytes
    //   +-             -+
    // 4 |               |
    //   +-             -+
    // 5 |               |
    //   +---------------+
    source.require(6);
    if (!source.rangeEquals(0, SIGNATURE)) {
      throw new UnsupportedFormatException("GIF signature not found");
    }
    source.skip(6);

    // Logical Screen Descriptor
    //
    //     7 6 5 4 3 2 1 0        Field Name                    Type
    //    +---------------+
    // 0  |               |       Logical Screen Width          Unsigned
    //    +-             -+
    // 1  |               |
    //    +---------------+
    // 2  |               |       Logical Screen Height         Unsigned
    //    +-             -+
    // 3  |               |
    //    +---------------+
    // 4  | |     | |     |       <Packed Fields>               See below
    //    +---------------+
    // 5  |               |       Background Color Index        Byte
    //    +---------------+
    // 6  |               |       Pixel Aspect Ratio            Byte
    //    +---------------+
    //
    //    <Packed Fields>  =      Global Color Table Flag       1 Bit
    //                            Color Resolution              3 Bits
    //                            Sort Flag                     1 Bit
    //                            Size of Global Color Table    3 Bits
    final int width = readShort();
    final int height = readShort();
    final int packed = readByte();
    final int globalColorTableSize = 2 << (packed & 0x07);
    final int backgroundIndex = readByte();
    source.skip(1); // Ignore pixel aspect ratio.

    int[] globalColorTable = null;
    if ((packed & 0x80) != 0 && globalColorTableSize > 0) {
      globalColorTable = readColorTable(globalColorTableSize);
    }

    // Peek for netscape extension
    int loopCount = 0;
    if (source.request(19)) {
      ByteString extension = source.buffer().snapshot(13);
      if (extension.getByte(0) == 0x21
          && extension.getByte(1) == 0xff
          && extension.rangeEquals(3, APPLICATION_NETSCAPE, 0, 11)) {
        source.skip(14); // extension header, app extension id
        loopCount = readShort();
        source.skip(1); // block terminator
      }
    }

    final Header header = new Header(width, height, globalColorTable, globalColorTableSize,
        backgroundIndex, loopCount);

    section = SECTION_BODY;
    this.header = header;
    return header;
  }

  @Nullable public Frame readFrame() throws IOException {
    if (section == SECTION_HEADER) header = readHeader();
    if (section == SECTION_DONE) return null;
    frame = readFrameInternal(frame);
    return frame;
  }

  @Nullable private Frame readFrameInternal(final Frame prev) throws IOException {
    frame = new Frame();
    while (frameSection == FRAME_HEADER) {
      int code = readByte();
      switch (code) {
        case 0x21:
          readFrameExtension(frame);
          break;
        case 0x2c:
          readImageDescriptor(frame);
          break;
        case 0x3b:
          section = SECTION_DONE;
          return null;  // Trailer
      }
    }
    frame.dispose(header, prev, restoreCanvas);
    readFrameImageData(frame);
    return frame;
  }

  private void readFrameExtension(Frame frame) throws IOException {
    int code = readByte();
    switch (code) {

      // Graphics control extension
      //     7 6 5 4 3 2 1 0        Field Name                    Type
      //    +---------------+
      // 0  |               |       Extension Introducer          Byte
      //    +---------------+
      // 1  |               |       Graphic Control Label         Byte
      //    +---------------+
      //
      //    +---------------+
      // 0  |               |       Block Size                    Byte
      //    +---------------+
      // 1  |     |     | | |       <Packed Fields>               See below
      //    +---------------+
      // 2  |               |       Delay Time                    Unsigned
      //    +-             -+
      // 3  |               |
      //    +---------------+
      // 4  |               |       Transparent Color Index       Byte
      //    +---------------+
      //
      //    +---------------+
      // 0  |               |       Block Terminator              Byte
      //    +---------------+
      //
      //     <Packed Fields>  =     Reserved                      3 Bits
      //                            Disposal Method               3 Bits
      //                            User Input Flag               1 Bit
      //                            Transparent Color Flag        1 Bit
      case 0xf9:
        source.skip(1); // fixed block size
        int packed = readByte();
        frame.disposalMethod = (packed & 0x1c) >> 2;
        frame.transparentColorFlag = (packed & 1) != 0;
        frame.setDelayTime(readShort() * 10);
        if (frame.transparentColorFlag) {
          frame.transparentColorIndex = readByte();
        } else {
          source.skip(1);
          frame.transparentColorIndex = -1;
        }
        source.skip(1); // block terminator
        break;

      // Application extension
      //     7 6 5 4 3 2 1 0        Field Name                    Type
      //    +---------------+
      // 0  |               |       Extension Introducer          Byte
      //    +---------------+
      // 1  |               |       Extension Label               Byte
      //    +---------------+
      //
      //    +---------------+
      // 0  |               |       Block Size                    Byte
      //    +---------------+
      // 1  |               |
      //    +-             -+
      // 2  |               |
      //    +-             -+
      // 3  |               |       Application Identifier        8 Bytes
      //    +-             -+
      // 4  |               |
      //    +-             -+
      // 5  |               |
      //    +-             -+
      // 6  |               |
      //    +-             -+
      // 7  |               |
      //    +-             -+
      // 8  |               |
      //    +---------------+
      // 9  |               |
      //    +-             -+
      //10  |               |       Appl. Authentication Code     3 Bytes
      //    +-             -+
      //11  |               |
      //    +---------------+
      //
      //    +===============+
      //    |               |
      //    |               |       Application Data              Data Sub-blocks
      //    |               |
      //    |               |
      //    +===============+
      //
      //    +---------------+
      // 0  |               |       Block Terminator              Byte
      //    +---------------+
      case 0xff:
        // Ignore application extensions; we've already read the only one we care about (Netscape).
        source.skip(readByte() + 13); // app extension id, app data, block terminator
    }
  }

  private void readImageDescriptor(Frame frame) throws IOException {
    // Image Descriptor
    //
    //     7 6 5 4 3 2 1 0        Field Name                    Type
    //    +---------------+
    // 0  |               |       Image Separator               Byte
    //    +---------------+
    // 1  |               |       Image Left Position           Unsigned
    //    +-             -+
    // 2  |               |
    //    +---------------+
    // 3  |               |       Image Top Position            Unsigned
    //    +-             -+
    // 4  |               |
    //    +---------------+
    // 5  |               |       Image Width                   Unsigned
    //    +-             -+
    // 6  |               |
    //    +---------------+
    // 7  |               |       Image Height                  Unsigned
    //    +-             -+
    // 8  |               |
    //    +---------------+
    // 9  | | | |   |     |       <Packed Fields>               See below
    //    +---------------+
    //
    //    <Packed Fields>  =      Local Color Table Flag        1 Bit
    //                            Interlace Flag                1 Bit
    //                            Sort Flag                     1 Bit
    //                            Reserved                      2 Bits
    //                            Size of Local Color Table     3 Bits
    source.require(9);

    frame.imageLeftPosition = readShort();
    frame.imageTopPosition = readShort();
    frame.imageWidth = readShort();
    frame.imageHeight = readShort();

    int packed = readByte();
    frame.localColorTableFlag = (packed & 0x80) != 0;
    frame.interlaceFlag = (packed & 0x40) != 0;
    frame.sortFlag = (packed & 0x20) != 0;
    frame.localColorTableSize = 2 << (packed & 0x07);

    if (frame.localColorTableFlag) {
      // Local color table
      //
      //       7 6 5 4 3 2 1 0        Field Name                    Type
      //      +===============+
      //   0  |               |       Red 0                         Byte
      //      +-             -+
      //   1  |               |       Green 0                       Byte
      //      +-             -+
      //   2  |               |       Blue 0                        Byte
      //      +-             -+
      //   3  |               |       Red 1                         Byte
      //      +-             -+
      //      |               |       Green 1                       Byte
      //      +-             -+
      //  up  |               |
      //      +-   . . . .   -+       ...
      //  to  |               |
      //      +-             -+
      //      |               |       Green 255                     Byte
      //      +-             -+
      // 767  |               |       Blue 255                      Byte
      //      +===============+
      frame.localColorTable = readColorTable(frame.localColorTableSize);
      frame.activeColorTable = frame.localColorTable;
    } else {
      frame.activeColorTable = header.globalColorTable;
    }

    frameSection = FRAME_DATA;
  }

  private void readFrameImageData(Frame frame) throws IOException {
    frame.indexData = readFrameIndexData(frame.imageWidth * frame.imageHeight);
    frame.pixelData = readFramePixelData(frame);

    if (frame.disposalMethod == DISPOSAL_METHOD_UNKNOWN
        || frame.disposalMethod == DISPOSAL_METHOD_LEAVE) {
      restoreCanvas = frame.pixelData;
    }

    frameSection = FRAME_HEADER;
  }

  private ByteString readFrameIndexData(final int count) throws IOException {
    // Image data block
    //
    //  7 6 5 4 3 2 1 0        Field Name                    Type
    // +---------------+
    // |               |       LZW Minimum Code Size         Byte
    // +---------------+
    //
    // +===============+
    // |               |
    // /               /       Image Data                    Data Sub-blocks
    // |               |
    // +===============+

    // LZW raster data

    //  7 6 5 4 3 2 1 0
    // +---------------+
    // | LZW code size |
    // +---------------+
    //
    // +---------------+ ----+
    // |  block size   |     |
    // +---------------+     |
    // |               |     +-- Repeated as many
    // |  data bytes   |     |   times as necessary.
    // |               |     |
    // +---------------+ ----+
    //
    // . . .       . . . ------- The code that terminates the LZW
    //                           compressed data must appear before
    //                           Block Terminator.
    // +---------------+
    // |0 0 0 0 0 0 0 0|  Block Terminator
    // +---------------+
    if (indexData == null) {
      indexData = new Buffer();
    } else {
      indexData.clear();
    }

    if (prefix == null) prefix = new short[MAX_STACK_SIZE];
    if (suffix == null) suffix = new byte[MAX_STACK_SIZE];
    if (pixelStack == null) pixelStack = new byte[MAX_STACK_SIZE + 1];

    final int dataSize = readByte();
    final int clearCode = 1 << dataSize;
    final int endCode = clearCode + 1;
    int available = clearCode + 2;
    int oldCode = -1;
    int codeSize = dataSize + 1;
    int codeMask = (1 << codeSize) - 1;
    int datum = 0;
    int bits = 0;
    int first = 0;
    int top = 0;
    int remaining = 0;
    int code;
    int i;

    for (code = 0; code < clearCode; code++) {
      prefix[code] = 0;
      suffix[code] = (byte) code;
    }

    for (i = 0; i < count; ) {
      if (remaining == 0) {
        remaining = readByte();
        if (remaining <= 0) break;
      }

      datum |= readByte() << bits;
      bits += 8;
      remaining--;

      while (bits >= codeSize) {
        // Get the next code.
        code = datum & codeMask;
        datum >>= codeSize;
        bits -= codeSize;

        // Interpret the code.
        if (code == clearCode) {
          // Reset decoder.
          codeSize = dataSize + 1;
          codeMask = (1 << codeSize) - 1;
          available = clearCode + 2;
          oldCode = -1;
          continue;
        }

        if (code == endCode || code > available) {
          break;
        }

        if (oldCode == -1) {
          indexData.writeByte(code);
          i++;
          oldCode = code;
          first = code;
          continue;
        }

        int inCode = code;
        if (code >= available) {
          pixelStack[top++] = (byte) first;
          code = oldCode;
        }

        while (code >= clearCode) {
          pixelStack[top++] = suffix[code];
          code = prefix[code];
        }

        first = suffix[code] & 0xff;
        pixelStack[top++] = (byte) first;

        if (available >= MAX_STACK_SIZE) break;

        prefix[available] = (short) oldCode;
        suffix[available] = (byte) first;
        available++;

        if (((available & codeMask) == 0) && (available < MAX_STACK_SIZE)) {
          codeSize++;
          codeMask += available;
        }

        oldCode = inCode;

        // Drain the pixel stack.
        while (top > 0) {
          indexData.writeByte(pixelStack[--top]);
          i++;
          pixelStack[top] = 0;
        }
      }
    }

    // Clear missing pixels.
    for (; i < count; i++) {
      indexData.writeByte(0);
    }

    source.skip(remaining + 1); // padding + block terminator
    return indexData.snapshot();
  }

  private ByteString readFramePixelData(final Frame frame) throws IOException {
    Buffer pixelData = new Buffer();
    ByteString indexData = frame.indexData;
    int[] colors = frame.activeColorTable;
    int transparentIndex = frame.transparentColorFlag ? frame.transparentColorIndex : -1;

    final int w = frame.imageWidth;
    final int h = frame.imageHeight;
    final int left = frame.imageLeftPosition;
    final int top = frame.imageTopPosition;
    final int right = left + w;
    final int bottom = top + h;
    final int stride = header.width * 4;
    final Buffer canvas = new Buffer();
    canvas.write(frame.canvas);

    int dy, dx, sy;

    int n1 = 0, n2 = 0, n3 = 0;
    if (frame.interlaceFlag) {
      n1 = (h + 7) / 8;
      n2 = (h + 3) / 4;
      n3 = (h + 1) / 2;
    }

    // Top unpopulated region
    if (top > 0) {
      pixelData.write(canvas, top * stride);
    }

    for (dy = 0; dy < h; dy++) {
      // Left unpopulated region
      if (left > 0) {
        pixelData.write(canvas, left * 4);
      }

      if (frame.interlaceFlag) {
        sy = dy % 8 == 0 ?       dy      / 8
           : dy % 4 == 0 ? n1 + (dy - 4) / 8
           : dy % 2 == 0 ? n2 + (dy - 2) / 4
           :               n3 + (dy - 1) / 2;
      } else {
        sy = dy;
      }

      for (dx = 0; dx < w; dx++) {
        final int pos = sy * w + dx;
        final int index = indexData.getByte(pos) & 0xff;
        if (index == transparentIndex) {
          pixelData.write(canvas, 4);
        } else {
          pixelData.writeInt(colors[index]);
          canvas.skip(4);
        }
      }

      // Right unpopulated region
      if (right < header.width) {
        pixelData.write(canvas, (header.width - right) * 4);
      }
    }

    // Bottom unpopulated region
    if (bottom < header.height) {
      pixelData.write(canvas, (header.height - bottom) * stride);
    }

    return pixelData.snapshot();
  }

  @Override public Timeout timeout() {
    return source.timeout();
  }

  @Override public void close() throws IOException {
    source.close();
  }

  private int readByte() throws IOException {
    return source.readByte() & 0xff;
  }

  private int readShort() throws IOException {
    return source.readShortLe() & 0xffff;
  }

  private int[] readColorTable(int count) throws IOException {
    // Global Color Table
    //
    //       7 6 5 4 3 2 1 0        Field Name                    Type
    //      +===============+
    //   0  |               |       Red 0                         Byte
    //      +-             -+
    //   1  |               |       Green 0                       Byte
    //      +-             -+
    //   2  |               |       Blue 0                        Byte
    //      +-             -+
    //   3  |               |       Red 1                         Byte
    //      +-             -+
    //      |               |       Green 1                       Byte
    //      +-             -+
    //  up  |               |
    //      +-   . . . .   -+       ...
    //  to  |               |
    //      +-             -+
    //      |               |       Green 255                     Byte
    //      +-             -+
    // 767  |               |       Blue 255                      Byte
    //      +===============+
    source.require(count * 3);
    int[] table = new int[count];
    for (int i = 0; i < count; i++) {
      table[i] = readByte() << 16
          | readByte() << 8
          | readByte()
          | 0xff000000;
    }
    return table;
  }

  public static class Header {
    final int width;
    final int height;
    final int globalColorTableSize;
    final int[] globalColorTable;
    final int backgroundIndex;
    final int loopCount;
    final ByteString background;

    Header(int width, int height, @Nullable int[] globalColorTable, int globalColorTableSize,
        int backgroundIndex, int loopCount) {
      this.width = width;
      this.height = height;
      this.globalColorTable = globalColorTable;
      this.globalColorTableSize = globalColorTableSize;
      this.backgroundIndex = backgroundIndex;
      this.loopCount = loopCount;

      Buffer canvas = new Buffer();
      int count = width * height;
      if (globalColorTable != null) {
        int background = globalColorTable[backgroundIndex];
        for (int i = 0; i < count; i++) {
          canvas.writeInt(background);
        }
      } else {
        for (int i = 0; i < count; i++) {
          canvas.writeInt(0);
        }
      }
      background = canvas.snapshot();
    }

    public int width() {
      return width;
    }

    public int height() {
      return height;
    }

    public int loopCount() {
      return loopCount;
    }
  }

  public static class Frame {
    // Graphic control extension
    int delayTime;
    int disposalMethod;
    int transparentColorIndex;
    boolean transparentColorFlag;

    // Netscape extension
    int loopCount;

    // Image header
    int imageLeftPosition;
    int imageTopPosition;
    int imageWidth;
    int imageHeight;
    boolean localColorTableFlag;
    int localColorTableSize;
    int[] localColorTable;
    boolean interlaceFlag;
    boolean sortFlag;
    int[] activeColorTable;

    // Image data
    ByteString canvas;
    ByteString indexData;
    ByteString pixelData;

    Frame() {
      // Prevent instantiation outside this package.
    }

    public int delayTime() {
      return delayTime;
    }

    public int loopCount() {
      return loopCount;
    }

    public ByteString pixels() {
      return pixelData;
    }

    void setDelayTime(int delayTime) {
      this.delayTime = delayTime <= 10 ? 100 : delayTime;
    }

    void dispose(final Header header, @Nullable final Frame prev,
        @Nullable final ByteString restoreCanvas) throws IOException {
      switch (disposalMethod) {
        case DISPOSAL_METHOD_LEAVE:
          canvas = prev == null
              ? header.background
              : prev.pixelData;
          break;

        case DISPOSAL_METHOD_RESTORE:
          canvas = restoreCanvas == null
              ? header.background
              : restoreCanvas;
          break;

        case DISPOSAL_METHOD_UNKNOWN:
        case DISPOSAL_METHOD_BACKGROUND:
        default:
          canvas = header.background;
      }
    }

    @SuppressLint("DefaultLocale")
    @Override public String toString() {
      return String.format("Frame(\n"
          + "  graphics control:\n"
          + "    delayTime=%d\n"
          + "    disposalMethod=%d\n"
          + "    transparentColorIndex=%d\n"
          + "    transparentColorFlag=%s\n"
          + "  netscape:\n"
          + "    loopCount=%d\n"
          + "  header:\n"
          + "    imageLeftPosition=%d\n"
          + "    imageTopPosition=%d\n"
          + "    imageWidth=%d\n"
          + "    imageHeight=%d\n"
          + "    localColorTableFlag=%s\n"
          + "    interlaceFlag=%s\n"
          + "    sortFlag=%s\n"
          + "    localColorTableSize=%d\n"
          + ")\n",
          delayTime,
          disposalMethod,
          transparentColorIndex,
          transparentColorFlag,
          loopCount,
          imageLeftPosition,
          imageTopPosition,
          imageWidth,
          imageHeight,
          localColorTableFlag,
          interlaceFlag,
          sortFlag,
          localColorTableSize
      );
    }
  }

  public static class UnsupportedFormatException extends IOException {
    UnsupportedFormatException(String message) {
      super(message);
    }
  }
}
