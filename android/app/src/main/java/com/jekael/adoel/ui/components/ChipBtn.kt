package com.jekael.adoel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jekael.adoel.ui.theme.*

@Composable
internal fun ChipBtn(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(Dimens.RadiusControl)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) Cyan600 else Color.Transparent)
            .border(1.dp, if (selected) Cyan500 else colors.border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Dimens.Space12),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = AppType.LabelSmallBold.copy(color = if (selected) Zinc100 else colors.textSecondary),
        )
    }
}
