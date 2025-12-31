package com.example.notes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.edit
import com.example.notes.ml.ImageEmbedderHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.notes.data.NoteEntity
import com.example.notes.data.ObjectBoxStore
import com.example.notes.data.TagRepository
import com.example.notes.data.NotebookRepository
import com.example.notes.ui.components.TagChip
import androidx.compose.foundation.layout.FlowRow
import com.example.notes.ui.components.NotebookNavigationDrawer
import com.example.notes.ui.components.NoteFilterOption
import com.example.notes.ui.components.NoteSortOption
import com.example.notes.ui.components.SortFilterBottomSheet
import com.example.notes.ui.components.MainBottomNavigation
import com.example.notes.ui.components.MainNavSection
import com.example.notes.ui.components.SwipeableNoteCard
import com.example.notes.ui.components.AdaptiveNoteGrid
import com.example.notes.ui.components.GridLayoutStyle
import com.example.notes.ui.components.QuickAddSheet
import com.example.notes.ui.components.QuickTextNoteDialog
import com.example.notes.ui.components.NotebookManagementSheet
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(emptyList<NoteEntity>()) }
    val scannerManager = remember { ScannerManager(context) }
    
    // Preferences
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    
    // Smart Default: If preference doesn't exist, default to GPU on real devices, CPU on emulator.
    // If it exists, respect the user's choice.
    var useGpu by remember { 
        mutableStateOf(
            if (prefs.contains("use_gpu")) {
                prefs.getBoolean("use_gpu", false)
            } else {
                val isEmulator = com.example.notes.util.DeviceUtils.isEmulator
                val defaultGpu = !isEmulator
                prefs.edit { putBoolean("use_gpu", defaultGpu) }
                defaultGpu
            }
        )
    }
    
    // Apply preference on start
    // Note: GPU delegate switching is handled during ImageEmbedderHelper initialization
    // LaunchedEffect(useGpu) {
    //     scannerManager.updateEmbedderDelegate(if (useGpu) 1 else 0)
    // }

    // Popup state
    var showSettings by remember { mutableStateOf(false) }
    var showSearchResults by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<ScannerManager.SearchResult>()) }
    var isSearching by remember { mutableStateOf(false) }

    // Navigation Drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Navigation state
    var currentSection by remember { mutableStateOf(MainNavSection.LIBRARY) }

    // Sort & Filter state
    var showSortFilter by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(NoteSortOption.DATE_NEWEST) }
    var currentFilters by remember { mutableStateOf(setOf(NoteFilterOption.ALL)) }

    // Quick Add Sheet state
    var showQuickAdd by remember { mutableStateOf(false) }
    var showQuickTextNote by remember { mutableStateOf(false) }
    var showNotebookManagement by remember { mutableStateOf(false) }

    // Progress state
    var showProgress by remember { mutableStateOf(false) }
    var processingState by remember { mutableStateOf(ScannerManager.ProcessingState()) }
    var progressTitle by remember { mutableStateOf("Processing") }
    
    // Simplified way to get notes and unique collections
    fun refreshNotes() {
        val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
        notes = box.all
    }

    // Initialize repositories and perform one-time migration
    LaunchedEffect(Unit) {
        val notebookRepository = NotebookRepository()

        // Ensure default notebook exists
        notebookRepository.ensureDefaultNotebook()

        // Migrate old collections to new notebook system (idempotent - safe to run multiple times)
        notebookRepository.migrateCollectionsToNotebooks()

        // Refresh notes after migration
        refreshNotes()
    }

    // Collection State
    val collections by remember(notes) {
        derivedStateOf {
            val list = notes.mapNotNull { it.collection }.distinct().toMutableList()
            if (!list.contains("Scratchpad")) list.add("Scratchpad")
            list.sort()
            // Ensure Scratchpad is first
            list.remove("Scratchpad")
            list.add(0, "Scratchpad")
            list.toList()
        }
    }
    var selectedCollection by remember { mutableStateOf<String?>("Scratchpad") }
    var selectedNotebookId by remember { mutableStateOf<Long?>(null) }
    var showAddCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    val notebookRepository = remember { NotebookRepository() }
    
    // Smart Scan State
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateNote by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingScanUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    // Note Details State
    var selectedNoteForDetails by remember { mutableStateOf<NoteEntity?>(null) }
    
    // Filtered Notes
    val filteredNotes by remember(notes, selectedNotebookId, currentSort, currentFilters) {
        derivedStateOf {
            // Filter by notebook
            var result = if (selectedNotebookId == null) notes
                        else notes.filter { it.notebookId == selectedNotebookId }

            // Apply filters
            if (currentFilters.isNotEmpty() && !currentFilters.contains(NoteFilterOption.ALL)) {
                result = result.filter { note ->
                    currentFilters.any { filter ->
                        when (filter) {
                            NoteFilterOption.ALL -> true
                            NoteFilterOption.FAVORITES -> note.isFavorite
                            NoteFilterOption.PINNED -> note.isPinned
                            NoteFilterOption.HAS_TEXT -> !note.mlKitText.isNullOrBlank() || !note.colorBasedText.isNullOrBlank()
                            NoteFilterOption.NO_TEXT -> note.mlKitText.isNullOrBlank() && note.colorBasedText.isNullOrBlank()
                            NoteFilterOption.TASKS_ONLY -> note.noteType == "TASK"
                            NoteFilterOption.NOTES_ONLY -> note.noteType != "TASK"
                            NoteFilterOption.TASKS_INCOMPLETE -> note.noteType == "TASK" && note.isTaskCompleted != true
                            NoteFilterOption.TASKS_COMPLETE -> note.noteType == "TASK" && note.isTaskCompleted == true
                        }
                    }
                }
            }

            // Apply sorting
            result = when (currentSort) {
                NoteSortOption.DATE_NEWEST -> result.sortedByDescending { it.timestamp }
                NoteSortOption.DATE_OLDEST -> result.sortedBy { it.timestamp }
                NoteSortOption.TITLE_AZ -> result.sortedBy { it.title }
                NoteSortOption.TITLE_ZA -> result.sortedByDescending { it.title }
                NoteSortOption.PINNED_FIRST -> result.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }
                        .thenByDescending { it.pinnedAt ?: 0 }
                        .thenByDescending { it.timestamp }
                )
            }

            result
        }
    }

    var isSearchMode by remember { mutableStateOf(false) }

    // Selection Mode State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }

    // Back button handler for search results
    BackHandler(enabled = showSearchResults) {
        showSearchResults = false
        isSearching = false
    }

    fun toggleSelection(noteId: Long) {
        val newSelection = selectedNoteIds.toMutableSet()
        if (newSelection.contains(noteId)) {
            newSelection.remove(noteId)
        } else {
            newSelection.add(noteId)
        }
        selectedNoteIds = newSelection
        
        if (selectedNoteIds.isEmpty()) {
            isSelectionMode = false
        }
    }

    fun enterSelectionMode(noteId: Long) {
        isSelectionMode = true
        selectedNoteIds = setOf(noteId)
    }
    
    fun clearSelection() {
        isSelectionMode = false
        selectedNoteIds = emptySet()
    }

    val scope = rememberCoroutineScope()

    // Image picker launcher for gallery import
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // Process imported image similar to scanned image
            val targetCollection = selectedCollection ?: "Scratchpad"

            scope.launch {
                showProgress = true
                progressTitle = "Processing Image"

                scannerManager.processScanResult(
                    uri = uri,
                    onProgress = { state ->
                        processingState = state
                    },
                    onWaitingForConfirmation = { },
                    onComplete = { newNote ->
                        val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                        newNote.collection = targetCollection
                        newNote.notebookId = selectedNotebookId
                        box.put(newNote)
                        refreshNotes()
                        showProgress = false
                    }
                )
            }
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val gmsResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            gmsResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val uri = pages[0].imageUri
                    
                    if (isSearchMode) {
                        // Search mode
                        scope.launch {
                            isSearching = true
                            showSearchResults = true
                            showProgress = true
                            progressTitle = "Searching Notes"
                            searchResults = emptyList()

                            scannerManager.search(
                                uri = uri,
                                onProgress = { state ->
                                    processingState = state
                                },
                                onComplete = { results ->
                                    searchResults = results
                                    isSearching = false
                                    showProgress = false
                                }
                            )
                        }
                    } else {
                        // Handle Add Note with Smart Collection Logic
                        val targetCollection = selectedCollection ?: "Scratchpad"

                        // Check for duplicates in target collection
                        val bitmap = com.example.notes.ml.ImagePreprocessor.loadBitmapFromUri(context, uri)

                        scope.launch {
                             val similar = scannerManager.findSimilarInCollection(bitmap, targetCollection)
                             if (similar != null) {
                                 // Found duplicate
                                 duplicateNote = similar.note
                                 pendingScanUri = uri
                                 showDuplicateDialog = true
                             } else {
                                 // No duplicate, show progress and save
                                 showProgress = true
                                 progressTitle = "Adding Note"

                                 scannerManager.processScanResult(
                                     uri = uri,
                                     onProgress = { state ->
                                         processingState = state
                                     },
                                     onWaitingForConfirmation = { },
                                     onComplete = { newNote ->
                                          val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                          newNote.collection = targetCollection
                                          box.put(newNote)
                                          refreshNotes()
                                          showProgress = false
                                     }
                                 )
                             }
                        }
                    }
                }
            }
        }
    }

    // Note Details Screen
    if (selectedNoteForDetails != null) {
        NoteDetailsScreen(
            note = selectedNoteForDetails!!,
            onDismiss = { selectedNoteForDetails = null },
            onSave = { updatedNote ->
                scope.launch {
                    val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                    box.put(updatedNote)
                    refreshNotes()
                }
            },
            onDelete = {
                scope.launch {
                    selectedNoteForDetails?.let { note ->
                        val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                        box.remove(note)
                        refreshNotes()
                    }
                    selectedNoteForDetails = null
                }
            }
        )
    }

    // Progress Dialog
    if (showProgress) {
        ProcessingProgressDialog(
            title = progressTitle,
            state = processingState,
            onDismiss = { showProgress = false }
        )
    }

    // Main UI (always shown)
    Box(modifier = Modifier.fillMaxSize()) {
        // Background main UI
            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("Settings") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            // GPU Settings
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                     val newValue = !useGpu
                                     useGpu = newValue
                                     prefs.edit { putBoolean("use_gpu", newValue) }
                                }
                            ) {
                                Text("Use GPU (Experimental)", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = useGpu,
                                    onCheckedChange = {
                                        useGpu = it
                                        prefs.edit { putBoolean("use_gpu", it) }
                                    }
                                )
                            }
                            Text(
                                "Enable GPU for faster processing. Disable if app crashes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            // Search Threshold Settings
                            Text(
                                "Search Thresholds",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Lower values = stricter matching (fewer results)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            var clipThreshold by remember {
                                mutableFloatStateOf(prefs.getFloat("clip_threshold", 0.2f))
                            }
                            SettingsSlider(
                                label = "CLIP Image Threshold",
                                value = clipThreshold,
                                onValueChange = {
                                    clipThreshold = it
                                    prefs.edit { putFloat("clip_threshold", it) }
                                }
                            )

                            var trocrThreshold by remember {
                                mutableFloatStateOf(prefs.getFloat("trocr_threshold", 0.2f))
                            }
                            SettingsSlider(
                                label = "TrOCR Visual Threshold",
                                value = trocrThreshold,
                                onValueChange = {
                                    trocrThreshold = it
                                    prefs.edit { putFloat("trocr_threshold", it) }
                                }
                            )

                            var mlkitThreshold by remember {
                                mutableFloatStateOf(prefs.getFloat("mlkit_threshold", 0.2f))
                            }
                            SettingsSlider(
                                label = "ML Kit Text Threshold",
                                value = mlkitThreshold,
                                onValueChange = {
                                    mlkitThreshold = it
                                    prefs.edit { putFloat("mlkit_threshold", it) }
                                }
                            )

                            var colorbasedThreshold by remember {
                                mutableFloatStateOf(prefs.getFloat("colorbased_threshold", 0.2f))
                            }
                            SettingsSlider(
                                label = "ColorBased Text Threshold",
                                value = colorbasedThreshold,
                                onValueChange = {
                                    colorbasedThreshold = it
                                    prefs.edit { putFloat("colorbased_threshold", it) }
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            // Regenerate Embeddings Section
                            Text(
                                "Maintenance",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Fix missing embeddings for existing notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            var isRegenerating by remember { mutableStateOf(false) }
                            var regenerationStatus by remember { mutableStateOf("") }

                            Button(
                                onClick = {
                                    isRegenerating = true
                                    regenerationStatus = "Checking notes..."
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                            val allNotes = box.all
                                            val notesWithoutTrOCR = allNotes.filter { it.trocrEmbedding == null && it.imagePath.isNotEmpty() }

                                            withContext(Dispatchers.Main) {
                                                regenerationStatus = "Found ${notesWithoutTrOCR.size} notes missing TrOCR embeddings"
                                            }

                                            if (notesWithoutTrOCR.isNotEmpty()) {
                                                notesWithoutTrOCR.forEachIndexed { index, note ->
                                                    withContext(Dispatchers.Main) {
                                                        regenerationStatus = "Processing ${index + 1}/${notesWithoutTrOCR.size}: ${note.title}"
                                                    }

                                                    try {
                                                        // Load the bitmap
                                                        val bitmap = android.graphics.BitmapFactory.decodeFile(note.imagePath)
                                                        if (bitmap != null) {
                                                            // Generate TrOCR embedding
                                                            val trocrResult = scannerManager.generateTrOCREmbedding(bitmap)
                                                            if (trocrResult != null) {
                                                                note.trocrEmbedding = trocrResult
                                                                box.put(note)
                                                                android.util.Log.d("HomeScreen", "Regenerated TrOCR for note ${note.id}: ${note.title}")
                                                            }
                                                            bitmap.recycle()
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("HomeScreen", "Failed to regenerate TrOCR for note ${note.id}", e)
                                                    }
                                                }

                                                withContext(Dispatchers.Main) {
                                                    regenerationStatus = "✓ Successfully regenerated ${notesWithoutTrOCR.size} embeddings"
                                                    refreshNotes()
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    regenerationStatus = "✓ All notes have TrOCR embeddings"
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                regenerationStatus = "✗ Error: ${e.message}"
                                            }
                                            android.util.Log.e("HomeScreen", "Error regenerating embeddings", e)
                                        } finally {
                                            withContext(Dispatchers.Main) {
                                                isRegenerating = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRegenerating
                            ) {
                                if (isRegenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (isRegenerating) "Processing..." else "Regenerate Missing TrOCR Embeddings")
                            }

                            if (regenerationStatus.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = regenerationStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        regenerationStatus.startsWith("✓") -> Color(0xFF4CAF50)
                                        regenerationStatus.startsWith("✗") -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettings = false }) {
                            Text("Done")
                        }
                    }
                )
            }

            // Navigation Drawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    NotebookNavigationDrawer(
                        selectedNotebookId = selectedNotebookId,
                        notebookRepository = notebookRepository,
                        onNotebookSelected = { notebookId ->
                            selectedNotebookId = notebookId
                            scope.launch { drawerState.close() }
                        },
                        onShowFavorites = {
                            // Show only favorites
                            selectedNotebookId = null
                            // TODO: Add favorites filter flag
                            scope.launch { drawerState.close() }
                        },
                        onShowAll = {
                            selectedNotebookId = null
                            scope.launch { drawerState.close() }
                        },
                        onAddNotebook = {
                            showNotebookManagement = true
                            scope.launch { drawerState.close() }
                        },
                        onSettingsClick = {
                            showSettings = true
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                Scaffold(
                    topBar = {
                        if (!isSelectionMode) {
                            TopAppBar(
                                title = {
                                    Text(
                                        "Fusion Notes",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Open Notebooks")
                                    }
                                },
                                actions = {
                                    // Only keep filter - search and settings now in bottom nav
                                    IconButton(onClick = { showSortFilter = true }) {
                                        Icon(Icons.Default.FilterList, contentDescription = "Sort & Filter")
                                    }
                                }
                )
            } else {
                TopAppBar(
                    title = { Text("${selectedNoteIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                             showAddCollectionDialog = true
                        }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move to Collection")
                        }
                        IconButton(onClick = {
                            val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                            box.remove(*selectedNoteIds.toLongArray())
                            clearSelection()
                            refreshNotes()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                 ExtendedFloatingActionButton(
                     onClick = { showQuickAdd = true },
                     icon = { Icon(Icons.Default.Add, "Add") },
                     text = { Text("New") }
                 )
            }
        },
        bottomBar = {
            MainBottomNavigation(
                currentSection = currentSection,
                onSectionSelected = { section ->
                    when (section) {
                        MainNavSection.LIBRARY -> {
                            currentSection = section
                            selectedNotebookId = null
                        }
                        MainNavSection.SEARCH -> {
                            currentSection = section
                            // Trigger search
                            isSearchMode = true
                            val scanner = GmsDocumentScanning.getClient(scannerManager.options)
                            scanner.getStartScanIntent(context as android.app.Activity)
                                .addOnSuccessListener { intentSender ->
                                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                }
                                .addOnFailureListener { it.printStackTrace() }
                        }
                        MainNavSection.NOTEBOOKS -> {
                            currentSection = section
                            // Open drawer to show notebook hierarchy
                            scope.launch { drawerState.open() }
                        }
                        MainNavSection.SETTINGS -> {
                            currentSection = section
                            showSettings = true
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            
            if (showAddCollectionDialog && isSelectionMode) {
                // Reuse logic or create specialized dialog for batch move
                 AlertDialog(
                    onDismissRequest = { showAddCollectionDialog = false },
                    title = { Text("Move ${selectedNoteIds.size} notes to Collection") },
                    text = {
                        Column {
                            collections.forEach { col ->
                                TextButton(onClick = { 
                                     val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                     val notesToUpdate = box.get(selectedNoteIds.toLongArray())
                                     notesToUpdate.forEach { it.collection = col }
                                     box.put(notesToUpdate)
                                     refreshNotes()
                                     clearSelection()
                                     showAddCollectionDialog = false 
                                }) {
                                    Text(col)
                                }
                            }
                            Divider()
                            OutlinedTextField(
                                value = newCollectionName,
                                onValueChange = { newCollectionName = it },
                                label = { Text("New Collection") }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newCollectionName.isNotBlank()) {
                                 val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                 val notesToUpdate = box.get(selectedNoteIds.toLongArray())
                                 notesToUpdate.forEach { it.collection = newCollectionName }
                                 box.put(notesToUpdate)
                                 refreshNotes()
                                 clearSelection()
                            }
                            showAddCollectionDialog = false
                        }) {
                            Text("Create & Move")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddCollectionDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ImageSearch, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No notes found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap 'Scan Note' to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Google Keep style masonry layout
                AdaptiveNoteGrid(
                    notes = filteredNotes,
                    layoutStyle = GridLayoutStyle.STAGGERED,
                    modifier = Modifier.fillMaxSize()
                ) { note ->
                        val isSelected = selectedNoteIds.contains(note.id)
                        val haptic = LocalHapticFeedback.current

                        SwipeableNoteCard(
                            onSwipeToFavorite = {
                                val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                note.isFavorite = !note.isFavorite
                                box.put(note)
                                refreshNotes()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onSwipeToDelete = {
                                val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                box.remove(note)
                                refreshNotes()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        ) {
                            NoteCard(
                                note = note,
                                score = null,
                                isSelected = isSelected,
                                onDelete = {
                                    val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                    box.remove(note)
                                    refreshNotes()
                                },
                                onMoveToCollection = { newCol ->
                                     val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                     note.collection = newCol
                                     box.put(note)
                                     refreshNotes()
                                },
                                 onClick = {
                                      if (isSelectionMode) {
                                          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                          toggleSelection(note.id)
                                      } else {
                                          selectedNoteForDetails = note
                                      }
                                 },
                                onLongClick = {
                                     if (!isSelectionMode) {
                                         haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                         enterSelectionMode(note.id)
                                     }
                                },
                                onToggleFavorite = {
                                    val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                    note.isFavorite = !note.isFavorite
                                    box.put(note)
                                    refreshNotes()
                                },
                                onTogglePin = {
                                    val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                                    note.isPinned = !note.isPinned
                                    note.pinnedAt = if (note.isPinned) System.currentTimeMillis() else null
                                    box.put(note)
                                    refreshNotes()
                                },
                                onManageTags = {
                                    // Open tag selector for this note
                                    // This will be handled via note details screen for now
                                    selectedNoteForDetails = note
                                },
                                availableCollections = collections
                            )
                        }
                    }
                }
            }
        }
    }

        // Search Results Overlay (3/4 screen height)
        if (showSearchResults) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.Center)
            ) {
                CompactSearchPage(
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onDismiss = {
                        showSearchResults = false
                        isSearching = false
                    },
                    onThresholdsChanged = { clip, trocr, mlkit, colorbased ->
                        // Thresholds changed - results are filtered in the UI
                    }
                )
            }
        }

        // Sort & Filter Bottom Sheet
        if (showSortFilter) {
        SortFilterBottomSheet(
            currentSort = currentSort,
            currentFilters = currentFilters,
            onSortChanged = { newSort ->
                currentSort = newSort
            },
            onFilterToggled = { filter ->
                currentFilters = if (filter == NoteFilterOption.ALL) {
                    setOf(NoteFilterOption.ALL)
                } else {
                    val newFilters = currentFilters.toMutableSet()
                    if (newFilters.contains(filter)) {
                        newFilters.remove(filter)
                        if (newFilters.isEmpty()) newFilters.add(NoteFilterOption.ALL)
                    } else {
                        newFilters.remove(NoteFilterOption.ALL)
                        newFilters.add(filter)
                    }
                    newFilters
                }
            },
            onDismiss = { showSortFilter = false }
        )
    }

    // Quick Add Bottom Sheet (Google Keep style)
    if (showQuickAdd) {
        QuickAddSheet(
            onScanNote = {
                isSearchMode = false
                val scanner = GmsDocumentScanning.getClient(scannerManager.options)
                scanner.getStartScanIntent(context as android.app.Activity)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener { it.printStackTrace() }
            },
            onImportImage = {
                imagePickerLauncher.launch("image/*")
            },
            onQuickNote = {
                showQuickTextNote = true
            },
            onQuickTask = {
                // TODO: Implement quick task list
            },
            onDismiss = { showQuickAdd = false }
        )
    }

    // Quick Text Note Dialog
    if (showQuickTextNote) {
        QuickTextNoteDialog(
            currentNotebookId = selectedNotebookId,
            onDismiss = { showQuickTextNote = false },
            onSave = { title, content, color, notebookId, tagIds ->
                // Create a text-only note
                scope.launch {
                    try {
                        showProgress = true
                        progressTitle = "Saving Note"

                        val note = NoteEntity(
                            title = title,
                            imagePath = "",  // No image for text notes
                            timestamp = System.currentTimeMillis(),
                            mlKitText = content,  // Store text content
                            collection = selectedCollection ?: "Scratchpad",
                            notebookId = notebookId,
                            color = color  // Note color
                        )

                        // Generate text embedding if content is not empty
                        if (content.isNotBlank()) {
                            val textEmbedding = scannerManager.generateTextEmbedding(content)
                            note.mlKitTextEmbedding = textEmbedding
                            note.textEmbedding = textEmbedding
                        }

                        val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                        val noteId = box.put(note)

                        // Add tags to the note
                        if (tagIds.isNotEmpty()) {
                            val tagRepository = TagRepository()
                            tagIds.forEach { tagId ->
                                tagRepository.addTagToNote(noteId, tagId)
                            }
                        }

                        // Refresh notes list
                        refreshNotes()

                        showProgress = false
                        showQuickTextNote = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showProgress = false
                    }
                }
            }
        )
    }

    // Notebook Management Sheet
    if (showNotebookManagement) {
        NotebookManagementSheet(
            onDismiss = { showNotebookManagement = false },
            onNotebookSelected = { notebook ->
                selectedNotebookId = notebook.id
                refreshNotes()
            }
        )
    }
    }  // Close ModalNavigationDrawer
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    score: Double? = null,
    imageScore: Double? = null,
    trocrScore: Double? = null,
    mlKitTextScore: Double? = null,
    colorBasedTextScore: Double? = null,
    isSelected: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onMoveToCollection: ((String) -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    availableCollections: List<String> = emptyList(),
    onToggleFavorite: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onManageTags: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    if (showCollectionDialog && onMoveToCollection != null) {
        AlertDialog(
            onDismissRequest = { showCollectionDialog = false },
            title = { Text("Move to Collection") },
            text = {
                Column {
                    availableCollections.forEach { col ->
                        TextButton(onClick = { 
                            onMoveToCollection(col)
                            showCollectionDialog = false 
                        }) {
                            Text(col)
                        }
                    }
                    Divider()
                    OutlinedTextField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it },
                        label = { Text("New Collection") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCollectionName.isNotBlank()) {
                         onMoveToCollection(newCollectionName)
                    }
                    showCollectionDialog = false
                }) {
                    Text("Create & Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCollectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ElevatedCard(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else if (note.color != null) {
            // Google Keep style: apply note color
            CardDefaults.elevatedCardColors(
                containerColor = androidx.compose.ui.graphics.Color(note.color!!).copy(alpha = 0.15f)
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (note.imagePath.isNotEmpty()) {
                AsyncImage(
                    model = note.imagePath,
                    contentDescription = "Note Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                   Text(
                       note.title,
                       style = MaterialTheme.typography.titleMedium,
                       maxLines = 1,
                       overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                       modifier = Modifier.weight(1f).padding(end = 4.dp)
                   )

                   Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                       // Favorite Icon
                       if (onToggleFavorite != null) {
                           IconButton(
                               onClick = onToggleFavorite,
                               modifier = Modifier.size(24.dp)
                           ) {
                               Icon(
                                   if (note.isFavorite) Icons.Default.Star else Icons.Default.Star,
                                   contentDescription = if (note.isFavorite) "Remove from favorites" else "Add to favorites",
                                   tint = if (note.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                   modifier = Modifier.size(18.dp)
                               )
                           }
                       }

                       if (onMoveToCollection != null) {
                           IconButton(
                               onClick = { showCollectionDialog = true },
                               modifier = Modifier.size(24.dp)
                           ) {
                               Icon(
                                   Icons.Default.FolderOpen,
                                   contentDescription = "Move",
                                   tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                   modifier = Modifier.size(18.dp)
                               )
                           }
                       }
                   }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Timestamp
                val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(note.timestamp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Task Progress (Google Keep style)
                val totalTasks = note.taskTotalCount ?: 0
                val completedTasks = note.taskCompletedCount ?: 0
                if (note.noteType == "TASK" && totalTasks > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { completedTasks.toFloat() / totalTasks.toFloat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp),
                        )
                        Text(
                            text = "$completedTasks/$totalTasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (note.collection != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SuggestionChip(
                        onClick = { },
                        label = { Text(note.collection!!, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                // Display tags
                if (onManageTags != null) {
                    val tagRepository = remember { TagRepository() }
                    val noteTags = remember(note.id) { tagRepository.getTagsForNote(note.id) }

                    if (noteTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            noteTags.take(3).forEach { tag ->  // Show max 3 tags on card
                                TagChip(
                                    tag = tag,
                                    onClick = onManageTags
                                )
                            }
                            if (noteTags.size > 3) {
                                SuggestionChip(
                                    onClick = onManageTags,
                                    label = { Text("+${noteTags.size - 3}", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }

                if (score != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Overall: %.3f".format(score),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    // Show individual embedding scores (cosine distance: 0.0 = identical, 2.0 = opposite)
                    if (imageScore != null) {
                        Text(
                            text = "  Image: %.3f".format(imageScore),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (trocrScore != null) {
                        Text(
                            text = "  TrOCR: %.3f".format(trocrScore),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (mlKitTextScore != null) {
                        Text(
                            text = "  ML Kit: %.3f".format(mlKitTextScore),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (colorBasedTextScore != null) {
                        Text(
                            text = "  ColorOCR: %.3f".format(colorBasedTextScore),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                if (onDelete != null) {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                        }
                    }
                 }
            }
        }
        
        // Context Menu for Delete (handled via long press -> selection mode usually, but if we want per-card menu)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
             if (isSelected) {
                 Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
             }
        }
    }
}

@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..0.5f,
            steps = 44
        )
    }
}

@Composable
fun ProcessingProgressDialog(
    title: String,
    state: ScannerManager.ProcessingState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (state.step == ScannerManager.ProcessingStep.COMPLETE ||
                state.step == ScannerManager.ProcessingStep.ERROR) {
                onDismiss()
            }
        },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Progress indicator
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Progress percentage
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Current step
                Text(
                    text = when (state.step) {
                        ScannerManager.ProcessingStep.IDLE -> "Idle"
                        ScannerManager.ProcessingStep.LOADING_IMAGE -> "Loading Image"
                        ScannerManager.ProcessingStep.GENERATING_IMAGE_EMBEDDING -> "Generating Image Embeddings"
                        ScannerManager.ProcessingStep.GENERATING_TROCR_EMBEDDING -> "Generating Visual Embeddings"
                        ScannerManager.ProcessingStep.RUNNING_OCR -> "Running OCR"
                        ScannerManager.ProcessingStep.GENERATING_TEXT_EMBEDDING -> "Generating Text Embeddings"
                        ScannerManager.ProcessingStep.SAVING -> "Saving"
                        ScannerManager.ProcessingStep.COMPLETE -> "Complete"
                        ScannerManager.ProcessingStep.ERROR -> "Error"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status message
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.step == ScannerManager.ProcessingStep.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
        confirmButton = {
            if (state.step == ScannerManager.ProcessingStep.COMPLETE ||
                state.step == ScannerManager.ProcessingStep.ERROR) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
