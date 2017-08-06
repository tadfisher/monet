package monet.decoder.gif;

import android.annotation.SuppressLint;
import android.support.annotation.VisibleForTesting;
import java.io.IOException;
import java.nio.charset.Charset;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Source;
import okio.Timeout;

final class GifBlockSource implements Source {
  private static final byte SECTION_HEADER = 0;
  private static final byte SECTION_DATA = 3;
  private static final byte SECTION_PIXELS = 4;
  private static final byte SECTION_DONE = 5;

  private static final int MAX_STACK_SIZE = 4096;
  private static final int MAX_BITS = MAX_STACK_SIZE + 1;

  private static final ByteString APPLICATION_NETSCAPE =
      ByteString.encodeString("NETSCAPE2.0", Charset.forName("US-ASCII"));

  private static final int DISPOSAL_METHOD_UNKNOWN = 0;
  private static final int DISPOSAL_METHOD_LEAVE = 1;
  private static final int DISPOSAL_METHOD_BACKGROUND = 2;
  private static final int DISPOSAL_METHOD_RESTORE = 3;

  @VisibleForTesting final Frame frame = new Frame();

  private final short[] prefix = new short[MAX_STACK_SIZE];
  private final byte[] suffix = new byte[MAX_STACK_SIZE];
  private final byte[] pixelStack = new byte[MAX_STACK_SIZE + 1];

  private final BufferedSource source;

  private int section;

  private long remaining;

  GifBlockSource(BufferedSource source) {
    this.source = source;
  }

  void setFormat(GifSource.Format format) {
    frame.format = format;
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    // The body is a sequence of data blocks, each containing an optional globalColorTable,
    // extension, image header, and required LZW-compressed data section.
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    if (byteCount == 0) return 0;
    if (frame.format == null) throw new IOException("format == null");

    if (section == SECTION_HEADER) readHeader();
    if (section == SECTION_DATA) readData();
    if (section == SECTION_PIXELS) {
      long result = readPixels(sink, byteCount);
      if (result != -1) {
        return result;
      }
    }
    // TODO
    return -1;
  }

  @VisibleForTesting
  void readHeader() throws IOException {
    frame.reset();
    while (section == SECTION_HEADER) {
      int code = readByte();
      switch (code) {
        case 0x21: readExtension(); break;
        case 0x2c: readImageHeader(); break;
        case 0x3b: section = SECTION_DONE; break;
        default: break;
      }
    }
  }

