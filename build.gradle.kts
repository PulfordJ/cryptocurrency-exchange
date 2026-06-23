plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.exchange"
version = "0.1.0"
description = "Mock cryptocurrency exchange (REST + WebSocket + FIX) with a Java integration test suite"

// Toolchain targets JDK 21. Inside `nix develop`, JAVA_HOME points to the Nix-provided
// JDK 21 so Gradle uses that directly. Outside Nix, Gradle auto-provisions via Foojay.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Mock exchange runtime ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // FIX 4.4 acceptor (order execution): QuickFIX/J engine + pre-generated FIX 4.4 messages.
    implementation("org.quickfixj:quickfixj-core:2.3.1")
    implementation("org.quickfixj:quickfixj-messages-fix44:2.3.1")

    // --- Integration test suite ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.rest-assured:rest-assured:5.5.0")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
