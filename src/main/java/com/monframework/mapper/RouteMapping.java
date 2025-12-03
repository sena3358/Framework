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

import com.monframework.annotation.MyController;
import com.monframework.annotation.HandleUrl;

public class RouteMapping {
    private final String className;
    private final String controllerValue;
    private final String urlValue;
    private final String methodName;

    public RouteMapping(String className, String controllerValue, String urlValue, String methodName) {
        this.className = className;
        this.controllerValue = controllerValue;
        this.urlValue = urlValue;
        this.methodName = methodName;
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
     * La méthode doit retourner un String, sinon une exception est levée.
     * 
     * @return Le résultat String retourné par la méthode
     * @throws Exception Si la méthode ne retourne pas un String ou si l'invocation échoue
     */
    public String callMethod() throws Exception {
        // Charger la classe du contrôleur
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> clazz = Class.forName(className, true, loader);

        // Créer une instance du contrôleur (constructeur par défaut)
        Object controllerInstance = clazz.getDeclaredConstructor().newInstance();

        // Trouver la méthode à invoquer
        Method method = clazz.getDeclaredMethod(methodName);

        // Vérifier que la méthode retourne un String
        if (!method.getReturnType().equals(String.class)) {
            throw new Exception("La méthode " + methodName + " de la classe " + className + 
                              " ne retourne pas un String (retourne: " + method.getReturnType().getName() + ")");
        }

        // Invoquer la méthode
        Object result = method.invoke(controllerInstance);

        // Retourner le résultat (déjà vérifié comme String)
        return (String) result;
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
}