import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

/**
 * Vrai lorsque la construction demandée capture ou compare des images.
 *
 * Sert à n'appliquer les réglages coûteux de JVM qu'à ces exécutions-là. Le
 * `test` ordinaire — celui de la CI — garde la configuration par défaut : sans
 * mode Roborazzi actif, `captureRoboImage` ne fait rien, les tests de rendu se
 * réduisent alors à quelques millisecondes.
 */
val isScreenshotRun =
    gradle.startParameter.taskNames.any {
        it.contains("roborazzi", ignoreCase = true)
    }

android {
    namespace = "fr.vbrosseau.tailscaleautorules"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "fr.vbrosseau.tailscaleautorules"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            // Requis par Robolectric pour résoudre les ressources et le manifeste.
            isIncludeAndroidResources = true

            all { test ->
                // Roborazzi lit ces propriétés pour savoir s'il doit enregistrer
                // les références ou les comparer. Les passer par Gradle évite de
                // recompiler les tests pour changer de mode.
                test.systemProperties(
                    providers.gradlePropertiesPrefixedBy("roborazzi.").get(),
                )

                if (isScreenshotRun) {
                    // Une exécution Roborazzi ne s'intéresse qu'aux captures :
                    // rejouer les 150 autres tests coûterait des minutes pour
                    // rien, et le rendu graphique natif ne rendant pas toute sa
                    // mémoire d'une classe à l'autre, la suite entière finissait
                    // par saturer le tas. Le symptôme était trompeur — un test
                    // sans rapport échouait, l'OutOfMemoryError lui parvenant
                    // sous forme d'« exception avant le test ».
                    test.filter.includeTestsMatching("*ScreenshotTest")
                    test.maxHeapSize = "2g"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Requis pour distinguer les constructions de débogage : la
        // journalisation n'est plantée que dans celles-là.
        buildConfig = true
    }

    lint {
        // Android Lint (AGP 9.3.1) plante sur ses propres composants d'analyse
        // Kotlin lorsqu'il visite nos sources de test :
        // SymbolLightClassForClassOrObject.isRecord lève une exception. Le code
        // concerné compile et s'exécute sans problème.
        //
        // Les sources de production restent analysées ; c'est là que se
        // trouvent les règles qui comptent (API dépréciées, permissions,
        // compatibilité de version). À réactiver dès qu'une version d'AGP
        // corrige ce plantage — voir TASKS.md.
        ignoreTestSources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    // Les schémas sont versionnés : c'est ce qui permet à Room de vérifier les
    // migrations automatiquement, et à une revue de voir une évolution de base.
    schemaDirectory("$projectDir/schemas")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

roborazzi {
    // Les références sont versionnées : une revue doit pouvoir constater un
    // changement visuel dans le diff, pas seulement lire qu'un test a échoué.
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.hilt.testing)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    kspTest(libs.hilt.compiler)
}
