package app.grapheneos.gmscompat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.setupcompat.template.FooterBarMixin
import com.google.android.setupdesign.GlifLayout

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
