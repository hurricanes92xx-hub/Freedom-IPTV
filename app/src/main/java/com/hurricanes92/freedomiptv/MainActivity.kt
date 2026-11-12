package com.hurricanes92xx.freedomiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import com.hurricanes92.freedomiptv.player.FreedomPlayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamApi
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamCredentials
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamCategory
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamLiveChannel

private val FreedomRed = Color(0xFFF04452)
private val FreedomBlue = Color(0xFF2878FF)
private val FreedomBlack = Color(0xFF080B10)
private val Panel = Color(0xFF111722)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FreedomApp() } }
}

@Composable
private fun FreedomApp() {
    var page by remember { mutableStateOf("Home") }
    var server by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var credentials by remember { mutableStateOf<XtreamCredentials?>(null) }
    var channels by remember { mutableStateOf<List<XtreamLiveChannel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var currentChannel by remember { mutableStateOf<XtreamLiveChannel?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { XtreamApi() }

    val filtered = channels.filter { (selectedCategory == "all" || it.categoryId == selectedCategory) && it.name.contains(search, true) }

    MaterialTheme(colorScheme = darkColorScheme(primary = FreedomRed, secondary = FreedomBlue, background = FreedomBlack, surface = Panel)) {
        Row(Modifier.fillMaxSize().background(FreedomBlack)) {
            Column(Modifier.width(210.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🦅 FREEDOM", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("IPTV", color = FreedomRed, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                listOf("Home", "Live TV", "Guide", "Sports", "Favorites", "Search", "Settings", "Add Provider").forEach {
                    NavigationRailItem(selected = page == it, onClick = { page = it }, icon = {}, label = { Text(it) })
                }
            }
            Column(Modifier.fillMaxSize().padding(30.dp)) {
                Text(page.uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                when (page) {
                    "Add Provider" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Connect your Xtream provider", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Use the HTTPS server address and credentials from your IPTV service.", color = Color.LightGray)
                            OutlinedTextField(server, { server = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Server URL (https://)") }, singleLine = true)
                            OutlinedTextField(username, { username = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                            OutlinedTextField(password, { password = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(enabled = !busy, onClick = {
                                    busy = true; result = null
                                    scope.launch {
                                        try {
                                            val creds = XtreamCredentials(server.trim(), username.trim(), password)
                                            val auth = api.authenticate(creds)
                                            val catalog = api.loadLiveCatalog(creds)
                                            credentials = creds; categories = catalog.categories; channels = catalog.channels
                                            connected = true; selectedCategory = "all"; page = "Live TV"
                                            result = "Loaded ${catalog.channels.size} channels • ${catalog.categories.size} categories • ${auth.status ?: "Active"}"
                                        } catch (e: Exception) { connected = false; result = e.message ?: "Connection failed." }
                                        finally { busy = false }
                                    }
                                }) { Text(if (busy) "Loading provider…" else "Connect & Load") }
                                OutlinedButton(onClick = { server = "https://"; username = ""; password = ""; result = null }) { Text("Clear") }
                            }
                            result?.let { Text(it, color = if (connected) Color(0xFF70D6A0) else Color(0xFFFF8A92)) }
                        }
                    }
                    "Live TV", "Search" -> {
                        if (!connected || channels.isEmpty()) EmptyState("Connect a provider to load channels.") { page = "Add Provider" }
                        else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search ${channels.size} channels") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selectedCategory == "all", onClick = { selectedCategory = "all" }, label = { Text("All") })
                                categories.take(8).forEach { cat -> FilterChip(selected = selectedCategory == cat.id, onClick = { selectedCategory = cat.id }, label = { Text(cat.name, maxLines = 1) }) }
                            }
                            Text("${filtered.size} channels", color = Color.LightGray)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(filtered, key = { it.id }) { channel ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { currentChannel = channel; page = "Player" }) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(channel.name, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                            Text("▶", color = FreedomRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Home" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (connected) "Provider connected" else "Welcome to Freedom IPTV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(if (connected) "${channels.size} channels loaded. Guide engine is next." else "Connect your IPTV provider to get started.", color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { page = "Add Provider" }) { Text(if (connected) "Manage Provider" else "Add Provider") }
                                OutlinedButton(onClick = { page = "Live TV" }) { Text("Browse Channels") }
                            }
                        }
                    }
                    "Player" -> {
                        val context = LocalContext.current
                        val player = remember { FreedomPlayer(context) }
                        DisposableEffect(player) { onDispose { player.release() } }
                        currentChannel?.let { channel -> LaunchedEffect(channel.id) { player.play(channel.id, channel.streamUrl) } }
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(currentChannel?.name ?: "Channel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player.exoPlayer; useController = true } }, modifier = Modifier.fillMaxWidth().height(360.dp), update = { it.player = player.exoPlayer })
                            Button(onClick = { player.exoPlayer.stop(); page = "Live TV" }) { Text("Back to channels") }
                        }
                    }
                    "Guide" -> EmptyState("EPG database and XMLTV import are next. Provider EPG endpoints are being added.") { page = "Live TV" }
                    else -> EmptyState("This section is queued for implementation.") { page = "Live TV" }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String, action: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = Color.LightGray)
            Button(onClick = action) { Text("Continue") }
        }
    }
}
