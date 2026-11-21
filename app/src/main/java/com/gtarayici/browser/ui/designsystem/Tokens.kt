package com.gtarayici.browser.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Tasarım belirteçleri (bkz. docs/DESIGN.md). Ekranlarda elle `dp` yazmak yerine bunlar kullanılır.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 48.dp

    /** Ekran kenar boşluğu. */
    val screen = lg
}

/** Şekil ölçeği: küçükten büyüğe çip → kutucuk → kart → alt sayfa. */
object AppShape {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(28.dp)
    val full = RoundedCornerShape(percent = 50)

    /** Bir bölüm kartındaki ardışık satırlar: dış köşeler büyük, iç köşeler küçük ("segmentli" liste). */
    fun segment(index: Int, count: Int) = when {
        count == 1 -> lg
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }

    /** MaterialTheme'e verilen şekiller; Material bileşenleri de aynı ölçeği kullansın. */
    val material = Shapes(
        extraSmall = xs,
        small = sm,
        medium = md,
        large = lg,
        extraLarge = xl,
    )
}

/** Boyutlar. */
object Size {
    /** Erişilebilir en küçük dokunma alanı. */
    val touchTarget = 48.dp
    val icon = 24.dp
    val iconContainer = 40.dp
    val tile = 56.dp
}
