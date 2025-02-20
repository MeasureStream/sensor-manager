package com.polito.tesi.measuremanager.controllers


import com.polito.tesi.measuremanager.exceptions.OperationNotAllowed
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ProblemDetailsHandler:ResponseEntityExceptionHandler() {
    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(e :EntityNotFoundException):ProblemDetail{
        val d = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        d.title="Not Found Entity"
        d.detail=e.message
        return d
    }
    @ExceptionHandler(NoSuchElementException::class)
    fun handleEntityNotFound(e :NoSuchElementException):ProblemDetail{
        val d = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        d.title="Not Found Entity"
        d.detail=e.message
        return d
    }
    @ExceptionHandler(OperationNotAllowed::class)
    fun handleOperationNotAllowed(e:OperationNotAllowed):ProblemDetail{
        val d = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        d.title="Operation Forbidden"
        d.detail=e.message
        return d
    }

    @ExceptionHandler( ConstraintViolationException::class)
    fun handleValidation(e:ConstraintViolationException):ProblemDetail{
        val d = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        d.title="Operation Forbidden"
        d.detail=e.message
        return d
    }

    @ExceptionHandler(EntityExistsException::class)
    fun handleEntityAlreadyExsits(e:EntityExistsException):ProblemDetail{
        val d = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        d.title="Entity Already exsists"
        d.detail=e.message
        return d
    }

}
class ExceptionHandler {
}