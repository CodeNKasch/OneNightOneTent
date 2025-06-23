package com.example.onenighttent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MarkerInfoBottomSheetFragment : BottomSheetDialogFragment()
{

        private var markerTitle: String? = null
        private var markerDescription: String? = null
        private var markerLink: String? = null
        // Add other data fields as needed

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            arguments?.let {
                markerTitle = it.getString(ARG_TITLE)
                markerDescription = it.getString(ARG_DESCRIPTION)
                markerLink = it.getString(ARG_LINK)
                // Retrieve other data
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            return inflater.inflate(R.layout.bottom_sheet_marker_info, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val titleTextView: TextView = view.findViewById(R.id.bottom_sheet_title)
            val descriptionTextView: TextView = view.findViewById(R.id.bottom_sheet_description)
            val linkTextView: TextView = view.findViewById(R.id.bottom_sheet_link)
            val closeButton: Button = view.findViewById(R.id.bottom_sheet_close_button)

            titleTextView.text = markerTitle ?: "N/A"
            descriptionTextView.text = markerDescription ?: "No description available."
            if (markerLink != null) {
                linkTextView.text = "Link: $markerLink"
                linkTextView.visibility = View.VISIBLE
            } else {
                linkTextView.visibility = View.GONE
            }

            // Add logic for other views

            closeButton.setOnClickListener {
                dismiss() // Dismisses the bottom sheet
            }
        }

        companion object {
            private const val ARG_TITLE = "arg_title"
            private const val ARG_DESCRIPTION = "arg_description"
            private const val ARG_LINK = "arg_link"
            // Add other ARG constants

            @JvmStatic
            fun newInstance(
                title: String,
                description: String?,
                link: String?
            ): MarkerInfoBottomSheetFragment{
                return MarkerInfoBottomSheetFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_TITLE, title)
                        putString(ARG_DESCRIPTION, description)
                        putString(ARG_LINK, link)
                        // Put other data
                    }
                }
            }
        }
    }

