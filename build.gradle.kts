import dev.detekt.gradle.extensions.FailOnSeverity

plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("com.diffplug.spotless") version "8.10.2"
    id("dev.detekt") version "2.0.0-alpha.6"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

repositories {
    maven {
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup("maven.modrinth") }
    }
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("cursed-oath") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "cursed-oath-test"
        eula = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
    implementation("maven.modrinth:player-animation-library:1.2.7")
    testImplementation(kotlin("test-junit5"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val metadata =
        listOf("version", "loader_version", "fabric_api_version", "fabric_kotlin_version")
            .associateWith { providers.gradleProperty(it).get() }
    inputs.properties(metadata)

    filesMatching("fabric.mod.json") {
        expand(metadata)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

kotlin {
    jvmToolchain(25)
    compilerOptions.allWarningsAsErrors = true
    target.compilations.named("gametest") {
        associateWith(target.compilations.getByName("main"))
    }
}

spotless {
    kotlin {
        ktlint("1.8.0")
    }
    kotlinGradle {
        ktlint("1.8.0")
    }
    java {
        palantirJavaFormat("2.98.0")
    }
    format("text") {
        target(
            "src/**/*.json",
            "*.md",
            "docs/*.md",
            ".editorconfig",
            "gradle.properties",
            "config/**/*.yml",
            ".github/**/*.yml",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    failOnSeverity = FailOnSeverity.Info
}

kover {
    reports {
        total {
            filters {
                includes {
                    classes(
                        "io.github.romeoahmed.cursedoath.combat.CursedEnergy",
                        "io.github.romeoahmed.cursedoath.network.RequestGate",
                    )
                }
            }
            verify {
                rule("Resource and request safety") { minBound(90) }
            }
        }
    }
}

tasks.check {
    dependsOn("detektMain", "detektClient", "detektTest", "detektGametest", "koverVerify")
}

java {
    withSourcesJar()
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}
