package eu.kanade.tachiyomi.ui.base

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor

@SuppressLint("CustomViewStyleable")
class CenteredToolbar@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialToolbar(context, attrs) {

    private lateinit var toolbarTitle: TextView
    private val defStyleRes = com.google.android.material.R.style.Widget_MaterialComponents_Toolbar

    private val titleTextAppeance: Int

    var incognito = false
    var hasDropdown: Boolean? = null
    init {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.Toolbar,
            0,
            defStyleRes
        )
        titleTextAppeance = a.getResourceId(R.styleable.Toolbar_titleTextAppearance, 0)
        a.recycle()
    }
    override fun onFinishInflate() {
        super.onFinishInflate()
        toolbarTitle = findViewById<MaterialTextView>(R.id.toolbar_title)
        toolbarTitle.setTextAppearance(titleTextAppeance)
        toolbarTitle.setTextColor(context.getResourceColor(R.attr.actionBarTintColor))
    }

    override fun setTitle(resId: Int) {
        setCustomTitle(context.getString(resId))
    }

    override fun setTitle(title: CharSequence?) {
        setCustomTitle(title)
    }

    private fun setCustomTitle(title: CharSequence?) {
        toolbarTitle.isVisible = true
        toolbarTitle.text = title
        super.setTitle(null)
        toolbarTitle.updateLayoutParams<LayoutParams> {
            gravity = if (navigationIcon is DrawerArrowDrawable) Gravity.START else Gravity.CENTER
        }
        toolbarTitle.compoundDrawablePadding = if (navigationIcon is DrawerArrowDrawable) 6.dpToPx else 0
        if (navigationIcon is DrawerArrowDrawable) {
            hideDropdown()
        }
        setIncognitoMode(incognito)
    }

    fun hideDropdown() {
        hasDropdown = null
        setIcons()
    }

    fun showDropdown(down: Boolean = true) {
        hasDropdown = down
        setIcons()
    }

    fun setIncognitoMode(enabled: Boolean) {
        incognito = enabled
        setIcons()
    }

    private fun setIcons() {
        toolbarTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
            getIncogRes(),
            0,
            getDropdownRes(),
            0
        )
    }

    @DrawableRes
    private fun getIncogRes(): Int {
        return when {
            incognito -> R.drawable.ic_incognito_circle_24dp
            hasDropdown != null -> R.drawable.ic_blank_24dp
            else -> 0
        }
    }

    @DrawableRes
    private fun getDropdownRes(): Int {
        return when {
            hasDropdown == true -> R.drawable.ic_arrow_drop_down_24dp
            hasDropdown == false -> R.drawable.ic_arrow_drop_up_24dp
            incognito && navigationIcon !is DrawerArrowDrawable -> R.drawable.ic_blank_28dp
            else -> 0
        }
    }
}