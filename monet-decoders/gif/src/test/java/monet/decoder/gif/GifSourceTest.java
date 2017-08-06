package monet.decoder.gif;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import okio.ByteString;
import okio.Okio;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class GifSourceTest {

  @Parameters(name = "{0}")
  public static Object[] params() {
    return new Object[] {
        "gifgrid"
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

  @Test
  public void imageHeader() throws IOException {
    source.readSignature();
    GifSource.Format format = source.readHeader();

    assertEquals(i("width"), format.width);
    assertEquals(i("height"), format.height);
    assertEquals(x("globalColorTable"), format.globalColorTable.readByteString());
    assertEquals(i("globalColorTableSize"), format.globalColorTableSize);
    assertEquals(i("backgroundIndex"), format.backgroundIndex);
  }

  @Test
  public void frameHeader() throws IOException {
    source.readSignature();
    source.readHeader();
    source.blockSource.readHeader();

    GifBlockSource.Frame frame = source.blockSource.frame;

    assertEquals(i("0.imageLeftPosition"), frame.imageLeftPosition);
    assertEquals(i("0.imageTopPosition"), frame.imageTopPosition);
    assertEquals(i("0.imageWidth"), frame.imageWidth);
    assertEquals(i("0.imageHeight"), frame.imageHeight);
    assertEquals(b("0.localColorTableFlag"), frame.localColorTableFlag);
    assertEquals(b("0.interlaceFlag"), frame.interlaceFlag);
    assertEquals(b("0.sortFlag"), frame.sortFlag);
    assertEquals(i("0.localColorTableSize"), frame.localColorTableSize);
  }

  @Test
  public void frameData() throws IOException {
    source.readSignature();
    source.readHeader();
    source.blockSource.readHeader();
    source.blockSource.readData();

    GifBlockSource.Frame frame = source.blockSource.frame;

    assertEquals(x("0.imageData"), ByteString.of(frame.imageData));
  }

  private boolean b(String key) {
    return Boolean.valueOf(p.getProperty(key));
  }

  private int i(String key) {
    return Integer.valueOf(p.getProperty(key));
  }

  private ByteString x(String key) {
    return ByteString.decodeHex(p.getProperty(key));
  }
}