  private void readExtension() throws IOException {
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
        // Skip fixed block size.
        source.skip(1);
        int packed = readByte();
        frame.disposalMethod = (packed & 0x1c) >> 2;
        frame.transparentColorFlag = (packed & 1) != 0;
        frame.setDelayTime(readShort() * 10);
        frame.transparentColorIndex = readByte();
        // Skip block terminator.
        source.skip(1);
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
        int blockSize = readByte();
        ByteString application = source.readByteString(11);
        if (APPLICATION_NETSCAPE.equals(application)) {
          readNetscapeExtension();
        } else {
          // We don't handle other extension types.
          source.skip(blockSize - 11);
        }
        break;
    }
  }

  private void readNetscapeExtension() throws IOException {
    //     +===============+                      --+
    // 14  |     0x03      |  Sub-block Data Size   |
    //     +---------------+                        |
    // 15  |     0x01      |  Sub-block ID          |
    //     +---------------+                        | Application Data Sub-block
    // 16  |               |                        |
    //     +-             -+  Loop Count (2 bytes)  |
    // 17  |               |                        |
    //     +===============+                      --+
    // 18  |     0x00      |  Block Terminator
    //     +---------------+

    source.require(5);

    // Skip fixed sub-block size and ID.
    source.skip(2);
    frame.loopCount = readShort();
    // Skip block terminator.
    source.skip(1);
  }

  private void readImageHeader() throws IOException {
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
      source.read(frame.localColorTable, frame.localColorTableSize * 3);
      frame.activeColorTable = frame.localColorTable;
    } else {
      frame.activeColorTable = frame.format.globalColorTable;
    }

    // Reset image data
    frame.indexData.clear();

    section = SECTION_DATA;
  }

  @VisibleForTesting
  void readData() throws IOException {
    readIndexData();
    populateImageData();
    remaining = frame.imageData.length;
    section = SECTION_PIXELS;
  }

  private void readIndexData() throws IOException {
    // Image data block

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

    final int pixels = frame.imageWidth * frame.imageHeight;
    final int dataSize = readByte();
    final int clearCode = 1 << dataSize;
    final int endCode = clearCode + 1;
    int available = clearCode + 2;
    int oldCode = -1;
    int codeSize = dataSize + 1;
    int codeMask = (1 << codeSize) - 1;

    for (int i = 0; i < clearCode; i++) {
      prefix[i] = 0;
      suffix[i] = (byte) i;
    }

    int code;
    int datum = 0;
    int bits = 0;
    int first = 0;
    int top = 0;
    int pi = 0;
    int remaining = 0;

    for (int i = 0; i < pixels; ) {
      if (remaining == 0) {
        remaining = readByte();
        if (remaining <= 0) {
          System.out.println("remaining <= 0");
          break;
        }
      }

      datum += readByte() << bits;
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

        if (code > available) {
          System.out.println("code > available");
          break;
        }

        // Check for explicit end-of-stream
        if (code == endCode) {
          source.skip(remaining);
          return;
        }

        if (oldCode == -1) {
          frame.indexData.writeByte(suffix[code]);
          pi++;
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

        first = suffix[code];
        pixelStack[top++] = (byte) first;

        if (available < MAX_STACK_SIZE) {
          prefix[available] = (short) oldCode;
          suffix[available] = (byte) first;
          available++;

          if (((available & codeMask) == 0) && (available < MAX_STACK_SIZE)) {
            codeSize++;
            codeMask += available;
          }
        }

        oldCode = inCode;

        // Drain the pixel stack.
        while (top > 0) {
          frame.indexData.writeByte(pixelStack[--top]);
          pi++;
          i++;
        }
      }
    }

    // Clear missing pixels.
    while (pi < pixels) {
      frame.indexData.writeByte(0);
      pi++;
    }
  }

  private long readPixels(Buffer sink, long byteCount) {
    byteCount = Math.min(remaining, byteCount);
    sink.write(frame.imageData, (int) (frame.imageData.length - byteCount), (int) byteCount);
    return byteCount;
  }

  private void populateImageData() throws IOException {
    frame.imageData = new byte[frame.imageWidth * frame.imageHeight];

    int pass = 1;
    int inc = 8;
    int iline = 0;
    for (int i = 0; i < frame.imageHeight; i++) {
      int line = i;
      if (frame.interlaceFlag) {
        if (iline >= frame.imageHeight) {
          switch (++pass) {
            case 2:
              iline = 4;
              break;
            case 3:
              iline = 2;
              inc = 4;
              break;
            case 4:
              iline = 1;
              inc = 2;
              break;
          }
        }
        line = iline;
        iline += inc;
      }

      // Map source line data to dest image data
      int dx = line * frame.imageWidth;
      int end = dx + frame.imageWidth;
      for (; dx < end; dx++) {
        int index = frame.indexData.readByte() & 0xff;
        byte c = frame.activeColorTable.getByte(index);
        if (c != 0) {
          frame.imageData[dx] = c;
        }
      }
    }
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
    return source.readShortLe() & 0xff;
  }

  static class Frame {
    GifSource.Format format;

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
    boolean interlaceFlag;
    boolean sortFlag;
    int localColorTableSize;
    Buffer activeColorTable;

    final Buffer localColorTable = new Buffer();
    final Buffer indexData = new Buffer();
    byte[] imageData;

    void setDelayTime(int delayTime) {
      this.delayTime = delayTime <= 10 ? 100 : delayTime;
    }

    void reset() {
      delayTime = -1;
      disposalMethod = DISPOSAL_METHOD_UNKNOWN;
      transparentColorIndex = -1;
      transparentColorFlag = false;
      loopCount = 0;
      imageLeftPosition = 0;
      imageTopPosition = 0;
      imageWidth = 0;
      imageHeight = 0;
      localColorTableFlag = false;
      interlaceFlag = false;
      sortFlag = false;
      localColorTableSize = 0;
      localColorTable.clear();
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
}
