package com.polito.tesi.measuremanager.controllers

import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import com.polito.tesi.measuremanager.template.TemplateRejected
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ProblemDetailsHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(e: EntityNotFoundException): ProblemDetail  {
        val d = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        d.title = "Not Found Entity"
        d.detail = e.message
        return d
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleEntityNotFound(e: NoSuchElementException): ProblemDetail  {
        val d = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        d.title = "Not Found Entity"
        d.detail = e.message
        return d
    }

    @ExceptionHandler(OperationNotAllowed::class)
    fun handleOperationNotAllowed(e: OperationNotAllowed): ProblemDetail  {
        val d = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        d.title = "Operation Forbidden"
        d.detail = e.message
        return d
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidation(e: ConstraintViolationException): ProblemDetail  {
        val d = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        d.title = "Operation Forbidden"
        d.detail = e.message
        return d
    }

    @ExceptionHandler(EntityExistsException::class)
    fun handleEntityAlreadyExsits(e: EntityExistsException): ProblemDetail  {
        val d = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        d.title = "Entity Already exsists"
        d.detail = e.message
        return d
    }

    @ExceptionHandler(TemplateRejected::class)
    fun handleTemplateRejected(e: TemplateRejected): ProblemDetail {
        val d = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY)
        d.title = "Template rifiutato"
        d.detail = e.message
        return d
    }

    /**
     * Tutto il resto.
     *
     * Senza questa, un'eccezione imprevista arriva all'interfaccia come un 500 con il corpo
     * vuoto: chi la riceve non sa nemmeno di che famiglia sia il guasto, e deve andare a
     * cercare i log del container per scoprire una riga che il server aveva gia' in mano.
     *
     * Qui lo stack trace si scrive nel log — per intero, perche' e' li' che serve — e nella
     * risposta va il nome dell'eccezione con il suo messaggio. E' una piattaforma interna
     * dietro autenticazione: sapere che era una `UninitializedPropertyAccessException` su un
     * campo vale molto piu' della riservatezza di quel dettaglio.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ProblemDetail {
        log.error("Errore non gestito: {}", e.message, e)
        val d = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        d.title = "Errore interno"
        d.detail = "${e.javaClass.simpleName}: ${e.message ?: "nessun messaggio"}"
        return d
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ProblemDetailsHandler::class.java)
    }
}

class ExceptionHandler
