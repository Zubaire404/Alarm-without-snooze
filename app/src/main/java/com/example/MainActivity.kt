package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.buildAnnotatedString

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Alarm
import com.example.service.AlarmService
import com.example.ui.AlarmViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    val alarms by viewModel.allAlarms.collectAsStateWithLifecycle()
    val isRinging by AlarmService.isRinging.collectAsStateWithLifecycle()
    val ringingAlarmLabel by AlarmService.ringingAlarmLabel.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { mutableStateOf(true) }

    // Request dynamic notification permission (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            notificationPermissionGranted = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Notification permission is required for alarm notifications to display", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            notificationPermissionGranted = isGranted
            if (!isGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "A-Minute",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Text(
                                text = "Pure intent. Zero delay.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFFD0BCFF),  // Soft Lavender accent from design specs
                    contentColor = Color(0xFF381E72),    // High-contrast deep violet from design specs
                    shape = RoundedCornerShape(16.dp),   // Minimalist rounded 16dp
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("add_alarm_fab")
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Drawing minimalist intersecting lines of the cross manually for maximum visual precision
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(2.dp)
                                .background(Color(0xFF381E72))
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight(0.45f)
                                .background(Color(0xFF381E72))
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ticking Digital Clock Card & Enforcement Guidelines Banner
                ClockHeader()

                Spacer(modifier = Modifier.height(28.dp))

                // Action summary banner about NO snooze
                RuleBanner()

                Spacer(modifier = Modifier.height(28.dp))

                // Master alarm list
                if (alarms.isEmpty()) {
                    EmptyAlarmsState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(alarms, key = { it.id }) { alarm ->
                            AlarmRow(
                                alarm = alarm,
                                onToggle = { viewModel.toggleAlarm(alarm) },
                                onDelete = { viewModel.deleteAlarm(alarm) }
                            )
                        }
                    }
                }
            }
        }

        // Add Alarm Screen Dialog
        if (showAddDialog) {
            AddAlarmDialog(
                onDismiss = { showAddDialog = false },
                onSave = { hour, minute, label, repeatDays ->
                    viewModel.addAlarm(hour, minute, label, repeatDays)
                    showAddDialog = false
                    Toast.makeText(context, "Alarm scheduled successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Ringing Overlay (Shown dynamically if service triggers sound)
        AnimatedVisibility(
            visible = isRinging,
            enter = fadeIn(animationSpec = tween(400)) + expandVertically(),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically()
        ) {
            AlarmRingingOverlay(
                label = ringingAlarmLabel,
                onDismiss = {
                    val dismissIntent = Intent(context, AlarmService::class.java).apply {
                        action = AlarmService.ACTION_DISMISS
                    }
                    context.stopService(dismissIntent)
                }
            )
        }
    }
}

@Composable
fun ClockHeader() {
    var timeStringOnly by remember { mutableStateOf("") }
    var amPmString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance().time
            timeStringOnly = SimpleDateFormat("hh:mm", Locale.getDefault()).format(now)
            amPmString = SimpleDateFormat("a", Locale.getDefault()).format(now)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High-Fidelity display text clock
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = timeStringOnly,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 84.sp,
                    letterSpacing = (-4).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = amPmString.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 0.sp
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // High-intent metadata configuration tags
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "NON-STOP RING",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer)
            )
            Text(
                text = "STRICT 60S",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
fun RuleBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.5f) // Transparent white from HTML
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Elegant inner circular dot mimicking HTML spec
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3EDF7)) // M3 light secondary surface
                    .wrapContentSize(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "The Minute Rule",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                
                // Styled Paragraph mapping HTML text
                Text(
                    text = buildAnnotatedString {
                        append("This alarm will ring at full volume for exactly 60 seconds. ")
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB3261E) // Warning dark red
                            )
                        )
                        append("Snooze is disabled.")
                        pop()
                        append(" If not silenced manually, it terminates after 1 minute.")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}

