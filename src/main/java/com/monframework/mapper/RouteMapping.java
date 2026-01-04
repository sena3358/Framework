package com.monframework.mapper;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.monframework.annotation.MyController;
import com.monframework.annotation.HandleUrl;
import com.monframework.core.ModelView;

public class RouteMapping {
    private final String className;
    private final String controllerValue;
    private final String urlValue;
    private final String methodName;
    private final UrlPattern urlPattern;

    public RouteMapping(String className, String controllerValue, String urlValue, String methodName) {
        this.className = className;
        this.controllerValue = controllerValue;
        this.urlValue = urlValue;
        this.methodName = methodName;
        this.urlPattern = new UrlPattern(getFullUrl());
    }

    public String getClassName() { return className; }
    public String getControllerValue() { return controllerValue; }
    public String getUrlValue() { return urlValue; }
    public String getMethodName() { return methodName; }
    
    /**
     * Retourne l'URL complète en combinant controllerValue et urlValue
     */
    public String getFullUrl() {
        String controller = controllerValue == null || controllerValue.isEmpty() ? "" : controllerValue;
        String url = urlValue == null || urlValue.isEmpty() ? "" : urlValue;
        
        // Ajouter des slashes si nécessaire
        if (!controller.startsWith("/")) {
            controller = "/" + controller;
        }
        if (!url.isEmpty() && !url.startsWith("/")) {
            url = "/" + url;
        }
        
        return controller + url;
    }

    /**
     * Vérifie si une URL correspond au pattern de cette route.
     */
    public boolean matches(String url) {
        return urlPattern.matches(url);
    }

    /**
     * Extrait les paramètres depuis une URL.
     */
    public Map<String, String> extractParams(String url) {
        return urlPattern.extractParams(url);
    }

    /**
     * Vérifie si cette route a des paramètres dynamiques.
     */
    public boolean isDynamic() {
        return urlPattern.isDynamic();
    }

    public UrlPattern getUrlPattern() {
        return urlPattern;
    }

    @Override
    public String toString() {
        return "RouteMapping{" +
                "className='" + className + '\'' +
                ", controllerValue='" + controllerValue + '\'' +
                ", urlValue='" + urlValue + '\'' +
                ", methodName='" + methodName + '\'' +
                ", fullUrl='" + getFullUrl() + '\'' +
                '}';
    }

    /**
     * Appelle la méthode du contrôleur en utilisant la réflexion.
     * La méthode peut retourner un String ou un ModelView.
     * 
     * @param urlParams Paramètres extraits de l'URL
     * @param request La requête HTTP pour extraire les paramètres additionnels
     * @return Le résultat Object retourné par la méthode (String ou ModelView)
     * @throws Exception Si l'invocation échoue
     */
    public Object callMethod(Map<String, String> urlParams, HttpServletRequest request) throws Exception {
        // Charger la classe du contrôleur
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> clazz = Class.forName(className, true, loader);

        // Créer une instance du contrôleur (constructeur par défaut)
        Object controllerInstance = clazz.getDeclaredConstructor().newInstance();

        // Trouver la méthode à invoquer
        Method method = findMethod(clazz, methodName, urlParams, request);

        // Vérifier que la méthode retourne un String ou un ModelView
        Class<?> returnType = method.getReturnType();
        if (!returnType.equals(String.class) && !returnType.equals(ModelView.class)) {
            throw new Exception("La méthode " + methodName + " de la classe " + className + 
                              " doit retourner un String ou un ModelView (retourne: " + returnType.getName() + ")");
        }

        // Préparer les arguments pour l'invocation
        Object[] args = prepareMethodArgs(method, urlParams, request);

        // Invoquer la méthode avec les arguments
        Object result = method.invoke(controllerInstance, args);

        // Retourner le résultat (String ou ModelView)
        return result;
    }

    /**
     * Trouve la méthode correspondante dans la classe.
     */
    private Method findMethod(Class<?> clazz, String methodName, Map<String, String> urlParams, HttpServletRequest request) throws NoSuchMethodException {
        // Chercher toutes les méthodes avec ce nom
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        
        // Si aucune méthode trouvée, essayer sans paramètres
        return clazz.getDeclaredMethod(methodName);
    }

    /**
     * Prépare les arguments pour l'invocation de la méthode.
     * Convertit les paramètres String en types appropriés.
     * Combine les paramètres d'URL et les paramètres HTTP.
     */
    private Object[] prepareMethodArgs(Method method, Map<String, String> urlParams, HttpServletRequest request) {
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[paramTypes.length];
        
        List<String> urlParamNames = urlPattern.getParamNames();
        
        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = parameters[i].getName(); // nom du paramètre (var2, id, etc.)
            String paramValue = null;
            
            // 1. Vérifier d'abord si c'est un paramètre d'URL (priorité aux paramètres d'URL)
            if (urlParamNames.contains(paramName)) {
                paramValue = urlParams.get(paramName);
            }
            // 2. Sinon, vérifier dans les paramètres HTTP (request.getParameter)
            else if (request != null) {
                paramValue = request.getParameter(paramName);
            }
            
            // Convertir la valeur String vers le type approprié
            args[i] = convertParameter(paramValue, paramTypes[i]);
        }
        
