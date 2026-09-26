import java.time.Duration
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("net.fabricmc.fabric-loom")
    id("com.diffplug.spotless") version "8.10.3"
    id("net.ltgt.errorprone") version "5.1.1"
    jacoco
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

loom.runs.named("clientGameTest") {
    programArguments.addAll("--graphicsBackend", "vulkan")
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    implementation("maven.modrinth:player-animation-library:1.2.7")
    compileOnly("org.jspecify:jspecify:1.0.1")
    compileOnly("com.google.errorprone:error_prone_annotations:2.50.0")
    compileOnly("com.google.j2objc:j2objc-annotations:3.1")
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.14.2")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(sourceSets["client"].output)
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("runGameTest") {
    mustRunAfter(tasks.test)
}

tasks.processResources {
    val metadata =
        listOf("version", "loader_version", "fabric_api_version")
            .associateWith { providers.gradleProperty(it).get() }
    inputs.properties(metadata)

    filesMatching("fabric.mod.json") {
        expand(metadata)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    // JOML ships Java 6 class files with Java 8 type annotations.
    options.compilerArgs.addAll(listOf("-Xlint:all,-classfile", "-Werror"))
    options.errorprone {
        error("NullAway", "RequireExplicitNullMarking", "JSpecifyUnrecognizedAnnotationLocation")
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

spotless {
    java {
        palantirJavaFormat("2.99.0")
    }
    format("text") {
        target(
            "src/**/*.json",
            "*.md",
            "*.gradle.kts",
            "docs/*.md",
            "art/**/*.md",
            "art/**/*.py",
            ".editorconfig",
            "gradle.properties",
            ".github/**/*.yml",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf(
                "io.github.romeoahmed.cursedoath.combat.CursedEnergy",
                "io.github.romeoahmed.cursedoath.network.RequestGate",
            )
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

// Fail before Minecraft opens its blocking backend-error dialog on headless runners.
val checkClientGraphics =
    tasks.register<JavaExec>("checkClientGraphics") {
        classpath = sourceSets["gametest"].runtimeClasspath
        mainClass = "io.github.romeoahmed.cursedoath.client.VulkanProbe"
        timeout = Duration.ofSeconds(30)
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs("-XstartOnFirstThread")
    }

tasks.named("runClientGameTest") {
    dependsOn(checkClientGraphics)
}
