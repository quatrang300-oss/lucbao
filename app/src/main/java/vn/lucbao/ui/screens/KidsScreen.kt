package vn.lucbao.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.engine.EngineManager
import vn.lucbao.kids.KIDS_TOPICS
import vn.lucbao.kids.KidsChannel
import vn.lucbao.kids.KidsStore
import vn.lucbao.ui.components.Avatar
import vn.lucbao.ui.components.LucChip
import vn.lucbao.ui.components.PillButton
import vn.lucbao.ui.components.Thumb
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

// Kids screens use their own bright, friendly colours (not the app's dark/light theme).
private val KidBg = Color(0xFFFFF8EC)
private val KidGreen = Color(0xFF1F8A5B)
private val KidOrange = Color(0xFFF59E0B)
private val KidText = Color(0xFF2B2B2B)
private val KidMuted = Color(0xFF6B6B6B)
private val KidLine = Color(0xFFEADFCA)
private val KidMint = Color(0xFF3DDC97)

/** The "KID" button (orange candy, three coloured tilted letters) next to the settings gear. */
@Composable
fun KidBadge(onClick: () -> Unit) {
    Box(Modifier.size(width = 52.dp, height = 40.dp).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
    ), contentAlignment = Alignment.Center) {
        Box(Modifier.offset(y = 2.dp).size(width = 46.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFC96F12)))
        Row(
            Modifier
                .size(width = 46.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFD166), Color(0xFFFF9F43)))),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("K", color = Color(0xFFE63946), fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.rotate(-8f))
            Text("I", color = Color(0xFF1D6FE0), fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(y = (-1).dp))
            Text("D", color = Color(0xFF11905A), fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.rotate(8f))
        }
    }
}

private enum class PinFor { PARENT, CHANGE }

/** Chế độ Trẻ em, shown instead of the tabs while it is on. */
@Composable
fun KidsScreen(expanded: Boolean, onPlay: (Video) -> Unit) {
    val channels by KidsStore.channels.collectAsStateWithLifecycle()
    val feed by KidsStore.feed.collectAsStateWithLifecycle()
    val sessionEnd by KidsStore.sessionEnd.collectAsStateWithLifecycle()
    // First time: the parents choose a PIN, then the channels.
    var settingPin by remember { mutableStateOf(!KidsStore.hasPin()) }
    var askPin by remember { mutableStateOf<PinFor?>(null) }
    var parentOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf<String?>(null) }
    var channelFilter by remember { mutableStateOf<String?>(null) }
    var pinErrors by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { KidsStore.refresh(force = false) }

    // The phone was locked or put away with a parents' page open: close it, so the child who
    // gets the phone next can't use it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (parentOpen || addOpen || askPin != null || (settingPin && KidsStore.hasPin())) {
            parentOpen = false
            addOpen = false
            askPin = null
            settingPin = !KidsStore.hasPin()
            KidsStore.startSession()
        }
    }
    // Parents' pages close by themselves after 2 minutes without a touch.
    var parentTouch by remember { mutableIntStateOf(0) }
    LaunchedEffect(parentOpen, addOpen, parentTouch) {
        if (parentOpen || addOpen) {
            delay(120_000)
            parentOpen = false
            addOpen = false
            KidsStore.startSession()
        }
    }

    // Back: closes the open page; on the kids home it asks for the parents' PIN.
    BackHandler(enabled = !expanded) {
        when {
            settingPin -> if (!KidsStore.hasPin()) KidsStore.exit() else settingPin = false
            askPin != null -> askPin = null
            addOpen -> addOpen = false
            parentOpen -> {
                parentOpen = false
                KidsStore.startSession()
            }
            else -> askPin = PinFor.PARENT
        }
    }

    Box(Modifier.fillMaxSize().background(KidBg)) {
        KidsHome(
            channels = channels,
            items = feed.items,
            loading = feed.loading,
            failed = feed.failed,
            sessionEnd = sessionEnd,
            topic = topic,
            channelFilter = channelFilter,
            onTopic = { topic = it; channelFilter = null },
            onChannel = { channelFilter = if (channelFilter == it) null else it },
            onParent = { askPin = PinFor.PARENT },
            onRetry = { KidsStore.refresh(force = true) },
            onPlay = onPlay,
        )

        if (parentOpen || addOpen) {
            // Any touch on the parents' pages restarts the 2-minute timer.
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press) parentTouch++
                    }
                }
            }) {
                if (parentOpen) {
                    ParentPage(
                        channels = channels,
                        onAdd = { addOpen = true },
                        onChangePin = { askPin = PinFor.CHANGE },
                        onDone = {
                            parentOpen = false
                            KidsStore.startSession()
                            KidsStore.refresh(force = false)
                        },
                        onExit = { KidsStore.exit() },
                    )
                }
                if (addOpen) AddChannelPage(channels, onClose = { addOpen = false })
            }
        }

        if (settingPin) {
            SetPinOverlay(
                first = !KidsStore.hasPin(),
                onDone = { pin ->
                    val first = !KidsStore.hasPin()
                    KidsStore.setPin(pin)
                    settingPin = false
                    if (first) {
                        if (KidsStore.channels.value.isEmpty()) parentOpen = true else KidsStore.startSession()
                    }
                },
                onCancel = { if (!KidsStore.hasPin()) KidsStore.exit() else settingPin = false },
            )
        }
        askPin?.let { purpose ->
            PinOverlay(
                title = "Dành cho bố mẹ",
                subtitle = if (purpose == PinFor.CHANGE) "Nhập mã PIN hiện tại" else "Nhập mã PIN 4 số để thoát hoặc cài đặt",
                errors = pinErrors,
                onDone = { pin ->
                    when {
                        System.currentTimeMillis() < KidsStore.lockedUntil -> pinErrors++
                        KidsStore.checkPin(pin) -> {
                            KidsStore.recordOk()
                            pinErrors = 0
                            askPin = null
                            if (purpose == PinFor.CHANGE) settingPin = true else parentOpen = true
                        }
                        else -> {
                            KidsStore.recordFail()
                            pinErrors++
                        }
                    }
                },
                onCancel = { askPin = null; pinErrors = 0 },
            )
        }
    }
}

