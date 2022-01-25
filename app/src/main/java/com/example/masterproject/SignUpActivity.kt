package com.example.masterproject

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Exception
import java.net.InetAddress


class SignUpActivity: AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private val client: MulticastClient = MulticastClient()

    private val TAG = "SignupActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //val intent = intent
        //val value = intent.getStringExtra("key") //if it's a string you stored.
        setContentView(R.layout.activity_sign_up)
        auth = Firebase.auth

        val signUpButton: Button = findViewById(R.id.sendInSignUpButton)
        signUpButton.setOnClickListener {
            val emailTextView: TextView = findViewById(R.id.emailInputText)
            val passwordTextView: TextView = findViewById(R.id.passwordInputText)
            GlobalScope.launch {
                signUp(emailTextView.text.toString(), passwordTextView.text.toString())
            }
        }

    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if(currentUser != null){
            Toast.makeText(
                baseContext, "Please log out first",
                Toast.LENGTH_SHORT
            ).show()
            returnToMainActivity()
        }
    }

    private fun signUp(email: String, password: String) {
        if(Utils.isEmail(email)) {
            val isOnline = networkIsOnline()
            Log.d(TAG, "Is online: $isOnline")
            if (isOnline) {
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
                            returnToMainActivity()
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w("FIREBASE LOGIN", "createUserWithEmail:failure", task.exception)
                            Toast.makeText(
                                baseContext, "Authentication failed.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            } else {
                if (RegistrationHandler.getReadyForRegistration()) {
                    try {
                        Ledger.createNewBlockFromEmail(email)
                        GlobalScope.launch {
                            client.broadcastBlock()
                            returnToMainActivity()
                        }
                    } catch (e: UsernameTakenError){
                        Toast.makeText(baseContext, "Username already taken.", Toast.LENGTH_SHORT)
                    }
                } else {
                    Toast.makeText(baseContext, "Not ready for registration", Toast.LENGTH_SHORT)
                }
            }
        }
        else{
            Toast.makeText(
                baseContext, "Not valid email",
                Toast.LENGTH_SHORT
            ).show()
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

    private fun returnToMainActivity() {
        val myIntent = Intent(this@SignUpActivity, MainActivity::class.java)
        //myIntent.putExtra("key", value) //Optional parameters
        this@SignUpActivity.startActivity(myIntent)
    }


}