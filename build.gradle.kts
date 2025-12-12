plugins {
	java
	id("org.springframework.boot") version "3.3.2"
	id("io.spring.dependency-management") version "1.1.6"
}

group = "com.gdpark"
version = "0.0.1-SNAPSHOT"
description = "ffmpeg-ffprobe"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {

    // smile
    implementation("com.github.haifengl:smile-core:3.1.1")
    
    // Vosk STT
    implementation("com.alphacephei:vosk:0.3.38")
    implementation("net.java.dev.jna:jna:5.13.0")

    // ffmpeg
    implementation("net.bramp.ffmpeg:ffmpeg:0.6.2")

    // Swagger (SpringDoc)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
