plugins {
    id("com.android.application")
}

android {
    compileSdkVersion(Versions.compileSdk)
    buildToolsVersion(Versions.buildTools)

    compileOptions {
        setSourceCompatibility(Versions.sourceCompatibility)
        setTargetCompatibility(Versions.targetCompatibility)
    }

    defaultConfig {
        applicationId = "com.example.monet"
        versionCode = 1
        versionName = "1.0"
        minSdkVersion(Versions.minSdk)
        targetSdkVersion(Versions.targetSdk)
    }
}

dependencies {
    implementation(project(":monet"))
    implementation(project(":monet-rxjava"))
    implementation(project(":decoder-bitmap"))
    implementation(project(":decoder-gif"))

    with(Libraries) {
        implementation(rxandroid)
        implementation(rxjava)
        implementation(retrofitRxjava)
    }

    with(Libraries.Support) {
        implementation(appcompat)
        implementation(recyclerview)
    }
}
