package fr.vbrosseau.tailscaleautorules.presentation.blacklist

/** Repères de test, indépendants des libellés traduisibles. */
object BlacklistTestTags {
    const val EMPTY = "networks:empty"
    const val LIST = "networks:list"
    const val ADD = "networks:add"
    const val ADD_CURRENT = "networks:add-current"
    const val MOBILE_RULE = "networks:mobile-rule"
    const val ERROR = "networks:error"
    const val LOCATION_RATIONALE = "networks:location-rationale"
    const val LOCATION_GRANT = "networks:location-grant"
    const val DIALOG_FIELD = "networks:dialog-field"
    const val DIALOG_SWITCH = "networks:dialog-switch"
    const val DIALOG_CONFIRM = "networks:dialog-confirm"

    fun preference(id: Long) = "networks:preference:$id"

    fun preferenceName(id: Long) = "networks:preference-name:$id"

    fun preferenceSwitch(id: Long) = "networks:preference-switch:$id"
}
