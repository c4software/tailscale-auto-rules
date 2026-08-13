package fr.vbrosseau.tailscaleautorules.presentation.onboarding

/** Repères de test, indépendants des libellés traduisibles. */
object OnboardingTestTags {
    const val PAGER = "onboarding:pager"
    const val CONTINUE = "onboarding:continue"
    const val LEARNING_ACCEPT = "onboarding:learning-accept"
    const val LEARNING_DECLINE = "onboarding:learning-decline"

    fun page(index: Int) = "onboarding:page:$index"
}
