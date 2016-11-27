@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("StringsKt")
@file:kotlin.jvm.JvmVersion
@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package kotlin.text


/**
 * Returns `true` if the contents of this string is equal to the word "true", ignoring case, and `false` otherwise.
 */
@kotlin.internal.InlineOnly
public inline fun String.toBoolean(): Boolean = java.lang.Boolean.parseBoolean(this)

/**
 * Parses the string as a signed [Byte] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
@kotlin.internal.InlineOnly
public inline fun String.toByte(radix: Int = 10): Byte = java.lang.Byte.parseByte(this, radix)

/**
 * Parses the string as a [Short] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
@kotlin.internal.InlineOnly
public inline fun String.toShort(radix: Int = 10): Short = java.lang.Short.parseShort(this, radix)

/**
 * Parses the string as an [Int] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
@kotlin.internal.InlineOnly
public inline fun String.toInt(radix: Int = 10): Int = java.lang.Integer.parseInt(this, radix)

/**
 * Parses the string as a [Long] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
@kotlin.internal.InlineOnly
public inline fun String.toLong(radix: Int = 10): Long = java.lang.Long.parseLong(this, radix)

/**
 * Parses the string as a [Float] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
@kotlin.internal.InlineOnly
public inline fun String.toFloat(radix: Int = 10): Float = java.lang.Float.parseFloat(this)

/**
 * Parses the string as a [Double] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
@kotlin.internal.InlineOnly
public inline fun String.toDouble(radix: Int = 10): Double = java.lang.Double.parseDouble(this)



/**
 * Parses the string as a signed [Byte] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public fun String.toByteOrNull(radix: Int = 10): Byte? {
    val int = this.toIntOrNull(radix) ?: return null
    if (int < Byte.MIN_VALUE || int > Byte.MAX_VALUE) return null
    return int.toByte()
}

/**
 * Parses the string as a [Short] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public fun String.toShortOrNull(radix: Int = 10): Short? {
    val int = this.toIntOrNull(radix) ?: return null
    if (int < Short.MIN_VALUE || int > Short.MAX_VALUE) return null
    return int.toShort()
}

/**
 * Parses the string as an [Int] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public fun String.toIntOrNull(radix: Int = 10): Int? {
    if (radix < Character.MIN_RADIX) throw NumberFormatException("radix $radix less than Character.MIN_RADIX")
    if (radix > Character.MAX_RADIX) throw NumberFormatException("radix $radix greater than Character.MAX_RADIX")

    val length = this.length
    if (length == 0) return null

    val start: Int
    val isNegative: Boolean
    val limit: Int

    val firstChar = this[0]
    if (firstChar < '0') {  // Possible leading sign
        start = 1

        if (firstChar == '-') {
            isNegative = true
            limit = Int.MIN_VALUE
        } else if (firstChar == '+') {
            isNegative = false
            limit = -Int.MAX_VALUE
        } else
            return null

        if (length == 1) return null  // sign only, no digits after

    } else {
        start = 0
        isNegative = false
        limit = -Int.MAX_VALUE
    }


    val limitBeforeMul = limit / radix
    var result = 0
    for (i in start..(length - 1)) {
        val digit = Character.digit(this[i], radix)

        if (digit < 0) return null
        if (result < limitBeforeMul) return null

        result *= radix

        if (result < limit + digit) return null

        result -= digit
    }

    return if (isNegative) result else -result
}

/**
 * Parses the string as a [Long] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public fun String.toLongOrNull(radix: Int = 10): Long? {
    if (radix < Character.MIN_RADIX) throw NumberFormatException("radix $radix less than Character.MIN_RADIX")
    if (radix > Character.MAX_RADIX) throw NumberFormatException("radix $radix greater than Character.MAX_RADIX")

    val length = this.length
    if (length == 0) return null

    val start: Int
    val isNegative: Boolean
    val limit: Long

    val firstChar = this[0]
    if (firstChar < '0') {  // Possible leading sign
        start = 1

        if (firstChar == '-') {
            isNegative = true
            limit = Long.MIN_VALUE
        } else if (firstChar == '+') {
            isNegative = false
            limit = -Long.MAX_VALUE
        } else
            return null

        if (length == 1) return null  // sign only, no digits after

    } else {
        start = 0
        isNegative = false
        limit = -Long.MAX_VALUE
    }


    val limitBeforeMul = limit / radix
    var result = 0L
    for (i in start..(length - 1)) {
        val digit = Character.digit(this[i], radix)

        if (digit < 0) return null
        if (result < limitBeforeMul) return null

        result *= radix

        if (result < limit + digit) return null

        result -= digit
    }

    return if (isNegative) result else -result
}

/**
 * Parses the string as a [Float] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public fun String.toFloatOrNull(): Float? = screenFloatValue(this, java.lang.Float::parseFloat)

/**
 * Parses the string as a [Double] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public fun String.toDoubleOrNull(): Double? = screenFloatValue(this, java.lang.Double::parseDouble)

/**
 * Recommended floating point number validation RegEx from the javadoc of `java.lang.Double.valueOf(String)`
 */
private object ScreenFloatValueRegEx {
    @JvmField val value = run {
        val Digits = "(\\p{Digit}+)"
        val HexDigits = "(\\p{XDigit}+)"
        val Exp = "[eE][+-]?$Digits"

        val HexString = "(0[xX]$HexDigits(\\.)?)|" + // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
                "(0[xX]$HexDigits?(\\.)$HexDigits)"  // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt

        val Number = "($Digits(\\.)?($Digits?)($Exp)?)|" +  // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
                "(\\.($Digits)($Exp)?)|" +                  // . Digits ExponentPart_opt FloatTypeSuffix_opt
                "(($HexString)[pP][+-]?$Digits)"            // HexSignificand BinaryExponent

        val fpRegex = "[\\x00-\\x20]*[+-]?(NaN|Infinity|(($Number)[fFdD]?))[\\x00-\\x20]*"

        Regex(fpRegex)
    }
}

private inline fun <T> screenFloatValue(str: String, parse: (String) -> T): T? {
    return try {
        if (ScreenFloatValueRegEx.value.matches(str))
            parse(str)
        else
            null
    } catch(e: NumberFormatException) {  // overflow
        null
    }
}

