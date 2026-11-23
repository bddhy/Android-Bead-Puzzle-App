package com.bddhy.pindou.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bddhy.pindou.data.MardPalette
import com.bddhy.pindou.logic.BeadEngine
import com.bddhy.pindou.logic.BeadGrid
import com.bddhy.pindou.logic.Exporter
import com.bddhy.pindou.logic.GridMath
import com.bddhy.pindou.logic.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_COLS = 29
private const val MIN_COLS = 6
private const val MAX_COLS = 120

/** Gamma 用整数「步」来表示，避免浮点累加误差：50 = 0.50，200 = 2.00。 */
private const val GAMMA_MIN_STEP = 50
private const val GAMMA_MAX_STEP = 200

private val GUIDE_COLOR = Color(0xFFE53935)

private enum class EditTarget { Cols, Total }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelArtScreen(sharedImage: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = remember(context) { MardPalette.load(context) }
    val snackbarState = remember { SnackbarHostState() }

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var cols by rememberSaveable { mutableIntStateOf(DEFAULT_COLS) }
    var gamma by rememberSaveable { mutableFloatStateOf(1f) }
    var grid by remember { mutableStateOf<BeadGrid?>(null) }
    var generating by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showCodes by rememberSaveable { mutableStateOf(false) }
    var showGuides by rememberSaveable { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    val notify: (String) -> Unit = { message ->
        scope.launch { snackbarState.showSnackbar(message) }
    }

    // 没有图片时按 1:1 占位，保证两个滑杆的范围始终有效
    val imageWidth = source?.width ?: 120
    val imageHeight = source?.height ?: 120
    val maxCols = minOf(MAX_COLS, imageWidth).coerceAtLeast(MIN_COLS)

    fun totalOf(c: Int): Int = GridMath.totalFor(imageWidth, imageHeight, c)

    val currentGrid = grid
    // 直接用当前列数推算，不能取已生成的图纸：重新生成有防抖，
    // 拖动过程中图纸还是旧的，滑杆值不变就会被弹回原位（表现为「拖不动」）
    val currentTotal = totalOf(cols)
    val minTotal = totalOf(MIN_COLS)
    val maxTotal = totalOf(maxCols).coerceAtLeast(minTotal + 1)

    val stepGamma: (Int) -> Unit = { delta ->
        val next = ((gamma * 100).roundToInt() + delta).coerceIn(GAMMA_MIN_STEP, GAMMA_MAX_STEP)
        gamma = next / 100f
    }

    val loadImage: (Uri) -> Unit = { uri ->
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
            if (bitmap == null) {
                notify("图片读取失败，换一张试试(✘_✘)")
            } else {
                grid = null
                source = bitmap
                if (cols > bitmap.width) cols = bitmap.width.coerceAtLeast(MIN_COLS)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(loadImage)
    }

    // 从相册「分享到」进来时直接加载
    LaunchedEffect(sharedImage) {
        sharedImage?.let(loadImage)
    }

    LaunchedEffect(source, cols, gamma) {
        val bitmap = source ?: return@LaunchedEffect
        generating = true
        delay(120)
        val result = withContext(Dispatchers.Default) {
            runCatching { BeadEngine.generate(bitmap, cols, gamma, palette) }.getOrNull()
        }
        grid = result
        generating = false
        if (result == null) notify("生成失败，试试调小列数(✘_✘)")
    }

    val doSave: () -> Unit = {
        val current = grid
        if (current != null) {
            scope.launch {
                saving = true
                val uri = withContext(Dispatchers.IO) {
                    runCatching { Exporter.save(context, current, showCodes, showGuides) }.getOrNull()
                }
                saving = false
                notify(if (uri != null) "已保存到相册 Pictures/PixelArt (｡>∀<｡)" else "保存失败(✘_✘)")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) doSave() else notify("没有存储权限，无法保存(✘_✘)")
    }

    val onSaveClick: () -> Unit = {
        if (grid != null) {
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                doSave()
            }
        }
    }

    // 一键还原：回到刚打开 App 时的参数
    val resetAll: () -> Unit = {
        cols = DEFAULT_COLS
        gamma = 1f
        showCodes = false
        showGuides = false
        notify("已还原为初始设置")
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("拼豆生成器", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = resetAll) { Text("还原") }
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Text(if (source == null) "选图" else "换图")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        bottomBar = {
            ControlPanel(
                cols = cols,
                maxCols = maxCols,
                total = currentTotal,
                onColsChange = { cols = it },
                onEditCols = { editing = EditTarget.Cols },
                onEditTotal = { editing = EditTarget.Total },
                showCodes = showCodes,
                onToggleCodes = { showCodes = !showCodes },
                showGuides = showGuides,
                onToggleGuides = { showGuides = !showGuides },
                enabled = currentGrid != null,
                saving = saving,
                onStats = { showStats = true },
                onSave = onSaveClick,
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PreviewArea(
                source = source,
                grid = currentGrid,
                showCodes = showCodes,
                showGuides = showGuides,
                generating = generating,
                gamma = gamma,
                onGammaStep = stepGamma,
                onPick = { picker.launch("image/*") },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showStats && currentGrid != null) {
        ModalBottomSheet(onDismissRequest = { showStats = false }) {
            StatsContent(grid = currentGrid, onClose = { showStats = false })
        }
    }

    val target = editing
    if (target != null) {
        val editingCols = target == EditTarget.Cols
        NumberInputDialog(
            title = if (editingCols) "输入列数" else "输入拼豆总数",
            hint = if (editingCols) "列数（$MIN_COLS – $maxCols）" else "总块数（$minTotal – $maxTotal）",
            initial = if (editingCols) cols else currentTotal,
            onDismiss = { editing = null },
            onConfirm = { value ->
                cols = if (editingCols) {
                    value.coerceIn(MIN_COLS, maxCols)
                } else {
                    GridMath.colsForTotal(imageWidth, imageHeight, value, MIN_COLS, maxCols)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun PreviewArea(
    source: Bitmap?,
    grid: BeadGrid?,
    showCodes: Boolean,
    showGuides: Boolean,
    generating: Boolean,
    gamma: Float,
    onGammaStep: (Int) -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .clipToBounds(),
    ) {
        when {
            grid != null -> ZoomableGrid(grid = grid, showCodes = showCodes, showGuides = showGuides)
            source == null -> EmptyState(onPick = onPick, modifier = Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        // 左上角：图纸信息 + Gamma 加减
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (grid != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = CircleShape,
                    tonalElevation = 2.dp,
                ) {
                    Text(
                        text = "${grid.cols} × ${grid.rows} · ${grid.total} 块 · ${grid.colors.size} 色",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            GammaStepper(gamma = gamma, enabled = grid != null, onStep = onGammaStep)
        }

        if (generating && grid != null) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

/** 小号 Gamma 控件：只保留 − / +，一次 0.01，长按可以连续加减。 */
@Composable
private fun GammaStepper(gamma: Float, enabled: Boolean, onStep: (Int) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                symbol = "−",
                enabled = enabled && gamma > GAMMA_MIN_STEP / 100f,
                onStep = { onStep(-1) },
            )
            Text(
                text = String.format(Locale.US, "γ %.2f", gamma),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            StepButton(
                symbol = "+",
                enabled = enabled && gamma < GAMMA_MAX_STEP / 100f,
                onStep = { onStep(1) },
            )
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onStep: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        onStep()
        delay(400)
        while (true) {
            onStep()
            delay(70)
        }
    }

    val gesture = if (enabled) {
        Modifier.pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(30.dp)
            .clipToBounds()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                shape = CircleShape,
            )
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(168.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⬛", fontSize = 64.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("选一张图片开始拼豆", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(22.dp))
        Button(onClick = onPick) { Text("从相册选择") }
    }
}

@Composable
private fun ZoomableGrid(grid: BeadGrid, showCodes: Boolean, showGuides: Boolean) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 280)
    val image: ImageBitmap = remember(grid) { grid.toImageBitmap() }

    var scale by remember(grid.cols) { mutableFloatStateOf(1f) }
    var offset by remember(grid.cols) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val areaWidth = with(density) { maxWidth.toPx() }
        val areaHeight = with(density) { maxHeight.toPx() }
        val cellPx = if (grid.cols > 0 && grid.rows > 0) {
            minOf(areaWidth * 0.94f / grid.cols, areaHeight * 0.94f / grid.rows).coerceAtLeast(1f)
        } else {
            1f
        }
        val contentWidth = cellPx * grid.cols
        val contentHeight = cellPx * grid.rows

        val labelLayouts: Map<String, TextLayoutResult> = if (showCodes) {
            remember(grid, cellPx) {
                val fontSize = with(density) { (cellPx * 0.40f).toSp() }
                grid.colors.values.associate { color ->
                    color.code to textMeasurer.measure(
                        text = color.code,
                        style = TextStyle(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (color.luminance > 128f) Color.Black else Color.White,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        } else {
            emptyMap()
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(grid.cols) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 16f)
                        scale = next
                        offset = if (next <= 1.001f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(grid.cols) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                    )
                },
        ) {
            val centerX = size.width / 2f + offset.x
            val centerY = size.height / 2f + offset.y
            withTransform({
                translate(centerX, centerY)
                scale(scale, scale, pivot = Offset.Zero)
                translate(-contentWidth / 2f, -contentHeight / 2f)
            }) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(grid.cols, grid.rows),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(contentWidth.roundToInt(), contentHeight.roundToInt()),
                    filterQuality = FilterQuality.None,
                )

                val onScreenCell = cellPx * scale
                if (grid.total <= 4000 && onScreenCell >= 12f) {
                    val lineColor = Color.Black.copy(alpha = 0.12f)
                    val stroke = 1f / scale
                    for (col in 0..grid.cols) {
                        val x = col * cellPx
                        drawLine(lineColor, Offset(x, 0f), Offset(x, contentHeight), strokeWidth = stroke)
                    }
                    for (row in 0..grid.rows) {
                        val y = row * cellPx
                        drawLine(lineColor, Offset(0f, y), Offset(contentWidth, y), strokeWidth = stroke)
                    }
                }

                // 每 10 格一条红色虚线（10×10 拼豆板分界）
                if (showGuides) {
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(14f / scale, 10f / scale),
                        0f,
                    )
                    val stroke = 2.5f / scale
                    for (col in 10 until grid.cols step 10) {
                        val x = col * cellPx
                        drawLine(
                            color = GUIDE_COLOR,
                            start = Offset(x, 0f),
                            end = Offset(x, contentHeight),
                            strokeWidth = stroke,
                            pathEffect = dash,
                        )
                    }
                    for (row in 10 until grid.rows step 10) {
                        val y = row * cellPx
                        drawLine(
                            color = GUIDE_COLOR,
                            start = Offset(0f, y),
                            end = Offset(contentWidth, y),
                            strokeWidth = stroke,
                            pathEffect = dash,
                        )
                    }
                }

                if (showCodes && onScreenCell >= 24f) {
                    for (row in 0 until grid.rows) {
                        for (col in 0 until grid.cols) {
                            val layout = labelLayouts[grid.codes[row * grid.cols + col]] ?: continue
                            val cx = (col + 0.5f) * cellPx
                            val cy = (row + 0.5f) * cellPx
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(
                                    cx - layout.size.width / 2f,
                                    cy - layout.size.height / 2f,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (scale > 1.05f) {
            TextButton(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            ) {
                Text("复位")
            }
        }
    }
}

private fun BeadGrid.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(argbs, 0, cols, 0, 0, cols, rows)
    return bitmap.asImageBitmap()
}

@Composable
private fun ControlPanel(
    cols: Int,
    maxCols: Int,
    total: Int,
    onColsChange: (Int) -> Unit,
    onEditCols: () -> Unit,
    onEditTotal: () -> Unit,
    showCodes: Boolean,
    onToggleCodes: () -> Unit,
    showGuides: Boolean,
    onToggleGuides: () -> Unit,
    enabled: Boolean,
    saving: Boolean,
    onStats: () -> Unit,
    onSave: () -> Unit,
) {
    val sliderStart = MIN_COLS.toFloat()
    val sliderEnd = maxCols.toFloat().coerceAtLeast(sliderStart + 1f)

    Surface(tonalElevation = 3.dp, shadowElevation = 10.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TapBubble(text = "列数", enabled = enabled, onClick = onEditCols)
                    TapBubble(text = "拼豆总数", enabled = enabled, onClick = onEditTotal)
                }
                Text(
                    text = "$cols 列 · $total 块",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // 两个数字都由列数决定，所以只保留一根滑杆
            Slider(
                value = cols.toFloat().coerceIn(sliderStart, sliderEnd),
                onValueChange = { onColsChange(it.roundToInt().coerceIn(MIN_COLS, maxCols)) },
                valueRange = sliderStart..sliderEnd,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleButton(
                    label = "色号",
                    active = showCodes,
                    enabled = enabled,
                    onClick = onToggleCodes,
                )
                ToggleButton(
                    label = "定位线",
                    active = showGuides,
                    enabled = enabled,
                    onClick = onToggleGuides,
                )
                OutlinedButton(
                    onClick = onStats,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                ) {
                    Text("统计", fontSize = 13.sp, maxLines = 1)
                }
                Button(
                    onClick = onSave,
                    enabled = enabled && !saving,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                ) {
                    Text(if (saving) "保存中" else "保存", fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ToggleButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val padding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)
    if (active) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = padding,
        ) {
            Text(label, fontSize = 13.sp, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = padding,
        ) {
            Text(label, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/**
 * 常显的小椭圆标签，点一下可以手动输入。
 */
@Composable
private fun TapBubble(text: String, enabled: Boolean, onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.85f else 0.35f)
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = textColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NumberInputDialog(
    title: String,
    hint: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember {
        val start = initial.toString()
        mutableStateOf(
            // 打开就全选，直接输入即可覆盖，不用先手动删
            TextFieldValue(text = start, selection = TextRange(0, start.length)),
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // 聚焦有时会把光标收到末尾，这里再确认一次全选
        value = value.copy(selection = TextRange(0, value.text.length))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    val digits = input.text.filter { it.isDigit() }.take(6)
                    value = if (digits == input.text) {
                        input
                    } else {
                        TextFieldValue(text = digits, selection = TextRange(digits.length))
                    }
                },
                singleLine = true,
                label = { Text(hint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                // 留空不填就沿用原来的值
                onClick = { onConfirm(value.text.toIntOrNull() ?: initial) },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun StatsContent(grid: BeadGrid, onClose: () -> Unit) {
    val groups = remember(grid) { grid.groups() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("色号分组统计", style = MaterialTheme.typography.titleLarge)
                Text(
                    "共 ${grid.total} 块 · ${grid.colors.size} 种颜色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClose) { Text("关闭") }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            for (group in groups) {
                item(key = "header-${group.prefix}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (group.prefix == "其他") "其他系列" else "${group.prefix} 系列",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "共 ${group.total} 块",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(group.items, key = { "${group.prefix}-${it.color.code}" }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .background(Color(item.color.argb), RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.color.code,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${item.count} 个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
