package com.monframework.core;

import jakarta.servlet.ServletException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.monframework.mapper.RouteMapping;
import com.monframework.core.ModelView;


@WebServlet(name = "FrontServlet", urlPatterns = {"/"}, loadOnStartup = 1)
public class FrontServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            scanAndCollectRoutes(getServletContext());
        } catch (Exception e) {
            throw new ServletException("Erreur lors du scan des contrôleurs et routes", e);
        }
    }
    
    private void scanAndCollectRoutes(ServletContext ctx) {
        try {
            String real = ctx.getRealPath("WEB-INF/classes");
            if (real == null) {
                System.err.println("WARNING: WEB-INF/classes path is null - déploiement non explosé");
                return;
            }
            
            System.out.println("[DEBUG] Scanning for controllers in: " + real);
            Path classesRoot = Paths.get(real);

            // Utiliser le ClassLoader du contexte de la servlet
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            System.out.println("[DEBUG] Using ClassLoader: " + contextClassLoader.getClass().getName());

            // Scanner et collecter les routes via RouteMapping
            List<RouteMapping> routeMappings = RouteMapping.scanFromClassesRoot(classesRoot, contextClassLoader);
            
            System.out.println("[DEBUG] Found " + routeMappings.size() + " route mappings");
            for (RouteMapping rm : routeMappings) {
                System.out.println("[DEBUG]   -> " + rm);
            }
            
            // Convertir la liste en Map pour une recherche rapide
            Map<String, RouteMapping> routeMap = RouteMapping.toMap(routeMappings);
            
            // Stocker le Map dans le ServletContext
            ctx.setAttribute("route.mappings", Collections.unmodifiableMap(routeMap));
            
        } catch (Exception e) {
            System.err.println("ERROR during route scanning:");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        service(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        service(request, response);
    }
    
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        
        String resourcePath = requestURI.substring(contextPath.length());
        
        // Vérifier d'abord si c'est une ressource statique
        try {
            java.net.URL resource = getServletContext().getResource(resourcePath);
            if (resource != null) {
                RequestDispatcher defaultServlet = getServletContext().getNamedDispatcher("default");
                if (defaultServlet != null) {
                    defaultServlet.forward(request, response);
                    return;
                }
            }
        } catch (Exception e) {
            // Continuer si ce n'est pas une ressource statique
        }
        
        // Récupérer le Map des routes depuis le ServletContext
        @SuppressWarnings("unchecked")
        Map<String, RouteMapping> routeMap = (Map<String, RouteMapping>) getServletContext().getAttribute("route.mappings");
        
        if (routeMap == null) {
            routeMap = Collections.emptyMap();
        }
        
        // Chercher une route correspondante
        // D'abord essayer un match exact
        RouteMapping matchedRoute = routeMap.get(resourcePath);
        Map<String, String> urlParams = new HashMap<>();
        
        // Si pas de match exact, chercher un pattern dynamique
        if (matchedRoute == null) {
            for (RouteMapping route : routeMap.values()) {
                if (route.matches(resourcePath)) {
                    matchedRoute = route;
                    urlParams = route.extractParams(resourcePath);
                    break;
                }
            }
        }
        
        if (matchedRoute != null) {
            // Route trouvée ! Afficher les informations
            showMatchedRoute(request, response, resourcePath, matchedRoute, urlParams);
        } else {
            // Aucune route trouvée, afficher la page par défaut
            showFrameworkPage(request, response, resourcePath, routeMap);
        }
    }
    

    
    /**
     * Affiche les informations de la route trouvée et gère le retour (String ou ModelView)
     */
    private void showMatchedRoute(HttpServletRequest request, HttpServletResponse response, 
                                   String requestedPath, RouteMapping route, Map<String, String> urlParams) 
            throws IOException, ServletException {
        try {
            // Appeler la méthode du contrôleur avec les paramètres extraits et la requête HTTP
            Object result = route.callMethod(urlParams, request);

            // Tester le type de retour
            if (result instanceof String) {
                // Si c'est un String, afficher directement
                response.setContentType("text/html; charset=UTF-8");
                PrintWriter out = response.getWriter();
                out.println((String) result);
                
            } else if (result instanceof ModelView) {
                // Si c'est un ModelView, faire un forward vers la JSP
                ModelView mv = (ModelView) result;
                String viewPath = mv.getView();
                
                if (viewPath != null && !viewPath.isEmpty()) {
                    // Transférer toutes les données du ModelView vers le request
                    for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                        request.setAttribute(entry.getKey(), entry.getValue());
                    }
                    
                    // Forward vers la JSP
                    RequestDispatcher dispatcher = request.getRequestDispatcher(viewPath);
                    dispatcher.forward(request, response);
                } else {
                    response.setContentType("text/plain; charset=UTF-8");
                    PrintWriter out = response.getWriter();
                    out.println("Erreur: ModelView sans vue définie");
                }
            } else {
                // Type de retour non supporté
                response.setContentType("text/plain; charset=UTF-8");
                PrintWriter out = response.getWriter();
                out.println("Erreur: Type de retour non supporté");
                out.println("Type: " + (result != null ? result.getClass().getName() : "null"));
            }

        } catch (Exception e) {
            // En cas d'erreur lors de l'appel de la méthode
            response.setContentType("text/plain; charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("Erreur lors de l'appel de la méthode");
            out.println("URL: " + requestedPath);
            out.println("Classe: " + route.getClassName());
            out.println("Méthode: " + route.getMethodName() + "()");
            out.println();
            out.println("Exception: " + e.getClass().getName());
            out.println("Message: " + e.getMessage());

            // Log l'erreur complète sur la console serveur
            System.err.println("Erreur lors de l'appel de la méthode " + route.getMethodName() + ":");
            e.printStackTrace();
        }
    }
    
    private void showFrameworkPage(HttpServletRequest request, HttpServletResponse response, 
                                 String requestedPath, Map<String, RouteMapping> routeMap) 
            throws IOException {
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter out = response.getWriter();

        out.println("Route non trouvée");
        out.println("URL demandée: " + requestedPath);
        out.println("Routes disponibles: " + routeMap.size());
        if (routeMap.isEmpty()) {
            out.println("(aucune)");
        } else {
            for (Map.Entry<String, RouteMapping> entry : routeMap.entrySet()) {
                RouteMapping rm = entry.getValue();
                // Minimal: fullUrl -> class#method
                out.println(rm.getFullUrl() + " -> " + rm.getClassName() + "#" + rm.getMethodName());
            }
        }
    }
}