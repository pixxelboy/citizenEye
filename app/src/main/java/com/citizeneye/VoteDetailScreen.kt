package com.citizeneye

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.citizeneye.data.ExternalResourcesState
import com.citizeneye.data.GroupVotePosition
import com.citizeneye.data.OfficialSource
import com.citizeneye.data.ParentTextDetails
import com.citizeneye.data.VoteBreakdown
import com.citizeneye.data.VoteDetail
import com.citizeneye.data.VotePosition
import com.citizeneye.data.VoteSubjectType
import com.citizeneye.data.badgeLabel

sealed interface VoteDetailUiState {
    data object Loading : VoteDetailUiState
    data class Success(val detail: VoteDetail) : VoteDetailUiState
    data class Error(val message: String) : VoteDetailUiState
}

@Composable
fun VoteDetailScreen(
    voteDetailUiState: VoteDetailUiState,
    onRetour: () -> Unit,
    onOuvrirUrl: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (voteDetailUiState) {
        VoteDetailUiState.Loading -> DetailLoadingState(onRetour)
        is VoteDetailUiState.Error -> DetailErrorState(voteDetailUiState.message, onRetour, onRetry)
        is VoteDetailUiState.Success -> VoteDetailContent(voteDetailUiState.detail, onRetour, onOuvrirUrl)
    }
}

