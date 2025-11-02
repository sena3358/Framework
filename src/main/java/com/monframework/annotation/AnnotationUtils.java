package com.monframework.annotation;
import java.lang.annotation.Annotation;

public class AnnotationUtils {

    /**
     * Vérifie si les classes fournies ont l'annotation spécifiée et affiche un message.
     *
     * @param classes    tableau de Class<?> à vérifier
     * @param annotation type de l'annotation à chercher
     */
    public static void checkAnnotation(Class<?>[] classes, Class<? extends Annotation> annotation) {
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(annotation)) {
                System.out.println("La classe " + clazz.getSimpleName() + " est un @" + annotation.getSimpleName());
            }
        }
    }
}
