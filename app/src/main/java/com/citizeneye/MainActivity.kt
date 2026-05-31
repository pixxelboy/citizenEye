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
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.citizeneye.data.AssembleeOfficialVoteEnrichmentRepository
import com.citizeneye.data.CitizenEyeRepository
import com.citizeneye.data.CitizenInputValidator
import com.citizeneye.data.CivicEmailContext
import com.citizeneye.data.Depute
import com.citizeneye.data.DeputeMatch
import com.citizeneye.data.DefaultVoteDetailRepository
import com.citizeneye.data.DeputyStats
import com.citizeneye.data.GroupCivicDashboard
import com.citizeneye.data.ImportanceLevel
import com.citizeneye.data.LocationPreview
import com.citizeneye.data.LookupState
import com.citizeneye.data.PublicDataCache
import com.citizeneye.data.PolicyTopicClassifier
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
import com.citizeneye.data.hasRelevantContactMoment
import com.citizeneye.data.importance
import com.citizeneye.data.independenceSummary
import com.citizeneye.data.isContactMomentRelevant
import com.citizeneye.data.legislativeTimeline
import com.citizeneye.data.policyTopicDistribution
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

private enum class MainTab(val label: String, val iconRawRes: Int) {
    HOME("Accueil", R.raw.nav_home),
    UPCOMING("À suivre", R.raw.nav_schedule),
    HISTORY("Votes", R.raw.nav_order_history),
    DEPUTY("Député", R.raw.nav_user_profile)
}

private object CitizenEyeBrandAnimationSession {
    var launchAnimationPlayed: Boolean = false
}

private sealed interface UpcomingVotesUiState {
    data object Loading : UpcomingVotesUiState
    data class Success(val votes: List<UpcomingVote>) : UpcomingVotesUiState
    data class Error(val message: String) : UpcomingVotesUiState
}

private sealed interface GroupDashboardUiState {
    data object Loading : GroupDashboardUiState
    data class Success(val dashboard: GroupCivicDashboard) : GroupDashboardUiState
    data class Error(val message: String) : GroupDashboardUiState
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
    var selectedGroupCode by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVote by remember { mutableStateOf<Vote?>(null) }
    var selectedUpcomingVote by remember { mutableStateOf<UpcomingVote?>(null) }
    var civicEmailContext by remember { mutableStateOf<CivicEmailContext?>(null) }
    var activeTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var logoAnimationTrigger by rememberSaveable { mutableStateOf(0) }
    var helpAnimationTrigger by rememberSaveable { mutableStateOf(0) }
    var upcomingVotesUiState by remember { mutableStateOf<UpcomingVotesUiState>(UpcomingVotesUiState.Loading) }
    var groupDashboardUiState by remember { mutableStateOf<GroupDashboardUiState>(GroupDashboardUiState.Loading) }
    var explorerOpen by rememberSaveable { mutableStateOf(false) }
    var explorerLoading by rememberSaveable { mutableStateOf(false) }
    var explorerDeputies by remember { mutableStateOf<List<Depute>>(emptyList()) }
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current
    val updateManager = remember(context) { AppUpdateManager(context.applicationContext) }

    fun selectMainTab(tab: MainTab) {
        if (activeTab != tab) {
            activeTab = tab
            logoAnimationTrigger += 1
        }
    }

    fun runLookup() {
        val currentQuery = query.trim()
        state = LookupState.Loading("Recherche de la commune…")
        showingStats = false
        selectedVote = null
        selectedUpcomingVote = null
        civicEmailContext = null
        showingGroupDetails = false
        activeTab = MainTab.HOME
        scope.launch { state = repository.lookup(currentQuery) }
    }

