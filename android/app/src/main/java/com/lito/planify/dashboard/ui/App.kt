package com.lito.planify.dashboard.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.lito.planify.dashboard.data.Event
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class Page(val label: String, val icon: ImageVector) {
    Today("Avui", Icons.Outlined.Today),
    Photos("Fotos", Icons.Outlined.PhotoLibrary),
    Calendar("Calendari", Icons.Outlined.CalendarMonth),
    Weather("El temps", Icons.Outlined.Thermostat),
    Energy("Energia", Icons.Outlined.Bolt),
    Home("Casa i energia", Icons.Outlined.Home),
    Tasks("Família", Icons.Outlined.Checklist),
    Reminders("Recordatoris", Icons.Outlined.Checklist),
    Settings("Opcions", Icons.Outlined.Settings)
}

/** Nombre corto bajo el icono de la barra lateral. */
private val Page.shortLabel: String
    get() = when (this) {
        Page.Home -> "Casa"
        else -> label
    }

/** Energia queda integrada a Casa, que també permet vincular les càmeres Nest. */
private fun Page.available() = when (this) {
    Page.Energy -> false // de momento dentro de «Casa y energía»
    else -> true
}

/** Acciones que dependen de la actividad (pantalla, quiosco, ajustes de Android). */
class DashboardActions(
    val sleep: () -> Unit,
    val openWifi: () -> Unit,
    val openSettings: () -> Unit,
    val powerMenu: () -> Unit,
)

private const val IDLE_MILLIS = 120_000L

