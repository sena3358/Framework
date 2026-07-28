package com.monframework.mapper;

import com.monframework.annotation.Session;
import com.monframework.core.SessionMap;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Résout les cas spéciaux de paramètres de contrôleur.
 */
public class ParameterResolver {

    public boolean isSessionMapParameter(Parameter parameter) {
        if (parameter.getType() != Map.class) {
            return false;
        }

        if (parameter.getAnnotation(Session.class) == null) {
            return false;
        }

        Type genericType = parameter.getParameterizedType();
        if (!(genericType instanceof ParameterizedType)) {
            return false;
        }

        ParameterizedType parameterizedType = (ParameterizedType) genericType;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        return typeArguments.length == 2
                && typeArguments[0] == String.class
                && typeArguments[1] == Object.class;
    }

    public SessionMap resolveSessionMap(HttpServletRequest request) {
        return new SessionMap(request.getSession());
    }

    public boolean isMapOfBytes(Parameter parameter) {
        Type genericType = parameter.getParameterizedType();
        if (!(genericType instanceof ParameterizedType)) {
            return false;
        }

        ParameterizedType parameterizedType = (ParameterizedType) genericType;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length != 2) {
            return false;
        }

        Type valueType = typeArguments[1];
        if (!(valueType instanceof Class)) {
            return false;
        }

        Class<?> valueClass = (Class<?>) valueType;
        return valueClass.isArray() && valueClass.getComponentType() == byte.class;
    }

    public Map<String, byte[]> extractUploadedFiles(HttpServletRequest request) {
        Map<String, byte[]> filesMap = new HashMap<>();

        if (request == null) {
            return filesMap;
        }

        try {
            Collection<Part> parts = request.getParts();
            for (Part part : parts) {
                String fileName = part.getSubmittedFileName();
                if (fileName == null || fileName.isEmpty()) {
                    continue;
                }

                try (InputStream inputStream = part.getInputStream()) {
                    byte[] fileBytes = inputStream.readAllBytes();
                    String fieldName = part.getName();
                    String compositeKey = fieldName + ":" + fileName;
                    filesMap.put(compositeKey, fileBytes);
                }
            }
        } catch (ServletException | IOException e) {
            throw new RuntimeException("Erreur lors de l'extraction des fichiers uploadés", e);
        }

        return filesMap;
    }
}