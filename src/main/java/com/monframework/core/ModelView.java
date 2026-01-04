package com.monframework.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Classe ModelView permettant de retourner une vue (JSP) depuis un contrôleur.
 * Alternative au retour de String direct.
 * Permet aussi de passer des données à la vue.
 */
public class ModelView {
    private String view;
    private Map<String, Object> data;

    public ModelView() {
        this.data = new HashMap<>();
    }

    public ModelView(String view) {
        this.view = view;
        this.data = new HashMap<>();
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Ajoute une donnée au modèle qui sera disponible dans la vue
     * @param key Le nom de l'attribut
     * @param value La valeur de l'attribut
     * @return this (pour permettre le chaînage)
     */
    public ModelView addObject(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
