package com.citizeneye

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.citizeneye.data.CivicEmailContext
import com.citizeneye.data.CivicEmailIntent
import com.citizeneye.data.buildCivicEmailDraft
import com.citizeneye.data.isContactMomentRelevant

@Composable
fun CivicEmailComposerScreen(
    context: CivicEmailContext,
    deputyEmail: String?,
    onBack: () -> Unit,
    onOpenEmailDraft: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val availableIntents = remember(context) { CivicEmailIntent.forContext(context) }
    var selectedIntent by remember(context) { mutableStateOf(CivicEmailIntent.defaultFor(context)) }
    var draft by remember(context, selectedIntent) { mutableStateOf(buildCivicEmailDraft(context, selectedIntent)) }
    var subject by remember(context, selectedIntent) { mutableStateOf(draft.subject) }
    var body by remember(context, selectedIntent) { mutableStateOf(draft.body) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("Retour") }
            Text("Écrire à mon député", fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Relisez et personnalisez ce message avant envoi. CitizenEye ouvre simplement votre app mail.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
        item { DeputyEmailCard(context = context, deputyEmail = deputyEmail) }
        item { CivicContextCard(context = context) }
        item {
            Text("Choisir l’angle du message", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Ce choix adapte l’objet et le paragraphe principal. Le message reste entièrement modifiable.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        items(availableIntents) { intent ->
            CivicIntentCard(
                intent = intent,
                selected = intent == selectedIntent,
                onSelect = {
                    selectedIntent = intent
                    val nextDraft = buildCivicEmailDraft(context, intent)
                    draft = nextDraft
                    subject = nextDraft.subject
                    body = nextDraft.body
                }
            )
        }
        item {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Objet") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1
            )
        }
        item {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message éditable") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 12
            )
        }
        item {
            if (deputyEmail == null) {
                NoticeCard("Aucune adresse email officielle n’est disponible pour ce député.")
            }
            Button(
                onClick = { deputyEmail?.let { onOpenEmailDraft(it, subject, body) } },
                enabled = deputyEmail != null,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Ouvrir dans mon app mail")
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString("Objet : $subject\n\n$body")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copier le message")
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun DeputyEmailCard(context: CivicEmailContext, deputyEmail: String?) {
    InfoCard(title = "Destinataire") {
        Text(context.depute.name, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
        Text(
            deputyEmail ?: "Aucune adresse email officielle disponible",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun CivicContextCard(context: CivicEmailContext) {
    InfoCard(title = "Contexte civique") {
        when (context) {
            is CivicEmailContext.PastVote -> {
                LabeledComposerText("Vote", context.vote.title.ifBlank { "Non renseigné" })
                LabeledComposerText("Date", context.vote.date.ifBlank { "Non renseignée" })
                LabeledComposerText("Position du député", context.vote.deputePosition.label)
                LabeledComposerText("Résultat", context.vote.result.ifBlank { "Non renseigné" })
                LabeledCompactSourceText(context.vote.sourceUrl.ifBlank { "Non renseignée" })
            }
            is CivicEmailContext.UpcomingVote -> {
                LabeledComposerText("Texte", context.vote.title.ifBlank { "Non renseigné" })
                LabeledComposerText("Étape", context.vote.currentStage.ifBlank { "Non renseignée" })
                LabeledComposerText("Statut", context.vote.status.label)
                LabeledComposerText("Calendrier", context.vote.expectedDateLabel.ifBlank { "Non renseigné" })
                LabeledCompactSourceText(context.vote.sourceUrl.ifBlank { "Non renseignée" })
                if (!context.vote.isContactMomentRelevant) {
                    Spacer(Modifier.height(8.dp))
                    NoticeCard("Moment de contact moins prioritaire. Vous pouvez tout de même écrire si ce sujet vous concerne.")
                }
            }
        }
    }
}

@Composable
private fun CivicIntentCard(intent: CivicEmailIntent, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(intent.label, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                FilterChip(selected = selected, onClick = onSelect, label = { Text(if (selected) "Choisi" else "Choisir") })
            }
            Text(intent.description, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun LabeledComposerText(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, lineHeight = 20.sp)
    }
}

@Composable
private fun LabeledCompactSourceText(value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text("Source officielle", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NoticeCard(message: String) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
    }
}
