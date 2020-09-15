package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview.base

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.addListener
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview.ItemBean
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Author: Michael Leo
 * Date: 2020/9/11 上午11:24
 */
class SimpleAdapter(private val dataArray: MutableList<ItemBean>) : RecyclerView.Adapter<SimpleAdapter.ItemViewHolder>() {
    companion object {
        const val STYLE_LIST = 1
        const val STYLE_GRID = 2
    }

    var onItemClickListener: OnItemClickListener? = null
    private var lastDeletedItem: Pair<Int, ItemBean>? = null
    var selectedItems = CopyOnWriteArrayList<ItemBean>()
        private set
    var startDragListener: OnStartDragListener? = null
    var editMode: Boolean = false
        private set
    var displayStyle: Int = STYLE_LIST
        private set
    private var shouldRunEditCancelAnimation = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_demo_item, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
        holder.bind(dataArray[holder.adapterPosition])
        holder.itemView.setOnClickListener {
            if (editMode) {
                holder.selectBtn.isChecked = !(holder.selectBtn.isChecked)
            }
            // // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            onItemClickListener?.onItemClick(holder.itemView, holder.layoutPosition)
        }

        holder.itemView.setOnLongClickListener {
            // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            onItemClickListener?.onItemLongClick(holder.itemView, holder.layoutPosition)
            true
        }

        holder.ivDrag.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                startDragListener?.onStartDrag(holder)
            }
            false
        }

        holder.selectBtn.setOnCheckedChangeListener { _, isChecked ->
            val selectedItem = dataArray[holder.layoutPosition]
            if (isChecked) {
                selectedItems.add(selectedItem)
            } else {
                selectedItems.remove(selectedItems.first { it.id == selectedItem.id })
            }
            onItemClickListener?.onItemClick(holder.itemView, holder.layoutPosition)
        }
        if (editMode) {
            val translationX = ObjectAnimator.ofFloat(holder.primaryLL, "translationX", 0F, 90F)
            val alpha = ObjectAnimator.ofFloat(holder.selectBtn, "alpha", 0F, 1F)
            AnimatorSet().apply {
                play(translationX).with(alpha)
                duration = 200
                addListener(onStart = {
                    holder.selectBtn.visibility = View.VISIBLE
                })
                start()
            }

            YoYo.with(Techniques.SlideInRight)
                .duration(200)
                .onStart { holder.ivDrag.visibility = View.VISIBLE }
                .playOn(holder.ivDrag)
        }
        if (shouldRunEditCancelAnimation) {
            val translationX = ObjectAnimator.ofFloat(holder.primaryLL, "translationX", 90F, 0F)
            val alpha = ObjectAnimator.ofFloat(holder.selectBtn, "alpha", 1F, 0F)
            AnimatorSet().apply {
                play(translationX).with(alpha)
                duration = 200
                addListener(onEnd = {
                    holder.selectBtn.visibility = View.GONE
                })
                start()
            }

            YoYo.with(Techniques.SlideOutRight)
                .duration(200)
                .onEnd { holder.ivDrag.visibility = View.GONE }
                .playOn(holder.ivDrag)
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = dataArray[position].id

    fun toggleEditMode() {
        editMode = !editMode
        shouldRunEditCancelAnimation = if (editMode) {
            selectedItems.clear()
            false
        } else {
            true
        }
        notifyDataSetChanged()
    }

    fun toggleDisplayMode() {
        notifyDataSetChanged()
        displayStyle = if (displayStyle == STYLE_LIST) STYLE_GRID else STYLE_LIST
    }

    fun insertAdd(position: Int, item: ItemBean) {
        dataArray.add(position, item)
        notifyItemRangeInserted(position, 1)
    }

    fun itemMove(srcPos: Int, targetPos: Int) {
        val sourceItem = dataArray.removeAt(srcPos)
        dataArray.add(targetPos, sourceItem)
        notifyItemMoved(srcPos, targetPos)
    }

    fun removeAt(position: Int) {
        lastDeletedItem = Pair(position, dataArray.removeAt(position))
        val maxIndex = selectedItems.size - 1
        for (i in maxIndex downTo 0) {
            if (selectedItems[i].id == lastDeletedItem?.second?.id) {
                selectedItems.removeAt(i)
                break
            }
        }
        notifyItemRemoved(position)
    }

    fun undo() {
        lastDeletedItem?.let {
            dataArray.add(it.first, it.second)
            notifyItemRangeInserted(it.first, 1)
            lastDeletedItem = null
        }
    }

    // =============================================
    interface OnItemClickListener {
        fun onItemClick(view: View, position: Int) {}
        fun onItemLongClick(view: View, position: Int) {}
    }

    /**
     * Implement this interface will let you drag item directly on an icon.
     */
    interface OnStartDragListener {
        /**
         * Call the following code in [onStartDrag]
         * ```kotlin
         * itemTouchHelper.startDrag(viewHolder)
         * ```
         */
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }
    // =============================================

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val txtView: TextView = itemView.findViewById(R.id.name)
        private val ivAlbum: ShapeableImageView = itemView.findViewById(R.id.ivAlbum)
        val ivDrag: ImageView = itemView.findViewById(R.id.ivDrag)
        val selectBtn: CheckBox = itemView.findViewById(R.id.selectBtn)
        val primaryLL: LinearLayout = itemView.findViewById(R.id.primaryLL)

        fun bind(item: ItemBean) {
            txtView.text = item.title
            ivAlbum.shapeAppearanceModel = ivAlbum.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, 30F)
                .build()
            Glide.with(view).load(item.imageUrl).into(ivAlbum)
        }
    }
}