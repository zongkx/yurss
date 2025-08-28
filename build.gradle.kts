plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.2"
    id("com.jetbrains.changelog") version "2.0.1"
    id("maven-publish") // 添加 maven-publish 插件
}

group = "com.zongkx"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
    // OkHttp 是一个现代的、高效的 HTTP 客户端
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Rome是一个用于解析RSS和Atom Feed的库
    implementation("com.rometools:rome:1.18.0")
    // Rome依赖JDom，它是一个XML解析库
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
            version = "1.0.0" // 你的插件版本

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
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${project.name}") // 发布到 GitHub Packages
            credentials {
                username = project.properties["GITHUB_ACTOR"] as? String ?: System.getenv("GITHUB_ACTOR")
                password = project.properties["GITHUB_TOKEN"] as? String ?: System.getenv("GITHUB_TOKEN")
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
