import java.util.Properties // <- Add this import
import java.io.FileInputStream //

plugins {
    alias(libs.plugins.android.application)
}


android {
    namespace = "fr.ozf.cronos"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.ozf.cronos"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        
        // Dynamic version name
        val majorVersion = 0
        val commitCount = "git rev-list --count HEAD".runCommand().trim()
        val commitHash = "git rev-parse --short HEAD".runCommand().trim()
        versionName = "$majorVersion.$commitCount-$commitHash"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

fun String.runCommand(): String {
    val process = ProcessBuilder(split(" ")).start()
    return process.inputStream.bufferedReader().readText()
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.navigation.ui)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}