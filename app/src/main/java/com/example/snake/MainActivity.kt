package com.example.snake

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(SnakeView(this))
    }
}

class SnakeView(context: Context) : View(context), GestureDetector.OnGestureListener {

    private companion object {
        const val CELL_COUNT = 20
        const val GAME_TICK_MS = 200L
    }

    private val snake = ArrayDeque<Pair<Int, Int>>()
    private var food = Pair(0, 0)
    private var dirX = 1
    private var dirY = 0
    private var nextDirX = 1
    private var nextDirY = 0
    private var score = 0
    private var gameOver = false
    private var cellSize = 0f

    private val paintSnake = Paint().apply { color = Color.GREEN }
    private val paintFood = Paint().apply { color = Color.RED }
    private val paintBg = Paint().apply { color = Color.BLACK }
    private val paintText = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gestureDetector = GestureDetector(context, this)
    private var loopRunning = false

    init {
        resetGame()
    }

    private fun resetGame() {
        snake.clear()
        snake.addFirst(Pair(7, 10))
        snake.addFirst(Pair(8, 10))
        snake.addFirst(Pair(9, 10))
        dirX = 1; dirY = 0
        nextDirX = 1; nextDirY = 0
        score = 0
        gameOver = false
        spawnFood()
        invalidate()
        if (isAttachedToWindow) startLoop()
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
                invalidate()
            }

            if (gameOver) {
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
        if (snake.contains(newHead)) { gameOver = true; return }
        snake.addFirst(newHead)
        if (newHead == food) { score++; spawnFood() } else { snake.removeLast() }
    }

    private fun spawnFood() {
        do { food = Pair((0 until CELL_COUNT).random(), (0 until CELL_COUNT).random()) }
        while (snake.contains(food))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        cellSize = minOf(w, h) / CELL_COUNT
        val offsetX = (w - cellSize * CELL_COUNT) / 2
        val offsetY = (h - cellSize * CELL_COUNT) / 2

        canvas.drawRect(0f, 0f, w, h, paintBg)

        for (seg in snake) {
            canvas.drawRect(
                offsetX + seg.first * cellSize + 1,
                offsetY + seg.second * cellSize + 1,
                offsetX + (seg.first + 1) * cellSize - 1,
                offsetY + (seg.second + 1) * cellSize - 1,
                paintSnake
            )
        }

        canvas.drawRect(
            offsetX + food.first * cellSize + 3,
            offsetY + food.second * cellSize + 3,
            offsetX + (food.first + 1) * cellSize - 3,
            offsetY + (food.second + 1) * cellSize - 3,
            paintFood
        )

        paintText.textSize = h * 0.05f
        canvas.drawText("Score: $score", w / 2, offsetY - 10f, paintText)

        if (gameOver) {
            paintText.textSize = h * 0.08f
            canvas.drawText("Game Over!", w / 2, h / 2 - h * 0.05f, paintText)
            paintText.textSize = h * 0.045f
            canvas.drawText("Tap to restart", w / 2, h / 2 + h * 0.04f, paintText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver && event.action == MotionEvent.ACTION_UP) {
            resetGame()
            return true
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
