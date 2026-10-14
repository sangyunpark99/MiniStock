plugins {
    application
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
}


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "sangyunpark99.ministock.App"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    resultFormat.set("JSON")
}