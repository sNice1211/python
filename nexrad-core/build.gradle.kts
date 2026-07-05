plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // BZIP2 decompression for NEXRAD Archive II "LDM compressed record" framing.
    // Level II files interleave 4-byte size-prefixed bzip2 blocks; the JDK has no bzip2
    // support, so we lean on a well-tested implementation rather than hand-rolling one.
    implementation("org.apache.commons:commons-compress:1.26.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
