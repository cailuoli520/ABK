@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleRepositoryScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    val mergedModules = remember(state.moduleCatalogRepositories) {
        mergeCatalogModules(state.moduleCatalogRepositories)
    }
    val selectedModules = remember(state.buildConfig.customExternalModules) {
        state.buildConfig.customExternalModules
            .map { it.url.trim().lowercase() to CustomExternalModuleStage.normalize(it.stage) }
            .toSet()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(title = "模块仓库")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveHeroCard(
                title = "模块仓库",
                subtitle = "中央仓库提供模块索引，构建时仍使用单模块仓库执行 setup.sh。",
                icon = Icons.Default.LibraryBooks,
                badge = {
                    ExpressiveStatusChip(
                        label = "${state.moduleCatalogRepositories.size} 个中央仓库",
                        icon = Icons.Default.Source,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveStatusChip(
                        label = "${mergedModules.size} 个合并模块",
                        icon = Icons.Default.Extension,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            ExpressiveSectionCard(
                title = "中央仓库",
                subtitle = "添加包含 abk-modules.json 的索引仓库。",
                icon = Icons.Default.Source
            ) {
                OutlinedTextField(
                    value = repositoryUrl,
                    onValueChange = { repositoryUrl = it },
                    label = { Text("中央仓库链接") },
                    placeholder = { Text("https://github.com/user/abk-module-catalog") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.addModuleCatalogRepository(repositoryUrl)
                            repositoryUrl = ""
                        },
                        enabled = repositoryUrl.isNotBlank(),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加")
                    }
                    OutlinedButton(
                        onClick = { vm.refreshAllModuleCatalogRepositories() },
                        enabled = state.moduleCatalogRepositories.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("刷新全部")
                    }
                }
            }

            if (state.moduleCatalogRepositories.isEmpty()) {
                ExpressiveSectionCard(
                    title = "暂无中央仓库",
                    subtitle = "添加中央仓库后，模块会在下方合并展示。",
                    icon = Icons.Default.LibraryBooks
                ) {
                    Text(
                        text = "删除中央仓库不会移除已经加入构建配置的单模块仓库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                state.moduleCatalogRepositories.forEach { repository ->
                    ModuleCatalogRepositoryCard(
                        repository = repository,
                        refreshing = repository.id in state.refreshingModuleCatalogRepositoryIds,
                        onRefresh = { vm.refreshModuleCatalogRepository(repository.id) },
                        onDelete = { vm.deleteModuleCatalogRepository(repository.id) }
                    )
                }
            }

            ExpressiveSectionCard(
                title = "合并模块",
                subtitle = "来自所有中央仓库，按单模块仓库链接去重。",
                icon = Icons.Default.Extension
            ) {
                if (mergedModules.isEmpty()) {
                    Text(
                        text = "刷新中央仓库后会显示可添加模块。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    mergedModules.forEach { merged ->
                        val module = merged.module
                        val stage = CustomExternalModuleStage.normalize(module.defaultStage)
                        val alreadyAdded = module.repoUrl.trim().lowercase() to stage in selectedModules
                        ExpressiveListItem(
                            title = module.displayName(),
                            subtitle = module.catalogSubtitle(merged.sources),
                            leadingIcon = if (alreadyAdded) Icons.Default.CheckCircle else Icons.Default.Extension,
                            trailingContent = {
                                TextButton(
                                    onClick = {
                                        vm.addModuleFromCatalog(module, stage)
                                        Toast.makeText(context, "模块已加入构建配置", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !alreadyAdded
                                ) {
                                    Text(if (alreadyAdded) "已加入" else "加入")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(outerPadding.calculateBottomPadding() + 24.dp))
        }
    }
}

@Composable
private fun ModuleCatalogRepositoryCard(
    repository: ModuleCatalogRepository,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(
        title = repository.name.ifBlank { repository.url },
        subtitle = repository.url,
        icon = Icons.Default.Source
    ) {
        if (refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveStatusChip(
                label = "${repository.modules.size} 个模块",
                icon = Icons.Default.Extension,
                color = MaterialTheme.colorScheme.primary
            )
            if (repository.skippedCount > 0) {
                ExpressiveStatusChip(
                    label = "跳过 ${repository.skippedCount}",
                    icon = Icons.Default.Link,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        repository.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        val indexUrl = repository.indexJsonUrl.ifBlank { repository.url }
        Text(
            text = indexUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("刷新")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("删除")
            }
        }
    }
}

private data class MergedCatalogModule(
    val module: ModuleCatalogItem,
    val sources: List<String>
)

private fun mergeCatalogModules(repositories: List<ModuleCatalogRepository>): List<MergedCatalogModule> =
    repositories
        .flatMap { repository ->
            repository.modules.map { module -> repository.name.ifBlank { repository.url } to module }
        }
        .groupBy { (_, module) -> module.repoUrl.trim().lowercase() }
        .values
        .map { entries ->
            val module = entries.first().second
            MergedCatalogModule(
                module = module,
                sources = entries.map { it.first }.distinct()
            )
        }
        .sortedBy { it.module.displayName().lowercase() }

private fun ModuleCatalogItem.displayName(): String =
    name.ifBlank { repoUrl.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git") }

private fun ModuleCatalogItem.catalogSubtitle(sources: List<String>): String = buildString {
    val meta = listOfNotNull(
        version.takeIf { it.isNotBlank() }?.let { "v$it" },
        author.takeIf { it.isNotBlank() },
        "默认 ${CustomExternalModuleStage.normalize(defaultStage)}"
    ).joinToString(" · ")
    if (meta.isNotBlank()) appendLine(meta)
    if (description.isNotBlank()) appendLine(description)
    appendLine(repoUrl)
    append("来源: ${sources.joinToString(", ")}")
}
