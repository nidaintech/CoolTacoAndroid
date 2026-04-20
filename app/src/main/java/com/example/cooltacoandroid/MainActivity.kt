package com.example.cooltacoandroid

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cooltacoandroid.ui.theme.CoolTacoAndroidTheme
import com.example.cooltacoandroid.workers.ActivityVerificationWorker
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ============================================================================
// RESEARCH-GRADE DESIGN SYSTEM & DATA MODELS
// ============================================================================
val DarkBg = Color(0xFF0F172A)
val CardBg = Color(0xFF1E293B)
val PrimaryBlue = Color(0xFF3B82F6)
val SuccessGreen = Color(0xFF10B981)
val RewardGold = Color(0xFFFBBF24)
val TextSecondary = Color(0xFF94A3B8)

data class TaskItem(
    val title: String, val schedule: String, val maxPoints: Int, val isAutomated: Boolean = false,
    val currentSteps: Int = 0, val targetSteps: Int = 1, val visualIcon: ImageVector = Icons.Default.Star,
    var isCompleted: Boolean = false
)

data class RewardItem(val title: String, val duration: String, val cost: Int)

val globalTasks = mutableStateListOf(
    TaskItem("Physical Exercise", "Daily Protocol", 10, true, 800, 1000, Icons.Default.DirectionsRun),
    TaskItem("Read Science Book", "Academic", 5, false, visualIcon = Icons.Default.MenuBook),
    TaskItem("Clean Room", "Routine", 5, false, visualIcon = Icons.Default.Home)
)

val globalRewards = mutableStateListOf(
    RewardItem("Play Videogames", "1 hour", 300),
    RewardItem("Watch YouTube", "30 mins", 1000),
    RewardItem("Family Movie Night", "2 hours", 150)
)

// ============================================================================
// MAIN ACTIVITY
// ============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupAutoVerification()
        enableEdgeToEdge()
        setContent {
            CoolTacoAndroidTheme {
                CoolTacoRootApp()
            }
        }
    }

    private fun setupAutoVerification() {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        val workRequest = PeriodicWorkRequestBuilder<ActivityVerificationWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("AutoVerify", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    @Composable
    private fun RequestHealthPermissions() {
        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        val isHealthConnectAvailable = HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE
        if (isHealthConnectAvailable) {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            val requestPermissionContract = PermissionController.createRequestPermissionResultContract()
            val launcher = rememberLauncherForActivityResult(requestPermissionContract) { }
            LaunchedEffect(Unit) { launcher.launch(permissions) }
        }
    }
}

// ============================================================================
// APP ROUTER & STATE MANAGEMENT
// ============================================================================
@Composable
fun CoolTacoRootApp() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("CoolTacoPrefs", Context.MODE_PRIVATE)

    val isLoggedIn = sharedPreferences.getBoolean("IS_LOGGED_IN", false)
    var currentScreen by remember { mutableStateOf(if (isLoggedIn) "MAIN" else "LOGIN") }
    var childName by remember { mutableStateOf(sharedPreferences.getString("CHILD_NAME", "Leo") ?: "Leo") }

    AnimatedContent(targetState = currentScreen, label = "ScreenRouter") { screen ->
        when (screen) {
            "LOGIN" -> LoginScreen(
                onLoginSuccess = {
                    sharedPreferences.edit().putBoolean("IS_LOGGED_IN", true).apply()
                    currentScreen = "MAIN"
                },
                onNavigateToRegister = { currentScreen = "REGISTER" }
            )
            "REGISTER" -> RegisterScreen(
                onRegisterSuccess = { name ->
                    childName = name
                    sharedPreferences.edit().putString("CHILD_NAME", name).putBoolean("IS_LOGGED_IN", true).apply()
                    currentScreen = "MAIN"
                },
                onNavigateToLogin = { currentScreen = "LOGIN" }
            )
            "MAIN" -> CoolTacoMainApp(
                childName = childName,
                onLogout = {
                    sharedPreferences.edit().putBoolean("IS_LOGGED_IN", false).apply()
                    currentScreen = "LOGIN"
                }
            )
        }
    }
}

