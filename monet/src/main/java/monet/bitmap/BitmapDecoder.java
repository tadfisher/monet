package monet.bitmap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;

import monet.DecodeResult;
import monet.Decoder;
import monet.Request;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

public class BitmapDecoder extends Decoder<Bitmap> {

  public static final Decoder.Factory FACTORY = (type, monet) -> {
    if (type == Bitmap.class) {
      return new BitmapDecoder();
    }
    return null;
  };

  private static final ByteString[] SIGNATURES = new ByteString[] {
      // BMP (0)
      ByteString.encodeUtf8("BM"),
      // GIF (1-2)
      ByteString.encodeUtf8("GIF87a"), ByteString.encodeUtf8("GIF89a"),
      // ICO/CUR (3-4)
      ByteString.decodeHex("00000100"), ByteString.decodeHex("00000200"),
      // JPEG (5)
      ByteString.decodeHex("ffd8ff"),
      // PNG (6)
      ByteString.decodeHex("89504e470d0a1a0a"),
      // WebP (7)
      ByteString.encodeUtf8("RIFF")
  };

  private static final int MAX_SIGNATURE_LENGTH = 16;

  private static final ByteString WEBP_SUFFIX = ByteString.encodeUtf8("WEBP");

  private static boolean checkSignature(BufferedSource source) throws IOException {
    if (!source.request(MAX_SIGNATURE_LENGTH)) {
      return false;
    }
    final Buffer buf = source.buffer();
    final ByteString bytes = buf.snapshot(MAX_SIGNATURE_LENGTH);
    for (int i = 0, size = SIGNATURES.length; i < size; i++) {
      if (bytes.startsWith(SIGNATURES[i])) {
        // WebP's signature is actually 'RIFFxxxxWEBP'
        if (i == 7 && !buf.rangeEquals(8, WEBP_SUFFIX)) {
          return false;
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public DecodeResult<Bitmap> decode(Request request) {
    final BufferedSource source = request.source();

    try {
      if (!checkSignature(source)) {
        return DecodeResult.unsupported();
      }
    } catch (IOException e) {
      return DecodeResult.error(e);
    }

    InputStream stream = source.inputStream();
    final BitmapFactory.Options options = createBitmapOptions(request);
    final boolean calculateSize = options != null && options.inJustDecodeBounds;

    if (calculateSize) {
      MarkableInputStream markStream = new MarkableInputStream(stream);
      stream = markStream;
      markStream.allowMarksToExpire(false);
      long mark = markStream.savePosition(1024);
      BitmapFactory.decodeStream(stream, null, options);
      calculateInSampleSize(request.targetWidth(), request.targetHeight(), options.outWidth,
          options.outHeight, options, request);
      try {
        markStream.reset(mark);
      } catch (IOException e) {
        return DecodeResult.error(e);
      }
      markStream.allowMarksToExpire(true);
    }
    Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
    if (bitmap == null) {
      return DecodeResult.error(new IOException("Failed to decode bitmap."));
    }
    return DecodeResult.success(bitmap);
  }

  private static BitmapFactory.Options createBitmapOptions(Request request) {
    final boolean justBounds = request.hasTargetSize();
    final Bitmap.Config config = request.config();
    final boolean hasConfig = config != null;
    BitmapFactory.Options options = null;
    if (justBounds || hasConfig) {
      options = new BitmapFactory.Options();
      options.inJustDecodeBounds = justBounds;
      if (hasConfig) {
        options.inPreferredConfig = config;
      }
    }
    return options;
  }

  private static void calculateInSampleSize(int reqWidth, int reqHeight, int width, int height,
      BitmapFactory.Options options, Request request) {
    int sampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
      final int heightRatio;
      final int widthRatio;
      if (reqHeight == 0) {
        sampleSize = (int) Math.floor((float) width / (float) reqWidth);
      } else if (reqWidth == 0) {
        sampleSize = (int) Math.floor((float) height / (float) reqHeight);
      } else {
        heightRatio = (int) Math.floor((float) height / (float) reqHeight);
        widthRatio = (int) Math.floor((float) width / (float) reqWidth);
        final View fitView = request.fitView();
        if (fitView != null
            && fitView instanceof ImageView
            && ((ImageView) fitView).getScaleType() == ImageView.ScaleType.CENTER_INSIDE) {
          sampleSize = Math.max(heightRatio, widthRatio);
        } else {
          sampleSize = Math.min(heightRatio, widthRatio);
        }
      }
    }
    options.inSampleSize = sampleSize;
    options.inJustDecodeBounds = false;
  }
}
