package com.gliffy.g2048.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gliffy.g2048.Game2048App

/**
 * Whole-app game screen. [GameMachine] is created once (remember) and
 * observed via collectAsState. Settings is a full-screen dimmed overlay
 * with a card (simpler + fully supported than the m3 ModalBottomSheet in
 * every cached version). Win/lose use the same overlay pattern.
 */
@Composable
fun GameScreen() {
    val ctx = LocalContext.current
    val app = Game2048App.instance
    // True on cold start when there is nothing to resume — greet via menu.
    val firstLaunch = app.prefs.liveState == null
    val machine = remember(app) { GameMachine(app.prefs, app.haptics, app.audio) }
    val ui by machine.ui.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (ui.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember(firstLaunch) { mutableStateOf(firstLaunch) }
    var keepGoingDismissed by remember { mutableStateOf(false) }

    GameTheme(dark = isDark) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Palette.pageDark else Palette.pageLight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // ---- header row: title / menu / new / undo / settings ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "2048",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Palette.textDark else Palette.textLight,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { machine.uiTap(); showMenu = true }) {
                        Text(
                            text = "☰",
                            fontSize = 20.sp,
                            color = if (isDark) Palette.textDark else Palette.textLight,
                        )
                    }
                    IconButton(onClick = { machine.uiTap(); machine.newGame(ui.state.daily) }) {
                        Text(
                            text = "+",
                            fontSize = 24.sp,
                            color = if (isDark) Palette.textDark else Palette.textLight,
                        )
                    }
                    val undoEnabled = ui.canUndo && !ui.state.isOver()
                    IconButton(
                        onClick = { if (undoEnabled) { machine.uiTap(); machine.undo() } },
                        enabled = undoEnabled,
                    ) {
                        Text(
                            text = "↩",
                            fontSize = 20.sp,
                            color = if (isDark) Palette.textDark else Palette.textLight,
                        )
                    }
                    IconButton(onClick = { machine.uiTap(); showSettings = true }) {
                        Text(
                            text = "⚙",
                            fontSize = 20.sp,
                            color = if (isDark) Palette.textDark else Palette.textLight,
                        )
                    }
                }

                // ---- mode + score row ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = ui.modeName,
                        fontSize = 13.sp,
                        color = if (isDark) Palette.textSubDark else Palette.textSubLight,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    ScoreBox("SCORE", ui.state.score, isDark)
                    ScoreBox("BEST", app.prefs.best(ui.key), isDark)
                }
                Spacer(Modifier.height(10.dp))

                // ---- board ----
                // Keep the board a true square: fill the remaining height but
                // cap width so aspectRatio(1f) never gets squeezed by the
                // header/score rows (which would letterbox the grid and make
                // tiles appear mid-row).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BoardPanel(
                        snap = ui,
                        enabled = !ui.state.isOver() &&
                            !ui.state.won || ui.state.keepGoing,
                        isDark = isDark,
                        onMove = machine::tryMove,
                        onAnimSettled = machine::onAnimSettled,
                    )
                }
            }

            // ---- main menu overlay ----
            if (showMenu) {
                MainMenuOverlay(
                    isDark = isDark,
                    canResume = !firstLaunch && !ui.state.isOver(),
                    dailyDate = machine.dailyDateLabel(),
                    onNewGame = {
                        machine.uiTap()
                        machine.newGame(false)
                        showMenu = false
                    },
                    onDaily = {
                        machine.uiTap()
                        machine.newGame(true)
                        showMenu = false
                    },
                    onResume = {
                        machine.uiTap()
                        showMenu = false
                    },
                )
            }
            // ---- settings overlay ----
            if (showSettings) {
                SettingsOverlay(
                    isDark = isDark,
                    ui = ui,
                    machine = machine,
                    onDismiss = { showSettings = false },
                )
            }
            // ---- win overlay ----
            if (ui.state.won && !ui.state.keepGoing && ui.animSettled && !keepGoingDismissed) {
                ResultOverlay(
                    isDark = isDark,
                    title = "You got 2048!",
                    score = ui.state.score,
                    best = app.prefs.best(ui.key),
                    primaryLabel = "Keep going",
                    secondaryLabel = "New game",
                    onPrimary = { machine.continuePlaying() },
                    onSecondary = { machine.newGame(ui.state.daily) },
                )
            }
            // ---- lose overlay ----
            if (ui.state.isOver() && ui.animSettled) {
                ResultOverlay(
                    isDark = isDark,
                    title = "Game over",
                    score = ui.state.score,
                    best = app.prefs.best(ui.key),
                    primaryLabel = "Try again",
                    secondaryLabel = "Main menu",
                    onPrimary = { machine.newGame(ui.state.daily) },
                    onSecondary = {
                        showMenu = true
                        showSettings = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MainMenuOverlay(
    isDark: Boolean,
    canResume: Boolean,
    dailyDate: String,
    onNewGame: () -> Unit,
    onDaily: () -> Unit,
    onResume: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Palette.pageDark else Palette.pageLight),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(24.dp).width(260.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Palette.boardDark else Color(0xFFF7F0E5),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title rendered as a 2048-style tile.
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFEDC22E), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "2048",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
                    Text("New game")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDaily, modifier = Modifier.fillMaxWidth()) {
                    Text("Daily challenge")
                }
                Text(
                    text = "Same seed for everyone: $dailyDate",
                    fontSize = 11.sp,
                    color = if (isDark) Palette.textSubDark else Palette.textSubLight,
                    textAlign = TextAlign.Center,
                )
                if (canResume) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF6C5CA7) else Color(0xFF8F7A66),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Resume last game") }
                }
            }
        }
    }
}

