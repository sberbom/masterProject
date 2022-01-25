package com.example.masterproject

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import android.content.Intent
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
import com.google.android.material.navigation.NavigationView





class MainActivity: AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var drawer: DrawerLayout
    private lateinit var auth: FirebaseAuth

    private val TAG = "MainActivity"


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
        baseContext.startService(Intent(MainActivity@ this, MulticastServer::class.java))

        //Set up view
        recyclerView = findViewById(R.id.recyclerView)
        var myAdapter = DeviceAdapter(Ledger.getFullLedger().toTypedArray(),this)
        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        //Start network processes
        val tcpServerThread = TCPServer(this)
        Thread(tcpServerThread).start()

        //Find and display my IP address
        val myIpAddressTextView: TextView = headerView.findViewById(R.id.nav_ip)
        val myIpAddressText = Utils.getMyIpAddress()
        myIpAddressTextView.text = myIpAddressText


        //Setup available devices button and display
        val updateAvailableDevicesButton: Button = findViewById(R.id.updateaAvailableDevicesButton)
        updateAvailableDevicesButton.setOnClickListener {
            recyclerView = findViewById(R.id.recyclerView)
            myAdapter = DeviceAdapter(Ledger.getFullLedger().toTypedArray(), this)
            recyclerView.adapter = myAdapter
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        //Logged in as text
        if (auth.currentUser != null) {
            val loggedInAsText: TextView = headerView.findViewById(R.id.nav_username)
            loggedInAsText.text = auth.currentUser!!.email
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_login -> {
                    val myIntent = Intent(this@MainActivity, LogInActivity::class.java)
                    this@MainActivity.startActivity(myIntent)
                    drawer.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_register -> {
                    val myIntent = Intent(this@MainActivity, SignUpActivity::class.java)
                    this@MainActivity.startActivity(myIntent)
                    drawer.closeDrawer(GravityCompat.START)
                    false
                }
                R.id.nav_logout -> {
                    if (auth.currentUser != null) {
                        auth.signOut()
                        val loggedInAsText: TextView = headerView.findViewById(R.id.nav_username)
                        loggedInAsText.text = "Not logged in"
                    }
                    Toast.makeText(
                        baseContext, "Logged out",
                        Toast.LENGTH_SHORT
                    ).show()
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
        Utils.deleteStoredCertificate(this)
        Utils.deleteStoredPrivateKey(this)
        Toast.makeText(
            baseContext, "Stored certificate and private key deleted",
            Toast.LENGTH_SHORT
        ).show()
    }

}

