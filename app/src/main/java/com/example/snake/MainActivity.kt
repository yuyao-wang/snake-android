package com.example.snake

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class MainActivity : Activity() {
    private companion object {
        const val PRIVACY_POLICY_URL = "https://yuyao-wang.github.io/snake-android/privacy-policy.html"
    }

    private lateinit var snakeView: SnakeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        snakeView = SnakeView(
            context = this,
            onExit = { finish() },
            onPrivacyPolicy = { openPrivacyPolicy() }
        )
        setContentView(snakeView)
    }

    override fun onBackPressed() {
        snakeView.saveScoreBeforeExit()
        super.onBackPressed()
    }

    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        } catch (_: ActivityNotFoundException) {
            // If no browser is available, the Play listing still includes the policy URL.
        }
    }
}

class SnakeView(
    context: Context,
    private val onExit: () -> Unit,
    private val onPrivacyPolicy: () -> Unit
) : View(context),
    GestureDetector.OnGestureListener {

    private companion object {
        const val CELL_COUNT = 20
        const val GAME_TICK_MS = 200L
        const val MAX_SCORE_HISTORY = 5
        const val PREFS_NAME = "snake_scores"
        const val KEY_SCORE_HISTORY = "score_history"
    }

    private val snake = ArrayDeque<Pair<Int, Int>>()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var scoreHistory = loadScoreHistory()
    private var food = Pair(0, 0)
    private var dirX = 1
    private var dirY = 0
    private var nextDirX = 1
    private var nextDirY = 0
    private var score = 0
    private var gameOver = false
    private var scoreRecorded = false
    private var cellSize = 0f

    private val paintSnake = Paint().apply { color = Color.GREEN }
    private val paintFood = Paint().apply { color = Color.RED }
    private val paintBg = Paint().apply { color = Color.BLACK }
    private val paintBoard = Paint().apply { color = Color.rgb(16, 28, 18) }
    private val paintGrid = Paint().apply {
        color = Color.rgb(34, 53, 38)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val paintButton = Paint().apply {
        color = Color.rgb(44, 67, 52)
        isAntiAlias = true
    }
    private val paintButtonStroke = Paint().apply {
        color = Color.rgb(120, 168, 127)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintOverlay = Paint().apply { color = Color.argb(180, 0, 0, 0) }
    private val paintText = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gestureDetector = GestureDetector(context, this)
    private val boardBounds = RectF()
    private val restartButtonBounds = RectF()
    private val privacyButtonBounds = RectF()
    private val exitButtonBounds = RectF()
    private var loopRunning = false

    init {
        resetGame()
    }

    private fun loadScoreHistory(): List<Int> {
        val raw = prefs.getString(KEY_SCORE_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .take(MAX_SCORE_HISTORY)
    }

    private fun recordScore() {
        if (scoreRecorded) return

        scoreHistory = (listOf(score) + scoreHistory).take(MAX_SCORE_HISTORY)
        prefs.edit()
            .putString(KEY_SCORE_HISTORY, scoreHistory.joinToString(","))
            .apply()
        scoreRecorded = true
    }

    private fun bestScore(): Int = maxOf(score, scoreHistory.maxOrNull() ?: 0)

    private fun resetGame() {
        snake.clear()
        snake.addFirst(Pair(7, 10))
        snake.addFirst(Pair(8, 10))
        snake.addFirst(Pair(9, 10))
        dirX = 1; dirY = 0
        nextDirX = 1; nextDirY = 0
        score = 0
        gameOver = false
        scoreRecorded = false
        spawnFood()
        invalidate()
        if (isAttachedToWindow) startLoop()
    }

    private fun restartGame() {
        if (!gameOver && score > 0) recordScore()
        resetGame()
    }

    fun saveScoreBeforeExit() {
        if (!gameOver && score > 0) recordScore()
    }

    private fun exitGame() {
        saveScoreBeforeExit()
        stopLoop()
        onExit()
    }

    private fun finishGame() {
        gameOver = true
        recordScore()
        stopLoop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    private fun startLoop() {
        if (loopRunning || gameOver) return
        loopRunning = true
        handler.removeCallbacks(gameLoop)
        handler.postDelayed(gameLoop, GAME_TICK_MS)
    }

    private fun stopLoop() {
        loopRunning = false
        handler.removeCallbacks(gameLoop)
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameOver) {
                update()
            }
            invalidate()

            if (gameOver || !loopRunning) {
                loopRunning = false
            } else {
                handler.postDelayed(this, GAME_TICK_MS)
            }
        }
    }

    private fun update() {
        dirX = nextDirX; dirY = nextDirY
        val head = snake.first()
        val newHead = Pair(
            (head.first + dirX + CELL_COUNT) % CELL_COUNT,
            (head.second + dirY + CELL_COUNT) % CELL_COUNT
        )

        val willEat = newHead == food
        val collisionBody = if (willEat) snake.toList() else snake.dropLast(1)
        if (collisionBody.contains(newHead)) {
            finishGame()
            return
        }

        snake.addFirst(newHead)
        if (willEat) {
            score++
            if (snake.size == CELL_COUNT * CELL_COUNT) {
                finishGame()
            } else {
                spawnFood()
            }
        } else {
            snake.removeLast()
        }
    }

    private fun spawnFood() {
        val emptyCells = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until CELL_COUNT) {
            for (y in 0 until CELL_COUNT) {
                val cell = Pair(x, y)
                if (!snake.contains(cell)) emptyCells.add(cell)
            }
        }
        if (emptyCells.isNotEmpty()) food = emptyCells.random()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padding = dp(16f)
        val topArea = dp(112f)
        val bottomArea = dp(112f)
        val boardSize = minOf(
            maxOf(1f, w - padding * 2),
            maxOf(1f, h - topArea - bottomArea)
        )
        val offsetX = (w - boardSize) / 2
        val offsetY = topArea + maxOf(0f, (h - topArea - bottomArea - boardSize) / 2)
        cellSize = boardSize / CELL_COUNT
        boardBounds.set(offsetX, offsetY, offsetX + boardSize, offsetY + boardSize)

        canvas.drawRect(0f, 0f, w, h, paintBg)
        drawHeader(canvas, w)
        drawBoard(canvas)

        for (seg in snake) {
            canvas.drawRect(
                offsetX + seg.first * cellSize + 1,
                offsetY + seg.second * cellSize + 1,
                offsetX + (seg.first + 1) * cellSize - 1,
                offsetY + (seg.second + 1) * cellSize - 1,
                paintSnake
            )
        }

        if (!gameOver) {
            canvas.drawRect(
                offsetX + food.first * cellSize + 3,
                offsetY + food.second * cellSize + 3,
                offsetX + (food.first + 1) * cellSize - 3,
                offsetY + (food.second + 1) * cellSize - 3,
                paintFood
            )
        }

        drawScoreHistory(canvas, w, boardBounds.bottom + dp(28f))

        if (gameOver) {
            drawGameOver(canvas)
        }
    }

    private fun drawHeader(canvas: Canvas, width: Float) {
        val padding = dp(16f)
        paintText.textAlign = Paint.Align.LEFT
        paintText.textSize = sp(20f)
        paintText.color = Color.WHITE
        canvas.drawText("Score: $score", padding, dp(32f), paintText)

        paintText.textSize = sp(15f)
        paintText.color = Color.rgb(185, 214, 188)
        canvas.drawText("Best: ${bestScore()}", padding, dp(56f), paintText)

        val buttonTop = dp(68f)
        val buttonBottom = dp(104f)
        val buttonGap = dp(8f)
        val buttonWidth = (width - padding * 2 - buttonGap * 2) / 3f
        restartButtonBounds.set(padding, buttonTop, padding + buttonWidth, buttonBottom)
        privacyButtonBounds.set(
            restartButtonBounds.right + buttonGap,
            buttonTop,
            restartButtonBounds.right + buttonGap + buttonWidth,
            buttonBottom
        )
        exitButtonBounds.set(width - padding - buttonWidth, buttonTop, width - padding, buttonBottom)

        drawButton(canvas, restartButtonBounds, "Restart")
        drawButton(canvas, privacyButtonBounds, "Privacy")
        drawButton(canvas, exitButtonBounds, "Exit")
    }

    private fun drawButton(canvas: Canvas, bounds: RectF, label: String) {
        val radius = dp(8f)
        canvas.drawRoundRect(bounds, radius, radius, paintButton)
        canvas.drawRoundRect(bounds, radius, radius, paintButtonStroke)

        paintText.textAlign = Paint.Align.CENTER
        paintText.textSize = sp(15f)
        paintText.color = Color.WHITE
        val textY = bounds.centerY() - (paintText.descent() + paintText.ascent()) / 2f
        canvas.drawText(label, bounds.centerX(), textY, paintText)
    }

    private fun drawBoard(canvas: Canvas) {
        canvas.drawRect(boardBounds, paintBoard)
        for (i in 0..CELL_COUNT) {
            val x = boardBounds.left + i * cellSize
            val y = boardBounds.top + i * cellSize
            canvas.drawLine(x, boardBounds.top, x, boardBounds.bottom, paintGrid)
            canvas.drawLine(boardBounds.left, y, boardBounds.right, y, paintGrid)
        }
    }

    private fun drawScoreHistory(canvas: Canvas, width: Float, top: Float) {
        paintText.textAlign = Paint.Align.CENTER
        paintText.textSize = sp(15f)
        paintText.color = Color.rgb(185, 214, 188)
        val recentScores = if (scoreHistory.isEmpty()) "none" else scoreHistory.joinToString("  ")
        canvas.drawText("Recent scores: $recentScores", width / 2f, top, paintText)
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(boardBounds, paintOverlay)

        paintText.textAlign = Paint.Align.CENTER
        paintText.color = Color.WHITE
        paintText.textSize = sp(28f)
        canvas.drawText("Game Over", boardBounds.centerX(), boardBounds.centerY() - dp(36f), paintText)

        paintText.textSize = sp(17f)
        canvas.drawText(
            "Score: $score   Best: ${bestScore()}",
            boardBounds.centerX(),
            boardBounds.centerY(),
            paintText
        )

        paintText.color = Color.rgb(210, 232, 210)
        paintText.textSize = sp(15f)
        canvas.drawText(
            "Tap Restart to play again",
            boardBounds.centerX(),
            boardBounds.centerY() + dp(34f),
            paintText
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (restartButtonBounds.contains(event.x, event.y)) {
                restartGame()
                return true
            }
            if (privacyButtonBounds.contains(event.x, event.y)) {
                onPrivacyPolicy()
                return true
            }
            if (exitButtonBounds.contains(event.x, event.y)) {
                exitGame()
                return true
            }
            if (gameOver && boardBounds.contains(event.x, event.y)) {
                resetGame()
                return true
            }
        }

        return gestureDetector.onTouchEvent(event)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
        if (abs(vX) > abs(vY)) {
            if (vX > 0 && dirX != -1) { nextDirX = 1; nextDirY = 0 }
            else if (vX < 0 && dirX != 1) { nextDirX = -1; nextDirY = 0 }
        } else {
            if (vY > 0 && dirY != -1) { nextDirX = 0; nextDirY = 1 }
            else if (vY < 0 && dirY != 1) { nextDirX = 0; nextDirY = -1 }
        }
        return true
    }

    override fun onDown(e: MotionEvent) = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent) = false
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float) = false
    override fun onLongPress(e: MotionEvent) {}
}
