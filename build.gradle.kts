buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:5.0.1")
    }
}

// Top-level build file
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
