package io.github.diegog0477.zombiebox.cast.core.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.github.diegog0477.zombiebox.cast.R

@Suppress("DEPRECATION")
class PhoneWidgets(val context: Context) {
    val accent = context.resources.getColor(R.color.zombie_green)
    val surface = context.resources.getColor(R.color.cast_surface)
    val background = context.resources.getColor(R.color.cast_background)
    val foreground = context.resources.getColor(R.color.cast_text)
    val muted = context.resources.getColor(R.color.cast_muted)

    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun row() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }

    fun shape(color: Int, border: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(16).toFloat()
            if (border != 0) setStroke(dp(2), border)
        }

    fun label(text: String, size: Float = 16f, color: Int = foreground) =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(0, dp(6), 0, dp(6))
        }

    fun label(id: Int, size: Float = 16f, color: Int = foreground) =
        label(context.getString(id), size, color)

    fun action(text: String, selected: Boolean = false, click: () -> Unit) =
        Button(context).apply {
            this.text = text
            isSelected = selected
            isAllCaps = false
            textSize = 15f
            setTextColor(
                if (selected) this@PhoneWidgets.background else this@PhoneWidgets.foreground
            )
            minHeight = dp(48)
            minimumHeight = dp(48)
            minimumWidth = 0
            minWidth = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            this.background = buttonBackground(selected)
            setOnClickListener { click() }
        }

    fun buttonBackground(selected: Boolean) =
        StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                shape(if (selected) accent else surface, accent),
            )
            addState(intArrayOf(android.R.attr.state_pressed), shape(accent))
            addState(intArrayOf(), shape(if (selected) accent else surface))
        }

    fun navigation(text: String, selected: Boolean, icon: Int, click: () -> Unit) =
        action(text, click = click).apply {
            textSize = 12f
            isSelected = selected
            val drawable = context.getDrawable(icon)!!.mutate()
            drawable.setTint(if (selected) accent else muted)
            drawable.setBounds(0, 0, dp(24), dp(24))
            setCompoundDrawables(null, drawable, null, null)
            compoundDrawablePadding = dp(4)
            setTextColor(if (selected) accent else muted)
            background =
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), shape(surface, accent))
                    addState(intArrayOf(android.R.attr.state_pressed), shape(surface))
                    addState(intArrayOf(), shape(this@PhoneWidgets.background))
                }
        }

    fun iconAction(icon: Int, label: Int, click: () -> Unit) =
        ImageButton(context).apply {
            setImageDrawable(
                context.getDrawable(icon)?.mutate()?.apply { setTint(this@PhoneWidgets.foreground) }
            )
            contentDescription = context.getString(label)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background =
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), shape(surface, accent))
                    addState(intArrayOf(android.R.attr.state_pressed), shape(surface))
                    addState(intArrayOf(), shape(this@PhoneWidgets.background))
                }
            setOnClickListener { click() }
        }

    fun provider(text: String, color: Int, click: () -> Unit) =
        action(text, click = click).apply {
            setTextColor(color)
            background =
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), shape(surface, color))
                    addState(intArrayOf(android.R.attr.state_pressed), shape(surface, color))
                    addState(intArrayOf(), shape(surface))
                }
        }

    fun device(name: String, selected: Boolean, click: () -> Unit) =
        action(
                if (selected) context.getString(R.string.device_selected, name) else name,
                click = click,
            )
            .apply {
                background =
                    StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), shape(surface, accent))
                        addState(intArrayOf(android.R.attr.state_pressed), shape(surface, accent))
                        addState(intArrayOf(), shape(surface, if (selected) accent else 0))
                    }
            }

    fun card() =
        column().apply {
            background = shape(surface)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        }
}
