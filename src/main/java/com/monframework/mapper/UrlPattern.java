package com.monframework.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classe pour gérer les patterns d'URL dynamiques avec paramètres.
 * Exemple: /etudiant/{id} ou /users/{userId}/posts/{postId}
 */
public class UrlPattern {
    private final String pattern;
    private final Pattern regex;
    private final List<String> paramNames;

    public UrlPattern(String pattern) {
        this.pattern = pattern;
        this.paramNames = new ArrayList<>();
        this.regex = compilePattern(pattern);
    }

    /**
     * Convertit un pattern d'URL en regex et extrait les noms des paramètres.
     * Exemple: /etudiant/{id} -> /etudiant/([^/]+)
     */
    private Pattern compilePattern(String urlPattern) {
        // Extraire les noms des paramètres {param}
        Pattern paramPattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = paramPattern.matcher(urlPattern);
        
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }
        
        // Remplacer {param} par ([^/]+) pour créer la regex
        String regexPattern = urlPattern.replaceAll("\\{[^}]+\\}", "([^/]+)");
        
        // Échapper les caractères spéciaux et créer le pattern
        regexPattern = "^" + regexPattern + "$";
        
        return Pattern.compile(regexPattern);
    }

    /**
     * Vérifie si une URL correspond au pattern.
     */
    public boolean matches(String url) {
        return regex.matcher(url).matches();
    }

    /**
     * Extrait les valeurs des paramètres depuis une URL.
     * Retourne une Map avec nom -> valeur.
     */
    public Map<String, String> extractParams(String url) {
        Map<String, String> params = new HashMap<>();
        
        Matcher matcher = regex.matcher(url);
        if (matcher.matches()) {
            for (int i = 0; i < paramNames.size(); i++) {
                params.put(paramNames.get(i), matcher.group(i + 1));
            }
        }
        
        return params;
    }

    /**
     * Retourne les noms des paramètres dans l'ordre.
     */
    public List<String> getParamNames() {
        return paramNames;
    }

    /**
     * Vérifie si le pattern contient des paramètres dynamiques.
     */
    public boolean isDynamic() {
        return !paramNames.isEmpty();
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return "UrlPattern{pattern='" + pattern + "', params=" + paramNames + "}";
    }
}
