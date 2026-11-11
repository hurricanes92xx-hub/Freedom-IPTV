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

private val FreedomRed = Color(0xFFF04452)
private val FreedomBlue = Color(0xFF2878FF)
private val FreedomBlack = Color(0xFF080B10)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreedomApp() }
    }
}

@Composable
private fun FreedomApp() {
    var page by remember { mutableStateOf("Home") }
    MaterialTheme(colorScheme = darkColorScheme(primary = FreedomRed, secondary = FreedomBlue, background = FreedomBlack, surface = Color(0xFF111722))) {
        Row(Modifier.fillMaxSize().background(FreedomBlack)) {
            Column(Modifier.width(210.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🦅 FREEDOM", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("IPTV", color = FreedomRed, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                listOf("Home", "Live TV", "Guide", "Sports", "Favorites", "Search", "Settings").forEach {
                    NavigationRailItem(selected = page == it, onClick = { page = it }, icon = {}, label = { Text(it) })
                }
            }
            Column(Modifier.fillMaxSize().padding(30.dp)) {
                Text(page.uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111722)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Welcome to Freedom IPTV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Pair your phone or add an IPTV provider to get started.", color = Color.LightGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { page = "Add Provider" }) { Text("Add Provider") }
                            OutlinedButton(onClick = { page = "Freedom Link" }) { Text("Pair Phone") }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Your channels and guide will appear here after setup.", color = Color.Gray)
                }
            }
        }
    }
}
