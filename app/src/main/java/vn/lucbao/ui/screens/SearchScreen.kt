package vn.lucbao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lucbao.data.Video
import vn.lucbao.ui.AppViewModel
import vn.lucbao.ui.components.EmptyState
import vn.lucbao.ui.components.ErrorBox
import vn.lucbao.ui.components.IconTap
import vn.lucbao.ui.components.LoadingBox
import vn.lucbao.ui.components.VideoRow
import vn.lucbao.ui.theme.BeVietnam
import vn.lucbao.ui.theme.Luc
import vn.lucbao.ui.theme.LucIcons

@Composable
fun SearchScreen(vm: AppViewModel, focusSignal: Int, onPlay: (Video) -> Unit, onBack: () -> Unit) {
    val c = Luc.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val submitted by vm.submitted.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(focusSignal) {
        if (focusSignal > 0 || submitted == null) runCatching { focus.requestFocus() }
    }

    fun go(q: String) {
        keyboard?.hide()
        vm.submit(q)?.let(onPlay)
    }

    val typing = submitted == null || query != submitted
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, results.items.size) { if (nearEnd && !typing) vm.loadMoreResults() }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "field") {
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTap(LucIcons.Back, "Quay lại", onClick = onBack)
                Spacer(Modifier.width(4.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.card)
                        .border(1.dp, c.primary, RoundedCornerShape(14.dp))
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Tìm hoặc dán link YouTube…", color = c.muted, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = vm::onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontFamily = BeVietnam),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { go(query) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconTap(LucIcons.Close, "Xoá", size = 20) {
                            vm.clearSearch()
                            runCatching { focus.requestFocus() }
                        }
                    }
                }
            }
        }

        if (typing) {
            val list = if (query.isBlank()) recent else suggestions
            if (query.isBlank() && recent.isNotEmpty()) {
                item(key = "recentTitle") {
                    Text(
                        "TÌM GẦN ĐÂY", color = c.muted, fontSize = 11.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
            }
            items(list, key = { "s-$it" }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { go(s) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (query.isBlank()) LucIcons.History else LucIcons.Search, null,
                        tint = c.muted, modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        highlight(s, query, c.primary), color = c.text, fontSize = 14.sp,
                        modifier = Modifier.weight(1f), maxLines = 1
                    )
                    if (query.isBlank()) {
                        IconTap(LucIcons.Close, "Xoá khỏi lịch sử", size = 16, tint = c.muted) {
                            vm.forgetRecent(s)
                        }
                    } else {
                        IconTap(LucIcons.NorthWest, "Điền", size = 16, tint = c.muted) {
                            vm.onQueryChange("$s ")
                        }
                    }
                }
            }
            if (query.isBlank() && recent.isEmpty()) {
                item(key = "hint") {
                    EmptyState(
                        "Tìm gì cũng được",
                        "Gõ tên bài hát, video, kênh… hoặc dán đường link YouTube để xem ngay — không quảng cáo."
                    )
                }
            }
        } else {
            results.suggestion?.let { s ->
                item(key = "didyoumean") {
                    Text(
                        buildAnnotatedString {
                            append("Có phải bạn muốn tìm: ")
                            withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.SemiBold)) { append(s) }
                        },
                        color = c.muted, fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { go(s) }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
            when {
                results.items.isEmpty() && results.loading -> item(key = "load") { LoadingBox() }
                results.items.isEmpty() && results.error != null -> item(key = "err") {
                    ErrorBox(results.error, onRetry = { go(submitted ?: query) })
                }
                results.items.isEmpty() -> item(key = "none") {
                    EmptyState("Không tìm thấy", "Thử từ khoá khác nhé.")
                }
                else -> {
                    itemsIndexed(results.items, key = { i, v -> "$i-${v.url}" }) { _, v ->
                        VideoRow(v, onClick = { onPlay(v) })
                    }
                    if (results.loading) item(key = "more") { LoadingBox() }
                }
            }
        }
        item(key = "pad") { Spacer(Modifier.height(12.dp)) }
    }
}

private fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        val q = query.trim()
        val i = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
        if (i < 0) {
            append(text)
        } else {
            append(text.substring(0, i))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(i, i + q.length))
            }
            append(text.substring(i + q.length))
        }
    }
