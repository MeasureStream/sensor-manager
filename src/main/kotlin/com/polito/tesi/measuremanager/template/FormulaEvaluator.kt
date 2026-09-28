package com.polito.tesi.measuremanager.template

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** La formula non e' valutabile: sintassi, funzione sconosciuta, coefficiente mancante. */
class FormulaError(message: String) : Exception(message)

/**
 * Valutatore delle formule di taratura dei template.
 *
 * **Grammatica chiusa**: numeri, la variabile d'ingresso, i coefficienti `c[i]`, le quattro
 * operazioni, l'elevamento a potenza e un elenco fisso di funzioni. Nessun accesso
 * all'ambiente, nessuna chiamata a codice esterno, nessuna variabile oltre a quelle
 * dichiarate. Un template e' un dato che arriva da fuori: deve poter descrivere una
 * conversione senza poter eseguire niente.
 *
 * Esempi dai template pubblicati:
 * ```
 * c[0] + c[1] * x
 * 1.0 / (c[0] + c[1] * ln(4095.0 / x - 1.0) + c[2] * ln(4095.0 / x - 1.0)**3)
 * ```
 */
object FormulaEvaluator {

    /** Le uniche funzioni ammesse. Aggiungerne una e' una decisione, non un incidente. */
    private val FUNCTIONS: Map<String, (Double) -> Double> =
            mapOf(
                    "ln" to ::ln,
                    "log" to ::log10,
                    "exp" to ::exp,
                    "sqrt" to ::sqrt,
                    "abs" to ::abs,
            )

    /**
     * Valuta [formula] con la variabile d'ingresso [x] e i coefficienti [c].
     * Solleva [FormulaError] se la formula non e' valida; restituisce null se il risultato
     * non e' un numero finito (divisione per zero, logaritmo di un negativo).
     */
    fun evaluate(formula: String, x: Double, c: List<Double>, variable: String = "x"): Double? {
        val result = Parser(formula, x, c, variable).parse()
        return if (result.isFinite()) result else null
    }

    /**
     * Discesa ricorsiva: espressione → termine → fattore → potenza → primario.
     * Il parser e' minimo di proposito: quello che non e' previsto qui non e' esprimibile.
     */
    private class Parser(
            private val text: String,
            private val x: Double,
            private val c: List<Double>,
            private val variable: String,
    ) {
        private var pos = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            if (pos < text.length) throw FormulaError("Carattere inatteso a ${pos}: '${text[pos]}'")
            return value
        }

        /** somme e sottrazioni */
        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value =
                        when {
                            consume('+') -> value + term()
                            consume('-') -> value - term()
                            else -> return value
                        }
            }
        }

        /** prodotti e divisioni. L'elevamento `**` lo consuma gia' [power], qui non arriva. */
        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                value =
                        when {
                            peek('*') -> {
                                pos++
                                value * power()
                            }
                            peek('/') -> {
                                pos++
                                value / power()
                            }
                            else -> return value
                        }
            }
        }

        private fun peek(expected: Char): Boolean =
                pos < text.length && text[pos] == expected

        /** elevamento a potenza, associativo a destra come nella notazione usuale */
        private fun power(): Double {
            val base = unary()
            skipSpaces()
            if (pos + 1 < text.length && text[pos] == '*' && text[pos + 1] == '*') {
                pos += 2
                return base.pow(power())
            }
            if (consume('^')) return base.pow(power())
            return base
        }

        private fun unary(): Double {
            skipSpaces()
            if (consume('-')) return -unary()
            if (consume('+')) return unary()
            return primary()
        }

        private fun primary(): Double {
            skipSpaces()
            if (pos >= text.length) throw FormulaError("Formula troncata")

            if (consume('(')) {
                val value = expression()
                skipSpaces()
                if (!consume(')')) throw FormulaError("Parentesi non chiusa")
                return value
            }

            val ch = text[pos]
            if (ch.isDigit() || ch == '.') return number()
            if (ch.isLetter() || ch == '_') return identifier()
            throw FormulaError("Carattere non ammesso: '$ch'")
        }

        private fun number(): Double {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            // notazione esponenziale: 1.5e-3
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                val mark = pos
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                if (pos < text.length && text[pos].isDigit()) {
                    while (pos < text.length && text[pos].isDigit()) pos++
                } else pos = mark
            }
            return text.substring(start, pos).toDoubleOrNull()
                    ?: throw FormulaError("Numero non valido: ${text.substring(start, pos)}")
        }

        /** variabile d'ingresso, coefficiente `c[i]` o funzione */
        private fun identifier(): Double {
            val start = pos
            while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_')) pos++
            val name = text.substring(start, pos)

            skipSpaces()

            if (name == "c") {
                if (!consume('[')) throw FormulaError("Atteso '[' dopo c")
                val index = expression().toInt()
                if (!consume(']')) throw FormulaError("Parentesi quadra non chiusa")
                return c.getOrNull(index)
                        ?: throw FormulaError("Coefficiente c[$index] non dichiarato")
            }

            if (name == variable) return x

            val function =
                    FUNCTIONS[name]
                            ?: throw FormulaError("Funzione o variabile sconosciuta: $name")
            if (!consume('(')) throw FormulaError("Attesa '(' dopo $name")
            val argument = expression()
            skipSpaces()
            if (!consume(')')) throw FormulaError("Parentesi non chiusa dopo $name")
            return function(argument)
        }

        private fun consume(expected: Char): Boolean {
            skipSpaces()
            if (pos < text.length && text[pos] == expected) {
                pos++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
