package org.brotherhood.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.DateFormat
import java.util.Date
import org.brotherhood.app.BuildConfig
import org.brotherhood.app.MainViewModel
import org.brotherhood.app.model.AppState
import org.brotherhood.app.model.ChatMessage
import org.brotherhood.app.model.Contact
import org.brotherhood.app.model.DeliveryStatus
import org.brotherhood.app.model.MessageKind
import org.brotherhood.app.model.PrivateGroup
import org.brotherhood.app.model.AvailabilityMode
import org.brotherhood.app.transport.RouterDiagnostics
import org.brotherhood.app.transport.TransportPhase
import org.brotherhood.app.transport.TransportState

private val BrotherhoodColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF70D6B3),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00382B),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF15513F),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFA7F2D2),
    secondary = androidx.compose.ui.graphics.Color(0xFFB5C9C0),
    background = androidx.compose.ui.graphics.Color(0xFF0B0F14),
    surface = androidx.compose.ui.graphics.Color(0xFF111820),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1A252E),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE6EDF2),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE6EDF2),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
)

@Composable
fun BrotherhoodTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BrotherhoodColors, content = content)
}

@Composable
fun BrotherhoodApp(viewModel: MainViewModel) {
    val initialized by viewModel.initialized.collectAsState()
    val unlocked by viewModel.unlocked.collectAsState()
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val lanStatus by viewModel.lanStatus.collectAsState()
    val torStatus by viewModel.torStatus.collectAsState()
    val routerDiagnostics by viewModel.routerDiagnostics.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            !initialized -> LoadingScreen()
            state.identity == null -> OnboardingScreen(viewModel)
            !unlocked -> UnlockScreen(state.identity?.displayName.orEmpty(), viewModel)
            else -> MainShell(
                state,
                lanStatus,
                torStatus,
                routerDiagnostics,
                viewModel,
                snackbar,
            )
        }
        if (busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun OnboardingScreen(viewModel: MainViewModel) {
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(58.dp),
            )
            Spacer(Modifier.height(22.dp))
            Text("Brotherhood", fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (step == 0) {
                Text(
                    "Messaggi privati, identità locale e contatti scelti da te. " +
                        "Nessun numero di telefono, nessuna rubrica e nessun account centrale.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(20.dp))
                PrivacyPoint("Le chiavi private restano sul dispositivo")
                PrivacyPoint("Gli inviti sostituiscono la ricerca pubblica")
                PrivacyPoint("Trasporto diretto in LAN o tramite onion service v3 Tor")
                Spacer(Modifier.height(28.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Crea identità locale")
                }
                Text(
                    "Versione sperimentale non sottoposta ad audit. Non garantisce anonimato assoluto.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text("La tua identità", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nome visibile") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.take(64) },
                    label = { Text("PIN, almeno 6 caratteri") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(64) },
                    label = { Text("Conferma PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { viewModel.createIdentity(name, pin, confirmation) },
                    enabled = name.trim().length >= 2 && pin.length >= 6 && confirmation.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Genera chiavi e continua")
                }
                TextButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Indietro")
                }
            }
        }
    }
}

@Composable
private fun PrivacyPoint(text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
}

@Composable
private fun UnlockScreen(name: String, viewModel: MainViewModel) {
    var pin by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Bentornato, $name", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.take(64) },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.unlock(pin) },
                enabled = pin.length >= 6,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sblocca")
            }
        }
    }
}