    fun loadVotes(query: String, commune: com.citizeneye.data.Commune, depute: Depute) {
        state = LookupState.Loading("Préparation des résultats…")
        showingStats = false
        selectedVote = null
        selectedUpcomingVote = null
        civicEmailContext = null
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

    fun openDeputyExplorer() {
        explorerOpen = true
        if (explorerDeputies.isEmpty() && !explorerLoading) {
            scope.launch {
                explorerLoading = true
                inlineError = null
                explorerDeputies = runCatching { repository.fetchDeputiesForExploration() }.getOrElse {
                    inlineError = "Impossible de charger la liste des députés depuis les sources officielles."
                    emptyList()
                }
                explorerLoading = false
            }
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
        if (!CitizenEyeBrandAnimationSession.launchAnimationPlayed) {
            CitizenEyeBrandAnimationSession.launchAnimationPlayed = true
            logoAnimationTrigger += 1
        }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            CitizenEyeSupportDrawer(
                onSupport = {
                    scope.launch { drawerState.close() }
                    openExternalUrl("https://buymeacoffee.com/pixxelboy")
                }
            )
        }
    ) {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.shadow(elevation = 3.dp),
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
                        else -> CitizenEyeBrandLogo(animationTrigger = logoAnimationTrigger)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            helpAnimationTrigger += 1
                            scope.launch { drawerState.open() }
                        }
                    ) {
                        AnimatedHelpIcon(animationTrigger = helpAnimationTrigger)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (state is LookupState.Loaded) {
                CitizenEyeBottomNavigation(activeTab = activeTab, onTabSelected = { tab ->
                    selectMainTab(tab)
                    selectedVote = null
                    selectedUpcomingVote = null
                    showingGroupDetails = false
                    showingStats = false
                })
            }
        }
    ) { padding ->
        Surface(Modifier.padding(padding).fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (explorerOpen) {
                DeputyExplorerScreen(
                    deputies = explorerDeputies,
                    loading = explorerLoading,
                    onClose = { explorerOpen = false }
                )
            } else when (val current = state) {
                LookupState.Idle -> OnboardingScreen(
                    query = query,
                    error = inlineError,
                    loading = false,
                    preview = preview,
                    previewLoading = previewLoading,
                    geoLoading = geoLoading,
                    locationAcquired = preview != null && geolocationPreviewQuery == query.trim(),
                    onQueryChange = { geolocationPreviewQuery = null; query = it },
                    onGeolocate = ::startGeolocationAutocomplete,
                    onSubmit = ::confirmLocation,
                    onExplore = ::openDeputyExplorer
                )
                is LookupState.Error -> OnboardingScreen(
                    query = query,
                    error = current.message,
                    loading = false,
                    preview = preview,
                    previewLoading = previewLoading,
                    geoLoading = geoLoading,
                    locationAcquired = preview != null && geolocationPreviewQuery == query.trim(),
                    onQueryChange = { geolocationPreviewQuery = null; query = it; state = LookupState.Idle },
                    onGeolocate = ::startGeolocationAutocomplete,
                    onSubmit = ::confirmLocation,
                    onExplore = ::openDeputyExplorer
                )
                is LookupState.Loading -> LoadingScreen(current.message)
                is LookupState.NeedSelection -> DeputySelectionScreen(
                    selection = current,
                    onSelect = { loadVotes(current, it) },
                    onReset = { state = LookupState.Idle }
                )
                is LookupState.Loaded -> if (civicEmailContext != null) {
                    CivicEmailComposerScreen(
                        context = civicEmailContext!!,
                        deputyEmail = civicEmailContext!!.depute.email,
                        onBack = { civicEmailContext = null },
                        onOpenEmailDraft = ::openEmailDraft
                    )
                } else if (selectedUpcomingVote != null) {
                    UpcomingVoteDetailScreen(
                        vote = selectedUpcomingVote!!,
                        depute = current.match.depute,
                        onOpenSource = ::openExternalUrl,
                        onOpenEmailComposer = { upcoming, depute ->
                            civicEmailContext = CivicEmailContext.UpcomingVote(upcoming, depute)
                        }
                    )
                } else if (selectedVote != null) {
                    VoteDetailScreen(
                        voteDetailUiState = voteDetailUiState,
                        deputyEmail = current.match.depute.email,
                        onRetour = { selectedVote = null },
                        onOuvrirUrl = ::openExternalUrl,
                        onOpenCivicEmailComposer = {
                            selectedVote?.let { vote -> civicEmailContext = CivicEmailContext.PastVote(vote, current.match.depute) }
                        },
                        onRetry = ::retryVoteDetails
                    )
                } else if (showingStats) {
                    DeputyStatsScreen(
                        match = current.match,
                        onRetour = { showingStats = false }
                    )
                } else if (showingGroupDetails) {
                    val activeGroupCode = selectedGroupCode ?: current.match.depute.displayPoliticalGroupShort
                    LaunchedEffect(activeGroupCode) {
                        groupDashboardUiState = GroupDashboardUiState.Loading
                        groupDashboardUiState = runCatching { repository.fetchGroupCivicDashboard(activeGroupCode) }
                            .fold(
                                onSuccess = { GroupDashboardUiState.Success(it) },
                                onFailure = { GroupDashboardUiState.Error("Impossible de charger le tableau du groupe depuis les données locales.") }
                            )
                    }
                    PoliticalGroupDetailScreen(
                        match = current.match,
                        uiState = groupDashboardUiState,
                        onOpenDeputy = { depute -> loadVotes(current.match.query, current.match.commune, depute) },
                        onSelectGroup = { code -> selectedGroupCode = code },
                        onOpenSource = ::openExternalUrl
                    )
                } else {
                    when (activeTab) {
                        MainTab.HOME -> HomeScreen(
                            match = current.match,
                            upcomingVotes = (upcomingVotesUiState as? UpcomingVotesUiState.Success)?.votes.orEmpty(),
                            onOpenUpcoming = { selectMainTab(MainTab.UPCOMING) },
                            onOpenHistory = { selectMainTab(MainTab.HISTORY) },
                            onOpenDeputy = { selectMainTab(MainTab.DEPUTY) },
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
                            onOpenStats = { showingStats = true },
                            onOpenGroup = { selectedGroupCode = null; showingGroupDetails = true },
                            onReset = { state = LookupState.Idle; query = ""; showingStats = false; showingGroupDetails = false; activeTab = MainTab.HOME }
                        )
                    }
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
    locationAcquired: Boolean,
    onQueryChange: (String) -> Unit,
    onGeolocate: () -> Unit,
    onSubmit: () -> Unit,
    onExplore: () -> Unit
) {
    val cleanQuery = query.trim()
    val canSubmit = CitizenInputValidator.canSearch(cleanQuery) && !loading && !previewLoading && !geoLoading
    val showBottomCta = canSubmit || locationAcquired

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = if (showBottomCta) 94.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CivicLocationHero(
                geoLoading = geoLoading,
                success = locationAcquired,
                failure = error != null
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Découvrez votre député",
                fontSize = 31.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Découvrez votre député" }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Votes, positions, actions à venir et moyens de contact.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onGeolocate,
                enabled = !geoLoading && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Utiliser ma localisation pour trouver mon député" },
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(if (geoLoading) "📍 Localisation…" else if (locationAcquired) "✓ Localisation prête" else "📍 Utiliser ma localisation")
            }
            Text(
                "Position utilisée seulement pour identifier la circonscription.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            OrDivider()
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                label = { Text("Code postal ou ville") },
                placeholder = { Text("92000 ou Nanterre") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                isError = error != null
            )
            val helper = error ?: when {
                previewLoading -> "Recherche de la commune…"
                preview != null -> preview.locationLabel
                else -> "Alternative si vous préférez ne pas partager votre position."
            }
            Text(
                helper,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
            )
            preview?.let {
                Spacer(Modifier.height(6.dp))
                LocationPreviewCard(it)
            }
            Spacer(Modifier.height(10.dp))
            ValuePreviewSection()
            Spacer(Modifier.weight(1f))
            TrustSection()
            TextButton(
                onClick = onExplore,
                modifier = Modifier.height(44.dp)
            ) {
                Text("Explorer les députés →")
            }
        }

        AnimatedVisibility(
            visible = showBottomCta,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 10.dp
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = showBottomCta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(56.dp)
                        .semantics { contentDescription = "Voir mon député" },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Voir mon député")
                }
            }
        }
    }
}