// ============================================================================
// AUTHENTICATION SCREENS (PARENTS)
// ============================================================================
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Logo", tint = PrimaryBlue, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("CoolTaco", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text("Clinical Co-Regulation Platform", color = TextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Parent Email", color = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password", color = TextSecondary) },
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLoginSuccess, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), shape = RoundedCornerShape(16.dp)
        ) { Text("Secure Login", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateToRegister) { Text("New Parent? Create an Account", color = PrimaryBlue) }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: (String) -> Unit, onNavigateToLogin: () -> Unit) {
    var parentName by remember { mutableStateOf("") }
    var childNameInput by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Join CoolTaco", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text("Empower your child's independence.", color = TextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(value = parentName, onValueChange = { parentName = it }, label = { Text("Parent Full Name", color = TextSecondary) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = childNameInput, onValueChange = { childNameInput = it }, label = { Text("Child's First Name", color = TextSecondary) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address", color = TextSecondary) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password", color = TextSecondary) }, visualTransformation = PasswordVisualTransformation(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val finalChildName = if (childNameInput.isNotBlank()) childNameInput else "Leo"
                onRegisterSuccess(finalChildName)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Register Account", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateToLogin) { Text("Already have an account? Login", color = PrimaryBlue) }
    }
}

// ============================================================================
// MAIN PARENT DASHBOARD & NAVIGATION
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoolTacoMainApp(childName: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("CoolTacoPrefs", Context.MODE_PRIVATE)
    var totalPoints by remember { mutableIntStateOf(sharedPreferences.getInt("TOTAL_POINTS", 1250)) }

    var showBonusDialog by remember { mutableStateOf(false) }
    var bonusReason by remember { mutableStateOf("") }
    var bonusPoints by remember { mutableStateOf("") }

    if (showBonusDialog) {
        AlertDialog(
            onDismissRequest = { showBonusDialog = false },
            title = { Text("Ad-Hoc Reinforcement", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Reward $childName's unplanned positive behaviors immediately.", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = bonusReason, onValueChange = { bonusReason = it }, label = { Text("Behavior") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = bonusPoints, onValueChange = { bonusPoints = it }, label = { Text("Points") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pts = bonusPoints.toIntOrNull() ?: 0
                        totalPoints += pts
                        sharedPreferences.edit().putInt("TOTAL_POINTS", totalPoints).apply()
                        showBonusDialog = false; bonusReason = ""; bonusPoints = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) { Text("Award") }
            },
            dismissButton = { TextButton(onClick = { showBonusDialog = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, "Logo", tint = PrimaryBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("CoolTaco", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = TextSecondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = CardBg, tonalElevation = 0.dp) {
                val items = listOf("Dashboard", "Rewards", "Analytics")
                val icons = listOf(Icons.Default.Dashboard, Icons.Rounded.Star, Icons.Default.Insights)
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item, fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryBlue, selectedTextColor = PrimaryBlue, indicatorColor = PrimaryBlue.copy(0.2f), unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary)
                    )
                }
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(targetState = selectedTab, label = "tabTransition") { targetTab ->
                when (targetTab) {
                    0 -> HomeDashboardScreen(
                        totalPoints = totalPoints, childName = childName,
                        onTaskComplete = { pts -> totalPoints += pts; sharedPreferences.edit().putInt("TOTAL_POINTS", totalPoints).apply() },
                        onQuickBonus = { showBonusDialog = true }
                    )
                    1 -> RewardsScreen(currentPoints = totalPoints, onRedeem = { cost -> if (totalPoints >= cost) { totalPoints -= cost; sharedPreferences.edit().putInt("TOTAL_POINTS", totalPoints).apply() } })
                    2 -> PremiumDailyReportScreen()
                }
            }
        }
    }
}

// ============================================================================
// TAB 1: HYBRID DASHBOARD (WITH CHILD PROFILE)
// ============================================================================
@Composable
fun HomeDashboardScreen(totalPoints: Int, childName: String, onTaskComplete: (Int) -> Unit, onQuickBonus: () -> Unit) {
    var showAddTaskDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    val initial = if (childName.isNotBlank()) childName.first().toString().uppercase() else "C"
                    Box(
                        modifier = Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), PrimaryBlue)), CircleShape).border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(initial, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }

                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monitoring progress for", color = TextSecondary, fontSize = 12.sp)
                        Text("$childName's Dashboard", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            item {
                Text("Overview", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                SmartwatchSyncCard()
                Spacer(modifier = Modifier.height(16.dp))
                IndependenceGrowthCard()
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MicroScaffoldingCard(modifier = Modifier.weight(1f))
                    QuickBonusCard(modifier = Modifier.weight(1f), onClick = onQuickBonus)
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text("Today's Protocol", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            }

            itemsIndexed(globalTasks) { index, task ->
                PremiumTaskCard(
                    task = task,
                    onManualClick = {
                        if (!task.isAutomated && !task.isCompleted) {
                            globalTasks[index] = task.copy(isCompleted = true)
                            onTaskComplete(task.maxPoints)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        FloatingActionButton(
            onClick = { showAddTaskDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = PrimaryBlue, contentColor = Color.White, shape = RoundedCornerShape(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Add Task") }
    }

    if (showAddTaskDialog) { AddTaskDialog(onDismiss = { showAddTaskDialog = false }, onSave = { task -> globalTasks.add(task); showAddTaskDialog = false }) }
}

@Composable
fun SmartwatchSyncCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).background(PrimaryBlue.copy(0.2f), CircleShape).border(2.dp, PrimaryBlue, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Watch, contentDescription = "Watch", tint = PrimaryBlue, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Smartwatch Sync", color = TextSecondary, fontSize = 13.sp)
                Text("Automated verification", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Surface(color = SuccessGreen.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
                Text("Sync Active", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun IndependenceGrowthCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Independence Growth", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                val chartData = listOf(0.80f to "W1", 0.85f to "W2", 0.92f to "W3", 0.95f to "W4")
                chartData.forEach { (percentage, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(percentage * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                        Box(modifier = Modifier.width(30.dp).fillMaxHeight(percentage).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Brush.verticalGradient(listOf(RewardGold, SuccessGreen))))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(label, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MicroScaffoldingCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.height(200.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text("Clean Room", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("AI-deconstructed steps", color = TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(12.dp))
            listOf("1. Put clothes in bin", "2. Clear desk", "3. Vacuum").forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.CheckCircle, "Done", tint = if(i < 2) SuccessGreen else TextSecondary.copy(0.5f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(step, color = if(i < 2) Color.White else TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            LinearProgressIndicator(progress = { 0.66f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = SuccessGreen, trackColor = Color.White.copy(0.1f))
        }
    }
}

@Composable
fun QuickBonusCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(200.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(20.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFFD700), RewardGold))).padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Star, "Bonus", tint = DarkBg, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Quick Bonus", color = DarkBg, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Reward Effort", color = DarkBg.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PremiumTaskCard(task: TaskItem, onManualClick: () -> Unit) {
    val targetProgress = if (task.isAutomated) (task.currentSteps.toFloat() / task.targetSteps.toFloat()).coerceIn(0f, 1f) else if (task.isCompleted) 1f else 0f
    val animatedProgress by animateFloatAsState(targetValue = targetProgress, animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "anim")
    val earnedPoints = if (task.isAutomated) (animatedProgress * task.maxPoints).roundToInt() else if(task.isCompleted) task.maxPoints else 0
    val isComplete = animatedProgress >= 1f

    Card(
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (isComplete) SuccessGreen.copy(0.5f) else Color.White.copy(0.05f)),
        modifier = Modifier.clickable { onManualClick() }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (task.isAutomated) PrimaryBlue.copy(0.2f) else Color.White.copy(0.05f)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = if (isComplete) Icons.Rounded.CheckCircle else task.visualIcon, contentDescription = "Icon", tint = if (isComplete) SuccessGreen else if (task.isAutomated) PrimaryBlue else TextSecondary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text(task.schedule, fontSize = 12.sp, color = TextSecondary)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = RewardGold.copy(0.1f), contentColor = RewardGold) {
                    Text("+$earnedPoints pts", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            if (task.isAutomated) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = if (isComplete) SuccessGreen else PrimaryBlue, trackColor = Color.White.copy(0.05f))
            } else if (!isComplete) {
                Text("Tap to mark as complete", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 12.dp, start = 64.dp))
            }
        }
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onSave: (TaskItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Task", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Name") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = points, onValueChange = { points = it }, label = { Text("Reward Points") })
            }
        },
        confirmButton = { Button(onClick = { onSave(TaskItem(title.ifEmpty { "New Task" }, "Custom", points.toIntOrNull() ?: 1, visualIcon = Icons.Default.Star)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ============================================================================
// TAB 2: REWARDS CENTER (ADVANCED CATEGORIES + SYNC TO WATCH)
// ============================================================================
@Composable
fun RewardsScreen(currentPoints: Int, onRedeem: (Int) -> Unit) {
    var showReflectionDialog by remember { mutableStateOf(false) }
    var selectedRewardCost by remember { mutableStateOf(0) }
    var selectedRewardName by remember { mutableStateOf("") }

    // State untuk memunculkan pop-up tambah hadiah
    var showAddRewardDialog by remember { mutableStateOf(false) }

    if (showReflectionDialog) {
        AlertDialog(
            onDismissRequest = { showReflectionDialog = false },
            title = { Text("Joint Reflection", fontWeight = FontWeight.Bold, color = PrimaryBlue) },
            text = { Text("1. What positive behaviors helped you earn these points?\n2. What was challenging today?", color = Color.White) },
            containerColor = CardBg,
            confirmButton = {
                Button(onClick = { onRedeem(selectedRewardCost); showReflectionDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RewardGold))
                { Text("Complete & Redeem", color = DarkBg, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showReflectionDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // Gunakan Box agar FloatingActionButton bisa mengambang di atas list
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)) {

            // 1. TOTAL TOKENS HEADER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, RewardGold.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Tokens", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$currentPoints", color = RewardGold, fontSize = 48.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Rounded.Star, "Tokens", tint = RewardGold, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }

            // 2. QUICK WINS
            item {
                SectionContainer(title = "⚡ Quick Wins") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RewardVerticalCard(modifier = Modifier.weight(1f), title = "15 Mins Extra Screen Time", cost = 50, icon = Icons.Default.Schedule, currentPoints = currentPoints, onClick = { selectedRewardName = "15 Mins Screen"; selectedRewardCost = 50; showReflectionDialog = true })
                        RewardVerticalCard(modifier = Modifier.weight(1f), title = "Unlock New Avatar", cost = 75, icon = Icons.Default.Face, currentPoints = currentPoints, onClick = { selectedRewardName = "New Avatar"; selectedRewardCost = 75; showReflectionDialog = true })
                    }
                }
            }

            // 3. BIG GOALS
            item {
                SectionContainer(title = "⛰️ Big Goals") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RewardHorizontalCard(title = "New LEGO Set", cost = 500, icon = Icons.Default.SmartToy, currentPoints = currentPoints, onClick = { selectedRewardName = "New LEGO Set"; selectedRewardCost = 500; showReflectionDialog = true })
                        RewardHorizontalCard(title = "Weekend Trip to Zoo", cost = 800, icon = Icons.Default.Park, currentPoints = currentPoints, onClick = { selectedRewardName = "Weekend Trip to Zoo"; selectedRewardCost = 800; showReflectionDialog = true })
                    }
                }
            }

            // 4. EXPERIENCES
            item {
                SectionContainer(title = "❤️ Experiences") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RewardVerticalCard(modifier = Modifier.weight(1f), title = "Pick Dinner for Tonight", cost = 150, icon = Icons.Default.Restaurant, currentPoints = currentPoints, onClick = { selectedRewardName = "Pick Dinner"; selectedRewardCost = 150; showReflectionDialog = true })
                        RewardVerticalCard(modifier = Modifier.weight(1f), title = "Family Movie Night Choice", cost = 200, icon = Icons.Default.Movie, currentPoints = currentPoints, onClick = { selectedRewardName = "Movie Night"; selectedRewardCost = 200; showReflectionDialog = true })
                    }
                }
            }

            // 5. CUSTOM REWARDS (Ditambahkan secara dinamis oleh orang tua)
            if (globalRewards.isNotEmpty()) {
                item {
                    SectionContainer(title = "🌟 Custom Rewards (Synced to Watch)") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            globalRewards.forEach { reward ->
                                RewardHorizontalCard(
                                    title = reward.title,
                                    cost = reward.cost,
                                    icon = Icons.Default.CardGiftcard,
                                    currentPoints = currentPoints,
                                    onClick = { selectedRewardName = reward.title; selectedRewardCost = reward.cost; showReflectionDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB TOMBOL TAMBAH HADIAH
        FloatingActionButton(
            onClick = { showAddRewardDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = RewardGold,
            contentColor = DarkBg,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Reward")
        }
    }

    // DIALOG TAMBAH HADIAH KE SMARTWATCH
    if (showAddRewardDialog) {
        AddRewardToWatchDialog(
            onDismiss = { showAddRewardDialog = false },
            onSave = { reward ->
                globalRewards.add(reward)
                showAddRewardDialog = false
            }
        )
    }
}

// --- SUB-COMPONENTS FOR REWARDS UI ---

@Composable
fun SectionContainer(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(16.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
fun RewardVerticalCard(modifier: Modifier, title: String, cost: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, currentPoints: Int, onClick: () -> Unit) {
    val canAfford = currentPoints >= cost
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).background(Color.White.copy(0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("$cost Tokens", color = RewardGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onClick, enabled = canAfford, modifier = Modifier.fillMaxWidth().height(36.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RewardGold, disabledContainerColor = Color.White.copy(0.1f)), contentPadding = PaddingValues(0.dp)) { Text("Redeem", color = if (canAfford) DarkBg else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun RewardHorizontalCard(title: String, cost: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, currentPoints: Int, onClick: () -> Unit) {
    val canAfford = currentPoints >= cost
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(Color.White.copy(0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("$cost Tokens", color = RewardGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onClick, enabled = canAfford, modifier = Modifier.height(36.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RewardGold, disabledContainerColor = Color.White.copy(0.1f)), contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Redeem", color = if (canAfford) DarkBg else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// DIALOG UNTUK MEMBUAT HADIAH BARU (FIXED: getMainLooper())
@Composable
fun AddRewardToWatchDialog(onDismiss: () -> Unit, onSave: (RewardItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    val isFirebaseReady = try { Firebase.firestore; true } catch (e: Exception) { false }

    AlertDialog(
        onDismissRequest = { if (!isSyncing) onDismiss() },
        containerColor = CardBg,
        title = { Text("Create New Reward", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column {
                Text("Add a custom incentive that will instantly sync to your child's smartwatch.", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Reward Name", color = TextSecondary) },
                    enabled = !isSyncing,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = points, onValueChange = { points = it },
                    label = { Text("Cost (Tokens)", color = TextSecondary) },
                    enabled = !isSyncing,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSyncing = true
                    val cost = points.toIntOrNull() ?: 50

                    if (isFirebaseReady) {
                        val db = Firebase.firestore
                        val newReward = mapOf("title" to title, "cost" to cost)
                        db.collection("families").document("family_01")
                            .collection("child_data").document("leo")
                            .update("customRewards", com.google.firebase.firestore.FieldValue.arrayUnion(newReward))
                            .addOnSuccessListener {
                                onSave(RewardItem(title.ifEmpty { "Custom Reward" }, "Custom", cost))
                            }
                            .addOnFailureListener {
                                isSyncing = false
                            }
                    } else {
                        // Demo mode: Animasi 800ms jika Firebase belum dinyalakan di emulator
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            onSave(RewardItem(title.ifEmpty { "Custom Reward" }, "Custom", cost))
                        }, 800)
                    }
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = RewardGold)
            ) { Text(if (isSyncing) "Transmitting..." else "Sync to Watch", color = DarkBg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { if (!isSyncing) { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } } }
    )
}

// ============================================================================
// TAB 3: DAILY ANALYTICS (CLINICAL GRAPHS)
// ============================================================================
@Composable
fun PremiumDailyReportScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            Text("Behavioral Insights & Growth", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp))
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        IndependenceScoreDetailedCard()
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) { CoRegulationChartCard() }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                TaskInitiationHeatmapCard()
                Spacer(modifier = Modifier.height(16.dp))
                TokenVelocityBarChartCard()
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun IndependenceScoreDetailedCard() {
    val animatedProgress by animateFloatAsState(targetValue = 0.87f, animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "ScoreRing")
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Independence Score", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(color = DarkBg, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 24f, cap = StrokeCap.Round))
                        drawArc(color = SuccessGreen, startAngle = 135f, sweepAngle = 270f * animatedProgress, useCenter = false, style = Stroke(width = 24f, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(animatedProgress * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White)
                        Text("Current Score", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Row { Text("Growth: ", color = Color.White, fontSize = 13.sp); Text("+5% this month", color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Trend: Improving", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tracks executive function and self-sufficiency.", color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun CoRegulationChartCard() {
    val cyanColor = Color(0xFF00BFFF)
    val neonGreen = Color(0xFF00FA9A)
    Card(modifier = Modifier.fillMaxWidth().height(340.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text("Co-regulation vs.\nSelf-regulation", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    for (i in 0..4) { val y = h - (h / 4 * i); drawLine(color = Color.White.copy(alpha = 0.1f), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 2f) }
                    val pathCyan = Path().apply { moveTo(0f, h * 0.1f); cubicTo(w * 0.3f, h * 0.15f, w * 0.6f, h * 0.6f, w, h * 0.8f) }
                    drawPath(pathCyan, color = cyanColor, style = Stroke(width = 6f))
                    drawCircle(color = cyanColor, radius = 8f, center = androidx.compose.ui.geometry.Offset(0f, h * 0.1f)); drawCircle(color = cyanColor, radius = 8f, center = androidx.compose.ui.geometry.Offset(w, h * 0.8f))
                    val pathGreen = Path().apply { moveTo(0f, h * 0.85f); cubicTo(w * 0.3f, h * 0.7f, w * 0.7f, h * 0.5f, w, h * 0.15f) }
                    drawPath(pathGreen, color = neonGreen, style = Stroke(width = 6f))
                    drawCircle(color = neonGreen, radius = 8f, center = androidx.compose.ui.geometry.Offset(0f, h * 0.85f)); drawCircle(color = neonGreen, radius = 8f, center = androidx.compose.ui.geometry.Offset(w, h * 0.15f))
                }
                Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf("100", "80", "60", "40", "20", "0").forEach { Text(it, color = TextSecondary, fontSize = 10.sp) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Jun 1", "Jun 10", "Jun 30").forEach { Text(it, color = TextSecondary, fontSize = 10.sp) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(8.dp).background(cyanColor, CircleShape)); Spacer(modifier = Modifier.width(4.dp)); Text("Co-regulation", color = TextSecondary, fontSize = 10.sp) }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(8.dp).background(neonGreen, CircleShape)); Spacer(modifier = Modifier.width(4.dp)); Text("Self-regulation", color = TextSecondary, fontSize = 10.sp) }
        }
    }
}

@Composable
fun TaskInitiationHeatmapCard() {
    Card(modifier = Modifier.fillMaxWidth().height(162.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Task Initiation", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val heatData = listOf(listOf(0.2f, 0.4f, 0.3f, 0.2f), listOf(0.3f, 0.7f, 0.4f, 0.3f), listOf(0.4f, 0.9f, 0.6f, 0.4f), listOf(0.3f, 0.8f, 0.5f, 0.3f), listOf(0.4f, 0.6f, 0.3f, 0.2f), listOf(0.1f, 0.2f, 0.2f, 0.1f), listOf(0.1f, 0.1f, 0.1f, 0.1f))
            Row(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, color = TextSecondary, fontSize = 8.sp) } }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    heatData.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { intensity -> Box(modifier = Modifier.fillMaxHeight().weight(1f).padding(1.dp).background(Color(red = CardBg.red + (SuccessGreen.red - CardBg.red) * intensity, green = CardBg.green + (SuccessGreen.green - CardBg.green) * intensity, blue = CardBg.blue + (SuccessGreen.blue - CardBg.blue) * intensity))) }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("6am-\n9am", "9am-\n12pm", "12pm-\n3pm", "3pm-\n6pm").forEach { Text(it, color = TextSecondary, fontSize = 8.sp, textAlign = TextAlign.Center) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Peak: Weekdays, 9am-12pm", color = TextSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TokenVelocityBarChartCard() {
    Card(modifier = Modifier.fillMaxWidth().height(162.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Token Velocity", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            val chartData = listOf(45f, 60f, 55f, 70f, 85f, 50f, 40f)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height; val barWidth = w / 7f; val path = Path()
                    chartData.forEachIndexed { index, value -> val x = (barWidth * index) + (barWidth / 2f); val y = h - ((value / 100f) * h) - 10f; if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    drawPath(path, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = 3f))
                }
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    chartData.forEach { value ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${value.toInt()}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                            Box(modifier = Modifier.width(16.dp).fillMaxHeight(value / 100f).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Brush.verticalGradient(listOf(RewardGold, Color(0xFFD97706)))))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, color = TextSecondary, fontSize = 8.sp) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Total: 405 tokens", color = TextSecondary, fontSize = 10.sp)
        }
    }
}