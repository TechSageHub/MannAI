package com.techsagehub.mannai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Background = Color(0xFF0B0D12)
val Panel = Color(0xFF151923)
val PanelLight = Color(0xFF1D2330)
val Accent = Color(0xFF9B8CFF)
val PrimaryText = Color(0xFFF4F5FA)
val MutedText = Color(0xFF9BA3B7)
val Green = Color(0xFF6DDBAD)
val Red = Color(0xFFE8838A)

private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE, d MMM h:mm a")
private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

fun formatDateTime(millis: Long?): String {
    if (millis == null) return ""
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(dateTimeFmt)
}

fun formatDate(millis: Long?): String {
    if (millis == null) return ""
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(dateFmt)
}

@Composable
fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MutedText, fontSize = 12.sp)
    }
}

@Composable
fun EmptyState(message: String, hint: String = "") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(message, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (hint.isNotEmpty()) Text(hint, color = MutedText, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
fun LoadingRow(label: String = "Loading…") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = MutedText, fontSize = 13.sp)
    }
}

@Composable
fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier.size(8.dp).background(if (active) Green else MutedText, CircleShape)
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelLight,
        titleContentColor = PrimaryText,
        textContentColor = MutedText,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedText) }
        }
    )
}

@Composable
fun InfoCard(emoji: String, title: String, description: String, onClick: (() -> Unit)? = null) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).background(PanelLight, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, color = Accent, fontSize = 21.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = MutedText, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}
