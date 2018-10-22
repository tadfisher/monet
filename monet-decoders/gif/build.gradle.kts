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
    implementation(project(":monet"))

    with(Libraries) {
        compileOnly(jsr305)
        implementation(reactivestreams)
        testImplementation(junit)
        testImplementation(hamcrest)
    }
}
