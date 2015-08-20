package com.think.linxuanxuan.progresswheelsample;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

/**
 * A Material style progress wheel, compatible up to 2.2.
 * Todd Davies' Progress Wheel https://github.com/Todd-Davies/ProgressWheel
 *
 * @author Nico Hormazábal
 *         <p>
 *         Licensed under the Apache License 2.0 license see:
 *         http://www.apache.org/licenses/LICENSE-2.0
 */
public class ProgressWheel extends View {
    private static final String TAG = ProgressWheel.class.getSimpleName();

    /**
     * *********
     * DEFAULTS *
     * **********
     */
    //Sizes (with defaults in DP)
    private int circleRadius = 28;
    private int barWidth = 4;
    private int rimWidth = 4;

    //bar最短的长度
    private final int barLength = 16;
    //最上面自由滚动的圆圈最长的长度
    private final int barMaxLength = 270;

    private boolean fillRadius = false;

    private double timeStartGrowing = 0;
    private double barSpinCycleTime = 460;
    /*
     * 每次bar绘制的长度都是由最短的长度加上ExtraLength，这个长度是通过对时间的计算来得到的
     * 像是一个山峰，先变大，在变小，但是不可以超过MaxLength
     */
    private float barExtraLength = 0;
    private boolean barGrowingFromFront = true;

    private long pausedTimeWithoutGrowing = 0;
    private final long pauseGrowingTime = 200;

    //Colors (with defaults)
    private int barColor = 0xAA000000;
    private int rimColor = 0x00FFFFFF;

    //Paints
    private Paint barPaint = new Paint();
    private Paint rimPaint = new Paint();

    //Rectangles
    private RectF circleBounds = new RectF();

    //Animation
    //The amount of degrees per second
    //旋转的速度
    private float spinSpeed = 230.0f;
    //private float spinSpeed = 120.0f;
    // The last time the spinner was animated
    private long lastTimeAnimated = 0;

    private boolean linearProgress;

    private float mProgress = 0.0f;
    private float mTargetProgress = 0.0f;
    //一种模式，自由旋转的为true，其余两种为false
    private boolean isSpinning = false;

    /*
     * My Implementation
     * making tick
     */
    private int tickSize = 20;
    private float lineProgress = 0.0f;
    private float lineSpeed = 0.1f;
    private boolean isDrawFirst = false;
    private boolean isDrawSecond = false;

    private ProgressCallback callback;

    private android.os.Handler handler;

