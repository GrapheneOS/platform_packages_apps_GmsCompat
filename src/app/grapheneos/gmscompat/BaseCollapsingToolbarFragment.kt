package app.grapheneos.gmscompat

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseFragment

abstract class BaseCollapsingToolbarFragment : CollapsingToolbarBaseFragment() {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val actionBar = requireActivity().getActionBar()
        actionBar?.setDisplayHomeAsUpEnabled(true)

        var preferenceFragment = getChildFragmentManager()
            .findFragmentById(com.android.settingslib.collapsingtoolbar.R.id.content_frame)
                as PreferenceFragmentCompat?

        if (preferenceFragment == null) {
            preferenceFragment = createPreferenceFragment()
            preferenceFragment.setArguments(arguments)
            getChildFragmentManager().beginTransaction()
                .add(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    preferenceFragment
                )
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        collapsingToolbarLayout?.let { layout ->
            val controller = findNavController()
            controller.currentDestination?.let { destination ->
                val args = controller.currentBackStackEntry?.arguments ?: Bundle.EMPTY
                val label: String? = destination.fillInLabel(layout.context, args)
                label?.let {
                    layout.title = it
                    activity?.title = it
                }
            }
        }
    }

    /**
     * @return a new instance of a customized PermissionsFrameFragment.
     */
    abstract fun createPreferenceFragment(): PreferenceFragmentCompat

    override fun useCollapsingToolbar() = true
}
