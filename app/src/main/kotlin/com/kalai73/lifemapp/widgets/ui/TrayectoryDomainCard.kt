package com.kalai73.lifemapp.widgets.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalai73.lifemapp.widgets.R
import com.kalai73.lifemapp.widgets.core.data.LifeMappConfig
import com.kalai73.lifemapp.widgets.core.designsystem.LucideIcons
import com.kalai73.lifemapp.widgets.core.presentation.IntegrityUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicItemUi
import com.kalai73.lifemapp.widgets.core.presentation.TopicUi
import com.kalai73.lifemapp.widgets.core.presentation.TrayectoryUi
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes

/**
 * Trayectory card (position 10) for the Monthly screen.
 * Embeds Tenacity (position 5) in a collapsible section styled with Tenacity's color palette.
 */
@Composable
internal fun TrayectoryDomainCard(
    trayectory: TrayectoryUi,
    onOpenWeb: (String) -> Unit,
    items: ItemHandlers,
    onToggleTopic: (String, Boolean) -> Unit,
) {
    DomainCard(
        integrity = trayectory.domain,
        onOpenWeb = onOpenWeb,
        items = items,
        onToggleTopic = onToggleTopic,
        preActionContent = trayectory.embeddedTenacity?.let { tenacity ->
            {
                TenacityCollapsibleSection(
                    tenacity = tenacity,
                    onOpenWeb = onOpenWeb,
                    items = items,
                    onToggleTopic = onToggleTopic,
                )
            }
        },
    )
}

/**
 * Collapsible section that renders Tenacity's subdomains, styled with Tenacity's
 * own color palette (green/emerald gradient and accent).
 */
@Composable
private fun TenacityCollapsibleSection(
    tenacity: IntegrityUi,
    onOpenWeb: (String) -> Unit,
    items: ItemHandlers,
    onToggleTopic: (String, Boolean) -> Unit,
) {
    val t = LocalTok.current
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<TopicItemUi, ItemAction>?>(null) }
    val linkCopied = stringResource(R.string.item_link_copied)

    editing?.let { (item, action) ->
        val close = { editing = null }
        when (action) {
            ItemAction.DELETE -> RemoveItemDialog(item, onConfirm = { items.delete(item.id); close() }, onDismiss = close)
            else -> ItemEditorDialog(
                item,
                focusTitle = true,
                loadText = items.loadText,
                onSave = { name, html -> items.save(item.id, name, html); close() },
                onDelete = { items.delete(item.id); close() },
                onDismiss = close,
            )
        }
    }

    val onItemAction = { topic: TopicUi, item: TopicItemUi, action: ItemAction ->
        when (action) {
            ItemAction.EDIT, ItemAction.DELETE -> editing = item to action
            ItemAction.MOVE_UP -> items.move(item.id, topic.id, true)
            ItemAction.MOVE_DOWN -> items.move(item.id, topic.id, false)
            ItemAction.TOGGLE_WARNING -> items.setWarning(item.id, !item.warning)
            ItemAction.COPY_LINK -> {
                val url = "${LifeMappConfig().webUrl}/today/${topic.id}/${item.id}"
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(url, url))
                Toast.makeText(context, linkCopied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val name = domainName(tenacity)
    val shape = if (t.predator) t.shape(6.dp) else RoundedCornerShape(12.dp)

    // Tenacity green/emerald banner gradient
    val bannerGradient = if (t.dark) {
        listOf(t.accent.copy(alpha = 0.35f), t.accent.copy(alpha = 0.08f), Color.Transparent)
    } else {
        listOf(t.accent.copy(alpha = 0.25f), t.accent.copy(alpha = 0.06f), Color.Transparent)
    }

    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(t.well, shape)
            .border(1.dp, t.line, shape),
    ) {
        // Collapsible header
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(bannerGradient))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(
                if (expanded) LucideIcons.CHEVRON_DOWN else LucideIcons.CHEVRON_RIGHT,
                14.dp,
                t.accent,
                null,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(20.dp).clip(if (t.predator) t.shape(3.dp) else CircleShape)
                    .background(t.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tenacity.position.toString().padStart(2, '0'),
                    style = if (t.predator) t.label(10.sp, t.accent) else t.body(10.sp, t.accent, FontWeight.Bold),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                t.s(name),
                style = if (t.predator) t.label(13.sp, t.accent) else t.body(13.sp, t.accent, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            tenacity.rating?.let { rating ->
                Text(
                    "$rating/10",
                    style = t.caption(10.sp, t.dim),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                if (tenacity.topics.isEmpty()) {
                    Text(
                        stringResource(R.string.today_no_content),
                        style = t.caption(11.sp),
                        modifier = Modifier.padding(vertical = 8.dp).align(Alignment.CenterHorizontally),
                    )
                } else {
                    tenacity.topics.forEach { topic ->
                        TopicHeader(topic) { onToggleTopic(topic.id, !topic.collapsed) }
                        if (!topic.collapsed && topic.items.isNotEmpty()) {
                            topic.items.forEachIndexed { i, item ->
                                ItemRow(
                                    topic, item, i + 1,
                                    canMoveUp = i > 0,
                                    canMoveDown = i < maxOf(topic.count, topic.items.size) - 1,
                                    onAction = { action -> onItemAction(topic, item, action) },
                                    onToggle = { items.setCollapsed(item.id, !item.collapsed) },
                                )
                                if (item.isImage && !item.collapsed) {
                                    ItemPicture(item, items.loadImage)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

