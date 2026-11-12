package com.hurricanes92xx.freedomiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamApi
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamCredentials

private val FreedomRed = Color(0xFFF04452)
private val FreedomBlue = Color(0xFF2878FF)
private val FreedomBlack = Color(0xFF080B10)
private val Panel = Color(0xFF111722)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreedomApp() }
    }
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
    val scope = rememberCoroutineScope()
    val api = remember { XtreamApi() }

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
                    "Add Provider" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Connect your Xtream provider", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Enter the HTTPS server address and the credentials supplied by your IPTV service.", color = Color.LightGray)
                            OutlinedTextField(server, { server = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Server URL (https://)") }, singleLine = true)
                            OutlinedTextField(username, { username = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                            OutlinedTextField(password, { password = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(enabled = !busy, onClick = {
                                    busy = true; result = null
                                    scope.launch {
                                        try {
                                            val auth = api.authenticate(XtreamCredentials(server, username, password))
                                            connected = true
                                            result = "Connected • Account status: ${auth.status ?: "Active"}"
                                        } catch (e: Exception) {
                                            connected = false
                                            result = e.message ?: "Unable to connect. Check the server and credentials."
                                        } finally { busy = false }
                                    }
                                }) { Text(if (busy) "Connecting…" else "Test & Connect") }
                                OutlinedButton(onClick = { server = "https://"; username = ""; password = ""; result = null }) { Text("Clear") }
                            }
                            result?.let { Text(it, color = if (connected) Color(0xFF70D6A0) else Color(0xFFFF8A92), fontWeight = FontWeight.Medium) }
                            Text("Credentials are only used for this connection test in this first setup step.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    "Home" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (connected) "Provider connected" else "Welcome to Freedom IPTV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(if (connected) "Xtream authentication succeeded. Channel loading is the next milestone." else "Connect an IPTV provider to get started.", color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { page = "Add Provider" }) { Text(if (connected) "Manage Provider" else "Add Provider") }
                                OutlinedButton(onClick = { page = "Freedom Link" }) { Text("Pair Phone") }
                            }
                        }
                    }
                    else -> Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (connected) "Provider authenticated" else "Connect a provider first", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("This screen is queued for the next implementation milestone.", color = Color.LightGray)
                            Button(onClick = { page = "Add Provider" }) { Text("Add Provider") }
                        }
                    }
                }
            }
        }
    }
}
