package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Line(val start: Offset, val end: Offset, val color: Color, val strokeWidth: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(uriString: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    val lines = remember { mutableStateListOf<Line>() }
    var mode by remember { mutableStateOf("VIEW") } // "VIEW", "DRAW", "ERASE", "CROP", "TEXT"
    var currentColor by remember { mutableStateOf(Color.Red) }
    
    val predefinedColors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Black)
    
    var showCompressionDialog by remember { mutableStateOf(false) }

    // Computed layout metrics for the image on Canvas
    var imgLeft by remember { mutableStateOf(0f) }
    var imgTop by remember { mutableStateOf(0f) }
    var imgWidth by remember { mutableStateOf(0f) }
    var imgHeight by remember { mutableStateOf(0f) }

    // Crop state in normalized coordinates (0f to 1f) relative to the image
    var cropLeft by remember { mutableStateOf(0.1f) }
    var cropTop by remember { mutableStateOf(0.1f) }
    var cropRight by remember { mutableStateOf(0.9f) }
    var cropBottom by remember { mutableStateOf(0.9f) }
    var resizeCorner by remember { mutableStateOf(0) } // 0: None, 1: Move, 2: TL, 3: TR, 4: BL, 5: BR
    
    val touchSlop = 60f

    DisposableEffect(Unit) {
        // Resource Exclusivity: Pause background playback to prevent memory pressure while editing photos
        try {
            com.example.service.PlayerManager.exoPlayer?.pause()
        } catch (e: Exception) {}
        onDispose {}
    }

    LaunchedEffect(uriString) {
        withContext(Dispatchers.IO) {
            try {
                val parsedUri = Uri.parse(uriString)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                
                // Keep max dimension bounded around 2048 to prevent OOM on 3GB RAM devices
                val maxTargetDim = 2048
                val maxSourceDim = maxOf(options.outWidth, options.outHeight)
                var sampleSize = 1
                while (maxSourceDim / (sampleSize * 2) >= maxTargetDim) {
                    sampleSize *= 2
                }
                options.inSampleSize = sampleSize
                options.inJustDecodeBounds = false

                var loadedBitmap: Bitmap? = null
                context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                    loadedBitmap = BitmapFactory.decodeStream(stream, null, options)
                }
                if (loadedBitmap != null) {
                    withContext(Dispatchers.Main) {
                        imageBitmap = loadedBitmap?.asImageBitmap()
                        cropLeft = 0f
                        cropTop = 0f
                        cropRight = 1f
                        cropBottom = 1f
                    }
                }
            } catch (e: Exception) {
                LogKeeper.logError("PhotoEditor", "Failed to load image", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Editor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showCompressionDialog = true
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save/Distribute")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (mode == "DRAW") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        predefinedColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(36.dp)
                                    .background(color, shape = CircleShape)
                                    .border(
                                        width = if (currentColor == color) 3.dp else 1.dp,
                                        color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { currentColor = color }
                            )
                        }
                    }
                }
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { mode = if (mode == "DRAW") "VIEW" else "DRAW" }) {
                        Icon(
                            Icons.Filled.Brush, 
                            contentDescription = "Draw",
                            tint = if (mode == "DRAW") MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.weight(0.5f))
                    IconButton(onClick = { mode = if (mode == "ERASE") "VIEW" else "ERASE" }) {
                        Icon(
                            Icons.Filled.Clear, 
                            contentDescription = "Erase",
                            tint = if (mode == "ERASE") MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.weight(0.5f))
                    IconButton(onClick = { mode = if (mode == "CROP") "VIEW" else "CROP" }) {
                        Icon(
                            Icons.Filled.Crop, 
                            contentDescription = "Crop",
                            tint = if (mode == "CROP") MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (imageBitmap != null) {
                var dragStart by remember { mutableStateOf(Offset.Zero) }
                
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(mode) {
                            if (mode == "DRAW") {
                                detectDragGestures(
                                    onDragStart = { offset -> dragStart = offset },
                                    onDrag = { change, dragAmount -> 
                                        change.consume()
                                        lines.add(Line(dragStart, dragStart + dragAmount, currentColor, 10f))
                                        dragStart += dragAmount
                                    }
                                )
                            }
                            if (mode == "ERASE") {
                                detectDragGestures(
                                    onDragStart = { offset -> dragStart = offset },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val eraseRadius = 40f
                                        val toRemove = lines.filter { line ->
                                            val dx = line.start.x - change.position.x
                                            val dy = line.start.y - change.position.y
                                            Math.sqrt((dx * dx + dy * dy).toDouble()) < eraseRadius
                                        }
                                        lines.removeAll(toRemove)
                                    }
                                )
                            }
                            if (mode == "CROP") {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (imgWidth == 0f || imgHeight == 0f) return@detectDragGestures
                                        val cl = imgLeft + cropLeft * imgWidth
                                        val ct = imgTop + cropTop * imgHeight
                                        val cr = imgLeft + cropRight * imgWidth
                                        val cb = imgTop + cropBottom * imgHeight
                                        
                                        // check corners in order: TL, TR, BL, BR, MOVE
                                        resizeCorner = when {
                                            (offset.x - cl).let { it * it } + (offset.y - ct).let { it * it } < touchSlop * touchSlop -> 2
                                            (offset.x - cr).let { it * it } + (offset.y - ct).let { it * it } < touchSlop * touchSlop -> 3
                                            (offset.x - cl).let { it * it } + (offset.y - cb).let { it * it } < touchSlop * touchSlop -> 4
                                            (offset.x - cr).let { it * it } + (offset.y - cb).let { it * it } < touchSlop * touchSlop -> 5
                                            offset.x in cl..cr && offset.y in ct..cb -> 1
                                            else -> 0
                                        }
                                        dragStart = offset
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (resizeCorner == 0 || imgWidth == 0f || imgHeight == 0f) return@detectDragGestures
                                        change.consume()
                                        val dx = dragAmount.x / imgWidth
                                        val dy = dragAmount.y / imgHeight
                                        when (resizeCorner) {
                                            1 -> { // Move
                                                val w = cropRight - cropLeft
                                                val h = cropBottom - cropTop
                                                var nL = cropLeft + dx
                                                var nT = cropTop + dy
                                                if (nL < 0f) nL = 0f
                                                if (nT < 0f) nT = 0f
                                                if (nL + w > 1f) nL = 1f - w
                                                if (nT + h > 1f) nT = 1f - h
                                                cropLeft = nL
                                                cropRight = nL + w
                                                cropTop = nT
                                                cropBottom = nT + h
                                            }
                                            2 -> { // TL
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                            }
                                            3 -> { // TR
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                            }
                                            4 -> { // BL
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                            }
                                            5 -> { // BR
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                            }
                                        }
                                    },
                                    onDragEnd = { resizeCorner = 0 },
                                    onDragCancel = { resizeCorner = 0 }
                                )
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    val imageAspect = imageBitmap!!.width.toFloat() / imageBitmap!!.height.toFloat()
                    val canvasAspect = canvasWidth / canvasHeight
                    
                    var drawWidth = canvasWidth
                    var drawHeight = canvasHeight
                    
                    if (imageAspect > canvasAspect) {
                        drawHeight = canvasWidth / imageAspect
                    } else {
                        drawWidth = canvasHeight * imageAspect
                    }
                    
                    val left = (canvasWidth - drawWidth) / 2f
                    val top = (canvasHeight - drawHeight) / 2f
                    
                    // Update computed metrics
                    imgLeft = left
                    imgTop = top
                    imgWidth = drawWidth
                    imgHeight = drawHeight
                    
                    drawImage(
                        image = imageBitmap!!,
                        dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt()),
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt())
                    )
                    
                    lines.forEach { line ->
                        drawLine(
                            color = line.color,
                            start = line.start,
                            end = line.end,
                            strokeWidth = line.strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    
                    if (mode == "CROP") {
                        val cL = left + cropLeft * drawWidth
                        val cT = top + cropTop * drawHeight
                        val cR = left + cropRight * drawWidth
                        val cB = top + cropBottom * drawHeight
                        
                        // Overlay shade
                        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(left, top), size = Size(drawWidth, cT - top)) // Top
                        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(left, cB), size = Size(drawWidth, top + drawHeight - cB)) // Bottom
                        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(left, cT), size = Size(cL - left, cB - cT)) // Left
                        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(cR, cT), size = Size(left + drawWidth - cR, cB - cT)) // Right
                        
                        // Crop box
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cL, cT),
                            size = Size(cR - cL, cB - cT),
                            style = Stroke(width = 5f)
                        )
                        // Corners
                        val cornerLen = 40f
                        // TL
                        drawLine(Color.Green, Offset(cL, cT), Offset(cL + cornerLen, cT), 12f)
                        drawLine(Color.Green, Offset(cL, cT), Offset(cL, cT + cornerLen), 12f)
                        // TR
                        drawLine(Color.Green, Offset(cR, cT), Offset(cR - cornerLen, cT), 12f)
                        drawLine(Color.Green, Offset(cR, cT), Offset(cR, cT + cornerLen), 12f)
                        // BL
                        drawLine(Color.Green, Offset(cL, cB), Offset(cL + cornerLen, cB), 12f)
                        drawLine(Color.Green, Offset(cL, cB), Offset(cL, cB - cornerLen), 12f)
                        // BR
                        drawLine(Color.Green, Offset(cR, cB), Offset(cR - cornerLen, cB), 12f)
                        drawLine(Color.Green, Offset(cR, cB), Offset(cR, cB - cornerLen), 12f)
                    }
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
    
    if (showCompressionDialog) {
        com.example.ui.components.CompressionOptionsDialog(
            uris = listOf(uriString),
            onDismiss = { showCompressionDialog = false },
            onStartCompression = { uris, w, h, q, f ->
                coroutineScope.launch {
                    val editedUri = withContext(Dispatchers.IO) {
                        try {
                            // Render to a new Bitmap
                            val originalImageBitmap = imageBitmap ?: return@withContext uriString
                            val origBmp = originalImageBitmap.asAndroidBitmap()
                            
                            val cw = cropRight - cropLeft
                            val ch = cropBottom - cropTop
                            val newW = (origBmp.width * cw).toInt()
                            val newH = (origBmp.height * ch).toInt()
                            
                            if (newW <= 0 || newH <= 0) return@withContext uriString
                            
                            val resBmp = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(resBmp)
                            
                            val srcRect = android.graphics.Rect(
                                (origBmp.width * cropLeft).toInt(),
                                (origBmp.height * cropTop).toInt(),
                                (origBmp.width * cropRight).toInt(),
                                (origBmp.height * cropBottom).toInt()
                            )
                            val dstRect = android.graphics.Rect(0, 0, newW, newH)
                            canvas.drawBitmap(origBmp, srcRect, dstRect, null)
                            
                            val paint = android.graphics.Paint().apply {
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                isAntiAlias = true
                            }
                            
                                // Map lines
                                val scaleX = origBmp.width / imgWidth
                                val scaleY = origBmp.height / imgHeight
                                val cropOffsetX = imgWidth * cropLeft
                                val cropOffsetY = imgHeight * cropTop
                                
                                lines.forEach { line ->
                                    paint.color = android.graphics.Color.argb(
                                        (line.color.alpha * 255).toInt(),
                                        (line.color.red * 255).toInt(),
                                        (line.color.green * 255).toInt(),
                                        (line.color.blue * 255).toInt()
                                    )
                                    paint.strokeWidth = line.strokeWidth * ((scaleX + scaleY) / 2f)
                                    
                                    val startX = (line.start.x - imgLeft - cropOffsetX) * scaleX
                                    val startY = (line.start.y - imgTop - cropOffsetY) * scaleY
                                    val endX = (line.end.x - imgLeft - cropOffsetX) * scaleX
                                    val endY = (line.end.y - imgTop - cropOffsetY) * scaleY
                                    
                                    canvas.drawLine(startX, startY, endX, endY, paint)
                                }
                                
                                // Save to temp file
                                LogKeeper.log("Rendering ${lines.size} drawn lines and cropping image...", "PhotoEditor")
                                val tempFile = java.io.File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
                                val out = java.io.FileOutputStream(tempFile)
                                resBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                out.flush()
                                out.close()
                                
                                LogKeeper.log("Editor render success. Handing off to compressor...", "PhotoEditor")
                                Uri.fromFile(tempFile).toString()
                            } catch (e: Exception) {
                                LogKeeper.logError("PhotoEditor", "Save rendering failed", e)
                                uriString
                            }
                        }
                        
                        val intent = android.content.Intent(context, com.example.service.CompressionService::class.java).apply {
                            putStringArrayListExtra("uris", java.util.ArrayList(listOf(editedUri)))
                            putExtra("maxWidth", w)
                            putExtra("maxHeight", h)
                            putExtra("quality", q)
                            putExtra("format", f)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        android.widget.Toast.makeText(context, "Rendering & Compression started.", android.widget.Toast.LENGTH_LONG).show()
                        showCompressionDialog = false
                        onNavigateBack()
                    }
                }
        )
    }
}
