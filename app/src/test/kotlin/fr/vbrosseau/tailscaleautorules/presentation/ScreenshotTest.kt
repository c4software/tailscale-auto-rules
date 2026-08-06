package fr.vbrosseau.tailscaleautorules.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base des tests de rendu visuel.
 *
 * Les tests d'interface existants vérifient **ce qui est affiché** ; ceux-ci
 * vérifient **à quoi cela ressemble**. Une régression de mise en page, de
 * contraste ou de thème sombre ne casse aucune assertion textuelle — seule une
 * comparaison d'image la révèle.
 *
 * Le format d'écran est figé par `@Config(qualifiers)` : sans lui, la référence
 * dépendrait de la configuration par défaut de Robolectric, susceptible de
 * changer d'une version à l'autre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-rFR-w411dp-h891dp-xhdpi")
abstract class ScreenshotTest {

    /**
     * Capture un écran dans les deux thèmes.
     *
     * Le thème sombre n'est jamais celui qu'on regarde en développant : c'est
     * donc là que les défauts de contraste s'installent sans être vus. Le
     * capturer systématiquement coûte une image et les rend visibles.
     *
     * La couleur dynamique est désactivée : elle dépend du fond d'écran de
     * l'utilisateur, ce qui rendrait toute référence instable.
     *
     * @param name racine du nom de fichier ; le thème y est suffixé.
     */
    @OptIn(ExperimentalTestApi::class)
    protected fun capture(name: String, content: @Composable () -> Unit) {
        listOf(THEME_LIGHT to false, THEME_DARK to true).forEach { (label, dark) ->
            runComposeUiTest {
                // L'horloge n'avance que sur ordre. Sans cela, un indicateur de
                // progression — animation sans fin — empêche l'interface
                // d'atteindre le repos : la capture ne se termine jamais et le
                // test tourne en boucle, processeur à fond. Constaté sur
                // l'écran d'accueil en cours de synchronisation.
                mainClock.autoAdvance = false

                setContent {
                    AppTheme(darkTheme = dark, dynamicColor = false) {
                        // Le fond du thème est peint explicitement : sans lui,
                        // la capture serait transparente et les deux thèmes
                        // paraîtraient identiques.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            content()
                        }
                    }
                }

                // Une image suffit à composer et mesurer ; la figer ici rend
                // aussi la référence reproductible, une animation capturée à un
                // instant quelconque ne l'étant pas.
                mainClock.advanceTimeByFrame()

                onRoot().captureRoboImage(filePath = "$SCREENSHOT_DIR/$name-$label.png")
            }
        }
    }

    private companion object {
        const val SCREENSHOT_DIR = "src/test/screenshots"
        const val THEME_LIGHT = "clair"
        const val THEME_DARK = "sombre"
    }
}
