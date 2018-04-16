package exh.util

import io.realm.*
import java.util.*

/**
 * Realm query with logging
 *
 * @author nulldev
 */

inline fun <reified E : RealmModel> RealmQuery<out E>.beginLog(clazz: Class<out E>? =
                                                           E::class.java): LoggingRealmQuery<out E>
    = LoggingRealmQuery.fromQuery(this, clazz)

class LoggingRealmQuery<E : RealmModel>(val query: RealmQuery<E>) {
    companion object {
        fun <E : RealmModel> fromQuery(q: RealmQuery<out E>, clazz: Class<out E>?)
                = LoggingRealmQuery(q).apply {
            log += "SELECT * FROM ${clazz?.name ?: "???"} WHERE"
        }
    }

    private val log = mutableListOf<String>()

    private fun sec(section: String) = "{$section}"

    fun log() = log.joinToString(separator = " ")

    fun isValid(): Boolean {
        return query.isValid
    }

    fun isNull(fieldName: String): RealmQuery<E> {
        log += sec("\"$fieldName\" IS NULL")
        return query.isNull(fieldName)
    }

    fun isNotNull(fieldName: String): RealmQuery<E> {
        log += sec("\"$fieldName\" IS NOT NULL")
        return query.isNotNull(fieldName)
    }

    private fun appendEqualTo(fieldName: String, value: String, casing: Case? = null) {
        log += sec("\"$fieldName\" == \"$value\"" + (casing?.let {
            " CASE ${casing.name}"
        } ?: ""))
    }

    fun equalTo(fieldName: String, value: String): RealmQuery<E> {
        appendEqualTo(fieldName, value)
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: String, casing: Case): RealmQuery<E> {
        appendEqualTo(fieldName, value, casing)
        return query.equalTo(fieldName, value, casing)
    }