    /**
     * The constructor for the ProgressWheel
     *
     * @param context
     * @param attrs
     */
    public ProgressWheel(Context context, AttributeSet attrs) {
        super(context, attrs);

        parseAttributes(context.obtainStyledAttributes(attrs,
                R.styleable.ProgressWheel));
        handler = new android.os.Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == 1) {
                    invalidate();
                }
            }
        };
    }

    /**
     * The constructor for the ProgressWheel
     *
     * @param context
     */
    public ProgressWheel(Context context) {
        super(context);
    }

    //----------------------------------
    //Setting up stuff
    //----------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int viewWidth = circleRadius + this.getPaddingLeft() + this.getPaddingRight();
        int viewHeight = circleRadius + this.getPaddingTop() + this.getPaddingBottom();

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(viewWidth, widthSize);
        } else {
            //Be whatever you want
            width = viewWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(viewHeight, heightSize);
        } else {
            //Be whatever you want
            height = viewHeight;
        }

        setMeasuredDimension(width, height);
    }

    /**
     * Use onSizeChanged instead of onAttachedToWindow to get the dimensions of the view,
     * because this method is called after measuring the dimensions of MATCH_PARENT & WRAP_CONTENT.
     * Use this dimensions to setup the bounds and paints.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupBounds(w, h);
        setupPaints();
        invalidate();
    }

    /**
     * Set the properties of the paints we're using to
     * draw the progress wheel
     */
    private void setupPaints() {
        barPaint.setColor(barColor);
        barPaint.setAntiAlias(true);
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(barWidth);
//        barPaint.setStrokeJoin(Paint.Join.ROUND);
        barPaint.setStrokeCap(Paint.Cap.ROUND);

        rimPaint.setColor(rimColor);
        rimPaint.setAntiAlias(true);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);
    }

    /**
     * Set the bounds of the component
     */
    private void setupBounds(int layout_width, int layout_height) {
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();

        if (!fillRadius) {
            // Width should equal to Height, find the min value to setup the circle
            int minValue = Math.min(layout_width - paddingLeft - paddingRight,
                    layout_height - paddingBottom - paddingTop);

            int circleDiameter = Math.min(minValue, circleRadius * 2 - barWidth * 2);

            // Calc the Offset if needed for centering the wheel in the available space
            int xOffset = (layout_width - paddingLeft - paddingRight - circleDiameter) / 2 + paddingLeft;
            int yOffset = (layout_height - paddingTop - paddingBottom - circleDiameter) / 2 + paddingTop;

            circleBounds = new RectF(xOffset + barWidth,
                    yOffset + barWidth,
                    xOffset + circleDiameter - barWidth,
                    yOffset + circleDiameter - barWidth);
        } else {
            circleBounds = new RectF(paddingLeft + barWidth,
                    paddingTop + barWidth,
                    layout_width - paddingRight - barWidth,
                    layout_height - paddingBottom - barWidth);
        }
    }

    /**
     * Parse the attributes passed to the view from the XML
     *
     * @param a the attributes to parse
     */
    private void parseAttributes(TypedArray a) {
        // We transform the default values from DIP to pixels
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        barWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barWidth, metrics);
        rimWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, rimWidth, metrics);
        circleRadius = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, circleRadius, metrics);
        /*
         * My implementation
         */
        tickSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, tickSize, metrics);

        circleRadius = (int) a.getDimension(R.styleable.ProgressWheel_matProg_circleRadius, circleRadius);

        fillRadius = a.getBoolean(R.styleable.ProgressWheel_matProg_fillRadius, false);

        barWidth = (int) a.getDimension(R.styleable.ProgressWheel_matProg_barWidth, barWidth);

        rimWidth = (int) a.getDimension(R.styleable.ProgressWheel_matProg_rimWidth, rimWidth);

        /*
         * My implementation
         */
        tickSize = (int) a.getDimension(R.styleable.ProgressWheel_matProg_tickSize, tickSize);

        float baseSpinSpeed = a.getFloat(R.styleable.ProgressWheel_matProg_spinSpeed, spinSpeed / 360.0f);
        spinSpeed = baseSpinSpeed * 360;

        barSpinCycleTime = a.getInt(R.styleable.ProgressWheel_matProg_barSpinCycleTime, (int) barSpinCycleTime);

        barColor = a.getColor(R.styleable.ProgressWheel_matProg_barColor, barColor);

        rimColor = a.getColor(R.styleable.ProgressWheel_matProg_rimColor, rimColor);

        linearProgress = a.getBoolean(R.styleable.ProgressWheel_matProg_linearProgress, false);

        if (a.getBoolean(R.styleable.ProgressWheel_matProg_progressIndeterminate, false)) {
            spin();
        }

        // Recycle
        a.recycle();
    }

    public void setCallback(ProgressCallback progressCallback) {
        callback = progressCallback;

        if (!isSpinning) {
            runCallback();
        }
    }

    //----------------------------------
    //Animation stuff
    //----------------------------------


    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //变量记录做完这次onDraw之后是否需要继续invalidate。
        boolean mustInvalidate = false;

        /*
         * My implementation
         * to draw tick
         */
        if (isDrawFirst && !isDrawSecond) {
            //开始画第一条线，获取圆心坐标
            float centerX = circleBounds.centerX();
            float centerY = circleBounds.centerY();
            //绘制外面的圆圈
            canvas.drawArc(circleBounds, 360, 360, false, barPaint);
            //根据速度来绘制直线
            canvas.drawLine(centerX - tickSize / 2, centerY, centerX - tickSize / 2 + tickSize / 2 * lineProgress,
                    centerY + tickSize / 2 * lineProgress, barPaint);
            //当绘制完第一条直线，开始绘制第二条
            if (lineProgress >= 1.0f) {
                lineProgress = 0f;
                isDrawSecond = true;
            } else {
                lineProgress += lineSpeed;
            }
            mustInvalidate = true;
        } else if (isDrawFirst && isDrawSecond) {
            //开始画第二条线，获取圆心
            float centerX = circleBounds.centerX();
            float centerY = circleBounds.centerY();
            //首先要画出第一条直线和外面的圆圈
            canvas.drawArc(circleBounds, 360, 360, false, barPaint);
            canvas.drawLine(centerX - tickSize / 2, centerY, centerX, centerY + tickSize / 2, barPaint);

            canvas.drawLine(centerX, centerY + tickSize / 2, centerX + tickSize / 2 * lineProgress, centerY +
                    tickSize / 2 - tickSize * lineProgress, barPaint);
            //当第二条直线绘制完毕，停止invalidate
            if (lineProgress >= 1.0f) {
                return;
            } else {
                lineProgress += lineSpeed;
                if (lineProgress > 1)
                    lineProgress = 1f;
                mustInvalidate = true;
            }
        } else {
            //这里是作者源代码，未更改
            canvas.drawArc(circleBounds, 360, 360, false, rimPaint);

            //自由旋转模式
            if (isSpinning) {
                //Draw the spinning bar
                mustInvalidate = true;

                long deltaTime = (SystemClock.uptimeMillis() - lastTimeAnimated);
                float deltaNormalized = deltaTime * spinSpeed / 1000.0f;

                updateBarLength(deltaTime);

                mProgress += deltaNormalized;
                if (mProgress > 360) {
                    mProgress -= 360f;

                    // A full turn has been completed
                    // we run the callback with -1 in case we want to
                    // do something, like changing the color
                    runCallback(-1.0f);
                }

                /*
                 * 这里作者的源代码是注释的那句话，为什么我加上5是有原因的。
                 * 因为作者对外面圆圈也就是bar的长度是通过上次绘制时间与当前时间的差的计算来得到的。
                 * 读者可以去看onDraw函数最下面的一段注释，这里对页面的刷新进行了延迟，导致圆圈旋转的时候有点不流畅
                 * 在这里加上5之后，会让其看起来更流畅,不会越位
                 */
//                lastTimeAnimated = SystemClock.uptimeMillis();
                lastTimeAnimated = SystemClock.uptimeMillis() + 5;

                float from = mProgress - 90;
                float length = barLength + barExtraLength;

                if (isInEditMode()) {
                    from = 0;
                    length = 135;
                }

                canvas.drawArc(circleBounds, from, length, false, barPaint);
            } else {
                float oldProgress = mProgress;

                if (mProgress != mTargetProgress) {
                    //We smoothly increase the progress bar
                    mustInvalidate = true;

                    float deltaTime = (float) (SystemClock.uptimeMillis() - lastTimeAnimated) / 1000;
                    float deltaNormalized = deltaTime * spinSpeed;

                    mProgress = Math.min(mProgress + deltaNormalized, mTargetProgress);
                    lastTimeAnimated = SystemClock.uptimeMillis() + 15;
                }

                if (oldProgress != mProgress) {
                    runCallback();
                }

                float offset = 0.0f;
                float progress = mProgress;
                if (!linearProgress) {
                    float factor = 2.0f;
                    offset = (float) (1.0f - Math.pow(1.0f - mProgress / 360.0f, 2.0f * factor)) * 360.0f;
                    progress = (float) (1.0f - Math.pow(1.0f - mProgress / 360.0f, factor)) * 360.0f;
                }

                if (isInEditMode()) {
                    progress = 360;
                }

                /*
                 * 这里为什么offset要减掉就是是因为当offset为0.0的时候
                 * 并不是我们以为的十二点钟的那个位置，而是三点钟的位置，所以减掉90刚好是十二点钟的位置
                 */
                canvas.drawArc(circleBounds, offset - 90, progress, false, barPaint);
            }

        }
        /*
         * 原来该代码的作者是在onDraw函数中直接调用invalidate函数
         * 如果onDraw函数中代码执行的时间很短的话，会导致频繁的去掉用invalidate，界面频繁刷新，占用cpu，有可能还会导致大量的GC
         * 于是我的老板和我说可以使用handler来延缓invalidate，每次做完onDraw，延迟几毫秒去invalidate
         * 但是要控制好延迟的时间，如果太大的话，会导致画面卡顿，如果大于16ms一般会造成卡顿
         * 因为在画勾的时候，测试过，当延迟的时间为20ms的时候，并不是很卡顿，所以就选择了20ms，所以具体延迟时间还是看需求吧
         */
        if (mustInvalidate) {
            Message message = handler.obtainMessage();
            message.what = 1;
            int delayMills = 25;
            if (!isDrawFirst)
                delayMills = 15;
            handler.sendMessageDelayed(message, delayMills);
        }

    }

    /**
     * My implementation
     * 开始画勾，这个时候要停止圆圈的转动
     */
    public void beginDrawTick() {
        isDrawFirst = true;
        isDrawSecond = false;
        lineProgress = 0.0f;
        invalidate();
    }

    /**
     * My implementation
     */
    public void setLineSpeed(float speed) {
        if (speed < 0 || speed > 1) {
            throw new IllegalArgumentException("Bad Speed");
        }
        this.lineSpeed = speed;
    }

    /**
     * 当该控件重新变为可见的时候，需要重新开始旋转，所以要重新获取时间
     *
     * @param changedView
     * @param visibility
     */
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == VISIBLE) {
            lastTimeAnimated = SystemClock.uptimeMillis();
        }
    }

    private void updateBarLength(long deltaTimeInMilliSeconds) {
        if (pausedTimeWithoutGrowing >= pauseGrowingTime) {
            timeStartGrowing += deltaTimeInMilliSeconds;

            if (timeStartGrowing > barSpinCycleTime) {
                // We completed a size change cycle
                // (growing or shrinking)
                timeStartGrowing -= barSpinCycleTime;
                //if(barGrowingFromFront) {
                pausedTimeWithoutGrowing = 0;
                //}
                barGrowingFromFront = !barGrowingFromFront;
            }

            float distance = (float) Math.cos((timeStartGrowing / barSpinCycleTime + 1) * Math.PI) / 2 + 0.5f;
            float destLength = (barMaxLength - barLength);

            if (barGrowingFromFront) {
                barExtraLength = distance * destLength;
            } else {
                float newLength = destLength * (1 - distance);
                mProgress += (barExtraLength - newLength);
                barExtraLength = newLength;
            }
        } else {
            pausedTimeWithoutGrowing += deltaTimeInMilliSeconds;
        }
    }

    /**
     * Check if the wheel is currently spinning
     */

    public boolean isSpinning() {
        return isSpinning;
    }

    /**
     * Reset the count (in increment mode)
     */
    public void resetCount() {
        mProgress = 0.0f;
        mTargetProgress = 0.0f;
        invalidate();
    }

    /**
     * Turn off spin mode
     */
    public void stopSpinning() {
        isSpinning = false;
        mProgress = 0.0f;
        mTargetProgress = 0.0f;
        invalidate();
    }

    /**
     * Puts the view on spin mode
     * 如果从布局文件中读到的属性为true，则调用该方法，设置isSpinning参数，并刷新
     */
    public void spin() {
        lastTimeAnimated = SystemClock.uptimeMillis();
        isSpinning = true;
        invalidate();
    }

    //自由旋转模式的是调用该方法
    private void runCallback(float value) {
        if (callback != null) {
            callback.onProgressUpdate(value);
        }
    }

    //下面两种旋转的模式调用该方法
    private void runCallback() {
        if (callback != null) {
            float normalizedProgress = (float) Math.round(mProgress * 100 / 360.0f) / 100;
            callback.onProgressUpdate(normalizedProgress);
        }
    }

    /**
     * Set the progress to a specific value,
     * the bar will smoothly animate until that value
     *
     * @param progress the progress between 0 and 1
     */
    public void setProgress(float progress) {
        //如果处于自由滚动模式的话，就切换模式为另外一种，需要为其初始化一些值
        if (isSpinning) {
            mProgress = 0.0f;
            isSpinning = false;

            runCallback();
        }

        //对参数的处理
        if (progress > 1.0f) {
            progress -= 1.0f;
        } else if (progress < 0) {
            progress = 0;
        }

        //如果已经等于目标progress，就返回
        if (progress == mTargetProgress) {
            return;
        }

        // If we are currently in the right position
        // we set again the last time animated so the
        // animation starts smooth from here
        //如果开始的progress和目标progress已经一样，那么需要重新获取时间
        if (mProgress == mTargetProgress) {
            lastTimeAnimated = SystemClock.uptimeMillis();
        }

        //是否多余，因为前面已经对progress进行处理过了，不可能大于1
        mTargetProgress = Math.min(progress * 360.0f, 360.0f);

        invalidate();
    }

    /**
     * Set the progress to a specific value,
     * the bar will be set instantly to that value
     *
     * @param progress the progress between 0 and 1
     */
    public void setInstantProgress(float progress) {
        if (isSpinning) {
            mProgress = 0.0f;
            isSpinning = false;
        }

        if (progress > 1.0f) {
            progress -= 1.0f;
        } else if (progress < 0) {
            progress = 0;
        }

        if (progress == mTargetProgress) {
            return;
        }

        mTargetProgress = Math.min(progress * 360.0f, 360.0f);
        mProgress = mTargetProgress;
        lastTimeAnimated = SystemClock.uptimeMillis();
        invalidate();
    }

    // Great way to save a view's state http://stackoverflow.com/a/7089687/1991053
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        WheelSavedState ss = new WheelSavedState(superState);

        // We save everything that can be changed at runtime
        ss.mProgress = this.mProgress;
        ss.mTargetProgress = this.mTargetProgress;
        ss.isSpinning = this.isSpinning;
        ss.spinSpeed = this.spinSpeed;
        ss.barWidth = this.barWidth;
        ss.barColor = this.barColor;
        ss.rimWidth = this.rimWidth;
        ss.rimColor = this.rimColor;
        ss.circleRadius = this.circleRadius;
        ss.linearProgress = this.linearProgress;
        ss.fillRadius = this.fillRadius;

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof WheelSavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        WheelSavedState ss = (WheelSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        this.mProgress = ss.mProgress;
        this.mTargetProgress = ss.mTargetProgress;
        this.isSpinning = ss.isSpinning;
        this.spinSpeed = ss.spinSpeed;
        this.barWidth = ss.barWidth;
        this.barColor = ss.barColor;
        this.rimWidth = ss.rimWidth;
        this.rimColor = ss.rimColor;
        this.circleRadius = ss.circleRadius;
        this.linearProgress = ss.linearProgress;
        this.fillRadius = ss.fillRadius;

        this.lastTimeAnimated = SystemClock.uptimeMillis();
    }

    //----------------------------------
    //Getters + setters
    //----------------------------------

    /**
     * @return the current progress between 0.0 and 1.0,
     * if the wheel is indeterminate, then the result is -1
     */
    public float getProgress() {
        return isSpinning ? -1 : mProgress / 360.0f;
    }

    /**
     * Sets the determinate progress mode
     *
     * @param isLinear if the progress should increase linearly
     */
    public void setLinearProgress(boolean isLinear) {
        linearProgress = isLinear;
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the radius of the wheel in pixels
     */
    public int getCircleRadius() {
        return circleRadius;
    }

    /**
     * Sets the radius of the wheel
     *
     * @param circleRadius the expected radius, in pixels
     */
    public void setCircleRadius(int circleRadius) {
        this.circleRadius = circleRadius;
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the width of the spinning bar
     */
    public int getBarWidth() {
        return barWidth;
    }

    /**
     * Sets the width of the spinning bar
     *
     * @param barWidth the spinning bar width in pixels
     */
    public void setBarWidth(int barWidth) {
        this.barWidth = barWidth;
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the color of the spinning bar
     */
    public int getBarColor() {
        return barColor;
    }

    /**
     * Sets the color of the spinning bar
     *
     * @param barColor The spinning bar color
     */
    public void setBarColor(int barColor) {
        this.barColor = barColor;
        setupPaints();
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the color of the wheel's contour
     */
    public int getRimColor() {
        return rimColor;
    }

    /**
     * Sets the color of the wheel's contour
     *
     * @param rimColor the color for the wheel
     */
    public void setRimColor(int rimColor) {
        this.rimColor = rimColor;
        setupPaints();
        if (!isSpinning) {
            invalidate();
        }
    }

    /**
     * @return the base spinning speed, in full circle turns per second
     * (1.0 equals on full turn in one second), this value also is applied for
     * the smoothness when setting a progress
     */
    public float getSpinSpeed() {
        return spinSpeed / 360.0f;
    }

    /**
     * Sets the base spinning speed, in full circle turns per second
     * (1.0 equals on full turn in one second), this value also is applied for
     * the smoothness when setting a progress
     *
     * @param spinSpeed the desired base speed in full turns per second
     */
    public void setSpinSpeed(float spinSpeed) {
        this.spinSpeed = spinSpeed * 360.0f;
    }

    /**
     * @return the width of the wheel's contour in pixels
     */
    public int getRimWidth() {
        return rimWidth;
    }

    /**
     * Sets the width of the wheel's contour
     *
     * @param rimWidth the width in pixels
     */
    public void setRimWidth(int rimWidth) {
        this.rimWidth = rimWidth;
        if (!isSpinning) {
            invalidate();
        }
    }

    static class WheelSavedState extends BaseSavedState {
        float mProgress;
        float mTargetProgress;
        boolean isSpinning;
        float spinSpeed;
        int barWidth;
        int barColor;
        int rimWidth;
        int rimColor;
        int circleRadius;
        boolean linearProgress;
        boolean fillRadius;

        WheelSavedState(Parcelable superState) {
            super(superState);
        }

        private WheelSavedState(Parcel in) {
            super(in);
            this.mProgress = in.readFloat();
            this.mTargetProgress = in.readFloat();
            this.isSpinning = in.readByte() != 0;
            this.spinSpeed = in.readFloat();
            this.barWidth = in.readInt();
            this.barColor = in.readInt();
            this.rimWidth = in.readInt();
            this.rimColor = in.readInt();
            this.circleRadius = in.readInt();
            this.linearProgress = in.readByte() != 0;
            this.fillRadius = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(this.mProgress);
            out.writeFloat(this.mTargetProgress);
            out.writeByte((byte) (isSpinning ? 1 : 0));
            out.writeFloat(this.spinSpeed);
            out.writeInt(this.barWidth);
            out.writeInt(this.barColor);
            out.writeInt(this.rimWidth);
            out.writeInt(this.rimColor);
            out.writeInt(this.circleRadius);
            out.writeByte((byte) (linearProgress ? 1 : 0));
            out.writeByte((byte) (fillRadius ? 1 : 0));
        }

        //required field that makes Parcelables from a Parcel
        public static final Parcelable.Creator<WheelSavedState> CREATOR =
                new Parcelable.Creator<WheelSavedState>() {
                    public WheelSavedState createFromParcel(Parcel in) {
                        return new WheelSavedState(in);
                    }

                    public WheelSavedState[] newArray(int size) {
                        return new WheelSavedState[size];
                    }
                };
    }

    public interface ProgressCallback {
        /**
         * Method to call when the progress reaches a value
         * in order to avoid float precision issues, the progress
         * is rounded to a float with two decimals.
         * <p>
         * In indeterminate mode, the callback is called each time
         * the wheel completes an animation cycle, with, the progress value is -1.0f
         *
         * @param progress a double value between 0.00 and 1.00 both included
         */
        public void onProgressUpdate(float progress);
    }
}
