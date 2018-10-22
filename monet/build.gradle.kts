plugins {
    id("com.android.library")
}

android {
    compileSdkVersion(Versions.compileSdk)
    buildToolsVersion(Versions.buildTools)

    compileOptions {
        setSourceCompatibility(Versions.sourceCompatibility)
        setTargetCompatibility(Versions.targetCompatibility)
    }

    defaultConfig {
        minSdkVersion(Versions.minSdk)
    }
}

dependencies {
    with(Libraries) {
        implementation(reactivestreams)
        api(okio)

        testImplementation(junit)
        testImplementation(robolectric)
    }

    with(Libraries.AutoValue) {
        annotationProcessor(compiler)
        annotationProcessor(with)
        compileOnly(annotations)
    }

    with(Libraries.Support) {
        api(annotations)
    }
}
