package app.grapheneos.gmscompat

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.view.BulletPointView
import kotlin.math.roundToInt

internal data class GlifPage(
    @param:StringRes @field:StringRes val header: Int,
    @param:StringRes @field:StringRes val description: Int,
    val sections: List<GlifPageSection>,
)

internal data class GlifPageSection(
    @param:StringRes @field:StringRes val title: Int,
    val bullets: List<GlifPageBullet>,
)

internal data class GlifPageBullet(
    @param:StringRes @field:StringRes val title: Int,
    val summary: CharSequence,
    @param:DrawableRes @field:DrawableRes val icon: Int,
)

internal fun renderGlifPage(context: Context, page: GlifPage): GlifLayout {
    val layout =
        GlifLayout(context).apply {
            setHeaderText(page.header)
            setDescriptionText(page.description)
            filterTouchesWhenObscured = true
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
        }

    // Apply the same client styles as SetupDesign's XML examples.
    val content =
        LinearLayout(context, null, 0, com.google.android.setupdesign.R.style.SudContentFrame)
            .apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }

    page.sections.forEachIndexed { sectionIndex, section ->
        content.addView(
            TextView(context, null, com.google.android.setupdesign.R.attr.sudSectionItemTitleStyle)
                .apply {
                    setText(section.title)
                    layoutParams =
                        LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            .apply {
                                if (sectionIndex != 0) {
                                    topMargin = context.dpToPx(24)
                                }
                                bottomMargin = context.dpToPx(8)
                            }
                }
        )

        section.bullets.forEach { bullet ->
            content.addView(
                BulletPointView(context).apply {
                    setTitle(context.getText(bullet.title))
                    setSummary(bullet.summary)
                    setIcon(context.getDrawable(bullet.icon))
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                }
            )
        }
    }

    layout.addView(content)
    return layout
}

private fun Context.dpToPx(value: Int): Int =
    TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        )
        .roundToInt()
