package vn.lucbao.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import vn.lucbao.data.Format
import vn.lucbao.data.Library
import vn.lucbao.data.SavedList
import vn.lucbao.data.videoKey
import vn.lucbao.music.MusicDetail
import vn.lucbao.music.MusicItem
import vn.lucbao.music.MusicRepo
import vn.lucbao.music.MusicViewModel
import vn.lucbao.music.toMusicItem
import vn.lucbao.player.PlayerController
import vn.lucbao.ui.components.IconTap
import vn.lucbao.ui.components.LoadingBox
import vn.lucbao.ui.components.LucChip
import vn.lucbao.ui.components.PillButton
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

private val GENRES = listOf(
    "Nhạc trẻ", "Bolero", "Nhạc Trịnh", "Lofi chill", "Rap Việt", "Nhạc không lời",
    "Nhạc xưa", "Remix", "US-UK", "K-Pop", "Nhạc thiếu nhi", "Thiền & ngủ ngon",
)

private val FILTERS = listOf(
    MusicRepo.SONGS to "Bài hát",
    MusicRepo.VIDEOS to "Video",
    MusicRepo.ALBUMS to "Album",
    MusicRepo.PLAYLISTS to "Danh sách phát",
)

/** The "Nhạc" tab: YouTube Music search, liked songs, recent songs, albums and charts. */
@Composable
fun MusicScreen(active: Boolean, onOpenPlayer: () -> Unit) {
    val vm: MusicViewModel = viewModel()
    val detail by vm.detail.collectAsStateWithLifecycle()
    // Kept here so the search results stay where they were after closing an album.
    val homeList = rememberLazyListState()
    // Each opened page keeps its scroll position while it is in the back stack.
    val pages = rememberSaveableStateHolder()
    var shownId by remember { mutableStateOf<Long?>(null) }
    val d = detail
    LaunchedEffect(d?.id) {
        val previous = shownId
        // A page that was closed (we went back past it) forgets its state.
        if (previous != null && d != null && d.id < previous) pages.removeState(previous)
        if (previous != null && d == null) pages.removeState(previous)
        shownId = d?.id
    }
    if (d != null) {
        BackHandler(enabled = active) { vm.closeDetail() }
        pages.SaveableStateProvider(d.id) { MusicDetailView(vm, d, onOpenPlayer) }
    } else {
        MusicHome(vm, active, homeList, onOpenPlayer)
    }
}

/** What every song row needs to know, collected once per list instead of once per row. */
@Immutable
internal data class RowCtx(val currentKey: String?, val playing: Boolean, val liked: Set<String>)

@Composable
internal fun rememberRowCtx(): RowCtx {
    val ui by PlayerController.ui.collectAsStateWithLifecycle()
    val liked by Library.likedKeys.collectAsStateWithLifecycle()
    val key = ui.video?.url?.let { videoKey(it) }
    return remember(key, ui.isPlaying, liked) { RowCtx(key, ui.isPlaying, liked) }
}

