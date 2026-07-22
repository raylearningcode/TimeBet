package com.timebet.app.features.controlledapps

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.ControlledAppEntity
import com.timebet.app.data.repositories.InstalledApp
import com.timebet.app.design.theme.*
import kotlinx.coroutines.launch

data class AppCategory(val name: String, val label: String, val packages: List<String> = emptyList())

// Cache installed apps to avoid slow PackageManager queries on every visit
private var cachedInstalledApps: List<InstalledApp>? = null

private val appCategories = listOf(
    AppCategory("all", "All"),
    AppCategory("social", "Social", listOf("com.facebook", "com.instagram", "com.twitter", "com.whatsapp", "com.tiktok", "com.snapchat", "com.discord", "com.telegram", "org.telegram", "com.reddit", "com.linkedin", "com.pinterest")),
    AppCategory("games", "Games", listOf("com.king", "com.supercell", "com.nianticlabs", "com.rovio", "com.mojang", "com.epicgames", "com.ea", "com.ubisoft", "com.netflix", "com.roblox", "com.miniclip")),
    AppCategory("video", "Video", listOf("com.google.android.youtube", "com.netflix.mediaclient", "com.disney", "com.hbo", "com.amazon.avod", "com.spotify", "com.apple.android.music", "com.soundcloud", "com.twitch")),
    AppCategory("browser", "Browsers", listOf("com.android.chrome", "com.chrome", "org.mozilla", "com.opera", "com.microsoft.emmx", "com.uc.browser", "com.duckduckgo")),
    AppCategory("other", "Other"),
)

