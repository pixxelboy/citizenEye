package com.citizeneye

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.citizeneye.data.ExternalResourcesState
import com.citizeneye.data.GroupVotePosition
import com.citizeneye.data.OfficialSource
import com.citizeneye.data.OfficialSourceType
import com.citizeneye.data.ParentTextDetails
import com.citizeneye.data.VoteBreakdown
import com.citizeneye.data.VoteDetail
import com.citizeneye.data.VotePosition
import com.citizeneye.data.VoteSubjectType
import com.citizeneye.data.badgeLabel

import com.citizeneye.data.buildPositionResultSentence
import com.citizeneye.data.buildTakeaway
import com.citizeneye.data.formatPositionInContext
import com.citizeneye.data.formatVoteResultLabel
import com.citizeneye.data.safeDisplayValue
import com.citizeneye.ui.CitizenEyeLoader

sealed interface VoteDetailUiState {
    data object Loading : VoteDetailUiState
    data class Success(val detail: VoteDetail) : VoteDetailUiState
    data class Error(val message: String) : VoteDetailUiState
}

@Composable
fun VoteDetailScreen(
    voteDetailUiState: VoteDetailUiState,
    deputyEmail: String?,
    onRetour: () -> Unit,
    onOuvrirUrl: (String) -> Unit,
    onOpenCivicEmailComposer: () -> Unit,
    onRetry: () -> Unit
) {
    when (voteDetailUiState) {
        VoteDetailUiState.Loading -> DetailLoadingState()
        is VoteDetailUiState.Error -> DetailErrorState(voteDetailUiState.message, onRetour, onRetry)
        is VoteDetailUiState.Success -> VoteDetailContent(voteDetailUiState.detail, deputyEmail, onOuvrirUrl, onOpenCivicEmailComposer)
    }
}

