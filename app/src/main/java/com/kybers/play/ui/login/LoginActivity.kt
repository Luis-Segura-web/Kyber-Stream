package com.kybers.play.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kybers.play.MainApplication
import com.kybers.play.data.local.model.User
import com.kybers.play.ui.LoginViewModelFactory
import com.kybers.play.ui.main.MainActivity
import com.kybers.play.ui.theme.IPTVAppTheme
import com.kybers.play.work.CacheWorker
import java.util.concurrent.TimeUnit

class LoginActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory((application as MainApplication).container.userRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVAppTheme {
                LoginScreen(
                    viewModel = loginViewModel,
                    onUserSelected = { user ->
                        // Al seleccionar un usuario, programamos el worker y navegamos a la pantalla principal
                        scheduleCacheWorker()
                        navigateToMain(user.id)
                    },
                    onUserAdded = {
                        // Después de añadir un usuario, también programamos el worker
                        // La navegación ocurre automáticamente al cambiar el estado de la lista de usuarios
                        scheduleCacheWorker()
                    }
                )
            }
        }
    }

    private fun navigateToMain(userId: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USER_ID", userId)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Configura y pone en cola la tarea periódica para sincronizar el caché.
     */
    private fun scheduleCacheWorker() {
        // Creamos una petición de trabajo periódico que se ejecutará cada 12 horas.
        val cacheWorkRequest = PeriodicWorkRequestBuilder<CacheWorker>(12, TimeUnit.HOURS)
            // Aquí podríamos añadir restricciones, como "solo con Wi-Fi" o "solo cargando"
            // .setConstraints(Constraints.Builder()...build())
            .build()

        // Usamos enqueueUniquePeriodicWork para asegurarnos de que solo haya una instancia
        // de este trabajo programada a la vez.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ContentCacheSync", // Un nombre único para nuestro trabajo
            ExistingPeriodicWorkPolicy.KEEP, // Si ya existe, no hace nada. Si no, la crea.
            cacheWorkRequest
        )
    }
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onUserSelected: (User) -> Unit,
    onUserAdded: () -> Unit
) {
    val users by viewModel.users.collectAsState()
    var showAddUserForm by remember { mutableStateOf(false) }

    // Efecto que se dispara cuando la lista de usuarios cambia de vacía a no vacía
    // (es decir, después de que se añade el primer usuario).
    LaunchedEffect(users.isNotEmpty()) {
        if (users.size == 1 && showAddUserForm) {
            // Si acabamos de añadir el primer usuario, lo seleccionamos automáticamente
            onUserSelected(users.first())
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (users.isEmpty() || showAddUserForm) {
            LoginForm(
                onUserAdded = { profile, url, username, password ->
                    viewModel.addUser(profile, url, username, password)
                    onUserAdded() // Llama al callback para programar el worker
                    // showAddUserForm se gestiona dentro del LaunchedEffect
                },
                onCancel = { if (users.isNotEmpty()) showAddUserForm = false }
            )
        } else {
            UserSelectionContent(
                users = users,
                onUserSelected = onUserSelected,
                onDeleteUser = { user -> viewModel.deleteUser(user) },
                onAddUserClicked = { showAddUserForm = true }
            )
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
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users) { user ->
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Icono de perfil",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(user.profileName, fontSize = 20.sp)
            }
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

@Composable
fun LoginForm(onUserAdded: (String, String, String, String) -> Unit, onCancel: () -> Unit) {
    var profileName by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Añadir Nuevo Perfil", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            label = { Text("Nombre del Perfil (ej. Casa)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL del Servidor") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    onUserAdded(profileName, url, username, password)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Guardar")
            }
        }
    }
}