@Composable
private fun CivicLocationHero(geoLoading: Boolean, success: Boolean, failure: Boolean) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.location))
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(geoLoading, success, failure, animationsEnabled, composition) {
        if (!animationsEnabled || composition == null) {
            progress.snapTo(if (success) 1f else 0f)
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }
        when {
            geoLoading -> {
                scale.snapTo(1f)
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
                progress.snapTo(0.08f)
                while (true) {
                    offsetY.animateTo(-10f, tween(150, easing = LinearEasing))
                    progress.animateTo(0.55f, tween(260, easing = LinearEasing))
                    offsetY.animateTo(0f, tween(170, easing = LinearEasing))
                    progress.animateTo(0.16f, tween(240, easing = LinearEasing))
                    delay(80)
                }
            }
            success -> {
                progress.animateTo(1f, tween(620, easing = LinearEasing))
                scale.animateTo(1.12f, tween(140, easing = LinearEasing))
                scale.animateTo(1f, tween(220, easing = LinearEasing))
            }
            failure -> {
                progress.snapTo(0.35f)
                repeat(2) {
                    offsetX.animateTo(-8f, tween(55, easing = LinearEasing))
                    offsetX.animateTo(8f, tween(55, easing = LinearEasing))
                }
                offsetX.animateTo(0f, tween(70, easing = LinearEasing))
            }
            else -> {
                progress.snapTo(0f)
                offsetY.snapTo(0f)
                scale.animateTo(1.025f, tween(900, easing = LinearEasing))
                scale.animateTo(1f, tween(900, easing = LinearEasing))
            }
        }
    }

    Box(
        Modifier
            .size(132.dp)
            .scale(scale.value)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress.value },
            modifier = Modifier
                .size(126.dp)
                .offset(x = offsetX.value.dp, y = offsetY.value.dp)
                .semantics { contentDescription = "État de recherche de localisation" }
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))
        Text("OU", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))
    }
}

@Composable
private fun ValuePreviewSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Ce que vous allez obtenir", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ValueChecklistItem("Votre député")
                    ValueChecklistItem("Votes récents")
                    ValueChecklistItem("Votes à venir")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ValueChecklistItem("Groupe politique")
                    ValueChecklistItem("Coordonnées")
                }
            }
        }
    }
}

