package com.example.notes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.edit
import com.example.notes.ml.ImageEmbedderHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import com.example.notes.data.NoteEntity
import com.example.notes.data.ObjectBoxStore
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

    // Progress state
    var showProgress by remember { mutableStateOf(false) }
    var processingState by remember { mutableStateOf(ScannerManager.ProcessingState()) }
    var progressTitle by remember { mutableStateOf("Processing") }
    
    // Simplified way to get notes and unique collections
    fun refreshNotes() {
        val box = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
        notes = box.all
    }

    LaunchedEffect(Unit) {
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
    var showAddCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    
    // Smart Scan State
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateNote by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingScanUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    // Note Details State
    var selectedNoteForDetails by remember { mutableStateOf<NoteEntity?>(null) }
    
    // Filtered Notes
    val filteredNotes by remember(notes, selectedCollection) {
        derivedStateOf {
            if (selectedCollection == null) notes
            else notes.filter { it.collection == selectedCollection }
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

    // Dialogs that should always be available
    if (selectedNoteForDetails != null) {
        val note = selectedNoteForDetails!!
        AlertDialog(
            onDismissRequest = { selectedNoteForDetails = null },
            title = { Text(note.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (note.imagePath.isNotEmpty()) {
                        AsyncImage(
                            model = note.imagePath,
                            contentDescription = "Note Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Collection: ${note.collection ?: "Uncategorized"}", style = MaterialTheme.typography.labelMedium)
                    val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.timestamp))
                    Text("Date: $dateStr", style = MaterialTheme.typography.labelMedium)
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text("OCR Text Content:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (!note.ocrText.isNullOrBlank()) {
                            Text(
                                note.ocrText!!, 
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                    } else {
                        Text(
                            "No text detected.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Debug Info:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("ID: ${note.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("Image Embedding: ${if (note.embedding != null) "Yes (${note.embedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("TrOCR Embedding: ${if (note.trocrEmbedding != null) "Yes (${note.trocrEmbedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("ML Kit Text Embedding: ${if (note.mlKitTextEmbedding != null) "Yes (${note.mlKitTextEmbedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("ColorBased Text Embedding: ${if (note.colorBasedTextEmbedding != null) "Yes (${note.colorBasedTextEmbedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNoteForDetails = null }) {
                    Text("Close")
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
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettings = false }) {
                            Text("Done")
                        }
                    }
                )
            }

            Scaffold(
        topBar = {
            if (!isSelectionMode) {
                 TopAppBar(
                    title = { Text("PaperNotes") },
                    actions = {
                        // Search Action
                        IconButton(onClick = {
                             isSearchMode = true
                             val scanner = GmsDocumentScanning.getClient(scannerManager.options)
                             scanner.getStartScanIntent(context as android.app.Activity)
                                .addOnSuccessListener { intentSender ->
                                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                }
                                .addOnFailureListener { it.printStackTrace() }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Note")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                     onClick = {
                        isSearchMode = false
                        val scanner = GmsDocumentScanning.getClient(scannerManager.options)
                        scanner.getStartScanIntent(context as android.app.Activity)
                            .addOnSuccessListener { intentSender ->
                                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            }
                            .addOnFailureListener {
                                // Handle error
                                it.printStackTrace()
                            }
                     },
                     icon = { Icon(Icons.Default.Add, "Scan") },
                     text = { Text("Scan Note") }
                 )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            
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
            } else if (!isSelectionMode) {
                 Text("My Notes", style = MaterialTheme.typography.headlineMedium)
            }
            
            // Collection Filter Row
            if (!isSelectionMode) {
                ScrollableTabRow(
                    selectedTabIndex = if (selectedCollection == null) 0 else collections.indexOf(selectedCollection) + 1,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Tab(
                        selected = selectedCollection == null,
                        onClick = { selectedCollection = null },
                        text = { Text("All") }
                    )
                    collections.forEach { collection ->
                        Tab(
                            selected = selectedCollection == collection,
                            onClick = { selectedCollection = collection },
                            text = { Text(collection) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNotes) { note ->
                        val isSelected = selectedNoteIds.contains(note.id)
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
                                      toggleSelection(note.id)
                                  } else {
                                      selectedNoteForDetails = note
                                  }
                             },
                            onLongClick = {
                                 if (!isSelectionMode) {
                                     enterSelectionMode(note.id)
                                 }
                            },
                            availableCollections = collections
                        )
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
    }
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
    availableCollections: List<String> = emptyList()
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
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        colors = if (isSelected) CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.elevatedCardColors(),
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
                   
                   if (onMoveToCollection != null) {
                       IconButton(
                           onClick = { showCollectionDialog = true },
                           modifier = Modifier.size(20.dp)
                       ) {
                           Icon(
                               Icons.Default.FolderOpen, 
                               contentDescription = "Move",
                               tint = MaterialTheme.colorScheme.onSurfaceVariant
                           )
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
