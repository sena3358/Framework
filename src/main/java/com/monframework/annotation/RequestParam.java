package com.monframework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour mapper explicitement un paramètre HTTP à un argument de méthode.
 * Permet de spécifier le nom du paramètre indépendamment du nom de la variable.
 * 
 * Exemple :
 * <pre>
 * {@code
 * @HandleUrl("/user/{id}")
 * public String get(@RequestParam("id") int userId) {
 *     // userId recevra la valeur du paramètre "id"
 * }
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {
    /**
     * Le nom du paramètre HTTP à récupérer.
     * Si non spécifié, utilise le nom de la variable.
     */
    String value() default "";
}
