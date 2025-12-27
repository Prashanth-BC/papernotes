package com.example.notes.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Main navigation sections for the app
 */
enum class MainNavSection(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    LIBRARY("library", "Library", Icons.Default.Home, Icons.Default.Home),
    SEARCH("search", "Search", Icons.Default.Search, Icons.Default.Search),
    NOTEBOOKS("notebooks", "Notebooks", Icons.Default.MenuBook, Icons.Default.MenuBook),
    SETTINGS("settings", "Settings", Icons.Default.Settings, Icons.Default.Settings)
}

/**
 * Bottom navigation bar with main app sections.
 * Inspired by Amazon Kindle's bottom navigation for thumb-friendly access.
 *
 * @param currentSection Currently selected section
 * @param onSectionSelected Callback when a section is selected
 * @param modifier Modifier for the navigation bar
 */
@Composable
fun MainBottomNavigation(
    currentSection: MainNavSection,
    onSectionSelected: (MainNavSection) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        MainNavSection.entries.forEach { section ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (currentSection == section) {
                            section.selectedIcon
                        } else {
                            section.icon
                        },
                        contentDescription = section.label
                    )
                },
                label = { Text(section.label) },
                selected = currentSection == section,
                onClick = { onSectionSelected(section) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
