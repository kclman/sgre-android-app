plugins {
    id("com.android.application")
}

android {
    
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/sgre_debug.jks")
            storePassword = "sgre123456"
            keyAlias = "sgre_debug"
            keyPassword = "sgre123456"
        }
    }

namespace = "com.sgre.webview"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sgre.webview"
        minSdk = 23
        targetSdk = 35
        versionCode = 42
        versionName = "1.0.42"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

}
