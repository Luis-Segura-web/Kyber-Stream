package com.kybers.play.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kybers.play.BuildConfig
import com.kybers.play.data.remote.model.Category
import com.kybers.play.ui.theme.ColorModeSelectionDialog
import com.kybers.play.ui.theme.LegacyThemeMode
import com.kybers.play.ui.theme.ThemeConfig
import com.kybers.play.ui.theme.ThemeColor
import com.kybers.play.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showSetInitialPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showCategoryBlockDialog by remember { mutableStateOf(false) }
    var showPinVerificationForCategories by remember { mutableStateOf(false) }
    var showPinVerificationForDisabling by remember { mutableStateOf(false) }
    var showColorModeSelectionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.NavigateToLogin -> {
                    onNavigateToLogin()
                }
                is SettingsEvent.ShowSyncForcedMessage -> Toast.makeText(context, "La sincronización se forzará la próxima vez que inicies sesión.", Toast.LENGTH_LONG).show()
                is SettingsEvent.ShowSyncCompletedMessage -> Toast.makeText(context, "Sincronización completada exitosamente.", Toast.LENGTH_LONG).show()
                is SettingsEvent.ShowSyncErrorMessage -> Toast.makeText(context, "Error en sincronización: ${event.message}", Toast.LENGTH_LONG).show()
                is SettingsEvent.ShowHistoryClearedMessage -> Toast.makeText(context, "Historial de reproducción borrado.", Toast.LENGTH_SHORT).show()
                is SettingsEvent.ShowPinSetSuccess -> Toast.makeText(context, "PIN guardado correctamente.", Toast.LENGTH_SHORT).show()
                is SettingsEvent.ShowPinChangeSuccess -> Toast.makeText(context, "PIN cambiado correctamente.", Toast.LENGTH_SHORT).show()
                is SettingsEvent.ShowPinChangeError -> Toast.makeText(context, "El PIN anterior es incorrecto.", Toast.LENGTH_SHORT).show()
                is SettingsEvent.ShowRecommendationsApplied -> Toast.makeText(context, "Configuración optimizada aplicada.", Toast.LENGTH_SHORT).show()
                is SettingsEvent.PlayerSettingsChanged -> {
                    // Player settings have been updated - handled by individual player ViewModels
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- SECCIÓN: RECOMENDACIONES INTELIGENTES ---
                if (uiState.showRecommendations) {
                    item {
                        SmartRecommendationsCard(
                            recommendations = uiState.adaptiveRecommendations,
                            onApplyRecommendations = { viewModel.applyRecommendedSettings() },
                            onDismissRecommendations = { viewModel.dismissRecommendations() }
                        )
                    }
                }
                
                // --- SECCIÓN: GESTIÓN DE CUENTA ---
                item {
                    SettingsCard(title = "Gestión de Cuenta y Sincronización") {
                        val userInfo = uiState.userInfo
                        InfoSettingItem(icon = Icons.Default.Person, title = "Usuario", subtitle = userInfo?.username ?: "No disponible")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        InfoSettingItem(icon = Icons.Default.CheckCircle, title = "Estado", subtitle = userInfo?.status?.replaceFirstChar { it.uppercase() } ?: "No disponible")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        InfoSettingItem(icon = Icons.Default.Event, title = "Vencimiento", subtitle = viewModel.formatUnixTimestamp(userInfo?.expDate))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        InfoSettingItem(icon = Icons.Default.Devices, title = "Conexiones permitidas", subtitle = userInfo?.maxConnections ?: "N/A")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        InfoSettingItem(icon = Icons.Default.NetworkCheck, title = "Conexiones activas", subtitle = userInfo?.activeCons?.toString() ?: "N/A")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        InfoSettingItem(icon = Icons.Default.Update, title = "Última Sincronización", subtitle = viewModel.formatTimestamp(uiState.lastSyncTimestamp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        DropdownSettingItem(
                            icon = Icons.Default.Schedule,
                            title = "Frecuencia de Sincronización",
                            options = mapOf(6 to "Cada 6 horas", 12 to "Cada 12 horas", 24 to "Cada 24 horas", 0 to "Nunca (Solo manual)"),
                            selectedKey = uiState.syncFrequency,
                            onOptionSelected = { viewModel.onSyncFrequencyChanged(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(onClick = { viewModel.onForceSyncClicked() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Sincronizar Ahora", maxLines = 1)
                            }
                            OutlinedButton(onClick = { viewModel.onChangeProfileClicked() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Cambiar Perfil", maxLines = 1)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // --- SECCIÓN: AJUSTES DEL REPRODUCTOR ---
                item {
                    SettingsCard(title = "Ajustes del Reproductor") {
                        DropdownSettingItem(
                            icon = Icons.Default.PlayArrow,
                            title = "Reproductor Preferido",
                            options = mapOf(
                                com.kybers.play.settings.Settings.PlayerSelection.AUTO to "Automático (recomendado)",
                                com.kybers.play.settings.Settings.PlayerSelection.MEDIA3 to "Media3 (ExoPlayer)",
                                com.kybers.play.settings.Settings.PlayerSelection.VLC to "VLC (LibVLC)"
                            ),
                            selectedKey = uiState.playerSelection,
                            onOptionSelected = { viewModel.onPlayerSelectionChanged(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        DropdownSettingItem(icon = Icons.Default.Tune, title = "Formato de Stream", options = mapOf("AUTOMATIC" to "Automático", "TS" to "MPEG-TS (.ts)", "HLS" to "HLS (.m3u8)"), selectedKey = uiState.streamFormat, onOptionSelected = { viewModel.onStreamFormatChanged(it) })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        DropdownSettingItem(icon = Icons.Default.NetworkCell, title = "Tamaño del Búfer de Red", options = mapOf("SMALL" to "Pequeño", "MEDIUM" to "Mediano", "LARGE" to "Grande"), selectedKey = uiState.networkBuffer, onOptionSelected = { viewModel.onNetworkBufferChanged(it) })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchSettingItem(icon = Icons.Default.Hardware, title = "Aceleración por Hardware", subtitle = "Usar decodificación por hardware", checked = uiState.hwAccelerationEnabled, onCheckedChange = { viewModel.onHwAccelerationChanged(it) })
                    }
                }

                // --- SECCIÓN: GESTIÓN DE DATOS Y PRIVACIDAD ---
                item {
                    SettingsCard(title = "Gestión de Datos y Privacidad") {
                        DropdownSettingItem(
                            icon = Icons.Default.History,
                            title = "Límite de 'Continuar Viendo'",
                            options = mapOf(10 to "10 elementos", 20 to "20 elementos", 30 to "30 elementos", 50 to "50 elementos"),
                            selectedKey = uiState.recentlyWatchedLimit,
                            onOptionSelected = { viewModel.onRecentlyWatchedLimitChanged(it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ClickableSettingItem(
                            icon = Icons.Default.DeleteSweep,
                            title = "Limpiar historial de reproducción",
                            subtitle = "Elimina el progreso de todo el contenido visto",
                            onClick = { viewModel.onClearHistoryClicked() }
                        )
                    }
                }

                // --- SECCIÓN: CONTROL PARENTAL ---
                item {
                    SettingsCard(title = "Control Parental") {
                        SwitchSettingItem(
                            icon = Icons.Default.Lock,
                            title = "Activar Control Parental",
                            subtitle = "Restringe el acceso a contenido mediante un PIN de seguridad",
                            checked = uiState.parentalControlEnabled,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled) {
                                    if (!uiState.hasParentalPin) {
                                        showSetInitialPinDialog = true
                                    } else {
                                        viewModel.onParentalControlEnabledChanged(true)
                                    }
                                } else {
                                    if (uiState.hasParentalPin) {
                                        showPinVerificationForDisabling = true
                                    } else {
                                        viewModel.onParentalControlEnabledChanged(false)
                                    }
                                }
                            }
                        )
                        if (uiState.parentalControlEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            ClickableSettingItem(
                                icon = Icons.Default.Password,
                                title = "Cambiar PIN de Seguridad",
                                subtitle = "Actualiza tu PIN de control parental",
                                onClick = { showChangePinDialog = true }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            ClickableSettingItem(
                                icon = Icons.Default.Block,
                                title = "Gestionar Categorías Bloqueadas",
                                subtitle = "${uiState.blockedCategories.size} categorías restringidas actualmente",
                                onClick = { showPinVerificationForCategories = true }
                            )
                        }
                    }
                }

                // --- SECCIÓN: APARIENCIA ---
                item {
                    SettingsCard(title = "Apariencia") {
                        ClickableSettingItem(
                            icon = Icons.Default.Palette,
                            title = "Tema de la aplicación",
                            subtitle = getThemeDisplayName(uiState.appTheme),
                            onClick = { showColorModeSelectionDialog = true }
                        )
                    }
                }

                // --- SECCIÓN: ACERCA DE ---
                item {
                    SettingsCard(title = "Acerca de") {
                        InfoSettingItem(icon = Icons.Default.Info, title = "Versión", subtitle = BuildConfig.VERSION_NAME)
                    }
                }
            }
        }
    }

    // --- DIÁLOGOS DE CONTROL PARENTAL ---
    if (showSetInitialPinDialog) {
        SetInitialPinDialog(
            onDismiss = { showSetInitialPinDialog = false },
            onPinSet = { pin ->
                viewModel.setInitialPin(pin)
                showSetInitialPinDialog = false
            }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onPinChanged = { oldPin, newPin ->
                viewModel.changePin(oldPin, newPin)
                showChangePinDialog = false
            }
        )
    }

    if (showPinVerificationForCategories) {
        PinEntryDialog(
            title = "Verificar PIN de Seguridad",
            prompt = "Ingresa tu PIN para acceder a la configuración de categorías restringidas.",
            onDismiss = { showPinVerificationForCategories = false },
            onPinVerified = {
                showPinVerificationForCategories = false
                showCategoryBlockDialog = true
            },
            verifyPin = viewModel::verifyPin
        )
    }

    if (showPinVerificationForDisabling) {
        PinEntryDialog(
            title = "Verificar PIN de Seguridad", 
            prompt = "Ingresa tu PIN para desactivar el Control Parental.",
            onDismiss = { showPinVerificationForDisabling = false },
            onPinVerified = {
                viewModel.onParentalControlEnabledChanged(false)
                showPinVerificationForDisabling = false
            },
            verifyPin = viewModel::verifyPin
        )
    }

    if (showCategoryBlockDialog) {
        CategorySelectionDialog(
            liveCategories = uiState.liveCategories,
            movieCategories = uiState.movieCategories,
            seriesCategories = uiState.seriesCategories,
            initiallyBlocked = uiState.blockedCategories,
            onDismiss = { showCategoryBlockDialog = false },
            onSave = { newBlockedSet ->
                viewModel.onBlockedCategoriesChanged(newBlockedSet)
                showCategoryBlockDialog = false
            }
        )
    }
    
    if (showColorModeSelectionDialog) {
        ColorModeSelectionDialog(
            currentConfig = stringToThemeConfig(uiState.appTheme),
            onConfigSelected = { themeConfig ->
                viewModel.onThemeConfigChanged(themeConfig)
            },
            onDismiss = { showColorModeSelectionDialog = false }
        )
    }
}

// --- COMPONENTES REUTILIZABLES DE LA UI ---

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
fun InfoSettingItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ClickableSettingItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchSettingItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSettingItem(
    icon: ImageVector,
    title: String,
    options: Map<T, String>,
    selectedKey: T,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedValue = options[selectedKey] ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, value) ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onOptionSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

// --- DIÁLOGOS DE CONTROL PARENTAL ---

@Composable
fun SetInitialPinDialog(onDismiss: () -> Unit, onPinSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val isError = pin.isNotEmpty() && confirmPin.isNotEmpty() && pin != confirmPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Establecer PIN de Seguridad") },
        text = {
            Column {
                Text("Crea un PIN de 4 dígitos para activar el Control Parental en tu cuenta.")
                Spacer(Modifier.height(16.dp))
                PinInputField(label = "Nuevo PIN", value = pin, onValueChange = { pin = it })
                Spacer(Modifier.height(8.dp))
                PinInputField(label = "Confirmar PIN", value = confirmPin, onValueChange = { confirmPin = it }, isError = isError)
                if (isError) {
                    Text("Los códigos PIN no coinciden", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPinSet(pin) },
                enabled = pin.length == 4 && pin == confirmPin
            ) { Text("Guardar PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ChangePinDialog(onDismiss: () -> Unit, onPinChanged: (String, String) -> Unit) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val isError = newPin.isNotEmpty() && confirmPin.isNotEmpty() && newPin != confirmPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar PIN de Seguridad") },
        text = {
            Column {
                PinInputField(label = "PIN Anterior", value = oldPin, onValueChange = { oldPin = it })
                Spacer(Modifier.height(16.dp))
                PinInputField(label = "Nuevo PIN", value = newPin, onValueChange = { newPin = it })
                Spacer(Modifier.height(8.dp))
                PinInputField(label = "Confirmar Nuevo PIN", value = confirmPin, onValueChange = { confirmPin = it }, isError = isError)
                if (isError) {
                    Text("Los códigos PIN no coinciden", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPinChanged(oldPin, newPin) },
                enabled = oldPin.length == 4 && newPin.length == 4 && newPin == confirmPin
            ) { Text("Actualizar PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun PinEntryDialog(title: String, prompt: String, onDismiss: () -> Unit, onPinVerified: () -> Unit, verifyPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(prompt)
                Spacer(Modifier.height(16.dp))
                PinInputField(
                    label = "PIN",
                    value = pin,
                    onValueChange = {
                        pin = it
                        error = false
                    },
                    isError = error
                )
                if (error) {
                    Text("PIN incorrecto. Intenta nuevamente.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (verifyPin(pin)) onPinVerified() else error = true
                },
                enabled = pin.length == 4
            ) { Text("Verificar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PinInputField(label: String, value: String, onValueChange: (String) -> Unit, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4) onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        isError = isError,
        textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun CategorySelectionDialog(
    liveCategories: List<Category>,
    movieCategories: List<Category>,
    seriesCategories: List<Category>,
    initiallyBlocked: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val (blockedCategories, setBlockedCategories) = remember { mutableStateOf(initiallyBlocked) }
    val tabs = listOf("TV en Vivo", "Películas", "Series")
    var selectedTabIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Configurar Control Parental",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selecciona las categorías que deseas bloquear para el control parental:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    text = tab,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content based on selected tab
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (selectedTabIndex) {
                        0 -> { // TV en Vivo
                            if (liveCategories.isNotEmpty()) {
                                items(liveCategories, key = { "live-${it.categoryId}" }) { category ->
                                    CategoryCheckboxItemImproved(
                                        category = category, 
                                        isChecked = blockedCategories.contains(category.categoryId)
                                    ) {
                                        val newSet = blockedCategories.toMutableSet()
                                        if (newSet.contains(category.categoryId)) {
                                            newSet.remove(category.categoryId) 
                                        } else {
                                            newSet.add(category.categoryId)
                                        }
                                        setBlockedCategories(newSet)
                                    }
                                }
                            } else {
                                item {
                                    EmptyCategoryMessage("No hay categorías de TV en vivo disponibles")
                                }
                            }
                        }
                        1 -> { // Películas
                            if (movieCategories.isNotEmpty()) {
                                items(movieCategories, key = { "movie-${it.categoryId}" }) { category ->
                                    CategoryCheckboxItemImproved(
                                        category = category, 
                                        isChecked = blockedCategories.contains(category.categoryId)
                                    ) {
                                        val newSet = blockedCategories.toMutableSet()
                                        if (newSet.contains(category.categoryId)) {
                                            newSet.remove(category.categoryId) 
                                        } else {
                                            newSet.add(category.categoryId)
                                        }
                                        setBlockedCategories(newSet)
                                    }
                                }
                            } else {
                                item {
                                    EmptyCategoryMessage("No hay categorías de películas disponibles")
                                }
                            }
                        }
                        2 -> { // Series
                            if (seriesCategories.isNotEmpty()) {
                                items(seriesCategories, key = { "series-${it.categoryId}" }) { category ->
                                    CategoryCheckboxItemImproved(
                                        category = category, 
                                        isChecked = blockedCategories.contains(category.categoryId)
                                    ) {
                                        val newSet = blockedCategories.toMutableSet()
                                        if (newSet.contains(category.categoryId)) {
                                            newSet.remove(category.categoryId) 
                                        } else {
                                            newSet.add(category.categoryId)
                                        }
                                        setBlockedCategories(newSet)
                                    }
                                }
                            } else {
                                item {
                                    EmptyCategoryMessage("No hay categorías de series disponibles")
                                }
                            }
                        }
                    }
                }
                
                // Summary of blocked categories
                val blockedCount = blockedCategories.size
                if (blockedCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "Total de categorías bloqueadas: $blockedCount",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = { onSave(blockedCategories) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) { 
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Configuración") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancelar") 
            } 
        }
    )
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun CategoryCheckboxItemImproved(category: Category, isChecked: Boolean, onCheckedChange: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckedChange),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isChecked) 
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) 
        else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = isChecked, 
                    onCheckedChange = { onCheckedChange() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.error,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.categoryName, 
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isChecked) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
            }
            if (isChecked) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = "Bloqueado",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyCategoryMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SmartRecommendationsCard(
    recommendations: Map<String, Any>,
    onApplyRecommendations: () -> Unit,
    onDismissRecommendations: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Configuración Inteligente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = "Hemos detectado configuraciones que pueden optimizar tu experiencia basándose en tu dispositivo y conexión de red.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (recommendations.containsKey("bufferSize")) {
                    RecommendationItem(
                        icon = Icons.Default.NetworkCell,
                        title = "Búfer de Red Optimizado",
                        recommendation = getBufferDisplayName(recommendations["bufferSize"] as String)
                    )
                }
                
                if (recommendations.containsKey("hardwareAcceleration")) {
                    RecommendationItem(
                        icon = Icons.Default.Hardware,
                        title = "Aceleración por Hardware",
                        recommendation = if (recommendations["hardwareAcceleration"] as Boolean) "Activada" else "Desactivada"
                    )
                }
                
                if (recommendations.containsKey("syncFrequency")) {
                    RecommendationItem(
                        icon = Icons.Default.Schedule,
                        title = "Frecuencia de Sincronización",
                        recommendation = "Cada ${recommendations["syncFrequency"]} horas"
                    )
                }
                
                if (recommendations.containsKey("networkType")) {
                    val networkType = recommendations["networkType"] as String
                    RecommendationItem(
                        icon = Icons.Default.NetworkCheck,
                        title = "Red Detectada",
                        recommendation = getNetworkDisplayName(networkType)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissRecommendations,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Recordar Luego")
                }
                
                Button(
                    onClick = onApplyRecommendations,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Aplicar")
                }
            }
        }
    }
}

@Composable
private fun RecommendationItem(
    icon: ImageVector,
    title: String,
    recommendation: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = recommendation,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun getBufferDisplayName(bufferSize: String): String {
    return when (bufferSize) {
        "SMALL" -> "Pequeño"
        "MEDIUM" -> "Mediano"
        "LARGE" -> "Grande"
        else -> bufferSize
    }
}

private fun getNetworkDisplayName(networkType: String): String {
    return when (networkType) {
        "WIFI" -> "WiFi"
        "CELLULAR_5G" -> "5G"
        "CELLULAR_4G" -> "4G"
        "CELLULAR_3G" -> "3G"
        "UNKNOWN" -> "Desconocida"
        else -> networkType
    }
}

private fun getThemeDisplayName(appTheme: String): String {
    // Temporal: usar el mapeo del nuevo sistema
    val config = stringToThemeConfig(appTheme)
    return "${getThemeColorDisplayName(config.color)} • ${getThemeModeDisplayName(config.mode)}"
}

private fun getThemeColorDisplayName(color: ThemeColor): String {
    return when (color) {
        ThemeColor.BLUE -> "Azul"
        ThemeColor.PURPLE -> "Púrpura"
        ThemeColor.PINK -> "Rosa"
    }
}

private fun getThemeModeDisplayName(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.LIGHT -> "Claro"
        ThemeMode.DARK -> "Oscuro"
        ThemeMode.SYSTEM -> "Sistema"
    }
}

private fun stringToThemeConfig(themeString: String): ThemeConfig {
    return when (themeString) {
        "LIGHT" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.LIGHT)
        "DARK" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
        "BLUE" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.DARK)
        "PURPLE" -> ThemeConfig(ThemeColor.PURPLE, ThemeMode.DARK)
        "PINK" -> ThemeConfig(ThemeColor.PINK, ThemeMode.DARK)
        "SYSTEM" -> ThemeConfig(ThemeColor.BLUE, ThemeMode.SYSTEM)
        else -> ThemeConfig.DEFAULT
    }
}