@Composable
private fun KidsHome(
    channels: List<KidsChannel>,
    items: List<Video>,
    loading: Boolean,
    failed: Boolean,
    sessionEnd: Long,
    topic: String?,
    channelFilter: String?,
    onTopic: (String?) -> Unit,
    onChannel: (String) -> Unit,
    onParent: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Video) -> Unit,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val topics = KIDS_TOPICS.filter { (id, _) -> channels.any { it.topic == id } }
    val allowed = when {
        channelFilter != null -> setOf(channelFilter)
        topic != null -> channels.filter { it.topic == topic }.map { it.url }.toSet()
        else -> null
    }
    val shown = if (allowed == null) items else items.filter { it.channelUrl in allowed }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lục Bảo ", color = KidGreen, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Bé", color = KidOrange, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .border(2.dp, KidLine, RoundedCornerShape(50))
                        .clickable(onClick = onParent)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LucIcons.Lock, null, tint = KidMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Bố mẹ", color = KidMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (sessionEnd > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val left = ((sessionEnd - now).coerceAtLeast(0L) + 59_999L) / 60_000L
                Text(
                    "Còn $left phút xem", color = KidGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE7F7EF))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        if (topics.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { KidChip("Tất cả", topic == null && channelFilter == null) { onTopic(null) } }
                    items(topics, key = { it.first }) { (id, label) ->
                        KidChip(label, topic == id && channelFilter == null) { onTopic(id) }
                    }
                }
            }
        }
        if (channels.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(channels, key = { it.url }) { ch ->
                        Column(
                            Modifier.width(66.dp).clickable { onChannel(ch.url) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .border(3.dp, if (channelFilter == ch.url) KidMint else Color.White, CircleShape)
                                    .padding(3.dp)
                            ) { Avatar(ch.name, 50.dp, ch.avatar) }
                            Text(
                                ch.name, color = KidMuted, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp,
                                textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
        when {
            channels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                KidMessage("Chưa có kênh nào.\nBố mẹ bấm \"Bố mẹ\" ở góc trên để chọn kênh cho bé nhé.")
            }
            shown.isEmpty() && loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KidGreen, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                }
            }
            shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    KidMessage(if (failed) "Chưa tải được video. Kiểm tra mạng rồi thử lại nhé." else "Chưa có video nào ở đây.")
                    KidChip("Thử lại", true, onRetry)
                }
            }
            else -> items(shown, key = { it.url }) { v ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .clickable { onPlay(v) }
                ) {
                    Thumb(v.thumbnail, Modifier.fillMaxWidth(), duration = v.duration, corner = 0.dp)
                    Text(
                        v.title, color = KidText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KidChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (on) Color(0xFF06301E) else KidMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) KidMint else Color.White)
            .border(2.dp, if (on) KidMint else KidLine, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun KidMessage(text: String) {
    Text(
        text, color = KidMuted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp)
    )
}

// ---------------------------------------------------------------------- PIN

