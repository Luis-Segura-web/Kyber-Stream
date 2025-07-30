package com.kybers.play.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.R

/**
 * Enhanced media poster component that adapts to screen size and orientation
 * Provides consistent design across movies, series, and other media content
 */
@Composable
fun AdaptiveMediaPoster(
    title: String,
    imageUrl: String?,
    rating: Float? = null,
    isFavorite: Boolean = false,
    onPosterClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    fallbackImageRes: Int = R.drawable.ic_launcher_background
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = configuration.screenWidthDp
    
    // Adaptive sizing based on screen size and orientation
    val aspectRatio = if (isLandscape) {
        // In landscape, make posters slightly wider for better space utilization
        1.8f / 3f
    } else {
        // In portrait, use standard poster ratio
        2f / 3f
    }
    
    // Adjust padding and sizes based on screen size
    val itemPadding = when {
        screenWidth > 800 -> 8.dp
        screenWidth > 600 -> 6.dp
        else -> 4.dp
    }
    
    val iconSize = if (isLandscape) 16.dp else 20.dp
    val buttonSize = if (isLandscape) 28.dp else 32.dp
    val textSizeLarge = if (isLandscape) 10.sp else 12.sp
    val textSizeSmall = if (isLandscape) 9.sp else 11.sp
    val starSize = if (isLandscape) 12.dp else 14.dp
    
    Card(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clickable(onClick = onPosterClick)
            .padding(top = itemPadding),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main poster image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .fallback(fallbackImageRes)
                    .error(fallbackImageRes)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Favorite button in top-right corner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(buttonSize)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (isFavorite) Color(0xFFE91E63) else Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            // Title and rating overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(
                        top = 16.dp, 
                        bottom = if (isLandscape) 6.dp else 8.dp, 
                        start = if (isLandscape) 6.dp else 8.dp, 
                        end = if (isLandscape) 6.dp else 8.dp
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 4.dp)
                ) {
                    // Title with exactly 3 lines max as required
                    Text(
                        text = title,
                        color = Color.White,
                        style = TextStyle(
                            fontSize = textSizeLarge,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = if (isLandscape) 12.sp else 14.sp,
                        ),
                        maxLines = 3, // Exactly 3 lines as required in specifications
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Rating with single star as requested in specifications
                    if (rating != null && rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Calificación",
                                tint = Color(0xFFFFC107), // Golden star color
                                modifier = Modifier.size(starSize)
                            )
                            Text(
                                text = "%.1f".format(rating),
                                color = Color.White,
                                fontSize = textSizeSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}