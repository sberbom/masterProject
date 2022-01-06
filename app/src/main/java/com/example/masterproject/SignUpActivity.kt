package com.example.masterproject

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase


class SignUpActivity: AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

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
            signUp(emailTextView.text.toString(), passwordTextView.text.toString())
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
        }
        else{
            Toast.makeText(
                baseContext, "Not valid email",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun returnToMainActivity() {
        val myIntent = Intent(this@SignUpActivity, MainActivity::class.java)
        //myIntent.putExtra("key", value) //Optional parameters
        this@SignUpActivity.startActivity(myIntent)
    }


}