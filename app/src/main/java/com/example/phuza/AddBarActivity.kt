package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.adapters.BarListAdapter
import com.example.phuza.data.BarUi
import com.google.firebase.firestore.FirebaseFirestore

class AddBarActivity : BaseActivity() {

    private lateinit var adapter: BarListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_bar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val rv = findViewById<RecyclerView>(R.id.rvBars)
        val etSearch = findViewById<EditText>(R.id.etSearchChats)
        val tvCancel = findViewById<TextView>(R.id.tvCancel)

        adapter = BarListAdapter(
            recentBars = emptyList(),
            allBars = emptyList(),
            onBarClick = { bar ->
                saveRecent(bar)
                val intent = Intent(this, AddReviewActivity::class.java)
                intent.putExtra("bar_name", bar.name)
                startActivity(intent)
            },
            onRemoveRecent = { bar ->
                deleteRecent(bar)
            }
        )

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                val viewType = adapter.getItemViewType(pos)

                if (viewType == 1) { // TYPE_RECENT
                    adapter.removeRecentAt(pos)
                } else {
                    adapter.notifyItemChanged(pos)
                }
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(rv)


        tvCancel.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        loadBars()
    }

    private fun loadBars() {
        val db = FirebaseFirestore.getInstance()
        db.collection("locations")
            .get()
            .addOnSuccessListener { snapshot ->
                val bars: List<BarUi> = snapshot.toObjects(BarUi::class.java)

                val recentNames = getRecentBars()
                val recentBars = bars.filter { recentNames.contains(it.name) }
                val allBarsList = bars

                adapter.setItems(
                    recentBars = recentBars,
                    allBars = allBarsList
                )
            }
    }

    private fun getRecentBars(): Set<String> {
        val prefs = getSharedPreferences("recent_bars", MODE_PRIVATE)
        return prefs.getStringSet("list", emptySet()) ?: emptySet()
    }

    private fun saveRecent(bar: BarUi) {
        val prefs = getSharedPreferences("recent_bars", MODE_PRIVATE)
        val set = prefs.getStringSet("list", mutableSetOf())!!.toMutableSet()

        bar.name?.let { set.add(it) }

        prefs.edit().putStringSet("list", set).apply()
    }

    private fun deleteRecent(bar: BarUi) {
        val prefs = getSharedPreferences("recent_bars", MODE_PRIVATE)
        val set = prefs.getStringSet("list", mutableSetOf())!!.toMutableSet()

        bar.name?.let { set.remove(it) }

        prefs.edit().putStringSet("list", set).apply()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, DashboardActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
