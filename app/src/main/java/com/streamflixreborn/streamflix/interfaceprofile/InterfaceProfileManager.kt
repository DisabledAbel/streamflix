package com.streamflixreborn.streamflix.interfaceprofile

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object InterfaceProfileManager {
    private const val PREFS = "interface_profiles_v1"
    private const val PROFILES = "profiles"
    private const val ACTIVE = "active_id"
    private val gson = Gson()
    private lateinit var context: Context
    private val _profiles = MutableStateFlow<List<InterfaceProfile>>(emptyList())
    private val _active = MutableStateFlow<InterfaceProfile?>(null)
    private val _changes = MutableSharedFlow<InterfaceProfile>(extraBufferCapacity = 1)
    val profiles = _profiles.asStateFlow()
    val activeProfile = _active.asStateFlow()
    val changes = _changes.asSharedFlow()

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val type = object : TypeToken<List<InterfaceProfile>>() {}.type
        val saved = runCatching {
            gson.fromJson<List<InterfaceProfile>>(prefs.getString(PROFILES, null), type)
        }.getOrNull().orEmpty()
        val legacy = UserPreferences.currentProvider ?: Provider.providers.keys.firstOrNull()
        val initial = saved.ifEmpty {
            listOf(InterfaceProfile(
                id = UUID.randomUUID().toString(),
                name = "Default",
                enabledProviderNames = listOfNotNull(legacy?.name),
                providerPriority = listOfNotNull(legacy?.name),
                defaultProviderName = legacy?.name,
            ))
        }
        _profiles.value = initial
        _active.value = initial.firstOrNull { it.id == prefs.getString(ACTIVE, null) } ?: initial.first()
        persist()
        syncLegacyProvider(_active.value!!)
    }

    fun requireActive(): InterfaceProfile = _active.value
        ?: error("InterfaceProfileManager has not been initialized")

    fun create(name: String, providers: List<String>): InterfaceProfile {
        require(providers.isNotEmpty()) { "At least one provider must be enabled" }
        val profile = InterfaceProfile(name = name.trim().ifEmpty { "Interface" }, enabledProviderNames = providers.distinct())
        _profiles.value = _profiles.value + profile
        persist()
        return profile
    }

    fun update(profile: InterfaceProfile) {
        require(profile.enabledProviderNames.isNotEmpty()) { "At least one provider must be enabled" }
        require(_profiles.value.any { it.id == profile.id }) { "Unknown interface profile" }
        _profiles.value = _profiles.value.map { if (it.id == profile.id) profile else it }
        if (_active.value?.id == profile.id) select(profile.id) else persist()
    }

    fun delete(id: String): Boolean {
        if (_profiles.value.size <= 1) return false
        _profiles.value = _profiles.value.filterNot { it.id == id }
        if (_active.value?.id == id) select(_profiles.value.first().id) else persist()
        return true
    }

    fun select(id: String): Boolean {
        val selected = _profiles.value.firstOrNull { it.id == id } ?: return false
        _active.value = selected
        persist()
        syncLegacyProvider(selected)
        _changes.tryEmit(selected)
        return true
    }

    fun availableProviders(profile: InterfaceProfile = requireActive()): List<Provider> {
        val byName = Provider.providers.keys.associateBy { it.name }
        return profile.normalized(byName.keys).providerPriority.mapNotNull(byName::get)
    }

    fun exportJson(): String = gson.toJson(_profiles.value)

    fun restoreJson(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        val type = object : TypeToken<List<InterfaceProfile>>() {}.type
        val restored = runCatching { gson.fromJson<List<InterfaceProfile>>(json, type) }.getOrNull()
            ?.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.enabledProviderNames.isNotEmpty() }
            .orEmpty()
        if (restored.isEmpty()) return false
        _profiles.value = restored
        _active.value = restored.first()
        persist()
        syncLegacyProvider(restored.first())
        _changes.tryEmit(restored.first())
        return true
    }

    private fun syncLegacyProvider(profile: InterfaceProfile) {
        val provider = Provider.findByName(profile.defaultProviderName ?: profile.providerPriority.firstOrNull().orEmpty())
        if (provider != null && UserPreferences.currentProvider?.name != provider.name) UserPreferences.currentProvider = provider
    }

    private fun persist() {
        if (!::context.isInitialized) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PROFILES, gson.toJson(_profiles.value))
            .putString(ACTIVE, _active.value?.id)
            .apply()
    }
}
