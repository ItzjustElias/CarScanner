package com.example.carscanner.data

import com.example.carscanner.domain.AnalysisOutcome
import com.example.carscanner.domain.CarScan
import com.example.carscanner.domain.Rarity
import com.example.carscanner.domain.RarityRules
import com.example.carscanner.domain.ScanException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// uses gemini API ( the free tier )
class GeminiService(
    private val apiKey: String,
    private val model: String,
    private val useSearch: Boolean = true,
) {
    private class HttpFailure(val code: Int) : Exception()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // identify a car from a picture
    suspend fun analyze(jpegBase64: String): AnalysisOutcome {
        val image = JSONObject().put(
            "inline_data",
            JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64),
        )
        return generate(listOf(JSONObject().put("text", PHOTO_PROMPT), image))
    }

    suspend fun lookup(description: String): AnalysisOutcome {
        val clean = description.trim().take(120).replace("\"", "'")
        return generate(listOf(JSONObject().put("text", lookupPrompt(clean))))
    }

    private suspend fun generate(parts: List<JSONObject>): AnalysisOutcome = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw ScanException("Missing Gemini API key. Add GEMINI_API_KEY to local.properties and rebuild.")
        }
        try {
            if (useSearch) {
                try {
                    return@withContext request(parts, grounded = true)
                } catch (e: HttpFailure) {
                    // fall through to the plain request below
                }
            }
            request(parts, grounded = false)
        } catch (e: HttpFailure) {
            throw ScanException(errorMessage(e.code))
        } catch (e: IOException) {
            throw ScanException("No connection. Check your internet and try again.")
        }
    }

    private fun request(parts: List<JSONObject>, grounded: Boolean): AnalysisOutcome {
        val partsJson = JSONArray().also { arr -> parts.forEach { arr.put(it) } }
        val config = JSONObject().put("temperature", 0.2)
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", partsJson)))
        if (grounded) {
            // Search can't be combined with JSON mode, so the prompt asks for JSON and we extract it.
            body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        } else {
            config.put("responseMimeType", "application/json")
        }
        body.put("generationConfig", config)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpFailure(response.code)
            return parse(text)
        }
    }

    private fun errorMessage(code: Int) = when (code) {
        400, 403 -> "The Gemini API key was rejected. Check GEMINI_API_KEY in local.properties."
        404 -> "That Gemini model isn't available. Change GEMINI_MODEL in local.properties."
        429 -> "Free daily limit reached. Try again later."
        in 500..599 -> "Gemini is having trouble right now. Try again in a moment."
        else -> "Request failed (error $code)."
    }

    private fun parse(responseText: String): AnalysisOutcome {
        try {
            val partsArr = JSONObject(responseText)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            val raw = buildString {
                if (partsArr != null) {
                    for (i in 0 until partsArr.length()) append(partsArr.optJSONObject(i)?.optString("text").orEmpty())
                }
            }
            if (raw.isBlank()) throw ScanException("The AI gave no answer. Try a different photo.")

            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start !in 0..<end) throw ScanException("Couldn't understand the AI's answer. Try again.")
            val json = JSONObject(raw.substring(start, end + 1))

            if (!json.optBoolean("is_car", false)) return AnalysisOutcome.NotACar

            val production = json.optLong("production_count", 0).takeIf { it > 0 }
            val value = json.optLong("value_usd", 0).takeIf { it > 0 }

            return AnalysisOutcome.Car(
                CarScan(
                    make = json.optString("make", "Unknown").ifBlank { "Unknown" },
                    model = json.optString("model", "").ifBlank { "Car" },
                    yearText = json.optString("year_range", ""),
                    bodyStyle = json.optString("body_style", ""),
                    rarity = RarityRules.resolve(Rarity.parse(json.optString("rarity")), value, production),
                    productionCount = production,
                    valueUsd = value,
                    funFact = json.optString("fun_fact", ""),
                    confidence = json.optDouble("confidence", 0.5).toFloat(),
                )
            )
        } catch (e: JSONException) {
            throw ScanException("Couldn't understand the AI's answer. Try again.$e")
        }
    }

    private companion object {
        val SPEC = """
            Cars up to the 2026 model year exist. If you don't recognise a car, or it may be a very recent
            model, search the web to identify it instead of guessing an older model.
            Reply with ONLY a single JSON object (no extra text, no markdown) using exactly these keys:
            is_car (boolean),
            make, model,
            year_range (string, e.g. "2015-2019"),
            body_style (e.g. "Coupe"),
            rarity (one of COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHICAL, DIVINE),
            production_count (integer, total units built of this model/variant, 0 if unknown),
            value_usd (integer, typical used market value in USD today, 0 if unknown),
            fun_fact (one short sentence),
            confidence (number from 0 to 1).
            Rarity guide (judge by how rare AND how valuable the car is):
            COMMON = mass-market cars seen everywhere;
            UNCOMMON = less frequent trims or enthusiast models;
            RARE = sports cars and limited trims;
            EPIC = performance icons and exotics (roughly under 20000 built or 100k-250k USD);
            LEGENDARY = supercars and special editions (under about 3000 built or over 250k USD);
            MYTHICAL = hypercars and legendary classics (under about 500 built or over 1M USD);
            DIVINE = holy-grail one-offs (under about 50 built or over 5M USD).
        """.trimIndent()

        val PHOTO_PROMPT = "You are an expert car identifier for a car-spotting game. " +
            "Identify the main car in the photo. is_car must be false if no real car is clearly visible.\n" + SPEC

        fun lookupPrompt(description: String) = "You are an expert car encyclopedia for a car-spotting game. " +
            "The player says the car is: \"$description\". Treat that as the correct identification and fill in " +
            "the details for that exact car (use the year or generation they mention, otherwise the most typical one). " +
            "is_car must be false only if this is not a real car model.\n" + SPEC
    }
}
