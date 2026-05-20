package com.motya.mvpn

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.motya.mvpn.core.ConnectionState
import com.motya.mvpn.core.VpnStatus
import com.motya.mvpn.data.ProfileStore
import com.motya.mvpn.data.VlessParser
import com.motya.mvpn.data.VlessProfile
import com.motya.mvpn.service.MvpnVpnService
import com.motya.mvpn.ui.theme.MvpnTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MvpnVpnService.ensureNotificationChannel(this)
        setContent { MvpnTheme { MvpnApp() } }
    }
}

class MainViewModel(private val store: ProfileStore) : ViewModel() {
    private val _profiles = MutableStateFlow(store.load())
    val profiles = _profiles.asStateFlow()

    fun add(raw: String): Result<Unit> = runCatching {
        val profile = VlessParser.parse(raw)
        val updated = (_profiles.value.filterNot { it.id == profile.id && it.host == profile.host } + profile)
        _profiles.value = updated
        store.save(updated)
    }

    fun remove(profile: VlessProfile) {
        val updated = _profiles.value - profile
        _profiles.value = updated
        store.save(updated)
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(ProfileStore(context.applicationContext)) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MvpnApp() {
    val context = LocalContext.current
    val model: MainViewModel = viewModel(factory = MainViewModelFactory(context))
    val profiles by model.profiles.collectAsState()
    val vpnState by VpnStatus.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember(profiles) { mutableStateOf(profiles.firstOrNull()) }
    var dialogOpen by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<VlessProfile?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingStart?.let { startVpn(context, it) }
        pendingStart = null
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("MVPN", fontWeight = FontWeight.Black)
                        Text("VLESS + Xray-core + Material You", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogOpen = true }, shape = CircleShape) {
                Icon(Icons.Rounded.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatusCard(
                    connected = vpnState.state == ConnectionState.Connected,
                    title = when (vpnState.state) {
                        ConnectionState.Connected -> "Подключено"
                        ConnectionState.Connecting -> "Подключаюсь"
                        ConnectionState.Error -> "Нужен фикс"
                        ConnectionState.Disconnected -> "Отключено"
                    },
                    message = vpnState.message,
                    traffic = "↑ ${formatBytes(vpnState.uploadBytes)}  ↓ ${formatBytes(vpnState.downloadBytes)}",
                    onPower = {
                        val active = vpnState.state == ConnectionState.Connected || vpnState.state == ConnectionState.Connecting
                        if (active) stopVpn(context) else selected?.let { profile ->
                            val prepare = VpnService.prepare(context)
                            if (prepare != null) {
                                pendingStart = profile
                                vpnPermission.launch(prepare)
                            } else {
                                startVpn(context, profile)
                            }
                        }
                    },
                )
            }


            if (profiles.isEmpty()) {
                item {
                    EmptyCard(onPaste = {
                        pasteVless(context)?.let { raw ->
                            model.add(raw).onFailure { error -> snackbar.showMessage(error.message.orEmpty()) }
                        } ?: snackbar.showMessage("В буфере нет vless:// ссылки")
                    })
                }
            } else {
                items(profiles, key = { it.id + it.host + it.port }) { profile ->
                    ProfileCard(
                        profile = profile,
                        selected = profile == selected,
                        onClick = { selected = profile },
                        onDelete = { model.remove(profile) },
                    )
                }
            }
        }
    }

    if (dialogOpen) {
        AddProfileDialog(
            onDismiss = { dialogOpen = false },
            onAdd = { raw ->
                model.add(raw)
                    .onSuccess { dialogOpen = false }
                    .onFailure { snackbar.showMessage(it.message.orEmpty()) }
            },
            onPaste = { pasteVless(context).orEmpty() },
        )
    }
}

@Composable
private fun StatusCard(connected: Boolean, title: String, message: String, traffic: String, onPower: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(32.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(traffic, style = MaterialTheme.typography.labelLarge)
            }
            ElevatedButton(onClick = onPower, shape = CircleShape) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Text(if (connected) " Stop" else " Go")
            }
        }
    }
}

@Composable
private fun EmptyCard(onPaste: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Серверов пока нет", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Добавь vless:// ссылку кнопкой снизу или вставь из буфера. UI милый, но без сервера даже капибара не телепортируется.")
            FilledTonalButton(onClick = onPaste) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Text(" Вставить из буфера")
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: VlessProfile, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${profile.host}:${profile.port}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${profile.security.uppercase()} · ${profile.transport}", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Удалить") }
        }
    }
}

@Composable
private fun AddProfileDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit, onPaste: () -> String) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить VLESS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    label = { Text("vless://…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(onClick = { text = onPaste() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                    Text(" Вставить")
                }
            }
        },
        confirmButton = { Button(onClick = { onAdd(text) }) { Text("Сохранить") } },
        dismissButton = { FilledTonalButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


private fun startVpn(context: Context, profile: VlessProfile) {
    val intent = Intent(context, MvpnVpnService::class.java)
        .setAction(MvpnVpnService.ACTION_START)
        .putExtra(MvpnVpnService.EXTRA_VLESS, profile.raw)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopVpn(context: Context) {
    context.startService(Intent(context, MvpnVpnService::class.java).setAction(MvpnVpnService.ACTION_STOP))
}

private fun pasteVless(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.trim().startsWith("vless://", true) }
}

private fun SnackbarHostState.showMessage(message: String) {
    kotlinx.coroutines.MainScope().launch { showSnackbar(message.ifBlank { "Что-то пошло не так" }) }
}

private fun formatBytes(value: Long): String = when {
    value > 1024L * 1024L * 1024L -> "%.1f GB".format(value / 1024f / 1024f / 1024f)
    value > 1024L * 1024L -> "%.1f MB".format(value / 1024f / 1024f)
    value > 1024L -> "%.1f KB".format(value / 1024f)
    else -> "$value B"
}
