package com.example.masterproject.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.masterproject.*
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
import java.lang.Exception
import java.util.*


class SignUpActivity: AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private val client: MulticastClient = MulticastClient(null)

    private val TAG = "SignupActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)
        auth = Firebase.auth

        val signUpButton: Button = findViewById(R.id.sendInSignUpButton)
        signUpButton.setOnClickListener {
            val emailTextView: TextView = findViewById(R.id.emailInputText)
            val passwordTextView: TextView = findViewById(R.id.passwordInputText)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    signUp(emailTextView.text.toString(), passwordTextView.text.toString())
                } catch (e: Exception) {
                    when (e) {
                        is UsernameTakenException, is InvalidEmailException ->
                            runOnUiThread(java.lang.Runnable {
                                Toast.makeText(
                                    baseContext, e.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            })
                        else -> throw e
                    }
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
        if (!MISCUtils.isEmail(email)) throw InvalidEmailException("The email is not valid.")
        if (Ledger.getLedgerEntry(email) != null) throw UsernameTakenException("The username is taken.")
        //val isOnline = networkIsOnline()
        val isOnline = false
        if (isOnline) {
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
                client.broadcastBlock(0)
            }
        } else {
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
                    Log.d("FIREBASE LOGIN", "createUserWithEmail:success")
                    val user = auth.currentUser
                    user!!.sendEmailVerification()
                    Toast.makeText(
                        baseContext, "Confirm email and log in",
                        Toast.LENGTH_SHORT
                    ).show()
                    auth.signOut()
                    returnToMainActivity(email)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("FIREBASE LOGIN", "createUserWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun networkIsOnline(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8")
            val exitValue = ipProcess.waitFor()
            ipProcess.destroy()
            val isOnline = exitValue == 0
            Log.d(TAG, "is Online: $isOnline")
            return isOnline
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
            false
        }
    }

    private fun returnToMainActivity(username: String) {
        val myIntent = Intent(this@SignUpActivity, MainActivity::class.java)
        myIntent.putExtra("username", username)
        this@SignUpActivity.startActivity(myIntent)
    }


}