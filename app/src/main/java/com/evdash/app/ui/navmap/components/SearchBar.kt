package com.evdash.app.ui.navmap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evdash.app.ui.navmap.search.PoiResult

/**
 * P3 占位 UI:顶部搜索栏 + 结果下拉。
 *
 * 实际接入后调用 [PoiSearchRepository.search] 异步更新 `results`。
 */
@Composable
fun SearchBar(
    results: List<PoiResult>,
    onQueryChange: (String) -> Unit,
    onPick: (PoiResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
    ) {
        TextField(
            value = text,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            placeholder = { Text("搜索地点", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = ""; onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清除")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (results.isNotEmpty()) {
            Column(modifier = Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp))) {
                results.take(5).forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Text(
                                text = r.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Black,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onPick(r); text = r.name }) {
                            Icon(Icons.Default.Search, contentDescription = "导航至")
                        }
                    }
                }
            }
        }
    }
}
