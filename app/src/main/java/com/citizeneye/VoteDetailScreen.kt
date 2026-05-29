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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.citizeneye.data.buildEmailBody
import com.citizeneye.data.buildEmailSubject
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
    onOpenEmailDraft: (String, String, String) -> Unit,
    onRetry: () -> Unit
) {
    when (voteDetailUiState) {
        VoteDetailUiState.Loading -> DetailLoadingState()
        is VoteDetailUiState.Error -> DetailErrorState(voteDetailUiState.message, onRetour, onRetry)
        is VoteDetailUiState.Success -> VoteDetailContent(voteDetailUiState.detail, deputyEmail, onOuvrirUrl, onOpenEmailDraft)
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
        Text("CitizenEye vérifie les sources officielles disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("Cela peut prendre quelques secondes.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
private fun VoteDetailContent(detail: VoteDetail, deputyEmail: String?, onOuvrirUrl: (String) -> Unit, onOpenEmailDraft: (String, String, String) -> Unit) {
    var showEmailDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HeroCard(detail) }
        item { OfficialTitleCard(detail.officialTitle) }
        item { UnderstandVoteCard(detail) }
        item { RelatedTextCard(detail.parentText, detail.sourceUrl, onOuvrirUrl) }
        if (detail.amendment != null || detail.subjectType == VoteSubjectType.AMENDMENT) item { AmendementCard(detail, onOuvrirUrl) }
        if (detail.article != null || detail.subjectType == VoteSubjectType.ARTICLE) item { ArticleCard(detail, onOuvrirUrl) }
        if (detail.motion != null || detail.subjectType == VoteSubjectType.NO_CONFIDENCE_MOTION || detail.subjectType == VoteSubjectType.PROCEDURAL_MOTION) item { MotionCard(detail, onOuvrirUrl) }
        item { VoteResultCard(detail) }
        item { GroupPositionCard(detail.groupPosition, detail.deputyPosition) }
        item { ContactDeputyCard { showEmailDialog = true } }
        item { OfficialSourcesCard(detail.officialSources, onOuvrirUrl) }
        item { LearnMoreSection(detail.externalResources, onOuvrirUrl) }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (showEmailDialog) {
        PrepareEmailDialog(
            detail = detail,
            deputyEmail = deputyEmail,
            onDismiss = { showEmailDialog = false },
            onOpenEmailDraft = onOpenEmailDraft
        )
    }
}

@Composable
private fun HeroCard(detail: VoteDetail) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            VoteSubjectBadge(detail.subjectType)
            Spacer(Modifier.height(12.dp))
            Text("Scrutin public n°${detail.voteNumber}", fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(formatVoteResultLabel(detail.subjectType, detail.result), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(detail.date, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
            Spacer(Modifier.height(12.dp))
            Text("Position enregistrée : ${formatPositionInContext(detail.deputyPosition, detail.subjectType)}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(buildPositionResultSentence(detail.deputyPosition, detail.subjectType, detail.result), lineHeight = 21.sp)
        }
    }
}

@Composable
private fun OfficialTitleCard(officialTitle: String) {
    DetailSectionCard(title = "Titre officiel du scrutin", subtitle = "Repris tel quel depuis l’Assemblée nationale.") {
        Text(officialTitle, lineHeight = 21.sp)
    }
}

@Composable
private fun UnderstandVoteCard(detail: VoteDetail) {
    DetailSectionCard(title = "Comprendre ce vote") {
        Text(detail.subjectExplanation, lineHeight = 21.sp)
        Spacer(Modifier.height(10.dp))
        Text("Dans ce scrutin, ${detail.voteEffectExplanation.replaceFirstChar { it.lowercase() }}", fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
        Spacer(Modifier.height(10.dp))
        NoticeText("À retenir\n${buildTakeaway(detail.subjectType)}")
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
private fun ContactDeputyCard(onPrepare: () -> Unit) {
    DetailSectionCard(title = "Vous souhaitez réagir à ce vote ?") {
        Text("CitizenEye peut préparer un email clair, respectueux et sourcé. Vous pourrez le relire et l’envoyer depuis votre propre application mail.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) { Text("Préparer un email") }
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
private fun PrepareEmailDialog(detail: VoteDetail, deputyEmail: String?, onDismiss: () -> Unit, onOpenEmailDraft: (String, String, String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var subject by remember { mutableStateOf(buildEmailSubject(detail.voteNumber)) }
    var body by remember { mutableStateOf(buildEmailBody(detail.voteNumber, detail.date, detail.officialTitle, formatPositionInContext(detail.deputyPosition, detail.subjectType), detail.sourceUrl)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Préparer un email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(deputyEmail?.let { "À : $it" } ?: "Adresse email officielle non disponible pour ce député.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(subject, { subject = it }, label = { Text("Objet") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 6)
                Text("CitizenEye ne l’enverra pas à votre place. Votre application mail s’ouvrira avec un brouillon que vous pourrez relire, modifier ou supprimer.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
        },
        confirmButton = { Button(enabled = deputyEmail != null, onClick = { deputyEmail?.let { onOpenEmailDraft(it, subject, body) } }) { Text("Ouvrir dans mon application mail") } },
        dismissButton = {
            Column {
                TextButton(onClick = { clipboard.setText(AnnotatedString(body)) }) { Text("Copier le message") }
                deputyEmail?.let { TextButton(onClick = { clipboard.setText(AnnotatedString(it)) }) { Text("Copier l’adresse email") } }
            }
        }
    )
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