/** Bottom card with a 4-digit keypad over a dimmed screen. */
@Composable
private fun PinOverlay(title: String, subtitle: String, errors: Int, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    var shownError by remember { mutableStateOf(false) }
    // [errors] counts wrong attempts: each new one shows the message again.
    LaunchedEffect(errors) { if (errors > 0) { shownError = true; digits = "" } }
    val error = errors > 0
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8C0A1E14))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color.White)
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = KidText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    shownError && error && System.currentTimeMillis() < KidsStore.lockedUntil ->
                        "Sai nhiều lần. Thử lại sau ${(KidsStore.lockedUntil - System.currentTimeMillis()) / 1000 + 1} giây"
                    shownError && error -> "Sai mã PIN, thử lại nhé"
                    else -> subtitle
                },
                color = if (shownError && error) Color(0xFFD64545) else KidMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 18.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i < digits.length) KidGreen else Color.Transparent)
                            .border(2.dp, KidGreen, CircleShape)
                    )
                }
            }
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Huỷ", "0", "⌫")
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF1F5F2))
                                .clickable {
                                    when (k) {
                                        "Huỷ" -> onCancel()
                                        "⌫" -> digits = digits.dropLast(1)
                                        else -> if (digits.length < 4) {
                                            digits += k
                                            shownError = false
                                            if (digits.length == 4) {
                                                val pin = digits
                                                digits = ""
                                                onDone(pin)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, color = KidText, fontSize = if (k.length > 1) 14.sp else 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Choosing a PIN: typed twice. */
@Composable
private fun SetPinOverlay(first: Boolean, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableIntStateOf(0) }
    PinOverlay(
        title = if (firstPin == null) (if (first) "Đặt mã PIN cho bố mẹ" else "Mã PIN mới") else "Nhập lại mã PIN",
        subtitle = if (firstPin == null) "4 số, dùng để thoát chế độ Trẻ em và cài đặt" else "Nhập lại để chắc chắn",
        errors = if (firstPin == null) mismatch else 0,
        onDone = { pin ->
            val p = firstPin
            when {
                p == null -> firstPin = pin
                p == pin -> onDone(pin)
                else -> {
                    firstPin = null
                    mismatch++
                }
            }
        },
        onCancel = onCancel,
    )
}

// ---------------------------------------------------------------------- parents

@Composable
private fun ParentPage(
    channels: List<KidsChannel>,
    onAdd: () -> Unit,
    onChangePin: () -> Unit,
    onDone: () -> Unit,
    onExit: () -> Unit,
) {
    val c = Luc.colors
    val timer by KidsStore.timer.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                vn.lucbao.ui.components.IconTap(LucIcons.Back, "Quay lại", tint = c.text, onClick = onDone)
                Text("Chế độ Trẻ em", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            ParentCard("Kênh được xem") {
                if (channels.isEmpty()) {
                    Text("Chưa có kênh nào. Thêm kênh để bé có video xem.", color = c.muted, fontSize = 13.sp)
                }
                channels.forEach { ch ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(ch.name, 36.dp, ch.avatar)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(topicLabel(ch.topic), color = c.muted, fontSize = 11.5.sp)
                        }
                        vn.lucbao.ui.components.IconTap(LucIcons.Close, "Bỏ kênh", size = 20, tint = c.muted) {
                            KidsStore.removeChannel(ch.url)
                        }
                    }
                }
                Text(
                    "＋ Thêm kênh", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, c.primary, RoundedCornerShape(12.dp))
                        .clickable(onClick = onAdd)
                        .padding(10.dp)
                )
            }
        }
        item {
            ParentCard("Thời gian xem mỗi lần") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(15, 30, 45, 60).forEach { m -> LucChip("$m phút", timer == m, { KidsStore.setTimer(m) }) }
                }
                Spacer(Modifier.height(7.dp))
                LucChip("Không giới hạn", timer == 0, { KidsStore.setTimer(0) })
                Text(
                    "Hết giờ, video tự dừng; bé cần bố mẹ nhập PIN để xem thêm.",
                    color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item {
            Text(
                "Bé chỉ xem được video của các kênh ở trên; video tiếp theo và tự phát cũng chỉ lấy từ các kênh này. " +
                    "Lịch sử xem của bé không lẫn vào gợi ý của người lớn.\n\n" +
                    "Mẹo: để bé không thoát được ra màn hình chính bằng nút Home, bật \"Ghim ứng dụng\" " +
                    "(Cài đặt điện thoại → Bảo mật) rồi ghim Lục Bảo.",
                color = c.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Cho bé xem", LucIcons.Play, onDone, modifier = Modifier.fillMaxWidth())
                PillButton("Thoát chế độ Trẻ em", null, onExit, filled = false, modifier = Modifier.fillMaxWidth())
                Text(
                    "Đổi mã PIN", color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onChangePin).padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun ParentCard(title: String, content: @Composable () -> Unit) {
    val c = Luc.colors
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .padding(14.dp)
    ) {
        Text(title, color = c.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

private fun topicLabel(id: String) = KIDS_TOPICS.firstOrNull { it.first == id }?.second ?: "Khác"

private data class FoundChannel(val url: String, val name: String, val avatar: String?)

/** Adding channels: from the followed channels, or by searching a name. */
@Composable
private fun AddChannelPage(channels: List<KidsChannel>, onClose: () -> Unit) {
    val c = Luc.colors
    val followed by Library.channels.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf("cartoon") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FoundChannel>?>(null) }
    var searchFailed by remember { mutableStateOf(false) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        searchFailed = false
        scope.launch {
            val found = runCatching {
                withContext(Dispatchers.IO) {
                    EngineManager.get().search(q, null).items
                        .filter { !it.channelUrl.isNullOrBlank() && !it.channel.isNullOrBlank() }
                        .distinctBy { it.channelUrl }
                        .map { FoundChannel(it.channelUrl, it.channel, null) }
                }
            }
            results = found.getOrNull() ?: emptyList()
            searchFailed = found.isFailure
            searching = false
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                vn.lucbao.ui.components.IconTap(LucIcons.Back, "Quay lại", tint = c.text, onClick = onClose)
                Text("Thêm kênh cho bé", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text("Thêm vào chủ đề", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(KIDS_TOPICS, key = { it.first }) { (id, label) -> LucChip(label, topic == id, { topic = id }) }
            }
        }
        item {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface2)
                    .border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LucIcons.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Tìm tên kênh, ví dụ: hoạt hình thiếu nhi", color = c.muted, fontSize = 13.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Tìm", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { search() }.padding(8.dp)
                )
            }
        }
        if (searching) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                }
            }
        }
        results?.let { list ->
            item { SectionLabel(if (searchFailed) "Chưa tìm được, kiểm tra mạng rồi thử lại" else "Kết quả tìm kiếm") }
            items(list, key = { "r" + it.url }) { ch -> ChannelPickRow(ch, channels, topic) }
        }
        if (followed.isNotEmpty()) {
            item { SectionLabel("Kênh bạn đang theo dõi") }
            items(followed, key = { "f" + it.url }) { f -> ChannelPickRow(FoundChannel(f.url, f.name, f.avatar), channels, topic) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Luc.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun ChannelPickRow(ch: FoundChannel, channels: List<KidsChannel>, topic: String) {
    val c = Luc.colors
    val added = channels.firstOrNull { it.url == ch.url }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                if (added != null) KidsStore.removeChannel(ch.url)
                else KidsStore.addChannel(KidsChannel(ch.url, ch.name, ch.avatar, topic))
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(ch.name, 38.dp, ch.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (added != null) Text("Đã thêm · ${topicLabel(added.topic)}", color = c.primary, fontSize = 11.5.sp)
        }
        if (added != null) Icon(LucIcons.Check, "Đã thêm", tint = c.primary, modifier = Modifier.size(22.dp))
        else Text("Thêm", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------- time up

/**
 * Covers everything (also the player) when the kids' time is up. Shown by the main screen
 * while kids mode is on; draws nothing otherwise.
 */
@Composable
fun KidsTimeUp(onTimeUp: () -> Unit) {
    val sessionEnd by KidsStore.sessionEnd.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), sessionEnd) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val up = sessionEnd > 0 && now >= sessionEnd
    var askPin by remember { mutableStateOf(false) }
    var pinErrors by remember { mutableIntStateOf(0) }
    LaunchedEffect(up) {
        if (up) {
            runCatching { vn.lucbao.player.PlayerController.exo.pause() }
            onTimeUp()
        }
    }
    if (!up) return
    BackHandler { askPin = true }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFBFE9FF), KidBg)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) {
            Canvas(Modifier.size(120.dp)) {
                drawCircle(Color(0xFFF7B733))
                drawCircle(Color(0xFFFFD56B), radius = size.minDimension * 0.46f, center = Offset(size.width * 0.42f, size.height * 0.45f))
            }
            Spacer(Modifier.height(26.dp))
            Text("Hết giờ xem rồi!", color = Color(0xFF1F5F43), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Bé nghỉ ngơi một chút nhé.\nHẹn gặp lại lần sau!", color = Color(0xFF4B5B53), fontSize = 16.sp,
                lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 30.dp)
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .border(2.dp, KidLine, RoundedCornerShape(50))
                    .clickable { askPin = true }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LucIcons.Lock, null, tint = KidMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bố mẹ mở thêm giờ", color = KidMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (askPin) {
            PinOverlay(
                title = "Dành cho bố mẹ",
                subtitle = "Nhập mã PIN để cho bé xem thêm",
                errors = pinErrors,
                onDone = { pin ->
                    when {
                        System.currentTimeMillis() < KidsStore.lockedUntil -> pinErrors++
                        KidsStore.checkPin(pin) -> {
                            KidsStore.recordOk()
                            pinErrors = 0
                            askPin = false
                            KidsStore.startSession()
                        }
                        else -> {
                            KidsStore.recordFail()
                            pinErrors++
                        }
                    }
                },
                onCancel = { askPin = false; pinErrors = 0 },
            )
        }
    }
}
