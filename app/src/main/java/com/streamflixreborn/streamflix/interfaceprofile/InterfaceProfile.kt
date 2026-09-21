package com.streamflixreborn.streamflix.interfaceprofile

import java.util.UUID

/** A user-owned interface configuration. It is deliberately unrelated to account profiles. */
data class InterfaceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabledProviderNames: List<String>,
    val providerPriority: List<String> = enabledProviderNames,
    val defaultProviderName: String? = providerPriority.firstOrNull(),
    val preferredLanguage: String? = null,
    val combineHome: Boolean = true,
    val combineSearch: Boolean = true,
    val combineFavorites: Boolean = true,
    val combineContinueWatching: Boolean = true,
) {
    fun normalized(available: Set<String>): InterfaceProfile {
        val enabled = enabledProviderNames.distinct().filter(available::contains)
        val ordered = (providerPriority.filter(enabled::contains) + enabled).distinct()
        return copy(
            enabledProviderNames = enabled,
            providerPriority = ordered,
            defaultProviderName = defaultProviderName?.takeIf(enabled::contains) ?: ordered.firstOrNull(),
        )
    }
}
