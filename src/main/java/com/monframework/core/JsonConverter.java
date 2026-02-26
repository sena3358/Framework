package com.monframework.core;

import java.util.List;
import java.util.Collection;

/**
 * Classe utilitaire pour convertir des objets Java en JSON.
 * Implémentation simple sans dépendance externe.
 */
public class JsonConverter {
    
    /**
     * Convertit un objet en JSON avec le format standardisé de l'API.
     * - Pour une liste : {status, code, count, result: [...]}
     * - Pour un objet : {status, code, data: {...}}
     */
    public static String toApiResponse(Object data, String status, int code) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"status\":\"").append(status).append("\",");
        json.append("\"code\":").append(code);
        
        if (data != null) {
            // Si c'est une collection/liste
            if (data instanceof Collection || data.getClass().isArray()) {
                Collection<?> collection;
                if (data.getClass().isArray()) {
                    // Convertir le tableau en liste
                    Object[] array = (Object[]) data;
                    collection = java.util.Arrays.asList(array);
                } else {
                    collection = (Collection<?>) data;
                }
                
                json.append(",\"count\":").append(collection.size());
                json.append(",\"result\":");
                json.append(toJsonArray(collection));
            } else {
                // Objet simple
                json.append(",\"data\":");
                json.append(toJson(data));
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Convertit un objet en JSON.
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        // Types primitifs et String
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        // Collection ou Array
        if (obj instanceof Collection) {
            return toJsonArray((Collection<?>) obj);
        }
        if (obj.getClass().isArray()) {
            return toJsonArray(java.util.Arrays.asList((Object[]) obj));
        }
        
        // Objet complexe - utiliser réflexion
        return toJsonObject(obj);
    }
    
    /**
     * Convertit une collection en tableau JSON.
     */
    private static String toJsonArray(Collection<?> collection) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        
        for (Object item : collection) {
            if (!first) {
                json.append(",");
            }
            json.append(toJson(item));
            first = false;
        }
        
        json.append("]");
        return json.toString();
    }
    
    /**
     * Convertit un objet en JSON en utilisant la réflexion.
     */
    private static String toJsonObject(Object obj) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        try {
            // Parcourir tous les champs
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(obj);
                
                if (!first) {
                    json.append(",");
                }
                
                json.append("\"").append(field.getName()).append("\":");
                json.append(toJson(value));
                
                first = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Échappe les caractères spéciaux JSON.
     */
    private static String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
