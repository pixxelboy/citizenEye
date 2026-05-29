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
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (voteDetailUiState) {
        VoteDetailUiState.Loading -> DetailLoadingState(onBack)
        is VoteDetailUiState.Error -> DetailErrorState(voteDetailUiState.message, onBack, onRetry)
        is VoteDetailUiState.Success -> VoteDetailContent(voteDetailUiState.detail, onBack, onOpenUrl)
    }
}

@Composable
private fun DetailLoadingState(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Loading vote details", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("CitizenEye is preparing official context for this public vote.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onBack) { Text("Back to votes") }
    }
}

@Composable
private fun DetailErrorState(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        DetailSectionCard(title = "Unable to load vote details") {
            Text(message.ifBlank { "Unable to load vote details. Try again." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            TextButton(onClick = onBack) { Text("Back to votes") }
        }
    }
}

@Composable
private fun VoteDetailContent(detail: VoteDetail, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to votes") }
            HeroCard(detail)
        }
        item {
            DetailSectionCard(title = "Official title") {
                Text(detail.officialTitle, lineHeight = 21.sp)
            }
        }
        item {
            DetailSectionCard(title = "Understand this vote") {
                Text(detail.subjectExplanation, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                Text("This means: ${detail.voteEffectExplanation}", fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
                if (detail.subjectType == VoteSubjectType.NO_CONFIDENCE_MOTION || detail.subjectType == VoteSubjectType.PROCEDURAL_MOTION) {
                    Spacer(Modifier.height(10.dp))
                    NoticeText("Pay attention to what FOR/AGAINST means here: procedural and censure votes can affect whether a text continues, is blocked, or challenges the Government.")
                }
                if (detail.subjectType == VoteSubjectType.AMENDMENT) {
                    Spacer(Modifier.height(10.dp))
                    NoticeText("This vote was not about the whole law. It was about a proposed change to part of the text.")
                }
            }
        }
        item { RelatedTextCard(detail.parentText, detail.sourceUrl, onOpenUrl) }
        if (detail.amendment != null || detail.subjectType == VoteSubjectType.AMENDMENT) item { AmendmentCard(detail, onOpenUrl) }
        if (detail.article != null || detail.subjectType == VoteSubjectType.ARTICLE) item { ArticleCard(detail, onOpenUrl) }
        if (detail.motion != null || detail.subjectType == VoteSubjectType.NO_CONFIDENCE_MOTION || detail.subjectType == VoteSubjectType.PROCEDURAL_MOTION) item { MotionCard(detail, onOpenUrl) }
        item { VoteResultCard(detail.voteBreakdown, detail.result) }
        item { GroupPositionCard(detail.groupPosition, detail.deputyPosition) }
        item { OfficialSourcesCard(detail.officialSources, onOpenUrl) }
        item { LearnMoreSection(detail.externalResources, onOpenUrl) }
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
            Text("Public vote no. ${detail.voteNumber}", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold)
            Text("${detail.result} · ${detail.date}", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
            Spacer(Modifier.height(14.dp))
            Text("Your MP voted: ${detail.deputyPosition.label}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("This means: ${detail.voteEffectExplanation}", lineHeight = 21.sp)
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
private fun RelatedTextCard(parentText: ParentTextDetails?, sourceUrl: String?, onOpenUrl: (String) -> Unit) {
    DetailSectionCard(title = "Related text") {
        if (parentText == null) {
            EmptyText("The related text is not available in CitizenEye yet for this public vote. Open the official vote source for the complete context.")
            sourceUrl?.let { OpenButton(it, onOpenUrl) }
        } else {
            parentText.title?.let { LabeledText("Title", it) }
            parentText.type?.let { LabeledText("Type", it) }
            parentText.procedureStage?.let { LabeledText("Stage", it) }
            parentText.commissionName?.let { LabeledText("Commission", it) }
            if (parentText.rapporteurs.isNotEmpty()) LabeledText("Rapporteurs", parentText.rapporteurs.joinToString())
            parentText.dossierUrl?.let { OpenButton(it, onOpenUrl) }
        }
    }
}

@Composable
private fun AmendmentCard(detail: VoteDetail, onOpenUrl: (String) -> Unit) {
    DetailSectionCard(title = "Amendment") {
        val amendment = detail.amendment
        if (amendment == null) {
            EmptyText("This public vote appears to concern an amendment, but the full amendment details are not available in the app yet.")
        } else {
            amendment.number?.let { LabeledText("Number", it) }
            amendment.status?.let { LabeledText("Status", it) }
            if (amendment.authors.isNotEmpty()) LabeledText("Authors", amendment.authors.joinToString())
            amendment.group?.let { LabeledText("Group", it) }
            amendment.objectText?.let { LabeledText("Object", it) }
            amendment.proposedChangeText?.let { LabeledText("Proposed change", it) }
            amendment.explanatoryStatement?.let { LabeledText("Explanatory statement", it) }
            amendment.sourceUrl?.let { OpenButton(it, onOpenUrl) }
        }
    }
}

@Composable
private fun ArticleCard(detail: VoteDetail, onOpenUrl: (String) -> Unit) {
    DetailSectionCard(title = "Article") {
        val article = detail.article
        if (article == null) {
            EmptyText("This public vote appears to concern an article, but the full article details are not available in the app yet.")
        } else {
            article.number?.let { LabeledText("Number", it) }
            article.title?.let { LabeledText("Title", it) }
            article.summary?.let { LabeledText("Summary", it) }
            article.sourceUrl?.let { OpenButton(it, onOpenUrl) }
        }
    }
}

@Composable
private fun MotionCard(detail: VoteDetail, onOpenUrl: (String) -> Unit) {
    DetailSectionCard(title = "Motion") {
        val motion = detail.motion
        if (motion == null) {
            EmptyText("This public vote appears to concern a motion, but the full motion details are not available in the app yet.")
        } else {
            motion.type?.let { LabeledText("Type", it) }
            if (motion.authors.isNotEmpty()) LabeledText("Authors", motion.authors.joinToString())
            motion.explanation?.let { LabeledText("Explanation", it) }
            motion.politicalEffectExplanation?.let { LabeledText("Political effect", it) }
            motion.sourceUrl?.let { OpenButton(it, onOpenUrl) }
        }
    }
}

@Composable
private fun VoteResultCard(breakdown: VoteBreakdown?, result: String) {
    DetailSectionCard(title = "Vote result") {
        LabeledText("Result", breakdown?.resultLabel ?: result)
        if (breakdown == null) {
            EmptyText("Detailed vote counts are not available in CitizenEye yet for this public vote.")
        } else {
            CountRow("Total voters", breakdown.totalVoters)
            CountRow("For", breakdown.forCount)
            CountRow("Against", breakdown.againstCount)
            CountRow("Abstentions", breakdown.abstentionCount)
            CountRow("Non-voting", breakdown.nonVotingCount)
            CountRow("Absolute majority", breakdown.absoluteMajority)
        }
    }
}

@Composable
private fun GroupPositionCard(group: GroupVotePosition?, deputyPosition: VotePosition) {
    DetailSectionCard(title = "Your MP and their group") {
        LabeledText("MP position", deputyPosition.label)
        if (group == null) {
            EmptyText("CitizenEye does not yet have enough information to compare this vote with the group majority position.")
        } else {
            LabeledText("Parliamentary group", group.groupName)
            LabeledText("Group majority position", group.groupMajorityPosition?.label ?: "Not available")
            LabeledText("Voted like the majority of their group", group.deputyVotedLikeGroup?.let { if (it) "Yes" else "No" } ?: "Not available")
            CountRow("Group for", group.forCount)
            CountRow("Group against", group.againstCount)
            CountRow("Group abstentions", group.abstentionCount)
            CountRow("Group non-voting", group.nonVotingCount)
        }
    }
}

@Composable
private fun OfficialSourcesCard(sources: List<OfficialSource>, onOpenUrl: (String) -> Unit) {
    DetailSectionCard(title = "Official sources") {
        if (sources.isEmpty()) {
            EmptyText("No open official source is available for this vote.")
        } else {
            sources.forEach { source ->
                ResourceRow(source.label, source.description, null, "Official source", source.url, onOpenUrl)
            }
        }
    }
}

@Composable
private fun LearnMoreSection(resources: ExternalResourcesState, onOpenUrl: (String) -> Unit) {
    DetailSectionCard(
        title = "Learn even more",
        subtitle = "These external resources are not official CitizenEye sources. They are provided to help explore public debate around the text."
    ) {
        when (resources) {
            ExternalResourcesState.NotLoaded -> EmptyText("External resources are not loaded yet.")
            ExternalResourcesState.Loading -> Text("Loading external resources…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is ExternalResourcesState.Error -> EmptyText(resources.message)
            is ExternalResourcesState.Empty -> {
                ExternalSubsection("Recent articles") { EmptyText(if (resources.message == "No external source is configured yet.") resources.message else "No recent article found for this text.") }
                ExternalSubsection("Videos") { EmptyText(if (resources.message == "No external source is configured yet.") resources.message else "No relevant video found for this text.") }
                ExternalSubsection("Useful links") { EmptyText(resources.message) }
            }
            is ExternalResourcesState.Available -> {
                ExternalSubsection("Recent articles") {
                    if (resources.newsArticles.isEmpty()) EmptyText("No recent article found for this text.") else resources.newsArticles.take(5).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Article · ${it.sourceName ?: "Unknown source"}", it.url, onOpenUrl)
                    }
                }
                ExternalSubsection("Videos") {
                    if (resources.videos.isEmpty()) EmptyText("No relevant video found for this text.") else resources.videos.take(3).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Video · ${it.channelName ?: "Unknown channel"}", it.url, onOpenUrl)
                    }
                }
                ExternalSubsection("Useful links") {
                    if (resources.webLinks.isEmpty()) EmptyText("No useful external link found for this text.") else resources.webLinks.take(3).forEach {
                        ResourceRow(it.title, it.description, it.publishedAt, "Web link · ${it.sourceName ?: "External source"}", it.url, onOpenUrl)
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
private fun ResourceRow(title: String, description: String?, date: String?, typeLabel: String, url: String, onOpenUrl: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(typeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(title, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        date?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        }
        TextButton(onClick = { onOpenUrl(url) }) { Text("Open") }
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
        Text(value?.toString() ?: "Not available", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun OpenButton(url: String, onOpenUrl: (String) -> Unit) {
    TextButton(onClick = { onOpenUrl(url) }) { Text("Open") }
}
