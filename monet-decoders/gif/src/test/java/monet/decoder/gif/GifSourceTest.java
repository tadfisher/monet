package monet.decoder.gif;

import android.annotation.SuppressLint;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
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
  }

  @Test
  public void readsFrames() throws IOException {
    GifSource.Frame frame;
    int i;

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

        assertThat("pixelData[" + i + "]", frame.pixelData, rgb(rgbSource));
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

  private String s(String key) {
    return p.getProperty(key);
  }

  private Matcher<ByteString> rgb(BufferedSource source) throws IOException {
    return new RgbMatcher(source);
  }

  static class RgbMatcher extends TypeSafeMatcher<ByteString> {
    private final BufferedSource rgb;
    private final byte[] expected = new byte[3];
    private int pos;

    RgbMatcher(BufferedSource rgb) {
      this.rgb = rgb;
    }

    @Override protected boolean matchesSafely(ByteString item) {
      try {
        for (pos = 0; pos < item.size(); pos += 4) {
          rgb.readFully(expected);
          if (!item.rangeEquals(pos + 1, expected, 0, 3)) {
            return false;
          }
        }
      } catch (IOException e) {
        return false;
      }
      return true;
    }

    @SuppressLint("DefaultLocale") @Override
    protected void describeMismatchSafely(ByteString item, Description mismatchDescription) {
      mismatchDescription.appendText(String.format("was %02x%02x%02x at position %x",
          item.getByte(pos + 1), item.getByte(pos + 2), item.getByte(pos + 3), pos));
    }

    @Override public void describeTo(Description description) {
      description.appendText(String.format("%02x%02x%02x", expected[0], expected[1], expected[2]));
    }
  }
}
