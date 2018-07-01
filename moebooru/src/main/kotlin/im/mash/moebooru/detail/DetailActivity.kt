package im.mash.moebooru.detail

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import im.mash.moebooru.App.Companion.app
import im.mash.moebooru.R
import im.mash.moebooru.Settings
import im.mash.moebooru.common.MoeDH
import im.mash.moebooru.common.data.local.MoeDatabase
import im.mash.moebooru.common.data.local.entity.Post
import im.mash.moebooru.common.data.local.entity.PostDownload
import im.mash.moebooru.common.data.local.entity.PostSearch
import im.mash.moebooru.core.application.SlidingActivity
import im.mash.moebooru.core.scheduler.Outcome
import im.mash.moebooru.core.widget.VerticalViewPager
import im.mash.moebooru.detail.adapter.DetailAdapter
import im.mash.moebooru.detail.viewmodel.DetailViewModel
import im.mash.moebooru.detail.viewmodel.DetailViewModelFactory
import im.mash.moebooru.detail.viewmodel.PositionViewModel
import im.mash.moebooru.detail.viewmodel.PositionViewModelFactory
import im.mash.moebooru.download.DownloadService
import im.mash.moebooru.helper.getViewModel
import im.mash.moebooru.common.viewmodel.DownloadViewModel
import im.mash.moebooru.common.viewmodel.DownloadViewModelFactory
import im.mash.moebooru.common.viewmodel.VoteViewModel
import im.mash.moebooru.common.viewmodel.VoteViewModelFactory
import im.mash.moebooru.main.viewmodel.TagViewModelFactory
import im.mash.moebooru.util.launchUrl
import im.mash.moebooru.util.logi
import im.mash.moebooru.util.mayRequestStoragePermission
import im.mash.moebooru.util.takeSnackbarShort
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class DetailActivity : SlidingActivity(), ViewPager.OnPageChangeListener, Toolbar.OnMenuItemClickListener {

    companion object {
        private const val TAG = "DetailActivity"
    }

    internal var paddingBottom = 0
    internal var paddingTop = 0

    private lateinit var detailPager: VerticalViewPager
    private lateinit var detailAdapter: DetailAdapter

    private val component by lazy { MoeDH.detailComponent() }

    @Inject
    lateinit var detailViewModelFactory: DetailViewModelFactory
    private val detailViewModel: DetailViewModel by lazy { this.getViewModel<DetailViewModel>(detailViewModelFactory) }

    @Inject
    lateinit var positionViewModelFactory: PositionViewModelFactory
    internal val positionViewModel: PositionViewModel by lazy { this.getViewModel<PositionViewModel>(positionViewModelFactory) }

    @Inject
    lateinit var database: MoeDatabase

    @Inject
    lateinit var downloadViewModelFactory: DownloadViewModelFactory
    internal val downloadViewModel: DownloadViewModel by lazy { this.getViewModel<DownloadViewModel>(downloadViewModelFactory) }

    @Inject
    lateinit var tagViewModelFactory: TagViewModelFactory

    @Inject
    lateinit var voteViewModelFactory: VoteViewModelFactory
    private val voteViewModel: VoteViewModel by lazy { this.getViewModel<VoteViewModel>(voteViewModelFactory) }

    private var posts: MutableList<Post> = mutableListOf()
    private var postsSearch: MutableList<PostSearch> = mutableListOf()

    internal var type = "post"
    internal var position = 0
    internal var keyword = ""

    private lateinit var bg: View
    private lateinit var appBarLayout: AppBarLayout
    internal lateinit var toolbar: Toolbar

    private var username = ""
    private var passwordHash = ""

    private var idsOneTwo: MutableList<Int> = mutableListOf()
    private var idsThree: MutableList<Int> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        component.inject(this)
        logi(TAG, "onCreate")
        val tags = intent?.getStringExtra("keyword")
        if (tags == null) finish()
        keyword = tags!!
        position = intent!!.getIntExtra("position", 0)
        username = intent!!.getStringExtra("username")
        passwordHash = intent!!.getStringExtra("password_hash")
        initView()
        if (username != "") {
            initVoteViewModel()
        }
        initDetailPager()
        initPositionViewModel()
    }

    private fun initVoteViewModel() {
        voteViewModel.idsOutcomeOneTwo.observe(this, Observer<Outcome<MutableList<Int>>> { outcome ->
            when (outcome) {
                is Outcome.Progress -> {}
                is Outcome.Success -> {
                    idsOneTwo = outcome.data
                    when (type) {
                        "post" -> {
                            if (posts.size > position) {
                                setToolbarStar(posts[position].id)
                            }
                        }
                        "search" -> {
                            if (postsSearch.size > position) {
                                setToolbarStar(postsSearch[position].id)
                            }
                        }
                    }
                }
                is Outcome.Failure -> {
                    val error = outcome.e
                    when (error) {
                        is HttpException -> {
                            Toast.makeText(this, "code: ${error.code()}, msg: ${error.message()}", Toast.LENGTH_SHORT).show()
                        }
                        is IOException -> {
                            error.printStackTrace()
                        }
                    }
                }
            }
        })
        voteViewModel.idsOutcomeThree.observe(this, Observer<Outcome<MutableList<Int>>> { outcome ->
            when (outcome) {
                is Outcome.Progress -> {}
                is Outcome.Success -> {
                    idsThree = outcome.data
                    when (type) {
                        "post" -> {
                            if (posts.size > position) {
                                setToolbarStar(posts[position].id)
                            }
                        }
                        "search" -> {
                            if (postsSearch.size > position) {
                                setToolbarStar(postsSearch[position].id)
                            }
                        }
                    }
                }
                is Outcome.Failure -> {
                    val error = outcome.e
                    when (error) {
                        is HttpException -> {
                            Toast.makeText(this, "code: ${error.code()}, msg: ${error.message()}", Toast.LENGTH_SHORT).show()
                        }
                        is IOException -> {
                            error.printStackTrace()
                        }
                    }
                }
            }
        })
        val host = app.settings.activeProfileHost
        voteViewModel.getVoteIdsOneTwo(host, username)
        voteViewModel.getVoteIdsThree(host, username)
    }

    override fun onStart() {
        super.onStart()
        logi(TAG, "onStart")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        logi(TAG, "onNewIntent")
        intent?.putExtra("new", true)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        logi(TAG, "onResume")
        val new = intent?.getBooleanExtra("new", false)
        if (new != null && new) {
            intent.putExtra("new", false)
            val tags = intent?.getStringExtra("keyword")
            if (tags == null) finish()
            keyword = tags!!
            position = intent!!.getIntExtra("position", 0)
            username = intent!!.getStringExtra("username")
            passwordHash = intent!!.getStringExtra("password_hash")
            if (username != "") {
                val host = app.settings.activeProfileHost
                voteViewModel.getVoteIdsOneTwo(host, username)
                voteViewModel.getVoteIdsThree(host, username)
            }
            initViewModel()
        }
    }

    @SuppressLint("InflateParams")
    private fun initView() {
        detailPager = findViewById(R.id.detail_pager)
        bg = findViewById(R.id.detail_bg)
        appBarLayout = findViewById(R.id.appbar_layout)
        toolbar = layoutInflater.inflate(R.layout.layout_toolbar, null) as Toolbar
        appBarLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_post))
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent))
        appBarLayout.addView(toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { _, insets ->
            paddingTop = insets.systemWindowInsetTop
            paddingBottom = insets.systemWindowInsetBottom
            appBarLayout.minimumHeight = toolbar.minimumHeight + paddingTop
            toolbar.setPadding(0, paddingTop, 0, 0)
            appBarLayout.removeView(toolbar)
            appBarLayout.addView(toolbar)
            insets
        }
        toolbar.inflateMenu(R.menu.menu_details)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setNavigationOnClickListener { finish() }
    }

    @SuppressLint("InflateParams")
    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_share_url -> {
                val shareUrl = getPostUrl()
                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/*"
                intent.putExtra(Intent.EXTRA_TEXT, shareUrl)
                try {
                    startActivity(Intent.createChooser(intent, getString(R.string.share_to)))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            R.id.action_download -> {
                if (type == "post" && posts.size <= position) {
                    return true
                } else if (type == "search" && postsSearch.size <= position) {
                    return true
                }
                if (mayRequestStoragePermission(this, 0)) {
                    downloadViewModel.addTask(getDownloadPost())
                    DownloadService.startTask(this)
                }
            }
            R.id.action_browser -> {
                this.launchUrl(getPostUrl())
            }
            R.id.action_vote -> {
                if (username == "") {
                    takeSnackbarShort(this.bg, "This operation requires a user account.", paddingBottom)
                    return true
                }
                var score = 0
                val v = layoutInflater.inflate(R.layout.layout_ratingbar, null)
                val scoreTv: TextView = v.findViewById(R.id.score)
                scoreTv.text = "0"
                val ratingBar: RatingBar = v.findViewById(R.id.rating_bar)
                val cancel: Button = v.findViewById(R.id.cancel)
                val vote: Button = v.findViewById(R.id.set_vote)
                ratingBar.setOnRatingBarChangeListener { _, star, _ ->
                    score = star.toInt()
                    scoreTv.text = score.toString()
                }
                val dialog = AlertDialog.Builder(this)
                        .setTitle("Vote post")
                        .create()
                dialog.apply {
                    setView(v)
                    setCanceledOnTouchOutside(true)
                    show()
                }
                cancel.setOnClickListener {
                    dialog.dismiss()
                }
                vote.setOnClickListener {
                    var id = -1
                    when (type) {
                        "post" -> {
                            if (posts.size > position) {
                                id = posts[position].id
                            }
                        }
                        "search" -> {
                            if (postsSearch.size > position) {
                                id = postsSearch[position].id
                            }
                        }
                    }
                    if (id > 0) {
                        val baseUrl = app.settings.activeProfileSchema + "://" + app.settings.activeProfileHost
                        val url = "$baseUrl/post/vote.json"
                        voteViewModel.votePost(url, id, score, username, passwordHash)
                    }
                    dialog.dismiss()
                }
            }
        }
        return true
    }

    private fun getDownloadPost(): PostDownload {
        return when (type) {
            "post" -> {
                val url = when (app.settings.postSizeDownload) {
                    Settings.POST_SIZE_SAMPLE -> posts[position].sample_url
                    Settings.POST_SIZE_LARGER -> posts[position].getJpegUrl()
                    else -> posts[position].getFileUrl()
                }
                PostDownload(null, app.settings.activeProfileHost, posts[position].id, posts[position].preview_url, url,"")
            }
            else -> {
                val url = when (app.settings.postSizeDownload) {
                    Settings.POST_SIZE_SAMPLE -> postsSearch[position].sample_url
                    Settings.POST_SIZE_LARGER -> postsSearch[position].getJpegUrl()
                    else -> postsSearch[position].getFileUrl()
                }
                PostDownload(null, app.settings.activeProfileHost, postsSearch[position].id,
                        postsSearch[position].preview_url, url,"")
            }
        }
    }

    private fun getPostUrl(): String {
        return when (type) {
            "post" -> {
                app.settings.activeProfileSchema + "://"+ app.settings.activeProfileHost + "/post/show/" + posts[position].id
            }
            else -> {
                app.settings.activeProfileSchema + "://"+ app.settings.activeProfileHost + "/post/show/" + postsSearch[position].id
            }
        }
    }

    fun setBg() {
        when (appBarLayout.visibility) {
            View.VISIBLE -> {
                bg.visibility = View.VISIBLE
                appBarLayout.visibility = View.GONE
                hideBar()
            }
            else -> {
                bg.visibility = View.GONE
                appBarLayout.visibility = View.VISIBLE
                showBar()
            }
        }
    }

    private fun resetBg() {
        if (appBarLayout.visibility == View.GONE) {
            bg.visibility = View.GONE
            appBarLayout.visibility = View.VISIBLE
            showBar()
        }
    }

    private fun initDetailPager() {
        detailAdapter = DetailAdapter(supportFragmentManager)
        detailPager.adapter = detailAdapter
        detailPager.currentItem = 1
        detailPager.addOnPageChangeListener(this)
    }

    internal fun initViewModel() {
        if (keyword == "") {
            type = "post"
            detailViewModel.postOutcome.observe(this,
                    Observer<Outcome<MutableList<Post>>> { outcome ->
                when (outcome) {
                    is Outcome.Progress -> {
                        logi(TAG, "Outcome.Progress")
                    }
                    is Outcome.Success -> {
                        logi(TAG, "Outcome.Success. type = post")
                        val data = outcome.data
                        posts = if (app.settings.safeMode) {
                            val postsSafe: MutableList<Post> = mutableListOf()
                            data.forEach { post ->
                                if (post.rating == "s") {
                                    postsSafe.add(post)
                                }
                            }
                            postsSafe
                        } else {
                            data
                        }
                        postsSearch.clear()
                        if (posts.size > position) {
                            val id = posts[position].id
                            setToolbarTitle(id)
                            setToolbarStar(id)
                            postsChangeListener?.onPostsChanged(posts, position)
                        } else {
                            logi(TAG, "position >= posts size")
                        }
                        detailViewModel.postOutcome.removeObservers(this)
                    }
                    is Outcome.Failure -> {
                        logi(TAG, "Outcome.Failure")
                    }
                }
            })
            detailViewModel.loadPosts(app.settings.activeProfileHost)
        } else {
            type = "search"
            detailViewModel.postSearchOutcome.observe(this,
                    Observer<Outcome<MutableList<PostSearch>>> { outcome ->
                when (outcome) {
                    is Outcome.Progress -> {
                        logi(TAG, "Outcome.Progress")
                    }
                    is Outcome.Success -> {
                        logi(TAG, "Outcome.Success. type = search")
                        val data = outcome.data
                        postsSearch = if (app.settings.safeMode) {
                            val postsSafe: MutableList<PostSearch> = mutableListOf()
                            data.forEach { post ->
                                if (post.rating == "s") {
                                    postsSafe.add(post)
                                }
                            }
                            postsSafe
                        } else {
                            data
                        }
                        posts.clear()
                        if (postsSearch.size > position) {
                            val id = postsSearch[position].id
                            setToolbarTitle(id)
                            setToolbarStar(id)
                            postsChangeListener?.onPostsSearchChanged(postsSearch, position)
                        } else {
                            logi(TAG, "position >= posts size")
                        }
                        detailViewModel.postSearchOutcome.removeObservers(this)
                    }
                    is Outcome.Failure -> {
                        logi(TAG, "Outcome.Failure")
                        outcome.e.printStackTrace()
                    }
                }
            })
            detailViewModel.loadPosts(app.settings.activeProfileHost, keyword)
        }
    }

    private fun initPositionViewModel() {
        positionViewModel.getPosition().observe(this, Observer { pos ->
            if (pos != null && pos != position) {
                position = pos
                when (type) {
                    "post" -> {
                        if (posts.size > position) {
                            val post = posts[position]
                            val id = post.id
                            setToolbarTitle(id)
                            setToolbarStar(id)
                        }
                    }
                    else -> {
                        if (postsSearch.size > position) {
                            val postSearch = postsSearch[position]
                            val id = postSearch.id
                            setToolbarTitle(id)
                            setToolbarStar(id)
                        }
                    }
                }
            }
        })
    }

    private fun setToolbarStar(id: Int) {
        when {
            idsOneTwo.contains(id) -> toolbar.menu.findItem(R.id.action_vote).setIcon(R.drawable.ic_action_star_half_24dp)
            idsThree.contains(id) -> toolbar.menu.findItem(R.id.action_vote).setIcon(R.drawable.ic_action_star_24dp)
            else -> toolbar.menu.findItem(R.id.action_vote).setIcon(R.drawable.ic_action_star_border_24dp)
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        resetBg()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

    }

    override fun onPageSelected(position: Int) {
        when (position) {
            0 -> {
                when (type) {
                    "post" -> {
                        if (posts.size > this.position)
                            infoChangeListener?.onInfoChanged(posts[this.position])
                    }
                    else -> {
                        if (postsSearch.size > this.position)
                            infoChangeListener?.onInfoChanged(postsSearch[this.position])
                    }
                }
            }
            2 -> {
                when (type) {
                    "post" -> {
                        if (posts.size > this.position)
                            tagsChangeListener?.onTagsChanged(posts[this.position])
                    }
                    else -> {
                        if (postsSearch.size > this.position)
                            tagsChangeListener?.onTagsChanged(postsSearch[this.position])
                    }
                }
            }
        }
    }

    private fun setToolbarTitle(id: Int) {
        toolbar.title = getString(R.string.post) + " " + id
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        var result = true
        try {
            result = super.dispatchTouchEvent(ev)
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        logi(TAG, "onDestroy")
    }

    override fun onPause() {
        super.onPause()
        logi(TAG, "onPause")
    }

    override fun onRestart() {
        super.onRestart()
        logi(TAG, "onRestart")
    }

    fun getPosts(): MutableList<Post> {
        return posts
    }

    fun getPostsSearch(): MutableList<PostSearch> {
        return postsSearch
    }

    fun getPost(position: Int): Post {
        return posts[position]
    }

    fun getPostSearch(position: Int): PostSearch {
        return postsSearch[position]
    }

    fun getType(): String {
        return type
    }

    fun setType(type: String) {
        this.type = type
    }

    fun getPosition(): Int {
        return position
    }

    fun setPosts(posts: MutableList<Post>) {
        this.posts = posts
    }

    fun setPostsSearch(postsSearch: MutableList<PostSearch>) {
        this.postsSearch = postsSearch
    }

    fun getKeyword(): String {
        return keyword
    }

    private var postsChangeListener: PostsChangeListener? = null

    fun setPostsChangeListener(listener: PostsChangeListener) {
        postsChangeListener = listener
    }

    interface PostsChangeListener {
        fun onPostsChanged(posts: MutableList<Post>, position: Int)
        fun onPostsSearchChanged(postsSearch: MutableList<PostSearch>, position: Int)
    }


    private var infoChangeListener: InfoChangeListener? = null

    fun setInfoChangeListener(listener: InfoChangeListener) {
        infoChangeListener = listener
    }

    interface InfoChangeListener {
        fun onInfoChanged(post: Any)
    }

    private var tagsChangeListener: TagsChangeListener? = null

    fun setTagsChangeListener(listener: TagsChangeListener) {
        tagsChangeListener = listener
    }

    interface TagsChangeListener {
        fun onTagsChanged(post: Any)
    }
}