// ---------------------------------------------------------------------- home + search

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicHome(vm: MusicViewModel, active: Boolean, listState: LazyListState, onOpenPlayer: () -> Unit) {
    val c = Luc.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val submitted by vm.submitted.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val liked by Library.likedSongs.collectAsStateWithLifecycle()
    val recent by Library.recentSongs.collectAsStateWithLifecycle()
    val saved by Library.savedLists.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val ctx = rememberRowCtx()

    LaunchedEffect(Unit) { vm.loadTrending() }
    BackHandler(enabled = active && (submitted != null || query.isNotEmpty())) { vm.clearSearch() }

    fun search(q: String) {
        keyboard?.hide()
        vm.submit(q)
    }

    val typing = query.isNotBlank() && query != submitted
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, results.items.size) {
        if (nearEnd && submitted != null && !typing) vm.loadMoreResults()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "head") {
            Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(LucIcons.Music, null, tint = c.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nhạc", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "không quảng cáo · nghe nền", color = c.muted, fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.card)
                        .border(1.dp, if (query.isNotEmpty()) c.primary else c.line, RoundedCornerShape(14.dp))
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LucIcons.Search, null, tint = c.muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Bài hát, nghệ sĩ, album…", color = c.muted, fontSize = 14.sp, maxLines = 1)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = vm::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text, fontSize = 14.sp),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search(query) }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) IconTap(LucIcons.Close, "Xoá", size = 20) { vm.clearSearch() }
                }
            }
        }

        if (typing) {
            items(suggestions, key = { "sg-$it" }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { search(s) }.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LucIcons.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(s, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            return@LazyColumn
        }

        if (submitted != null) {
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FILTERS.forEach { (f, label) -> LucChip(label, filter == f, { vm.setFilter(f) }) }
                }
            }
            val songs = results.items.filter { it.playable }
            itemsIndexed(results.items, key = { i, m -> "r$i-${m.url}" }) { _, m ->
                if (m.playable) {
                    SongRow(m, ctx, onClick = {
                        val list = songs.map { it.toVideo() }
                        PlayerController.playQueue(list, songs.indexOf(m), "Tìm: $submitted")
                        onOpenPlayer()
                    }, onArtist = { name, url -> vm.openArtist(name, url) })
                } else {
                    ListRow(m) { vm.openList(m) }
                }
            }
            if (results.loading) item(key = "rload") { LoadingBox() }
            results.error?.let { e -> item(key = "rerr") { Message(e) } }
            if (results.loaded && !results.loading && results.items.isEmpty() && results.error == null) {
                item(key = "rnone") { Message("Không tìm thấy kết quả nào.") }
            }
            return@LazyColumn
        }

        // ---- browse
        item(key = "genres") {
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GENRES.forEach { g -> LucChip(g, false, { search(g) }) }
            }
        }
        item(key = "shortcuts") {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Shortcut(
                    LucIcons.Heart, "Bài hát đã thích", "${liked.size} bài", Modifier.weight(1f)
                ) { vm.openLocal("liked", "Bài hát đã thích") }
                Shortcut(
                    LucIcons.History, "Nghe gần đây", "${recent.size} bài", Modifier.weight(1f)
                ) { vm.openLocal("recent", "Nghe gần đây") }
            }
        }
        if (saved.isNotEmpty()) {
            item(key = "savedTitle") { Header("Album & danh sách đã lưu") }
            item(key = "saved") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(saved, key = { "sv-${it.url}" }) { s ->
                        Column(
                            Modifier.width(128.dp).clickable { vm.openSaved(s.url, s.title, s.artist, s.thumb, s.type) }
                        ) {
                            Art(s.thumb, 128.dp)
                            Text(
                                s.title, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(s.artist, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recentTitle") {
                Header("Nghe gần đây", "Xem tất cả") { vm.openLocal("recent", "Nghe gần đây") }
            }
            itemsIndexed(recent.take(5), key = { i, v -> "rc$i-${v.url}" }) { i, v ->
                SongRow(v.toMusicItem(), ctx, onClick = {
                    PlayerController.playQueue(recent, i, "Nghe gần đây")
                    onOpenPlayer()
                }, onArtist = { name, url -> vm.openArtist(name, url) })
            }
        }
        item(key = "trendTitle") { Header("Thịnh hành") }
        val chart = trending.items
        itemsIndexed(chart.take(30), key = { i, m -> "t$i-${m.url}" }) { i, m ->
            SongRow(m, ctx, rank = i + 1, onClick = {
                PlayerController.playQueue(chart.map { it.toVideo() }, i, "Thịnh hành")
                onOpenPlayer()
            }, onArtist = { name, url -> vm.openArtist(name, url) })
        }
        if (trending.loading) item(key = "tload") { LoadingBox() }
        trending.error?.let { e ->
            item(key = "terr") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Message(e)
                    PillButton("Thử lại", LucIcons.Refresh, { vm.loadTrending(force = true) }, filled = false)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- album / playlist / artist

@Composable
private fun MusicDetailView(vm: MusicViewModel, d: MusicDetail, onOpenPlayer: () -> Unit) {
    val c = Luc.colors
    val liked by Library.likedSongs.collectAsStateWithLifecycle()
    val recent by Library.recentSongs.collectAsStateWithLifecycle()
    val saved by Library.savedLists.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val ctx = rememberRowCtx()
    var confirmClear by remember { mutableStateOf(false) }

    val local = d.kind == "liked" || d.kind == "recent"
    val items: List<MusicItem> = when (d.kind) {
        "liked" -> liked.map { it.toMusicItem() }
        "recent" -> recent.map { it.toMusicItem() }
        else -> d.list.items
    }
    val songs = items.filter { it.playable }
    val songVideos = songs.map { it.toVideo() }
    val isSaved = d.url != null && saved.any { it.url == d.url }
    val source = d.title

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd && !local) vm.loadMoreDetail() }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "top") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.22f), Color.Transparent)))
                    .statusBarsPadding()
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Box(Modifier.padding(4.dp)) { IconTap(LucIcons.Back, "Quay lại") { vm.closeDetail() } }
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val thumb = d.thumb ?: items.firstOrNull()?.thumb
                        when (d.kind) {
                            "liked" -> IconArt(LucIcons.Heart, 120.dp)
                            "recent" -> IconArt(LucIcons.History, 120.dp)
                            "artist" -> IconArt(LucIcons.Person, 120.dp)
                            else -> Art(thumb, 120.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (d.kind) {
                                    "album" -> "ALBUM"
                                    "playlist" -> "DANH SÁCH PHÁT"
                                    "artist" -> "NGHỆ SĨ"
                                    else -> "THƯ VIỆN"
                                },
                                color = c.primary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                            )
                            Text(
                                d.title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                            if (d.subtitle.isNotBlank()) {
                                Text(d.subtitle, color = c.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${songs.size} bài", color = c.muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PillButton("Phát tất cả", LucIcons.Play, {
                            if (songVideos.isNotEmpty()) {
                                PlayerController.playQueue(songVideos, 0, source, shuffle = false,
                                    moreUrl = d.url?.takeIf { !local && d.kind != "artist" }, moreToken = d.list.next)
                                onOpenPlayer()
                            }
                        })
                        PillButton("Trộn bài", LucIcons.Shuffle, {
                            if (songVideos.isNotEmpty()) {
                                PlayerController.playQueue(songVideos, -1, source, shuffle = true)
                                onOpenPlayer()
                            }
                        }, filled = false)
                        if (d.url != null && (d.kind == "album" || d.kind == "playlist")) {
                            LucChip(if (isSaved) "Đã lưu" else "Lưu", isSaved, {
                                Library.toggleSavedList(
                                    SavedList(d.url, d.title, d.subtitle, d.thumb ?: items.firstOrNull()?.thumb,
                                        d.kind, System.currentTimeMillis())
                                )
                            }, icon = LucIcons.Bookmark)
                        }
                        if (d.kind == "recent" && recent.isNotEmpty()) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Xoá", color = c.muted, fontSize = 12.5.sp,
                                modifier = Modifier.clickable { confirmClear = true }.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(items, key = { i, m -> "d$i-${m.url}" }) { _, m ->
            if (m.playable) {
                SongRow(m, ctx, onClick = {
                    PlayerController.playQueue(songVideos, songs.indexOf(m), source,
                        moreUrl = d.url?.takeIf { !local && d.kind != "artist" }, moreToken = d.list.next)
                    onOpenPlayer()
                }, onArtist = { name, url -> vm.openArtist(name, url) })
            } else {
                ListRow(m) { vm.openList(m) }
            }
        }
        if (!local && d.list.loading) item(key = "dload") { LoadingBox() }
        if (!local) d.list.error?.let { e -> item(key = "derr") { Message(e) } }
        if (items.isEmpty() && (local || (d.list.loaded && d.list.error == null))) {
            item(key = "dnone") {
                Message(
                    when (d.kind) {
                        "liked" -> "Bấm ♥ khi đang nghe một bài để lưu vào đây."
                        "recent" -> "Các bài bạn nghe trong mục Nhạc sẽ hiện ở đây."
                        "artist" -> "Chưa có bài mới từ nghệ sĩ này."
                        else -> "Không có bài hát nào."
                    }
                )
            }
        }
    }
    if (confirmClear) ConfirmClearRecent { confirmClear = false }
}

