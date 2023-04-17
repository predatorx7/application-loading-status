package com.udacity

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.withStyledAttributes
import kotlin.properties.Delegates
import android.animation.AnimatorSet
import android.graphics.*
import android.text.TextPaint
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import kotlin.math.min

fun AnimatorSet.disableViewDuringAnimation(view: View) = apply {
    doOnStart { view.isEnabled = false }
    doOnEnd { view.isEnabled = true }
}

class DownloadLoadingButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val PROGRESS_CIRCLE_SIZE_MULTIPLIER = 0.4f
        private const val PROGRESS_CIRCLE_LEFT_MARGIN_OFFSET = 0f
        private const val BY_HALF = 2f
        private val THREE_SECONDS = TimeUnit.SECONDS.toMillis(3)
    }

    private var text: CharSequence = context.getString(R.string.download)
    private var buttonBackgroundColor = 0
    private var defaultBackgroundColor = 0
    private var defaultText: CharSequence = context.getString(R.string.download)
    private var progressCircleBackgroundColor = 0

    private var widthSize = 0
    private var heightSize = 0
    private var textColor = 0

    // handling state change
    private var buttonState: ButtonState by
    Delegates.observable<ButtonState>(ButtonState.Completed) { p, old, newState ->
        when (newState) {
            ButtonState.Loading -> {
                // LoadingButton is now Loading and we need to set the correct text
                buttonText = text.toString()

                // We only calculate ButtonText bounds and ProgressCircle rect once,
                // Only when buttonText is first initialized with loadingText
                if (!::textBounds.isInitialized) {
                    retrieveButtonTextBounds()
                    computeProgressCircleRect()
                }

                // ProgressCircle and Button background animations must start now
                animatorSet.start()
            }
            else -> {
                // LoadingButton is not doing any Loading so we need to reset to default text
                buttonText = defaultText.toString()

                // ProgressCircle animation must stop now
                newState.takeIf { it == ButtonState.Completed }?.run { animatorSet.cancel() }
            }
        }
    }

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 55f
        typeface = Typeface.create("", Typeface.BOLD)
    }

    init {
        isClickable = true

        context.withStyledAttributes(attrs, R.styleable.DownloadLoadingButton) {
            textColor = getColor(R.styleable.DownloadLoadingButton_textColor, 0)
            defaultBackgroundColor = getColor(R.styleable.DownloadLoadingButton_defaultBackgroundColor, 0)
            buttonBackgroundColor = getColor(R.styleable.DownloadLoadingButton_backgroundColor, 0)
            defaultText = getText(R.styleable.DownloadLoadingButton_defaultText)
            text = getText(R.styleable.DownloadLoadingButton_text)
        }.also {
            buttonText = defaultText.toString()
            progressCircleBackgroundColor = ContextCompat.getColor(context, R.color.colorAccent)
        }
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setState(state: ButtonState) {
        if (state != buttonState) {
            buttonState = state
            invalidate()
        }
    }



    private var buttonText = defaultText.toString()


    // It'll be initialized when first Loading state is trigger
    private lateinit var textBounds: Rect


    // region Progress Circle/Arc variables
    private val progressCircleRect = RectF()
    private var progressCircleSize = 0f


    // region Animation variables
    private val animatorSet: AnimatorSet = AnimatorSet().apply {
        duration = THREE_SECONDS
        disableViewDuringAnimation(this@DownloadLoadingButton)
    }
    private var currentProgressCircleAnimationValue = 0f
    private val progressCircleAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            currentProgressCircleAnimationValue = it.animatedValue as Float
            invalidate()
        }
    }
    private var currentBackgroundAnimationValue = 0f
    private lateinit var backgroundAnimator: ValueAnimator


    /**
     * Initialize and retrieve the text boundary box of [buttonText] and store it into [textBounds].
     */
    private fun retrieveButtonTextBounds() {
        textBounds = Rect()
        paint.getTextBounds(buttonText, 0, buttonText.length, textBounds)
    }

    /**
     * Calculate left, top, right and bottom for [progressCircleRect] based on [textBounds]
     * and [heightSize].
     *
     * **This needs to be called after [retrieveButtonTextBounds()] call**
     */
    private fun computeProgressCircleRect() {
        val horizontalCenter =
            (textBounds.right + textBounds.width() + PROGRESS_CIRCLE_LEFT_MARGIN_OFFSET)
        val verticalCenter = (heightSize / BY_HALF)

        progressCircleRect.set(
            horizontalCenter - progressCircleSize,
            verticalCenter - progressCircleSize,
            horizontalCenter + progressCircleSize,
            verticalCenter + progressCircleSize
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val effectiveMinWidth: Int = paddingLeft + paddingRight + suggestedMinimumWidth
        val w: Int = resolveSizeAndState(effectiveMinWidth, widthMeasureSpec, 1)
        val h: Int = resolveSizeAndState(
            MeasureSpec.getSize(w),
            heightMeasureSpec,
            0
        )
        widthSize = w
        heightSize = h
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        progressCircleSize = (min(w, h) / BY_HALF) * PROGRESS_CIRCLE_SIZE_MULTIPLIER
        ValueAnimator.ofFloat(0f, widthSize.toFloat()).apply {
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                currentBackgroundAnimationValue = it.animatedValue as Float
                invalidate()
            }
        }.also {
            backgroundAnimator = it
            animatorSet.playProgressCircleAndBackgroundTogether()
        }
    }


    /**
     * Sets up [animatorSet] to play [progressCircleAnimator] and [backgroundAnimator]
     * animations at the same time.
     */
    private fun AnimatorSet.playProgressCircleAndBackgroundTogether() =
        apply { playTogether(progressCircleAnimator, backgroundAnimator) }

    override fun performClick(): Boolean {
        super.performClick()
        // We only change button state to Clicked if the current state is Completed
        if (buttonState == ButtonState.Completed) {
            buttonState = ButtonState.Clicked
            invalidate()
        }
        return true
    }


    // region LoadingButton drawing
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let { buttonCanvas ->
            buttonCanvas.apply {
                drawBackgroundColor()
                drawButtonText()
                drawProgressCircleIfLoading()
            }
        }
    }

    /**
     * Draws the button text using current value of [buttonText] which may change based on
     * the [buttonState].
     */
    private fun Canvas.drawButtonText() {
        // Draw the Loading Text at the Center of the Canvas
        // ref.: https://blog.danlew.net/2013/10/03/centering_single_line_text_in_a_canvas/
        paint.color = textColor
        drawText(
            buttonText,
            (widthSize / BY_HALF),
            (heightSize / BY_HALF) + paint.computeTextOffset(),
            paint
        )
    }

    /**
     * Calculates the height of the TextPaint using [TextPaint.ascent] and [TextPaint.descent].
     * These measure the size above/below the text's baseline. Combined, they add up to the total
     * height of the drawn text.
     */
    private fun TextPaint.computeTextOffset() = ((descent() - ascent()) / 2) - descent()

    /**
     * Draws the default button background color using [buttonBackgroundColor].
     */
    private fun Canvas.drawBackgroundColor() {
        when (buttonState) {
            ButtonState.Loading -> {
                drawLoadingBackgroundColor()
                drawDefaultBackgroundColor()
            }
            else -> drawColor(defaultBackgroundColor)
        }
    }

    /**
     * Draws the [Rect] with [buttonBackgroundColor] representing the loading progress.
     */
    private fun Canvas.drawLoadingBackgroundColor() = backgroundPaint.apply {
        color = buttonBackgroundColor
    }.run {
        drawRect(
            0f,
            0f,
            currentBackgroundAnimationValue,
            heightSize.toFloat(),
            backgroundPaint
        )
    }

    /**
     * Draws the [Rect] with [defaultBackgroundColor] representing the background of the button.
     */
    private fun Canvas.drawDefaultBackgroundColor() = backgroundPaint.apply {
        color = defaultBackgroundColor
    }.run {
        drawRect(
            currentBackgroundAnimationValue,
            0f,
            widthSize.toFloat(),
            heightSize.toFloat(),
            backgroundPaint
        )
    }

    /**
     * Draws progress circle if [buttonState] is [ButtonState.Loading].
     */
    private fun Canvas.drawProgressCircleIfLoading() =
        buttonState.takeIf { it == ButtonState.Loading }?.let { drawProgressCircleIfLoading(this) }

    /**
     * Draws the progress circle using an arc only when [buttonState] changes to [ButtonState.Loading].
     * The sweep angle uses [currentProgressCircleAnimationValue] which is changed according to when
     * [progressCircleAnimator] send updates after the values for the animation have been calculated.
     */
    private fun drawProgressCircleIfLoading(buttonCanvas: Canvas) {
        backgroundPaint.color = progressCircleBackgroundColor
        buttonCanvas.drawArc(
            progressCircleRect,
            0f,
            currentProgressCircleAnimationValue,
            true,
            backgroundPaint
        )
    }
}