    fun equalTo(fieldName: String, value: Byte?): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: ByteArray): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: Short?): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: Int?): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: Long?): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: Double?): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: Float?): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: Boolean?): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun equalTo(fieldName: String, value: Date): RealmQuery<E> {
        appendEqualTo(fieldName, value.toString())
        return query.equalTo(fieldName, value)
    }

    fun appendIn(fieldName: String, values: Array<out Any?>, casing: Case? = null) {
        log += sec("[${values.joinToString(separator = ", ", transform = {
            "\"$it\""
        })}] IN \"$fieldName\"" + (casing?.let {
            " CASE ${casing.name}"
        } ?: ""))
    }

    fun `in`(fieldName: String, values: Array<String>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<String>, casing: Case): RealmQuery<E> {
        appendIn(fieldName, values, casing)
        return query.`in`(fieldName, values, casing)
    }

    fun `in`(fieldName: String, values: Array<Byte>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<Short>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<Int>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<Long>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<Double>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<Float>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<Boolean>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    fun `in`(fieldName: String, values: Array<Date>): RealmQuery<E> {
        appendIn(fieldName, values)
        return query.`in`(fieldName, values)
    }

    private fun appendNotEqualTo(fieldName: String, value: Any?, casing: Case? = null) {
        log += sec("\"$fieldName\" != \"$value\"" + (casing?.let {
            " CASE ${casing.name}"
        } ?: ""))
    }

    fun notEqualTo(fieldName: String, value: String): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: String, casing: Case): RealmQuery<E> {
        appendNotEqualTo(fieldName, value, casing)
        return query.notEqualTo(fieldName, value, casing)
    }

    fun notEqualTo(fieldName: String, value: Byte?): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: ByteArray): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: Short?): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: Int?): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: Long?): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: Double?): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: Float?): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: Boolean?): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    fun notEqualTo(fieldName: String, value: Date): RealmQuery<E> {
        appendNotEqualTo(fieldName, value)
        return query.notEqualTo(fieldName, value)
    }

    private fun appendGreaterThan(fieldName: String, value: Any?) {
        log += sec("\"$fieldName\" > $value")
    }

    fun greaterThan(fieldName: String, value: Int): RealmQuery<E> {
        appendGreaterThan(fieldName, value)
        return query.greaterThan(fieldName, value)
    }

    fun greaterThan(fieldName: String, value: Long): RealmQuery<E> {
        appendGreaterThan(fieldName, value)
        return query.greaterThan(fieldName, value)
    }

    fun greaterThan(fieldName: String, value: Double): RealmQuery<E> {
        appendGreaterThan(fieldName, value)
        return query.greaterThan(fieldName, value)
    }

    fun greaterThan(fieldName: String, value: Float): RealmQuery<E> {
        appendGreaterThan(fieldName, value)
        return query.greaterThan(fieldName, value)
    }

    fun greaterThan(fieldName: String, value: Date): RealmQuery<E> {
        appendGreaterThan(fieldName, value)
        return query.greaterThan(fieldName, value)
    }

    private fun appendGreaterThanOrEqualTo(fieldName: String, value: Any?) {
        log += sec("\"$fieldName\" >= $value")
    }

    fun greaterThanOrEqualTo(fieldName: String, value: Int): RealmQuery<E> {
        appendGreaterThanOrEqualTo(fieldName, value)
        return query.greaterThanOrEqualTo(fieldName, value)
    }

    fun greaterThanOrEqualTo(fieldName: String, value: Long): RealmQuery<E> {
        appendGreaterThanOrEqualTo(fieldName, value)
        return query.greaterThanOrEqualTo(fieldName, value)
    }

    fun greaterThanOrEqualTo(fieldName: String, value: Double): RealmQuery<E> {
        appendGreaterThanOrEqualTo(fieldName, value)
        return query.greaterThanOrEqualTo(fieldName, value)
    }

    fun greaterThanOrEqualTo(fieldName: String, value: Float): RealmQuery<E> {
        appendGreaterThanOrEqualTo(fieldName, value)
        return query.greaterThanOrEqualTo(fieldName, value)
    }

    fun greaterThanOrEqualTo(fieldName: String, value: Date): RealmQuery<E> {
        appendGreaterThanOrEqualTo(fieldName, value)
        return query.greaterThanOrEqualTo(fieldName, value)
    }

    private fun appendLessThan(fieldName: String, value: Any?) {
        log += sec("\"$fieldName\" < $value")
    }

    fun lessThan(fieldName: String, value: Int): RealmQuery<E> {
        appendLessThan(fieldName, value)
        return query.lessThan(fieldName, value)
    }

    fun lessThan(fieldName: String, value: Long): RealmQuery<E> {
        appendLessThan(fieldName, value)
        return query.lessThan(fieldName, value)
    }

    fun lessThan(fieldName: String, value: Double): RealmQuery<E> {
        appendLessThan(fieldName, value)
        return query.lessThan(fieldName, value)
    }

    fun lessThan(fieldName: String, value: Float): RealmQuery<E> {
        appendLessThan(fieldName, value)
        return query.lessThan(fieldName, value)
    }

    fun lessThan(fieldName: String, value: Date): RealmQuery<E> {
        appendLessThan(fieldName, value)
        return query.lessThan(fieldName, value)
    }

    private fun appendLessThanOrEqualTo(fieldName: String, value: Any?) {
        log += sec("\"$fieldName\" <= $value")
    }

    fun lessThanOrEqualTo(fieldName: String, value: Int): RealmQuery<E> {
        appendLessThanOrEqualTo(fieldName, value)
        return query.lessThanOrEqualTo(fieldName, value)
    }

    fun lessThanOrEqualTo(fieldName: String, value: Long): RealmQuery<E> {
        appendLessThanOrEqualTo(fieldName, value)
        return query.lessThanOrEqualTo(fieldName, value)
    }

    fun lessThanOrEqualTo(fieldName: String, value: Double): RealmQuery<E> {
        appendLessThanOrEqualTo(fieldName, value)
        return query.lessThanOrEqualTo(fieldName, value)
    }

    fun lessThanOrEqualTo(fieldName: String, value: Float): RealmQuery<E> {
        appendLessThanOrEqualTo(fieldName, value)
        return query.lessThanOrEqualTo(fieldName, value)
    }

    fun lessThanOrEqualTo(fieldName: String, value: Date): RealmQuery<E> {
        appendLessThanOrEqualTo(fieldName, value)
        return query.lessThanOrEqualTo(fieldName, value)
    }

    private fun appendBetween(fieldName: String, from: Any?, to: Any?) {
        log += sec("\"$fieldName\" BETWEEN $from - $to")
    }

    fun between(fieldName: String, from: Int, to: Int): RealmQuery<E> {
        appendBetween(fieldName, from, to)
        return query.between(fieldName, from, to)
    }

    fun between(fieldName: String, from: Long, to: Long): RealmQuery<E> {
        appendBetween(fieldName, from, to)
        return query.between(fieldName, from, to)
    }

    fun between(fieldName: String, from: Double, to: Double): RealmQuery<E> {
        appendBetween(fieldName, from, to)
        return query.between(fieldName, from, to)
    }

    fun between(fieldName: String, from: Float, to: Float): RealmQuery<E> {
        appendBetween(fieldName, from, to)
        return query.between(fieldName, from, to)
    }

    fun between(fieldName: String, from: Date, to: Date): RealmQuery<E> {
        appendBetween(fieldName, from, to)
        return query.between(fieldName, from, to)
    }

    private fun appendContains(fieldName: String, value: Any?, casing: Case? = null) {
        log += sec("\"$fieldName\" CONTAINS \"$value\"" + (casing?.let {
            " CASE ${casing.name}"
        } ?: ""))
    }

    fun contains(fieldName: String, value: String): RealmQuery<E> {
        appendContains(fieldName, value)
        return query.contains(fieldName, value)
    }

    fun contains(fieldName: String, value: String, casing: Case): RealmQuery<E> {
        appendContains(fieldName, value, casing)
        return query.contains(fieldName, value, casing)
    }

    private fun appendBeginsWith(fieldName: String, value: Any?, casing: Case? = null) {
        log += sec("\"$fieldName\" BEGINS WITH \"$value\"" + (casing?.let {
            " CASE ${casing.name}"
        } ?: ""))
    }

    fun beginsWith(fieldName: String, value: String): RealmQuery<E> {
        appendBeginsWith(fieldName, value)
        return query.beginsWith(fieldName, value)
    }

    fun beginsWith(fieldName: String, value: String, casing: Case): RealmQuery<E> {
        appendBeginsWith(fieldName, value, casing)
        return query.beginsWith(fieldName, value, casing)
    }

    private fun appendEndsWith(fieldName: String, value: Any?, casing: Case? = null) {
        log += sec("\"$fieldName\" ENDS WITH \"$value\"" + (casing?.let {
            " CASE ${casing.name}"
        } ?: ""))
    }

    fun endsWith(fieldName: String, value: String): RealmQuery<E> {
        appendEndsWith(fieldName, value)
        return query.endsWith(fieldName, value)
    }

    fun endsWith(fieldName: String, value: String, casing: Case): RealmQuery<E> {
        appendEndsWith(fieldName, value, casing)
        return query.endsWith(fieldName, value, casing)
    }

    private fun appendLike(fieldName: String, value: Any?, casing: Case? = null) {
        log += sec("\"$fieldName\" LIKE \"$value\"" + (casing?.let {
            " CASE ${casing.name}"
        } ?: ""))
    }

    fun like(fieldName: String, value: String): RealmQuery<E> {
        appendLike(fieldName, value)
        return query.like(fieldName, value)
    }

    fun like(fieldName: String, value: String, casing: Case): RealmQuery<E> {
        appendLike(fieldName, value, casing)
        return query.like(fieldName, value, casing)
    }

    fun beginGroup(): RealmQuery<E> {
        log += "("
        return query.beginGroup()
    }

    fun endGroup(): RealmQuery<E> {
        log += ")"
        return query.endGroup()
    }

    fun or(): RealmQuery<E> {
        log += "OR"
        return query.or()
    }

    operator fun not(): RealmQuery<E> {
        log += "NOT"
        return query.not()
    }

    fun isEmpty(fieldName: String): RealmQuery<E> {
        log += "\"$fieldName\" IS EMPTY"
        return query.isEmpty(fieldName)
    }

    fun isNotEmpty(fieldName: String): RealmQuery<E> {
        log += "\"$fieldName\" IS NOT EMPTY"
        return query.isNotEmpty(fieldName)
    }

    fun sum(fieldName: String): Number {
        return query.sum(fieldName)
    }

    fun average(fieldName: String): Double {
        return query.average(fieldName)
    }

    fun min(fieldName: String): Number? {
        return query.min(fieldName)
    }

    fun minimumDate(fieldName: String): Date? {
        return query.minimumDate(fieldName)
    }

    fun max(fieldName: String): Number? {
        return query.max(fieldName)
    }

    fun maximumDate(fieldName: String): Date? {
        return query.maximumDate(fieldName)
    }

    fun count(): Long {
        return query.count()
    }

    fun findAll(): RealmResults<E> {
        return query.findAll()
    }

    fun findAllAsync(): RealmResults<E> {
        return query.findAllAsync()
    }

    fun findFirst(): E? {
        return query.findFirst()
    }

    fun findFirstAsync(): E {
        return query.findFirstAsync()
    }
}
