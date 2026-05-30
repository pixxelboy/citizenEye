package com.citizeneye

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Ballot
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.citizeneye.data.AssembleeOfficialVoteEnrichmentRepository
import com.citizeneye.data.CitizenEyeRepository
import com.citizeneye.data.CitizenInputValidator
import com.citizeneye.data.Depute
import com.citizeneye.data.DeputeMatch
import com.citizeneye.data.DefaultVoteDetailRepository
import com.citizeneye.data.DeputyStats
import com.citizeneye.data.LocationPreview
import com.citizeneye.data.LookupState
import com.citizeneye.data.PublicDataCache
import com.citizeneye.data.PoliticalFamilies
import com.citizeneye.data.StaticCitizenEyeDatasetClient
import com.citizeneye.data.TopicVotingSummary
import com.citizeneye.data.UpcomingVote
import com.citizeneye.data.UpcomingVoteStatus
import com.citizeneye.data.VoteConcern
import com.citizeneye.data.VotePosition
import com.citizeneye.data.Vote
import com.citizeneye.data.classifyVoteSubjectType
import com.citizeneye.data.formatPositionInContext
import com.citizeneye.data.formatVoteResultLabel
import com.citizeneye.ui.CitizenEyeLoader
import com.citizeneye.ui.theme.CitizenEyeTheme
import com.citizeneye.update.AppUpdateManager
import com.citizeneye.update.UpdateCheckResult
import com.citizeneye.update.UpdateInstallResult
import com.citizeneye.update.UpdateManifest
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class MainTab(val label: String) {
    HOME("Accueil"),
    UPCOMING("À suivre"),
    HISTORY("Votes"),
    DEPUTY("Député")
}

