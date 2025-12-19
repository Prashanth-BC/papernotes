package com.example.notes.ml

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class BpeTokenizer(private val context: Context) {

    private val vocab = mutableMapOf<String, Int>()
    private val idToToken = mutableMapOf<Int, String>()
    private val bpeRanks = mutableMapOf<Pair<String, String>, Int>()
    private val cache = mutableMapOf<String, List<String>>()
    private val byteEncoder = bytesToUnicode()
    private val byteDecoder = byteEncoder.entries.associate { (k, v) -> v to k }

    val bosTokenId: Int by lazy { vocab["<s>"] ?: 0 }
    val eosTokenId: Int by lazy { vocab["</s>"] ?: 2 }
    val padTokenId: Int by lazy { vocab["<pad>"] ?: 1 }
    val unkTokenId: Int by lazy { vocab["<unk>"] ?: 3 }

    init {
        loadVocab()
        loadMerges()
    }

    private fun loadVocab() {
        try {
            val inputStream = context.assets.open("vocab.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            jsonObject.keys().forEach { key ->
                val id = jsonObject.getInt(key)
                vocab[key] = id
                idToToken[id] = key
            }
        } catch (e: Exception) {
            Log.e("BpeTokenizer", "Error loading vocab: ${e.message}")
        }
    }

    private fun loadMerges() {
        try {
            val inputStream = context.assets.open("merges.txt")
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            // Skip first line (version/comment usually)
            reader.readLine() 
            var rank = 0
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    val parts = line.split(" ")
                    if (parts.size == 2) {
                        bpeRanks[parts[0] to parts[1]] = rank++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BpeTokenizer", "Error loading merges: ${e.message}")
        }
    }

    private fun bytesToUnicode(): Map<Int, String> {
        val bs = mutableListOf<Int>()
        bs.addAll((33..126).toList())
        bs.addAll((161..172).toList())
        bs.addAll((174..255).toList())
        val cs = bs.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n += 1
            }
        }
        return bs.zip(cs).associate { (b, c) -> b to c.toChar().toString() }
    }

    private fun getBpe(token: String): List<String> {
        if (token in cache) return cache[token]!!

        var word = token.map { it.toString() }.toMutableList()
        if (word.isEmpty()) return emptyList()

        while (true) {
            if (word.size == 1) break

            var minRank = Int.MAX_VALUE
            var bestPair: Pair<String, String>? = null
            var bestIdx = -1

            for (i in 0 until word.size - 1) {
                val pair = word[i] to word[i + 1]
                val rank = bpeRanks[pair] ?: Int.MAX_VALUE
                if (rank < minRank) {
                    minRank = rank
                    bestPair = pair
                    bestIdx = i
                }
            }

            if (bestPair == null || minRank == Int.MAX_VALUE) break

            val first = bestPair.first
            val second = bestPair.second
            
            val newWord = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                 val j = word.indexOfFirst(i, first) // custom helper logic needed or standard loop
                 // Simple optimized loop replacement:
                 if (i < word.size - 1 && word[i] == first && word[i+1] == second) {
                     newWord.add(first + second)
                     i += 2
                 } else {
                     newWord.add(word[i])
                     i += 1
                 }
            }
            word = newWord
        }
        cache[token] = word
        return word
    }
    
    // Helper to find index since List doesn't have indexOfFirst taking start index in stdlib easily like Python's index()
    private fun List<String>.indexOfFirst(startIndex: Int, target: String): Int {
        for (i in startIndex until this.size) {
            if (this[i] == target) return i
        }
        return -1
    }

    fun tokenize(text: String): List<Int> {
        // Simple pre-tokenization (split by space) - RoBERTa is more complex but this approximates
        // For accurate RoBERTa we should use Regex split.
        // Regex details from GPT-2/RoBERTa paper usually: 
        // 's|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+
        
        // Simplified fallback regex for mobile
        // 1. Clean
        // 2. Space handling
        
        // Byte encode
        // This is a simplified version. For full fidelity we'd match HuggingFace implementation.
        // Assuming inputs roughly standard English/Latin for OCR.
        
        val words = text.split(Regex("(?<=\\s)|(?=\\s)")) // Split keeping delimiters
        
        val bpeTokens = mutableListOf<String>()
        
        for (token in words) {
            if (token.isEmpty()) continue
            // Byte Encode
            val bytes = token.toByteArray(StandardCharsets.UTF_8)
            val newString = StringBuilder()
            for (b in bytes) {
                // b is signed byte in Kotlin (-128..127), we need unsigned 0..255 for map
                val key = b.toInt() and 0xFF
                newString.append(byteEncoder[key])
            }
            
            val bpeSubwords = getBpe(newString.toString())
            bpeTokens.addAll(bpeSubwords)
        }
        
        return bpeTokens.map { vocab[it] ?: unkTokenId }
    }

    fun decode(ids: List<Int>): String {
        val text = StringBuilder()
        for (id in ids) {
            val token = idToToken[id] ?: continue
            text.append(token)
        }
        
        val utf8Bytes = mutableListOf<Byte>()
        for (char in text.toString()) {
            val key = char.toString()
            val byteInt = byteDecoder[key]
            if (byteInt != null) {
                utf8Bytes.add(byteInt.toByte())
            } else {
                // Fallback or unicode char directly?
                 utf8Bytes.addAll(char.toString().toByteArray().toList())
            }
        }
        
        val decoded = String(utf8Bytes.toByteArray(), StandardCharsets.UTF_8)
        return decoded.replace("Ġ", " ").trim()
    }
}
