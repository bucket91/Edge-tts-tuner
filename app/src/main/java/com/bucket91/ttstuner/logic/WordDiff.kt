package com.bucket91.ttstuner.logic

import java.text.Normalizer

sealed class DiffSegment {
    abstract val isChange: Boolean

    data class Equal(val text: String, val tokens: List<String>) : DiffSegment() {
        override val isChange: Boolean = false
    }

    data class Replace(
        val oldText: String,
        val newText: String,
        val oldTokens: List<String>,
        val newTokens: List<String>
    ) : DiffSegment() {
        override val isChange: Boolean = true
    }

    data class Insert(
        val newText: String,
        val newTokens: List<String>
    ) : DiffSegment() {
        override val isChange: Boolean = true
    }

    data class Delete(
        val oldText: String,
        val oldTokens: List<String>
    ) : DiffSegment() {
        override val isChange: Boolean = true
    }
}

data class CandidateWordPair(
    val base: String,
    val preferred: String,
    val wholeWord: Boolean,
    val isSelected: Boolean = true
)

object WordDiff {

    private val TOKEN_REGEX = Regex("[\\p{L}\\p{M}\\p{N}_]+|[^\\s\\p{L}\\p{M}\\p{N}_]")

    fun tokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        return TOKEN_REGEX.findAll(normalized).map { it.value }.toList()
    }

    private enum class OpType { EQUAL, DELETE, INSERT }

    private data class EditOp(val type: OpType, val token: String)

    fun computeDiff(text1: String, text2: String): List<DiffSegment> {
        val tokens1 = tokenize(text1)
        val tokens2 = tokenize(text2)

        val m = tokens1.size
        val n = tokens2.size

        // Longest Common Subsequence DP table
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0 until m) {
            for (j in 0 until n) {
                if (tokens1[i] == tokens2[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        // Trace back edit operations
        var i = m
        var j = n
        val ops = mutableListOf<EditOp>()

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && tokens1[i - 1] == tokens2[j - 1]) {
                ops.add(EditOp(OpType.EQUAL, tokens1[i - 1]))
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                ops.add(EditOp(OpType.INSERT, tokens2[j - 1]))
                j--
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                ops.add(EditOp(OpType.DELETE, tokens1[i - 1]))
                i--
            }
        }

        ops.reverse()

        // Group into segments: runs of equal, or runs of changes (replace, insert, delete)
        val segments = mutableListOf<DiffSegment>()
        var idx = 0

        while (idx < ops.size) {
            val op = ops[idx]
            if (op.type == OpType.EQUAL) {
                val equalTokens = mutableListOf<String>()
                while (idx < ops.size && ops[idx].type == OpType.EQUAL) {
                    equalTokens.add(ops[idx].token)
                    idx++
                }
                segments.add(DiffSegment.Equal(equalTokens.joinToString(" "), equalTokens))
            } else {
                val oldTokens = mutableListOf<String>()
                val newTokens = mutableListOf<String>()

                while (idx < ops.size && ops[idx].type != OpType.EQUAL) {
                    when (ops[idx].type) {
                        OpType.DELETE -> oldTokens.add(ops[idx].token)
                        OpType.INSERT -> newTokens.add(ops[idx].token)
                        OpType.EQUAL -> break
                    }
                    idx++
                }

                if (oldTokens.isNotEmpty() && newTokens.isNotEmpty()) {
                    segments.add(
                        DiffSegment.Replace(
                            oldText = oldTokens.joinToString(" "),
                            newText = newTokens.joinToString(" "),
                            oldTokens = oldTokens,
                            newTokens = newTokens
                        )
                    )
                } else if (oldTokens.isNotEmpty()) {
                    segments.add(
                        DiffSegment.Delete(
                            oldText = oldTokens.joinToString(" "),
                            oldTokens = oldTokens
                        )
                    )
                } else if (newTokens.isNotEmpty()) {
                    segments.add(
                        DiffSegment.Insert(
                            newText = newTokens.joinToString(" "),
                            newTokens = newTokens
                        )
                    )
                }
            }
        }

        return segments
    }

    /**
     * Converts a diff between pre-edit and edited text into candidate word pairs
     * for saving as word corrections (A5).
     */
    fun extractCandidateWordPairs(preEditText: String, editedText: String): List<CandidateWordPair> {
        val diff = computeDiff(preEditText, editedText)
        val candidates = mutableListOf<CandidateWordPair>()

        for (seg in diff) {
            when (seg) {
                is DiffSegment.Replace -> {
                    if (seg.oldTokens.size == seg.newTokens.size) {
                        for (k in seg.oldTokens.indices) {
                            if (seg.oldTokens[k] != seg.newTokens[k]) {
                                candidates.add(
                                    CandidateWordPair(
                                        base = seg.oldTokens[k],
                                        preferred = seg.newTokens[k],
                                        wholeWord = true,
                                        isSelected = true
                                    )
                                )
                            }
                        }
                    } else {
                        candidates.add(
                            CandidateWordPair(
                                base = seg.oldText,
                                preferred = seg.newText,
                                wholeWord = false,
                                isSelected = true
                            )
                        )
                    }
                }
                is DiffSegment.Delete -> {
                    // Deleted word without replacement
                    candidates.add(
                        CandidateWordPair(
                            base = seg.oldText,
                            preferred = "",
                            wholeWord = true,
                            isSelected = false
                        )
                    )
                }
                is DiffSegment.Insert -> {
                    // Added word without base
                }
                is DiffSegment.Equal -> {}
            }
        }

        return candidates
    }
}
