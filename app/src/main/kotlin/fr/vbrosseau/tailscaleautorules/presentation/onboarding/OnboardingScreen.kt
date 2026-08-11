package fr.vbrosseau.tailscaleautorules.presentation.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import fr.vbrosseau.tailscaleautorules.presentation.theme.Spacing
import kotlinx.coroutines.launch

private const val WELCOME_PAGE = 0
private const val NOTIFICATION_PAGE = 1
private const val LOCATION_PAGE = 2
private const val LEARNING_PAGE = 3
private const val PAGE_COUNT = 4

private val IconBadgeSize = 96.dp
private val IconSize = 48.dp

/**
 * Parcours du premier lancement (SPECS.md §6.5), sans état applicatif.
 *
 * Quatre pages qui avancent d'un bouton ou d'un glissement. Les pages de
 * permission tiennent le rôle d'écran d'explication préalable exigé par
 * SPECS.md §8 : la demande part de la page, jamais avant elle. Un refus est
 * sans conséquence ici — les cartes d'explication de l'application
 * redemandent au moment du besoin réel.
 *
 * L'écran vit **hors** de l'ossature — pas de `Scaffold`, donc pas de gestion
 * d'insets héritée : il pose lui-même `safeDrawingPadding`, sans quoi le titre
 * passait sous la barre d'état et les boutons sous la barre gestuelle.
 *
 * La dernière page clôt le parcours par le choix d'apprentissage : les deux
 * réponses sont des issues de même rang, pas un accord et un renoncement.
 */
@Composable
fun OnboardingScreen(
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onFinish: (learningEnabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    // Une `Surface`, parce que l'écran vit hors du `Scaffold` : sans elle,
    // rien ne pose la couleur de contenu, et le texte restait noir sur le
    // fond sombre — le même défaut que le harnais de captures a eu.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        OnboardingBody(
            pagerState = pagerState,
            onAdvance = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
            onFinish = onFinish,
        )
    }
}

@Composable
private fun OnboardingBody(
    pagerState: PagerState,
    onAdvance: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onFinish: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .testTag(OnboardingTestTags.PAGER),
        ) { page ->
            PageContent(
                page = page,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestLocationPermission = onRequestLocationPermission,
            )
        }

        PageDots(
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        if (pagerState.currentPage == LEARNING_PAGE) {
            LearningChoiceRow(onFinish = onFinish)
        } else {
            Button(
                onClick = onAdvance,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(OnboardingTestTags.CONTINUE),
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

@Composable
private fun PageContent(
    page: Int,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (page) {
        WELCOME_PAGE -> OnboardingPage(
            index = page,
            iconRes = R.drawable.ic_onboarding_shield,
            titleRes = R.string.onboarding_welcome_title,
            bodyRes = R.string.onboarding_welcome_body,
            modifier = modifier,
        )

        NOTIFICATION_PAGE -> OnboardingPage(
            index = page,
            iconRes = R.drawable.ic_onboarding_notification,
            titleRes = R.string.onboarding_notification_title,
            bodyRes = R.string.onboarding_notification_body,
            modifier = modifier,
            grantLabelRes = R.string.onboarding_notification_grant,
            grantTestTag = OnboardingTestTags.NOTIFICATION_GRANT,
            onGrant = onRequestNotificationPermission,
        )

        LOCATION_PAGE -> OnboardingPage(
            index = page,
            iconRes = R.drawable.ic_onboarding_location,
            titleRes = R.string.onboarding_location_title,
            bodyRes = R.string.onboarding_location_body,
            modifier = modifier,
            grantLabelRes = R.string.onboarding_location_grant,
            grantTestTag = OnboardingTestTags.LOCATION_GRANT,
            onGrant = onRequestLocationPermission,
        )

        LEARNING_PAGE -> OnboardingPage(
            index = page,
            iconRes = R.drawable.ic_onboarding_learning,
            titleRes = R.string.onboarding_learning_title,
            bodyRes = R.string.onboarding_learning_body,
            modifier = modifier,
        )
    }
}

/**
 * Une page du parcours : pastille d'icône, titre et texte centrés, et
 * l'éventuelle demande de permission.
 *
 * Le bouton d'autorisation vit **dans** la page, sous son explication : c'est
 * l'ordre qu'exige le Play Store, et celui qui donne une chance à la demande
 * d'être comprise.
 */
@Composable
private fun OnboardingPage(
    index: Int,
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier,
    @StringRes grantLabelRes: Int? = null,
    grantTestTag: String? = null,
    onGrant: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .testTag(OnboardingTestTags.page(index)),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centré, pas collé en haut : une page d'accueil est une présentation,
        // pas un écran de travail.
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(IconBadgeSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(IconSize),
            )
        }
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (grantLabelRes != null && grantTestTag != null) {
            FilledTonalButton(
                onClick = onGrant,
                modifier = Modifier.testTag(grantTestTag),
            ) {
                Text(stringResource(grantLabelRes))
            }
        }
    }
}

@Composable
private fun LearningChoiceRow(
    onFinish: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(
            onClick = { onFinish(false) },
            modifier = Modifier
                .weight(1f)
                .testTag(OnboardingTestTags.LEARNING_DECLINE),
        ) {
            Text(stringResource(R.string.onboarding_learning_decline))
        }
        Button(
            onClick = { onFinish(true) },
            modifier = Modifier
                .weight(1f)
                .testTag(OnboardingTestTags.LEARNING_ACCEPT),
        ) {
            Text(stringResource(R.string.onboarding_learning_accept))
        }
    }
}

@Composable
private fun PageDots(currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(PAGE_COUNT) { index ->
            val color = if (index == currentPage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(Spacing.sm)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingWelcomePreview() {
    AppTheme(dynamicColor = false) {
        OnboardingScreen(
            onRequestNotificationPermission = {},
            onRequestLocationPermission = {},
            onFinish = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingLearningPreview() {
    AppTheme(dynamicColor = false) {
        OnboardingScreen(
            onRequestNotificationPermission = {},
            onRequestLocationPermission = {},
            onFinish = {},
            initialPage = LEARNING_PAGE,
        )
    }
}
