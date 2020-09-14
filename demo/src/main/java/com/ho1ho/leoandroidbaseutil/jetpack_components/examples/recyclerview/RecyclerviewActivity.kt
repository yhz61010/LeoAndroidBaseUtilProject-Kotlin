package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview

import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ho1ho.androidbase.exts.action
import com.ho1ho.androidbase.exts.snack
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview.base.SimpleAdapter
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview.base.SimpleItemTouchCallback
import kotlinx.android.synthetic.main.activity_recyclerview.*


class RecyclerviewActivity : BaseDemonstrationActivity() {
    private lateinit var simpleAdapter: SimpleAdapter
    private lateinit var itemTouchHandler: SimpleItemTouchCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recyclerview)

        val featureList = mutableListOf<ItemBean>()
        for (i in 0..100) {
            featureList.add(ItemBean("Demo String ${i + 1}", "https://picsum.photos/80?random=$i"))
        }

        simpleAdapter = SimpleAdapter(featureList)
        simpleAdapter.onItemClickListener = object : SimpleAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                ToastUtil.showToast("You click position: $position")
            }

            override fun onItemLongClick(view: View, position: Int) {
                ToastUtil.showToast("Long click on position: $position")
            }
        }
        recyclerView.run {
//            setHasFixedSize(true)
//            layoutManager = LinearLayoutManager(requireActivity())
            adapter = simpleAdapter
        }

        itemTouchHandler = object : SimpleItemTouchCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapter = recyclerView.adapter as SimpleAdapter
                adapter.removeAt(viewHolder.adapterPosition)
                rootLL.snack("Undo last delete?") {
                    action("Undo") { adapter.undo() }
                }
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
        simpleAdapter.startDragListener = object : SimpleAdapter.OnStartDragListener {
            override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
                itemTouchHelper.startDrag(viewHolder)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.recyclerview_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val editItem = menu.findItem(R.id.edit)
        val cancelItem = menu.findItem(R.id.cancel)
        if (simpleAdapter.editMode) {
            editItem.isVisible = false
            cancelItem.isVisible = true
        } else {
            editItem.isVisible = true
            cancelItem.isVisible = false
        }

        val listItem = menu.findItem(R.id.change_to_list)
        val gridItem = menu.findItem(R.id.change_to_grid)
        if (simpleAdapter.displayStyle == SimpleAdapter.STYLE_LIST) {
            listItem.isVisible = false
            gridItem.isVisible = true
            itemTouchHandler.setDefaultDragDirs(ItemTouchHelper.UP or ItemTouchHelper.DOWN)
        } else {
            listItem.isVisible = true
            gridItem.isVisible = false
            itemTouchHandler.setDefaultDragDirs(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_item -> {
                simpleAdapter.insertAdd(
                    0,
                    ItemBean("Add-${SystemClock.elapsedRealtime()}", "https://picsum.photos/80?random=${SystemClock.elapsedRealtime()}")
                )
                recyclerView.scrollToPosition(0)
            }
            R.id.edit, R.id.cancel -> {
                simpleAdapter.toggleEditMode()
                invalidateOptionsMenu()
            }
            R.id.change_to_grid, R.id.change_to_list -> {
                simpleAdapter.toggleDisplayMode()
                invalidateOptionsMenu()
                changeDisplayStyle()
            }
        }
        return true
    }

    private fun changeDisplayStyle() {
        when (simpleAdapter.displayStyle) {
            SimpleAdapter.STYLE_LIST -> {
                recyclerView.layoutManager = LinearLayoutManager(this)
            }
            SimpleAdapter.STYLE_GRID -> {
                recyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.grid_columns))
            }
        }
    }
}

data class ItemBean(val title: String, val imageUrl: String)