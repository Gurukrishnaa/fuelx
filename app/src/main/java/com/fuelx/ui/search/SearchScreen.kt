package com.fuelx.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.data.MusicRepository
import com.fuelx.model.Track
import com.fuelx.ui.components.TrackArtwork
import com.fuelx.ui.theme.*
import com.fuelx.util.cleanArtistString
import kotlinx.coroutines.delay

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

@Composable
fun SearchScreen(
    repository: MusicRepository,
    currentTrack: Track?,
    isFavorite: (String) -> Boolean = { false },
    onToggleFavorite: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onTrackSelect: (Track, List<Track>) -> Unit,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Debounced real search against MusicRepository
    LaunchedEffect(searchQuery, retryTrigger) {
        if (searchQuery.isBlank()) {
            isLoading = true
            errorMessage = null
            val result = repository.getTrending()
            isLoading = false
            if (result.isSuccess) {
                searchResults = result.getOrDefault(emptyList())
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Unable to load tracks"
            }
        } else {
            // Debounce: wait 350ms before executing search
            delay(350)
            isLoading = true
            errorMessage = null
            val result = repository.search(searchQuery)
            isLoading = false
            if (result.isSuccess) {
                searchResults = result.getOrDefault(emptyList())
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Search failed"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp
                ),
                color = AccentPrimary
            )
        }

        // Liquid Glass Search Bar Input
        val searchBarShape = RoundedCornerShape(18.dp)
        val searchBarBorder = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.05f)
            )
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, searchBarShape, spotColor = AccentPrimary.copy(alpha = 0.15f))
                .border(BorderStroke(1.dp, searchBarBorder), searchBarShape),
            placeholder = {
                Text(
                    "Search songs, artists, albums...",
                    color = TextSecondary.copy(alpha = 0.65f),
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = AccentPrimary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
            }),
            shape = searchBarShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF14141B).copy(alpha = 0.85f),
                unfocusedContainerColor = Color(0xFF121217).copy(alpha = 0.75f),
                focusedBorderColor = AccentPrimary.copy(alpha = 0.60f),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentPrimary, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Loading catalog..." else "Searching for \"$searchQuery\"...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .padding(24.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF141418).copy(alpha = 0.85f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(22.dp))
                            .padding(26.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Error",
                                tint = AccentSecondary,
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { retryTrigger++ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPrimary,
                                    contentColor = FuelXColors.OnAccent
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { track ->
                        val isCurrent = track.id == currentTrack?.id
                        val cleanArtist = remember(track.artist) {
                            cleanArtistString(track.artist).ifBlank { "Unknown Artist" }
                        }
                        val itemShape = RoundedCornerShape(16.dp)
                        val itemBg = if (isCurrent) {
                            Brush.horizontalGradient(
                                listOf(
                                    AccentPrimary.copy(alpha = 0.16f),
                                    Color(0xFF16161E).copy(alpha = 0.75f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF131318).copy(alpha = 0.65f),
                                    Color(0xFF0F0F13).copy(alpha = 0.75f)
                                )
                            )
                        }
                        val itemBorder = if (isCurrent) {
                            BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.45f))
                        } else {
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(itemBg)
                                .border(itemBorder, itemShape)
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onTrackSelect(track, searchResults)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail with live equalizer
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TrackArtwork(
                                        artworkUrl = track.displayArtwork,
                                        contentDescription = "${track.title} artwork",
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(10.dp),
                                        placeholderColor = DarkSurfaceVariant,
                                        placeholderText = "♪"
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.60f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.GraphicEq,
                                                contentDescription = "Playing",
                                                tint = AccentPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            letterSpacing = (-0.2).sp
                                        ),
                                        color = if (isCurrent) AccentPrimary else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = cleanArtist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        color = TextSecondary.copy(alpha = 0.70f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                track.formattedDuration?.let { dur ->
                                    Text(
                                        text = dur,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                val fav = isFavorite(track.id)

                                // Quick Favorite button
                                IconButton(
                                    onClick = { onToggleFavorite(track) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (fav) "Remove favorite" else "Favorite",
                                        tint = if (fav) AccentPrimary else TextSecondary.copy(alpha = 0.50f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                var showMenu by remember { mutableStateOf(false) }

                                Box {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "Song options",
                                            tint = TextSecondary.copy(alpha = 0.70f),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier
                                            .background(Color(0xFF181820))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(12.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Add to Queue",
                                                    color = TextPrimary,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                                    contentDescription = null,
                                                    tint = AccentPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                onAddToQueue(track)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Add to Playlist",
                                                    color = TextPrimary,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                    contentDescription = null,
                                                    tint = AccentPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                onAddToPlaylist(track)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
