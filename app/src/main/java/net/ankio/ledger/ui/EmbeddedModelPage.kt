package net.ankio.ledger.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/** A partial page in the Activity window. IME insets drive the same surface, frame by frame. */
internal class EmbeddedModelPage(
    private val activity: MainActivity,
    private val page: View,
    private val origin: RectF?,
    private val originRadius: Float? = null,
    private val onDismiss: () -> Unit,
) {
    private val host=activity.pageHost
    private val stage=FrameLayout(activity)
    private val surface=View(activity).apply { background=GradientDrawable().apply { setColor(Color.WHITE) } }
    private val oldInputMode=activity.window.attributes.softInputMode
    private val oldAccessibility=activity.webView.importantForAccessibility
    private var animator:ValueAnimator?=null
    private var current=RectF()
    private var source=RectF()
    private var radius=0f
    private var closing=false
    val isClosing get()=closing
    private var removed=false
    private var imeAnimating=false
    private var imeAnimator:ValueAnimator?=null
    private var imeTarget=0
    private var imeFrames=0
    private var opened=false
    private var dim=0f
    private var contentAlpha=0f
    private val pageOutline=object:ViewOutlineProvider() {
        override fun getOutline(view:View,outline:Outline) {
            val left=(current.left-page.left).toInt().coerceIn(0,page.width)
            val top=(current.top-page.top).toInt().coerceIn(0,page.height)
            val right=(current.right-page.left).toInt().coerceIn(left,page.width)
            val bottom=(current.bottom-page.top).toInt().coerceIn(top,page.height)
            outline.setRoundRect(left,top,right,bottom,radius)
        }
    }
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()

    fun show() {
        // Keep the ledger at its original size; only the partial page yields space to the IME.
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        activity.webView.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        stage.isClickable=true
        stage.tag="native-morph-stage"
        stage.setBackgroundColor(Color.TRANSPARENT)
        surface.tag="native-morph-surface"
        stage.addView(surface,FrameLayout.LayoutParams(1,1))
        stage.addView(page,FrameLayout.LayoutParams(minOf(host.width,dp(560)),-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { topMargin=dp(24) })
        page.isClickable=true
        page.visibility=View.INVISIBLE
        page.clipToOutline=true
        page.outlineProvider=pageOutline
        stage.setOnClickListener { dismiss() }
        stage.setOnApplyWindowInsetsListener { _,insets ->
            if(!imeAnimating)applyIme(insets,true)
            insets
        }
        stage.setWindowInsetsAnimationCallback(object:WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onPrepare(animation:WindowInsetsAnimation) {
                if(animation.typeMask and WindowInsets.Type.ime()!=0) { imeAnimating=true;imeFrames=0;imeAnimator?.cancel() }
            }
            override fun onProgress(insets:WindowInsets,runningAnimations:MutableList<WindowInsetsAnimation>):WindowInsets {
                imeFrames++
                val terminal=runningAnimations.filter { it.typeMask and WindowInsets.Type.ime()!=0 }.all { it.interpolatedFraction>=.999f }
                applyIme(insets,imeFrames==1 && terminal);return insets
            }
            override fun onEnd(animation:WindowInsetsAnimation) {
                if(animation.typeMask and WindowInsets.Type.ime()!=0) {
                    imeAnimating=false;stage.rootWindowInsets?.let { applyIme(it,imeFrames<=1) }
                    page.findFocus()?.let { focused -> focused.post { focused.requestRectangleOnScreen(android.graphics.Rect(0,0,focused.width,focused.height),false) } }
                }
            }
        })
        host.addView(stage,FrameLayout.LayoutParams(-1,-1))
        stage.requestApplyInsets()
        stage.post {
            if(removed)return@post
            val location=IntArray(2);stage.getLocationOnScreen(location)
            source=origin?.let { RectF(it).apply { offset(-location[0].toFloat(),-location[1].toFloat()) } }
                ?: RectF(stage.width/2f-dp(80),stage.height-dp(80).toFloat(),stage.width/2f+dp(80),stage.height-dp(28).toFloat())
            current=RectF(source)
            radius=originRadius?.coerceIn(0f,minOf(source.width(),source.height())/2f) ?: minOf(source.width(),source.height())/2f
            opened=true
            animate(false)
        }
    }

    private fun applyIme(insets:WindowInsets,fallbackAnimation:Boolean=false) {
        val bottom=(insets.getInsets(WindowInsets.Type.ime()).bottom-host.paddingBottom).coerceAtLeast(0)
        if(bottom==imeTarget && (imeAnimator?.isRunning==true || stage.paddingBottom==bottom))return
        imeTarget=bottom;imeAnimator?.cancel()
        // Some keyboards/ROMs send only the final inset, without animation frames.
        // Keep the page motion continuous in that case too.
        if(opened && fallbackAnimation && ValueAnimator.areAnimatorsEnabled()) {
            imeAnimator=ValueAnimator.ofInt(stage.paddingBottom,bottom).apply {
                duration=240;interpolator=PathInterpolator(.16f,1f,.3f,1f)
                addUpdateListener { stage.setPadding(0,0,0,it.animatedValue as Int) }
                start()
            }
        } else if(stage.paddingBottom!=bottom)stage.setPadding(0,0,0,bottom)
    }

    fun back() {
        if(closing)return
        if(stage.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true) {
            activity.window.insetsController?.hide(WindowInsets.Type.ime())
        } else dismiss()
    }

    fun dismiss(animate:Boolean=true) {
        if(removed)return
        if(closing) { if(!animate)finish();return }
        closing=true
        activity.window.insetsController?.hide(WindowInsets.Type.ime())
        // A rapid back press can arrive before the first measured frame. There is
        // no valid destination/source geometry yet, so remove this stage directly.
        if(!opened) { finish();return }
        if(animate && stage.isAttachedToWindow && ValueAnimator.areAnimatorsEnabled())animate(true) else finish()
    }

    private fun animate(reverse:Boolean) {
        animator?.cancel()
        val start=if(reverse && surface.visibility==View.GONE)RectF(page.left.toFloat(),page.top.toFloat(),page.right.toFloat(),page.bottom.toFloat()) else RectF(current)
        val end=if(reverse)source else RectF(page.left.toFloat(),page.top.toFloat(),page.right.toFloat(),page.bottom.toFloat())
        val fromRadius=if(reverse && surface.visibility==View.GONE)dp(30).toFloat() else radius
        val toRadius=if(reverse)originRadius?.coerceIn(0f,minOf(source.width(),source.height())/2f) ?: minOf(source.width(),source.height())/2f else dp(30).toFloat()
        val startDim=dim
        val startAlpha=contentAlpha
        if(!ValueAnimator.areAnimatorsEnabled()) {
            current=RectF(end);radius=toRadius;dim=1f;contentAlpha=1f
            stage.setBackgroundColor(0x381B2633)
            page.visibility=View.VISIBLE;page.alpha=1f;page.clipBounds=null;page.clipToOutline=false
            surface.visibility=View.GONE
            if(reverse)finish()
            return
        }
        surface.visibility=View.VISIBLE;page.visibility=View.VISIBLE
        page.clipToOutline=true
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=if(reverse)280 else 420
            interpolator=PathInterpolator(.22f,1f,.36f,1f)
            addUpdateListener {
                val p=it.animatedValue as Float
                current=RectF(start.left+(end.left-start.left)*p,start.top+(end.top-start.top)*p,start.right+(end.right-start.right)*p,start.bottom+(end.bottom-start.bottom)*p)
                radius=fromRadius+(toRadius-fromRadius)*p
                surface.layoutParams=(surface.layoutParams as FrameLayout.LayoutParams).apply { width=current.width().toInt().coerceAtLeast(1);height=current.height().toInt().coerceAtLeast(1) }
                surface.x=current.left;surface.y=current.top
                val lower=if(reverse)radius*p else radius*(1-p)
                (surface.background as GradientDrawable).cornerRadii=floatArrayOf(radius,radius,radius,radius,lower,lower,lower,lower)
                dim=startDim+((if(reverse)0f else 1f)-startDim)*p
                stage.setBackgroundColor(Color.argb((56*dim).toInt(),27,38,51))
                contentAlpha=if(reverse)startAlpha*(1-p) else startAlpha+(1-startAlpha)*((p-.08f)/.65f).coerceIn(0f,1f)
                page.alpha=contentAlpha
                // Reveal the real, unscaled content inside the changing surface. Text
                // remains crisp instead of stretching with a full-panel scale animation.
                val left=(current.left-page.left).toInt().coerceIn(0,page.width)
                val top=(current.top-page.top).toInt().coerceIn(0,page.height)
                val right=(current.right-page.left).toInt().coerceIn(left,page.width)
                val bottom=(current.bottom-page.top).toInt().coerceIn(top,page.height)
                page.clipBounds=Rect(left,top,right,bottom)
                page.invalidateOutline()
            }
            addListener(object:AnimatorListenerAdapter() {
                private var cancelled=false
                override fun onAnimationCancel(animation:Animator) { cancelled=true }
                override fun onAnimationEnd(animation:Animator) {
                    if(!cancelled) {
                        // Closing must not restore the full opaque page before removing
                        // the stage. Keep the final clipped/faded frame through teardown.
                        if(reverse) { finish();return }
                        surface.visibility=View.GONE;page.alpha=1f;page.clipBounds=null;page.clipToOutline=false
                    }
                }
            })
            start()
        }
    }

    private fun finish() {
        if(removed)return
        removed=true;animator?.cancel()
        imeAnimator?.cancel()
        page.clearFocus()
        stage.setWindowInsetsAnimationCallback(null)
        host.removeView(stage)
        activity.webView.importantForAccessibility=oldAccessibility
        activity.window.setSoftInputMode(oldInputMode)
        if(activity.modelPage===this)activity.modelPage=null
        host.requestApplyInsets()
        onDismiss()
    }
}
