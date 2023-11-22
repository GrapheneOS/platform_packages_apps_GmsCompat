import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    namespace = "app.grapheneos.gmscompat.config"

    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        minSdk = 32
        targetSdk = 34
        versionCode = 84
        versionName = versionCode.toString()
    }

    sourceSets.getByName("main") {
        manifest.srcFile("AndroidManifest.xml")
        res.srcDir("res")
        resources.srcDir("../../gmscompat_config")
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val useKeystoreProperties = keystorePropertiesFile.canRead()

    if (useKeystoreProperties) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))

        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            if (useKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            isMinifyEnabled = true
            applicationIdSuffix = ".dev"
        }
    }
}