/** Solape entre las líneas del reloj lateral: deja la distancia entre las cifras en el interlineado (52). */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardApp(
    state: UiState,
    actions: DashboardActions,
    onLoadWeek: (LocalDate) -> Unit,
    onSync: () -> Unit,
    settings: SettingsCallbacks,
    onPeek: () -> Unit,
    chores: ChoreCallbacks,
    home: HomeCallbacks = HomeCallbacks(),
    nightClock: NightClockSettings
) {
    var page by remember { mutableStateOf(Page.Today) }
    // Manté els camps editats en tornar a una pestanya, sense crear una segona còpia de les dades.
    val pageState = rememberSaveableStateHolder()
    // Sopars compartits entre la portada i el calendari, desats només al dispositiu.
    val dinners = rememberDinnerPlan()
    val family = rememberLocalFamilyStore()
    val reminders = rememberFamilyReminders()
    val sync = rememberFamilySync()
    val frameMode = rememberFrameModeSettings()
    val sharedPhotos = rememberFamilyPhotoCache()
    // Manté la seqüència en reprendre les fotos després de mostrar Avui.
    val photoPlayback = remember { PhotoPlaybackState() }
    val context = LocalContext.current
    val nestPrefs = remember(context) { context.getSharedPreferences("marc_nest", Context.MODE_PRIVATE) }
    var nestKey by remember(nestPrefs) { mutableStateOf(nestPrefs.getString("camera_key", "").orEmpty()) }
    var nestDevice by remember { mutableStateOf<NestDevice?>(null) }
    var nestPairing by remember { mutableStateOf(false) }
    var photosAvailable by remember {
        mutableStateOf(sharedPhotos.photos.isNotEmpty() ||
            context.getSharedPreferences("marc_fotos", Context.MODE_PRIVATE).getString("carpeta", null) != null)
    }
    val coroutineScope = rememberCoroutineScope()
    // Escolta els canvis locals: en cas de no tenir Internet, queden a la cua del marc.
    LaunchedEffect(sync, dinners, family, reminders) {
        sync.attachReminders(reminders)
        sync.attachCalendars(settings.onReadCalendars, settings.onApplySharedCalendars)
        settings.onCalendarMutationListener { sync.queueCalendar(it) }
        reminders.onMutation = { op -> sync.queue(op) }
        dinners.onLocalChange = { day, meal ->
            sync.queue(JSONObject().put("type", "dinner_set")
                .put("date", day.toString()).put("value", meal))
        }
        family.onGroceryMutation = { op -> sync.queue(op) }
        family.onFamilyMutation = { op -> sync.queue(op) }
        try {
            awaitCancellation()
        } finally {
            settings.onCalendarMutationListener(null)
            sync.detachCalendars()
            dinners.onLocalChange = null
            family.onGroceryMutation = null
            family.onFamilyMutation = null
            reminders.onMutation = null
        }
    }
    // En obrir el marc: si fa més de 5 minuts, consulta les dades.
    // Després comprova el rellotge cada minut, però només fa una petició quan toca.
    LaunchedEffect(sync.connected, sync.dataIntervalMinutes) {
        if (!sync.connected) return@LaunchedEffect
        sync.synchronize(dinners, family, minAgeMillis = 5 * 60_000L)
        while (true) {
            delay(60_000L)
            sync.synchronize(dinners, family, minAgeMillis = sync.dataIntervalMinutes * 60_000L)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        coroutineScope.launch {
            sync.synchronize(dinners, family, minAgeMillis = 5 * 60_000L)
        }
    }
    LaunchedEffect(sync.connected) {
        if (!sync.connected) return@LaunchedEffect
        snapshotFlow { sync.pendingRevision }.collect {
            if (sync.pendingCount > 0) sync.synchronize(dinners, family, minAgeMillis = 1L)
        }
    }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cycle by remember { mutableStateOf(FrameCycle.interact(SystemClock.elapsedRealtime())) }
    var cycleSuspended by remember { mutableStateOf(true) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val keyboardVisible = WindowInsets.isImeVisible
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var appResumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var pointerDown by remember { mutableStateOf(false) }
    val automaticPhotos = cycle.phase == FrameCyclePhase.PHOTOS
    val onInteraction: () -> Boolean = {
        val leavePhotos = cycle.phase == FrameCyclePhase.PHOTOS
        cycle = FrameCycle.interact(SystemClock.elapsedRealtime())
        lastTouch = System.currentTimeMillis()
        if (leavePhotos) page = Page.Today
        leavePhotos
    }
    val currentInteraction by rememberUpdatedState(onInteraction)
    val selectPage: (Page) -> Unit = { selected -> onInteraction(); nestDevice = null; page = selected }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appResumed = true
        onInteraction()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        onInteraction()
        pointerDown = false
        nestDevice = null
        appResumed = false
    }
    /** Cita abierta en el aviso ([EventOverlay]); se cierra sola al volver a Hoy por inactividad. */
    var shownEvent by remember { mutableStateOf<Event?>(null) }

    val hasPhotos = photosAvailable || sharedPhotos.photos.isNotEmpty()
    // Una sola màquina d'estats coordina les fotos i les visites periòdiques a Avui.
    LaunchedEffect(frameMode.config, page, shownEvent, nestDevice, nestPairing, state.night, appResumed, pointerDown, hasPhotos, windowFocused, keyboardVisible) {
        val config = frameMode.config
        if (!config.enabled || !appResumed || state.night || pointerDown || !windowFocused || keyboardVisible || page == Page.Settings || shownEvent != null || nestDevice != null || nestPairing || !hasPhotos) {
            if (cycle.phase != FrameCyclePhase.MANUAL) page = Page.Today
            cycle = FrameCycle.interact(SystemClock.elapsedRealtime())
            cycleSuspended = true
            return@LaunchedEffect
        }
        if (cycleSuspended) {
            cycle = FrameCycle.interact(SystemClock.elapsedRealtime())
            cycleSuspended = false
        }
        while (true) {
            val updated = FrameCycle.advance(cycle, SystemClock.elapsedRealtime(), config)
            if (updated != cycle) {
                cycle = updated
                page = if (updated.phase == FrameCyclePhase.PHOTOS) Page.Photos else Page.Today
            }
            delay(1_000L)
        }
    }
    // Prepara la còpia compartida encara que encara no hàgim obert la galeria.
    LaunchedEffect(frameMode.config.enabled, sync.connected, sync.photoIntervalMinutes, appResumed) {
        if (!frameMode.config.enabled || !sync.connected || !appResumed) return@LaunchedEffect
        while (true) {
            sharedPhotos.refresh(sync, minAgeMillis = sync.photoIntervalMinutes * 60_000L)
            delay(60_000L)
        }
    }
    // Amb l'alternança desactivada, es conserva el retorn habitual de dos minuts.
    LaunchedEffect(page, lastTouch, frameMode.config.enabled, nestDevice, nestPairing) {
        if (nestDevice != null || nestPairing || frameMode.config.enabled || page == Page.Today || page == Page.Photos || page == Page.Settings) return@LaunchedEffect
        delay(IDLE_MILLIS)
        page = Page.Today
    }
    // Tras 2 minutos sin tocar, el aviso de una cita también se cierra (en cualquier pestaña).
    LaunchedEffect(shownEvent, lastTouch) {
        if (shownEvent == null) return@LaunchedEffect
        delay(IDLE_MILLIS)
        shownEvent = null
    }
    // Es el launcher: "atrás" cierra el aviso o vuelve a Hoy, y nunca cierra la actividad.
    BackHandler {
        onInteraction()
        when {
            nestDevice != null -> nestDevice = null
            shownEvent != null -> shownEvent = null
            else -> page = Page.Today
        }
    }
    LaunchedEffect(state.night) { if (state.night) nestDevice = null }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .onPreviewKeyEvent { currentInteraction(); false }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var consumeGesture = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed || it.previousPressed }) {
                            consumeGesture = currentInteraction() || consumeGesture
                        }
                        pointerDown = event.changes.any { it.pressed }
                        if (consumeGesture) event.changes.forEach { it.consume() }
                        if (!pointerDown) consumeGesture = false
                    }
                }
            }
    ) {
        if (state.night) {
            NightScreen(state, nightClock.config, onWake = {
                selectPage(Page.Today)
                onPeek()
            })
            return@Box
        }
        CompositionLocalProvider(LocalShowEvent provides { onInteraction(); shownEvent = it }) {
            // La barra lateral a la derecha, con el nombre bajo cada icono
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    pageState.SaveableStateProvider(page.name) {
                    when (page) {
                        // La portada familiar funciona sense credencials; el backend antic és opcional.
                        Page.Today -> if (state.ics || state.token == null) FamilyTodayScreen(state, reminders, family, onOpen = selectPage) else TodayScreen(state, lastTouch)
                        Page.Photos -> PhotosScreen(sync, shared = sharedPhotos, presentationMode = automaticPhotos,
                            slideSeconds = frameMode.config.slideSeconds,
                            shufflePhotos = frameMode.config.shufflePhotos,
                            playback = photoPlayback,
                            onPhotosAvailable = { photosAvailable = it },
                            onExitPresentation = { selectPage(Page.Today) })
                        // Con los calendarios de la familia, semanas en vertical como la portada; con Planify, la de Lito
                        Page.Calendar -> if (state.ics || state.token == null) FamilyCalendarScreen(state, reminders, onOpenReminders = { selectPage(Page.Reminders) }) else CalendarScreen(state, onLoadWeek)
                        Page.Weather -> WeatherScreen(state)
                        Page.Energy -> EnergyScreen(state)
                        // Prueba: Casa y Energía juntas (EnergyScreen y HomeScreen siguen por si se separan)
                        Page.Home -> {
                            val device = nestDevice
                            if (device != null) {
                                NestCameraViewer(device, nestKey, onClose = { onInteraction(); nestDevice = null })
                            } else {
                                HomeEnergyScreen(state, home, nestKey,
                                    onSaveNestKey = { key ->
                                        nestPrefs.edit().putString("camera_key", key).apply()
                                        nestKey = key
                                    },
                                    onOpenNest = { onInteraction(); nestDevice = it },
                                    onNestPairingChanged = { active ->
                                        if (nestPairing && !active) onInteraction()
                                        nestPairing = active
                                    })
                            }
                        }
                        Page.Tasks -> LocalFamilyScreen(family, state.now.toLocalDate(), sync.connected, sync.pendingCount, sync.cloudTasks >= 0)
                        Page.Reminders -> FamilyRemindersScreen(reminders, state.now.toLocalDate(), family, sync.connected, sync.pendingCount)
                        Page.Settings -> SettingsScreen(state, actions, settings, sync, dinners, family, frameMode, nightClock)
                    }
                    }
                }
                if (!automaticPhotos) Rail(page, state, onSelect = selectPage, onSync = {
                    onInteraction()
                    onSync()
                    coroutineScope.launch { sync.synchronize(dinners, family) }
                }, onSleep = actions.sleep)
            }
        }
        EventOverlay(shownEvent) { shownEvent = null }
    }
}