        return args;
    }

    /**
     * Convertit un paramètre String vers le type demandé.
     */
    private Object convertParameter(String value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType == String.class) {
            return value;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        
        // Par défaut, retourner la valeur String
        return value;
    }

    /**
     * Liste tous les fichiers .class dans un répertoire
     */
    private static List<Path> listClassFiles(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * Convertit un chemin relatif en nom de classe Java
     */
    private static String toClassName(Path rel) {
        String path = rel.toString();
        
        // Normaliser tous les séparateurs de chemin en points
        path = path.replace('\\', '.').replace('/', '.');
        
        // Supprimer l'extension .class
        if (path.endsWith(".class")) {
            path = path.substring(0, path.length() - 6);
        }
        
        return path;
    }

    /**
     * Scan classes under the given classes root and return found route mappings.
     * Cette méthode fait tout le scan en interne.
     */
    public static List<RouteMapping> scanFromClassesRoot(Path classesRoot) throws Exception {
        return scanFromClassesRoot(classesRoot, null);
    }

    /**
     * Version avec ClassLoader explicite pour les environnements Servlet.
     * Cette méthode scanne tous les fichiers .class, charge les classes avec les annotations
     * @MyController et collecte les méthodes avec @HandleURL.
     */
    public static List<RouteMapping> scanFromClassesRoot(Path classesRoot, ClassLoader contextClassLoader) throws Exception {
        List<RouteMapping> result = new ArrayList<>();
        
        // Utiliser le ClassLoader approprié
        ClassLoader loader = contextClassLoader;
        URLClassLoader urlLoader = null;
        boolean shouldCloseLoader = false;
        
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        
        // Si toujours null, créer un URLClassLoader (fallback pour tests unitaires)
        if (loader == null) {
            URL url = classesRoot.toUri().toURL();
            urlLoader = new URLClassLoader(new URL[] { url });
            loader = urlLoader;
            shouldCloseLoader = true;
        }
        
        try {
            // Lister tous les fichiers .class
            List<Path> classFiles = listClassFiles(classesRoot);
            System.out.println("[DEBUG RouteMapping] Found " + classFiles.size() + " class files");
            
            // Pour chaque fichier .class
            for (Path p : classFiles) {
                Path rel = classesRoot.relativize(p);
                String className = toClassName(rel);
                
                System.out.println("[DEBUG RouteMapping] Checking class: '" + className + "'");
                
                try {
                    // Charger la classe
                    Class<?> clazz = Class.forName(className, false, loader);
                    
                    // Vérifier si elle a l'annotation @MyController
                    if (clazz.isAnnotationPresent(MyController.class)) {
                        MyController ctrl = clazz.getAnnotation(MyController.class);
                        String controllerValue = ctrl.value();
                        
                        System.out.println("[DEBUG RouteMapping] Found controller: " + className + " with value: " + controllerValue);
                        
                        // Parcourir toutes les méthodes de la classe
                        for (Method m : clazz.getDeclaredMethods()) {
                            // Vérifier si la méthode a l'annotation @HandleUrl
                            HandleUrl urlAnn = m.getAnnotation(HandleUrl.class);
                            if (urlAnn != null) {
                                String urlValue = urlAnn.value();
                                RouteMapping mapping = new RouteMapping(clazz.getName(), controllerValue, urlValue, m.getName());
                                result.add(mapping);
                                System.out.println("[DEBUG RouteMapping] Added route: " + mapping);
                            }
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("Warning: unable to load " + className + " : " + t.getClass().getSimpleName() + " " + t.getMessage());
                }
            }
        } finally {
            if (shouldCloseLoader && urlLoader != null) {
                try {
                    urlLoader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return result;
    }

    /**
     * Convertit une liste de RouteMapping en Map avec l'URL complète comme clé.
     * Facilite la recherche rapide des routes par URL.
     * 
     * @param routeMappings Liste des routes à convertir
     * @return Map avec URL -> RouteMapping
     */
    public static Map<String, RouteMapping> toMap(List<RouteMapping> routeMappings) {
        Map<String, RouteMapping> map = new HashMap<>();
        for (RouteMapping route : routeMappings) {
            map.put(route.getFullUrl(), route);
        }
        return map;
    }
}