package com.example.cooltacoandroid.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Dialog

// IMPORT FIREBASE UNTUK SINKRONISASI
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// ============================================================================
// DATA MODELS (DITAMBAHKAN ICON DAN COLOR UNTUK VISUAL)
// ============================================================================
data class WearTask(val id: String, val title: String, val points: Int, var isCompleted: Boolean = false)
data class WearReward(val title: String, val cost: Int, val icon: String = "🎁", val color: Color = Color(0xFF1E293B))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            androidx.wear.compose.material.MaterialTheme {
                ChildSmartwatchApp()
            }
        }
    }
}

@Composable
fun ChildSmartwatchApp() {
    var currentScreen by remember { mutableStateOf("HOME") }
    var totalPoints by remember { mutableIntStateOf(1250) }
    var isCloudConnected by remember { mutableStateOf(false) }

    var tasks by remember { mutableStateOf(
        listOf(
            WearTask("1", "Physical Exercise", 10, false),
            WearTask("2", "Read Science Book", 5, false),
            WearTask("3", "Clean Room", 5, false)
        )
    )}

    // FITUR TAMBAHAN: Preset Hadiah Default yang Menarik (Free Time, Games, dll)
    var rewards by remember { mutableStateOf(
        listOf(
            WearReward("Free Time!", 50, "⏱️", Color(0xFF3B82F6)),
            WearReward("Play Games", 100, "🎮", Color(0xFF8B5CF6)),
            WearReward("Watch YouTube", 150, "📺", Color(0xFFEF4444)),
            WearReward("Special Snack", 200, "🍦", Color(0xFFF59E0B))
        )
    )}

    var showSuccessDialog by remember { mutableStateOf(false) }
    var earnedPoints by remember { mutableIntStateOf(0) }

    // REAL-TIME CLOUD LISTENER (TIDAK ADA YANG DIKURANGI)
    LaunchedEffect(Unit) {
        try {
            val db = Firebase.firestore
            val docRef = db.collection("families").document("family_01").collection("child_data").document("leo")

            docRef.addSnapshotListener { snapshot, e ->
                if (e != null) { return@addSnapshotListener }
                if (snapshot != null && snapshot.exists()) {
                    isCloudConnected = true
                    snapshot.getLong("totalPoints")?.let { totalPoints = it.toInt() }

                    val cloudRewards = snapshot.get("customRewards") as? List<Map<String, Any>>
                    if (cloudRewards != null && cloudRewards.isNotEmpty()) {
                        // Jika ada data dari Firebase, gabungkan dengan visual icon
                        rewards = cloudRewards.map {
                            WearReward(
                                title = it["title"] as? String ?: "Special Reward",
                                cost = (it["cost"] as? Long)?.toInt() ?: 50,
                                icon = "🎁",
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            isCloudConnected = false
        }
    }

    when (currentScreen) {
        "HOME" -> HomeScreen(
            totalPoints = totalPoints,
            isCloudConnected = isCloudConnected,
            onNavigateToTasks = { currentScreen = "TASKS" },
            onNavigateToRewards = { currentScreen = "REWARDS" }
        )
        "TASKS" -> TasksScreen(
            tasks = tasks,
            onBack = { currentScreen = "HOME" },
            onTaskCompleted = { task ->
                tasks = tasks.map { if (it.id == task.id) it.copy(isCompleted = true) else it }
                totalPoints += task.points

                try {
                    Firebase.firestore.collection("families").document("family_01")
                        .collection("child_data").document("leo").update("totalPoints", totalPoints)
                } catch (e: Exception) {}

                earnedPoints = task.points
                showSuccessDialog = true
            }
        )
        "REWARDS" -> RewardsScreen(
            rewards = rewards,
            currentPoints = totalPoints,
            onBack = { currentScreen = "HOME" },
            onRewardRedeemed = { cost ->
                totalPoints -= cost
                // Update ke Firebase jika diperlukan saat poin berkurang
                try {
                    Firebase.firestore.collection("families").document("family_01")
                        .collection("child_data").document("leo").update("totalPoints", totalPoints)
                } catch (e: Exception) {}
            }
        )
    }

    // ========================================================================
    // DIALOG PUJIAN GLOBAL (TIDAK ADA YANG DIKURANGI)
    // ========================================================================
    Dialog(
        showDialog = showSuccessDialog,
        onDismissRequest = {
            showSuccessDialog = false
            currentScreen = "HOME"
        }
    ) {
        var startAnimation by remember { mutableStateOf(false) }
        LaunchedEffect(showSuccessDialog) {
            startAnimation = showSuccessDialog
        }
        val bounceScale by animateFloatAsState(
            targetValue = if (startAnimation) 1f else 0.3f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessMedium
            ), label = "bounce"
        )

        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF10B981).copy(alpha = 0.95f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎉 AWESOME! 🎉", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.graphicsLayer(scaleX = bounceScale, scaleY = bounceScale))
            Spacer(modifier = Modifier.height(4.dp))
            Text("+$earnedPoints ⭐", color = Color(0xFFFBBF24), fontWeight = FontWeight.Black, fontSize = 36.sp, modifier = Modifier.graphicsLayer(scaleX = bounceScale, scaleY = bounceScale))
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    showSuccessDialog = false
                    currentScreen = "HOME"
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)
            ) { Text("YAY!", color = Color(0xFF0F172A), fontWeight = FontWeight.Black) }
        }
    }
}

// ============================================================================
// 1. LAYAR UTAMA (MAIN MENU) - (TIDAK ADA YANG DIKURANGI)
// ============================================================================
@Composable
fun HomeScreen(totalPoints: Int, isCloudConnected: Boolean, onNavigateToTasks: () -> Unit, onNavigateToRewards: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val starScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "starPulse"
    )

    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📡", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCloudConnected) "Magic Sync On" else "Offline Play", color = if (isCloudConnected) Color(0xFF10B981) else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    Text("My Treasure 👑", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$totalPoints", color = Color(0xFFFBBF24), fontSize = 36.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("⭐", fontSize = 24.sp, modifier = Modifier.graphicsLayer(scaleX = starScale, scaleY = starScale))
                    }
                }
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = onNavigateToTasks,
                    label = { Text("🚀 Let's Go Do Tasks!", fontWeight = FontWeight.Bold, color = Color.White) },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF3B82F6))
                )
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = onNavigateToRewards,
                    label = { Text("🎁 My Wishlist!", fontWeight = FontWeight.Black, color = Color(0xFF0F172A)) },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFFBBF24))
                )
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ============================================================================
// 2. LAYAR TUGAS (TASKS SCREEN) - (TIDAK ADA YANG DIKURANGI)
// ============================================================================
@Composable
fun TasksScreen(tasks: List<WearTask>, onBack: () -> Unit, onTaskCompleted: (WearTask) -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                CompactChip(
                    onClick = onBack,
                    label = { Text("⬅ Back to Home", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    colors = ChipDefaults.chipColors(backgroundColor = Color.DarkGray)
                )
            }
            items(tasks) { task ->
                ToggleChip(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp),
                    checked = task.isCompleted,
                    onCheckedChange = { if (!task.isCompleted) onTaskCompleted(task) },
                    label = { Text(task.title, maxLines = 1, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold) },
                    secondaryLabel = { Text("+${task.points} ⭐", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Black) },
                    toggleControl = {
                        Box(
                            modifier = Modifier.size(28.dp).background(if (task.isCompleted) Color(0xFF10B981) else Color(0xFF1E293B), CircleShape).border(2.dp, if (task.isCompleted) Color.Transparent else Color.White.copy(0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { if (task.isCompleted) { Text("🎉", fontSize = 14.sp) } }
                    },
                    colors = ToggleChipDefaults.toggleChipColors(checkedStartBackgroundColor = Color(0xFF10B981).copy(alpha = 0.3f), uncheckedStartBackgroundColor = Color(0xFF1E293B))
                )
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ============================================================================
// 3. LAYAR HADIAH (REWARDS SCREEN) - (DITAMBAH FITUR VISUAL & REFLEKSI JURNAL)
// ============================================================================
@Composable
fun RewardsScreen(rewards: List<WearReward>, currentPoints: Int, onBack: () -> Unit, onRewardRedeemed: (Int) -> Unit) {
    val listState = rememberScalingLazyListState()

    // State untuk memunculkan dialog refleksi dari Celah Jurnal CHI '23
    var selectedReward by remember { mutableStateOf<WearReward?>(null) }
    var showReflectionDialog by remember { mutableStateOf(false) }

    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                CompactChip(
                    onClick = onBack,
                    label = { Text("⬅ Back", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    colors = ChipDefaults.chipColors(backgroundColor = Color.DarkGray)
                )
            }

            if (rewards.isEmpty()) {
                item {
                    Text("Ask Mom/Dad for\nNew Rewards! 🥺", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                }
            } else {
                item {
                    Text("Can I Buy It? 🤔", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(rewards) { reward ->
                    val progress = (currentPoints.toFloat() / reward.cost.toFloat()).coerceIn(0f, 1f)
                    val canAfford = currentPoints >= reward.cost

                    // FITUR TAMBAHAN: Mengganti Chip dengan Card interaktif bergaya Progress Bar
                    Card(
                        onClick = {
                            if (canAfford) {
                                selectedReward = reward
                                showReflectionDialog = true // Memicu Self-Reflection
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = reward.color.copy(alpha = 0.2f),
                            endBackgroundColor = reward.color.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(reward.icon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(reward.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text("${reward.cost} ⭐", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Visual Progress Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.DarkGray)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = progress)
                                        .fillMaxHeight()
                                        .background(if (canAfford) Color(0xFF10B981) else reward.color)
                                )
                            }

                            if (canAfford) {
                                Text(
                                    "Tap to Claim! 🎉",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                                )
                            } else {
                                Text(
                                    "Need ${reward.cost - currentPoints} more 🏋️",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    // ============================================================================
    // DIALOG REFLEKSI (MENJAWAB CELAH JURNAL: SENSEMAKING & SELF-EVALUATION)
    // ============================================================================
    if (showReflectionDialog && selectedReward != null) {
        Dialog(
            showDialog = showReflectionDialog,
            onDismissRequest = { showReflectionDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(selectedReward!!.icon, fontSize = 32.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Before you play...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Are you proud of the hard work you did today?",
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Chip(
                    onClick = {
                        onRewardRedeemed(selectedReward!!.cost)
                        showReflectionDialog = false
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF10B981)),
                    label = { Text("Yes, I earned it! 💪", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )

                Chip(
                    onClick = { showReflectionDialog = false },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFEF4444)),
                    label = { Text("Wait, I'll do more", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}