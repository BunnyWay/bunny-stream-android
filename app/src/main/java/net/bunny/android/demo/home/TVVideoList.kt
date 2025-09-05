@file:OptIn(ExperimentalMaterial3Api::class)

package net.bunny.android.demo.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import net.bunny.android.demo.library.model.Video
import net.bunny.android.demo.library.model.VideoStatus
import java.util.Locale

// Updated TV-optimized video list composable
@Composable
fun TVVideoList(
    videos: List<Video>,
    onVideoSelected: (Video) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    title = {
                        Text("Videos", fontSize = 24.sp)
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackPressed,
                            modifier = Modifier.focusable()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = "Back"
                            )
                        }
                    },
                )
            }
        }
    ) { paddingValues ->
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No videos available",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                itemsIndexed(videos) { index, video ->
                    TVVideoItem(
                        video = video,
                        isSelected = index == selectedIndex,
                        onClick = {
                            selectedIndex = index
                            onVideoSelected(video)
                        },
                        onFocused = { selectedIndex = index }
                    )
                }
            }
        }
    }
}

@Composable
private fun TVVideoItem(
    video: Video,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // Enhanced animations with different durations for smoother feedback
    val targetScale = when {
        isPressed -> 0.98f // Slightly smaller when pressed for tactile feedback
        isFocused -> 1.08f // Bigger when focused
        else -> 1.0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (isPressed) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "scale"
    )

    // More pronounced border effects
    val targetBorderWidth = when {
        isPressed -> 6.dp
        isFocused -> 4.dp
        else -> 0.dp
    }
    val borderWidth by animateDpAsState(
        targetValue = targetBorderWidth,
        animationSpec = tween(durationMillis = 150),
        label = "borderWidth"
    )

    val targetBorderColor = when {
        isPressed -> Color(0xFFE65100) // Darker orange when pressed
        isFocused -> Color(0xFFFF9800) // Orange when focused
        else -> Color.Transparent
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 150),
        label = "borderColor"
    )

    // Enhanced container color with more noticeable press effect
    val targetContainerColor = when {
        isPressed -> Color(0xFFBF360C).copy(alpha = 0.4f) // Much darker orange when pressed
        isFocused -> Color(0xFFFF9800).copy(alpha = 0.15f) // Light orange when focused
        else -> MaterialTheme.colorScheme.surface
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 150),
        label = "containerColor"
    )

    // Enhanced title color changes
    val targetTitleColor = when {
        isPressed -> Color(0xFFFF6F00) // Vibrant orange when pressed
        isFocused -> Color(0xFFFF9800) // Orange when focused
        else -> MaterialTheme.colorScheme.onSurface
    }
    val titleColor by animateColorAsState(
        targetValue = targetTitleColor,
        animationSpec = tween(durationMillis = 150),
        label = "titleColor"
    )

    // Enhanced elevation for more dramatic effect
    val targetElevation = when {
        isPressed -> 2.dp // Lower when pressed
        isFocused -> 12.dp // Higher when focused
        else -> 4.dp
    }
    val elevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = tween(durationMillis = 150),
        label = "elevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .scale(scale)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null // Custom indication handled by our animations
            ) { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Enhanced Thumbnail with press effect
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isPressed) Color.Gray.copy(alpha = 0.7f) else Color.Gray
                    )
            ) {
                video.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Enhanced Play icon with press effect
                val playIconColor by animateColorAsState(
                    targetValue = if (isPressed) Color(0xFFFF9800) else Color.White,
                    animationSpec = tween(durationMillis = 150),
                    label = "playIconColor"
                )

                val playIconScale by animateFloatAsState(
                    targetValue = if (isPressed) 1.1f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "playIconScale"
                )

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = playIconColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .scale(playIconScale)
                        .background(
                            color = Color.Black.copy(alpha = if (isPressed) 0.8f else 0.6f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Video info with enhanced animations
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = video.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isPressed) FontWeight.ExtraBold else FontWeight.Bold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = if (isPressed) 19.sp else 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusChip(status = video.status)
                        Text(
                            text = "${video.viewCount} views",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isPressed) titleColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Duration: ${video.duration}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPressed) titleColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f MB", video.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPressed) titleColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@Composable
private fun StatusChip(status: VideoStatus) {
    val color = when (status) {
        VideoStatus.FINISHED -> Color(0xFF4CAF50)
        VideoStatus.PROCESSING, VideoStatus.TRANSCODING -> Color(0xFFFF9800)
        VideoStatus.ERROR, VideoStatus.UPLOAD_FAILED -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    
    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = status.name,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
