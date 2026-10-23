package com.jarvis.ai.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.ai.data.ChatMsg
import com.jarvis.ai.services.JarvisService
import com.jarvis.ai.services.ScreenCaptureService

@Composable
fun App(vm: JarvisViewModel, onStartWatch: () -> Unit) {
    val nav = rememberNavController()
    val items = listOf(
        Triple("dashboard", "Home", Icons.Default.Home),
        Triple("chat", "Chat", Icons.Default.Email),
        Triple("agents", "Agents", Icons.Default.Person),
        Triple("history", "History", Icons.Default.DateRange),
        Triple("settings", "Settings", Icons.Default.Settings)
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1526)) {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = { nav.navigate(route) { launchSingleTop = true; popUpTo("dashboard") } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(navController = nav, startDestination = "dashboard", modifier = Modifier.padding(pad)) {
            composable("dashboard") { DashboardScreen(vm, onStartWatch) }
            composable("chat") { ChatScreen(vm) }
            composable("agents") { AgentsScreen(vm) }
            composable("history") { HistoryScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}

@Composable
private fun StatusCard(title: String, subtitle: String, control: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526))) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color(0xFF00E5FF))
                control()
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.LightGray, fontSize = 13.sp)
        }
    }
}

@Composable
fun DashboardScreen(vm: JarvisViewModel, onStartWatch: () -> Unit) {
    val ctx = LocalContext.current
    val watch by ScreenCaptureService.status.collectAsState()
    val agent by vm.activeAgent.collectAsState()
    var boss by remember { mutableStateOf(vm.store.bossOn) }
    var bg by remember { mutableStateOf(vm.store.bgOn) }
    var timeout by remember { mutableStateOf(vm.store.timeoutSec) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("J.A.R.V.I.S.", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF00E5FF))
            Text("Just A Rather Very Intelligent System", color = Color.Gray, fontSize = 12.sp)
        }
        item {
            StatusCard("Gemini Boss", if (boss) "ON — analyzing & delegating every request" else "OFF — local screen actions only") {
                Switch(checked = boss, onCheckedChange = { boss = it; vm.store.bossOn = it })
            }
        }
        item {
            StatusCard("Screen Watch", "Status: $watch · inactivity timeout ${timeout}s") {
                Row {
                    TextButton(onClick = onStartWatch) { Text("START", color = Color(0xFF00E5FF)) }
                    TextButton(onClick = { ctx.stopService(Intent(ctx, ScreenCaptureService::class.java)) }) { Text("STOP", color = Color(0xFFFF5252)) }
                }
            }
            Slider(
                value = timeout.toFloat(),
                onValueChange = { timeout = it.toInt(); vm.store.timeoutSec = timeout; ScreenCaptureService.timeoutSec = timeout },
                valueRange = 5f..300f
            )
            Text("Auto-stops after $timeout seconds of inactivity (5–300s)", color = Color.Gray, fontSize = 11.sp)
        }
        item {
            StatusCard("Background Assistant", if (bg) "ON — visible notification, honest: Android may still restrict it" else "OFF") {
                Switch(checked = bg, onCheckedChange = { on ->
                    bg = on; vm.store.bgOn = on
                    if (on) ContextCompat.startForegroundService(ctx, Intent(ctx, JarvisService::class.java))
                    else ctx.stopService(Intent(ctx, JarvisService::class.java))
                })
            }
        }
        item {
            StatusCard("Active Agent", "Currently working: $agent") { }
        }
        item {
            Text(
                "Privacy: Screen Watch always shows Android's consent dialog first. The accessibility service acts only on your confirmation. Nothing is sent to Gemini except what you ask.",
                color = Color.Gray, fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ChatBubble(m: ChatMsg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (mine) Color(0xFF123B4A) else Color(0xFF131C33)
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                m.text,
                color = if (m.role == "task") Color(0xFFFFE082) else Color(0xFFE0F7FA),
                fontSize = 14.sp,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
fun ChatScreen(vm: JarvisViewModel) {
    val msgs by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val listening by vm.listening.collectAsState()
    val pending by vm.pendingAction.collectAsState()
    val voiceErr by vm.voiceError.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    pending?.let { a ->
        AlertDialog(
            onDismissRequest = { vm.confirmAction(false) },
            title = { Text("Confirm sensitive action") },
            text = { Text("JARVIS wants to run:\n$a\n\nProceed?") },
            confirmButton = { TextButton(onClick = { vm.confirmAction(true) }) { Text("CONFIRM", color = Color(0xFF00E5FF)) } },
            dismissButton = { TextButton(onClick = { vm.confirmAction(false) }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        voiceErr?.let {
            Text(it, color = Color(0xFFFFAB40), fontSize = 12.sp)
            LaunchedEffect(it) { kotlinx.coroutines.delay(4000); vm.voiceError.value = null }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) { items(msgs) { ChatBubble(it) } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type or tap mic (Hindi/Hinglish OK)") }
            )
            IconButton(onClick = { vm.analyzeScreen("Describe this screen briefly and tell me the most useful thing I could do next on it.") }) {
                Icon(Icons.Default.Visibility, contentDescription = "Analyze screen", tint = Color(0xFF00E5FF))
            }
            IconButton(onClick = { vm.toggleVoice() }) {
                Icon(
                    if (listening) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = "Voice",
                    tint = if (listening) Color(0xFFFF5252) else Color(0xFF00E5FF)
                )
            }
            IconButton(onClick = { vm.send(input); input = "" }, enabled = !busy) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF00E5FF))
            }
        }
    }
}

@Composable
fun AgentsScreen(vm: JarvisViewModel) {
    val active by vm.activeAgent.collectAsState()
    val agents = listOf(
        "BOSS" to "Gemini Boss — reads every request, picks the right specialist, and composes the final answer.",
        "RESEARCH" to "Deep explanations, learning help and analysis. Admits uncertainty honestly.",
        "CODING" to "Writes and debugs Kotlin, Python, JavaScript and more, with run instructions.",
        "WEB" to "Answers search-like questions from built-in knowledge. Free API has no live browsing — it labels possibly-outdated info instead of pretending to search."
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Specialist Agents", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF00E5FF)) }
        items(agents) { (name, desc) ->
            val hot = active == name
            Card(colors = CardDefaults.cardColors(containerColor = if (hot) Color(0xFF14324A) else Color(0xFF0D1526))) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (hot) "● $name" else name, color = if (hot) Color(0xFF00E5FF) else Color.White)
                    Text(desc, color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: JarvisViewModel) {
    val tasks by vm.tasks.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Task History", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF00E5FF))
            TextButton(onClick = { vm.clearTasks() }) { Text("Clear", color = Color(0xFFFF5252)) }
        }
        if (tasks.isEmpty()) Text("No actions executed yet. Try: tap on a label / scroll down / read the screen — with the accessibility service enabled.", color = Color.Gray)
        LazyColumn { items(tasks) { Text("• $it", color = Color(0xFFE0F7FA), fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)) } }
    }
}

@Composable
fun SettingsScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(vm.store.apiKey) }
    var model by remember { mutableStateOf(vm.store.model) }
    var showKey by remember { mutableStateOf(false) }
    var female by remember { mutableStateOf(vm.store.femaleVoice) }
    var timeout by remember { mutableStateOf(vm.store.timeoutSec) }
    var testResult by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF00E5FF)) }
        item {
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.Lock else Icons.Default.Edit, null) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model (default gemini-2.5-flash)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.store.apiKey = key; vm.store.model = model; testResult = "Saved." }) { Text("SAVE") }
                Button(onClick = { vm.store.apiKey = key; vm.store.model = model; testResult = "Testing…"; vm.testApiKey { testResult = it } }) { Text("TEST KEY") }
            }
            if (testResult.isNotEmpty()) Text(testResult, color = Color(0xFF80DEEA), fontSize = 13.sp)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Female voice (if device has one)")
                Switch(checked = female, onCheckedChange = { female = it; vm.store.femaleVoice = it })
            }
        }
        item {
            Text("Screen Watch inactivity timeout: ${timeout}s (5–300)")
            Slider(value = timeout.toFloat(), onValueChange = { timeout = it.toInt(); vm.store.timeoutSec = timeout; ScreenCaptureService.timeoutSec = timeout }, valueRange = 5f..300f)
        }
        item {
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("ENABLE ACCESSIBILITY SERVICE") }
        }
        item {
            Text(
                "Security notes:\n• Key is stored only on your phone (private app storage), never hardcoded or logged.\n• All Gemini calls use HTTPS.\n• Screen content is sent to Gemini only when you tap the eye button or ask.\n• Screen Watch never starts without Android's consent dialog.\n• Sensitive actions (pay/send/delete-like) always ask for confirmation.",
                color = Color.Gray, fontSize = 12.sp
            )
        }
    }
}
