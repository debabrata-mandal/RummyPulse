// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}

tasks.register("deployDebug") {
    group = "deployment"
    description =
        "Install debug APK on the connected device or running emulator, then launch LoginActivity."
    dependsOn(":app:installDebug")
    doLast {
        exec {
            commandLine(
                "adb",
                "shell",
                "am",
                "start",
                "-n",
                "com.example.rummypulse/.LoginActivity",
            )
        }
    }
}