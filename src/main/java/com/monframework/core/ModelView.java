package com.monframework.core;

/**
 * Classe ModelView permettant de retourner une vue (JSP) depuis un contrôleur.
 * Alternative au retour de String direct.
 */
public class ModelView {
    private String view;

    public ModelView() {
    }

    public ModelView(String view) {
        this.view = view;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }
}
