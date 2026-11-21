package com.gtarayici.browser.ui.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Alttan açılan sayfaların ortak durumu: yarı açık ara durum yoktur, tek "Geri" ya da aşağı kaydırma kapatır.
 * (Material 3 1.5'te kullanımdan kalkan rememberModalBottomSheetState(skipPartiallyExpanded = true) yerine.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFullSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)