@Composable
private fun DetailLoadingState() {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CitizenEyeLoader(
            size = 88.dp,
            label = "Chargement du détail du vote…"
        )
        Spacer(Modifier.height(8.dp))
        Text("Sources officielles…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailErrorState(message: String, onRetour: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        DetailSectionCard(title = "Impossible de charger le détail du vote") {
            Text(message.ifBlank { "Impossible de charger le détail du vote. Réessayer." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Réessayer") }
            TextButton(onClick = onRetour) { Text("Retour aux votes") }
        }
    }
}

@Composable
private fun VoteDetailContent(detail: VoteDetail, deputyEmail: String?, onOuvrirUrl: (String) -> Unit, onOpenCivicEmailComposer: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutcomeSummaryCard(detail) }
        item { PoliticalAlignmentCard(detail.groupPosition, detail.deputyPosition) }
        item { CompactResultsCard(detail.voteBreakdown) }
        item { BulletCard("Pourquoi ce vote comptait", voteMatterBullets(detail)) }
        item { CivicPastVoteActionCard(deputyEmail = deputyEmail, onOpenComposer = onOpenCivicEmailComposer) }
        item { CollapsedSourcesCard(detail.officialSources, detail.sourceUrl, onOuvrirUrl) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun OutcomeSummaryCard(detail: VoteDetail) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Députée : ${formatPositionInContext(detail.deputyPosition, detail.subjectType).uppercase()}", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            Text("Vote ${formatVoteResultLabel(detail.subjectType, detail.result).uppercase()}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(detail.date, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
            Text(detail.officialTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun PoliticalAlignmentCard(group: GroupVotePosition?, deputyPosition: VotePosition) {
    DetailSectionCard(title = "Position du groupe") {
        val groupLabel = group?.groupMajorityPosition?.label?.uppercase() ?: "N/D"
        val deputyLabel = deputyPosition.label.uppercase()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VotePositionPill("Groupe", groupLabel, Modifier.weight(1f))
            VotePositionPill("Député", deputyLabel, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        val aligned = group?.deputyVotedLikeGroup
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (aligned == false) Color(0xFFFFF1D6) else MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                when (aligned) {
                    true -> "✓ Vote aligné"
                    false -> "⚠ Vote dissident"
                    null -> "— Alignement N/D"
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VotePositionPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CompactResultsCard(breakdown: VoteBreakdown?) {
    DetailSectionCard(title = "Résultats") {
        if (breakdown == null) {
            EmptyText("Décompte indisponible")
        } else {
            CountRow("Pour", breakdown.forCount)
            CountRow("Contre", breakdown.againstCount)
            CountRow("Abst.", breakdown.abstentionCount)
        }
    }
}

@Composable
private fun CollapsedSourcesCard(sources: List<OfficialSource>, sourceUrl: String?, onOuvrirUrl: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    DetailSectionCard(title = "Sources") {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Masquer" else "Afficher les sources") }
        if (expanded) {
            if (sources.isEmpty() && sourceUrl == null) EmptyText("Aucune source ouvrable")
            sources.forEach { source -> ResourceRow(source.label, safeDisplayValue(source.description) ?: "Assemblée nationale", null, officialSourceCta(source), source.url, onOuvrirUrl) }
            if (sources.isEmpty()) sourceUrl?.let { ActionButton("Ouvrir le scrutin", it, onOuvrirUrl) }
        }
    }
}

private fun voteMatterBullets(detail: VoteDetail): List<String> = listOfNotNull(
    detail.parentText?.title ?: detail.officialTitle,
    detail.voteEffectExplanation,
    detail.groupPosition?.groupMajorityPosition?.let { "Groupe : ${it.label}" }
).map { it.compactBullet() }.filter { it.isNotBlank() }.take(3)

private fun String.compactBullet(maxChars: Int = 120): String =
    replace("\n", " ").replace(Regex("\\s+"), " ").trim().let { value ->
        if (value.length <= maxChars) value else value.take(maxChars).trimEnd() + "…"
    }

@Composable
private fun BulletCard(title: String, bullets: List<String>) {
    DetailSectionCard(title = title) {
        bullets.take(3).forEach { Text("• $it", lineHeight = 20.sp) }
    }
}

@Composable
fun DetailSectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Spacer(Modifier.height(4.dp)); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp) }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun VoteSubjectBadge(subjectType: VoteSubjectType) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(subjectType.badgeLabel(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RelatedTextCard(parentText: ParentTextDetails?, sourceUrl: String?, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Texte concerné") {
        if (parentText == null) {
            EmptyText("Le texte parent n’est pas encore disponible dans CitizenEye pour ce scrutin. Consultez la source officielle du scrutin pour le contexte complet.")
            sourceUrl?.let { ActionButton("Ouvrir le scrutin", it, onOuvrirUrl) }
        } else {
            safeDisplayValue(parentText.title)?.let { Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp) }
            listOfNotNull(parentText.type, parentText.procedureStage).mapNotNull(::safeDisplayValue).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" — "), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            safeDisplayValue(parentText.commissionName)?.let { LabeledText("Commission", it) }
            parentText.rapporteurs.mapNotNull(::safeDisplayValue).takeIf { it.isNotEmpty() }?.let { LabeledText(if (it.size > 1) "Rapporteurs" else "Rapporteur", it.joinToString()) }
            parentText.dossierUrl?.let { ActionButton("Ouvrir le dossier législatif officiel", it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun AmendementCard(detail: VoteDetail, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Amendement") {
        val amendment = detail.amendment
        if (amendment == null) EmptyText("Ce scrutin public semble concerner un amendement, mais le détail complet de l’amendement n’est pas encore disponible dans l’app.") else {
            safeDisplayValue(amendment.number)?.let { LabeledText("Numéro", it) }
            safeDisplayValue(amendment.status)?.let { LabeledText("Statut", formatVoteResultLabel(VoteSubjectType.AMENDMENT, it).removePrefix("Amendement ").replaceFirstChar { c -> c.uppercase() }) }
            amendment.authors.mapNotNull(::safeDisplayValue).takeIf { it.isNotEmpty() }?.let { LabeledText(if (it.size > 1) "Auteurs" else "Auteur", it.joinToString()) }
            safeDisplayValue(amendment.group)?.let { LabeledText("Groupe", it) }
            safeDisplayValue(amendment.objectText)?.let { LabeledText("Objet", it) }
            safeDisplayValue(amendment.proposedChangeText)?.let { LabeledText("Modification proposée", it) }
            safeDisplayValue(amendment.explanatoryStatement)?.let { LabeledText("Exposé sommaire", it) }
            amendment.sourceUrl?.let { ActionButton("Ouvrir l’amendement officiel", it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun ArticleCard(detail: VoteDetail, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Article") {
        val article = detail.article
        if (article == null) EmptyText("Ce scrutin public semble concerner un article, mais le détail complet de l’article n’est pas encore disponible dans l’app.") else {
            safeDisplayValue(article.number)?.let { LabeledText("Numéro", it) }
            safeDisplayValue(article.title)?.let { LabeledText("Titre", it) }
            safeDisplayValue(article.summary)?.let { LabeledText("Résumé", it) }
            article.sourceUrl?.let { ActionButton("Voir la source Assemblée nationale", it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun MotionCard(detail: VoteDetail, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Motion") {
        val motion = detail.motion
        if (motion == null) EmptyText("Ce scrutin public semble concerner une motion, mais le détail complet de la motion n’est pas encore disponible dans l’app.") else {
            safeDisplayValue(motion.type)?.let { LabeledText("Type", it) }
            motion.authors.mapNotNull(::safeDisplayValue).takeIf { it.isNotEmpty() }?.let { LabeledText("Auteurs", it.joinToString()) }
            safeDisplayValue(motion.explanation)?.let { LabeledText("Explication", it) }
            safeDisplayValue(motion.politicalEffectExplanation)?.let { LabeledText("Effet", it) }
            motion.sourceUrl?.let { ActionButton("Voir la source Assemblée nationale", it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun VoteResultCard(detail: VoteDetail) {
    DetailSectionCard(title = "Résultat du scrutin") {
        LabeledText("Résultat", formatVoteResultLabel(detail.subjectType, detail.voteBreakdown?.resultLabel ?: detail.result))
        val breakdown = detail.voteBreakdown
        if (breakdown == null) EmptyText("Le décompte détaillé du vote n’est pas encore disponible dans CitizenEye pour ce scrutin public.") else {
            CountRow("Pour", breakdown.forCount)
            CountRow("Contre", breakdown.againstCount)
            CountRow("Abstentions", breakdown.abstentionCount)
            CountRow("Nombre de votants", breakdown.totalVoters)
            CountRow("Non-votants", breakdown.nonVotingCount)
            CountRow("Majorité absolue", breakdown.absoluteMajority)
            Spacer(Modifier.height(8.dp))
            Text("Position enregistrée : ${formatPositionInContext(detail.deputyPosition, detail.subjectType)}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GroupPositionCard(group: GroupVotePosition?, deputyPosition: VotePosition) {
    DetailSectionCard(title = "Position du groupe parlementaire") {
        if (group == null || group.groupMajorityPosition == null) {
            EmptyText("CitizenEye ne dispose pas encore d’assez d’informations pour comparer ce vote avec la position majoritaire du groupe.")
            LabeledText("Position enregistrée", deputyPosition.label)
        } else {
            val majority = group.groupMajorityPosition.label.lowercase()
            Text("La majorité du groupe a voté : $majority.", lineHeight = 20.sp)
            Text("Position enregistrée : ${deputyPosition.label}.", lineHeight = 20.sp)
            val aligned = group.deputyVotedLikeGroup
            if (aligned != null) Text(if (aligned) "Ce vote est aligné avec la majorité de son groupe." else "Ce vote n’est pas aligné avec la majorité de son groupe.", fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Détail du groupe : ${group.forCount ?: 0} pour, ${group.againstCount ?: 0} contre, ${group.abstentionCount ?: 0} abstentions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CivicPastVoteActionCard(deputyEmail: String?, onOpenComposer: () -> Unit) {
    DetailSectionCard(title = "Demander une explication") {
        Text("Préparez un email sourcé à votre député à partir de ce vote.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
        if (deputyEmail == null) {
            Spacer(Modifier.height(8.dp))
            Text("Aucune adresse email officielle n’est disponible pour ce député.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenComposer, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Écrire à mon député") }
    }
}

@Composable
private fun OfficialSourcesCard(sources: List<OfficialSource>, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Sources officielles") {
        if (sources.isEmpty()) EmptyText("Aucune source officielle ouvrable n’est disponible pour ce vote.") else sources.forEach { source ->
            ResourceRow(source.label, safeDisplayValue(source.description) ?: "Assemblée nationale", null, officialSourceCta(source), source.url, onOuvrirUrl)
        }
    }
}

private fun officialSourceCta(source: OfficialSource): String = when (source.sourceType) {
    OfficialSourceType.PUBLIC_VOTE -> "Ouvrir le scrutin"
    OfficialSourceType.LEGISLATIVE_FILE -> "Ouvrir le dossier"
    OfficialSourceType.AMENDMENT -> "Ouvrir l’amendement"
    OfficialSourceType.REPORT -> "Ouvrir le rapport"
    else -> "Voir la source Assemblée nationale"
}

@Composable
private fun LearnMoreSection(resources: ExternalResourcesState, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "En savoir encore plus", subtitle = "Ces ressources externes ne sont pas des sources officielles de CitizenEye. Elles sont proposées pour explorer le débat public autour du texte.") {
        when (resources) {
            ExternalResourcesState.NotLoaded -> EmptyText("Aucune source externe configurée pour le moment.")
            ExternalResourcesState.Loading -> Text("Chargement des ressources externes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is ExternalResourcesState.Error -> EmptyText(resources.message)
            is ExternalResourcesState.Empty -> {
                ExternalSubsection("Articles récents") { EmptyText(if (resources.message == "Aucune source externe n’est configurée pour le moment.") resources.message else "Aucun article récent trouvé pour ce texte.") }
                ExternalSubsection("Vidéos") { EmptyText(if (resources.message == "Aucune source externe n’est configurée pour le moment.") resources.message else "Aucune vidéo pertinente trouvée pour ce texte.") }
                ExternalSubsection("Liens utiles") { EmptyText(resources.message) }
            }
            is ExternalResourcesState.Available -> {
                ExternalSubsection("Articles récents") {
                    if (resources.newsArticles.isEmpty()) EmptyText("Aucun article récent trouvé pour ce texte.") else resources.newsArticles.take(5).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Lire l’article", it.url, onOuvrirUrl, "Article · ${it.sourceName ?: "Source inconnue"}")
                    }
                }
                ExternalSubsection("Vidéos") {
                    if (resources.videos.isEmpty()) EmptyText("Aucune vidéo pertinente trouvée pour ce texte.") else resources.videos.take(3).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Voir la vidéo", it.url, onOuvrirUrl, "Vidéo · ${it.channelName ?: "Chaîne inconnue"}")
                    }
                }
                ExternalSubsection("Liens utiles") {
                    if (resources.webLinks.isEmpty()) EmptyText("Aucun lien externe utile trouvé pour ce texte.") else resources.webLinks.take(3).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Ouvrir le lien", it.url, onOuvrirUrl, "Lien web · ${it.sourceName ?: "Source externe"}")
                    }
                }
            }
        }
    }
}


@Composable
private fun ExternalSubsection(title: String, content: @Composable () -> Unit) { Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); content() }

@Composable
private fun ResourceRow(title: String, description: String?, date: String?, cta: String, url: String, onOuvrirUrl: (String) -> Unit, typeLabel: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).clickable { onOuvrirUrl(url) }.padding(12.dp)) {
        typeLabel?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        Text(title, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        date?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        description?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp) }
        TextButton(onClick = { onOuvrirUrl(url) }) { Text(cta) }
    }
}

@Composable
private fun LabeledText(label: String, value: String) { Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(value, lineHeight = 20.sp) } }

@Composable
private fun CountRow(label: String, value: Int?) { if (value != null) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun EmptyText(message: String) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp) }

@Composable
private fun NoticeText(message: String) { Text(message, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp) }

@Composable
private fun ActionButton(label: String, url: String, onOuvrirUrl: (String) -> Unit) { TextButton(onClick = { onOuvrirUrl(url) }) { Text(label) } }
