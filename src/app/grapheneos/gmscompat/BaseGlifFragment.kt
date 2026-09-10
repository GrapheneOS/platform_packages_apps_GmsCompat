package app.grapheneos.gmscompat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SettingsPreferenceGroupAdapter
import com.android.settingslib.widget.SettingsThemeHelper
import com.google.android.setupcompat.template.FooterBarMixin
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.GlifPreferenceLayout

abstract class BaseGlifFragment : Fragment() {

    protected abstract fun createGlifLayout(context: Context): GlifLayout

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = createGlifLayout(inflater.context)

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layout = view as GlifLayout
        onGlifViewCreated(layout, savedInstanceState)

        // The Settings app's BiometricEnrollBase makes its footer opaque with the screen
        // background.
        val footerBackgroundColor = layout.footerBackgroundColor
        val footerBar = layout.getMixin(FooterBarMixin::class.java)
        footerBar.buttonContainer?.setBackgroundColor(footerBackgroundColor)
    }

    protected open fun onGlifViewCreated(layout: GlifLayout, savedInstanceState: Bundle?) {}
}

abstract class BaseGlifPreferenceFragment : SettingsBasePreferenceFragment() {

    final override fun onCreateAdapter(
        preferenceScreen: PreferenceScreen,
    ): RecyclerView.Adapter<*> =
        if (SettingsThemeHelper.isExpressiveTheme(requireContext())) {
            SetupFlowPreferenceAdapter(preferenceScreen)
        } else {
            super.onCreateAdapter(preferenceScreen)
        }

    final override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?,
    ): RecyclerView {
        val layout = parent as? GlifPreferenceLayout
            ?: return super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        return layout.onCreateRecyclerView(inflater, parent, savedInstanceState)
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layout = view as GlifPreferenceLayout
        onGlifPreferenceViewCreated(layout, savedInstanceState)

        // The Settings app's BiometricEnrollBase makes its footer opaque with the screen
        // background.
        val footerBackgroundColor = layout.footerBackgroundColor
        val footerBar = layout.getMixin(FooterBarMixin::class.java)
        footerBar.buttonContainer?.setBackgroundColor(footerBackgroundColor)
    }

    protected abstract fun onGlifPreferenceViewCreated(
        layout: GlifPreferenceLayout,
        savedInstanceState: Bundle?,
    )
}

// Match Settings' PreferenceAdapterInSuw, which corrects the extra horizontal padding produced by
// combining SetupDesign preference margins with SettingsLib's expressive preference backgrounds.
private class SetupFlowPreferenceAdapter(
    preferenceScreen: PreferenceScreen,
) : SettingsPreferenceGroupAdapter(preferenceScreen) {
    private val itemPaddingStart: Int
    private val itemPaddingEnd: Int
    private val contentPadding: Int

    init {
        val context = preferenceScreen.context
        val attributes = context.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.listPreferredItemPaddingStart,
                android.R.attr.listPreferredItemPaddingEnd,
            ),
        )
        itemPaddingStart = attributes.getDimensionPixelSize(0, 0)
        itemPaddingEnd = attributes.getDimensionPixelSize(1, 0)
        attributes.recycle()
        contentPadding = context.resources.getDimensionPixelSize(
            com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_space_small1
        )
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)

        val view = holder.itemView
        val hasRoundedBackground = getRoundCornerDrawableRes(position, false) != 0
        view.setPaddingRelative(
            itemPaddingStart + if (hasRoundedBackground) contentPadding else 0,
            view.paddingTop,
            itemPaddingEnd + if (hasRoundedBackground) contentPadding else 0,
            view.paddingBottom,
        )
    }
}
