package com.timebet.app.core.security

import java.security.SecureRandom

/**
 * Cryptographically secure random number generator for casino games.
 * PRD Section 37.3: Casino RNG must use cryptographically secure random.
 * Do not use predictable pseudo-random logic for production outcomes.
 */
object CryptoRNG {

    private val secureRandom = SecureRandom.getInstanceStrong()

    /**
     * Returns a random integer in [0, bound).
     */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive" }
        return secureRandom.nextInt(bound)
    }

    /**
     * Returns a random integer in [min, max] inclusive.
     */
    fun nextIntInRange(min: Int, max: Int): Int {
        require(max >= min) { "max must be >= min" }
        return min + secureRandom.nextInt(max - min + 1)
    }

    /**
     * Returns a random double in [0.0, 1.0).
     */
    fun nextDouble(): Double {
        return secureRandom.nextDouble()
    }

    /**
     * Returns true with the given probability (0.0 to 1.0).
     */
    fun nextBoolean(probability: Double): Boolean {
        require(probability in 0.0..1.0) { "Probability must be in [0,1]" }
        return secureRandom.nextDouble() < probability
    }

    /**
     * Shuffles a list in-place using Fisher-Yates with secure randomness.
     */
    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }

    /**
     * Generate a random permutation of 0..(n-1).
     */
    fun randomPermutation(n: Int): List<Int> {
        val list = (0 until n).toMutableList()
        shuffle(list)
        return list
    }
}
