package app.grapheneos.gmscompat.configui

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.android.settingslib.widget.FooterPreference

fun PreferenceFragmentCompat.createFooterPreference(): FooterPreference {
    return FooterPreference(requireContext()).apply {
        isSelectable = false
    }
}

fun PreferenceGroup.addOrRemove(p: Preference,  add: Boolean) {
    val added = p.parent != null
    if (add == added) {
        return
    }
    if (add) {
        addPreference(p)
    } else {
        removePreference(p)
    }
}

fun PreferenceFragmentCompat.createCategory(title: Int): PreferenceCategory {
    return PreferenceCategory(requireContext()).apply {
        setTitle(title)
        key = Integer.toString(title)
    }
}

fun createPref(ctx: Context, title: CharSequence, @DrawableRes icon: Int,
                                        l: Preference.OnPreferenceClickListener?): Preference {
    val p = Preference(ctx)
    p.title = title
    if (icon != 0) {
        p.setIcon(icon)
    }
    if (l != null) {
        p.onPreferenceClickListener = l
    }
    return p
}

fun PreferenceFragmentCompat.addPref(title: CharSequence, icon: Int = 0, l: Preference.OnPreferenceClickListener? = null): Preference {
    return preferenceScreen.addPref(title, icon, l)
}

fun PreferenceGroup.addPref(title: CharSequence, icon: Int = 0, l: Preference.OnPreferenceClickListener? = null): Preference {
    val p = createPref(this.context, title, icon, l)
    addPreference(p)
    return p
}

fun PreferenceGroup.addCategory(@StringRes title: Int) = addCategory(context.getText(title))

fun PreferenceGroup.addCategory(title: CharSequence): PreferenceCategory {
    return PreferenceCategory(this.context).apply {
        this.title = title
        this@addCategory.addPreference(this)
    }
}

fun PreferenceFragmentCompat.addOrRemove(p: Preference, add: Boolean) {
    preferenceScreen.addOrRemove(p, add)
}

fun addMenuItem(@StringRes title: Int, menu: Menu): MenuItem {
    return menu.add(Menu.NONE, title, Menu.NONE, title)
}
