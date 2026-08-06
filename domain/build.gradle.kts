import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    // Expose les Fakes du domaine aux tests des autres modules.
    `java-test-fixtures`
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Module Kotlin/JVM pur : le SDK Android n'est volontairement pas sur le
// classpath, afin qu'une dépendance Android devienne une erreur de compilation
// et non une remarque de revue. Voir ARCHITECTURE.md §1.1.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":domain")))
}
