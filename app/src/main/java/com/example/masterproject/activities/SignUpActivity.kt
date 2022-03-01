package com.example.masterproject.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.masterproject.R
import com.example.masterproject.exceptions.InvalidEmailException
import com.example.masterproject.exceptions.UsernameTakenException
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.ledger.RegistrationHandler
import com.example.masterproject.network.MulticastClient
import com.example.masterproject.utils.MISCUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit


class SignUpActivity: AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var emailInvalidTextView: TextView
    private lateinit var passwordInvalidTextView: TextView
    private lateinit var signUpButton: Button
    private lateinit var signUpProgressBar: ProgressBar

    private val TAG = "SignupActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)
        auth = Firebase.auth

        emailInvalidTextView = findViewById(R.id.notValidEmailText)
        passwordInvalidTextView = findViewById(R.id.notValidPasswordText)
        signUpButton = findViewById(R.id.sendInSignUpButton)
        signUpProgressBar = findViewById(R.id.signUpProgressBar)

        signUpButton.setOnClickListener {
            Log.d(TAG, "Sign up button pressed")
            val emailTextView: TextView = findViewById(R.id.emailInputText)
            val passwordTextView: TextView = findViewById(R.id.passwordInputText)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    signUp(emailTextView.text.toString(), passwordTextView.text.toString())
                } catch (e: Exception) {
                   e.printStackTrace()
                }
            }
        }

    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        if(MISCUtils.isLoggedIn()){
            Toast.makeText(
                baseContext, "Please log out first",
                Toast.LENGTH_SHORT
            ).show()
            returnToMainActivity(MISCUtils.getCurrentUserString())
        }
    }

    private fun signUp(email: String, password: String) {
        if (!MISCUtils.isEmail(email)) {
            runOnUiThread{emailInvalidTextView.visibility = View.VISIBLE}
            return
        }
        if (Ledger.getLedgerEntry(email) != null){
            runOnUiThread{
                emailInvalidTextView.visibility = View.VISIBLE
                emailInvalidTextView.text = getString(R.string.username_taken)
            }
            return
        }
        signupLoading(true)
        // networkIsOnline can be a slow function
        val isOnline = networkIsOnline()
        if (isOnline) {
            if(password == "") {
                runOnUiThread{passwordInvalidTextView.visibility = View.VISIBLE}
                signupLoading(false)
                return
            }
            onlineRegistration(email, password)
        } else {
            offlineRegistration(email)
        }
    }

    private fun offlineRegistration(email: String) {
        if (RegistrationHandler.getReadyForRegistration()) {
            returnToMainActivity(email)
            Ledger.createNewBlockFromEmail(email)
            GlobalScope.launch(Dispatchers.IO) {
                MulticastClient.broadcastBlock(0)
            }
            Log.d(TAG, "Offline registration complete")
        } else {
            signupLoading(false)
            runOnUiThread(java.lang.Runnable {
                Toast.makeText(baseContext, "Not ready for registration", Toast.LENGTH_SHORT).show()
            })
        }
    }

    private fun onlineRegistration(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "Firebase registration succeed")
                    val user = auth.currentUser
                    user!!.sendEmailVerification()
                    Toast.makeText(
                        baseContext, "Confirm email and log in",
                        Toast.LENGTH_SHORT
                    ).show()
                    auth.signOut()
                    returnToMainActivity(null)
                } else {
                    // If sign in fails, display a message to the user.
                   signupLoading(false)
                    Log.d(TAG, "Firebase registration failed")
                    Toast.makeText(
                        baseContext, "Registration failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun signupLoading(isLoading: Boolean){
        if(isLoading) {
            runOnUiThread {
                signUpProgressBar.visibility = View.VISIBLE
                signUpButton.isEnabled = false
            }
        }
        else {
            runOnUiThread {
                signUpProgressBar.visibility = View.INVISIBLE
                signUpButton.isEnabled = true
            }
        }
    }

    private fun networkIsOnline(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8")
            val hasFinished = ipProcess.waitFor(200, TimeUnit.MILLISECONDS)
            val exitValue = try{ ipProcess.exitValue() } catch (e: IllegalThreadStateException) {-1}
            ipProcess.destroy()
            return hasFinished && exitValue == 0
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
            false
        }
    }

    private fun returnToMainActivity(username: String?) {
        val myIntent = Intent(this@SignUpActivity, MainActivity::class.java)
        if(username != null) {
            myIntent.putExtra("username", username)
        }
        this@SignUpActivity.startActivity(myIntent)
    }


}