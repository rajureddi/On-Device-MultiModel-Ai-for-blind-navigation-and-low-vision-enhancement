// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.visionaidplusplus.mnnllm.android.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.visionaidplusplus.mls.api.download.ModelDownloadManager
import com.visionaidplusplus.mls.api.source.ModelSources
import com.visionaidplusplus.mnnllm.android.R
import com.visionaidplusplus.mnnllm.android.chat.ChatRouter
import com.visionaidplusplus.mnnllm.android.chat.SelectSourceFragment
import com.visionaidplusplus.mnnllm.android.mainsettings.MainSettings
import com.visionaidplusplus.mnnllm.android.mainsettings.MainSettingsActivity
import com.visionaidplusplus.mnnllm.android.modelmarket.ModelMarketFragment
import com.visionaidplusplus.mnnllm.android.modelmarket.CommunityDownloadDialogFragment
import com.visionaidplusplus.mnnllm.android.capture.PromptSettingsDialogFragment
import com.visionaidplusplus.mnnllm.android.privacy.PrivacyPolicyDialogFragment
import com.visionaidplusplus.mnnllm.android.privacy.PrivacyPolicyManager
import com.visionaidplusplus.mnnllm.android.update.UpdateChecker
import com.visionaidplusplus.mnnllm.android.utils.CrashUtil
import com.visionaidplusplus.mnnllm.android.utils.GithubUtils
import com.visionaidplusplus.mnnllm.android.utils.RouterUtils.startActivity
import com.visionaidplusplus.mnnllm.android.utils.Searchable
import com.visionaidplusplus.mnnllm.android.widgets.BottomTabBar
import com.visionaidplusplus.mnnllm.android.widgets.ModelSwitcherView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity(), MainFragmentManager.FragmentLifecycleListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var materialToolbar: MaterialToolbar
    private lateinit var mainTitleSwitcher: ModelSwitcherView
    private var toolbarHeightPx: Int = 0
    private var offsetChangedListener: AppBarLayout.OnOffsetChangedListener? = null
    private var updateChecker: UpdateChecker? = null
    private var currentSearchView: SearchView? = null

    private lateinit var bottomNav: BottomTabBar
    public lateinit var mainFragmentManager: MainFragmentManager

    private val currentFragment: Fragment?
        get() {
            return mainFragmentManager.activeFragment
        }

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_main, menu)
            setupSearchView(menu)
            setupOtherMenuItems(menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.action_community_download) {
                CommunityDownloadDialogFragment.newInstance().show(supportFragmentManager, CommunityDownloadDialogFragment.TAG)
                return true
            }
            return true
        }

        override fun onPrepareMenu(menu: Menu) {
            Log.d(TAG, "onPrepareMenu")
            super.onPrepareMenu(menu)

            val isHomeScreen = bottomNav.getSelectedTab() == BottomTabBar.Tab.LOCAL_MODELS

            val searchItem = menu.findItem(R.id.action_search)
            val communityDownloadItem = menu.findItem(R.id.action_community_download)
            val reportCrashMenu = menu.findItem(R.id.action_report_crash)
            val settingsMenu = menu.findItem(R.id.action_report_crash)
            val issueMenu = menu.findItem(R.id.action_github_issue)
            val starGithub = menu.findItem(R.id.action_star_project)

            reportCrashMenu.isVisible = if (isHomeScreen) false else CrashUtil.hasCrash()
            searchItem.isVisible = if (isHomeScreen) false else true
            communityDownloadItem?.isVisible = !isHomeScreen
            settingsMenu.isVisible = !isHomeScreen
            issueMenu.isVisible = !isHomeScreen
            starGithub.isVisible = !isHomeScreen
        }
    }

    private fun setupSearchView(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView?
        if (searchView != null) {
            currentSearchView = searchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    handleSearch(query)
                    return false
                }

                override fun onQueryTextChange(query: String): Boolean {
                    handleSearch(query)
                    return true
                }
            })
            searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    Log.d(TAG, "SearchView expanded")
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    Log.d(TAG, "SearchView collapsed")
                    handleSearchCleared()
                    return true
                }
            })
        }
    }

    private fun setupOtherMenuItems(menu: Menu) {
        val issueMenu = menu.findItem(R.id.action_github_issue)
        issueMenu.setOnMenuItemClickListener {
            onReportIssue(null)
            true
        }

        val settingsMenu = menu.findItem(R.id.action_report_crash)
        settingsMenu.setOnMenuItemClickListener {
            startActivity(this@MainActivity, MainSettingsActivity::class.java)
            true
        }

        val starGithub = menu.findItem(R.id.action_star_project)
        starGithub.setOnMenuItemClickListener {
            onStarProject(null)
            true
        }

        val reportCrashMenu = menu.findItem(R.id.action_report_crash)
        reportCrashMenu.setOnMenuItemClickListener {
            if (CrashUtil.hasCrash()) {
                CrashUtil.shareLatestCrash(this@MainActivity)
            }
            true
        }
    }

    private fun handleSearch(query: String) {
        val searchableFragment = currentFragment as? Searchable
        searchableFragment?.onSearchQuery(query)
    }

    private fun handleSearchCleared() {
        val searchableFragment = currentFragment as? Searchable
        searchableFragment?.onSearchCleared()
    }

    fun setSearchQuery(query: String) {
        if (query.isEmpty()) return

        val menu = materialToolbar.menu
        val searchItem = menu?.findItem(R.id.action_search)

        if (searchItem != null && searchItem.isVisible) {
            try {
                searchItem.expandActionView()
                currentSearchView?.let { searchView ->
                    searchView.setQuery(query, false)
                    searchView.clearFocus()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set search query: $query", e)
            }
        }
    }

    fun getCurrentSearchQuery(): String {
        return currentSearchView?.query?.toString() ?: ""
    }

    fun clearSearch() {
        val menu = materialToolbar.menu
        val searchItem = menu?.findItem(R.id.action_search)
        searchItem?.collapseActionView()
    }

    private fun setupAppBar() {
        appBarLayout = findViewById(R.id.app_bar)
        materialToolbar = findViewById(R.id.toolbar)
        mainTitleSwitcher = findViewById(R.id.main_title_switcher)

        updateMainTitleSwitcherMode(false)

        toolbarHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            48f,
            resources.displayMetrics
        ).toInt()

        materialToolbar.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                materialToolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val measuredHeight = materialToolbar.height
                if (measuredHeight > 0) {
                    toolbarHeightPx = measuredHeight
                }
            }
        })

        offsetChangedListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (toolbarHeightPx <= 0) {
                val currentToolbarHeight = materialToolbar.height
                if (currentToolbarHeight > 0) {
                    toolbarHeightPx = currentToolbarHeight
                } else {
                    toolbarHeightPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt()
                    if (toolbarHeightPx == 0) return@OnOffsetChangedListener
                }
            }
            val absVerticalOffset = Math.abs(verticalOffset)
            var alpha = 1.0f - (absVerticalOffset.toFloat() / toolbarHeightPx.toFloat())
            alpha = alpha.coerceIn(0.0f, 1.0f)
            materialToolbar.alpha = alpha
        }
    }

    private fun updateMainTitleSwitcherMode(isSourceSwitcherMode: Boolean) {
        val dropdownArrow = mainTitleSwitcher.findViewById<View>(R.id.iv_dropdown_arrow)
        if (isSourceSwitcherMode) {
            dropdownArrow?.visibility = View.VISIBLE
            mainTitleSwitcher.isClickable = true
            mainTitleSwitcher.isFocusable = true
            mainTitleSwitcher.setOnClickListener {
                showSourceSelectionDialog()
            }
        } else {
            dropdownArrow?.visibility = View.GONE
            mainTitleSwitcher.isClickable = false
            mainTitleSwitcher.isFocusable = false
            mainTitleSwitcher.setOnClickListener(null)
        }
    }

    private fun showSourceSelectionDialog() {
        val availableSources = ModelSources.sourceList
        val displayNames = ModelSources.sourceDisPlayList
        val currentProvider = MainSettings.getDownloadProviderString(this)

        val fragment = SelectSourceFragment.newInstance(availableSources, displayNames, currentProvider)
        fragment.setOnSourceSelectedListener { selectedSource ->
            MainSettings.setDownloadProvider(this, selectedSource)
            val idx = ModelSources.sourceList.indexOf(selectedSource)
            val displayName = if (idx != -1) getString(ModelSources.sourceDisPlayList[idx]) else selectedSource
            mainTitleSwitcher.text = displayName
            if (currentFragment is ModelMarketFragment) {
                (currentFragment as ModelMarketFragment).onSourceChanged()
            }
        }
        fragment.show(supportFragmentManager, "SourceSelectionDialog")
    }

    fun refreshModelMarket() {
        if (currentFragment is ModelMarketFragment) {
            (currentFragment as ModelMarketFragment).onSourceChanged()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mainFragmentManager.onSaveInstanceState(outState)
    }

    override fun onTabChanged(newTab: BottomTabBar.Tab) {
        Log.d(TAG, "Tab changed to $newTab, updating UI accordingly.")

        when (newTab) {
            BottomTabBar.Tab.LOCAL_MODELS -> {
                updateMainTitleSwitcherMode(false)
                mainTitleSwitcher.text = getString(R.string.nav_name_chats)
                appBarLayout.visibility = View.VISIBLE
                appBarLayout.setExpanded(true, false)
            }
            BottomTabBar.Tab.MODEL_MARKET -> {
                updateMainTitleSwitcherMode(true)
                appBarLayout.visibility = View.VISIBLE
                appBarLayout.setExpanded(true, false)
                val currentProvider = MainSettings.getDownloadProviderString(this)
                val idx = ModelSources.sourceList.indexOf(currentProvider)
                val displayName =
                    if (idx != -1) getString(ModelSources.sourceDisPlayList[idx]) else currentProvider
                mainTitleSwitcher.text = displayName
            }
        }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawerLayout.requestLayout()
        invalidateOptionsMenu()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPrivacyPolicyAgreement()
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setupAppBar()
        bottomNav = findViewById(R.id.bottom_navigation)
        drawerLayout = findViewById(R.id.drawer_layout)

        updateChecker = UpdateChecker(this)
        updateChecker!!.checkForUpdates(this, false)

        mainFragmentManager = MainFragmentManager(this, R.id.main_fragment_container, bottomNav, this)
        mainFragmentManager.initialize(savedInstanceState)

        Log.d(TAG, "onCreate: Before bottomNav.select, currentFragment: ${currentFragment?.javaClass?.simpleName}")

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val menuHost: MenuHost = this
        menuHost.addMenuProvider(menuProvider, this, Lifecycle.State.RESUMED)

        handleIntentExtras(intent)
    }
    private fun handleIntentExtras(intent: Intent?) {
        intent?.let {
            val selectTab = it.getStringExtra(EXTRA_SELECT_TAB)
            if (selectTab == TAB_MODEL_MARKET) {
                bottomNav.post {
                    bottomNav.select(BottomTabBar.Tab.MODEL_MARKET)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    fun runModel(destModelDir: String?, modelIdParam: String?, sessionId: String?) {
        ChatRouter.startRun(this, modelIdParam!!, destModelDir, sessionId)
        drawerLayout.close()
    }

    fun onStarProject(view: View?) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.star_project_confirm_title)
            .setMessage(R.string.star_project_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                GithubUtils.starProject(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    fun onReportIssue(view: View?) {
        GithubUtils.reportIssue(this)
    }

    fun addLocalModels(view: View?) {
        val adbCommand = "adb shell mkdir -p /data/local/tmp/mnn_models && adb push \${model_path} /data/local/tmp/mnn_models/"
        val message = getResources().getString(R.string.add_local_models_message, adbCommand)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.add_local_models_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setNeutralButton(R.string.copy_command) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ADB Command", adbCommand)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            .create()
        dialog.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ModelDownloadManager.REQUEST_CODE_POST_NOTIFICATIONS) {
            ModelDownloadManager.getInstance(this).tryStartForegroundService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun onAddModelButtonClick(view: View) {
        bottomNav.select(BottomTabBar.Tab.MODEL_MARKET)
    }

    private fun checkPrivacyPolicyAgreement() {
        if (!ENABLE_PRIVACY_POLICY_CHECK) {
            return
        }

        val privacyManager = PrivacyPolicyManager.getInstance(this)

        if (!privacyManager.hasUserAgreed()) {
            showPrivacyPolicyDialog()
        }
    }

    private fun showPrivacyPolicyDialog() {
        val dialog = PrivacyPolicyDialogFragment.newInstance(
            onAgree = {
                val privacyManager = PrivacyPolicyManager.getInstance(this)
                privacyManager.setUserAgreed(true)
                Log.d(TAG, "User agreed to privacy policy")
            },
            onDisagree = {
                Toast.makeText(this, getString(R.string.privacy_policy_exit_message), Toast.LENGTH_LONG).show()
                Log.d(TAG, "User disagreed to privacy policy")
                finishAffinity()
            }
        )

        dialog.show(supportFragmentManager, PrivacyPolicyDialogFragment.TAG)
    }

    companion object {
        const val TAG: String = "MainActivity"
        const val EXTRA_SELECT_TAB = "com.visionaidplusplus.mnnllm.android.select_tab"
        const val TAB_MODEL_MARKET = "model_market"
        const val ENABLE_PRIVACY_POLICY_CHECK = false
    }
}