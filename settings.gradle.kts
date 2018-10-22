import Versions

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application", "com.android.library" -> useModule("com.android.tools.build:gradle:${Versions.androidGradlePlugin}")
            }
        }
    }
}

rootProject.name = "monet-parent"

include(":monet")
include(":monet-rxjava")
include(":decoder-bitmap")
include(":decoder-gif")
include(":sample")

project(":decoder-bitmap").projectDir = file("monet-decoders/bitmap")
project(":decoder-gif").projectDir = file("monet-decoders/gif")
