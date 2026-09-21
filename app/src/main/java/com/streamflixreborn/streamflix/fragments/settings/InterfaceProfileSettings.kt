package com.streamflixreborn.streamflix.fragments.settings

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.streamflixreborn.streamflix.interfaceprofile.InterfaceProfile
import com.streamflixreborn.streamflix.interfaceprofile.InterfaceProfileManager
import com.streamflixreborn.streamflix.providers.Provider

/** Shared preference UI; Preference widgets preserve focus and D-pad behavior on TV. */
object InterfaceProfileSettings {
    fun install(fragment: Fragment, screen: PreferenceScreen) {
        val context = fragment.requireContext()
        screen.removePreferenceRecursively("interface_profile_category")
        val category = PreferenceCategory(context).apply {
            key = "interface_profile_category"
            title = "Interface Profiles"
        }
        screen.addPreference(category)
        category.addPreference(Preference(context).apply {
            key = "interface_profile_active"
            title = "Active Interface Profile"
            summary = InterfaceProfileManager.requireActive().name
            setOnPreferenceClickListener { showProfiles(fragment, screen); true }
        })
        addSwitch(context, category, "Combine Home") { it.combineHome } { p, v -> p.copy(combineHome = v) }
        addSwitch(context, category, "Combine Search") { it.combineSearch } { p, v -> p.copy(combineSearch = v) }
        addSwitch(context, category, "Combine Favorites") { it.combineFavorites } { p, v -> p.copy(combineFavorites = v) }
        addSwitch(context, category, "Combine Continue Watching") { it.combineContinueWatching } { p, v -> p.copy(combineContinueWatching = v) }
    }

    private fun addSwitch(
        context: Context,
        category: PreferenceCategory,
        titleText: String,
        read: (InterfaceProfile) -> Boolean,
        change: (InterfaceProfile, Boolean) -> InterfaceProfile,
    ) = category.addPreference(SwitchPreferenceCompat(context).apply {
        title = titleText
        isChecked = read(InterfaceProfileManager.requireActive())
        setOnPreferenceChangeListener { _, value ->
            InterfaceProfileManager.update(change(InterfaceProfileManager.requireActive(), value as Boolean)); true
        }
    })

    private fun showProfiles(fragment: Fragment, screen: PreferenceScreen) {
        val profiles = InterfaceProfileManager.profiles.value
        val actions = profiles.map { if (it.id == InterfaceProfileManager.requireActive().id) "✓ ${it.name}" else it.name } + "＋ Create profile"
        AlertDialog.Builder(fragment.requireContext()).setTitle("Interface Profiles")
            .setItems(actions.toTypedArray()) { _, index ->
                if (index == profiles.size) edit(fragment, screen, null) else showProfileActions(fragment, screen, profiles[index])
            }.show()
    }

    private fun showProfileActions(fragment: Fragment, screen: PreferenceScreen, profile: InterfaceProfile) {
        val actions = arrayOf("Use profile", "Edit", "Delete")
        AlertDialog.Builder(fragment.requireContext()).setTitle(profile.name).setItems(actions) { _, which ->
            when (which) {
                0 -> InterfaceProfileManager.select(profile.id)
                1 -> edit(fragment, screen, profile)
                2 -> InterfaceProfileManager.delete(profile.id)
            }
            install(fragment, screen)
        }.show()
    }

    private fun edit(fragment: Fragment, screen: PreferenceScreen, existing: InterfaceProfile?) {
        val context = fragment.requireContext()
        val available = Provider.providers.keys.sortedWith(compareBy({ it.language }, { it.name }))
        val names = available.map { "${it.name} (${it.language})" }.toTypedArray()
        val selected = BooleanArray(available.size) { available[it].name in existing?.enabledProviderNames.orEmpty() }
        val name = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.name.orEmpty())
            hint = "Profile name"
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(context).setTitle(if (existing == null) "Create Interface Profile" else "Edit Interface Profile")
            .setView(name).setMultiChoiceItems(names, selected) { _, which, checked -> selected[which] = checked }
            .setPositiveButton("Next") { _, _ ->
                val enabled = available.filterIndexed { index, _ -> selected[index] }.map { it.name }
                if (enabled.isEmpty()) return@setPositiveButton
                choosePriority(fragment, screen, existing, name.text.toString(), enabled)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun choosePriority(fragment: Fragment, screen: PreferenceScreen, existing: InterfaceProfile?, name: String, enabled: List<String>) {
        val ordered = (existing?.providerPriority.orEmpty().filter(enabled::contains) + enabled).distinct()
        AlertDialog.Builder(fragment.requireContext()).setTitle("Default source (priority #1)")
            .setSingleChoiceItems(ordered.toTypedArray(), 0, null)
            .setPositiveButton("Save") { dialog, _ ->
                val selected = (dialog as AlertDialog).listView.checkedItemPosition.coerceAtLeast(0)
                val priority = listOf(ordered[selected]) + ordered.filterNot { it == ordered[selected] }
                if (existing == null) {
                    InterfaceProfileManager.create(name, priority).also { InterfaceProfileManager.select(it.id) }
                } else {
                    InterfaceProfileManager.update(existing.copy(name = name.ifBlank { existing.name }, enabledProviderNames = enabled, providerPriority = priority, defaultProviderName = priority.first()))
                }
                install(fragment, screen)
            }.setNegativeButton("Cancel", null).show()
    }
}
