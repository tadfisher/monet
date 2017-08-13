package monet.decoder.gif;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class GifSourceTest {

  @Parameters(name = "{0}")
  public static Object[] params() {
    return new Object[] {
        "fire",
        "gifgrid",
        "porsche",
        "solid2",
        "traffic",
        "treescap",
        "treescap-interlaced",
        "welcome2",
        "x-trans"
    };
  }

  @Parameter public String image;

  private GifSource gifSource;
  private final Properties p = new Properties();

  @Before
  public void setup() throws IOException {
    InputStream gif = getClass().getResourceAsStream(image + ".gif");
    gifSource = new GifSource(Okio.buffer(Okio.source(gif)));

    p.clear();
    p.load(getClass().getResourceAsStream(image + ".properties"));
  }

  @After
  public void teardown() throws IOException {
    gifSource.close();
  }

  @Test
  public void readsHeader() throws IOException {
    GifSource.Header header = gifSource.readHeader();

    assertEquals("width", i("width"), header.width);
    assertEquals("height", i("height"), header.height);
    assertEquals("globalColorTableSize", i("globalColorTableSize"), header.globalColorTableSize);
    assertEquals("backgroundIndex", i("backgroundIndex"), header.backgroundIndex);
    assertEquals("loopCount", i("loopCount"), header.loopCount);

    if (header.globalColorTable != null) {
      try (BufferedSource map = Okio.buffer(Okio.source(
          getClass().getResourceAsStream(image + ".map")))) {
        assertThat("globalColorTable",
            Arrays.stream(header.globalColorTable).boxed().toArray(Integer[]::new),
            colorTable(map));
      }
    }
  }

  @Test
  public void readsFrames() throws IOException {
    GifSource.Frame frame;
    int i;

    GifSource.Header header = gifSource.readHeader();

    try (BufferedSource rgbSource =
             Okio.buffer(Okio.source(getClass().getResourceAsStream(image + ".rgb")))) {
      for (i = 0; ((frame = gifSource.readFrame()) != null); i++) {
        assertThat("exceeded frame count", i, lessThan(i("frames")));
        assertEquals("delayTime[" + i + "]", i(i + ".delayTime"), frame.delayTime);
        assertEquals("disposalMethod[" + i + "]", i(i + ".disposalMethod"), frame.disposalMethod);
        assertEquals("transparentColorIndex[" + i + "]", i(i + ".transparentColorIndex"), frame.transparentColorIndex);
        assertEquals("transparentColorFlag[" + i + "]", b(i + ".transparentColorFlag"), frame.transparentColorFlag);
        assertEquals("imageLeftPosition[" + i + "]", i(i + ".imageLeftPosition"), frame.imageLeftPosition);
        assertEquals("imageTopPosition[" + i + "]", i(i + ".imageTopPosition"), frame.imageTopPosition);
        assertEquals("imageWidth[" + i + "]", i(i + ".imageWidth"), frame.imageWidth);
        assertEquals("imageHeight[" + i + "]", i(i + ".imageHeight"), frame.imageHeight);
        assertEquals("localColorTableFlag[" + i + "]", b(i + ".localColorTableFlag"), frame.localColorTableFlag);
        assertEquals("interlaceFlag[" + i + "]", b(i + ".interlaceFlag"), frame.interlaceFlag);
        assertEquals("sortFlag[" + i + "]", b(i + ".sortFlag"), frame.sortFlag);
        assertEquals("localColorTableSize[" + i + "]", i(i + ".localColorTableSize"), frame.localColorTableSize);

        assertEquals("pixelData[" + i + "].size", header.width * header.height * 4,
            frame.pixelData.size());
        assertThat("pixelData[" + i + "]", withoutAlpha(frame.pixelData), rgb(rgbSource,
            header.width * header.height));
      }
    }

    assertThat("missing frames", i, equalTo(i("frames")));
  }

  private boolean b(String key) {
    return Boolean.valueOf(p.getProperty(key));
  }

  private int i(String key) {
    return Integer.valueOf(p.getProperty(key));
  }

  private ByteString withoutAlpha(ByteString pixelData) {
    Buffer buf = new Buffer();
    for (int i = 0; i < pixelData.size(); i += 4) {
      buf.writeByte(pixelData.getByte(i + 1));
      buf.writeByte(pixelData.getByte(i + 2));
      buf.writeByte(pixelData.getByte(i + 3));
    }
    return buf.snapshot();
  }

  private Matcher<ByteString> rgb(BufferedSource source, int count) throws IOException {
    Buffer expected = new Buffer();
    long total = 0;
    while (total < count * 3) {
      total += source.read(expected, count * 3 - total);
    }
    return new RgbMatcher(expected.snapshot());
  }

  private Matcher<Integer[]> colorTable(BufferedSource source) throws IOException {
    String line;
    ArrayList<Matcher<? super Integer>> expected = new ArrayList<>();
    while ((line = source.readUtf8Line()) != null) {
      String[] fields = line.trim().split("\\s+");
      int value = Integer.parseInt(fields[1]) << 16
          | Integer.parseInt(fields[2]) << 8
          | Integer.parseInt(fields[3])
          | 0xff000000;
      expected.add(Matchers.equalTo(value));
    }
    return Matchers.arrayContaining(expected);
  }

  static int readRgb(ByteString in, int pos) {
    return (in.getByte(pos) & 0xff) << 16
        | (in.getByte(pos + 1) & 0xff) << 8
        | (in.getByte(pos + 2));
  }

  static class RgbMatcher extends IsEqual<ByteString> {
    private final ByteString expected;

    RgbMatcher(ByteString expected) {
      super(expected);
      this.expected = expected;
    }

    @Override public void describeMismatch(Object item, Description description) {
      super.describeMismatch(item, description);

      if (!(item instanceof ByteString)) return;
      ByteString actual = (ByteString) item;
      description.appendText("\n   pixel     byte  expect actual");
      int len = Math.min(actual.size(), expected.size());

      for (int i = 0; i < len; i += 3) {
        if (expected.rangeEquals(i, actual, i, 3)) continue;
        description.appendText(String.format("\n%8x %8x: %06x %06x", i / 3, i,
            readRgb(expected, i),
            readRgb(actual, i)));
      }

      if (actual.size() != expected.size()) {
        description.appendText("\nsize mismatch: expected=" + expected.size() + " actual=" +
            actual.size());
      }
    }
  }
}
