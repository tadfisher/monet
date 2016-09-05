# Monet

A collection of RxJava helpers for building image pipelines.

## Introduction

Loading images on Android, especially from network sources, is a complex task.
Thankfully, there exist many capable libraries which make image loading simple.
However, these libraries do a lot of heavy lifting behind the scenes to enable
an end-to-end image processing pipeline with a simple API, with features like
cache hierarchies, adapter code to work with multiple networking libraries, and
bespoke image transformation APIs.

Monet eschews this complexity by relying on RxJava to model the pipeline. For
example, it does not integrate with a networking library to fetch images, but it
will happily decode any `InputStream` you pipe to it. Similarly, Monet does not
cache images transparently, as standard RxJava operators such as
`Observable.cache()` can serve that purpose.

The end result is an API that is more complicated, but is finely broken down
into composable parts. You won't be loading images with a single line of code,
but you can easily adapt the image pipeline to serve your unique needs.

## Example

A common invocation of Monet looks something like this:

```java
service.fetch(imageUrl)
  .map(ResponseBody::byteStream)
  .compose(Monet.fromInputStream())
  .compose(Monet.fit(imageView))
  .compose(Monet.decode())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(view::setImageBitmap);
```

In this example, `service` could be a Retrofit service which returns an
`Observable<ResponseBody>` for the request. Monet provides a collection of
`Observable.Transformer`s which are composed with the stream to transform the
image data as it moves through the pipeline.

## API

### `fromInputStream()`

Wraps an `InputStream` with a `Request.Builder`, which may be subsequently
modified with additional image decoding options.

### `fit(View)`, `fitX(View)`, `fitY(View)`

Sets the target width and/or height on a `Request.Builder` to fit the dimensions
of a `View`. If necessary, this will delay the stream until the view is
measured.

### `decode()`

Builds the request and decodes the data provided by its `InputStream` into a
`Bitmap`. If target dimensions are set on the request, decodes a sampled bitmap
to reduce memory consumption.

## Obtaining

Add the following to your `build.gradle`:

```groovy
dependencies {
    compile 'com.simple.monet:monet:0.1.0-SNAPSHOT'
}
```

## License

```
Copyright 2016 Simple Finance Technology Corp.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
