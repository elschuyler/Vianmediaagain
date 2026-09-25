package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Test() {
    val insets = WindowInsets.systemBarsIgnoringVisibility
    val insets2 = WindowInsets.displayCutout
    val comb = insets.union(insets2)
    Box(modifier = Modifier.windowInsetsPadding(comb.only(WindowInsetsSides.Top)))
}
