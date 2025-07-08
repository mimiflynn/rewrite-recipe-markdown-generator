plugins {
    application
    id("org.jetbrains.kotlin.jvm").version("1.9.25")
    id("org.owasp.dependencycheck") version "latest.release"
}

dependencyCheck {
    analyzers.assemblyEnabled = false
    failBuildOnCVSS = 9.0F
    suppressionFile = "suppressions.xml"
    scanConfigurations = listOf("runtimeClasspath")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://maven.diffblue.com/snapshot") }
    gradlePluginPortal()
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

val recipeConf = configurations.create("recipe")

// Either `latest.release` or `latest.integration`
val rewriteVersion = "latest.release"

// Used to determine what type of changelog to build up.
//   * "release"  : When making a changelog for larger releases of OpenRewrite
//   * "snapshot" : When making a changelog for snapshot releases on a weekly cadence.
//   * "diff" : When making a diff-log for what recipes are made over time.
val deployType = "release"

// When you set the above to diff, this will be the name of the markdown file generated
val diffFileName = "desjardins"

// read recipe dependencies file
val recipeDeps = file("recipes.txt").readLines()
    .filter { it.isNotBlank() && !it.startsWith("#") }

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(platform("io.moderne.recipe:moderne-recipe-bom:$rewriteVersion"))

    implementation("info.picocli:picocli:latest.release")
    implementation("org.openrewrite:rewrite-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    implementation("io.github.java-diff-utils:java-diff-utils:4.11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    testImplementation("org.junit.jupiter:junit-jupiter:5.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "recipe"(platform("io.moderne.recipe:moderne-recipe-bom:$rewriteVersion"))

    recipeDeps.forEach { dep ->
        "recipe"(dep)
    }

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("org.openrewrite.RecipeMarkdownGenerator")
}

tasks.named<JavaExec>("run").configure {
    val targetDir = layout.buildDirectory.dir("docs").get().asFile

    val latestVersionsOnly = providers.gradleProperty("latestVersionsOnly").getOrElse("").equals("true")
    if (latestVersionsOnly) {
        // Additional modules whose versions we want to show, but not (yet) their recipes
        dependencies {
            "recipe"("org.openrewrite:rewrite-cobol:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-csharp:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-javascript:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-polyglot:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-python:$rewriteVersion")
            "recipe"("org.openrewrite:rewrite-templating:$rewriteVersion")
        }
    }

    // Collect all of the dependencies from recipeConf, then stuff them into a string representation
    val recipeModules = recipeConf.resolvedConfiguration.firstLevelModuleDependencies.flatMap { dep ->
        dep.moduleArtifacts.map { artifact ->
            "${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}:${artifact.file}"
        }
    }.joinToString(";")
    // recipeModules doesn't include transitive dependencies, but those are needed to load recipes and their descriptors
    val recipeClasspath = recipeConf.resolvedConfiguration.files.asSequence()
        .map { it.absolutePath }
        .joinToString(";")

    description = "Writes generated markdown docs to $targetDir"
    val arguments = mutableListOf(
        targetDir.toString(),
        recipeModules,
        recipeClasspath,
        latestVersion("org.openrewrite:rewrite-bom:latest.release"),
        latestVersion("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"),
        latestVersion("io.moderne.recipe:moderne-recipe-bom:latest.release"),
        latestVersion("org.openrewrite:plugin:latest.release"),
        latestVersion("org.openrewrite.maven:rewrite-maven-plugin:latest.release"),
        deployType,
        diffFileName
    )
    if (latestVersionsOnly) {
        arguments.add("--latest-versions-only")
    }
    args = arguments
    doFirst {
        logger.lifecycle("Recipe modules: ")
        logger.lifecycle(recipeModules.replace(";", "\n"))

        // Ensure no stale output from previous runs is in the output directory
        targetDir.deleteRecursively()
        targetDir.mkdirs()
    }
    doLast {
        this as JavaExec
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // IntelliJ says this is unnecessary, kotlin compiler disagrees
        logger.lifecycle("Wrote generated docs to: file://${args!!.first()}")
    }
}

defaultTasks = mutableListOf("run")

fun latestVersion(arg: String) =
    configurations.detachedConfiguration(dependencies.create(arg))
        .resolvedConfiguration
        .firstLevelModuleDependencies
        .first()
        .moduleVersion