@Composable
private fun ScoreBox(label: String, value: Int, isDark: Boolean) {
    Surface(
        color = if (isDark) Palette.scoreBoxDark else Palette.scoreBoxLight,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.sizeIn(minWidth = 64.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = if (isDark) Palette.scoreLblDark else Palette.scoreLblLight,
            )
            Text(
                text = value.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ResultOverlay(
    isDark: Boolean,
    title: String,
    score: Int,
    best: Int,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(24.dp).width(230.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Palette.boardDark else Color(0xFFF7F0E5),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = if (isDark) Palette.textDark else Color(0xFF776E65),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Score $score   ·   Best $best",
                    fontSize = 13.sp,
                    color = if (isDark) Palette.textSubDark else Palette.textSubLight,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onPrimary) { Text(primaryLabel) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsOverlay(
    isDark: Boolean,
    ui: UiSnap,
    machine: GameMachine,
    onDismiss: () -> Unit,
) {
    var pendingSize by remember { mutableStateOf<Int?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Palette.boardDark else Color(0xFFF7F0E5),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Palette.textDark else Color(0xFF776E65),
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Board size",
                    fontSize = 12.sp,
                    color = if (isDark) Palette.textSubDark else Palette.textSubLight,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sizes = listOf(3 to "Mini 3×3", 4 to "Classic 4×4", 6 to "Expert 6×6")
                    for ((size, name) in sizes) {
                        Chip(
                            text = name,
                            selected = ui.state.size == size,
                            isDark = isDark,
                            onClick = {
                                machine.uiTap()
                                if (!machine.chooseSize(size)) {
                                    // in-progress game: ask before restarting
                                    pendingSize = size
                                }
                            },
                        )
                    }
                }
                if (pendingSize != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Changing board size restarts the game.",
                        fontSize = 12.sp,
                        color = if (isDark) Palette.textSubDark else Palette.textSubLight,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            machine.uiTap()
                            pendingSize = null
                        }) { Text("Cancel") }
                        Button(onClick = {
                            machine.uiTap()
                            machine.chooseSize(pendingSize!!, force = true)
                            pendingSize = null
                        }) { Text("Restart at new size") }
                    }
                }
                Spacer(Modifier.height(14.dp))

                ToggleRow("Sound", isDark, ui.soundOn, on = { machine.setSound(true) }, off = { machine.setSound(false) })
                ToggleRow("Haptics", isDark, ui.hapticsOn, on = { machine.setHaptics(true) }, off = { machine.setHaptics(false) })
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Daily challenge — same seed for everyone: ${machine.dailyDateLabel()}",
                    fontSize = 12.sp,
                    color = if (isDark) Palette.textSubDark else Palette.textSubLight,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        machine.uiTap()
                        machine.newGame(true)
                        onDismiss()
                    }) { Text("Start daily") }
                    Button(onClick = {
                        machine.uiTap()
                        machine.newGame(false)
                        onDismiss()
                    }) { Text("New regular") }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            selected -> if (isDark) Color(0xFF6C5CA7) else Color(0xFF8F7A66)
            else -> if (isDark) Palette.scoreBoxDark else Palette.scoreBoxLight.copy(alpha = 0.6f)
        },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = if (selected) Color.White else if (isDark) Palette.textDark else Palette.textLight,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    isDark: Boolean,
    checked: Boolean,
    on: () -> Unit,
    off: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (isDark) Palette.textDark else Color(0xFF776E65),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = { if (it) on() else off() })
    }
}