@Composable
private fun Rail(current: Page, state: UiState, onSelect: (Page) -> Unit, onSync: () -> Unit, onSleep: () -> Unit) {
    BoxWithConstraints(Modifier.width(136.dp).fillMaxHeight()) {
        val compact = maxHeight < 850.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Rail)
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = if (compact) 12.dp else 48.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
                Page.entries.filter { it != Page.Settings && it.available() }.forEach { page ->
                    RailItem(page.shortLabel, page.icon, page == current) { onSelect(page) }
                }
                SyncItem(state.syncing, onSync)
                RailItem(Page.Settings.shortLabel, Page.Settings.icon, current == Page.Settings) { onSelect(Page.Settings) }
                RailItem("Dormir", Icons.Outlined.Bedtime, false, onClick = onSleep)
            }
            if (!state.online || !state.connected) {
                if (compact) Spacer(Modifier.height(18.dp)) else Spacer(Modifier.weight(1f))
                Text(
                    "Sense connexió",
                    style = Type.Tiny.copy(color = Palette.Accent),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Sincronizar ahora. Gira mientras dura; el giro va con el reloj de fotogramas porque el marco tiene las animaciones
 * del sistema a 0 y Compose las respeta.
 */
@Composable
private fun SyncItem(syncing: Boolean, onSync: () -> Unit) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(syncing) {
        angle = 0f
        if (!syncing) return@LaunchedEffect
        val start = withFrameMillis { it }
        // Hacia la izquierda, como indica la flecha del icono
        while (true) withFrameMillis { angle = -((it - start) % 1000L) * 0.36f }
    }
    RailItem("Actualizar", Icons.Outlined.Sync, syncing, rotation = angle) { onSync() }
}

/** Botón de la barra lateral: el icono y su nombre debajo, en pequeño. */
@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, rotation: Float = 0f, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 104.dp, height = 92.dp)
            .background(if (selected) Palette.Card else Palette.Rail, RoundedCornerShape(26.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Palette.Ink else Palette.InkMuted, modifier = Modifier.size(38.dp).rotate(rotation))
        Text(
            label,
            style = Type.Tiny.copy(fontSize = 15.sp, color = if (selected) Palette.Ink else Palette.InkMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Botón en píldora. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    background: Color = Palette.Card,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(84.dp)
            // Recortado a su forma, para que el efecto al pulsar no salga cuadrado
            .clip(RoundedCornerShape(42.dp))
            .background(
                when {
                    !enabled -> Palette.Line
                    primary -> Palette.Ink
                    else -> background
                },
                RoundedCornerShape(42.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 40.dp)
    ) {
        Text(text, style = Type.Body.copy(color = if (primary) Palette.Card else Palette.Ink), maxLines = 1)
    }
}

@Composable
fun ScreenPadding(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 60.dp, bottom = 52.dp)) { content() }
}