@Composable
private fun ConfirmClearRecent(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xoá danh sách nghe gần đây?") },
        text = { Text("Các bài đã thích không bị ảnh hưởng.") },
        confirmButton = {
            TextButton(onClick = {
                Library.clearRecentSongs()
                onDismiss()
            }) { Text("Xoá") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

// ---------------------------------------------------------------------- rows

@Composable
internal fun SongRow(
    m: MusicItem,
    ctx: RowCtx,
    rank: Int? = null,
    onClick: () -> Unit,
    onArtist: ((String, String) -> Unit)? = null,
) {
    val c = Luc.colors
    val key = remember(m.url) { videoKey(m.url) }
    val isCurrent = ctx.currentKey == key
    val isLiked = key in ctx.liked
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                "$rank", color = if (rank <= 3) c.primary else c.muted, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Box {
            Art(m.thumb, 52.dp, corner = 8.dp)
            if (isCurrent) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        LucIcons.Music, if (ctx.playing) "Đang phát" else "Đang tạm dừng",
                        tint = if (ctx.playing) c.primary else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title, color = if (isCurrent) c.primary else c.text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                Format.dot(m.artist, if (m.type == "video") "Video" else null, Format.duration(m.duration)),
                color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (isLiked) Icon(LucIcons.Heart, null, tint = c.primary, modifier = Modifier.size(15.dp))
        Box {
            IconTap(LucIcons.More, "Tuỳ chọn", size = 20, tint = c.muted) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val v = m.toVideo()
                MenuRow(LucIcons.PlayNext, "Phát tiếp theo") { PlayerController.playNext(v); menu = false }
                MenuRow(LucIcons.PlaylistAdd, "Thêm vào hàng chờ") { PlayerController.addToQueue(v); menu = false }
                MenuRow(LucIcons.Radio, "Phát các bài tương tự") { PlayerController.startRadio(v); menu = false }
                MenuRow(if (isLiked) LucIcons.Heart else LucIcons.HeartOutline, if (isLiked) "Bỏ thích" else "Thích") {
                    Library.toggleLikedSong(v); menu = false
                }
                val artistUrl = m.artistUrl
                if (onArtist != null && artistUrl != null && m.artist.isNotBlank()) {
                    MenuRow(LucIcons.Person, "Nghệ sĩ: ${m.artist}") { onArtist(m.artist, artistUrl); menu = false }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun ListRow(m: MusicItem, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (m.type == "artist") IconArt(LucIcons.Person, 60.dp) else Art(m.thumb, 60.dp, corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                Format.dot(
                    when (m.type) {
                        "album" -> "Album"
                        "playlist" -> "Danh sách phát"
                        else -> "Nghệ sĩ"
                    },
                    m.artist.ifBlank { null },
                    if (m.count > 0 && m.type != "artist") "${m.count} bài" else null,
                ),
                color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Luc.colors
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(c.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = c.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Header(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Luc.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = c.primary, fontSize = 12.sp, modifier = Modifier.clickable { onAction?.invoke() }.padding(4.dp))
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text, color = Luc.colors.muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)
    )
}

/** Square cover art (YouTube thumbnails are cropped to the middle). */
@Composable
internal fun Art(url: String?, size: Dp, corner: Dp = 12.dp) {
    val c = Luc.colors
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(c.card)) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun IconArt(icon: ImageVector, size: Dp) {
    val c = Luc.colors
    Box(
        Modifier
            .size(size)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(c.primary.copy(alpha = 0.55f), c.primary.copy(alpha = 0.15f)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.38f))
    }
}
