package com.kybers.play.ui.home

import android.util.Log // Importación necesaria para Log.d
import android.widget.Toast // Importación necesaria para Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.data.remote.model.Movie // Asegúrate de que Movie esté importado
import com.kybers.play.data.remote.model.Series // Asegúrate de que Series esté importado

@Composable
fun HomeScreen(homeViewModel: HomeViewModel) {
    // Observamos el estado de la UI desde el ViewModel.
    // La UI se recompondrá automáticamente cuando este estado cambie.
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current // Obtenemos el contexto para el Toast

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            // Si está cargando, mostramos un indicador de progreso en el centro.
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // Cuando la carga termina, mostramos el contenido.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Fila para las películas recomendadas
                item {
                    ContentRow(
                        title = "Películas Recomendadas",
                        items = uiState.recommendedMovies,
                        itemContent = { movie ->
                            ContentPoster(
                                imageUrl = movie.streamIcon,
                                contentDescription = movie.name,
                                onClick = {
                                    // ¡CORRECCIÓN! Placeholder para la navegación de películas
                                    Log.d("HomeScreen", "Clic en película: ${movie.name}")
                                    Toast.makeText(context, "Clic en película: ${movie.name}", Toast.LENGTH_SHORT).show()
                                    // TODO: Aquí se integraría la navegación a la pantalla de detalles de la película
                                }
                            )
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // Fila para las series recomendadas
                item {
                    ContentRow(
                        title = "Series Recomendadas",
                        items = uiState.recommendedSeries,
                        itemContent = { series ->
                            ContentPoster(
                                imageUrl = series.cover,
                                contentDescription = series.name,
                                onClick = {
                                    // ¡CORRECCIÓN! Placeholder para la navegación de series
                                    Log.d("HomeScreen", "Clic en serie: ${series.name}")
                                    Toast.makeText(context, "Clic en serie: ${series.name}", Toast.LENGTH_SHORT).show()
                                    // TODO: Aquí se integraría la navegación a la pantalla de detalles de la serie
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Un Composable reutilizable para mostrar una fila horizontal de contenido con un título.
 */
@Composable
fun <T> ContentRow(
    title: String,
    items: List<T>,
    itemContent: @Composable (T) -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                itemContent(item)
            }
        }
    }
}

/**
 * Un Composable para mostrar una única portada de contenido.
 */
@Composable
fun ContentPoster(
    imageUrl: String?,
    contentDescription: String?,
    onClick: () -> Unit // El callback onClick se mantiene
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .clickable(onClick = onClick), // El clickable usa el callback
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        // Usamos Coil para cargar la imagen desde la URL.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true) // Efecto de fundido suave
                .error(android.R.drawable.stat_notify_error) // Añadir un placeholder de error
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop, // Escala la imagen para llenar el espacio
            modifier = Modifier.fillMaxSize()
        )
    }
}
