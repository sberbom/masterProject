package com.example.masterproject.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import android.content.Intent
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.view.View
import androidx.appcompat.widget.Toolbar
import android.widget.*
import com.example.masterproject.*
import com.example.masterproject.activities.adapters.DeviceAdapter
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.network.MulticastServer
import com.example.masterproject.network.TCPServer
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import com.google.android.material.navigation.NavigationView





class MainActivity: AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var auth: FirebaseAuth



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val headerView: View = navigationView.getHeaderView(0)

        setSupportActionBar(findViewById(R.id.mainToolBar))
        supportActionBar!!.setDisplayShowTitleEnabled(false);
        findViewById<Toolbar>(R.id.mainToolBar).title = "Master Project"

        drawer = findViewById(R.id.drawer)
        val drawerToggle = ActionBarDrawerToggle(this, drawer, R.string.open, R.string.close)
        drawer.addDrawerListener(drawerToggle)
        drawerToggle.syncState()


        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        auth = Firebase.auth

        // Start multicast server
        baseContext.startService(Intent(this, MulticastServer::class.java))

        //Set up view
        recyclerView = findViewById(R.id.recyclerView)


        myAdapter = DeviceAdapter(Ledger.availableDevices,this)
        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)



        //Start network processes
        baseContext.startService(Intent(this, TCPServer::class.java))

        //Find and display my IP address
        val myIpAddressTextView: TextView = headerView.findViewById(R.id.nav_ip)
        val myIpAddressText = MISCUtils.getMyIpAddress()
        myIpAddressTextView.text = myIpAddressText


        //Setup available devices button and display
        /*
        val updateAvailableDevicesButton: Button = findViewById(R.id.updateaAvailableDevicesButton)
        updateAvailableDevicesButton.setOnClickListener {
            recyclerView = findViewById(R.id.recyclerView)
            myAdapter = DeviceAdapter(Ledger.getFullLedger().toTypedArray(), this)
            recyclerView.adapter = myAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.scrollToPosition(myAdapter.itemCount-1)
        }

         */

        //Logged in as text
        val loggedInAsText: TextView = headerView.findViewById(R.id.nav_username)
        loggedInAsText.text = MISCUtils.getCurrentUserString(this)


        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_login -> {
                    handleLogIn()
                    drawer.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_register -> {
                    handleRegister()
                    drawer.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_logout -> {
                    handleLogOut(headerView)
                    drawer.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_delete_stored_data -> {
                    deleteStoredData()
                    drawer.closeDrawer(GravityCompat.START)
                    false
                }
                else -> false
            }
        }

    }

    private fun handleRegister() {
        if(!MISCUtils.isLoggedIn(this)) {
            val myIntent = Intent(this@MainActivity, SignUpActivity::class.java)
            this@MainActivity.startActivity(myIntent)
        }else {
            Toast.makeText(
                baseContext, "Please log out",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleLogIn() {
        if(!MISCUtils.isLoggedIn(this)) {
            val myIntent = Intent(this@MainActivity, LogInActivity::class.java)
            this@MainActivity.startActivity(myIntent)
        }else {
            Toast.makeText(
                baseContext, "Please log out",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleLogOut(headerView: View) {
        if (auth.currentUser != null) {
            auth.signOut()
            val loggedInAsText: TextView = headerView.findViewById(R.id.nav_username)
            loggedInAsText.text = getString(R.string.not_logged_in)
        }
        Toast.makeText(
            baseContext, "Logged out",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawer.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun deleteStoredData() {
        PKIUtils.deleteStoredCertificate(this)
        PKIUtils.deleteStoredPrivateKey(this)
        AESUtils.deleteAllStoredKeys(this)
        MISCUtils.deleteCache(this)
        Toast.makeText(
            baseContext, "Stored certificate, private key and symmetric keys deleted",
            Toast.LENGTH_SHORT
        ).show()
    }



    companion object {
        private lateinit var recyclerView: RecyclerView
        private lateinit var myAdapter: DeviceAdapter
        private val TAG = "MainActivity"


        fun updateAvailableDevices() {
            Log.d(TAG, "Update available devices called")

            myAdapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(myAdapter.itemCount-1)
        }

    }

}

