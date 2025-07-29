package com.kybers.play.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kybers.play.MainApplication
import com.kybers.play.data.local.model.User
import com.kybers.play.ui.LoginViewModelFactory
import com.kybers.play.ui.main.MainActivity
import com.kybers.play.ui.sync.SyncActivity
import com.kybers.play.ui.theme.IPTVAppTheme

class LoginActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory((application as MainApplication).container.userRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val syncManager = (application as MainApplication).container.syncManager

        setContent {
            IPTVAppTheme {
                LoginScreen(
                    viewModel = loginViewModel,
                    onUserSelected = { user ->
                        Log.d("LoginActivity", "Usuario seleccionado: ${user.profileName} (ID: ${user.id})")
                        if (syncManager.isSyncNeeded(user.id)) {
                            Log.d("LoginActivity", "Sincronización necesaria para userId: ${user.id}")
                            navigateToSync(user.id)
                        } else {
                            Log.d("LoginActivity", "Sincronización NO necesaria para userId: ${user.id}. Navegando a Main.")
                            navigateToMain(user.id)
                        }
                    },
                    onNavigateToSyncAfterUserAdded = { newUser ->
                        Log.d("LoginActivity", "Nuevo usuario añadido: ${newUser.profileName} (ID: ${newUser.id}). Forzando sincronización.")
                        navigateToSync(newUser.id)
                    }
                )
            }
        }
    }

    private fun navigateToSync(userId: Int) {
        Log.d("LoginActivity", "navigateToSync: Navegando a SyncActivity con userId = $userId")
        val intent = Intent(this, SyncActivity::class.java).apply {
            putExtra("USER_ID", userId)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToMain(userId: Int) {
        Log.d("LoginActivity", "navigateToMain: Navegando a MainActivity con userId = $userId")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USER_ID", userId)
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onUserSelected: (User) -> Unit,
    onNavigateToSyncAfterUserAdded: (User) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddUserForm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        val currentState = uiState
        if (showAddUserForm && currentState is LoginUiState.UserList) {
            currentState.users.maxByOrNull { it.id }?.let { newUser ->
                onNavigateToSyncAfterUserAdded(newUser)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is LoginUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = "Logo de la App",
                            modifier = Modifier.size(120.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator()
                    }
                }
            }
            is LoginUiState.ShowRegistration -> {
                LoginForm(
                    onUserAdded = { profile, url, username, password ->
                        viewModel.addUser(profile, url, username, password)
                        showAddUserForm = true
                    },
                    onCancel = { /* No cancelable */ },
                    isCancelable = false
                )
            }
            is LoginUiState.UserList -> {
                if (showAddUserForm) {
                    LoginForm(
                        onUserAdded = { profile, url, username, password ->
                            viewModel.addUser(profile, url, username, password)
                        },
                        onCancel = { showAddUserForm = false },
                        isCancelable = true
                    )
                } else {
                    UserSelectionContent(
                        users = state.users,
                        onUserSelected = onUserSelected,
                        onDeleteUser = { user -> viewModel.deleteUser(user) },
                        onAddUserClicked = { showAddUserForm = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSelectionContent(
    users: List<User>,
    onUserSelected: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onAddUserClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar Perfil") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddUserClicked) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Perfil")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users, key = { user -> user.id }) { user ->
                UserItem(
                    user = user,
                    onClick = { onUserSelected(user) },
                    onDelete = { onDeleteUser(user) }
                )
            }
        }
    }
}

@Composable
fun UserItem(user: User, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Icono de perfil",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(user.profileName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar Perfil",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginForm(
    onUserAdded: (String, String, String, String) -> Unit,
    onCancel: () -> Unit,
    isCancelable: Boolean
) {
    var profileName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val isFormValid by remember(profileName, url, username, password) {
        mutableStateOf(profileName.isNotBlank() && url.isNotBlank() && username.isNotBlank() && password.isNotBlank())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Añadir Nuevo Perfil", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        // --- CAMPO 1: Nombre del Perfil ---
        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            label = { Text("Nombre del Perfil (ej. Casa)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, // Asegura que sea de una sola línea
            leadingIcon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- CAMPO 2: Usuario ---
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, // Asegura que sea de una sola línea
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- CAMPO 3: Contraseña con visibilidad ---
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, // Asegura que sea de una sola línea
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- CAMPO 4: URL ---
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL del Servidor (ej. http://servidor.com:80)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, // Asegura que sea de una sola línea
            leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isCancelable) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
            }
            Button(
                onClick = {
                    // Limpiamos los espacios antes de guardar
                    onUserAdded(profileName.trim(), url.trim(), username.trim(), password.trim())
                },
                modifier = Modifier.weight(1f),
                enabled = isFormValid
            ) {
                Text("Guardar")
            }
        }
    }
}