@Composable
private fun ValueChecklistItem(label: String) {
    Text("✓ $label", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun TrustSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("577 députés suivis", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text("Données ouvertes de l’Assemblée nationale", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text("Source : geo.api.gouv.fr", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun DeputyExplorerScreen(deputies: List<Depute>, loading: Boolean, onClose: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val cleanFilter = filter.trim()
    val visibleDeputies = remember(deputies, cleanFilter) {
        if (cleanFilter.length < 2) deputies.take(80) else deputies.filter { depute ->
            depute.name.contains(cleanFilter, ignoreCase = true) ||
                depute.departmentCode.contains(cleanFilter, ignoreCase = true) ||
                depute.displayPoliticalGroupShort.contains(cleanFilter, ignoreCase = true)
        }.take(80)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = onClose, modifier = Modifier.height(44.dp)) { Text("Retour") }
            Text("Explorer les députés", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "Parcourez la liste officielle, puis revenez à la recherche locale pour trouver votre circonscription.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nom, groupe ou département") },
                placeholder = { Text("Dupont, SOC, 92") },
                singleLine = true
            )
            if (loading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        items(visibleDeputies) { depute ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeputyPhoto(depute, sizeDp = 54)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(depute.name, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                        GroupBadge(depute.displayPoliticalGroupShort, depute.displayPoliticalGroupFull)
                        Text(depute.constituencyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LocationPreviewCard(preview: LocationPreview) {
    val detectedDeputy = preview.deputies.singleOrNull()
    val trackedVoteCount = detectedDeputy?.let { preview.voteCountsByDeputyId[it.id] }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (preview.preciseBoundary != null) "Député détecté par GPS" else "Localisation déduite",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(preview.locationLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (detectedDeputy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeputyPhoto(detectedDeputy, sizeDp = 58)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(detectedDeputy.name, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                        Text(detectedDeputy.displayPoliticalGroupShort, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(detectedDeputy.constituencyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
                if (trackedVoteCount != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            "$trackedVoteCount votes publics suivis",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Text(preview.representativeHint, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            }
            if (preview.preciseBoundary != null) {
                Spacer(Modifier.height(8.dp))
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
                label = message.ifBlank { "Recherche de votre circonscription…" },
                useBrandLogo = true
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
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeputyPhoto(depute, sizeDp = 62)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(depute.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        GroupBadge(depute.displayPoliticalGroupShort, depute.displayPoliticalGroupFull)
                        Text(depute.constituencyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HomeScreen(match: DeputeMatch, upcomingVotes: List<UpcomingVote>, onOpenUpcoming: () -> Unit, onOpenHistory: () -> Unit, onOpenDeputy: () -> Unit, onReset: () -> Unit) {
    val criticalVotes = match.importantVotes.take(3)
    val recentlyAdopted = match.recentlyAdoptedVotes.take(3)
    val topics = match.topicDistribution.take(5)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Tableau civique", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text("${match.commune.name} · ${match.depute.displayPoliticalGroupShort}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        item { HomeUpcomingActions(upcomingVotes.datedFirst().take(3), onOpenUpcoming) }
        item { HomeImportantVotes(criticalVotes, onOpenHistory) }
        item { HomeRecentlyAdopted(recentlyAdopted) }
        item { CompactDeputyCard(match, onOpenDeputy) }
        item { HomeExploreTopics(topics) }
        item { TextButton(onClick = onReset) { Text("Changer de localisation") } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun HomeUpcomingActions(upcoming: List<UpcomingVote>, onOpenUpcoming: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ce qui arrive", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenUpcoming) { Text("Voir") }
            }
            if (upcoming.isEmpty()) {
                Text("Aucune échéance parlementaire locale disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                upcoming.forEach { vote -> UpcomingActionRow(vote) }
            }
        }
    }
}

@Composable
private fun UpcomingActionRow(vote: UpcomingVote) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(vote.expectedDateLabel, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(vote.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            Text(vote.calendarMetaLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${vote.currentStage} · ${vote.shortTopicLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (vote.isContactMomentRelevant) Text("Écrire à mon député", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeImportantVotes(votes: List<Vote>, onOpenHistory: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Votes importants", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenHistory) { Text("Votes") }
            }
            votes.forEach { vote -> ImportantVoteRow(vote) }
        }
    }
}

@Composable
private fun ImportantVoteRow(vote: Vote) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        ImpactBadge(vote.importance.level)
        Column(Modifier.weight(1f)) {
            Text(vote.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Text("${vote.date.compactDate()} · ${vote.deputePosition.label}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeRecentlyAdopted(votes: List<Vote>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Récemment adopté", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (votes.isEmpty()) Text("Aucun texte adopté disponible dans les scrutins chargés.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            votes.forEach { vote -> Text("✓ ${vote.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun HomeExploreTopics(topics: List<com.citizeneye.data.PolicyTopicDistribution>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Explorer les sujets", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            topics.forEach { TopicDistributionRow(it.topic.label, it.percentage, it.voteCount) }
        }
    }
}

@Composable
private fun TopicDistributionRow(label: String, percent: Int, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("$percent%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp))) {
            Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(99.dp)))
        }
    }
}

@Composable
private fun ImpactBadge(level: ImportanceLevel) {
    val text = when (level) {
        ImportanceLevel.CRITICAL -> "CRITICAL"
        ImportanceLevel.HIGH -> "HIGH IMPACT"
        ImportanceLevel.MEDIUM -> "IMPORTANT"
        ImportanceLevel.LOW -> "LOW"
    }
    val color = when (level) {
        ImportanceLevel.CRITICAL -> Color(0xFF7A1F1F)
        ImportanceLevel.HIGH -> Color(0xFF9A5A1F)
        ImportanceLevel.MEDIUM -> MaterialTheme.colorScheme.primary
        ImportanceLevel.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.12f), contentColor = color) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun UpcomingVote.shortTopicLabel(): String = PolicyTopicClassifier().classify(
    dossierTitle = title,
    legislativeReference = currentStage,
    objectTitle = shortSummary
).primaryTopic.label

private fun List<UpcomingVote>.datedFirst(): List<UpcomingVote> = sortedWith(
    compareByDescending<UpcomingVote> { it.eventDate != null }
        .thenBy { it.eventDateTime ?: it.eventDate ?: "9999-12-31" }
        .thenBy { it.title }
)

private fun UpcomingVote.eventTypeLabel(): String = when (eventType) {
    "public_session" -> "Séance publique"
    "committee" -> "Commission"
    "vote" -> "Vote"
    "debate" -> "Débat"
    "hearing" -> "Audition"
    else -> "Agenda"
}

private fun UpcomingVote.calendarMetaLabel(): String = listOfNotNull(chamber, eventTypeLabel()).joinToString(" · ")

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
private fun DeputyProfileScreen(match: DeputeMatch, onOpenStats: () -> Unit, onOpenGroup: () -> Unit, onReset: () -> Unit) {
    val stats = match.legislatureStats
    var selectedTopic by remember { mutableStateOf<com.citizeneye.data.PolicyTopic?>(null) }
    val topicDistribution = match.topicDistribution.take(5)
    val filteredTopicVotes = remember(selectedTopic, match.depute.id) {
        selectedTopic?.let { topic -> match.allLegislatureVotes.filter { vote -> vote.matchesTopic(topic) }.take(8) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { DeputyCivicOverview(match) }
        item { IndependenceSection(match) }
        item { MainTopicsSection(topicDistribution, onSelectTopic = { selectedTopic = it.topic }) }
        filteredTopicVotes?.let { votes ->
            selectedTopic?.let { topic -> item { TopicFilteredVoteSection(topic.label, votes) } }
        }
        item { VoteDistributionCard(stats, onOpenStats) }
        item { RecentVotesPreview(match) }
        item { TextButton(onClick = onOpenGroup) { Text("Voir le groupe politique") } }
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeputyPhoto(depute, sizeDp = 92)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(depute.name, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
                    GroupBadge(depute.displayPoliticalGroupShort, depute.displayPoliticalGroupFull)
                    Text(depute.displayDepartment, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (depute.displayProfession != "Non renseignée") {
                        Text(depute.displayProfession, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeputyCivicOverview(match: DeputeMatch) {
    val depute = match.depute
    val stats = match.legislatureStats
    val independence = match.independenceSummary
    val topTopics = match.topicDistribution.take(3)
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeputyPhoto(depute, sizeDp = 70)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(depute.name, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    GroupBadge(depute.displayPoliticalGroupShort, depute.displayPoliticalGroupFull)
                    Text("${depute.displayDepartment} · ${depute.displayProfession}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityScorecard("${stats.participationPercent}%", "Présence", Modifier.weight(1f))
                ActivityScorecard(stats.participatedVotes.toString(), "Votes", Modifier.weight(1f))
                ActivityScorecard("N/D", "Interv.", Modifier.weight(1f))
                ActivityScorecard("N/D", "Amend.", Modifier.weight(1f))
            }
            IndependenceSplitBar(independence.alignmentPercentage, independence.dissidentPercentage)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityScorecard("${independence.alignmentPercentage}%", "Aligné", Modifier.weight(1f))
                ActivityScorecard("${independence.dissidentPercentage}%", "Dissident", Modifier.weight(1f))
            }
            if (topTopics.isNotEmpty()) {
                Text("Sujets principaux : ${topTopics.joinToString(" · ") { it.topic.label }}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Focus actuel : ${topTopics.first().topic.label}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IndependenceSection(match: DeputeMatch) {
    val summary = match.independenceSummary
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Indépendance", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IndependenceSplitBar(summary.alignmentPercentage, summary.dissidentPercentage)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityScorecard(summary.alignedVotes.toString(), "Votes alignés", Modifier.weight(1f))
                ActivityScorecard(summary.dissidentVotes.toString(), "Votes dissidents", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistributionMetric("${summary.alignmentPercentage}%", "Aligné", Modifier.weight(1f))
                DistributionMetric("${summary.dissidentPercentage}%", "Dissident", Modifier.weight(1f))
            }
            Text("Votes dissidents récents", fontWeight = FontWeight.Bold)
            if (summary.recentDissidentVotes.isEmpty()) {
                Text("Aucun vote dissident enregistré.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                summary.recentDissidentVotes.forEach { vote -> DissidentVoteCard(vote) }
            }
        }
    }
}

@Composable
private fun IndependenceSplitBar(alignedPercent: Int, dissidentPercent: Int) {
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (alignedPercent > 0) Box(Modifier.weight(alignedPercent.toFloat()).height(14.dp).background(Color(0xFF2F6F73)))
        if (dissidentPercent > 0) Box(Modifier.weight(dissidentPercent.toFloat()).height(14.dp).background(Color(0xFFB05D4D)))
    }
}

@Composable
private fun DissidentVoteCard(vote: Vote) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(vote.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Text("Groupe : ${vote.groupPosition?.groupMajorityPosition?.label ?: "N/D"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text("Député : ${vote.deputePosition.label}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(vote.date.compactDate(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MainTopicsSection(topics: List<com.citizeneye.data.PolicyTopicDistribution>, onSelectTopic: (com.citizeneye.data.PolicyTopicDistribution) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sujets principaux", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (topics.isEmpty()) {
                Text("Aucun sujet officiel disponible pour les scrutins chargés.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                topics.forEach { topic ->
                    Box(Modifier.fillMaxWidth().clickable { onSelectTopic(topic) }) {
                        TopicDistributionRow(topic.topic.label, topic.percentage, topic.voteCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicFilteredVoteSection(topicLabel: String, votes: List<Vote>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Votes · $topicLabel", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (votes.isEmpty()) {
                Text("Aucun vote disponible pour ce sujet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                votes.forEach { vote ->
                    Text("${vote.date.compactDate()} · ${vote.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

private fun Vote.matchesTopic(topic: com.citizeneye.data.PolicyTopic): Boolean = PolicyTopicClassifier().classify(
    dossierTitle = dossierTitle,
    legislativeReference = legislativeReference,
    objectTitle = objectTitle
).allTopics.contains(topic)

@Composable
private fun GroupBadge(shortName: String, fullName: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(
            text = if (fullName.length <= 34) "$shortName · $fullName" else shortName,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActivityScorecards(stats: DeputyStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityScorecard("${stats.participationPercent}%", "Présence", Modifier.weight(1f))
            ActivityScorecard(stats.participatedVotes.toString(), "Votes", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivityScorecard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(value, fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VoteDistributionStackedBar(stats: DeputyStats) {
    val total = stats.totalVotes.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        VotePosition.entries.forEach { position ->
            val count = stats.countFor(position)
            if (count > 0) {
                Box(Modifier.weight(count.toFloat() / total.toFloat()).height(16.dp).background(positionColor(position)))
            }
        }
    }
}

@Composable
private fun DistributionMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
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
private fun VoteDistributionCard(stats: DeputyStats, onOpenStats: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenStats() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Répartition des votes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Détails →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            VoteDistributionStackedBar(stats)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistributionMetric("${stats.percentFor(VotePosition.POUR)}%", "Pour", Modifier.weight(1f))
                DistributionMetric("${stats.percentFor(VotePosition.CONTRE)}%", "Contre", Modifier.weight(1f))
                DistributionMetric("${stats.percentFor(VotePosition.ABSTENTION)}%", "Abst.", Modifier.weight(1f))
                DistributionMetric("${stats.percentFor(VotePosition.NON_VOTANT)}%", "Absent", Modifier.weight(1f))
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
private fun GroupAlignmentScorecard(stats: DeputyStats) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Alignement avec le groupe", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${stats.groupAlignmentComparableVotes} votes comparés", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${stats.alignmentRate}%", fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text("Aligné", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
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
private fun RecentVotesPreview(match: DeputeMatch) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Votes récents", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        match.recentVotes.take(5).forEach { vote -> VoteCard(vote, onOuvrirDetails = {}) }
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
private fun PoliticalGroupDetailScreen(match: DeputeMatch, uiState: GroupDashboardUiState, onOpenDeputy: (Depute) -> Unit, onSelectGroup: (String) -> Unit, onOpenSource: (String) -> Unit) {
    val family = PoliticalFamilies.forDeputy(match.depute)
    when (uiState) {
        GroupDashboardUiState.Loading -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item { InfoCard("Chargement", "Calcul du tableau du groupe depuis les données locales…") }
        }
        is GroupDashboardUiState.Error -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            item { InfoCard("Groupe politique", uiState.message) }
        }
        is GroupDashboardUiState.Success -> {
            val dashboard = uiState.dashboard
            var memberQuery by remember { mutableStateOf("") }
            var memberSort by remember { mutableStateOf("Nom") }
            val filteredMembers = dashboard.members
                .filter { it.depute.name.contains(memberQuery, ignoreCase = true) }
                .let { members ->
                    when (memberSort) {
                        "Présence" -> members.sortedByDescending { it.presencePercent }
                        "Activité" -> members.sortedByDescending { it.activityCount }
                        "Alignement" -> members.sortedByDescending { it.alignmentPercent }
                        else -> members.sortedBy { it.depute.name }
                    }
                }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { GroupDashboardHeader(dashboard, family.officialAssemblyUrl, onOpenSource) }
                item { GroupKeyMetrics(dashboard) }
                item { GroupVotingBehaviorCard(dashboard) }
                item { GroupInternalDisciplineCard(dashboard) }
                item { GroupPoliticalProximityCard(dashboard, onSelectGroup) }
                item { GroupMainTopicsCard(dashboard) }
                item { GroupActivityCard(dashboard) }
                item { GroupInfluenceCard(dashboard) }
                item { GroupIndependentDeputiesCard(dashboard, onOpenDeputy) }
                item { GroupMembersCard(filteredMembers, memberQuery, onQuery = { memberQuery = it }, memberSort, onSort = { memberSort = it }, onOpenDeputy) }
                item { GroupAssemblyRepresentationCard(dashboard) }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun GroupDashboardHeader(dashboard: GroupCivicDashboard, sourceUrl: String, onOpenSource: (String) -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Text(dashboard.groupAbbreviation, modifier = Modifier.padding(16.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dashboard.groupName, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${dashboard.deputyCount} députés", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${dashboard.assemblySharePercent}% de l’Assemblée", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            TextButton(onClick = { onOpenSource(sourceUrl) }) { Text("Source") }
        }
    }
}

@Composable
private fun GroupKeyMetrics(dashboard: GroupCivicDashboard) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityScorecard("${dashboard.disciplinePercent}%", "Discipline", Modifier.weight(1f))
            ActivityScorecard("${dashboard.presencePercent}%", "Présence", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityScorecard("${dashboard.influencePercent}%", "Influence", Modifier.weight(1f))
            ActivityScorecard(dashboard.deputyCount.toString(), "Députés", Modifier.weight(1f))
        }
    }
}

@Composable
private fun GroupVotingBehaviorCard(dashboard: GroupCivicDashboard) {
    val dist = dashboard.voteDistribution
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comportement de vote", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (dist.pourCount > 0) Box(Modifier.weight(dist.pourCount.toFloat()).height(16.dp).background(positionColor(VotePosition.POUR)))
                if (dist.contreCount > 0) Box(Modifier.weight(dist.contreCount.toFloat()).height(16.dp).background(positionColor(VotePosition.CONTRE)))
                if (dist.abstentionCount > 0) Box(Modifier.weight(dist.abstentionCount.toFloat()).height(16.dp).background(positionColor(VotePosition.ABSTENTION)))
                if (dist.absentCount > 0) Box(Modifier.weight(dist.absentCount.toFloat()).height(16.dp).background(positionColor(VotePosition.NON_VOTANT)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistributionMetric("${dist.pourPercent}%", "Pour", Modifier.weight(1f))
                DistributionMetric("${dist.contrePercent}%", "Contre", Modifier.weight(1f))
                DistributionMetric("${dist.abstentionPercent}%", "Abst.", Modifier.weight(1f))
                DistributionMetric("${dist.absentPercent}%", "Absent", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GroupInternalDisciplineCard(dashboard: GroupCivicDashboard) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Discipline interne", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${dashboard.disciplinePercent}%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ComparisonBar("Discipline", dashboard.disciplinePercent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityScorecard(dashboard.alignedVotes.toString(), "Alignés", Modifier.weight(1f))
                ActivityScorecard(dashboard.dissidentVotes.toString(), "Dissidents", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GroupPoliticalProximityCard(dashboard: GroupCivicDashboard, onSelectGroup: (String) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Partenaires de vote fréquents", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (dashboard.politicalProximities.isEmpty()) Text("Aucune comparaison disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            dashboard.politicalProximities.take(8).forEach { proximity ->
                Column(Modifier.clickable { onSelectGroup(proximity.groupAbbreviation) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(proximity.groupAbbreviation, fontWeight = FontWeight.Bold)
                        Text("${proximity.sharedVotingRatePercent}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))) {
                        Box(Modifier.fillMaxWidth(proximity.sharedVotingRatePercent.coerceIn(0, 100) / 100f).height(12.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + proximity.sharedVotingRatePercent.coerceIn(0, 100) / 140f), RoundedCornerShape(999.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMainTopicsCard(dashboard: GroupCivicDashboard) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sujets principaux", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (dashboard.topTopics.isEmpty()) Text("Aucun sujet disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            dashboard.topTopics.take(5).forEach { TopicDistributionRow(it.topic.label, it.percentage, it.voteCount) }
        }
    }
}

@Composable
private fun GroupActivityCard(dashboard: GroupCivicDashboard) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activité parlementaire", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityScorecard("${dashboard.presencePercent}%", "Présence", Modifier.weight(1f))
                ActivityScorecard(dashboard.votesParticipated.toString(), "Votes", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityScorecard("N/D", "Interv.", Modifier.weight(1f))
                ActivityScorecard("N/D", "Amend.", Modifier.weight(1f))
            }
            ComparisonBar("Groupe", dashboard.presencePercent)
            ComparisonBar("Assemblée", null)
        }
    }
}

@Composable
private fun GroupInfluenceCard(dashboard: GroupCivicDashboard) {
    val total = (dashboard.wonVotes + dashboard.lostVotes).coerceAtLeast(1)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Influence législative", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${dashboard.influencePercent}%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (dashboard.wonVotes > 0) Box(Modifier.weight(dashboard.wonVotes.toFloat() / total).height(14.dp).background(Color(0xFF2F6F73)))
                if (dashboard.lostVotes > 0) Box(Modifier.weight(dashboard.lostVotes.toFloat() / total).height(14.dp).background(Color(0xFFB05D4D)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistributionMetric("${percentLabel(dashboard.wonVotes, total)}%", "Gagnés", Modifier.weight(1f))
                DistributionMetric("${percentLabel(dashboard.lostVotes, total)}%", "Perdus", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GroupIndependentDeputiesCard(dashboard: GroupCivicDashboard, onOpenDeputy: (Depute) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Députés les plus indépendants", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (dashboard.mostIndependentDeputies.isEmpty()) Text("Aucun vote dissident disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            dashboard.mostIndependentDeputies.take(10).forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { onOpenDeputy(item.depute) }, verticalAlignment = Alignment.CenterVertically) {
                    DeputyPhoto(item.depute, sizeDp = 44)
                    Spacer(Modifier.width(10.dp))
                    Text(item.depute.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.dissidentPercent}%\nDissident", fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun GroupMembersCard(members: List<com.citizeneye.data.GroupMemberSummary>, query: String, onQuery: (String) -> Unit, sort: String, onSort: (String) -> Unit, onOpenDeputy: (Depute) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Membres", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Rechercher") })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Nom", "Présence", "Activité", "Alignement").forEach { label ->
                    FilterChip(selected = sort == label, onClick = { onSort(label) }, label = { Text(label, fontSize = 12.sp) })
                }
            }
            members.take(30).forEach { member ->
                Row(Modifier.fillMaxWidth().clickable { onOpenDeputy(member.depute) }, verticalAlignment = Alignment.CenterVertically) {
                    DeputyPhoto(member.depute, sizeDp = 38)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.depute.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Présence ${member.presencePercent}% · Alignement ${member.alignmentPercent}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupAssemblyRepresentationCard(dashboard: GroupCivicDashboard) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Poids dans l’Assemblée", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${dashboard.deputyCount} sièges", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("${dashboard.assemblySharePercent}% de l’Assemblée", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            MiniHemicycle(dashboard.deputyCount, dashboard.assemblySeatCount)
        }
    }
}

@Composable
private fun MiniHemicycle(groupSeats: Int, assemblySeats: Int) {
    val dots = 60
    val activeDots = ((groupSeats.toDouble() / assemblySeats.coerceAtLeast(1).toDouble()) * dots).roundToInt().coerceIn(0, dots)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(5) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(12) { col ->
                    val index = row * 12 + col
                    Box(Modifier.padding(2.dp).size(10.dp).clip(CircleShape).background(if (index < activeDots) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

private fun percentLabel(value: Int, total: Int): Int = if (total <= 0) 0 else ((value.toDouble() / total.toDouble()) * 100).roundToInt()

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
                    items(uiState.votes.datedFirst()) { vote -> UpcomingVoteCard(vote, onClick = { onOpenDetail(vote) }) }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(vote.expectedDateLabel, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                UpcomingStatusChip(vote.status)
            }
            Text(vote.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(vote.calendarMetaLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${vote.currentStage} · ${vote.shortTopicLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (vote.isContactMomentRelevant) Text("Contact pertinent", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UpcomingVoteDetailScreen(vote: UpcomingVote, depute: Depute, onOpenSource: (String) -> Unit, onOpenEmailComposer: (UpcomingVote, Depute) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(vote.title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        item { Text(vote.citizenSummary, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpcomingStatusChip(vote.status)
                SimpleChip(vote.currentStage)
            }
            Spacer(Modifier.height(8.dp))
            SimpleChip(vote.expectedDateLabel)
            vote.chamber?.let { SimpleChip(it) }
            SimpleChip(vote.eventTypeLabel())
        }
        item { UpcomingTimeline(vote) }
        item { BulletCard("Pourquoi ça compte", upcomingBullets(vote)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!vote.isContactMomentRelevant) {
                    Text("Moment de contact moins prioritaire", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                if (depute.email == null) {
                    Text("Aucune adresse email officielle n’est disponible pour ce député.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Button(onClick = { onOpenEmailComposer(vote, depute) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Écrire à mon député")
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
    val steps = vote.legislativeTimeline
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Où en est le texte ?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (steps.isEmpty()) {
                Text("Aucune étape officielle structurée disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                steps.forEach { step ->
                    Text(
                        text = "${if (step.current) "●" else if (step.completed) "✓" else "○"} ${step.label}",
                        fontWeight = if (step.current) FontWeight.Bold else FontWeight.Normal,
                        color = if (step.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
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
private fun CitizenEyeSupportDrawer(onSupport: () -> Unit) {
    ModalDrawerSheet {
        Column(
            Modifier
                .width(308.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("CitizenEye", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Rendre le travail parlementaire plus lisible, sans score partisan ni interprétation automatique.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Text("Pourquoi ce projet", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(
                "CitizenEye aide à retrouver son député, suivre les votes publics et préparer une prise de contact à partir de sources officielles.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Text("Principe", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(
                "Des faits, des liens vers les sources, et des résumés courts. Pas de notation politique, pas de recommandation de vote.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Button(onClick = onSupport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Soutenir avec Buy Me a Coffee")
            }
        }
    }
}

@Composable
private fun AnimatedHelpIcon(animationTrigger: Int) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.help))
    val progress = remember { Animatable(0f) }

    LaunchedEffect(animationTrigger, animationsEnabled, composition) {
        progress.snapTo(0f)
        if (animationTrigger > 0 && animationsEnabled && composition != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = LinearEasing)
            )
            progress.snapTo(0f)
        }
    }

    LottieAnimation(
        composition = composition,
        progress = { progress.value },
        modifier = Modifier.size(24.dp)
    )
}

@Composable
private fun CitizenEyeBrandLogo(animationTrigger: Int) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.eye))
    val progress = remember { Animatable(0f) }

    LaunchedEffect(animationTrigger, animationsEnabled, composition) {
        progress.snapTo(0f)
        if (animationTrigger > 0 && animationsEnabled && composition != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = LinearEasing)
            )
            progress.snapTo(0f)
        }
    }

    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
        LottieAnimation(
            composition = composition,
            progress = { progress.value },
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun CitizenEyeBottomNavigation(activeTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    var animationTriggerByTab by remember { mutableStateOf(MainTab.values().associateWith { 0 }) }

    NavigationBar {
        MainTab.values().forEach { tab ->
            NavigationBarItem(
                selected = activeTab == tab,
                onClick = {
                    animationTriggerByTab = animationTriggerByTab + (tab to ((animationTriggerByTab[tab] ?: 0) + 1))
                    onTabSelected(tab)
                },
                icon = {
                    AnimatedNavigationIcon(
                        iconRawRes = tab.iconRawRes,
                        animationTrigger = animationTriggerByTab[tab] ?: 0
                    )
                },
                label = { Text(tab.label) }
            )
        }
    }
}

@Composable
private fun AnimatedNavigationIcon(iconRawRes: Int, animationTrigger: Int) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(iconRawRes))
    val progress = remember { Animatable(0f) }

    LaunchedEffect(animationTrigger, animationsEnabled, composition) {
        progress.snapTo(0f)
        if (animationTrigger > 0 && animationsEnabled && composition != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = LinearEasing)
            )
            progress.snapTo(0f)
        }
    }

    LottieAnimation(
        composition = composition,
        progress = { progress.value },
        modifier = Modifier.size(26.dp)
    )
}

@Composable
private fun CompactDeputyCard(match: DeputeMatch, onOpenDeputy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDeputy() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DeputyPhoto(match.depute, sizeDp = 68)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(match.depute.name, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                GroupBadge(match.depute.displayPoliticalGroupShort, match.depute.displayPoliticalGroupFull)
                Text(match.depute.displayDepartment, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
    val groupLabel = vote.groupPosition?.groupMajorityPosition?.label?.uppercase() ?: "N/D"
    val alignment = vote.groupAlignment
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOuvrirDetails() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(vote.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                ImpactBadge(vote.importance.level)
            }
            VoteResultBar(vote.voteBreakdown)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoteHighlight("Mon député", positionLabel, Modifier.weight(1f), emphasized = true)
                VoteHighlight("Groupe", groupLabel, Modifier.weight(1f), emphasized = false)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AlignmentBadge(alignment)
                Text(vote.date.compactDate(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Résultat : $resultLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun VoteResultBar(breakdown: com.citizeneye.data.VoteBreakdown?) {
    val pour = breakdown?.forCount ?: 0
    val contre = breakdown?.againstCount ?: 0
    val abst = breakdown?.abstentionCount ?: 0
    val total = (pour + contre + abst).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (pour > 0) Box(Modifier.weight(pour.toFloat() / total).height(10.dp).background(positionColor(VotePosition.POUR)))
            if (contre > 0) Box(Modifier.weight(contre.toFloat() / total).height(10.dp).background(positionColor(VotePosition.CONTRE)))
            if (abst > 0) Box(Modifier.weight(abst.toFloat() / total).height(10.dp).background(positionColor(VotePosition.ABSTENTION)))
        }
        if (breakdown != null) {
            Text("POUR $pour   CONTRE $contre   ABS $abst", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun VoteHighlight(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun AlignmentBadge(alignment: com.citizeneye.data.VoteGroupAlignment) {
    Surface(shape = RoundedCornerShape(999.dp), color = if (alignment == com.citizeneye.data.VoteGroupAlignment.DISSIDENT) Color(0xFFFFF1D6) else MaterialTheme.colorScheme.primaryContainer) {
        Text("${alignment.icon} ${alignment.label}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    VotePosition.POUR -> Color(0xFF2F6F73)
    VotePosition.CONTRE -> Color(0xFF7A4D63)
    VotePosition.ABSTENTION -> Color(0xFF8B7B45)
    VotePosition.NON_VOTANT -> Color(0xFF8D96A3)
}


