package com.johny.cameraxwithcropping

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

open class ImageCropperView(context: Context, attributeSet: AttributeSet) :
    View(context, attributeSet),
    CropUtils.TouchEventDetector.TouchEventListener {

    companion object {
        const val CROP_WINDOW_PAINTER_WIDTH = 10.0f
        const val OUTSIDE_WINDOW_PAINTER_WIDTH = 1.0f
        const val DRAG_ICONS_RADIUS = 10.0f
    }

    private val cropPainter = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = CROP_WINDOW_PAINTER_WIDTH
        isAntiAlias = true
        color = Color.WHITE
    }

    private val outsidePainter = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = OUTSIDE_WINDOW_PAINTER_WIDTH
        setARGB(125, 50, 50, 50)
    }


    private var mOriginBitmap: Bitmap? = null
    private var mCropBitmap: CropUtils.RotateBitmap? = null
    private val mMatrix: Matrix = Matrix()

    private var mCropParam: CropUtils.CropParam? = null
    private var mCropWindow: CropUtils.CropWindow? = null
    private var mIsCropParamChanged = true

    private var mScaleRate = 1.0.toFloat()
    private val mTouchEventDetector = CropUtils.TouchEventDetector()

    private val mDragDrawables = arrayOf(
        ContextCompat.getDrawable(context, R.drawable.ic_crop_x),
        ContextCompat.getDrawable(context, R.drawable.ic_crop_y),
        ContextCompat.getDrawable(context, R.drawable.ic_crop_x),
        ContextCompat.getDrawable(context, R.drawable.ic_crop_y)
    )


    //import group drawable
    private val canvasDrawable = ContextCompat.getDrawable(context, R.drawable.ic_canvas)


    fun destroy() {
        if (mCropBitmap != null && !mCropBitmap!!.bitmap?.isRecycled!!) {
            mCropBitmap!!.recycle()
            mCropBitmap = null
        }

        if (mOriginBitmap != null && !mOriginBitmap!!.isRecycled) {
            mOriginBitmap!!.recycle()
            mOriginBitmap = null
        }
    }


    open fun initialize(bitmap: Bitmap) {
        initialize(bitmap, 0, CropUtils.CropParam())
    }

    fun initialize(bitmap: Bitmap, param: CropUtils.CropParam) {
        initialize(bitmap, 0, param)
    }

    fun initialize(bitmap: Bitmap, degrees: Int) {
        initialize(bitmap, degrees, CropUtils.CropParam())
    }

    fun initialize(bitmap: Bitmap, degrees: Int, param: CropUtils.CropParam) {
        mCropParam = param
        mOriginBitmap = bitmap
        replace(bitmap, degrees)
    }

    fun getCropBitmap(): Bitmap? {
        return if (mCropBitmap != null) {
            mCropBitmap!!.bitmap
        } else null
    }

    fun rotate() {
        if (mCropBitmap != null) {
            mCropBitmap!!.rotation = mCropBitmap!!.rotation + 90
            mIsCropParamChanged = true
            invalidate()
        }
    }

    fun crop(): Bitmap? {
        if (mCropBitmap != null) {
            val cropWidth = mCropWindow!!.width() / mScaleRate
            val cropHeight = mCropWindow!!.height() / mScaleRate
            val cropRect: Rect = mCropWindow!!.getWindowRect(mScaleRate)
            val dstRect = RectF(0F, 0F, cropWidth, cropHeight)
            val cropMatrix = Matrix()
            cropMatrix.setRectToRect(RectF(cropRect), dstRect, Matrix.ScaleToFit.FILL)
            cropMatrix.preConcat(mCropBitmap!!.rotateMatrix)
            val cropped =
                Bitmap.createBitmap(cropWidth.toInt(), cropHeight.toInt(), Bitmap.Config.RGB_565)
            val canvas = Canvas(cropped)
            canvas.drawBitmap(mCropBitmap!!.bitmap!!, cropMatrix, null)
            // replace(cropped, 0)
            return cropped
        }
        return null
    }

    fun reset() {
        if (mCropBitmap == null) {
            return
        }
        replace(mOriginBitmap!!, 0)
    }

    private fun replace(bitmap: Bitmap, degrees: Int) {
        if (mCropBitmap != null && mCropBitmap!!.bitmap != mOriginBitmap) {
            mCropBitmap!!.recycle()
        }
        mCropBitmap = CropUtils.RotateBitmap(bitmap, degrees)
        mIsCropParamChanged = true
        invalidate()
    }

    private fun calculateCropParams(bitmap: CropUtils.RotateBitmap) {
        mScaleRate = Math.min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val offsetX = (width - bitmap.width * mScaleRate) / 2
        val offsetY = (height - bitmap.height * mScaleRate) / 2
        mMatrix.reset()
        mMatrix.postConcat(bitmap.rotateMatrix)
        mMatrix.postScale(mScaleRate, mScaleRate)
        mMatrix.postTranslate(offsetX, offsetY)
        val border = RectF(
            offsetX,
            offsetY,
            offsetX + bitmap.width * mScaleRate,
            offsetY + bitmap.height * mScaleRate
        )
        val param = CropUtils.CropParam()
        param.mAspectX = mCropParam!!.mAspectX
        param.mAspectY = mCropParam!!.mAspectY
        param.mOutputX = (mCropParam!!.mOutputX * mScaleRate).toInt()
        param.mOutputY = (mCropParam!!.mOutputY * mScaleRate).toInt()
        param.mMaxOutputX = (mCropParam!!.mMaxOutputX * mScaleRate).toInt()
        param.mMaxOutputY = (mCropParam!!.mMaxOutputY * mScaleRate).toInt()
        mCropWindow = CropUtils.CropWindow(border, param)
        mTouchEventDetector.setTouchEventListener(this)
    }


    fun drawOutsideCropArea(canvas: Canvas) {
        val rects = mCropWindow!!.outWindowRects
        for (rect in rects) {
            canvas.drawRect(rect!!, outsidePainter)
        }
    }

    private fun drawDragIcons(canvas: Canvas) {
        val points = mCropWindow!!.dragPoints
        for (i in points.indices) {
            if (i % 2 != 0) {
                mDragDrawables[i]!!.setBounds(
                    (points[i]!!.x - 200).toInt(),
                    (points[i]!!.y - 10).toInt(),

                    (points[i]!!.x + 200).toInt(),
                    (points[i]!!.y + 10).toInt()
                )
            } else {
                mDragDrawables[i]!!.setBounds(
                    (points[i]!!.x - DRAG_ICONS_RADIUS).toInt(),
                    (points[i]!!.y - 100).toInt(),
                    (points[i]!!.x + DRAG_ICONS_RADIUS).toInt(),
                    (points[i]!!.y + 100).toInt()
                )
            }
            mDragDrawables[i]!!.draw(canvas)
        }
    }

    private fun drawRectVector(canvas: Canvas) {
        val poins = mCropWindow!!.dragPoints
        canvasDrawable!!.setBounds(poins[0]!!.x, poins[1]!!.y, poins[2]!!.x, poins[3]!!.y)
        canvasDrawable.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        if (mCropBitmap != null) {
            if (mIsCropParamChanged) {
                calculateCropParams(mCropBitmap!!)
                mIsCropParamChanged = false
            }
            mCropBitmap!!.bitmap?.let { canvas.drawBitmap(it, mMatrix, cropPainter) }
            //canvas.drawRect(mCropWindow!!.windowRectF, cropPainter)
            // drawOutsideCropArea(canvas)
            // drawDragIcons(canvas)
            drawRectVector(canvas)
        }
        canvas.restore()
        super.onDraw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (mCropBitmap != null) {
            mTouchEventDetector.onTouchEvent(event)
        } else true
    }

    override fun onTouchDown(x: Float, y: Float) {
        mCropWindow?.onTouchDown(x, y)
    }

    override fun onTouchUp(x: Float, y: Float) {
        mCropWindow?.onTouchUp()
    }

    override fun onTouchMoved(srcX: Float, srcY: Float, deltaX: Float, deltaY: Float) {
        mCropWindow?.onTouchMoved(deltaX, deltaY)
        invalidate()
    }
}