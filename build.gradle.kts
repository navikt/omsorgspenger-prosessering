import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val dusseldorfKtorVersion = "1.3.2.4e29fb7"
val k9FormatVersion = "3.0.0.496a500"
val ktorVersion = ext.get("ktorVersion").toString()
val slf4jVersion = ext.get("slf4jVersion").toString()
val kotlinxCoroutinesVersion = ext.get("kotlinxCoroutinesVersion").toString()

val openhtmltopdfVersion = "1.0.2"
val kafkaEmbeddedEnvVersion = "2.4.0"
val kafkaVersion = "2.4.0" // Alligned med version fra kafka-embedded-env
val handlebarsVersion = "4.1.2"

val mainClass = "no.nav.helse.OmsorgspengesoknadProsesseringKt"

plugins {
    kotlin("jvm") version "1.3.70"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

buildscript {
    // Henter ut diverse dependency versjoner, i.e. ktorVersion.
    apply("https://raw.githubusercontent.com/navikt/dusseldorf-ktor/4e29fb7f5f69ab1b8d998fc1f674085e2e01e7ad/gradle/dusseldorf-ktor.gradle.kts")
}

dependencies {
    // Server
    compile ( "no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-auth:$dusseldorfKtorVersion")
    compile ( "no.nav.k9:soknad-omsorgspenger:$k9FormatVersion")
    compile ( "org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$kotlinxCoroutinesVersion")
    
    // Client
    compile ( "no.nav.helse:dusseldorf-ktor-client:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-oauth2-client:$dusseldorfKtorVersion")

    // PDF
    compile ( "com.openhtmltopdf:openhtmltopdf-pdfbox:$openhtmltopdfVersion")
    compile ( "com.openhtmltopdf:openhtmltopdf-slf4j:$openhtmltopdfVersion")
    compile ("org.slf4j:jcl-over-slf4j:$slf4jVersion")
    compile ("com.github.jknack:handlebars:$handlebarsVersion")

    // Kafka
    compile("org.apache.kafka:kafka-streams:$kafkaVersion")

    // Test
    testCompile("org.apache.kafka:kafka-clients:$kafkaVersion")
    testCompile ("no.nav:kafka-embedded-env:$kafkaEmbeddedEnvVersion")
    testCompile ( "no.nav.helse:dusseldorf-test-support:$dusseldorfKtorVersion")
    testCompile("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testCompile("org.skyscreamer:jsonassert:1.5.0")
    implementation(kotlin("stdlib-jdk8"))
}

repositories {
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://kotlin.bintray.com/kotlinx")
    maven("http://packages.confluent.io/maven/")

    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/k9-format")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    jcenter()
    mavenLocal()
    mavenCentral()
}


java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("app")
    archiveClassifier.set("")
    manifest {
        attributes(
            mapOf(
                "Main-Class" to mainClass
            )
        )
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.2.2"
}
