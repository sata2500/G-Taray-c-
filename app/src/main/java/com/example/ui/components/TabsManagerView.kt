package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BrowserTab
import com.example.viewmodel.BrowserViewModel

@Composable
fun TabsManagerView(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    val isIncognito by viewModel.isIncognitoMode.collectAsState()
    
    val normalTabs by viewModel.normalTabs.collectAsState()
    val incognitoTabs by viewModel.incognitoTabs.collectAsState()
    
    val activeNormalId by viewModel.currentNormalTabId.collectAsState()
    val activeIncognitoId by viewModel.currentIncognitoTabId.collectAsState()

    val currentTabs = if (isIncognito) incognitoTabs else normalTabs
    val activeTabId = if (isIncognito) activeIncognitoId else activeNormalId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .testTag("tabs_manager_panel"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isIncognito) Color(0xFF16161a) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isIncognito) Icons.Default.Security else Icons.Default.Public,
                        contentDescription = "Sekmeler",
                        tint = if (isIncognito) Color(0xFF8ab4f8) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isIncognito) "Gizli Sekmeler (${currentTabs.size})" else "Açık Sekmeler (${currentTabs.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (isIncognito) {
                                viewModel.createIncognitoTab()
                            } else {
                                viewModel.createNormalTab()
                            }
                        },
                        modifier = Modifier
                            .background(
                                color = if (isIncognito) Color(0x33ffffff) else MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                            .size(36.dp)
                            .testTag("add_tab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ekle",
                            tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_tabs_view")) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Grid Layout of Active Tabs
            if (currentTabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Açık sekme bulunmuyor.",
                        color = if (isIncognito) Color.Gray else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1.0f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentTabs, key = { it.id }) { tab ->
                        val isSelected = tab.id == activeTabId
                        TabCard(
                            tab = tab,
                            isSelected = isSelected,
                            isIncognito = isIncognito,
                            onClick = {
                                if (isIncognito) {
                                    viewModel.selectIncognitoTab(tab.id)
                                } else {
                                    viewModel.selectNormalTab(tab.id)
                                }
                                onDismiss()
                            },
                            onClose = {
                                viewModel.closeTab(tab)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabCard(
    tab: BrowserTab,
    isSelected: Boolean,
    isIncognito: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected && isIncognito -> Color(0xFF24242a)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            isIncognito -> Color(0xFF1a1a1f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = BorderStroke(
            width = 1.5.dp,
            color = when {
                isSelected && isIncognito -> Color(0xFF8ab4f8)
                isSelected -> MaterialTheme.colorScheme.primary
                isIncognito -> Color(0xFF3c4043)
                else -> Color.Transparent
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clickable { onClick() }
            .testTag("tab_card_${tab.id}")
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = tab.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.0f)
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("close_tab_btn_${tab.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Sekmeyi Kapat",
                        tint = if (isIncognito) Color.LightGray else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = tab.url,
                fontSize = 9.sp,
                color = if (isIncognito) Color(0xFF9aa0a6) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
