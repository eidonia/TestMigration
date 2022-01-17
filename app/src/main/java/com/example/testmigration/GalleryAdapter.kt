package com.example.testmigration

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryAdapter(val onClick: (MediaStoreImage) -> Unit): RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    private var images = mutableListOf<MediaStoreImage>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.gallery_layout, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val image = images[position]
        holder.rootview.tag = image
        Glide.with(holder.imageView)
            .load(image.contentUri)
            .thumbnail(0.33f)
            .centerCrop()
            .into(holder.imageView)

    }

    override fun getItemCount(): Int {
        return images.size
    }

    fun addList(list: List<MediaStoreImage>) {
        images.clear()
        images += list
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View, onClick: (MediaStoreImage) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val rootview = itemView
        val imageView: ImageView = itemView.findViewById(R.id.image)

        init {
            imageView.setOnClickListener {
                val image = rootview.tag as? MediaStoreImage ?: return@setOnClickListener
                onClick(image)
            }
        }
    }

}