import org.gradle.process.ExecOperations
import javax.inject.Inject

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}

abstract class DeployDebugTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @TaskAction
    fun run() {
        execOps.exec {
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

tasks.register<DeployDebugTask>("deployDebug") {
    group = "deployment"
    description = "Install debug APK on the connected device or running emulator, then launch LoginActivity."
    dependsOn(":app:installDebug")
}