@Composable
fun EmptyAlarmsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "No Alarms",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No alarm schedules",
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Set a high-urgency wake-up alarm below",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AlarmRow(
    alarm: Alarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val amPm = if (alarm.hour >= 12) "PM" else "AM"
    val adjustedHour = when {
        alarm.hour == 0 -> 12
        alarm.hour > 12 -> alarm.hour - 12
        else -> alarm.hour
    }
    val timeFormatted = String.format("%02d:%02d", adjustedHour, alarm.minute)

    // Border and container color transitions based on Clean Minimalism specification
    val isEnabled = alarm.isEnabled
    val cardBg = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
    val cardContentColor = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
    val cardBorder = if (isEnabled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        shape = RoundedCornerShape(28.dp), // Premium high-corner 28.dp from visual model
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Next alarm dynamic labeling helper
                if (isEnabled) {
                    Text(
                        text = "Active Alarm",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = cardContentColor.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Normal, // Minimalist medium weight display
                            color = if (isEnabled) cardContentColor else cardContentColor.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = amPm,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = if (isEnabled) cardContentColor.copy(alpha = 0.6f) else cardContentColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (alarm.label.isNotEmpty()) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isEnabled) cardContentColor.copy(alpha = 0.8f) else cardContentColor.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom recurrence days view
                if (alarm.isRepeating()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        val activeDays = mutableListOf<String>()
                        days.forEachIndexed { index, dayName ->
                            val active = alarm.isDayEnabled(
                                when (index) {
                                    0 -> Calendar.MONDAY
                                    1 -> Calendar.TUESDAY
                                    2 -> Calendar.WEDNESDAY
                                    3 -> Calendar.THURSDAY
                                    4 -> Calendar.FRIDAY
                                    5 -> Calendar.SATURDAY
                                    6 -> Calendar.SUNDAY
                                    else -> Calendar.MONDAY
                                }
                            )
                            if (active) {
                                activeDays.add(dayName)
                            }
                        }
                        
                        Text(
                            text = if (activeDays.size == 7) "Every day" 
                                   else if (activeDays.size == 5 && !activeDays.contains("Sat") && !activeDays.contains("Sun")) "Weekdays"
                                   else if (activeDays.isEmpty()) "One-time action"
                                   else activeDays.joinToString(", "),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isEnabled) cardContentColor.copy(alpha = 0.62f) else cardContentColor.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Once-off ring",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isEnabled) cardContentColor.copy(alpha = 0.62f) else cardContentColor.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Controls layout side-by-side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Material 3 Switch with custom track color mapping from HTML spec
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("alarm_switch_${alarm.id}")
                )

                // Delete Button
                IconButton(
                    onClick = {
                        onDelete()
                        Toast.makeText(context, "Alarm removed", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("alarm_delete_${alarm.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Alarm",
                        tint = if (isEnabled) cardContentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// Custom Picker Selector helper view
@Composable
fun TimePartPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    max: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(6.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        IconButton(
            onClick = { onValueChange((value + 1) % (max + 1)) },
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increment $label",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = String.format("%02d", value),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Normal, // Clean Light Display heading
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 44.sp
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        IconButton(
            onClick = { onValueChange((value - 1 + max + 1) % (max + 1)) },
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrement $label",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAlarmDialog(
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, label: String, repeatDays: Int) -> Unit
) {
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var label by remember { mutableStateOf("") }
    var repeatDays by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("add_alarm_dialog"),
            shape = RoundedCornerShape(28.dp), // Unified high-corner rounding
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SCHEDULE PROTOCOL",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 1.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Custom Hours / Minutes tactile control center
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimePartPicker(
                        value = hour,
                        onValueChange = { hour = it },
                        label = "Hour",
                        max = 23
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 44.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    )
                    TimePartPicker(
                        value = minute,
                        onValueChange = { minute = it },
                        label = "Minute",
                        max = 59
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Text field for Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Alarm Note / Warning Label") },
                    placeholder = { Text("e.g. GET UP AND RUN!") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_label_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Week repeats picker
                Text(
                    text = "REPETITION DAYS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    days.forEachIndexed { index, dayLetter ->
                        val bitPosition = index
                        val isSelected = (repeatDays and (1 shl bitPosition)) != 0
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                                )
                                .clickable {
                                    repeatDays = repeatDays xor (1 shl bitPosition)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLetter,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Buttons container
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("CANCEL")
                    }
                    Button(
                        onClick = { onSave(hour, minute, label, repeatDays) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_alarm_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("SAVE")
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmRingingOverlay(
    label: String,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Red warning backdrop gradient
    val ringGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFD32F2F), // Blood red
            Color(0xFF5D1212) // Midnight dark red
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ringGradient)
            .padding(24.dp)
            .testTag("ringing_overlay"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High Urgency pulsing alarm bell icon
        Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = "ALARM ACTIVE",
            tint = Color.White,
            modifier = Modifier
                .size(110.dp)
                .scale(pulseScale)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "ALARM TRIGGERED",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 3.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label.ifEmpty { "WAKE UP PROTOCOL" }.uppercase(),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 32.sp
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large prominent visual countdown rule statement
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "RING FORCE: 1 MINUTE MAX",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCDD2),
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NO SNOOZE IS POSSIBLE.\nIt will auto-stop automatically in 60 seconds if you do not dismiss it manually.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(54.dp))

        // Large circular, satisfying DISMISS button
        Button(
            onClick = { onDismiss() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(80.dp)
                .scale(pulseScale)
                .testTag("dismiss_ringing_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "DISMISS NOW",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFD32F2F),
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}
