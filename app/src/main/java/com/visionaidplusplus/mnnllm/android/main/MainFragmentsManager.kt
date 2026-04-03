package com.visionaidplusplus.mnnllm.android.main

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.visionaidplusplus.mnnllm.android.capture.CaptureFragment
import com.visionaidplusplus.mnnllm.android.modelmarket.ModelMarketFragment
import com.visionaidplusplus.mnnllm.android.utils.Searchable
import com.visionaidplusplus.mnnllm.android.widgets.BottomTabBar

/**
 * Manages the main fragments in MainActivity, including creation, transactions,
 * and state restoration.
 *
 * @param activity The host activity.
 * @param containerId The ID of the container where fragments will be placed.
 * @param bottomNav The BottomTabBar view to control fragment switching.
 * @param listener A listener to communicate UI update events back to the activity.
 */
class MainFragmentManager(
    private val activity: AppCompatActivity,
    private val containerId: Int,
    private val bottomNav: BottomTabBar,
    private val listener: FragmentLifecycleListener
) {
    private var captureFragment: CaptureFragment? = null
    private var modelMarketFragment: ModelMarketFragment? = null
    var activeFragment: Fragment? = null

    /** * An interface for the manager to communicate important events back to the hosting Activity. * This lets Activity can respond to Fragment changes to updateits own UI (e.g., Toolbar title).*/
    interface FragmentLifecycleListener {
        fun onTabChanged(newTab: BottomTabBar.Tab)
    }

    /**
     * Initializes the fragments. Call this in Activity's onCreate.
     * This method handles both initial creation and restoration from a saved state.
     */
    fun initialize(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            captureFragment = CaptureFragment()
            modelMarketFragment = ModelMarketFragment()

            activity.supportFragmentManager.beginTransaction()
                .add(containerId, modelMarketFragment!!, TAG_MARKET).hide(modelMarketFragment!!)
                .add(containerId, captureFragment!!, TAG_CAPTURE) //Default display
                .commit()

            activeFragment = captureFragment
        } else {
            captureFragment = activity.supportFragmentManager.findFragmentByTag(TAG_CAPTURE) as? CaptureFragment
            modelMarketFragment = activity.supportFragmentManager.findFragmentByTag(TAG_MARKET) as? ModelMarketFragment

            val activeTag = savedInstanceState.getString(SAVED_STATE_ACTIVE_TAG)
            if (activeTag != null) {
                activeFragment = activity.supportFragmentManager.findFragmentByTag(activeTag)
            } else {
                activeFragment = listOfNotNull(captureFragment, modelMarketFragment)
                    .find { !it.isHidden }
            }
        }

        setupTabListener()
        val initialTab = getTabForFragment(activeFragment)
        bottomNav.select(initialTab)
        listener.onTabChanged(initialTab)
    }

    /**
     * Saves the current active fragment's tag to the bundle. Call this in Activity's onSaveInstanceState.
     */
    fun onSaveInstanceState(outState: Bundle) {
        if (activeFragment?.tag != null) {
            outState.putString(SAVED_STATE_ACTIVE_TAG, activeFragment!!.tag)
        }
    }

    /**
     * Returns the currently active fragment, which might implement the Searchable interface.
     */
    fun getActiveSearchableFragment(): Searchable? {
        return activeFragment as? Searchable
    }

    private fun setupTabListener() {
        bottomNav.setOnTabSelectedListener { tab ->
            Log.d(TAG, "Tab selected: $tab")

            val targetFragment = when (tab) {
                BottomTabBar.Tab.LOCAL_MODELS -> captureFragment
                BottomTabBar.Tab.MODEL_MARKET -> modelMarketFragment
            }

            if (targetFragment != null && activeFragment != targetFragment) {
                switchFragment(targetFragment)
                listener.onTabChanged(tab) //Notify Activity
            }
        }
    }

    private fun switchFragment(targetFragment: Fragment) {
        activity.supportFragmentManager.beginTransaction().apply {
            if (activeFragment != null) {
                hide(activeFragment!!)
            }
            show(targetFragment)
            commitNow()
        }
        activeFragment = targetFragment
    }

    private fun getTabForFragment(fragment: Fragment?): BottomTabBar.Tab {
        return when (fragment) {
            is ModelMarketFragment -> BottomTabBar.Tab.MODEL_MARKET
            else -> BottomTabBar.Tab.LOCAL_MODELS
        }
    }

    companion object {
        private const val TAG = "MainFragmentManager"
        private const val TAG_CAPTURE = "capture"
        private const val TAG_MARKET = "market"
        private const val SAVED_STATE_ACTIVE_TAG = "active_fragment_tag"
    }
}