package com.example.attendence_1

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide

class FullScreenImageDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_IMAGE_PATH = "image_path"

        fun newInstance(imagePath: String): FullScreenImageDialogFragment {
            Log.d(TAG, "Called new instance")
            val fragment = FullScreenImageDialogFragment()
            val args = Bundle().apply {
                putString(ARG_IMAGE_PATH, imagePath)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_full_screen_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imagePath = arguments?.getString(ARG_IMAGE_PATH)
        val imageView = view.findViewById<ImageView>(R.id.ivFullScreenImage)
        Log.d(TAG, "Image path: $imagePath")

        if (!imagePath.isNullOrEmpty()) {
            Glide.with(this)
                .load(imagePath)
                .into(imageView)
        }

        // Close the dialog when the image is clicked
        imageView.setOnClickListener {
            dismiss()
        }
    }
}
