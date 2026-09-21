package com.streamflixreborn.streamflix.interfaceprofile

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceProfileTest {
    @Test fun normalizationDropsMissingProvidersAndPreservesPriority() {
        val profile = InterfaceProfile(
            id = "stable-id",
            name = "Living room",
            enabledProviderNames = listOf("Missing", "B", "A"),
            providerPriority = listOf("A", "Missing", "B"),
            defaultProviderName = "Missing",
        ).normalized(setOf("A", "B"))

        assertEquals("stable-id", profile.id)
        assertEquals(listOf("B", "A"), profile.enabledProviderNames)
        assertEquals(listOf("A", "B"), profile.providerPriority)
        assertEquals("A", profile.defaultProviderName)
    }
}
