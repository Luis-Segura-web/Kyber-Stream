package com.kybers.play.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
                        if (syncManager.isSyncNeeded()) {
                            navigateToSync(user.id)
                        } else {
                            navigateToMain(user.id)
                        }
                    },
                    onUserAdded = { user ->
                        // A new user always requires a sync.
                        navigateToSync(user.id)
                    }
                )
            }
        }
    }

    private fun navigateToSync(userId: Int) {
        val intent = Intent(this, SyncActivity::class.java).apply {
            putExtra("USER_ID", userId)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToMain(userId: Int) {
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
    onUserAdded: (User) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddUserForm by remember { mutableStateOf(false) }

    // This effect handles the navigation for the FIRST user added.
    // It watches for the uiState to change to a UserList.
    LaunchedEffect(uiState) {
        val currentState = uiState
        // If we were showing the form and now we have a list of users,
        // it means the first user was just added.
        if (showAddUserForm && currentState is LoginUiState.UserList) {
            currentState.users.firstOrNull()?.let { onUserAdded(it) }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            // State 1: Loading. Show a logo and a progress indicator.
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
            // State 2: No users found. Show the registration form.
            is LoginUiState.ShowRegistration -> {
                // Set the flag to true so the LaunchedEffect knows we are in the registration flow.
                showAddUserForm = true
                LoginForm(
                    onUserAdded = { profile, url, username, password ->
                        viewModel.addUser(profile, url, username, password)
                    },
                    onCancel = { /* Not cancelable if no users exist */ },
                    isCancelable = false
                )
            }
            // State 3: Users exist. Show the selection list or the add form if requested.
            is LoginUiState.UserList -> {
                if (showAddUserForm) {
                    LoginForm(
                        onUserAdded = { profile, url, username, password ->
                            viewModel.addUser(profile, url, username, password)
                            showAddUserForm = false // Hide form after adding
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
fun LoginForm(
    onUserAdded: (String, String, String, String) -> Unit,
    onCancel: () -> Unit,
    isCancelable: Boolean
) {
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
            if (isCancelable) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
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