private sealed interface UpcomingVotesUiState {
    data object Loading : UpcomingVotesUiState
    data class Success(val votes: List<UpcomingVote>) : UpcomingVotesUiState
    data class Error(val message: String) : UpcomingVotesUiState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        val repository = CitizenEyeRepository.create(applicationContext)
        val publicDataCache = PublicDataCache(java.io.File(applicationContext.filesDir, "public-data"))
        val staticDatasetClient = StaticCitizenEyeDatasetClient(java.io.File(applicationContext.filesDir, "static-public-data"))
        setContent { CitizenEyeTheme { CitizenEyeApp(repository = repository, publicDataCache = publicDataCache, staticDatasetClient = staticDatasetClient) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenEyeApp(repository: CitizenEyeRepository = CitizenEyeRepository(), publicDataCache: PublicDataCache? = null, staticDatasetClient: StaticCitizenEyeDatasetClient? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<LookupState>(LookupState.Idle) }
    var showingStats by rememberSaveable { mutableStateOf(false) }
    var showingGroupDetails by rememberSaveable { mutableStateOf(false) }
    var selectedVote by remember { mutableStateOf<Vote?>(null) }
    var selectedUpcomingVote by remember { mutableStateOf<UpcomingVote?>(null) }
    var activeTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var upcomingVotesUiState by remember { mutableStateOf<UpcomingVotesUiState>(UpcomingVotesUiState.Loading) }
    var voteDetailUiState by remember { mutableStateOf<VoteDetailUiState>(VoteDetailUiState.Loading) }
    val voteDetailRepository = remember(publicDataCache, staticDatasetClient) {
        DefaultVoteDetailRepository(
            officialEnrichmentRepository = AssembleeOfficialVoteEnrichmentRepository(publicDataCache, staticDatasetClient)
        )
    }
    var preview by remember { mutableStateOf<LocationPreview?>(null) }
    var previewLoading by rememberSaveable { mutableStateOf(false) }
    var geoLoading by rememberSaveable { mutableStateOf(false) }
    var geolocationPreviewQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var inlineError by rememberSaveable { mutableStateOf<String?>(null) }
    var updatePrompt by remember { mutableStateOf<UpdateManifest?>(null) }
    var updateDownloading by rememberSaveable { mutableStateOf(false) }
    var updateMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val updateManager = remember(context) { AppUpdateManager(context.applicationContext) }

    fun runLookup() {
        val currentQuery = query.trim()
        state = LookupState.Loading("Recherche de la commune…")
        showingStats = false
        selectedVote = null
        selectedUpcomingVote = null
        showingGroupDetails = false
        activeTab = MainTab.HOME
        scope.launch { state = repository.lookup(currentQuery) }
    }

    fun loadVotes(query: String, commune: com.citizeneye.data.Commune, depute: Depute) {
        state = LookupState.Loading("Préparation des résultats…")
        showingStats = false
        selectedVote = null
        selectedUpcomingVote = null
        showingGroupDetails = false
        activeTab = MainTab.HOME
        scope.launch { state = repository.loadDeputyVotes(query, commune, depute) }
    }

    fun loadVotes(selection: LookupState.NeedSelection, depute: Depute) {
        loadVotes(selection.query, selection.commune, depute)
    }

    fun openVoteDetails(vote: Vote, depute: Depute) {
        selectedVote = vote
        voteDetailUiState = VoteDetailUiState.Loading
        scope.launch {
            voteDetailUiState = runCatching { voteDetailRepository.getVoteDetail(vote, depute) }
                .fold(
                    onSuccess = { VoteDetailUiState.Success(it) },
                    onFailure = { VoteDetailUiState.Error("Impossible de charger le détail du vote. Réessayer.") }
                )
        }
    }

    fun retryVoteDetails() {
        val vote = selectedVote ?: return
        val current = state as? LookupState.Loaded ?: return
        openVoteDetails(vote, current.match.depute)
    }

    fun openExternalUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun openEmailDraft(to: String, subject: String, body: String) {
        val uri = Uri.parse("mailto:${Uri.encode(to)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        val intent = Intent(Intent.ACTION_SENDTO).apply { data = uri }
        try {
            context.startActivity(Intent.createChooser(intent, "Choisir une application mail"))
        } catch (_: ActivityNotFoundException) {
            inlineError = "Aucune application mail n’a été trouvée. Vous pouvez copier le message."
        }
    }

    fun confirmLocation() {
        val currentPreview = preview
        if (currentPreview != null && currentPreview.deputies.isNotEmpty()) {
            showingStats = false
            inlineError = null
            if (currentPreview.deputies.size == 1) {
                loadVotes(currentPreview.query, currentPreview.commune, currentPreview.deputies.first())
            } else {
                state = LookupState.NeedSelection(
                    query = currentPreview.query,
                    commune = currentPreview.commune,
                    deputies = currentPreview.deputies,
                    reason = "Confirmez votre circonscription. Le code postal ou la ville peut couvrir plusieurs députés."
                )
            }
        } else {
            runLookup()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            scope.launch {
                geoLoading = true
                inlineError = null
                val detected = detectPostalOrCityFromDeviceLocation(context)
                if (detected != null) {
                    geolocationPreviewQuery = detected.query
                    query = detected.query
                    previewLoading = true
                    preview = runCatching { repository.previewPreciseLocation(detected.query, detected.latitude, detected.longitude) }.getOrNull()
                    if (preview == null) inlineError = "Position détectée, mais aucune commune exploitable trouvée. Vous pouvez modifier manuellement."
                    previewLoading = false
                } else {
                    inlineError = "Position indisponible. Entrez ou corrigez le code postal manuellement."
                }
                geoLoading = false
            }
        } else {
            inlineError = "Autorisez la localisation ou entrez votre code postal manuellement."
        }
    }

    fun startGeolocationAutocomplete() {
        if (context.hasLocationPermission()) {
            scope.launch {
                geoLoading = true
                inlineError = null
                val detected = detectPostalOrCityFromDeviceLocation(context)
                if (detected != null) {
                    geolocationPreviewQuery = detected.query
                    query = detected.query
                    previewLoading = true
                    preview = runCatching { repository.previewPreciseLocation(detected.query, detected.latitude, detected.longitude) }.getOrNull()
                    if (preview == null) inlineError = "Position détectée, mais aucune commune exploitable trouvée. Vous pouvez modifier manuellement."
                    previewLoading = false
                } else {
                    inlineError = "Position indisponible. Entrez ou corrigez le code postal manuellement."
                }
                geoLoading = false
            }
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    LaunchedEffect(query) {
        val cleanQuery = query.trim()
        inlineError = null
        if (!CitizenInputValidator.canSearch(cleanQuery)) {
            preview = null
            previewLoading = false
            return@LaunchedEffect
        }
        if (geolocationPreviewQuery == cleanQuery) {
            return@LaunchedEffect
        }
        delay(450)
        previewLoading = true
        preview = runCatching { repository.previewLocation(cleanQuery) }.getOrNull()
        previewLoading = false
    }

    LaunchedEffect(Unit) {
        when (val result = updateManager.checkForUpdate()) {
            is UpdateCheckResult.UpdateAvailable -> updatePrompt = result.manifest
            else -> Unit
        }
    }

    LaunchedEffect(activeTab) {
        if (activeTab == MainTab.UPCOMING && upcomingVotesUiState !is UpcomingVotesUiState.Success) {
            upcomingVotesUiState = UpcomingVotesUiState.Loading
            upcomingVotesUiState = runCatching { repository.fetchUpcomingVotes() }
                .fold(
                    onSuccess = { UpcomingVotesUiState.Success(it) },
                    onFailure = { UpcomingVotesUiState.Error("Impossible de charger les textes à venir depuis les sources officielles.") }
                )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val title = when {
                        selectedVote != null -> "Comprendre ce vote"
                        selectedUpcomingVote != null -> "Texte à venir"
                        showingGroupDetails -> "Groupe politique"
                        showingStats -> "Profil député"
                        activeTab == MainTab.UPCOMING -> "À suivre"
                        activeTab == MainTab.HISTORY -> "Votes"
                        activeTab == MainTab.DEPUTY -> "Député"
                        else -> "CitizenEye"
                    }
                    Text(title, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    when {
                        selectedVote != null -> TextButton(onClick = { selectedVote = null }) { Text("Retour") }
                        selectedUpcomingVote != null -> TextButton(onClick = { selectedUpcomingVote = null }) { Text("Retour") }
                        showingGroupDetails -> TextButton(onClick = { showingGroupDetails = false }) { Text("Retour") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (state is LookupState.Loaded) {
                CitizenEyeBottomNavigation(activeTab = activeTab, onTabSelected = { tab ->
                    activeTab = tab
                    selectedVote = null
                    selectedUpcomingVote = null
                    showingGroupDetails = false
                    showingStats = false
                })
            }
        }
    ) { padding ->
        Surface(Modifier.padding(padding).fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val current = state) {
                LookupState.Idle -> OnboardingScreen(
                    query = query,
                    error = inlineError,
                    loading = false,
                    preview = preview,
                    previewLoading = previewLoading,
                    geoLoading = geoLoading,
                    onQueryChange = { geolocationPreviewQuery = null; query = it },
                    onGeolocate = ::startGeolocationAutocomplete,
                    onSubmit = ::confirmLocation
                )
                is LookupState.Error -> OnboardingScreen(
                    query = query,
                    error = current.message,
                    loading = false,
                    preview = preview,
                    previewLoading = previewLoading,
                    geoLoading = geoLoading,
                    onQueryChange = { geolocationPreviewQuery = null; query = it; state = LookupState.Idle },
                    onGeolocate = ::startGeolocationAutocomplete,
                    onSubmit = ::confirmLocation
                )
                is LookupState.Loading -> LoadingScreen(current.message)
                is LookupState.NeedSelection -> DeputySelectionScreen(
                    selection = current,
                    onSelect = { loadVotes(current, it) },
                    onReset = { state = LookupState.Idle }
                )
                is LookupState.Loaded -> if (selectedUpcomingVote != null) {
                    UpcomingVoteDetailScreen(
                        vote = selectedUpcomingVote!!,
                        depute = current.match.depute,
                        onOpenSource = ::openExternalUrl,
                        onOpenEmailDraft = { upcoming, depute ->
                            val email = depute.email
                            if (email == null) {
                                inlineError = "Aucune adresse email officielle n’est disponible pour ce député."
                            } else {
                                openEmailDraft(
                                    to = email,
                                    subject = "À propos du texte parlementaire à venir : ${upcoming.title}",
                                    body = "Bonjour,\n\nJe vous contacte au sujet du texte parlementaire suivant : ${upcoming.title}.\n\nSource officielle : ${upcoming.sourceUrl}\n\nJe souhaite vous faire part de mon attention sur ce sujet avant les prochaines étapes parlementaires.\n\nCordialement,"
                                )
                            }
                        }
                    )
                } else if (selectedVote != null) {
                    VoteDetailScreen(
                        voteDetailUiState = voteDetailUiState,
                        deputyEmail = current.match.depute.email,
                        onRetour = { selectedVote = null },
                        onOuvrirUrl = ::openExternalUrl,
                        onOpenEmailDraft = ::openEmailDraft,
                        onRetry = ::retryVoteDetails
                    )
                } else if (showingGroupDetails) {
                    PoliticalGroupDetailScreen(
                        match = current.match,
                        onOpenSource = ::openExternalUrl
                    )
                } else {
                    when (activeTab) {
                        MainTab.HOME -> HomeScreen(
                            match = current.match,
                            onOpenUpcoming = { activeTab = MainTab.UPCOMING },
                            onOpenHistory = { activeTab = MainTab.HISTORY },
                            onOpenDeputy = { activeTab = MainTab.DEPUTY },
                            onReset = { state = LookupState.Idle; query = ""; showingStats = false; activeTab = MainTab.HOME }
                        )
                        MainTab.UPCOMING -> UpcomingVotesScreen(
                            uiState = upcomingVotesUiState,
                            onOpenDetail = { selectedUpcomingVote = it },
                            onRetry = { upcomingVotesUiState = UpcomingVotesUiState.Loading; scope.launch { upcomingVotesUiState = runCatching { repository.fetchUpcomingVotes() }.fold(onSuccess = { UpcomingVotesUiState.Success(it) }, onFailure = { UpcomingVotesUiState.Error("Impossible de charger les textes à venir depuis les sources officielles.") }) } }
                        )
                        MainTab.HISTORY -> VoteHistoryScreen(
                            match = current.match,
                            onOuvrirVoteDetails = { openVoteDetails(it, current.match.depute) },
                            onLoadMoreVotes = { state = LookupState.Loaded(current.match.withMoreVisibleVotes()) }
                        )
                        MainTab.DEPUTY -> DeputyProfileScreen(
                            match = current.match,
                            onOpenGroup = { showingGroupDetails = true },
                            onReset = { state = LookupState.Idle; query = ""; showingStats = false; showingGroupDetails = false; activeTab = MainTab.HOME }
                        )
                    }
                }
            }
        }
    }

    updatePrompt?.let { manifest ->
        UpdateAvailableDialog(
            manifest = manifest,
            downloading = updateDownloading,
            message = updateMessage,
            onLater = {
                updatePrompt = null
                updateMessage = null
            },
            onUpdate = {
                scope.launch {
                    updateDownloading = true
                    updateMessage = "Téléchargement de l’APK…"
                    when (updateManager.downloadAndStartInstall(manifest)) {
                        UpdateInstallResult.InstallerStarted -> {
                            updateMessage = "Confirmez l’installation dans Android."
                            updatePrompt = null
                        }
                        UpdateInstallResult.UnknownSourcesPermissionRequired -> {
                            updateMessage = "Autorisez CitizenEye à installer des APK, puis relancez la mise à jour."
                        }
                        is UpdateInstallResult.Failed -> {
                            updateMessage = "Mise à jour impossible. Réessayez plus tard."
                        }
                    }
                    updateDownloading = false
                }
            }
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    downloading: Boolean,
    message: String?,
    onLater: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        title = { Text("Nouvelle version disponible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${manifest.versionName}", fontWeight = FontWeight.SemiBold)
                manifest.releaseNotes.take(4).forEach { note -> Text("• $note") }
                Text("Android vous demandera de confirmer l’installation de l’APK.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                if (downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !downloading) { Text("Mettre à jour") }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !downloading) { Text(if (manifest.mandatory) "Continuer" else "Plus tard") }
        }
    )
}

@Composable
private fun OnboardingScreen(
    query: String,
    error: String?,
    loading: Boolean,
    preview: LocationPreview?,
    previewLoading: Boolean,
    geoLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onGeolocate: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(Modifier.height(24.dp))
            Text("Suivre votre député", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Code postal ou ville. Résultat immédiat.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                lineHeight = 23.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("Sources : Assemblée nationale · geo.api.gouv.fr", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Code postal ou ville") },
                placeholder = { Text("92000 ou Nanterre") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                supportingText = { Text(error ?: if (previewLoading) "Recherche…" else "Exemple : 92000 ou Nanterre") },
                isError = error != null
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onGeolocate, enabled = !geoLoading && !loading) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (geoLoading) "Localisation…" else "Utiliser ma localisation")
            }
            Text("Position utilisée seulement pour la circonscription.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            preview?.let { LocationPreviewCard(it) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSubmit, enabled = query.trim().length >= 2 && !loading && !previewLoading && !geoLoading, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (preview != null) "Valider cette localisation" else "Trouver mon député")
            }
        }
    }
}

@Composable
private fun LocationPreviewCard(preview: LocationPreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (preview.preciseBoundary != null) "Circonscription détectée par GPS" else "Localisation déduite", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(preview.locationLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(preview.representativeHint, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            if (preview.preciseBoundary != null) {
                Spacer(Modifier.height(6.dp))
                Text("Confirmez quand même : la position peut venir d’un lieu récent, pas forcément de votre domicile.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else if (preview.requiresDeputyChoice) {
                Spacer(Modifier.height(6.dp))
                Text("Vous choisirez la bonne circonscription après validation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CitizenEyeLoader(
                size = 88.dp,
                label = message.ifBlank { "Recherche de votre circonscription…" }
            )
            Spacer(Modifier.height(8.dp))
            Text("CitizenEye vérifie les sources publiques disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Vous pourrez confirmer si plusieurs circonscriptions sont possibles.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DeputySelectionScreen(selection: LookupState.NeedSelection, onSelect: (Depute) -> Unit, onReset: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Plusieurs circonscriptions possibles", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(selection.commune.name, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Cette commune peut correspondre à plusieurs circonscriptions. Choisissez votre député si vous le connaissez, ou utilisez votre localisation pour une recherche plus précise.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            TextButton(onClick = onReset) { Text("Changer de recherche") }
        }
        items(selection.deputies) { depute ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(depute) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(depute.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(depute.constituencyLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(depute.displayPoliticalGroupShort, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    depute.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onSelect(depute) }, modifier = Modifier.fillMaxWidth()) { Text("Choisir ce député") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HomeScreen(match: DeputeMatch, onOpenUpcoming: () -> Unit, onOpenHistory: () -> Unit, onOpenDeputy: () -> Unit, onReset: () -> Unit) {
    val stats = match.legislatureStats
    val allTopicSummaries = match.topicVotingSummaries
    val topTopics = allTopicSummaries.take(5)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("${match.commune.name} · ${match.depute.constituencyNumber}e circonscription", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(match.depute.name, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text(match.depute.displayPoliticalGroupShort, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        item { CompactDeputyCard(match, onOpenDeputy) }
        item { TopicVotingSection(topTopics) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetric(allTopicSummaries.size.toString(), "sujets votés", Modifier.weight(1f))
                DashboardMetric(match.totalLegislatureVotes.toString(), "votes récents", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpenUpcoming, modifier = Modifier.weight(1f).height(56.dp)) { Text("À suivre") }
                Button(onClick = onOpenHistory, modifier = Modifier.weight(1f).height(56.dp)) { Text("Votes") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatMetricCard("Participation", "${stats.participationPercent}%", "scrutins", Modifier.weight(1f))
                StatMetricCard("Alignement", "${stats.groupAlignmentPercent}%", "avec groupe", Modifier.weight(1f))
            }
        }
        item { TextButton(onClick = onReset) { Text("Changer de localisation") } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TopicVotingSection(topics: List<TopicVotingSummary>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sujets les plus votés", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (topics.isEmpty()) {
                Text("Aucun scrutin public disponible pour établir les sujets votés.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            } else {
                topics.forEach { summary -> TopicVotingRow(summary) }
            }
            Text(
                "Les sujets sont attribués depuis les titres officiels des dossiers parlementaires, puis hérités par les votes liés. Aucun score politique ni classement par IA.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun TopicVotingRow(summary: TopicVotingSummary) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(summary.topic.label, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text("${summary.totalVotes} votes", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(summary.dominantPositionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "Pour ${summary.pour} · Contre ${summary.contre} · Abst. ${summary.abstention}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DashboardMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VoteHistoryScreen(match: DeputeMatch, onOuvrirVoteDetails: (Vote) -> Unit, onLoadMoreVotes: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Votes", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text("${match.recentVotes.size}/${match.totalLegislatureVotes} scrutins", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (match.recentVotes.isEmpty()) {
            item { InfoCard("Aucun vote", "Aucun scrutin public disponible.") }
        } else {
            items(match.recentVotes) { vote -> VoteCard(vote, onOuvrirDetails = { onOuvrirVoteDetails(vote) }) }
            if (match.hasMoreVotes) {
                item { Button(onClick = onLoadMoreVotes, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Charger 20 votes") } }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DeputyProfileScreen(match: DeputeMatch, onOpenGroup: () -> Unit, onReset: () -> Unit) {
    val stats = match.legislatureStats
    val family = PoliticalFamilies.forDeputy(match.depute)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DeputyDashboardHeader(match) }
        item { PoliticalIdentityCard(match, family.politicalFamily, onOpenGroup) }
        item { ParliamentaryActivityCard(match) }
        item { VotingBehaviourCard(stats) }
        item { VoteDistributionCard(stats) }
        item { GroupAlignmentCard(stats) }
        item { ActivityTimelineCard(match) }
        item { RecentImportantVotesCard(match) }
        item { BiographyBackgroundCard(match) }
        item { SourcesMethodologyCard(match) }
        item { TextButton(onClick = onReset) { Text("Changer de localisation") } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DeputyDashboardHeader(match: DeputeMatch) {
    val depute = match.depute
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeputyPhoto(depute, sizeDp = 88)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(depute.name, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Text("Député de la ${depute.constituencyNumber}e circonscription du ${depute.departmentName}", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GroupBadge(depute.displayPoliticalGroupShort, depute.displayPoliticalGroupFull, Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text(depute.departmentName, fontWeight = FontWeight.SemiBold)
                    Text(depute.displayRegion, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            SourceLine("Assemblée nationale · données députés actifs")
        }
    }
}

@Composable
private fun GroupBadge(shortName: String, fullName: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(12.dp)) {
            Text(shortName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(fullName, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PoliticalIdentityCard(match: DeputeMatch, politicalFamily: String, onOpenGroup: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Identité politique", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            DeputyInfoRow("Groupe politique", "${match.depute.displayPoliticalGroupFull} (${match.depute.displayPoliticalGroupShort})")
            DeputyInfoRow("Famille politique", politicalFamily)
            DeputyInfoRow("Commission", "Information non disponible depuis les sources officielles chargées.")
            DeputyInfoRow("Rôle parlementaire", "Membre")
            TextButton(onClick = onOpenGroup) { Text("Voir le groupe politique") }
            SourceLine("Assemblée nationale · configuration CitizenEye des familles politiques")
        }
    }
}

@Composable
private fun ParliamentaryActivityCard(match: DeputeMatch) {
    val stats = match.legislatureStats
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activité parlementaire", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniKpi("Votes participés", stats.participatedVotes.toString(), Modifier.weight(1f))
                MiniKpi("Questions", "N/D", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniKpi("Interventions", "N/D", Modifier.weight(1f))
                MiniKpi("Amendements", "N/D", Modifier.weight(1f))
            }
            MiniKpi("Rapports", "N/D")
            Text("N/D : information non disponible dans les sources officielles actuellement chargées par l’application.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
            SourceLine("Scrutins publics · Assemblée nationale")
        }
    }
}

@Composable
private fun MiniKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun VotingBehaviourCard(stats: DeputyStats) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comportement de vote", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Participation aux scrutins publics analysés", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${stats.participationPercent}%", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            ComparisonBar("Député", stats.participationPercent)
            ComparisonBar("Moyenne du groupe", stats.groupAverageParticipationPercent)
            ComparisonBar("Moyenne Assemblée", stats.assemblyAverageParticipationPercent)
            Text("La participation affichée concerne les scrutins publics chargés, pas la présence physique en séance.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
            SourceLine("Scrutins publics · Assemblée nationale")
        }
    }
}

@Composable
private fun ComparisonBar(label: String, percent: Int?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(percent?.let { "$it%" } ?: "N/D", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp))) {
            if (percent != null) {
                Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(99.dp)))
            }
        }
    }
}

@Composable
private fun VoteDistributionCard(stats: DeputyStats) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            VoteDistributionDonut(stats)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Répartition des votes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                PositionLegend("Pour", VotePosition.POUR, stats)
                PositionLegend("Contre", VotePosition.CONTRE, stats)
                PositionLegend("Abstention", VotePosition.ABSTENTION, stats)
                PositionLegend("Absent / non-votant", VotePosition.NON_VOTANT, stats)
                SourceLine("Scrutins publics · Assemblée nationale")
            }
        }
    }
}

@Composable
private fun VoteDistributionDonut(stats: DeputyStats) {
    val segments = listOf(
        VotePosition.POUR to Color(0xFF53667E),
        VotePosition.CONTRE to Color(0xFF7B6474),
        VotePosition.ABSTENTION to Color(0xFF7A7461),
        VotePosition.NON_VOTANT to Color(0xFF9AA1AA)
    )
    Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 18.dp.toPx())
            var start = -90f
            segments.forEach { (position, color) ->
                val sweep = if (stats.totalVotes == 0) 0f else 360f * stats.countFor(position).toFloat() / stats.totalVotes.toFloat()
                if (sweep > 0f) drawArc(color = color, startAngle = start, sweepAngle = sweep, useCenter = false, style = stroke)
                start += sweep
            }
        }
        Text("${stats.totalVotes}\nvotes", fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PositionLegend(label: String, position: VotePosition, stats: DeputyStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp)
        Text("${stats.countFor(position)} · ${stats.percentFor(position)}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun GroupAlignmentCard(stats: DeputyStats) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alignement avec le groupe politique", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${stats.groupAlignmentPercent}%", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            ComparisonBar("Votes comparables", stats.groupAlignmentPercent)
            Text("Pourcentage de votes où le député a voté comme la majorité de son groupe politique.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
            Text("Base : ${stats.groupAlignmentComparableVotes} scrutins avec position de groupe disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            SourceLine("Ventilation par groupes · Assemblée nationale")
        }
    }
}

@Composable
private fun ActivityTimelineCard(match: DeputeMatch) {
    val byMonth = match.allLegislatureVotes.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it }).entries.take(6)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Activité dans le temps", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleChip("Votes")
                SimpleChip("Questions N/D")
                SimpleChip("Interventions N/D")
            }
            if (byMonth.isEmpty()) {
                Text("Aucun scrutin public chargé.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val max = byMonth.maxOf { it.value.size }.coerceAtLeast(1)
                byMonth.forEach { (month, votes) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(month.monthLabel(), modifier = Modifier.width(62.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f).height(9.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp))) {
                            Box(Modifier.fillMaxWidth(votes.size.toFloat() / max.toFloat()).height(9.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(99.dp)))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(votes.size.toString(), fontSize = 12.sp)
                    }
                }
            }
            SourceLine("Dates des scrutins publics · Assemblée nationale")
        }
    }
}

@Composable
private fun RecentImportantVotesCard(match: DeputeMatch) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Votes récents importants", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Derniers scrutins publics disponibles dans les données chargées.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            match.recentVotes.take(6).forEach { vote -> RecentImportantVoteRow(vote) }
            SourceLine("Scrutins publics · Assemblée nationale")
        }
    }
}

@Composable
private fun RecentImportantVoteRow(vote: Vote) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(vote.date.compactDate(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(vote.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        Text("Député : ${vote.deputePosition.label} · Groupe : ${vote.groupPosition?.groupMajorityPosition?.label ?: "N/D"} · Résultat : ${vote.result}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun BiographyBackgroundCard(match: DeputeMatch) {
    val depute = match.depute
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Biographie & parcours", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeputyPhoto(depute, sizeDp = 62)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(depute.name, fontWeight = FontWeight.Bold)
                    Text(depute.displayProfession, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DeputyInfoRow("Année de naissance", "Information non disponible depuis les sources officielles chargées.")
            DeputyInfoRow("Profession", depute.displayProfession)
            DeputyInfoRow("Département", depute.displayDepartment)
            DeputyInfoRow("Région", depute.displayRegion)
            DeputyInfoRow("Affiliation politique", "${depute.displayPoliticalGroupFull} (${depute.displayPoliticalGroupShort})")
            DeputyInfoRow("Mandat actuel", "Député · ${depute.constituencyNumber}e circonscription du ${depute.departmentName}")
            DeputyInfoRow("Mandats précédents", "Information non disponible depuis les sources officielles chargées.")
            DeputyInfoRow("Commissions", "Information non disponible depuis les sources officielles chargées.")
            DeputyInfoRow("Responsabilités particulières", "Information non disponible depuis les sources officielles chargées.")
            SourceLine("Assemblée nationale · données députés actifs")
        }
    }
}

@Composable
private fun SourcesMethodologyCard(match: DeputeMatch) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sources & méthodologie", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Assemblée nationale · députés actifs, mandats et organes", lineHeight = 19.sp)
            Text("Assemblée nationale · scrutins publics et ventilations de vote", lineHeight = 19.sp)
            Text("geo.api.gouv.fr · communes et départements", lineHeight = 19.sp)
            Text("CitizenEye calcule uniquement des indicateurs descriptifs à partir des votes publics chargés. Aucun score politique, aucune opinion et aucune inférence idéologique ne sont générés.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            Text("Base analysée : ${match.totalLegislatureVotes} scrutins publics pour ce député.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PoliticalGroupDetailScreen(match: DeputeMatch, onOpenSource: (String) -> Unit) {
    val depute = match.depute
    val family = PoliticalFamilies.forDeputy(depute)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(family.groupCode, color = MaterialTheme.colorScheme.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(depute.displayPoliticalGroupFull, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    DeputyInfoRow("Famille politique", family.politicalFamily)
                    DeputyInfoRow("Nombre de députés", "Information non disponible depuis les sources officielles chargées.")
                    DeputyInfoRow("Président du groupe", "Information non disponible depuis les sources officielles chargées.")
                    DeputyInfoRow("Description officielle", "Consultez la page officielle de l’Assemblée nationale pour la description institutionnelle du groupe.")
                    Text("Ce groupe représente actuellement un nombre de députés non disponible dans les sources chargées localement, sur 577 sièges.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                    Button(onClick = { onOpenSource(family.officialAssemblyUrl) }, modifier = Modifier.fillMaxWidth()) { Text("Page officielle Assemblée") }
                    SourceLine("Assemblée nationale · groupes politiques")
                }
            }
        }
    }
}

@Composable
private fun SourceLine(label: String) {
    Text("Source : $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
}

private fun String.monthLabel(): String {
    val parts = split('-')
    if (parts.size < 2) return this
    return "${monthShort(parts[1])} ${parts[0]}"
}

@Composable
private fun UpcomingVotesScreen(uiState: UpcomingVotesUiState, onOpenDetail: (UpcomingVote) -> Unit, onRetry: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("À suivre", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        }
        when (uiState) {
            UpcomingVotesUiState.Loading -> item { InfoCard("Chargement", "Sources officielles…") }
            is UpcomingVotesUiState.Error -> item { InfoCard("Erreur", uiState.message); Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Réessayer") } }
            is UpcomingVotesUiState.Success -> {
                if (uiState.votes.isEmpty()) {
                    item { InfoCard("Aucun texte", "Aucun texte à suivre disponible.") }
                } else {
                    items(uiState.votes) { vote -> UpcomingVoteCard(vote, onClick = { onOpenDetail(vote) }) }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun UpcomingVoteCard(vote: UpcomingVote, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(vote.status.label.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(vote.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Étape : ${vote.currentStage}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("→ Voir le texte", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UpcomingVoteDetailScreen(vote: UpcomingVote, depute: Depute, onOpenSource: (String) -> Unit, onOpenEmailDraft: (UpcomingVote, Depute) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(vote.title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpcomingStatusChip(vote.status)
                SimpleChip(vote.currentStage)
            }
            Spacer(Modifier.height(8.dp))
            SimpleChip(vote.expectedDateLabel)
        }
        item { UpcomingTimeline(vote) }
        item { BulletCard("Pourquoi ça compte", upcomingBullets(vote)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onOpenEmailDraft(vote, depute) }, enabled = depute.email != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Contacter mon député")
                }
                Button(onClick = { onOpenSource(vote.sourceUrl) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Source officielle")
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun UpcomingTimeline(vote: UpcomingVote) {
    val labels = listOf("Déposé", "Commission", vote.currentStage.takeIf { it.isNotBlank() } ?: "Séance publique", "Vote")
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                val symbol = when (index) { 0, 1 -> "✓"; 2 -> "●"; else -> "○" }
                Text("$symbol $label", fontWeight = if (index == 2) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun UpcomingStatusChip(status: UpcomingVoteStatus) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(status.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CitizenEyeBottomNavigation(activeTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    NavigationBar {
        MainTab.values().forEach { tab ->
            val icon = when (tab) {
                MainTab.HOME -> Icons.Outlined.Home
                MainTab.UPCOMING -> Icons.Outlined.CalendarMonth
                MainTab.HISTORY -> Icons.Outlined.History
                MainTab.DEPUTY -> Icons.Outlined.Person
            }
            NavigationBarItem(
                selected = activeTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(tab.label) }
            )
        }
    }
}

@Composable
private fun CompactDeputyCard(match: DeputeMatch, onOpenDeputy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDeputy() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DeputyPhoto(match.depute, sizeDp = 64)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(match.depute.name, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                Text(match.depute.displayPoliticalGroupShort, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
            }
            Text("→", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeputyPhoto(depute: Depute, sizeDp: Int) {
    val modifier = Modifier
        .size(sizeDp.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (depute.photoUrl != null) {
        AsyncImage(
            model = depute.photoUrl,
            contentDescription = "Portrait de ${depute.name}",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(depute.name.initials(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.initials(): String = trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }

@Composable
private fun DeputyStatsScreen(match: DeputeMatch, onRetour: () -> Unit) {
    val stats = match.legislatureStats
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeputyPhoto(match.depute, sizeDp = 92)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Statistiques", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(match.depute.name, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(match.depute.constituencyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onRetour) { Text("Retour aux votes") }
        }
        item {
            InfoCard(
                title = "Statistiques de la législature en cours",
                body = "Ces calculs couvrent les ${stats.totalVotes} scrutins publics de la XVIIe législature où ce député apparaît dans les données chargées. Ils mesurent la participation aux scrutins publics, pas la présence physique en séance."
            )
        }
        item { DeputyInformationCard(match.depute) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatMetricCard(
                    label = "Participation aux scrutins publics analysés",
                    value = "${stats.participationPercent}%",
                    detail = "${stats.participatedVotes}/${stats.totalVotes} scrutins",
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    label = "Base analysée",
                    value = stats.totalVotes.toString(),
                    detail = "votes publics",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Répartition des positions", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    PositionStatRow("Pour", VotePosition.POUR, stats)
                    PositionStatRow("Contre", VotePosition.CONTRE, stats)
                    PositionStatRow("Abstention", VotePosition.ABSTENTION, stats)
                    PositionStatRow("Non-votant", VotePosition.NON_VOTANT, stats)
                }
            }
        }
        item {
            InfoCard(
                title = "Limites des données",
                body = "Ces statistiques portent uniquement sur les scrutins publics disponibles dans les données chargées. Elles ne mesurent pas le travail en circonscription, les réunions, les amendements ou les interventions en séance."
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DeputyInformationCard(depute: Depute) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text("Informations du député", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            DeputyInfoRow("Région", depute.displayRegion)
            DeputyInfoRow("Département", depute.displayDepartment)
            DeputyInfoRow("Profession", depute.displayProfession)
            DeputyInfoRow("Groupe politique", depute.displayPoliticalGroupFull)
        }
    }
}

@Composable
private fun DeputyInfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, lineHeight = 20.sp)
    }
}

@Composable
private fun StatMetricCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PositionStatRow(label: String, position: VotePosition, stats: DeputyStats) {
    val percent = stats.percentFor(position)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text("${stats.countFor(position)} · $percent%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))) {
            Box(
                Modifier
                    .fillMaxWidth((percent.coerceIn(0, 100) / 100f).coerceAtLeast(if (percent > 0) 0.03f else 0f))
                    .height(8.dp)
                    .background(positionColor(position), RoundedCornerShape(100.dp))
            )
        }
    }
}

@Composable
private fun VoteCard(vote: com.citizeneye.data.Vote, onOuvrirDetails: () -> Unit) {
    val subjectType = classifyVoteSubjectType(vote.title)
    val resultLabel = formatVoteResultLabel(subjectType, vote.result).uppercase()
    val positionLabel = formatPositionInContext(vote.deputePosition, subjectType).uppercase()
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOuvrirDetails() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(vote.date.compactDate(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(vote.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Député : $positionLabel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Résultat : $resultLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("→ Comprendre", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VoteConcernChip(concern: VoteConcern) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            concern.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)).padding(18.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
    }
}

private fun DeputeMatch.dissentPercent(): Int {
    val comparable = allLegislatureVotes.mapNotNull { it.groupPosition?.deputyVotedLikeGroup }
    if (comparable.isEmpty()) return 0
    return ((comparable.count { !it } * 100.0) / comparable.size).toInt()
}

private fun upcomingBullets(vote: UpcomingVote): List<String> = listOf(
    "Texte parlementaire en cours",
    "Étape : ${vote.currentStage}",
    "Vote : ${vote.expectedDateLabel}"
).map { it.cleanBullet() }.filter { it.isNotBlank() }.take(3)

private fun String.cleanBullet(maxChars: Int = 110): String =
    replace("\n", " ").replace(Regex("\\s+"), " ").trim().let { value ->
        if (value.length <= maxChars) value else value.take(maxChars).trimEnd() + "…"
    }

private fun String.compactDate(): String = trim().let { value ->
    val parts = value.split('-', '/')
    if (parts.size >= 3 && parts[0].length == 4) "${parts[2].take(2)} ${monthShort(parts[1])}" else value
}

private fun monthShort(month: String): String = when (month.padStart(2, '0')) {
    "01" -> "janv."
    "02" -> "févr."
    "03" -> "mars"
    "04" -> "avr."
    "05" -> "mai"
    "06" -> "juin"
    "07" -> "juil."
    "08" -> "août"
    "09" -> "sept."
    "10" -> "oct."
    "11" -> "nov."
    "12" -> "déc."
    else -> month
}

@Composable
private fun SimpleChip(label: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BulletCard(title: String, bullets: List<String>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            bullets.take(3).forEach { Text("• $it", lineHeight = 20.sp) }
        }
    }
}

private data class DeviceLocationAutocomplete(
    val query: String,
    val latitude: Double,
    val longitude: Double
)

@Suppress("DEPRECATION")
private suspend fun detectPostalOrCityFromDeviceLocation(context: Context): DeviceLocationAutocomplete? = withContext(Dispatchers.IO) {
    if (!context.hasLocationPermission()) return@withContext null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
    val location = runCatching {
        manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull() ?: return@withContext null
    val address = runCatching {
        Geocoder(context, Locale.FRANCE).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
    }.getOrNull()
    val query = address?.postalCode?.takeIf { it.length == 5 } ?: address?.locality?.takeIf { it.isNotBlank() } ?: return@withContext null
    DeviceLocationAutocomplete(query = query, latitude = location.latitude, longitude = location.longitude)
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun positionColor(position: VotePosition): Color = when (position) {
    VotePosition.POUR,
    VotePosition.CONTRE,
    VotePosition.ABSTENTION,
    VotePosition.NON_VOTANT -> Color(0xFF5B667A)
}


