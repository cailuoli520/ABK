@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private const val MODULE_REPOSITORY_BACK_VISUAL_EXPONENT = 1.8f
private const val MODULE_REPOSITORY_BACK_SCALE_DELTA = 0.09f
private const val MODULE_REPOSITORY_BACK_SCRIM_ALPHA = 0.32f
private const val MODULE_REPOSITORY_PAGE_EXIT_DELAY_MS = 280L
private val MODULE_REPOSITORY_BACK_MAX_OFFSET = 56.dp
private val MODULE_REPOSITORY_BACK_MAX_CORNER = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleRepositoryScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onRepositoryPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val motionScheme = MaterialTheme.motionScheme
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showRepositorySettings by rememberSaveable { mutableStateOf(false) }
    var repositoryBackProgress by remember { mutableFloatStateOf(0f) }
    val animatedRepositoryBackProgress by animateFloatAsState(
        targetValue = repositoryBackProgress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "module-repository-back-progress"
    )
    val visualRepositoryBackProgress = animatedRepositoryBackProgress
        .coerceIn(0f, 1f)
        .pow(MODULE_REPOSITORY_BACK_VISUAL_EXPONENT)
    val density = LocalDensity.current
    val repositoryBackOffsetPx = with(density) { MODULE_REPOSITORY_BACK_MAX_OFFSET.toPx() }
    val repositoryBackCorner = with(density) {
        (MODULE_REPOSITORY_BACK_MAX_CORNER.toPx() * visualRepositoryBackProgress).toDp()
    }
    val mergedModules = remember(state.moduleCatalogRepositories) {
        mergeCatalogModules(state.moduleCatalogRepositories)
    }
    val filteredModules = remember(mergedModules, searchQuery) {
        mergedModules.filter { it.matchesQuery(searchQuery) }
    }
    val selectedModules = remember(state.buildConfig.customExternalModules) {
        state.buildConfig.customExternalModules
            .map { it.url.trim().lowercase() to CustomExternalModuleStage.normalize(it.stage) }
            .toSet()
    }

    fun openRepositorySettings() {
        repositoryBackProgress = 0f
        onRepositoryPageVisibleChange(true)
        showRepositorySettings = true
    }

    fun closeRepositorySettings() {
        showRepositorySettings = false
    }

    LaunchedEffect(showRepositorySettings) {
        if (showRepositorySettings) {
            onRepositoryPageVisibleChange(true)
        } else {
            delay(MODULE_REPOSITORY_PAGE_EXIT_DELAY_MS)
            repositoryBackProgress = 0f
            onRepositoryPageVisibleChange(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onRepositoryPageVisibleChange(false) }
    }

    PredictiveBackHandler(enabled = showRepositorySettings && state.predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                repositoryBackProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            closeRepositorySettings()
        } catch (_: CancellationException) {
            repositoryBackProgress = 0f
        }
    }

    BackHandler(enabled = showRepositorySettings && !state.predictiveBackEnabled) {
        closeRepositorySettings()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)

        Scaffold(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
            topBar = {
                ExpressiveTopBar(
                    title = "模块仓库",
                    actions = {
                        IconButton(onClick = ::openRepositorySettings) {
                            Icon(Icons.Default.Storage, contentDescription = "配置模块仓库")
                        }
                    }
                )
            }
        ) { padding ->
            ModuleRepositoryListContent(
                padding = padding,
                modules = filteredModules,
                totalModules = mergedModules.size,
                repositories = state.moduleCatalogRepositories,
                refreshing = state.refreshingModuleCatalogRepositoryIds.isNotEmpty(),
                selectedModules = selectedModules,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenRepositorySettings = ::openRepositorySettings,
                onAddModule = { module, stage ->
                    vm.addModuleFromCatalog(module, stage)
                    Toast.makeText(context, "模块已加入构建配置", Toast.LENGTH_SHORT).show()
                },
                onOpenModule = { module ->
                    val url = module.homepage.ifBlank { module.repoUrl }
                    runCatching { uriHandler.openUri(url) }
                        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
                },
                bottomPadding = outerPadding.calculateBottomPadding()
            )
        }

        AnimatedVisibility(
            visible = showRepositorySettings,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = MODULE_REPOSITORY_BACK_SCRIM_ALPHA * visualRepositoryBackProgress))
            )
        }

        AnimatedVisibility(
            visible = showRepositorySettings,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> width / 4 },
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width },
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = repositoryBackOffsetPx * visualRepositoryBackProgress
                        scaleX = 1f - MODULE_REPOSITORY_BACK_SCALE_DELTA * visualRepositoryBackProgress
                        scaleY = 1f - MODULE_REPOSITORY_BACK_SCALE_DELTA * visualRepositoryBackProgress
                        alpha = 1f - 0.06f * visualRepositoryBackProgress
                        shape = RoundedCornerShape(repositoryBackCorner)
                        clip = visualRepositoryBackProgress > 0.01f
                    }
            ) {
                ModuleRepositoryPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = "中央仓库",
                            navigationIcon = {
                                IconButton(onClick = ::closeRepositorySettings) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回模块仓库")
                                }
                            }
                        )
                    }
                ) { padding ->
                    ModuleRepositorySettingsPage(
                        padding = padding,
                        repositories = state.moduleCatalogRepositories,
                        refreshingRepositoryIds = state.refreshingModuleCatalogRepositoryIds,
                        onAddRepository = { vm.addModuleCatalogRepository(it) },
                        onRefreshAll = { vm.refreshAllModuleCatalogRepositories() },
                        onRefreshRepository = { vm.refreshModuleCatalogRepository(it) },
                        onDeleteRepository = { vm.deleteModuleCatalogRepository(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleRepositoryListContent(
    padding: PaddingValues,
    modules: List<MergedCatalogModule>,
    totalModules: Int,
    repositories: List<ModuleCatalogRepository>,
    refreshing: Boolean,
    selectedModules: Set<Pair<String, String>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenRepositorySettings: () -> Unit,
    onAddModule: (ModuleCatalogItem, String) -> Unit,
    onOpenModule: (ModuleCatalogItem) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("搜索模块") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(22.dp)
        )

        if (refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (modules.isEmpty()) {
            EmptyModuleRepositoryState(
                totalModules = totalModules,
                repositoryCount = repositories.size,
                hasQuery = searchQuery.isNotBlank(),
                onOpenRepositorySettings = onOpenRepositorySettings
            )
        } else {
            modules.forEach { merged ->
                val module = merged.module
                val stage = CustomExternalModuleStage.normalize(module.defaultStage)
                val alreadyAdded = module.repoUrl.trim().lowercase() to stage in selectedModules
                ModuleRepositoryListItem(
                    module = module,
                    sources = merged.sources,
                    alreadyAdded = alreadyAdded,
                    onOpen = { onOpenModule(module) },
                    onAdd = { onAddModule(module, stage) }
                )
            }
        }

        Spacer(Modifier.height(bottomPadding + 24.dp))
    }
}

@Composable
private fun EmptyModuleRepositoryState(
    totalModules: Int,
    repositoryCount: Int,
    hasQuery: Boolean,
    onOpenRepositorySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(38.dp)
        )
        Text(
            text = when {
                hasQuery -> "没有匹配的模块"
                repositoryCount == 0 -> "还没有中央仓库"
                totalModules == 0 -> "刷新中央仓库后会显示模块"
                else -> "没有可显示的模块"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onOpenRepositorySettings) {
            Icon(Icons.Default.Storage, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("管理中央仓库")
        }
    }
}

@Composable
private fun ModuleRepositoryListItem(
    module: ModuleCatalogItem,
    sources: List<String>,
    alreadyAdded: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = module.displayName(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = module.metaLine()
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (sources.size > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Source,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = sources.size.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModuleTagChip(label = module.repoUrl.repoSlug())
            module.supportedStages.take(2).forEach { stage ->
                ModuleTagChip(label = CustomExternalModuleStage.normalize(stage), secondary = true)
            }
            if (alreadyAdded) {
                ModuleTagChip(label = "已加入", secondary = true)
            }
            if (sources.isNotEmpty()) {
                ModuleTagChip(label = "来源 ${sources.joinToString(", ")}", secondary = true)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onOpen,
                modifier = Modifier.width(76.dp).height(56.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "打开模块仓库")
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalIconButton(
                onClick = onAdd,
                enabled = !alreadyAdded,
                modifier = Modifier.width(76.dp).height(56.dp)
            ) {
                Icon(
                    imageVector = if (alreadyAdded) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = if (alreadyAdded) "已加入" else "加入构建配置"
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        )
    }
}

@Composable
private fun ModuleTagChip(
    label: String,
    secondary: Boolean = false
) {
    val color = if (secondary) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (secondary) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = if (secondary) 0.82f else 0.9f),
        contentColor = contentColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ModuleRepositorySettingsPage(
    padding: PaddingValues,
    repositories: List<ModuleCatalogRepository>,
    refreshingRepositoryIds: Set<String>,
    onAddRepository: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshRepository: (String) -> Unit,
    onDeleteRepository: (String) -> Unit
) {
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveSectionCard(
            title = "中央仓库",
            subtitle = "添加包含 abk-modules.json 的索引仓库。",
            icon = Icons.Default.Storage
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
                        onAddRepository(repositoryUrl)
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
                    onClick = onRefreshAll,
                    enabled = repositories.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新全部")
                }
            }
        }

        if (repositories.isEmpty()) {
            ExpressiveSectionCard(
                title = "暂无中央仓库",
                subtitle = "添加中央仓库后，模块会在主页面合并展示。",
                icon = Icons.Default.LibraryBooks
            ) {
                Text(
                    text = "删除中央仓库不会移除已经加入构建配置的单模块仓库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            repositories.forEach { repository ->
                ModuleCatalogRepositoryCard(
                    repository = repository,
                    refreshing = repository.id in refreshingRepositoryIds,
                    onRefresh = { onRefreshRepository(repository.id) },
                    onDelete = { onDeleteRepository(repository.id) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
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
        icon = Icons.Default.Storage
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

@Composable
private fun ModuleRepositoryPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasBackground = backgroundImageEnabled && !backgroundUri.isNullOrBlank()
    val scrimColor = if (colorScheme.surface.luminance() > 0.5f) {
        colorScheme.surface.copy(alpha = 0.28f)
    } else {
        Color.Black.copy(alpha = 0.38f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        if (hasBackground) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
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

private fun MergedCatalogModule.matchesQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    val catalogItem = module
    return listOf(
        catalogItem.name,
        catalogItem.version,
        catalogItem.description,
        catalogItem.repoUrl,
        catalogItem.author,
        catalogItem.homepage,
        sources.joinToString(" ")
    ).any { it.contains(cleanQuery, ignoreCase = true) }
}

private fun ModuleCatalogItem.displayName(): String =
    name.ifBlank { repoUrl.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git") }

private fun ModuleCatalogItem.metaLine(): String =
    listOfNotNull(
        version.takeIf { it.isNotBlank() }?.let { "版本: $it" },
        author.takeIf { it.isNotBlank() }?.let { "作者: $it" }
    ).joinToString("\n")

private fun String.repoSlug(): String =
    trim()
        .trimEnd('/')
        .removeSuffix(".git")
        .substringAfter("github.com/")
        .ifBlank { trim().trimEnd('/').substringAfterLast('/') }
