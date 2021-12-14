// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

plugins {
    java
    application
    kotlin("jvm") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                licenses {
                    license {
                        name.set("MIT No Attribution")
                        url.set("https://opensource.org/licenses/MIT-0")
                    }
                }
            }
        }
    }
}

group "com.amazonaws.services.kinesisanalytics"
version "1.0"
description "Flink Amazon TimeStream Kotlin sample"

repositories {
    mavenCentral()
}

val javaVersion = "11"
val flinkVersion = "1.11.1"
val scalaBinaryVersion = "2.12"
val kdaVersion = "1.2.0"
val gsonVersion = "2.8.+"
val timestreamSdkVersion = "1.+"
val sl4jVersion = "1.7.+"
val javaMainClass = "services.kinesisanalytics.StreamingJob"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.apache.flink:flink-java:$flinkVersion")
    implementation("org.apache.flink:flink-streaming-java_$scalaBinaryVersion:$flinkVersion")
    implementation("org.apache.flink:flink-clients_$scalaBinaryVersion:$flinkVersion")
    implementation("org.apache.flink:flink-connector-kinesis_$scalaBinaryVersion:$flinkVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("com.amazonaws:aws-kinesisanalytics-runtime:$kdaVersion")
    implementation("com.amazonaws:aws-java-sdk-timestreamwrite:$timestreamSdkVersion")
    implementation("org.slf4j:slf4j-simple:$sl4jVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

application {
    mainClass.set(javaMainClass)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to javaMainClass
        )
    }
}

tasks.shadowJar {
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    dependencies {
        exclude("org.apache.flink:force-shading")
        exclude("com.google.code.findbugs:jsr305")
        exclude("org.slf4j:*")
        exclude("log4j:*")
    }
}
