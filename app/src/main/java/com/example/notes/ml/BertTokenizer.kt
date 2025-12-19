package com.example.notes.ml

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.HashMap

class BertTokenizer(context: Context, vocabFileName: String) {
    private val vocab = HashMap<String, Int>()
    private val idToToken = HashMap<Int, String>()
    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"

    init {
        loadVocab(context, vocabFileName)
    }

    private fun loadVocab(context: Context, vocabFileName: String) {
        try {
            val inputStream = context.assets.open(vocabFileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.forEachLine { line ->
                val token = line.trim()
                if (token.isNotEmpty()) {
                    val id = vocab.size
                    vocab[token] = id
                    idToToken[id] = token
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun tokenize(text: String, maxLength: Int): Pair<IntArray, IntArray> {
        val tokens = basicTokenize(text)
        val wordPieceTokens = ArrayList<String>()
        wordPieceTokens.add(clsToken)

        for (token in tokens) {
            val subTokens = wordPieceTokenize(token)
            wordPieceTokens.addAll(subTokens)
        }
        
        // Truncate if needed (reserve space for SEP)
        if (wordPieceTokens.size > maxLength - 1) {
             // Keep first maxLength-1 (minus SEP)
             while (wordPieceTokens.size > maxLength - 1) {
                 wordPieceTokens.removeAt(wordPieceTokens.size - 1)
             }
        }
        
        wordPieceTokens.add(sepToken)

        val inputIds = IntArray(maxLength)
        val attentionMask = IntArray(maxLength)

        for (i in wordPieceTokens.indices) {
            val token = wordPieceTokens[i]
            inputIds[i] = vocab[token] ?: vocab[unkToken] ?: 0
            attentionMask[i] = 1
        }
        
        // Padding (already 0 initialized)

        return Pair(inputIds, attentionMask)
    }

    private fun basicTokenize(text: String): List<String> {
        // Simple whitespace and punctuation split (simplified)
        // Ideally should be more robust, but for this task basic split + lowercasing is a start.
        // BERT basic tokenizer also separates punctuation.
        // Let's do a best effort simple split.
        val cleaned = text.lowercase().replace(Regex("[^a-z0-9]"), " $0 ")
        return cleaned.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }

    private fun wordPieceTokenize(token: String): List<String> {
        if (token.length > 200) return listOf(unkToken)
        
        val subTokens = ArrayList<String>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var curSubToken: String? = null
            while (start < end) {
                var substr = token.substring(start, end)
                if (start > 0) {
                    substr = "##$substr"
                }
                if (vocab.containsKey(substr)) {
                    curSubToken = substr
                    break
                }
                end--
            }
            if (curSubToken == null) {
                return listOf(unkToken)
            }
            subTokens.add(curSubToken)
            start = end
        }
        return subTokens
    }
}
