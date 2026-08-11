package fr.vbrosseau.tailscaleautorules.presentation.blacklist

/** Repères de test, indépendants des libellés traduisibles. */
object BlacklistTestTags {
    const val EMPTY = "blacklist:empty"
    const val LIST = "blacklist:list"
    const val ADD = "blacklist:add"
    const val ADD_CURRENT = "blacklist:add-current"
    const val MOBILE_RULE = "blacklist:mobile-rule"
    const val ERROR = "blacklist:error"
    const val LOCATION_RATIONALE = "blacklist:location-rationale"
    const val LOCATION_GRANT = "blacklist:location-grant"
    const val DIALOG_FIELD = "blacklist:dialog-field"
    const val DIALOG_CONFIRM = "blacklist:dialog-confirm"

    const val EXCEPTIONS_TITLE = "blacklist:exceptions-title"

    fun entry(id: Long) = "blacklist:entry:$id"

    fun exception(id: Long) = "blacklist:exception:$id"

    fun remove(id: Long) = "blacklist:remove:$id"

    fun rename(id: Long) = "blacklist:rename:$id"
}
