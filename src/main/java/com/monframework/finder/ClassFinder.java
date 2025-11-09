package com.monframework.finder;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.monframework.annotation.MyController;



/**
 * Utilitaire pour lister des fichiers .class et rechercher des classes par simpleName.
 */
public class ClassFinder {

    public static List<Path> listClassFiles(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }
    }

    public static List<String> findByFileNameSimpleName(Path root, String simpleName) throws IOException {
        List<Path> classFiles = listClassFiles(root);
        List<String> found = new ArrayList<>();
        for (Path p : classFiles) {
            String fileName = p.getFileName().toString();
            if (fileName.equals(simpleName + ".class") || fileName.startsWith(simpleName + "$")) {
                found.add(root.relativize(p).toString());
            }
        }
        return found;
    }

    public static List<String> findByLoadingSimpleName(Path root, String simpleName) throws IOException {
        List<String> result = new ArrayList<>();
        List<Path> classFiles = listClassFiles(root);

        URL url = root.toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] { url })) {
            for (Path p : classFiles) {
                Path rel = root.relativize(p);
                String className = toClassName(rel);
                try {
                    Class<?> cls = loader.loadClass(className);
                    if (simpleName.equals(cls.getSimpleName())) {
                        result.add(className);
                    }
                } catch (Throwable t) {
                    System.err.println("Warning: unable to load " + className + " : " + t.getClass().getSimpleName() + " " + t.getMessage());
                }
            }
        }
        return result;
    }

    public static List<String> findClassesAnnotatedWithControleur(Path root) throws IOException {
        return findClassesAnnotatedWithControleur(root, null);
    }

    /**
     * Version avec ClassLoader explicite - préférable pour les environnements Servlet
     */
    public static List<String> findClassesAnnotatedWithControleur(Path root, ClassLoader contextClassLoader) throws IOException {
        List<String> result = new ArrayList<>();
        List<Path> classFiles = listClassFiles(root);

        // Utiliser le ClassLoader fourni, ou le context ClassLoader, ou créer un nouveau URLClassLoader
        ClassLoader loader = contextClassLoader;
        URLClassLoader urlLoader = null;
        boolean shouldCloseLoader = false;
        
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        
        // Si toujours null, créer un URLClassLoader (fallback pour tests unitaires)
        if (loader == null) {
            URL url = root.toUri().toURL();
            urlLoader = new URLClassLoader(new URL[] { url });
            loader = urlLoader;
            shouldCloseLoader = true;
        }

        try {
            for (Path p : classFiles) {
                Path rel = root.relativize(p);
                String className = toClassName(rel);
                
                System.out.println("[DEBUG] Loading class: '" + className + "' from path: " + p);
                
                try {
                    Class<?> cls = Class.forName(className, false, loader);
                    if (cls.isAnnotationPresent(MyController.class)) {
                        MyController ann = cls.getAnnotation(MyController.class);
                        String val = ann.value();
                        if (val == null || val.isEmpty()) {
                            result.add(className);
                        } else {
                            result.add(className + " (value=" + val + ")");
                        }
                        System.out.println("[DEBUG] Found controller: " + className + " with value: " + val);
                    }
                } catch (Throwable t) {
                    System.err.println("Warning: unable to load " + className + " : " + t.getClass().getSimpleName() + " " + t.getMessage());
                    t.printStackTrace();
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
     * Convertit un chemin relatif en nom de classe Java.
     * Cette version est plus générale et supprime automatiquement les préfixes
     * de répertoire qui ne font pas partie du nom de package.
     */
    private static String toClassName(Path rel) {
        String path = rel.toString();
        
        // Normaliser tous les séparateurs de chemin en points
        path = path.replace('\\', '.').replace('/', '.');
        
        // Supprimer l'extension .class
        if (path.endsWith(".class")) {
            path = path.substring(0, path.length() - 6);
        }
        
        // Supprimer les préfixes de répertoire communs qui ne font pas partie du package
        // Cette approche est plus générale que les vérifications spécifiques
        path = removeCommonBuildAndOutputPrefixes(path);
        
        return path;
    }

    /**
     * Supprime les préfixes de répertoire courants qui ne font pas partie du package.
     * Cette méthode est extensible et peut gérer de nombreux scénarios différents.
     */
    private static String removeCommonBuildAndOutputPrefixes(String path) {
        // Liste des préfixes à supprimer (peut être étendue)
        String[] prefixes = {
            "WEB-INF.classes.",
            "target.classes.", 
            "bin.classes.",
            "build.classes.",
            "out.classes.",
            "classes.",
            "src.main.java.",  // Cas où les sources sont compilées in-place
            "src.main.test.",
            "main.java.",
            "test.java."
        };
        
        // Essayer chaque préfixe
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                String newPath = path.substring(prefix.length());
                // Vérifier que le résultat a au moins un point (package.Class)
                if (newPath.contains(".")) {
                    return newPath;
                }
            }
        }
        
        // Si aucun préfixe connu ne correspond, essayer une approche heuristique
        return removePathPrefixesHeuristically(path);
    }
    
    /**
     * Approche heuristique pour supprimer les préfixes de chemin.
     * Cherche le premier segment qui ressemble à un package Java valide.
     */
    private static String removePathPrefixesHeuristically(String path) {
        String[] parts = path.split("\\.");
        
        // Un package Java valide commence généralement par une lettre minuscule
        // et ne contient que des caractères alphanumériques et underscores
        Pattern packagePattern = Pattern.compile("^[a-z][a-z0-9_]*$");
        
        for (int i = 0; i < parts.length; i++) {
            if (packagePattern.matcher(parts[i]).matches()) {
                // Reconstruire à partir de cette partie
                StringBuilder sb = new StringBuilder();
                for (int j = i; j < parts.length; j++) {
                    if (sb.length() > 0) sb.append(".");
                    sb.append(parts[j]);
                }
                return sb.toString();
            }
        }
        
        // Si on ne trouve pas de pattern de package clair, retourner le chemin original
        return path;
    }
    
    /**
     * Version alternative : détection automatique basée sur la structure typique
     */
    private static String toClassNameAutoDetect(Path rel, Path root) {
        String path = rel.toString().replace('\\', '.').replace('/', '.');
        
        if (path.endsWith(".class")) {
            path = path.substring(0, path.length() - 6);
        }
        
        // Essayer de détecter automatiquement le préfixe à supprimer
        // en analysant la structure du répertoire racine
        String rootName = root.getFileName().toString();
        String potentialPrefix = rootName + ".classes.";
        if (path.startsWith(potentialPrefix)) {
            path = path.substring(potentialPrefix.length());
        }
        
        // Supprimer aussi "classes." seul si présent
        if (path.startsWith("classes.")) {
            path = path.substring("classes.".length());
        }
        
        return path;
    }
    
    /**
     * Méthode utilitaire pour debugger la conversion
     */
    private static String toClassNameWithDebug(Path rel) {
        String original = rel.toString();
        String normalized = original.replace('\\', '.').replace('/', '.');
        String withoutExtension = normalized.endsWith(".class") ? 
            normalized.substring(0, normalized.length() - 6) : normalized;
        String finalName = removeCommonBuildAndOutputPrefixes(withoutExtension);
        
        System.out.println("[DEBUG] Class name conversion:");
        System.out.println("  Original: " + original);
        System.out.println("  Normalized: " + normalized);
        System.out.println("  Without extension: " + withoutExtension);
        System.out.println("  Final: " + finalName);
        
        return finalName;
    }
}