@Composable
fun ControlledAppsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var controlledApps by remember { mutableStateOf<List<ControlledAppEntity>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var selectedCount by remember { mutableIntStateOf(0) }
    var showSelectAllConfirm by remember { mutableStateOf(false) }
    var showDeselectAllConfirm by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Use cached list if available, otherwise query PackageManager
        installedApps = cachedInstalledApps ?: run {
            val apps = ServiceLocator.appRepository.getInstalledApps()
            cachedInstalledApps = apps
            apps
        }
        controlledApps = ServiceLocator.appRepository.getAllControlledApps()
        isLoading = false
    }

    val controlledPackages = controlledApps.filter { it.isControlled }.map { it.packageName }.toSet()
    selectedCount = controlledPackages.size

    // Filter apps
    val filteredApps = remember(installedApps, searchQuery, selectedCategory) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isEmpty() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "all" ||
                appCategories.find { it.name == selectedCategory }?.packages?.any { pkg ->
                    app.packageName.lowercase().startsWith(pkg.lowercase())
                } == true
            matchesSearch && matchesCategory
        }.sortedBy { it.appName.lowercase() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(TimeBetBlack)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TimeBetWhite)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Manage Apps", style = TimeBetTypography.headlineMedium, color = TimeBetWhite)
                Text(
                    "$selectedCount apps tracked · ${controlledPackages.size} active",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps...", color = TimeBetTextTertiary) },
            leadingIcon = { Icon(Icons.Filled.Search, "Search", tint = TimeBetTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, "Clear", tint = TimeBetTextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TimeBetWhite,
                unfocusedTextColor = TimeBetWhite,
                focusedBorderColor = TimeBetBorderLight,
                unfocusedBorderColor = TimeBetBorder,
                cursorColor = TimeBetWhite,
                focusedContainerColor = TimeBetSurfaceElevated,
                unfocusedContainerColor = TimeBetSurfaceElevated
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Category chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            appCategories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category.name,
                    onClick = { selectedCategory = category.name },
                    label = { Text(category.label, style = TimeBetTypography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TimeBetWhite,
                        selectedLabelColor = TimeBetBlack,
                        containerColor = TimeBetSurfaceElevated,
                        labelColor = TimeBetWhite
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Quick actions
        if (searchQuery.isEmpty() && selectedCategory == "all") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = { showSelectAllConfirm = true }) {
                    Text("Select All", color = TimeBetGreen, style = TimeBetTypography.labelMedium)
                }
                TextButton(onClick = { showDeselectAllConfirm = true }) {
                    Text("Deselect All", color = TimeBetRed, style = TimeBetTypography.labelMedium)
                }
            }
        }

        HorizontalDivider(color = TimeBetBorder.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))

        // App list
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TimeBetWhite)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading apps...", style = TimeBetTypography.bodyMedium, color = TimeBetTextTertiary)
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotEmpty()) "No apps matching \"$searchQuery\"" else "No apps found",
                    style = TimeBetTypography.bodyLarge,
                    color = TimeBetTextTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isControlled = app.packageName in controlledPackages
                    AppListItem(
                        app = app,
                        isControlled = isControlled,
                        onToggle = { checked ->
                            scope.launch {
                                ServiceLocator.appRepository.setAppControlled(
                                    app.packageName, app.appName, controlled = checked
                                )
                                controlledApps = ServiceLocator.appRepository.getAllControlledApps()
                            }
                        }
                    )
                }
            }
        }
    }

    // Confirmation dialog for Select All
    if (showSelectAllConfirm) {
        AlertDialog(
            onDismissRequest = { showSelectAllConfirm = false },
            containerColor = TimeBetSurfaceElevated,
            title = { Text("Select All Apps?", color = TimeBetWhite) },
            text = {
                Text(
                    "This will track ALL ${installedApps.size} installed apps. This may drain battery faster and make the app harder to use. Consider selecting only entertainment apps.",
                    color = TimeBetTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSelectAllConfirm = false
                    scope.launch {
                        installedApps.forEach { app ->
                            if (app.packageName !in controlledPackages) {
                                ServiceLocator.appRepository.setAppControlled(app.packageName, app.appName, controlled = true)
                            }
                        }
                        controlledApps = ServiceLocator.appRepository.getAllControlledApps()
                    }
                }) { Text("Select All", color = TimeBetGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showSelectAllConfirm = false }) {
                    Text("Cancel", color = TimeBetTextTertiary)
                }
            }
        )
    }

    // Confirmation dialog for Deselect All
    if (showDeselectAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeselectAllConfirm = false },
            containerColor = TimeBetSurfaceElevated,
            title = { Text("Deselect All Apps?", color = TimeBetWhite) },
            text = {
                Text(
                    "This will stop tracking all ${controlledPackages.size} apps. TimeBet won't monitor any apps until you re-select them.",
                    color = TimeBetTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeselectAllConfirm = false
                    scope.launch {
                        controlledApps.filter { it.isControlled }.forEach { app ->
                            ServiceLocator.appRepository.setAppControlled(app.packageName, app.appName, controlled = false)
                        }
                        controlledApps = ServiceLocator.appRepository.getAllControlledApps()
                    }
                }) { Text("Deselect All", color = TimeBetRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeselectAllConfirm = false }) {
                    Text("Cancel", color = TimeBetTextTertiary)
                }
            }
        )
    }
}

@Composable
private fun AppListItem(
    app: InstalledApp,
    isControlled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isControlled) TimeBetGreen.copy(alpha = 0.08f) else TimeBetSurfaceElevated.copy(alpha = 0.5f))
            .clickable { onToggle(!isControlled) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(TimeBetSurface),
            contentAlignment = Alignment.Center
        ) {
            // Load app icon via PackageManager
            var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
            LaunchedEffect(app.packageName) {
                try {
                    appIcon = context.packageManager.getApplicationIcon(app.packageName)
                } catch (_: Exception) {}
            }
            if (appIcon != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = appIcon),
                    contentDescription = app.appName,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    app.appName.take(1).uppercase(),
                    style = TimeBetTypography.labelLarge,
                    color = if (isControlled) TimeBetGreen else TimeBetTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // App name only
        Text(
            app.appName,
            style = TimeBetTypography.bodyLarge,
            color = if (isControlled) TimeBetGreen else TimeBetWhite,
            fontWeight = if (isControlled) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Toggle
        Switch(
            checked = isControlled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TimeBetBlack,
                checkedTrackColor = TimeBetGreen,
                uncheckedThumbColor = TimeBetWhite,
                uncheckedTrackColor = TimeBetBorder
            )
        )
    }
}
