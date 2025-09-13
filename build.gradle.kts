plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("maven-publish") // 添加 maven-publish 插件
}

group = "com.zongkx"
version = "1.0.2"

repositories {
    gradlePluginPortal()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Or the latest version
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.rometools:rome:1.18.0")
    implementation("com.rometools:rome-modules:1.18.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}
// build.gradle.kts
publishing {
    publications {
        create<MavenPublication>("pluginPublication") {
            groupId = "com.zongkx.yurss" // 你的 GroupId
            artifactId = "yurss" // 你的 ArtifactId
            version = "1.0.2" // 你的插件版本

            artifact(tasks.getByName("buildPlugin")) {
                extension = "zip"
            }

            pom {
                name.set("Yurss")
                description.set("A simple RSS reader plugin for IntelliJ IDEA.")
                // ... 更多 POM 信息
            }
        }
    }
}
tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
