package com.example.masterproject.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.masterproject.network.HTTPClient
import com.example.masterproject.R
import com.example.masterproject.utils.PKIUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LogInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var logInButton: Button
    private lateinit var logInProgressBar: ProgressBar

    val TAG = "LogInActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_in)
        auth = Firebase.auth

        setSupportActionBar(findViewById(R.id.logInToolBar))
        supportActionBar!!.setDisplayShowTitleEnabled(false);
        findViewById<Toolbar>(R.id.logInToolBar).title = "Log In"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        logInButton = findViewById(R.id.logInButton)
        logInProgressBar = findViewById(R.id.logInProgressBar)

        logInButton.setOnClickListener {
            Log.d(TAG, "Log in clicked")
            logInLoading(true)
            val emailTextView: TextView = findViewById(R.id.emailLogInInput)
            val passwordTextView: TextView = findViewById(R.id.passwordLogInInput)
            logIn(emailTextView.text.toString(), passwordTextView.text.toString())
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Toast.makeText(baseContext, "Please log out first", Toast.LENGTH_SHORT).show()
            returnToMainActivity()
        }
    }

    private fun logIn(email: String, password: String) {
        try {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("FIREBASE LOGIN", "signInWithEmail:success")
                        /** FOR TEST **/
                        //if (true) {
                        /****/
                        if (auth.currentUser!!.isEmailVerified) {
                            Toast.makeText(baseContext, "Sign in success.", Toast.LENGTH_SHORT).show()
                            val storedCertificate = PKIUtils.getStoredCertificate()
                            if (storedCertificate == null || !PKIUtils.isCASignedCertificate(storedCertificate)) {
                                val httpThread = HTTPClient(email, this)
                                Thread(httpThread).start()
                            }
                        } else {
                            auth.currentUser!!.sendEmailVerification()
                            Toast.makeText(baseContext, "Please verify email.", Toast.LENGTH_SHORT).show()
                            auth.signOut()
                        }
                        returnToMainActivity()
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("FIREBASE LOGIN", "signInWithEmail:failure", task.exception)
                        Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                        logInLoading(false)
                    }
                }
        } catch (e: Exception) {
            logInLoading(false)
            e.printStackTrace()
            Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun returnToMainActivity() {
        val myIntent = Intent(this@LogInActivity, MainActivity::class.java)
        this@LogInActivity.startActivity(myIntent)
    }

    private fun logInLoading(isLoading: Boolean) {
        Log.d(TAG, "LOADING FUNCTIONS")
        if (isLoading) {
            runOnUiThread {
                logInProgressBar.visibility = View.VISIBLE
                logInButton.isEnabled = false
            }
        } else {
            runOnUiThread {
                logInProgressBar.visibility = View.INVISIBLE
                logInButton.isEnabled = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        returnToMainActivity()
        return super.onNavigateUp()
    }

}