@Composable
private fun DetailLoadingState(onRetour: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Chargement du détail du vote", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("CitizenEye prépare le contexte officiel de ce scrutin public.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetour) { Text("Retour aux votes") }
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
private fun VoteDetailContent(detail: VoteDetail, onRetour: () -> Unit, onOuvrirUrl: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick = onRetour) { Text("Retour aux votes") }
            HeroCard(detail)
        }
        item {
            DetailSectionCard(title = "Titre officiel") {
                Text(detail.officialTitle, lineHeight = 21.sp)
            }
        }
        item {
            DetailSectionCard(title = "Comprendre ce vote") {
                Text(detail.subjectExplanation, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                Text("Concrètement : ${detail.voteEffectExplanation}", fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
                if (detail.subjectType == VoteSubjectType.NO_CONFIDENCE_MOTION || detail.subjectType == VoteSubjectType.PROCEDURAL_MOTION) {
                    Spacer(Modifier.height(10.dp))
                    NoticeText("Attention au sens de POUR/CONTRE ici : les votes de procédure et de censure peuvent déterminer si un texte poursuit son examen, est bloqué, ou met en cause le Gouvernement.")
                }
                if (detail.subjectType == VoteSubjectType.AMENDMENT) {
                    Spacer(Modifier.height(10.dp))
                    NoticeText("Ce vote ne portait pas sur toute la loi. Il portait sur une modification proposée à une partie du texte.")
                }
            }
        }
        item { RelatedTextCard(detail.parentText, detail.sourceUrl, onOuvrirUrl) }
        if (detail.amendment != null || detail.subjectType == VoteSubjectType.AMENDMENT) item { AmendementCard(detail, onOuvrirUrl) }
        if (detail.article != null || detail.subjectType == VoteSubjectType.ARTICLE) item { ArticleCard(detail, onOuvrirUrl) }
        if (detail.motion != null || detail.subjectType == VoteSubjectType.NO_CONFIDENCE_MOTION || detail.subjectType == VoteSubjectType.PROCEDURAL_MOTION) item { MotionCard(detail, onOuvrirUrl) }
        item { VoteRésultatCard(detail.voteBreakdown, detail.result) }
        item { GroupePositionCard(detail.groupPosition, detail.deputyPosition) }
        item { OfficialSourcesCard(detail.officialSources, onOuvrirUrl) }
        item { LearnMoreSection(detail.externalResources, onOuvrirUrl) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeroCard(detail: VoteDetail) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            VoteSubjectBadge(detail.subjectType)
            Spacer(Modifier.height(12.dp))
            Text(detail.plainLanguageTitle ?: detail.officialTitle, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Scrutin public n° ${detail.voteNumber}", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
            Text("${detail.result} · ${detail.date}", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
            Spacer(Modifier.height(14.dp))
            Text("Votre député a voté : ${detail.deputyPosition.label}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Concrètement : ${detail.voteEffectExplanation}", lineHeight = 21.sp)
        }
    }
}

@Composable
fun DetailSectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
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
            EmptyText("Le texte concerné n’est pas encore disponible dans CitizenEye pour ce scrutin public. Ouvrez la source officielle pour le contexte complet.")
            sourceUrl?.let { OuvrirButton(it, onOuvrirUrl) }
        } else {
            parentText.title?.let { LabeledText("Titre", it) }
            parentText.type?.let { LabeledText("Type", it) }
            parentText.procedureStage?.let { LabeledText("Étape", it) }
            parentText.commissionName?.let { LabeledText("Commission", it) }
            if (parentText.rapporteurs.isNotEmpty()) LabeledText("Rapporteurs", parentText.rapporteurs.joinToString())
            parentText.dossierUrl?.let { OuvrirButton(it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun AmendementCard(detail: VoteDetail, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Amendement") {
        val amendment = detail.amendment
        if (amendment == null) {
            EmptyText("Ce scrutin public semble concerner un amendement, mais le détail complet de l’amendement n’est pas encore disponible dans l’app.")
        } else {
            amendment.number?.let { LabeledText("Numéro", it) }
            amendment.status?.let { LabeledText("Statut", it) }
            if (amendment.authors.isNotEmpty()) LabeledText("Auteurs", amendment.authors.joinToString())
            amendment.group?.let { LabeledText("Groupe", it) }
            amendment.objectText?.let { LabeledText("Objet", it) }
            amendment.proposedChangeText?.let { LabeledText("Modification proposée", it) }
            amendment.explanatoryStatement?.let { LabeledText("Exposé sommaire", it) }
            amendment.sourceUrl?.let { OuvrirButton(it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun ArticleCard(detail: VoteDetail, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Article") {
        val article = detail.article
        if (article == null) {
            EmptyText("Ce scrutin public semble concerner un article, mais le détail complet de l’article n’est pas encore disponible dans l’app.")
        } else {
            article.number?.let { LabeledText("Numéro", it) }
            article.title?.let { LabeledText("Titre", it) }
            article.summary?.let { LabeledText("Résumé", it) }
            article.sourceUrl?.let { OuvrirButton(it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun MotionCard(detail: VoteDetail, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Motion") {
        val motion = detail.motion
        if (motion == null) {
            EmptyText("Ce scrutin public semble concerner une motion, mais le détail complet de la motion n’est pas encore disponible dans l’app.")
        } else {
            motion.type?.let { LabeledText("Type", it) }
            if (motion.authors.isNotEmpty()) LabeledText("Auteurs", motion.authors.joinToString())
            motion.explanation?.let { LabeledText("Explication", it) }
            motion.politicalEffectExplanation?.let { LabeledText("Effet politique", it) }
            motion.sourceUrl?.let { OuvrirButton(it, onOuvrirUrl) }
        }
    }
}

@Composable
private fun VoteRésultatCard(breakdown: VoteBreakdown?, result: String) {
    DetailSectionCard(title = "Résultat du vote") {
        LabeledText("Résultat", breakdown?.resultLabel ?: result)
        if (breakdown == null) {
            EmptyText("Le décompte détaillé du vote n’est pas encore disponible dans CitizenEye pour ce scrutin public.")
        } else {
            CountRow("Nombre de votants", breakdown.totalVoters)
            CountRow("Pour", breakdown.forCount)
            CountRow("Contre", breakdown.againstCount)
            CountRow("Abstentions", breakdown.abstentionCount)
            CountRow("Non-votants", breakdown.nonVotingCount)
            CountRow("Majorité absolue", breakdown.absoluteMajority)
        }
    }
}

@Composable
private fun GroupePositionCard(group: GroupVotePosition?, deputyPosition: VotePosition) {
    DetailSectionCard(title = "Votre député et son groupe") {
        LabeledText("Position du député", deputyPosition.label)
        if (group == null) {
            EmptyText("CitizenEye n’a pas encore assez d’informations pour comparer ce vote à la position majoritaire du groupe.")
        } else {
            LabeledText("Groupe parlementaire", group.groupName)
            LabeledText("Position majoritaire du groupe", group.groupMajorityPosition?.label ?: "Non disponible")
            LabeledText("Vote aligné avec la majorité de son groupe", group.deputyVotedLikeGroup?.let { if (it) "Oui" else "Non" } ?: "Non disponible")
            CountRow("Groupe pour", group.forCount)
            CountRow("Groupe contre", group.againstCount)
            CountRow("Groupe abstentions", group.abstentionCount)
            CountRow("Non-votants du groupe", group.nonVotingCount)
        }
    }
}

@Composable
private fun OfficialSourcesCard(sources: List<OfficialSource>, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(title = "Sources officielles") {
        if (sources.isEmpty()) {
            EmptyText("Aucune source officielle ouverte n’est disponible pour ce vote.")
        } else {
            sources.forEach { source ->
                ResourceRow(source.label, source.description, null, "Source officielle", source.url, onOuvrirUrl)
            }
        }
    }
}

@Composable
private fun LearnMoreSection(resources: ExternalResourcesState, onOuvrirUrl: (String) -> Unit) {
    DetailSectionCard(
        title = "Approfondir",
        subtitle = "Ces ressources externes ne sont pas des sources officielles CitizenEye. Elles servent à explorer le débat public autour du texte."
    ) {
        when (resources) {
            ExternalResourcesState.NotLoaded -> EmptyText("Les ressources externes ne sont pas encore chargées.")
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
                        ResourceRow(it.title, it.description, it.publishedAt, "Article · ${it.sourceName ?: "Source inconnue"}", it.url, onOuvrirUrl)
                    }
                }
                ExternalSubsection("Vidéos") {
                    if (resources.videos.isEmpty()) EmptyText("Aucune vidéo pertinente trouvée pour ce texte.") else resources.videos.take(3).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Vidéo · ${it.channelName ?: "Chaîne inconnue"}", it.url, onOuvrirUrl)
                    }
                }
                ExternalSubsection("Liens utiles") {
                    if (resources.webLinks.isEmpty()) EmptyText("Aucun lien externe utile trouvé pour ce texte.") else resources.webLinks.take(3).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Lien web · ${it.sourceName ?: "Source externe"}", it.url, onOuvrirUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalSubsection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(title, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    content()
}

@Composable
private fun ResourceRow(title: String, description: String?, date: String?, typeLabel: String, url: String, onOuvrirUrl: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(typeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(title, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        date?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        }
        TextButton(onClick = { onOuvrirUrl(url) }) { Text("Ouvrir") }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, lineHeight = 20.sp)
    }
}

@Composable
private fun CountRow(label: String, value: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value?.toString() ?: "Non disponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
}

@Composable
private fun NoticeText(message: String) {
    Text(message, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
}

@Composable
private fun OuvrirButton(url: String, onOuvrirUrl: (String) -> Unit) {
    TextButton(onClick = { onOuvrirUrl(url) }) { Text("Ouvrir") }
}
