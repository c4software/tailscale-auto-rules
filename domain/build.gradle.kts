import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    // Expose les Fakes du domaine aux tests des autres modules.
    `java-test-fixtures`
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
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

kover {
    reports {
        filters {
            excludes {
                // Les Fakes et fabriques de test ne sont pas du code de
                // production : les compter fausserait la mesure dans les deux
                // sens.
                classes("*.Fake*", "*.Contexts")
            }
        }
        verify {
            rule {
                // Le moteur et les règles sont le cœur du projet : c'est le
                // seul endroit où une couverture quasi totale est exigée, et
                // la pureté de `evaluate` la rend atteignable.
                minBound(95)
            }
        }
    }
}

dependencies {
    // Flow et suspend font partie du vocabulaire du domaine ; ce n'est pas
    // une dépendance Android.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":domain")))
}
