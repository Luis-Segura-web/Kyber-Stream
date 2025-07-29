package com.kybers.play.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Scroll indicator that shows when the user can scroll up or down
 */
@Composable
fun ScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val showTopIndicator by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    
    val showBottomIndicator by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisibleItem == null) {
                false
            } else {
                lastVisibleItem.index < layoutInfo.totalItemsCount - 1 ||
                lastVisibleItem.offset + lastVisibleItem.size > layoutInfo.viewportEndOffset
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Top scroll indicator (positioned on right side)
        AnimatedVisibility(
            visible = showTopIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
            )
        }

        // Bottom scroll indicator (positioned on right side)
        AnimatedVisibility(
            visible = showBottomIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp, end = 8.dp)
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
            )
        }
    }
}

/**
 * Extended scroll indicator with percentage
 */
@Composable
fun DetailedScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = false
) {
    val scrollPercentage by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.totalItemsCount == 0) return@derivedStateOf 0f
            
            val firstVisibleIndex = listState.firstVisibleItemIndex.toFloat()
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset.toFloat()
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 1f
            
            val totalScrollableItems = (layoutInfo.totalItemsCount - 1).coerceAtLeast(1)
            val currentPosition = firstVisibleIndex + (firstVisibleOffset / itemHeight)
            
            (currentPosition / totalScrollableItems).coerceIn(0f, 1f)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Scroll track
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )

        // Scroll thumb
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .width(4.dp)
                .fillMaxHeight(scrollPercentage.coerceAtLeast(0.1f))
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        )
    }
}