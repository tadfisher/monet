import org.gradle.api.JavaVersion

object Versions {
    const val gradle = "4.10.2"
    const val androidGradlePlugin = "3.2.1"
    const val hugoPlugin = "1.2.1"

    const val compileSdk = 28
    const val buildTools = "28.0.3"
    const val minSdk = 21
    const val targetSdk = 28
    val sourceCompatibility: JavaVersion = JavaVersion.VERSION_1_8
    val targetCompatibility: JavaVersion = JavaVersion.VERSION_1_8
}

object Libraries {
    object AutoValue {
        private const val ver = "1.6.3rc1"
        const val compiler = "com.google.auto.value:auto-value:$ver"
        const val annotations = "com.google.auto.value:auto-value-annotations:$ver"
        const val with = "com.gabrielittner.auto.value:auto-value-with:1.0.0"
    }

    object Support {
        private const val ver = "28.0.0"
        const val annotations = "com.android.support:support-annotations:$ver"
        const val appcompat = "com.android.support:appcompat-v7:$ver"
        const val recyclerview = "com.android.support:recyclerview-v7:$ver"
    }

    const val hamcrest = "org.hamcrest:hamcrest-junit:2.0.0.0"
    const val jsr305 = "com.google.code.findbugs:jsr305:3.0.2"
    const val junit = "junit:junit:4.12"
    const val okio = "com.squareup.okio:okio:2.1.0"
    const val reactivestreams = "org.reactivestreams:reactive-streams:1.0.2"
    const val retrofitRxjava = "com.squareup.retrofit2:adapter-rxjava2:2.4.0"
    const val robolectric = "org.robolectric:robolectric:4.0-beta-1"
    const val rxjava = "io.reactivex.rxjava2:rxjava:2.2.2"
    const val rxandroid = "io.reactivex.rxjava2:rxandroid:2.1.0"
}