private enum class RootTab(val label: String) {
    CHATS("Chat"),
    CONTACTS("Contatti"),
    GROUPS("Gruppi"),
    SETTINGS("Impostazioni"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: AppState,
    lanStatus: TransportState,
    torStatus: TransportState,
    routerDiagnostics: RouterDiagnostics,
    viewModel: MainViewModel,
    snackbar: SnackbarHostState,
) {
    var route by rememberSaveable { mutableStateOf("root") }
    var rootTab by rememberSaveable { mutableStateOf(RootTab.CHATS) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    val pendingInvite by viewModel.pendingInvite.collectAsState()
    val isRoot = route == "root"
    BackHandler(enabled = !isRoot) { route = "root" }
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null) route = "add"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!isRoot) {
                TopAppBar(
                    title = {
                        Text(
                            when (route) {
                                "add" -> "Aggiungi contatto"
                                "invite" -> "Il mio invito"
                                "chat" -> state.contacts.firstOrNull { it.id == selectedId }?.effectiveName.orEmpty()
                                "contact" -> "Dettagli contatto"
                                "group" -> state.groups.firstOrNull { it.id == selectedId }?.name.orEmpty()
                                "createGroup" -> "Nuovo gruppo"
                                else -> "Brotherhood"
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { route = "root" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isRoot) {
                NavigationBar {
                    RootTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = rootTab == tab,
                            onClick = { rootTab = tab },
                            icon = {
                                Icon(
                                    when (tab) {
                                        RootTab.CHATS -> Icons.Default.ChatBubbleOutline
                                        RootTab.CONTACTS -> Icons.Default.Person
                                        RootTab.GROUPS -> Icons.Default.Group
                                        RootTab.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isRoot) {
                when (rootTab) {
                    RootTab.CHATS, RootTab.CONTACTS -> ExtendedFloatingActionButton(
                        onClick = { route = "add" },
                        icon = { Icon(Icons.Default.PersonAdd, null) },
                        text = { Text("Aggiungi") },
                    )
                    RootTab.GROUPS -> ExtendedFloatingActionButton(
                        onClick = { route = "createGroup" },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Nuovo gruppo") },
                    )
                    RootTab.SETTINGS -> Unit
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (route) {
                "root" -> when (rootTab) {
                    RootTab.CHATS -> ConversationsScreen(state) {
                        selectedId = it
                        route = "chat"
                    }
                    RootTab.CONTACTS -> ContactsScreen(
                        state = state,
                        onChat = {
                            selectedId = it
                            route = "chat"
                        },
                        onDetails = {
                            selectedId = it
                            route = "contact"
                        },
                        onInvite = { route = "invite" },
                    )
                    RootTab.GROUPS -> GroupsScreen(state) {
                        selectedId = it
                        route = "group"
                    }
                    RootTab.SETTINGS -> SettingsScreen(
                        state,
                        lanStatus,
                        torStatus,
                        routerDiagnostics,
                        viewModel,
                    )
                }
                "add" -> AddContactScreen(viewModel) { id ->
                    selectedId = id
                    route = "contact"
                }
                "invite" -> InviteScreen(viewModel)
                "chat" -> state.contacts.firstOrNull { it.id == selectedId }?.let {
                    ChatScreen(state, it, viewModel) { route = "contact" }
                }
                "contact" -> state.contacts.firstOrNull { it.id == selectedId }?.let {
                    ContactDetailsScreen(it, viewModel) { route = "root" }
                }
                "createGroup" -> CreateGroupScreen(state, viewModel) {
                    selectedId = it
                    route = "group"
                }
                "group" -> state.groups.firstOrNull { it.id == selectedId }?.let {
                    GroupChatScreen(state, it, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(icon: @Composable () -> Unit, title: String, body: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConversationsScreen(state: AppState, onOpen: (String) -> Unit) {
    val contacts = state.contacts.filterNot { it.blocked }
    Column(Modifier.fillMaxSize()) {
        SectionHeader("Conversazioni", "Solo persone aggiunte da te")
        if (contacts.isEmpty()) {
            EmptyState(
                icon = { Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(46.dp)) },
                title = "Nessuna conversazione",
                body = "Aggiungi una persona con un invito o un QR per iniziare.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(contacts, key = { it.id }) { contact ->
                    val last = state.messages
                        .filter { it.conversationId == contact.id }
                        .maxByOrNull { it.sentAt }
                    ConversationRow(contact, last, state.preferences.confidentialPreviews) {
                        onOpen(contact.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    contact: Contact,
    message: ChatMessage?,
    confidential: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.effectiveName)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.effectiveName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (contact.verified) {
                    Icon(Icons.Default.Security, "Verificato", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                when {
                    message == null -> "Nessun messaggio"
                    confidential -> "Contenuto nascosto"
                    message.kind == MessageKind.IMAGE -> "Immagine"
                    else -> message.body
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContactsScreen(
    state: AppState,
    onChat: (String) -> Unit,
    onDetails: (String) -> Unit,
    onInvite: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SectionHeader("Contatti", "${state.contacts.size} salvati sul dispositivo")
        OutlinedButton(
            onClick = onInvite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Icon(Icons.Default.QrCode2, null)
            Spacer(Modifier.width(8.dp))
            Text("Mostra il mio invito")
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(state.contacts, key = { it.id }) { contact ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChat(contact.id) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(contact.effectiveName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(contact.effectiveName, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                contact.blocked -> "Bloccato"
                                contact.verified -> "Impronta verificata"
                                else -> "Da verificare"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { onDetails(contact.id) }) {
                        Icon(Icons.Default.MoreVert, "Dettagli")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddContactScreen(viewModel: MainViewModel, onImported: (String) -> Unit) {
    var invite by rememberSaveable { mutableStateOf("") }
    val pendingInvite by viewModel.pendingInvite.collectAsState()
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { invite = it }
    }
    LaunchedEffect(pendingInvite) {
        pendingInvite?.let {
            invite = it
            viewModel.consumePendingInvite()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Scansiona il QR dell’altra persona oppure incolla il suo invito. " +
                "Controlla poi l’impronta insieme a lei.",
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Inquadra un invito Brotherhood")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.QrCode2, null)
            Spacer(Modifier.width(8.dp))
            Text("Scansiona QR")
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = invite,
            onValueChange = { invite = it.take(32_000) },
            label = { Text("Invito Brotherhood") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                viewModel.importInvite(invite, onImported)
            },
            enabled = invite.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Verifica e aggiungi")
        }
        Text(
            "Limite MVP: l’invito scade ma non è monouso. La conferma dell’impronta resta necessaria.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun InviteScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var invite by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { invite = viewModel.createInvite() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "L’invito contiene chiavi pubbliche e gli endpoint LAN/Tor firmati. " +
                "Condividilo solo con il contatto previsto.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        if (invite.isNotBlank()) {
            val qr = remember(invite) { createQrBitmap(invite, 720) }
            Image(
                qr.asImageBitmap(),
                contentDescription = "QR invito",
                modifier = Modifier
                    .size(280.dp)
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(10.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(invite))
                }) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copia")
                }
                Button(onClick = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, invite)
                            },
                            "Condividi invito",
                        ),
                    )
                }) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Condividi")
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = { invite = viewModel.createInvite() }) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Genera un nuovo invito")
            }
        } else {
            Text("Attendi che almeno il trasporto LAN o Tor sia disponibile.")
        }
    }
}

private fun createQrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}

@Composable
private fun ChatScreen(
    state: AppState,
    contact: Contact,
    viewModel: MainViewModel,
    onDetails: () -> Unit,
) {
    val messages = state.messages
        .filter { it.conversationId == contact.id }
        .sortedBy { it.sentAt }
    var text by rememberSaveable(contact.id) { mutableStateOf("") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.sendImage(contact.id, it) }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onDetails)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Security, null, tint = if (contact.verified) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            })
            Spacer(Modifier.width(8.dp))
            Text(
                if (contact.verified) "Impronta verificata" else "Tocca per verificare l’impronta",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "I messaggi sono cifrati per questo contatto. " +
                            "Brotherhood prova prima la LAN e poi Tor. Se il destinatario non è " +
                                "raggiungibile, il messaggio resta nella coda locale.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message, message.senderId == state.identity?.id, viewModel)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { imagePicker.launch("image/*") }) {
                Icon(Icons.Default.AttachFile, "Invia immagine")
            }
            VoiceRecordButton(contact.id, group = false, viewModel = viewModel)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(8_000) },
                placeholder = { Text("Messaggio") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
            )
            IconButton(
                onClick = {
                    viewModel.sendText(contact.id, text)
                    text = ""
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Invia")
            }
        }
    }
}

@Composable
private fun VoiceRecordButton(
    targetId: String,
    group: Boolean,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val recording by viewModel.voiceRecording.collectAsState()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }
    if (!permissionGranted) {
        IconButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
            Icon(Icons.Default.Mic, "Consenti microfono")
        }
        return
    }
    var cancelled by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableStateOf(0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (recording.active) {
            Text(
                if (cancelled) "Rilascia per annullare" else formatDuration(recording.elapsedMillis),
                style = MaterialTheme.typography.labelSmall,
                color = if (cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(targetId, group, permissionGranted) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            cancelled = false
                            horizontalDrag = 0f
                            if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.startVoiceRecording(targetId, group)
                            } else {
                                permissionGranted = false
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            horizontalDrag += dragAmount.x
                            if (horizontalDrag < -90.dp.toPx()) cancelled = true
                        },
                        onDragEnd = {
                            viewModel.finishVoiceRecording(cancelled)
                            horizontalDrag = 0f
                        },
                        onDragCancel = {
                            viewModel.finishVoiceRecording(cancel = true)
                            horizontalDrag = 0f
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Tieni premuto per registrare, trascina a sinistra per annullare",
                tint = if (recording.active) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean, viewModel: MainViewModel) {
    val playback by viewModel.voicePlaybackState.collectAsState()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f),
            colors = CardDefaults.cardColors(
                containerColor = if (mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (message.kind == MessageKind.IMAGE && message.attachmentBase64.isNotBlank()) {
                    decodeImage(message.attachmentBase64)?.let { bitmap ->
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = "Immagine ricevuta",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (message.kind == MessageKind.VOICE && message.attachmentBase64.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.playOrPauseVoice(message) }) {
                            Icon(
                                if (playback.messageId == message.id && playback.playing) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = "Riproduci o pausa",
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(formatDuration(message.durationMillis))
                            if (playback.messageId == message.id && playback.durationMillis > 0) {
                                LinearProgressIndicator(
                                    progress = {
                                        playback.positionMillis.toFloat() /
                                            playback.durationMillis.toFloat()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (message.body.isNotBlank() && message.kind != MessageKind.IMAGE) {
                    Text(message.body)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (mine) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            statusLabel(message.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun decodeImage(base64: String): Bitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

private fun statusLabel(status: DeliveryStatus): String = when (status) {
    DeliveryStatus.PREPARING -> "preparazione"
    DeliveryStatus.QUEUED -> "in attesa"
    DeliveryStatus.SENDING -> "invio"
    DeliveryStatus.DELIVERED -> "consegnato"
    DeliveryStatus.TEMPORARY_FAILURE -> "offline · riprovo"
    DeliveryStatus.PERMANENT_FAILURE -> "non inviato"
    DeliveryStatus.EXPIRED -> "scaduto"
}

private fun torPhaseLabel(phase: TransportPhase): String = when (phase) {
    TransportPhase.STOPPED -> "fermo"
    TransportPhase.STARTING -> "avvio"
    TransportPhase.CONNECTING -> "connessione"
    TransportPhase.ONLINE -> "connesso"
    TransportPhase.DEGRADED -> "attenzione"
    TransportPhase.ERROR -> "errore"
}

private fun availabilityLabel(mode: AvailabilityMode): String = when (mode) {
    AvailabilityMode.ALWAYS -> "Sempre disponibile"
    AvailabilityMode.BALANCED -> "Bilanciata"
    AvailabilityMode.WHEN_OPEN -> "Solo quando aperta"
}

@Composable
private fun ContactDetailsScreen(contact: Contact, viewModel: MainViewModel, onRemoved: () -> Unit) {
    var alias by rememberSaveable(contact.id) { mutableStateOf(contact.localAlias) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(contact.effectiveName)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(contact.effectiveName, style = MaterialTheme.typography.titleLarge)
                Text(contact.displayName, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Impronta di sicurezza", fontWeight = FontWeight.SemiBold)
        Text(contact.fingerprint, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(
            "Confrontala a voce o di persona prima di segnare il contatto come verificato.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Impronta verificata", modifier = Modifier.weight(1f))
            Switch(
                checked = contact.verified,
                onCheckedChange = { viewModel.verifyContact(contact.id, it) },
            )
        }
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it.take(40) },
            label = { Text("Nome locale") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { viewModel.renameContact(contact.id, alias) }) {
            Text("Salva nome locale")
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Blocca contatto", modifier = Modifier.weight(1f))
            Switch(
                checked = contact.blocked,
                onCheckedChange = { viewModel.blockContact(contact.id, it) },
            )
        }
        if (contact.torOnion.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Usa endpoint Tor")
                    Text(
                        "Puoi revocarlo localmente senza mostrare l’indirizzo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = !contact.torEndpointRevoked,
                    onCheckedChange = { viewModel.setContactTorRevoked(contact.id, !it) },
                )
            }
        }
        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        ) {
            Text("Rimuovi contatto")
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Rimuovere il contatto?") },
            text = { Text("I messaggi già ricevuti resteranno sul dispositivo finché non li elimini.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeContact(contact.id)
                    confirmDelete = false
                    onRemoved()
                }) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun GroupsScreen(state: AppState, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SectionHeader("Gruppi privati", "Massimo 20 partecipanti")
        if (state.groups.isEmpty()) {
            EmptyState(
                icon = { Icon(Icons.Default.Group, null, modifier = Modifier.size(46.dp)) },
                title = "Nessun gruppo",
                body = "Crea un gruppo scegliendo tra i contatti salvati.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.groups, key = { it.id }) { group ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(group.id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Group, null, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                            Text("${group.memberIds.size} membri · revisione ${group.revision}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupScreen(
    state: AppState,
    viewModel: MainViewModel,
    onCreated: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("Nome del gruppo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text("Membri", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(state.contacts.filterNot { it.blocked }, key = { it.id }) { contact ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (contact.id in selected) selected.remove(contact.id)
                            else if (selected.size < 19) selected.add(contact.id)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(contact.effectiveName)
                    Spacer(Modifier.width(12.dp))
                    Text(contact.effectiveName, modifier = Modifier.weight(1f))
                    Switch(
                        checked = contact.id in selected,
                        onCheckedChange = {
                            if (it && selected.size < 19) selected.add(contact.id)
                            else selected.remove(contact.id)
                        },
                    )
                }
            }
        }
        Button(
            onClick = { viewModel.createGroup(name, selected.toList(), onCreated) },
            enabled = name.trim().length >= 2 && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Crea gruppo cifrato")
        }
    }
}

@Composable
private fun GroupChatScreen(state: AppState, group: PrivateGroup, viewModel: MainViewModel) {
    val messages = state.messages.filter { it.conversationId == group.id }.sortedBy { it.sentAt }
    var text by rememberSaveable(group.id) { mutableStateOf("") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.sendGroupImage(group.id, it) }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Text(
            "${group.memberIds.size} membri · cifratura separata per ciascun destinatario",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message, message.senderId == state.identity?.id, viewModel)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { imagePicker.launch("image/*") }) {
                Icon(Icons.Default.AttachFile, "Invia immagine")
            }
            VoiceRecordButton(group.id, group = true, viewModel = viewModel)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(8_000) },
                placeholder = { Text("Messaggio al gruppo") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
            )
            IconButton(
                onClick = {
                    viewModel.sendGroupText(group.id, text)
                    text = ""
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Invia")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppState,
    lanStatus: TransportState,
    torStatus: TransportState,
    routerDiagnostics: RouterDiagnostics,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    var deleteDialog by remember { mutableStateOf(false) }
    var rotateTorDialog by remember { mutableStateOf(false) }
    var pendingAvailability by remember { mutableStateOf<AvailabilityMode?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingAvailability?.let(viewModel::setAvailabilityMode)
        pendingAvailability = null
    }
    fun selectAvailability(mode: AvailabilityMode) {
        if (
            mode == AvailabilityMode.ALWAYS &&
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingAvailability = mode
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setAvailabilityMode(mode)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp),
    ) {
        SectionHeader("Impostazioni", "Privacy, rete e progetto")
        SettingsCard(Icons.Default.Wifi, "Stato rete") {
            Text(
                if (lanStatus.phase == TransportPhase.ONLINE) {
                    "LAN attiva: ${lanStatus.listeningAddress.ifBlank { "indirizzo non disponibile" }}:" +
                        "${lanStatus.listeningPort}"
                } else {
                    "LAN non in ascolto"
                },
            )
            Text(
                "Tor: ${torPhaseLabel(torStatus.phase)} · bootstrap ${torStatus.bootstrapPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (torStatus.onionServiceReady) {
                    "Onion service v3 pubblicato"
                } else {
                    "Onion service non ancora pubblicato"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Integrazione Tor compilata, non verificata su dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            routerDiagnostics.lastTransport?.let {
                Text("Ultima consegna tecnica: ${it.name}", style = MaterialTheme.typography.bodySmall)
            }
        }
        SettingsCard(Icons.Default.Settings, "Disponibilità") {
            Text(
                "La modalità bilanciata non garantisce ricezione immediata.",
                style = MaterialTheme.typography.bodySmall,
            )
            AvailabilityMode.entries.forEach { mode ->
                val selected = state.preferences.availabilityMode == mode
                if (selected) {
                    Button(
                        onClick = { selectAvailability(mode) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text(availabilityLabel(mode)) }
                } else {
                    OutlinedButton(
                        onClick = { selectAvailability(mode) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text(availabilityLabel(mode)) }
                }
            }
        }
        SettingsCard(Icons.Default.Security, "Privacy e sicurezza") {
            Text("Archivio locale cifrato con chiave Android Keystore")
            Text("Backup Android disabilitato · anteprime riservate")
            Text("Nessuna telemetria, rubrica o analytics")
            OutlinedButton(
                onClick = { rotateTorDialog = true },
                enabled = torStatus.phase != TransportPhase.STARTING &&
                    torStatus.phase != TransportPhase.CONNECTING,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Rigenera endpoint Tor")
            }
            Text(
                "Il vecchio endpoint viene revocato localmente. I contatti richiedono un nuovo invito firmato.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SettingsCard(Icons.Default.Person, "Identità") {
            Text(state.identity?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold)
            Text(state.identity?.fingerprint.orEmpty(), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::lock) {
                Icon(Icons.Default.Lock, null)
                Spacer(Modifier.width(6.dp))
                Text("Blocca ora")
            }
        }
        SettingsCard(Icons.Default.Info, "Open source e diagnostica") {
            Text(
                "Brotherhood ${BuildConfig.VERSION_NAME} · " +
                    "${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"} · GNU GPLv3",
            )
            Text(
                "Coda: ${state.outbound.size} · contatti: ${state.contacts.size} · messaggi: ${state.messages.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Retry totali in coda: ${state.outbound.sumOf { it.attempts }} · " +
                    "identità: ${state.identity?.id?.take(8).orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (routerDiagnostics.lastError.isNotBlank()) {
                Text(
                    "Ultimo errore tecnico: ${routerDiagnostics.lastError.take(80)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Build sperimentale, non sottoposta ad audit di sicurezza.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(
            onClick = { deleteDialog = true },
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        ) {
            Icon(Icons.Default.DeleteForever, null)
            Spacer(Modifier.width(8.dp))
            Text("Elimina identità e dati locali")
        }
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null) },
            title = { Text("Eliminazione definitiva") },
            text = {
                Text(
                    "Verranno eliminati identità, chiavi, contatti, messaggi e gruppi da questo dispositivo. " +
                        "L’operazione non può essere annullata.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    viewModel.deleteIdentity()
                }) { Text("Elimina tutto") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text("Annulla") }
            },
        )
    }
    if (rotateTorDialog) {
        AlertDialog(
            onDismissRequest = { rotateTorDialog = false },
            title = { Text("Rigenerare l’endpoint Tor?") },
            text = {
                Text(
                    "Il vecchio indirizzo non sarà più usato. Dovrai condividere un nuovo " +
                        "invito firmato con ogni contatto.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rotateTorDialog = false
                        viewModel.rotateTorIdentity()
                    },
                ) { Text("Rigenera") }
            },
            dismissButton = {
                TextButton(onClick = { rotateTorDialog = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
