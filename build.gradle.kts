import com.android.build.gradle.BaseExtension
import java.net.URL

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("maven-publish")
    id("java")
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.0.0")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

val dlPackageList by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        /* Merge framework packages with AndroidX packages into the same list
        * so links to Android classes can work properly in Javadoc */
        rootProject.buildDir.mkdirs()
        File(rootProject.buildDir, "package-list").outputStream().use { out ->
            URL("https://developer.android.com/reference/package-list")
                .openStream().use { src -> src.copyTo(out) }
            URL("https://developer.android.com/reference/androidx/package-list")
                .openStream().use { src -> src.copyTo(out) }
        }
    }
}

val javadoc = tasks.replace("javadoc", Javadoc::class).apply {
    dependsOn(dlPackageList)
    isFailOnError = false
    title = "libsu API"
    exclude("**/internal/**")
    (options as StandardJavadocDocletOptions).apply {
        links = listOf("https://docs.oracle.com/javase/8/docs/api/")
        linksOffline = listOf(JavadocOfflineLink(
            "https://developer.android.com/reference/", rootProject.buildDir.path))
        isNoDeprecated = true
    }
    setDestinationDir(File(rootProject.buildDir, "javadoc"))
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(javadoc)
    archiveClassifier.set("javadoc")
    from(javadoc.destinationDir)
}

/* Force JitPack to build javadocJar and publish */
tasks.register("install") {
    dependsOn(tasks["publishToMavenLocal"])
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(javadocJar.get())
            groupId = "com.github.topjohnwu"
            artifactId = "docs"
        }
    }
}

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
    }

    repositories {
        google()
        mavenCentral()
    }

    configurations.create("javadocDeps")

    afterEvaluate {
        android {
            compileSdkVersion(30)
            buildToolsVersion = "30.0.3"

            defaultConfig {
                if (minSdkVersion == null)
                    minSdk = 14
                targetSdk = 30
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        if (plugins.hasPlugin("com.android.library")) {
            android {
                buildFeatures.apply {
                    buildConfig = false
                }

                val sources = sourceSets.getByName("main").java.getSourceFiles()

                (rootProject.tasks["javadoc"] as Javadoc).apply {
                    source += sources
                    classpath += project.files(bootClasspath)
                    classpath += configurations.getByName("javadocDeps")
                }

                val sourcesJar = tasks.register("sourcesJar", Jar::class) {
                    archiveClassifier.set("sources")
                    from(sources)
                }

                artifacts {
                    add("archives", sourcesJar)
                }
            }
        }
    }
}
