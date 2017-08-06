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

  private short[] prefix;
  private byte[] suffix;
  private byte[] pixelStack;

  private final BufferedSource source;

  private int section = SECTION_HEADER;
  private int frameSection = FRAME_HEADER;

  private Header header;
  private Frame frame;
  private long pos = 0;

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
      if (frame == null || pos >= frame.imageData.length) {
        frameSection = FRAME_HEADER;
        frame = readFrame();
        pos = 0;
      }
      if (frame == null) {
        section = SECTION_DONE;
        return -1;
      }
      byteCount = Math.min(frame.imageData.length - pos, byteCount);
      sink.write(frame.imageData, (int) pos, (int) byteCount);
      pos = byteCount;
      return byteCount;
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
      throw new IOException("GIF signature not found");
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
    final Buffer globalColorTable = new Buffer();
    if ((packed & 0x80) != 0 && globalColorTableSize > 0) {
      globalColorTable.write(source, globalColorTableSize * 3);
    }

    final Header header =
        new Header(width, height, globalColorTable, globalColorTableSize, backgroundIndex);

    section = SECTION_BODY;

    return header;
  }

  @Nullable public Frame readFrame() throws IOException {
    if (section == SECTION_HEADER) header = readHeader();
    if (section == SECTION_DONE) return null;
    return readFrameInternal(new Frame());
  }

  @Nullable private Frame readFrameInternal(final Frame frame) throws IOException {
    while (frameSection == FRAME_HEADER) {
      int code = readByte();
      switch (code) {
        case 0x21:
          readFrameExtension(frame);
          break;
        case 0x2c:
          readFrameHeader(frame);
          break;
        case 0x3b:
          section = SECTION_DONE;
          return null;  // Trailer
      }
    }
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
        frame.transparentColorIndex = readByte();
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
        int blockSize = readByte();
        ByteString application = source.readByteString(11);
        if (APPLICATION_NETSCAPE.equals(application)) {
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
          source.skip(2); // fixed sub-block size and ID
          frame.loopCount = readShort();
          source.skip(1); // block terminator
        } else {
          // We don't handle other extension types.
          source.skip(blockSize - 10);
        }
        break;
    }
  }

  private void readFrameHeader(Frame frame) throws IOException {
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
    source.require(10);

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
      source.read(frame.localColorTable, frame.localColorTableSize * 3);
      frame.activeColorTable = frame.localColorTable;
    } else {
      frame.activeColorTable = header.globalColorTable;
    }

    frameSection = FRAME_DATA;
  }

  private void readFrameImageData(Frame frame) throws IOException {
    // TODO dispose previous frame (populate new frame data?)
    readFrameIndexData(frame);
    readFramePixelData(frame);
    frameSection = FRAME_HEADER;
  }

  private void readFrameIndexData(final Frame frame) throws IOException {
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
    final int pixels = frame.imageWidth * frame.imageHeight;
    final int dataSize = readByte();
    final int clearCode = 1 << dataSize;
    final int endCode = clearCode + 1;
    int available = clearCode + 2;
    int oldCode = -1;
    int codeSize = dataSize + 1;
    int codeMask = (1 << codeSize) - 1;

    if (prefix == null) prefix = new short[MAX_STACK_SIZE];
    if (suffix == null) suffix = new byte[MAX_STACK_SIZE];
    if (pixelStack == null) pixelStack = new byte[MAX_STACK_SIZE + 1];

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
          source.skip(remaining + 1); // block terminator
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

    source.skip(1); // block terminator
  }

  private void readFramePixelData(final Frame frame) throws IOException {
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
    return source.readShortLe() & 0xffff;
  }

  static class Header {
    final int width;
    final int height;

    final Buffer globalColorTable;
    final int globalColorTableSize;
    final int backgroundIndex;

    Header(int width, int height, Buffer globalColorTable, int globalColorTableSize,
        int backgroundIndex) {
      this.width = width;
      this.height = height;
      this.globalColorTable = globalColorTable;
      this.globalColorTableSize = globalColorTableSize;
      this.backgroundIndex = backgroundIndex;
    }
  }

  static class Frame {
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
