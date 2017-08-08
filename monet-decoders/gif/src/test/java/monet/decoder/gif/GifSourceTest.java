package monet.decoder.gif;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import okio.Okio;
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
        "gifgrid",
        "interlaced",
        "animated-simple"
    };
  }

  @Parameter public String image;

  private GifSource source;
  private final Properties p = new Properties();

  @Before
  public void setup() throws IOException {
    InputStream gif = getClass().getResourceAsStream(image + ".gif");
    source = new GifSource(Okio.buffer(Okio.source(gif)));

    p.clear();
    p.load(getClass().getResourceAsStream(image + ".properties"));
  }

  @After
  public void teardown() throws IOException {
    source.close();
  }

  @Test
  public void readsHeader() throws IOException {
    GifSource.Header header = source.readHeader();

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

    for (i = 0; ((frame = source.readFrame()) != null); i++) {
      assertThat("exceeded frame count", i, lessThan(i("frames")));
      assertEquals("imageLeftPosition[" + i + "]",
          i(i + ".imageLeftPosition"), frame.imageLeftPosition);
      assertEquals("imageTopPosition[" + i + "]",
          i(i + ".imageTopPosition"), frame.imageTopPosition);
      assertEquals("imageWidth[" + i + "]",
          i(i + ".imageWidth"), frame.imageWidth);
      assertEquals("imageHeight[" + i + "]",
          i(i + ".imageHeight"), frame.imageHeight);
      assertEquals("localColorTableFlag[" + i + "]",
          b(i + ".localColorTableFlag"), frame.localColorTableFlag);
      assertEquals("interlaceFlag[" + i + "]",
          b(i + ".interlaceFlag"), frame.interlaceFlag);
      assertEquals("sortFlag[" + i + "]",
          b(i + ".sortFlag"), frame.sortFlag);
      assertEquals("localColorTableSize[" + i + "]",
          i(i + ".localColorTableSize"), frame.localColorTableSize);
      assertEquals("pixelData[" + i + "]",
          s(i + ".pixelData"), frame.pixelData.hex());
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
}
