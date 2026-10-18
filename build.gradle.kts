import java.security.MessageDigest

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "dev.shulkerporter"
version = "0.1.0"

val minecraftVersion = "26.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

// Zips resourcepack/ into build/resourcepack/shulkerporter-pack.zip and writes its SHA-1 next to it.
val resourcePack = tasks.register<Zip>("resourcePack") {
    group = "build"
    description = "Builds the optional client resource pack."
    from("resourcepack")
    archiveFileName.set("shulkerporter-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("resourcepack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val zipFile = archiveFile
    val shaFile = layout.buildDirectory.file("resourcepack/shulkerporter-pack.zip.sha1")
    outputs.file(shaFile)
    doLast {
        val bytes = zipFile.get().asFile.readBytes()
        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        shaFile.get().asFile.writeText(sha1)
        logger.lifecycle("Resource pack SHA-1: $sha1")
    }
}

// Published packs live in docs/resourcepacks/ and are served by GitHub Pages, one immutable zip per version.
val packBaseUrl = providers.gradleProperty("packBaseUrl").map { it.trim().trimEnd('/') }.orElse("")
val publishedPack = layout.projectDirectory.file("docs/resourcepacks/v$version.zip")

val publishPack = tasks.register("publishPack") {
    group = "publishing"
    description = "Copies the resource pack to docs/resourcepacks/v<version>.zip for GitHub Pages."
    val built = resourcePack.flatMap { it.archiveFile }
    val target = publishedPack
    inputs.file(built)
    doLast {
        val source = built.get().asFile
        val destination = target.asFile
        if (destination.exists() && !destination.readBytes().contentEquals(source.readBytes())) {
            throw GradleException(
                "$destination already exists with different contents. A published pack must never change, " +
                    "since players' clients cache it by hash: bump `version` and run publishPack again."
            )
        }
        destination.parentFile.mkdirs()
        source.copyTo(destination, overwrite = true)
        logger.lifecycle("Published $destination. Commit and push it before distributing the jar.")
    }
}

// Bakes the GitHub Pages URL and SHA-1 of this version's pack into the jar, but only when the published
// zip matches the pack being built. Otherwise the plugin falls back to serving the bundled pack itself.
val packProperties = tasks.register("packProperties") {
    val built = resourcePack.flatMap { it.archiveFile }
    val published = publishedPack
    val baseUrl = packBaseUrl
    val packVersion = project.version.toString()
    val output = layout.buildDirectory.file("generated/pack/shulkerporter-pack.properties")
    inputs.file(built)
    inputs.files(published)
    inputs.property("baseUrl", baseUrl)
    inputs.property("version", packVersion)
    outputs.file(output)
    mustRunAfter(publishPack)
    doLast {
        val zip = published.asFile
        var url = ""
        var sha1 = ""
        when {
            baseUrl.get().isEmpty() ->
                logger.lifecycle("packBaseUrl is not set; the plugin will serve the resource pack itself.")
            !zip.exists() ->
                logger.warn("The resource pack for $packVersion is not published; run `gradle publishPack`. Until then the plugin serves it itself.")
            !zip.readBytes().contentEquals(built.get().asFile.readBytes()) ->
                logger.warn("The resource pack changed since $zip was published. Bump `version` and run `gradle publishPack`. Until then the plugin serves it itself.")
            else -> {
                url = "${baseUrl.get()}/resourcepacks/v$packVersion.zip"
                sha1 = MessageDigest.getInstance("SHA-1").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
            }
        }
        output.get().asFile.writeText("url=$url\nsha1=$sha1\n")
    }
}

tasks.processResources {
    from(resourcePack.flatMap { it.archiveFile }) {
        into("resourcepack")
    }
    from(packProperties)
    val props = mapOf("version" to project.version, "minecraftVersion" to minecraftVersion)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.runServer {
    minecraftVersion(minecraftVersion)
}

tasks.build {
    dependsOn(resourcePack)
}
