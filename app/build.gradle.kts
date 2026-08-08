import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

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

/**
 * Version utilisée quand rien ne permet de la déduire — dépôt sans étiquette,
 * archive téléchargée, `git` absent de la machine.
 *
 * Elle est **volontairement invalide comme numéro de publication** : une
 * construction qui ne descend d'aucune étiquette n'est pas une version, et lui
 * en donner une plausible ferait passer un artefact local pour une livraison.
 */
val fallbackVersionName = "0.0.0-inconnue"

/**
 * Le nom que `git describe` donne au commit construit.
 *
 * Trois formes, et chacune dit quelque chose de différent :
 * - `v1.0.0` — le commit **est** l'étiquette : c'est une version ;
 * - `v1.0.0-3-gabc1234` — trois commits après elle : ce n'en est pas une ;
 * - `v1.0.0-3-gabc1234-dirty` — et l'arbre de travail est modifié.
 *
 * `RELEASE_VERSION` a la priorité, pour deux raisons pratiques : la CI connaît
 * l'étiquette (`GITHUB_REF_NAME`) sans avoir à rapatrier tout l'historique, et
 * une construction hors dépôt Git reste possible.
 *
 * `providers.exec` et non `ProcessBuilder` : c'est ce qui rend l'appel
 * compatible avec le cache de configuration, que ce dépôt utilise. Un appel
 * direct l'invaliderait à chaque construction.
 *
 * L'échec n'est pas une erreur : `isIgnoreExitValue` puis contrôle du code de
 * retour. `git describe` échoue légitimement dans un dépôt sans aucune
 * étiquette, et faire échouer la construction pour cela empêcherait quiconque
 * de compiler le projet depuis un clone frais.
 */
val describedVersion: String =
    providers.environmentVariable("RELEASE_VERSION").orNull?.takeIf(String::isNotBlank)
        ?: providers.exec {
            commandLine("git", "describe", "--tags", "--always", "--dirty")
            isIgnoreExitValue = true
        }.let { execution ->
            execution.standardOutput.asText.get().trim()
                .takeIf { execution.result.get().exitValue == 0 && it.isNotEmpty() }
        }
        ?: fallbackVersionName

/** Les trois nombres d'une version sémantique, où qu'ils commencent. */
val semanticVersion = Regex("""^v?(\d+)\.(\d+)\.(\d+)""").find(describedVersion)

/**
 * Le `versionCode`, dérivé des **mêmes** nombres que le nom de version.
 *
 * Il ne se saisit pas à la main : deux sources de vérité pour une même version
 * sont une divergence programmée, et c'est celle-là qu'on découvre le jour où
 * l'on publie une 1.1 portant encore le code de la 1.0.
 *
 * `major × 1 000 000 + minor × 1 000 + patch` : strictement croissant avec la
 * version tant que `minor` et `patch` restent sous 1 000, ce qui laisse de la
 * marge, et borné bien en deçà du maximum d'un entier signé — Google Play
 * refuse au-delà de 2 100 000 000.
 *
 * Le plancher à **1** n'est pas une précaution de style : Android refuse un
 * `versionCode` nul, et le repli `0.0.0-inconnue` en produirait précisément un
 * — l'expression régulière y trouve trois zéros.
 *
 * Une construction intermédiaire porte le code de l'étiquette dont elle
 * descend : `v1.0.0-3-gabc1234` vaut donc autant que `v1.0.0`. C'est sans
 * conséquence — son **nom** dit qu'elle n'est pas publiable, et rien ne la
 * publie — mais l'installer par-dessus la version publiée est possible.
 */
val derivedVersionCode: Int =
    semanticVersion
        ?.destructured
        ?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt() }
        ?.coerceAtLeast(1)
        ?: 1

/**
 * Le nom affiché par l'application.
 *
 * Le `v` initial de l'étiquette est retiré — il appartient à la convention de
 * nommage Git, pas au numéro de version — et tout ce que `git describe` ajoute
 * est **conservé**. C'est précisément ce qui distingue, dans une capture d'écran
 * de rapport de bogue, une version publiée d'une construction intermédiaire.
 */
val derivedVersionName: String = describedVersion.removePrefix("v")

android {
    namespace = "fr.vbrosseau.tailscaleautorules"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "fr.vbrosseau.tailscaleautorules"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = derivedVersionCode
        versionName = derivedVersionName

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

    /**
     * Signature de production, entièrement pilotée par l'environnement.
     *
     * Rien n'est écrit dans le dépôt : ni chemin, ni alias, ni mot de passe. Le
     * keystore vit dans les secrets, jamais dans une copie de travail.
     *
     * **L'absence des variables ne fait pas échouer la construction** :
     * `assembleRelease` produit alors un artefact non signé. C'est délibéré —
     * quiconque construit le projet sans elles doit y parvenir, et un échec à
     * cet endroit ressemblerait à une erreur de configuration de sa part.
     *
     * `providers.environmentVariable` plutôt que `System.getenv` : le second est
     * une lecture non déclarée, que le cache de configuration ne sait pas
     * invalider — une variable modifiée resterait sans effet.
     */
    val releaseKeystore =
        providers.environmentVariable("RELEASE_KEYSTORE").orNull
            ?.let(::File)
            ?.takeIf(File::exists)

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
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
            signingConfig = signingConfigs.findByName("release")
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
