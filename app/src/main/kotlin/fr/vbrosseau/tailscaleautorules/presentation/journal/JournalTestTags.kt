package fr.vbrosseau.tailscaleautorules.presentation.journal

/** Repères de test, indépendants des libellés traduisibles. */
object JournalTestTags {
    const val EMPTY = "journal:empty"
    const val LIST = "journal:list"
    const val CLEAR = "journal:clear"
    const val CLEAR_CONFIRM = "journal:clear-confirm"

    fun transition(id: Long) = "journal:transition:$id"

    fun rule(id: Long) = "journal:rule:$id"

    fun timestamp(id: Long) = "journal:timestamp:$id"
}
