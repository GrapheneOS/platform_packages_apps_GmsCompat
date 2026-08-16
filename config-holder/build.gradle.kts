plugins {
    id("com.android.application") version "9.2.1" apply false
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")
    }
}
