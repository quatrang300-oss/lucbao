package vn.lucbao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lucbao.data.Library
import vn.lucbao.data.Video
import vn.lucbao.ui.AppViewModel
import vn.lucbao.ui.FeedState
import vn.lucbao.ui.components.EmptyState
import vn.lucbao.ui.components.ErrorBox
import vn.lucbao.ui.components.HeroCard
import vn.lucbao.ui.components.LoadingBox
import vn.lucbao.ui.components.LucChip
import vn.lucbao.ui.components.SectionTitle
import vn.lucbao.ui.components.VideoCard
import vn.lucbao.ui.theme.BrandMark
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val kiosks by vm.kiosks.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val feeds by vm.feeds.collectAsStateWithLifecycle()
    val feed = feeds[selected] ?: FeedState(loading = true)
    val channels by Library.channels.collectAsStateWithLifecycle()
    val chips = listOf(AppViewModel.FOR_YOU to "Dành cho bạn") +
        (if (channels.isNotEmpty()) listOf(AppViewModel.FOLLOWING to "Đang theo dõi") else emptyList()) +
        kiosks.map { it.id to it.name }
    val title = chips.firstOrNull { it.first == selected }?.second ?: ""
    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed.items.size) { if (nearEnd) vm.loadMore() }
    LaunchedEffect(selected) { listState.scrollToItem(0) }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "bar") {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandMark(24.dp)
                    Spacer(Modifier.weight(1f))
                    // Settings moved here from the bottom bar (which now has Shorts).
                    vn.lucbao.ui.components.IconTap(LucIcons.Settings, "Cài đặt", size = 24, tint = c.text) {
                        vm.go(vn.lucbao.ui.Tab.SETTINGS)
                    }
                }
            }
            item(key = "search") {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface2)
                        .border(1.dp, c.line, RoundedCornerShape(14.dp))
                        .clickable { vm.openSearch() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LucIcons.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Tìm kiếm hoặc dán link youtube", color = c.muted, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item(key = "chips") {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    items(chips, key = { it.first }) { (id, name) ->
                        LucChip(name, id == selected, onClick = { vm.select(id) })
                    }
                }
            }

            val items = feed.items
            when {
                items.isEmpty() && feed.error != null -> item(key = "err") {
                    ErrorBox(feed.error, onRetry = { vm.refresh() })
                }
                items.isEmpty() && (feed.loading || !feed.loaded) -> item(key = "loading") { LoadingBox() }
                items.isEmpty() -> item(key = "empty") {
                    if (selected == AppViewModel.FOLLOWING) {
                        EmptyState("Chưa có video mới", "Các kênh bạn theo dõi chưa đăng video nào trong 2 tháng qua.")
                    } else {
                        EmptyState("Chưa có gì ở đây", "Kéo xuống để tải lại nhé.")
                    }
                }
                else -> {
                    item(key = "hero") { HeroCard(items[0]) { onPlay(items[0]) } }
                    item(key = "title") { SectionTitle(title) }
                    itemsIndexed(
                        items.drop(1),
                        key = { i, v -> "$i-${v.url}" }
                    ) { _, v -> VideoCard(v, onClick = { onPlay(v) }) }
                    if (feed.loading) item(key = "more") { LoadingBox() }
                    else if (feed.error != null) item(key = "moreErr") {
                        ErrorBox(feed.error, onRetry = { vm.loadMore() })
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(12.dp)) }
        }
    }
}
