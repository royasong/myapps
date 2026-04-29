package com.example.mycard.parser

import com.google.gson.annotations.SerializedName

data class CardFiltersFile(
    val version: Int = 1,
    @SerializedName("updated_at") val updatedAt: String = "",
    val filters: List<CardFilter> = emptyList()
)

data class CardFilter(
    val id: String,
    @SerializedName("card_company") val cardCompany: String,
    @SerializedName("package") val packageName: String,
    val match: Match,
    val examples: List<String> = emptyList(),
    @SerializedName("added_at") val addedAt: String = "",
    @SerializedName("added_from_ts") val addedFromTs: Long? = null
)

data class Match(
    @SerializedName("title_regex") val titleRegex: String? = null,
    @SerializedName("body_regex") val bodyRegex: String? = null,
    val type: String
) {
    companion object {
        const val TYPE_APPROVAL = "approval"
        const val TYPE_CANCEL = "cancel"
    }
}
