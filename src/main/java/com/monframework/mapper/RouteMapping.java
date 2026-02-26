package com.monframework.mapper;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
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
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;

import com.monframework.annotation.MyController;
import com.monframework.annotation.HandleUrl;
import com.monframework.annotation.RequestParam;
import com.monframework.annotation.GET;
import com.monframework.annotation.POST;
import com.monframework.core.ModelView;

public class RouteMapping {
    private final String className;
    private final String controllerValue;
    private final String urlValue;
    private final String methodName;
    private final UrlPattern urlPattern;
    private final String httpMethod; // GET, POST, PUT, DELETE, etc.

    public RouteMapping(String className, String controllerValue, String urlValue, String methodName, String httpMethod) {
        this.className = className;
        this.controllerValue = controllerValue;
        this.urlValue = urlValue;
        this.methodName = methodName;
        this.httpMethod = httpMethod;
        this.urlPattern = new UrlPattern(getFullUrl());
    }

    public String getClassName() { return className; }
    public String getControllerValue() { return controllerValue; }
    public String getUrlValue() { return urlValue; }
    public String getMethodName() { return methodName; }
    public String getHttpMethod() { return httpMethod; }
    
    /**
     * Vérifie si cette route correspond à la méthode HTTP spécifiée.
     */
    public boolean matchesHttpMethod(String requestMethod) {
        // Si aucune méthode HTTP n'est spécifiée, accepter toutes les méthodes
        if (httpMethod == null || httpMethod.isEmpty()) {
            return true;
        }
        return httpMethod.equalsIgnoreCase(requestMethod);
    }
    
