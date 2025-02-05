package com.nitor.multimodalcoreweekdemo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.microsoft.graph.concurrency.ICallback
import com.microsoft.graph.core.ClientException
import com.microsoft.graph.models.extensions.*
import com.microsoft.graph.requests.extensions.GraphServiceClient
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.*
import com.speechly.client.slu.Entity
import com.speechly.client.slu.Segment
import com.speechly.client.speech.Client
import com.speechly.ui.SpeechlyButton
import com.squareup.okhttp.Dispatcher
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.*
import kotlin.math.roundToInt

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private var microsoftAccount: MicrosoftAccount? = null

    private val speechlyClient: Client = Client.fromActivity(
        activity = this,
        appId = UUID.fromString("3f5c6686-7e89-4540-a3b4-dc0e2f2ace68")
    )

    private var speechlyButton: SpeechlyButton? = null
    private var textView: TextView? = null
    private var resultView: TextView? = null

    private var signInButton: Button? = null
    private var signOutButton: Button? = null
    private var currentUserTextView: TextView? = null
    private var currentUserImageView: ImageView? = null

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeUi()


        microsoftAccount =  MicrosoftAccount(applicationContext,
            object: MicrosoftAccount.MicrosoftAccountCallback {

                override fun onAccountLoaded(account: IAccount?) {
                    updateUI(account)
                }
            }
        )

        GlobalScope.launch(Dispatchers.Default) {
            speechlyClient.onSegmentChange { segment: Segment ->
                val transcript: String = segment.words.values.joinToString(" ",
                    transform = { it.value })

                GlobalScope.launch(Dispatchers.Main) {
                    textView?.text = transcript
                    if (segment.intent != null) {
                        when (segment.intent?.intent) {
                            "query" -> handleQuery(segment)
                        }
                    }
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeUi() {
        speechlyButton = findViewById(R.id.speechly)
        textView = findViewById(R.id.textView)
        resultView = findViewById(R.id.resultView)
        textView?.visibility = View.INVISIBLE

        signInButton = findViewById(R.id.signIn)
        signOutButton = findViewById(R.id.clearCache)
        currentUserTextView = findViewById(R.id.currentUser)
        currentUserImageView = findViewById(R.id.avatar)

        // Start speechly context
        speechlyButton?.setOnTouchListener(View.OnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    textView?.visibility = View.VISIBLE
                    textView?.text = ""
                    resultView?.text = "..."
                    speechlyClient.startContext()
                }
                MotionEvent.ACTION_UP -> {
                    speechlyClient.stopContext()
                    GlobalScope.launch(Dispatchers.Default) {
                        delay(500)
                        textView?.visibility = View.INVISIBLE
                    }
                }
            }
            true
        })

        // Sign in user
        signInButton?.setOnClickListener(View.OnClickListener {
            if (microsoftAccount == null) {
                return@OnClickListener
            }
            microsoftAccount!!.signIn(this@MainActivity)
        })

        // Sign out user
        signOutButton?.setOnClickListener(View.OnClickListener {
            if (microsoftAccount == null) {
                return@OnClickListener
            }
            microsoftAccount!!.signOut()
        })
    }

    private fun updateUI(account: IAccount?) {
        if (account != null) {
            signInButton?.isEnabled = false
            signOutButton?.isEnabled = true
            currentUserTextView?.text = account.username
            microsoftAccount!!.loadProfilePicture(object: ICallback<InputStream> {
                override fun success(photoInputStream: InputStream) {
                    val bitmap: Bitmap = BitmapFactory.decodeStream(photoInputStream)
                    runOnUiThread { currentUserImageView?.setImageBitmap(bitmap) }
                }

                override fun failure(exception: ClientException?) {
                    Log.e(TAG, "Profile picture fetch failed", exception)
                }
            })
            resultView?.text = "..."
        } else {
            signInButton?.isEnabled = true
            signOutButton?.isEnabled = false
            currentUserTextView?.text = "Please login..."
            currentUserImageView?.setImageResource(0)
            resultView?.text = "..."
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleQuery(segment: Segment) {
        val subject: Entity? = segment.getEntityByType("subject")
        Log.d(TAG, segment.entities.toString())
        if (segment.isFinal && subject?.isFinal == true) {
            when (subject.value.toUpperCase(Locale.ENGLISH)) {
                "IRON BANK BALANCE" -> {
                    microsoftAccount!!.queryIronBankBalance(object: ICallback<String> {
                    override fun success(result: String?) {
                        runOnUiThread{ resultView?.text = "Iron Bank Balance\n$result€" }
                    }

                    override fun failure(ex: ClientException?) {
                        runOnUiThread{ resultView?.text = "Failed to get it" }
                    }
                    })
                }
                else -> {
                    //resultView?.text = "Don't know about that"
                }
            }
        }
    }
}