package com.example.httpserver

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.example.httpserver.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.server.Http4kServer
import org.http4k.server.Undertow
import org.http4k.server.asServer
import java.time.Instant
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.timer
import kotlin.math.max


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val server: Http4kServer by lazy { startServer() }

    private var reqTimestamps: Queue<Pair<Long, String>> = ConcurrentLinkedQueue()
    private val maxPerSecGlobal = AtomicInteger(0)
    private var maxPerSecIp: HashMap<String, Int> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.ipLbl.text = IpHelper.getIPAddress(true) + ":" + server.port()
        binding.fab.setOnClickListener { view -> onFabClick(view) }

        timer("timer", false, 0, 250) {
            // Timer task is not on the UI thread
            val handler = Handler(Looper.getMainLooper())

            val now = Instant.now().toEpochMilli()
            if (reqTimestamps.isNotEmpty()) {
                var record = reqTimestamps.peek()
                while (record != null && now - record.first > 1000) {
                    reqTimestamps.remove()
                    record = reqTimestamps.peek()
                }
            }
            maxPerSecGlobal.set(max(reqTimestamps.size, maxPerSecGlobal.get()))
            handler.post {
                binding.perSecLbl.text = "per second : ${reqTimestamps.size} (max=$maxPerSecGlobal)"
            }

            val perSecIp = reqTimestamps.groupBy { it.second }.mapValues { it.value.count() }
            perSecIp.forEach {
                val maxVal = maxPerSecIp[it.key]
                if (maxVal != null) maxPerSecIp[it.key] = max(maxVal, it.value)
                else maxPerSecIp[it.key] = it.value
            }
            val rankings = ArrayList<String>()
            maxPerSecIp.entries.sortedByDescending { it.value }.forEachIndexed { idx, it ->
                var ranking = "${idx + 1}. ${it.key} : "
                ranking += perSecIp[it.key] ?: 0
                ranking += " (max=${it.value})"
                rankings.add(ranking)
            }
            handler.post { binding.rankingLbl.text = TextUtils.join("\n", rankings) }
        }
    }

    override fun onDestroy() {
        server.close()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    fun onFabClick(v: View) {
        Snackbar.make(v, "Server started", Snackbar.LENGTH_LONG).show()
    }

    private fun startServer(): Http4kServer {
        val app = { request: Request -> handleRequest(request).body("Hello there") }
        return app.asServer(Undertow(9000)).start()
    }

    private fun handleRequest(req: Request): Response {
        val reqIp: String = req.source?.address ?: ""
        reqTimestamps.add(Pair(Instant.now().toEpochMilli(), reqIp))
        return Response(OK)
    }
}