    /**
     * Retourne une clé unique pour cette route (URL + méthode HTTP).
     */
    public String getRouteKey() {
        String method = (httpMethod != null && !httpMethod.isEmpty()) ? httpMethod : "ALL";
        return method + ":" + getFullUrl();
    }
    
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
                ", httpMethod='" + httpMethod + '\'' +
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
     * Supporte l'annotation @RequestParam pour mapper explicitement les paramètres.
     */
    private Object[] prepareMethodArgs(Method method, Map<String, String> urlParams, HttpServletRequest request) {
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[paramTypes.length];
        
        List<String> urlParamNames = urlPattern.getParamNames();
        
        for (int i = 0; i < paramTypes.length; i++) {
            // Cas spécial: si le paramètre est de type Map, copier tous les paramètres dedans
            if (paramTypes[i] == Map.class) {
                Map<String, Object> dataMap = new HashMap<>();
                
                // Copier tous les paramètres d'URL
                dataMap.putAll(urlParams);
                
                // Copier tous les paramètres HTTP de la requête
                if (request != null) {
                    java.util.Enumeration<String> paramNames = request.getParameterNames();
                    while (paramNames.hasMoreElements()) {
                        String paramName = paramNames.nextElement();
                        dataMap.put(paramName, request.getParameter(paramName));
                    }
                }
                
                args[i] = dataMap;
                continue; // Passer au paramètre suivant
            }
            
            // Traitement normal pour les autres types
            String paramName = parameters[i].getName(); // nom par défaut de la variable
            String paramValue = null;
            
            // Vérifier si le paramètre a l'annotation @RequestParam
            RequestParam requestParamAnnotation = parameters[i].getAnnotation(RequestParam.class);
            if (requestParamAnnotation != null && !requestParamAnnotation.value().isEmpty()) {
                // Si @RequestParam est présent avec une valeur, utiliser cette valeur
                paramName = requestParamAnnotation.value();
            }
            
            // 1. Vérifier d'abord si c'est un paramètre d'URL (priorité aux paramètres d'URL)
            if (urlParamNames.contains(paramName)) {
                paramValue = urlParams.get(paramName);
            }
            // 2. Sinon, vérifier dans les paramètres HTTP (request.getParameter)
            else if (request != null) {
                paramValue = request.getParameter(paramName);
            }
            
            // Si c'est un type primitif/String, convertir directement
            if (isPrimitiveOrString(paramTypes[i])) {
                args[i] = convertParameter(paramValue, paramTypes[i]);
            }
            // Si c'est un tableau, gérer le mapping de tableau
            else if (paramTypes[i].isArray()) {
                Map<String, String[]> allParams = extractAllParameters(request, urlParams);
                args[i] = mapToArray(paramTypes[i], allParams, paramName);
            }
            // Sinon, c'est un objet complexe - utiliser mapToObject
            else {
                Map<String, String[]> allParams = extractAllParameters(request, urlParams);
                args[i] = mapToObject(paramTypes[i], allParams, paramName);
            }
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
     * Vérifie si un type est primitif ou String
     */
    private boolean isPrimitiveOrString(Class<?> type) {
        return type.isPrimitive() || 
               type == String.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Boolean.class ||
               type == Float.class ||
               type == Short.class ||
               type == Byte.class ||
               type == Character.class;
    }

    /**
     * Extrait tous les paramètres de la requête et les combine avec les paramètres d'URL
     */
    private Map<String, String[]> extractAllParameters(HttpServletRequest request, Map<String, String> urlParams) {
        Map<String, String[]> allParams = new HashMap<>();
        
        // Ajouter les paramètres d'URL
        for (Map.Entry<String, String> entry : urlParams.entrySet()) {
            allParams.put(entry.getKey(), new String[]{entry.getValue()});
        }
        
        // Ajouter tous les paramètres HTTP
        if (request != null) {
            Enumeration<String> paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = paramNames.nextElement();
                allParams.put(paramName, request.getParameterValues(paramName));
            }
        }
        
        return allParams;
    }

    /**
     * Mappe les paramètres vers un objet complexe
     * Supporte les objets imbriqués avec notation point (e.name, e.department.name)
     * Supporte les tableaux avec notation crochet (e.departments[0].name)
     */
    private <T> T mapToObject(Class<T> targetType, Map<String, String[]> parameterMap, String prefix) {
        try {
            // Créer une nouvelle instance de l'objet
            T obj = targetType.getDeclaredConstructor().newInstance();
            
            // Parcourir tous les champs de la classe
            Field[] fields = targetType.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                
                // Construire la clé du paramètre
                String key = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
                
                Class<?> fieldType = field.getType();
                
                // Cas 1: Type primitif ou String
                if (isPrimitiveOrString(fieldType)) {
                    String[] values = parameterMap.get(key);
                    if (values != null && values.length > 0) {
                        Object convertedValue = convertParameter(values[0], fieldType);
                        field.set(obj, convertedValue);
                    }
                }
                // Cas 2: Tableau
                else if (fieldType.isArray()) {
                    Object arrayValue = mapToArray(fieldType, parameterMap, key);
                    if (arrayValue != null) {
                        field.set(obj, arrayValue);
                    }
                }
                // Cas 3: Objet imbriqué
                else {
                    // Vérifier s'il y a des paramètres avec ce préfixe
                    boolean hasNestedParams = false;
                    for (String paramKey : parameterMap.keySet()) {
                        if (paramKey.startsWith(key + ".")) {
                            hasNestedParams = true;
                            break;
                        }
                    }
                    
                    if (hasNestedParams) {
                        Object nestedObj = mapToObject(fieldType, parameterMap, key);
                        field.set(obj, nestedObj);
                    }
                }
            }
            
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Mappe les paramètres vers un tableau
     * Supporte la notation tableau[0], tableau[1], etc.
     */
    private Object mapToArray(Class<?> arrayType, Map<String, String[]> parameterMap, String prefix) {
        try {
            Class<?> componentType = arrayType.getComponentType();
            
            // Trouver le nombre d'éléments dans le tableau
            int maxIndex = -1;
            for (String key : parameterMap.keySet()) {
                if (key.startsWith(prefix + "[")) {
                    int startIdx = key.indexOf('[', prefix.length()) + 1;
                    int endIdx = key.indexOf(']', startIdx);
                    if (endIdx > startIdx) {
                        try {
                            int index = Integer.parseInt(key.substring(startIdx, endIdx));
                            maxIndex = Math.max(maxIndex, index);
                        } catch (NumberFormatException e) {
                            // Ignorer si ce n'est pas un nombre
                        }
                    }
                }
            }
            
            if (maxIndex == -1) {
                // Pas de notation tableau - essayer avec un tableau de valeurs simples
                String[] values = parameterMap.get(prefix);
                if (values != null && values.length > 0) {
                    Object array = Array.newInstance(componentType, values.length);
                    for (int i = 0; i < values.length; i++) {
                        if (isPrimitiveOrString(componentType)) {
                            Array.set(array, i, convertParameter(values[i], componentType));
                        }
                    }
                    return array;
                }
                return null;
            }
            
            // Créer le tableau avec la taille appropriée
            Object array = Array.newInstance(componentType, maxIndex + 1);
            
            // Remplir le tableau
            for (int i = 0; i <= maxIndex; i++) {
                String elemPrefix = prefix + "[" + i + "]";
                
                if (isPrimitiveOrString(componentType)) {
                    // Type primitif - chercher la valeur directe
                    String[] values = parameterMap.get(elemPrefix);
                    if (values != null && values.length > 0) {
                        Array.set(array, i, convertParameter(values[0], componentType));
                    }
                } else {
                    // Objet complexe - mapper récursivement
                    Object element = mapToObject(componentType, parameterMap, elemPrefix);
                    Array.set(array, i, element);
                }
            }
            
            return array;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
                                
                                // Détecter la méthode HTTP via les annotations
                                String httpMethod = "GET"; // Par défaut
                                if (m.isAnnotationPresent(GET.class)) {
                                    httpMethod = "GET";
                                } else if (m.isAnnotationPresent(POST.class)) {
                                    httpMethod = "POST";
                                }
                                RouteMapping mapping = new RouteMapping(clazz.getName(), controllerValue, urlValue, m.getName(), httpMethod);
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
            map.put(route.getRouteKey(), route);
        }
        return map;
    }
}