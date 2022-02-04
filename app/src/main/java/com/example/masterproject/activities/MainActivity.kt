package com.example.masterproject.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.masterproject.R
import com.example.masterproject.activities.adapters.DeviceAdapter
import com.example.masterproject.ledger.Ledger
import com.example.masterproject.network.MulticastServer
import com.example.masterproject.network.TCPListener
import com.example.masterproject.network.TLSListener
import com.example.masterproject.utils.AESUtils
import com.example.masterproject.utils.MISCUtils
import com.example.masterproject.utils.PKIUtils
import com.example.masterproject.utils.TLSUtils
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security


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

        if(TLSUtils.keyStore == null) {
            TLSUtils.keyStore = TLSUtils.loadKeyStore()
        }

        // Start multicast server
        baseContext.startService(Intent(this, MulticastServer::class.java))

        //Set up view
        recyclerView = findViewById(R.id.recyclerView)
        myAdapter = DeviceAdapter(Ledger.availableDevices)
        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        //Start network processes
        baseContext.startService(Intent(this, TCPListener::class.java))
        baseContext.startService(Intent(this, TLSListener::class.java))


        //Find and display my IP address
        val myIpAddressTextView: TextView = headerView.findViewById(R.id.nav_ip)
        val myIpAddressText = MISCUtils.getMyIpAddress()
        myIpAddressTextView.text = myIpAddressText


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
                    changeToRegisterView()
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

    private fun changeToRegisterView() {
        if(!MISCUtils.isLoggedIn()) {
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
        if(!MISCUtils.isLoggedIn()) {
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
        private val TAG = "MainActivity"
        private lateinit var myAdapter: DeviceAdapter


        fun updateAvailableDevices() {
            myAdapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(myAdapter.itemCount-1)
        }

    }

}

