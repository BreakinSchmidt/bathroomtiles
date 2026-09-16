package com.radialtiles.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.radialtiles.data.model.AppConfiguration
import com.radialtiles.data.model.ButtonConfig
import com.radialtiles.presentation.components.DynamicDialContainer
import com.radialtiles.presentation.theme.AmberAccent
import com.radialtiles.presentation.theme.Black
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    config: AppConfiguration,
    entityStates: Map<String, Boolean>,
    loadingEntityIds: Set<String>,
    onButtonClick: (ButtonConfig) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onCrownTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = config.pages.ifEmpty { AppConfiguration.defaultPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                coroutineScope.launch {
                    val delta = event.verticalScrollPixels
                    if (delta > 20f && pagerState.currentPage < pages.size - 1) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        onCrownTick()
                    } else if (delta < -20f && pagerState.currentPage > 0) {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        onCrownTick()
                    }
                }
                true
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val page = pages[pageIndex]
            DynamicDialContainer(
                page = page,
                entityStates = entityStates,
                loadingEntityIds = loadingEntityIds,
                onButtonClick = onButtonClick,
                onOpenSetup = onOpenSetup
            )
        }

        // Room / Page Title at Top Center (Long-press to open settings)
        val currentPage = pages.getOrNull(pagerState.currentPage)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Text(
                text = currentPage?.title ?: "RadialTiles",
                style = MaterialTheme.typography.caption1,
                color = AmberAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onOpenSettings
                )
            )

            // Subtle page indicator dots when multiple pages exist
            if (pages.size > 1) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    pages.indices.forEach { index ->
                        val isCurrent = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(if (isCurrent) 4.dp else 2.5.dp)
                                .background(
                                    color = if (isCurrent) AmberAccent else androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.6f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}
