package com.pfc.thindesk.controller;

import com.pfc.thindesk.dto.ErroApi;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler {

    @ExceptionHandler(ParametroInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroApi tratarParametroInvalido(ParametroInvalidoException excecao) {
        return erro(excecao.getMessage());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroApi tratarFiltroInvalido(BindException excecao) {
        String campos = excecao.getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .collect(Collectors.joining(", "));
        return erro("Valor inválido para: " + campos);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroApi tratarTipoInvalido(MethodArgumentTypeMismatchException excecao) {
        return erro("Valor inválido para: " + excecao.getName());
    }

    private static ErroApi erro(String mensagem) {
        return new ErroApi(HttpStatus.BAD_REQUEST.value(), mensagem);
    }
}
