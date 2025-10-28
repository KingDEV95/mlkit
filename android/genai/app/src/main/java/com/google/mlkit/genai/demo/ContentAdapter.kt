/*
 * Copyright 2025 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mlkit.genai.demo

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.genai.demo.ContentItem.ImageItem
import com.google.mlkit.genai.demo.ContentItem.TextAndImagesItem
import com.google.mlkit.genai.demo.ContentItem.TextItem
import com.google.mlkit.genai.demo.ContentItem.TextWithPromptPrefixItem

/** A recycler view adapter for displaying the request and response views. */
class ContentAdapter : RecyclerView.Adapter<ViewHolder>() {
  private var recyclerView: RecyclerView? = null

  private val contentList: MutableList<ContentItem> = ArrayList()

  fun addContent(content: ContentItem) {
    contentList.add(content)
    notifyItemInserted(contentList.size - 1)
    recyclerView?.post { recyclerView?.smoothScrollToPosition(contentList.size - 1) }
  }

  fun updateStreamingResponse(response: String) {
    contentList[contentList.size - 1] = TextItem.fromStreamingResponse(response)
    notifyDataSetChanged()
    recyclerView?.post { recyclerView?.smoothScrollToPosition(contentList.size - 1) }
  }

  override fun getItemViewType(position: Int): Int {
    return contentList[position].viewType
  }

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)
    this.recyclerView = recyclerView
  }

  override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
    val layoutInflater = LayoutInflater.from(viewGroup.context)
    return when (viewType) {
      VIEW_TYPE_REQUEST_TEXT ->
        TextViewHolder(layoutInflater.inflate(R.layout.row_item_request_text, viewGroup, false))
      VIEW_TYPE_REQUEST_IMAGE ->
        ImageViewHolder(layoutInflater.inflate(R.layout.row_item_request_image, viewGroup, false))
      VIEW_TYPE_RESPONSE,
      VIEW_TYPE_RESPONSE_STREAMING,
      VIEW_TYPE_RESPONSE_ERROR ->
        TextViewHolder(layoutInflater.inflate(R.layout.row_item_response, viewGroup, false))
      VIEW_TYPE_REQUEST_TEXT_AND_IMAGES ->
        TextAndImagesViewHolder(
          layoutInflater.inflate(R.layout.row_item_request_text_and_images, viewGroup, false)
        )
      VIEW_TYPE_REQUEST_TEXT_WITH_PROMPT_PREFIX ->
        TextWithPromptPrefixViewHolder(
          layoutInflater.inflate(R.layout.row_item_request_text, viewGroup, false)
        )
      else -> throw IllegalArgumentException("Invalid view type $viewType")
    }
  }

  override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
    (viewHolder as ContentViewHolder).bind(contentList[position])
  }

  override fun getItemCount(): Int {
    return contentList.size
  }

  interface ContentViewHolder {
    fun bind(item: ContentItem)
  }

  /** Hosts text request or response item view. */
  class TextViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {

    private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
    private val metadataTextView: TextView? = itemView.findViewById(R.id.metadata_text_view)
    private val defaultTextColors: ColorStateList = contentTextView.textColors

    override fun bind(item: ContentItem) {
      if (item is TextItem) {
        contentTextView.setTextColor(defaultTextColors)
        metadataTextView?.visibility = View.GONE

        if (item.viewType == VIEW_TYPE_RESPONSE_ERROR) {
          contentTextView.text = item.text
          contentTextView.setTextColor(Color.RED)
        } else if (item.viewType == VIEW_TYPE_RESPONSE_STREAMING) {
          contentTextView.text =
            SpannableStringBuilder().apply {
              append(STREAMING_INDICATOR)
              setSpan(StyleSpan(Typeface.BOLD), 0, length, SPAN_EXCLUSIVE_EXCLUSIVE)
              append(item.text)
            }
        } else {
          contentTextView.text = item.text
          if (item.metadata != null) {
            metadataTextView?.apply {
              text = item.metadata
              visibility = View.VISIBLE
            }
          }
        }
      }
    }
  }

  /** Hosts image request item view. */
  class ImageViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {

    private val contentImageView: ImageView = itemView.findViewById(R.id.content_image_view)

    override fun bind(item: ContentItem) {
      if (item is ImageItem) {
        contentImageView.setImageURI(item.imageUri)
      }
    }
  }

  /** Hosts combined text and image request item view. */
  class TextAndImagesViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {
    private val messageText: TextView = itemView.findViewById(R.id.chat_message_text)
    private val imageContainer: LinearLayout = itemView.findViewById(R.id.image_container)
    private val bubbleLayout: LinearLayout = itemView.findViewById(R.id.chat_bubble_layout)

    private val defaultTextColors: ColorStateList = messageText.textColors

    override fun bind(item: ContentItem) {
      if (item !is TextAndImagesItem) {
        return
      }
      if (!item.text.isEmpty()) {
        messageText.text = item.text
        messageText.visibility = View.VISIBLE
      } else {
        messageText.visibility = View.GONE
      }

      imageContainer.removeAllViews()
      if (item.imageUris.isNotEmpty()) {
        imageContainer.visibility = View.VISIBLE
        for (uri in item.imageUris) {
          val imageView = ImageView(imageContainer.context)
          val layoutParams = LinearLayout.LayoutParams(400, 400)
          layoutParams.setMargins(0, 0, 16, 0)
          imageView.layoutParams = layoutParams
          imageView.setImageURI(uri)
          imageView.scaleType = ImageView.ScaleType.CENTER_CROP
          imageContainer.addView(imageView)
        }
      } else {
        imageContainer.visibility = View.GONE
      }

      bubbleLayout.setBackgroundResource(R.drawable.request_item_background)
      messageText.setTextColor(defaultTextColors)
    }
  }

  /** Hosts text request with prompt prefix item view. */
  class TextWithPromptPrefixViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {
    private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
    private val defaultTextColors: ColorStateList = contentTextView.textColors

    override fun bind(item: ContentItem) {
      if (item !is TextWithPromptPrefixItem) {
        return
      }
      contentTextView.setTextColor(defaultTextColors)

      contentTextView.text =
        contentTextView.context.getString(
          R.string.message_format_prefix_and_suffix,
          item.promptPrefix,
          item.dynamicSuffix,
        )
    }
  }

  companion object {
    const val VIEW_TYPE_REQUEST_TEXT: Int = 0
    const val VIEW_TYPE_REQUEST_IMAGE: Int = 1
    const val VIEW_TYPE_RESPONSE: Int = 2
    const val VIEW_TYPE_RESPONSE_STREAMING: Int = 3
    const val VIEW_TYPE_RESPONSE_ERROR: Int = 4
    const val VIEW_TYPE_REQUEST_TEXT_AND_IMAGES: Int = 5
    const val VIEW_TYPE_REQUEST_TEXT_WITH_PROMPT_PREFIX: Int = 6

    const val STREAMING_INDICATOR: String = "STREAMING...\n"
  }
}
