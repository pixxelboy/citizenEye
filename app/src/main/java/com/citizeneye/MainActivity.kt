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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.citizeneye.data.StaticCitizenEyeDatasetClient
import com.citizeneye.data.VoteConcern
import com.citizeneye.data.VotePosition
import com.citizeneye.data.Vote
import com.citizeneye.data.classifyVoteSubjectType
import com.citizeneye.data.formatPositionInContext
import com.citizeneye.data.formatVoteResultLabel
import com.citizeneye.ui.CitizenEyeLoader
import com.citizeneye.ui.theme.CitizenEyeTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    var selectedVote by remember { mutableStateOf<Vote?>(null) }
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun runLookup() {
        val currentQuery = query.trim()
        state = LookupState.Loading("Recherche de la commune…")
        showingStats = false
        selectedVote = null
        scope.launch { state = repository.lookup(currentQuery) }
    }

    fun loadVotes(query: String, commune: com.citizeneye.data.Commune, depute: Depute) {
        state = LookupState.Loading("Préparation des résultats…")
        showingStats = false
        selectedVote = null
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (selectedVote != null) "Comprendre ce vote" else "CitizenEye", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (selectedVote != null) TextButton(onClick = { selectedVote = null }) { Text("Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
                is LookupState.Loaded -> if (selectedVote != null) {
                    VoteDetailScreen(
                        voteDetailUiState = voteDetailUiState,
                        deputyEmail = current.match.depute.email,
                        onRetour = { selectedVote = null },
                        onOuvrirUrl = ::openExternalUrl,
                        onOpenEmailDraft = ::openEmailDraft,
                        onRetry = ::retryVoteDetails
                    )
                } else if (showingStats) {
                    DeputyStatsScreen(match = current.match, onRetour = { showingStats = false })
                } else {
                    HomeScreen(
                        match = current.match,
                        onOuvrirStats = { showingStats = true },
                        onOuvrirVoteDetails = { openVoteDetails(it, current.match.depute) },
                        onLoadMoreVotes = {
                            state = LookupState.Loaded(current.match.withMoreVisibleVotes())
                        },
                        onReset = { state = LookupState.Idle; query = ""; showingStats = false }
                    )
                }
            }
        }
    }
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
            Text("Comprendre les votes de votre député.\nSans compte.", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Entrez votre ville ou votre code postal pour retrouver votre circonscription, votre député et ses votes publics à l’Assemblée nationale.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                lineHeight = 23.sp
            )
            Spacer(Modifier.height(28.dp))
            InfoCard("Données publiques officielles", "Commune : geo.api.gouv.fr.\nDéputés et scrutins : Open Data de l’Assemblée nationale.\nSi plusieurs circonscriptions sont possibles, CitizenEye vous demande de choisir au lieu de deviner.")
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
                supportingText = { Text(error ?: if (previewLoading) "Recherche de votre commune…" else "Exemple : 92000 ou Nanterre. Vous pourrez confirmer la circonscription ensuite.") },
                isError = error != null
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onGeolocate, enabled = !geoLoading && !loading) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (geoLoading) "Localisation…" else "Utiliser ma localisation")
            }
            Text("Votre position sert uniquement à identifier votre circonscription.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
private fun HomeScreen(match: DeputeMatch, onOuvrirStats: () -> Unit, onOuvrirVoteDetails: (Vote) -> Unit, onLoadMoreVotes: () -> Unit, onReset: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("${match.commune.name} · données officielles", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(match.depute.constituencyLabel, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset) { Text("Changer de ville") }
        }
        item { RepresentativeCard(match, onOuvrirStats) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Votes publics de la législature", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.Ballot, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text("${match.recentVotes.size} scrutins affichés sur ${match.totalLegislatureVotes} disponibles", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (match.recentVotes.isEmpty()) {
            item { InfoCard("Aucun vote nominatif récent", "Le député n’apparaît pas dans les scrutins publics téléchargés. Réessayez plus tard ou vérifiez la source Assemblée nationale.") }
        } else {
            items(match.recentVotes) { vote -> VoteCard(vote, onOuvrirDetails = { onOuvrirVoteDetails(vote) }) }
            if (match.hasMoreVotes) {
                item {
                    Button(onClick = onLoadMoreVotes, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Charger 20 votes de plus")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RepresentativeCard(match: DeputeMatch, onOuvrirStats: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOuvrirStats() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeputyPhoto(match.depute, sizeDp = 72)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(match.depute.name, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    Text(match.depute.displayPoliticalGroupShort, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                    Spacer(Modifier.height(8.dp))
                    Text(match.depute.constituencyLabel, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Voir les statistiques", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOuvrirDetails() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            val subjectType = classifyVoteSubjectType(vote.title)
            val resultLabel = formatVoteResultLabel(subjectType, vote.result)
            val positionLabel = formatPositionInContext(vote.deputePosition, subjectType)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Ballot, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("${vote.date} · $resultLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoteConcernChip(vote.concern)
                Spacer(Modifier.width(8.dp))
                Text(vote.concern.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(vote.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(8.dp))
            Text("Position enregistrée : $positionLabel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Résultat : ${vote.summary}", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            Text("Comprendre ce vote", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
