package com.destrukter.chameleon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.destrukter.chameleon.core.online.ChatMessage
import com.destrukter.chameleon.core.online.Protocol

/**
 * A collapsible chat docked to the bottom of the game screen. It opens by itself when [expandByDefault]
 * switches on (lobby and voting), and shows a badge with unread messages while collapsed.
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    myId: Int,
    expandByDefault: Boolean,
    onSend: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(expandByDefault) }
    var seen by rememberSaveable { mutableIntStateOf(messages.size) }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(expandByDefault) {
        if (expandByDefault) expanded = true
    }
    LaunchedEffect(messages.size, expanded) {
        if (expanded) {
            seen = messages.size
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        }
    }
    val unread = (messages.size - seen).coerceAtLeast(0)

    fun send() {
        if (draft.isNotBlank()) {
            onSend(draft.trim())
            draft = ""
        }
    }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!expanded && unread > 0) {
                    Badge { Text("$unread") }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse chat" else "Expand chat",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (expanded) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatLine(message, isMine = message.senderId == myId)
                    }
                }
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(Protocol.MAX_CHAT_LENGTH) },
                        placeholder = { Text("Message") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                    )
                    IconButton(onClick = { send() }, enabled = draft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatLine(message: ChatMessage, isMine: Boolean) {
    if (message.system) {
        Text(
            message.text,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.secondary,
        )
    } else {
        val nameColor = if (isMine) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = nameColor)) { append(message.senderName) }
                append(": ")
                append(message.text)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
