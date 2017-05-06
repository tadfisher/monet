package monet;

import com.google.auto.value.AutoValue;

@AutoValue abstract class ViewDimensions {

  static ViewDimensions create(int width, int height) {
    return new AutoValue_ViewDimensions(width, height);
  }

  abstract int width();

  abstract int height();
}
